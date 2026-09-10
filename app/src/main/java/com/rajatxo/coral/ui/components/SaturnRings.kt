package com.rajatxo.coral.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import kotlin.math.cos
import kotlin.math.sin

/**
 * Saturn Rings — Coral's "Quick Picks" selector (3D tilted orbital ring).
 *
 * Concept:
 *   A large elliptical ring (a circle tilted in 3D perspective) fills the
 *   screen width. 15 picks are positioned evenly along the ring's
 *   circumference. The ring is tilted ~22° forward, so the bottom of the
 *   ellipse is the "front" (closest to viewer) and the top is the "back"
 *   (furthest away).
 *
 *   The 3D depth effect is the wow factor:
 *     - Picks at the FRONT (bottom) are large (37dp) and bright (100% alpha)
 *     - Picks at the BACK (top) are small (21dp) and dim (35% alpha)
 *     - As the ring rotates, picks smoothly grow/shrink + brighten/dim
 *     - Picks drawn back-to-front (painter's algorithm) for correct occlusion
 *
 *   The active pick is always at the front (6 o'clock position). It has a
 *   pulsing white glow halo behind it (3-second sine wave) and a solid
 *   white ring border.
 *
 *   Background: 60-star starfield that twinkles slowly (each star at a
 *   different phase, all driven by the same 3-second pulse).
 *
 * Interactions:
 *   - Tap any pick → ring rotates (spring physics, shortest path) to bring
 *     that pick to the front. Haptic fires.
 *   - Tap the active pick → plays it.
 *
 * Why this design:
 *   Rajat wanted a wheel concept that gives the 😲 reaction. The 3D tilted
 *   ring with depth-scaled picks is distinctly different from PlaylistWheel
 *   (which is a flat front-view text wheel with off-screen-left pivot).
 *   Saturn is centered, image-based, 3D, and tap-to-rotate — a completely
 *   different wheel paradigm that still rhymes with Coral's rotational DNA.
 */
