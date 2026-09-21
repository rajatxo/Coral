package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ginkgo leaf — hand-drawn Canvas composable.
 *
 * Why Canvas instead of ImageVector:
 *   - ImageVector paths only support SolidColor fills — no gradients.
 *   - We want a radial gradient body (yellow center → amber edge) for
 *     that "alive" autumn look, not a flat fill.
 *   - We want visible veins with subtle alpha + a separate stem color.
 *   - Each leaf can be a different color (autumn palette: yellow, amber,
 *     deep gold, red-amber).
 *
 * Anatomy (24×24 viewport, scaled to whatever size you pass):
 *   1. BODY silhouette — fan shape with a distinctive notch cut into
 *      the top edge. Hand-drawn with 4 cubic Beziers + 2 quads for the
 *      corners + 2 cubic Beziers for the notch dip.
 *   2. RADIAL GRADIENT fill — 3 stops: bright yellow at the bottom
 *      center (where the stem attaches), amber in the middle, deep
 *      amber at the outer edges. Centered at (12, 14), radius ~12.
 *   3. VEINS — 5 radial lines fanning from the stem-attachment point
 *      (12, 20) up to the top edge at angles -60°, -30°, 0°, 30°, 60°.
 *      Drawn with low alpha (0.45) so they're visible but not loud.
 *   4. STEM — short straight line from (12, 20) to (12, 22). Dark brown.
 *   5. SUBTLE HIGHLIGHT — small lighter ellipse near top-left of the
 *      body for a 3D volumetric feel (sun hitting the upper edge).
 *
 * The leaf can be tinted via [bodyColor] / [edgeColor] / [veinColor] /
 * [stemColor] so multiple leaves in a particle system don't all look
 * identical. Pass different colors for the autumn palette variety.
 */
