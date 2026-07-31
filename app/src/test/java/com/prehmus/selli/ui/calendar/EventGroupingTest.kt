package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EventGroupingTest {
    private val day = LocalDate.of(2026, 7, 31)

    @Test
    fun `keeps input order when day has no together event`() {
        val first = event(id = "first", startHour = 10, endHour = 11)
        val second = event(id = "second", startHour = 9, endHour = 10)

        assertEquals(
            listOf(EventListRow.Single(first), EventListRow.Single(second)),
            listOf(first, second).toEventListRows(),
        )
    }

    @Test
    fun `keeps together event without overlap as single`() {
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )

        assertEquals(
            listOf(EventListRow.Single(wirTermin)),
            listOf(wirTermin).toEventListRows(),
        )
    }

    @Test
    fun `groups overlapping event and leaves later event single`() {
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val overlap = event(id = "overlap", startHour = 10, endHour = 11)
        val evening = event(id = "evening", startHour = 18, endHour = 19)

        assertEquals(
            listOf(
                EventListRow.Group(wirTermin, listOf(overlap)),
                EventListRow.Single(evening),
            ),
            listOf(wirTermin, overlap, evening).toEventListRows(),
        )
    }

    @Test
    fun `groups all and only overlapping events in chronological order`() {
        val allDay = event(
            id = "all-day",
            title = "All day",
            startHour = 0,
            endHour = 0,
            endDayOffset = 1,
            isAllDay = true,
        )
        val early = event(id = "early", title = "Early", startHour = 8, endHour = 10)
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val sameStartSecond = event(id = "z", title = "Beta", startHour = 14, endHour = 15)
        val sameStartFirst = event(id = "a", title = "Alpha", startHour = 14, endHour = 16)
        val evening = event(id = "evening", startHour = 18, endHour = 19)

        assertEquals(
            listOf(
                EventListRow.Group(
                    wirTermin,
                    listOf(allDay, early, sameStartFirst, sameStartSecond),
                ),
                EventListRow.Single(evening),
            ),
            listOf(
                allDay,
                early,
                wirTermin,
                sameStartSecond,
                sameStartFirst,
                evening,
            ).toEventListRows(),
        )
    }

    @Test
    fun `uses earlier overlapping together event as group head`() {
        val earlier = event(
            id = "earlier",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val later = event(
            id = "later",
            startHour = 10,
            endHour = 12,
            category = EventCategory.TOGETHER,
        )

        assertEquals(
            listOf(EventListRow.Group(earlier, listOf(later))),
            listOf(earlier, later).toEventListRows(),
        )
    }

    @Test
    fun `keeps event starting before together event under group head`() {
        val overlap = event(
            id = "overlap",
            startHour = 8,
            startMinute = 30,
            endHour = 9,
            endMinute = 30,
        )
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val before = event(id = "before", startHour = 7, endHour = 8)

        assertEquals(
            listOf(
                EventListRow.Single(before),
                EventListRow.Group(wirTermin, listOf(overlap)),
            ),
            listOf(before, overlap, wirTermin).toEventListRows(),
        )
    }

    @Test
    fun `resolves grouping after time or category changes`() {
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val overlap = event(id = "overlap", startHour = 10, endHour = 11)
        assertEquals(
            listOf(EventListRow.Group(wirTermin, listOf(overlap))),
            listOf(wirTermin, overlap).toEventListRows(),
        )

        val moved = overlap.copy(
            start = day.atTime(18, 0),
            end = day.atTime(19, 0),
        )
        assertEquals(
            listOf(EventListRow.Single(wirTermin), EventListRow.Single(moved)),
            listOf(wirTermin, moved).toEventListRows(),
        )

        val privateTermin = wirTermin.copy(category = EventCategory.PRIVATE)
        assertEquals(
            listOf(EventListRow.Single(privateTermin), EventListRow.Single(overlap)),
            listOf(privateTermin, overlap).toEventListRows(),
        )
    }

    @Test
    fun `does not group events whose boundaries only touch`() {
        val endingAtStart = event(id = "before", startHour = 8, endHour = 9)
        val wirTermin = event(
            id = "together",
            startHour = 9,
            endHour = 17,
            category = EventCategory.TOGETHER,
        )
        val startingAtEnd = event(id = "after", startHour = 17, endHour = 18)

        assertEquals(
            listOf(
                EventListRow.Single(endingAtStart),
                EventListRow.Single(wirTermin),
                EventListRow.Single(startingAtEnd),
            ),
            listOf(endingAtStart, wirTermin, startingAtEnd).toEventListRows(),
        )
    }

    private fun event(
        id: String,
        title: String = id,
        startHour: Int,
        startMinute: Int = 0,
        endHour: Int,
        endMinute: Int = 0,
        endDayOffset: Long = 0,
        isAllDay: Boolean = false,
        category: EventCategory = EventCategory.PRIVATE,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = day.atTime(startHour, startMinute),
            end = day.plusDays(endDayOffset).atTime(endHour, endMinute),
            isAllDay = isAllDay,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            isSharedEvent = category == EventCategory.TOGETHER,
            category = category,
        )
}
