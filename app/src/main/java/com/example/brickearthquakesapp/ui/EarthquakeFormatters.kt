package com.example.brickearthquakesapp.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "4.5", or "–" when USGS has not published a magnitude yet. */
fun formatMagnitude(magnitude: Double?): String =
    if (magnitude == null) "–" else String.format(Locale.US, "%.1f", magnitude)

/** "10.2 km" */
fun formatDepth(depthKm: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f km", depthKm)

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
