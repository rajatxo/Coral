package com.rajatxo.coral.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            // Step 2: compute inSampleSize so the decoded bitmap is at
            // most 256x256. We only need the palette colors, not the
            // full-res image, so a small bitmap is plenty and saves
            // a ton of memory + time.
            // Without this, decoding a 4MB album art (e.g. 3000x3000)
            // blocks for ~1 second on slow devices — that was the ANR.
            var sampleSize = 1
            while (imageWidth / (sampleSize * 2) >= 256 && imageHeight / (sampleSize * 2) >= 256) {
                sampleSize *= 2
            }

            // Step 3: re-open the stream and decode at the reduced size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565  // half memory, fine for palette
            }
            val bitmap = context.contentResolver.openInputStream(artUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val palette = Palette.from(bitmap).generate()
            // Recycle the bitmap immediately — we only need the palette colors,
            // not the bitmap pixels. Without this, bitmaps can accumulate
            // and cause OOM crashes after several song changes.
            bitmap.recycle()
            val dominant = palette.dominantSwatch?.rgb
            val darkVibrant = palette.darkVibrantSwatch?.rgb ?: dominant
            val darkMuted = palette.darkMutedSwatch?.rgb ?: darkVibrant ?: dominant
            val vibrant = palette.vibrantSwatch?.rgb ?: palette.lightVibrantSwatch?.rgb ?: dominant

            if (dominant == null) return@withContext null

            CoralPalette(
                primary = Color(dominant),
                secondary = Color(darkVibrant ?: dominant),
                tertiary = Color(darkMuted ?: dominant),
                accent = Color(vibrant ?: dominant)
            )
        } catch (_: Exception) {
            null
        }
    }
}
