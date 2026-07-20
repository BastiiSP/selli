package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.ui.components.eventTimelineStyle
import com.prehmus.selli.ui.theme.selliGradient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HourHeight = 56.dp
private val GutterWidth = 46.dp
private val MinBlockHeight = 24.dp
private const val HOURS_PER_DAY = 24

private val BlockTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Echter, scrollbarer 24-Stunden-Zeitstrahl für Woche (sieben Tagesspalten) und
 * Tag (eine Spalte). Termine sitzen proportional zu Uhrzeit/Dauer, überschneidende
 * teilen sich nebeneinander die Breite; ganztägige liegen im schmalen Bereich
 * oberhalb des Rasters. „Heute" trägt einen Kreis in der Spaltenüberschrift, die
 * aktuelle Uhrzeit eine deutliche „Jetzt"-Linie. Termine tragen die Personenfarbe
 * (Grün = Basti, Lila = Melli) wie in der Monatsansicht; die Kategorie tritt zurück
 * (Arbeit = Rahmen, Wir-Zeit = Verlauf) — siehe [eventTimelineStyle].
 */
@Composable
fun TimelineView(
    days: List<LocalDate>,
    today: LocalDate,
    selectedDay: LocalDate,
    now: LocalDateTime,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timelines = remember(days, eventsByDay) {
        days.associateWith { day -> layoutDay(eventsByDay[day].orEmpty(), day) }
    }
    val hasAllDay = timelines.values.any { it.allDay.isNotEmpty() }
    val nowMinute = if (days.contains(today)) now.hour * 60 + now.minute else null

    Column(modifier = modifier.fillMaxSize()) {
        DayHeaderRow(
            days = days,
            today = today,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay,
        )
        if (hasAllDay) {
            AllDayLane(
                days = days,
                timelines = timelines,
                onEventClick = onEventClick,
            )
        }
        HourGridDivider()
        // Nimmt den Platz unter den (fixen) Kopf-/Ganztagszeilen ein und scrollt darin
        // durch die 24 Stunden — weight statt fillMaxSize, sonst überliefe die Spalte.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            HourGutter()
            days.forEachIndexed { index, day ->
                DayColumn(
                    timeline = timelines.getValue(day),
                    isToday = day == today,
                    nowMinute = nowMinute,
                    drawRightBorder = index < days.lastIndex,
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayHeaderRow(
    days: List<LocalDate>,
    today: LocalDate,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(GutterWidth))
        days.forEach { day ->
            DayHeaderCell(
                day = day,
                isToday = day == today,
                isSelected = day == selectedDay,
                onClick = { onSelectDay(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // "Heute" trägt einen Verlaufs-Kreis; die reine Auswahl bleibt ein weicher Ring.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(30.dp)
                .then(
                    when {
                        isToday -> Modifier.background(selliGradient(), CircleShape)
                        isSelected -> Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> com.prehmus.selli.ui.theme.onAccentColor()
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** Schmaler Bereich oberhalb des Stundenrasters für ganztägige Termine. */
@Composable
private fun AllDayLane(
    days: List<LocalDate>,
    timelines: Map<LocalDate, DayTimeline>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier.width(GutterWidth).padding(top = 4.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = "ganzt.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val allDay = timelines.getValue(day).allDay
                allDay.take(2).forEach { event ->
                    AllDayChip(event = event, onClick = { onEventClick(event) })
                }
                if (allDay.size > 2) {
                    Text(
                        text = "+${allDay.size - 2}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayChip(event: CalendarEvent, onClick: () -> Unit) {
    val style = eventTimelineStyle(event)
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.fill, shape)
            .then(style.outline?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = style.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HourGridDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Linke Spalte mit den Stundenbeschriftungen, exakt auf die Rasterlinien ausgerichtet. */
@Composable
private fun HourGutter(modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .width(GutterWidth)
            .height(HourHeight * HOURS_PER_DAY),
    ) {
        for (hour in 1 until HOURS_PER_DAY) {
            Text(
                text = "%02d".format(hour),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier
                    .offset(y = HourHeight * hour - 8.dp)
                    .width(GutterWidth - 6.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun DayColumn(
    timeline: DayTimeline,
    isToday: Boolean,
    nowMinute: Int?,
    drawRightBorder: Boolean,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val nowColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(HourHeight * HOURS_PER_DAY)
            .drawBehind {
                val hourPx = HourHeight.toPx()
                // Stundenlinien
                for (hour in 1 until HOURS_PER_DAY) {
                    val y = hourPx * hour
                    drawLine(
                        color = hourLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
                if (drawRightBorder) {
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f,
                    )
                }
                // "Jetzt"-Linie: über alle Spalten dünn (wirkt durchgehend), auf „heute" kräftig mit Punkt.
                nowMinute?.let { minute ->
                    val y = hourPx * (minute / 60f)
                    drawLine(
                        color = if (isToday) nowColor else nowColor.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = if (isToday) 2.5f else 1.5f,
                    )
                    if (isToday) {
                        drawCircle(color = nowColor, radius = 5.dp.toPx(), center = Offset(0f, y))
                    }
                }
            },
    ) {
        val columnWidth = maxWidth
        timeline.timed.forEach { positioned ->
            val segmentWidth = columnWidth / positioned.columnCount
            TimeBlock(
                event = positioned.event,
                onClick = { onEventClick(positioned.event) },
                modifier = Modifier
                    .offset(
                        x = segmentWidth * positioned.column + 1.dp,
                        y = HourHeight * (positioned.startMinute / 60f),
                    )
                    .width(segmentWidth - 3.dp)
                    .height((HourHeight * (positioned.durationMinutes / 60f)).coerceAtLeast(MinBlockHeight)),
            )
        }
    }
}

@Composable
private fun TimeBlock(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = eventTimelineStyle(event)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(style.fill, shape)
            .then(style.outline?.let { Modifier.border(1.5.dp, it, shape) } ?: Modifier)
            .clickable(onClick = onClick),
    ) {
        // Linker Akzentstreifen in Personenfarbe (Verlauf bei Wir-Zeit).
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxSize()
                .background(style.edge),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = style.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${event.start.toLocalTime().format(BlockTimeFormat)} – " +
                    event.end.toLocalTime().format(BlockTimeFormat),
                style = MaterialTheme.typography.labelSmall,
                color = style.content.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
