package dev.gwaboard.companion.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToneAnalyzerTest {

    @Test
    fun `analyze returns casual for slang and abbreviations`() {
        val messages = listOf(
            "lol yeah that's funny",
            "omg no way haha",
            "gonna grab lunch brb",
            "thx for the info dude",
        )
        assertEquals("casual", ToneAnalyzer.analyze(messages))
    }

    @Test
    fun `analyze returns formal for professional language`() {
        val messages = listOf(
            "Dear Sir, please find attached the documents you requested.",
            "Thank you for your prompt response. I would appreciate your feedback at your convenience.",
            "Best regards, looking forward to our meeting next week.",
        )
        assertEquals("formal", ToneAnalyzer.analyze(messages))
    }

    @Test
    fun `analyze returns formal for French formal language`() {
        val messages = listOf(
            "Bonjour Monsieur, je vous prie de bien vouloir trouver ci-joint le document.",
            "Cordialement, en vous remerciant pour votre attention.",
        )
        assertEquals("formal", ToneAnalyzer.analyze(messages))
    }

    @Test
    fun `analyze returns casual for French informal language`() {
        val messages = listOf(
            "slt cc",
            "tkt jsp",
            "mdr ptdr trop drôle",
            "ok stp",
        )
        assertEquals("casual", ToneAnalyzer.analyze(messages))
    }

    @Test
    fun `analyze returns casual for empty messages`() {
        assertEquals("casual", ToneAnalyzer.analyze(emptyList()))
    }

    @Test
    fun `analyze returns friendly for mixed tone`() {
        val messages = listOf(
            "Hey! How are you doing?",
            "That sounds great, thanks!",
            "Sure, no problem at all.",
        )
        val result = ToneAnalyzer.analyze(messages)
        // Mixed signals should produce friendly or casual
        assert(result == "friendly" || result == "casual") {
            "Expected friendly or casual but got $result"
        }
    }
}
