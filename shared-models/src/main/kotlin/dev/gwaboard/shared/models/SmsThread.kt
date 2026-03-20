package dev.gwaboard.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an SMS conversation thread, aggregating messages
 * with the same recipient for display and AI context building.
 */
@Serializable
data class SmsThread(
    /** Unique thread identifier */
    val id: Long,

    /** Phone number or address of the conversation partner */
    @SerialName("recipient_address")
    val recipientAddress: String,

    /** Preview text from the most recent message */
    val snippet: String,

    /** Total number of messages in this thread */
    @SerialName("message_count")
    val messageCount: Int,

    /** Timestamp of the most recent message (millis since epoch) */
    @SerialName("last_date")
    val lastDate: Long,
)
