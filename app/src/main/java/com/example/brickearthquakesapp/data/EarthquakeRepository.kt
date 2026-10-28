package com.example.brickearthquakesapp.data

import com.example.brickearthquakesapp.data.local.EarthquakeDao
import com.example.brickearthquakesapp.data.remote.UsgsApi
import com.example.brickearthquakesapp.model.Earthquake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

interface EarthquakeRepository {
    /** The cache, newest first. Room is the single source of truth; this never hits the network. */
    fun observeEarthquakes(): Flow<List<Earthquake>>

    /** When the cache was last filled from the network, or null if it never was. */
    fun observeLastUpdated(): Flow<Long?>

    /**
     * Downloads the feed and replaces the cache. On failure the cache is untouched and the
     * result holds a [RefreshError]. Cancellation is not a failure and is rethrown.
     */
    suspend fun refresh(): Result<Unit>
}

sealed class RefreshError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, timeout, DNS failure, etc. */
    class Network(cause: IOException) : RefreshError("Network error: ${cause.message}", cause)

    /** The server answered with a non-2xx status. */
    class Http(val code: Int, cause: HttpException) : RefreshError("HTTP $code", cause)

    /** The response was not the GeoJSON we expect. */
    class Parse(cause: Exception) : RefreshError("Unreadable response: ${cause.message}", cause)

    /** Anything else, e.g. a database failure while writing. */
    class Unknown(cause: Exception) : RefreshError("Unexpected error: ${cause.message}", cause)
}

class DefaultEarthquakeRepository(
    private val api: UsgsApi,
    private val dao: EarthquakeDao,
    private val now: () -> Long = System::currentTimeMillis,
) : EarthquakeRepository {

    override fun observeEarthquakes(): Flow<List<Earthquake>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeLastUpdated(): Flow<Long?> = dao.observeLastFetchedAt()

    override suspend fun refresh(): Result<Unit> = try {
        val feed = api.getEarthquakesPastWeek()
        // Parse everything first: if anything throws, the database has not been touched.
        val entities = feed.toEntities(fetchedAtMillis = now())
        dao.replaceAll(entities)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(RefreshError.Http(e.code(), e))
    } catch (e: IOException) {
        Result.failure(RefreshError.Network(e))
    } catch (e: SerializationException) {
        Result.failure(RefreshError.Parse(e))
    } catch (e: Exception) {
        Result.failure(RefreshError.Unknown(e))
    }
}
