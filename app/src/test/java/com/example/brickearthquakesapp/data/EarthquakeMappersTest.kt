package com.example.brickearthquakesapp.data

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthquakeMappersTest {

    @Test
    fun mapsCoordinatesInLongitudeLatitudeDepthOrder() {
        val feed = parseFeed(feedJson(featureJson(lon = -122.5, lat = 37.8, depth = 10.2)))

        val entity = feed.toEntities(fetchedAtMillis = 99L).single()

        assertEquals(-122.5, entity.longitude, 0.0)
        assertEquals(37.8, entity.latitude, 0.0)
        assertEquals(10.2, entity.depthKm, 0.0)
    }

    @Test
    fun mapsAllPropertiesAndUsesUsgsIdAsKey() {
        val feed = parseFeed(feedJson(featureJson(id = "us7000abcd", tsunami = 1)))

        val entity = feed.toEntities(fetchedAtMillis = 99L).single()

        assertEquals("us7000abcd", entity.id)
        assertEquals(4.5, entity.magnitude!!, 0.0)
        assertEquals("5 km N of Somewhere", entity.place)
        assertEquals(1_700_000_000_000L, entity.timeMillis)
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us7000abcd", entity.url)
        assertTrue(entity.tsunami)
        assertEquals(12, entity.felt)
        assertEquals("green", entity.alert)
        assertEquals(300, entity.significance)
        assertEquals(99L, entity.fetchedAtMillis)
    }

    @Test
    fun tsunamiZeroMapsToFalse() {
        val entity = parseFeed(feedJson(featureJson(tsunami = 0))).toEntities(0L).single()
        assertFalse(entity.tsunami)
    }

    @Test
    fun nullMagnitudeAndPlaceAreAllowed() {
        val feed = parseFeed(feedJson(featureJson(mag = "null", place = "null")))

        val entity = feed.toEntities(0L).single()

        assertNull(entity.magnitude)
        assertNull(entity.place)
    }

    @Test
    fun malformedFeaturesAreSkippedAndValidOnesKept() {
        val missingId = """{"properties":{"mag":1.0,"time":1},"geometry":{"coordinates":[1,2,3]}}"""
        val shortCoordinates = featureJson(id = "short").replace("[-122.5,37.8,10.2]", "[-122.5,37.8]")
        val badLatitude = featureJson(id = "lat", lat = 123.0)
        val badTime = featureJson(id = "time", time = "\"yesterday\"")
        val notAnObject = "42"
        val feed = parseFeed(
            feedJson(
                missingId, shortCoordinates, badLatitude, badTime, notAnObject,
                featureJson(id = "good1"), featureJson(id = "good2"),
            ),
        )

        val entities = feed.toEntities(0L)

        assertEquals(listOf("good1", "good2"), entities.map { it.id })
    }

    @Test
    fun feedWhereNoFeatureIsUsableFails() {
        val feed = parseFeed(feedJson("""{"id":"x"}""", "42"))

        assertThrows(SerializationException::class.java) { feed.toEntities(0L) }
    }

    @Test
    fun emptyFeedMapsToEmptyList() {
        assertTrue(parseFeed(feedJson()).toEntities(0L).isEmpty())
    }

    @Test
    fun responseWithoutFeaturesArrayFailsToParse() {
        assertThrows(SerializationException::class.java) { parseFeed("""{"type":"FeatureCollection"}""") }
    }

    @Test
    fun entityMapsToDomainModel() {
        val model = entity(id = "abc", timeMillis = 5L).toModel()

        assertEquals("abc", model.id)
        assertEquals(5L, model.timeMillis)
        assertEquals(1.0, model.latitude, 0.0)
        assertEquals(2.0, model.longitude, 0.0)
        assertEquals(3.0, model.depthKm, 0.0)
    }
}
