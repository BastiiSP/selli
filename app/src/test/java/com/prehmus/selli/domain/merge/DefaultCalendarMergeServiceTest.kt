package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.CalendarAuthRequiredException
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCalendarMergeServiceTest {

    @Test
    fun mergedEvents_mergesBothSources() = runTest {
        val googleEvent = event(
            id = "google",
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
        )
        val icsEvent = event(
            id = "ics",
            source = CalendarSource.WORK_ICS,
            owner = Person.BASTI,
        )
        val service = service(
            googleEvents = listOf(googleEvent),
            icsEvents = listOf(icsEvent),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(listOf(googleEvent, icsEvent), result)
    }

    @Test
    fun mergedEvents_sortsAllDayEventsBeforeTimedEventsThenByStart() = runTest {
        val timedEarly = event(
            id = "timed-early",
            start = dateTime(hour = 8),
            end = dateTime(hour = 9),
        )
        val allDayLate = event(
            id = "all-day-late",
            start = dateTime(hour = 12),
            end = dateTime(hour = 13),
            isAllDay = true,
        )
        val allDayEarly = event(
            id = "all-day-early",
            start = dateTime(hour = 0),
            end = dateTime(hour = 23),
            isAllDay = true,
        )
        val timedLate = event(
            id = "timed-late",
            start = dateTime(hour = 20),
            end = dateTime(hour = 21),
        )
        val service = service(
            googleEvents = listOf(timedLate, allDayLate),
            icsEvents = listOf(timedEarly, allDayEarly),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(
            listOf(allDayEarly, allDayLate, timedEarly, timedLate),
            result,
        )
    }

    @Test
    fun mergedEvents_deduplicatesByIdAndSource() = runTest {
        val original = event(id = "same", source = CalendarSource.GOOGLE_OWN)
        val duplicate = original.copy(title = "Changed title")
        val sameIdDifferentSource = original.copy(source = CalendarSource.WORK_ICS)
        val service = service(
            googleEvents = listOf(original, duplicate),
            icsEvents = listOf(sameIdDifferentSource),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(listOf(original, sameIdDifferentSource), result)
    }

    @Test
    fun mergedEvents_returnsIcsEventsWhenGoogleFails() = runTest {
        val icsEvent = event(id = "ics", source = CalendarSource.WORK_ICS)
        val service = service(
            googleFailure = IllegalStateException("Google unavailable"),
            icsEvents = listOf(icsEvent),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(listOf(icsEvent), result)
    }

    @Test
    fun mergedEvents_returnsGoogleEventsWhenIcsFails() = runTest {
        val googleEvent = event(id = "google", source = CalendarSource.GOOGLE_OWN)
        val service = service(
            googleEvents = listOf(googleEvent),
            icsFailure = IllegalStateException("ICS unavailable"),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(listOf(googleEvent), result)
    }

    @Test
    fun mergedEvents_rethrowsCalendarAuthRequiredException() = runTest {
        val authException = CalendarAuthRequiredException("Consent needed")
        val service = service(googleFailure = authException)

        val thrown = try {
            service.mergedEvents(testRange)
            null
        } catch (error: CalendarAuthRequiredException) {
            error
        }

        assertTrue(thrown is CalendarAuthRequiredException)
        assertEquals("Consent needed", thrown?.message)
    }

    @Test
    fun isBothFree_returnsTrueWhenDayIsEmpty() = runTest {
        val service = service()

        val result = service.isBothFree(testDay)

        assertTrue(result)
    }

    @Test
    fun isBothFree_returnsTrueWhenEveningEventLeavesThreeHoursDuringDay() = runTest {
        val service = service(
            googleEvents = listOf(
                event(
                    id = "evening",
                    start = dateTime(hour = 19),
                    end = dateTime(hour = 21),
                ),
            ),
        )

        val result = service.isBothFree(testDay)

        assertTrue(result)
    }

    @Test
    fun isBothFree_returnsFalseWhenOnlyGapsShorterThanThreeHoursRemain() = runTest {
        val service = service(
            googleEvents = listOf(
                event(
                    id = "morning",
                    start = dateTime(hour = 9),
                    end = testDay.atTime(11, 0),
                ),
                event(
                    id = "afternoon",
                    start = testDay.atTime(13, 30),
                    end = testDay.atTime(16, 0),
                ),
                event(
                    id = "evening",
                    start = testDay.atTime(18, 30),
                    end = dateTime(hour = 22),
                ),
            ),
        )

        val result = service.isBothFree(testDay)

        assertFalse(result)
    }

    @Test
    fun isBothFree_mergesOverlappingEventsFromBothPeople() = runTest {
        val service = service(
            googleEvents = listOf(
                event(
                    id = "basti",
                    start = dateTime(hour = 9),
                    end = dateTime(hour = 13),
                    owner = Person.BASTI,
                ),
                event(
                    id = "melli",
                    start = dateTime(hour = 12),
                    end = dateTime(hour = 19),
                    source = CalendarSource.GOOGLE_PARTNER,
                    owner = Person.MELLI,
                ),
            ),
            icsEvents = listOf(
                event(
                    id = "work",
                    start = dateTime(hour = 19),
                    end = dateTime(hour = 20),
                    source = CalendarSource.WORK_ICS,
                    owner = Person.BASTI,
                ),
            ),
        )

        val result = service.isBothFree(testDay)

        assertFalse(result)
    }

    @Test
    fun isBothFree_returnsTrueWhenOnlyAllDayEventExists() = runTest {
        val service = service(
            googleEvents = listOf(
                event(
                    id = "all-day",
                    start = testDay.atStartOfDay(),
                    end = testDay.plusDays(1).atStartOfDay(),
                    isAllDay = true,
                ),
            ),
        )

        val result = service.isBothFree(testDay)

        assertTrue(result)
    }

    @Test
    fun isBothFree_returnsTrueWhenEventIsCompletelyOutsideFreeWindow() = runTest {
        val service = service(
            googleEvents = listOf(
                event(
                    id = "late",
                    start = dateTime(hour = 23),
                    end = testDay.plusDays(1).atTime(1, 0),
                ),
            ),
        )

        val result = service.isBothFree(testDay)

        assertTrue(result)
    }

    private fun service(
        googleEvents: List<CalendarEvent> = emptyList(),
        icsEvents: List<CalendarEvent> = emptyList(),
        googleFailure: Throwable? = null,
        icsFailure: Throwable? = null,
    ): DefaultCalendarMergeService =
        DefaultCalendarMergeService(
            googleCalendarRepository = FakeGoogleCalendarRepository(
                events = googleEvents,
                failure = googleFailure,
            ),
            icsCalendarRepository = FakeIcsCalendarRepository(
                events = icsEvents,
                failure = icsFailure,
            ),
        )

    private fun event(
        id: String,
        title: String = id,
        start: LocalDateTime = dateTime(hour = 10),
        end: LocalDateTime = dateTime(hour = 11),
        isAllDay: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE_OWN,
        owner: Person = Person.BASTI,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = start,
            end = end,
            isAllDay = isAllDay,
            source = source,
            owner = owner,
            isSharedEvent = false,
        )

    private class FakeGoogleCalendarRepository(
        private val events: List<CalendarEvent>,
        private val failure: Throwable?,
    ) : GoogleCalendarRepository {

        override suspend fun signIn(): AuthResult =
            error("Not needed for merge tests")

        override suspend fun grantMutualAccess(
            ownAccount: Account,
            partnerAccount: Account,
        ): Result<Unit> =
            error("Not needed for merge tests")

        override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> {
            failure?.let { throw it }
            return events
        }
    }

    private class FakeIcsCalendarRepository(
        private val events: List<CalendarEvent>,
        private val failure: Throwable?,
    ) : IcsCalendarRepository {

        override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> {
            failure?.let { throw it }
            return events
        }
    }

    private companion object {
        val testDay: LocalDate = LocalDate.of(2026, 7, 18)
        val testRange: DateRange = DateRange(
            start = testDay,
            endInclusive = testDay,
        )

        fun dateTime(hour: Int): LocalDateTime =
            testDay.atTime(hour, 0)
    }
}
