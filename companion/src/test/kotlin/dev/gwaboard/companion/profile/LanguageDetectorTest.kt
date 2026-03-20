package dev.gwaboard.companion.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageDetectorTest {

    @Test
    fun `detect returns French for French text`() {
        val frenchText = "Bonjour, comment allez-vous aujourd'hui? Je suis content de vous revoir."
        val result = LanguageDetector.detect(frenchText)
        assertEquals("fr", result)
    }

    @Test
    fun `detect returns English for English text`() {
        val englishText = "Hello, how are you doing today? I'm happy to see you again."
        val result = LanguageDetector.detect(englishText)
        assertEquals("en", result)
    }

    @Test
    fun `detect returns English for short ambiguous text`() {
        val shortText = "ok"
        val result = LanguageDetector.detect(shortText)
        assertEquals("en", result)
    }

    @Test
    fun `detect returns English for empty text`() {
        assertEquals("en", LanguageDetector.detect(""))
    }

    @Test
    fun `extractTrigrams returns correct counts`() {
        val trigrams = LanguageDetector.extractTrigrams("hello")
        assertEquals(3, trigrams.size) // "hel", "ell", "llo"
        assertEquals(1, trigrams["hel"])
        assertEquals(1, trigrams["ell"])
        assertEquals(1, trigrams["llo"])
    }

    @Test
    fun `extractTrigrams returns empty for text shorter than 3 chars`() {
        assertTrue(LanguageDetector.extractTrigrams("ab").isEmpty())
    }

    @Test
    fun `detect handles longer French paragraph`() {
        val text = """
            Les vacances d'été sont toujours les meilleures.
            Nous allons partir en France pour visiter les châteaux de la Loire.
            C'est une belle région avec beaucoup de choses à voir et à faire.
        """.trimIndent()
        assertEquals("fr", LanguageDetector.detect(text))
    }

    @Test
    fun `detect handles longer English paragraph`() {
        val text = """
            The summer holidays are always the best.
            We are going to visit the castles in the countryside.
            There are many things to see and do in this beautiful region.
        """.trimIndent()
        assertEquals("en", LanguageDetector.detect(text))
    }
}
