package com.prehmus.selli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Monatsraster im vertrauten Outlook-Stil, aber "warm eingekleidet": abgerundete
 * Tageszellen, weiche Auswahl statt harter Rahmen, Termin-Punkte in den
 * Personenfarben (Verlauf = gemeinsamer Termin).
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDay: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WeekdayHeader()
        val weeks = buildWeeks(month)
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        isSelected = day == selectedDay,
                        events = eventsByDay[day].orEmpty(),
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        isSelected -> MaterialTheme.colorScheme.surfaceVariant
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val borderBrush: Brush? = if (isSelected) selliGradient() else null

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.82f)
            .background(background, shape)
            .then(if (borderBrush != null) Modifier.border(2.dp, borderBrush, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            EventDots(events = events)
        }
    }
}

/** Bis zu drei Punkte pro Tag; jeder trägt die Farbe seiner Person. */
@Composable
private fun EventDots(events: List<CalendarEvent>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        events.distinctByAppearance().take(3).forEach { event ->
            val brush = if (event.isSharedEvent) selliGradient() else SolidColor(personColor(event.owner))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(brush, CircleShape)
            )
        }
    }
}

/** Für die Punkte zählt die Erscheinung (Person/gemeinsam), nicht jeder einzelne Termin. */
private fun List<CalendarEvent>.distinctByAppearance(): List<CalendarEvent> =
    distinctBy { if (it.isSharedEvent) "shared" else it.owner.name }

/** Immer volle Wochen (Mo–So), inklusive Überhang des Vor-/Folgemonats. */
private fun buildWeeks(month: YearMonth): List<List<LocalDate>> {
    val firstDay = month.atDay(1)
    val start = firstDay.minusDays(((firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
    val weeks = mutableListOf<List<LocalDate>>()
    var cursor = start
    do {
        weeks += (0..6).map { cursor.plusDays(it.toLong()) }
        cursor = cursor.plusDays(7)
    } while (!cursor.isAfter(month.atEndOfMonth()))
    return weeks
}
