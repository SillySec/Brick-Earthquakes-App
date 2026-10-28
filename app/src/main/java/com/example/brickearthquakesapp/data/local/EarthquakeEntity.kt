package com.example.brickearthquakesapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "earthquakes")
data class EarthquakeEntity(
    /** USGS event id. */
    @PrimaryKey val id: String,
    val magnitude: Double?,
    val place: String?,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val url: String?,
    val tsunami: Boolean,
    val felt: Int?,
    val alert: String?,
    val significance: Int?,
    /** When this row was downloaded; all rows of one refresh share the same value. */
    val fetchedAtMillis: Long,
)
