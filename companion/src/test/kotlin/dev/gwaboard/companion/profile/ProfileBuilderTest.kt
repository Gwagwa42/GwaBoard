package dev.gwaboard.companion.profile

import dev.gwaboard.shared.models.SmsMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileBuilderTest {

    private val profileBuilder = ProfileBuilder()

    private fun createMessage(
        id: Long,
        body: String,
        type: Int = 1,
        address: String = "+33612345678",
    ) = SmsMessage(
        id = id,
        threadId = 1L,
        address = address,
        body = body,
        date = System.currentTimeMillis() - (id * 60_000),
        type = type,
    )

    @Test
    fun `buildProfile returns null when too few messages`() = runTest {
        val messages = listOf(
            createMessage(1, "Hello"),
            createMessage(2, "Hi there"),
        )
        assertNull(profileBuilder.buildProfile(messages))
    }

    @Test
    fun `buildProfile returns profile for sufficient French messages`() = runTest {
        val messages = listOf(
            createMessage(1, "Bonjour, comment ça va aujourd'hui?"),
            createMessage(2, "Je vais bien merci, et toi?"),
            createMessage(3, "On se voit demain pour le déjeuner?"),
            createMessage(4, "Oui, parfait! À demain alors."),
            createMessage(5, "N'oublie pas les documents pour la réunion."),
            createMessage(6, "C'est noté, je les apporte demain matin."),
        )

        val profile = profileBuilder.buildProfile(messages)

        assertNotNull(profile)
        assertEquals("fr", profile!!.dominantLanguage)
    }

    @Test
    fun `buildProfile returns profile for English messages`() = runTest {
        val messages = listOf(
            createMessage(1, "Hey, how are you doing today?"),
            createMessage(2, "I'm doing great, thanks for asking!"),
            createMessage(3, "Want to grab lunch tomorrow?"),
            createMessage(4, "Sure, that sounds like a plan."),
            createMessage(5, "Don't forget the meeting at three."),
            createMessage(6, "Got it, I'll be there on time."),
        )

        val profile = profileBuilder.buildProfile(messages)

        assertNotNull(profile)
        assertEquals("en", profile!!.dominantLanguage)
    }

    @Test
    fun `buildProfile computes avg response length from sent messages`() = runTest {
        val messages = listOf(
            createMessage(1, "Hello there!", type = 1),       // received
            createMessage(2, "Hi! How are you?", type = 2),   // sent (16 chars)
            createMessage(3, "I'm good, thanks", type = 1),   // received
            createMessage(4, "Great to hear!", type = 2),      // sent (14 chars)
            createMessage(5, "See you later", type = 1),       // received
            createMessage(6, "Bye!", type = 2),                // sent (4 chars)
        )

        val profile = profileBuilder.buildProfile(messages)

        assertNotNull(profile)
        // Avg of sent messages: (16 + 14 + 4) / 3 = 11
        assertEquals(11, profile!!.avgResponseLength)
    }

    @Test
    fun `buildProfile generates 64-dim style embedding`() = runTest {
        val messages = (1..10).map { i ->
            createMessage(i.toLong(), "This is test message number $i with some content")
        }

        val profile = profileBuilder.buildProfile(messages)

        assertNotNull(profile)
        assertEquals(ProfileBuilder.EMBEDDING_DIM, profile!!.styleEmbedding.size)
    }

    @Test
    fun `buildProfile style embedding is normalized`() = runTest {
        val messages = (1..10).map { i ->
            createMessage(i.toLong(), "Testing the embedding normalization for message $i")
        }

        val profile = profileBuilder.buildProfile(messages)
        assertNotNull(profile)

        // L2 norm should be approximately 1.0
        val magnitude = Math.sqrt(
            profile!!.styleEmbedding.map { (it * it).toDouble() }.sum()
        )
        assertTrue(Math.abs(magnitude - 1.0) < 0.01, "Embedding magnitude should be ~1.0, got $magnitude")
    }

    @Test
    fun `extractTopNgrams returns frequent bigrams`() {
        val bodies = listOf(
            "hello world hello world",
            "hello world again",
            "hello world one more time",
        )

        val ngrams = profileBuilder.extractTopNgrams(bodies, 5)

        assertTrue(ngrams.isNotEmpty())
        // "hello world" appears 4 times — should be the top bigram
        assertEquals("hello world", ngrams.first())
    }

    @Test
    fun `extractTopNgrams filters single-occurrence ngrams`() {
        val bodies = listOf(
            "unique phrase here",
            "another unique thing",
        )

        val ngrams = profileBuilder.extractTopNgrams(bodies, 10)

        // No bigram appears 2+ times, so all should be filtered out
        assertTrue(ngrams.isEmpty())
    }

    @Test
    fun `buildProfile returns null for blank messages`() = runTest {
        val messages = (1..10).map { i ->
            createMessage(i.toLong(), "   ")
        }

        assertNull(profileBuilder.buildProfile(messages))
    }

    @Test
    fun `computeStyleEmbedding returns zero vector for empty input`() {
        val embedding = profileBuilder.computeStyleEmbedding(emptyList())
        assertEquals(ProfileBuilder.EMBEDDING_DIM, embedding.size)
        assertTrue(embedding.all { it == 0f })
    }
}
