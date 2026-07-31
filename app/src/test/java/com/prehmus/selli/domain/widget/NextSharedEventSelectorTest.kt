package com.prehmus.selli.domain.widget

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextSharedEventSelectorTest {
    private val selector = NextSharedEventSelector()
    private val now = LocalDateTime.of(2026, 7, 31, 12, 0)

    @Test
    fun `selects earliest together event regardless of owner`() {
        val ownLater = event(
            id = "own-later",
            owner = Person.BASTI,
            start = now.plusHours(2),
            end = now.plusHours(3),
        )
        val partnerEarlier = event(
            id = "partner-earlier",
            owner = Person.MELLI,
            start = now.plusHours(1),
            end = now.plusHours(2),
        )
        val ownEarlier = event(
            id = "own-earlier",
            owner = Person.BASTI,
            start = now.plusMinutes(30),
            end = now.plusHours(1),
        )

        assertEquals(partnerEarlier, selector.select(listOf(ownLater, partnerEarlier), now))
        assertEquals(ownEarlier, selector.select(listOf(partnerEarlier, ownEarlier), now))
    }

    @Test
    fun `ignores private and work events`() {
        val private = event(
            id = "private",
            category = EventCategory.PRIVATE,
            start = now.plusMinutes(15),
            end = now.plusMinutes(45),
        )
        val work = event(
            id = "work",
            category = EventCategory.WORK,
            start = now.plusMinutes(30),
            end = now.plusHours(1),
        )
        val together = event(
            id = "together",
            start = now.plusHours(1),
            end = now.plusHours(2),
        )

        assertEquals(together, selector.select(listOf(private, work, together), now))
    }

    @Test
    fun `ignores ended events and keeps running event`() {
        val endedBeforeNow = event(
            id = "ended-before",
            start = now.minusHours(2),
            end = now.minusMinutes(1),
        )
        val endedAtNow = event(
            id = "ended-at-now",
            start = now.minusHours(1),
            end = now,
        )
        val running = event(
            id = "running",
            start = now.minusMinutes(30),
            end = now.plusMinutes(30),
        )
        val later = event(
            id = "later",
            start = now.plusHours(1),
            end = now.plusHours(2),
        )

        assertEquals(running, selector.select(listOf(endedBeforeNow, endedAtNow, later, running), now))
    }

    @Test
    fun `returns null when no qualifying event exists`() {
        val private = event(id = "private", category = EventCategory.PRIVATE)
        val endedTogether = event(
            id = "ended",
            start = now.minusHours(2),
            end = now,
        )

        assertNull(selector.select(listOf(private, endedTogether), now))
    }

    @Test
    fun `uses title and id as deterministic tie breakers`() {
        val zoo = event(id = "a", title = "Zoo")
        val alphaWithLaterId = event(id = "b", title = "Alpha")
        val alphaWithEarlierId = event(id = "a", title = "Alpha")

        assertEquals(alphaWithLaterId, selector.select(listOf(zoo, alphaWithLaterId), now))
        assertEquals(alphaWithEarlierId, selector.select(listOf(alphaWithLaterId, alphaWithEarlierId), now))
    }

    private fun event(
        id: String,
        title: String = "Event",
        start: LocalDateTime = now.plusHours(1),
        end: LocalDateTime = now.plusHours(2),
        owner: Person = Person.MELLI,
        category: EventCategory = EventCategory.TOGETHER,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = start,
            end = end,
            isAllDay = false,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = owner,
            isSharedEvent = category == EventCategory.TOGETHER,
            category = category,
        )
}
