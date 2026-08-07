package com.prehmus.selli.ui.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.SourceLoadError
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.ui.settings.rememberLayoutPreferences
import com.prehmus.selli.ui.event.CreateEventSheet
import com.prehmus.selli.ui.event.CustomizationManagerSheet
import com.prehmus.selli.ui.event.EditEventSheet
import com.prehmus.selli.ui.event.EventActionsSheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Hauptansicht: Monatsgrid mit Tagesdetail darunter (Bedienlogik wie im
 * Outlook-Kalender, bewusst übernommen) im warmen Selli-Look. Alle Termine
 * beider Personen plus Arbeitskalender in einer gemeinsamen Ansicht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
    // Adressvorschläge fürs Ortsfeld der Sheets; null = reines Textfeld (Previews/Tests).
    suggestionRepository: PlaceSuggestionRepository? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    // Gemeinsame, dauerhaft gespeicherte Layout-Aufteilung für alle drei Ansichten.
    val layout = rememberLayoutPreferences()
    // Höhe des mittleren Bereichs (Kalender + Liste) in px — Basis, um Zieh-Deltas des
    // Handles in einen Verhältnis-Anteil umzurechnen.
    var middleHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
    }

    // Erstzugriffs-Consent der Google Calendar API: Dialog sichtbar öffnen,
    // nach Zustimmung lädt der ViewModel-Callback direkt weiter.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(uiState.pendingConsent) {
        uiState.pendingConsent?.let(consentLauncher::launch)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateSheet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Termin anlegen")
            }
        },
    ) { innerPadding ->
        // Richtung des letzten Blätterns (+1 vor, -1 zurück, 0 neutral/Ansichtswechsel)
        // steuert die Slide-Animation im SwipeNavigator. Wisch und Pfeile teilen sich
        // bewusst dieselben Callbacks, damit beide Navigationswege identisch animieren.
        var navDirection by remember { mutableIntStateOf(0) }
        val goNext = {
            navDirection = 1
            viewModel.showNext()
        }
        val goPrevious = {
            navDirection = -1
            viewModel.showPrevious()
        }
        val selectViewMode: (CalendarViewMode) -> Unit = { mode ->
            navDirection = 0
            viewModel.setViewMode(mode)
        }

        // Identität des gezeigten Zeitraums: ändert sie sich (Blättern oder Ansichts-
        // wechsel), spielt der SwipeNavigator den Übergang. Ein Tag-Antippen ändert sie
        // bewusst nicht — dann aktualisiert sich nur die Detailliste ohne Animation.
        val periodKey: Any = when (uiState.viewMode) {
            CalendarViewMode.MONTH -> uiState.viewMode to uiState.visibleMonth
            CalendarViewMode.WEEK -> uiState.viewMode to weekDays(uiState.selectedDay).first()
            CalendarViewMode.DAY -> uiState.viewMode to uiState.selectedDay
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            MascotHeader(
                title = calendarHeaderTitle(uiState.viewMode, uiState.visibleMonth, uiState.selectedDay),
                isSyncing = uiState.isSyncing,
                nextWirZeitEvent = uiState.nextWirZeitEvent,
                collapsed = layout.headerCollapsed,
                onToggleCollapsed = { layout.updateHeaderCollapsed(!layout.headerCollapsed) },
                onPrevious = goPrevious,
                onNext = goNext,
                onRefresh = { viewModel.refresh(forceNetwork = true) },
                onManageCustomizations = viewModel::openCustomizationManager,
                onSwitchAccount = { showSwitchAccountDialog = true },
                onWirZeitCountdownClick = viewModel::onWirZeitCountdownClick,
            )
            ViewModeSwitcher(
                selected = uiState.viewMode,
                onSelect = selectViewMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Fehlgeschlagene Kalenderquellen sichtbar machen (früher still leer): so fällt
            // ein zeitweise fehlender Kalender – z. B. Mellis Dr.-Plano-Feed – sofort auf.
            if (uiState.loadErrors.isNotEmpty()) {
                SourceLoadErrorBanner(
                    errors = uiState.loadErrors,
                    onRetry = { viewModel.refresh(forceNetwork = true) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Austauschbarer mittlerer Baustein samt Detailliste. Wischen (und die
            // Pfeile oben) blättern innerhalb der aktiven Ansicht (Monat/Woche/Tag) und
            // lösen dieselbe kurze, richtungsabhängige Slide-Animation mit dem
            // schiebenden Maskottchen aus. Der Header oben bleibt unberührt.
            // Nach unten ziehen aktualisiert manuell (fängt neue Termine der anderen Person ab).
            PullToRefreshBox(
                isRefreshing = uiState.isSyncing,
                onRefresh = { viewModel.refresh(forceNetwork = true) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SwipeNavigator(
                    contentKey = periodKey,
                    direction = navDirection,
                    onNext = goNext,
                    onPrevious = goPrevious,
                    modifier = Modifier.fillMaxSize(),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { middleHeightPx = it.height },
                ) {
                    // Kalender (Grid/Zeitstrahl) und Terminliste teilen sich den Platz nach
                    // dem gespeicherten, per Zieh-Handle einstellbaren Verhältnis — gemeinsam
                    // für alle drei Ansichten. So bekommt die Liste an vollen Tagen mehr Raum.
                    val calendarWeight = layout.calendarFraction
                    val listWeight = 1f - calendarWeight

                    // Zieh-Handle: verschiebt live das Verhältnis (Delta relativ zur
                    // Bereichshöhe), sichert nach dem Loslassen.
                    val resizeHandle: @Composable () -> Unit = {
                        CalendarListResizeHandle(
                            onDrag = { deltaPx ->
                                if (middleHeightPx > 0) {
                                    layout.nudgeCalendarFraction(deltaPx / middleHeightPx)
                                }
                            },
                            onDragStopped = layout::persistCalendarFraction,
                        )
                    }

                    when (uiState.viewMode) {
                        CalendarViewMode.MONTH -> {
                            MonthGrid(
                                month = uiState.visibleMonth,
                                today = uiState.today,
                                selectedDay = uiState.selectedDay,
                                eventsByDay = uiState.eventsByDay,
                                onSelectDay = viewModel::selectDay,
                                modifier = Modifier
                                    .weight(calendarWeight)
                                    .padding(horizontal = 12.dp),
                            )
                            resizeHandle()
                            DayDetail(
                                day = uiState.selectedDay,
                                events = uiState.selectedDayEvents,
                                bothFree = uiState.bothFreeOnSelectedDay,
                                onEventClick = viewModel::selectEvent,
                                modifier = Modifier.weight(listWeight),
                            )
                        }
                        else -> {
                            val days = if (uiState.viewMode == CalendarViewMode.WEEK) {
                                weekDays(uiState.selectedDay)
                            } else {
                                listOf(uiState.selectedDay)
                            }
                            // „Jetzt" bei jeder Navigation/Ansicht neu bestimmen (MVP: kein Live-Tick).
                            val now = remember(uiState.selectedDay, uiState.viewMode) { LocalDateTime.now() }
                            TimelineView(
                                days = days,
                                today = uiState.today,
                                selectedDay = uiState.selectedDay,
                                now = now,
                                eventsByDay = uiState.eventsByDay,
                                onSelectDay = viewModel::selectDay,
                                onEventClick = viewModel::selectEvent,
                                modifier = Modifier.weight(calendarWeight),
                            )
                            resizeHandle()
                            DayDetail(
                                day = uiState.selectedDay,
                                events = uiState.selectedDayEvents,
                                bothFree = uiState.bothFreeOnSelectedDay,
                                onEventClick = viewModel::selectEvent,
                                modifier = Modifier.weight(listWeight),
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (uiState.isCreateSheetOpen) {
        val prefill = uiState.createSheetPrefill
        CreateEventSheet(
            initialDay = uiState.selectedDay,
            isSaving = uiState.isSavingEvent,
            onSave = viewModel::createEvent,
            onDismiss = viewModel::dismissCreateSheet,
            initialStartTime = prefill?.startTime,
            initialEndTime = prefill?.endTime,
            initialCategory = prefill?.category,
            suggestionRepository = suggestionRepository,
        )
    }

    uiState.selectedEvent?.let { event ->
        EventActionsSheet(
            event = event,
            onEdit = viewModel::beginEditingSelectedEvent,
            onHide = viewModel::hideSelectedEvent,
            onSetCategory = viewModel::setSelectedEventCategory,
            onResetCustomization = viewModel::resetSelectedEventCustomization,
            onDelete = viewModel::deleteSelectedEvent,
            onRequestDeletion = viewModel::requestDeletionOfSelectedEvent,
            onDismiss = viewModel::dismissEventActions,
        )
    }

    uiState.editingEvent?.let { event ->
        EditEventSheet(
            event = event,
            seriesScope = uiState.isEditingSeries,
            onSave = viewModel::saveEventOverrides,
            onDismiss = viewModel::dismissEditing,
            suggestionRepository = suggestionRepository,
        )
    }

    if (uiState.isCustomizationManagerOpen) {
        CustomizationManagerSheet(
            customizations = uiState.storedCustomizations,
            onRemove = viewModel::removeCustomization,
            onDismiss = viewModel::dismissCustomizationManager,
        )
    }

    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            title = { Text("Konto wechseln?") },
            text = {
                Text(
                    "Selli vergisst eure Verknüpfung auf diesem Gerät und startet wieder " +
                        "bei der Anmeldung. Dein Google-Konto und eure Termine bleiben unverändert.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchAccountDialog = false
                    onSwitchAccount()
                }) { Text("Konto wechseln") }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Schlankes Zieh-Handle zwischen Kalender und Terminliste: ein weiches Griff-Pill,
 * das vertikal gezogen das Größenverhältnis verschiebt. Nur vertikale Gesten — die
 * horizontale Wischnavigation des [SwipeNavigator] bleibt unberührt.
 */
@Composable
private fun CalendarListResizeHandle(
    onDrag: (deltaPx: Float) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(delta) },
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Sanfter Warnhinweis, wenn beim letzten Laden einzelne Kalenderquellen scheiterten.
 * Zuvor verschwanden solche Quellen kommentarlos (leere Liste) — dadurch fiel etwa
 * Mellis zeitweise fehlender Dr.-Plano-Kalender gar nicht auf. „Erneut" lädt sofort neu.
 */
@Composable
private fun SourceLoadErrorBanner(
    errors: List<SourceLoadError>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = errors.joinToString(", ") { it.displayName }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (errors.size == 1) {
                        "$names konnte nicht geladen werden"
                    } else {
                        "Einige Kalender konnten nicht geladen werden"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (errors.size == 1) {
                        "Zieh zum Aktualisieren oder tippe „Erneut“."
                    } else {
                        "$names — zieh zum Aktualisieren oder tippe „Erneut“."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRetry) { Text("Erneut") }
        }
    }
}

private val DayTitleFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val WeekEndFormat = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
private val WeekDayNumberFormat = DateTimeFormatter.ofPattern("d.", Locale.GERMAN)

/** Kopfzeilen-Titel, passend zur aktiven Ansicht (Monat / Woche / Tag). */
private fun calendarHeaderTitle(
    mode: CalendarViewMode,
    visibleMonth: YearMonth,
    selectedDay: LocalDate,
): String = when (mode) {
    CalendarViewMode.MONTH ->
        "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${visibleMonth.year}"
    CalendarViewMode.DAY ->
        selectedDay.format(DayTitleFormat).replaceFirstChar { it.uppercase(Locale.GERMAN) }
    CalendarViewMode.WEEK -> {
        val week = weekDays(selectedDay)
        val start = week.first()
        val end = week.last()
        if (start.month == end.month) {
            "${start.format(WeekDayNumberFormat)}–${end.format(WeekEndFormat)}"
        } else {
            "${start.format(WeekEndFormat)} – ${end.format(WeekEndFormat)}"
        }
    }
}
