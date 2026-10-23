package com.prehmus.selli.domain.model

import com.prehmus.selli.domain.location.IMPRECISE_ACCURACY_METERS
import com.prehmus.selli.domain.location.MAX_LIVE_SHARE_DURATION
import java.time.Duration
import java.time.Instant

/** Aktuelle Position einer der beiden Personen. Keine Historie — nur der jeweils letzte Stand. */
data class PersonLocation(
    val person: Person,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val isMoving: Boolean,
    val updatedAt: Instant,
    val liveUntil: Instant? = null,
)

enum class LocationFreshness { JUST_NOW, RECENT, STALE }

fun PersonLocation.freshness(now: Instant): LocationFreshness = when {
    measurementAge(now) < Duration.ofMinutes(2) -> LocationFreshness.JUST_NOW
    isLiveSharing(now) -> LocationFreshness.STALE
    measurementAge(now) < Duration.ofMinutes(45) -> LocationFreshness.RECENT
    else -> LocationFreshness.STALE
}

fun PersonLocation.measurementAge(now: Instant): Duration =
    Duration.between(updatedAt, now).coerceAtLeast(Duration.ZERO)

fun PersonLocation.isLiveSharing(now: Instant): Boolean = liveUntil?.let {
    it > now && it <= now.plus(MAX_LIVE_SHARE_DURATION).plusSeconds(300)
} ?: false

fun PersonLocation.liveRemaining(now: Instant): Duration? =
    if (isLiveSharing(now)) Duration.between(now, liveUntil) else null

fun PersonLocation.isImprecise(): Boolean = accuracyMeters?.let { it > IMPRECISE_ACCURACY_METERS } ?: false
