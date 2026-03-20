package dev.gwaboard.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [NGramModel] — verifies n-gram recording, lookup,
 * backoff scoring, prefix filtering, and pruning behavior.
 */
class NGramModelTest {

    private lateinit var model: NGramModel

    @Before
    fun setup() {
        model = NGramModel(maxOrder = 3)
    }

    @Test
    fun `record and lookup unigram`() {
        model.record(listOf("bonjour"))
        model.record(listOf("bonjour"))
        model.record(listOf("bonsoir"))

        val results = model.lookup(precedingWords = emptyList(), prefix = "bon", maxResults = 3)

        assertTrue("Should return at least 2 results", results.size >= 2)
        assertEquals("Most frequent word first", "bonjour", results[0].first)
    }

    @Test
    fun `record and lookup bigram`() {
        // Train: "je suis" appears 3 times, "je vais" appears 1 time
        repeat(3) { model.record(listOf("je", "suis")) }
        model.record(listOf("je", "vais"))

        val results = model.lookup(precedingWords = listOf("je"), maxResults = 3)

        assertTrue("Should return at least 2 results", results.size >= 2)
        assertEquals("'suis' should rank highest after 'je'", "suis", results[0].first)
    }

    @Test
    fun `record and lookup trigram`() {
        repeat(5) { model.record(listOf("je", "suis", "content")) }
        repeat(2) { model.record(listOf("je", "suis", "fatigué")) }

        val results = model.lookup(precedingWords = listOf("je", "suis"), maxResults = 3)

        assertTrue("Should return results", results.isNotEmpty())
        assertEquals("'content' should rank highest", "content", results[0].first)
    }

    @Test
    fun `prefix filtering works`() {
        model.record(listOf("chat"))
        model.record(listOf("chien"))
        model.record(listOf("maison"))

        val results = model.lookup(precedingWords = emptyList(), prefix = "ch", maxResults = 10)

        assertTrue("Should only return words starting with 'ch'", results.all { it.first.startsWith("ch") })
        assertEquals("Should return 2 matching words", 2, results.size)
    }

    @Test
    fun `empty context returns unigram suggestions`() {
        model.record(listOf("le"))
        model.record(listOf("la"))
        model.record(listOf("les"))

        val results = model.lookup(precedingWords = emptyList(), maxResults = 5)

        assertTrue("Should return unigram results", results.isNotEmpty())
    }

    @Test
    fun `loadEntries populates model correctly`() {
        val entries = listOf(
            listOf("hello") to 10,
            listOf("hello", "world") to 5,
            listOf("good", "morning") to 3,
        )
        model.loadEntries(entries)

        assertEquals("Should have 3 entries (1 unigram + 2 bigrams)", 3, model.size())

        val results = model.lookup(precedingWords = listOf("hello"), maxResults = 1)
        assertTrue("Should find 'world' after 'hello'", results.isNotEmpty())
        assertEquals("world", results[0].first)
    }

    @Test
    fun `exportEntries roundtrips correctly`() {
        model.record(listOf("a", "b", "c"))
        val exported = model.exportEntries()

        val newModel = NGramModel()
        val reloadEntries = exported.map { (_, key, count) ->
            key.split(NGramModel.SEPARATOR) to count
        }
        newModel.loadEntries(reloadEntries)

        assertEquals("Exported and reloaded model should have same size", model.size(), newModel.size())
    }

    @Test
    fun `case insensitive lookups`() {
        model.record(listOf("Bonjour"))
        model.record(listOf("BONJOUR"))

        val results = model.lookup(precedingWords = emptyList(), prefix = "bon", maxResults = 3)

        assertEquals("Case-insensitive: should merge into one entry", 1, results.size)
        assertEquals("bonjour", results[0].first)
    }

    @Test
    fun `clear removes all entries`() {
        model.record(listOf("test", "data"))
        assertTrue("Model should have entries", model.size() > 0)

        model.clear()
        assertEquals("Model should be empty after clear", 0, model.size())
    }

    @Test
    fun `scores are normalized to 0-1 range`() {
        model.record(listOf("alpha"))
        model.record(listOf("beta"))

        val results = model.lookup(precedingWords = emptyList(), maxResults = 5)

        for ((_, score) in results) {
            assertTrue("Score should be <= 1.0, got $score", score <= 1.0f)
            assertTrue("Score should be > 0.0, got $score", score > 0.0f)
        }
    }

    @Test
    fun `backoff scoring prefers higher-order matches`() {
        // Train bigram "je|suis" heavily and unigram "mange" lightly
        repeat(10) { model.record(listOf("je", "suis")) }
        repeat(10) { model.record(listOf("mange")) }

        // With context "je", bigram match "suis" should rank above unigram "mange"
        val results = model.lookup(precedingWords = listOf("je"), maxResults = 5)

        assertTrue("Should have results", results.isNotEmpty())
        val suisIdx = results.indexOfFirst { it.first == "suis" }
        val mangeIdx = results.indexOfFirst { it.first == "mange" }
        assertTrue("'suis' (bigram) should rank above 'mange' (unigram)", suisIdx < mangeIdx)
    }
}
