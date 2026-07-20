package com.prehmus.selli.domain.model

data class SourceLoadError(
    val displayName: String,
    val message: String,
)

data class MergedCalendar(
    val events: List<CalendarEvent>,
    val errors: List<SourceLoadError>,
)
