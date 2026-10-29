package com.example.brickearthquakesapp.ui.map

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.brickearthquakesapp.R
import com.example.brickearthquakesapp.model.Earthquake
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

/**
 * The USGS feed is worldwide, so the map opens on a whole-world view instead of trying to
 * fit every marker. Hoist this above the list/map switch: the saved camera then survives
 * toggling and rotation, and later data refreshes never move it.
 */
@Composable
fun rememberWorldCameraPositionState(): CameraPositionState =
    rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 1.5f)
    }

private class MarkerData(
    val id: String,
    val position: LatLng,
    val title: String,
)

/**
 * Plots the same cached [earthquakes] the list shows. Independent of the user's location:
 * no my-location layer is enabled and no location permission is involved.
 */
@Composable
fun EarthquakeMap(
    earthquakes: List<Earthquake>,
    cameraPositionState: CameraPositionState,
    onEarthquakeClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Without a key the Maps SDK only shows a blank grid, so say so instead of creating it.
    val hasApiKey = remember { context.hasMapsApiKey() }
    if (!hasApiKey) {
        MapUnavailable(modifier)
        return
    }

    // Built once per data change rather than on every recomposition. Earthquake stores
    // latitude and longitude separately (the GeoJSON [lon, lat, depth] order is handled in
    // the mapper), and LatLng takes latitude first.
    val markers = remember(earthquakes) {
        earthquakes.map {
            MarkerData(
                id = it.id,
                position = LatLng(it.latitude, it.longitude),
                title = it.place ?: context.getString(R.string.unknown_location),
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
    ) {
        for (marker in markers) {
            key(marker.id) {
                Marker(
                    state = rememberUpdatedMarkerState(position = marker.position),
                    title = marker.title, // kept for accessibility; the info window is not shown
                    onClick = {
                        onEarthquakeClick(marker.id)
                        // true = handled: the SDK then skips its default behavior of showing the
                        // info window and animating the camera to the marker, so the camera
                        // stays exactly where the user left it.
                        true
                    },
                )
            }
        }
    }
}

@Composable
private fun MapUnavailable(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.map_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** True if a non-blank Maps key was injected into the manifest at build time. */
@Suppress("DEPRECATION") // the flags-object overload needs API 33; minSdk is 31
private fun Context.hasMapsApiKey(): Boolean {
    val metaData = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
    return !metaData?.getString("com.google.android.geo.API_KEY").isNullOrBlank()
}
