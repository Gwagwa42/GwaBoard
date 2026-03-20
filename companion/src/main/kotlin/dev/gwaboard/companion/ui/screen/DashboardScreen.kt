package dev.gwaboard.companion.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gwaboard.companion.R

/**
 * Status dashboard — main screen after onboarding.
 *
 * Displays:
 * - Number of contact profiles built from SMS analysis
 * - Last sync timestamp with the keyboard app
 * - Keyboard connection status (via ContentProvider availability)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profilesBuilt: Int,
    lastSync: String,
    isKeyboardConnected: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        StatusCard(
            label = stringResource(R.string.dashboard_profiles_built),
            value = profilesBuilt.toString(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusCard(
            label = stringResource(R.string.dashboard_last_sync),
            value = lastSync,
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusCard(
            label = stringResource(R.string.dashboard_keyboard_status),
            value = if (isKeyboardConnected) {
                stringResource(R.string.dashboard_status_connected)
            } else {
                stringResource(R.string.dashboard_status_disconnected)
            },
            valueColor = if (isKeyboardConnected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun StatusCard(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}
