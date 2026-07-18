package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.NewCalendarEvent

interface CalendarRepository {
    suspend fun createEvent(event: NewCalendarEvent): Result<CalendarEvent>
}
