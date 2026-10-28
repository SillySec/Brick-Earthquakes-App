package com.example.brickearthquakesapp.model

data class Earthquake(
    /** USGS event id, e.g. "us7000abcd". Stable across feed refreshes. */
    val id: String,
    /** Null when USGS has not (yet) computed a magnitude. */
    val magnitude: Double?,
    val place: String?,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    /** Link to the USGS event page. */
    val url: String?,
    val tsunami: Boolean,
    /** Number of "Did you feel it?" reports. */
    val felt: Int?,
    /** PAGER alert level: green, yellow, orange or red. */
    val alert: String?,
    /** USGS significance score. */
    val significance: Int?,
)
