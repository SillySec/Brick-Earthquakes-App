package com.example.brickearthquakesapp.ui

import com.example.brickearthquakesapp.data.RefreshError
import com.example.brickearthquakesapp.ui.EarthquakesUiState.Content
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class EarthquakesUiStateTest {

    private val error = RefreshError.Network(IOException("offline"))
    private val data = listOf(earthquake("a"))

    @Test
    fun cachedEventsAlwaysWin() {
        // Even mid-refresh or with an error, cached events are shown.
        assertEquals(Content.Data, EarthquakesUiState(data, cacheLoaded = true).content)
        assertEquals(Content.Data, EarthquakesUiState(data, cacheLoaded = true, isRefreshing = true).content)
        assertEquals(Content.Data, EarthquakesUiState(data, cacheLoaded = true, refreshError = error).content)
    }

    @Test
    fun loadingUntilTheCacheHasAnswered() {
        assertEquals(Content.Loading, EarthquakesUiState(cacheLoaded = false).content)
        // Even a confirmed feed or an error must not show before Room has reported.
        assertEquals(Content.Loading, EarthquakesUiState(cacheLoaded = false, feedConfirmed = true).content)
        assertEquals(Content.Loading, EarthquakesUiState(cacheLoaded = false, refreshError = error).content)
    }

    @Test
    fun loadingWhileRefreshingWithEmptyCache() {
        assertEquals(Content.Loading, EarthquakesUiState(cacheLoaded = true, isRefreshing = true).content)
        // A retry after a failure shows the spinner rather than the old error.
        assertEquals(
            Content.Loading,
            EarthquakesUiState(cacheLoaded = true, isRefreshing = true, refreshError = error).content,
        )
    }

    @Test
    fun errorWhenRefreshFailedAndNothingIsCached() {
        assertEquals(Content.Error, EarthquakesUiState(cacheLoaded = true, refreshError = error).content)
        // The error also wins over an earlier success: the latest outcome is what matters.
        assertEquals(
            Content.Error,
            EarthquakesUiState(cacheLoaded = true, refreshError = error, feedConfirmed = true).content,
        )
    }

    @Test
    fun emptyOnlyWhenASuccessfulRefreshConfirmedIt() {
        assertEquals(Content.Empty, EarthquakesUiState(cacheLoaded = true, feedConfirmed = true).content)
    }

    @Test
    fun noDataWhenNothingIsCachedAndNothingWasConfirmed() {
        assertEquals(Content.NoData, EarthquakesUiState(cacheLoaded = true).content)
    }
}
