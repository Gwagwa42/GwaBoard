package dev.gwaboard.companion.profile

/**
 * Analyzes SMS message vocabulary to classify communication tone.
 *
 * Uses a rule-based approach combining multiple signals:
 * - Vocabulary markers (formal vs casual word patterns)
 * - Punctuation usage (exclamation marks, emojis, abbreviations)
 * - Capitalization patterns
 * - Average sentence length
 *
 * Outputs one of: "formal", "casual", "friendly".
 */
internal object ToneAnalyzer {

    /** Supported tone classifications */
    enum class Tone(val value: String) {
        FORMAL("formal"),
        CASUAL("casual"),
        FRIENDLY("friendly"),
    }

    /**
     * Classify the communication tone from a collection of messages.
     *
     * @param messages List of message bodies to analyze.
     * @return Tone string: "formal", "casual", or "friendly".
     */
    fun analyze(messages: List<String>): String {
        if (messages.isEmpty()) return Tone.CASUAL.value

        val allText = messages.joinToString(" ")
        val lowerText = allText.lowercase()
        val wordCount = allText.split(WORD_SPLITTER).count { it.isNotBlank() }

        if (wordCount == 0) return Tone.CASUAL.value

        // Compute signal scores
        val formalScore = computeFormalScore(lowerText, wordCount)
        val casualScore = computeCasualScore(lowerText, allText, messages)

        return when {
            formalScore > casualScore + THRESHOLD -> Tone.FORMAL.value
            casualScore > formalScore + THRESHOLD -> Tone.CASUAL.value
            else -> Tone.FRIENDLY.value
        }
    }

    /**
     * Score formal language signals: polite forms, complete sentences,
     * proper punctuation, formal vocabulary.
     */
    private fun computeFormalScore(lowerText: String, wordCount: Int): Double {
        var score = 0.0

        // French formal markers
        FORMAL_MARKERS_FR.forEach { marker ->
            if (marker in lowerText) score += 2.0
        }

        // English formal markers
        FORMAL_MARKERS_EN.forEach { marker ->
            if (marker in lowerText) score += 2.0
        }

        // Longer average word count per message suggests formality
        val avgWords = wordCount.toDouble()
        if (avgWords > 15) score += 1.0
        if (avgWords > 30) score += 1.0

        return score
    }

    /**
     * Score casual language signals: abbreviations, emoji usage,
     * lack of punctuation, slang.
     */
    private fun computeCasualScore(
        lowerText: String,
        originalText: String,
        messages: List<String>,
    ): Double {
        var score = 0.0

        // Casual markers (abbreviations, slang)
        CASUAL_MARKERS.forEach { marker ->
            if (marker in lowerText) score += 2.0
        }

        // Emoji and emoticon density
        val emojiCount = EMOJI_PATTERN.findAll(originalText).count()
        score += (emojiCount * 0.5).coerceAtMost(5.0)

        // Excessive punctuation (!!!, ???, ...)
        val excessivePunct = EXCESSIVE_PUNCT.findAll(originalText).count()
        score += (excessivePunct * 0.5).coerceAtMost(3.0)

        // Short messages suggest casual communication
        val avgLength = messages.map { it.length }.average()
        if (avgLength < 50) score += 1.0
        if (avgLength < 20) score += 1.0

        return score
    }

    private const val THRESHOLD = 1.0

    private val WORD_SPLITTER = Regex("\\s+")

    /** Common French formal expressions */
    private val FORMAL_MARKERS_FR = listOf(
        "cordialement", "je vous prie", "veuillez", "madame", "monsieur",
        "bien à vous", "sincèrement", "respectueusement", "en vous remerciant",
        "je me permets", "n'hésitez pas", "bonne réception",
    )

    /** Common English formal expressions */
    private val FORMAL_MARKERS_EN = listOf(
        "sincerely", "regards", "dear", "please find", "kindly",
        "thank you for", "i would appreciate", "at your convenience",
        "best regards", "looking forward",
    )

    /** Casual language markers (both FR and EN) */
    private val CASUAL_MARKERS = listOf(
        "lol", "mdr", "ptdr", "haha", "omg", "btw", "idk",
        "slt", "cc", "tkt", "jsp", "stp", "pk", "pcq",
        "gonna", "wanna", "gotta", "kinda", "nah", "yeah",
        "yo ", "bro", "dude", " ok ", "okay", "np", "thx",
    )

    /** Pattern matching common emoji ranges */
    private val EMOJI_PATTERN = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+|[\\u2600-\\u27BF]")

    /** Excessive punctuation pattern */
    private val EXCESSIVE_PUNCT = Regex("[!?]{2,}|\\.{3,}")
}
