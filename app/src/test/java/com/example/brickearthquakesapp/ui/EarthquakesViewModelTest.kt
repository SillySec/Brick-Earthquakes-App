package com.example.brickearthquakesapp.ui

import com.example.brickearthquakesapp.data.RefreshError
import com.example.brickearthquakesapp.ui.EarthquakesUiState.Content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EarthquakesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cached = listOf(earthquake("a", 2L), earthquake("b", 1L))
    private val networkError = RefreshError.Network(IOException("offline"))

    private fun TestScope.viewModel(repository: FakeEarthquakeRepository) = createViewModel(repository)

    @Test
    fun showsLoadingWhileCacheIsEmptyAndFirstRefreshIsRunning() = runTest {
        val repository = FakeEarthquakeRepository()
        repository.onRefresh = { CompletableDeferred<Result<Unit>>().await() }

        val state = viewModel(repository).uiState.value

        assertEquals(Content.Loading, state.content)
        assertTrue(state.isRefreshing)
        assertTrue(state.cacheLoaded)
        assertTrue(state.earthquakes.isEmpty())
    }

    @Test
    fun showsCachedEventsImmediatelyWhileRefreshIsStillInFlight() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.onRefresh = { CompletableDeferred<Result<Unit>>().await() }

        val state = viewModel(repository).uiState.value

        assertEquals(Content.Data, state.content)
        assertEquals(cached, state.earthquakes)
        assertTrue("refresh should be running in the background", state.isRefreshing)
    }

    @Test
    fun refreshOnStartIsTriggeredExactlyOnce() = runTest {
        val repository = FakeEarthquakeRepository(cached)

        viewModel(repository)

        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun refreshSuccessShowsNewDataAndClearsRefreshing() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        val gate = CompletableDeferred<Unit>()
        val fresh = listOf(earthquake("new", 9L))
        repository.onRefresh = {
            gate.await()
            repository.cache.value = fresh
            repository.lastUpdated.value = 1234L
            Result.success(Unit)
        }
        val viewModel = viewModel(repository)
        assertTrue(viewModel.uiState.value.isRefreshing)

        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertNull(state.refreshError)
        assertEquals(fresh, state.earthquakes)
        assertEquals(1234L, state.lastUpdatedMillis)
    }

    @Test
    fun refreshFailureKeepsCachedEventsAndExposesTheError() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.lastUpdated.value = 50L
        repository.onRefresh = { Result.failure(networkError) }

        val state = viewModel(repository).uiState.value

        assertEquals(Content.Data, state.content)
        assertEquals("cached events must stay visible", cached, state.earthquakes)
        assertEquals(networkError, state.refreshError)
        assertEquals(50L, state.lastUpdatedMillis)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun refreshFailureWithEmptyCacheShowsErrorContent() = runTest {
        val repository = FakeEarthquakeRepository()
        repository.onRefresh = { Result.failure(networkError) }

        val state = viewModel(repository).uiState.value

        assertEquals(Content.Error, state.content)
        assertEquals(networkError, state.refreshError)
    }

    @Test
    fun successfulRefreshWithEmptyFeedShowsEmptyContent() = runTest {
        val repository = FakeEarthquakeRepository()

        val state = viewModel(repository).uiState.value

        assertEquals(Content.Empty, state.content)
        assertNull(state.refreshError)
    }

    @Test
    fun refreshWhileOneIsRunningDoesNotStartAnother() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.onRefresh = { CompletableDeferred<Result<Unit>>().await() }
        val viewModel = viewModel(repository)

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun canRefreshAgainAfterThePreviousRefreshFinished() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        val viewModel = viewModel(repository)

        viewModel.refresh()

        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun retryClearsPreviousErrorAndSuccessKeepsItCleared() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.onRefresh = { Result.failure(networkError) }
        val viewModel = viewModel(repository)
        assertEquals(networkError, viewModel.uiState.value.refreshError)

        val gate = CompletableDeferred<Unit>()
        repository.onRefresh = { gate.await(); Result.success(Unit) }
        viewModel.refresh()
        assertNull("error should clear as soon as a retry starts", viewModel.uiState.value.refreshError)
        assertTrue(viewModel.uiState.value.isRefreshing)

        gate.complete(Unit)

        assertNull(viewModel.uiState.value.refreshError)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun dismissErrorClearsTheErrorButKeepsData() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.onRefresh = { Result.failure(networkError) }
        val viewModel = viewModel(repository)

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.refreshError)
        assertEquals(cached, viewModel.uiState.value.earthquakes)
    }

    @Test
    fun emptyCacheFailureThenRetryShowsLoadingThenConfirmedEmpty() = runTest {
        val repository = FakeEarthquakeRepository()
        repository.onRefresh = { Result.failure(networkError) }
        val viewModel = viewModel(repository)
        assertEquals(Content.Error, viewModel.uiState.value.content)

        val gate = CompletableDeferred<Unit>()
        repository.onRefresh = { gate.await(); Result.success(Unit) }
        viewModel.refresh()
        assertEquals(Content.Loading, viewModel.uiState.value.content)

        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(Content.Empty, state.content)
        assertTrue(state.feedConfirmed)
    }

    @Test
    fun dismissingTheErrorWithNothingCachedDoesNotClaimTheFeedIsEmpty() = runTest {
        val repository = FakeEarthquakeRepository()
        repository.onRefresh = { Result.failure(networkError) }
        val viewModel = viewModel(repository)
        assertEquals(Content.Error, viewModel.uiState.value.content)

        viewModel.dismissError()

        assertFalse(viewModel.uiState.value.feedConfirmed)
        assertEquals(Content.NoData, viewModel.uiState.value.content)
    }

    @Test
    fun confirmedEmptyFeedThenFailedRefreshShowsError() = runTest {
        val repository = FakeEarthquakeRepository()
        val viewModel = viewModel(repository)
        assertEquals(Content.Empty, viewModel.uiState.value.content)

        repository.onRefresh = { Result.failure(networkError) }
        viewModel.refresh()

        assertEquals(Content.Error, viewModel.uiState.value.content)

        viewModel.dismissError()

        // An earlier success still stands, so this is accurate: at the last check, no events.
        assertEquals(Content.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun cancelledRefreshIsNeitherAFailureNorASuccess() = runTest {
        val repository = FakeEarthquakeRepository()
        repository.onRefresh = { throw CancellationException("cancelled") }

        val viewModel = viewModel(repository)

        // Not turned into a RefreshError, not counted as success, spinner not stuck.
        val state = viewModel.uiState.value
        assertNull(state.refreshError)
        assertFalse(state.feedConfirmed)
        assertFalse(state.isRefreshing)
        assertEquals("must not claim the feed is empty", Content.NoData, state.content)

        // The cancelled job no longer blocks new refreshes.
        repository.onRefresh = { Result.success(Unit) }
        viewModel.refresh()

        assertEquals(2, repository.refreshCalls)
        assertEquals(Content.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun unexpectedNonRefreshErrorIsWrappedAsUnknown() = runTest {
        val repository = FakeEarthquakeRepository(cached)
        repository.onRefresh = { Result.failure(IllegalStateException("boom")) }

        val error = viewModel(repository).uiState.value.refreshError

        assertTrue(error is RefreshError.Unknown)
    }
}
