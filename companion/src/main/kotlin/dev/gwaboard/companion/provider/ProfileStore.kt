package dev.gwaboard.companion.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.gwaboard.shared.models.ContactProfile

/**
 * In-memory store for contact profiles.
 *
 * This is a placeholder implementation. The real version will be backed
 * by a Room database populated by the companion app's SMS analysis
 * pipeline (Tier-2 LLM profile generation).
 *
 * The store serializes [ContactProfile] instances to JSON before
 * returning them, as the ContentProvider encrypts the JSON payload
 * before sending it over IPC.
 */
class ProfileStore {

    private val json = Json { encodeDefaults = true }

    /** In-memory profile map: contactId → ContactProfile */
    private val profiles = mutableMapOf<Long, ContactProfile>()

    /**
     * Store or update a profile for a contact.
     *
     * @param contactId System contact identifier
     * @param profile AI-generated contact profile
     */
    fun putProfile(contactId: Long, profile: ContactProfile) {
        profiles[contactId] = profile
    }

    /**
     * Retrieve the [ContactProfile] for a specific contact.
     *
     * @param contactId System contact identifier
     * @return The [ContactProfile], or `null` if no profile exists
     */
    fun getProfileObject(contactId: Long): ContactProfile? {
        return profiles[contactId]
    }

    /**
     * Retrieve all stored profiles as (contactId, [ContactProfile]) pairs.
     *
     * @return Map of contact IDs to their [ContactProfile] instances
     */
    fun getAllProfileObjects(): Map<Long, ContactProfile> {
        return profiles.toMap()
    }

    /**
     * Retrieve the profile JSON for a specific contact.
     *
     * @param contactId System contact identifier
     * @return Serialized JSON string, or `null` if no profile exists
     */
    fun getProfile(contactId: Long): String? {
        val profile = profiles[contactId] ?: return null
        return json.encodeToString(profile)
    }

    /**
     * Retrieve all stored profiles as (contactId, profileJson) pairs.
     *
     * @return Map of contact IDs to their serialized JSON profiles
     */
    fun getAllProfiles(): Map<Long, String> {
        return profiles.mapValues { (_, profile) ->
            json.encodeToString(profile)
        }
    }

    /**
     * Remove the profile for a specific contact.
     *
     * @param contactId System contact identifier
     * @return `true` if a profile was removed, `false` if none existed
     */
    fun removeProfile(contactId: Long): Boolean {
        return profiles.remove(contactId) != null
    }

    /** Number of stored profiles */
    val size: Int get() = profiles.size
}
