package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class GoogleCalendarEventCache(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<DateRange, Entry>()

    suspend fun load(
        range: DateRange,
        forceRefresh: Boolean,
        fetch: suspend () -> List<CalendarEvent>,
    ): List<CalendarEvent> = mutex.withLock {
        entries[range]?.takeIf { entry ->
            !forceRefresh && now() - entry.loadedAtMillis in 0 until CACHE_TTL_MILLIS
        }?.events ?: fetch().toList().also { events ->
            entries[range] = Entry(events, now())
        }
    }

    private data class Entry(
        val events: List<CalendarEvent>,
        val loadedAtMillis: Long,
    )

    private companion object {
        const val CACHE_TTL_MILLIS = 15 * 60 * 1_000L
    }
}
