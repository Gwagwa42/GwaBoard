package dev.gwaboard.keyboard.ui

import dev.gwaboard.keyboard.engine.Suggestion

/**
 * Represents the visual mode of the suggestion bar.
 *
 * The bar transitions between [Compact] and [Expanded] states.
 * [Hidden] is used for password fields where no suggestions should appear.
 */
enum class SuggestionBarMode {
    /** Standard mode: 3 n-gram word chips + AI expand button */
    Compact,
    /** Full AI panel: header with contact name + 3 LLM response suggestions */
    Expanded,
    /** Password/sensitive field detected — bar is invisible */
    Hidden,
}

/**
 * Immutable state snapshot for the [SuggestionBar] composable.
 *
 * Produced by [SuggestionBarViewModel] and consumed by the Compose UI.
 * All state transitions go through the ViewModel to maintain a clean
 * unidirectional data flow.
 *
 * @property mode Current visual mode (compact, expanded, or hidden)
 * @property ngramSuggestions Tier 1 word completions for compact mode chips
 * @property aiSuggestions Tier 2 LLM response suggestions for expanded mode
 * @property contactName Display name of the current contact (shown in expanded header)
 * @property isAiLoading Whether Tier 2 inference is in progress
 * @property isAiAvailable Whether Tier 2 engine can be triggered (false during thermal throttle)
 */
data class SuggestionBarState(
    val mode: SuggestionBarMode = SuggestionBarMode.Compact,
    val ngramSuggestions: List<Suggestion> = emptyList(),
    val aiSuggestions: List<Suggestion> = emptyList(),
    val contactName: String? = null,
    val isAiLoading: Boolean = false,
    val isAiAvailable: Boolean = true,
)
