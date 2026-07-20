package com.prehmus.selli.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Deutscher Name der Ansicht für den Umschalter. */
private fun CalendarViewMode.label(): String = when (this) {
    CalendarViewMode.MONTH -> "Monat"
    CalendarViewMode.WEEK -> "Woche"
    CalendarViewMode.DAY -> "Tag"
}

/**
 * Unauffälliger, segmentierter Umschalter zwischen Monat / Woche / Tag – sitzt
 * direkt unter dem Header, oberhalb des austauschbaren Kalenderbausteins. Das
 * aktive Segment hebt sich als weiche Karte ab, der Rest bleibt ruhig.
 */
@Composable
fun ViewModeSwitcher(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape)
            .padding(3.dp),
    ) {
        CalendarViewMode.entries.forEach { mode ->
            SegmentButton(
                label = mode.label(),
                selected = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "segmentBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "segmentContent",
    )
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = shape,
        color = background,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = content,
            )
        }
    }
}
