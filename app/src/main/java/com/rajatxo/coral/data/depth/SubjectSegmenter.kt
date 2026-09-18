package com.rajatxo.coral.data.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SubjectSegmenter — extracts the foreground subject from a photo.
 *
 * CURRENT IMPLEMENTATION (v1): Color-based background removal.
 * Samples the corners of the image to detect the background color,
 * then removes all pixels that are close to that color. Uses
 * flood-fill from the borders so only the OUTER background is removed
 * (interior pixels that match the background color stay, preserving
 * shadows on the subject).
 *
 * This works well for photos with a relatively uniform background
 * (studio shots, plain walls, sky, etc.). For complex backgrounds
 * (busy scenes, forests, crowds) the cutout will be rough.
 *
 * FUTURE: Replace with ML Kit Subject Segmentation once the correct
 * artifact/package names are verified on-device. ML Kit uses a neural
 * network that works on ANY photo regardless of background complexity.
 */
class SubjectSegmenter(private val context: Context) {

    /**
     * Extract the foreground subject from [photoUri] and return a cutout
     * Bitmap with a transparent background. Returns null on failure.
     */
    suspend fun extractSubject(photoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val original = loadBitmap(photoUri) ?: return@withContext null
            val cutout = removeBackground(original)
            original.recycle()
            cutout
        } catch (e: Exception) {
            android.util.Log.e("SubjectSegmenter", "Extraction failed", e)
            null
        }
    }

    /**
     * Remove the background from [bitmap] using color-based detection.
     * Samples the 4 corners to determine the background color, then
     * flood-fills from the borders to remove only the outer background.
     */
    private fun removeBackground(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Sample the 4 corners + edge midpoints to get the background color
        val samplePoints = listOf(
            0 to 0,
            width - 1 to 0,
            0 to height - 1,
            width - 1 to height - 1,
            width / 2 to 0,
            width / 2 to height - 1,
            0 to height / 2,
            width - 1 to height / 2
        )
        var avgR = 0; var avgG = 0; var avgB = 0
        for ((x, y) in samplePoints) {
            val pixel = pixels[y * width + x]
            avgR += Color.red(pixel)
            avgG += Color.green(pixel)
            avgB += Color.blue(pixel)
        }
        avgR /= samplePoints.size
        avgG /= samplePoints.size
        avgB /= samplePoints.size

        // Tolerance: pixels within this distance of the background color
        // are considered background. 40 is a reasonable default that
        // handles slight gradients in the background.
        val tolerance = 40

        // Mark background pixels (flood fill from borders)
        val isBackground = BooleanArray(width * height) { false }
        val queue = ArrayDeque<Int>()

        // Seed: all border pixels that match the background color
        for (x in 0 until width) {
            seedIfBackground(pixels, isBackground, x, 0, width, avgR, avgG, avgB, tolerance, queue)
            seedIfBackground(pixels, isBackground, x, height - 1, width, avgR, avgG, avgB, tolerance, queue)
        }
        for (y in 0 until height) {
            seedIfBackground(pixels, isBackground, 0, y, width, avgR, avgG, avgB, tolerance, queue)
            seedIfBackground(pixels, isBackground, width - 1, y, width, avgR, avgG, avgB, tolerance, queue)
        }

        // BFS flood fill
        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val x = idx % width
            val y = idx / width
            // Check 4 neighbors
            val neighbors = listOf(
                x - 1 to y, x + 1 to y, x to y - 1, x to y + 1
            )
            for ((nx, ny) in neighbors) {
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val nidx = ny * width + nx
                if (isBackground[nidx]) continue
                if (colorMatches(pixels[nidx], avgR, avgG, avgB, tolerance)) {
                    isBackground[nidx] = true
                    queue.add(nidx)
                }
            }
        }

        // Apply: set background pixels to transparent
        for (i in pixels.indices) {
            if (isBackground[i]) {
                pixels[i] = 0 // fully transparent
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun seedIfBackground(
        pixels: IntArray, isBackground: BooleanArray,
        x: Int, y: Int, width: Int,
        bgR: Int, bgG: Int, bgB: Int, tolerance: Int,
        queue: ArrayDeque<Int>
    ) {
        val idx = y * width + x
        if (!isBackground[idx] && colorMatches(pixels[idx], bgR, bgG, bgB, tolerance)) {
            isBackground[idx] = true
            queue.add(idx)
        }
    }

    private fun colorMatches(pixel: Int, r: Int, g: Int, b: Int, tolerance: Int): Boolean {
        val pr = Color.red(pixel)
        val pg = Color.green(pixel)
        val pb = Color.blue(pixel)
        return kotlin.math.abs(pr - r) <= tolerance &&
               kotlin.math.abs(pg - g) <= tolerance &&
               kotlin.math.abs(pb - b) <= tolerance
    }

    /** Load a Bitmap from a Uri, downscaled if too large */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap?.let { downscaleIfLarge(it, 1500) }
        } catch (e: Exception) {
            android.util.Log.e("SubjectSegmenter", "Load failed", e)
            null
        }
    }

    /** Downscale a bitmap if its longest side exceeds [maxSize] */
    private fun downscaleIfLarge(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longestSide = maxOf(width, height)
        if (longestSide <= maxSize) return bitmap

        val scale = maxSize.toFloat() / longestSide
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
