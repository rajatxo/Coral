package com.rajatxo.coral.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ★ Vibrant palette extraction — scans every pixel of the album art
 *   and produces a vibrant, highly-readable text color.
 *
 * ALGORITHM:
 *   1. Decode bitmap at reduced size (max 128x128 for speed)
 *   2. Scan every pixel:
 *      - Track the MOST USED color (highest count, quantized to reduce unique values)
 *      - Track the MOST BRIGHT color (highest luminance that's still saturated)
 *   3. Mix the two:
 *      - mostUsed provides the HUE (the dominant color identity)
 *      - mostBright provides the BRIGHTNESS (ensures visibility on dark bg)
 *   4. Boost saturation to make it pop
 *   5. Clamp lightness to at least 0.55 (readable on dark backgrounds)
 *
 * Returns the mixed color. If extraction fails, returns the default color.
 */
suspend fun extractVibrantTextColor(context: Context, artUri: Uri?): Color {
    if (artUri == null) return DEFAULT_TEXT_COLOR
    return withContext(Dispatchers.IO) {
        try {
            // Step 1: decode bounds
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(artUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val imageWidth = boundsOptions.outWidth
            val imageHeight = boundsOptions.outHeight
            if (imageWidth <= 0 || imageHeight <= 0) return@withContext DEFAULT_TEXT_COLOR

            // Step 2: compute sample size (target ~128x128)
            var sampleSize = 1
            while (imageWidth / (sampleSize * 2) >= 128 && imageHeight / (sampleSize * 2) >= 128) {
                sampleSize *= 2
            }

            // Step 3: decode bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(artUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext DEFAULT_TEXT_COLOR

            // Step 4: scan every pixel
            // Quantize each pixel to a 16-level bucket per channel (16^3 = 4096 buckets)
            // to group similar colors together.
            val colorCounts = HashMap<Int, Int>(4096)
            var brightestR = 0
            var brightestG = 0
            var brightestB = 0
            var brightestLum = 0f
            var brightestSat = 0f

            val w = bitmap.width
            val h = bitmap.height
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // Quantize: divide each channel by 16 (0-15 buckets)
                    val qr = r / 16
                    val qg = g / 16
                    val qb = b / 16
                    val bucket = (qr shl 8) or (qg shl 4) or qb
                    colorCounts[bucket] = (colorCounts[bucket] ?: 0) + 1

                    // Track most bright + saturated pixel
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    // Compute saturation in HSV
                    val hsv = FloatArray(3)
                    android.graphics.Color.RGBToHSV(r, g, b, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]

                    // We want bright (high value) AND saturated (high sat) colors
                    // Score = value * saturation (rewards both brightness and saturation)
                    val score = value * (0.3f + sat * 0.7f)  // weight saturation more
                    if (score > brightestLum && value > 0.3f && sat > 0.2f) {
                        brightestLum = score
                        brightestSat = sat
                        brightestR = r
                        brightestG = g
                        brightestB = b
                    }
                }
            }
            bitmap.recycle()

            // Step 5: find the most used color bucket
            val mostUsedBucket = colorCounts.maxByOrNull { it.value }?.key ?: 0
            // De-quantize: multiply by 16 + add 8 (center of bucket)
            val mostUsedR = ((mostUsedBucket shr 8) and 0x0F) * 16 + 8
            val mostUsedG = ((mostUsedBucket shr 4) and 0x0F) * 16 + 8
            val mostUsedB = (mostUsedBucket and 0x0F) * 16 + 8

            // Step 6: mix most-used (hue) with most-bright (lightness)
            // Convert both to HSV
            val mostUsedHSV = FloatArray(3)
            android.graphics.Color.RGBToHSV(mostUsedR, mostUsedG, mostUsedB, mostUsedHSV)

            val brightHSV = FloatArray(3)
            android.graphics.Color.RGBToHSV(brightestR, brightestG, brightestB, brightHSV)

            // Mix: use mostUsed's HUE, but boost its lightness toward the bright pixel's lightness
            // This keeps the dominant color identity but makes it readable
            val mixedHue = mostUsedHSV[0]
            // Saturation: use the higher of the two (more vibrant)
            val mixedSat = maxOf(mostUsedHSV[1], brightHSV[1], 0.6f).coerceIn(0.6f, 1f)
            // Lightness: average of mostUsed and bright, clamped to at least 0.55
            val mixedValue = ((mostUsedHSV[2] + brightHSV[2]) / 2f).coerceIn(0.55f, 0.85f)

            // Build the final color
            val resultHSV = floatArrayOf(mixedHue, mixedSat, mixedValue)
            val resultRGB = android.graphics.Color.HSVToColor(resultHSV)
            Color(
                red = ((resultRGB shr 16) and 0xFF) / 255f,
                green = ((resultRGB shr 8) and 0xFF) / 255f,
                blue = (resultRGB and 0xFF) / 255f
            )
        } catch (_: Exception) {
            DEFAULT_TEXT_COLOR
        }
    }
}

/** Default text color when no song is playing — warm cream #F5EBD0 */
val DEFAULT_TEXT_COLOR = Color(0xFFF5EBD0)

/** Default background top color when no song is playing — deep wine red #7F011F */
val DEFAULT_BG_COLOR = Color(0xFF7F011F)
