package dev.gwaboard.companion.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gwaboard.companion.R

/**
 * Re-analyze frequency options.
 *
 * Controls how often the companion app re-scans SMS history
 * to update contact profiles.
 */
enum class ReanalyzeFrequency(val label: String) {
    HOURLY("Every hour"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MANUAL("Manual only"),
}

/**
 * Profile detail level options.
 *
 * Controls the depth of analysis performed on SMS conversations.
 * Higher detail levels consume more storage and processing time.
 */
enum class ProfileDetailLevel(val label: String) {
    MINIMAL("Minimal"),
    STANDARD("Standard"),
    DETAILED("Detailed"),
}

/**
 * Settings screen with GDPR-compliant data deletion.
 *
 * Provides controls for:
 * - Re-analyze frequency: how often SMS history is rescanned
 * - Profile detail level: depth of per-contact analysis
 * - Data deletion: permanent removal of all profiles and cached data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    reanalyzeFrequency: ReanalyzeFrequency,
    onReanalyzeFrequencyChanged: (ReanalyzeFrequency) -> Unit,
    profileDetailLevel: ProfileDetailLevel,
    onProfileDetailLevelChanged: (ProfileDetailLevel) -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Re-analyze frequency dropdown
        Text(
            text = stringResource(R.string.settings_reanalyze_frequency),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FrequencyDropdown(
            selected = reanalyzeFrequency,
            onSelected = onReanalyzeFrequencyChanged,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile detail level dropdown
        Text(
            text = stringResource(R.string.settings_profile_detail),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DetailLevelDropdown(
            selected = profileDetailLevel,
            onSelected = onProfileDetailLevelChanged,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // GDPR delete all data
        Text(
            text = stringResource(R.string.settings_delete_data_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(text = stringResource(R.string.settings_delete_data))
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteAllData()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_delete_confirm_title)) },
        text = { Text(text = stringResource(R.string.settings_delete_confirm_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.settings_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyDropdown(
    selected: ReanalyzeFrequency,
    onSelected: (ReanalyzeFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        TextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ReanalyzeFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(frequency.label) },
                    onClick = {
                        onSelected(frequency)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailLevelDropdown(
    selected: ProfileDetailLevel,
    onSelected: (ProfileDetailLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        TextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ProfileDetailLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        onSelected(level)
                        expanded = false
                    },
                )
            }
        }
    }
}
