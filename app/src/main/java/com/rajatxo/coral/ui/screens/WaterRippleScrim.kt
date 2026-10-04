package com.rajatxo.coral.ui.screens

import android.os.Build
import android.graphics.RuntimeShader
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
import androidx.compose.ui.graphics.ShaderBrush
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
 *   - A soft central "dimple" highlight at the tap point (very dim)
 *   - Dark scrim fades out gently as the ring expands
 *
 * The ripple is LOCALIZED — max radius is ~220dp from the tap point,
 * NOT the full screen. Like a real drop hitting a big pond: only a
 * small area around the impact is disturbed.
 *
 * GPU path (Android 13+):
 *   Uses AGSL (Android Graphics Shading Language) — runs as a real GPU
 *   shader via RuntimeShader. Math is true Gaussian-enveloped sine wave.
 *   Smooth, no jank.
 *
 * CPU fallback (Android 12 and below):
 *   Canvas-drawn radial gradient annulus with the same visual structure.
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // ★ AGSL GPU shader path — Android 13+
                WaterRippleShaderCanvas(
                    tapPoint = point,
                    progress = p,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // ★ Canvas fallback path — Android 12 and below
                WaterRippleCanvasFallback(
                    tapPoint = point,
                    progress = p,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// AGSL GPU SHADER — Android 13+
// ════════════════════════════════════════════════════════════════════

/**
 * AGSL shader source for the water droplet ripple.
 *
 * Uniforms:
 *   u_center     — tap point in pixels (float2)
 *   u_progress   — animation progress 0..1 (float)
 *   u_maxRadius  — max ripple radius in pixels (float)
 *
 * Math:
 *   - Main ring: Gaussian envelope around the expanding wave front.
 *     A ring at radius = u_maxRadius * u_progress, soft Gaussian width.
 *   - Dark trough: A black Gaussian just inside the crest — gives 3D
 *     depth (wave has height, not a flat circle).
 *   - Echo ring: A delayed, dimmer Gaussian ring following the main.
 *   - Central dimple: A small bright Gaussian at the tap point that
 *     fades 3.5x faster than the ring — like a drop impact.
 *
 * Output is white where there's highlight, transparent elsewhere.
 */
private const val WATER_RIPPLE_AGSL = """
uniform float2 u_center;
uniform float  u_progress;
uniform float  u_maxRadius;

half4 main(float2 fragCoord) {
    float dist = distance(fragCoord, u_center);

    // Main ring radius expands from 0 → u_maxRadius
    float waveR = u_maxRadius * u_progress;

    // Gaussian envelope around the wave front — soft ring
    float diff = dist - waveR;
    float envelope = exp(-diff * diff * 0.0006);

    // Fade as the ring expands outward
    float fade = 1.0 - u_progress;

    // White crest intensity (the wave peak)
    float crest = envelope * fade * 0.7;

    // Dark trough just INSIDE the crest — gives 3D depth (the dip
    // behind the wave front)
    float troughDiff = (waveR - 8.0) - dist;
    float troughEnvelope = exp(-troughDiff * troughDiff * 0.0012);
    float trough = troughEnvelope * fade * 0.3;

    // Echo ring — delayed, dimmer, follows the main ring
    float echoR = u_maxRadius * max(0.0, u_progress - 0.18) * 1.22;
    float echoDiff = dist - echoR;
    float echoEnvelope = exp(-echoDiff * echoDiff * 0.0015);
    float echo = echoEnvelope * fade * 0.2;

    // Central dimple — small bright spot at the tap point that fades
    // quickly. Simulates the impact crater a drop makes when it hits.
    float dimpleR = u_maxRadius * 0.06;
    float dimpleEnvelope = exp(-dist * dist / (dimpleR * dimpleR * 1.5));
    float dimpleFade = max(0.0, 1.0 - u_progress * 3.5);
    float dimple = dimpleEnvelope * dimpleFade * 0.4;

    // Total brightness = white highlights (crest + echo + dimple)
    // minus the dark trough (subtract a bit from the alpha)
    float brightness = crest + echo + dimple - trough * 0.3;
    brightness = max(0.0, brightness);

    // Output white color with the brightness as alpha
    return half4(half3(1.0), half(brightness));
}
"""

/**
 * Compose wrapper that runs the AGSL shader on Android 13+.
 * Draws the ripple via ShaderBrush — fully GPU-accelerated.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun WaterRippleShaderCanvas(
    tapPoint: Offset,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val shader = remember { RuntimeShader(WATER_RIPPLE_AGSL) }

    // ★ LOCALIZED ripple — max radius is ~220dp from the tap point,
    //   NOT the full screen. Like a real drop on a big pond: only the
    //   area near the impact is disturbed.
    val maxR = with(density) { 220.dp.toPx() }

    shader.setFloatUniform("u_center", tapPoint.x, tapPoint.y)
    shader.setFloatUniform("u_progress", progress)
    shader.setFloatUniform("u_maxRadius", maxR)

    val brush = remember(shader) { ShaderBrush(shader) }

    Box(modifier = modifier.background(brush))
}

// ════════════════════════════════════════════════════════════════════
// CANVAS FALLBACK — Android 12 and below (no AGSL support)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun WaterRippleCanvasFallback(
    tapPoint: Offset,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val point = tapPoint
    val p = progress

    // Same localized radius as the shader path — 220dp.
    val maxR = with(density) { 220.dp.toPx() }

    Canvas(modifier = modifier) {
        // ─── Central dimple (subtle highlight at tap point) ───────
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
