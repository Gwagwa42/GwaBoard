package dev.gwaboard.keyboard.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.gwaboard.shared.models.IpcContract

/**
 * Receives data deletion broadcasts from the companion app.
 *
 * When the companion app deletes profile data (per-contact or all),
 * it sends a directed broadcast to this receiver. The receiver
 * invalidates the keyboard's in-memory profile cache to ensure
 * stale data is never used for suggestions.
 *
 * Security: the broadcast is sent with `setPackage("dev.gwaboard.keyboard")`
 * from the companion app, and this receiver is not exported to other apps.
 */
class DataDeletionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DataDeletionReceiver"

        /**
         * Callback interface for notifying the keyboard service about deletion events.
         * Set by the IME service during initialization.
         */
        @Volatile
        var onAllDataDeleted: (() -> Unit)? = null

        @Volatile
        var onContactDataDeleted: ((contactAddress: String) -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            IpcContract.Actions.DATA_DELETED_ALL -> {
                Log.i(TAG, "Received DATA_DELETED_ALL broadcast — invalidating all caches")
                onAllDataDeleted?.invoke()
            }

            IpcContract.Actions.DATA_DELETED_CONTACT -> {
                val address = intent.getStringExtra(IpcContract.Extras.CONTACT_ADDRESS)
                if (address != null) {
                    Log.i(TAG, "Received DATA_DELETED_CONTACT broadcast for: $address")
                    onContactDataDeleted?.invoke(address)
                } else {
                    Log.w(TAG, "DATA_DELETED_CONTACT broadcast missing contact address extra")
                }
            }
        }
    }
}
