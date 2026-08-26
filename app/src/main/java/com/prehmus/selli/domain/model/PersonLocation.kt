package com.prehmus.selli.domain.model

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
)

/** Wie frisch die Position ist — treibt die Beschriftung unter dem Kartenmarker. */
enum class LocationFreshness { LIVE, RECENT, STALE }

fun PersonLocation.freshness(now: Instant): LocationFreshness {
    val age = Duration.between(updatedAt, now)
    return when {
        age.isNegative -> LocationFreshness.LIVE
        age < LIVE_MAX_AGE -> LocationFreshness.LIVE
        age < RECENT_MAX_AGE -> LocationFreshness.RECENT
        else -> LocationFreshness.STALE
    }
}

private val LIVE_MAX_AGE: Duration = Duration.ofMinutes(2)
private val RECENT_MAX_AGE: Duration = Duration.ofMinutes(30)
