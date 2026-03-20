package dev.gwaboard.companion.profile

/**
 * Lightweight n-gram language detection for French and English.
 *
 * Uses character-level trigram frequency profiles built from corpus data.
 * The detector compares a text's trigram distribution against reference
 * profiles using cosine distance — the closest match wins.
 *
 * This is intentionally minimal: only FR/EN are supported since those
 * are the target languages for the GwaBoard keyboard.
 */
internal object LanguageDetector {

    /** Supported languages with ISO 639-1 codes */
    enum class Language(val code: String) {
        FRENCH("fr"),
        ENGLISH("en"),
    }

    /**
     * Detect the dominant language of the given [text].
     *
     * @param text Input text to analyze (should be at least a few words).
     * @return ISO 639-1 language code ("fr" or "en").
     *         Returns "en" as default for very short or ambiguous texts.
     */
    fun detect(text: String): String {
        val normalized = text.lowercase().trim()
        if (normalized.length < MIN_TEXT_LENGTH) return Language.ENGLISH.code

        val trigrams = extractTrigrams(normalized)
        if (trigrams.isEmpty()) return Language.ENGLISH.code

        val frScore = computeScore(trigrams, FRENCH_TRIGRAMS)
        val enScore = computeScore(trigrams, ENGLISH_TRIGRAMS)

        return if (frScore > enScore) Language.FRENCH.code else Language.ENGLISH.code
    }

    /**
     * Extract character-level trigrams from normalized text.
     * Returns a frequency map of trigram -> count.
     */
    internal fun extractTrigrams(text: String): Map<String, Int> {
        if (text.length < 3) return emptyMap()

        val counts = mutableMapOf<String, Int>()
        for (i in 0..text.length - 3) {
            val trigram = text.substring(i, i + 3)
            counts[trigram] = (counts[trigram] ?: 0) + 1
        }
        return counts
    }

    /**
     * Compute a similarity score between observed trigrams and a reference profile.
     * Uses dot product of normalized frequency vectors (cosine similarity).
     */
    private fun computeScore(observed: Map<String, Int>, reference: Set<String>): Double {
        val totalObserved = observed.values.sum().toDouble()
        if (totalObserved == 0.0) return 0.0

        var matchScore = 0.0
        for ((trigram, count) in observed) {
            if (trigram in reference) {
                matchScore += count / totalObserved
            }
        }
        return matchScore
    }

    /** Minimum text length for reliable detection */
    private const val MIN_TEXT_LENGTH = 10

    /**
     * Top French character trigrams from corpus analysis.
     * These are the most distinctive trigrams that separate French from English.
     */
    private val FRENCH_TRIGRAMS = setOf(
        "les", "ent", "des", "que", "ous", "est", "ion", "ait", "par", "ons",
        "une", "our", "ant", "eur", "men", "tio", "con", "com", "ais", "dan",
        "ans", "pou", "pas", "ell", "tre", "qui", "ire", "ien", "eme", "ous",
        " de", " le", " la", " qu", " co", " pa", " un", " en", " pr", " ce",
        "e l", "e d", "s d", "s l", "de ", "le ", "la ", "es ", "en ", "on ",
        "oi ", "er ", "re ", "ne ", "nt ", "it ", "et ", " et", " po", " so",
        "ais", "ait", "eur", "eau", "oux", "eux", "oi ", "é ", "ée", "ès",
    )

    /**
     * Top English character trigrams from corpus analysis.
     */
    private val ENGLISH_TRIGRAMS = setOf(
        "the", "and", "ing", "ion", "tio", "ent", "ati", "for", "her", "ter",
        "hat", "tha", "ere", "ate", "his", "con", "res", "ver", "all", "ons",
        "nce", "men", "ith", "ted", "ers", "pro", "thi", "wit", "are", "ess",
        " th", " an", " co", " to", " in", " of", " he", " wh", " it", " fo",
        "he ", "nd ", "is ", "in ", "ed ", "of ", "to ", "al ", "ng ", "re ",
        "er ", "on ", "or ", "se ", "an ", "at ", "en ", "it ", "es ", " be",
        "ble", "ght", "ght", "ful", "ous", "ive", "ily", "ity", "ess", "ow ",
    )
}
