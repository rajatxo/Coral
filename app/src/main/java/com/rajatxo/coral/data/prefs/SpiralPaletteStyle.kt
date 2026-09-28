package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpiralPaletteStyle — controls how the Spiral player's background
 * gradient colors are extracted from album art.
 *
 * 4 styles, each with a different approach to picking colors:
 *
 * VIBRANT — Maximum saturation boost (2.5x). Picks the most vibrant
 *   colors (vibrantSwatch, lightVibrant) and cranks their saturation.
 *   Result: very colorful, punchy, Apple Music-like. Best for albums
 *   with strong color palettes.
 *
 * DOMINANT — Uses the dominant swatch (the color that covers the most
 *   pixels in the image) with a mild saturation boost (1.5x). Result:
 *   natural, accurate to the album art, not oversaturated.
 *
 * MUTED — Uses muted/darkMuted swatches with a moderate boost (1.8x).
 *   Result: softer, more subtle, darker tones. Good for minimal/monochrome
 *   albums where vibrant colors don't exist.
 *
   DEEP — Uses darkVibrant + darkMuted with a saturation boost (2.0x)
 *   but a brightness REDUCTION (0.85x). Result: deep, rich, dark colors
 *   with a moody feel. Good for night listening.
 */
object SpiralPaletteStyle {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_STYLE = "spiral_palette_style_v1"

    enum class PaletteStyle(val displayName: String, val description: String) {
        VIBRANT("Vibrant", "Maximum saturation — punchy, colorful, Apple Music style"),
        DOMINANT("Dominant", "Most common color — natural, accurate to album art"),
        MUTED("Muted", "Soft, subtle tones — good for minimal albums"),
        DEEP("Deep", "Dark, rich, moody — good for night listening")
    }

    private lateinit var prefs: android.content.SharedPreferences

    private val _style = MutableStateFlow(PaletteStyle.VIBRANT)
    val style: StateFlow<PaletteStyle> = _style.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_STYLE, null)
        _style.value = try {
            saved?.let { PaletteStyle.valueOf(it) } ?: PaletteStyle.VIBRANT
        } catch (_: Exception) {
            PaletteStyle.VIBRANT
        }
    }

    fun setStyle(style: PaletteStyle) {
        _style.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }
}
