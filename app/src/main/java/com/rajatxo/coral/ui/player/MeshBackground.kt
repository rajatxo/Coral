package com.rajatxo.coral.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.data.prefs.SpiralPaletteStyle
import com.rajatxo.coral.util.CoralPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MeshBackground — renders the Spiral player's background as a SINGLE LAYER.
 *
 * Merges two adjustments into one render pass:
 *   1. ColorMatrix (hue rotation + saturation + brightness) applied to the
 *      blurred album cover via ColorFilter — bakes the color treatment
 *      directly into the image pixels.
 *   2. Mesh overlay shapes (radial blobs, dual-tone gradient, tritone bands)
 *      drawn ON TOP via drawWithContent inside the SAME graphicsLayer.
 *
 * Because both adjustments are in the same composable + same graphicsLayer,
 * they composite as ONE unit — no desync, no timing step. The blur, color
 * treatment, and mesh shapes all load/animate together.
 *
 * Offscreen compositing is used so BlendMode draws correctly.
 */
@Composable
fun MeshBackground(
    albumArtUri: android.net.Uri?,
    palette: CoralPalette,
    style: SpiralPaletteStyle.PaletteStyle,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val blurDp = style.blurRadiusDp.dp

    // ★ ColorMatrix: combines hue + saturation + brightness into one filter
    // applied directly to the blurred image pixels.
    val colorFilter = remember(style) {
        buildColorFilter(style)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                // ★ Offscreen: required for BlendMode + drawWithContent to
                //   composite correctly as a single layer.
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        if (albumArtUri != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(albumArtUri) {
                ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(100)  // 100ms — ultra fast, matches sharp art crossfade
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier
                    )
            )
        }

        // ★ Mesh overlay — drawn ON TOP of the blurred image, but INSIDE
        //   the same graphicsLayer. This is the key: it's not a separate
        //   Box/composable that could desync — it's part of the same draw
        //   pass. Uses SourceOver (normal alpha blending) by default.
        //   The mesh shapes use semi-transparent palette colors so the
        //   blurred image shows through underneath.
        //
        //   drawWithContent: drawContent() draws the AsyncImage first,
        //   then the mesh shapes are drawn on top — all in one layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawMeshOverlay(style, palette, size.width, size.height)
                }
        )
    }
}

/**
 * Builds a combined ColorFilter from the style's saturation, brightness,
 * and hue parameters. Returns null if no transformation is needed.
 */
private fun buildColorFilter(style: SpiralPaletteStyle.PaletteStyle): ColorFilter? {
    val sat = style.saturationBoost
    val bri = style.brightnessFactor
    val hue = style.hueShiftDeg

    if (sat == 1f && bri == 1f && hue == 0f) return null

    val hueMatrix = hueRotationMatrix(hue)
    val satMatrix = saturationMatrix(sat)
    val briMatrix = brightnessMatrix(bri)

    val combined = multiplyColorMatrices(briMatrix, multiplyColorMatrices(satMatrix, hueMatrix))
    return ColorFilter.colorMatrix(ColorMatrix(combined))
}

private fun saturationMatrix(s: Float): FloatArray {
    val r = 0.3086f; val g = 0.6094f; val b = 0.0820f
    return floatArrayOf(
        r + (1 - r) * s, g * (1 - s), b * (1 - s), 0f, 0f,
        r * (1 - s), g + (1 - g) * s, b * (1 - s), 0f, 0f,
        r * (1 - s), g * (1 - s), b + (1 - b) * s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun brightnessMatrix(b: Float): FloatArray {
    return floatArrayOf(
        b, 0f, 0f, 0f, 0f,
        0f, b, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun hueRotationMatrix(degrees: Float): FloatArray {
    if (degrees == 0f) return identityMatrix()
    val rad = Math.toRadians(degrees.toDouble())
    val cos = cos(rad.toFloat())
    val sin = sin(rad.toFloat())
    val inv3 = (1f - cos) / 3f
    val sinSqrt3 = sin / sqrt(3f)
    return floatArrayOf(
        cos + inv3, inv3 - sinSqrt3, inv3 + sinSqrt3, 0f, 0f,
        inv3 + sinSqrt3, cos + inv3, inv3 - sinSqrt3, 0f, 0f,
        inv3 - sinSqrt3, inv3 + sinSqrt3, cos + inv3, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun identityMatrix(): FloatArray {
    return floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun multiplyColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += a[row * 5 + k] * b[k * 5 + col]
            }
            result[row * 5 + col] = sum
        }
    }
    return result
}

// ═══════════════════════════════════════════════════════════════════
// Mesh overlay rendering — draws semi-transparent shapes on top of the
// blurred image. Uses the palette colors extracted from the album art.
// ═══════════════════════════════════════════════════════════════════

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeshOverlay(
    style: SpiralPaletteStyle.PaletteStyle,
    palette: CoralPalette,
    width: Float,
    height: Float
) {
    when (style.meshType) {
        SpiralPaletteStyle.MeshType.NONE -> {
            // No overlay — just the blurred + color-filtered image
        }

        SpiralPaletteStyle.MeshType.RADIAL_BLOBS -> {
            val blobRadius = width * 0.6f
            drawRadialBlob(palette.primary, Offset(width * 0.15f, height * 0.15f), blobRadius)
            drawRadialBlob(palette.secondary, Offset(width * 0.85f, height * 0.2f), blobRadius)
            drawRadialBlob(palette.tertiary, Offset(width * 0.2f, height * 0.85f), blobRadius)
            drawRadialBlob(palette.accent, Offset(width * 0.8f, height * 0.8f), blobRadius)
        }

        SpiralPaletteStyle.MeshType.SOLID_WASH -> {
            drawRect(color = palette.primary.copy(alpha = 0.5f))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.3f),
                        Color.Transparent,
                        palette.tertiary.copy(alpha = 0.4f)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.VERTICAL_GRADIENT -> {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to palette.primary.copy(alpha = 0.85f),
                        0.35f to palette.secondary.copy(alpha = 0.7f),
                        0.65f to palette.tertiary.copy(alpha = 0.8f),
                        1.0f to Color(0xFF05050A)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.DUAL_TONE -> {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.7f),
                        palette.tertiary.copy(alpha = 0.7f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
            )
        }

        SpiralPaletteStyle.MeshType.TRITONE -> {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to palette.primary.copy(alpha = 0.75f),
                        0.33f to palette.primary.copy(alpha = 0.4f),
                        0.5f to palette.secondary.copy(alpha = 0.6f),
                        0.66f to palette.tertiary.copy(alpha = 0.4f),
                        1.0f to palette.tertiary.copy(alpha = 0.8f)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.GRAYSCALE_ACCENT -> {
            // Image is already grayscale via ColorFilter. Add accent blob.
            drawRadialBlob(
                palette.accent.copy(alpha = 0.4f),
                Offset(width * 0.5f, height * 0.4f),
                width * 0.5f
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0xFF05050A).copy(alpha = 0.6f)),
                    center = Offset(width * 0.5f, height * 0.5f),
                    radius = width * 0.8f
                )
            )
        }
    }

    // Subtle bottom dark gradient for control readability
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.7f to Color.Transparent,
                1.0f to Color(0xFF05050A).copy(alpha = 0.3f)
            )
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadialBlob(
    color: Color,
    center: Offset,
    radius: Float
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.6f),
                color.copy(alpha = 0.0f)
            ),
            center = center,
            radius = radius
        )
    )
}
