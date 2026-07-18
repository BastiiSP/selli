package com.prehmus.selli.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Folgt automatisch dem System-Theme (kein manueller Umschalter) — siehe Designkonzept.
// Dynamic Color (Material You) bleibt bewusst aus: Die Personenfarben Lila/Grün sind
// Teil der App-Identität und sollen nicht vom System-Wallpaper überschrieben werden.
private val LightColors = lightColorScheme(
    primary = MelliPurple,
    onPrimary = WarmSurface,
    primaryContainer = MelliPurpleSoft,
    onPrimaryContainer = InkText,
    secondary = BastiGreen,
    onSecondary = WarmSurface,
    secondaryContainer = BastiGreenSoft,
    onSecondaryContainer = InkText,
    background = WarmBackground,
    onBackground = InkText,
    surface = WarmSurface,
    onSurface = InkText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkTextSoft,
    outline = WarmOutline,
    outlineVariant = WarmSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = MelliPurpleDark,
    onPrimary = NightBackground,
    primaryContainer = MelliPurpleSoftDark,
    onPrimaryContainer = MilkText,
    secondary = BastiGreenDark,
    onSecondary = NightBackground,
    secondaryContainer = BastiGreenSoftDark,
    onSecondaryContainer = MilkText,
    background = NightBackground,
    onBackground = MilkText,
    surface = NightSurface,
    onSurface = MilkText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = MilkTextSoft,
    outline = NightOutline,
    outlineVariant = NightSurfaceVariant,
)

@Composable
fun SelliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = SelliShapes,
        content = content
    )
}
