package dev.gwaboard.shared.ipc

import android.net.Uri
import dev.gwaboard.shared.models.IpcContract

/**
 * Android URI construction helpers built on top of [IpcContract] constants.
 *
 * Translates the pure-Kotlin contract strings into Android [Uri] objects
 * that can be passed to [android.content.ContentResolver.query].
 */
object SmsProviderContract {

    /** Base content URI for the companion app's ContentProvider */
    val CONTENT_URI: Uri = Uri.parse(IpcContract.BASE_URI)

    /** URI to query SMS messages */
    val SMS_MESSAGES_URI: Uri = Uri.withAppendedPath(CONTENT_URI, IpcContract.Paths.SMS_MESSAGES)

    /** URI to query SMS threads */
    val SMS_THREADS_URI: Uri = Uri.withAppendedPath(CONTENT_URI, IpcContract.Paths.SMS_THREADS)

    /** URI to query contacts */
    val CONTACTS_URI: Uri = Uri.withAppendedPath(CONTENT_URI, IpcContract.Paths.CONTACTS)

    /** URI to query contact profiles */
    val CONTACT_PROFILES_URI: Uri = Uri.withAppendedPath(CONTENT_URI, IpcContract.Paths.CONTACT_PROFILES)
}
