package com.example.brickearthquakesapp.ui

import com.example.brickearthquakesapp.location.LocationResult
import com.example.brickearthquakesapp.location.UserLocation
import com.example.brickearthquakesapp.ui.EarthquakesUiState.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EarthquakesViewModelLocationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Events at the equator, so distances are easy to reason about.
    private val here = earthquake("here").copy(latitude = 0.0, longitude = 0.0)
    private val oneDegreeEast = earthquake("east").copy(latitude = 0.0, longitude = 1.0)
    private val cached = listOf(here, oneDegreeEast)
    private val user = UserLocation(latitude = 0.0, longitude = 0.0)

    private val repository = FakeEarthquakeRepository(cached)
    private val location = FakeLocationProvider()

    @Test
    fun nothingIsLookedUpAtLaunchWithoutPermission() = runTest {
        val viewModel = createViewModel(repository, location)

        val state = viewModel.uiState.value

        assertEquals(0, location.lookups)
        assertEquals(LocationStatus.NotRequested, state.locationStatus)
        assertNull(state.userLocation)
        assertTrue(state.distancesMiles.isEmpty())
        assertEquals(Content.Data, state.content)
    }

    @Test
    fun permissionGrantedInAnEarlierSessionLooksUpLocationAtLaunch() = runTest {
        location.permissionGranted = true
        location.onLookup = { LocationResult.Success(user) }

        val state = createViewModel(repository, location).uiState.value

        assertEquals(1, location.lookups)
        assertEquals(LocationStatus.Available(user), state.locationStatus)
        assertEquals(user, state.userLocation)
    }

    @Test
    fun grantingPermissionLooksUpLocationAndProvidesDistances() = runTest {
        location.onLookup = { LocationResult.Success(user) }
        val viewModel = createViewModel(repository, location)

        viewModel.onLocationPermissionResult(granted = true, canRequestAgain = false)

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.Available(user), state.locationStatus)
        assertEquals(0.0, state.distancesMiles.getValue("here"), 1e-9)
        assertEquals(69.09, state.distancesMiles.getValue("east"), 0.05) // 1 degree of longitude
        assertEquals(setOf("here", "east"), state.distancesMiles.keys)
    }

    @Test
    fun distancesAreOmittedUntilTheLocationIsKnown() = runTest {
        val viewModel = createViewModel(repository, location)

        assertTrue(viewModel.uiState.value.distancesMiles.isEmpty())
    }

    @Test
    fun distancesFollowTheCacheWhenARefreshReplacesTheEvents() = runTest {
        location.permissionGranted = true
        location.onLookup = { LocationResult.Success(user) }
        val viewModel = createViewModel(repository, location)

        repository.cache.value = listOf(earthquake("fresh").copy(latitude = 0.0, longitude = 2.0))

        val distances = viewModel.uiState.value.distancesMiles
        assertEquals(setOf("fresh"), distances.keys)
        assertEquals(138.19, distances.getValue("fresh"), 0.1)
    }

    @Test
    fun deniedPermissionHidesDistancesButKeepsTheList() = runTest {
        val viewModel = createViewModel(repository, location)

        viewModel.onLocationPermissionResult(granted = false, canRequestAgain = true)

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.PermissionDenied(canRequestAgain = true), state.locationStatus)
        assertTrue(state.distancesMiles.isEmpty())
        assertEquals(cached, state.earthquakes)
        assertEquals(Content.Data, state.content)
        assertEquals("a denial must not trigger a lookup", 0, location.lookups)
    }

    @Test
    fun permanentDenialIsReportedSoTheUiCanOfferSettings() = runTest {
        val viewModel = createViewModel(repository, location)

        viewModel.onLocationPermissionResult(granted = false, canRequestAgain = false)

        assertEquals(LocationStatus.PermissionDenied(canRequestAgain = false), viewModel.uiState.value.locationStatus)
    }

    @Test
    fun permissionRevokedBeforeTheLookupCountsAsDeniedAndRetryable() = runTest {
        location.onLookup = { LocationResult.PermissionMissing }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        assertEquals(LocationStatus.PermissionDenied(canRequestAgain = true), viewModel.uiState.value.locationStatus)
    }

    @Test
    fun disabledLocationServicesAreReportedWithoutHidingTheList() = runTest {
        location.onLookup = { LocationResult.ServicesDisabled }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.ServicesDisabled, state.locationStatus)
        assertEquals(Content.Data, state.content)
        assertTrue(state.distancesMiles.isEmpty())
    }

    @Test
    fun unavailableLocationIsReportedWithoutHidingTheList() = runTest {
        location.onLookup = { LocationResult.Unavailable }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.Unavailable, state.locationStatus)
        assertEquals(Content.Data, state.content)
        assertNull(state.refreshError)
    }

    @Test
    fun lookupFailureBecomesUnavailableInsteadOfCrashing() = runTest {
        location.onLookup = { throw IllegalStateException("play services exploded") }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        assertEquals(LocationStatus.Unavailable, viewModel.uiState.value.locationStatus)
        assertEquals(cached, viewModel.uiState.value.earthquakes)
    }

    @Test
    fun retryAfterAFailedLookupCanSucceed() = runTest {
        location.onLookup = { LocationResult.Unavailable }
        val viewModel = createViewModel(repository, location)
        viewModel.refreshLocation()
        assertEquals(LocationStatus.Unavailable, viewModel.uiState.value.locationStatus)

        location.onLookup = { LocationResult.Success(user) }
        viewModel.refreshLocation()

        assertEquals(LocationStatus.Available(user), viewModel.uiState.value.locationStatus)
    }

    @Test
    fun cancelledLookupIsNeitherAFailureNorAFix() = runTest {
        location.onLookup = { throw CancellationException("cancelled") }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        val state = viewModel.uiState.value
        // Not stuck on Loading, not reported as a failure, no distances invented.
        assertEquals(LocationStatus.NotRequested, state.locationStatus)
        assertTrue(state.distancesMiles.isEmpty())

        // The cancelled job does not block a later attempt.
        location.onLookup = { LocationResult.Success(user) }
        viewModel.refreshLocation()
        assertEquals(LocationStatus.Available(user), viewModel.uiState.value.locationStatus)
    }

    @Test
    fun slowLookupDoesNotBlockTheCacheOrTheNetworkRefresh() = runTest {
        val gate = CompletableDeferred<LocationResult>()
        location.permissionGranted = true
        location.onLookup = { gate.await() }
        val refreshGate = CompletableDeferred<Unit>()
        repository.onRefresh = { refreshGate.await(); Result.success(Unit) }

        val viewModel = createViewModel(repository, location)

        // Lookup still pending: the list is already on screen and the refresh is running.
        var state = viewModel.uiState.value
        assertEquals(LocationStatus.Loading, state.locationStatus)
        assertEquals(Content.Data, state.content)
        assertEquals(cached, state.earthquakes)
        assertTrue(state.isRefreshing)

        refreshGate.complete(Unit)
        state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals("location is still pending", LocationStatus.Loading, state.locationStatus)

        gate.complete(LocationResult.Success(user))
        assertEquals(LocationStatus.Available(user), viewModel.uiState.value.locationStatus)
    }

    @Test
    fun secondLookupWhileOneIsRunningIsIgnored() = runTest {
        location.onLookup = { CompletableDeferred<LocationResult>().await() }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()
        viewModel.refreshLocation()

        assertEquals(1, location.lookups)
    }

    @Test
    fun locationFailureNeverTouchesTheRefreshState() = runTest {
        location.onLookup = { LocationResult.Unavailable }
        val viewModel = createViewModel(repository, location)

        viewModel.refreshLocation()

        val state = viewModel.uiState.value
        assertNull(state.refreshError)
        assertFalse(state.isRefreshing)
        assertNotNull(state.earthquakes)
        assertEquals(1, repository.refreshCalls)
    }
}
