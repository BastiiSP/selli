package com.prehmus.selli.data.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.FreeSlot
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.WidgetSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetSnapshotCodec {
    fun encode(snapshot: WidgetSnapshot): Map<String, String> =
        buildMap {
            put(KEY_PARTNER_PERSON, snapshot.partnerPerson.name)
            put(KEY_PARTNER_DISPLAY_NAME, snapshot.partnerDisplayName)
            put(KEY_UPDATED_AT, snapshot.updatedAt.toString())
            put(KEY_NEXT_EVENT_PRESENT, (snapshot.nextEvent != null).toString())
            snapshot.nextEvent?.let { event -> putEvent(this, KEY_NEXT_EVENT_PREFIX, event) }
            put(KEY_NEXT_SHARED_EVENT_PRESENT, (snapshot.nextSharedEvent != null).toString())
            snapshot.nextSharedEvent?.let { event ->
                putEvent(this, KEY_NEXT_SHARED_EVENT_PREFIX, event)
            }
            put(KEY_FREE_SLOT_PRESENT, (snapshot.nextFreeSlot != null).toString())
            snapshot.nextFreeSlot?.let { slot ->
                put(KEY_FREE_SLOT_DAY, slot.day.toString())
                put(KEY_FREE_SLOT_START, slot.block.start.toString())
                put(KEY_FREE_SLOT_END, slot.block.end.toString())
            }
        }

    fun decode(values: Map<String, String>): WidgetSnapshot? =
        runCatching {
            val partnerPerson = Person.valueOf(values.require(KEY_PARTNER_PERSON))
            val partnerDisplayName = values.require(KEY_PARTNER_DISPLAY_NAME)
            val updatedAt = LocalDateTime.parse(values.require(KEY_UPDATED_AT))
            val nextEvent = when (values.require(KEY_NEXT_EVENT_PRESENT).toBooleanStrict()) {
                true -> values.readEvent(KEY_NEXT_EVENT_PREFIX)
                false -> null
            }
            val nextSharedEvent =
                when ((values[KEY_NEXT_SHARED_EVENT_PRESENT] ?: "false").toBooleanStrict()) {
                    true -> values.readEvent(KEY_NEXT_SHARED_EVENT_PREFIX)
                    false -> null
                }
            val nextFreeSlot =
                when ((values[KEY_FREE_SLOT_PRESENT] ?: "false").toBooleanStrict()) {
                    true -> values.readFreeSlot()
                    false -> null
                }

            WidgetSnapshot(
                partnerPerson = partnerPerson,
                partnerDisplayName = partnerDisplayName,
                nextEvent = nextEvent,
                updatedAt = updatedAt,
                nextSharedEvent = nextSharedEvent,
                nextFreeSlot = nextFreeSlot,
            )
        }.getOrNull()

    private fun putEvent(
        target: MutableMap<String, String>,
        keyPrefix: String,
        event: CalendarEvent,
    ) {
        target[keyPrefix + KEY_EVENT_ID] = event.id
        target[keyPrefix + KEY_EVENT_TITLE] = event.title
        target[keyPrefix + KEY_EVENT_START] = event.start.toString()
        target[keyPrefix + KEY_EVENT_END] = event.end.toString()
        target[keyPrefix + KEY_EVENT_IS_ALL_DAY] = event.isAllDay.toString()
        target[keyPrefix + KEY_EVENT_SOURCE] = event.source.name
        target[keyPrefix + KEY_EVENT_OWNER] = event.owner.name
        target[keyPrefix + KEY_EVENT_IS_SHARED] = event.isSharedEvent.toString()
        target[keyPrefix + KEY_EVENT_CATEGORY] = event.category.name
        event.location?.let { location -> target[keyPrefix + KEY_EVENT_LOCATION] = location }
        event.description?.let { description ->
            target[keyPrefix + KEY_EVENT_DESCRIPTION] = description
        }
    }

    private fun Map<String, String>.readEvent(keyPrefix: String): CalendarEvent =
        CalendarEvent(
            id = require(keyPrefix + KEY_EVENT_ID),
            title = require(keyPrefix + KEY_EVENT_TITLE),
            start = LocalDateTime.parse(require(keyPrefix + KEY_EVENT_START)),
            end = LocalDateTime.parse(require(keyPrefix + KEY_EVENT_END)),
            isAllDay = require(keyPrefix + KEY_EVENT_IS_ALL_DAY).toBooleanStrict(),
            source = CalendarSource.valueOf(require(keyPrefix + KEY_EVENT_SOURCE)),
            owner = Person.valueOf(require(keyPrefix + KEY_EVENT_OWNER)),
            isSharedEvent = require(keyPrefix + KEY_EVENT_IS_SHARED).toBooleanStrict(),
            location = this[keyPrefix + KEY_EVENT_LOCATION],
            description = this[keyPrefix + KEY_EVENT_DESCRIPTION],
            category =
                EventCategory.valueOf(
                    this[keyPrefix + KEY_EVENT_CATEGORY] ?: EventCategory.PRIVATE.name,
                ),
        )

    private fun Map<String, String>.readFreeSlot(): FreeSlot =
        FreeSlot(
            day = LocalDate.parse(require(KEY_FREE_SLOT_DAY)),
            block = FreeTimeBlock(
                start = LocalDateTime.parse(require(KEY_FREE_SLOT_START)),
                end = LocalDateTime.parse(require(KEY_FREE_SLOT_END)),
            ),
        )

    private fun Map<String, String>.require(key: String): String =
        requireNotNull(this[key]) { "Missing widget snapshot key '$key'." }

    private companion object {
        const val KEY_PARTNER_PERSON = "partner_person"
        const val KEY_PARTNER_DISPLAY_NAME = "partner_display_name"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_NEXT_EVENT_PRESENT = "next_event_present"
        const val KEY_NEXT_SHARED_EVENT_PRESENT = "next_shared_event_present"
        const val KEY_FREE_SLOT_PRESENT = "free_slot_present"
        const val KEY_FREE_SLOT_DAY = "free_slot_day"
        const val KEY_FREE_SLOT_START = "free_slot_start"
        const val KEY_FREE_SLOT_END = "free_slot_end"

        const val KEY_NEXT_EVENT_PREFIX = "next_event_"
        const val KEY_NEXT_SHARED_EVENT_PREFIX = "next_shared_event_"
        const val KEY_EVENT_ID = "id"
        const val KEY_EVENT_TITLE = "title"
        const val KEY_EVENT_START = "start"
        const val KEY_EVENT_END = "end"
        const val KEY_EVENT_IS_ALL_DAY = "is_all_day"
        const val KEY_EVENT_SOURCE = "source"
        const val KEY_EVENT_OWNER = "owner"
        const val KEY_EVENT_IS_SHARED = "is_shared"
        const val KEY_EVENT_LOCATION = "location"
        const val KEY_EVENT_DESCRIPTION = "description"
        const val KEY_EVENT_CATEGORY = "category"
    }
}
