package dev.gwaboard.keyboard.engine

import android.util.Log
import dev.gwaboard.shared.models.ContactProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates the 2-tier AI suggestion pipeline.
 *
 * - **Tier 1 (N-gram)**: fires on every keystroke, <10ms latency target.
 * - **Tier 2 (SmolLM2)**: fires on pause >300ms or incoming SMS context.
 *   Loads lazily on first Tier 2 trigger, unloads after 30s inactivity.
 *
 * The hybrid engine always returns Tier 1 results immediately. When Tier 2
 * is triggered and completes within its time budget, results are merged
 * additively (no UI flicker — LLM results supplement n-gram results).
 *
 * If the user resumes typing while Tier 2 inference is running, the ongoing
 * inference is cancelled to avoid wasting compute on stale context.
 */
class HybridSuggestionEngine(
    private val ngramEngine: NGramEngine,
    private val smolLMEngine: SmolLMEngine? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    backgroundScope: CoroutineScope? = null,
) : SuggestionEngine {

    /**
     * Scope for background tasks (inactivity unload timer) that must outlive
     * individual [suggestWithTier2] calls. Uses a SupervisorJob so failures
     * in the timer don't cancel unrelated work.
     */
    private val backgroundScope: CoroutineScope =
        backgroundScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Timestamp of the last Tier 2 usage, for inactivity-based unloading. */
    private val lastTier2UsageMs = AtomicLong(0L)

    /** The last received SMS body, injected via [onSmsReceived] for LLM context. */
    @Volatile
    private var lastReceivedSms: String? = null

    /** Contact profile from the companion app, injected via [setContactProfile]. */
    @Volatile
    private var contactProfile: ContactProfile? = null

    /** Whether Tier 2 has been triggered at least once in the current session. */
    @Volatile
    private var tier2EverTriggered = false

    /**
     * Whether AI suggestions (Tier 2) are enabled. When disabled, only Tier 1 (n-gram)
     * suggestions are returned, effectively falling back to standard NLP behavior.
     * Controlled by the user via the privacy settings "Disable AI suggestions" toggle.
     */
    @Volatile
    var isAiEnabled: Boolean = true
        private set

    /** Active inactivity unload job, cancelled and relaunched on each Tier 2 usage. */
    @Volatile
    private var unloadJob: Job? = null

    /** Guards Tier 2 load/unload transitions to prevent races. */
    private val tier2LifecycleMutex = Mutex()

    /**
     * Returns suggestions from Tier 1 only. This is the fast path called on
     * every keystroke. Tier 2 is triggered separately via [suggestWithTier2].
     */
    override suspend fun suggest(context: String, maxResults: Int): List<Suggestion> =
        coroutineScope {
            // Cancel any ongoing Tier 2 inference — user is actively typing,
            // so the previous pause-triggered LLM result is now stale
            smolLMEngine?.cancelInference()

            withTimeoutOrNull(TIER1_TIMEOUT_MS) {
                ngramEngine.suggest(context, maxResults)
            } ?: emptyList()
        }

    /**
     * Triggers Tier 2 (LLM) inference alongside Tier 1. Called when the IME
     * detects a typing pause >300ms or a sentence boundary.
     *
     * Tier 2 is loaded lazily on first invocation. The engine is unloaded
     * after [INACTIVITY_TIMEOUT_MS] of no Tier 2 calls.
     *
     * @param context Text preceding the cursor.
     * @param maxResults Maximum suggestions to return.
     * @param onTier1Ready Callback with immediate Tier 1 results (for UI update).
     * @param onTier2Ready Callback with merged Tier 1 + Tier 2 results (additive UI update).
     */
    suspend fun suggestWithTier2(
        context: String,
        maxResults: Int = 3,
        onTier1Ready: (List<Suggestion>) -> Unit = {},
        onTier2Ready: (List<Suggestion>) -> Unit = {},
    ) = coroutineScope {
        // Tier 1 always fires first
        val tier1Deferred = async {
            withTimeoutOrNull(TIER1_TIMEOUT_MS) {
                ngramEngine.suggest(context, maxResults)
            } ?: emptyList()
        }

        // Deliver Tier 1 results immediately
        val tier1Results = tier1Deferred.await()
        onTier1Ready(tier1Results)

        // Tier 2 runs only if the engine is available and AI is enabled
        val engine = smolLMEngine ?: return@coroutineScope
        if (!isAiEnabled) return@coroutineScope
        ensureTier2Loaded(engine)

        val tier2Results = withTimeoutOrNull(TIER2_TIMEOUT_MS) {
            engine.suggestWithContext(
                context = context,
                maxResults = maxResults,
                contactProfile = contactProfile,
                lastReceivedMessage = lastReceivedSms,
            )
        } ?: emptyList()

        recordTier2Usage()

        if (tier2Results.isNotEmpty()) {
            val merged = mergeSuggestions(tier1Results, tier2Results, maxResults)
            onTier2Ready(merged)
        }

        // Schedule unload after inactivity
        scheduleUnloadAfterInactivity(engine)
    }

    /**
     * Notifies the engine that an SMS was received. The message body is stored
     * and injected into the next Tier 2 prompt for conversational context.
     */
    fun onSmsReceived(messageBody: String) {
        lastReceivedSms = messageBody
        Log.d(TAG, "SMS context updated (${messageBody.length} chars)")
    }

    /**
     * Sets the contact profile from the companion app. Used by the LLM
     * to personalize tone, language, and response length.
     */
    fun setContactProfile(profile: ContactProfile?) {
        contactProfile = profile
        Log.d(TAG, "Contact profile ${if (profile != null) "set" else "cleared"}")
    }

    /** Clears the SMS context (e.g., when switching conversations). */
    fun clearSmsContext() {
        lastReceivedSms = null
    }

    /** Returns true if Tier 2 is currently loaded in memory. */
    fun isTier2Loaded(): Boolean = smolLMEngine?.isLoaded == true

    /**
     * Enables or disables AI (Tier 2) suggestions. When disabled, only Tier 1
     * n-gram suggestions are returned and the LLM model is unloaded to free memory.
     * This is the runtime toggle for the "Disable AI suggestions" privacy control.
     */
    suspend fun setAiEnabled(enabled: Boolean) {
        isAiEnabled = enabled
        Log.i(TAG, "AI suggestions ${if (enabled) "enabled" else "disabled"}")

        if (!enabled) {
            // Unload the LLM model to free ~400MB RAM
            smolLMEngine?.let { engine ->
                tier2LifecycleMutex.withLock {
                    if (engine.isLoaded) {
                        engine.unload()
                        Log.i(TAG, "Tier 2 engine unloaded (AI disabled by user)")
                    }
                }
            }
            clearSmsContext()
            contactProfile = null
        }
    }

    /**
     * Clears all user-learned data from the n-gram engine.
     * Delegates to [NGramEngine.clearLearnedData] which purges both
     * in-memory and persisted n-gram entries.
     */
    suspend fun clearLearnedData() {
        ngramEngine.clearLearnedData()
        Log.i(TAG, "Learned word data cleared")
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
        backgroundScope.cancel()
        ngramEngine.close()
        smolLMEngine?.close()
        Log.i(TAG, "HybridSuggestionEngine closed")
    }

    /**
     * Ensures the Tier 2 engine is loaded, performing lazy initialization
     * on first use. Protected by mutex to prevent concurrent loads.
     */
    private suspend fun ensureTier2Loaded(engine: SmolLMEngine) {
        if (engine.isLoaded) return

        tier2LifecycleMutex.withLock {
            // Double-check after acquiring lock
            if (engine.isLoaded) return

            Log.i(TAG, "Lazy-loading Tier 2 engine (first trigger)")
            engine.load()
            tier2EverTriggered = true
            Log.i(TAG, "Tier 2 engine loaded")
        }
    }

    /** Records the current time as the last Tier 2 usage for inactivity tracking. */
    private fun recordTier2Usage() {
        lastTier2UsageMs.set(clock())
    }

    /**
     * Schedules Tier 2 unloading after [INACTIVITY_TIMEOUT_MS] of no Tier 2 calls.
     * Cancels any previously scheduled unload job. Runs in [backgroundScope] so
     * the timer outlives individual [suggestWithTier2] calls.
     */
    private fun scheduleUnloadAfterInactivity(engine: SmolLMEngine) {
        unloadJob?.cancel()
        unloadJob = backgroundScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            // Only unload if no Tier 2 usage happened during the delay
            val elapsed = clock() - lastTier2UsageMs.get()
            if (elapsed >= INACTIVITY_TIMEOUT_MS) {
                tier2LifecycleMutex.withLock {
                    if (engine.isLoaded) {
                        Log.i(TAG, "Unloading Tier 2 after ${elapsed}ms inactivity")
                        engine.unload()
                    }
                }
            }
        }
    }

    /**
     * Merges suggestions from both tiers. Tier 2 (LLM) results are treated as
     * higher quality and take priority when the same word appears in both sets.
     * The merge is additive — Tier 1 results remain unless displaced by Tier 2.
     */
    internal fun mergeSuggestions(
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

        /** Tier 1 (n-gram) must complete within 10ms. */
        internal const val TIER1_TIMEOUT_MS = 10L

        /** Tier 2 (LLM) is allowed up to 500ms for inference. */
        internal const val TIER2_TIMEOUT_MS = 500L

        /** Unload Tier 2 model after 30 seconds of no Tier 2 triggers. */
        internal const val INACTIVITY_TIMEOUT_MS = 30_000L

        /** IME triggers Tier 2 after this typing pause duration. */
        const val TYPING_PAUSE_TRIGGER_MS = 300L
    }
}
