package com.prehmus.selli.ui.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DeletionScope
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.ui.components.CategorySelector
import com.prehmus.selli.ui.components.PersonPill
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ActionsDayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val ActionsTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Aktionen-Sheet für einen angetippten Termin: lokal ausblenden oder anpassen.
 * Arbeit/Privat bleiben lokale Kategorien; „Wir-Zeit" synchronisiert bei eigenen
 * Terminen den Partner als Google-Teilnehmer. Bei Serien-Vorkommen wird vorher der
 * Geltungsbereich erfragt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventActionsSheet(
    event: CalendarEvent,
    onEdit: (wholeSeries: Boolean) -> Unit,
    onHide: (wholeSeries: Boolean) -> Unit,
    onSetCategory: (category: EventCategory, wholeSeries: Boolean) -> Unit,
    onResetCustomization: () -> Unit,
    onDelete: (scope: DeletionScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingScopeAction by remember { mutableStateOf<ScopeAction?>(null) }
    // Bei Serien wird vor dem Umkategorisieren der Geltungsbereich erfragt.
    var pendingCategory by remember { mutableStateOf<EventCategory?>(null) }
    // Echtes Löschen wird immer per Dialog bestätigt (destruktiv, verändert Google).
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Nur eigene Google-Termine lassen sich echt löschen; sonst bleibt nur lokales Ausblenden.
    val canDelete = event.source == CalendarSource.GOOGLE_OWN
    val enabledCategories = when {
        event.source == CalendarSource.GOOGLE_OWN -> EventCategory.entries.toSet()
        event.category == EventCategory.TOGETHER -> setOf(EventCategory.TOGETHER)
        else -> setOf(EventCategory.WORK, EventCategory.PRIVATE)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                PersonPill(person = event.owner, isSharedEvent = event.isSharedEvent)
            }
            Text(
                text = buildString {
                    append(event.start.toLocalDate().format(ActionsDayFormat))
                    if (!event.isAllDay) {
                        append(", ${event.start.toLocalTime().format(ActionsTimeFormat)}")
                        append(" – ${event.end.toLocalTime().format(ActionsTimeFormat)}")
                    } else {
                        append(", ganztägig")
                    }
                    event.location?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Kategorie",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CategorySelector(
                selected = event.category,
                enabledCategories = enabledCategories,
                onSelect = { category ->
                    if (category != event.category) {
                        if (event.seriesId == null) {
                            onSetCategory(category, false)
                        } else {
                            pendingCategory = category
                        }
                    }
                },
            )

            Text(
                text = if (event.source == CalendarSource.GOOGLE_OWN) {
                    "Wir-Zeit erscheint in beiden Google-Kalendern. Arbeit und Privat sind lokale Kategorien."
                } else {
                    "Dieser Kalender ist schreibgeschützt; Wir-Zeit lässt sich nur bei eigenen Terminen ändern."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { resolveScope(event, ScopeAction.EDIT, onEdit) { pendingScopeAction = it } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("In Selli anpassen", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { resolveScope(event, ScopeAction.HIDE, onHide) { pendingScopeAction = it } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Text("In Selli ausblenden", modifier = Modifier.padding(start = 8.dp))
            }
            if (event.hasAnyCustomization) {
                OutlinedButton(
                    onClick = onResetCustomization,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Anpassung zurücksetzen", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // Echtes Löschen nur für eigene Google-Termine. Bewusst rot umrandet mit
            // transparenter Füllung, damit die destruktive Aktion nicht versehentlich
            // wie die daneben liegenden, harmlosen Aktionen aussieht.
            if (canDelete) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Endgültig löschen", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val destructiveColors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        )
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (event.seriesId == null) "Termin löschen?" else "Serientermin löschen?") },
            text = {
                Text(
                    buildString {
                        append("„${event.title}“ wird endgültig aus deinem Google-Kalender ")
                        append("entfernt. Das lässt sich nicht rückgängig machen.")
                        if (event.seriesId != null) {
                            append(" Wähle, wie viel der Serie gelöscht wird — ")
                            append("vergangene Vorkommen bleiben unberührt.")
                        }
                    },
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (event.seriesId == null) {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete(DeletionScope.SINGLE_OCCURRENCE)
                            },
                            colors = destructiveColors,
                        ) { Text("Löschen") }
                    } else {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete(DeletionScope.SINGLE_OCCURRENCE)
                            },
                            colors = destructiveColors,
                        ) { Text("Nur dieses Vorkommen") }
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete(DeletionScope.THIS_AND_FOLLOWING)
                            },
                            colors = destructiveColors,
                        ) { Text("Dieses und alle folgenden") }
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Abbrechen") }
                }
            },
        )
    }

    pendingScopeAction?.let { action ->
        val execute: (Boolean) -> Unit = if (action == ScopeAction.EDIT) onEdit else onHide
        AlertDialog(
            onDismissRequest = { pendingScopeAction = null },
            title = { Text(if (action == ScopeAction.EDIT) "Serie anpassen" else "Serie ausblenden") },
            text = {
                Text(
                    "Dieser Termin gehört zu einer Serie. Soll die Änderung nur dieses " +
                        "Vorkommen betreffen oder dieses und alle folgenden? " +
                        "Vergangene Vorkommen bleiben unberührt.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        pendingScopeAction = null
                        execute(false)
                    }) { Text("Nur dieses Vorkommen") }
                    TextButton(onClick = {
                        pendingScopeAction = null
                        execute(true)
                    }) { Text("Dieses und alle folgenden") }
                    TextButton(
                        onClick = { pendingScopeAction = null },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Abbrechen") }
                }
            },
        )
    }

    pendingCategory?.let { category ->
        val changesGoogleSharing =
            event.source == CalendarSource.GOOGLE_OWN &&
                event.isSharedEvent != (category == EventCategory.TOGETHER)
        AlertDialog(
            onDismissRequest = { pendingCategory = null },
            title = {
                Text(if (changesGoogleSharing) "Wir-Zeit für die Serie" else "Kategorie für die Serie")
            },
            text = {
                Text(
                    if (changesGoogleSharing) {
                        "Google kann Teilnehmer für ein einzelnes Vorkommen oder die gesamte " +
                            "Serie ändern. Welche Termine sollen in beiden Kalendern erscheinen?"
                    } else {
                        "Dieser Termin gehört zu einer Serie. Soll diese Kategorie nur für " +
                            "dieses Vorkommen gelten oder für dieses und alle folgenden? " +
                            "Vergangene Vorkommen bleiben unberührt."
                    },
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        pendingCategory = null
                        onSetCategory(category, false)
                    }) { Text("Nur dieses Vorkommen") }
                    TextButton(onClick = {
                        pendingCategory = null
                        onSetCategory(category, true)
                    }) {
                        Text(if (changesGoogleSharing) "Gesamte Serie" else "Dieses und alle folgenden")
                    }
                    TextButton(
                        onClick = { pendingCategory = null },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Abbrechen") }
                }
            },
        )
    }
}

private enum class ScopeAction { EDIT, HIDE }

/** Ohne Serie direkt ausführen, sonst erst den Geltungsbereich erfragen. */
private fun resolveScope(
    event: CalendarEvent,
    action: ScopeAction,
    execute: (wholeSeries: Boolean) -> Unit,
    askScope: (ScopeAction) -> Unit,
) {
    if (event.seriesId == null) execute(false) else askScope(action)
}
