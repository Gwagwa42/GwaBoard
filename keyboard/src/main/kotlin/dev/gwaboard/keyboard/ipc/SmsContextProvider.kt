package dev.gwaboard.keyboard.ipc

import android.util.Log
import dev.gwaboard.shared.ipc.SmsProviderClient
import dev.gwaboard.shared.models.ContactProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keyboard-side IPC component that queries the companion app for contact profiles.
 *
 * Wraps [SmsProviderClient] and exposes a reactive [Flow] of the current contact's
 * [ContactProfile]. Profiles are cached in memory for the current session to avoid
 * re-querying the ContentProvider on every keystroke.
 *
 * Graceful degradation: if the companion app is not installed or its signature
 * does not match, the flow emits `null` and the keyboard works normally without
 * contact context.
 *
 * @param smsProviderClient The shared-ipc client for querying the companion ContentProvider
 * @param dispatcher Coroutine dispatcher for background IPC queries
 * @param scope Coroutine scope for launching async profile fetches
 */
class SmsContextProvider(
    private val smsProviderClient: SmsProviderClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
) {
    companion object {
        private const val TAG = "SmsContextProvider"
    }

    private val scope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + dispatcher)

    /** Backing state for the current contact profile. Emits null when no profile is available. */
    private val _contactProfile = MutableStateFlow<ContactProfile?>(null)

    /** Observable profile for the current contact. Emits null when companion is unavailable. */
    val contactProfile: Flow<ContactProfile?> = _contactProfile.asStateFlow()

    /** Current value of the cached profile (non-reactive access for synchronous reads). */
    val currentProfile: ContactProfile? get() = _contactProfile.value

    /** Cached contact ID to avoid redundant IPC queries for the same contact. */
    @Volatile
    private var cachedContactId: Long? = null

    /** Whether the companion app is installed and signature-verified. */
    val isCompanionAvailable: Boolean get() = smsProviderClient.isCompanionAvailable

    /**
     * Requests the profile for a contact identified by [contactId].
     *
     * If the profile for this contact is already cached, this is a no-op.
     * Otherwise, queries the companion app's ContentProvider asynchronously
     * and updates the [contactProfile] flow.
     *
     * If the companion app is not installed, the flow emits `null` immediately.
     *
     * @param contactId System contact identifier to look up
     */
    fun requestProfile(contactId: Long) {
        // Skip if already cached for this contact
        if (contactId == cachedContactId && _contactProfile.value != null) {
            Log.d(TAG, "Profile for contact $contactId already cached, skipping query")
            return
        }

        scope.launch {
            val profile = try {
                smsProviderClient.queryContactProfile(contactId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query profile for contact $contactId", e)
                null
            }

            cachedContactId = contactId
            _contactProfile.value = profile

            if (profile != null) {
                Log.d(TAG, "Profile loaded for contact $contactId: lang=${profile.dominantLanguage}, tone=${profile.tone}")
            } else {
                Log.d(TAG, "No profile available for contact $contactId")
            }
        }
    }

    /**
     * Resolves a contact ID from a phone address (hashed) by querying the
     * companion's contacts list and matching against the hashed address.
     *
     * This enables the keyboard to find the right contact profile when only
     * the SMS address is known (e.g., from EditorInfo context).
     *
     * @param hashedAddress SHA-256 hash of the normalized phone number
     * @return The contact ID if found, or null
     */
    fun resolveContactId(hashedAddress: String): Long? {
        if (!isCompanionAvailable) return null

        return try {
            smsProviderClient.queryContacts()
                .firstOrNull { it.hashedAddress == hashedAddress }
                ?.contactId
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve contact for address hash", e)
            null
        }
    }

    /**
     * Invalidates the cached profile. Should be called on app switch
     * (when the user navigates away from the current messaging app)
     * to ensure fresh data is fetched for the next conversation.
     */
    fun invalidateCache() {
        cachedContactId = null
        _contactProfile.value = null
        Log.d(TAG, "Profile cache invalidated")
    }

    /**
     * Releases resources. After calling this, the provider should not be used.
     */
    fun close() {
        invalidateCache()
        Log.d(TAG, "SmsContextProvider closed")
    }
}
