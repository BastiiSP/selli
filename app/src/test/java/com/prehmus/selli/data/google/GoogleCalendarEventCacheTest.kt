package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarEventCacheTest {
    @Test
    fun `load reuses events for same range within TTL`() = runTest {
        var calls = 0
        val cache = GoogleCalendarEventCache(now = { 1_000L })

        cache.load(testRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }
        cache.load(testRange, forceRefresh = false) {
            calls += 1
            emptyList()
        }

        assertEquals(1, calls)
    }

    @Test
    fun `load force refreshes same range within TTL`() = runTest {
        var calls = 0
        val cache = GoogleCalendarEventCache(now = { 1_000L })

        cache.load(testRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }
        cache.load(testRange, forceRefresh = true) {
            calls += 1
            listOf(testEvent)
        }

        assertEquals(2, calls)
    }

    @Test
    fun `load refreshes same range after TTL`() = runTest {
        var currentTime = 1_000L
        var calls = 0
        val cache = GoogleCalendarEventCache(now = { currentTime })

        cache.load(testRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }
        currentTime += 15 * 60 * 1_000L
        cache.load(testRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }

        assertEquals(2, calls)
    }

    @Test
    fun `load caches ranges independently`() = runTest {
        var calls = 0
        val cache = GoogleCalendarEventCache(now = { 1_000L })
        val otherRange = DateRange(
            start = LocalDate.of(2026, 8, 1),
            endInclusive = LocalDate.of(2026, 8, 31),
        )

        cache.load(testRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }
        cache.load(otherRange, forceRefresh = false) {
            calls += 1
            listOf(testEvent)
        }

        assertEquals(2, calls)
    }

    private companion object {
        val testRange = DateRange(
            start = LocalDate.of(2026, 7, 1),
            endInclusive = LocalDate.of(2026, 7, 31),
        )
        val testEvent = CalendarEvent(
            id = "google",
            title = "Google event",
            start = LocalDateTime.of(2026, 7, 18, 10, 0),
            end = LocalDateTime.of(2026, 7, 18, 11, 0),
            isAllDay = false,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            isSharedEvent = false,
        )
    }
}
