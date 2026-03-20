package dev.gwaboard.companion.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import dev.gwaboard.shared.ipc.SignatureVerifier
import dev.gwaboard.shared.models.IpcContract
import dev.gwaboard.shared.models.ContactProfile
import kotlinx.serialization.json.Json

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
 * 3. **Payload-level**: Profile data is returned as plaintext columns in the cursor.
 *    Encryption was removed because the Keystore key cannot be shared across apps
 *    (see issue #22). Security relies on layers 1 and 2 (signature permission +
 *    runtime certificate verification).
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

        /** Plaintext column names returned in profile query cursors */
        private val PROFILE_COLUMNS = arrayOf(
            IpcContract.ProfileColumns.CONTACT_ID,
            IpcContract.ProfileColumns.DOMINANT_LANGUAGE,
            IpcContract.ProfileColumns.TONE,
            IpcContract.ProfileColumns.AVG_RESPONSE_LENGTH,
            IpcContract.ProfileColumns.TOP_NGRAMS,
            IpcContract.ProfileColumns.STYLE_EMBEDDING,
        )

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(IpcContract.AUTHORITY, IpcContract.Paths.CONTACT_PROFILES, PROFILES_ALL)
            addURI(IpcContract.AUTHORITY, "${IpcContract.Paths.CONTACT_PROFILES}/#", PROFILES_BY_CONTACT)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * In-memory profile store. In a real implementation, this would be backed
     * by a Room database or similar persistent storage populated by the
     * companion app's SMS analysis pipeline.
     */
    private val profileStore = ProfileStore()

    override fun onCreate(): Boolean {
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
     * Returns all available contact profiles as plaintext columns.
     *
     * Cursor columns match [IpcContract.ProfileColumns]:
     * - contact_id, dominant_language, tone, avg_response_length, top_ngrams, style_embedding
     */
    private fun queryAllProfiles(): Cursor {
        val cursor = MatrixCursor(PROFILE_COLUMNS)

        for ((contactId, profileJson) in profileStore.getAllProfiles()) {
            addProfileRow(cursor, contactId, profileJson)
        }

        return cursor
    }

    /**
     * Returns a single contact profile as plaintext columns.
     *
     * @param contactId System contact identifier
     * @return Cursor with one row if profile exists, empty cursor otherwise
     */
    private fun querySingleProfile(contactId: Long): Cursor {
        val cursor = MatrixCursor(PROFILE_COLUMNS)

        val profileJson = profileStore.getProfile(contactId) ?: return cursor
        addProfileRow(cursor, contactId, profileJson)

        return cursor
    }

    /**
     * Deserializes a profile JSON string and adds a plaintext row to the cursor.
     * List fields (top_ngrams, style_embedding) are serialized as CSV strings
     * to match the flat cursor schema expected by [SmsProviderClient].
     * Column order matches [PROFILE_COLUMNS].
     */
    private fun addProfileRow(cursor: MatrixCursor, contactId: Long, profileJson: String) {
        val profile = json.decodeFromString<ContactProfile>(profileJson)
        cursor.addRow(
            arrayOf(
                contactId,
                profile.dominantLanguage,
                profile.tone,
                profile.avgResponseLength,
                profile.topNgrams.joinToString(","),
                profile.styleEmbedding.joinToString(","),
            ),
        )
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
