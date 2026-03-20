package dev.gwaboard.companion.provider

import dev.gwaboard.shared.models.ContactProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ProfileStoreTest {

    private lateinit var store: ProfileStore

    private val sampleProfile = ContactProfile(
        dominantLanguage = "fr",
        tone = "casual",
        avgResponseLength = 42,
        topNgrams = listOf("salut", "merci", "ok"),
        styleEmbedding = listOf(0.1f, 0.2f, 0.3f),
    )

    @Before
    fun setUp() {
        store = ProfileStore()
    }

    @Test
    fun `getProfile returns null for unknown contact`() {
        assertNull(store.getProfile(999L))
    }

    @Test
    fun `putProfile and getProfile round-trip produces valid JSON`() {
        store.putProfile(1L, sampleProfile)

        val json = store.getProfile(1L)
        assertNotNull(json)

        // Deserialize back and verify fields
        val decoded = Json.decodeFromString<ContactProfile>(json!!)
        assertEquals("fr", decoded.dominantLanguage)
        assertEquals("casual", decoded.tone)
        assertEquals(42, decoded.avgResponseLength)
        assertEquals(listOf("salut", "merci", "ok"), decoded.topNgrams)
        assertEquals(3, decoded.styleEmbedding.size)
    }

    @Test
    fun `getAllProfiles returns all stored profiles`() {
        store.putProfile(1L, sampleProfile)
        store.putProfile(2L, sampleProfile.copy(dominantLanguage = "en"))

        val all = store.getAllProfiles()
        assertEquals(2, all.size)
        assertTrue(all.containsKey(1L))
        assertTrue(all.containsKey(2L))
    }

    @Test
    fun `getAllProfiles returns empty map when no profiles exist`() {
        assertTrue(store.getAllProfiles().isEmpty())
    }

    @Test
    fun `putProfile overwrites existing profile`() {
        store.putProfile(1L, sampleProfile)
        store.putProfile(1L, sampleProfile.copy(tone = "formal"))

        val json = store.getProfile(1L)!!
        val decoded = Json.decodeFromString<ContactProfile>(json)
        assertEquals("formal", decoded.tone)
        assertEquals(1, store.size)
    }

    @Test
    fun `removeProfile returns true when profile exists`() {
        store.putProfile(1L, sampleProfile)
        assertTrue(store.removeProfile(1L))
        assertNull(store.getProfile(1L))
        assertEquals(0, store.size)
    }

    @Test
    fun `removeProfile returns false when profile does not exist`() {
        assertFalse(store.removeProfile(999L))
    }

    @Test
    fun `size reflects number of stored profiles`() {
        assertEquals(0, store.size)
        store.putProfile(1L, sampleProfile)
        assertEquals(1, store.size)
        store.putProfile(2L, sampleProfile)
        assertEquals(2, store.size)
    }
}
