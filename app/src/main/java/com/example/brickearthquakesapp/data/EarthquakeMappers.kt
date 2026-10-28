package com.example.brickearthquakesapp.data

import com.example.brickearthquakesapp.data.local.EarthquakeEntity
import com.example.brickearthquakesapp.data.remote.FeatureCollectionDto
import com.example.brickearthquakesapp.data.remote.FeatureDto
import com.example.brickearthquakesapp.data.remote.UsgsJson
import com.example.brickearthquakesapp.model.Earthquake
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/**
 * Converts every decodable feature to an entity and silently drops the malformed ones
 * (missing id/time/geometry, wrong types, out-of-range coordinates).
 *
 * @throws SerializationException if the feed contained features but none were usable.
 *   That points at a format change rather than a few bad rows, and failing here keeps the
 *   repository from replacing a good cache with an empty one.
 */
fun FeatureCollectionDto.toEntities(fetchedAtMillis: Long): List<EarthquakeEntity> {
    val entities = features.mapNotNull { it.toEntityOrNull(fetchedAtMillis) }
    if (features.isNotEmpty() && entities.isEmpty()) {
        throw SerializationException("None of the ${features.size} features could be parsed")
    }
    return entities
}

private fun JsonElement.toEntityOrNull(fetchedAtMillis: Long): EarthquakeEntity? {
    val feature = try {
        UsgsJson.decodeFromJsonElement(FeatureDto.serializer(), this)
    } catch (_: IllegalArgumentException) {
        // SerializationException extends IllegalArgumentException; both mean "bad feature".
        return null
    }
    // GeoJSON coordinate order is [longitude, latitude, depth].
    val coordinates = feature.geometry.coordinates
    if (feature.id.isBlank() || coordinates.size < 3) return null
    val longitude = coordinates[0]
    val latitude = coordinates[1]
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    return EarthquakeEntity(
        id = feature.id,
        magnitude = feature.properties.mag,
        place = feature.properties.place,
        timeMillis = feature.properties.time,
        latitude = latitude,
        longitude = longitude,
        depthKm = coordinates[2],
        url = feature.properties.url,
        tsunami = feature.properties.tsunami != 0,
        felt = feature.properties.felt,
        alert = feature.properties.alert,
        significance = feature.properties.sig,
        fetchedAtMillis = fetchedAtMillis,
    )
}

fun EarthquakeEntity.toModel() = Earthquake(
    id = id,
    magnitude = magnitude,
    place = place,
    timeMillis = timeMillis,
    latitude = latitude,
    longitude = longitude,
    depthKm = depthKm,
    url = url,
    tsunami = tsunami,
    felt = felt,
    alert = alert,
    significance = significance,
)
