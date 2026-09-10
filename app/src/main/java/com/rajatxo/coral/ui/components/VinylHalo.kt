package com.rajatxo.coral.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vinyl Halo — Coral's signature "Quick Picks" selector.
 *
 * Concept:
 *   A large vinyl record sits centered in the screen, idly rotating like a
 *   real turntable (12s per revolution). The center label of the vinyl is the
 *   album art of the CURRENT pick — circle-clipped, with a 400ms crossfade
 *   when the pick changes.
 *
 *   Surrounding the vinyl, in the negative space, is a halo ring of small
 *   "satellite" album art thumbnails — one per pick (up to 15). They sit at
 *   clock positions around the vinyl like planets orbiting a star. They do
 *   NOT rotate with the vinyl — they're fixed in space.
 *
 *   The ACTIVE satellite (current pick):
 *     - Scales up to 1.4x
 *     - Has a glowing white ring around it
 *     - Has a radial glow halo
 *     - Is connected to the vinyl's edge by a thin "needle line" — like a
 *       tonearm needle dropping onto the record at that pick's track position
 *
 * Interactions:
 *   - Tap a satellite → that pick becomes active. Haptic fires.
 *   - Drag on the vinyl → angle of finger relative to vinyl center maps to
 *     a pick. Crossing each pick boundary fires a haptic. (Same rotational
 *     gesture language as PlaylistWheel.)
 *   - Tap the vinyl center → plays the current pick.
 *
 * Why this design:
 *   Coral's DNA is rotational geometry (PlaylistWheel arc), bespoke
 *   typography, tactile feedback, and asymmetric compositions. Quick Picks
 *   previously used a generic HorizontalPager — totally off-brand. The
 *   Vinyl Halo is a sibling to PlaylistWheel: same rotational soul, different
 *   physical metaphor (turntable vs dial). The "needle line" connecting the
 *   active satellite to the vinyl is a signature element nobody else has.
 *
 * @param songs The picks to display (1-15 recommended)
 * @param currentPickIndex Which pick is currently active
 * @param onPickChange Called when user changes pick (drag or tap satellite)
 * @param onPlayCurrentPick Called when user taps the vinyl center
 */
