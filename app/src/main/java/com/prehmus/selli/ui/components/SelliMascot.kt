package com.prehmus.selli.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R

/**
 * Das Selli-Maskottchen — die illustrierten Posen (an Rußmännchen-Ästhetik
 * angelehnt) stammen aus der einmaligen Bildgenerierung und liegen als
 * statische WebP-Assets unter res/drawable-nodpi. Die Stimmung wählt die Pose;
 * mascot_traveling bleibt für das Distanz-Feature (v3) reserviert.
 */
enum class MascotMood {
    /** Ruhiger Normalzustand (Header). */
    NEUTRAL,

    /** Beide sind frei — das Maskottchen freut sich sichtbar und hüpft. */
    HAPPY,

    /** Sync läuft — das Maskottchen "sammelt" Termine ein. */
    BUSY,

    /** Leerzustand: keine Termine am betrachteten Tag. */
    EMPTY,
}

@Composable
fun SelliMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mascot")

    // Fröhliches Hüpfen, wenn beide frei sind; sonst ruhiges Atmen.
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mood == MascotMood.HAPPY) 450 else 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    val bounceHeight = if (mood == MascotMood.HAPPY) 6.dp else 1.5.dp

    val painter = painterResource(
        when (mood) {
            MascotMood.NEUTRAL -> R.drawable.mascot_idle
            MascotMood.HAPPY -> R.drawable.mascot_celebrating
            MascotMood.BUSY -> R.drawable.mascot_loading
            MascotMood.EMPTY -> R.drawable.mascot_empty_state
        }
    )

    Image(
        painter = painter,
        contentDescription = when (mood) {
            MascotMood.NEUTRAL -> "Selli-Maskottchen"
            MascotMood.HAPPY -> "Selli-Maskottchen freut sich — ihr habt beide frei"
            MascotMood.BUSY -> "Selli-Maskottchen sammelt Termine ein"
            MascotMood.EMPTY -> "Selli-Maskottchen — keine Termine"
        },
        modifier = modifier
            .size(64.dp)
            .offset(y = -bounceHeight * bounce),
    )
}

@Preview(showBackground = true)
@Composable
private fun SelliMascotPreview() {
    SelliMascot(mood = MascotMood.NEUTRAL)
}
