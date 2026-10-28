package com.example.brickearthquakesapp.data.remote

import retrofit2.http.GET

interface UsgsApi {
    /**
     * Magnitude 2.5+ events from the past 7 days. Base URL is https://earthquake.usgs.gov/.
     * Non-2xx responses throw [retrofit2.HttpException]; connectivity problems throw
     * [java.io.IOException].
     */
    @GET("earthquakes/feed/v1.0/summary/2.5_week.geojson")
    suspend fun getEarthquakesPastWeek(): FeatureCollectionDto
}
