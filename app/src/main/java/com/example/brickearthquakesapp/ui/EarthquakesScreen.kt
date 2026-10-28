package com.example.brickearthquakesapp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.brickearthquakesapp.R
import com.example.brickearthquakesapp.data.RefreshError
import com.example.brickearthquakesapp.ui.list.EarthquakeList
import com.example.brickearthquakesapp.ui.map.EarthquakeMap
import com.example.brickearthquakesapp.ui.map.rememberWorldCameraPositionState

/**
 * Connects the ViewModel to the stateless [EarthquakesScreen]. Everything that touches the
 * Android permission system lives here, in the UI layer: the ViewModel only hears the outcome.
 */
@Composable
fun EarthquakesRoute(viewModel: EarthquakesViewModel, modifier: Modifier = Modifier) {
    // Stops collecting when the app is in the background, resumes when it returns.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After a denial, "should show rationale" is true if Android will still show the dialog
        // next time, false if the user chose "don't ask again" (the only way out is app settings).
        val canRequestAgain = !granted && context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, LOCATION_PERMISSION)
        } == true
        viewModel.onLocationPermissionResult(granted, canRequestAgain)
    }

    // Coming back from the system settings screen: pick up a permission granted there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.locationStatus is LocationStatus.PermissionDenied && context.hasLocationPermission()) {
            viewModel.refreshLocation()
        }
    }

    EarthquakesScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::dismissError,
        // Only ever called from a button tap, so the dialog is never shown unprompted.
        onUseLocation = {
            if (context.hasLocationPermission()) {
                viewModel.refreshLocation()
            } else {
                permissionLauncher.launch(LOCATION_PERMISSION)
            }
        },
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
        modifier = modifier,
    )
}

// Approximate location only: no fine or background access is requested.
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthquakesScreen(
    state: EarthquakesUiState,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onUseLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Purely presentational, so it lives in the UI rather than the ViewModel. Saved across
    // rotation; the map camera is hoisted here so it also survives switching to the list and back.
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.LIST) }
    val cameraPositionState = rememberWorldCameraPositionState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title)) },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Text(stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.content) {
                EarthquakesUiState.Content.Loading -> LoadingContent()
                EarthquakesUiState.Content.Error -> MessageContent(
                    title = stringResource(R.string.error_title),
                    body = state.refreshError?.let { errorMessage(it) },
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRefresh,
                )
                EarthquakesUiState.Content.Empty -> MessageContent(
                    title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.empty_body),
                    actionLabel = stringResource(R.string.action_refresh),
                    onAction = onRefresh,
                )
                EarthquakesUiState.Content.NoData -> MessageContent(
                    title = stringResource(R.string.no_data_title),
                    body = stringResource(R.string.no_data_body),
                    actionLabel = stringResource(R.string.action_refresh),
                    onAction = onRefresh,
                )
                EarthquakesUiState.Content.Data -> {
                    if (state.isRefreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    ViewModeToggle(selected = viewMode, onSelect = { viewMode = it })
                    val error = state.refreshError
                    if (error != null) {
                        StaleDataBanner(
                            error = error,
                            lastUpdatedMillis = state.lastUpdatedMillis,
                            onRetry = onRefresh,
                            onDismiss = onDismissError,
                        )
                    }
                    // Same cached events either way; switching never touches the network.
                    when (viewMode) {
                        ViewMode.LIST -> {
                            LocationPrompt(
                                status = state.locationStatus,
                                onUseLocation = onUseLocation,
                                onOpenAppSettings = onOpenAppSettings,
                            )
                            EarthquakeList(
                                earthquakes = state.earthquakes,
                                distancesMiles = state.distancesMiles,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ViewMode.MAP -> EarthquakeMap(
                            earthquakes = state.earthquakes,
                            cameraPositionState = cameraPositionState,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private enum class ViewMode(@param:StringRes val label: Int) {
    LIST(R.string.view_list),
    MAP(R.string.view_map),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(selected: ViewMode, onSelect: (ViewMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
            ) {
                Text(stringResource(mode.label))
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MessageContent(
    title: String,
    body: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Shown above the list when a refresh failed but cached events are still being displayed. */
@Composable
private fun StaleDataBanner(
    error: RefreshError,
    lastUpdatedMillis: Long?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reason = errorMessage(error)
    val message = if (lastUpdatedMillis != null) {
        val ago = DateUtils.getRelativeTimeSpanString(
            lastUpdatedMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        )
        stringResource(R.string.stale_banner, reason, ago)
    } else {
        reason
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun errorMessage(error: RefreshError): String = when (error) {
    is RefreshError.Network -> stringResource(R.string.error_network)
    is RefreshError.Http -> stringResource(R.string.error_http, error.code)
    is RefreshError.Parse -> stringResource(R.string.error_parse)
    is RefreshError.Unknown -> stringResource(R.string.error_unknown)
}
