package com.prehmus.selli.ui.calendar

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.data.google.GoogleRecoverableAuthException
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import java.time.LocalDate
import java.time.LocalTime
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
    /** Alle qualifizierenden gemeinsamen freien Blöcke am ausgewählten Tag (≥3h, 9–22 Uhr). */
    val freeBlocksOnSelectedDay: List<FreeTimeBlock> = emptyList(),
    val isCreateSheetOpen: Boolean = false,
    /** Vorbefüllung des Anlegen-Sheets, wenn es aus einem freien Block heraus geöffnet wird. */
    val createSheetPrefill: CreateEventPrefill? = null,
    val isSavingEvent: Boolean = false,
    val userMessage: String? = null,
    /** Google verlangt beim Erstzugriff einmalig Zustimmung — dieser Intent öffnet den Dialog. */
    val pendingConsent: Intent? = null,
    /** Angetippter Termin — öffnet das Aktionen-Sheet (Ausblenden/Bearbeiten). */
    val selectedEvent: CalendarEvent? = null,
    /** Termin im Bearbeiten-Sheet; [isEditingSeries] = Änderung gilt für die Serie ab diesem Vorkommen. */
    val editingEvent: CalendarEvent? = null,
    val isEditingSeries: Boolean = false,
    /** Verwaltung der lokal gespeicherten Ausblendungen/Anpassungen. */
    val isCustomizationManagerOpen: Boolean = false,
    val storedCustomizations: List<EventCustomization> = emptyList(),
) {
    val selectedDayEvents: List<CalendarEvent>
        get() = eventsByDay[selectedDay].orEmpty()

    val bothFreeOnSelectedDay: Boolean
        get() = freeBlocksOnSelectedDay.isNotEmpty()
}

/** Startwerte für das Anlegen-Sheet aus der Schnellanlage eines freien Blocks. */
data class CreateEventPrefill(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val category: EventCategory,
)

