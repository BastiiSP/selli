package com.prehmus.selli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.personSoftColor
import com.prehmus.selli.ui.theme.selliGradient

/** Deutscher Anzeigename der Kategorie. */
fun EventCategory.label(): String = when (this) {
    EventCategory.WORK -> "Arbeit"
    EventCategory.PRIVATE -> "Privat"
    EventCategory.TOGETHER -> "Wir-Zeit"
}

/**
 * Darstellung eines Termins im Zeitstrahl. Primär trägt jetzt die **Person** die Farbe
 * (Grün = Basti, Lila = Melli) — genau wie die Pills in der Monatsansicht, damit Bastis
 * Outlook- und Mellis Dr.-Plano-Arbeitstermine nicht länger gleich aussehen. Die
 * **Kategorie** tritt optisch zurück und wird über die Form vermittelt: „Arbeit" bekommt
 * einen Rahmen ([outline]) in Personenfarbe auf hellem Grund, „Privat" bleibt eine satt
 * gefüllte Personen-Kachel, „Wir-Zeit" behält bewusst den Lila-Grün-Verlauf (betrifft
 * ohnehin beide).
 *
 * [fill] füllt den Terminblock, [edge] ist der kräftige linke Akzentstreifen, [content]
 * die Text-/Detailfarbe und [outline] – wenn gesetzt – der Kategorie-Rahmen für Arbeit.
 */
data class EventTimelineStyle(
    val fill: Brush,
    val edge: Brush,
    val content: Color,
    val outline: Color? = null,
)

@Composable
@ReadOnlyComposable
fun eventTimelineStyle(event: CalendarEvent): EventTimelineStyle {
    val content = MaterialTheme.colorScheme.onSurface
    if (event.category == EventCategory.TOGETHER) {
        // Wir-Zeit: der Lila-Grün-Verlauf als sanft getönte Fläche, kräftiger Verlaufs-Rand.
        val melli = personColor(Person.MELLI)
        val basti = personColor(Person.BASTI)
        return EventTimelineStyle(
            fill = Brush.linearGradient(listOf(melli.copy(alpha = 0.22f), basti.copy(alpha = 0.22f))),
            edge = selliGradient(),
            content = content,
        )
    }

    val person = personColor(event.owner)
    return when (event.category) {
        // Arbeit: heller Personen-Hauch mit Rahmen → „gerahmt = Arbeit", tritt farblich zurück.
        EventCategory.WORK -> EventTimelineStyle(
            fill = SolidColor(person.copy(alpha = 0.10f)),
            edge = SolidColor(person),
            content = content,
            outline = person,
        )
        // Privat: satt in Personenfarbe gefüllte Kachel, kein Rahmen.
        else -> EventTimelineStyle(
            fill = SolidColor(personSoftColor(event.owner)),
            edge = SolidColor(person),
            content = content,
        )
    }
}

/**
 * Kleine Kategorie-Kennung an jedem Termin. „Wir-Zeit" greift bewusst den schon
 * vorhandenen Lila-Grün-Verlauf für gemeinsame Termine auf (keine neue Farbsprache).
 * „Arbeit" trägt den weichen Personen-Container-Ton (Lila für Melli, Grün für Basti,
 * über das Theme schon als `primaryContainer`/`secondaryContainer` hinterlegt) — sonst
 * sähen Mellis Arbeitstermine fälschlich grün statt lila aus. „Privat" bleibt bewusst
 * neutral, damit „Arbeit"/„Wir-Zeit" optisch heraussticht.
 */
@Composable
fun CategoryChip(category: EventCategory, owner: Person, modifier: Modifier = Modifier) {
    when (category) {
        EventCategory.TOGETHER -> Box(
            modifier = modifier
                .background(selliGradient(), CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.label(),
                style = MaterialTheme.typography.labelMedium,
                color = onAccentColor(),
            )
        }
        else -> {
            val container = when (category) {
                EventCategory.WORK -> if (owner == Person.MELLI) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val content = when (category) {
                EventCategory.WORK -> if (owner == Person.MELLI) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(modifier = modifier, shape = CircleShape, color = container) {
                Text(
                    text = category.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * Drei-Wege-Auswahl der Kategorie (Arbeit / Privat / Wir-Zeit). Die aktive Option
 * ist gefüllt, „Wir-Zeit" im Lila-Grün-Verlauf; inaktive bleiben schlicht umrandet.
 * Gemeinsam genutzt von Anlegen- und Aktionen-Sheet.
 */
@Composable
fun CategorySelector(
    selected: EventCategory,
    onSelect: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabledCategories: Set<EventCategory> = EventCategory.entries.toSet(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventCategory.entries.forEach { category ->
            CategoryOption(
                category = category,
                selected = category == selected,
                onClick = { onSelect(category) },
                enabled = category in enabledCategories,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryOption(
    category: EventCategory,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val fill = when {
        !selected -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
        category == EventCategory.TOGETHER -> Modifier.background(selliGradient(), shape)
        category == EventCategory.WORK ->
            Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape)
        else -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)
    }
    val textColor = when {
        !selected -> MaterialTheme.colorScheme.onSurfaceVariant
        category == EventCategory.TOGETHER -> onAccentColor()
        category == EventCategory.WORK -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .then(fill)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.label(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}
