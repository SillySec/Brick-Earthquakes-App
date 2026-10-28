package com.example.brickearthquakesapp.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Shared by Retrofit's converter and the per-feature decoding in the mapper, so both
 * interpret the feed identically.
 */
val UsgsJson = Json { ignoreUnknownKeys = true }

/**
 * Top-level GeoJSON FeatureCollection. Features stay as raw [JsonElement]s on purpose:
 * decoding the whole list in one go would fail the entire response because of a single
 * malformed feature. The mapper decodes each element into a [FeatureDto] individually.
 */
@Serializable
data class FeatureCollectionDto(
    val features: List<JsonElement>,
)

@Serializable
data class FeatureDto(
    val id: String,
    val properties: PropertiesDto,
    val geometry: GeometryDto,
)

@Serializable
data class PropertiesDto(
    val mag: Double? = null,
    val place: String? = null,
    /** Epoch milliseconds. */
    val time: Long,
    val url: String? = null,
    val felt: Int? = null,
    val alert: String? = null,
    /** 1 if a tsunami message may apply, otherwise 0. */
    val tsunami: Int = 0,
    val sig: Int? = null,
)

@Serializable
data class GeometryDto(
    /** GeoJSON order: [longitude, latitude, depthKm]. */
    val coordinates: List<Double>,
)
