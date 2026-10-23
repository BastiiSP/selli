package com.prehmus.selli.domain.location

import java.time.Duration
import java.time.Instant

val MAX_LIVE_SHARE_DURATION: Duration = Duration.ofHours(8)

enum class LiveShareDuration(val duration: Duration) {
    MINUTES_15(Duration.ofMinutes(15)),
    HOUR_1(Duration.ofHours(1)),
    HOURS_8(MAX_LIVE_SHARE_DURATION),
}

data class LiveShare(val startedAt: Instant, val endsAt: Instant) {
    fun isActive(now: Instant): Boolean = now < endsAt

    fun remaining(now: Instant): Duration = Duration.between(now, endsAt).coerceAtLeast(Duration.ZERO)
}

fun startLiveShare(now: Instant, duration: LiveShareDuration): LiveShare = LiveShare(now, now.plus(duration.duration))

/** Löscht fachlich abgelaufene Freigaben und erkennt stark zurückgestellte Uhren. */
fun LiveShare?.validAt(now: Instant): LiveShare? = this?.takeIf {
    it.isActive(now) && it.endsAt <= now.plus(MAX_LIVE_SHARE_DURATION).plusSeconds(60)
}

enum class SharingMode { NORMAL, LIVE }

fun sharingModeAt(share: LiveShare?, now: Instant): SharingMode =
    if (share.validAt(now) != null) SharingMode.LIVE else SharingMode.NORMAL
