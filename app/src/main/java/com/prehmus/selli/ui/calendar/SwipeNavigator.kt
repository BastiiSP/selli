package com.prehmus.selli.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import kotlin.math.roundToInt

/**
 * Umhüllt den austauschbaren Kalenderbereich (Grid/Timeline + Detailliste) und
 * verpasst dem Ansichtswechsel eine kleine, verspielte Geste:
 *
 *  1. **Wischen:** Horizontale Drag-Gesten blättern vor/zurück ([onNext]/[onPrevious]).
 *     Vertikales Scrollen der inneren `LazyColumn` bleibt unberührt, da
 *     `detectHorizontalDragGestures` nur horizontale Bewegungen für sich beansprucht.
 *  2. **Übergang:** Wechselt der [contentKey] (durch Wisch *oder* die externen
 *     Pfeil-Buttons), gleitet der alte Inhalt zur einen Seite raus, während der
 *     neue von der anderen Seite reingleitet – Richtung folgt [direction].
 *  3. **Schiebendes Maskottchen:** Kurz sichtbar huscht das Maskottchen in
 *     Bewegungsrichtung durchs Bild und wirkt so, als würde es die neue Seite
 *     an ihren Platz *schieben*. Es erscheint, schiebt und verschwindet wieder –
 *     schnell und leichtfüßig, kein aufdringlicher Ladescreen.
 *
 * Bewusst ohne State-Snapshot: `content()` liest den Live-App-State. Dass während
 * des ~300 ms langen Kreuz-Slides kurz auf beiden Ebenen dieselben Daten liegen
 * können, ist gewollt und wird vom Fade kaschiert.
 *
 * @param contentKey Schlüssel des aktuell gezeigten Zeitraums – ändert er sich, läuft der Übergang.
 * @param direction  +1 = vor, -1 = zurück, 0 = neutral (z. B. Ansichtsmodus-Wechsel → nur Fade).
 */
@Composable
fun SwipeNavigator(
    contentKey: Any,
    direction: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Schwellwert, ab dem eine Wischgeste als Blättern zählt.
    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier.pointerInput(contentKey) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onDragEnd = {
                    when {
                        totalDrag <= -swipeThresholdPx -> onNext()
                        totalDrag >= swipeThresholdPx -> onPrevious()
                    }
                },
            ) { change, dragAmount ->
                totalDrag += dragAmount
                change.consume()
            }
        },
    ) {
        // Pixelmaße des Bereichs für die Maskottchen-Reise.
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val mascotSizePx = with(LocalDensity.current) { 64.dp.toPx() }

        AnimatedContent(
            targetState = contentKey,
            transitionSpec = {
                val anim = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
                val slideAnim = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
                when {
                    direction > 0 -> // vor: neu kommt von rechts, alt geht nach links → Seite wandert LINKS
                        (slideInHorizontally(slideAnim) { it } + fadeIn(anim)) togetherWith
                            (slideOutHorizontally(slideAnim) { -it } + fadeOut(anim))

                    direction < 0 -> // zurück: gespiegelt → Seite wandert RECHTS
                        (slideInHorizontally(slideAnim) { -it } + fadeIn(anim)) togetherWith
                            (slideOutHorizontally(slideAnim) { it } + fadeOut(anim))

                    else -> // neutral: nur sanftes Faden, kein Slide
                        fadeIn(anim) togetherWith fadeOut(anim)
                }.using(SizeTransform(clip = false))
            },
            label = "calendarContentTransition",
        ) { _ ->
            // Bewusst der Live-State über content(), nicht der übergebene Snapshot-Key.
            content()
        }

        // --- Schiebendes Maskottchen (Overlay ÜBER dem Inhalt) ---
        val progress = remember { Animatable(0f) }
        var hasComposedOnce by remember { mutableStateOf(false) }

        LaunchedEffect(contentKey) {
            if (!hasComposedOnce) {
                // Erste Komposition: kein Übergang, Maskottchen bleibt still.
                hasComposedOnce = true
                return@LaunchedEffect
            }
            if (direction == 0) return@LaunchedEffect
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
        }

        val p = progress.value
        // Nur während einer laufenden Animation zeichnen; im Ruhezustand nichts.
        if (p > 0f && p < 1f) {
            // Horizontale Reise folgt der Wanderrichtung der Seite.
            val startX: Float
            val endX: Float
            val mirror: Boolean
            if (direction > 0) {
                // Seite wandert nach links → Maskottchen läuft von rechts nach links, schaut nach links (gespiegelt).
                startX = 0.72f * widthPx
                endX = 0.12f * widthPx
                mirror = true
            } else {
                // Seite wandert nach rechts → Maskottchen läuft von links nach rechts, schaut nach rechts (Original).
                startX = 0.12f * widthPx - mascotSizePx
                endX = 0.72f * widthPx
                mirror = false
            }
            val x = startX + (endX - startX) * p
            // Vertikal im unteren Mittelband (~58 % nach unten).
            val y = 0.58f * heightPx

            // Alpha-Hüllkurve: einblenden (0→0.2), halten, ausblenden (0.7→1.0).
            val alpha = when {
                p < 0.2f -> p / 0.2f
                p > 0.7f -> (1f - p) / 0.3f
                else -> 1f
            }.coerceIn(0f, 1f)

            Image(
                painter = painterResource(R.drawable.mascot_pushing),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(64.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = if (mirror) -1f else 1f
                    },
            )
        }
    }
}
