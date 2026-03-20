package dev.gwaboard.keyboard.engine

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates the 2-tier AI suggestion pipeline.
 *
 * - **Tier 1 (N-gram)**: fires on every keystroke, <10ms latency target.
 * - **Tier 2 (SmolLM2)**: fires on pause >300ms or incoming SMS context.
 *   Loads lazily, unloads after 30s inactivity. (Stub — implemented in a future milestone.)
 *
 * The hybrid engine always returns Tier 1 results immediately. If Tier 2 is
 * available and completes within its time budget, its results are merged in
 * (higher-quality suggestions may replace lower-scored Tier 1 candidates).
 */
class HybridSuggestionEngine(
    private val ngramEngine: NGramEngine,
    private val llmEngine: SuggestionEngine? = null, // Tier 2 — provided in a future milestone
) : SuggestionEngine {

    override suspend fun suggest(context: String, maxResults: Int): List<Suggestion> =
        coroutineScope {
            // Tier 1 always runs — must complete within TIER1_TIMEOUT_MS
            val tier1Deferred = async {
                withTimeoutOrNull(TIER1_TIMEOUT_MS) {
                    ngramEngine.suggest(context, maxResults)
                } ?: emptyList()
            }

            // Tier 2 runs only if engine is available (future milestone)
            val tier2Deferred = llmEngine?.let { engine ->
                async {
                    withTimeoutOrNull(TIER2_TIMEOUT_MS) {
                        engine.suggest(context, maxResults)
                    }
                }
            }

            val tier1Results = tier1Deferred.await()
            val tier2Results = tier2Deferred?.await() ?: emptyList()

            if (tier2Results.isEmpty()) {
                return@coroutineScope tier1Results
            }

            // Merge: Tier 2 results take priority when they overlap with Tier 1
            mergeSuggestions(tier1Results, tier2Results, maxResults)
        }

    override suspend fun learn(
        precedingWords: List<String>,
        completedWord: String,
        isPasswordField: Boolean,
    ) {
        ngramEngine.learn(precedingWords, completedWord, isPasswordField)
        // Tier 2 learning is not needed — the LLM is pre-trained
    }

    override fun close() {
        ngramEngine.close()
        llmEngine?.close()
        Log.i(TAG, "HybridSuggestionEngine closed")
    }

    /**
     * Merges suggestions from both tiers. Tier 2 (LLM) results are scored higher
     * and take priority when the same word appears in both result sets.
     */
    private fun mergeSuggestions(
        tier1: List<Suggestion>,
        tier2: List<Suggestion>,
        maxResults: Int,
    ): List<Suggestion> {
        val merged = LinkedHashMap<String, Suggestion>()

        // Tier 2 results first (higher quality)
        for (suggestion in tier2) {
            merged[suggestion.word] = suggestion
        }

        // Tier 1 fills remaining slots (only if not already present from Tier 2)
        for (suggestion in tier1) {
            merged.putIfAbsent(suggestion.word, suggestion)
        }

        return merged.values.take(maxResults)
    }

    companion object {
        private const val TAG = "HybridEngine"
        private const val TIER1_TIMEOUT_MS = 10L
        private const val TIER2_TIMEOUT_MS = 500L
    }
}
