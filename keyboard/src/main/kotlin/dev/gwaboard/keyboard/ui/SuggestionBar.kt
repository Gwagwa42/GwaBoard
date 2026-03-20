package dev.gwaboard.keyboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gwaboard.keyboard.engine.Suggestion

/**
 * Expandable suggestion bar for the GwaBoard keyboard.
 *
 * Two visual states:
 * - **Compact**: horizontal row of 3 n-gram word chips + AI expand button
 * - **Expanded**: full AI panel with header, 3 LLM response suggestions
 *
 * Gestures:
 * - Swipe up on compact bar -> expand to AI panel
 * - Swipe down on expanded panel / tap close button -> collapse
 * - Tap chip in compact mode -> commit word via InputConnection
 * - Tap suggestion in expanded mode -> replace text + close
 * - Long-press suggestion in expanded mode -> insert as editable text
 *
 * The bar is fully hidden when a password field is detected.
 *
 * @param viewModel The [SuggestionBarViewModel] providing state and handling actions
 * @param currentContext Current text before cursor (for triggering expand)
 * @param onCommitText Callback to commit text via InputConnection
 * @param onReplaceText Callback to replace current text with AI suggestion
 * @param modifier Optional [Modifier] for the root container
 */
@Composable
fun SuggestionBar(
    viewModel: SuggestionBarViewModel,
    currentContext: String,
    onCommitText: (String) -> Unit,
    onReplaceText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Hidden mode: render nothing for password fields
    if (state.mode == SuggestionBarMode.Hidden) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        when (state.mode) {
            SuggestionBarMode.Compact -> CompactBar(
                suggestions = state.ngramSuggestions,
                isAiLoading = state.isAiLoading,
                isAiAvailable = state.isAiAvailable,
                onSuggestionTap = { suggestion ->
                    val text = viewModel.onSuggestionSelected(suggestion)
                    onCommitText(text)
                },
                onExpandTap = { viewModel.expand(currentContext) },
                onSwipeUp = { viewModel.expand(currentContext) },
            )
            SuggestionBarMode.Expanded -> ExpandedPanel(
                aiSuggestions = state.aiSuggestions,
                contactName = state.contactName,
                isLoading = state.isAiLoading,
                onSuggestionTap = { suggestion ->
                    val text = viewModel.onAiSuggestionSelected(suggestion, shouldAutoSend = true)
                    onReplaceText(text)
                },
                onSuggestionLongPress = { suggestion ->
                    val text = viewModel.onAiSuggestionSelected(suggestion, shouldAutoSend = false)
                    onReplaceText(text)
                },
                onCollapse = { viewModel.collapse() },
                onSwipeDown = { viewModel.collapse() },
            )
            SuggestionBarMode.Hidden -> { /* Already handled above */ }
        }
    }
}

/**
 * Compact mode: horizontal row of word suggestion chips with an optional
 * AI expand button on the right side.
 */
@Composable
internal fun CompactBar(
    suggestions: List<Suggestion>,
    isAiLoading: Boolean,
    isAiAvailable: Boolean,
    onSuggestionTap: (Suggestion) -> Unit,
    onExpandTap: () -> Unit,
    onSwipeUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(COMPACT_BAR_HEIGHT)
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Negative dragAmount = swipe up
                    if (dragAmount < -SWIPE_THRESHOLD) {
                        onSwipeUp()
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Suggestion chips with stagger animation
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = CHIP_FADE_DURATION_MS,
                            delayMillis = index * CHIP_STAGGER_DELAY_MS,
                        )
                    ),
                ) {
                    SuggestionChip(
                        text = suggestion.word,
                        onClick = { onSuggestionTap(suggestion) },
                    )
                }
            }
        }

        // AI expand button (hidden during thermal throttle)
        if (isAiAvailable) {
            Spacer(modifier = Modifier.width(4.dp))
            if (isAiLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButton(
                    onClick = onExpandTap,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text(
                        text = EXPAND_ICON,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Expanded AI panel showing full response suggestions from the LLM.
 * Includes a header with contact name and a close button.
 */
@Composable
internal fun ExpandedPanel(
    aiSuggestions: List<Suggestion>,
    contactName: String?,
    isLoading: Boolean,
    onSuggestionTap: (Suggestion) -> Unit,
    onSuggestionLongPress: (Suggestion) -> Unit,
    onCollapse: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Positive dragAmount = swipe down
                    if (dragAmount > SWIPE_THRESHOLD) {
                        onSwipeDown()
                    }
                }
            },
    ) {
        // Header row: "AI Suggestions (ContactName)" + close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildHeaderText(contactName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    text = COLLAPSE_ICON,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // AI suggestion items with stagger animation
        aiSuggestions.forEachIndexed { index, suggestion ->
            AnimatedVisibility(
                visible = !isLoading,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(
                        durationMillis = SUGGESTION_SLIDE_DURATION_MS,
                        delayMillis = index * SUGGESTION_STAGGER_DELAY_MS,
                    ),
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = SUGGESTION_SLIDE_DURATION_MS,
                        delayMillis = index * SUGGESTION_STAGGER_DELAY_MS,
                    ),
                ),
                exit = slideOutVertically() + fadeOut(),
            ) {
                AiSuggestionItem(
                    text = suggestion.word,
                    onClick = { onSuggestionTap(suggestion) },
                    onLongClick = { onSuggestionLongPress(suggestion) },
                )
            }
        }
    }
}

/**
 * Individual word chip for compact mode. Material 3 styled with rounded corners.
 */
@Composable
internal fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Individual AI suggestion row for expanded mode.
 * Supports both tap (auto-send) and long-press (editable insert).
 */
@Composable
internal fun AiSuggestionItem(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Builds the header text for expanded mode.
 * Shows "AI Suggestions" or "AI Suggestions (ContactName)" if a contact is known.
 */
internal fun buildHeaderText(contactName: String?): String =
    if (contactName != null) "AI Suggestions ($contactName)" else "AI Suggestions"

// -- Layout constants --

/** Height of the compact suggestion bar */
private val COMPACT_BAR_HEIGHT = 48.dp

/** Minimum drag distance (px) to trigger swipe expand/collapse */
private const val SWIPE_THRESHOLD = 50f

/** Unicode arrows used as expand/collapse icons (avoids icon dependency) */
private const val EXPAND_ICON = "\u2191"   // ↑
private const val COLLAPSE_ICON = "\u2193" // ↓

// -- Animation constants --

/** Duration for individual chip fade-in */
private const val CHIP_FADE_DURATION_MS = 150

/** Stagger delay between consecutive chip animations */
private const val CHIP_STAGGER_DELAY_MS = 50

/** Duration for AI suggestion slide-in */
private const val SUGGESTION_SLIDE_DURATION_MS = 200

/** Stagger delay between consecutive AI suggestion animations */
private const val SUGGESTION_STAGGER_DELAY_MS = 75
