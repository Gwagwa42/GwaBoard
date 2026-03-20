package dev.gwaboard.keyboard.engine

/**
 * Contract for word suggestion engines in GwaBoard.
 *
 * Both Tier 1 (N-gram) and Tier 2 (SmolLM2) engines implement this interface,
 * enabling [HybridSuggestionEngine] to orchestrate them transparently.
 */
interface SuggestionEngine {

    /**
     * Returns up to [maxResults] word completions for the given [context].
     *
     * @param context The text preceding the cursor (may be partial word + preceding words).
     * @param maxResults Maximum number of suggestions to return.
     * @return Ranked list of suggestions, best first.
     */
    suspend fun suggest(context: String, maxResults: Int = 3): List<Suggestion>

    /**
     * Learns from a completed word in context, updating internal models.
     * Implementations must skip learning when [isPasswordField] is true.
     *
     * @param precedingWords Words before the completed word (up to n-1 for n-gram context).
     * @param completedWord The word the user just finished typing.
     * @param isPasswordField True if the current input field is a password/sensitive type.
     */
    suspend fun learn(precedingWords: List<String>, completedWord: String, isPasswordField: Boolean)

    /** Release any resources held by this engine (model memory, database handles). */
    fun close()
}

/**
 * A single word suggestion with a confidence score.
 *
 * @property word The suggested word.
 * @property score Confidence score in [0.0, 1.0] range — higher is better.
 * @property source Which engine produced this suggestion, useful for debugging/telemetry.
 */
data class Suggestion(
    val word: String,
    val score: Float,
    val source: SuggestionSource = SuggestionSource.NGRAM,
)

/** Identifies which engine produced a suggestion. */
enum class SuggestionSource {
    NGRAM,
    LLM,
}
