package com.prehmus.selli.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.repository.CalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDay: LocalDate,
    val today: LocalDate,
    val eventsByDay: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val isSyncing: Boolean = false,
    val bothFreeOnSelectedDay: Boolean = false,
    val isCreateSheetOpen: Boolean = false,
    val isSavingEvent: Boolean = false,
    val userMessage: String? = null,
) {
    val selectedDayEvents: List<CalendarEvent>
        get() = eventsByDay[selectedDay].orEmpty()
}

class CalendarViewModel(
    private val mergeService: CalendarMergeService,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<CalendarUiState>
    val uiState: StateFlow<CalendarUiState>

    init {
        val today = LocalDate.now()
        _uiState = MutableStateFlow(
            CalendarUiState(
                visibleMonth = YearMonth.from(today),
                selectedDay = today,
                today = today,
            )
        )
        uiState = _uiState.asStateFlow()
        refresh()
    }

    /** MVP-Sync-Modell: Refresh beim App-Öffnen bzw. auf Nutzerwunsch. */
    fun refresh() {
        val month = _uiState.value.visibleMonth
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            runCatching {
                // Etwas Puffer um den Monat, damit Wochenüberhänge im Grid gefüllt sind.
                val range = DateRange(
                    start = month.atDay(1).minusDays(7),
                    endInclusive = month.atEndOfMonth().plusDays(7),
                )
                mergeService.mergedEvents(range)
            }.onSuccess { events ->
                _uiState.update { it.copy(isSyncing = false, eventsByDay = events.groupByDay()) }
                refreshBothFree(_uiState.value.selectedDay)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        userMessage = error.message ?: "Kalender konnten nicht geladen werden.",
                    )
                }
            }
        }
    }

    fun showMonth(month: YearMonth) {
        _uiState.update { it.copy(visibleMonth = month) }
        refresh()
    }

    fun selectDay(day: LocalDate) {
        _uiState.update { it.copy(selectedDay = day, bothFreeOnSelectedDay = false) }
        refreshBothFree(day)
    }

    private fun refreshBothFree(day: LocalDate) {
        viewModelScope.launch {
            val bothFree = runCatching { mergeService.isBothFree(day) }.getOrDefault(false)
            _uiState.update { current ->
                if (current.selectedDay == day) current.copy(bothFreeOnSelectedDay = bothFree) else current
            }
        }
    }

    fun openCreateSheet() = _uiState.update { it.copy(isCreateSheetOpen = true) }

    fun dismissCreateSheet() = _uiState.update { it.copy(isCreateSheetOpen = false) }

    fun createEvent(draft: NewCalendarEvent) {
        if (_uiState.value.isSavingEvent) return
        _uiState.update { it.copy(isSavingEvent = true) }
        viewModelScope.launch {
            calendarRepository.createEvent(draft)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingEvent = false,
                            isCreateSheetOpen = false,
                            userMessage = "Termin angelegt.",
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingEvent = false,
                            userMessage = error.message ?: "Termin konnte nicht gespeichert werden.",
                        )
                    }
                }
        }
    }

    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        fun factory(
            mergeService: CalendarMergeService,
            calendarRepository: CalendarRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalendarViewModel(mergeService, calendarRepository) as T
        }
    }
}

/** Mehrtägige Termine erscheinen an jedem Tag, den sie berühren. */
private fun List<CalendarEvent>.groupByDay(): Map<LocalDate, List<CalendarEvent>> {
    val byDay = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (event in this) {
        var day = event.start.toLocalDate()
        // Bei Ganztagsterminen ist das Ende häufig exklusiv (Mitternacht des Folgetags).
        val lastDay = event.end.toLocalDate().let {
            if (event.end.toLocalTime() == java.time.LocalTime.MIDNIGHT && it > day) it.minusDays(1) else it
        }
        while (day <= lastDay) {
            byDay.getOrPut(day) { mutableListOf() }.add(event)
            day = day.plusDays(1)
        }
    }
    return byDay.mapValues { (_, events) -> events.sortedWith(compareBy({ !it.isAllDay }, { it.start })) }
}
