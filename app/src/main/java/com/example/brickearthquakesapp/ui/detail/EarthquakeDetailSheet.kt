package com.example.brickearthquakesapp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.brickearthquakesapp.R
import com.example.brickearthquakesapp.model.Earthquake
import com.example.brickearthquakesapp.ui.formatCoordinates
import com.example.brickearthquakesapp.ui.formatDepth
import com.example.brickearthquakesapp.ui.formatDistanceMiles
import com.example.brickearthquakesapp.ui.formatEventTime
import com.example.brickearthquakesapp.ui.formatMagnitude

/**
 * Details for one earthquake, shared by the list and the map. Swiping down, pressing back or
 * tapping outside all call [onDismiss]. [distanceMiles] is null when the user's location is
 * unknown, and the distance row is then left out entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthquakeDetailSheet(
    earthquake: Earthquake,
    distanceMiles: Double?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The content is short, so skip the half-open state and size the sheet to its content.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = earthquake.place ?: stringResource(R.string.unknown_location),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            DetailRow(stringResource(R.string.detail_magnitude), formatMagnitude(earthquake.magnitude))
            DetailRow(stringResource(R.string.detail_time), formatEventTime(earthquake.timeMillis))
            DetailRow(stringResource(R.string.detail_depth), formatDepth(earthquake.depthKm))
            DetailRow(
                stringResource(R.string.detail_coordinates),
                formatCoordinates(earthquake.latitude, earthquake.longitude),
            )
            if (distanceMiles != null) {
                DetailRow(stringResource(R.string.detail_distance), formatDistanceMiles(distanceMiles))
            }
        }
    }
}

/** Label above value. Each row is a single accessibility node, read as "Depth, 10.2 km". */
@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