@Composable
fun VinylHalo(
    songs: List<Song>,
    currentPickIndex: Int,
    onPickChange: (Int) -> Unit,
    onPlayCurrentPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val totalPicks = songs.size
    val safeCurrentIndex = currentPickIndex.coerceIn(0, totalPicks - 1)
    val currentPick = songs[safeCurrentIndex]
    val context = LocalContext.current
    val view = LocalView.current

    // --- Idle rotation: vinyl spins slowly like a real turntable (12s/rev) ---
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // Halo is square — fits in narrow space, fills available height
        val haloSizePx = minOf(maxWidthPx, maxHeightPx)
        val haloSizeDp = with(density) { haloSizePx.toDp() }

        val satelliteSizeDp = 30.dp
        val satelliteSizePx = with(density) { satelliteSizeDp.toPx() }
        val haloPaddingPx = with(density) { 6.dp.toPx() }

        // Vinyl is smaller than halo to leave room for satellites
        val vinylSizePx = haloSizePx - 2 * (satelliteSizePx + haloPaddingPx)
        val vinylSizeDp = with(density) { vinylSizePx.toDp() }

        // Ring radius = distance from halo center to satellite CENTER
        val ringRadiusPx = (haloSizePx - satelliteSizePx) / 2f
        val centerPx = haloSizePx / 2f

        // Halo box centered in available space
        Box(
            modifier = Modifier
                .size(haloSizeDp)
                .align(Alignment.Center)
        ) {
            // ============================================================
            // LAYER 1: Vinyl disc (rotates idly, 12s/rev)
            // ============================================================
            Box(
                modifier = Modifier
                    .size(vinylSizeDp)
                    .align(Alignment.Center)
                    .rotate(rotationDegrees)
                    .pointerInput(totalPicks, safeCurrentIndex) {
                        // Drag-to-rotate: finger angle relative to vinyl center
                        // maps directly to a pick. Crossing each pick boundary
                        // fires a haptic (same language as PlaylistWheel).
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val center = Offset(
                                    size.width / 2f,
                                    size.height / 2f
                                )
                                val angle = atan2(
                                    change.position.y - center.y,
                                    change.position.x - center.x
                                )
                                // atan2 returns: 0 at 3 o'clock, π/2 at 6,
                                // π at 9, -π/2 at 12.
                                // We want "from 12 o'clock, clockwise":
                                // shift by +π/2 so 12 o'clock = 0.
                                var angleFromTopCW = angle + (Math.PI / 2).toFloat()
                                // Normalize to [0, 2π)
                                val twoPi = (2 * Math.PI).toFloat()
                                angleFromTopCW %= twoPi
                                if (angleFromTopCW < 0f) angleFromTopCW += twoPi

                                val anglePerPick = twoPi / totalPicks
                                val pickFromAngle =
                                    (angleFromTopCW / anglePerPick).toInt() % totalPicks

                                if (pickFromAngle != safeCurrentIndex) {
                                    onPickChange(pickFromAngle)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayCurrentPick
                    )
            ) {
                VinylDisc(
                    albumArtUri = currentPick.albumArtUri,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ============================================================
            // LAYER 2: Needle line + active satellite glow (does NOT rotate)
            // ============================================================
            val activeAngle =
                (2.0 * Math.PI * safeCurrentIndex / totalPicks).toFloat() -
                    (Math.PI / 2).toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val satelliteX = centerPx + ringRadiusPx * cos(activeAngle)
                val satelliteY = centerPx + ringRadiusPx * sin(activeAngle)
                val vinylEdgeX = centerPx + (vinylSizePx / 2f) * cos(activeAngle)
                val vinylEdgeY = centerPx + (vinylSizePx / 2f) * sin(activeAngle)

                // Radial glow halo around active satellite
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(satelliteX, satelliteY),
                        radius = satelliteSizePx * 1.6f
                    ),
                    center = Offset(satelliteX, satelliteY),
                    radius = satelliteSizePx * 1.6f
                )

                // Needle line: from active satellite toward vinyl edge
                // (looks like a tonearm needle dropping onto the record)
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(satelliteX, satelliteY),
                    end = Offset(vinylEdgeX, vinylEdgeY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Tiny needle dot where the line meets the vinyl edge
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 2.dp.toPx(),
                    center = Offset(vinylEdgeX, vinylEdgeY)
                )
            }

            // ============================================================
            // LAYER 3: Satellites around the ring (positioned, NOT rotating)
            // ============================================================
            songs.forEachIndexed { i, song ->
                val angle =
                    (2.0 * Math.PI * i / totalPicks).toFloat() - (Math.PI / 2).toFloat()
                val satCenterX = centerPx + ringRadiusPx * cos(angle)
                val satCenterY = centerPx + ringRadiusPx * sin(angle)
                val satTopLeftX = satCenterX - satelliteSizePx / 2f
                val satTopLeftY = satCenterY - satelliteSizePx / 2f
                val satTopLeftXDp = with(density) { satTopLeftX.toDp() }
                val satTopLeftYDp = with(density) { satTopLeftY.toDp() }

                val isActive = i == safeCurrentIndex
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.4f else 1f,
                    animationSpec = tween(durationMillis = 250),
                    label = "sat_scale_$i"
                )
                val ringAlpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "sat_ring_$i"
                )

                Box(
                    modifier = Modifier
                        .offset(x = satTopLeftXDp, y = satTopLeftYDp)
                        .size(satelliteSizeDp)
                        .scale(scale)
                ) {
                    // Album art thumbnail (with crossfade)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (i != safeCurrentIndex) {
                                        onPickChange(i)
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                    }
                                }
                            )
                    ) {
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
                            // Placeholder: dark disc with a music note feel
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1A1A1A))
                            )
                        }
                    }

                    // Active satellite: glowing white ring border
                    if (ringAlpha > 0.01f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White.copy(alpha = ringAlpha),
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
 * The vinyl disc itself — drawn on Canvas with concentric grooves, radial
 * sheen, and a specular highlight arc. The album art is rendered at the
 * center as the "label" (55% of disc diameter, circle-clipped).
 *
 * The disc rotates as a unit (grooves + label + spindle). The rotation is
 * applied by the parent via Modifier.rotate().
 */
@Composable
private fun VinylDisc(
    albumArtUri: android.net.Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // --- Outer disc body (near-pure black) ---
            drawCircle(
                color = Color(0xFF050505),
                radius = radius,
                center = center
            )

            // --- Concentric grooves (30 rings, varying alpha for depth) ---
            val numGrooves = 30
            for (i in 1..numGrooves) {
                val grooveRadius = radius * (i.toFloat() / numGrooves) * 0.93f
                val alpha = if (i % 5 == 0) 0.06f else 0.02f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = grooveRadius,
                    center = center,
                    style = Stroke(width = 0.6.dp.toPx())
                )
            }

            // --- Radial sheen (top-left highlight, simulating overhead light) ---
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                    radius = radius * 0.7f
                ),
                center = center,
                radius = radius
            )

            // --- Specular highlight arc (top-left, like light catching the edge) ---
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 200f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                size = androidx.compose.ui.geometry.Size(
                    radius * 1.84f,
                    radius * 1.84f
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // --- Center label backdrop (slightly larger than album art for halo) ---
            val labelRadius = radius * 0.32f
            drawCircle(
                color = Color.Black,
                radius = labelRadius + 1.dp.toPx(),
                center = center
            )
        }

        // --- Album art at center (the "label" of the vinyl) ---
        // 55% of disc diameter, circle-clipped, with 400ms crossfade on URI change.
        Box(
            modifier = Modifier
                .fillMaxSize(0.55f)
                .align(Alignment.Center)
                .clip(CircleShape)
        ) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(albumArtUri)
                        .crossfade(400)
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

        // --- Center spindle hole (tiny dark dot at exact center) ---
        Box(
            modifier = Modifier
                .size(7.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color(0xFF333333))
        )
        // Spindle highlight (even tinier lighter dot, gives depth)
        Box(
            modifier = Modifier
                .size(3.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color(0xFF666666))
        )
    }
}
