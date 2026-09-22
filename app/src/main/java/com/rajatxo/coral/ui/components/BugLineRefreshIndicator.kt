package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Bug-on-a-line pull-to-refresh indicator — Coral's signature animation.
 *
 * A thin horizontal line with faded ends sits next to the "Speed dial"
 * chevron. As the user pulls down, a Bug icon WALKS from left to right
 * along the line (no rotation — just a subtle vertical bob that reads
 * as "legs walking"). When the pull threshold is reached and refresh
 * starts, the bug does a U-turn (squishes flat then expands) and walks
 * back from right to left, slowly, matching the refresh duration.
 *
 * Animation sequence (ONE direction per phase — no back-and-forth):
 *   1. PULL: bug walks LEFT → RIGHT (position = pullProgress)
 *   2. REFRESH START: bug U-turns (scaleX squish: 1 → 0 → 1, 300ms)
 *   3. REFRESH: bug walks RIGHT → LEFT (position 1 → 0, 900ms, slow)
 *   4. DONE: everything fades out
 *
 * The "walking" feel comes from a subtle vertical bob (±1.5dp sine wave
 * at ~5Hz) that only animates while the bug is moving. No individual
 * leg animation — at 16dp, a bob reads as "walking" much better than
 * actual leg wiggles would.
 *
 * @param progress Pull distance as a fraction of the refresh threshold.
 * @param isRefreshing True when a refresh is in progress.
 * @param modifier Standard modifier.
 */
@Composable
fun BugLineRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val pullProgress = progress.coerceIn(0f, 1f)

    // Visibility: fade in over first 30% of pull, stay at 1 while refreshing.
    val visibility = if (isRefreshing) 1f else (pullProgress / 0.3f).coerceIn(0f, 1f)
    if (visibility <= 0.01f && !isRefreshing) return

    // ─── Bug X position ─────────────────────────────────────────────
    // PULLING: position follows the finger instantly (snap).
    //   - pullProgress is driven by ptrState.distanceFraction, which
    //     PullToRefreshBox animates smoothly on release (spring-back).
    //   - Using snap() means the bug follows that animated value, so
    //     on release-without-trigger, the bug walks back smoothly.
    //
    // REFRESHING: position animates from current (1.0) → 0.0 slowly.
    //   - 900ms LinearEasing — matches the refresh duration.
    //   - The bug walks from right to left, one direction, slow.
    val targetPosition = if (isRefreshing) 0f else pullProgress
    val bugPosition by animateFloatAsState(
        targetValue = targetPosition,
        animationSpec = if (isRefreshing) {
            tween(durationMillis = 900, easing = LinearEasing)
        } else {
            snap()
        },
        label = "bugPosition"
    )

    // ─── U-turn via smooth fade ─────────────────────────────────────
    // The Bug icon's head is at the TOP (perpendicular to the line).
    // We rotate it 90° clockwise so the head is PARALLEL to the line,
    // pointing right during the pull phase.
    //
    // When refresh starts, the bug does a U-turn via a SMOOTH FADE:
    //   Phase 1 (0–150ms): bug fades out (alpha 1 → 0). Head still
    //     points right.
    //   Phase 2 (invisible, instant): rotationZ jumps from 90° → 270°.
    //   Phase 3 (150–300ms): bug fades back in (alpha 0 → 1). Head now
    //     points left — the direction the bug walks during refresh.
    //
    // This gives a "smooth transition" (the bug dissolves, reappears
    // facing the other way) without a visible rotation or squish —
    // which the user found felt "funny".
    val uTurnProgress = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            // U-turn: 0 → 1 over 300ms (150ms fade out + 150ms fade in)
            uTurnProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        } else {
            uTurnProgress.snapTo(0f)
        }
    }

    // Derive rotationZ and bug alpha from U-turn progress:
    //   progress 0.0–0.5: alpha 1 → 0 (fade out), rotationZ = 90° (head right)
    //   progress 0.5:     alpha = 0 (invisible — rotation flips to 270°)
    //   progress 0.5–1.0: alpha 0 → 1 (fade in), rotationZ = 270° (head left)
    val uTurn = uTurnProgress.value
    val bugRotationZ = if (isRefreshing) {
        // 90° for the first half (fading out, head right),
        // 270° for the second half (fading in, head left).
        if (uTurn < 0.5f) 90f else 270f
    } else {
        90f  // head right during pull
    }
    // Alpha: 1 at uTurn=0, 0 at uTurn=0.5, 1 at uTurn=1.
    // Triangle wave — smooth fade out then smooth fade in.
    val bugAlpha = if (isRefreshing) {
        (1f - kotlin.math.abs(uTurn * 2f - 1f))
    } else {
        1f
    }

    val bugSize = 16.dp
    val density = LocalDensity.current
    val bugSizePx = with(density) { bugSize.toPx() }

    Box(
        modifier = modifier.graphicsLayer { alpha = visibility }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(20.dp)
        ) {
            val lineWidthPx = with(density) { maxWidth.toPx() }
            val lineEndPaddingPx = bugSizePx / 2f
            // Bug X position: clamped so it stays centered on the line.
            val bugX = lineEndPaddingPx +
                bugPosition * (lineWidthPx - 2 * lineEndPaddingPx)
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

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.4f),
                    topLeft = Offset(0f, centerY - lineHeight / 2f),
                    size = Size(canvasWidth, lineHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        lineHeight / 2f, lineHeight / 2f
                    )
                )

                // Fade mask — both ends fade to transparent.
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

            // ─── Layer 2: Bug icon (stable, no bob) ───
            // Vertically centered on the line. No vertical wobble —
            // the bug is stable while walking.
            val bugOffsetY = (20.dp - bugSize) / 2f

            Icon(
                imageVector = CoralIcons.Bug,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(bugSize)
                    .offset(x = bugOffsetX, y = bugOffsetY)
                    .graphicsLayer {
                        // Head parallel to line:
                        //   Pull: rotationZ = 90° (head points right)
                        //   After U-turn: rotationZ = 270° (head points left)
                        // The rotation flips INSTANTLY while the bug is
                        // invisible (alpha = 0 at the U-turn midpoint),
                        // so the user doesn't see a spin — just a smooth
                        // fade out + fade in with the head now pointing
                        // the other way.
                        rotationZ = bugRotationZ
                        // Smooth fade for the U-turn transition.
                        alpha = bugAlpha
                    }
            )
        }
    }
}
