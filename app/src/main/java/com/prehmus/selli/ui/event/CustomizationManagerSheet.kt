package com.prehmus.selli.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.components.label
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FromDateFormat = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

/**
 * Verwaltung aller lokal gespeicherten Ausblendungen und Anpassungen — der
 * einzige Weg, ausgeblendete Termine wieder sichtbar zu machen. Einträge
 * betreffen nur Sellis Ansicht, nie den echten Google-Kalender.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationManagerSheet(
    customizations: List<EventCustomization>,
    onRemove: (CustomizationTarget) -> Unit,
    onDelete: (CustomizationTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ausgeblendet & angepasst",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Diese Änderungen gelten nur in Selli. Entfernst du einen Eintrag, zeigt Selli wieder das Original aus dem Kalender.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (customizations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelliMascot(mood = MascotMood.EMPTY, modifier = Modifier.size(72.dp))
                    Text(
                        text = "Nichts ausgeblendet, nichts angepasst.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(customizations, key = { it.target.toString() }) { customization ->
                        CustomizationRow(
                            customization = customization,
                            onRemove = onRemove,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationRow(
    customization: EventCustomization,
    onRemove: (CustomizationTarget) -> Unit,
    onDelete: (CustomizationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val occurrence = customization.target as? CustomizationTarget.Occurrence
    val source = occurrence?.key?.source
    val canDelete = source == CalendarSource.GOOGLE_OWN
    val canRequestDeletion =
        source == CalendarSource.GOOGLE_PARTNER &&
            customization.originalCategory == EventCategory.TOGETHER
    val label = customization.label.ifBlank { "Unbenannter Termin" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = buildString {
                        append(if (customization.hidden) "Ausgeblendet" else "Angepasst")
                        customization.overrides.category?.let { append(" · Kategorie: ${it.label()}") }
                        val target = customization.target
                        if (target is CustomizationTarget.SeriesFrom) {
                            append(" · Serie ab ${target.fromStart.toLocalDate().format(FromDateFormat)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canDelete || canRequestDeletion) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = if (canDelete) "Endgültig löschen" else "Löschen anfragen",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { onRemove(customization.target) }) {
                Text(if (customization.hidden) "Einblenden" else "Zurücksetzen")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (canDelete) "Termin löschen?" else "Löschen anfragen?") },
            text = {
                Text(
                    if (canDelete) {
                        "„$label“ wird endgültig aus deinem Google-Kalender entfernt. " +
                            "Das lässt sich nicht rückgängig machen."
                    } else {
                        "Die Person, die „$label“ angelegt hat, bekommt eine Anfrage, ihn zu löschen."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(customization.target)
                    },
                    colors = if (canDelete) {
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                ) {
                    Text(if (canDelete) "Löschen" else "Anfrage senden")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}
