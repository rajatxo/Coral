package com.rajatxo.coral.data.prefs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * ★ TextColorStyle — 22 different ALGORITHMS for extracting text color from album art.
 *
 * Each style scans the album art pixels and applies a DIFFERENT approach to
 * pick/transform the color. Like SpiralPaletteStyle has different background styles,
 * these have different TEXT color styles — the user picks which one looks best.
 *
 * The color CHANGES with each song (dynamic), but the ALGORITHM stays the same.
 */
enum class TextColorStyle(val label: String) {
    VIBRANT("Vibrant"),
    BRIGHTEST("Brightest"),
    DOMINANT("Dominant"),
    DOMINANT_BOOSTED("Dominant+"),
    COMPLEMENT("Complement"),
    TRIADIC("Triadic"),
    ANALOGOUS_PLUS("Analogous+"),
    ANALOGOUS_MINUS("Analogous-"),
    SPLIT_COMPLEMENT("Split Comp"),
    WARM("Warm"),
    COOL("Cool"),
    NEON("Neon"),
    PASTEL("Pastel"),
    GOLD("Gold"),
    ROSE("Rose"),
    EMERALD("Emerald"),
    SAPPHIRE("Sapphire"),
    AMBER("Amber"),
    CRIMSON("Crimson"),
    TEAL("Teal"),
    LAVENDER("Lavender"),
    CORAL("Coral");

    companion object {
        val DEFAULT = VIBRANT
    }
}

object TextColorStyleManager {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_STYLE = "text_color_style_v1"

    private lateinit var prefs: android.content.SharedPreferences
    private val _selectedStyle = MutableStateFlow(TextColorStyle.DEFAULT)
    val selectedStyle: StateFlow<TextColorStyle> = _selectedStyle.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_STYLE, TextColorStyle.DEFAULT.ordinal)
        _selectedStyle.value = TextColorStyle.entries.getOrElse(saved) { TextColorStyle.DEFAULT }
    }

    fun setStyle(style: TextColorStyle) {
        _selectedStyle.value = style
        prefs.edit().putInt(KEY_STYLE, style.ordinal).apply()
    }
}

/**
 * Extract a text color from album art using the specified style.
 * Each style uses a different algorithm to pick/transform the color.
 */
suspend fun extractTextColor(context: Context, artUri: Uri?, style: TextColorStyle): Color {
    if (artUri == null) return Color(0xFFF5EBD0)  // default cream
    return withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeBitmap(context, artUri) ?: return@withContext Color(0xFFF5EBD0)
            val result = extractColorFromBitmap(bitmap, style)
            bitmap.recycle()
            result
        } catch (_: Exception) {
            Color(0xFFF5EBD0)
        }
    }
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, boundsOptions)
    }
    val w = boundsOptions.outWidth
    val h = boundsOptions.outHeight
    if (w <= 0 || h <= 0) return null
    var sampleSize = 1
    while (w / (sampleSize * 2) >= 128 && h / (sampleSize * 2) >= 128) sampleSize *= 2
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
    }
}

