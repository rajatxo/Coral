package com.rajatxo.coral.data.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.subjectsegmentation.Subject
import com.google.mlkit.subjectsegmentation.SubjectSegmentation
import com.google.mlkit.subjectsegmentation.SubjectSegmenter
import com.google.mlkit.subjectsegmentation.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * SubjectSegmenter — uses ML Kit Subject Segmentation to extract the
 * foreground subject from ANY photo and return a cutout Bitmap with a
 * transparent background.
 *
 * HOW IT WORKS:
 * 1. Load the photo from the Uri into a Bitmap
 * 2. Pass it to ML Kit's SubjectSegmenter (on-device neural network)
 * 3. ML Kit returns a list of Subject objects, each with a Bitmap mask
 *    (white = subject, black = background)
 * 4. We apply the mask to the original Bitmap: where the mask is white,
 *    keep the original pixel; where black, set alpha to 0 (transparent)
 * 5. Return the cutout Bitmap
 *
 * The ML model is the same one Google Photos uses for "Select subject"
 * long-press. Works on people, pets, objects, food — anything that's
 * recognizably a "subject" against a background.
 *
 * Runs on-device, ~200-500ms on a typical phone. No API key, no network.
 */
class SubjectSegmenter(private val context: Context) {

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects()
                .build()
        )
    }

    /**
     * Extract the foreground subject from [photoUri] and return a cutout
     * Bitmap with a transparent background. Returns null on failure.
     */
    suspend fun extractSubject(photoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Load the photo
            val original = loadBitmap(photoUri) ?: return@withContext null

            // 2. Run ML Kit subject segmentation
            val subjects = segmentSubjects(original) ?: return@withContext null
            if (subjects.isEmpty()) return@withContext null

            // 3. Merge all detected subjects into a single mask.
            val width = original.width
            val height = original.height
            val mergedMask = IntArray(width * height) { 0 }

            for (subject in subjects) {
                val maskBitmap = subject.bitmap
                if (maskBitmap.width != width || maskBitmap.height != height) continue
                val maskPixels = IntArray(width * height)
                maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)
                for (i in maskPixels.indices) {
                    val confidence = Color.red(maskPixels[i])
                    if (confidence > 0) {
                        mergedMask[i] = 255
                    }
                }
            }

            // 4. Apply the merged mask to the original bitmap
            val originalPixels = IntArray(width * height)
            original.getPixels(originalPixels, 0, width, 0, 0, width, height)
            for (i in originalPixels.indices) {
                val alpha = mergedMask[i]
                if (alpha == 0) {
                    originalPixels[i] = 0
                } else {
                    originalPixels[i] = (alpha shl 24) or (originalPixels[i] and 0x00FFFFFF)
                }
            }
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.setPixels(originalPixels, 0, width, 0, 0, width, height)

            original.recycle()
            result
        } catch (e: Exception) {
            android.util.Log.e("SubjectSegmenter", "Extraction failed", e)
            null
        }
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

    /** Run ML Kit segmentation and await the result */
    private suspend fun segmentSubjects(bitmap: Bitmap): List<Subject>? {
        return suspendCancellableCoroutine { cont ->
            segmenter.process(bitmap)
                .addOnSuccessListener { result ->
                    cont.resume(result.subjects)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("SubjectSegmenter", "ML Kit failed", e)
                    cont.resume(null)
                }
        }
    }
}
