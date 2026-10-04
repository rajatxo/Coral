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
 * ★ WaterRippleScrim — dark scrim with a real water-ripple dismiss animation.
 *
 * When user taps anywhere on the scrim:
 *   1. A bright "splash" appears at the tap point (radial gradient highlight
 *      that expands quickly and fades — like a drop hitting water).
 *   2. Multiple concentric water rings expand outward from the tap point
 *      with staggered delays. Each ring is drawn as a soft radial-gradient
 *      annulus with:
 *        - Dark "trough" shadow just inside the crest (gives 3D depth —
 *          looks like a real wave, not a flat circle)
 *        - Bright white "crest" at the ring radius
 *        - Dimmer "trail" just outside the crest
 *        - Transparent edges (soft fade, no hard line)
 *   3. The dark scrim itself fades out (alpha 0.3 → 0) as the rings expand.
 *   4. Once the animation completes (~900ms), `onDismiss` is called.
 *
 * The card itself (rendered as a sibling ON TOP of this scrim) consumes
 * taps inside its bounds — so tapping the card doesn't trigger the ripple.
 * Only taps on the dark scrim area do.
 *
 * Real water feel comes from:
 *   - Radial gradients (not solid stroke circles) → soft, depth-y edges
 *   - Multiple staggered rings → looks like wave fronts moving outward
 *   - Trough shadow inside each crest → 3D depth (wave has height)
 *   - Central splash → water-drop impact highlight
 *   - Scrim fades as rings expand → "water absorbs the dark"
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
    // Animation runs 0 → 1 over 900ms, then calls onDismiss.
    LaunchedEffect(tapPoint) {
        if (tapPoint != null) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
            onDismiss()
            tapPoint = null
        }
    }

    val p = progress.value
    // Scrim alpha fades from full → 0 as ripple expands.
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
                val maxR = max(size.width, size.height) * 1.4f

                // ─── Central splash — bright white highlight at the tap
                //   point that expands quickly (5x faster than the rings)
                //   and fades. Simulates a water drop hitting the surface.
                val splashP = (p * 5f).coerceIn(0f, 1f)
                if (splashP < 1f) {
                    val splashR = with(density) { 25.dp.toPx() } * (1 + splashP * 2.5f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = (1f - splashP) * 0.95f),
                                Color.White.copy(alpha = (1f - splashP) * 0.5f),
                                Color.White.copy(alpha = (1f - splashP) * 0.1f),
                                Color.Transparent
                            ),
                            center = point,
                            radius = splashR
                        ),
                        center = point,
                        radius = splashR
                    )
                }

                // ─── Multiple staggered water rings
                // Each ring is delayed by `delay = i * 0.10f` so they don't
                // all start at the same time — creates a "wave train" effect.
                // Outer rings are progressively dimmer.
                //
                // Each ring's gradient has 6 stops:
                //   0.0                                  transparent
                //   peakPos - shadowW - crestW           transparent  (start of trough fade-in)
                //   peakPos - crestW                     BLACK shadow  (trough — depth!)
                //   peakPos                              WHITE crest   (wave peak)
                //   peakPos + crestW                     WHITE trail   (dimmer)
                //   peakPos + crestW + shadowW            transparent  (end of trail fade-out)
                //   1.0                                  transparent
                //
                // The black trough inside the white crest is what gives
                // the 3D depth — looks like a real wave with a crest and
                // a trough behind it, not a flat circle outline.
                val ringCount = 4
                val ringWidthPx = with(density) { 20.dp.toPx() }
                for (i in 0 until ringCount) {
                    val delay = i * 0.10f
                    val ringP = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
                    if (ringP > 0 && ringP < 1f) {
                        val ringR = maxR * ringP
                        val baseAlpha = (1f - ringP) * 0.5f
                        val ringAlpha = baseAlpha * (1f - i * 0.15f)
                        if (ringAlpha > 0.01f && ringR > 0f) {
                            val outerR = ringR + ringWidthPx * 2f
                            val peakPos = (ringR / outerR).coerceIn(0f, 1f)
                            val crestW = 0.035f
                            val shadowW = 0.06f

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        (peakPos - shadowW - crestW).coerceIn(0f, 1f) to Color.Transparent,
                                        (peakPos - crestW).coerceIn(0f, 1f) to
                                            Color.Black.copy(alpha = ringAlpha * 0.45f),
                                        peakPos to Color.White.copy(alpha = ringAlpha),
                                        (peakPos + crestW).coerceIn(0f, 1f) to
                                            Color.White.copy(alpha = ringAlpha * 0.3f),
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
}