@Composable
fun GinkgoLeaf(
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFFF4C724),     // golden yellow (center)
    edgeColor: Color = Color(0xFFC8851B),     // deep amber (outer)
    veinColor: Color = Color(0xFF8B5A1C),     // brown
    stemColor: Color = Color(0xFF6B4423),      // darker brown
    rotationDegrees: Float = 0f,
    alpha: Float = 1f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Scale our 24×24 design to whatever size the canvas is.
        val s = minOf(w, h) / 24f
        val cx = w / 2f
        val cy = h / 2f

        // Translate so the leaf is centered (design is centered at 12,12).
        rotate(degrees = rotationDegrees, pivot = Offset(cx, cy)) {
            // ── (1) BODY silhouette ────────────────────────────────────────
            // Hand-drawn fan shape with the distinctive center notch.
            val body = Path().apply {
                // Bottom point (stem attachment)
                moveTo(12f * s + (cx - 12 * s), 20f * s + (cy - 12 * s))

                // LEFT SIDE — fan curve going up from bottom to top-left
                cubicTo(
                    6f * s + (cx - 12 * s), 18f * s + (cy - 12 * s),
                    3f * s + (cx - 12 * s), 13f * s + (cy - 12 * s),
                    5f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)
                )

                // TOP-LEFT CORNER — small rounded curve
                quadraticTo(
                    5f * s + (cx - 12 * s), 4f * s + (cy - 12 * s),
                    7f * s + (cx - 12 * s), 4f * s + (cy - 12 * s)
                )

                // TOP EDGE (left half) — to just before the notch
                lineTo(11f * s + (cx - 12 * s), 4f * s + (cy - 12 * s))

                // NOTCH (left side going down) — distinctive ginkgo split
                cubicTo(
                    11.5f * s + (cx - 12 * s), 5.5f * s + (cy - 12 * s),
                    11.7f * s + (cx - 12 * s), 6.5f * s + (cy - 12 * s),
                    12f * s + (cx - 12 * s), 7f * s + (cy - 12 * s)
                )

                // NOTCH (right side coming back up)
                cubicTo(
                    12.3f * s + (cx - 12 * s), 6.5f * s + (cy - 12 * s),
                    12.5f * s + (cx - 12 * s), 5.5f * s + (cy - 12 * s),
                    13f * s + (cx - 12 * s), 4f * s + (cy - 12 * s)
                )

                // TOP EDGE (right half) — past the notch to top-right
                lineTo(17f * s + (cx - 12 * s), 4f * s + (cy - 12 * s))

                // TOP-RIGHT CORNER — small rounded curve
                quadraticTo(
                    19f * s + (cx - 12 * s), 4f * s + (cy - 12 * s),
                    19f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)
                )

                // RIGHT SIDE — fan curve going down to bottom
                cubicTo(
                    21f * s + (cx - 12 * s), 13f * s + (cy - 12 * s),
                    18f * s + (cx - 12 * s), 18f * s + (cy - 12 * s),
                    12f * s + (cx - 12 * s), 20f * s + (cy - 12 * s)
                )

                close()
            }

            // ── (2) RADIAL GRADIENT fill ───────────────────────────────────
            // Centered at the lower-middle of the leaf (where the stem
            // attaches). Yellow at the center fades to deep amber at the
            // edges. Gives the leaf a 3D, sun-lit feel.
            val bodyBrush = Brush.radialGradient(
                colors = listOf(bodyColor, edgeColor, edgeColor.copy(alpha = 0.85f)),
                center = Offset(12f * s + (cx - 12 * s), 14f * s + (cy - 12 * s)),
                radius = 14f * s
            )
            drawPath(
                path = body,
                brush = bodyBrush,
                alpha = alpha
            )

            // ── (3) SUBTLE HIGHLIGHT ───────────────────────────────────────
            // A small lighter ellipse near the top-left of the body —
            // simulates sunlight hitting the upper-left of the leaf.
            drawOval(
                color = Color.White.copy(alpha = 0.18f * alpha),
                topLeft = Offset(
                    8f * s + (cx - 12 * s),
                    7f * s + (cy - 12 * s)
                ),
                size = Size(4f * s, 2.5f * s)
            )

            // ── (4) VEINS ──────────────────────────────────────────────────
            // 5 radial lines from (12, 20) [stem attachment] to the top
            // edge at angles -60°, -30°, 0°, 30°, 60°.
            // The center vein stops at the notch bottom (12, 7) so it
            // doesn't poke through the notch.
            val veinStroke = Stroke(
                width = 0.6f * s,
                cap = StrokeCap.Round
            )
            val stemX = 12f * s + (cx - 12 * s)
            val stemY = 20f * s + (cy - 12 * s)

            // Vein 1: -60° (leftmost)
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(6f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // Vein 2: -30°
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(8.5f * s + (cx - 12 * s), 4.5f * s + (cy - 12 * s)),
                strokeWidth = 0.55f * s,
                cap = StrokeCap.Round
            )

            // Vein 3: 0° (center — stops at notch bottom)
            drawLine(
                color = veinColor.copy(alpha = 0.50f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(12f * s + (cx - 12 * s), 7.2f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // Vein 4: +30°
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(15.5f * s + (cx - 12 * s), 4.5f * s + (cy - 12 * s)),
                strokeWidth = 0.55f * s,
                cap = StrokeCap.Round
            )

            // Vein 5: +60° (rightmost)
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(18f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // ── (5) STEM ───────────────────────────────────────────────────
            // Short straight line from the bottom of the leaf downward.
            drawLine(
                color = stemColor.copy(alpha = 0.9f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(12f * s + (cx - 12 * s), 23f * s + (cy - 12 * s)),
                strokeWidth = 1.2f * s,
                cap = StrokeCap.Round
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// LEAVES OVERLAY — pure Canvas, no nestedScroll/pointerInput
// ════════════════════════════════════════════════════════════════════
// Why this exists separately from AutumnLeavesRefresh:
//   AutumnLeavesRefresh used `nestedScroll` which conflicted with the
//   outer PullToRefreshBox's pull detection — neither fired properly.
//
//   LeavesOverlay is just a Canvas overlay. It doesn't try to detect
//   gestures. The caller (QuickPicksScreen) passes the pull progress
//   and isRefreshing state from the PullToRefreshBox — so the overlay
//   shows leaves WHEN the PullToRefreshBox says so, without interfering
//   with its gesture detection.
//
// Behavior:
//   - progress < 0.1 AND !isRefreshing → invisible (early return)
//   - progress rising 0→1 → leaves fade in + spawn rate increases
//   - isRefreshing = true → full density, leaves continue falling
//   - 4-6 leaves max on screen at a time
//   - Each leaf: random x, slow rotation, sine-wave sway, fall speed
//   - 4-color autumn palette: golden yellow, amber, deep gold, red-amber
// ════════════════════════════════════════════════════════════════════

private data class LeafParticle(
    val x: Float,              // normalized 0..1 (canvas width)
    val y: Float,              // normalized 0..1.3 (1.0 = bottom edge, 1.3 = past bottom)
    val rotation: Float,        // degrees
    val rotationSpeed: Float,   // degrees per second
    val swayPhase: Float,       // sine wave phase offset
    val swayAmplitude: Float,   // normalized x-amplitude (0.01-0.04)
    val swayFreq: Float,        // Hz (0.3-0.7 — slow sway)
    val fallSpeed: Float,       // normalized y per second (0.08-0.16)
    val size: Float,            // dp (24-40)
    val colorIndex: Int,        // 0..3 palette index
    val alpha: Float            // 0..1
) {
    fun update(dt: Float): LeafParticle {
        val newY = y + fallSpeed * dt
        val newRotation = rotation + rotationSpeed * dt
        val newAlpha = when {
            y < 0.05f -> (y / 0.05f).coerceIn(0f, 1f)
            y > 0.95f -> ((1.1f - y) / 0.15f).coerceIn(0f, 1f)
            else -> 1f
        }
        return copy(y = newY, rotation = newRotation, alpha = newAlpha)
    }

    companion object {
        fun randomSpawn(): LeafParticle {
            val r = kotlin.random.Random
            return LeafParticle(
                x = r.nextFloat(),
                y = -0.05f - r.nextFloat() * 0.1f,
                rotation = r.nextFloat() * 360f,
                rotationSpeed = (r.nextFloat() - 0.5f) * 20f,
                swayPhase = r.nextFloat() * (Math.PI.toFloat() * 2f),
                swayAmplitude = 0.01f + r.nextFloat() * 0.03f,
                swayFreq = 0.3f + r.nextFloat() * 0.4f,
                fallSpeed = 0.08f + r.nextFloat() * 0.08f,
                size = 24f + r.nextFloat() * 16f,
                colorIndex = r.nextInt(4),
                alpha = 0f
            )
        }
    }
}

// Autumn palette — Ghost of Yotei golden hour
private val LEAF_PALETTE = listOf(
    LeafColor(body = Color(0xFFF4C724), edge = Color(0xFFC8851B)),  // golden yellow
    LeafColor(body = Color(0xFFE89B2C), edge = Color(0xFFA86A1B)),  // amber
    LeafColor(body = Color(0xFFD4861C), edge = Color(0xFF8B5A1C)),  // deep gold
    LeafColor(body = Color(0xFFB85626), edge = Color(0xFF6B3416))   // red-amber
)

private data class LeafColor(val body: Color, val edge: Color)

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

    // Animation loop — updates particle positions every frame
    androidx.compose.runtime.LaunchedEffect(visibility) {
        while (visibility > 0f) {
            val now = System.currentTimeMillis()
            val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
            lastFrameTime = now

            // Spawn rate scales with visibility
            val targetLeafCount = (visibility * 6).toInt().coerceIn(0, 6)
            if (leaves.size < targetLeafCount && kotlin.random.Random.nextFloat() < 0.08f * visibility) {
                leaves = leaves + LeafParticle.randomSpawn()
            }

            // Update leaves, drop any that fell off
            leaves = leaves.mapNotNull { leaf ->
                val newLeaf = leaf.update(dt.toFloat() / 1000f)
                if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
            }

            kotlinx.coroutines.delay(16)
        }
        // When visibility drops to 0, let existing leaves finish their fall
        while (leaves.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
            lastFrameTime = now
            leaves = leaves.mapNotNull { leaf ->
                val newLeaf = leaf.update(dt.toFloat() / 1000f)
                if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
            }
            kotlinx.coroutines.delay(16)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        leaves.forEach { leaf ->
            val leafSizePx = leaf.size.dp.toPx()
            // Sway (sine wave) applied to x
            val sway = (kotlin.math.sin(leaf.y * leaf.swayFreq * Math.PI.toFloat() * 2f * 4f + leaf.swayPhase) * leaf.swayAmplitude)
            val actualX = (leaf.x + sway) * w
            val actualY = leaf.y * h
            val palette = LEAF_PALETTE[leaf.colorIndex.coerceIn(0, LEAF_PALETTE.size - 1)]

            // Tint alpha by overall visibility too
            val finalAlpha = leaf.alpha * visibility

            rotate(degrees = leaf.rotation, pivot = androidx.compose.ui.geometry.Offset(actualX, actualY)) {
                // Leaf body — fan shape with notch (inlined for perf)
                val s = leafSizePx / 24f
                val cx = actualX
                val cy = actualY
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy + 8 * s)
                    cubicTo(cx - 6 * s, cy + 6 * s, cx - 9 * s, cy + 1 * s, cx - 7 * s, cy - 6 * s)
                    quadraticTo(cx - 7 * s, cy - 8 * s, cx - 5 * s, cy - 8 * s)
                    lineTo(cx - 1 * s, cy - 8 * s)
                    cubicTo(cx - 0.5f * s, cy - 6.5f * s, cx - 0.3f * s, cy - 5.5f * s, cx, cy - 5 * s)
                    cubicTo(cx + 0.3f * s, cy - 5.5f * s, cx + 0.5f * s, cy - 6.5f * s, cx + 1 * s, cy - 8 * s)
                    lineTo(cx + 5 * s, cy - 8 * s)
                    quadraticTo(cx + 7 * s, cy - 8 * s, cx + 7 * s, cy - 6 * s)
                    cubicTo(cx + 9 * s, cy + 1 * s, cx + 6 * s, cy + 6 * s, cx, cy + 8 * s)
                    close()
                }

                // Body — radial gradient
                val bodyBrush = Brush.radialGradient(
                    colors = listOf(palette.body, palette.edge, palette.edge.copy(alpha = 0.85f)),
                    center = androidx.compose.ui.geometry.Offset(actualX, actualY + 2 * s),
                    radius = leafSizePx * 0.6f
                )
                drawPath(path = path, brush = bodyBrush, alpha = finalAlpha)

                // Veins — 5 radial lines
                val veinColor = Color(0xFF6B4423).copy(alpha = 0.5f * finalAlpha)
                val stemX = actualX
                val stemY = actualY + 8 * s
                for (angle in listOf(-60f, -30f, 0f, 30f, 60f)) {
                    val rad = Math.toRadians(angle.toDouble())
                    val endX = (stemX + 8 * s * kotlin.math.cos(rad)).toFloat()
                    val endY = if (angle == 0f) stemY - 13 * s
                               else (stemY - 12 * s * kotlin.math.sin(rad)).toFloat()
                    drawLine(
                        color = veinColor,
                        start = androidx.compose.ui.geometry.Offset(stemX, stemY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = leafSizePx / 40f,
                        cap = StrokeCap.Round
                    )
                }

                // Stem
                drawLine(
                    color = Color(0xFF6B4423).copy(alpha = 0.9f * finalAlpha),
                    start = androidx.compose.ui.geometry.Offset(stemX, stemY),
                    end = androidx.compose.ui.geometry.Offset(stemX, stemY + 3 * s),
                    strokeWidth = leafSizePx / 20f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
