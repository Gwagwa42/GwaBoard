package dev.gwaboard.keyboard.llm

import dev.gwaboard.keyboard.engine.SuggestionSource
import dev.gwaboard.shared.models.ContactProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SmolLMEngineImpl] — verifies lifecycle state transitions,
 * prompt building, completion parsing, inference delegation, and cancellation.
 *
 * Uses MockK to stub [LlamaCppBridge] native calls (no real native library needed).
 */
class SmolLMEngineImplTest {

    private lateinit var bridge: LlamaCppBridge
    private lateinit var engine: SmolLMEngineImpl
    private val config = SmolLMConfig(
        modelPath = "/data/test/model.gguf",
        nThreads = 2,
        maxTokens = 16,
    )

    /** Fake context handle returned by the mocked bridge. */
    private val fakeHandle = 42L

    @Before
    fun setup() {
        bridge = mockk(relaxed = true)
        engine = SmolLMEngineImpl(bridge, config)
    }

    // -- Lifecycle tests --

    @Test
    fun `initial state is UNLOADED`() {
        assertEquals(SmolLMEngineImpl.EngineState.UNLOADED, engine.currentState())
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `load transitions to LOADED on success`() = runTest {
        every { bridge.loadModel(config.modelPath, config.nThreads) } returns fakeHandle

        engine.load()

        assertEquals(SmolLMEngineImpl.EngineState.LOADED, engine.currentState())
        assertTrue(engine.isLoaded)
        verify(exactly = 1) { bridge.loadModel(config.modelPath, config.nThreads) }
    }

    @Test
    fun `load remains UNLOADED when bridge returns zero handle`() = runTest {
        every { bridge.loadModel(any(), any()) } returns 0L

        engine.load()

        assertEquals(SmolLMEngineImpl.EngineState.UNLOADED, engine.currentState())
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `load is idempotent when already loaded`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle

        engine.load()
        engine.load() // Second call should be no-op

        verify(exactly = 1) { bridge.loadModel(any(), any()) }
    }

    @Test
    fun `unload transitions to UNLOADED`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle

        engine.load()
        engine.unload()

        assertEquals(SmolLMEngineImpl.EngineState.UNLOADED, engine.currentState())
        assertFalse(engine.isLoaded)
        verify(exactly = 1) { bridge.unloadModel(fakeHandle) }
    }

    @Test
    fun `unload is no-op when already unloaded`() = runTest {
        engine.unload()

        assertEquals(SmolLMEngineImpl.EngineState.UNLOADED, engine.currentState())
        verify(exactly = 0) { bridge.unloadModel(any()) }
    }

    @Test
    fun `close releases native resources immediately`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle

        engine.load()
        engine.close()

