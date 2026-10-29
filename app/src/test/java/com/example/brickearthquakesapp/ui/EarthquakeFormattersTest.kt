package com.example.brickearthquakesapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class EarthquakeFormattersTest {

    @Test
    fun magnitudeShowsOneDecimal() {
        assertEquals("4.5", formatMagnitude(4.5))
        assertEquals("5.0", formatMagnitude(5.0))
        assertEquals("2.7", formatMagnitude(2.66))
    }

    @Test
    fun missingMagnitudeShowsPlaceholder() {
        assertEquals("–", formatMagnitude(null))
    }

    @Test
    fun depthShowsKilometersWithOneDecimal() {
        assertEquals("10.2 km", formatDepth(10.2, Locale.US))
        assertEquals("0.0 km", formatDepth(0.0, Locale.US))
    }

    @Test
    fun shortDistancesKeepOneDecimal() {
        assertEquals("0.0 mi", formatDistanceMiles(0.0, Locale.US))
        assertEquals("0.0 mi", formatDistanceMiles(0.04, Locale.US))
        assertEquals("4.3 mi", formatDistanceMiles(4.26, Locale.US))
        assertEquals("9.9 mi", formatDistanceMiles(9.94, Locale.US))
    }

    @Test
    fun longerDistancesAreWholeMilesWithGrouping() {
        assertEquals("10 mi", formatDistanceMiles(9.96, Locale.US)) // would round up to 10.0
        assertEquals("12 mi", formatDistanceMiles(12.4, Locale.US))
        assertEquals("13 mi", formatDistanceMiles(12.5, Locale.US))
        assertEquals("1,235 mi", formatDistanceMiles(1234.6, Locale.US))
        assertEquals("12,437 mi", formatDistanceMiles(12436.8, Locale.US))
    }

    @Test
    fun coordinatesUseHemisphereLettersInsteadOfSigns() {
        assertEquals("37.8000° N, 122.5000° W", formatCoordinates(37.8, -122.5, Locale.US))
        assertEquals("33.8688° S, 151.2093° E", formatCoordinates(-33.8688, 151.2093, Locale.US))
    }

    @Test
    fun coordinatesAtZeroAreNorthAndEast() {
        assertEquals("0.0000° N, 0.0000° E", formatCoordinates(0.0, 0.0, Locale.US))
    }

    @Test
    fun coordinatesAreRoundedToFourDecimals() {
        assertEquals("10.1235° N, 20.0000° E", formatCoordinates(10.123456, 19.99999, Locale.US))
    }

    @Test
    fun eventTimeUsesTheRequestedZone() {
        // 2023-11-14T22:13:20Z
        val millis = 1_700_000_000_000L

        val utc = formatEventTime(millis, ZoneId.of("UTC"), Locale.US)
        val tokyo = formatEventTime(millis, ZoneId.of("Asia/Tokyo"), Locale.US)

        assertTrue(utc, utc.contains("Nov 14, 2023"))
        assertTrue(tokyo, tokyo.contains("Nov 15, 2023"))
    }
}
