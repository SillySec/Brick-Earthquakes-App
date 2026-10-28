package com.example.brickearthquakesapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * An abstract class rather than an interface so that [replaceAll] can be an
 * `@Transaction` method with a body.
 */
@Dao
abstract class EarthquakeDao {

    @Query("SELECT * FROM earthquakes ORDER BY timeMillis DESC")
    abstract fun observeAll(): Flow<List<EarthquakeEntity>>

    /** Null while the table is empty. */
    @Query("SELECT MAX(fetchedAtMillis) FROM earthquakes")
    abstract fun observeLastFetchedAt(): Flow<Long?>

    @Query("DELETE FROM earthquakes")
    protected abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(earthquakes: List<EarthquakeEntity>)

    /**
     * Swaps the cache contents in one transaction: if the insert fails, the delete is rolled
     * back and observers never see a half-replaced table. Observers get a single emission.
     */
    @Transaction
    open suspend fun replaceAll(earthquakes: List<EarthquakeEntity>) {
        deleteAll()
        insertAll(earthquakes)
    }
}
