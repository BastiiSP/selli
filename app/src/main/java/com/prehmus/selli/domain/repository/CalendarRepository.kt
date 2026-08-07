package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.NewCalendarEvent

interface CalendarRepository {
    suspend fun createEvent(event: NewCalendarEvent): Result<CalendarEvent>

    suspend fun setPartnerAttendance(
        event: CalendarEvent,
        shared: Boolean,
        wholeSeries: Boolean,
    ): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Wir-Zeit-Synchronisation wird für diese Quelle nicht unterstützt."),
        )

    suspend fun requestPartnerDeletion(event: CalendarEvent, wholeSeries: Boolean): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Lösch-Anfrage wird für diese Quelle nicht unterstützt."),
        )

    suspend fun deleteEvent(event: CalendarEvent, scope: DeletionScope): Result<Unit> =
        Result.failure(UnsupportedOperationException("Löschen wird für diese Quelle nicht unterstützt."))
}