class CalendarViewModel(
    private val mergeService: CalendarMergeService,
    private val calendarRepository: CalendarRepository,
    private val customizationRepository: EventCustomizationRepository,
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
                refreshFreeBlocks(_uiState.value.selectedDay)
            }.onFailure { error ->
                when (error) {
                    is GoogleRecoverableAuthException -> _uiState.update {
                        it.copy(isSyncing = false, pendingConsent = error.recoveryIntent)
                    }
                    else -> _uiState.update {
                        it.copy(
                            isSyncing = false,
                            userMessage = error.message ?: "Kalender konnten nicht geladen werden.",
                        )
                    }
                }
            }
        }
    }

    fun showMonth(month: YearMonth) {
        _uiState.update { it.copy(visibleMonth = month) }
        refresh()
    }

    fun selectDay(day: LocalDate) {
        _uiState.update { it.copy(selectedDay = day, freeBlocksOnSelectedDay = emptyList()) }
        refreshFreeBlocks(day)
    }

    private fun refreshFreeBlocks(day: LocalDate) {
        viewModelScope.launch {
            val blocks = runCatching { mergeService.freeBlocks(day) }.getOrDefault(emptyList())
            _uiState.update { current ->
                if (current.selectedDay == day) current.copy(freeBlocksOnSelectedDay = blocks) else current
            }
        }
    }

    fun openCreateSheet() =
        _uiState.update { it.copy(isCreateSheetOpen = true, createSheetPrefill = null) }

    /** Schnellanlage aus einem freien Block: Sheet öffnet mit Zeit + „Wir-Zeit" vorbelegt. */
    fun openCreateSheetForFreeBlock(block: FreeTimeBlock) = _uiState.update {
        it.copy(
            isCreateSheetOpen = true,
            createSheetPrefill = CreateEventPrefill(
                startTime = block.start.toLocalTime(),
                endTime = block.end.toLocalTime(),
                category = EventCategory.TOGETHER,
            ),
        )
    }

    fun dismissCreateSheet() =
        _uiState.update { it.copy(isCreateSheetOpen = false, createSheetPrefill = null) }

    fun createEvent(draft: NewCalendarEvent, category: EventCategory?) {
        if (_uiState.value.isSavingEvent) return
        _uiState.update { it.copy(isSavingEvent = true) }
        viewModelScope.launch {
            calendarRepository.createEvent(draft)
                .onSuccess { created ->
                    // Kategorie ist rein lokal: nur ablegen, wenn sie von der automatischen
                    // Ableitung des neuen Termins abweicht (nichts nach Google zurückschreiben).
                    applyCategoryToCreatedEvent(created, draft, category)
                    _uiState.update {
                        it.copy(
                            isSavingEvent = false,
                            isCreateSheetOpen = false,
                            createSheetPrefill = null,
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

    // --- Lokale Ausblendungen/Anpassungen (ändern nie den echten Google-Kalender) ---

    fun selectEvent(event: CalendarEvent) = _uiState.update { it.copy(selectedEvent = event) }

    fun dismissEventActions() = _uiState.update { it.copy(selectedEvent = null) }

    /** Blendet den ausgewählten Termin lokal aus — nur dieses Vorkommen oder die Serie ab hier. */
    fun hideSelectedEvent(wholeSeries: Boolean) {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        applyCustomization(
            EventCustomization(
                target = event.customizationTarget(wholeSeries),
                hidden = true,
                label = event.title,
            ),
            successMessage = "Nur in Selli ausgeblendet — dein Google-Kalender bleibt unverändert.",
        )
    }

    /**
     * Setzt die Kategorie des ausgewählten Termins lokal — nur dieses Vorkommen oder
     * die Serie ab hier. Bereits vorhandene Feld-Anpassungen desselben Ziels bleiben
     * erhalten (die Kategorie wird nur ergänzt/überschrieben).
     */
    fun setSelectedEventCategory(category: EventCategory, wholeSeries: Boolean) {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        val target = event.customizationTarget(wholeSeries)
        viewModelScope.launch {
            val existing = runCatching { customizationRepository.all() }
                .getOrDefault(emptyList())
                .firstOrNull { it.target == target }
            val mergedOverrides = (existing?.overrides ?: EventFieldOverrides())
                .copy(category = category)
            applyCustomization(
                EventCustomization(
                    target = target,
                    hidden = existing?.hidden ?: false,
                    overrides = mergedOverrides,
                    label = existing?.label?.ifBlank { event.title } ?: event.title,
                ),
                successMessage = "Kategorie nur in Selli gesetzt — dein Google-Kalender bleibt unverändert.",
            )
        }
    }

    fun beginEditingSelectedEvent(wholeSeries: Boolean) {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update {
            it.copy(selectedEvent = null, editingEvent = event, isEditingSeries = wholeSeries)
        }
    }

    fun dismissEditing() = _uiState.update { it.copy(editingEvent = null, isEditingSeries = false) }

    /** Legt Feld-Überschreibungen lokal über den Termin bzw. die Serie ab diesem Vorkommen. */
    fun saveEventOverrides(overrides: EventFieldOverrides) {
        val event = _uiState.value.editingEvent ?: return
        val wholeSeries = _uiState.value.isEditingSeries
        _uiState.update { it.copy(editingEvent = null, isEditingSeries = false) }
        if (overrides.isEmpty()) return
        val target = event.customizationTarget(wholeSeries)
        viewModelScope.launch {
            // Eine bereits gesetzte Kategorie beim reinen Feld-Bearbeiten nicht verlieren.
            val existingCategory = runCatching { customizationRepository.all() }
                .getOrDefault(emptyList())
                .firstOrNull { it.target == target }
                ?.overrides
                ?.category
            applyCustomization(
                EventCustomization(
                    target = target,
                    hidden = false,
                    overrides = overrides.copy(category = overrides.category ?: existingCategory),
                    label = event.title,
                ),
                successMessage = "Nur in Selli angepasst — dein Google-Kalender bleibt unverändert.",
            )
        }
    }

    /** Hebt alle Anpassungen auf, die auf den ausgewählten Termin wirken. */
    fun resetSelectedEventCustomization() {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        viewModelScope.launch {
            runCatching {
                customizationRepository.remove(CustomizationTarget.Occurrence(event.key()))
                event.seriesId?.let { seriesId ->
                    customizationRepository.all()
                        .map { it.target }
                        .filterIsInstance<CustomizationTarget.SeriesFrom>()
                        .filter {
                            it.source == event.source && it.seriesId == seriesId &&
                                it.fromStart <= event.start
                        }
                        .forEach { customizationRepository.remove(it) }
                }
            }.onSuccess {
                _uiState.update { it.copy(userMessage = "Anpassung entfernt — Selli zeigt wieder das Original.") }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.message ?: "Zurücksetzen fehlgeschlagen.")
                }
            }
        }
    }

    fun openCustomizationManager() {
        viewModelScope.launch {
            val stored = runCatching { customizationRepository.all() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(isCustomizationManagerOpen = true, storedCustomizations = stored)
            }
        }
    }

    fun dismissCustomizationManager() =
        _uiState.update { it.copy(isCustomizationManagerOpen = false) }

    /** Entfernt eine gespeicherte Ausblendung/Anpassung aus der Verwaltungsliste. */
    fun removeCustomization(target: CustomizationTarget) {
        viewModelScope.launch {
            runCatching { customizationRepository.remove(target) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Entfernen fehlgeschlagen.")
                    }
                    return@launch
                }
            val stored = runCatching { customizationRepository.all() }.getOrDefault(emptyList())
            _uiState.update { it.copy(storedCustomizations = stored) }
            refresh()
        }
    }

    private fun applyCustomization(customization: EventCustomization, successMessage: String) {
        viewModelScope.launch {
            runCatching { customizationRepository.save(customization) }
                .onSuccess {
                    _uiState.update { it.copy(userMessage = successMessage) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Speichern der Anpassung fehlgeschlagen.")
                    }
                }
        }
    }

    /**
     * Legt für einen frisch erstellten Termin bei Bedarf eine lokale Kategorie-Anpassung ab.
     * Entspricht die gewünschte Kategorie ohnehin der automatischen Ableitung (eingeladener
     * Partner → Wir-Zeit, sonst Privat), wird nichts gespeichert.
     */
    private suspend fun applyCategoryToCreatedEvent(
        created: CalendarEvent,
        draft: NewCalendarEvent,
        category: EventCategory?,
    ) {
        if (category == null) return
        val derived = if (draft.invitePartner) EventCategory.TOGETHER else EventCategory.PRIVATE
        if (category == derived) return
        runCatching {
            customizationRepository.save(
                EventCustomization(
                    target = CustomizationTarget.Occurrence(created.key()),
                    hidden = false,
                    overrides = EventFieldOverrides(category = category),
                    label = created.title,
                ),
            )
        }
    }

    private fun CalendarEvent.key() = EventKey(source = source, eventId = id)

    private fun CalendarEvent.customizationTarget(wholeSeries: Boolean): CustomizationTarget {
        val seriesId = seriesId
        return if (wholeSeries && seriesId != null) {
            CustomizationTarget.SeriesFrom(source = source, seriesId = seriesId, fromStart = start)
        } else {
            CustomizationTarget.Occurrence(key())
        }
    }

    /**
     * Ergebnis des Google-Consent-Dialogs (Erstzugriff auf die Calendar API):
     * nach erteilter Zustimmung lädt Selli sofort weiter — ohne Neustart.
     */
    fun onConsentResult(granted: Boolean) {
        _uiState.update { it.copy(pendingConsent = null) }
        if (granted) {
            refresh()
        } else {
            _uiState.update {
                it.copy(userMessage = "Ohne Google-Zustimmung kann Selli eure Kalender nicht laden.")
            }
        }
    }

    companion object {
        fun factory(
            mergeService: CalendarMergeService,
            calendarRepository: CalendarRepository,
            customizationRepository: EventCustomizationRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalendarViewModel(mergeService, calendarRepository, customizationRepository) as T
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
