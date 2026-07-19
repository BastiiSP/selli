package com.prehmus.selli.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import com.prehmus.selli.ui.components.PersonPill
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ActionsDayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val ActionsTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Aktionen-Sheet für einen angetippten Termin: lokal ausblenden oder anpassen.
 * Beides gilt nur für Sellis eigene Ansicht — der echte Google-Termin wird nie
 * verändert. Bei Serien-Vorkommen wird vorher der Geltungsbereich erfragt
 * ("nur dieses Vorkommen" vs. "dieses und alle folgenden").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventActionsSheet(
    event: CalendarEvent,
    onEdit: (wholeSeries: Boolean) -> Unit,
    onHide: (wholeSeries: Boolean) -> Unit,
    onResetCustomization: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingScopeAction by remember { mutableStateOf<ScopeAction?>(null) }

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
                text = "Änderungen gelten nur in Selli — der echte Kalender bleibt unverändert.",
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
            if (event.isCustomized) {
                OutlinedButton(
                    onClick = onResetCustomization,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Anpassung zurücksetzen", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
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
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pendingScopeAction = null
                        execute(false)
                    }) { Text("Nur dieses Vorkommen") }
                    TextButton(onClick = {
                        pendingScopeAction = null
                        execute(true)
                    }) { Text("Dieses und alle folgenden") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingScopeAction = null }) { Text("Abbrechen") }
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
