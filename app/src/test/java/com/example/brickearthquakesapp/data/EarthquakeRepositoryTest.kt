package com.example.brickearthquakesapp.data

import com.example.brickearthquakesapp.data.remote.FeatureCollectionDto
import com.example.brickearthquakesapp.data.remote.UsgsJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class EarthquakeRepositoryTest {

    private val cached = listOf(
        entity("old-1", timeMillis = 1_000L, fetchedAtMillis = 50L),
        entity("old-2", timeMillis = 2_000L, fetchedAtMillis = 50L),
    )
    private val api = FakeUsgsApi()
    private val dao = FakeEarthquakeDao(initial = cached)
    private val repository = DefaultEarthquakeRepository(api, dao, now = { 777L })

    private fun assertCachePreserved() {
        assertEquals(cached, dao.current)
        assertEquals(0, dao.replaceAllCalls)
    }

    private fun exceptionOf(result: Result<Unit>): Throwable {
        assertTrue("expected failure but was success", result.isFailure)
        return result.exceptionOrNull()!!
    }

    @Test
    fun successReplacesCacheAndExposesItNewestFirst() = runTest {
        api.behavior = {
            parseFeed(
                feedJson(
                    featureJson(id = "a", time = "100"),
                    featureJson(id = "b", time = "300"),
                    featureJson(id = "c", time = "200"),
                ),
            )
        }

        val result = repository.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("b", "c", "a"), repository.observeEarthquakes().first().map { it.id })
        assertEquals(1, dao.replaceAllCalls)
    }

    @Test
    fun successRemovesEventsNoLongerInTheFeed() = runTest {
        api.behavior = { parseFeed(feedJson(featureJson(id = "new"))) }

        repository.refresh()

        assertEquals(listOf("new"), repository.observeEarthquakes().first().map { it.id })
    }

    @Test
    fun successRecordsFetchTimeFromInjectedClock() = runTest {
        assertEquals(50L, repository.observeLastUpdated().first())
        api.behavior = { parseFeed(feedJson(featureJson())) }

        repository.refresh()

        assertEquals(777L, repository.observeLastUpdated().first())
    }

    @Test
    fun malformedFeaturesDoNotFailRefresh() = runTest {
        api.behavior = { parseFeed(feedJson("42", featureJson(id = "good"))) }

        val result = repository.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("good"), repository.observeEarthquakes().first().map { it.id })
    }

    @Test
    fun networkFailurePreservesCache() = runTest {
        api.behavior = { throw SocketTimeoutException("timeout") }

        val error = exceptionOf(repository.refresh())

        assertTrue(error is RefreshError.Network)
        assertCachePreserved()
    }

    @Test
    fun httpFailurePreservesCacheAndReportsStatusCode() = runTest {
        api.behavior = {
            throw HttpException(Response.error<FeatureCollectionDto>(503, "".toResponseBody(null)))
        }

        val error = exceptionOf(repository.refresh())

        assertTrue(error is RefreshError.Http)
        assertEquals(503, (error as RefreshError.Http).code)
        assertCachePreserved()
    }

    @Test
    fun unparseableResponsePreservesCache() = runTest {
        // What the Retrofit converter would throw for a non-GeoJSON body.
        api.behavior = { UsgsJson.decodeFromString(FeatureCollectionDto.serializer(), "<html>oops</html>") }

        val error = exceptionOf(repository.refresh())

        assertTrue(error is RefreshError.Parse)
        assertCachePreserved()
    }

    @Test
    fun feedWithOnlyUnusableFeaturesPreservesCache() = runTest {
        api.behavior = { parseFeed(feedJson("42", """{"id":"x"}""")) }

        val error = exceptionOf(repository.refresh())

        assertTrue(error is RefreshError.Parse)
        assertCachePreserved()
    }

    @Test
    fun unexpectedExceptionIsReportedNotThrown() = runTest {
        api.behavior = { throw IllegalStateException("boom") }

        val error = exceptionOf(repository.refresh())

        assertTrue(error is RefreshError.Unknown)
        assertCachePreserved()
    }

    @Test
    fun cancellationExceptionIsRethrownNotWrapped() = runTest {
        api.behavior = { throw CancellationException("cancelled") }

        var thrown: CancellationException? = null
        try {
            repository.refresh()
        } catch (e: CancellationException) {
            thrown = e
        }

        assertNotNull("CancellationException must propagate out of refresh()", thrown)
        assertCachePreserved()
    }

    @Test
    fun cancellingTheCallerAbortsRefreshAndKeepsCache() = runTest {
        val gate = CompletableDeferred<FeatureCollectionDto>()
        api.behavior = { gate.await() }
        var result: Result<Unit>? = null

        val job = launch { result = repository.refresh() }
        advanceUntilIdle()
        assertEquals(1, api.calls)
        assertFalse(job.isCompleted)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull("refresh must not return a Result when cancelled", result)
        assertCachePreserved()
    }

    @Test
    fun ioExceptionSubclassesAreAllTreatedAsNetworkErrors() = runTest {
        api.behavior = { throw IOException("connection reset") }

        assertTrue(exceptionOf(repository.refresh()) is RefreshError.Network)
    }
}
