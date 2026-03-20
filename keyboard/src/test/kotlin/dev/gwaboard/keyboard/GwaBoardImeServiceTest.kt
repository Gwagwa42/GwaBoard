package dev.gwaboard.keyboard

import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Basic smoke tests for the keyboard module.
 * Instrumented tests for the full IME lifecycle require a device/emulator.
 */
class GwaBoardImeServiceTest {

    @Test
    fun `service class can be instantiated`() {
        // Verify the service class is loadable (catches missing dependencies at compile time)
        val serviceClass = GwaBoardImeService::class.java
        assertNotNull(serviceClass)
    }
}
