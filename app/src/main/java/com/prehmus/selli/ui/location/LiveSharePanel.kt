package com.prehmus.selli.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.location.LiveShareDuration
import com.prehmus.selli.domain.location.validAt
import com.prehmus.selli.domain.model.LocationFreshness
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.model.freshness
import com.prehmus.selli.domain.model.isLiveSharing
import com.prehmus.selli.domain.model.liveRemaining
import com.prehmus.selli.domain.model.measurementAge
import com.prehmus.selli.domain.repository.OwnLiveShareState
import com.prehmus.selli.ui.components.PersonAvatar
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.personSoftColor
import java.time.Instant

/**
 * Untere Karte des Standort-Tabs, Bedienprinzip wie bei WhatsApp: wer gerade live teilt (mit
 * Restzeit und echtem Messalter), darunter die eigene Steuerung — „Live teilen“ bzw. Restzeit
 * mit „Beenden“. Ohne Live-Freigabe erklärt sie den sparsamen Normalbetrieb.
 */
@Composable
fun LiveSharePanel(
    ownPerson: Person,
    ownState: OwnLiveShareState,
    locations: List<PersonLocation>,
    now: Instant,
    startProblem: LiveStartProblem?,
    showBackgroundHint: Boolean,
    onStartRequest: () -> Unit,
    onStop: () -> Unit,
    onDismissProblem: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partnerLive = locations.filter { it.person != ownPerson && it.isLiveSharing(now) }
    val ownShare = ownState.share.validAt(now)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            partnerLive.forEach { location -> PartnerLiveRow(location = location, now = now) }
            if (partnerLive.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (ownShare != null) {
                OwnLiveRow(
                    ownPerson = ownPerson,
                    remainingLabel = formatRemaining(ownShare.remaining(now)),
                    endsAtLabel = formatClockTime(ownShare.endsAt),
                    statusLabel = ownState.statusLabel(ownPerson.partner()),
                    showBackgroundHint = showBackgroundHint,
                    onStop = onStop,
                )
            } else {
                NormalModeRow(ownPerson = ownPerson, onStartRequest = onStartRequest)
            }

            if (startProblem != null) {
                StartProblemRow(
                    problem = startProblem,
                    onDismiss = onDismissProblem,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun PartnerLiveRow(location: PersonLocation, now: Instant) {
    val remaining = location.liveRemaining(now)?.let(::formatRemaining).orEmpty()
    val age = formatMeasurementAge(location.measurementAge(now))
    val stale = location.freshness(now) == LocationFreshness.STALE
    Row(
        // Eine Ansage statt vier Fokusziele (Avatar, Titel, Restzeit, Pill).
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(person = location.person, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${location.person.displayName()} teilt live",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$remaining · gemessen $age",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stale) {
                Text(
                    // Freigabe läuft noch, aber es kommen keine Messungen an (Funkloch, Akku …).
                    text = "Keine neuen Positionen — Anzeige ist veraltet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LivePill(person = location.person, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun OwnLiveRow(
    ownPerson: Person,
    remainingLabel: String,
    endsAtLabel: String,
    statusLabel: String,
    showBackgroundHint: Boolean,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PersonAvatar(person = ownPerson, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Du teilst live",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "$remainingLabel · bis $endsAtLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onStop) { Text("Beenden") }
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (showBackgroundHint) {
            Text(
                text = "Läuft auch bei gesperrtem Bildschirm. Mit Standort „Immer erlauben“ " +
                    "übersteht die Freigabe zusätzlich einen Neustart der App durch Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NormalModeRow(ownPerson: Person, onStartRequest: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Normalbetrieb", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Dein Standort wird sparsam etwa alle 15 Min. aktualisiert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onStartRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = personColor(ownPerson),
                contentColor = onAccentColor(),
            ),
        ) { Text("Live teilen") }
    }
}

@Composable
private fun StartProblemRow(
    problem: LiveStartProblem,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        Text(
            text = when (problem) {
                LiveStartProblem.MISSING_PRECISE_PERMISSION ->
                    "Für die Live-Freigabe braucht Selli den genauen Standort. Bitte in den " +
                        "App-Einstellungen „Genauen Standort verwenden“ erlauben."
                LiveStartProblem.NOT_CONFIGURED -> "Die Standortfreigabe ist noch nicht eingerichtet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (problem == LiveStartProblem.MISSING_PRECISE_PERMISSION) {
                TextButton(onClick = onOpenSettings) { Text("Einstellungen") }
            }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** Kleiner „● LIVE“-Hinweis in der Personenfarbe. */
@Composable
fun LivePill(person: Person, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = personColor(person), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(onAccentColor(), CircleShape),
            )
            Text(text = "LIVE", style = MaterialTheme.typography.labelSmall, color = onAccentColor())
        }
    }
}

/**
 * Dauerauswahl beim Start. Der Akku-Hinweis steht bewusst hier und nicht erst in den
 * Einstellungen: die erhöhte Taktung kostet spürbar Akku und soll eine bewusste Wahl sein.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveShareDurationSheet(
    ownPerson: Person,
    onSelect: (LiveShareDuration) -> Unit,
    onDismiss: () -> Unit,
) {
    val partnerName = ownPerson.partner().displayName()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Live-Standort teilen",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$partnerName sieht deine Bewegung laufend auf der Karte. Selli misst dafür " +
                    "etwa alle 10 Sekunden per GPS — das braucht spürbar mehr Akku. Danach geht es " +
                    "automatisch zurück zum sparsamen Normalbetrieb.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LiveShareDuration.entries.forEach { duration ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelect(duration) },
                    shape = RoundedCornerShape(16.dp),
                    color = personSoftColor(ownPerson),
                ) {
                    Text(
                        text = duration.label(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

private fun OwnLiveShareState.statusLabel(partner: Person): String = when {
    published -> "Für ${partner.displayName()} sichtbar."
    syncError != null -> "Noch nicht für ${partner.displayName()} sichtbar — keine Verbindung, " +
        "Selli versucht es weiter."
    else -> "Wird für ${partner.displayName()} sichtbar gemacht …"
}
