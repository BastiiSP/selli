package com.prehmus.selli.ui.calendar

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.data.google.GoogleRecoverableAuthException
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.model.SourceLoadError
import com.prehmus.selli.domain.model.key
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDay: LocalDate,
    val today: LocalDate,
    /** Aktive Zeitraum-Darstellung des mittleren Bausteins (Monat/Woche/Tag). */
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val eventsByDay: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val isSyncing: Boolean = false,
    /** Kalenderquellen, die beim letzten Laden fehlgeschlagen sind — sichtbar statt still leer. */
    val loadErrors: List<SourceLoadError> = emptyList(),
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

    // Schnelles, wiederholtes Blättern (oder eine manuelle Aktualisierung mitten in einem
    // laufenden Fetch) darf sich nicht stapeln: Jeder neue Refresh bricht den vorherigen ab.
    // Das verhindert paralleles „Hämmern" der Kalenderquellen (relevant für den fremden
    // Dr.-Plano-ICS-Server) und dass eine veraltete, langsamere Antwort die frische überschreibt.
    private var refreshJob: Job? = null

    // Termin aus einer Benachrichtigung, dessen Tag beim Antippen noch nicht geladen war.
    private var pendingDeepLink: EventKey? = null

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

    /**
     * MVP-Sync-Modell: Refresh beim App-Öffnen, beim Blättern und jederzeit manuell
     * (Pull-to-Refresh / Menü) — deckt so auch den Fall ab, dass die *andere* Person
     * während der laufenden Sitzung etwas einträgt. Kein Push/Realtime (bewusst).
     *
     * [forceNetwork] = true umgeht den quellenweisen Kurzzeit-Cache und erzwingt echte
     * Netzwerk-Anfragen. Das ist für **manuelles** Aktualisieren (Pull-to-Refresh, „Erneut",
     * Menü) sowie nach eigenen Schreibvorgängen (Anlegen/Löschen) gewollt. Automatisches
     * Nachladen durch Navigation lässt [forceNetwork] = false, damit reines Blättern nie in
     * die Server-Ratenbegrenzung (Dr. Plano HTTP 429) läuft.
     */
    fun refresh(forceNetwork: Boolean = false) {
        val month = _uiState.value.visibleMonth
        _uiState.update { it.copy(isSyncing = true) }
        // Vorherigen (evtl. noch laufenden) Refresh abbrechen — nur der jüngste zählt.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                // Zeitraum je nach aktiver Ansicht (Monat/Woche/Tag), jeweils mit Puffer,
                // damit Ränder gefüllt sind — dieselben Daten, nur ein anderer Ausschnitt.
                val range = fetchRangeFor(
                    mode = _uiState.value.viewMode,
                    visibleMonth = month,
                    anchorDay = _uiState.value.selectedDay,
                )
                val merged = mergeService.mergedEventsWithStatus(range, forceNetwork)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        eventsByDay = merged.events.groupByDay(),
                        loadErrors = merged.errors,
                    )
                }
                refreshFreeBlocks(_uiState.value.selectedDay)
                // Wartet ein Benachrichtigungs-Termin auf seinen Tag, ist er jetzt da. Danach in
                // jedem Fall verwerfen: ein inzwischen gelöschter Termin darf nicht irgendwann
                // später beim Blättern noch ein Sheet aufreißen.
                resolvePendingDeepLink()
                pendingDeepLink = null
            } catch (cancellation: CancellationException) {
                // Ein neuerer Refresh hat übernommen — dessen Lauf besitzt jetzt isSyncing.
                throw cancellation
            } catch (error: GoogleRecoverableAuthException) {
                _uiState.update { it.copy(isSyncing = false, pendingConsent = error.recoveryIntent) }
            } catch (error: Exception) {
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

    /** Wechselt die Zeitraum-Darstellung; Termine/Kategorien/Freizeit bleiben unberührt. */
    fun setViewMode(mode: CalendarViewMode) {
        if (_uiState.value.viewMode == mode) return
        _uiState.update {
            it.copy(
                viewMode = mode,
                // Woche/Tag ankern am ausgewählten Tag, damit der Fetch-Zeitraum passt.
                visibleMonth = if (mode == CalendarViewMode.MONTH) it.visibleMonth else YearMonth.from(it.selectedDay),
            )
        }
        refresh()
    }

    /** Vor-/Zurück-Navigation, die sich an die aktive Ansicht anpasst (Monat/Woche/Tag). */
    fun showPrevious() = navigate(-1)

    fun showNext() = navigate(1)

    private fun navigate(direction: Int) {
        when (_uiState.value.viewMode) {
            CalendarViewMode.MONTH ->
                _uiState.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(direction.toLong())) }
            CalendarViewMode.WEEK -> moveSelectedDay(_uiState.value.selectedDay.plusWeeks(direction.toLong()))
            CalendarViewMode.DAY -> moveSelectedDay(_uiState.value.selectedDay.plusDays(direction.toLong()))
        }
        refresh()
    }

    private fun moveSelectedDay(target: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDay = target,
                visibleMonth = YearMonth.from(target),
                freeBlocksOnSelectedDay = emptyList(),
            )
        }
        refreshFreeBlocks(target)
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
                    // Kategorie und Ganztags-Frei-Zeit-Festlegung sind rein lokal und werden
                    // gemeinsam gespeichert — nichts davon nach Google zurückschreiben.
                    val localSettingsSaved =
                        applyLocalSettingsToCreatedEvent(created, draft, category)
                    _uiState.update {
                        it.copy(
                            isSavingEvent = false,
                            isCreateSheetOpen = false,
                            createSheetPrefill = null,
                            userMessage = if (localSettingsSaved) {
                                "Termin angelegt."
                            } else {
                                "Termin angelegt, lokale Einstellungen konnten nicht gespeichert werden."
                            },
                        )
                    }
                    refresh(forceNetwork = true)
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

    /**
     * Einstieg aus einer Wir-Zeit-Benachrichtigung: springt auf [day] und öffnet dort das
     * Aktionen-Sheet für den Termin mit [key]. Nutzt bewusst nur die bestehenden Mechanismen
     * (`selectedDay` + `selectedEvent`) statt einer eigenen Navigation.
     *
     * Beim App-Kaltstart läuft der erste Refresh noch — der Termin wird dann gemerkt und
     * aufgelöst, sobald die Termine dieses Tages da sind (siehe [resolvePendingDeepLink]).
     */
    fun openDeepLinkedEvent(day: LocalDate, key: EventKey) {
        pendingDeepLink = key
        _uiState.update {
            it.copy(
                selectedDay = day,
                visibleMonth = YearMonth.from(day),
                freeBlocksOnSelectedDay = emptyList(),
            )
        }
        refreshFreeBlocks(day)
        // Sind die Termine schon geladen, greift das sofort; sonst übernimmt refresh().
        if (!resolvePendingDeepLink()) refresh()
    }

    /** @return true, wenn der gemerkte Termin gefunden und geöffnet wurde. */
    private fun resolvePendingDeepLink(): Boolean {
        val key = pendingDeepLink ?: return false
        val state = _uiState.value
        val event = state.eventsByDay[state.selectedDay]
            ?.firstOrNull { candidate -> candidate.key() == key }
            ?: return false

        pendingDeepLink = null
        _uiState.update { it.copy(selectedEvent = event) }
        return true
    }

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
     * „Wir-Zeit" ist mehr als eine lokale Kategorie: Eigene Google-Termine werden dabei
     * mit dem Partner geteilt beziehungsweise wieder entteilt. Erst nach erfolgreicher
     * Google-Aktualisierung wird die lokale Kategorie gespeichert.
     */
    fun setSelectedEventCategory(category: EventCategory, wholeSeries: Boolean) {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        val shouldBeShared = category == EventCategory.TOGETHER
        val requiresGoogleUpdate = event.isSharedEvent != shouldBeShared

        if (requiresGoogleUpdate && event.source != CalendarSource.GOOGLE_OWN) {
            _uiState.update {
                it.copy(userMessage = "Nur eigene Google-Termine können als Wir-Zeit geändert werden.")
            }
            return
        }

        viewModelScope.launch {
            if (requiresGoogleUpdate) {
                val sharingResult = calendarRepository.setPartnerAttendance(
                    event = event,
                    shared = shouldBeShared,
                    wholeSeries = wholeSeries,
                )
                if (sharingResult.isFailure) {
                    val error = sharingResult.exceptionOrNull()
                    _uiState.update {
                        it.copy(
                            userMessage = error?.message
                                ?: "Wir-Zeit konnte nicht mit Google synchronisiert werden.",
                        )
                    }
                    return@launch
                }
            }

            saveEventCategory(
                event = event,
                category = category,
                wholeSeries = wholeSeries,
                successMessage = if (requiresGoogleUpdate) {
                    if (shouldBeShared) {
                        "Wir-Zeit gespeichert — der Termin ist jetzt in beiden Kalendern."
                    } else {
                        "Wir-Zeit beendet — der Termin wurde aus dem Partnerkalender entfernt."
                    }
                } else {
                    "Kategorie in Selli gespeichert."
                },
                forceNetworkRefresh = requiresGoogleUpdate,
            )
        }
    }

    private suspend fun saveEventCategory(
        event: CalendarEvent,
        category: EventCategory,
        wholeSeries: Boolean,
        successMessage: String,
        forceNetworkRefresh: Boolean,
    ) {
        val target = event.customizationTarget(wholeSeries)
        val existing = runCatching { customizationRepository.all() }
            .getOrDefault(emptyList())
            .firstOrNull { it.target == target }
        val customization = EventCustomization(
            target = target,
            hidden = existing?.hidden ?: false,
            overrides = (existing?.overrides ?: EventFieldOverrides()).copy(category = category),
            label = existing?.label?.ifBlank { event.title } ?: event.title,
        )

        runCatching { customizationRepository.save(customization) }
            .onSuccess {
                _uiState.update { it.copy(userMessage = successMessage) }
                refresh(forceNetwork = forceNetworkRefresh)
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.message ?: "Speichern der Kategorie fehlgeschlagen.")
                }
            }
    }

    /**
     * Löscht den ausgewählten Termin **echt** über die Google Calendar API (nur eigene
     * Google-Termine — die UI bietet die Aktion sonst nicht an). Bei Serien entscheidet
     * [scope] über „nur dieses Vorkommen" vs. „dieses und alle folgenden". Anders als das
     * lokale Ausblenden verändert das den echten Kalender.
     */
    fun deleteSelectedEvent(scope: DeletionScope) {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        viewModelScope.launch {
            calendarRepository.deleteEvent(event, scope)
                .onSuccess {
                    _uiState.update { it.copy(userMessage = "Termin gelöscht.") }
                    refresh(forceNetwork = true)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.message ?: "Termin konnte nicht gelöscht werden.")
                    }
                }
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
            // Unveränderte Felder der bereits wirksamen lokalen Anpassung nicht verlieren.
            // Das Bearbeiten-Sheet liefert nur Abweichungen zum aktuell angezeigten Event;
            // ein vollständiges Zurücksetzen ist eine separate Aktion.
            val existing = runCatching { customizationRepository.all() }
                .getOrDefault(emptyList())
                .firstOrNull { it.target == target }
            val existingOverrides = existing?.overrides ?: EventFieldOverrides()
            val mergedOverrides = EventFieldOverrides(
                title = overrides.title ?: existingOverrides.title,
                date = overrides.date ?: existingOverrides.date,
                startTime = overrides.startTime ?: existingOverrides.startTime,
                endTime = overrides.endTime ?: existingOverrides.endTime,
                location = overrides.location ?: existingOverrides.location,
                description = overrides.description ?: existingOverrides.description,
                category = overrides.category
                    ?: existingOverrides.category
                    ?: event.category.takeIf { event.hasAnyCustomization },
                blocksSharedFreeTime = overrides.blocksSharedFreeTime
                    ?: existingOverrides.blocksSharedFreeTime
                    ?: event.blocksSharedFreeTime.takeIf { event.isAllDay },
            )
            applyCustomization(
                EventCustomization(
                    target = target,
                    hidden = existing?.hidden ?: false,
                    overrides = mergedOverrides,
                    label = existing?.label?.ifBlank { event.title } ?: event.title,
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
     * Legt für einen frisch erstellten Termin genau eine kombinierte lokale Anpassung ab.
     * Ganztags-Frei-Zeit wird immer explizit gespeichert (auch false); die Kategorie nur dann,
     * wenn sie von der automatischen Ableitung abweicht.
     */
    private suspend fun applyLocalSettingsToCreatedEvent(
        created: CalendarEvent,
        draft: NewCalendarEvent,
        category: EventCategory?,
    ): Boolean {
        val derived = if (draft.invitePartner) EventCategory.TOGETHER else EventCategory.PRIVATE
        val overrides = EventFieldOverrides(
            category = category?.takeIf { it != derived },
            blocksSharedFreeTime = draft.blocksSharedFreeTime.takeIf { draft.isAllDay },
        )
        if (overrides.isEmpty()) return true

        val target = if (draft.recurrence != null) {
            CustomizationTarget.SeriesFrom(
                source = created.source,
                seriesId = created.id,
                fromStart = created.start,
            )
        } else {
            CustomizationTarget.Occurrence(created.key())
        }
        return runCatching {
            customizationRepository.save(
                EventCustomization(
                    target = target,
                    hidden = false,
                    overrides = overrides,
                    label = created.title,
                ),
            )
        }.isSuccess
    }

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
            refresh(forceNetwork = true)
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
