package com.rajatxo.coral.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
 * The blur and color treatment are combined into ONE render pass via a
 * ColorFilter ColorMatrix applied to the blurred album cover. There is no
 * separate overlay layer — this eliminates the "step" / delay that occurred
 * when Layer 1 (blur) and Layer 2 (mesh overlay) loaded/animated at
 * different times.
 *
 * The ColorMatrix combines three transforms:
 *   1. Hue rotation (shifts all colors by style.hueShiftDeg degrees)
 *   2. Saturation boost (scales color intensity by style.saturationBoost)
 *   3. Brightness adjustment (scales RGB by style.brightnessFactor)
 *
 * For MONOCHROME, saturation is forced to 0 (grayscale).
 *
 * The result: SUNSET = warm hue-shifted blur, OCEAN = cool blur, etc. —
 * all in a single layer with no overlay timing issues.
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

    // ★ Build a single ColorFilter that combines hue + saturation + brightness.
    // This is applied directly to the blurred AsyncImage — no separate layer.
    val colorFilter = remember(style) {
        buildColorFilter(style)
    }

    if (albumArtUri != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val request = remember(albumArtUri) {
            ImageRequest.Builder(context)
                .data(albumArtUri)
                .crossfade(300)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier
                )
                .then(
                    if (alpha < 1f) Modifier.then(
                        Modifier.graphicsLayer { this.alpha = alpha }
                    ) else Modifier
                )
        )
    }
}

/**
 * Builds a combined ColorFilter from the style's saturation, brightness,
 * and hue parameters. Returns null if no transformation is needed (BLUR style
 * with default params).
 *
 * The three transforms are combined into a single 4x5 ColorMatrix:
 *   1. Hue rotation (around the grayscale axis)
 *   2. Saturation boost (scales RGB toward/away from grayscale)
 *   3. Brightness adjustment (scales RGB values)
 */
private fun buildColorFilter(style: SpiralPaletteStyle.PaletteStyle): ColorFilter? {
    val sat = style.saturationBoost
    val bri = style.brightnessFactor
    val hue = style.hueShiftDeg

    // BLUR style — no color treatment, just the raw blurred image
    if (sat == 1f && bri == 1f && hue == 0f) return null

    // Build the combined matrix by multiplying: hue × saturation × brightness
    val hueMatrix = hueRotationMatrix(hue)
    val satMatrix = saturationMatrix(sat)
    val briMatrix = brightnessMatrix(bri)

    // Multiply: result = bri × sat × hue (applied right-to-left)
    val combined = multiplyColorMatrices(briMatrix, multiplyColorMatrices(satMatrix, hueMatrix))

    return ColorFilter.colorMatrix(ColorMatrix(combined))
}

/**
 * Saturation boost matrix.
 *
 * s=0: grayscale (all channels = luminance)
 * s=1: identity (no change)
 * s>1: boost saturation (diagonal > 1, off-diagonal < 0)
 */
private fun saturationMatrix(s: Float): FloatArray {
    val r = 0.3086f; val g = 0.6094f; val b = 0.0820f
    return floatArrayOf(
        r + (1 - r) * s, g * (1 - s), b * (1 - s), 0f, 0f,
        r * (1 - s), g + (1 - g) * s, b * (1 - s), 0f, 0f,
        r * (1 - s), g * (1 - s), b + (1 - b) * s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/**
 * Brightness adjustment matrix.
 *
 * b=1: identity
 * b<1: darker
 * b>1: brighter
 */
private fun brightnessMatrix(b: Float): FloatArray {
    return floatArrayOf(
        b, 0f, 0f, 0f, 0f,
        0f, b, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/**
 * Hue rotation matrix (rotation around the (1,1,1) grayscale axis).
 *
 * degrees=0: identity
 * degrees>0: rotate hue forward (warm shift)
 * degrees<0: rotate hue backward (cool shift)
 */
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

/**
 * Identity color matrix (no transformation).
 */
private fun identityMatrix(): FloatArray {
    return floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/**
 * Multiplies two 4x5 color matrices (treating them as 4x4 augmented matrices).
 *
 * Result = A × B (A applied after B)
 */
private fun multiplyColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val result = FloatArray(20)
    // Treat as 4x5 matrices (4 rows, 5 columns, but only 4x4 matters for mult)
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
