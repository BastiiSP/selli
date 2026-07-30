package com.prehmus.selli.ui.calendar

import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
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

    private fun fixture(
        initialCustomizations: List<EventCustomization> = emptyList(),
    ): Fixture {
        val calendarRepository = RecordingCalendarRepository()
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

    private data class Fixture(
        val viewModel: CalendarViewModel,
        val calendarRepository: RecordingCalendarRepository,
        val customizationRepository: InMemoryCustomizationRepository,
    )

    private class EmptyMergeService : CalendarMergeService {
        override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> = emptyList()

        override suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock> = emptyList()
    }

    private class RecordingCalendarRepository : CalendarRepository {
        val createCalls = mutableListOf<NewCalendarEvent>()
        val deleteCalls = mutableListOf<Pair<CalendarEvent, DeletionScope>>()

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
