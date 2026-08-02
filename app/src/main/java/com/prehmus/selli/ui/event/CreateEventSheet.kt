package com.prehmus.selli.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventRecurrence
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.model.RecurrenceFrequency
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.ui.components.CategorySelector
import com.prehmus.selli.ui.components.LocationAutocompleteField
import com.prehmus.selli.ui.components.scrollIntoViewOnFocus
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMAN)

/** Kurzform fürs Datum, wenn Datum und Uhrzeit sich eine Zeile teilen. */
private val CompactDateFormat = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val LocalDateSaver = Saver<LocalDate, Long>(
    save = { date -> date.toEpochDay() },
    restore = { epochDay -> LocalDate.ofEpochDay(epochDay) },
)

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
    onSave: (NewCalendarEvent, EventCategory?) -> Unit,
    onDismiss: () -> Unit,
    initialStartTime: LocalTime? = null,
    initialEndTime: LocalTime? = null,
    initialCategory: EventCategory? = null,
    suggestionRepository: PlaceSuggestionRepository? = null,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isAllDay by rememberSaveable { mutableStateOf(false) }
    var blocksSharedFreeTime by rememberSaveable { mutableStateOf(true) }
    var category by remember { mutableStateOf(initialCategory ?: EventCategory.PRIVATE) }
    var startDay by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(initialDay)
    }
    // Ein gemeinsamer Endtag für beide Fälle: ganztägig ist er der letzte (inklusive)
    // Tag, getimt das Datum der Endzeit. So bleibt der Ganztägig-Schalter verlustfrei.
    var endDay by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(initialDay)
    }
    var startTime by remember { mutableStateOf(initialStartTime ?: LocalTime.of(18, 0)) }
    var endTime by remember { mutableStateOf(initialEndTime ?: LocalTime.of(19, 0)) }
    var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }
    var timePickerTarget by remember { mutableStateOf<TimeTarget?>(null) }
    // Wiederholung: Google verwaltet die Serie nativ (RRULE) — die App legt keine Einzeltermine an.
    var recurrenceFrequency by rememberSaveable { mutableStateOf<RecurrenceFrequency?>(null) }
    var recurrenceUntil by remember { mutableStateOf<LocalDate?>(null) }
    var showUntilDatePicker by remember { mutableStateOf(false) }

    val isRecurrenceValid = recurrenceFrequency == null ||
        recurrenceUntil == null || !recurrenceUntil!!.isBefore(startDay)
    val isDateRangeValid = !isAllDay || !endDay.isBefore(startDay)
    // Getimte Termine dürfen über Mitternacht laufen — geprüft wird deshalb der
    // vollständige Zeitpunkt aus Datum und Uhrzeit, nicht mehr nur die Uhrzeit.
    val isTimeRangeValid = isAllDay || endDay.atTime(endTime).isAfter(startDay.atTime(startTime))
    val isValid =
        title.isNotBlank() &&
            isTimeRangeValid &&
            isDateRangeValid &&
            isRecurrenceValid

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
            Text(text = "Neuer Termin", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollIntoViewOnFocus(),
            )

            Text(
                text = "Kategorie",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CategorySelector(selected = category, onSelect = { category = it })

            LabeledSwitch(
                label = "Ganztägig",
                checked = isAllDay,
                onCheckedChange = { enabled ->
                    isAllDay = enabled
                    // In beiden Richtungen darf der Endtag nie vor dem Starttag landen.
                    if (endDay.isBefore(startDay)) {
                        endDay = startDay
                    }
                    // Getimt: gleicher Tag, aber Ende vor Beginn wäre ungültig — geraderücken.
                    if (!enabled && endDay == startDay && !endTime.isAfter(startTime)) {
                        endTime = startTime.plusHours(1)
                        if (!endTime.isAfter(startTime)) endDay = startDay.plusDays(1)
                    }
                },
            )

            if (isAllDay) {
                OutlinedButton(
                    onClick = { datePickerTarget = DateTarget.START },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Von ${startDay.format(DateFormat)}")
                }
                OutlinedButton(
                    onClick = { datePickerTarget = DateTarget.END },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Bis einschließlich ${endDay.format(DateFormat)}")
                }
                if (!isDateRangeValid) {
                    Text(
                        text = "Der Endtag darf nicht vor dem Starttag liegen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LabeledSwitch(
                    label = "Zählt als beschäftigt",
                    checked = blocksSharedFreeTime,
                    onCheckedChange = { blocksSharedFreeTime = it },
                    description = "Blockiert eure gemeinsame Frei-Zeit-Anzeige – unabhängig von der Kategorie.",
                )
            } else {
                // Beginn und Ende bekommen je eine eigene Zeile mit Datum UND Uhrzeit,
                // damit ein Termin auch über Mitternacht hinausreichen kann.
                DateTimeRow(
                    label = "Von",
                    day = startDay,
                    time = startTime,
                    onPickDay = { datePickerTarget = DateTarget.START },
                    onPickTime = { timePickerTarget = TimeTarget.START },
                )
                DateTimeRow(
                    label = "Bis",
                    day = endDay,
                    time = endTime,
                    onPickDay = { datePickerTarget = DateTarget.END },
                    onPickTime = { timePickerTarget = TimeTarget.END },
                )
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

            RecurrencePicker(
                frequency = recurrenceFrequency,
                until = recurrenceUntil,
                onFrequencyChange = { frequency ->
                    recurrenceFrequency = frequency
                    if (frequency == null) recurrenceUntil = null
                },
                onPickUntil = { showUntilDatePicker = true },
                onClearUntil = { recurrenceUntil = null },
            )
            if (!isRecurrenceValid) {
                Text(
                    text = "Das Serienende darf nicht vor dem ersten Termin liegen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    onSave(
                        buildNewCalendarEvent(
                            title = title,
                            startDay = startDay,
                            startTime = startTime,
                            endDay = endDay,
                            endTime = endTime,
                            isAllDay = isAllDay,
                            location = location,
                            category = category,
                            recurrence = recurrenceFrequency?.let {
                                EventRecurrence(frequency = it, until = recurrenceUntil)
                            },
                            blocksSharedFreeTime = blocksSharedFreeTime,
                            description = description,
                        ),
                        category,
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

    if (showUntilDatePicker) {
        val untilPickerState = rememberDatePickerState(
            initialSelectedDateMillis = (recurrenceUntil ?: startDay)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showUntilDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    untilPickerState.selectedDateMillis?.let {
                        recurrenceUntil = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showUntilDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showUntilDatePicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = untilPickerState)
        }
    }

    datePickerTarget?.let { target ->
        val initialDayForTarget =
            if (target == DateTarget.START) startDay else endDay
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
                            DateTarget.START -> {
                                if (isAllDay) {
                                    startDay = selected
                                    if (endDay.isBefore(selected)) endDay = selected
                                } else {
                                    // Getimt: der Endtag wandert mit, die Dauer bleibt erhalten
                                    // (wie beim Verschieben der Beginn-Uhrzeit).
                                    val dayOffset = ChronoUnit.DAYS.between(startDay, endDay)
                                    startDay = selected
                                    endDay = selected.plusDays(dayOffset)
                                }
                            }
                            DateTarget.END -> endDay = selected
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
                            // Termindauer beim Verschieben des Beginns beibehalten —
                            // über den vollen Zeitpunkt, damit auch Termine über
                            // Mitternacht korrekt mitwandern.
                            val duration = Duration.between(
                                startDay.atTime(startTime),
                                endDay.atTime(endTime),
                            )
                            val newEnd = startDay.atTime(picked).plus(duration)
                            startTime = picked
                            endDay = newEnd.toLocalDate()
                            endTime = newEnd.toLocalTime()
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

private enum class DateTarget { START, END }

private enum class TimeTarget { START, END }

private fun RecurrenceFrequency?.label(): String = when (this) {
    null -> "Nie"
    RecurrenceFrequency.DAILY -> "Täglich"
    RecurrenceFrequency.WEEKLY -> "Wöchentlich"
    RecurrenceFrequency.MONTHLY -> "Monatlich"
    RecurrenceFrequency.YEARLY -> "Jährlich"
}

/**
 * Wiederholungs-Auswahl: Häufigkeit plus optionales Serienende. Google
 * verwaltet die Serie nativ — hier wird nur der Wunsch erfasst.
 */
@Composable
private fun RecurrencePicker(
    frequency: RecurrenceFrequency?,
    until: LocalDate?,
    onFrequencyChange: (RecurrenceFrequency?) -> Unit,
    onPickUntil: () -> Unit,
    onClearUntil: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var frequencyMenuOpen by remember { mutableStateOf(false) }
    var untilMenuOpen by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { frequencyMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Wiederholung: ${frequency.label()}")
            }
            DropdownMenu(expanded = frequencyMenuOpen, onDismissRequest = { frequencyMenuOpen = false }) {
                (listOf<RecurrenceFrequency?>(null) + RecurrenceFrequency.entries).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        onClick = {
                            frequencyMenuOpen = false
                            onFrequencyChange(option)
                        },
                    )
                }
            }
        }

        if (frequency != null) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { untilMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(until?.let { "Bis ${it.format(UntilFormat)}" } ?: "Endet: nie")
                }
                DropdownMenu(expanded = untilMenuOpen, onDismissRequest = { untilMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Endet: nie") },
                        onClick = {
                            untilMenuOpen = false
                            onClearUntil()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("An einem Datum …") },
                        onClick = {
                            untilMenuOpen = false
                            onPickUntil()
                        },
                    )
                }
            }
        }
    }
}

private val UntilFormat = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.GERMAN)

/**
 * Eine Zeile „Von"/„Bis" mit Datums- und Uhrzeit-Auswahl. Das Datum bekommt mehr
 * Platz als die Uhrzeit; das Label steht davor, damit beide Buttons kurz bleiben.
 * Wird von Anlegen- und Bearbeiten-Sheet geteilt.
 */
@Composable
internal fun DateTimeRow(
    label: String,
    day: LocalDate,
    time: LocalTime,
    onPickDay: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        OutlinedButton(
            onClick = onPickDay,
            contentPadding = compactPadding,
            modifier = Modifier.weight(2f),
        ) {
            Text(
                text = day.format(CompactDateFormat),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onPickTime,
            contentPadding = compactPadding,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = time.format(TimeFormat), maxLines = 1)
        }
    }
}

@Composable
internal fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
