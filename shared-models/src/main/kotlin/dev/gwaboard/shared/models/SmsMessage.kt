package dev.gwaboard.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single SMS message exchanged via IPC between
 * the companion app (SMS provider) and the keyboard (AI consumer).
 */
@Serializable
data class SmsMessage(
    /** Unique message identifier */
    val id: Long,

    /** Conversation thread this message belongs to */
    @SerialName("thread_id")
    val threadId: Long,

    /** Phone number or address of the sender/recipient */
    val address: String,

    /** Message text content */
    val body: String,

    /** Timestamp in milliseconds since epoch */
    val date: Long,

    /** Message type: 1 = inbox (received), 2 = sent */
    val type: Int,
)
