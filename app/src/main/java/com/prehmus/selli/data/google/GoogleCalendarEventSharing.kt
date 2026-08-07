package com.prehmus.selli.data.google

import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person

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

    fun requestDeletion(
        event: CalendarEvent,
        requestedBy: Person,
        wholeSeries: Boolean,
    ): Result<Unit> = runCatching {
        require(event.source != CalendarSource.GOOGLE_OWN) {
            "Eigene Termine werden direkt gelöscht, keine Anfrage nötig."
        }
        require(event.category == EventCategory.TOGETHER) {
            "Eine Lösch-Anfrage ist nur für Wir-Zeit-Termine vorgesehen."
        }

        val targetId = event.seriesId.takeIf { wholeSeries } ?: event.id
        val remoteEvent = calendar.events()
            .get(PRIMARY_CALENDAR_ID, targetId)
            .execute()

        remoteEvent.setSharedProperty(
            GoogleCalendarEventMapper.SELLI_DELETE_REQUESTED_PROPERTY,
            requestedBy.name,
        )

        calendar.events()
            .update(PRIMARY_CALENDAR_ID, targetId, remoteEvent)
            .setSendUpdates(SEND_UPDATES_NONE)
            .execute()
        Unit
    }

    private fun Event.updateSelliSharedMarker(shared: Boolean) {
        setSharedProperty(
            GoogleCalendarEventMapper.SELLI_SHARED_PROPERTY,
            "true".takeIf { shared },
        )
    }

    /** Generische Variante von [updateSelliSharedMarker] für beliebige geteilte Schlüssel. */
    private fun Event.setSharedProperty(key: String, value: String?) {
        val properties = extendedProperties ?: Event.ExtendedProperties()
        val sharedProperties = properties.shared.orEmpty().toMutableMap()
        if (value != null) sharedProperties[key] = value else sharedProperties.remove(key)
        properties.shared = sharedProperties.ifEmpty { null }
        extendedProperties = properties.takeIf {
            !it.shared.isNullOrEmpty() || !it.private.isNullOrEmpty()
        }
    }

    private companion object {
        const val PRIMARY_CALENDAR_ID = "primary"
        const val SEND_UPDATES_ALL = "all"
        const val SEND_UPDATES_NONE = "none"
    }
}
