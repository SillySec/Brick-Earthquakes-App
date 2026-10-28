package com.example.brickearthquakesapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.brickearthquakesapp.R

/**
 * Slim row above the list that explains the benefit of sharing location and offers the
 * action. Shows nothing once the location is known, so it never competes with the list.
 */
@Composable
fun LocationPrompt(
    status: LocationStatus,
    onUseLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompt = when (status) {
        LocationStatus.NotRequested ->
            Prompt(R.string.location_prompt_body, R.string.location_action_use, onUseLocation)
        LocationStatus.Loading ->
            Prompt(R.string.location_loading)
        is LocationStatus.Available -> null
        is LocationStatus.PermissionDenied ->
            if (status.canRequestAgain) {
                Prompt(R.string.location_denied, R.string.action_retry, onUseLocation)
            } else {
                Prompt(R.string.location_denied_settings, R.string.location_action_settings, onOpenAppSettings)
            }
        LocationStatus.ServicesDisabled ->
            Prompt(R.string.location_services_disabled, R.string.action_retry, onUseLocation)
        LocationStatus.Unavailable ->
            Prompt(R.string.location_unavailable, R.string.action_retry, onUseLocation)
    } ?: return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(prompt.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (prompt.actionLabel != null && prompt.onAction != null) {
                TextButton(onClick = prompt.onAction) { Text(stringResource(prompt.actionLabel)) }
            }
        }
    }
}

private class Prompt(
    @param:StringRes val message: Int,
    @param:StringRes val actionLabel: Int? = null,
    val onAction: (() -> Unit)? = null,
)
