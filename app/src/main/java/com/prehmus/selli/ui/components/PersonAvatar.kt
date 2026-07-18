package com.prehmus.selli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.selliGradient

/**
 * Platzhalter-Avatar: Kreis in der Personenfarbe mit Initial. Wird später durch
 * die illustrierten Avatare (gleiche Bildsprache wie das Maskottchen) ersetzt.
 */
@Composable
fun PersonAvatar(
    person: Person,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(personColor(person), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (person == Person.MELLI) "M" else "B",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/** Beide Avatare leicht überlappend — "unsere kleine gemeinsame Welt". */
@Composable
fun CoupleAvatars(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        PersonAvatar(person = Person.MELLI, size = size)
        PersonAvatar(person = Person.BASTI, size = size)
    }
}

/** Kleine Pill mit Personenfarbe und Name — Kennzeichnung, wem ein Termin gehört. */
@Composable
fun PersonPill(
    person: Person,
    modifier: Modifier = Modifier,
    isSharedEvent: Boolean = false,
) {
    val label = when {
        isSharedEvent -> "Gemeinsam"
        person == Person.MELLI -> "Melli"
        else -> "Basti"
    }
    val background = if (isSharedEvent) {
        Modifier.background(selliGradient(), CircleShape)
    } else {
        Modifier.background(personColor(person), CircleShape)
    }
    Box(modifier = modifier.then(background), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
