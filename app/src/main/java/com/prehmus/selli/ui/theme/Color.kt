package com.prehmus.selli.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.prehmus.selli.domain.model.Person

// Personenfarben — siehe Designkonzept (CLAUDE.md). Gegenüber den ersten Platzhaltern
// leicht abgedunkelt, damit weißer Text auf den Pills die 4,5:1-Kontrastgrenze erreicht;
// der Grundcharakter (Lila für Melli, Grün für Basti) bleibt erhalten.
val MelliPurple = Color(0xFF7E58B4)
val BastiGreen = Color(0xFF3E8A5C)

val MelliPurpleDark = Color(0xFFB49BD9)
val BastiGreenDark = Color(0xFF7FC79A)

// Sanfte Container-Töne für Flächen, auf denen die Personenfarbe nur anklingt
// (z. B. Termin-Karten im Tagesdetail).
val MelliPurpleSoft = Color(0xFFEFE7F8)
val BastiGreenSoft = Color(0xFFE2F1E7)
val MelliPurpleSoftDark = Color(0xFF372C49)
val BastiGreenSoftDark = Color(0xFF223B2C)

// Warme Neutraltöne: weiche, leicht getönte Flächen statt hartem Weiß/Grau
// ("warm eingekleidet" laut Designkonzept). Dark Mode entsättigt & gedimmt.
val WarmBackground = Color(0xFFFAF5EF)
val WarmSurface = Color(0xFFFFFDFA)
val WarmSurfaceVariant = Color(0xFFF1EAE0)
val WarmOutline = Color(0xFFDCD3C6)
val InkText = Color(0xFF3B3439)
val InkTextSoft = Color(0xFF776E74)

val NightBackground = Color(0xFF181419)
val NightSurface = Color(0xFF221D23)
val NightSurfaceVariant = Color(0xFF2F292F)
val NightOutline = Color(0xFF4C444C)
val MilkText = Color(0xFFF1EBE7)
val MilkTextSoft = Color(0xFFB5ABB1)

/** Farbe der Person, passend zum aktuellen Hell-/Dunkel-Theme. */
@Composable
@ReadOnlyComposable
fun personColor(person: Person): Color = personColor(person, isSystemInDarkTheme())

fun personColor(person: Person, darkTheme: Boolean): Color = when (person) {
    Person.MELLI -> if (darkTheme) MelliPurpleDark else MelliPurple
    Person.BASTI -> if (darkTheme) BastiGreenDark else BastiGreen
}

/** Sanfter Container-Ton der Person (für Kartenhintergründe). */
@Composable
@ReadOnlyComposable
fun personSoftColor(person: Person): Color {
    val dark = isSystemInDarkTheme()
    return when (person) {
        Person.MELLI -> if (dark) MelliPurpleSoftDark else MelliPurpleSoft
        Person.BASTI -> if (dark) BastiGreenSoftDark else BastiGreenSoft
    }
}

/**
 * Text-/Icon-Farbe auf den kräftigen Akzentflächen (Personenfarben, selliGradient):
 * Weiß im Light Mode; im Dark Mode sind die Personenfarben pastellig-hell,
 * dort braucht es dunkle Tinte für die 4,5:1-Kontrastgrenze.
 */
@Composable
@ReadOnlyComposable
fun onAccentColor(): Color = onAccentColor(isSystemInDarkTheme())

fun onAccentColor(darkTheme: Boolean): Color = if (darkTheme) NightBackground else Color.White

/**
 * Der Lila-Grün-Verlauf: App-weites Branding-Element (Header, Splash) und
 * zugleich die dritte Kennung für gemeinsam erstellte Termine.
 */
@Composable
@ReadOnlyComposable
fun selliGradient(): Brush = selliGradient(isSystemInDarkTheme())

fun selliGradient(darkTheme: Boolean): Brush = Brush.linearGradient(
    colors = listOf(
        personColor(Person.MELLI, darkTheme),
        personColor(Person.BASTI, darkTheme),
    )
)
