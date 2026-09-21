package com.rajatxo.coral.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.coral.R
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

// ════════════════════════════════════════════════════════════════════
// LEAVES OVERLAY — spring-path falling ginkgo leaves (Box + Icon version)
// ════════════════════════════════════════════════════════════════════
// Pure overlay — no nestedScroll, no pointerInput — so it never
// conflicts with PullToRefreshBox's gesture detection. The caller
// passes pull progress + isRefreshing state from PullToRefreshBox.
//
// THE "INVISIBLE SPRING" PATH
//   Rajat's idea: imagine a spring hanging vertically, stretched so
//   its coils are far apart. Each leaf falls from the top, following
//   the spring's coil path (sine wave in X axis as Y increases).
//
//   Math:
//     x(y) = startX + swayAmplitude * sin(y * springCoilFreq + phase)
//
//   springCoilFreq is LOW (stretched spring, coils far apart vertically)
//   swayAmplitude is moderate (40-120dp swing)
//   Each leaf rotates slowly (5-15°/s) for drifting feel.
//
// FADE
//   Fade IN at the top edge (first 5% of fall) — gentle appearance.
//   Fade OUT at the bottom edge (last 10% of fall) — dissolves.
//
// WHY BOX + ICON INSTEAD OF CANVAS
//   Canvas + painter.draw() can fail silently — the painter might
//   not render if the size/transform isn't set up exactly right.
//   Box + Icon uses the standard Icon composable which handles all
//   the painter plumbing internally. Rock-solid.
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
        val newAlpha = when {
            y < 0.05f -> (y / 0.05f).coerceIn(0f, 1f)
            y > 0.90f -> ((1.0f - y) / 0.10f).coerceIn(0f, 1f)
            else -> 1f
        }
        return copy(y = newY, rotation = newRotation, alpha = newAlpha)
    }

    /** Compute the leaf's X position (normalized) at its current Y, following the spring path. */
    fun currentX(): Float {
        return startX + swayAmplitude * sin(y * springCoilFreq * 2f * Math.PI.toFloat() + swayPhase)
    }

    companion object {
        fun randomSpawn(): LeafParticle {
            val r = Random
            return LeafParticle(
                startX = 0.1f + r.nextFloat() * 0.8f,
                y = -0.05f - r.nextFloat() * 0.10f,
                rotation = r.nextFloat() * 360f,
                rotationSpeed = (r.nextFloat() - 0.5f) * 20f,
                swayPhase = r.nextFloat() * (Math.PI.toFloat() * 2f),
                swayAmplitude = 0.04f + r.nextFloat() * 0.08f,
                springCoilFreq = 1.5f + r.nextFloat() * 1.5f,
                fallSpeed = 0.10f + r.nextFloat() * 0.10f,
                sizeDp = 24f + r.nextFloat() * 16f,
                colorIndex = r.nextInt(4),
                alpha = 0f
            )
        }
    }
}

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

    var leaves by remember { mutableStateOf<List<LeafParticle>>(emptyList()) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    // Animation loop — always running so leaves can spawn when visibility rises
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
            lastFrameTime = now

            if (visibility > 0f) {
                // Spawn leaves when pulling/refreshing
                val targetCount = (visibility * 6).toInt().coerceIn(0, 6)
                if (leaves.size < targetCount && Random.nextFloat() < 0.08f * visibility) {
                    leaves = leaves + LeafParticle.randomSpawn()
                }
            }

            // Always update leaves in flight (so they finish their fall
            // even after visibility drops to 0)
            leaves = leaves.mapNotNull { leaf ->
                val newLeaf = leaf.update(dt.toFloat() / 1000f)
                if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
            }

            delay(16)
        }
    }

    // Use BoxWithConstraints to get the canvas dimensions in dp.
    // Then each leaf is positioned via Modifier.offset + rotate + alpha.
    //
    // NOTE: we do NOT early-return when leaves is empty — the LaunchedEffect
    // above needs to keep running (it's tied to composition) so leaves can
    // spawn when visibility rises above 0.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthDp = maxWidth
        val canvasHeightDp = maxHeight

        leaves.forEach { leaf ->
            val leafX = leaf.currentX() * canvasWidthDp.value  // dp
            val leafY = leaf.y * canvasHeightDp.value          // dp
            val palette = LEAF_PALETTE[leaf.colorIndex.coerceIn(0, LEAF_PALETTE.size - 1)]
            val finalAlpha = leaf.alpha * visibility

            Icon(
                painter = painterResource(id = R.drawable.ginkgo_leaf),
                contentDescription = null,
                tint = palette,
                modifier = Modifier
                    .size(leaf.sizeDp.dp)
                    // Position the leaf's center at (leafX, leafY) by offsetting
                    // from the top-left of the Box.
                    .offset(
                        x = (leafX - leaf.sizeDp / 2f).dp,
                        y = (leafY - leaf.sizeDp / 2f).dp
                    )
                    .rotate(leaf.rotation)
                    .alpha(finalAlpha)
            )
        }
    }
}
