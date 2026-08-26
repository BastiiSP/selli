package com.prehmus.selli.domain.location

internal const val MOVING_SPEED_THRESHOLD_MPS = 2.5f
internal const val MOVING_DISTANCE_THRESHOLD_METERS = 200f
internal const val STATIONARY_GRACE_MILLIS = 300_000L

/**
 * Wie dicht der Standort gerade erfasst wird. Vorbild ist Googles Standortfreigabe, nicht
 * WhatsApps Live-Standort: im Stillstand stromsparende WLAN-/Funkzellen-Fixes, dichte
 * GPS-Fixes erst bei erkannter Bewegung.
 */
enum class TrackingMode { STATIONARY, MOVING }

/**
 * Parameter für den `LocationRequest`. [maxUpdateDelayMillis] ist der wichtigste Akku-Hebel:
 * damit darf Android die Fixes bündeln und das Funkmodul zwischendurch schlafen lassen.
 */
data class CadenceSettings(
    val intervalMillis: Long,
    val minUpdateDistanceMeters: Float,
    val maxUpdateDelayMillis: Long,
    val highAccuracy: Boolean,
)

/** Werte laut Design-Spec — Schätzung: ~0,5 %/h im Stillstand, ~2–4 %/h während einer Fahrt. */
fun TrackingMode.settings(): CadenceSettings = when (this) {
    TrackingMode.STATIONARY -> CadenceSettings(
        intervalMillis = 300_000,
        minUpdateDistanceMeters = 150f,
        maxUpdateDelayMillis = 900_000,
        highAccuracy = false,
    )

    TrackingMode.MOVING -> CadenceSettings(
        intervalMillis = 30_000,
        minUpdateDistanceMeters = 50f,
        maxUpdateDelayMillis = 60_000,
        highAccuracy = true,
    )
}

/**
 * Entscheidet nach jedem Fix, ob weiter dicht oder sparsam erfasst wird.
 *
 * Bewusst eine reine Funktion ohne Android-Bezug, damit die Taktung testbar bleibt.
 * [speedMetersPerSecond] darf `null` sein — nicht jeder Fix liefert eine Geschwindigkeit,
 * dann entscheidet allein die zurückgelegte Strecke.
 */
fun nextTrackingMode(
    current: TrackingMode,
    speedMetersPerSecond: Float?,
    metersSinceLastUpload: Float,
    millisSinceLastMovement: Long,
): TrackingMode {
    val movementDetected =
        speedMetersPerSecond != null && speedMetersPerSecond > MOVING_SPEED_THRESHOLD_MPS ||
            metersSinceLastUpload > MOVING_DISTANCE_THRESHOLD_METERS

    return when {
        movementDetected -> TrackingMode.MOVING
        current == TrackingMode.MOVING && millisSinceLastMovement < STATIONARY_GRACE_MILLIS ->
            TrackingMode.MOVING

        else -> TrackingMode.STATIONARY
    }
}
