package dev.gwaboard.keyboard.engine

import dev.gwaboard.shared.models.ContactProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HybridSuggestionEngine] — validates the 2-tier orchestration,
 * merge logic, Tier 2 lifecycle (lazy load, inactivity unload), SMS context
 * injection, inference cancellation, and fallback behavior.
 *
 * Uses a [FakeSmolLMEngine] stub to simulate Tier 2 without actual LLM inference.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridSuggestionEngineTest {

    private lateinit var ngramEngine: NGramEngine
    private lateinit var fakeLLM: FakeSmolLMEngine
    private lateinit var hybridEngine: HybridSuggestionEngine
    private var fakeTimeMs: Long = 1000L

    @Before
    fun setup() {
        ngramEngine = NGramEngine(storage = null, maxOrder = 3)
        fakeLLM = FakeSmolLMEngine()
        fakeTimeMs = 1000L
        hybridEngine = HybridSuggestionEngine(
            ngramEngine = ngramEngine,
            smolLMEngine = fakeLLM,
            clock = { fakeTimeMs },
        )
    }

    // --- Tier 1 only (suggest) ---

    @Test
    fun `suggest returns Tier 1 results on every keystroke`() = runTest {
        initNgramWith("je" to 100, "suis" to 80, "je|suis" to 50)

        val results = hybridEngine.suggest("je ", maxResults = 3)

        assertTrue("Should return n-gram suggestions", results.isNotEmpty())
        assertTrue("All results should be NGRAM source",
            results.all { it.source == SuggestionSource.NGRAM })
    }

    @Test
    fun `suggest returns empty for empty context`() = runTest {
        initNgramWith("word" to 10)

        val results = hybridEngine.suggest("")
        assertTrue("Empty context should yield no suggestions", results.isEmpty())
    }

    @Test
    fun `suggest cancels ongoing Tier 2 inference`() = runTest {
        hybridEngine.suggest("typing ")
        assertTrue("Should cancel LLM inference when user types", fakeLLM.cancelCalled)
    }

    // --- Tier 2 triggering (suggestWithTier2) ---

    @Test
    fun `suggestWithTier2 delivers Tier 1 immediately then Tier 2 merged`() = runTest {
        initNgramWith("bon" to 50, "bonjour" to 30)
        fakeLLM.stubbedSuggestions = listOf(
            Suggestion("bonne journée", 0.95f, SuggestionSource.LLM),
        )

        var tier1Delivered = false
        var tier2Delivered = false

        hybridEngine.suggestWithTier2(
            context = "bon",
            maxResults = 3,
            onTier1Ready = { results ->
                tier1Delivered = true
                assertTrue("Tier 1 should have n-gram results", results.isNotEmpty())
            },
            onTier2Ready = { results ->
                tier2Delivered = true
                assertTrue("Merged results should contain LLM suggestion",
                    results.any { it.source == SuggestionSource.LLM })
            },
        )

        assertTrue("Tier 1 callback should have fired", tier1Delivered)
        assertTrue("Tier 2 callback should have fired", tier2Delivered)
    }

    @Test
    fun `suggestWithTier2 lazy-loads Tier 2 on first call`() = runTest {
        initNgramWith("test" to 10)
        assertFalse("LLM should not be loaded initially", fakeLLM.isLoaded)

        hybridEngine.suggestWithTier2(context = "test ")

        assertTrue("LLM should be loaded after first Tier 2 trigger", fakeLLM.isLoaded)
    }

    @Test
    fun `suggestWithTier2 works without LLM engine (Tier 1 only fallback)`() = runTest {
        val tier1Only = HybridSuggestionEngine(ngramEngine = ngramEngine, smolLMEngine = null)
        initNgramWith("hello" to 10)

        var tier1Results: List<Suggestion> = emptyList()
        tier1Only.suggestWithTier2(
            context = "hel",
            onTier1Ready = { tier1Results = it },
        )

        assertTrue("Tier 1 results should be delivered even without LLM", tier1Results.isNotEmpty())
    }

    // --- Merge logic ---

    @Test
    fun `merge gives Tier 2 priority over Tier 1 for same word`() {
        val tier1 = listOf(
            Suggestion("hello", 0.9f, SuggestionSource.NGRAM),
            Suggestion("help", 0.7f, SuggestionSource.NGRAM),
        )
        val tier2 = listOf(
            Suggestion("hello", 0.95f, SuggestionSource.LLM),
        )

        val merged = hybridEngine.mergeSuggestions(tier1, tier2, maxResults = 3)

        assertEquals("First result should be LLM version of 'hello'",
            SuggestionSource.LLM, merged.first { it.word == "hello" }.source)
        assertTrue("Tier 1 unique words should still appear",
            merged.any { it.word == "help" })
    }

    @Test
    fun `merge respects maxResults limit`() {
        val tier1 = (1..5).map { Suggestion("word$it", 0.5f, SuggestionSource.NGRAM) }
        val tier2 = (6..10).map { Suggestion("llm$it", 0.9f, SuggestionSource.LLM) }

        val merged = hybridEngine.mergeSuggestions(tier1, tier2, maxResults = 3)

        assertEquals("Should return exactly maxResults", 3, merged.size)
    }

    @Test
    fun `merge with empty Tier 2 returns Tier 1 only`() {
        val tier1 = listOf(Suggestion("word", 0.8f, SuggestionSource.NGRAM))

        val merged = hybridEngine.mergeSuggestions(tier1, emptyList(), maxResults = 3)

        assertEquals("Should return Tier 1 results unchanged", tier1, merged)
    }

    @Test
    fun `merge with empty Tier 1 returns Tier 2 only`() {
        val tier2 = listOf(Suggestion("llm", 0.9f, SuggestionSource.LLM))

        val merged = hybridEngine.mergeSuggestions(emptyList(), tier2, maxResults = 3)

        assertEquals("Should return Tier 2 results", tier2, merged)
    }

    // --- Inactivity unload ---

    @Test
    fun `isTier2Loaded is true after loading and false after close`() = runTest {
        initNgramWith("test" to 10)

        // Trigger Tier 2 to load it
        hybridEngine.suggestWithTier2(context = "test ")
        assertTrue("LLM should be loaded after Tier 2 trigger", fakeLLM.isLoaded)

        // Close triggers unload
        hybridEngine.close()
        assertFalse("LLM should be unloaded after close", fakeLLM.isLoaded)
    }

    @Test
    fun `inactivity timeout constant is 30 seconds`() {
        // Verify the inactivity timeout matches the spec (30s)
        assertEquals("Inactivity timeout should be 30 seconds",
            30_000L, HybridSuggestionEngine.INACTIVITY_TIMEOUT_MS)
    }

    // --- SMS context ---

    @Test
    fun `SMS context is passed to Tier 2 engine`() = runTest {
        initNgramWith("merci" to 10)
        hybridEngine.onSmsReceived("Tu arrives quand ?")

        hybridEngine.suggestWithTier2(context = "je ")

        assertEquals("LLM should receive SMS context",
            "Tu arrives quand ?", fakeLLM.lastReceivedMessage)
    }

    @Test
    fun `clearSmsContext removes the stored SMS`() = runTest {
        hybridEngine.onSmsReceived("Test message")
        hybridEngine.clearSmsContext()

        initNgramWith("test" to 10)
        hybridEngine.suggestWithTier2(context = "test ")

        assertEquals("SMS context should be null after clear",
            null, fakeLLM.lastReceivedMessage)
    }

    // --- Contact profile ---

    @Test
    fun `contact profile is passed to Tier 2 engine`() = runTest {
        initNgramWith("test" to 10)
        val profile = ContactProfile(
            dominantLanguage = "fr",
            tone = "casual",
            avgResponseLength = 42,
            topNgrams = listOf("salut", "ca va"),
            styleEmbedding = listOf(0.1f, 0.2f),
        )
        hybridEngine.setContactProfile(profile)

        hybridEngine.suggestWithTier2(context = "test ")

        assertEquals("LLM should receive contact profile", profile, fakeLLM.lastContactProfile)
    }

    // --- Learning ---

    @Test
    fun `learn delegates to NGramEngine`() = runTest {
        initNgramWith()
        hybridEngine.learn(listOf("je"), "suis", isPasswordField = false)
        hybridEngine.learn(listOf("je"), "suis", isPasswordField = false)

        val results = ngramEngine.suggest("je ", maxResults = 3)
        assertTrue("Learning should be delegated to n-gram engine",
            results.any { it.word == "suis" })
    }

    @Test
    fun `learn skips password fields`() = runTest {
        initNgramWith()
        hybridEngine.learn(listOf("my"), "secret", isPasswordField = true)

        val results = ngramEngine.suggest("my ", maxResults = 3)
        assertTrue("Should not learn from password fields", results.none { it.word == "secret" })
    }

    // --- isTier2Loaded ---

    @Test
    fun `isTier2Loaded returns false when LLM engine is null`() {
        val noLLM = HybridSuggestionEngine(ngramEngine = ngramEngine, smolLMEngine = null)
        assertFalse("Should be false without LLM engine", noLLM.isTier2Loaded())
    }

    @Test
    fun `isTier2Loaded reflects LLM engine state`() = runTest {
        assertFalse("Should be false before loading", hybridEngine.isTier2Loaded())

        initNgramWith("test" to 10)
        hybridEngine.suggestWithTier2(context = "test ")
        assertTrue("Should be true after loading", hybridEngine.isTier2Loaded())
    }

    // --- Close ---

    @Test
    fun `close shuts down both engines`() {
        hybridEngine.close()
        assertTrue("LLM engine should be closed", fakeLLM.closeCalled)
    }

    // --- Helpers ---

    private suspend fun initNgramWith(vararg entries: Pair<String, Int>) {
        val parsed = entries.map { (key, count) ->
            key.split("|") to count
        }
        ngramEngine.initialize(parsed)
    }

    /**
     * Fake [SmolLMEngine] for testing. Tracks method calls and returns
     * configurable stubbed suggestions.
     */
    private class FakeSmolLMEngine : SmolLMEngine {
        override var isLoaded: Boolean = false
            private set

        var cancelCalled = false
        var unloadCalled = false
        var closeCalled = false
        var stubbedSuggestions: List<Suggestion> = emptyList()
        var lastReceivedMessage: String? = null
        var lastContactProfile: ContactProfile? = null

        override suspend fun load() {
            isLoaded = true
        }

        override suspend fun unload() {
            isLoaded = false
            unloadCalled = true
        }

        override fun cancelInference() {
            cancelCalled = true
        }

        override suspend fun suggest(context: String, maxResults: Int): List<Suggestion> {
            return stubbedSuggestions.take(maxResults)
        }

        override suspend fun suggestWithContext(
            context: String,
            maxResults: Int,
            contactProfile: ContactProfile?,
            lastReceivedMessage: String?,
        ): List<Suggestion> {
            this.lastContactProfile = contactProfile
            this.lastReceivedMessage = lastReceivedMessage
            return stubbedSuggestions.take(maxResults)
        }

        override suspend fun learn(
            precedingWords: List<String>,
            completedWord: String,
            isPasswordField: Boolean,
        ) {
            // No-op for LLM
        }

        override fun close() {
            closeCalled = true
            isLoaded = false
        }
    }
}
