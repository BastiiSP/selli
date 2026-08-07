package com.prehmus.selli.domain.countdown

import com.prehmus.selli.domain.model.CalendarEvent
import java.time.Duration
import java.time.LocalDateTime

/** Sichtbarer Zustand des Wir-Zeit-Countdowns: läuft, heute angekommen, oder nichts geplant. */
enum class WirZeitCountdownState { WALKING, ARRIVED_TODAY, NONE_PLANNED }

/**
 * Ergebnis der Countdown-Berechnung für Header und Widget. [progress] ist nur im Zustand
 * [WirZeitCountdownState.WALKING] aussagekräftig (0f..1f). [remainingText] ist nur für
 * [WirZeitCountdownState.NONE_PLANNED] leer. Für [WirZeitCountdownState.ARRIVED_TODAY] ist der
 * Text nicht leer, wird von Header und Widget aber bewusst nicht angezeigt — diese zeigen
 * stattdessen ihre eigene "Heute ist es soweit"-Formulierung.
 */
data class WirZeitCountdown(
    val state: WirZeitCountdownState,
    val progress: Float,
    val remainingText: String,
)

/**
 * Berechnet den Wir-Zeit-Countdown aus dem nächsten Wir-Zeit-Termin (Ergebnis von
 * `NextSharedEventSelector.select()`, `null` = keine zukünftige Wir-Zeit).
 *
 * [includeMinutes] = `true` für den live tickenden Kalender-Header (minutengenau), `false` für
 * das Widget (nur Tage/Stunden — ein bis zu 30 Minuten alter Snapshot zeigt ohnehin keine
 * Live-Aktualisierung, Minuten würden das nur vortäuschen).
 */
fun calculateWirZeitCountdown(
    event: CalendarEvent?,
    now: LocalDateTime,
    includeMinutes: Boolean = true,
): WirZeitCountdown {
    if (event == null) {
        return WirZeitCountdown(WirZeitCountdownState.NONE_PLANNED, progress = 0f, remainingText = "")
    }
    val remaining = Duration.between(now, event.start).let { if (it.isNegative) Duration.ZERO else it }
    val remainingText = formatCountdown(remaining, includeMinutes)
    if (!now.toLocalDate().isBefore(event.start.toLocalDate())) {
        return WirZeitCountdown(WirZeitCountdownState.ARRIVED_TODAY, progress = 1f, remainingText = remainingText)
    }
    return WirZeitCountdown(
        state = WirZeitCountdownState.WALKING,
        progress = calculateProgress(created = event.created, start = event.start, now = now),
        remainingText = remainingText,
    )
}

/**
 * 0f = Termin wurde gerade erst angelegt, 1f = Termin-Start erreicht. Ohne bekanntes `created`
 * (z. B. ICS-Importe) bleibt der Fortschritt fest bei 0f (Weg-Anfang) statt zu raten.
 */
internal fun calculateProgress(created: LocalDateTime?, start: LocalDateTime, now: LocalDateTime): Float {
    if (created == null) return 0f
    val total = Duration.between(created, start)
    if (total.isZero || total.isNegative) return 1f
    val elapsed = Duration.between(created, now)
    if (elapsed.isNegative) return 0f
    if (elapsed >= total) return 1f
    return elapsed.toMillis().toFloat() / total.toMillis().toFloat()
}

/** "noch 3 Tage, 4 Std., 22 Min." (Header) bzw. "noch 3 Tage, 4 Std." (Widget, [includeMinutes] = false). */
internal fun formatCountdown(remaining: Duration, includeMinutes: Boolean): String {
    val totalMinutes = remaining.toMinutes()
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    val parts = mutableListOf<String>()
    if (days > 0) parts += if (days == 1L) "1 Tag" else "$days Tage"
    if (hours > 0) parts += "$hours Std."
    if (includeMinutes && minutes > 0) parts += "$minutes Min."

    if (parts.isEmpty()) return if (includeMinutes) "gleich" else "< 1 Std."
    return "noch " + parts.joinToString(", ")
}
