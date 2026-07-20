package com.prehmus.selli.ui.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            MascotHeader(
                title = calendarHeaderTitle(uiState.viewMode, uiState.visibleMonth, uiState.selectedDay),
                isSyncing = uiState.isSyncing,
                freeBlocks = uiState.freeBlocksOnSelectedDay,
                onPrevious = viewModel::showPrevious,
                onNext = viewModel::showNext,
                onManageCustomizations = viewModel::openCustomizationManager,
                onSwitchAccount = { showSwitchAccountDialog = true },
                onFreeBlockClick = viewModel::openCreateSheetForFreeBlock,
            )
            ViewModeSwitcher(
                selected = uiState.viewMode,
                onSelect = viewModel::setViewMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Austauschbarer mittlerer Baustein. Wischen navigiert innerhalb der aktiven
            // Ansicht (Monat/Woche/Tag); Header oben und Terminliste unten bleiben unberührt.
            val swipe = Modifier.pointerInput(uiState.viewMode) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragX += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val threshold = 48.dp.toPx()
                        when {
                            dragX <= -threshold -> viewModel.showNext()
                            dragX >= threshold -> viewModel.showPrevious()
                        }
                    },
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
                        modifier = swipe.padding(horizontal = 12.dp),
                    )
                    DayDetail(
                        day = uiState.selectedDay,
                        events = uiState.selectedDayEvents,
                        bothFree = uiState.bothFreeOnSelectedDay,
                        onEventClick = viewModel::selectEvent,
                        modifier = Modifier.weight(1f),
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
                        modifier = swipe.weight(1.3f),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DayDetail(
                        day = uiState.selectedDay,
                        events = uiState.selectedDayEvents,
                        bothFree = uiState.bothFreeOnSelectedDay,
                        onEventClick = viewModel::selectEvent,
                        modifier = Modifier.weight(1f),
                    )
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
        )
    }

    uiState.selectedEvent?.let { event ->
        EventActionsSheet(
            event = event,
            onEdit = viewModel::beginEditingSelectedEvent,
            onHide = viewModel::hideSelectedEvent,
            onSetCategory = viewModel::setSelectedEventCategory,
            onResetCustomization = viewModel::resetSelectedEventCustomization,
            onDismiss = viewModel::dismissEventActions,
        )
    }

    uiState.editingEvent?.let { event ->
        EditEventSheet(
            event = event,
            seriesScope = uiState.isEditingSeries,
            onSave = viewModel::saveEventOverrides,
            onDismiss = viewModel::dismissEditing,
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
