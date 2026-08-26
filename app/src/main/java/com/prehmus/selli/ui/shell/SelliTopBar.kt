package com.prehmus.selli.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.components.PersonAvatar
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient

/**
 * Schmaler, auf jedem Bildschirm sichtbarer Header: Lila-Grün-Verlauf als Branding,
 * Wortmarke links, eigener Avatar rechts als Zugang zum Profil-/Einstellungsbereich.
 *
 * Bewusst flach — der frühere Kalender-Header trug Zeitraumnavigation und
 * Wir-Zeit-Countdown mit, die jetzt im Kalender-Tab bzw. auf dem Homescreen sitzen.
 */
@Composable
fun SelliTopBar(
    ownPerson: Person,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = selliGradient(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Selli",
            style = MaterialTheme.typography.titleLarge,
            color = onAccentColor(),
            modifier = Modifier.weight(1f),
        )
        PersonAvatar(
            person = ownPerson,
            size = 36.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings)
                .semantics { contentDescription = "Profil und Einstellungen" },
        )
    }
}
