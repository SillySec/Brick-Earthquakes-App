package com.example.brickearthquakesapp.ui

import com.example.brickearthquakesapp.data.RefreshError
import com.example.brickearthquakesapp.location.UserLocation
import com.example.brickearthquakesapp.model.Earthquake

sealed interface LocationStatus {
    /** Nothing asked yet (or a lookup was cancelled). The UI offers "Use my location". */
    data object NotRequested : LocationStatus

    data object Loading : LocationStatus

    data class Available(val location: UserLocation) : LocationStatus

    /** [canRequestAgain] is false once Android will no longer show the permission dialog. */
    data class PermissionDenied(val canRequestAgain: Boolean) : LocationStatus

    data object ServicesDisabled : LocationStatus

    data object Unavailable : LocationStatus
}

data class EarthquakesUiState(
    /** Cached events, newest first. */
    val earthquakes: List<Earthquake> = emptyList(),
    /** False until Room has delivered its first result, so "empty" is never shown prematurely. */
    val cacheLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    /** When the cache was last filled from the network; null if never. */
    val lastUpdatedMillis: Long? = null,
    /** Set when the latest refresh failed. Any cached [earthquakes] are still valid. */
    val refreshError: RefreshError? = null,
    /**
     * A refresh has succeeded since the app started. Without it an empty cache proves nothing:
     * Room is also empty before the first download and after a failed or cancelled one.
     */
    val feedConfirmed: Boolean = false,
    /** Where the optional "distance from me" feature stands. Independent of [content]. */
    val locationStatus: LocationStatus = LocationStatus.NotRequested,
    /** Miles from the user to each event, keyed by event id. Empty unless the location is known. */
    val distancesMiles: Map<String, Double> = emptyMap(),
) {
    /** Null unless the user's location is known; the map milestone reuses this. */
    val userLocation: UserLocation?
        get() = (locationStatus as? LocationStatus.Available)?.location

    /**
     * The current data for a selected event. The UI keeps only the id, so a refresh that
     * changes the event is reflected immediately and one that removes it yields null.
     */
    fun earthquakeById(id: String?): Earthquake? =
        if (id == null) null else earthquakes.firstOrNull { it.id == id }

    /**
     * True when a remembered selection should be dropped because the event is gone. Never true
     * before the cache has loaded: an empty list then means "not loaded yet" (for example
     * right after the process was restored), not "removed".
     */
    fun isSelectionStale(id: String?): Boolean =
        id != null && cacheLoaded && earthquakes.none { it.id == id }

    enum class Content {
        /** Nothing to show yet; waiting for the cache or the first download. */
        Loading,

        /** Show the list (possibly with a refresh progress bar or error banner on top). */
        Data,

        /** No cached data and the download failed. */
        Error,

        /** A download succeeded and the feed contained no events. */
        Empty,

        /** Nothing cached and nothing confirmed, e.g. an error was dismissed. Says nothing about the feed. */
        NoData,
    }

    val content: Content
        get() = when {
            earthquakes.isNotEmpty() -> Content.Data
            !cacheLoaded || isRefreshing -> Content.Loading
            refreshError != null -> Content.Error
            feedConfirmed -> Content.Empty
            else -> Content.NoData
        }
}