@Composable
fun SaturnRings(
    songs: List<Song>,
    currentPickIndex: Int,
    onPickChange: (Int) -> Unit,
    onPlayCurrentPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val totalPicks = songs.size
    val safeCurrentIndex = currentPickIndex.coerceIn(0, totalPicks - 1)
    val context = LocalContext.current
    val view = LocalView.current

    // --- Rotation animatable (spring physics, shortest path) ---
    // When currentPickIndex changes, the ring rotates to bring that pick to
    // the front (angle = π/2, bottom of ellipse). We compute the shortest
    // angular path (clockwise or counter-clockwise) so the rotation never
    // takes the long way around.
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(safeCurrentIndex, totalPicks) {
        val target = computeRotationForFront(safeCurrentIndex, totalPicks)
        val current = rotation.value
        val shortestTarget = current + shortestAngleDiff(current, target)
        rotation.animateTo(
            targetValue = shortestTarget,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 80f)
        )
    }

    // --- Pulsing glow + starfield twinkle (3-second sine wave) ---
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Ring geometry — wide and flat (Saturn from above)
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f
        val rx = widthPx * 0.45f    // horizontal radius (90% of screen width)
        val ry = widthPx * 0.18f    // vertical radius (tilt factor ~0.4)

        val basePickSizeDp = 32.dp
        val basePickSizePx = with(density) { basePickSizeDp.toPx() }

        // --- Compute pick layouts (Double math for type safety) ---
        val twoPi = 2.0 * Math.PI
        val halfPi = Math.PI / 2.0

        val layouts = (0 until totalPicks).map { i ->
            val baseAngle = i.toDouble() * (twoPi / totalPicks) - halfPi
            val actualAngle = baseAngle + rotation.value.toDouble()
            val x = (centerX + rx * cos(actualAngle)).toFloat()
            val y = (centerY + ry * sin(actualAngle)).toFloat()
            // Depth: -1 (back/top) to +1 (front/bottom)
            val depth = sin(actualAngle).toFloat()
            // Scale: 0.65 (back) to 1.15 (front)
            val scale = 0.65f + 0.5f * (depth + 1f) / 2f
            // Alpha: 0.35 (back) to 1.0 (front)
            val alpha = 0.35f + 0.65f * (depth + 1f) / 2f
            PickLayout(i, x, y, depth, scale, alpha)
        }

        // Sort by depth for painter's algorithm (back first, front last)
        val sortedLayouts = layouts.sortedBy { it.depth }
        val activeLayout = layouts[safeCurrentIndex]

        Box(modifier = Modifier.fillMaxSize()) {
            // ============================================================
            // CANVAS: starfield + ring + active pick glow
            // ============================================================
            Canvas(modifier = Modifier.fillMaxSize()) {
                // --- Starfield (60 stars, fixed seed for stable positions) ---
                val starRandom = kotlin.random.Random(42)
                val starCount = 60
                for (s in 0 until starCount) {
                    val sx = starRandom.nextFloat() * size.width
                    val sy = starRandom.nextFloat() * size.height
                    val starSize = 0.5f + starRandom.nextFloat() * 1.5f
                    val twinklePhase = pulsePhase + s * 0.5f
                    val twinkleAlpha = (0.2f + 0.35f * sin(twinklePhase.toDouble()).toFloat())
                        .coerceIn(0.08f, 0.55f)
                    drawCircle(
                        color = Color.White.copy(alpha = twinkleAlpha),
                        radius = starSize,
                        center = Offset(sx, sy)
                    )
                }

                // --- Ring (elliptical orbit path, barely visible) ---
                drawOval(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(centerX - rx, centerY - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )

                // --- Active pick pulsing glow ---
                val glowRadius = basePickSizePx * 1.5f
                val pulseAlpha = 0.3f + 0.15f * sin(pulsePhase.toDouble()).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = pulseAlpha),
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(activeLayout.x, activeLayout.y),
                        radius = glowRadius
                    ),
                    center = Offset(activeLayout.x, activeLayout.y),
                    radius = glowRadius
                )
            }

            // ============================================================
            // PICKS (drawn in sorted order, back to front)
            // ============================================================
            sortedLayouts.forEach { pick ->
                val isActive = pick.index == safeCurrentIndex
                val pickSizeDp = with(density) { (basePickSizePx * pick.scale).toDp() }
                val topLeftXDp = with(density) {
                    (pick.x - basePickSizePx * pick.scale / 2f).toDp()
                }
                val topLeftYDp = with(density) {
                    (pick.y - basePickSizePx * pick.scale / 2f).toDp()
                }

                Box(
                    modifier = Modifier
                        .offset(x = topLeftXDp, y = topLeftYDp)
                        .size(pickSizeDp)
                        .alpha(pick.alpha)
                ) {
                    // Album art thumbnail (circle-clipped, with crossfade)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (pick.index != safeCurrentIndex) {
                                        onPickChange(pick.index)
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                    } else {
                                        onPlayCurrentPick()
                                    }
                                }
                            )
                    ) {
                        val song = songs[pick.index]
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(song.albumArtUri)
                                    .crossfade(300)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1A1A1A))
                            )
                        }
                    }

                    // Active pick: solid white ring border
                    if (isActive) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White,
                                radius = (size.minDimension / 2f) - 1.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Computes the rotation offset that brings pick at `pickIndex` to the
 * front (angle = π/2, bottom of ellipse).
 *
 * baseAngle_i = i * (2π / totalPicks) - π/2
 * actualAngle = baseAngle_i + rotation = π/2  (we want pick at front)
 * rotation = π/2 - baseAngle_i
 */
private fun computeRotationForFront(pickIndex: Int, totalPicks: Int): Float {
    val twoPi = 2.0 * Math.PI
    val halfPi = Math.PI / 2.0
    val baseAngle = pickIndex.toDouble() * (twoPi / totalPicks) - halfPi
    return (halfPi - baseAngle).toFloat()
}

/**
 * Returns the shortest angular difference from `from` to `to`, in range
 * [-π, π]. This ensures the ring always rotates the shortest path (never
 * takes the long way around).
 */
private fun shortestAngleDiff(from: Float, to: Float): Float {
    val twoPi = (2.0 * Math.PI).toFloat()
    var diff = (to - from) % twoPi
    if (diff < -Math.PI.toFloat()) diff += twoPi
    if (diff > Math.PI.toFloat()) diff -= twoPi
    return diff
}

/**
 * Layout data for a single pick on the ring.
 */
private data class PickLayout(
    val index: Int,
    val x: Float,
    val y: Float,
    val depth: Float,   // -1 (back) to +1 (front)
    val scale: Float,   // 0.65 (back) to 1.15 (front)
    val alpha: Float    // 0.35 (back) to 1.0 (front)
)
