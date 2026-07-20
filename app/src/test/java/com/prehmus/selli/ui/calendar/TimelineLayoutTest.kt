package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineLayoutTest {

    // --- layoutDay: all-day / spanning ------------------------------------

    @Test
    fun layoutDay_putsAllDayEventInAllDayNotTimed() {
        val allDay = event(
            id = "all-day",
            start = testDay.atStartOfDay(),
            end = testDay.plusDays(1).atStartOfDay(),
            isAllDay = true,
        )

        val timeline = layoutDay(listOf(allDay), testDay)

        assertEquals(listOf(allDay), timeline.allDay)
        assertTrue(timeline.timed.isEmpty())
    }

    @Test
    fun layoutDay_treatsEventSpanningEntireDayAsAllDay() {
        // Not flagged isAllDay, but start <= 00:00 and end >= next-day-00:00.
        val spanning = event(
            id = "spanning",
            start = testDay.atStartOfDay(),
            end = testDay.plusDays(1).atStartOfDay(),
            isAllDay = false,
        )

        val timeline = layoutDay(listOf(spanning), testDay)

        assertEquals(listOf(spanning), timeline.allDay)
        assertTrue(timeline.timed.isEmpty())
    }

    // --- layoutDay: minutes & clipping ------------------------------------

    @Test
    fun layoutDay_simpleTimedEventGetsMinutesAndSingleColumn() {
        val meeting = event(
            id = "meeting",
            start = testDay.atTime(10, 0),
            end = testDay.atTime(11, 0),
        )

        val positioned = layoutDay(listOf(meeting), testDay).timed.single()

        assertEquals(600, positioned.startMinute)
        assertEquals(660, positioned.endMinute)
        assertEquals(0, positioned.column)
        assertEquals(1, positioned.columnCount)
    }

    @Test
    fun layoutDay_eventStartingPreviousDayClipsStartToZero() {
        val overnight = event(
            id = "overnight",
            start = testDay.minusDays(1).atTime(22, 0),
            end = testDay.atTime(9, 0),
        )

        val positioned = layoutDay(listOf(overnight), testDay).timed.single()

        assertEquals(0, positioned.startMinute)
        assertEquals(540, positioned.endMinute)
    }

    @Test
    fun layoutDay_eventEndingNextDayClipsEndToMinutesPerDay() {
        val lateNight = event(
            id = "late",
            start = testDay.atTime(22, 0),
            end = testDay.plusDays(1).atTime(1, 0),
        )

        val positioned = layoutDay(listOf(lateNight), testDay).timed.single()

        assertEquals(1320, positioned.startMinute)
        assertEquals(MINUTES_PER_DAY, positioned.endMinute)
    }

    @Test
    fun layoutDay_skipsEventEntirelyBeforeTheDay() {
        val yesterday = event(
            id = "yesterday",
            start = testDay.minusDays(1).atTime(8, 0),
            end = testDay.minusDays(1).atTime(9, 0),
        )

        val timeline = layoutDay(listOf(yesterday), testDay)

        assertTrue(timeline.timed.isEmpty())
        assertTrue(timeline.allDay.isEmpty())
    }

    // --- layoutDay: column assignment -------------------------------------

    @Test
    fun layoutDay_nonOverlappingEventsEachGetSingleColumn() {
        val morning = event(id = "morning", start = testDay.atTime(10, 0), end = testDay.atTime(11, 0))
        val noon = event(id = "noon", start = testDay.atTime(12, 0), end = testDay.atTime(13, 0))

        val positioned = layoutDay(listOf(morning, noon), testDay).timed.associateBy { it.event.id }

        assertEquals(1, positioned.getValue("morning").columnCount)
        assertEquals(1, positioned.getValue("noon").columnCount)
        assertEquals(0, positioned.getValue("morning").column)
        assertEquals(0, positioned.getValue("noon").column)
    }

    @Test
    fun layoutDay_twoOverlappingEventsSplitIntoTwoColumns() {
        val first = event(id = "first", start = testDay.atTime(10, 0), end = testDay.atTime(12, 0))
        val second = event(id = "second", start = testDay.atTime(11, 0), end = testDay.atTime(13, 0))

        val positioned = layoutDay(listOf(first, second), testDay).timed.associateBy { it.event.id }

        assertEquals(2, positioned.getValue("first").columnCount)
        assertEquals(2, positioned.getValue("second").columnCount)
        assertEquals(0, positioned.getValue("first").column)
        assertEquals(1, positioned.getValue("second").column)
    }

    @Test
    fun layoutDay_threeMutuallyOverlappingEventsGetThreeColumns() {
        val a = event(id = "a", start = testDay.atTime(10, 0), end = testDay.atTime(13, 0))
        val b = event(id = "b", start = testDay.atTime(10, 30), end = testDay.atTime(13, 0))
        val c = event(id = "c", start = testDay.atTime(11, 0), end = testDay.atTime(13, 0))

        val positioned = layoutDay(listOf(a, b, c), testDay).timed.associateBy { it.event.id }

        assertEquals(3, positioned.getValue("a").columnCount)
        assertEquals(0, positioned.getValue("a").column)
        assertEquals(1, positioned.getValue("b").column)
        assertEquals(2, positioned.getValue("c").column)
    }

    @Test
    fun layoutDay_staircaseFormsOneClusterAndReusesFreedColumn() {
        // A(10:00-11:00) overlaps B(10:30-12:00) overlaps C(11:30-13:00),
        // but A and C do NOT overlap. They still form ONE transitive cluster.
        //
        // Greedy column assignment over columnEnds (each = effectiveEnd):
        //   A start=600 -> no free column -> new column 0, ends 660
        //   B start=630 -> column 0 (ends 660) not free (660 !<= 630) -> new column 1, ends 720
        //   C start=690 -> column 0 (ends 660) IS free (660 <= 690) -> reuse column 0
        // Only two columns were ever created, so columnCount == 2 for all three.
        val a = event(id = "a", start = testDay.atTime(10, 0), end = testDay.atTime(11, 0))
        val b = event(id = "b", start = testDay.atTime(10, 30), end = testDay.atTime(12, 0))
        val c = event(id = "c", start = testDay.atTime(11, 30), end = testDay.atTime(13, 0))

        val timeline = layoutDay(listOf(a, b, c), testDay)
        val positioned = timeline.timed.associateBy { it.event.id }

        // One cluster => identical (maximum) columnCount shared by every member.
        assertEquals(2, positioned.getValue("a").columnCount)
        assertEquals(2, positioned.getValue("b").columnCount)
        assertEquals(2, positioned.getValue("c").columnCount)

        assertEquals(0, positioned.getValue("a").column)
        assertEquals(1, positioned.getValue("b").column)
        // C reuses the column freed by A.
        assertEquals(0, positioned.getValue("c").column)
    }

    // --- PositionedEvent.durationMinutes ----------------------------------

    @Test
    fun durationMinutes_returnsAtLeastOneForZeroLengthEvent() {
        val zeroLength = PositionedEvent(
            event = event(id = "point", start = testDay.atTime(10, 0), end = testDay.atTime(10, 0)),
            startMinute = 600,
            endMinute = 600,
            column = 0,
            columnCount = 1,
        )

        assertEquals(1, zeroLength.durationMinutes)
    }

    @Test
    fun durationMinutes_returnsSpanForNormalEvent() {
        val positioned = PositionedEvent(
            event = event(id = "meeting"),
            startMinute = 600,
            endMinute = 660,
            column = 0,
            columnCount = 1,
        )

        assertEquals(60, positioned.durationMinutes)
    }

    // --- weekDays ----------------------------------------------------------

    @Test
    fun weekDays_returnsMondayThroughSundayForAnchor() {
        // 2026-07-22 is a Wednesday; its Monday is 2026-07-20.
        val week = weekDays(LocalDate.of(2026, 7, 22))

        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 7, 20), week.first())
        assertEquals(LocalDate.of(2026, 7, 26), week.last())
        assertEquals(java.time.DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, week.last().dayOfWeek)
    }

    // --- fetchRangeFor -----------------------------------------------------

    @Test
    fun fetchRangeFor_monthCoversMonthPaddedBySevenDays() {
        val range = fetchRangeFor(
            mode = CalendarViewMode.MONTH,
            visibleMonth = YearMonth.of(2026, 7),
            anchorDay = LocalDate.of(2026, 7, 22),
        )

        assertEquals(LocalDate.of(2026, 6, 24), range.start)
        assertEquals(LocalDate.of(2026, 8, 7), range.endInclusive)
    }

    @Test
    fun fetchRangeFor_weekCoversMondayMinusOneThroughSundayPlusOne() {
        val range = fetchRangeFor(
            mode = CalendarViewMode.WEEK,
            visibleMonth = YearMonth.of(2026, 7),
            anchorDay = LocalDate.of(2026, 7, 22),
        )

        // Week Mon 2026-07-20 .. Sun 2026-07-26, padded by one day on each side.
        assertEquals(LocalDate.of(2026, 7, 19), range.start)
        assertEquals(LocalDate.of(2026, 7, 27), range.endInclusive)
    }

    @Test
    fun fetchRangeFor_dayCoversAnchorPaddedByOneDay() {
        val range = fetchRangeFor(
            mode = CalendarViewMode.DAY,
            visibleMonth = YearMonth.of(2026, 7),
            anchorDay = LocalDate.of(2026, 7, 22),
        )

        assertEquals(LocalDate.of(2026, 7, 21), range.start)
        assertEquals(LocalDate.of(2026, 7, 23), range.endInclusive)
    }

    private companion object {
        val testDay: LocalDate = LocalDate.of(2026, 7, 22)

        fun event(
            id: String,
            title: String = id,
            start: LocalDateTime = testDay.atTime(10, 0),
            end: LocalDateTime = testDay.atTime(11, 0),
            isAllDay: Boolean = false,
        ): CalendarEvent =
            CalendarEvent(
                id = id,
                title = title,
                start = start,
                end = end,
                isAllDay = isAllDay,
                source = CalendarSource.GOOGLE_OWN,
                owner = Person.BASTI,
                isSharedEvent = false,
            )
    }
}
