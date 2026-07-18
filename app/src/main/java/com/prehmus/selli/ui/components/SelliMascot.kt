package com.prehmus.selli.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Platzhalter für das Selli-Maskottchen (rundliches, dunkles Wesen mit großen
 * Kulleraugen, an Rußmännchen-Ästhetik angelehnt). Wird später durch die
 * illustrierte Version aus der Bildgenerierung ersetzt — die Einsatzorte und
 * Stimmungen ([MascotMood]) bleiben dabei gleich.
 */
enum class MascotMood {
    /** Ruhiger Normalzustand (Header, Leerzustand). */
    NEUTRAL,

    /** Beide sind frei — das Maskottchen freut sich sichtbar und hüpft. */
    HAPPY,

    /** Sync läuft — das Maskottchen "sammelt" Termine ein. */
    BUSY,
}

@Composable
fun SelliMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mascot")

    // Fröhliches Hüpfen, wenn beide frei sind; sonst ruhiges Auf und Ab.
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mood == MascotMood.HAPPY) 450 else 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    // BUSY: Die Pupillen wandern hin und her, als würden Termine eingesammelt.
    val gaze by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                -1f at 0 using LinearEasing
                -1f at 500
                1f at 900
                1f at 1400
                -1f at 1800
            },
        ),
        label = "gaze",
    )

    Canvas(modifier = modifier.size(64.dp)) {
        val bounceAmount = if (mood == MascotMood.HAPPY) size.height * 0.10f else size.height * 0.03f
        translate(top = -bounce * bounceAmount) {
            drawMascot(mood, gaze)
        }
    }
}

private fun DrawScope.drawMascot(mood: MascotMood, gaze: Float) {
    val body = Color(0xFF322B33)
    val radius = size.minDimension * 0.42f
    val center = Offset(size.width / 2f, size.height / 2f + size.minDimension * 0.06f)

    // Tapsige Füßchen
    val footRadius = radius * 0.22f
    drawCircle(body, footRadius, center + Offset(-radius * 0.5f, radius * 0.92f))
    drawCircle(body, footRadius, center + Offset(radius * 0.5f, radius * 0.92f))

    // Rundlicher Körper
    drawCircle(color = body, radius = radius, center = center)

    val eyeOffsetX = radius * 0.42f
    val eyeOffsetY = radius * 0.15f
    val eyeRadius = radius * 0.30f
    val leftEye = center + Offset(-eyeOffsetX, -eyeOffsetY)
    val rightEye = center + Offset(eyeOffsetX, -eyeOffsetY)

    if (mood == MascotMood.HAPPY) {
        // Zugekniffene, lachende Augen als Bögen + rote Bäckchen
        val stroke = Stroke(width = radius * 0.14f, cap = StrokeCap.Round)
        listOf(leftEye, rightEye).forEach { eye ->
            drawArc(
                color = Color.White,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = eye - Offset(eyeRadius, eyeRadius * 0.7f),
                size = Size(eyeRadius * 2f, eyeRadius * 1.6f),
                style = stroke,
            )
        }
        val blush = Color(0xFFE8A0A8).copy(alpha = 0.75f)
        drawCircle(blush, eyeRadius * 0.5f, leftEye + Offset(-eyeRadius * 0.4f, eyeRadius * 1.6f))
        drawCircle(blush, eyeRadius * 0.5f, rightEye + Offset(eyeRadius * 0.4f, eyeRadius * 1.6f))
    } else {
        // Große Kulleraugen; im BUSY-Zustand wandern die Pupillen umher.
        val pupilShift = if (mood == MascotMood.BUSY) gaze * eyeRadius * 0.35f else 0f
        listOf(leftEye, rightEye).forEach { eye ->
            drawCircle(Color.White, eyeRadius, eye)
            drawCircle(body, eyeRadius * 0.45f, eye + Offset(pupilShift, eyeRadius * 0.1f))
            drawCircle(Color.White, eyeRadius * 0.14f, eye + Offset(pupilShift - eyeRadius * 0.12f, -eyeRadius * 0.08f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelliMascotPreview() {
    SelliMascot(mood = MascotMood.NEUTRAL)
}
