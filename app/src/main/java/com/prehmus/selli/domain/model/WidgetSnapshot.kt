package com.prehmus.selli.domain.model

import java.time.LocalDateTime

data class WidgetSnapshot(
    val partnerPerson: Person,
    val partnerDisplayName: String,
    val nextEvent: CalendarEvent?,
    val updatedAt: LocalDateTime,
)
