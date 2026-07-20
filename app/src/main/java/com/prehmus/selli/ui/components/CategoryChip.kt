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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient

/** Deutscher Anzeigename der Kategorie. */
fun EventCategory.label(): String = when (this) {
    EventCategory.WORK -> "Arbeit"
    EventCategory.PRIVATE -> "Privat"
    EventCategory.TOGETHER -> "Wir-Zeit"
}

/**
 * Getönte Darstellung einer Kategorie im Zeitstrahl — dieselbe Farbsprache wie die
 * Kategorie-Chips ([CategoryChip]), nur als transparent getönte Fläche statt voller
 * Pill. [fill] füllt den Terminblock (weich, durchscheinend), [edge] ist der
 * kräftigere linke Akzentstreifen (Verlauf bei Wir-Zeit) und [content] die
 * Text-/Detailfarbe darauf. Keine zweite Farblogik – nur die vorhandene, getönt.
 */
data class CategoryTimelineStyle(
    val fill: Brush,
    val edge: Brush,
    val content: Color,
)

@Composable
@ReadOnlyComposable
fun categoryTimelineStyle(category: EventCategory): CategoryTimelineStyle = when (category) {
    EventCategory.TOGETHER -> {
        // Wir-Zeit: der Lila-Grün-Verlauf, als sanft getönte Fläche.
        val melli = personColor(Person.MELLI)
        val basti = personColor(Person.BASTI)
        CategoryTimelineStyle(
            fill = Brush.linearGradient(listOf(melli.copy(alpha = 0.22f), basti.copy(alpha = 0.22f))),
            edge = selliGradient(),
            content = MaterialTheme.colorScheme.onSurface,
        )
    }
    EventCategory.WORK -> CategoryTimelineStyle(
        fill = SolidColor(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)),
        edge = SolidColor(MaterialTheme.colorScheme.secondary),
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    EventCategory.PRIVATE -> CategoryTimelineStyle(
        fill = SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        edge = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Kleine Kategorie-Kennung an jedem Termin. „Wir-Zeit" greift bewusst den schon
 * vorhandenen Lila-Grün-Verlauf für gemeinsame Termine auf (keine neue
 * Farbsprache), Arbeit/Privat bleiben ruhige Container-Töne, damit die Wir-Zeit
 * optisch heraussticht.
 */
@Composable
fun CategoryChip(category: EventCategory, modifier: Modifier = Modifier) {
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
                EventCategory.WORK -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val content = when (category) {
                EventCategory.WORK -> MaterialTheme.colorScheme.onSecondaryContainer
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
            .clip(shape)
            .clickable(onClick = onClick)
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
