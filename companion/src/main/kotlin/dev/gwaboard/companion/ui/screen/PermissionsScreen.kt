package dev.gwaboard.companion.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gwaboard.companion.R

/**
 * Permission request screen — explains each required permission
 * and allows the user to grant or skip.
 *
 * Permissions requested:
 * - READ_SMS: analyze message patterns for contact profiles
 * - RECEIVE_SMS: real-time context updates on incoming messages
 * - SEND_SMS: required when acting as default SMS handler
 * - READ_CONTACTS: personalize suggestions with contact names
 */
@Composable
fun PermissionsScreen(
    onGrantPermissions: () -> Unit,
    onSkip: () -> Unit,
    permissionsGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.permissions_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission list items
        PermissionItem(
            text = stringResource(R.string.permission_read_sms),
            granted = permissionsGranted,
        )
        PermissionItem(
            text = stringResource(R.string.permission_receive_sms),
            granted = permissionsGranted,
        )
        PermissionItem(
            text = stringResource(R.string.permission_send_sms),
            granted = permissionsGranted,
        )
        PermissionItem(
            text = stringResource(R.string.permission_read_contacts),
            granted = permissionsGranted,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGrantPermissions,
            modifier = Modifier.fillMaxWidth(),
            enabled = !permissionsGranted,
        ) {
            Text(text = stringResource(R.string.permissions_grant))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.permissions_skip))
        }
    }
}

@Composable
private fun PermissionItem(
    text: String,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
