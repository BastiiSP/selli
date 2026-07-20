package com.prehmus.selli.domain.merge

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.CalendarAuthRequiredException
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.EventCustomizationRepository
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

        assertEquals(
            listOf(
                googleEvent,
                icsEvent.copy(category = EventCategory.WORK),
            ),
            result,
        )
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

        assertEquals(
            listOf(
                original,
                sameIdDifferentSource.copy(category = EventCategory.WORK),
            ),
            result,
        )
    }

    @Test
    fun mergedEvents_returnsIcsEventsWhenGoogleFails() = runTest {
        val icsEvent = event(id = "ics", source = CalendarSource.WORK_ICS)
        val service = service(
            googleFailure = IllegalStateException("Google unavailable"),
            icsEvents = listOf(icsEvent),
        )

        val result = service.mergedEvents(testRange)

        assertEquals(listOf(icsEvent.copy(category = EventCategory.WORK)), result)
    }

    @Test
    fun mergedEvents_logsFetchFailureWithSourceAndCause() = runTest {
        val failure = IllegalStateException("Google unavailable")
        val logger = FakeCalendarLogger()
        val service = service(
            googleFailure = failure,
            logger = logger,
        )

        service.mergedEvents(testRange)

        assertEquals(1, logger.errors.size)
        assertEquals("Google calendars", logger.errors.single().source)
        assertTrue(logger.errors.single().cause is IllegalStateException)
        assertEquals("Google unavailable", logger.errors.single().cause.message)
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
        val logger = FakeCalendarLogger()
        val service = service(
            googleFailure = authException,
            logger = logger,
        )

        val thrown = try {
            service.mergedEvents(testRange)
            null
        } catch (error: CalendarAuthRequiredException) {
            error
        }

        assertTrue(thrown is CalendarAuthRequiredException)
        assertEquals("Consent needed", thrown?.message)
        assertTrue(logger.errors.isEmpty())
    }

    @Test
    fun mergedEvents_hidesMatchingOccurrence() = runTest {
        val hidden = event(id = "hidden")
        val visible = event(id = "visible")
        val service = service(
            googleEvents = listOf(hidden, visible),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(
                        EventKey(hidden.source, hidden.id),
                    ),
                    hidden = true,
                ),
            ),
        )

        assertEquals(listOf(visible), service.mergedEvents(testRange))
    }

    @Test
    fun mergedEvents_hidesSeriesFromBoundaryWithoutHidingPastOccurrences() = runTest {
        val past = event(id = "past", start = dateTime(hour = 9), seriesId = "series")
        val boundary = event(id = "boundary", start = dateTime(hour = 10), seriesId = "series")
        val future = event(id = "future", start = dateTime(hour = 11), seriesId = "series")
        val service = service(
            googleEvents = listOf(past, boundary, future),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        source = CalendarSource.GOOGLE_OWN,
                        seriesId = "series",
                        fromStart = boundary.start,
                    ),
                    hidden = true,
                ),
            ),
        )

        assertEquals(listOf(past), service.mergedEvents(testRange))
    }

    @Test
    fun mergedEvents_appliesOccurrenceFieldsIncludingDateAndTimes() = runTest {
        val original = event(
            id = "occurrence",
            title = "Original",
            start = dateTime(hour = 10),
            end = dateTime(hour = 11),
        )
        val newDay = testDay.plusDays(1)
        val service = service(
            googleEvents = listOf(original),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(original.source, original.id)),
                    overrides = EventFieldOverrides(
                        title = "Changed",
                        date = newDay,
                        startTime = java.time.LocalTime.of(14, 30),
                        endTime = java.time.LocalTime.of(16, 0),
                        location = "New place",
                        description = "New description",
                    ),
                ),
            ),
        )

        val customized = service.mergedEvents(testRange).single()
        assertEquals("Changed", customized.title)
        assertEquals(newDay.atTime(14, 30), customized.start)
        assertEquals(newDay.atTime(16, 0), customized.end)
        assertEquals("New place", customized.location)
        assertEquals("New description", customized.description)
        assertTrue(customized.isCustomized)
    }

    @Test
    fun mergedEvents_usesLatestMatchingSeriesCustomizationAndIgnoresItsDate() = runTest {
        val occurrence = event(id = "occurrence", start = dateTime(hour = 12), seriesId = "series")
        val service = service(
            googleEvents = listOf(occurrence),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        occurrence.source,
                        "series",
                        dateTime(hour = 9),
                    ),
                    overrides = EventFieldOverrides(title = "Older"),
                ),
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        occurrence.source,
                        "series",
                        dateTime(hour = 11),
                    ),
                    overrides = EventFieldOverrides(
                        title = "Latest",
                        date = testDay.plusDays(2),
                    ),
                ),
            ),
        )

        val customized = service.mergedEvents(testRange).single()
        assertEquals("Latest", customized.title)
        assertEquals(occurrence.start, customized.start)
    }

    @Test
    fun mergedEvents_doesNotMarkEventCustomizedWhenOnlyIgnoredSeriesDateIsSet() = runTest {
        val occurrence = event(id = "occurrence", seriesId = "series")
        val service = service(
            googleEvents = listOf(occurrence),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        occurrence.source,
                        "series",
                        occurrence.start,
                    ),
                    overrides = EventFieldOverrides(date = testDay.plusDays(1)),
                ),
            ),
        )

        assertEquals(occurrence, service.mergedEvents(testRange).single())
    }

    @Test
    fun mergedEvents_occurrenceCustomizationTakesPrecedenceOverSeriesCustomization() = runTest {
        val occurrence = event(id = "occurrence", seriesId = "series")
        val service = service(
            googleEvents = listOf(occurrence),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        occurrence.source,
                        "series",
                        occurrence.start.minusDays(1),
                    ),
                    hidden = true,
                ),
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(occurrence.source, occurrence.id)),
                    overrides = EventFieldOverrides(title = "Visible override"),
                ),
            ),
        )

        assertEquals("Visible override", service.mergedEvents(testRange).single().title)
    }

    @Test
    fun mergedEvents_movesAllDayDatesAndIgnoresTimeOverrides() = runTest {
        val original = event(
            id = "all-day",
            start = testDay.atStartOfDay(),
            end = testDay.plusDays(2).atStartOfDay(),
            isAllDay = true,
        )
        val newDay = testDay.plusDays(5)
        val service = service(
            googleEvents = listOf(original),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(original.source, original.id)),
                    overrides = EventFieldOverrides(
                        date = newDay,
                        startTime = java.time.LocalTime.NOON,
                        endTime = java.time.LocalTime.of(13, 0),
                    ),
                ),
            ),
        )

        val customized = service.mergedEvents(testRange).single()
        assertEquals(newDay.atStartOfDay(), customized.start)
        assertEquals(newDay.plusDays(2).atStartOfDay(), customized.end)
        assertTrue(customized.isCustomized)
    }

    @Test
    fun mergedEvents_doesNotMarkAllDayEventCustomizedForIgnoredTimeOverridesOnly() = runTest {
        val original = event(
            id = "all-day",
            start = testDay.atStartOfDay(),
            end = testDay.plusDays(1).atStartOfDay(),
            isAllDay = true,
        )
        val service = service(
            googleEvents = listOf(original),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(original.source, original.id)),
                    overrides = EventFieldOverrides(startTime = java.time.LocalTime.NOON),
                ),
            ),
        )

        assertEquals(original, service.mergedEvents(testRange).single())
    }

    @Test
    fun mergedEvents_returnsUnchangedEventsAndLogsWhenCustomizationLoadingFails() = runTest {
        val original = event(id = "original")
        val logger = FakeCalendarLogger()
        val service = service(
            googleEvents = listOf(original),
            customizationFailure = IllegalStateException("Customizations unavailable"),
            logger = logger,
        )

        assertEquals(listOf(original), service.mergedEvents(testRange))
        assertEquals("Event customizations", logger.errors.single().source)
        assertEquals("Customizations unavailable", logger.errors.single().cause.message)
    }

    @Test
    fun mergedEvents_derivesCategoryFromSourceAndSharedStatus() = runTest {
        val privateEvent = event(id = "private")
        val sharedEvent = event(id = "shared", isSharedEvent = true)
        val workEvent = event(id = "work", source = CalendarSource.WORK_ICS)
        val service = service(
            googleEvents = listOf(privateEvent, sharedEvent),
            icsEvents = listOf(workEvent),
        )

        val result = service.mergedEvents(testRange).associateBy { event -> event.id }

        assertEquals(EventCategory.PRIVATE, result.getValue("private").category)
        assertEquals(EventCategory.TOGETHER, result.getValue("shared").category)
        assertEquals(EventCategory.WORK, result.getValue("work").category)
    }

    @Test
    fun mergedEvents_appliesOccurrenceCategoryOverride() = runTest {
        val original = event(id = "private")
        val service = service(
            googleEvents = listOf(original),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(original.source, original.id)),
                    overrides = EventFieldOverrides(category = EventCategory.WORK),
                ),
            ),
        )

        val customized = service.mergedEvents(testRange).single()

        assertEquals(EventCategory.WORK, customized.category)
        // Reine Kategorie-Änderung: kein "Angepasst"-Badge (die Kategorie-Pill zeigt das schon),
        // aber weiterhin über "Anpassung zurücksetzen" aufhebbar.
        assertFalse(customized.isCustomized)
        assertTrue(customized.hasAnyCustomization)
        assertEquals(original.start, customized.start)
        assertEquals(original.end, customized.end)
    }

    @Test
    fun mergedEvents_appliesSeriesCategoryOverrideFromBoundary() = runTest {
        val past = event(id = "past", start = dateTime(hour = 9), seriesId = "series")
        val boundary = event(id = "boundary", start = dateTime(hour = 10), seriesId = "series")
        val future = event(id = "future", start = dateTime(hour = 11), seriesId = "series")
        val service = service(
            googleEvents = listOf(past, boundary, future),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.SeriesFrom(
                        source = CalendarSource.GOOGLE_OWN,
                        seriesId = "series",
                        fromStart = boundary.start,
                    ),
                    overrides = EventFieldOverrides(category = EventCategory.TOGETHER),
                ),
            ),
        )

        val result = service.mergedEvents(testRange).associateBy { event -> event.id }

        assertEquals(EventCategory.PRIVATE, result.getValue("past").category)
        assertFalse(result.getValue("past").isCustomized)
        assertFalse(result.getValue("past").hasAnyCustomization)
        assertEquals(EventCategory.TOGETHER, result.getValue("boundary").category)
        assertFalse(result.getValue("boundary").isCustomized)
        assertTrue(result.getValue("boundary").hasAnyCustomization)
        assertEquals(EventCategory.TOGETHER, result.getValue("future").category)
        assertFalse(result.getValue("future").isCustomized)
        assertTrue(result.getValue("future").hasAnyCustomization)
    }

    @Test
    fun freeBlocks_returnsEveryQualifyingGapInStartOrder() = runTest {
        val service = service(
            googleEvents = listOf(
                event(id = "midday", start = testDay.atTime(12, 0), end = testDay.atTime(14, 0)),
                event(id = "evening", start = testDay.atTime(18, 0), end = testDay.atTime(22, 0)),
            ),
        )

        assertEquals(
            listOf(
                FreeTimeBlock(testDay.atTime(9, 0), testDay.atTime(12, 0)),
                FreeTimeBlock(testDay.atTime(14, 0), testDay.atTime(18, 0)),
            ),
            service.freeBlocks(testDay),
        )
    }

    @Test
    fun freeBlocks_ignoresAllDayEvents() = runTest {
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

        assertEquals(
            listOf(FreeTimeBlock(testDay.atTime(9, 0), testDay.atTime(22, 0))),
            service.freeBlocks(testDay),
        )
    }

    @Test
    fun freeBlocks_returnsEmptyListWhenWindowIsFullyBlocked() = runTest {
        val service = service(
            googleEvents = listOf(
                event(id = "full-day", start = testDay.atTime(9, 0), end = testDay.atTime(22, 0)),
            ),
        )

        assertTrue(service.freeBlocks(testDay).isEmpty())
    }

    @Test
    fun freeBlocks_includesGapOfExactlyThreeHours() = runTest {
        val service = service(
            googleEvents = listOf(
                event(id = "after-gap", start = testDay.atTime(12, 0), end = testDay.atTime(22, 0)),
            ),
        )

        assertEquals(
            listOf(FreeTimeBlock(testDay.atTime(9, 0), testDay.atTime(12, 0))),
            service.freeBlocks(testDay),
        )
    }

    @Test
    fun freeBlocks_excludesGapShorterThanThreeHours() = runTest {
        val service = service(
            googleEvents = listOf(
                event(id = "after-gap", start = testDay.atTime(11, 59), end = testDay.atTime(22, 0)),
            ),
        )

        assertTrue(service.freeBlocks(testDay).isEmpty())
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

    @Test
    fun isBothFree_ignoresLocallyHiddenEvents() = runTest {
        val blocking = event(
            id = "blocking",
            start = dateTime(hour = 9),
            end = dateTime(hour = 22),
        )
        val service = service(
            googleEvents = listOf(blocking),
            customizations = listOf(
                customization(
                    target = CustomizationTarget.Occurrence(EventKey(blocking.source, blocking.id)),
                    hidden = true,
                ),
            ),
        )

        assertTrue(service.isBothFree(testDay))
    }

    private fun service(
        googleEvents: List<CalendarEvent> = emptyList(),
        icsEvents: List<CalendarEvent> = emptyList(),
        googleFailure: Throwable? = null,
        icsFailure: Throwable? = null,
        customizations: List<EventCustomization> = emptyList(),
        customizationFailure: Throwable? = null,
        logger: CalendarLogger = FakeCalendarLogger(),
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
            customizationRepository = FakeEventCustomizationRepository(
                customizations = customizations,
                failure = customizationFailure,
            ),
            logger = logger,
        )

    private fun customization(
        target: CustomizationTarget,
        hidden: Boolean = false,
        overrides: EventFieldOverrides = EventFieldOverrides(),
    ): EventCustomization = EventCustomization(
        target = target,
        hidden = hidden,
        overrides = overrides,
    )

    private class FakeCalendarLogger : CalendarLogger {
        val errors = mutableListOf<LoggedError>()

        override fun error(source: String, cause: Throwable) {
            errors += LoggedError(source, cause)
        }

        data class LoggedError(val source: String, val cause: Throwable)
    }

    private fun event(
        id: String,
        title: String = id,
        start: LocalDateTime = dateTime(hour = 10),
        end: LocalDateTime = dateTime(hour = 11),
        isAllDay: Boolean = false,
        source: CalendarSource = CalendarSource.GOOGLE_OWN,
        owner: Person = Person.BASTI,
        seriesId: String? = null,
        isSharedEvent: Boolean = false,
    ): CalendarEvent =
        CalendarEvent(
            id = id,
            title = title,
            start = start,
            end = end,
            isAllDay = isAllDay,
            source = source,
            owner = owner,
            isSharedEvent = isSharedEvent,
            seriesId = seriesId,
        )

    private class FakeEventCustomizationRepository(
        private val customizations: List<EventCustomization>,
        private val failure: Throwable?,
    ) : EventCustomizationRepository {
        override suspend fun save(customization: EventCustomization) = Unit

        override suspend fun remove(target: CustomizationTarget) = Unit

        override suspend fun all(): List<EventCustomization> {
            failure?.let { throw it }
            return customizations
        }
    }

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
