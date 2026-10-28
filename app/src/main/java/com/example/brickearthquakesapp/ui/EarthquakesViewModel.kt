package com.example.brickearthquakesapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brickearthquakesapp.data.EarthquakeRepository
import com.example.brickearthquakesapp.data.RefreshError
import com.example.brickearthquakesapp.location.LocationProvider
import com.example.brickearthquakesapp.location.LocationResult
import com.example.brickearthquakesapp.location.haversineMiles
import com.example.brickearthquakesapp.model.Earthquake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EarthquakesViewModel(
    private val repository: EarthquakeRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private data class RefreshState(
        val isRefreshing: Boolean = false,
        val error: RefreshError? = null,
        /**
         * True once any refresh in this session succeeded. Only then is an empty cache known
         * to mean "USGS returned no events". Deliberately not cleared by [dismissError].
         */
        val hasSucceeded: Boolean = false,
    )

    private val refreshState = MutableStateFlow(RefreshState())
    private val locationState = MutableStateFlow<LocationStatus>(LocationStatus.NotRequested)

    private class CacheSnapshot(
        val earthquakes: List<Earthquake>,
        val distancesMiles: Map<String, Double>,
    )

    /**
     * Distances are recomputed only when the cached events or the location change, not on
     * every refresh-state change and never during composition.
     */
    private val cacheSnapshot = combine(repository.observeEarthquakes(), locationState) { earthquakes, location ->
        val user = (location as? LocationStatus.Available)?.location
        CacheSnapshot(
            earthquakes = earthquakes,
            distancesMiles = if (user == null) {
                emptyMap()
            } else {
                earthquakes.associate { it.id to haversineMiles(user.latitude, user.longitude, it.latitude, it.longitude) }
            },
        )
    }

    /**
     * The cache (Room) is observed independently of the network refresh and of the location
     * lookup, so cached events appear as soon as the database answers, even while a download
     * or a location request is still in flight.
     */
    val uiState: StateFlow<EarthquakesUiState> = combine(
        cacheSnapshot,
        repository.observeLastUpdated(),
        refreshState,
        locationState,
    ) { cache, lastUpdated, refresh, location ->
        EarthquakesUiState(
            earthquakes = cache.earthquakes,
            cacheLoaded = true,
            isRefreshing = refresh.isRefreshing,
            lastUpdatedMillis = lastUpdated,
            refreshError = refresh.error,
            feedConfirmed = refresh.hasSucceeded,
            locationStatus = location,
            distancesMiles = cache.distancesMiles,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the upstream alive across a configuration change but not forever in the background.
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = EarthquakesUiState(),
    )

    private var refreshJob: Job? = null
    private var locationJob: Job? = null

    init {
        refresh()
        // Never asks for the permission. If the user granted it in an earlier session, show
        // distances right away instead of making them tap "Use my location" again.
        if (locationProvider.isPermissionGranted()) refreshLocation()
    }

    /**
     * Starts a download unless one is already running. Safe to call repeatedly.
     *
     * Must be called on the main thread (init and Compose callbacks do). [refreshJob] is a
     * plain var, and the check-then-launch below is only atomic because [viewModelScope] is
     * confined to the main thread. Do not call this from a background thread.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.refresh()
                refreshState.update {
                    it.copy(
                        error = result.exceptionOrNull()?.toRefreshError(),
                        hasSucceeded = it.hasSucceeded || result.isSuccess,
                    )
                }
            } finally {
                // Runs on success, failure and cancellation. A CancellationException still
                // propagates (it is neither an error nor a success); this only makes sure
                // the spinner never stays on.
                refreshState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun dismissError() {
        refreshState.update { it.copy(error = null) }
    }

    /** The UI reports the outcome of the system permission dialog (the ViewModel never shows it). */
    fun onLocationPermissionResult(granted: Boolean, canRequestAgain: Boolean) {
        if (granted) {
            refreshLocation()
        } else {
            locationState.value = LocationStatus.PermissionDenied(canRequestAgain)
        }
    }

    /**
     * One-shot location lookup, ignored while one is already running. Runs in its own
     * coroutine so it never delays the cached list or the network refresh. Same
     * main-thread rule as [refresh].
     */
    fun refreshLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationState.value = LocationStatus.Loading
            try {
                locationState.value = when (val result = locationProvider.currentLocation()) {
                    is LocationResult.Success -> LocationStatus.Available(result.location)
                    // Revoked since the UI last checked; trying again re-runs the permission flow.
                    LocationResult.PermissionMissing -> LocationStatus.PermissionDenied(canRequestAgain = true)
                    LocationResult.ServicesDisabled -> LocationStatus.ServicesDisabled
                    LocationResult.Unavailable -> LocationStatus.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The provider should return Unavailable itself; this keeps a bug there from crashing the app.
                locationState.value = LocationStatus.Unavailable
            } finally {
                // A cancelled lookup is not a failure and says nothing about the user's
                // location: go back to offering "Use my location" rather than a stuck spinner.
                locationState.update { if (it is LocationStatus.Loading) LocationStatus.NotRequested else it }
            }
        }
    }

    private fun Throwable.toRefreshError(): RefreshError =
        this as? RefreshError ?: RefreshError.Unknown(this as? Exception ?: Exception(this))
}
