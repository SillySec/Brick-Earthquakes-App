package com.example.brickearthquakesapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema is off: the DB is a disposable cache, so there are no migrations to verify.
@Database(entities = [EarthquakeEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun earthquakeDao(): EarthquakeDao
}
