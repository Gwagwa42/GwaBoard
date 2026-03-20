package dev.gwaboard.keyboard.privacy

import android.content.Context
import android.content.Intent
import dev.gwaboard.shared.models.IpcContract
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataDeletionReceiverTest {

    private val context = mockk<Context>(relaxed = true)
    private val receiver = DataDeletionReceiver()

    @Before
    fun setUp() {
        DataDeletionReceiver.onAllDataDeleted = null
        DataDeletionReceiver.onContactDataDeleted = null
    }

    @After
    fun tearDown() {
        DataDeletionReceiver.onAllDataDeleted = null
        DataDeletionReceiver.onContactDataDeleted = null
    }

    @Test
    fun `DATA_DELETED_ALL broadcast invokes onAllDataDeleted callback`() {
        var callbackInvoked = false
        DataDeletionReceiver.onAllDataDeleted = { callbackInvoked = true }

        val intent = mockk<Intent> {
            every { action } returns IpcContract.Actions.DATA_DELETED_ALL
        }
        receiver.onReceive(context, intent)

        assertTrue("onAllDataDeleted callback should have been invoked", callbackInvoked)
    }

    @Test
    fun `DATA_DELETED_CONTACT broadcast invokes onContactDataDeleted with address`() {
        var receivedAddress: String? = null
        DataDeletionReceiver.onContactDataDeleted = { address -> receivedAddress = address }

        val intent = mockk<Intent> {
            every { action } returns IpcContract.Actions.DATA_DELETED_CONTACT
            every { getStringExtra(IpcContract.Extras.CONTACT_ADDRESS) } returns "+33612345678"
        }
        receiver.onReceive(context, intent)

        assertEquals("+33612345678", receivedAddress)
    }

    @Test
    fun `DATA_DELETED_CONTACT broadcast without address does not crash`() {
        var callbackInvoked = false
        DataDeletionReceiver.onContactDataDeleted = { callbackInvoked = true }

        val intent = mockk<Intent> {
            every { action } returns IpcContract.Actions.DATA_DELETED_CONTACT
            every { getStringExtra(IpcContract.Extras.CONTACT_ADDRESS) } returns null
        }
        receiver.onReceive(context, intent)

        assertFalse("Callback should not be invoked without address extra", callbackInvoked)
    }

    @Test
    fun `broadcast with no registered callbacks does not crash`() {
        val intent = mockk<Intent> {
            every { action } returns IpcContract.Actions.DATA_DELETED_ALL
        }
        receiver.onReceive(context, intent)
    }
}
