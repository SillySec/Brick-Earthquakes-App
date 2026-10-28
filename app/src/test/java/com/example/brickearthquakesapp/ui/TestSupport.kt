package com.example.brickearthquakesapp.ui

import com.example.brickearthquakesapp.data.EarthquakeRepository
import com.example.brickearthquakesapp.location.LocationProvider
import com.example.brickearthquakesapp.location.LocationResult
import com.example.brickearthquakesapp.model.Earthquake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope runs on Dispatchers.Main, which does not exist in plain JVM tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun earthquake(id: String, timeMillis: Long = 0L) = Earthquake(
    id = id,
    magnitude = 4.0,
    place = "Somewhere",
    timeMillis = timeMillis,
    latitude = 1.0,
    longitude = 2.0,
    depthKm = 3.0,
    url = null,
    tsunami = false,
    felt = null,
    alert = null,
    significance = null,
)

/** Defaults to "permission not granted", i.e. the state of a fresh install. */
class FakeLocationProvider : LocationProvider {
    var permissionGranted = false
    var onLookup: suspend () -> LocationResult = { LocationResult.PermissionMissing }
    var lookups = 0
        private set

    override fun isPermissionGranted() = permissionGranted

    override suspend fun currentLocation(): LocationResult {
        lookups++
        return onLookup()
    }
}

/** uiState uses WhileSubscribed, so something has to collect it for the state to update. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.createViewModel(
    repository: EarthquakeRepository,
    locationProvider: LocationProvider = FakeLocationProvider(),
): EarthquakesViewModel {
    val viewModel = EarthquakesViewModel(repository, locationProvider)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    return viewModel
}

/**
 * The cache is a state flow so the ViewModel sees it immediately, like Room. [onRefresh]
 * decides what each refresh does (suspend, fail, or write new data into the cache).
 */
class FakeEarthquakeRepository(initial: List<Earthquake> = emptyList()) : EarthquakeRepository {
    val cache = MutableStateFlow(initial)
    val lastUpdated = MutableStateFlow<Long?>(null)

    var onRefresh: suspend () -> Result<Unit> = { Result.success(Unit) }
    var refreshCalls = 0
        private set

    override fun observeEarthquakes(): Flow<List<Earthquake>> = cache
    override fun observeLastUpdated(): Flow<Long?> = lastUpdated

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        return onRefresh()
    }
}
