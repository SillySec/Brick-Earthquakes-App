package com.example.brickearthquakesapp.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius: 6371.0088 km. */
const val EARTH_RADIUS_MILES = 3958.7613

/**
 * Great-circle distance in miles between two points given in degrees (latitude first,
 * then longitude), using the haversine formula. It treats the Earth as a sphere, which
 * is accurate to roughly 0.5%, plenty for "how far away was this earthquake".
 */
fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    // sin² makes the longitude difference wrap correctly across the antimeridian.
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
    // Rounding can push `a` a hair past 1 for antipodal points; asin would return NaN.
    return 2 * EARTH_RADIUS_MILES * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
