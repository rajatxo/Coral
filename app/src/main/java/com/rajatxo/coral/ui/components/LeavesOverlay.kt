package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.coral.R
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

// ════════════════════════════════════════════════════════════════════
// LEAVES OVERLAY — spring-path falling ginkgo leaves
// ════════════════════════════════════════════════════════════════════
// Pure Canvas overlay — no nestedScroll, no pointerInput — so it never
// conflicts with PullToRefreshBox's gesture detection. The caller
// passes pull progress + isRefreshing state from PullToRefreshBox.
//
// THE "INVISIBLE SPRING" PATH
//   Rajat's idea: imagine a spring hanging vertically, stretched so
//   its coils are far apart. Each leaf falls from the top, following
//   the spring's coil path (sine wave in the X axis as Y increases).
//
//   Math:
//     x(y) = startX + swayAmplitude * sin(y * springCoilFreq + phase)
//
//   springCoilFreq is LOW (one sine cycle every ~200dp of fall)
//   because the spring is STRETCHED — coils are far apart vertically.
//   swayAmplitude is moderate (40-80dp) — the leaf swings left/right
//   noticeably but not chaotically.
//
//   Each leaf also rotates slowly as it falls (5-15°/s) for that
//   drifting, weightless feel.
//
// FADE
//   Fade IN at the top edge (first 5% of fall) so leaves appear gently.
//   Fade OUT at the bottom edge (last 10% of fall) so they dissolve
//   instead of popping off-screen.
//
// PALETTE — autumn Ghost-of-Yotei
//   golden yellow, amber, deep gold, red-amber.
//   Tint applied via ImageVector.painterResource — tints the
//   VectorDrawable's fillColor to the per-leaf color.
// ════════════════════════════════════════════════════════════════════

private data class LeafParticle(
    val startX: Float,            // normalized 0..1 (where in canvas width the leaf starts)
    val y: Float,                 // normalized 0..1.3 (1.0 = bottom edge, 1.3 = past bottom)
    val rotation: Float,          // degrees
    val rotationSpeed: Float,     // degrees per second
    val swayPhase: Float,         // sine wave phase offset (radians)
    val swayAmplitude: Float,     // normalized x-amplitude (0.04-0.12 of canvas width)
    val springCoilFreq: Float,    // coil frequency: 2π/wavelength_y (small = stretched spring)
    val fallSpeed: Float,         // normalized y per second (0.10-0.20)
    val sizeDp: Float,            // leaf icon size in dp (24-40)
    val colorIndex: Int,          // 0..3 palette index
    val alpha: Float              // 0..1
) {
    fun update(dt: Float): LeafParticle {
        val newY = y + fallSpeed * dt
        val newRotation = rotation + rotationSpeed * dt
        // Fade in at top, fade out at bottom
        val newAlpha = when {
            y < 0.05f -> (y / 0.05f).coerceIn(0f, 1f)
            y > 0.90f -> ((1.0f - y) / 0.10f).coerceIn(0f, 1f)
            else -> 1f
        }
        return copy(y = newY, rotation = newRotation, alpha = newAlpha)
    }

    /** Compute the leaf's X position at its current Y, following the spring path. */
    fun currentX(): Float {
        return startX + swayAmplitude * sin(y * springCoilFreq * 2f * Math.PI.toFloat() + swayPhase)
    }

    companion object {
        fun randomSpawn(): LeafParticle {
            val r = Random
            return LeafParticle(
                startX = 0.1f + r.nextFloat() * 0.8f,  // start within 10%-90% of width
                y = -0.05f - r.nextFloat() * 0.10f,    // start above the top
                rotation = r.nextFloat() * 360f,
                rotationSpeed = (r.nextFloat() - 0.5f) * 20f,  // ±10°/s
                swayPhase = r.nextFloat() * (Math.PI.toFloat() * 2f),
                swayAmplitude = 0.04f + r.nextFloat() * 0.08f,  // 0.04-0.12
                springCoilFreq = 1.5f + r.nextFloat() * 1.5f,    // LOW: stretched spring
                fallSpeed = 0.10f + r.nextFloat() * 0.10f,       // 0.10-0.20
                sizeDp = 24f + r.nextFloat() * 16f,               // 24-40dp
                colorIndex = r.nextInt(4),
                alpha = 0f
            )
        }
    }
}

// Autumn palette — Ghost of Yotei golden hour
private val LEAF_PALETTE = listOf(
    Color(0xFFF4C724),  // golden yellow
    Color(0xFFE89B2C),  // amber
    Color(0xFFD4861C),  // deep gold
    Color(0xFFB85626)   // red-amber
)

@Composable
fun LeavesOverlay(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val pullProgress = progress.coerceIn(0f, 1f)
    val visibility = if (isRefreshing) 1f else pullProgress
    if (visibility <= 0.05f) return

    var leaves by remember { mutableStateOf<List<LeafParticle>>(emptyList()) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    // Pre-load the ginkgo leaf painter once (not per-frame).
    val leafPainter = painterResource(id = R.drawable.ginkgo_leaf)

    // Animation loop — updates particle positions every frame.
    // Restarts whenever visibility changes (0 → >0 or back).
    LaunchedEffect(visibility) {
        if (visibility > 0f) {
            while (true) {
                val now = System.currentTimeMillis()
                val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
                lastFrameTime = now

                // Target leaf count scales with visibility
                val targetCount = (visibility * 6).toInt().coerceIn(0, 6)
                if (leaves.size < targetCount && Random.nextFloat() < 0.08f * visibility) {
                    leaves = leaves + LeafParticle.randomSpawn()
                }

                // Update leaves, drop any that fell off
                leaves = leaves.mapNotNull { leaf ->
                    val newLeaf = leaf.update(dt.toFloat() / 1000f)
                    if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
                }

                delay(16)  // ~60fps
            }
        } else {
            // Visibility dropped to 0 — let existing leaves finish their fall
            lastFrameTime = 0L
            while (leaves.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
                lastFrameTime = now
                leaves = leaves.mapNotNull { leaf ->
                    val newLeaf = leaf.update(dt.toFloat() / 1000f)
                    if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
                }
                delay(16)
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        leaves.forEach { leaf ->
            val leafSizePx = leaf.sizeDp.dp.toPx()
            val actualX = leaf.currentX() * w
            val actualY = leaf.y * h
            val palette = LEAF_PALETTE[leaf.colorIndex.coerceIn(0, LEAF_PALETTE.size - 1)]
            val finalAlpha = leaf.alpha * visibility

            // Translate to the leaf's position, rotate around the leaf's center.
            // The painter's intrinsic size is 512x512 (the SVG viewBox).
            // We scale it down to leafSizePx.
            rotate(degrees = leaf.rotation, pivot = Offset(actualX, actualY)) {
                withTransform({
                    translate(
                        left = actualX - leafSizePx / 2f,
                        top = actualY - leafSizePx / 2f
                    )
                }) {
                    // Draw the painter. painterResource returns a VectorPainter
                    // that respects the fillColor tint when drawn via draw scope.
                    // We override the color by passing our palette color directly
                    // via the painter's intrinsic draw — since VectorDrawable's
                    // fillColor is #FF6B6B but we want per-leaf tint, we apply
                    // a ColorFilter tint.
                    with(leafPainter) {
                        // Paint the leaf at leafSizePx x leafSizePx
                        val painterSize = androidx.compose.ui.geometry.Size(
                            leafSizePx,
                            leafSizePx
                        )
                        draw(
                            size = painterSize,
                            alpha = finalAlpha,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(palette)
                        )
                    }
                }
            }
        }
    }
}
