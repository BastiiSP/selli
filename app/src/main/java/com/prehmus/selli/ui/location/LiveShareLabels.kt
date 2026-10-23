package com.prehmus.selli.ui.location

import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.model.LocationFreshness
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.model.freshness
import com.prehmus.selli.domain.model.isImprecise
import com.prehmus.selli.domain.model.isLiveSharing
import com.prehmus.selli.domain.model.measurementAge
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/*
 * Reine Textbausteine der Standort-Ansicht — bewusst ohne Compose, damit die Beschriftungen
 * (Restzeit, Messalter, veraltet/ungenau) ohne Gerät prüfbar bleiben.
 */

fun Person.displayName(): String = if (this == Person.MELLI) "Melli" else "Basti"

fun Person.partner(): Person = if (this == Person.MELLI) Person.BASTI else Person.MELLI

fun LiveShareDuration.label(): String = when (this) {
    LiveShareDuration.MINUTES_15 -> "15 Minuten"
    LiveShareDuration.HOUR_1 -> "1 Stunde"
    LiveShareDuration.HOURS_8 -> "8 Stunden"
}

/** „noch 52 Min.“ / „noch 1 Std. 5 Min.“ — angefangene Minuten zählen voll, wie bei WhatsApp. */
fun formatRemaining(remaining: Duration): String {
    if (remaining.isNegative || remaining.isZero) return "endet gleich"
    val totalMinutes = (remaining.seconds + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "noch $minutes Min."
        minutes == 0L -> "noch $hours Std."
        else -> "noch $hours Std. $minutes Min."
    }
}

/** Alter der letzten Messung: „gerade eben“, „vor 40 s“, „vor 8 Min.“, „vor 2 Std.“, „vor 3 Tagen“. */
fun formatMeasurementAge(age: Duration): String {
    val seconds = age.seconds.coerceAtLeast(0)
    return when {
        seconds < 15 -> "gerade eben"
        seconds < 60 -> "vor ${seconds / 5 * 5} s"
        seconds < 3_600 -> "vor ${seconds / 60} Min."
        seconds < 86_400 -> "vor ${seconds / 3_600} Std."
        seconds < 2 * 86_400 -> "vor 1 Tag"
        else -> "vor ${seconds / 86_400} Tagen"
    }
}

fun formatClockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    CLOCK_FORMAT.format(instant.atZone(zone))

/** Beschriftung unter dem Kartenmarker: Modus, Messalter und — wenn nötig — „veraltet“. */
fun PersonLocation.markerLabel(now: Instant): String {
    val age = formatMeasurementAge(measurementAge(now))
    val live = isLiveSharing(now)
    return when (freshness(now)) {
        LocationFreshness.JUST_NOW -> if (live) "LIVE · $age" else age
        LocationFreshness.RECENT -> age
        LocationFreshness.STALE -> if (live) "Live-Signal veraltet · $age" else "veraltet · $age"
    }
}

/** „ungenau (± 300 m)“ oder null, wenn die Messung genau genug ist. */
fun PersonLocation.imprecisionLabel(): String? {
    val accuracy = accuracyMeters ?: return null
    if (!isImprecise()) return null
    return if (accuracy >= 1_000) {
        "ungenau (± ${"%.1f".format(accuracy / 1_000).replace('.', ',')} km)"
    } else {
        "ungenau (± ${(accuracy / 10).roundToInt() * 10} m)"
    }
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
