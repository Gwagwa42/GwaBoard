package dev.gwaboard.keyboard.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tier 1 suggestion engine using n-gram/Markov models.
 *
 * Provides fast word-by-word completions triggered on every keystroke.
 * Target performance: <10ms latency, ~50MB RAM, top 3 suggestions.
 *
 * The engine maintains two data sources:
 * 1. **Static dictionary n-grams** — loaded from pre-built frequency corpora
 *    for French and English (loaded once at startup).
 * 2. **User-learned n-grams** — accumulated from typing patterns, persisted
 *    to encrypted SQLite via [NGramStorage]. Password fields are excluded.
 *
 * Thread safety: all mutable state is protected by a [Mutex] so the engine
 * can be called concurrently from the IME thread and background learning jobs.
 */
class NGramEngine(
    private val storage: NGramStorage? = null,
    maxOrder: Int = 3,
) : SuggestionEngine {

    private val model = NGramModel(maxOrder = maxOrder)
    private val mutex = Mutex()

    @Volatile
    private var isLoaded = false

    /**
     * Loads pre-built dictionary n-grams and any persisted user-learned n-grams.
     * Should be called once during IME initialization.
     *
     * @param dictionaryEntries Static n-gram data from language dictionaries.
     */
    suspend fun initialize(dictionaryEntries: List<Pair<List<String>, Int>> = emptyList()) {
        mutex.withLock {
            if (isLoaded) return

            // Load static dictionary data
            if (dictionaryEntries.isNotEmpty()) {
                model.loadEntries(dictionaryEntries)
                Log.i(TAG, "Loaded ${dictionaryEntries.size} dictionary n-grams")
            }

            // Restore persisted user n-grams
            storage?.let { store ->
                try {
                    val userEntries = withContext(Dispatchers.IO) { store.loadAll() }
                    model.loadEntries(userEntries)
                    Log.i(TAG, "Restored ${userEntries.size} user-learned n-grams")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore user n-grams, starting fresh", e)
                }
            }

            isLoaded = true
            Log.i(TAG, "NGramEngine initialized — ${model.size()} total entries")
        }
    }

    override suspend fun suggest(context: String, maxResults: Int): List<Suggestion> {
        if (!isLoaded) return emptyList()

        val trimmed = context.trimEnd()
        if (trimmed.isEmpty()) return emptyList()

        // Split context into words; the last token may be a partial word (prefix)
        val tokens = trimmed.split(WORD_SPLITTER)
        val hasTrailingSpace = context.endsWith(" ")

        val precedingWords: List<String>
        val prefix: String

        if (hasTrailingSpace) {
            // User just finished a word, predict next word
            precedingWords = tokens.takeLast(2)
            prefix = ""
        } else {
            // User is mid-word, complete the current partial word
            prefix = tokens.last()
            precedingWords = tokens.dropLast(1).takeLast(2)
        }

        // Lookup is O(n) over the map but n-gram maps are typically small enough
        // to complete well under the 10ms budget on Tensor G3
        val results = mutex.withLock {
            model.lookup(precedingWords, prefix, maxResults)
        }

        return results.map { (word, score) ->
            Suggestion(word = word, score = score, source = SuggestionSource.NGRAM)
        }
    }

    override suspend fun learn(
        precedingWords: List<String>,
        completedWord: String,
        isPasswordField: Boolean,
    ) {
        // Never learn from password or sensitive input fields
        if (isPasswordField) return
        if (completedWord.isBlank()) return

        val words = (precedingWords.takeLast(2) + completedWord)
            .filter { it.isNotBlank() }

        mutex.withLock {
            model.record(words)
        }

        // Persist to encrypted storage in background
        storage?.let { store ->
            try {
                withContext(Dispatchers.IO) {
                    store.recordNGram(words)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist learned n-gram", e)
            }
        }
    }

    override fun close() {
        storage?.close()
        Log.i(TAG, "NGramEngine closed")
    }

    /** Exports current model state for testing/debugging. */
    internal fun modelSize(): Int = model.size()

    companion object {
        private const val TAG = "NGramEngine"
        private val WORD_SPLITTER = Regex("\\s+")
    }
}
