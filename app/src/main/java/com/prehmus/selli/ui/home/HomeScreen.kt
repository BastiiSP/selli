package com.prehmus.selli.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.format.EventTimeFormatter
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.ui.calendar.CalendarUiState
import com.prehmus.selli.ui.theme.selliGradient
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Das zentrale Ziel („Wir"): die Wanderweg-Szene zur nächsten Wir-Zeit mit dem Flussgeist
 * als Begleitfigur, darunter die nächsten gemeinsamen Termine. Nimmt auf, was früher in
 * den Kalender-Header gequetscht war — und hat jetzt Platz dafür.
 *
 * Tickt minütlich, solange diese Composable in der Komposition ist — kein Hintergrundlauf.
 */
@Composable
fun HomeScreen(
    uiState: CalendarUiState,
    onOpenNextWirZeit: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }

    // Zweigeteilt statt einer LazyColumn: die Wanderweg-Szene bekommt den Großteil des
    // Bildschirms (Basti-Feedback 14.09.2026 — vorher nur ein kleiner, fest hoher Ausschnitt),
    // "Nächste gemeinsame Termine" bleibt darunter dauerhaft sichtbar und für sich scrollbar,
    // statt mit der Szene gemeinsam aus dem Bild zu wandern.
    Column(modifier = modifier.fillMaxSize()) {
        JourneyHero(
            uiState = uiState,
            now = now,
            onClick = onOpenNextWirZeit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.8f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "heading") {
                Text(
                    text = "Nächste gemeinsame Termine",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (uiState.upcomingWirZeitEvents.isEmpty()) {
                item(key = "empty") { NoSharedEventsHint() }
            } else {
                items(
                    items = uiState.upcomingWirZeitEvents,
                    key = { event -> "${event.source}:${event.id}" },
                ) { event ->
                    SharedEventCard(event = event, onClick = { onEventClick(event) })
                }
            }
        }
    }
}

/**
 * Die Szene sitzt auf einer Lila-Grün-Fläche, weil sie durchgehend mit
 * [com.prehmus.selli.ui.theme.onAccentColor] zeichnet — auf warmem Hintergrund wäre sie
 * unlesbar. Zugleich bleibt der Verlauf so das Branding-Element, das er im Header war.
 */
@Composable
private fun JourneyHero(
    uiState: CalendarUiState,
    now: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdown = remember(uiState.nextWirZeitEvent, now) {
        com.prehmus.selli.domain.countdown.calculateWirZeitCountdown(uiState.nextWirZeitEvent, now)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(selliGradient()),
        ) {
            // sceneHeight wächst mit dem zugewiesenen Platz statt fest zu sein (vorher 132.dp) —
            // die Pfad-/Wolken-Geometrie in WirZeitJourneyScene skaliert bereits über Anteile von
            // sceneHeightPx, siehe deren interne 0.28f/0.90f-Werte.
            val sceneHeight = (maxHeight - 32.dp).coerceAtLeast(84.dp)
            WirZeitCountdownScene(
                countdown = countdown,
                isSyncing = uiState.isSyncing,
                onClick = onClick,
                sceneHeight = sceneHeight,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            RiverSpirit(
                size = 84.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp),
            )
        }
    }
}

/** Termin-Karte mit Gradient-Rand als „gemeinsam"-Kennung — dieselbe dritte Kennung wie im Kalender. */
@Composable
private fun SharedEventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, selliGradient()),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Zentral formatiert, damit Homescreen und Tagesdetail identisch aussehen.
                text = EventTimeFormatter.formatRange(event.start, event.end, event.isAllDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.location?.takeUnless(String::isBlank)?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Leerzustand: das Maskottchen grübelt, wenn nichts Gemeinsames ansteht. */
@Composable
private fun NoSharedEventsHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.mascot_pondering),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Text(
            text = "Noch keine Wir-Zeit geplant",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Legt im Kalender einen Termin an und wählt „Wir-Zeit“ als Kategorie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
