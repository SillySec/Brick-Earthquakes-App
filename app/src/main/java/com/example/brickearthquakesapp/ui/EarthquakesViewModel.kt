package com.example.brickearthquakesapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brickearthquakesapp.data.EarthquakeRepository
import com.example.brickearthquakesapp.data.RefreshError
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

    /**
     * The cache (Room) is observed independently of the network refresh, so cached events
     * appear as soon as the database answers, even while a download is still in flight.
     */
    val uiState: StateFlow<EarthquakesUiState> = combine(
        repository.observeEarthquakes(),
        repository.observeLastUpdated(),
        refreshState,
    ) { earthquakes, lastUpdated, refresh ->
        EarthquakesUiState(
            earthquakes = earthquakes,
            cacheLoaded = true,
            isRefreshing = refresh.isRefreshing,
            lastUpdatedMillis = lastUpdated,
            refreshError = refresh.error,
            feedConfirmed = refresh.hasSucceeded,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the upstream alive across a configuration change but not forever in the background.
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = EarthquakesUiState(),
    )

    private var refreshJob: Job? = null

    init {
        refresh()
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

    private fun Throwable.toRefreshError(): RefreshError =
        this as? RefreshError ?: RefreshError.Unknown(this as? Exception ?: Exception(this))
}
