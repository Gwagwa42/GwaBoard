package dev.gwaboard.keyboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gwaboard.keyboard.R

/**
 * Data class representing the privacy dashboard state.
 *
 * Provides an overview of what data the keyboard stores locally,
 * giving the user full visibility into on-device storage.
 */
data class PrivacyDashboardState(
    /** Number of user-learned n-gram entries in the database */
    val learnedWordCount: Int = 0,
    /** Size of the n-gram database file in bytes */
    val ngramDatabaseSizeBytes: Long = 0L,
    /** Whether AI (Tier 2) suggestions are currently enabled */
    val isAiEnabled: Boolean = true,
    /** Whether the Tier 2 LLM model is currently loaded in memory */
    val isLlmLoaded: Boolean = false,
    /** Whether the companion app is installed and connected */
    val isCompanionConnected: Boolean = false,
)

/**
 * Privacy dashboard screen for the keyboard settings.
 *
 * Shows the user exactly what data the keyboard stores:
 * - Learned words count and storage size
 * - AI engine status and toggle
 * - Companion app connection status
 *
 * Provides controls for:
 * - Clearing all learned words (n-gram reset)
 * - Toggling AI suggestions on/off
 *
 * This screen fulfills the GDPR transparency requirement by making
 * all stored data visible and controllable by the user.
 */
@Composable
fun PrivacyDashboardScreen(
    state: PrivacyDashboardState,
    onClearLearnedWords: () -> Unit,
    onAiToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.privacy_dashboard_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Data Inventory Section ──────────────────────────────────────

        SectionHeader(text = stringResource(R.string.privacy_section_data_stored))

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DataRow(
                    label = stringResource(R.string.privacy_learned_words),
                    value = state.learnedWordCount.toString(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataRow(
                    label = stringResource(R.string.privacy_storage_used),
                    value = formatBytes(state.ngramDatabaseSizeBytes),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataRow(
                    label = stringResource(R.string.privacy_companion_status),
                    value = if (state.isCompanionConnected) {
                        stringResource(R.string.privacy_companion_connected)
                    } else {
                        stringResource(R.string.privacy_companion_not_connected)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.privacy_data_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── AI Controls Section ─────────────────────────────────────────

        SectionHeader(text = stringResource(R.string.privacy_section_ai_controls))

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.privacy_ai_suggestions),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.privacy_ai_suggestions_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = state.isAiEnabled,
                        onCheckedChange = onAiToggled,
                    )
                }

                if (state.isLlmLoaded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.privacy_llm_loaded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Data Deletion Section ───────────────────────────────────────

        SectionHeader(text = stringResource(R.string.privacy_section_data_deletion))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.privacy_clear_words_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(text = stringResource(R.string.privacy_clear_learned_words))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Privacy Policy Section ──────────────────────────────────────

        SectionHeader(text = stringResource(R.string.privacy_section_about))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.privacy_policy_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showClearDialog) {
        ClearWordsConfirmationDialog(
            onConfirm = {
                showClearDialog = false
                onClearLearnedWords()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClearWordsConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.privacy_clear_confirm_title)) },
        text = { Text(text = stringResource(R.string.privacy_clear_confirm_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.privacy_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.privacy_cancel))
            }
        },
    )
}

/**
 * Formats a byte count into a human-readable string (B, KB, MB).
 */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
