package com.prehmus.selli.data.google

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DeletionScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class GoogleCalendarEventDeletion(
    private val calendar: Calendar,
    private val zoneId: ZoneId,
) {
    suspend fun delete(event: CalendarEvent, scope: DeletionScope): Result<Unit> =
        try {
            deleteOrThrow(event, scope)
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(error)
        }

    fun deleteOrThrow(event: CalendarEvent, scope: DeletionScope) {
        require(event.source == CalendarSource.GOOGLE_OWN) {
            "Nur eigene Google-Kalender-Termine können gelöscht werden."
        }

        try {
            deleteExistingEvent(event, scope)
        } catch (error: GoogleJsonResponseException) {
            if (error.statusCode != HTTP_NOT_FOUND && error.statusCode != HTTP_GONE) throw error
        }
    }

    private fun deleteExistingEvent(event: CalendarEvent, scope: DeletionScope) {
        val seriesId = event.seriesId
        if (scope == DeletionScope.SINGLE_OCCURRENCE || seriesId == null) {
            calendar.events().delete(PRIMARY_CALENDAR_ID, event.id).execute()
            return
        }

        val master = calendar.events().get(PRIMARY_CALENDAR_ID, seriesId).execute()
        if (event.start <= master.seriesStart(zoneId)) {
            calendar.events().delete(PRIMARY_CALENDAR_ID, seriesId).execute()
            return
        }

        master.recurrence = master.recurrence.orEmpty().map { recurrence ->
            if (recurrence.startsWith(RRULE_PREFIX, ignoreCase = true)) {
                recurrence.withUntil(event.start, master.isAllDaySeries(), zoneId)
            } else {
                recurrence
            }
        }
        calendar.events().update(PRIMARY_CALENDAR_ID, seriesId, master).execute()
    }

    private companion object {
        const val PRIMARY_CALENDAR_ID = "primary"
        const val RRULE_PREFIX = "RRULE:"
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}

private fun Event.seriesStart(zoneId: ZoneId): LocalDateTime {
    start?.date?.let { return LocalDate.parse(it.toStringRfc3339()).atStartOfDay() }
    val startMillis = requireNotNull(start?.dateTime) { "Google series master is missing DTSTART." }.value
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zoneId)
}

private fun Event.isAllDaySeries(): Boolean = start?.date != null

private fun String.withUntil(
    occurrenceStart: LocalDateTime,
    isAllDay: Boolean,
    zoneId: ZoneId,
): String {
    val parts = split(';').filterNot { part ->
        part.startsWith("COUNT=", ignoreCase = true) || part.startsWith("UNTIL=", ignoreCase = true)
    }
    val until = if (isAllDay) {
        // DATE values are inclusive, so the day before the selected occurrence is the cutoff.
        occurrenceStart.toLocalDate().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
    } else {
        occurrenceStart.atZone(zoneId).toInstant().minusSeconds(1)
            .atZone(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    }
    return (parts + "UNTIL=$until").joinToString(";")
}
