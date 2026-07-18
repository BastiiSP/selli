package com.prehmus.selli.ui.calendar

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prehmus.selli.ui.components.CoupleAvatars
import com.prehmus.selli.ui.components.MascotMood
import com.prehmus.selli.ui.components.SelliMascot
import com.prehmus.selli.ui.theme.selliGradient
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Header der Kalenderansicht: Lila-Grün-Verlauf als Branding-Element, Monats-
 * navigation und das reagierende Maskottchen — es freut sich sichtbar, wenn am
 * ausgewählten Tag beide frei sind (Brücke zum späteren Feature "Vorschläge
 * für gemeinsame Slots").
 */
@Composable
fun MascotHeader(
    month: YearMonth,
    isSyncing: Boolean,
    bothFreeOnSelectedDay: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mood = when {
        isSyncing -> MascotMood.BUSY
        bothFreeOnSelectedDay -> MascotMood.HAPPY
        else -> MascotMood.NEUTRAL
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(selliGradient(), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
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
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                CoupleAvatars(size = 34.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Voriger Monat",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${month.year}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        IconButton(onClick = onNextMonth) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Nächster Monat",
                                tint = Color.White,
                            )
                        }
                    }
                    HeaderStatusLine(isSyncing = isSyncing, bothFree = bothFreeOnSelectedDay)
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SelliMascot(mood = mood, modifier = Modifier.size(60.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderStatusLine(isSyncing: Boolean, bothFree: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        isSyncing -> "Selli sammelt eure Termine ein …"
        bothFree -> "Ihr habt beide frei!"
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.95f),
            modifier = modifier.padding(start = 12.dp),
        )
    }
}
