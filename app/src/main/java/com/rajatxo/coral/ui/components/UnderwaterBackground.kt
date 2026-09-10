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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Underwater Background — Coral's signature ocean ambience.
 *
 * Inspired by the reference image: a deep underwater scene with volumetric
 * god rays from the surface, caustic light patterns, drifting bioluminescent
 * particles, and distant manta ray silhouettes.
 *
 * Pure Canvas-drawn procedural animation. No video file needed — this is
 * all math + draw calls. Zero APK bloat, zero battery hit from video
 * decoding. Infinite variation (never loops visibly).
 *
 * All animations driven by a single 8-second infinite transition for a
 * slow, tranquil pace. The mood is awe + serenity + dreamlike.
 *
 * Layers (drawn back-to-front):
 *   1. Vertical gradient: abyss (#050D1A) -> ocean (#0A1F3A) -> teal (#1E3A5F)
 *   2. 4 volumetric god rays from top center, slowly shifting angle
 *   3. 5 distant manta ray silhouettes gliding (atmospheric perspective)
 *   4. 8 caustic light patches (sine-wave-distorted bright patches)
 *   5. 35 bioluminescent particles drifting upward
 *
 * @param tintColor Optional color to blend into the gradient (e.g., the
 *        active album's palette color, blended with deep navy so the
 *        underwater vibe stays consistent). Default = null (pure ocean).
 */
@Composable
fun UnderwaterBackground(
    modifier: Modifier = Modifier,
    tintColor: Color? = null
) {
    // Single 8-second pulse drives everything — slow, tranquil pace
    val transition = rememberInfiniteTransition(label = "ocean")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    // Secondary slow phase for caustics (different period for variety)
    val causticPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "causticPhase"
    )

    // Stable RNG for particle/manta positions (don't regenerate per frame)
    val particles = remember {
        List(35) { i ->
            ParticleSpec(
                seed = i,
                xFraction = Random(i * 7L + 13).nextFloat(),
                startYFraction = Random(i * 11L + 29).nextFloat(),
                size = 1f + Random(i * 17L + 41).nextFloat() * 2.5f,
                driftSpeed = 0.04f + Random(i * 19L + 53).nextFloat() * 0.08f
            )
        }
    }
    val mantas = remember {
        List(5) { i ->
            MantaSpec(
                seed = i,
                yFraction = 0.15f + Random(i * 23L + 71).nextFloat() * 0.7f,
                size = 28f + Random(i * 31L + 89).nextFloat() * 24f,
                driftSpeed = 0.02f + Random(i * 37L + 97).nextFloat() * 0.04f,
                depthAlpha = 0.12f + Random(i * 41L + 101).nextFloat() * 0.08f
            )
        }
    }
    val godRays = remember {
        List(4) { i ->
            GodRaySpec(
                xOffsetFraction = -0.18f + i * 0.12f,
                angleDeg = -8f + i * 5f,
                widthFraction = 0.10f + (i % 2) * 0.04f
            )
        }
    }
    val caustics = remember {
        List(8) { i ->
            CausticSpec(
                seed = i,
                xFraction = Random(i * 61L + 113).nextFloat(),
                yFraction = 0.1f + Random(i * 67L + 127).nextFloat() * 0.5f,
                size = 30f + Random(i * 71L + 131).nextFloat() * 60f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ============================================================
        // LAYER 1: Vertical gradient (abyss -> ocean -> teal)
        // ============================================================
        val baseColors = listOf(
            Color(0xFF1E3A5F),  // top (surface, lighter teal)
            Color(0xFF0A1F3A),  // mid (ocean)
            Color(0xFF050D1A)   // bottom (abyss)
        )
        val gradientColors = if (tintColor != null) {
            // Blend tint with each gradient stop, weighting toward navy
            baseColors.map { base ->
                Color(
                    red = (base.red * 0.7f + tintColor.red * 0.3f),
                    green = (base.green * 0.7f + tintColor.green * 0.3f),
                    blue = (base.blue * 0.7f + tintColor.blue * 0.3f),
                    alpha = 1f
                )
            }
        } else baseColors

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to gradientColors[0],
                    0.5f to gradientColors[1],
                    1f to gradientColors[2]
                ),
                startY = 0f,
                endY = h
            ),
            size = size
        )

        // ============================================================
        // LAYER 2: Volumetric god rays (additive blending, from top)
        // ============================================================
        // Each ray is a thin triangle from the top center, slowly swaying.
        // Drawn with BlendMode.Screen so they brighten the bg below.
        godRays.forEachIndexed { idx, ray ->
            val sway = sin(phase.toDouble() + idx * 0.7).toFloat() * 0.02f
            val baseX = w * (0.5f + ray.xOffsetFraction + sway)
            val angleRad = Math.toRadians(ray.angleDeg.toDouble()) +
                sin(phase.toDouble() + idx * 0.5).toFloat() * 0.05
            val rayLength = h * 1.1f
            val rayWidth = w * ray.widthFraction

            // Triangle: top point at surface, widening as it goes down
            val tipX = baseX
            val tipY = -h * 0.05f
            val bottomLeftX = (tipX - rayWidth / 2f + sin(angleRad).toFloat() * rayLength).toFloat()
            val bottomRightX = (tipX + rayWidth / 2f + sin(angleRad).toFloat() * rayLength).toFloat()
            val bottomY = h * 1.05f

            val rayPath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(bottomLeftX, bottomY)
                lineTo(bottomRightX, bottomY)
                close()
            }
            // Alpha pulses slowly — gives "light through water" feel
            val rayAlpha = 0.06f + 0.04f * sin(phase.toDouble() + idx * 0.8).toFloat()
            drawPath(
                path = rayPath,
                color = Color(0xFFA8D5FF).copy(alpha = rayAlpha),
                blendMode = BlendMode.Screen
            )
        }

        // ============================================================
        // LAYER 3: Distant manta ray silhouettes (gliding)
        // ============================================================
        mantas.forEachIndexed { idx, manta ->
            // Horizontal drift, wrapping around
            val drift = (phase * manta.driftSpeed + idx * 0.4f) % 1.4f - 0.2f
            val cx = w * drift
            val cy = h * manta.yFraction
            val sizePx = manta.size
            val wingPhase = sin(phase.toDouble() * 1.5f + idx).toFloat()

            // Manta silhouette — diamond shape with curved wings
            val mantaPath = Path().apply {
                moveTo(cx, cy - sizePx * 0.3f)  // head (pointed up)
                // Right wing — curves out and down, with wing flap
                cubicTo(
                    cx + sizePx * 0.5f, cy - sizePx * 0.1f + wingPhase * 3f,
                    cx + sizePx * 0.7f, cy + sizePx * 0.2f,
                    cx + sizePx * 0.3f, cy + sizePx * 0.3f
                )
                // Tail
                cubicTo(
                    cx + sizePx * 0.1f, cy + sizePx * 0.5f,
                    cx - sizePx * 0.1f, cy + sizePx * 0.5f,
                    cx - sizePx * 0.3f, cy + sizePx * 0.3f
                )
                // Left wing — mirror
                cubicTo(
                    cx - sizePx * 0.7f, cy + sizePx * 0.2f,
                    cx - sizePx * 0.5f, cy - sizePx * 0.1f + wingPhase * 3f,
                    cx, cy - sizePx * 0.3f
                )
                close()
            }
            drawPath(
                path = mantaPath,
                color = Color(0xFF020408).copy(alpha = manta.depthAlpha)
            )
        }

        // ============================================================
        // LAYER 4: Caustic light patches (dancing web pattern)
        // ============================================================
        // Each caustic is a bright spot that distorts via sine waves.
        caustics.forEachIndexed { idx, c ->
            val driftX = sin(causticPhase.toDouble() + idx * 0.9).toFloat() * 0.05f
            val driftY = cos(causticPhase.toDouble() + idx * 1.1).toFloat() * 0.03f
            val cx = w * (c.xFraction + driftX)
            val cy = h * (c.yFraction + driftY)
            val pulseScale = 0.7f + 0.3f * sin(causticPhase.toDouble() * 1.5f + idx).toFloat()
            val radius = c.size * pulseScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE0F4FF).copy(alpha = 0.18f),
                        Color(0xFFE0F4FF).copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                center = Offset(cx, cy),
                radius = radius,
                blendMode = BlendMode.Screen
            )
        }

        // ============================================================
        // LAYER 5: Bioluminescent particles (drifting upward)
        // ============================================================
        particles.forEachIndexed { idx, p ->
            // Particle drifts upward slowly, wrapping to bottom
            val driftY = (phase * p.driftSpeed + idx * 0.07f) % 1.2f - 0.1f
            val yPx = h * (1.1f - driftY)
            val xPx = w * p.xFraction +
                sin(phase.toDouble() * 0.5f + idx).toFloat() * w * 0.015f
            // Twinkle alpha
            val twinkle = 0.3f + 0.5f * sin(phase.toDouble() * 2f + idx * 1.3f).toFloat()
            val clampedAlpha = twinkle.coerceIn(0.1f, 0.85f)

            drawCircle(
                color = Color(0xFFE0F4FF).copy(alpha = clampedAlpha),
                radius = p.size,
                center = Offset(xPx, yPx),
                blendMode = BlendMode.Screen
            )
        }
    }
}

// --- Specs (stable, remembered, not regenerated per frame) ---

private data class ParticleSpec(
    val seed: Int,
    val xFraction: Float,      // 0..1 horizontal position
    val startYFraction: Float, // 0..1 starting y
    val size: Float,            // px radius
    val driftSpeed: Float      // fraction of phase per cycle
)

private data class MantaSpec(
    val seed: Int,
    val yFraction: Float,      // vertical position 0..1
    val size: Float,            // px
    val driftSpeed: Float,
    val depthAlpha: Float       // 0..0.25 — atmospheric perspective
)

private data class GodRaySpec(
    val xOffsetFraction: Float, // -0.5..0.5 from center
    val angleDeg: Float,         // lean angle
    val widthFraction: Float     // 0..1 of screen width
)

private data class CausticSpec(
    val seed: Int,
    val xFraction: Float,
    val yFraction: Float,
    val size: Float
)
