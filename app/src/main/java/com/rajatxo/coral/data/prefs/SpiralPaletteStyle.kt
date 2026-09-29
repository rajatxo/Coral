package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpiralPaletteStyle — controls how the Spiral player's background
 * is rendered. 12 styles, each combining a different blur radius
 * with a different color mesh/gradient treatment.
 *
 * Each style has:
 *   - blurRadiusDp:     how much to blur the album cover (0 = sharp, 96 = heavy)
 *   - meshType:         how to render the color overlay (NONE, RADIAL_BLOBS, etc.)
 *   - saturationBoost:  multiplier for extracted palette colors (1.0 = no change)
 *   - brightnessFactor: multiplier for extracted palette colors (1.0 = no change)
 *   - hueShiftDeg:      degrees to rotate hue (0 = no shift, 30 = warm, -30 = cool)
 *
 * The 12 styles:
 *
 * 1. BLUR          — 96dp heavy blur, no color overlay (original)
 * 2. VIBRANT_MESH  — 48dp blur + 4 radial color blobs (vibrant, sat 2.5x)
 * 3. DOMINANT_WASH — sharp (0dp) + solid dominant color wash (50% alpha)
 * 4. NEON_PULSE    — 16dp blur + neon radial blobs (sat 3x, bright 1.3x)
 * 5. PASTEL_DREAM  — 64dp blur + soft vertical gradient (sat 0.6x, bright 1.2x)
 * 6. MIDNIGHT      — 96dp blur + dark color wash (brightness 0.5x)
 * 7. SUNSET        — 32dp blur + warm dual-tone (hue +20°, sat 1.8x)
 * 8. OCEAN         — 48dp blur + cool dual-tone (hue -30°, sat 1.5x)
 * 9. MONOCHROME    — 96dp blur + grayscale + single accent radial blob
 * 10. RAINBOW_MESH — 32dp blur + 4 large radial blobs (all palette colors)
 * 11. VINTAGE      — 64dp blur + tritone sepia (hue +15°, sat 0.7x, bright 0.9x)
 * 12. AURORA       — 48dp blur + green-purple radial blobs (hue shift +60°)
 */
object SpiralPaletteStyle {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_STYLE = "spiral_palette_style_v3"  // bumped v2→v3 to reset default to SUNSET

    enum class MeshType {
        NONE,               // just the blurred image, no color overlay
        RADIAL_BLOBS,       // 4 radial color blobs at corners + center
        SOLID_WASH,         // solid color overlay with alpha
        VERTICAL_GRADIENT,  // top→bottom color gradient
        DUAL_TONE,          // two-color diagonal split
        TRITONE,            // three horizontal color bands
        GRAYSCALE_ACCENT    // grayscale image + single accent blob
    }

    enum class PaletteStyle(
        val displayName: String,
        val description: String,
        val blurRadiusDp: Int,
        val meshType: MeshType,
        val saturationBoost: Float,
        val brightnessFactor: Float,
        val hueShiftDeg: Float
    ) {
        BLUR(
            displayName = "Blur",
            description = "Original — heavy 96dp blur of album cover",
            blurRadiusDp = 96,
            meshType = MeshType.NONE,
            saturationBoost = 1.0f,
            brightnessFactor = 1.0f,
            hueShiftDeg = 0f
        ),
        VIBRANT_MESH(
            displayName = "Vibrant Mesh",
            description = "48dp blur + 4 vibrant radial color blobs",
            blurRadiusDp = 48,
            meshType = MeshType.RADIAL_BLOBS,
            saturationBoost = 2.5f,
            brightnessFactor = 1.15f,
            hueShiftDeg = 0f
        ),
        DOMINANT_WASH(
            displayName = "Dominant Wash",
            description = "Sharp cover + solid dominant color wash",
            blurRadiusDp = 0,
            meshType = MeshType.SOLID_WASH,
            saturationBoost = 1.5f,
            brightnessFactor = 1.05f,
            hueShiftDeg = 0f
        ),
        NEON_PULSE(
            displayName = "Neon Pulse",
            description = "16dp blur + neon glow (3x saturation, high contrast)",
            blurRadiusDp = 16,
            meshType = MeshType.RADIAL_BLOBS,
            saturationBoost = 3.0f,
            brightnessFactor = 1.3f,
            hueShiftDeg = 0f
        ),
        PASTEL_DREAM(
            displayName = "Pastel Dream",
            description = "64dp blur + soft pastel vertical gradient",
            blurRadiusDp = 64,
            meshType = MeshType.VERTICAL_GRADIENT,
            saturationBoost = 0.6f,
            brightnessFactor = 1.2f,
            hueShiftDeg = 0f
        ),
        MIDNIGHT(
            displayName = "Midnight",
            description = "96dp blur + dark moody color wash",
            blurRadiusDp = 96,
            meshType = MeshType.SOLID_WASH,
            saturationBoost = 1.5f,
            brightnessFactor = 0.5f,
            hueShiftDeg = 0f
        ),
        SUNSET(
            displayName = "Sunset",
            description = "32dp blur + warm dual-tone (orange/red shift)",
            blurRadiusDp = 32,
            meshType = MeshType.DUAL_TONE,
            saturationBoost = 1.8f,
            brightnessFactor = 1.1f,
            hueShiftDeg = 20f
        ),
        OCEAN(
            displayName = "Ocean",
            description = "48dp blur + cool dual-tone (blue/cyan shift)",
            blurRadiusDp = 48,
            meshType = MeshType.DUAL_TONE,
            saturationBoost = 1.5f,
            brightnessFactor = 1.0f,
            hueShiftDeg = -30f
        ),
        MONOCHROME(
            displayName = "Monochrome",
            description = "96dp blur + grayscale + single accent blob",
            blurRadiusDp = 96,
            meshType = MeshType.GRAYSCALE_ACCENT,
            saturationBoost = 0f,       // full desaturate for the blur
            brightnessFactor = 0.9f,
            hueShiftDeg = 0f
        ),
        RAINBOW_MESH(
            displayName = "Rainbow Mesh",
            description = "32dp blur + 4 large overlapping color blobs",
            blurRadiusDp = 32,
            meshType = MeshType.RADIAL_BLOBS,
            saturationBoost = 2.0f,
            brightnessFactor = 1.1f,
            hueShiftDeg = 0f
        ),
        VINTAGE(
            displayName = "Vintage",
            description = "64dp blur + sepia tritone (warm, faded film look)",
            blurRadiusDp = 64,
            meshType = MeshType.TRITONE,
            saturationBoost = 0.7f,
            brightnessFactor = 0.9f,
            hueShiftDeg = 15f
        ),
        AURORA(
            displayName = "Aurora",
            description = "48dp blur + green-purple aurora radial blobs",
            blurRadiusDp = 48,
            meshType = MeshType.RADIAL_BLOBS,
            saturationBoost = 2.2f,
            brightnessFactor = 1.15f,
            hueShiftDeg = 60f
        )
    }

    private lateinit var prefs: android.content.SharedPreferences

    private val _style = MutableStateFlow(PaletteStyle.SUNSET)
    val style: StateFlow<PaletteStyle> = _style.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_STYLE, null)
        _style.value = try {
            saved?.let { PaletteStyle.valueOf(it) } ?: PaletteStyle.SUNSET
        } catch (_: Exception) {
            PaletteStyle.SUNSET
        }
    }

    fun setStyle(style: PaletteStyle) {
        _style.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }
}
