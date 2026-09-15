package com.prehmus.selli.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    ownPerson: Person,
    onSave: (amount: Double, description: String, paidBy: Person, spentAt: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Vorbelegung ist die eigene Person (Spec) — sonst wäre auf Mellis Gerät jede Ausgabe
    // ohne manuelles Umschalten fälschlich als "Basti bezahlt" erfasst.
    var paidBy by remember { mutableStateOf(ownPerson) }
    var spentAt by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val canSave = amount != null && amount > 0.0 && description.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Ausgabe eintragen", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Betrag (€)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Bezahlt von", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Person.entries.forEachIndexed { index, person ->
                    SegmentedButton(
                        selected = paidBy == person,
                        onClick = { paidBy = person },
                        shape = SegmentedButtonDefaults.itemShape(index, Person.entries.size),
                    ) {
                        Text(if (person == Person.MELLI) "Melli" else "Basti")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Datum: ${spentAt.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))}")
                TextButton(onClick = { showDatePicker = true }) { Text("Ändern") }
            }

            Button(
                onClick = { onSave(amount!!, description, paidBy, spentAt) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = spentAt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        spentAt = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
