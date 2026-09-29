package com.rajatxo.coral.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of palette extraction. Holds the four colors Coral needs:
 *  - primary     — the dominant color of the album art (used for top of gradient)
 *  - secondary   — a darker variant (used for middle of gradient)
 *  - tertiary    — a muted dark variant (used for bottom of gradient, blends to black)
 *  - accent      — a vibrant pop color (used for the heart and shuffle icons when active)
 */
data class CoralPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color
) {
    companion object {
        /** Fallback used before the first palette loads, or when no album art is set. */
        val Default = CoralPalette(
            primary = Color(0xFF1A1A1A),
            secondary = Color(0xFF0F0F0F),
            tertiary = Color(0xFF000000),
            accent = Color(0xFFFF6B6B)
        )
    }
}

/**
 * PaletteCache — a singleton that caches the last-extracted palette by art URI.
 *
 * This solves the "solid color flash" when opening the full player from the
 * mini player: the mini player extracts the palette while the song is playing,
 * stores it here. When the full player opens, it reads the cached palette
 * INSTANTLY — no black flash while waiting for async extraction.
 */
object PaletteCache {
    private val _cached = MutableStateFlow<Pair<Uri?, CoralPalette>?>(null)
    val cached: StateFlow<Pair<Uri?, CoralPalette>?> = _cached.asStateFlow()

    /** Store a palette keyed by its art URI. */
    fun put(artUri: Uri, palette: CoralPalette) {
        _cached.value = artUri to palette
    }

    /** Get the cached palette for the given URI, or null if not cached. */
    fun get(artUri: Uri?): CoralPalette? {
        val current = _cached.value
        return if (current != null && current.first == artUri) current.second else null
    }
}

/**
 * Extracts a [CoralPalette] from the album art at [artUri].
 *
 * Strategy:
 *  1. Decode URI to a Bitmap (in sample size to keep memory low).
 *  2. Generate a Palette.
 *  3. Pick dominant, darkVibrant, darkMuted, vibrant swatches.
 *  4. Fall back gracefully if any swatch is missing.
 *
 * Returns null on any failure (caller keeps the previous palette).
 */
suspend fun extractPalette(context: Context, artUri: Uri?): CoralPalette? {
    return extractPalette(context, artUri, com.rajatxo.coral.data.prefs.SpiralPaletteStyle.PaletteStyle.BLUR)
}

/**
 * Extracts a [CoralPalette] from album art using the specified style.
 * Different styles pick different swatches and apply different saturation
 * boosts, giving the user control over the Spiral player's background look.
 */
