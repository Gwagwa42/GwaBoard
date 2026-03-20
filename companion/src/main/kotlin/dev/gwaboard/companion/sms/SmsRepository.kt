package dev.gwaboard.companion.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import dev.gwaboard.shared.models.SmsMessage
import dev.gwaboard.shared.models.SmsThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS data from the system Telephony ContentProvider.
 *
 * Requires READ_SMS permission. All queries run on [Dispatchers.IO]
 * to avoid blocking the main thread.
 *
 * This repository is read-only — it never modifies the SMS database.
 * Raw message bodies are only used transiently for profile analysis;
 * they are never persisted by the companion app.
 */
class SmsRepository(
    private val contentResolver: ContentResolver,
) {

    /**
     * Query all SMS messages (inbox + sent) from the system database.
     *
     * @param sinceTimestamp Only return messages newer than this epoch millis.
     *                      Pass 0 to retrieve all messages.
     * @param limit Maximum number of messages to return. Pass 0 for no limit.
     * @return List of [SmsMessage] sorted by date descending (newest first).
     */
    suspend fun getAllMessages(
        sinceTimestamp: Long = 0,
        limit: Int = 0,
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()

        val selection = if (sinceTimestamp > 0) {
            "${Telephony.Sms.DATE} > ?"
        } else {
            null
        }
        val selectionArgs = if (sinceTimestamp > 0) {
            arrayOf(sinceTimestamp.toString())
        } else {
            null
        }
        val sortOrder = buildString {
            append("${Telephony.Sms.DATE} DESC")
            if (limit > 0) append(" LIMIT $limit")
        }

        val cursor: Cursor? = try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                selection,
                selectionArgs,
                sortOrder,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "READ_SMS permission not granted", e)
            null
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (c.moveToNext()) {
                val address = c.getString(addressIdx)
                // Skip messages with null address (rare system messages)
                if (address == null) continue

                messages += SmsMessage(
                    id = c.getLong(idIdx),
                    threadId = c.getLong(threadIdx),
                    address = address,
                    body = c.getString(bodyIdx) ?: "",
                    date = c.getLong(dateIdx),
                    type = c.getInt(typeIdx),
                )
            }
        }

        Log.d(TAG, "Loaded ${messages.size} SMS messages")
        messages
    }

    /**
     * Group SMS messages by contact address.
     *
     * @param sinceTimestamp Only include messages newer than this epoch millis.
     * @return Map of contact address to list of messages for that contact.
     */
    suspend fun getMessagesByContact(
        sinceTimestamp: Long = 0,
    ): Map<String, List<SmsMessage>> {
        return getAllMessages(sinceTimestamp = sinceTimestamp)
            .groupBy { it.address }
    }

    /**
     * Query SMS conversation threads from the system database.
     *
     * @return List of [SmsThread] sorted by most recent activity.
     */
    suspend fun getThreads(): List<SmsThread> = withContext(Dispatchers.IO) {
        val threads = mutableListOf<SmsThread>()

        val cursor: Cursor? = try {
            contentResolver.query(
                THREADS_URI,
                THREAD_PROJECTION,
                null,
                null,
                "${Telephony.Sms.Conversations.DEFAULT_SORT_ORDER}",
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "READ_SMS permission not granted", e)
            null
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID)
            val snippetIdx = c.getColumnIndexOrThrow(Telephony.Sms.Conversations.SNIPPET)
            val countIdx = c.getColumnIndexOrThrow("msg_count")

            while (c.moveToNext()) {
                val threadId = c.getLong(idIdx)
                val snippet = c.getString(snippetIdx) ?: ""

                threads += SmsThread(
                    id = threadId,
                    recipientAddress = resolveThreadAddress(threadId),
                    snippet = snippet,
                    messageCount = c.getInt(countIdx),
                    lastDate = getLatestDateForThread(threadId),
                )
            }
        }

        Log.d(TAG, "Loaded ${threads.size} SMS threads")
        threads
    }

    /**
     * Resolve the contact address for a given thread ID by reading
     * the most recent message in that thread.
     */
    private fun resolveThreadAddress(threadId: Long): String {
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )

        return cursor?.use { c ->
            if (c.moveToFirst()) c.getString(0) ?: "" else ""
        } ?: ""
    }

    /**
     * Get the timestamp of the most recent message in a thread.
     */
    private fun getLatestDateForThread(threadId: Long): Long {
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )

        return cursor?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L
    }

    companion object {
        private const val TAG = "SmsRepository"

        /** Conversations/threads URI */
        private val THREADS_URI: Uri = Uri.parse("content://sms/conversations")

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )

        private val THREAD_PROJECTION = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET,
            "msg_count",
        )
    }
}
