package com.rajatxo.coral.util

import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Pre-computed blur transformation for Coil's image pipeline.
 *
 * The .blur() Compose modifier applies a RenderEffect on EVERY FRAME
 * the card is visible. With 9 SpeedDialCards on screen, that's 9
 * per-frame blurs → scroll jank.
 *
 * This Transformation pre-blurs the bitmap ONCE when Coil decodes the
 * image, then caches it. Subsequent scrolls just look up the cached
 * blurred bitmap → no per-frame work → smooth scrolling.
 *
 * ALGORITHM: downscale-upscale with two passes
 *   1. Downscale by sqrt(factor) → mid-size bitmap
 *   2. Downscale by factor → small bitmap
 *   3. Upscale back to original size with bilinear filtering → blurred
 *
 * Two passes produce a smoother blur than single-pass (less blocky).
 *
 * The result is visually similar to a Gaussian blur but not identical.
 * Tuned to match the .blur() modifier's visual intensity closely.
 */
class BlurTransformation(
    private val radius: Dp
) : Transformation() {

    // Cache key includes the radius so different blur levels cache separately.
    override val cacheKey: String = "blur_${radius.value.toInt()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Convert dp to approximate pixels (assume 2.5x density — typical phone).
        val radiusPx = (radius.value * 2.5f).toInt().coerceAtLeast(1)

        // Downscale factor: smaller = more blur.
        // Formula: 4 / radiusPx gives:
        //   36dp → 4/90  = 0.044 → downscale to 4.4% → heavy blur
        //   20dp → 4/50  = 0.08  → downscale to 8%   → medium blur
        // Clamped to [0.04, 0.5] to avoid extreme cases.
        val downscaleFactor = (4f / radiusPx).coerceIn(0.04f, 0.5f)

        val smallW = (input.width * downscaleFactor).toInt().coerceAtLeast(2)
        val smallH = (input.height * downscaleFactor).toInt().coerceAtLeast(2)

        // Two-pass downscale for smoother blur (less blocky).
        // First pass: downscale by sqrt(factor) → mid-size
        // Second pass: downscale by factor → small
        // Then upscale back to original size with bilinear filtering.
        val midFactor = Math.sqrt(downscaleFactor.toDouble()).toFloat()
        val midW = (input.width * midFactor).toInt().coerceAtLeast(2)
        val midH = (input.height * midFactor).toInt().coerceAtLeast(2)

        val mid = Bitmap.createScaledBitmap(input, midW, midH, true)
        val small = Bitmap.createScaledBitmap(mid, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, input.width, input.height, true)

        // Recycle intermediate bitmaps to free memory.
        if (mid !== blurred) mid.recycle()
        if (small !== blurred) small.recycle()

        return blurred
    }
}
