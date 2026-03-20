package dev.gwaboard.companion.profile

import android.util.Log
import dev.gwaboard.shared.models.ContactProfile
import dev.gwaboard.shared.models.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds per-contact statistical profiles from SMS message history.
 *
 * The profile captures communication patterns (language, tone, n-grams,
 * style embedding) without storing raw message content — only derived
 * statistics are persisted. This is the core privacy guarantee.
 *
 * Analysis pipeline per contact:
 * 1. Language detection (FR/EN via character trigrams)
 * 2. Tone classification (formal/casual/friendly)
 * 3. N-gram extraction (top word pairs/triples)
 * 4. Style embedding (64-dim vocabulary distribution vector)
 * 5. Metadata (avg response length, frequency)
 */
class ProfileBuilder {

    /**
     * Build a [ContactProfile] from the given SMS messages.
     *
     * All messages should belong to the same contact. Both sent and
     * received messages are analyzed — sent messages reveal the user's
     * style with that contact, while received messages inform language
     * and tone detection.
     *
     * @param messages SMS messages for a single contact.
     * @return A [ContactProfile] with derived statistics, or null if
     *         there are too few messages for meaningful analysis.
     */
    suspend fun buildProfile(
        messages: List<SmsMessage>,
    ): ContactProfile? = withContext(Dispatchers.Default) {
        if (messages.size < MIN_MESSAGES_FOR_PROFILE) {
            Log.d(TAG, "Not enough messages (${messages.size}) for profile, need $MIN_MESSAGES_FOR_PROFILE")
            return@withContext null
        }

        val bodies = messages.map { it.body }.filter { it.isNotBlank() }
        if (bodies.isEmpty()) return@withContext null

        val allText = bodies.joinToString(" ")

        // 1. Language detection
        val language = LanguageDetector.detect(allText)

        // 2. Tone classification
        val tone = ToneAnalyzer.analyze(bodies)

        // 3. Average response length (from sent messages only)
        val sentMessages = messages.filter { it.type == SMS_TYPE_SENT }
        val avgResponseLength = if (sentMessages.isNotEmpty()) {
            sentMessages.map { it.body.length }.average().toInt()
        } else {
            bodies.map { it.length }.average().toInt()
        }

        // 4. Top n-grams (word-level bigrams and trigrams)
        val topNgrams = extractTopNgrams(bodies, TOP_NGRAM_COUNT)

        // 5. Style embedding (64-dim vocabulary distribution vector)
        val styleEmbedding = computeStyleEmbedding(bodies)

        ContactProfile(
            dominantLanguage = language,
            tone = tone,
            avgResponseLength = avgResponseLength,
            topNgrams = topNgrams,
            styleEmbedding = styleEmbedding,
        )
    }

    /**
     * Incrementally update an existing profile with new messages.
     *
     * Merges new message data with the existing profile rather than
     * recomputing from scratch. Useful for processing incoming SMS
     * without full re-analysis.
     *
     * @param existing The current profile for this contact.
     * @param newMessages Newly received messages to incorporate.
     * @param allMessages Complete message history (for accurate n-grams).
     * @return Updated [ContactProfile].
     */
    suspend fun updateProfile(
        existing: ContactProfile,
        newMessages: List<SmsMessage>,
        allMessages: List<SmsMessage>,
    ): ContactProfile = withContext(Dispatchers.Default) {
        // For incremental updates, rebuild with the full message set
        // A more sophisticated approach could merge statistics, but
        // full rebuild is simpler and always consistent
        buildProfile(allMessages) ?: existing
    }

    /**
     * Extract the top n-grams (word-level bigrams) from message bodies.
     *
     * @param bodies Message texts to analyze.
     * @param count Number of top n-grams to return.
     * @return List of most frequent word bigrams as space-separated strings.
     */
    internal fun extractTopNgrams(bodies: List<String>, count: Int): List<String> {
        val ngramCounts = mutableMapOf<String, Int>()

        for (body in bodies) {
            val words = body.lowercase()
                .replace(PUNCTUATION, " ")
                .split(WORD_SPLITTER)
                .filter { it.length > 1 } // Skip single-char tokens

            // Bigrams
            for (i in 0 until words.size - 1) {
                val bigram = "${words[i]} ${words[i + 1]}"
                ngramCounts[bigram] = (ngramCounts[bigram] ?: 0) + 1
            }
        }

        return ngramCounts.entries
            .filter { it.value >= MIN_NGRAM_FREQUENCY }
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
    }

    /**
     * Compute a 64-dimensional style embedding from vocabulary distribution.
     *
     * Uses a simple bag-of-words hashing approach: each word is hashed to
     * one of 64 buckets, and the bucket values are normalized to create
     * a unit vector. This produces a compact representation of vocabulary
     * usage patterns that the Tier-2 LLM can use for style matching.
     */
    internal fun computeStyleEmbedding(bodies: List<String>): List<Float> {
        val vector = FloatArray(EMBEDDING_DIM)

        for (body in bodies) {
            val words = body.lowercase()
                .replace(PUNCTUATION, " ")
                .split(WORD_SPLITTER)
                .filter { it.length > 1 }

            for (word in words) {
                // Hash word to a bucket index
                val bucket = (word.hashCode() and 0x7FFFFFFF) % EMBEDDING_DIM
                vector[bucket] += 1f
            }
        }

        // L2-normalize the vector
        val magnitude = Math.sqrt(vector.map { (it * it).toDouble() }.sum()).toFloat()
        if (magnitude > 0f) {
            for (i in vector.indices) {
                vector[i] = vector[i] / magnitude
            }
        }

        return vector.toList()
    }

    companion object {
        private const val TAG = "ProfileBuilder"

        /** Minimum messages needed for a meaningful profile */
        private const val MIN_MESSAGES_FOR_PROFILE = 5

        /** Number of top n-grams to include in profile */
        private const val TOP_NGRAM_COUNT = 20

        /** Minimum frequency for an n-gram to be included */
        private const val MIN_NGRAM_FREQUENCY = 2

        /** Style embedding vector dimensionality */
        internal const val EMBEDDING_DIM = 64

        /** SMS type constant for sent messages */
        private const val SMS_TYPE_SENT = 2

        private val WORD_SPLITTER = Regex("\\s+")
        private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
    }
}
