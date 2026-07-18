package com.prehmus.selli.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Platzhalter: Standard-Systemschrift. Wird beim Umsetzen des Designkonzepts auf die
// rundliche, freundliche Schrift (Kandidaten: Nunito/Quicksand) umgestellt (Owner: Claude).
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
