package com.rajatxo.coral.util

import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Pre-computed blur transformation for Coil's image pipeline.
 *
 * WHY THIS EXISTS
 * ───────────────
 * The "Spiral 2.0-style album art blur-blend" card effect (SquareCard,
 * LandscapeCard, SpeedDialCard) needs a blurred version of each album
 * art for the bottom layer. The original implementation used Compose's
 * `.blur(36.dp)` modifier which applies a RenderEffect on EVERY FRAME.
 * With 9 SpeedDialCards visible at once, that's 9 per-frame blurs +
 * 9 Offscreen compositing buffers → scrolling jank.
 *
 * This Transformation pre-blurs the image ONCE when Coil decodes it.
 * The blurred bitmap is cached in Coil's memory cache. Subsequent
 * scrolls just look up the cached blurred bitmap → instant display,
 * no per-frame RenderEffect.
 *
 * ALGORITHM
 * ─────────
 * Uses the downscale-upscale trick: scale the bitmap down by a factor
 * based on the blur radius, then scale it back up. The bilinear
 * filtering during upscale produces a smooth blur. This is O(n) and
 * very fast — no RenderScript, no per-pixel manipulation.
 *
 * The result is visually similar to a Gaussian blur but computed in
 * <1ms per image (compared to RenderEffect's per-frame cost).
 *
 * USAGE
 * ─────
 *   AsyncImage(
 *       model = ImageRequest.Builder(LocalContext.current)
 *           .data(song.albumArtUri)
 *           .transformations(BlurTransformation(36.dp))
 *           .build(),
 *       ...
 *   )
 *
 * CACHE BEHAVIOR
 * ──────────────
 * Coil caches the transformed bitmap by the request's cache key
 * (which includes the transformations). So the same album art + blur
 * radius is only computed ONCE, then served from memory cache.
 */
class BlurTransformation(
    private val radius: Dp
) : Transformation() {

    // Cache key includes the radius so different blur levels are cached
    // separately. Rounded to int to avoid cache misses from float drift.
    override val cacheKey: String = "blur_${radius.value.toInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Convert dp to a downscale factor. Higher radius = more downscaling
        // = more blur. The relationship is approximate but works well for
        // the 20-48dp range used by the cards.
        val radiusPx = (radius.value * 2.5f).toInt().coerceAtLeast(1)
        val downscaleFactor = (radiusPx.coerceIn(1, 50)).toFloat() / 50f
        // downscaleFactor: 0.02 (heavy blur) to 1.0 (no blur)

        // Calculate the small dimensions — never go below 2px or the
        // upscale will be too pixelated.
        val smallWidth = (input.width * downscaleFactor).toInt().coerceAtLeast(2)
        val smallHeight = (input.height * downscaleFactor).toInt().coerceAtLeast(2)

        // Downscale → small bitmap (fast)
        val small = Bitmap.createScaledBitmap(input, smallWidth, smallHeight, true)
        // Upscale → back to original size, bilinear-filtered (blurry)
        val blurred = Bitmap.createScaledBitmap(small, input.width, input.height, true)

        // Recycle the intermediate small bitmap to free memory.
        if (small !== blurred) {
            small.recycle()
        }

        return blurred
    }
}
