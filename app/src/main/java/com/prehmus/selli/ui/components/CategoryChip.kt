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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient

/** Deutscher Anzeigename der Kategorie. */
fun EventCategory.label(): String = when (this) {
    EventCategory.WORK -> "Arbeit"
    EventCategory.PRIVATE -> "Privat"
    EventCategory.TOGETHER -> "Wir-Zeit"
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
