package com.rajatxo.coral.util

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * SongCoverManager — manages custom song covers (app-only overrides).
 *
 * ★ KEY DESIGN: Instead of storing the temporary gallery URI returned by
 *   ActivityResultContracts.GetContent() (which is NOT persistable and
 *   expires after the process dies), we COPY the picked image bytes to
 *   the app's internal storage (filesDir/song_covers/cover_<songId>.jpg).
 *   We then store a FileProvider URI pointing to that file.
 *
 *   Why this matters:
 *   - Gallery URIs from GetContent() are temporary — takePersistableUriPermission
 *     silently fails for MediaStore URIs, so the URI expires.
 *   - FileProvider URIs are ALWAYS readable by our app (it's our own provider).
 *   - The cover survives app restarts, process death, and URI permission revocation.
 *   - Coil3's AsyncImage can always load the URI — no permission issues.
 *
 * Used by ALL screens (QuickPicks, Songs, Playlists, Players) to check if a song
 * has a custom cover and return it instead of the original albumArtUri.
 *
 * ★ Observable state: [revision] is a monotonically increasing counter
 *   that bumps every time a cover is set or reset. Composables that
 *   display covers should `collectAsState()` it and include it in their
 *   `remember(...)` key so they re-read the cover URI when it changes.
 */
object SongCoverManager {
    private const val PREFS_NAME = "song_covers"
    private const val KEY_PREFIX = "cover_"
    private const val COVER_DIR = "song_covers"

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var appContext: Context

    // ★ Revision counter — bumps on every write so observers recompose.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Ensure the cover directory exists
        getCoverDir().mkdirs()
    }

    private fun getCoverDir(): File {
        return File(appContext.filesDir, COVER_DIR)
    }

    private fun getCoverFile(songId: Long): File {
        return File(getCoverDir(), "cover_$songId.jpg")
    }

    /**
     * ★ Copy a picked cover image to the app's internal storage and return
     *   a FileProvider URI that's always readable.
     *
     * This is the SMART approach: instead of storing a temporary gallery
     * URI (which expires), we copy the image bytes to filesDir/song_covers/
     * and use a FileProvider URI. This guarantees:
     *   - The URI is always readable by our app (our own FileProvider)
     *   - The cover survives app restarts
     *   - No takePersistableUriPermission needed
     *   - Coil3's AsyncImage can always load it
     *
     * Must be called on a background thread (IO dispatcher).
     *
     * @param songId the song to associate the cover with
     * @param sourceUri the temporary gallery URI from GetContent()
     * @return the FileProvider URI for the copied file, or null on failure
     */
    fun copyAndStoreCover(songId: Long, sourceUri: Uri): Uri? {
        return try {
            val coverFile = getCoverFile(songId)

            // Copy the image bytes from the gallery URI to our private file
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                coverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!coverFile.exists() || coverFile.length() == 0L) {
                return null
            }

            // Convert to a FileProvider URI
            val fileProviderUri = androidx.core.content.FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                coverFile
            )

            // Store the FileProvider URI in SharedPreferences
            prefs.edit().putString("${KEY_PREFIX}$songId", fileProviderUri.toString()).apply()

            // ★ Bump revision so all observers recompose
            _revision.value = _revision.value + 1

            fileProviderUri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the custom cover URI for a song, or null if none is set.
     */
    fun getCustomCover(songId: Long?): Uri? {
        if (songId == null) return null
        val coverStr = prefs.getString("${KEY_PREFIX}$songId", null) ?: return null
        return try { Uri.parse(coverStr) } catch (_: Exception) { null }
    }

    /**
     * Set a custom cover for a song (app-only).
     * ★ Bumps [revision] so all observers recompose.
     *
     * NOTE: This stores the URI as-is. Prefer [copyAndStoreCover] for
     * gallery-picked images — it copies to private storage first.
     * This method is for cases where the URI is already a FileProvider
     * URI or a persistent content URI.
     */
    fun setCustomCover(songId: Long, coverUri: Uri) {
        prefs.edit().putString("${KEY_PREFIX}$songId", coverUri.toString()).apply()
        _revision.value = _revision.value + 1
    }

    /**
     * Remove the custom cover for a song (reset to original).
     * Also deletes the copied image file from internal storage.
     * ★ Bumps [revision] so all observers revert to the original cover.
     */
    fun resetCover(songId: Long) {
        // Delete the copied image file
        try {
            getCoverFile(songId).delete()
        } catch (_: Exception) { }
        prefs.edit().remove("${KEY_PREFIX}$songId").apply()
        _revision.value = _revision.value + 1
    }

    /**
     * Check if a song has a custom cover set.
     */
    fun hasCustomCover(songId: Long?): Boolean {
        if (songId == null) return false
        return prefs.contains("${KEY_PREFIX}$songId")
    }

    /**
     * Get the effective cover URI — returns the custom cover if set,
     * otherwise returns the original albumArtUri.
     */
    fun getEffectiveCover(songId: Long?, originalArtUri: Uri?): Uri? {
        val custom = getCustomCover(songId)
        return custom ?: originalArtUri
    }
}

/**
 * ★ rememberEffectiveCover — Compose-friendly wrapper around
 *   [SongCoverManager.getEffectiveCover].
 *
 * Automatically observes [SongCoverManager.revision] so the composable
 * recomposes immediately when a custom cover is set or reset for the
 * given song — even if `songId` hasn't changed.
 */
@Composable
fun rememberEffectiveCover(songId: Long?, originalArtUri: Uri?): Uri? {
    val revision by SongCoverManager.revision.collectAsState()
    return remember(songId, revision, originalArtUri) {
        SongCoverManager.getEffectiveCover(songId, originalArtUri)
    }
}
