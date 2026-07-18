package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.PersonPill
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val DayTitleFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** Tagesdetail unter dem Grid — wie im Outlook-Kalender, nur weicher. */
@Composable
fun DayDetail(
    day: LocalDate,
    events: List<CalendarEvent>,
    bothFree: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = day.format(DayTitleFormat),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (events.isEmpty()) {
            EmptyDay(bothFree = bothFree, modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 96.dp,
                ),
            ) {
                items(events, key = { "${it.source}:${it.id}" }) { event ->
                    EventCard(event = event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Farbkapsel: Personenfarbe bzw. Verlauf bei gemeinsamen Terminen.
            val capsuleBrush =
                if (event.isSharedEvent) selliGradient() else SolidColor(personColor(event.owner))
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .size(width = 5.dp, height = 44.dp)
                    .background(capsuleBrush, CircleShape),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(
                            if (event.isAllDay) "Ganztägig"
                            else "${event.start.toLocalTime().format(TimeFormat)} – ${event.end.toLocalTime().format(TimeFormat)}"
                        )
                        event.location?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PersonPill(person = event.owner, isSharedEvent = event.isSharedEvent)
                if (event.source == CalendarSource.WORK_ICS) {
                    WorkBadge()
                }
            }
        }
    }
}

/** Kennzeichnung für Termine aus Bastis (read-only) Arbeitskalender. */
@Composable
private fun WorkBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "Arbeit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Leerzustand mit Maskottchen — freut sich, wenn beide frei sind. */
@Composable
private fun EmptyDay(bothFree: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelliMascot(
            mood = if (bothFree) MascotMood.HAPPY else MascotMood.EMPTY,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = if (bothFree) "Ihr habt beide frei — Zeit für euch!" else "Hier steht nichts an.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
