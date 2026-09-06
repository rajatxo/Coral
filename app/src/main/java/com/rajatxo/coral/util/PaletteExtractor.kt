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
            tertiary = Color(0xFF050505),
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
            val inputStream = context.contentResolver.openInputStream(artUri) ?: return@withContext null
            val bitmap = inputStream.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null

            val palette = Palette.from(bitmap).generate()
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
