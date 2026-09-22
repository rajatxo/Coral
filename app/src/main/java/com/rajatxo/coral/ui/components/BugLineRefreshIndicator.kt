package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.rajatxo.coral.ui.icons.CoralIcons
import androidx.compose.foundation.layout.Box

/**
 * Bug-on-a-line pull-to-refresh indicator — Coral's signature animation.
 *
 * A thin horizontal line with faded ends sits next to the "Speed dial"
 * chevron. As the user pulls down, a Bug icon crawls from left to right
 * along the line, rotating as it moves. The whole thing fades in when
 * pulling starts and fades out when refresh completes.
 *
 * Behavior:
 *  - [progress] = 0, [isRefreshing] = false → invisible (alpha = 0).
 *  - Pull in progress (progress rising 0→1): line + bug fade in.
 *    Bug position = progress × lineWidth (left → right).
 *    Bug rotation = progress × 360° (one full rotation to threshold).
 *  - [isRefreshing] = true: bug spins continuously at the right end
 *    of the line. Line stays visible.
 *  - Refresh complete: everything fades out smoothly.
 *
 * Visual design:
 *  - Line: 2dp tall, white at 0.4 alpha, both ends fade to transparent
 *    via a BlendMode.DstIn gradient mask.
 *  - Bug: 16dp Lucide Bug icon, white, rotates + translates along the
 *    line based on pull progress.
 *  - The bug's position is CLAMPED to the line bounds (can't go past
 *    the right edge).
 *
 * @param progress Pull distance as a fraction of the refresh threshold.
 *                 0 = no pull, 1 = threshold reached, >1 = over-pulled.
 * @param isRefreshing True when a refresh is in progress.
 * @param modifier Standard modifier. The caller controls width/height.
 */
@Composable
fun BugLineRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    // Clamp progress — distanceFraction can exceed 1 on over-pull.
    val pullProgress = progress.coerceIn(0f, 1f)

    // Overall visibility:
    //   • Fades in as the pull starts (0 → 1 over the first 30% of pull)
    //   • Stays at 1 while refreshing
    //   • Fades out when pull is released AND not refreshing
    val visibility = if (isRefreshing) 1f else (pullProgress / 0.3f).coerceIn(0f, 1f)
    if (visibility <= 0.01f && !isRefreshing) return

    // ─── Continuous spin animation (only while refreshing) ──────────
    val infiniteTransition = rememberInfiniteTransition(label = "bugSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bugSpinAngle"
    )

    // ─── Bug position + rotation ────────────────────────────────────
    val bugPositionFraction = if (isRefreshing) 1f else pullProgress
    val bugRotation = if (isRefreshing) spinAngle else (pullProgress * 360f)

    val bugSize = 16.dp
    val density = LocalDensity.current
    val bugSizePx = with(density) { bugSize.toPx() }

    Box(
        modifier = modifier.graphicsLayer { alpha = visibility }
    ) {
        // Use BoxWithConstraints to get the available width, so we can
        // calculate the bug's X position directly.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(20.dp)
        ) {
            val lineWidthPx = with(density) { maxWidth.toPx() }
            val lineEndPaddingPx = bugSizePx / 2f
            // Bug X position: clamped so it stays centered on the line.
            val bugX = lineEndPaddingPx +
                bugPositionFraction * (lineWidthPx - 2 * lineEndPaddingPx)
            // Convert to dp for the offset modifier.
            val bugOffsetX = with(density) { (bugX - bugSizePx / 2f).toDp() }

            // ─── Layer 1: Thin line with faded ends ───
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                val canvasWidth = size.width
                val centerY = size.height / 2f
                val lineHeight = 2f

                // Draw the line — a thin rounded rect.
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.4f),
                    topLeft = Offset(0f, centerY - lineHeight / 2f),
                    size = Size(canvasWidth, lineHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        lineHeight / 2f, lineHeight / 2f
                    )
                )

                // Apply the fade mask — both ends fade to transparent.
                val fadeBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.15f to Color.Black,
                        0.85f to Color.Black,
                        1.00f to Color.Transparent
                    )
                )
                drawRect(
                    brush = fadeBrush,
                    topLeft = Offset.Zero,
                    size = size,
                    blendMode = BlendMode.DstIn
                )
            }

            // ─── Layer 2: Bug icon, positioned + rotated ───
            // The bug is vertically centered on the line (centerY = 10dp
            // for a 20dp tall container). We offset the bug by
            // (bugOffsetX, 2dp) to center it.
            val bugOffsetY = (20.dp - bugSize) / 2f
            Icon(
                imageVector = CoralIcons.Bug,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(bugSize)
                    .offset(x = bugOffsetX, y = bugOffsetY)
                    .graphicsLayer { rotationZ = bugRotation }
            )
        }
    }
}
