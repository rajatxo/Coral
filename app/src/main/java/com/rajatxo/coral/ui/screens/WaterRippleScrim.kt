package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * ★ WaterRippleScrim — dark scrim with a SUBTLE water-droplet dismiss animation.
 *
 * Like a single drop falling from a leaf into a still pond:
 *   - ONE soft ring expands slowly from the tap point
 *   - A tiny secondary "echo" ring follows it (barely visible)
 *   - A soft central "impact" highlight at the tap point (very dim)
 *   - Dark scrim fades out gently as the ring expands
 *
 * NOT a noisy multi-ring splash — just one quiet drop.
 *
 * The card itself (rendered as a sibling ON TOP of this scrim) consumes
 * taps inside its bounds — so tapping the card doesn't trigger the ripple.
 * Only taps on the dark scrim area do.
 */
@Composable
fun WaterRippleScrim(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black.copy(alpha = 0.3f)
) {
    var tapPoint by remember { mutableStateOf<Offset?>(null) }
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current

    // Trigger the ripple + scrim fade animation when user taps.
    // Animation runs 0 → 1 over 1100ms (slow, gentle), then calls onDismiss.
    LaunchedEffect(tapPoint) {
        if (tapPoint != null) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
            )
            onDismiss()
            tapPoint = null
        }
    }

    val p = progress.value
    // Scrim alpha fades from full → 0 as ripple expands. Gentle curve.
    val scrimAlpha = (1f - p).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor.copy(alpha = scrimColor.alpha * scrimAlpha))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Only start a new ripple if no animation is in progress.
                    if (tapPoint == null) tapPoint = offset
                }
            }
    ) {
        tapPoint?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxR = max(size.width, size.height) * 0.85f

                // ─── Central impact highlight (very subtle) ───────────────
                // Soft white glow at the tap point — small, fades quickly.
                // Like the dimple a drop makes when it hits water.
                val impactP = (p * 3f).coerceIn(0f, 1f)
                if (impactP < 1f) {
                    val impactR = with(density) { 18.dp.toPx() } * (1 + impactP * 1.5f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = (1f - impactP) * 0.35f),
                                Color.White.copy(alpha = (1f - impactP) * 0.12f),
                                Color.Transparent
                            ),
                            center = point,
                            radius = impactR
                        ),
                        center = point,
                        radius = impactR
                    )
                }

                // ─── Main ring — ONE soft expanding wave ──────────────────
                // Drawn as a soft radial-gradient annulus with:
                //   - A faint dark trough just inside the crest (depth)
                //   - A soft white crest at the expanding radius
                //   - A dimmer trail just outside
                //   - Transparent edges
                val ringP = p
                if (ringP > 0f && ringP < 1f) {
                    val ringR = maxR * ringP
                    val baseAlpha = (1f - ringP) * 0.45f
                    if (baseAlpha > 0.01f && ringR > 0f) {
                        val ringWidthPx = with(density) { 16.dp.toPx() }
                        val outerR = ringR + ringWidthPx * 2f
                        val peakPos = (ringR / outerR).coerceIn(0f, 1f)
                        val crestW = 0.025f
                        val shadowW = 0.05f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    (peakPos - shadowW - crestW).coerceIn(0f, 1f) to Color.Transparent,
                                    (peakPos - crestW).coerceIn(0f, 1f) to
                                        Color.Black.copy(alpha = baseAlpha * 0.25f),
                                    peakPos to Color.White.copy(alpha = baseAlpha * 0.75f),
                                    (peakPos + crestW).coerceIn(0f, 1f) to
                                        Color.White.copy(alpha = baseAlpha * 0.2f),
                                    (peakPos + crestW + shadowW).coerceIn(0f, 1f) to Color.Transparent,
                                    1f to Color.Transparent
                                ),
                                center = point,
                                radius = outerR
                            ),
                            center = point,
                            radius = outerR
                        )
                    }
                }

                // ─── Echo ring — ONE secondary ring, very dim ──────────────
                // Follows the main ring with a small delay. Barely visible —
                // gives the impression of a single drop with a quiet echo,
                // not a multi-ring splash.
                val echoDelay = 0.18f
                val echoP = ((p - echoDelay) / (1f - echoDelay)).coerceIn(0f, 1f)
                if (echoP > 0f && echoP < 1f) {
                    val echoR = maxR * echoP
                    val echoAlpha = (1f - echoP) * 0.18f
                    if (echoAlpha > 0.01f && echoR > 0f) {
                        val echoWidthPx = with(density) { 10.dp.toPx() }
                        val outerR = echoR + echoWidthPx * 2f
                        val peakPos = (echoR / outerR).coerceIn(0f, 1f)
                        val crestW = 0.022f
                        val shadowW = 0.045f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    (peakPos - shadowW - crestW).coerceIn(0f, 1f) to Color.Transparent,
                                    (peakPos - crestW).coerceIn(0f, 1f) to
                                        Color.Black.copy(alpha = echoAlpha * 0.2f),
                                    peakPos to Color.White.copy(alpha = echoAlpha),
                                    (peakPos + crestW).coerceIn(0f, 1f) to
                                        Color.White.copy(alpha = echoAlpha * 0.15f),
                                    (peakPos + crestW + shadowW).coerceIn(0f, 1f) to Color.Transparent,
                                    1f to Color.Transparent
                                ),
                                center = point,
                                radius = outerR
                            ),
                            center = point,
                            radius = outerR
                        )
                    }
                }
            }
        }
    }
}
