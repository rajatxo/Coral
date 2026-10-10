package com.rajatxo.coral.util

import android.content.Context
import android.net.Uri

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
 */
object SongCoverManager {
    private const val PREFS_NAME = "song_covers"
    private const val KEY_PREFIX = "cover_"

    private lateinit var prefs: android.content.SharedPreferences

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
     */
    fun setCustomCover(songId: Long, coverUri: Uri) {
        prefs.edit().putString("${KEY_PREFIX}$songId", coverUri.toString()).apply()
    }

    /**
     * Remove the custom cover for a song (reset to original).
     */
    fun resetCover(songId: Long) {
        prefs.edit().remove("${KEY_PREFIX}$songId").apply()
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
     */
    fun getEffectiveCover(songId: Long?, originalArtUri: Uri?): Uri? {
        val custom = getCustomCover(songId)
        return custom ?: originalArtUri
    }
}
