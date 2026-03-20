package dev.gwaboard.keyboard.ui

import android.util.Log
import dev.gwaboard.keyboard.engine.HybridSuggestionEngine
import dev.gwaboard.keyboard.engine.Suggestion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages the [SuggestionBarState] for the Compose suggestion bar.
 *
 * Bridges the IME service and [HybridSuggestionEngine] with the UI layer.
 * Handles:
 * - Keystroke-driven Tier 1 suggestions (compact mode chips)
 * - Pause-triggered Tier 2 suggestions (expanded mode AI responses)
 * - Mode transitions (compact/expanded/hidden)
 * - Auto-collapse on typing during expanded mode
 *
 * @param engine The hybrid engine providing both tier suggestions
 * @param dispatcher Coroutine dispatcher for suggestion queries
 * @param scope Coroutine scope for background work (defaults to SupervisorJob)
 */
class SuggestionBarViewModel(
    private val engine: HybridSuggestionEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    scope: CoroutineScope? = null,
) {
    companion object {
        private const val TAG = "SuggestionBarVM"

        /** Number of suggestion chips in compact mode */
        const val COMPACT_MAX_RESULTS = 3

        /** Number of AI suggestions in expanded mode */
        const val EXPANDED_MAX_RESULTS = 3
    }

    private val scope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(SuggestionBarState())

    /** Observable state for the Compose UI */
    val state: StateFlow<SuggestionBarState> = _state.asStateFlow()

    /** Tracks the active Tier 2 inference job so it can be cancelled on new input */
    private var tier2Job: Job? = null

    /** Tracks the typing pause detection job */
    private var pauseDetectionJob: Job? = null

    /**
     * Called on every keystroke from the IME. Fetches Tier 1 suggestions
     * for compact mode and cancels any in-progress Tier 2 inference.
     *
     * If the bar is in expanded mode, typing auto-collapses it back to compact.
     *
     * @param context Text preceding the cursor
     */
    fun onTextChanged(context: String) {
        // Auto-collapse expanded mode when user starts typing
        if (_state.value.mode == SuggestionBarMode.Expanded) {
            collapse()
        }

        // Cancel any pending Tier 2 work — the context is now stale
        tier2Job?.cancel()
        pauseDetectionJob?.cancel()

        // Fetch Tier 1 (n-gram) suggestions immediately
        scope.launch(dispatcher) {
            try {
                val suggestions = engine.suggest(context, COMPACT_MAX_RESULTS)
                _state.update { it.copy(ngramSuggestions = suggestions) }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 1 suggestion failed", e)
            }
        }

        // Start pause detection for Tier 2 trigger
        if (_state.value.isAiAvailable) {
            scheduleTier2AfterPause(context)
        }
    }

    /**
     * Expands the suggestion bar to show AI (Tier 2) suggestions.
     * Triggers Tier 2 inference if not already loaded.
     *
     * @param context Current text preceding the cursor
     */
    fun expand(context: String) {
        if (_state.value.mode == SuggestionBarMode.Hidden) return

        _state.update {
            it.copy(
                mode = SuggestionBarMode.Expanded,
                isAiLoading = true,
            )
        }

        requestTier2Suggestions(context)
    }

    /**
     * Collapses the suggestion bar back to compact mode.
     * Cancels any in-progress Tier 2 inference.
     */
    fun collapse() {
        tier2Job?.cancel()
        _state.update {
            it.copy(
                mode = SuggestionBarMode.Compact,
                aiSuggestions = emptyList(),
                isAiLoading = false,
            )
        }
    }

    /**
     * Hides the suggestion bar entirely. Used when a password or
     * sensitive input field is detected by the IME.
     */
    fun hide() {
        tier2Job?.cancel()
        pauseDetectionJob?.cancel()
        _state.update {
            SuggestionBarState(mode = SuggestionBarMode.Hidden)
        }
    }

    /**
     * Shows the suggestion bar (transitions from hidden to compact).
     * Called when the user moves away from a password field.
     */
    fun show() {
        if (_state.value.mode == SuggestionBarMode.Hidden) {
            _state.update {
                SuggestionBarState(mode = SuggestionBarMode.Compact)
            }
        }
    }

    /**
     * Updates the contact name shown in the expanded mode header.
     * Pass null to clear the contact context.
     */
    fun setContactName(name: String?) {
        _state.update { it.copy(contactName = name) }
    }

    /**
     * Marks the AI engine as unavailable (e.g., thermal throttle).
     * Hides the AI expand button and prevents Tier 2 triggers.
     */
    fun setAiAvailable(available: Boolean) {
        _state.update { it.copy(isAiAvailable = available) }
        if (!available && _state.value.mode == SuggestionBarMode.Expanded) {
            collapse()
        }
    }

    /**
     * Handles a suggestion chip tap in compact mode.
     * Returns the word text for the IME to commit via InputConnection.
     */
    fun onSuggestionSelected(suggestion: Suggestion): String {
        return suggestion.word
    }

    /**
     * Handles a suggestion tap in expanded mode.
     * Returns the full text for the IME to replace current input.
     * Auto-collapses the bar after selection.
     *
     * @param suggestion The selected AI suggestion
     * @param shouldAutoSend True for tap (replace + close), false for long-press (editable)
     * @return The suggestion text to insert
     */
    fun onAiSuggestionSelected(suggestion: Suggestion, shouldAutoSend: Boolean): String {
        if (shouldAutoSend) {
            collapse()
        }
        return suggestion.word
    }

    /**
     * Schedules Tier 2 inference after a typing pause.
     * If the user types again before the pause threshold, the job is cancelled.
     */
    private fun scheduleTier2AfterPause(context: String) {
        pauseDetectionJob = scope.launch(dispatcher) {
            delay(HybridSuggestionEngine.TYPING_PAUSE_TRIGGER_MS)
            // User paused long enough — trigger Tier 2 loading indicator
            _state.update { it.copy(isAiLoading = true) }
            requestTier2Suggestions(context)
        }
    }

    /**
     * Triggers Tier 2 (LLM) inference via the hybrid engine.
     * Updates the state with AI suggestions when ready.
     */
    private fun requestTier2Suggestions(context: String) {
        tier2Job?.cancel()
        tier2Job = scope.launch(dispatcher) {
            try {
                engine.suggestWithTier2(
                    context = context,
                    maxResults = EXPANDED_MAX_RESULTS,
                    onTier1Ready = { tier1 ->
                        _state.update { it.copy(ngramSuggestions = tier1) }
                    },
                    onTier2Ready = { merged ->
                        _state.update {
                            it.copy(
                                aiSuggestions = merged,
                                isAiLoading = false,
                            )
                        }
                    },
                )
                // If Tier 2 completed without calling onTier2Ready (no results),
                // clear the loading state
                _state.update { it.copy(isAiLoading = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 2 suggestion failed", e)
                _state.update { it.copy(isAiLoading = false) }
            }
        }
    }

    /** Releases resources. Call when the IME service is destroyed. */
    fun close() {
        tier2Job?.cancel()
        pauseDetectionJob?.cancel()
    }
}
