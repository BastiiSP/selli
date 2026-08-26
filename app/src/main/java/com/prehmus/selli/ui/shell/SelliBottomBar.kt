package com.prehmus.selli.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.theme.personColor

/**
 * Schmale Bottom-Navigation mit den drei gleichwertigen Zielen. Der Indikator trägt die
 * Personenfarbe der angemeldeten Person — kleine Personalisierung, ohne ein neues
 * Farbsystem zu erfinden.
 */
@Composable
fun SelliBottomBar(
    current: SelliDestination,
    ownPerson: Person,
    onSelect: (SelliDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = personColor(ownPerson)
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        SelliDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(text = destination.label, style = MaterialTheme.typography.labelMedium)
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.14f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

// Bewusst nur Icons aus material-icons-core — die App bindet material-icons-extended
// nicht ein, "CalendarMonth" & Co. wären hier nicht auflösbar.
private fun SelliDestination.icon(): ImageVector = when (this) {
    SelliDestination.CALENDAR -> Icons.Default.DateRange
    SelliDestination.HOME -> Icons.Default.Favorite
    SelliDestination.LOCATION -> Icons.Default.Place
}
