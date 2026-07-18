package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange

interface IcsCalendarRepository {
    suspend fun fetchEvents(range: DateRange): List<CalendarEvent>
}
