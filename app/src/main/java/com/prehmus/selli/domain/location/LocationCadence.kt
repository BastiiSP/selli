package com.prehmus.selli.domain.location

import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.model.isLiveSharing
import java.time.Instant

const val LIVE_HEARTBEAT_MILLIS = 60_000L
const val LIVE_MIN_UPLOAD_SPACING_MILLIS = 10_000L
const val LIVE_MIN_MOVE_METERS = 20f
const val IMPRECISE_ACCURACY_METERS = 100f

data class CadenceSettings(
    val intervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val minUpdateDistanceMeters: Float,
    val maxUpdateDelayMillis: Long,
    val highAccuracy: Boolean,
)

fun SharingMode.cadence(): CadenceSettings = when (this) {
    SharingMode.NORMAL -> CadenceSettings(900_000L, 600_000L, 0f, 0L, false)
    SharingMode.LIVE -> CadenceSettings(10_000L, 5_000L, 0f, 0L, true)
}

/** Ungenaue Live-Fixes dienen nur als Herzschlag, nicht als Bewegungsnachweis. */
fun shouldUploadFix(
    mode: SharingMode,
    millisSinceLastUpload: Long?,
    metersSinceLastUpload: Float?,
    accuracyMeters: Float?,
): Boolean = when {
    mode == SharingMode.NORMAL -> true
    millisSinceLastUpload == null || metersSinceLastUpload == null -> true
    millisSinceLastUpload >= LIVE_HEARTBEAT_MILLIS -> true
    millisSinceLastUpload < LIVE_MIN_UPLOAD_SPACING_MILLIS -> false
    accuracyMeters == null || !accuracyMeters.isFinite() || accuracyMeters > IMPRECISE_ACCURACY_METERS -> false
    else -> metersSinceLastUpload >= maxOf(LIVE_MIN_MOVE_METERS, accuracyMeters)
}

fun feedPollingMillis(locations: List<PersonLocation>, now: Instant): Long =
    if (locations.any { it.isLiveSharing(now) }) 15_000L else 60_000L
