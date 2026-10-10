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

/**
 * SongCoverManager — manages custom song covers (app-only overrides).
 *
 * Stores custom cover URIs in SharedPreferences, keyed by song ID.
 * Used by ALL screens (QuickPicks, Songs, Playlists, Players) to
 * check if a song has a custom cover and return it instead of the
 * original albumArtUri.
 *
 * This makes custom covers show EVERYWHERE in the app, not just
 * in the player.
 *
 * ★ Observable state: [revision] is a monotonically increasing counter
 *   that bumps every time a cover is set or reset. Composables that
 *   display covers should `collectAsState()` it and include it in their
 *   `remember(...)` key so they re-read the cover URI when it changes.
 *   Without this, Compose has no way to know that SharedPreferences was
 *   updated — the UI would show the stale (original) cover forever until
 *   the song ID changed.
 */
object SongCoverManager {
    private const val PREFS_NAME = "song_covers"
    private const val KEY_PREFIX = "cover_"

    private lateinit var prefs: android.content.SharedPreferences

    // ★ Revision counter — bumps on every write so observers recompose.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
     * ★ Bumps [revision] so all observers (MiniPlayer, Today's Top,
     *   Spiral2Player, QuickPicks, etc.) recompose and pick up the new cover.
     */
    fun setCustomCover(songId: Long, coverUri: Uri) {
        prefs.edit().putString("${KEY_PREFIX}$songId", coverUri.toString()).apply()
        _revision.value = _revision.value + 1
    }

    /**
     * Remove the custom cover for a song (reset to original).
     * ★ Bumps [revision] so all observers revert to the original cover.
     */
    fun resetCover(songId: Long) {
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
     *
     * Use this everywhere in the app instead of directly using song.albumArtUri.
     *
     * ★ NOTE: This is a plain function — it does NOT trigger recomposition.
     *   Callers in @Composable functions should also collectAsState() the
     *   [revision] flow and include it in their `remember(...)` key, otherwise
     *   the cover won't update when setCustomCover / resetCover is called.
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
 * given song — even if `songId` hasn't changed (e.g. the currently
 * playing song gets a new cover applied).
 *
 * Usage in any @Composable:
 *   val cover = rememberEffectiveCover(song.id, song.albumArtUri)
 *   AsyncImage(model = cover, ...)
 *
 * This replaces the verbose pattern:
 *   val rev by SongCoverManager.revision.collectAsState()
 *   val cover = remember(songId, rev, originalUri) {
 *       SongCoverManager.getEffectiveCover(songId, originalUri)
 *   }
 */
@Composable
fun rememberEffectiveCover(songId: Long?, originalArtUri: Uri?): Uri? {
    val revision by SongCoverManager.revision.collectAsState()
    return remember(songId, revision, originalArtUri) {
        SongCoverManager.getEffectiveCover(songId, originalArtUri)
    }
}
