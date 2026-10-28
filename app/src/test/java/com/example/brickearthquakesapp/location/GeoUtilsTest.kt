package com.example.brickearthquakesapp.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun identicalCoordinatesAreZeroMilesApart() {
        assertEquals(0.0, haversineMiles(37.8, -122.5, 37.8, -122.5), 0.0)
        assertEquals(0.0, haversineMiles(0.0, 0.0, 0.0, 0.0), 0.0)
    }

    @Test
    fun newYorkToLosAngelesIsAbout2445Miles() {
        val miles = haversineMiles(40.7128, -74.0060, 34.0522, -118.2437)

        assertEquals(2445.0, miles, 5.0)
    }

    @Test
    fun londonToParisIsAbout213Miles() {
        val miles = haversineMiles(51.5074, -0.1278, 48.8566, 2.3522)

        assertEquals(213.0, miles, 2.0)
    }

    @Test
    fun argumentOrderIsLatitudeThenLongitude() {
        // San Francisco to Los Angeles. If latitude and longitude were swapped for one
        // point the result would be thousands of miles off.
        val miles = haversineMiles(37.7749, -122.4194, 34.0522, -118.2437)

        assertEquals(347.0, miles, 3.0)
    }

    @Test
    fun distanceIsSymmetric() {
        val there = haversineMiles(40.7128, -74.0060, 34.0522, -118.2437)
        val back = haversineMiles(34.0522, -118.2437, 40.7128, -74.0060)

        assertEquals(there, back, 1e-9)
    }

    @Test
    fun oneDegreeOfLongitudeAtTheEquatorIsAbout69Miles() {
        assertEquals(69.09, haversineMiles(0.0, 0.0, 0.0, 1.0), 0.05)
    }

    @Test
    fun crossingTheEquatorAddsBothHemispheres() {
        // 1 degree south to 1 degree north: 2 degrees of arc.
        assertEquals(138.19, haversineMiles(-1.0, 10.0, 1.0, 10.0), 0.1)
    }

    @Test
    fun crossingThePrimeMeridianAddsBothSides() {
        assertEquals(138.19, haversineMiles(0.0, -1.0, 0.0, 1.0), 0.1)
    }

    @Test
    fun crossingTheAntimeridianTakesTheShortWay() {
        // 179E to 179W is 2 degrees apart, not 358.
        assertEquals(138.19, haversineMiles(0.0, 179.0, 0.0, -179.0), 0.1)
    }

    @Test
    fun poleToPoleIsHalfTheEarthsCircumference() {
        assertEquals(Math.PI * EARTH_RADIUS_MILES, haversineMiles(90.0, 0.0, -90.0, 0.0), 0.01)
    }

    @Test
    fun antipodalPointsDoNotProduceNaN() {
        val miles = haversineMiles(0.0, 0.0, 0.0, 180.0)

        assertFalse(miles.isNaN())
        assertEquals(Math.PI * EARTH_RADIUS_MILES, miles, 0.01)
    }

    @Test
    fun longitudeDoesNotMatterAtThePole() {
        assertEquals(0.0, haversineMiles(90.0, 0.0, 90.0, 120.0), 1e-6)
    }

    @Test
    fun distancesAreNeverNegative() {
        assertTrue(haversineMiles(-33.9, 151.2, 35.7, 139.7) > 0)
    }
}
