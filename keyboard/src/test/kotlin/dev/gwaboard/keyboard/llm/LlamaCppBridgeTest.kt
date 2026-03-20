package dev.gwaboard.keyboard.llm

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LlamaCppBridge] — verifies the Kotlin-side contract,
 * input validation, and library loading behavior.
 *
 * Native methods are not available in JVM unit tests, so these tests focus
 * on the Kotlin logic: parameter validation, null-handle guards, and the
 * ensureLoaded() mechanism. Full integration tests require an Android device.
 */
class LlamaCppBridgeTest {

    private lateinit var bridge: LlamaCppBridge

    @Before
    fun setup() {
        bridge = LlamaCppBridge()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -- ensureLoaded tests --

    @Test
    fun `ensureLoaded returns false when native library is unavailable`() {
        // In JVM unit tests, the native .so is not available
        // System.loadLibrary will throw UnsatisfiedLinkError
        // The method should catch it and return false
        mockkStatic(System::class)
        every { System.loadLibrary(any()) } throws UnsatisfiedLinkError("test: no native lib")

        val result = LlamaCppBridge.ensureLoaded()
        assertFalse("Should return false when native lib unavailable", result)
    }

    // -- loadModel validation tests --

    @Test(expected = IllegalArgumentException::class)
    fun `loadModel rejects blank model path`() {
        bridge.loadModel("", nThreads = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `loadModel rejects whitespace-only model path`() {
        bridge.loadModel("   ", nThreads = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `loadModel rejects zero thread count`() {
        bridge.loadModel("/data/model.gguf", nThreads = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `loadModel rejects negative thread count`() {
        bridge.loadModel("/data/model.gguf", nThreads = -1)
    }

    // -- unloadModel guard tests --

    @Test
    fun `unloadModel with zero handle is a no-op`() {
        // Should not throw — zero handle is silently ignored
        bridge.unloadModel(0L)
    }

    // -- infer validation tests --

    @Test(expected = IllegalArgumentException::class)
    fun `infer rejects zero context handle`() {
        bridge.infer(0L, "test prompt", maxTokens = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `infer rejects zero maxTokens`() {
        bridge.infer(1L, "test prompt", maxTokens = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `infer rejects negative maxTokens`() {
        bridge.infer(1L, "test prompt", maxTokens = -5)
    }

    // -- cancelInference guard tests --

    @Test
    fun `cancelInference with zero handle is a no-op`() {
        // Should not throw — zero handle is silently ignored
        bridge.cancelInference(0L)
    }

    // -- Library name constant test --

    @Test
    fun `native library name matches CMakeLists target`() {
        // Verify the library name constant matches what CMakeLists.txt produces
        // The CMake target is "gwaboard_llm" which produces "libgwaboard_llm.so"
        mockkStatic(System::class)
        every { System.loadLibrary(any()) } throws UnsatisfiedLinkError("expected")

        LlamaCppBridge.ensureLoaded()

        verify { System.loadLibrary("gwaboard_llm") }
    }
}
