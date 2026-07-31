package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import java.time.LocalDateTime

data class SharedEventFingerprint(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
    val location: String?,
    val description: String?,
)

fun CalendarEvent.fingerprint(): SharedEventFingerprint =
    SharedEventFingerprint(
        title = title,
        start = start,
        end = end,
        isAllDay = isAllDay,
        location = location,
        description = description,
    )