private fun extractColorFromBitmap(bitmap: Bitmap, style: TextColorStyle): Color {
    val w = bitmap.width
    val h = bitmap.height

    // Collect pixel data
    var mostUsedR = 0; var mostUsedG = 0; var mostUsedB = 0
    var mostUsedCount = 0
    val colorBuckets = HashMap<Int, Int>(4096)

    var brightR = 0; var brightG = 0; var brightB = 0
    var brightScore = 0f

    var vibrantR = 0; var vibrantG = 0; var vibrantB = 0
    var vibrantScore = 0f

    for (x in 0 until w) {
        for (y in 0 until h) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Quantize for "most used"
            val bucket = ((r / 16) shl 8) or ((g / 16) shl 4) or (b / 16)
            val count = (colorBuckets[bucket] ?: 0) + 1
            colorBuckets[bucket] = count
            if (count > mostUsedCount) {
                mostUsedCount = count
                mostUsedR = (bucket shr 8 and 0x0F) * 16 + 8
                mostUsedG = (bucket shr 4 and 0x0F) * 16 + 8
                mostUsedB = (bucket and 0x0F) * 16 + 8
            }

            // HSV for scoring
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            // Brightest (high luminance)
            if (lum > brightScore && lum > 0.3f) {
                brightScore = lum
                brightR = r; brightG = g; brightB = b
            }

            // Vibrant (saturation × value, weighted)
            val vScore = value * (0.3f + sat * 0.7f)
            if (vScore > vibrantScore && value > 0.3f && sat > 0.2f) {
                vibrantScore = vScore
                vibrantR = r; vibrantG = g; vibrantB = b
            }
        }
    }

    // Get HSV of dominant color
    val domHSV = FloatArray(3)
    android.graphics.Color.RGBToHSV(mostUsedR, mostUsedG, mostUsedB, domHSV)

    // Get HSV of vibrant
    val vibHSV = FloatArray(3)
    android.graphics.Color.RGBToHSV(vibrantR, vibrantG, vibrantB, vibHSV)

    // Apply style-specific transformation
    val resultHSV = when (style) {
        TextColorStyle.VIBRANT -> floatArrayOf(vibHSV[0], (vibHSV[1]).coerceIn(0.7f, 1f), (vibHSV[2]).coerceIn(0.6f, 0.9f))
        TextColorStyle.BRIGHTEST -> floatArrayOf(domHSV[0], (domHSV[1] * 1.5f).coerceIn(0f, 1f), 0.85f)
        TextColorStyle.DOMINANT -> floatArrayOf(domHSV[0], (domHSV[1] * 1.3f).coerceIn(0.5f, 1f), (domHSV[2]).coerceIn(0.6f, 0.85f))
        TextColorStyle.DOMINANT_BOOSTED -> floatArrayOf(domHSV[0], 0.9f, 0.75f)
        TextColorStyle.COMPLEMENT -> floatArrayOf((domHSV[0] + 180f) % 360f, (domHSV[1] * 1.5f).coerceIn(0.6f, 1f), 0.75f)
        TextColorStyle.TRIADIC -> floatArrayOf((domHSV[0] + 120f) % 360f, 0.85f, 0.75f)
        TextColorStyle.ANALOGOUS_PLUS -> floatArrayOf((domHSV[0] + 30f) % 360f, (domHSV[1] * 1.4f).coerceIn(0.6f, 1f), 0.72f)
        TextColorStyle.ANALOGOUS_MINUS -> floatArrayOf((domHSV[0] - 30f + 360f) % 360f, (domHSV[1] * 1.4f).coerceIn(0.6f, 1f), 0.72f)
        TextColorStyle.SPLIT_COMPLEMENT -> floatArrayOf((domHSV[0] + 150f) % 360f, 0.8f, 0.72f)
        TextColorStyle.WARM -> floatArrayOf(((domHSV[0] + 20f) % 60f), 0.85f, 0.75f)
        TextColorStyle.COOL -> floatArrayOf(180f + (domHSV[0] % 120f), 0.8f, 0.72f)
        TextColorStyle.NEON -> floatArrayOf(vibHSV[0], 1f, 0.85f)
        TextColorStyle.PASTEL -> floatArrayOf(domHSV[0], 0.5f, 0.85f)
        TextColorStyle.GOLD -> floatArrayOf(45f, 0.9f, 0.8f)
        TextColorStyle.ROSE -> floatArrayOf(350f, 0.85f, 0.75f)
        TextColorStyle.EMERALD -> floatArrayOf(150f, 0.85f, 0.72f)
        TextColorStyle.SAPPHIRE -> floatArrayOf(220f, 0.85f, 0.75f)
        TextColorStyle.AMBER -> floatArrayOf(35f, 0.95f, 0.78f)
        TextColorStyle.CRIMSON -> floatArrayOf(355f, 0.9f, 0.72f)
        TextColorStyle.TEAL -> floatArrayOf(180f, 0.8f, 0.7f)
        TextColorStyle.LAVENDER -> floatArrayOf(270f, 0.6f, 0.8f)
        TextColorStyle.CORAL -> floatArrayOf(15f, 0.85f, 0.78f)
    }

    val rgb = android.graphics.Color.HSVToColor(resultHSV)
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f
    )
}
