package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ════════════════════════════════════════════════════════════════════
// AUTUMN LEAVES REFRESH
// ════════════════════════════════════════════════════════════════════
// Pull-to-refresh with falling ginkgo leaves + tapered wind lines.
//
// Leaves: 4-6 particles spawn at the top, each with random:
//   • x position
//   • rotation speed (slow — 5-15°/sec, never spins fast)
//   • sway phase + amplitude (sine wave side-to-side as they fall)
//   • fall speed (60-120 dp/s — slow drift, not snowfall)
//   • size (24-40 dp — variety)
//   • color (4-color autumn palette: golden yellow, amber, deep gold,
//     red-amber)
//
// Wind: 2-4 lines visible at a time. Each line is:
//   • A tapered "lens" shape — pointed at both ends, fat in the middle
//     (NOT a straight stroke with constant width)
//   • Follows a spiral/coil path: head position coils once (one full
//     sin/cos cycle) then continues to a random direction
//   • Starts from the top-left or top edge
//   • Fades IN over the first 30% of its lifespan (alpha 0 → 1)
//   • Fades OUT over the last 30% of its lifespan (alpha 1 → 0)
//   • White-ish color with low alpha so it reads as "wind" not "line"
//
// Aesthetic: Ghost of Yotei inspired — sparse, poetic, golden hour.
// ════════════════════════════════════════════════════════════════════

@Composable
fun AutumnLeavesRefresh(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullThreshold: Float = 200f,  // pull-down distance in dp needed to trigger refresh
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    // Pull state — how far the user has pulled down (in pixels)
    var pullPx by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var leaves by remember { mutableStateOf<List<LeafParticle>>(emptyList()) }
    var winds by remember { mutableStateOf<List<WindParticle>>(emptyList()) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    val pullThresholdPx = with(density) { pullThreshold.dp.toPx() }
    // Pull progress 0..1 — drives how many leaves/winds to spawn
    val pullProgress = (pullPx / pullThresholdPx).coerceIn(0f, 1.5f)

    // Animation loop — drives all particle updates
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val dt = if (lastFrameTime == 0L) 16L else (now - lastFrameTime).coerceAtMost(50L)
            lastFrameTime = now

            // Spawn leaves when pull > 0 OR still some leaves in flight
            val targetLeafCount = (pullProgress * 6).toInt().coerceAtMost(6)
            if (leaves.size < targetLeafCount && Random.nextFloat() < 0.06f) {
                leaves = leaves + LeafParticle.randomSpawn(width = 1f)  // normalized 0-1
            }

            // Spawn wind lines — 2-4 max, sparse
            val targetWindCount = (pullProgress * 3).toInt().coerceAtMost(4)
            if (winds.size < targetWindCount && Random.nextFloat() < 0.02f) {
                winds = winds + WindParticle.randomSpawn()
            }

            // Update leaves
            leaves = leaves.mapNotNull { leaf ->
                val newLeaf = leaf.update(dt.toFloat() / 1000f)  // dt in seconds
                if (newLeaf.y > 1.3f || newLeaf.alpha <= 0f) null else newLeaf
            }

            // Update winds
            winds = winds.mapNotNull { wind ->
                val newWind = wind.update(dt.toFloat() / 1000f)
                if (newWind.alpha <= 0f && newWind.age > newWind.lifespan) null else newWind
            }

            // If pull released and no particles left and not refreshing,
            // the loop still runs (cheap) — fine for an idle composable.
            delay(16)  // ~60fps
        }
    }

    // nestedScroll connection — this is the CORRECT way to detect
    // pull-to-refresh without breaking the inner LazyColumn's vertical
    // scrolling. detectVerticalDragGestures steals ALL vertical drags;
    // nestedScroll only consumes what the LazyColumn didn't (i.e. overscroll
    // at the top).
    val nestedScrollConnection = remember(pullThresholdPx) {
        object : NestedScrollConnection {
            // Fires AFTER the inner LazyColumn has consumed what it can.
            // If leftover.y > 0 here, it means the LazyColumn was at the
            // top and the user pulled DOWN past its top edge → consume
            // that as pull-down for refresh.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0) {
                    // Pulling down at the top — accumulate pull distance
                    pullPx = (pullPx + available.y).coerceAtLeast(0f)
                    return available  // consume it so the list doesn't bounce
                }
                // User scrolling up (away from top) — cancel any pull
                if (available.y < 0 && pullPx > 0) {
                    pullPx = 0f
                }
                return Offset.Zero
            }

            // Fires when the user lifts their finger (fling or stop).
            // If we've crossed the threshold, trigger refresh.
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                if (pullPx >= pullThresholdPx && !isRefreshing) {
                    isRefreshing = true
                    onRefresh()
                } else {
                    // Didn't cross threshold — snap back to 0
                    pullPx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        content()

        // Particles layer on top
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Pull decay — if user released and we're past threshold,
            // gently pull back to 0 so leaves fade out
            if (pullPx > 0 && !isRefreshing) {
                pullPx = (pullPx - size.height * 0.01f).coerceAtLeast(0f)
            }

            val w = size.width
            val h = size.height

            // Draw wind lines (BEHIND leaves)
            winds.forEach { wind ->
                drawWindLine(wind, w, h)
            }

            // Draw leaves (ON TOP of wind)
            leaves.forEach { leaf ->
                drawLeaf(leaf, w, h)
            }
        }
    }

    // When refresh callback fires, eventually reset isRefreshing
    // (caller is responsible for setting isRefreshing back to false via
    // recomposition — we can't know when refresh is done)
    // For demo purposes, auto-reset after 2.5s:
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(2500)
            isRefreshing = false
            pullPx = 0f
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PARTICLE DATA CLASSES
// ════════════════════════════════════════════════════════════════════

