package dev.gwaboard.companion.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import dev.gwaboard.shared.crypto.EncryptedPayload
import dev.gwaboard.shared.crypto.SessionCipher
import dev.gwaboard.shared.ipc.SignatureVerifier
import dev.gwaboard.shared.models.IpcContract

/**
 * ContentProvider that exposes per-contact SMS profiles to the keyboard app
 * via signature-protected IPC.
 *
 * ## Security layers
 *
 * 1. **OS-level**: The `android:readPermission` attribute on the `<provider>` tag
 *    restricts access to apps holding `dev.gwaboard.permission.READ_SMS_IPC`,
 *    which has `protectionLevel="signature"` — only apps signed with the same
 *    certificate can obtain this permission.
 *
 * 2. **App-level**: [validateCaller] performs an additional runtime certificate
 *    verification using [SignatureVerifier], guarding against edge cases where
 *    permission grants could be spoofed.
 *
 * 3. **Payload-level**: All query results are encrypted with AES-256-GCM via
 *    [SessionCipher] before being returned in the cursor. The keyboard app
 *    must decrypt using the same Keystore-backed key.
 *
 * ## Supported URI patterns
 *
 * - `content://dev.gwaboard.companion.provider/contact_profiles` — list all profiles
 * - `content://dev.gwaboard.companion.provider/contact_profiles/{contactId}` — single profile
 */
class SmsContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "SmsContentProvider"

        // URI matcher codes
        private const val PROFILES_ALL = 1
        private const val PROFILES_BY_CONTACT = 2

        /** Column name for the encrypted payload (Base64-encoded ciphertext) */
        const val COLUMN_ENCRYPTED_DATA = "encrypted_data"

        /** Column name for the IV (Base64-encoded) needed to decrypt the payload */
        const val COLUMN_ENCRYPTED_IV = "encrypted_iv"

        /** Column name for the contact ID in encrypted result sets */
        const val COLUMN_CONTACT_ID = "contact_id"

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(IpcContract.AUTHORITY, IpcContract.Paths.CONTACT_PROFILES, PROFILES_ALL)
            addURI(IpcContract.AUTHORITY, "${IpcContract.Paths.CONTACT_PROFILES}/#", PROFILES_BY_CONTACT)
        }
    }

    private lateinit var sessionCipher: SessionCipher

    /**
     * In-memory profile store. In a real implementation, this would be backed
     * by a Room database or similar persistent storage populated by the
     * companion app's SMS analysis pipeline.
     */
    private val profileStore = ProfileStore()

    override fun onCreate(): Boolean {
        sessionCipher = SessionCipher()
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null

        // Security layer 2: runtime certificate verification
        if (!validateCaller()) {
            Log.w(TAG, "Rejected query from caller with mismatched signature")
            return null
        }

        return when (uriMatcher.match(uri)) {
            PROFILES_ALL -> queryAllProfiles()
            PROFILES_BY_CONTACT -> {
                val contactId = uri.lastPathSegment?.toLongOrNull()
                    ?: return null
                querySingleProfile(contactId)
            }
            else -> {
                Log.w(TAG, "Unknown URI pattern: $uri")
                null
            }
        }
    }

    // ── Query implementations ─────────────────────────────────────────

    /**
     * Returns all available contact profiles, each row encrypted individually.
     *
     * Cursor columns:
     * - [COLUMN_CONTACT_ID]: the contact ID (unencrypted, needed for routing)
     * - [COLUMN_ENCRYPTED_DATA]: Base64-encoded AES-256-GCM ciphertext of the profile JSON
     * - [COLUMN_ENCRYPTED_IV]: Base64-encoded IV for decryption
     */
    private fun queryAllProfiles(): Cursor {
        val cursor = MatrixCursor(
            arrayOf(COLUMN_CONTACT_ID, COLUMN_ENCRYPTED_DATA, COLUMN_ENCRYPTED_IV),
        )

        for ((contactId, profileJson) in profileStore.getAllProfiles()) {
            val encrypted = sessionCipher.encrypt(profileJson.toByteArray(Charsets.UTF_8))
            cursor.addRow(
                arrayOf(
                    contactId,
                    Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
                    Base64.encodeToString(encrypted.iv, Base64.NO_WRAP),
                ),
            )
        }

        return cursor
    }

    /**
     * Returns a single contact profile, encrypted with AES-256-GCM.
     *
     * @param contactId System contact identifier
     * @return Cursor with one row if profile exists, empty cursor otherwise
     */
    private fun querySingleProfile(contactId: Long): Cursor {
        val cursor = MatrixCursor(
            arrayOf(COLUMN_CONTACT_ID, COLUMN_ENCRYPTED_DATA, COLUMN_ENCRYPTED_IV),
        )

        val profileJson = profileStore.getProfile(contactId) ?: return cursor

        val encrypted = sessionCipher.encrypt(profileJson.toByteArray(Charsets.UTF_8))
        cursor.addRow(
            arrayOf(
                contactId,
                Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
                Base64.encodeToString(encrypted.iv, Base64.NO_WRAP),
            ),
        )

        return cursor
    }

    // ── Security ──────────────────────────────────────────────────────

    /**
     * Validates that the calling app shares the same signing certificate.
     *
     * This is security layer 2 — an additional app-level check on top of
     * the OS-enforced signature permission (layer 1). Guards against
     * theoretical edge cases in permission grant mechanics.
     *
     * @return `true` if the caller's certificate matches, `false` otherwise
     */
    private fun validateCaller(): Boolean {
        val ctx = context ?: return false
        val callingPackage = callingPackage ?: return false

        return SignatureVerifier.isSameSignature(ctx, callingPackage)
    }

    // ── Unsupported operations ────────────────────────────────────────
    // This provider is read-only — the keyboard never writes to the companion.

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            PROFILES_ALL -> "vnd.android.cursor.dir/vnd.${IpcContract.AUTHORITY}.profile"
            PROFILES_BY_CONTACT -> "vnd.android.cursor.item/vnd.${IpcContract.AUTHORITY}.profile"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Read-only provider: insert not supported")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Read-only provider: update not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Read-only provider: delete not supported")
    }
}
