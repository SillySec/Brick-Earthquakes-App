package com.example.brickearthquakesapp.data

import com.example.brickearthquakesapp.data.local.EarthquakeEntity
import com.example.brickearthquakesapp.data.remote.FeatureCollectionDto
import com.example.brickearthquakesapp.data.remote.UsgsJson

/** A GeoJSON feature the way USGS sends it (including fields we ignore). */
fun featureJson(
    id: String = "us1000test",
    lon: Double = -122.5,
    lat: Double = 37.8,
    depth: Double = 10.2,
    mag: String = "4.5",
    place: String = "\"5 km N of Somewhere\"",
    time: String = "1700000000000",
    tsunami: Int = 0,
) = """
    {"type":"Feature","id":"$id",
     "properties":{"mag":$mag,"place":$place,"time":$time,"updated":1700000100000,
                   "url":"https://earthquake.usgs.gov/earthquakes/eventpage/$id",
                   "felt":12,"alert":"green","tsunami":$tsunami,"sig":300,"magType":"ml"},
     "geometry":{"type":"Point","coordinates":[$lon,$lat,$depth]}}
""".trimIndent()

fun feedJson(vararg features: String) =
    """{"type":"FeatureCollection","metadata":{"count":${features.size}},"features":[${features.joinToString(",")}]}"""

fun parseFeed(json: String): FeatureCollectionDto =
    UsgsJson.decodeFromString(FeatureCollectionDto.serializer(), json)

fun entity(id: String, timeMillis: Long, fetchedAtMillis: Long = 0L) = EarthquakeEntity(
    id = id,
    magnitude = 4.0,
    place = "Somewhere",
    timeMillis = timeMillis,
    latitude = 1.0,
    longitude = 2.0,
    depthKm = 3.0,
    url = null,
    tsunami = false,
    felt = null,
    alert = null,
    significance = null,
    fetchedAtMillis = fetchedAtMillis,
)
