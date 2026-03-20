package dev.gwaboard.companion.privacy

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.gwaboard.companion.db.ProfileDatabase
import dev.gwaboard.shared.models.IpcContract

/**
 * Manages GDPR-compliant data deletion for the companion app.
 *
 * Coordinates:
 * 1. Purging data from the local SQLite database
 * 2. Broadcasting deletion events to the keyboard app so it can
 *    invalidate its in-memory profile caches
 *
 * Broadcasts use signature-level protection — only the keyboard app
 * (signed with the same certificate) will receive them.
 */
class DataDeletionManager(
    private val context: Context,
    private val profileDatabase: ProfileDatabase,
) {

    companion object {
        private const val TAG = "DataDeletionManager"
    }

    /**
     * Deletes all profile data and notifies the keyboard app.
     *
     * This implements the GDPR "right to erasure" — all derived statistical
     * profiles are permanently removed. Raw SMS data is never stored by the
     * companion app, so only profiles need deletion.
     */
    suspend fun deleteAllData() {
        profileDatabase.deleteAllProfiles()
        Log.i(TAG, "All profile data deleted from database")

        // Notify keyboard app to invalidate all cached profiles
        val intent = Intent(IpcContract.Actions.DATA_DELETED_ALL).apply {
            setPackage("dev.gwaboard.keyboard")
        }
        context.sendBroadcast(intent)
        Log.i(TAG, "Broadcast sent: DATA_DELETED_ALL")
    }

    /**
     * Deletes a single contact's profile and notifies the keyboard app.
     *
     * @param contactAddress Phone number or address identifying the contact.
     * @return True if a profile was found and deleted.
     */
    suspend fun deleteContactData(contactAddress: String): Boolean {
        val deleted = profileDatabase.deleteProfile(contactAddress)

        if (deleted) {
            Log.i(TAG, "Profile deleted for contact: $contactAddress")

            // Notify keyboard app to invalidate this contact's cached profile
            val intent = Intent(IpcContract.Actions.DATA_DELETED_CONTACT).apply {
                setPackage("dev.gwaboard.keyboard")
                putExtra(IpcContract.Extras.CONTACT_ADDRESS, contactAddress)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "Broadcast sent: DATA_DELETED_CONTACT for $contactAddress")
        }

        return deleted
    }
}
