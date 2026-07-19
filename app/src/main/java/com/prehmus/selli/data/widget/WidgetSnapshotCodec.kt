package com.prehmus.selli.data.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import java.time.LocalDateTime

class WidgetSnapshotCodec {
    fun encode(snapshot: WidgetSnapshot): Map<String, String> =
        buildMap {
            put(KEY_PARTNER_PERSON, snapshot.partnerPerson.name)
            put(KEY_PARTNER_DISPLAY_NAME, snapshot.partnerDisplayName)
            put(KEY_UPDATED_AT, snapshot.updatedAt.toString())
            put(KEY_NEXT_EVENT_PRESENT, (snapshot.nextEvent != null).toString())
            snapshot.nextEvent?.let { event -> putEvent(this, event) }
        }

    fun decode(values: Map<String, String>): WidgetSnapshot? =
        runCatching {
            val partnerPerson = Person.valueOf(values.require(KEY_PARTNER_PERSON))
            val partnerDisplayName = values.require(KEY_PARTNER_DISPLAY_NAME)
            val updatedAt = LocalDateTime.parse(values.require(KEY_UPDATED_AT))
            val nextEvent = when (values.require(KEY_NEXT_EVENT_PRESENT).toBooleanStrict()) {
                true -> values.readEvent()
                false -> null
            }

            WidgetSnapshot(
                partnerPerson = partnerPerson,
                partnerDisplayName = partnerDisplayName,
                nextEvent = nextEvent,
                updatedAt = updatedAt,
            )
        }.getOrNull()

    private fun putEvent(target: MutableMap<String, String>, event: CalendarEvent) {
        target[KEY_EVENT_ID] = event.id
        target[KEY_EVENT_TITLE] = event.title
        target[KEY_EVENT_START] = event.start.toString()
        target[KEY_EVENT_END] = event.end.toString()
        target[KEY_EVENT_IS_ALL_DAY] = event.isAllDay.toString()
        target[KEY_EVENT_SOURCE] = event.source.name
        target[KEY_EVENT_OWNER] = event.owner.name
        target[KEY_EVENT_IS_SHARED] = event.isSharedEvent.toString()
        event.location?.let { location -> target[KEY_EVENT_LOCATION] = location }
        event.description?.let { description -> target[KEY_EVENT_DESCRIPTION] = description }
    }

    private fun Map<String, String>.readEvent(): CalendarEvent =
        CalendarEvent(
            id = require(KEY_EVENT_ID),
            title = require(KEY_EVENT_TITLE),
            start = LocalDateTime.parse(require(KEY_EVENT_START)),
            end = LocalDateTime.parse(require(KEY_EVENT_END)),
            isAllDay = require(KEY_EVENT_IS_ALL_DAY).toBooleanStrict(),
            source = CalendarSource.valueOf(require(KEY_EVENT_SOURCE)),
            owner = Person.valueOf(require(KEY_EVENT_OWNER)),
            isSharedEvent = require(KEY_EVENT_IS_SHARED).toBooleanStrict(),
            location = this[KEY_EVENT_LOCATION],
            description = this[KEY_EVENT_DESCRIPTION],
        )

    private fun Map<String, String>.require(key: String): String =
        requireNotNull(this[key]) { "Missing widget snapshot key '$key'." }

    private companion object {
        const val KEY_PARTNER_PERSON = "partner_person"
        const val KEY_PARTNER_DISPLAY_NAME = "partner_display_name"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_NEXT_EVENT_PRESENT = "next_event_present"
        const val KEY_EVENT_ID = "next_event_id"
        const val KEY_EVENT_TITLE = "next_event_title"
        const val KEY_EVENT_START = "next_event_start"
        const val KEY_EVENT_END = "next_event_end"
        const val KEY_EVENT_IS_ALL_DAY = "next_event_is_all_day"
        const val KEY_EVENT_SOURCE = "next_event_source"
        const val KEY_EVENT_OWNER = "next_event_owner"
        const val KEY_EVENT_IS_SHARED = "next_event_is_shared"
        const val KEY_EVENT_LOCATION = "next_event_location"
        const val KEY_EVENT_DESCRIPTION = "next_event_description"
    }
}
