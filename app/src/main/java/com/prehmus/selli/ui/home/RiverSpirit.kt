package com.prehmus.selli.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import kotlin.math.roundToInt

/**
 * Der Flussgeist als Begleitfigur des Homescreens: driftet langsam und schwebend, ohne je
 * anzukommen. Die beiden Achsen laufen mit teilerfremden Dauern (7 s / 11 s), damit die
 * Bewegung nicht sichtbar taktet.
 *
 * Die Animationswerte werden im Layout-Block (`Modifier.offset { }`) gelesen statt in der
 * Komposition — so kostet jeder Frame nur ein Re-Layout und keine Recomposition.
 */
@Composable
fun RiverSpirit(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val transition = rememberInfiniteTransition(label = "riverSpirit")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "verticalDrift",
    )
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "horizontalSway",
    )
    val density = LocalDensity.current
    val verticalRangePx = with(density) { 10.dp.toPx() }
    val horizontalRangePx = with(density) { 14.dp.toPx() }

    Image(
        painter = painterResource(R.drawable.spirit_river),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .offset {
                IntOffset(
                    x = (sway * horizontalRangePx).roundToInt(),
                    y = (drift * verticalRangePx).roundToInt(),
                )
            },
    )
}
