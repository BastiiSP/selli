package com.prehmus.selli.ui.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import com.prehmus.selli.ui.event.CreateEventSheet
import com.prehmus.selli.ui.event.CustomizationManagerSheet
import com.prehmus.selli.ui.event.EditEventSheet
import com.prehmus.selli.ui.event.EventActionsSheet

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
                month = uiState.visibleMonth,
                isSyncing = uiState.isSyncing,
                freeBlocks = uiState.freeBlocksOnSelectedDay,
                onPreviousMonth = { viewModel.showMonth(uiState.visibleMonth.minusMonths(1)) },
                onNextMonth = { viewModel.showMonth(uiState.visibleMonth.plusMonths(1)) },
                onManageCustomizations = viewModel::openCustomizationManager,
                onSwitchAccount = { showSwitchAccountDialog = true },
                onFreeBlockClick = viewModel::openCreateSheetForFreeBlock,
            )
            MonthGrid(
                month = uiState.visibleMonth,
                today = uiState.today,
                selectedDay = uiState.selectedDay,
                eventsByDay = uiState.eventsByDay,
                onSelectDay = viewModel::selectDay,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            DayDetail(
                day = uiState.selectedDay,
                events = uiState.selectedDayEvents,
                bothFree = uiState.bothFreeOnSelectedDay,
                onEventClick = viewModel::selectEvent,
            )
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
