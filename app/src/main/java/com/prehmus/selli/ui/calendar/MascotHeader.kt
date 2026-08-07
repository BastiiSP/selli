package com.prehmus.selli.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.countdown.WirZeitCountdown
import com.prehmus.selli.domain.countdown.WirZeitCountdownState
import com.prehmus.selli.domain.countdown.calculateWirZeitCountdown
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.ui.components.CoupleAvatars
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Header der Kalenderansicht: Lila-Grün-Verlauf als Branding-Element, Zeitraumnavigation
 * und Countdown zur nächsten Wir-Zeit. Ein Tipp auf den Countdown springt zum Termin.
 * Der Header wächst beim Ein- und Ausklappen weich (animateContentSize), statt zu springen.
 */
@Composable
fun MascotHeader(
    title: String,
    isSyncing: Boolean,
    nextWirZeitEvent: CalendarEvent?,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    onManageCustomizations: () -> Unit,
    onSwitchAccount: () -> Unit,
    onWirZeitCountdownClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(selliGradient(), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = if (collapsed) 4.dp else 12.dp),
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
                    onRefresh = onRefresh,
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
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Zurück",
                            tint = onAccentColor(),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = onAccentColor(),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Weiter",
                            tint = onAccentColor(),
                        )
                    }
                }
            }

            WirZeitCountdownSection(
                event = nextWirZeitEvent,
                collapsed = collapsed,
                onClick = onWirZeitCountdownClick,
                modifier = Modifier.padding(top = 8.dp),
            )

            CollapseToggle(
                collapsed = collapsed,
                onToggle = onToggleCollapsed,
                modifier = Modifier.padding(top = if (collapsed) 2.dp else 6.dp),
            )
        }
    }
}

/**
 * Countdown zur nächsten Wir-Zeit. Eingeklappt nur als Text, ausgeklappt als Wanderweg-Szene
 * (siehe [WirZeitCountdownScene], Task 5). Tippen springt in beiden Zuständen zum Termin.
 * Tickt minütlich, solange diese Composable in der Komposition ist — kein Hintergrundlauf.
 */
@Composable
private fun WirZeitCountdownSection(
    event: CalendarEvent?,
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }
    val countdown = remember(event, now) { calculateWirZeitCountdown(event, now) }

    if (collapsed) {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = modifier,
        ) {
            Text(
                text = countdownLabel(countdown),
                style = MaterialTheme.typography.labelLarge,
                color = onAccentColor(),
            )
        }
    } else {
        WirZeitCountdownScene(countdown = countdown, onClick = onClick, modifier = modifier)
    }
}

private fun countdownLabel(countdown: WirZeitCountdown): String =
    when (countdown.state) {
        WirZeitCountdownState.WALKING -> "${countdown.remainingText} bis zur nächsten Wir-Zeit"
        WirZeitCountdownState.ARRIVED_TODAY -> "Heute ist es soweit — eure Wir-Zeit!"
        WirZeitCountdownState.NONE_PLANNED -> "Noch keine Wir-Zeit geplant"
    }

@Composable
private fun WirZeitCountdownScene(
    countdown: WirZeitCountdown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = countdownLabel(countdown),
            style = MaterialTheme.typography.titleMedium,
            color = onAccentColor(),
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/**
 * Griff am unteren Header-Rand: klappt den Header zwischen ausführlicher Countdown-Ansicht
 * und platzsparender Textform um. Der eingestellte Zustand wird gemeinsam mit der
 * Kalender/Liste-Aufteilung dauerhaft gespeichert.
 */
@Composable
private fun CollapseToggle(
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = onAccentColor().copy(alpha = 0.16f),
            onClick = onToggle,
        ) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (collapsed) "Header ausklappen" else "Header einklappen",
                tint = onAccentColor(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 1.dp),
            )
        }
    }
}

/** Überlaufmenü im Header: manuelle Aktualisierung, Verwaltung der lokalen Anpassungen und "Konto wechseln". */
@Composable
private fun HeaderMenu(
    onRefresh: () -> Unit,
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
                text = { Text("Jetzt aktualisieren") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
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
