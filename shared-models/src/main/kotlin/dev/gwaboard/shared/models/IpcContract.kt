package dev.gwaboard.shared.models

/**
 * IPC contract constants shared between :keyboard and :companion modules.
 * Defines the ContentProvider authority, URI paths, and column names
 * used for signature-protected inter-process communication.
 */
object IpcContract {

    /** ContentProvider authority for the companion app */
    const val AUTHORITY = "dev.gwaboard.companion.provider"

    /** Base content URI */
    const val BASE_URI = "content://$AUTHORITY"

    /** URI paths for each content type */
    object Paths {
        const val SMS_MESSAGES = "sms_messages"
        const val SMS_THREADS = "sms_threads"
        const val CONTACTS = "contacts"
        const val CONTACT_PROFILES = "contact_profiles"
    }

    /** Column name constants for SMS messages */
    object SmsColumns {
        const val ID = "id"
        const val THREAD_ID = "thread_id"
        const val ADDRESS = "address"
        const val BODY = "body"
        const val DATE = "date"
        const val TYPE = "type"
    }

    /** Column name constants for SMS threads */
    object ThreadColumns {
        const val ID = "id"
        const val RECIPIENT_ADDRESS = "recipient_address"
        const val SNIPPET = "snippet"
        const val MESSAGE_COUNT = "message_count"
        const val LAST_DATE = "last_date"
    }

    /** Column name constants for contacts */
    object ContactColumns {
        const val CONTACT_ID = "contact_id"
        const val DISPLAY_NAME = "display_name"
        const val HASHED_ADDRESS = "hashed_address"
    }

    /**
     * Broadcast actions for GDPR data deletion coordination between apps.
     * The companion app sends these broadcasts when user requests data erasure.
     * The keyboard app listens and invalidates its in-memory caches accordingly.
     */
    object Actions {
        /** Broadcast sent when all profile data has been deleted in the companion app */
        const val DATA_DELETED_ALL = "dev.gwaboard.action.DATA_DELETED_ALL"

        /** Broadcast sent when a single contact's profile has been deleted.
         *  Includes extra [Extras.CONTACT_ADDRESS] identifying the removed contact. */
        const val DATA_DELETED_CONTACT = "dev.gwaboard.action.DATA_DELETED_CONTACT"
    }

    /** Intent extras for data deletion broadcasts */
    object Extras {
        /** Contact phone address (String) included with [Actions.DATA_DELETED_CONTACT] */
        const val CONTACT_ADDRESS = "dev.gwaboard.extra.CONTACT_ADDRESS"
    }

    /** Column name constants for contact profiles */
    object ProfileColumns {
        const val CONTACT_ID = "contact_id"
        const val DOMINANT_LANGUAGE = "dominant_language"
        const val TONE = "tone"
        const val AVG_RESPONSE_LENGTH = "avg_response_length"
        const val TOP_NGRAMS = "top_ngrams"
        const val STYLE_EMBEDDING = "style_embedding"
    }
}