private data class LeafParticle(
    val x: Float,              // normalized 0..1 (relative to canvas width)
    val y: Float,              // normalized 0..1 (0 = top, 1+ = below bottom)
    val rotation: Float,        // degrees
    val rotationSpeed: Float,   // degrees per second
    val swayPhase: Float,       // sine wave phase offset
    val swayAmplitude: Float,   // normalized x-amplitude (0.01-0.04)
    val swayFreq: Float,        // Hz (0.3-0.7 — slow sway)
    val fallSpeed: Float,       // normalized y per second (0.08-0.16)
    val size: Float,            // dp
    val colorIndex: Int,        // 0-3 palette index
    val alpha: Float            // 0..1
) {
    fun update(dt: Float): LeafParticle {
        val newY = y + fallSpeed * dt
        val newRotation = rotation + rotationSpeed * dt
        // Sway is added in the draw step (we need canvas dimensions to
        // apply the sine wave to actual x position), so we don't modify x here.
        val newAlpha = when {
            y < 0.05f -> (y / 0.05f).coerceIn(0f, 1f)        // fade in at top
            y > 0.95f -> ((1.1f - y) / 0.15f).coerceIn(0f, 1f) // fade out at bottom
            else -> 1f
        }
        return copy(y = newY, rotation = newRotation, alpha = newAlpha)
    }

    companion object {
        fun randomSpawn(width: Float): LeafParticle {
            val r = Random
            return LeafParticle(
                x = r.nextFloat(),                              // random x
                y = -0.05f - r.nextFloat() * 0.1f,               // start above top
                rotation = r.nextFloat() * 360f,                // random initial rotation
                rotationSpeed = (r.nextFloat() - 0.5f) * 20f,   // ±10°/sec
                swayPhase = r.nextFloat() * Math.PI.toFloat() * 2f,
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

private data class WindParticle(
    val startX: Float,         // normalized 0..1
    val startY: Float,         // normalized 0..1
    val directionX: Float,     // unit vector x
    val directionY: Float,     // unit vector y
    val age: Float,            // seconds since spawn
    val lifespan: Float,       // seconds total (2-4s)
    val coilAmplitude: Float,  // normalized coil radius
    val coilFreq: Float,       // Hz (one full cycle in lifespan)
    val alpha: Float           // 0..1
) {
    fun update(dt: Float): WindParticle {
        val newAge = age + dt
        // Fade in for first 30%, hold for middle 40%, fade out for last 30%
        val progress = (newAge / lifespan).coerceIn(0f, 1f)
        val newAlpha = when {
            progress < 0.3f -> (progress / 0.3f).coerceIn(0f, 1f)
            progress > 0.7f -> ((1f - progress) / 0.3f).coerceIn(0f, 1f)
            else -> 1f
        }
        return copy(age = newAge, alpha = newAlpha)
    }

    companion object {
        fun randomSpawn(): WindParticle {
            val r = Random
            // Start from top-left or top area
            val fromTopLeft = r.nextBoolean()
            val startX = if (fromTopLeft) r.nextFloat() * 0.2f else r.nextFloat() * 0.4f
            val startY = if (fromTopLeft) r.nextFloat() * 0.15f else 0f
            // Random direction (mostly rightward + downward)
            val angle = r.nextFloat() * Math.PI.toFloat() * 0.6f  // 0-108° (right-downward fan)
            return WindParticle(
                startX = startX,
                startY = startY,
                directionX = cos(angle),
                directionY = sin(angle),
                age = 0f,
                lifespan = 2.5f + r.nextFloat() * 1.5f,  // 2.5-4s
                coilAmplitude = 0.04f + r.nextFloat() * 0.04f,  // 0.04-0.08
                coilFreq = 1f,  // one full coil in lifespan
                alpha = 0f
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DRAW HELPERS
// ════════════════════════════════════════════════════════════════════

// Autumn palette — Ghost of Yotei golden hour
private val LEAF_PALETTE = listOf(
    LeafColor(body = Color(0xFFF4C724), edge = Color(0xFFC8851B)),  // golden yellow
    LeafColor(body = Color(0xFFE89B2C), edge = Color(0xFFA86A1B)),  // amber
    LeafColor(body = Color(0xFFD4861C), edge = Color(0xFF8B5A1C)),  // deep gold
    LeafColor(body = Color(0xFFB85626), edge = Color(0xFF6B3416))   // red-amber
)

private data class LeafColor(val body: Color, val edge: Color)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeaf(
    leaf: LeafParticle,
    canvasW: Float,
    canvasH: Float
) {
    val density = this
    val leafSizePx = with(density) { leaf.size.dp.toPx() }
    // Apply sway (sine wave) to x position
    val sway = (sin(leaf.y * leaf.swayFreq * Math.PI.toFloat() * 2f * 4f + leaf.swayPhase) * leaf.swayAmplitude)
    val actualX = (leaf.x + sway) * canvasW
    val actualY = leaf.y * canvasH
    val palette = LEAF_PALETTE[leaf.colorIndex.coerceIn(0, LEAF_PALETTE.size - 1)]

    // Translate + rotate to draw the leaf
    rotate(degrees = leaf.rotation, pivot = Offset(actualX, actualY)) {
        // Draw leaf body — fan shape with notch
        val path = Path().apply {
            val s = leafSizePx / 24f
            val cx = actualX
            val cy = actualY
            // (Same path as GinkgoLeaf.kt — inlined here for performance
            // since we're drawing many leaves per frame.)
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
            center = Offset(actualX, actualY + 2 * (leafSizePx / 24f)),
            radius = leafSizePx * 0.6f
        )
        drawPath(path = path, brush = bodyBrush, alpha = leaf.alpha)

        // Veins — 5 radial lines
        val veinColor = Color(0xFF6B4423).copy(alpha = 0.5f * leaf.alpha)
        val veinStroke = Stroke(width = leafSizePx / 40f, cap = StrokeCap.Round)
        val stemX = actualX
        val stemY = actualY + 8 * (leafSizePx / 24f)
        val angles = listOf(-60f, -30f, 0f, 30f, 60f)
        for (angle in angles) {
            val rad = Math.toRadians(angle.toDouble())
            val endX = (stemX + 8 * (leafSizePx / 24f) * cos(rad)).toFloat()
            val endY = if (angle == 0f) stemY - 13 * (leafSizePx / 24f)
                       else (stemY - 12 * (leafSizePx / 24f) * sin(rad)).toFloat()
            drawLine(
                color = veinColor,
                start = Offset(stemX, stemY),
                end = Offset(endX, endY),
                strokeWidth = leafSizePx / 40f,
                cap = StrokeCap.Round
            )
        }

        // Stem
        drawLine(
            color = Color(0xFF6B4423).copy(alpha = 0.9f * leaf.alpha),
            start = Offset(stemX, stemY),
            end = Offset(stemX, stemY + 3 * (leafSizePx / 24f)),
            strokeWidth = leafSizePx / 20f,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWindLine(
    wind: WindParticle,
    canvasW: Float,
    canvasH: Float
) {
    val startX = wind.startX * canvasW
    val startY = wind.startY * canvasH

    // Total travel distance — wind line traverses ~70% of the canvas
    val travelDist = canvasW * 0.7f
    val progress = (wind.age / wind.lifespan).coerceIn(0f, 1f)

    // Head position (where the wind line currently ends)
    // Apply coil: head position spirals once during its lifespan.
    val coilPhase = progress * Math.PI.toFloat() * 2f * wind.coilFreq
    val coilOffsetX = sin(coilPhase) * wind.coilAmplitude * canvasW
    val coilOffsetY = cos(coilPhase) * wind.coilAmplitude * canvasH

    val headX = startX + wind.directionX * travelDist * progress + coilOffsetX
    val headY = startY + wind.directionY * travelDist * progress + coilOffsetY

    // Tail position (where the wind line started — fixed)
    // Tail trails behind the head by ~25% of the travel distance
    val tailProgress = (progress - 0.25f).coerceAtLeast(0f)
    val tailCoilPhase = tailProgress * Math.PI.toFloat() * 2f * wind.coilFreq
    val tailCoilX = sin(tailCoilPhase) * wind.coilAmplitude * canvasW
    val tailCoilY = cos(tailCoilPhase) * wind.coilAmplitude * canvasH
    val tailX = startX + wind.directionX * travelDist * tailProgress + tailCoilX
    val tailY = startY + wind.directionY * travelDist * tailProgress + tailCoilY

    // TAPERED WIND LINE — lens shape (pointed at both ends, fat in middle)
    // Constructed as a closed Path:
    //   - moveTo(tail)
    //   - quadraticTo(perpendicular offset 1, head)  ← one side of the lens
    //   - quadraticTo(perpendicular offset 2, tail) ← other side back to tail
    //   - close
    //
    // The perpendicular offset is perpendicular to the head→tail direction,
    // magnitude ~2-3dp (skinny in the middle, ends at points).

    val dx = headX - tailX
    val dy = headY - tailY
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    // Perpendicular unit vector
    val perpX = -dy / length
    val perpY = dx / length
    val widthPx = with(this) { 2.dp.toPx() }  // max width in the middle

    // Midpoint of the lens — where the perpendicular offset is max
    val midX = (headX + tailX) / 2f
    val midY = (headY + tailY) / 2f
    // Control points for the quad — pushed outward from midpoint
    val ctrl1X = midX + perpX * widthPx
    val ctrl1Y = midY + perpY * widthPx
    val ctrl2X = midX - perpX * widthPx
    val ctrl2Y = midY - perpY * widthPx

    val windPath = Path().apply {
        moveTo(tailX, tailY)
        quadraticTo(ctrl1X, ctrl1Y, headX, headY)   // one side
        quadraticTo(ctrl2X, ctrl2Y, tailX, tailY)   // other side
        close()
    }

    // White-ish color, low alpha so it reads as "wind" not "line"
    val windColor = Color.White.copy(alpha = 0.5f * wind.alpha)
    drawPath(
        path = windPath,
        color = windColor
    )
}
