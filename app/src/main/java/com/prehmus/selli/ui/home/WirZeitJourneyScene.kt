package com.prehmus.selli.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.countdown.WirZeitCountdown
import com.prehmus.selli.domain.countdown.WirZeitCountdownState
import com.prehmus.selli.ui.theme.onAccentColor
import com.prehmus.selli.ui.theme.selliGradient
import kotlin.math.roundToInt
/**
 * Wir-Zeit-Countdown als Wanderweg-Szene. Unverändert aus dem früheren Kalender-Header
 * übernommen (dort ab 05.08.2026 in Betrieb) und nur ins Homescreen-Paket verschoben.
 *
 * Wichtig: Die Szene ist durchgehend auf [onAccentColor] gebaut und gehört damit auf eine
 * Fläche mit [selliGradient] oder Personenfarbe — auf warmem Hintergrund wäre sie unlesbar.
 */
internal const val SYNC_LABEL = "Selli sammelt eure Termine ein …"

@Composable
internal fun WirZeitCountdownScene(
    countdown: WirZeitCountdown,
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Im früheren Header waren 84 dp das Maximum; auf dem Homescreen ist Platz für mehr.
    sceneHeight: Dp = 84.dp,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val expandedPrimaryText = when {
                isSyncing -> SYNC_LABEL
                countdown.state == WirZeitCountdownState.WALKING -> countdown.remainingText
                countdown.state == WirZeitCountdownState.ARRIVED_TODAY -> "Heute ist eure Wir-Zeit!"
                else -> "Noch keine Wir-Zeit geplant"
            }
            val expandedSecondaryText = if (
                !isSyncing && countdown.state == WirZeitCountdownState.WALKING
            ) {
                "bis zur nächsten Wir-Zeit"
            } else {
                null
            }

            Text(
                text = expandedPrimaryText,
                style = MaterialTheme.typography.titleMedium,
                color = onAccentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expandedSecondaryText != null) {
                Text(
                    text = expandedSecondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = onAccentColor().copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            AnimatedContent(
                targetState = countdown,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(durationMillis = 240),
                    )
                },
                contentKey = { it.state },
                label = "wirZeitSceneState",
            ) { sceneCountdown ->
                WirZeitJourneyScene(
                    countdown = sceneCountdown,
                    isSyncing = isSyncing,
                    sceneHeight = sceneHeight,
                )
            }
        }
    }
}

