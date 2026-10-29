package com.example.brickearthquakesapp.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/** "4.5", or "–" when USGS has not published a magnitude yet. */
fun formatMagnitude(magnitude: Double?): String =
    if (magnitude == null) "–" else String.format(Locale.US, "%.1f", magnitude)

/** "10.2 km" */
fun formatDepth(depthKm: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f km", depthKm)

/** "37.8000° N, 122.5000° W": unsigned degrees with a hemisphere letter, easier to read than signs. */
fun formatCoordinates(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String =
    String.format(
        locale,
        "%.4f° %s, %.4f° %s",
        abs(latitude),
        if (latitude >= 0) "N" else "S",
        abs(longitude),
        if (longitude >= 0) "E" else "W",
    )

/**
 * "4.3 mi" below 10 miles, otherwise whole miles with grouping ("1,235 mi"), so short
 * distances stay precise and long ones stay readable.
 */
fun formatDistanceMiles(miles: Double, locale: Locale = Locale.getDefault()): String {
    val tenths = Math.round(miles * 10) / 10.0
    return if (tenths < 10) {
        String.format(locale, "%.1f mi", tenths)
    } else {
        String.format(locale, "%,d mi", Math.round(miles))
    }
}

/** Localized date and time in the given zone, e.g. "Nov 14, 2023, 10:13 PM". */
fun formatEventTime(
    timeMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zone)
    .format(Instant.ofEpochMilli(timeMillis))
