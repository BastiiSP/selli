package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.EventRecurrence
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.RecurrenceFrequency
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `changing own event to Wir-Zeit shares in Google before saving category`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val event = ownEvent(category = EventCategory.PRIVATE, isShared = false)

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.setSelectedEventCategory(EventCategory.TOGETHER, wholeSeries = false)
            advanceUntilIdle()

            assertEquals(
                listOf(SharingCall(event, shared = true, wholeSeries = false)),
                fixture.calendarRepository.sharingCalls,
            )
            assertEquals(
                EventCategory.TOGETHER,
                fixture.customizationRepository.customizations.single().overrides.category,
            )
        }

    @Test
    fun `changing Wir-Zeit to private removes partner in Google`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val event = ownEvent(category = EventCategory.TOGETHER, isShared = true)

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.setSelectedEventCategory(EventCategory.PRIVATE, wholeSeries = true)
            advanceUntilIdle()

            assertEquals(
                listOf(SharingCall(event, shared = false, wholeSeries = true)),
                fixture.calendarRepository.sharingCalls,
            )
            assertEquals(
                EventCategory.PRIVATE,
                fixture.customizationRepository.customizations.single().overrides.category,
            )
        }

    @Test
    fun `failed Google sharing does not save local Wir-Zeit category`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture(sharingResult = Result.failure(IllegalStateException("Google nicht erreichbar")))
            val event = ownEvent(category = EventCategory.PRIVATE, isShared = false)

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.setSelectedEventCategory(EventCategory.TOGETHER, wholeSeries = false)
            advanceUntilIdle()

            assertTrue(fixture.customizationRepository.customizations.isEmpty())
            assertEquals("Google nicht erreichbar", fixture.viewModel.uiState.value.userMessage)
        }

    @Test
    fun `changing private to work remains local`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val event = ownEvent(category = EventCategory.PRIVATE, isShared = false)

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.setSelectedEventCategory(EventCategory.WORK, wholeSeries = false)
            advanceUntilIdle()

            assertTrue(fixture.calendarRepository.sharingCalls.isEmpty())
            assertEquals(
                EventCategory.WORK,
                fixture.customizationRepository.customizations.single().overrides.category,
            )
        }

    @Test
    fun `creating blocking all-day event persists explicit local preference`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()

            fixture.viewModel.createEvent(allDayDraft(blocksSharedFreeTime = true), EventCategory.PRIVATE)
            advanceUntilIdle()

            assertEquals(1, fixture.calendarRepository.createCalls.size)
            assertTrue(
                fixture.customizationRepository.customizations
                    .single()
                    .overrides
                    .blocksSharedFreeTime == true,
            )
        }

    @Test
    fun `creating non-blocking all-day event persists explicit false preference`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()

            fixture.viewModel.createEvent(allDayDraft(blocksSharedFreeTime = false), EventCategory.PRIVATE)
            advanceUntilIdle()

            assertEquals(1, fixture.calendarRepository.createCalls.size)
            assertFalse(
                fixture.customizationRepository.customizations
                    .single()
                    .overrides
                    .blocksSharedFreeTime
                    ?: true,
            )
        }

    @Test
    fun `creating recurring all-day event stores preference for series from first occurrence`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val draft = allDayDraft(blocksSharedFreeTime = true).copy(
                recurrence = EventRecurrence(RecurrenceFrequency.WEEKLY),
            )

            fixture.viewModel.createEvent(draft, EventCategory.PRIVATE)
            advanceUntilIdle()

            assertEquals(
                CustomizationTarget.SeriesFrom(
                    source = CalendarSource.GOOGLE_OWN,
                    seriesId = "created-event",
                    fromStart = CREATED_START,
                ),
                fixture.customizationRepository.customizations.single().target,
            )
        }

    @Test
    fun `editing imported all-day event stores blocking preference only locally`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = fixture()
            val event = importedAllDay(blocksSharedFreeTime = false)

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.beginEditingSelectedEvent(wholeSeries = false)
            fixture.viewModel.saveEventOverrides(
                EventFieldOverrides(blocksSharedFreeTime = true),
            )
            advanceUntilIdle()

            assertEquals(
                true,
                fixture.customizationRepository.customizations
                    .single()
                    .overrides
                    .blocksSharedFreeTime,
            )
            assertTrue(fixture.calendarRepository.createCalls.isEmpty())
            assertTrue(fixture.calendarRepository.deleteCalls.isEmpty())
        }

    @Test
    fun `editing fields preserves existing category and explicit false free-time preference`() =
        runTest(mainDispatcherRule.dispatcher) {
            val event = importedAllDay(blocksSharedFreeTime = false)
            val target = CustomizationTarget.Occurrence(
                EventKey(event.source, event.id),
            )
            val fixture = fixture(
                initialCustomizations = listOf(
                    EventCustomization(
                        target = target,
                        hidden = false,
                        overrides = EventFieldOverrides(
                            category = EventCategory.WORK,
                            blocksSharedFreeTime = false,
                        ),
                        label = "Bestehende Anpassung",
                    ),
                ),
            )

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.beginEditingSelectedEvent(wholeSeries = false)
            fixture.viewModel.saveEventOverrides(EventFieldOverrides(title = "Geändert"))
            advanceUntilIdle()

            val saved = fixture.customizationRepository.customizations.single()
            assertEquals("Geändert", saved.overrides.title)
            assertEquals(EventCategory.WORK, saved.overrides.category)
            assertEquals(false, saved.overrides.blocksSharedFreeTime)
            assertEquals("Bestehende Anpassung", saved.label)
            assertTrue(fixture.calendarRepository.createCalls.isEmpty())
            assertTrue(fixture.calendarRepository.deleteCalls.isEmpty())
        }

    @Test
    fun `editing blocking all-day event can persist explicit false preference`() =
        runTest(mainDispatcherRule.dispatcher) {
            val event = importedAllDay(blocksSharedFreeTime = true)
            val fixture = fixture()

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.beginEditingSelectedEvent(wholeSeries = false)
            fixture.viewModel.saveEventOverrides(
                EventFieldOverrides(blocksSharedFreeTime = false),
            )
            advanceUntilIdle()

            assertEquals(
                false,
                fixture.customizationRepository.customizations
                    .single()
                    .overrides
                    .blocksSharedFreeTime,
            )
        }

    @Test
    fun `editing one occurrence preserves free-time and category inherited from series`() =
        runTest(mainDispatcherRule.dispatcher) {
            val event = importedAllDay(blocksSharedFreeTime = true).copy(
                seriesId = "imported-series",
                category = EventCategory.WORK,
                hasAnyCustomization = true,
            )
            val fixture = fixture(
                initialCustomizations = listOf(
                    EventCustomization(
                        target = CustomizationTarget.SeriesFrom(
                            source = event.source,
                            seriesId = "imported-series",
                            fromStart = event.start.minusDays(7),
                        ),
                        hidden = false,
                        overrides = EventFieldOverrides(
                            category = EventCategory.WORK,
                            blocksSharedFreeTime = true,
                        ),
                        label = "Serienanpassung",
                    ),
                ),
            )

            fixture.viewModel.selectEvent(event)
            fixture.viewModel.beginEditingSelectedEvent(wholeSeries = false)
            fixture.viewModel.saveEventOverrides(EventFieldOverrides(title = "Einzeltag geändert"))
            advanceUntilIdle()

            val occurrence = fixture.customizationRepository.customizations
                .single { it.target is CustomizationTarget.Occurrence }
            assertEquals(EventCategory.WORK, occurrence.overrides.category)
            assertEquals(true, occurrence.overrides.blocksSharedFreeTime)
        }

    private fun fixture(
        initialCustomizations: List<EventCustomization> = emptyList(),
        sharingResult: Result<Unit> = Result.success(Unit),
    ): Fixture {
        val calendarRepository = RecordingCalendarRepository(sharingResult)
        val customizationRepository = InMemoryCustomizationRepository(initialCustomizations)
        return Fixture(
            viewModel = CalendarViewModel(
                mergeService = EmptyMergeService(),
                calendarRepository = calendarRepository,
                customizationRepository = customizationRepository,
            ),
            calendarRepository = calendarRepository,
            customizationRepository = customizationRepository,
        )
    }

    private fun allDayDraft(blocksSharedFreeTime: Boolean): NewCalendarEvent =
        NewCalendarEvent(
            title = "Urlaub",
            start = CREATED_START,
            end = LocalDateTime.of(2026, 8, 8, 0, 0),
            isAllDay = true,
            blocksSharedFreeTime = blocksSharedFreeTime,
        )

    private fun importedAllDay(blocksSharedFreeTime: Boolean): CalendarEvent =
        CalendarEvent(
            id = "imported-event",
            title = "Importiert",
            start = CREATED_START,
            end = CREATED_START.plusDays(1),
            isAllDay = true,
            source = CalendarSource.GOOGLE_PARTNER,
            owner = Person.MELLI,
            isSharedEvent = false,
            blocksSharedFreeTime = blocksSharedFreeTime,
        )

    private fun ownEvent(
        category: EventCategory,
        isShared: Boolean,
    ): CalendarEvent =
        CalendarEvent(
            id = "own-event",
            title = "Eigener Termin",
            start = CREATED_START,
            end = CREATED_START.plusHours(1),
            isAllDay = false,
            source = CalendarSource.GOOGLE_OWN,
            owner = Person.BASTI,
            isSharedEvent = isShared,
            category = category,
        )

    private data class Fixture(
        val viewModel: CalendarViewModel,
        val calendarRepository: RecordingCalendarRepository,
        val customizationRepository: InMemoryCustomizationRepository,
    )

    private class EmptyMergeService : CalendarMergeService {
        override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> = emptyList()

        override suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock> = emptyList()

        override suspend fun freeBlocksInRange(
            range: DateRange,
        ): Map<LocalDate, List<FreeTimeBlock>> {
            val freeBlocksByDay = linkedMapOf<LocalDate, List<FreeTimeBlock>>()
            var day = range.start
            while (!day.isAfter(range.endInclusive)) {
                freeBlocksByDay[day] = emptyList()
                day = day.plusDays(1)
            }
            return freeBlocksByDay
        }
    }

    private data class SharingCall(
        val event: CalendarEvent,
        val shared: Boolean,
        val wholeSeries: Boolean,
    )

    private class RecordingCalendarRepository(
        private val sharingResult: Result<Unit>,
    ) : CalendarRepository {
        val createCalls = mutableListOf<NewCalendarEvent>()
        val deleteCalls = mutableListOf<Pair<CalendarEvent, DeletionScope>>()
        val sharingCalls = mutableListOf<SharingCall>()

        override suspend fun createEvent(event: NewCalendarEvent): Result<CalendarEvent> {
            createCalls += event
            return Result.success(
                CalendarEvent(
                    id = "created-event",
                    title = event.title,
                    start = event.start,
                    end = event.end,
                    isAllDay = event.isAllDay,
                    source = CalendarSource.GOOGLE_OWN,
                    owner = Person.BASTI,
                    isSharedEvent = event.invitePartner,
                ),
            )
        }

        override suspend fun deleteEvent(
            event: CalendarEvent,
            scope: DeletionScope,
        ): Result<Unit> {
            deleteCalls += event to scope
            return Result.success(Unit)
        }

        override suspend fun setPartnerAttendance(
            event: CalendarEvent,
            shared: Boolean,
            wholeSeries: Boolean,
        ): Result<Unit> {
            sharingCalls += SharingCall(event, shared, wholeSeries)
            return sharingResult
        }
    }

    private class InMemoryCustomizationRepository(
        initial: List<EventCustomization>,
    ) : EventCustomizationRepository {
        val customizations = initial.toMutableList()

        override suspend fun save(customization: EventCustomization) {
            val index = customizations.indexOfFirst { it.target == customization.target }
            if (index >= 0) {
                customizations[index] = customization
            } else {
                customizations += customization
            }
        }

        override suspend fun remove(target: CustomizationTarget) {
            customizations.removeAll { it.target == target }
        }

        override suspend fun all(): List<EventCustomization> = customizations.toList()
    }

    private companion object {
        val CREATED_START: LocalDateTime = LocalDateTime.of(2026, 8, 3, 0, 0)
    }
}
