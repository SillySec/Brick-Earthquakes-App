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
    fun eventTimeUsesTheRequestedZone() {
        // 2023-11-14T22:13:20Z
        val millis = 1_700_000_000_000L

        val utc = formatEventTime(millis, ZoneId.of("UTC"), Locale.US)
        val tokyo = formatEventTime(millis, ZoneId.of("Asia/Tokyo"), Locale.US)

        assertTrue(utc, utc.contains("Nov 14, 2023"))
        assertTrue(tokyo, tokyo.contains("Nov 15, 2023"))
    }
}
