package com.example.brickearthquakesapp.data

import com.example.brickearthquakesapp.data.local.EarthquakeDao
import com.example.brickearthquakesapp.data.local.EarthquakeEntity
import com.example.brickearthquakesapp.data.remote.FeatureCollectionDto
import com.example.brickearthquakesapp.data.remote.UsgsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Runs whatever [behavior] the test installs; it may return, throw, or suspend. */
class FakeUsgsApi(
    var behavior: suspend () -> FeatureCollectionDto = { FeatureCollectionDto(emptyList()) },
) : UsgsApi {
    var calls = 0
        private set

    override suspend fun getEarthquakesPastWeek(): FeatureCollectionDto {
        calls++
        return behavior()
    }
}

/**
 * In-memory stand-in for the Room DAO. It reuses the real [replaceAll], which calls
 * [deleteAll] then [insertAll]. It does NOT prove SQLite transaction atomicity; that is
 * Room's job and would need an instrumented test with a real database.
 */
class FakeEarthquakeDao(initial: List<EarthquakeEntity> = emptyList()) : EarthquakeDao() {
    private val rows = MutableStateFlow(initial)

    var replaceAllCalls = 0
        private set

    val current: List<EarthquakeEntity> get() = rows.value

    override fun observeAll(): Flow<List<EarthquakeEntity>> =
        rows.map { list -> list.sortedByDescending { it.timeMillis } }

    override fun observeLastFetchedAt(): Flow<Long?> =
        rows.map { list -> list.maxOfOrNull { it.fetchedAtMillis } }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun insertAll(earthquakes: List<EarthquakeEntity>) {
        rows.value = earthquakes
    }

    override suspend fun replaceAll(earthquakes: List<EarthquakeEntity>) {
        replaceAllCalls++
        super.replaceAll(earthquakes)
    }
}
