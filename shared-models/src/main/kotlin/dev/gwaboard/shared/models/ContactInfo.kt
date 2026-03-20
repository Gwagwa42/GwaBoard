package dev.gwaboard.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Basic contact information shared via IPC.
 * The address is stored as a hash for privacy — the keyboard
 * never sees raw phone numbers.
 */
@Serializable
data class ContactInfo(
    /** System contact identifier */
    @SerialName("contact_id")
    val contactId: Long,

    /** Contact display name */
    @SerialName("display_name")
    val displayName: String,

    /** SHA-256 hash of the normalized phone number for privacy-safe matching */
    @SerialName("hashed_address")
    val hashedAddress: String,
)
