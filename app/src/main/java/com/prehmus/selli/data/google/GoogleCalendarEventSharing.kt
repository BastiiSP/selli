package com.prehmus.selli.data.google

import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource

internal class GoogleCalendarEventSharing(
    private val calendar: Calendar,
) {
    fun setPartnerAttendance(
        event: CalendarEvent,
        partnerEmail: String,
        shared: Boolean,
        wholeSeries: Boolean,
    ): Result<Unit> = runCatching {
        require(event.source == CalendarSource.GOOGLE_OWN) {
            "Nur eigene Google-Kalender-Termine können als Wir-Zeit geteilt werden."
        }
        require(partnerEmail.isNotBlank()) {
            "Das Partnerkonto fehlt. Bitte die Kalender-Verknüpfung erneut einrichten."
        }

        val targetId = event.seriesId.takeIf { wholeSeries } ?: event.id
        val remoteEvent = calendar.events()
            .get(PRIMARY_CALENDAR_ID, targetId)
            .execute()

        remoteEvent.attendees = remoteEvent.attendees
            .orEmpty()
            .filterNot { attendee -> attendee.email.equals(partnerEmail, ignoreCase = true) }
            .toMutableList()
            .apply {
                if (shared) add(EventAttendee().setEmail(partnerEmail))
            }
            .ifEmpty { null }

        remoteEvent.updateSelliSharedMarker(shared)

        calendar.events()
            .update(PRIMARY_CALENDAR_ID, targetId, remoteEvent)
            .setSendUpdates(SEND_UPDATES_ALL)
            .execute()
        Unit
    }

    private fun Event.updateSelliSharedMarker(shared: Boolean) {
        val properties = extendedProperties ?: Event.ExtendedProperties()
        val sharedProperties = properties.shared.orEmpty().toMutableMap()
        if (shared) {
            sharedProperties[GoogleCalendarEventMapper.SELLI_SHARED_PROPERTY] = "true"
        } else {
            sharedProperties.remove(GoogleCalendarEventMapper.SELLI_SHARED_PROPERTY)
        }
        properties.shared = sharedProperties.ifEmpty { null }
        extendedProperties = properties.takeIf {
            !it.shared.isNullOrEmpty() || !it.private.isNullOrEmpty()
        }
    }

    private companion object {
        const val PRIMARY_CALENDAR_ID = "primary"
        const val SEND_UPDATES_ALL = "all"
    }
}