@Composable
private fun WirZeitJourneyScene(
    countdown: WirZeitCountdown,
    isSyncing: Boolean,
    sceneHeight: Dp,
) {
    val mascotSize = 50.dp
    val goalSize = 34.dp
    val density = LocalDensity.current
    val accentColor = onAccentColor()
    val sceneMotion = rememberInfiniteTransition(label = "wirZeitSceneMotion")
    val cloudPhaseNear = sceneMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
        ),
        label = "nearCloudDrift",
    )
    val cloudPhaseFar = sceneMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing),
        ),
        label = "farCloudDrift",
    )
    val cloudPhaseHigh = sceneMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 41_000, easing = LinearEasing),
        ),
        label = "highCloudDrift",
    )
    val mascotBob = sceneMotion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "journeyMascotBob",
    )
    val heartPulse = sceneMotion.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrivalHeartPulse",
    )

    val targetProgress = when (countdown.state) {
        WirZeitCountdownState.ARRIVED_TODAY -> 1f
        WirZeitCountdownState.WALKING -> countdown.progress.coerceIn(0f, 1f)
        WirZeitCountdownState.NONE_PLANNED -> 0f
    }
    var progressTarget by remember(countdown.state) { mutableFloatStateOf(0f) }
    LaunchedEffect(targetProgress) {
        progressTarget = targetProgress
    }
    val animatedProgress = animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
        label = "journeyProgress",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(sceneHeight),
    ) {
        val sceneWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(0f)
        val sceneHeightPx = with(density) { sceneHeight.toPx() }
        val routeInsetPx = with(density) { 26.dp.toPx() }
        val mascotSizePx = with(density) { mascotSize.toPx() }
        val goalSizePx = with(density) { goalSize.toPx() }
        val mascotBobAmplitudePx = with(density) { 1.dp.toPx() }
        val bottomInsetPx = with(density) { 3.dp.toPx() }
        val journeyPath = remember(sceneWidthPx, sceneHeightPx, routeInsetPx) {
            createJourneyPath(
                width = sceneWidthPx,
                height = sceneHeightPx,
                horizontalInset = routeInsetPx,
            )
        }
        val pathMeasure = remember(journeyPath) {
            PathMeasure().apply { setPath(journeyPath, forceClosed = false) }
        }
        val traveledPath = remember(journeyPath) { Path() }
        val remainingPath = remember(journeyPath) { Path() }
        val showsJourney = countdown.state != WirZeitCountdownState.NONE_PLANNED

        Canvas(modifier = Modifier.matchParentSize()) {
            val nearCloudWidth = 46.dp.toPx()
            val nearCloudHeight = 26.dp.toPx()
            drawSoftCloud(
                color = accentColor.copy(alpha = 0.24f),
                x = loopingCloudX(
                    phase = cloudPhaseNear.value,
                    startFraction = 0.08f,
                    sceneWidth = size.width,
                    cloudWidth = nearCloudWidth,
                ),
                top = 0.dp.toPx(),
                width = nearCloudWidth,
                height = nearCloudHeight,
            )

            val farCloudWidth = 34.dp.toPx()
            val farCloudHeight = 19.dp.toPx()
            drawSoftCloud(
                color = accentColor.copy(alpha = 0.20f),
                x = loopingCloudX(
                    phase = cloudPhaseFar.value,
                    startFraction = 0.48f,
                    sceneWidth = size.width,
                    cloudWidth = farCloudWidth,
                ),
                top = 7.dp.toPx(),
                width = farCloudWidth,
                height = farCloudHeight,
            )

            val highCloudWidth = 54.dp.toPx()
            val highCloudHeight = 30.dp.toPx()
            drawSoftCloud(
                color = accentColor.copy(alpha = 0.27f),
                x = loopingCloudX(
                    phase = cloudPhaseHigh.value,
                    startFraction = 0.60f,
                    sceneWidth = size.width,
                    cloudWidth = highCloudWidth,
                ),
                top = (-2).dp.toPx(),
                width = highCloudWidth,
                height = highCloudHeight,
            )

            if (!showsJourney) {
                val shadowWidth = 42.dp.toPx()
                val shadowHeight = 5.dp.toPx()
                drawOval(
                    color = accentColor.copy(alpha = 0.12f),
                    topLeft = Offset(
                        x = size.width / 2f - shadowWidth / 2f,
                        y = size.height - 8.dp.toPx(),
                    ),
                    size = Size(shadowWidth, shadowHeight),
                )
            }

            if (showsJourney && pathMeasure.length > 0f) {
                val traveledDistance = animatedProgress.value.coerceIn(0f, 1f) * pathMeasure.length
                traveledPath.reset()
                remainingPath.reset()
                pathMeasure.getSegment(
                    startDistance = traveledDistance,
                    stopDistance = pathMeasure.length,
                    destination = remainingPath,
                    startWithMoveTo = true,
                )
                pathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = traveledDistance,
                    destination = traveledPath,
                    startWithMoveTo = true,
                )

                drawPath(
                    path = remainingPath,
                    color = accentColor.copy(alpha = 0.27f),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(5.dp.toPx(), 10.dp.toPx()),
                        ),
                    ),
                )
                drawPath(
                    path = traveledPath,
                    color = accentColor.copy(alpha = 0.68f),
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }

        if (showsJourney) {
            Box(
                modifier = Modifier
                    .offset {
                        val endpoint = pathPosition(
                            pathMeasure = pathMeasure,
                            progress = 1f,
                            fallback = Offset(
                                sceneWidthPx - routeInsetPx,
                                sceneHeightPx * 0.28f,
                            ),
                        )
                        IntOffset(
                            x = (endpoint.x - goalSizePx / 2f).roundToInt(),
                            y = (endpoint.y - goalSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(goalSize)
                    .graphicsLayer {
                        val scale = if (countdown.state == WirZeitCountdownState.ARRIVED_TODAY) {
                            heartPulse.value
                        } else {
                            1f
                        }
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Ziel der nächsten Wir-Zeit",
                    tint = accentColor.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val mascotDrawable = when {
            isSyncing -> R.drawable.mascot_loading
            countdown.state == WirZeitCountdownState.WALKING -> R.drawable.mascot_traveling
            countdown.state == WirZeitCountdownState.ARRIVED_TODAY -> R.drawable.mascot_celebrating
            else -> R.drawable.mascot_pondering
        }
        Image(
            painter = painterResource(mascotDrawable),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset {
                    val position = if (showsJourney) {
                        val pathLength = pathMeasure.length
                        val goalRadiusPx = goalSizePx / 2f
                        val maximumMascotDistance = (
                            pathLength - (goalRadiusPx + mascotSizePx * 0.45f)
                        ).coerceAtLeast(0f)
                        val mascotDistance = (
                            animatedProgress.value.coerceIn(0f, 1f) * pathLength
                        ).coerceAtMost(maximumMascotDistance)
                        pathPositionAtDistance(
                            pathMeasure = pathMeasure,
                            distance = mascotDistance,
                            fallback = Offset(routeInsetPx, sceneHeightPx * 0.90f),
                        )
                    } else {
                        Offset(sceneWidthPx / 2f, sceneHeightPx - bottomInsetPx)
                    }
                    val bobOffset = if (
                        countdown.state == WirZeitCountdownState.NONE_PLANNED && !isSyncing
                    ) {
                        0f
                    } else {
                        mascotBob.value * mascotBobAmplitudePx
                    }
                    IntOffset(
                        x = (position.x - mascotSizePx / 2f).roundToInt(),
                        y = (position.y - mascotSizePx + bobOffset).roundToInt(),
                    )
                }
                .size(mascotSize),
        )
    }
}

private fun createJourneyPath(
    width: Float,
    height: Float,
    horizontalInset: Float,
): Path = Path().apply {
    if (width <= 0f || height <= 0f) return@apply

    val startX = horizontalInset.coerceAtMost(width / 2f)
    val endX = (width - horizontalInset).coerceAtLeast(startX)
    moveTo(startX, height * 0.90f)
    cubicTo(
        width * 0.30f,
        height * 0.84f,
        width * 0.60f,
        height * 0.34f,
        endX,
        height * 0.28f,
    )
}

private fun pathPosition(
    pathMeasure: PathMeasure,
    progress: Float,
    fallback: Offset,
): Offset = if (pathMeasure.length > 0f) {
    pathMeasure.getPosition(progress.coerceIn(0f, 1f) * pathMeasure.length)
} else {
    fallback
}

private fun pathPositionAtDistance(
    pathMeasure: PathMeasure,
    distance: Float,
    fallback: Offset,
): Offset = if (pathMeasure.length > 0f) {
    pathMeasure.getPosition(distance.coerceIn(0f, pathMeasure.length))
} else {
    fallback
}

private fun loopingCloudX(
    phase: Float,
    startFraction: Float,
    sceneWidth: Float,
    cloudWidth: Float,
): Float {
    val travelWidth = sceneWidth + cloudWidth
    if (travelWidth <= 0f) return -cloudWidth

    val wrappedPhase = (phase + startFraction) % 1f
    return wrappedPhase * travelWidth - cloudWidth
}

private fun DrawScope.drawSoftCloud(
    color: Color,
    x: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    val baseline = top + height
    val leftRadius = height * 0.30f
    drawCircle(
        color = color,
        radius = leftRadius,
        center = Offset(
            x = x + width * 0.20f,
            y = baseline - leftRadius,
        ),
    )
    val centerRadius = height * 0.43f
    drawCircle(
        color = color,
        radius = centerRadius,
        center = Offset(
            x = x + width * 0.39f,
            y = baseline - centerRadius,
        ),
    )
    val rightCenterRadius = height * 0.36f
    drawCircle(
        color = color,
        radius = rightCenterRadius,
        center = Offset(
            x = x + width * 0.60f,
            y = baseline - rightCenterRadius,
        ),
    )
    val rightRadius = height * 0.28f
    drawCircle(
        color = color,
        radius = rightRadius,
        center = Offset(
            x = x + width * 0.80f,
            y = baseline - rightRadius,
        ),
    )
}

