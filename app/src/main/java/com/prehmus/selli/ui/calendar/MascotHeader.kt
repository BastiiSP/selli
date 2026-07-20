package com.prehmus.selli.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.ui.components.CoupleAvatars
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.Duration
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HeaderTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Header der Kalenderansicht: Lila-Grün-Verlauf als Branding-Element, Monats-
 * navigation und das reagierende Maskottchen — es freut sich sichtbar, wenn am
 * ausgewählten Tag gemeinsame freie Blöcke da sind. Diese Blöcke werden mit
 * Uhrzeit und Dauer angezeigt; ein Tipp legt daraus direkt einen „Wir-Zeit"-
 * Termin an. Der Header wächst dafür weich (animateContentSize), statt zu springen.
 */
@Composable
fun MascotHeader(
    month: YearMonth,
    isSyncing: Boolean,
    freeBlocks: List<FreeTimeBlock>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onManageCustomizations: () -> Unit,
    onSwitchAccount: () -> Unit,
    onFreeBlockClick: (FreeTimeBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mood = when {
        isSyncing -> MascotMood.BUSY
        freeBlocks.isNotEmpty() -> MascotMood.HAPPY
        else -> MascotMood.NEUTRAL
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(selliGradient(), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Selli",
                    style = MaterialTheme.typography.headlineSmall,
                    color = onAccentColor(),
                    modifier = Modifier.weight(1f),
                )
                CoupleAvatars(size = 34.dp)
                HeaderMenu(
                    onManageCustomizations = onManageCustomizations,
                    onSwitchAccount = onSwitchAccount,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Voriger Monat",
                            tint = onAccentColor(),
                        )
                    }
                    Text(
                        text = "${month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${month.year}",
                        style = MaterialTheme.typography.titleLarge,
                        color = onAccentColor(),
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Nächster Monat",
                            tint = onAccentColor(),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(onAccentColor().copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SelliMascot(mood = mood, modifier = Modifier.size(60.dp))
                }
            }

            FreeTimeSection(
                isSyncing = isSyncing,
                freeBlocks = freeBlocks,
                onFreeBlockClick = onFreeBlockClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Zeigt beim Sync einen Hinweis, sonst alle gemeinsamen freien Blöcke des Tages
 * als antippbare Karten. Ohne Blöcke bleibt der Bereich leer (Header schrumpft weich).
 */
@Composable
private fun FreeTimeSection(
    isSyncing: Boolean,
    freeBlocks: List<FreeTimeBlock>,
    onFreeBlockClick: (FreeTimeBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isSyncing -> Text(
            text = "Selli sammelt eure Termine ein …",
            style = MaterialTheme.typography.labelLarge,
            color = onAccentColor().copy(alpha = 0.95f),
            modifier = modifier,
        )
        freeBlocks.isNotEmpty() -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (freeBlocks.size == 1) "Gemeinsam frei — tipp den Block für eure Zeit" else "Gemeinsam frei — tipp einen Block für eure Zeit",
                style = MaterialTheme.typography.labelLarge,
                color = onAccentColor(),
            )
            freeBlocks.forEach { block ->
                FreeBlockCard(block = block, onClick = { onFreeBlockClick(block) })
            }
        }
    }
}

/** Antippbare Karte für einen freien Block: Uhrzeitspanne, Dauer und „+" als Einladung. */
@Composable
private fun FreeBlockCard(
    block: FreeTimeBlock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = onAccentColor().copy(alpha = 0.16f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(onAccentColor().copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = onAccentColor(),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${block.start.toLocalTime().format(HeaderTimeFormat)} – " +
                        block.end.toLocalTime().format(HeaderTimeFormat),
                    style = MaterialTheme.typography.titleSmall,
                    color = onAccentColor(),
                )
                Text(
                    text = "${formatDuration(block.duration)} frei",
                    style = MaterialTheme.typography.labelMedium,
                    color = onAccentColor().copy(alpha = 0.85f),
                )
            }
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return when {
        hours == 0L -> "$minutes Min"
        minutes == 0L -> "$hours Std"
        else -> "$hours Std $minutes Min"
    }
}

/** Überlaufmenü im Header: Verwaltung der lokalen Anpassungen und "Konto wechseln". */
@Composable
private fun HeaderMenu(
    onManageCustomizations: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menü",
                tint = onAccentColor(),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Ausgeblendet & angepasst") },
                onClick = {
                    expanded = false
                    onManageCustomizations()
                },
            )
            DropdownMenuItem(
                text = { Text("Konto wechseln …") },
                onClick = {
                    expanded = false
                    onSwitchAccount()
                },
            )
        }
    }
}
