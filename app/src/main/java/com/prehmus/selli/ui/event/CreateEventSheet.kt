package com.prehmus.selli.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMAN)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Bottom Sheet zum Anlegen eines neuen Termins. Die Speicherung übernimmt die
 * Google-Kalender-Anbindung (Owner: Codex) über CalendarRepository.createEvent —
 * der Termin landet im eigenen Google-Konto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventSheet(
    initialDay: LocalDate,
    isSaving: Boolean,
    onSave: (NewCalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var isAllDay by rememberSaveable { mutableStateOf(false) }
    var invitePartner by rememberSaveable { mutableStateOf(false) }
    var day by remember { mutableStateOf(initialDay) }
    var startTime by remember { mutableStateOf(LocalTime.of(18, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(19, 0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<TimeTarget?>(null) }

    val isValid = title.isNotBlank() && (isAllDay || endTime.isAfter(startTime))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Neuer Termin", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(day.format(DateFormat))
            }

            LabeledSwitch(
                label = "Ganztägig",
                checked = isAllDay,
                onCheckedChange = { isAllDay = it },
            )

            if (!isAllDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { timePickerTarget = TimeTarget.START },
                        modifier = Modifier.weight(1f),
                    ) { Text("Von ${startTime.format(TimeFormat)}") }
                    OutlinedButton(
                        onClick = { timePickerTarget = TimeTarget.END },
                        modifier = Modifier.weight(1f),
                    ) { Text("Bis ${endTime.format(TimeFormat)}") }
                }
                if (!endTime.isAfter(startTime)) {
                    Text(
                        text = "Das Ende muss nach dem Beginn liegen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledSwitch(
                label = "Partner einladen — wird ein gemeinsamer Termin",
                checked = invitePartner,
                onCheckedChange = { invitePartner = it },
            )

            Button(
                onClick = {
                    val start = if (isAllDay) day.atStartOfDay() else day.atTime(startTime)
                    val end = if (isAllDay) day.plusDays(1).atStartOfDay() else day.atTime(endTime)
                    onSave(
                        NewCalendarEvent(
                            title = title.trim(),
                            start = start,
                            end = end,
                            isAllDay = isAllDay,
                            location = location.trim().ifBlank { null },
                            invitePartner = invitePartner,
                        )
                    )
                },
                enabled = isValid && !isSaving,
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
                        text = if (isSaving) "Speichert …" else "Termin anlegen",
                        style = MaterialTheme.typography.labelLarge,
                        color = onAccentColor(),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    timePickerTarget?.let { target ->
        val initial = if (target == TimeTarget.START) startTime else endTime
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(if (target == TimeTarget.START) "Beginn" else "Ende") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = LocalTime.of(timeState.hour, timeState.minute)
                    when (target) {
                        TimeTarget.START -> {
                            // Termindauer beim Verschieben des Beginns beibehalten.
                            val duration = java.time.Duration.between(startTime, endTime)
                            startTime = picked
                            endTime = picked.plus(duration)
                        }
                        TimeTarget.END -> endTime = picked
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

private enum class TimeTarget { START, END }

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
