package com.prehmus.selli.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.ui.components.LocationAutocompleteField
import com.prehmus.selli.ui.components.scrollIntoViewOnFocus
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val EditDateFormat = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMAN)
private val EditTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Bearbeiten-Sheet für lokale Anpassungen: schreibt nie in den Google-Kalender,
 * sondern erzeugt Feld-Überschreibungen (EventFieldOverrides), die die
 * Merge-Logik (Owner Codex) bei jedem Abruf über die Rohdaten legt.
 * Bei Serien-Geltungsbereich ist das Datum fixiert — angepasst werden dann
 * Titel, Uhrzeiten, Ort und Beschreibung aller künftigen Vorkommen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventSheet(
    event: CalendarEvent,
    seriesScope: Boolean,
    onSave: (EventFieldOverrides) -> Unit,
    onDismiss: () -> Unit,
    suggestionRepository: PlaceSuggestionRepository? = null,
) {
    var title by rememberSaveable { mutableStateOf(event.title) }
    var location by rememberSaveable { mutableStateOf(event.location.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(event.description.orEmpty()) }
    var day by remember { mutableStateOf(event.start.toLocalDate()) }
    // Enddatum nur für einzelne, getimte Vorkommen — bei Serien trägt die Merge-Logik
    // das Tages-Offset weiter, deshalb bleibt das Datum dort fixiert.
    var endDay by remember { mutableStateOf(event.end.toLocalDate()) }
    var startTime by remember { mutableStateOf(event.start.toLocalTime()) }
    var endTime by remember { mutableStateOf(event.end.toLocalTime()) }
    var blocksSharedFreeTime by rememberSaveable(event.id, event.blocksSharedFreeTime) {
        mutableStateOf(event.blocksSharedFreeTime)
    }
    var datePickerTarget by remember { mutableStateOf<EditDateTarget?>(null) }
    var timePickerTarget by remember { mutableStateOf<EditTimeTarget?>(null) }

    // Ein tagesübergreifender Serientermin (z. B. 22:00–02:00) darf beim
    // Serien-Anpassen nicht an der Uhrzeit-Prüfung hängen bleiben.
    val originalSpansDays = event.end.toLocalDate() != event.start.toLocalDate()
    val isTimeRangeValid = when {
        event.isAllDay -> true
        seriesScope -> originalSpansDays || endTime.isAfter(startTime)
        else -> endDay.atTime(endTime).isAfter(day.atTime(startTime))
    }
    val isValid = title.isNotBlank() && isTimeRangeValid

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (seriesScope) "Serie in Selli anpassen" else "Termin in Selli anpassen",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (seriesScope) {
                    "Gilt für dieses und alle folgenden Vorkommen — nur in Sellis Ansicht."
                } else {
                    "Gilt nur für Sellis Ansicht — der echte Kalender bleibt unverändert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollIntoViewOnFocus(),
            )

            if (event.isAllDay) {
                if (!seriesScope) {
                    OutlinedButton(
                        onClick = { datePickerTarget = EditDateTarget.START },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(day.format(EditDateFormat))
                    }
                }
                LabeledSwitch(
                    label = "Zählt als beschäftigt",
                    checked = blocksSharedFreeTime,
                    onCheckedChange = { blocksSharedFreeTime = it },
                    description = "Blockiert eure gemeinsame Frei-Zeit-Anzeige – unabhängig von der Kategorie.",
                )
            } else {
                if (seriesScope) {
                    // Serie: nur die Uhrzeiten — das Tages-Offset der Serie bleibt erhalten.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { timePickerTarget = EditTimeTarget.START },
                            modifier = Modifier.weight(1f),
                        ) { Text("Von ${startTime.format(EditTimeFormat)}") }
                        OutlinedButton(
                            onClick = { timePickerTarget = EditTimeTarget.END },
                            modifier = Modifier.weight(1f),
                        ) { Text("Bis ${endTime.format(EditTimeFormat)}") }
                    }
                } else {
                    // Einzelvorkommen: Beginn und Ende je mit eigenem Datum, damit ein
                    // Termin auch über Mitternacht hinausreichen darf.
                    DateTimeRow(
                        label = "Von",
                        day = day,
                        time = startTime,
                        onPickDay = { datePickerTarget = EditDateTarget.START },
                        onPickTime = { timePickerTarget = EditTimeTarget.START },
                    )
                    DateTimeRow(
                        label = "Bis",
                        day = endDay,
                        time = endTime,
                        onPickDay = { datePickerTarget = EditDateTarget.END },
                        onPickTime = { timePickerTarget = EditTimeTarget.END },
                    )
                }
                if (!isTimeRangeValid) {
                    Text(
                        text = "Das Ende muss nach dem Beginn liegen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            LocationAutocompleteField(
                value = location,
                onValueChange = { location = it },
                suggestionRepository = suggestionRepository,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung (optional)") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollIntoViewOnFocus(),
            )

            Button(
                onClick = {
                    onSave(
                        EventFieldOverrides(
                            title = title.trim().takeIf { it.isNotBlank() && it != event.title },
                            date = day.takeIf { !seriesScope && it != event.start.toLocalDate() },
                            endDate = endDay.takeIf {
                                !seriesScope && !event.isAllDay && it != event.end.toLocalDate()
                            },
                            startTime = startTime.takeIf { !event.isAllDay && it != event.start.toLocalTime() },
                            endTime = endTime.takeIf { !event.isAllDay && it != event.end.toLocalTime() },
                            location = location.trim().takeIf { it != event.location.orEmpty() },
                            description = description.trim().takeIf { it != event.description.orEmpty() },
                            blocksSharedFreeTime = blocksSharedFreeTime.takeIf {
                                event.isAllDay && it != event.blocksSharedFreeTime
                            },
                        )
                    )
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(selliGradient(), MaterialTheme.shapes.large)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Anpassung speichern",
                        style = MaterialTheme.typography.labelLarge,
                        color = onAccentColor(),
                    )
                }
            }
        }
    }

    datePickerTarget?.let { target ->
        val initialDayForTarget = if (target == EditDateTarget.START) day else endDay
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDayForTarget
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) {
                            EditDateTarget.START -> {
                                // Getimt: das Enddatum wandert mit, die Dauer bleibt erhalten.
                                val dayOffset = ChronoUnit.DAYS.between(day, endDay)
                                day = selected
                                if (!event.isAllDay) endDay = selected.plusDays(dayOffset)
                            }
                            EditDateTarget.END -> endDay = selected
                        }
                    }
                    datePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    timePickerTarget?.let { target ->
        val initial = if (target == EditTimeTarget.START) startTime else endTime
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(if (target == EditTimeTarget.START) "Beginn" else "Ende") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(timeState.hour, timeState.minute)
                    when (target) {
                        EditTimeTarget.START -> {
                            // Termindauer beim Verschieben des Beginns beibehalten. Bei
                            // Serien bleibt es bei der reinen Uhrzeit (das Tages-Offset
                            // trägt die Merge-Logik), sonst über den vollen Zeitpunkt.
                            if (seriesScope) {
                                val duration = Duration.between(startTime, endTime)
                                startTime = picked
                                endTime = picked.plus(duration)
                            } else {
                                val duration = Duration.between(
                                    day.atTime(startTime),
                                    endDay.atTime(endTime),
                                )
                                val newEnd = day.atTime(picked).plus(duration)
                                startTime = picked
                                endDay = newEnd.toLocalDate()
                                endTime = newEnd.toLocalTime()
                            }
                        }
                        EditTimeTarget.END -> endTime = picked
                    }
                    timePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTarget = null }) { Text("Abbrechen") }
            },
        )
    }
}

private enum class EditDateTarget { START, END }

private enum class EditTimeTarget { START, END }
