package dev.gwaboard.companion.privacy

import android.content.Context
import android.content.Intent
import dev.gwaboard.companion.db.ProfileDatabase
import dev.gwaboard.shared.models.IpcContract
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.junit.Assert.assertEquals

/**
 * Tests for [DataDeletionManager] GDPR data deletion and broadcast behavior.
 */
class DataDeletionManagerTest {

    private val context = mock(Context::class.java)
    private val profileDatabase = mock(ProfileDatabase::class.java)
    private lateinit var manager: DataDeletionManager

    @Before
    fun setUp() {
        manager = DataDeletionManager(context, profileDatabase)
    }

    @Test
    fun `deleteAllData purges database and sends broadcast`() = runTest {
        manager.deleteAllData()

        verify(profileDatabase).deleteAllProfiles()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).sendBroadcast(intentCaptor.capture())

        val sentIntent = intentCaptor.value
        assertEquals(IpcContract.Actions.DATA_DELETED_ALL, sentIntent.action)
        assertEquals("dev.gwaboard.keyboard", sentIntent.`package`)
    }

    @Test
    fun `deleteContactData purges specific profile and sends broadcast`() = runTest {
        `when`(profileDatabase.deleteProfile("+33612345678")).thenReturn(true)

        val result = manager.deleteContactData("+33612345678")

        assertEquals(true, result)
        verify(profileDatabase).deleteProfile("+33612345678")

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).sendBroadcast(intentCaptor.capture())

        val sentIntent = intentCaptor.value
        assertEquals(IpcContract.Actions.DATA_DELETED_CONTACT, sentIntent.action)
        assertEquals("dev.gwaboard.keyboard", sentIntent.`package`)
        assertEquals(
            "+33612345678",
            sentIntent.getStringExtra(IpcContract.Extras.CONTACT_ADDRESS),
        )
    }

    @Test
    fun `deleteContactData returns false when no profile exists`() = runTest {
        `when`(profileDatabase.deleteProfile("+33600000000")).thenReturn(false)

        val result = manager.deleteContactData("+33600000000")

        assertEquals(false, result)
    }
}
