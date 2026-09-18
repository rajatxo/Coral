package com.rajatxo.coral.data.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * QuickPicksFigureStore — persists the user's chosen photo + the ML Kit
 * cutout to internal storage so they load instantly on every app launch.
 *
 * Files (in app's internal filesDir):
 *   quick_picks_figure.png  — the cutout (transparent background)
 *   quick_picks_figure_uri.txt — the original photo Uri (for reference)
 *
 * State flow:
 *   cutoutBitmap: StateFlow<Bitmap?> — null = no figure set, non-null =
 *   the cutout is ready to display. UI observes this.
 *
 * User flow:
 *   1. User picks a photo via the picker
 *   2. SubjectSegmenter extracts the cutout
 *   3. saveCutout() writes the PNG to internal storage
 *   4. cutoutBitmap emits the new Bitmap → UI updates
 *   5. On next app launch, loadCutout() reads the PNG → instant display
 */
class QuickPicksFigureStore private constructor(private val context: Context) {

    private val cutoutFile = File(context.filesDir, "quick_picks_figure.png")

    private val _cutoutBitmap = MutableStateFlow<Bitmap?>(null)
    val cutoutBitmap: StateFlow<Bitmap?> = _cutoutBitmap.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /** Load the saved cutout from disk. Call on app start. */
    fun loadCutout() {
        if (cutoutFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cutoutFile.absolutePath)
                if (bitmap != null) {
                    _cutoutBitmap.value = bitmap
                }
            } catch (e: Exception) {
                android.util.Log.e("FigureStore", "Load failed", e)
            }
        }
    }

    /**
     * Process a newly-picked photo: extract the subject via ML Kit and
     * save the cutout. Updates [cutoutBitmap] when done.
     */
    suspend fun processAndSave(photoUri: Uri) {
        _isProcessing.value = true
        try {
            val segmenter = SubjectSegmenter(context)
            val cutout = segmenter.extractSubject(photoUri)
            if (cutout != null) {
                saveCutout(cutout)
                _cutoutBitmap.value = cutout
            } else {
                android.util.Log.e("FigureStore", "ML Kit returned null cutout")
            }
        } finally {
            _isProcessing.value = false
        }
    }

    /** Write the cutout Bitmap to internal storage as a PNG */
    private fun saveCutout(bitmap: Bitmap) {
        try {
            FileOutputStream(cutoutFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            android.util.Log.e("FigureStore", "Save failed", e)
        }
    }

    /** Remove the saved cutout (user wants to clear/reset) */
    fun clearCutout() {
        if (cutoutFile.exists()) cutoutFile.delete()
        _cutoutBitmap.value = null
    }

    companion object {
        @Volatile private var INSTANCE: QuickPicksFigureStore? = null

        fun get(context: Context): QuickPicksFigureStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuickPicksFigureStore(context.applicationContext).also {
                    INSTANCE = it
                    it.loadCutout()
                }
            }
        }
    }
}
