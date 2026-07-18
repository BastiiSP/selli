package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.ui.event.CreateEventSheet

/**
 * Hauptansicht: Monatsgrid mit Tagesdetail darunter (Bedienlogik wie im
 * Outlook-Kalender, bewusst übernommen) im warmen Selli-Look. Alle Termine
 * beider Personen plus Arbeitskalender in einer gemeinsamen Ansicht.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
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
                bothFreeOnSelectedDay = uiState.bothFreeOnSelectedDay,
                onPreviousMonth = { viewModel.showMonth(uiState.visibleMonth.minusMonths(1)) },
                onNextMonth = { viewModel.showMonth(uiState.visibleMonth.plusMonths(1)) },
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
            )
        }
    }

    if (uiState.isCreateSheetOpen) {
        CreateEventSheet(
            initialDay = uiState.selectedDay,
            isSaving = uiState.isSavingEvent,
            onSave = viewModel::createEvent,
            onDismiss = viewModel::dismissCreateSheet,
        )
    }
}
