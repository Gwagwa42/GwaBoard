package dev.gwaboard.keyboard.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NGramEngine] — verifies the engine's suggest/learn lifecycle,
 * password field exclusion, and initialization behavior.
 *
 * These tests run without Android context (no NGramStorage) to keep them as
 * fast JVM unit tests. Storage integration is tested separately.
 */
class NGramEngineTest {

    private lateinit var engine: NGramEngine

    @Before
    fun setup() {
        // No storage — pure in-memory engine for unit tests
        engine = NGramEngine(storage = null, maxOrder = 3)
    }

    @Test
    fun `suggest returns empty before initialization`() = runTest {
        val results = engine.suggest("hello ")
        assertTrue("Should return empty before init", results.isEmpty())
    }

    @Test
    fun `suggest returns results after initialization with dictionary data`() = runTest {
        val dictionary = listOf(
            listOf("je") to 100,
            listOf("suis") to 80,
            listOf("je", "suis") to 50,
            listOf("je", "vais") to 20,
            listOf("je", "suis", "content") to 15,
        )
        engine.initialize(dictionary)

        val results = engine.suggest("je ", maxResults = 3)
        assertTrue("Should return suggestions", results.isNotEmpty())
        assertEquals("Best suggestion after 'je' should be 'suis'", "suis", results[0].word)
        assertEquals("Source should be NGRAM", SuggestionSource.NGRAM, results[0].source)
    }

    @Test
    fun `suggest handles partial word prefix`() = runTest {
        val dictionary = listOf(
            listOf("bonjour") to 50,
            listOf("bonsoir") to 30,
            listOf("merci") to 40,
        )
        engine.initialize(dictionary)

        // No trailing space = partial word completion mode
        val results = engine.suggest("bon", maxResults = 5)
        assertTrue("Should suggest words starting with 'bon'", results.isNotEmpty())
        assertTrue("All suggestions should start with 'bon'", results.all { it.word.startsWith("bon") })
    }

    @Test
    fun `learn adds n-grams that appear in subsequent suggestions`() = runTest {
        engine.initialize()

        // Learn a pattern
        engine.learn(listOf("je"), "suis", isPasswordField = false)
        engine.learn(listOf("je"), "suis", isPasswordField = false)
        engine.learn(listOf("je"), "suis", isPasswordField = false)

        val results = engine.suggest("je ", maxResults = 3)
        assertTrue("Should suggest learned word", results.any { it.word == "suis" })
    }

    @Test
    fun `learn is skipped for password fields`() = runTest {
        engine.initialize()

        // Attempt to learn from a password field
        engine.learn(listOf("my"), "password123", isPasswordField = true)

        val results = engine.suggest("my ", maxResults = 3)
        assertTrue("Should not have learned password field content", results.none { it.word == "password123" })
    }

    @Test
    fun `learn is skipped for blank words`() = runTest {
        engine.initialize()

        engine.learn(listOf("test"), "", isPasswordField = false)
        engine.learn(listOf("test"), "   ", isPasswordField = false)

        assertEquals("Model should be empty — blank words rejected", 0, engine.modelSize())
    }

    @Test
    fun `suggest with empty context returns empty`() = runTest {
        engine.initialize(listOf(listOf("word") to 10))

        val results = engine.suggest("")
        assertTrue("Empty context should return no suggestions", results.isEmpty())
    }

    @Test
    fun `suggest with only whitespace returns empty`() = runTest {
        engine.initialize(listOf(listOf("word") to 10))

        val results = engine.suggest("   ")
        assertTrue("Whitespace-only context should return no suggestions", results.isEmpty())
    }

    @Test
    fun `suggest respects maxResults parameter`() = runTest {
        val dictionary = (1..20).map { listOf("word$it") to (20 - it) }
        engine.initialize(dictionary)

        val results = engine.suggest("w", maxResults = 3)
        assertTrue("Should return at most 3 results", results.size <= 3)
    }

    @Test
    fun `initialization is idempotent`() = runTest {
        val dictionary = listOf(listOf("test") to 10)
        engine.initialize(dictionary)
        engine.initialize(dictionary) // Second call should be no-op

        // Should still work normally
        val results = engine.suggest("tes", maxResults = 3)
        assertTrue("Engine should work after double init", results.isNotEmpty())
    }

    @Test
    fun `trigram context produces relevant suggestions`() = runTest {
        val dictionary = listOf(
            listOf("i", "am", "happy") to 30,
            listOf("i", "am", "tired") to 20,
            listOf("i", "am", "hungry") to 10,
            listOf("i") to 100,
            listOf("am") to 80,
        )
        engine.initialize(dictionary)

        val results = engine.suggest("i am ", maxResults = 3)
        assertTrue("Should return trigram-matched suggestions", results.isNotEmpty())
        assertEquals("Best trigram match should be 'happy'", "happy", results[0].word)
    }
}