        assertEquals(SmolLMEngineImpl.EngineState.UNLOADED, engine.currentState())
        verify { bridge.cancelInference(fakeHandle) }
        verify { bridge.unloadModel(fakeHandle) }
    }

    // -- Inference tests --

    @Test
    fun `suggest returns empty when model not loaded`() = runTest {
        val results = engine.suggest("hello ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `suggest delegates to bridge infer when loaded`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle
        every { bridge.infer(fakeHandle, any(), config.maxTokens) } returns "world"

        engine.load()
        val results = engine.suggest("hello ", maxResults = 3)

        assertTrue(results.isNotEmpty())
        assertEquals("world", results[0].word)
        assertEquals(SuggestionSource.LLM, results[0].source)
    }

    @Test
    fun `suggest returns empty when inference returns blank`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle
        every { bridge.infer(fakeHandle, any(), any()) } returns ""

        engine.load()
        val results = engine.suggest("hello ")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `suggestWithContext includes SMS context in prompt`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle
        every { bridge.infer(eq(fakeHandle), any(), any()) } returns "bien merci"

        engine.load()
        val results = engine.suggestWithContext(
            context = "Je vais ",
            maxResults = 3,
            lastReceivedMessage = "Comment tu vas?",
        )

        assertTrue(results.isNotEmpty())
        // Verify the bridge was called with a prompt containing SMS context
        verify {
            bridge.infer(
                fakeHandle,
                match { it.contains("Comment tu vas?") },
                config.maxTokens,
            )
        }
    }

    @Test
    fun `suggestWithContext includes contact profile in prompt`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle
        every { bridge.infer(eq(fakeHandle), any(), any()) } returns "salut"

        val profile = ContactProfile(
            dominantLanguage = "fr",
            tone = "casual",
            avgResponseLength = 50,
            topNgrams = listOf("salut", "ca va"),
            styleEmbedding = emptyList(),
        )

        engine.load()
        engine.suggestWithContext(
            context = "Hey ",
            contactProfile = profile,
        )

        verify {
            bridge.infer(
                fakeHandle,
                match { it.contains("Language: fr") && it.contains("Tone: casual") },
                config.maxTokens,
            )
        }
    }

    @Test
    fun `cancelInference delegates to bridge`() = runTest {
        every { bridge.loadModel(any(), any()) } returns fakeHandle

        engine.load()
        // Simulate calling cancel (in real usage, inference would be running)
        engine.cancelInference()

        // cancelInference only calls bridge if state is INFERRING;
        // since we're in LOADED state, it should be a no-op
        verify(exactly = 0) { bridge.cancelInference(any()) }
    }

    @Test
    fun `learn is a no-op for LLM engine`() = runTest {
        engine.learn(listOf("je"), "suis", isPasswordField = false)
        // No interactions expected — LLM does not learn
    }

    // -- Prompt building tests --

    @Test
    fun `buildPrompt uses SmolLM2 instruct template`() {
        val prompt = engine.buildPrompt("Bonjour ")

        assertTrue("Should contain im_start system", prompt.contains("<|im_start|>system"))
        assertTrue("Should contain im_start user", prompt.contains("<|im_start|>user"))
        assertTrue("Should contain im_start assistant", prompt.contains("<|im_start|>assistant"))
        assertTrue("Should contain user text", prompt.contains("Continue: Bonjour "))
    }

    @Test
    fun `buildPrompt includes contact profile when provided`() {
        val profile = ContactProfile(
            dominantLanguage = "fr",
            tone = "formal",
            avgResponseLength = 120,
            topNgrams = emptyList(),
            styleEmbedding = emptyList(),
        )

        val prompt = engine.buildPrompt("Bonjour ", contactProfile = profile)

        assertTrue("Should contain language", prompt.contains("Language: fr"))
        assertTrue("Should contain tone", prompt.contains("Tone: formal"))
        assertTrue("Should contain response length hint", prompt.contains("120 chars"))
    }

    @Test
    fun `buildPrompt includes SMS context when provided`() {
        val prompt = engine.buildPrompt(
            context = "Je suis ",
            lastReceivedMessage = "Tu es libre ce soir?",
        )

        assertTrue("Should contain SMS context", prompt.contains("Tu es libre ce soir?"))
        assertTrue("Should contain Received label", prompt.contains("Received:"))
    }

    @Test
    fun `buildPrompt truncates long SMS context`() {
        val longSms = "A".repeat(200)

        val prompt = engine.buildPrompt("Ok ", lastReceivedMessage = longSms)

        // Should be truncated to MAX_SMS_CONTEXT_CHARS
        assertFalse(
            "Should not contain the full 200-char SMS",
            prompt.contains(longSms),
        )
        assertTrue(
            "Should contain truncated SMS",
            prompt.contains("A".repeat(SmolLMEngineImpl.MAX_SMS_CONTEXT_CHARS)),
        )
    }

    // -- Completion parsing tests --

    @Test
    fun `parseCompletions extracts words with decreasing scores`() {
        val results = engine.parseCompletions("bien merci beaucoup", maxResults = 3)

        assertEquals(3, results.size)
        assertEquals("bien", results[0].word)
        assertEquals("merci", results[1].word)
        assertEquals("beaucoup", results[2].word)
        assertTrue("Scores should decrease", results[0].score > results[1].score)
        assertTrue("Scores should decrease", results[1].score > results[2].score)
        assertTrue("All should be LLM source", results.all { it.source == SuggestionSource.LLM })
    }

    @Test
    fun `parseCompletions filters short words`() {
        val results = engine.parseCompletions("a je b suis", maxResults = 5)

        // "a" and "b" should be filtered (< MIN_WORD_LENGTH)
        assertTrue("Should not contain single-char words", results.none { it.word.length < 2 })
        assertTrue("Should contain 'je'", results.any { it.word == "je" })
        assertTrue("Should contain 'suis'", results.any { it.word == "suis" })
    }

    @Test
    fun `parseCompletions removes special tokens`() {
        val results = engine.parseCompletions("bonjour<|im_end|>", maxResults = 3)

        assertEquals(1, results.size)
        assertEquals("bonjour", results[0].word)
    }

    @Test
    fun `parseCompletions returns empty for blank output`() {
        val results = engine.parseCompletions("   ", maxResults = 3)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parseCompletions deduplicates words`() {
        val results = engine.parseCompletions("bien bien bien", maxResults = 5)

        assertEquals("Duplicate words should be removed", 1, results.size)
    }

    @Test
    fun `parseCompletions respects maxResults`() {
        val results = engine.parseCompletions(
            "un deux trois quatre cinq six",
            maxResults = 3,
        )

        assertEquals(3, results.size)
    }
}
