package com.example.brickearthquakesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema controls whether Room writes a JSON snapshot of each schema version (to the
// room.schemaLocation directory). Those snapshots feed auto-migrations and migration tests.
// We write no migrations (see the destructive fallback in AppContainer), so it stays off.
@Database(entities = [EarthquakeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun earthquakeDao(): EarthquakeDao
}
