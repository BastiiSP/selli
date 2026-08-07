package com.prehmus.selli.data.google

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GoogleCalendarEventMapper(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun toCalendarEvent(
        event: Event,
        source: CalendarSource,
        owner: Person,
        ownEmail: String,
        partnerEmail: String?,
    ): CalendarEvent {
        val isAllDay = event.start?.date != null

        return CalendarEvent(
            id = event.id.orEmpty(),
            title = event.summary.orEmpty(),
            start = event.start.toLocalDateTime(isAllDay),
            end = event.end.toLocalDateTime(isAllDay),
            isAllDay = isAllDay,
            source = source,
            owner = owner,
            isSharedEvent = event.isSharedByAttendees(ownEmail, partnerEmail) || event.hasSelliSharedProperty(),
            location = event.location,
            description = event.description,
            seriesId = event.recurringEventId,
            created = event.created?.toInstant()?.atZone(zoneId)?.toLocalDateTime(),
        )
    }

    private fun com.google.api.services.calendar.model.EventDateTime?.toLocalDateTime(
        isAllDay: Boolean,
    ): LocalDateTime {
        val value = requireNotNull(this) { "Google Calendar event is missing start or end time." }
        return if (isAllDay) {
            requireNotNull(value.date).toLocalDate().atStartOfDay()
        } else {
            requireNotNull(value.dateTime).toInstant().atZone(zoneId).toLocalDateTime()
        }
    }

    private fun DateTime.toInstant(): Instant = Instant.ofEpochMilli(value)

    private fun DateTime.toLocalDate(): LocalDate = LocalDate.parse(toStringRfc3339().take(10))

    private fun Event.isSharedByAttendees(ownEmail: String, partnerEmail: String?): Boolean {
        val normalizedOwnEmail = ownEmail.normalizedEmail()
        val normalizedPartnerEmail = partnerEmail?.normalizedEmail() ?: return false
        val attendeeEmails = attendees
            .orEmpty()
            .mapNotNull { it.email?.normalizedEmail() }
            .toSet()

        return normalizedOwnEmail in attendeeEmails && normalizedPartnerEmail in attendeeEmails
    }

    private fun Event.hasSelliSharedProperty(): Boolean {
        val sharedValue = extendedProperties?.shared?.get(SELLI_SHARED_PROPERTY)
            ?: extendedProperties?.`private`?.get(SELLI_SHARED_PROPERTY)

        return sharedValue.equals("true", ignoreCase = true)
    }

    private fun String.normalizedEmail(): String = trim().lowercase()

    companion object {
        const val SELLI_SHARED_PROPERTY = "selli:shared"
    }
}
