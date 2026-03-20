package dev.gwaboard.keyboard.ipc

import app.cash.turbine.test
import dev.gwaboard.shared.ipc.SmsProviderClient
import dev.gwaboard.shared.models.ContactInfo
import dev.gwaboard.shared.models.ContactProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsContextProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockClient: SmsProviderClient
    private lateinit var provider: SmsContextProvider

    private val sampleProfile = ContactProfile(
        dominantLanguage = "fr",
        tone = "casual",
        avgResponseLength = 42,
        topNgrams = listOf("salut", "ok", "merci"),
        styleEmbedding = listOf(0.1f, 0.2f, 0.3f),
    )

    @Before
    fun setup() {
        mockClient = mockk(relaxed = true)
        every { mockClient.isCompanionAvailable } returns true
        provider = SmsContextProvider(
            smsProviderClient = mockClient,
            dispatcher = testDispatcher,
            scope = testScope,
        )
    }

    @Test
    fun `initial profile is null`() = testScope.runTest {
        assertNull(provider.currentProfile)
    }

    @Test
    fun `requestProfile emits profile via flow`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } returns sampleProfile

        provider.contactProfile.test {
            // Initial null emission
            assertNull(awaitItem())

            provider.requestProfile(1L)
            advanceUntilIdle()

            assertEquals(sampleProfile, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestProfile caches and skips redundant queries`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } returns sampleProfile

        provider.requestProfile(1L)
        advanceUntilIdle()

        // Second request for same contact should not trigger another query
        provider.requestProfile(1L)
        advanceUntilIdle()

        verify(exactly = 1) { mockClient.queryContactProfile(1L) }
    }

    @Test
    fun `requestProfile fetches new profile for different contact`() = testScope.runTest {
        val profile2 = sampleProfile.copy(dominantLanguage = "en", tone = "formal")
        every { mockClient.queryContactProfile(1L) } returns sampleProfile
        every { mockClient.queryContactProfile(2L) } returns profile2

        provider.requestProfile(1L)
        advanceUntilIdle()
        assertEquals(sampleProfile, provider.currentProfile)

        provider.requestProfile(2L)
        advanceUntilIdle()
        assertEquals(profile2, provider.currentProfile)

        verify(exactly = 1) { mockClient.queryContactProfile(1L) }
        verify(exactly = 1) { mockClient.queryContactProfile(2L) }
    }

    @Test
    fun `graceful degradation when companion unavailable`() = testScope.runTest {
        every { mockClient.isCompanionAvailable } returns false
        every { mockClient.queryContactProfile(any()) } returns null

        provider.requestProfile(1L)
        advanceUntilIdle()

        assertNull(provider.currentProfile)
    }

    @Test
    fun `graceful degradation when query throws exception`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } throws SecurityException("No permission")

        provider.requestProfile(1L)
        advanceUntilIdle()

        // Should not crash, profile should be null
        assertNull(provider.currentProfile)
    }

    @Test
    fun `invalidateCache clears cached profile`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } returns sampleProfile

        provider.requestProfile(1L)
        advanceUntilIdle()
        assertEquals(sampleProfile, provider.currentProfile)

        provider.invalidateCache()
        assertNull(provider.currentProfile)
    }

    @Test
    fun `invalidateCache forces re-fetch on next requestProfile`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } returns sampleProfile

        provider.requestProfile(1L)
        advanceUntilIdle()

        provider.invalidateCache()

        provider.requestProfile(1L)
        advanceUntilIdle()

        // Should have queried twice since cache was invalidated
        verify(exactly = 2) { mockClient.queryContactProfile(1L) }
    }

    @Test
    fun `resolveContactId returns matching contact`() {
        val contacts = listOf(
            ContactInfo(contactId = 1L, displayName = "Alice", hashedAddress = "abc123"),
            ContactInfo(contactId = 2L, displayName = "Bob", hashedAddress = "def456"),
        )
        every { mockClient.queryContacts() } returns contacts

        val result = provider.resolveContactId("def456")
        assertEquals(2L, result)
    }

    @Test
    fun `resolveContactId returns null when no match`() {
        every { mockClient.queryContacts() } returns emptyList()

        val result = provider.resolveContactId("unknown")
        assertNull(result)
    }

    @Test
    fun `resolveContactId returns null when companion unavailable`() {
        every { mockClient.isCompanionAvailable } returns false

        val result = provider.resolveContactId("abc123")
        assertNull(result)
    }

    @Test
    fun `isCompanionAvailable delegates to client`() {
        every { mockClient.isCompanionAvailable } returns true
        assertTrue(provider.isCompanionAvailable)

        every { mockClient.isCompanionAvailable } returns false
        assertFalse(provider.isCompanionAvailable)
    }

    @Test
    fun `close clears state`() = testScope.runTest {
        every { mockClient.queryContactProfile(1L) } returns sampleProfile

        provider.requestProfile(1L)
        advanceUntilIdle()
        assertEquals(sampleProfile, provider.currentProfile)

        provider.close()
        assertNull(provider.currentProfile)
    }
}
