package com.trackmygymgains.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats a rest time in seconds as m:ss (e.g. 180 -> "3:00", 90 -> "1:30"). */
fun formatRest(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

/**
 * Formats a workout duration in milliseconds: "< 1 min" under a minute, "48 min" under an hour,
 * then "1 h 05 min". Seconds are truncated.
 */
fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    return when {
        millis < 60_000 -> "< 1 min"
        totalMinutes < 60 -> "$totalMinutes min"
        else -> String.format(Locale.ROOT, "%d h %02d min", totalMinutes / 60, totalMinutes % 60)
    }
}

/**
 * Date and clock time of a workout, e.g. "ven 28 nov · 18:30" for [Locale.ITALIAN] in the device zone.
 * Zone and locale are parameters so the output is deterministic in tests.
 */
fun formatSessionDateTime(epochMillis: Long, zone: ZoneId, locale: Locale): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(
        DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale),
    )
