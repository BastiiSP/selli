package com.prehmus.selli.data.notification

import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.notification.SharedEventFingerprint
import java.time.LocalDateTime

class SharedEventFingerprintCodec {
    fun encode(fingerprints: Map<EventKey, SharedEventFingerprint>): Map<String, String> =
        buildMap {
            val entries = fingerprints.entries.sortedWith(
                compareBy(
                    { it.key.source.name },
                    { it.key.eventId },
                ),
            )
            put(KEY_COUNT, entries.size.toString())
            entries.forEachIndexed { index, (key, fingerprint) ->
                putEntry(this, index, key, fingerprint)
            }
        }

    fun decode(values: Map<String, String>): Map<EventKey, SharedEventFingerprint> {
        val count = values[KEY_COUNT]?.toIntOrNull() ?: return emptyMap()

        return buildMap {
            repeat(count.coerceAtLeast(0)) { index ->
                readEntry(values, index)?.let { (key, fingerprint) ->
                    put(key, fingerprint)
                }
            }
        }
    }

    private fun putEntry(
        target: MutableMap<String, String>,
        index: Int,
        key: EventKey,
        fingerprint: SharedEventFingerprint,
    ) {
        val prefix = entryPrefix(index)
        target[prefix + KEY_SOURCE] = key.source.name
        target[prefix + KEY_EVENT_ID] = key.eventId
        target[prefix + KEY_TITLE] = fingerprint.title
        target[prefix + KEY_START] = fingerprint.start.toString()
        target[prefix + KEY_END] = fingerprint.end.toString()
        target[prefix + KEY_IS_ALL_DAY] = fingerprint.isAllDay.toString()
        target[prefix + KEY_LOCATION_PRESENT] = (fingerprint.location != null).toString()
        fingerprint.location?.let { target[prefix + KEY_LOCATION] = it }
        target[prefix + KEY_DESCRIPTION_PRESENT] = (fingerprint.description != null).toString()
        fingerprint.description?.let { target[prefix + KEY_DESCRIPTION] = it }
    }

    private fun readEntry(
        values: Map<String, String>,
        index: Int,
    ): Pair<EventKey, SharedEventFingerprint>? =
        runCatching {
            val prefix = entryPrefix(index)
            val key = EventKey(
                source = CalendarSource.valueOf(values.require(prefix + KEY_SOURCE)),
                eventId = values.require(prefix + KEY_EVENT_ID),
            )
            val fingerprint = SharedEventFingerprint(
                title = values.require(prefix + KEY_TITLE),
                start = LocalDateTime.parse(values.require(prefix + KEY_START)),
                end = LocalDateTime.parse(values.require(prefix + KEY_END)),
                isAllDay = values.require(prefix + KEY_IS_ALL_DAY).toBooleanStrict(),
                location = values.readNullable(
                    presentKey = prefix + KEY_LOCATION_PRESENT,
                    valueKey = prefix + KEY_LOCATION,
                ),
                description = values.readNullable(
                    presentKey = prefix + KEY_DESCRIPTION_PRESENT,
                    valueKey = prefix + KEY_DESCRIPTION,
                ),
            )
            key to fingerprint
        }.getOrNull()

    private fun Map<String, String>.readNullable(
        presentKey: String,
        valueKey: String,
    ): String? =
        when (require(presentKey).toBooleanStrict()) {
            true -> require(valueKey)
            false -> null
        }

    private fun Map<String, String>.require(key: String): String =
        requireNotNull(this[key]) { "Missing shared event fingerprint key '$key'." }

    private fun entryPrefix(index: Int): String = "event.$index."

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_SOURCE = "source"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_TITLE = "title"
        const val KEY_START = "start"
        const val KEY_END = "end"
        const val KEY_IS_ALL_DAY = "is_all_day"
        const val KEY_LOCATION_PRESENT = "location_present"
        const val KEY_LOCATION = "location"
        const val KEY_DESCRIPTION_PRESENT = "description_present"
        const val KEY_DESCRIPTION = "description"
    }
}
