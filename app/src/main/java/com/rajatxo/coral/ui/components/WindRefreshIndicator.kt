package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Soft wind pull-to-refresh indicator — anime-inspired aesthetic.
 *
 * Renders thin white curved streaks flowing horizontally across the top
 * of the screen, like wind currents. Replaces the boring circular arrow
 * spinner that every other app uses.
 *
 * Behavior:
 *  - [progress] = 0, [isRefreshing] = false → invisible (early return).
 *  - Pull in progress ([progress] rising 0→1) → streaks fade in
 *    progressively, frozen at a position derived from pull depth.
 *  - [isRefreshing] = true → all streaks visible, continuously flowing
 *    left-to-right with desynchronized phases (so they don't look
 *    mechanical).
 *
 * Visual design:
 *  - 7 hand-tuned streaks at different y-offsets, lengths, speeds,
 *    phases, alphas, and curve directions.
 *  - Each streak is a cubic Bézier path with a gentle vertical wave
 *    (±8px) — not a straight line, not a hard sine.
 *  - Strokes are 1.6dp thick with rounded caps.
 *  - Alpha is modulated by both streak's own alpha and overall
 *    visibility (driven by pull progress or refreshing state).
 *
 * Positioning:
 *  - Caller should align this to TopCenter of the PullToRefreshBox's
 *    BoxScope. The Canvas fills the full width, 80dp tall.
 *  - Designed to sit behind the fixed header's glass blur — the
 *    streaks are softened by the translucent blur, creating a "wind
 *    through frosted glass" effect.
 *
 * @param progress Pull distance as a fraction of the refresh threshold.
 *                 0 = no pull, 1 = threshold reached, >1 = over-pulled.
 *                 Clamped internally to [0, 1] for visibility math.
 * @param isRefreshing True when a refresh is in progress. Forces full
 *                     visibility and continuous flow animation.
 * @param modifier Standard modifier. Caller is responsible for
 *                 alignment + sizing (height is forced to 80dp here).
 */
@Composable
fun WindRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    // Clamp pull progress — distanceFraction can exceed 1 on over-pull,
    // but visibility math only cares about 0..1.
    val pullProgress = progress.coerceIn(0f, 1f)

    // Overall visibility: full when refreshing, scales with pull otherwise.
    val visibility = if (isRefreshing) 1f else pullProgress
    if (visibility <= 0.01f) return

    // ─── Streak definitions ─────────────────────────────────────────
    // Each streak has its own personality (y-offset, length, speed,
    // phase, alpha, curve direction) so they don't look mechanical.
    //
    // yOffset:     vertical position (0..1 of canvas height)
    // lengthRatio: streak length (0..1 of canvas width)
    // speed:       flow speed multiplier (relative to base time)
    // phase:       starting phase offset (0..1) — desyncs streaks
    // alpha:       base opacity (0..1)
    // curveDir:    +1 = curve up-then-down, -1 = down-then-up
    data class WindStreak(
        val yOffset: Float,
        val lengthRatio: Float,
        val speed: Float,
        val phase: Float,
        val alpha: Float,
        val curveDir: Float
    )

    val streaks = remember {
        listOf(
            WindStreak(yOffset = 0.10f, lengthRatio = 0.30f, speed = 1.00f, phase = 0.00f, alpha = 0.30f, curveDir = +1f),
            WindStreak(yOffset = 0.22f, lengthRatio = 0.45f, speed = 1.30f, phase = 0.25f, alpha = 0.55f, curveDir = -1f),
            WindStreak(yOffset = 0.36f, lengthRatio = 0.25f, speed = 0.85f, phase = 0.55f, alpha = 0.40f, curveDir = +1f),
            WindStreak(yOffset = 0.50f, lengthRatio = 0.50f, speed = 1.50f, phase = 0.15f, alpha = 0.65f, curveDir = -1f),
            WindStreak(yOffset = 0.64f, lengthRatio = 0.35f, speed = 1.10f, phase = 0.50f, alpha = 0.50f, curveDir = +1f),
            WindStreak(yOffset = 0.78f, lengthRatio = 0.28f, speed = 0.75f, phase = 0.80f, alpha = 0.35f, curveDir = -1f),
            WindStreak(yOffset = 0.90f, lengthRatio = 0.42f, speed = 1.25f, phase = 0.05f, alpha = 0.45f, curveDir = +1f)
        )
    }

    // ─── Continuous flow animation ──────────────────────────────────
    // Only runs while refreshing. When not refreshing, the streaks are
    // frozen at a position derived from pull depth (so they don't look
    // busy/jittery while the user is still deciding whether to pull
    // all the way).
    val infiniteTransition = rememberInfiniteTransition(label = "wind")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "windFlow"
    )

    // When refreshing, use the live animation time. When pulling (not
    // refreshing), use a frozen time derived from pull progress — this
    // gives a subtle drift as the user pulls, without animating.
    val flowTime = if (isRefreshing) animTime else (pullProgress * 0.4f)

    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        streaks.forEach { streak ->
            val baseAlpha = streak.alpha * visibility
            if (baseAlpha <= 0.02f) return@forEach

            val y = canvasHeight * streak.yOffset
            val length = (canvasWidth * streak.lengthRatio).coerceAtLeast(40f)

            // Phase wraps around [0, 1) — streak cycles across the canvas.
            val phase = ((flowTime * streak.speed) + streak.phase) % 1f

            // Streak enters from off-screen left, exits past off-screen right.
            // Range: -length (just off-screen left) → +canvasWidth (off-screen right).
            val startX = phase * (canvasWidth + length) - length

            // Subtle vertical curve — gentle wave, not a hard sine.
            val curveAmount = 8f * streak.curveDir

            val path = Path().apply {
                moveTo(startX, y)
                cubicTo(
                    startX + length * 0.30f, y - curveAmount,
                    startX + length * 0.70f, y + curveAmount,
                    startX + length, y
                )
            }

            drawPath(
                path = path,
                color = Color.White.copy(alpha = baseAlpha),
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
