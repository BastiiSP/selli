package com.prehmus.selli.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.CloudSyncException

/**
 * Darstellungskategorie eines Cloud-Fehlers (Ideen, Kosten, Standort). Die Einteilung selbst
 * trifft die Datenschicht über [CloudSyncException]; hier wird nur entschieden, welcher Text und
 * welche Handlung angeboten wird — rohe HTTP-Meldungen erreichen die Oberfläche nie.
 */
enum class CloudIssue {
    /** Sitzung nicht still wiederherstellbar: Google-Konto kurz bestätigen. */
    RECONFIRM,

    /** Netz oder Server nicht erreichbar. */
    OFFLINE,

    /** Sonstiger Serverfehler. */
    FAILED,

    /** Keine Supabase-Zugangsdaten im Build. */
    NOT_CONFIGURED,
}

fun Throwable.toCloudIssue(): CloudIssue = when (this) {
    is CloudSyncException.ReconfirmationRequired -> CloudIssue.RECONFIRM
    is CloudSyncException.Offline -> CloudIssue.OFFLINE
    is CloudSyncException.NotConfigured -> CloudIssue.NOT_CONFIGURED
    else -> CloudIssue.FAILED
}

/**
 * Snackbar-Text für eine fehlgeschlagene Aktion, z. B.
 * „Ordner konnte nicht angelegt werden. Keine Verbindung – bitte später erneut versuchen."
 */
fun Throwable.cloudActionMessage(failedAction: String): String =
    "$failedAction ${toCloudIssue().actionHint()}"

private fun CloudIssue.actionHint(): String = when (this) {
    CloudIssue.RECONFIRM -> "Bitte bestätige dein Google-Konto erneut."
    CloudIssue.OFFLINE -> "Keine Verbindung – bitte später erneut versuchen."
    CloudIssue.FAILED -> "Bitte versuch es gleich noch einmal."
    CloudIssue.NOT_CONFIGURED -> "Die Cloud-Anbindung ist nicht eingerichtet."
}

private fun CloudIssue.title(): String = when (this) {
    CloudIssue.RECONFIRM -> "Anmeldung abgelaufen"
    CloudIssue.OFFLINE -> "Keine Verbindung"
    CloudIssue.FAILED -> "Gerade hakt es"
    CloudIssue.NOT_CONFIGURED -> "Nicht eingerichtet"
}

/** [subject] im Plural, z. B. „Ideen", „Ausgaben", „Standorte". */
private fun CloudIssue.body(subject: String, hasContent: Boolean): String = when (this) {
    CloudIssue.RECONFIRM ->
        "Bestätige kurz dein Google-Konto, dann geht's weiter. Deine Partnerverknüpfung bleibt bestehen."
    CloudIssue.OFFLINE -> if (hasContent) {
        "$subject sind gerade nicht aktuell. Selli versucht es weiter."
    } else {
        "$subject konnten nicht geladen werden. Prüfe deine Internetverbindung."
    }
    CloudIssue.FAILED -> if (hasContent) {
        "$subject konnten nicht aktualisiert werden."
    } else {
        "$subject konnten nicht geladen werden."
    }
    CloudIssue.NOT_CONFIGURED -> "Die Cloud-Anbindung fehlt in diesem Build."
}

/**
 * Was die Bestätigungs-Schaltfläche gerade zeigt — kommt aus dem gemeinsamen
 * [com.prehmus.selli.ui.shell.CloudAccessViewModel], damit alle drei Bereiche denselben Stand
 * sehen.
 */
data class CloudReconfirmUi(
    val isRunning: Boolean = false,
    /** Rückmeldung nach Abbruch/falschem Konto, sonst null. */
    val hint: String? = null,
)

/**
 * Schmale Hinweiskarte über bereits geladenen Inhalten: der zuletzt bekannte Stand bleibt
 * sichtbar, die Karte erklärt nur, warum er gerade nicht frisch ist.
 */
@Composable
fun CloudIssueBanner(
    issue: CloudIssue,
    subject: String,
    reconfirm: CloudReconfirmUi,
    onRetry: () -> Unit,
    onReconfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // Erscheint nachträglich über geladenem Inhalt — TalkBack soll ihn ankündigen. Bewusst
        // ohne mergeDescendants, damit die Schaltflächen einzeln fokussierbar bleiben.
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_pondering),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = issue.title(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = issue.body(subject, hasContent = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CloudIssueActions(
                    issue = issue,
                    reconfirm = reconfirm,
                    onRetry = onRetry,
                    onReconfirm = onReconfirm,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Ersetzt den Leerzustand, solange noch nie erfolgreich geladen wurde — sonst stünde dort
 * fälschlich „Noch keine Ordner", obwohl nur das Laden gescheitert ist.
 */
@Composable
fun CloudLoadFailedState(
    issue: CloudIssue,
    subject: String,
    reconfirm: CloudReconfirmUi,
    onRetry: () -> Unit,
    onReconfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        // Scrollbar, damit die Schaltfläche auch bei großer Schriftskalierung erreichbar bleibt.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_pondering),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Text(
                text = issue.title(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = issue.body(subject, hasContent = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            CloudIssueActions(
                issue = issue,
                reconfirm = reconfirm,
                onRetry = onRetry,
                onReconfirm = onReconfirm,
                centered = true,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Ladezustand vor dem allerersten Ergebnis — mit der „sammelnden" Pose statt leerer Fläche. */
@Composable
fun CloudInitialLoading(subject: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_loading),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Text(
                text = "$subject werden geladen …",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CloudIssueActions(
    issue: CloudIssue,
    reconfirm: CloudReconfirmUi,
    onRetry: () -> Unit,
    onReconfirm: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    if (issue == CloudIssue.NOT_CONFIGURED) return
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (issue == CloudIssue.RECONFIRM) {
            Button(onClick = onReconfirm, enabled = !reconfirm.isRunning) {
                // Text bleibt auch im Laufzustand stehen, sonst hätte der Button für TalkBack
                // keinen Namen mehr.
                if (reconfirm.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("Konto wird bestätigt …")
                } else {
                    Text("Konto bestätigen")
                }
            }
            reconfirm.hint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
            }
        } else {
            OutlinedButton(onClick = onRetry) { Text("Erneut versuchen") }
        }
    }
}
