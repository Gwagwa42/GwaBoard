package dev.gwaboard.keyboard.engine

/**
 * In-memory n-gram model backed by a hash map for O(1) lookups.
 *
 * Stores unigrams, bigrams, and trigrams as frequency counts. The hash map approach
 * was chosen over a trie for simpler implementation and comparable performance at the
 * vocabulary sizes expected for a keyboard (~50k-100k entries). Lookups are O(1)
 * amortized, well within the <10ms latency budget.
 *
 * Memory layout: key = "w1|w2|w3" (pipe-delimited), value = frequency count.
 * Unigrams use single word keys; bigrams use "w1|w2"; trigrams use "w1|w2|w3".
 */
class NGramModel(
    /** Maximum n-gram order (2 = bigrams, 3 = trigrams). */
    val maxOrder: Int = 3,
    /** Maximum number of entries before pruning low-frequency n-grams. */
    private val maxEntries: Int = 500_000,
) {

    // Separate maps per order for efficient prefix-based lookups
    private val unigrams = HashMap<String, Int>(10_000)
    private val bigrams = HashMap<String, Int>(50_000)
    private val trigrams = HashMap<String, Int>(100_000)

    /** Total count of all unigrams, cached for probability calculations. */
    @Volatile
    private var totalUnigramCount: Long = 0L

    /**
     * Records an n-gram occurrence. Increments the frequency count for the given
     * word sequence and all shorter sub-sequences (backoff counts).
     *
     * @param words Word sequence, length must be in [1, maxOrder].
     */
    fun record(words: List<String>) {
        require(words.isNotEmpty()) { "Word list must not be empty" }

        val normalizedWords = words.map { it.lowercase() }
        val order = normalizedWords.size.coerceAtMost(maxOrder)

        // Record the full n-gram and all shorter prefixes (backoff chain)
        for (n in 1..order) {
            val startIdx = normalizedWords.size - n
            val ngram = normalizedWords.subList(startIdx, normalizedWords.size)
            val key = ngram.joinToString(SEPARATOR)

            when (n) {
                1 -> {
                    unigrams[key] = (unigrams[key] ?: 0) + 1
                    totalUnigramCount++
                }
                2 -> bigrams[key] = (bigrams[key] ?: 0) + 1
                3 -> trigrams[key] = (trigrams[key] ?: 0) + 1
            }
        }

        // Prune if any map exceeds capacity
        pruneIfNeeded()
    }

    /**
     * Looks up completions given a context of preceding words.
     *
     * Uses stupid backoff: tries trigram match first, then bigram, then unigram,
     * applying a discount factor at each backoff level. This is simple and fast
     * compared to Kneser-Ney while still producing reasonable results.
     *
     * @param precedingWords The words before the cursor (last 1-2 words used as context).
     * @param prefix Optional partial word prefix for filtering completions.
     * @param maxResults Maximum number of suggestions.
     * @return List of (word, score) pairs sorted by descending score.
     */
    fun lookup(
        precedingWords: List<String>,
        prefix: String = "",
        maxResults: Int = 3,
    ): List<Pair<String, Float>> {
        val normalizedPrefix = prefix.lowercase()
        val normalizedContext = precedingWords.map { it.lowercase() }

        // Collect candidates with scores using stupid backoff
        val candidates = HashMap<String, Float>()

        // Try trigram: context = [w1, w2], predict w3
        if (normalizedContext.size >= 2) {
            val ctx = normalizedContext.takeLast(2)
            val contextKey = ctx.joinToString(SEPARATOR) + SEPARATOR
            collectCandidates(trigrams, contextKey, normalizedPrefix, candidates, TRIGRAM_WEIGHT)
        }

        // Try bigram: context = [w1], predict w2
        if (normalizedContext.isNotEmpty()) {
            val ctx = normalizedContext.last()
            val contextKey = ctx + SEPARATOR
            collectCandidates(bigrams, contextKey, normalizedPrefix, candidates, BIGRAM_WEIGHT)
        }

        // Unigram fallback
        collectCandidates(unigrams, "", normalizedPrefix, candidates, UNIGRAM_WEIGHT)

        // Normalize scores to [0, 1] range and sort
        val maxScore = candidates.values.maxOrNull() ?: return emptyList()
        return candidates.entries
            .map { (word, score) -> word to (score / maxScore) }
            .sortedByDescending { it.second }
            .take(maxResults)
    }

    /**
     * Loads pre-built frequency data from a flat list of (ngram-words, count) pairs.
     * Used for loading dictionary/corpus data at startup.
     */
    fun loadEntries(entries: List<Pair<List<String>, Int>>) {
        for ((words, count) in entries) {
            val key = words.joinToString(SEPARATOR) { it.lowercase() }
            when (words.size) {
                1 -> {
                    unigrams[key] = (unigrams[key] ?: 0) + count
                    totalUnigramCount += count
                }
                2 -> bigrams[key] = (bigrams[key] ?: 0) + count
                3 -> trigrams[key] = (trigrams[key] ?: 0) + count
            }
        }
    }

    /**
     * Exports all n-gram entries for persistence.
     * Returns a flat list of (order, key, count) triples.
     */
    fun exportEntries(): List<Triple<Int, String, Int>> {
        val result = mutableListOf<Triple<Int, String, Int>>()
        unigrams.forEach { (key, count) -> result.add(Triple(1, key, count)) }
        bigrams.forEach { (key, count) -> result.add(Triple(2, key, count)) }
        trigrams.forEach { (key, count) -> result.add(Triple(3, key, count)) }
        return result
    }

    /** Number of total entries across all n-gram orders. */
    fun size(): Int = unigrams.size + bigrams.size + trigrams.size

    /** Clears all n-gram data. */
    fun clear() {
        unigrams.clear()
        bigrams.clear()
        trigrams.clear()
        totalUnigramCount = 0L
    }

    /**
     * Collects candidates from the given map that start with [contextKey] and
     * whose predicted word starts with [prefix].
     */
    private fun collectCandidates(
        map: HashMap<String, Int>,
        contextKey: String,
        prefix: String,
        candidates: HashMap<String, Float>,
        weight: Float,
    ) {
        if (contextKey.isEmpty()) {
            // Unigram mode: iterate unigrams matching prefix
            for ((key, count) in map) {
                if (prefix.isEmpty() || key.startsWith(prefix)) {
                    val existingScore = candidates[key] ?: 0f
                    candidates[key] = existingScore + count * weight
                }
            }
        } else {
            // Bigram/trigram mode: find entries starting with contextKey
            for ((key, count) in map) {
                if (key.startsWith(contextKey)) {
                    val predictedWord = key.substringAfterLast(SEPARATOR)
                    if (prefix.isEmpty() || predictedWord.startsWith(prefix)) {
                        val existingScore = candidates[predictedWord] ?: 0f
                        candidates[predictedWord] = existingScore + count * weight
                    }
                }
            }
        }
    }

    /**
     * Prunes the lowest-frequency entries when any map exceeds its share of [maxEntries].
     * Uses a simple threshold: remove entries with count <= 1.
     */
    private fun pruneIfNeeded() {
        val totalSize = size()
        if (totalSize <= maxEntries) return

        // Remove hapax legomena (entries appearing only once), starting from highest order
        trigrams.entries.removeAll { it.value <= 1 }
        if (size() <= maxEntries) return

        bigrams.entries.removeAll { it.value <= 1 }
    }

    companion object {
        /** Separator used to join words into n-gram keys. */
        internal const val SEPARATOR = "|"

        // Stupid backoff weights: higher-order matches are preferred
        private const val TRIGRAM_WEIGHT = 1.0f
        private const val BIGRAM_WEIGHT = 0.4f   // alpha = 0.4 discount per backoff level
        private const val UNIGRAM_WEIGHT = 0.16f  // alpha^2
    }
}
