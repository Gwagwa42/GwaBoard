package dev.gwaboard.shared.ipc

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import dev.gwaboard.shared.models.ContactInfo
import dev.gwaboard.shared.models.ContactProfile
import dev.gwaboard.shared.models.IpcContract
import dev.gwaboard.shared.models.SmsMessage

/**
 * Client for querying the companion app's ContentProvider.
 *
 * Wraps [ContentResolver.query] calls and parses [Cursor] rows
 * into shared-models data classes. If the companion app is not
 * installed or its signature does not match, all queries return
 * empty results gracefully — no crash, no exception.
 *
 * @param context Application or service context
 * @param companionPackage Package name of the companion app
 */
class SmsProviderClient(
    private val context: Context,
    private val companionPackage: String = COMPANION_PACKAGE,
) {
    companion object {
        /** Default companion app package name */
        const val COMPANION_PACKAGE = "dev.gwaboard.companion"
    }

    /**
     * Whether the companion app is installed and signed with
     * the same certificate. Cached per instance.
     */
    val isCompanionAvailable: Boolean by lazy {
        SignatureVerifier.isSameSignature(context, companionPackage)
    }

    /**
     * Queries recent SMS messages, optionally filtered by thread.
     *
     * @param threadId If non-null, only return messages from this thread
     * @param limit Maximum number of messages to return
     * @return List of [SmsMessage], or empty list if companion is unavailable
     */
    fun queryMessages(threadId: Long? = null, limit: Int = 50): List<SmsMessage> {
        if (!isCompanionAvailable) return emptyList()

        val selection = threadId?.let { "${IpcContract.SmsColumns.THREAD_ID} = ?" }
        val selectionArgs = threadId?.let { arrayOf(it.toString()) }
        val sortOrder = "${IpcContract.SmsColumns.DATE} DESC LIMIT $limit"

        return safeQuery(SmsProviderContract.SMS_MESSAGES_URI, selection, selectionArgs, sortOrder) { cursor ->
            SmsMessage(
                id = cursor.getLongColumn(IpcContract.SmsColumns.ID),
                threadId = cursor.getLongColumn(IpcContract.SmsColumns.THREAD_ID),
                address = cursor.getStringColumn(IpcContract.SmsColumns.ADDRESS),
                body = cursor.getStringColumn(IpcContract.SmsColumns.BODY),
                date = cursor.getLongColumn(IpcContract.SmsColumns.DATE),
                type = cursor.getIntColumn(IpcContract.SmsColumns.TYPE),
            )
        }
    }

    /**
     * Queries contact information.
     *
     * @return List of [ContactInfo], or empty list if companion is unavailable
     */
    fun queryContacts(): List<ContactInfo> {
        if (!isCompanionAvailable) return emptyList()

        return safeQuery(SmsProviderContract.CONTACTS_URI) { cursor ->
            ContactInfo(
                contactId = cursor.getLongColumn(IpcContract.ContactColumns.CONTACT_ID),
                displayName = cursor.getStringColumn(IpcContract.ContactColumns.DISPLAY_NAME),
                hashedAddress = cursor.getStringColumn(IpcContract.ContactColumns.HASHED_ADDRESS),
            )
        }
    }

    /**
     * Queries the AI-generated profile for a specific contact.
     *
     * @param contactId System contact identifier
     * @return [ContactProfile] if available, or `null`
     */
    fun queryContactProfile(contactId: Long): ContactProfile? {
        if (!isCompanionAvailable) return null

        val selection = "${IpcContract.ContactColumns.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        val results = safeQuery(
            SmsProviderContract.CONTACT_PROFILES_URI,
            selection,
            selectionArgs,
        ) { cursor ->
            ContactProfile(
                dominantLanguage = cursor.getStringColumn(IpcContract.ProfileColumns.DOMINANT_LANGUAGE),
                tone = cursor.getStringColumn(IpcContract.ProfileColumns.TONE),
                avgResponseLength = cursor.getIntColumn(IpcContract.ProfileColumns.AVG_RESPONSE_LENGTH),
                topNgrams = cursor.getStringColumn(IpcContract.ProfileColumns.TOP_NGRAMS)
                    .split(",")
                    .filter { it.isNotBlank() },
                styleEmbedding = cursor.getStringColumn(IpcContract.ProfileColumns.STYLE_EMBEDDING)
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { it.toFloat() },
            )
        }

        return results.firstOrNull()
    }

    // ── Internal helpers ──────────────────────────────────────────────

    /**
     * Executes a ContentResolver query wrapped in a try-catch for
     * [SecurityException] (companion not installed or signature mismatch)
     * and general exceptions. Returns empty list on any failure.
     */
    private fun <T> safeQuery(
        uri: android.net.Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        mapper: (Cursor) -> T,
    ): List<T> {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                null, // projection — request all columns
                selection,
                selectionArgs,
                sortOrder,
            ) ?: return emptyList()

            cursor.use { c ->
                val results = mutableListOf<T>()
                while (c.moveToNext()) {
                    results.add(mapper(c))
                }
                results
            }
        } catch (_: SecurityException) {
            // Companion app not installed, wrong signature, or no permission
            emptyList()
        } catch (_: Exception) {
            // ContentProvider threw an unexpected error
            emptyList()
        }
    }
}

// ── Cursor extension helpers ──────────────────────────────────────────

/**
 * Reads a [String] column by name, returning an empty string if the
 * column is missing or the value is null.
 */
private fun Cursor.getStringColumn(columnName: String): String {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getString(index).orEmpty() else ""
}

/**
 * Reads a [Long] column by name, returning 0 if the column is missing.
 */
private fun Cursor.getLongColumn(columnName: String): Long {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getLong(index) else 0L
}

/**
 * Reads an [Int] column by name, returning 0 if the column is missing.
 */
private fun Cursor.getIntColumn(columnName: String): Int {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getInt(index) else 0
}
