package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPartnerEventSelectorTest {
    private val selector = NextPartnerEventSelector()
    private val now = LocalDateTime.of(2026, 7, 19, 12, 0)

    @Test
    fun `returns null for empty list`() {
        assertNull(selector.select(emptyList(), Person.MELLI, now))
    }

    @Test
    fun `returns null when list contains only events owned by other person`() {
        val events = listOf(event(id = "basti-later", owner = Person.BASTI))

        assertNull(selector.select(events, Person.MELLI, now))
    }

    @Test
    fun `selects running event before later event`() {
        val running = event(
            id = "running",
            start = now.minusHours(1),
            end = now.plusMinutes(30),
        )
        val later = event(
            id = "later",
            start = now.plusHours(1),
            end = now.plusHours(2),
        )

        assertEquals(running, selector.select(listOf(later, running), Person.MELLI, now))
    }

    @Test
    fun `ignores events that have already ended`() {
        val past = event(
            id = "past",
            start = now.minusHours(2),
            end = now,
        )
        val next = event(
            id = "next",
            start = now.plusMinutes(15),
            end = now.plusHours(1),
        )

        assertEquals(next, selector.select(listOf(past, next), Person.MELLI, now))
    }

    @Test
    fun `ignores non blocking all day event today`() {
        val birthdayToday = event(
            id = "all-day",
            start = now.toLocalDate().atStartOfDay(),
            end = now.toLocalDate().plusDays(1).atStartOfDay(),
            isAllDay = true,
            blocksSharedFreeTime = false,
        )
        val later = event(
            id = "later",
            start = now.plusHours(2),
            end = now.plusHours(3),
        )

        assertEquals(later, selector.select(listOf(later, birthdayToday), Person.MELLI, now))
        assertNull(selector.select(listOf(birthdayToday), Person.MELLI, now))
    }

    @Test
    fun `counts blocking all day event today`() {
        val vacationToday = event(
            id = "all-day",
            start = now.toLocalDate().atStartOfDay(),
            end = now.toLocalDate().plusDays(1).atStartOfDay(),
            isAllDay = true,
            blocksSharedFreeTime = true,
        )
        val later = event(
            id = "later",
            start = now.plusHours(2),
            end = now.plusHours(3),
        )

        assertEquals(vacationToday, selector.select(listOf(later, vacationToday), Person.MELLI, now))
    }

    @Test
    fun `uses title and id as deterministic tie breakers`() {
        val second = event(id = "a", title = "Zoo")
        val first = event(id = "b", title = "Alpha")

        assertEquals(first, selector.select(listOf(second, first), Person.MELLI, now))
    }

    private fun event(
        id: String,
        title: String = "Event",
        start: LocalDateTime = now.plusHours(1),
        end: LocalDateTime = now.plusHours(2),
        isAllDay: Boolean = false,
        owner: Person = Person.MELLI,
        blocksSharedFreeTime: Boolean = !isAllDay,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = start,
            end = end,
            isAllDay = isAllDay,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = owner,
            isSharedEvent = false,
            blocksSharedFreeTime = blocksSharedFreeTime,
        )
}
