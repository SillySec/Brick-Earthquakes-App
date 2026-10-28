package com.example.brickearthquakesapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.brickearthquakesapp.data.DefaultEarthquakeRepository
import com.example.brickearthquakesapp.data.EarthquakeRepository
import com.example.brickearthquakesapp.data.local.AppDatabase
import com.example.brickearthquakesapp.data.remote.UsgsApi
import com.example.brickearthquakesapp.data.remote.UsgsJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class BrickApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Hand-rolled dependency graph: everything is created lazily, once, and handed to
 * consumers through constructors. Small enough that a DI framework would add more
 * setup than it removes.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "earthquakes.db")
            // The table only caches the last downloaded USGS feed, which the next successful
            // refresh replaces anyway. If the schema version changes, Room wipes it instead of
            // running a migration (without a fallback it would crash on startup).
            // Limitation: a user who is offline right after an update that changes the schema
            // sees no cached earthquakes until the next successful refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private val api: UsgsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://earthquake.usgs.gov/")
            .client(OkHttpClient())
            .addConverterFactory(UsgsJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UsgsApi::class.java)
    }

    val earthquakeRepository: EarthquakeRepository by lazy {
        DefaultEarthquakeRepository(api, database.earthquakeDao())
    }
}
