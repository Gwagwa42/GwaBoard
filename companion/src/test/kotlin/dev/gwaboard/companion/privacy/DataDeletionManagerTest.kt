package dev.gwaboard.companion.privacy

import android.content.Context
import android.content.Intent
import dev.gwaboard.companion.db.ProfileDatabase
import dev.gwaboard.shared.models.IpcContract
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataDeletionManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val profileDatabase = mockk<ProfileDatabase>(relaxed = true)
    private lateinit var manager: DataDeletionManager

    @Before
    fun setUp() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        manager = DataDeletionManager(context, profileDatabase)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deleteAllData purges database and sends broadcast`() = runTest {
        manager.deleteAllData()

        coVerify { profileDatabase.deleteAllProfiles() }
        verify { context.sendBroadcast(any()) }
    }

    @Test
    fun `deleteContactData purges specific profile and sends broadcast`() = runTest {
        coEvery { profileDatabase.deleteProfile("+33612345678") } returns true

        val result = manager.deleteContactData("+33612345678")

        assertTrue(result)
        coVerify { profileDatabase.deleteProfile("+33612345678") }
        verify { context.sendBroadcast(any()) }
    }

    @Test
    fun `deleteContactData returns false when no profile exists`() = runTest {
        coEvery { profileDatabase.deleteProfile("+33600000000") } returns false

        val result = manager.deleteContactData("+33600000000")

        assertFalse(result)
    }
}
