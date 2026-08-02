package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.format.EventTimeFormatter
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.ui.components.CategoryChip
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.PersonPill
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayTitleFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** Tagesdetail unter dem Grid — wie im Outlook-Kalender, nur weicher. */
@Composable
fun DayDetail(
    day: LocalDate,
    events: List<CalendarEvent>,
    bothFree: Boolean,
    onEventClick: (CalendarEvent) -> Unit,
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
            val rows = remember(events) { events.toEventListRows() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 96.dp,
                ),
            ) {
                items(
                    items = rows,
                    key = { row ->
                        when (row) {
                            is EventListRow.Single ->
                                "single:${row.event.source}:${row.event.id}"
                            is EventListRow.Group ->
                                "group:${row.wirTermin.source}:${row.wirTermin.id}"
                        }
                    },
                ) { row ->
                    when (row) {
                        is EventListRow.Single ->
                            EventCard(
                                event = row.event,
                                onClick = { onEventClick(row.event) },
                            )
                        is EventListRow.Group ->
                            EventGroupCard(row = row, onEventClick = onEventClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventGroupCard(
    row: EventListRow.Group,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EventCard(
            event = row.wirTermin,
            onClick = { onEventClick(row.wirTermin) },
        )
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(selliGradient(), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.overlapping.forEach { event ->
                    EventCard(
                        event = event,
                        onClick = { onEventClick(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Farbkapsel: Personenfarbe, bzw. der Lila-Grün-Verlauf für Wir-Zeit
            // (und weiterhin für explizit gemeinsam erstellte Termine).
            val capsuleBrush =
                if (event.category == EventCategory.TOGETHER || event.isSharedEvent) {
                    selliGradient()
                } else {
                    SolidColor(personColor(event.owner))
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            if (event.seriesId != null) {
                                // Serien-Vorkommen: kleines Wiederholungszeichen vor der Zeit.
                                append("↻  ")
                            }
                            // Tagesübergreifende Termine nennen zusätzlich die Tage —
                            // die Formatierung liegt zentral in EventTimeFormatter.
                            append(
                                EventTimeFormatter
                                    .formatRange(event.start, event.end, event.isAllDay)
                                    .replaceFirstChar { it.uppercase(Locale.GERMAN) },
                            )
                            event.location?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Mehrtägige Termine werden länger — höchstens zwei Zeilen, damit
                        // die Karten in der Liste nicht ungleich hoch werden.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Bei Wir-Zeit trägt der Verlaufs-Chip die „gemeinsam"-Aussage schon —
                // dann keine zusätzliche „Gemeinsam"-Pill, um Dopplung zu vermeiden.
                if (event.category != EventCategory.TOGETHER) {
                    PersonPill(person = event.owner, isSharedEvent = event.isSharedEvent)
                }
                CategoryChip(category = event.category, owner = event.owner)
            }
        }
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