suspend fun extractPalette(
    context: Context,
    artUri: Uri?,
    style: com.rajatxo.coral.data.prefs.SpiralPaletteStyle.PaletteStyle
): CoralPalette? {
    if (artUri == null) return null
    return withContext(Dispatchers.IO) {
        try {
            // Step 1: decode bounds only to get the original dimensions
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(artUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val imageWidth = boundsOptions.outWidth
            val imageHeight = boundsOptions.outHeight
            if (imageWidth <= 0 || imageHeight <= 0) return@withContext null

            // Step 2: compute inSampleSize so the decoded bitmap is at most 256x256
            var sampleSize = 1
            while (imageWidth / (sampleSize * 2) >= 256 && imageHeight / (sampleSize * 2) >= 256) {
                sampleSize *= 2
            }

            // Step 3: re-open the stream and decode at the reduced size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(artUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val palette2 = Palette.from(bitmap).generate()
            bitmap.recycle()
            val dominant = palette2.dominantSwatch?.rgb
            val darkVibrant = palette2.darkVibrantSwatch?.rgb ?: dominant
            val darkMuted = palette2.darkMutedSwatch?.rgb ?: darkVibrant ?: dominant
            val vibrant = palette2.vibrantSwatch?.rgb ?: palette2.lightVibrantSwatch?.rgb ?: dominant
            val lightVibrant = palette2.lightVibrantSwatch?.rgb ?: vibrant ?: dominant
            val muted = palette2.mutedSwatch?.rgb ?: darkMuted ?: dominant
            val lightMuted = palette2.lightMutedSwatch?.rgb ?: muted

            if (dominant == null) return@withContext null

            // ★ Apply per-style color transformations
            // Each style has its own saturation, brightness, and hue shift.
            val sat = style.saturationBoost
            val bri = style.brightnessFactor
            val hue = style.hueShiftDeg

            CoralPalette(
                primary = transformColor(Color(dominant), sat, bri, hue),
                secondary = transformColor(Color(lightVibrant ?: dominant), sat, bri, hue),
                tertiary = transformColor(Color(darkVibrant ?: dominant), sat * 0.8f, bri * 0.85f, hue),
                accent = transformColor(Color(vibrant ?: dominant), sat.coerceAtMost(3f), bri, hue)
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Transform a color: boost saturation, adjust brightness, and shift hue.
 * All three transforms are applied in HSV space.
 */
private fun transformColor(color: Color, satBoost: Float, brightnessFactor: Float, hueShiftDeg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    // Saturation
    hsv[1] = if (satBoost == 0f) 0f else (hsv[1] * satBoost).coerceIn(0f, 1f)
    // Brightness
    hsv[2] = (hsv[2] * brightnessFactor).coerceIn(0f, 1f)
    // Hue shift (degrees → 0-360)
    if (hueShiftDeg != 0f) {
        hsv[0] = (hsv[0] + hueShiftDeg + 360f) % 360f
    }
    val rgb = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = color.alpha
    )
}

/**
 * Compute the relative luminance of a color (per WCAG 2.1).
 *
 * Returns a value in [0, 1] where:
 *   - 0 = pure black
 *   - 1 = pure white
 *   - < 0.5 = dark color
 *   - ≥ 0.5 = light color
 *
 * Used by [adaptiveContrastColor] to decide whether to use a light or
 * dark color for text/icons based on the background they sit on.
 */
fun luminanceOf(color: Color): Float {
    // Linear sRGB → relative luminance (WCAG formula)
    val r = if (color.red <= 0.03928f) color.red / 12.92f
            else Math.pow(((color.red + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    val g = if (color.green <= 0.03928f) color.green / 12.92f
            else Math.pow(((color.green + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    val b = if (color.blue <= 0.03928f) color.blue / 12.92f
            else Math.pow(((color.blue + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Adaptive contrast color — returns a color that's readable against
 * the given [background] color.
 *
 * Use case: the player's shuffle/loop icon tints to palette.accent when
 * active. If the album art is dark (95% black), palette.accent is also
 * dark → the icon becomes invisible. This function detects that and
 * brightens the accent color so it's readable against the dark bg.
 *
 * @param color    The intended color (e.g. palette.accent).
 * @param background The background the color will sit on (e.g.
 *                  palette.tertiary or the album art's dominant color).
 * @return Either [color] (if it has good contrast) or a brightened
 *         version (boosted lightness) so it pops against [background].
 */
fun adaptiveContrastColor(color: Color, background: Color): Color {
    val colorLum = luminanceOf(color)
    val bgLum = luminanceOf(background)

    // If the colors are similar in luminance (both dark or both light),
    // the contrast is poor. Compute the delta — anything < 0.3 means
    // the contrast ratio is below ~3:1 (WCAG AA large text minimum).
    val delta = kotlin.math.abs(colorLum - bgLum)
    if (delta >= 0.3f) return color  // already good contrast

    // Poor contrast — boost the color in the OPPOSITE direction of the bg.
    // Dark bg → brighten the color; light bg → darken the color.
    return if (bgLum < 0.5f) {
        // Background is dark — brighten [color] by boosting its value (HSV)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            hsv
        )
        hsv[2] = (hsv[2] + 0.5f).coerceIn(0.5f, 1f)  // push to at least 50% lightness
        hsv[1] = (hsv[1] * 0.7f).coerceIn(0f, 1f)     // slightly desaturate for vividness
        val rgb = android.graphics.Color.HSVToColor(hsv)
        Color(
            red = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr 8) and 0xFF) / 255f,
            blue = (rgb and 0xFF) / 255f,
            alpha = color.alpha
        )
    } else {
        // Background is light — darken [color]
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            hsv
        )
        hsv[2] = (hsv[2] - 0.5f).coerceIn(0f, 0.5f)
        val rgb = android.graphics.Color.HSVToColor(hsv)
        Color(
            red = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr 8) and 0xFF) / 255f,
            blue = (rgb and 0xFF) / 255f,
            alpha = color.alpha
        )
    }
}
