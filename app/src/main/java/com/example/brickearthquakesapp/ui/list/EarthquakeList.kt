package com.example.brickearthquakesapp.ui.list

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.brickearthquakesapp.R
import com.example.brickearthquakesapp.model.Earthquake
import com.example.brickearthquakesapp.ui.formatDepth
import com.example.brickearthquakesapp.ui.formatDistanceMiles
import com.example.brickearthquakesapp.ui.formatEventTime
import com.example.brickearthquakesapp.ui.formatMagnitude

@Composable
fun EarthquakeList(
    earthquakes: List<Earthquake>,
    distancesMiles: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        // The USGS id is stable across refreshes, so rows keep their identity when data changes.
        items(items = earthquakes, key = { it.id }) { earthquake ->
            EarthquakeRow(earthquake, distanceMiles = distancesMiles[earthquake.id])
            HorizontalDivider()
        }
    }
}

@Composable
private fun EarthquakeRow(earthquake: Earthquake, distanceMiles: Double?, modifier: Modifier = Modifier) {
    val magnitude = formatMagnitude(earthquake.magnitude)
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        earthquake.timeMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MagnitudeBadge(magnitude = earthquake.magnitude)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = earthquake.place ?: stringResource(R.string.unknown_location),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$relativeTime · ${formatEventTime(earthquake.timeMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Distance only when the user's location is known; otherwise the row is unchanged.
            val details = listOfNotNull(
                distanceMiles?.let { stringResource(R.string.distance_away, formatDistanceMiles(it)) },
                stringResource(R.string.depth_label, formatDepth(earthquake.depthKm)),
            ).joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MagnitudeBadge(magnitude: Double?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when {
        magnitude == null -> colors.surfaceVariant to colors.onSurfaceVariant
        magnitude >= 5.5 -> colors.errorContainer to colors.onErrorContainer
        magnitude >= 4.0 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val text = formatMagnitude(magnitude)
    val description = stringResource(R.string.magnitude_label, text)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}
