package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackHistory — tracks the most recently played songs.
 *
 * Used by the Speed Dial "Based on: Last Played" mode to fill the
 * grid with songs from the user's recent listening history, biased
 * toward the most-recently-played artist (80% from that artist,
 * 20% from other artists).
 *
 * Storage:
 *   A simple ring buffer in SharedPreferences. Stores up to
 *   [MAX_HISTORY] song IDs + their artist names, most-recent-first.
 *   Dedupes by song ID (replays don't add a duplicate entry — they
 *   bump the existing entry to the front).
 *
 * Persistence:
 *   - songIds: comma-separated Long IDs
 *   - artists: comma-separated artist names (parallel array — same index)
 *   - The arrays are always the same length and ordered by recency.
 *
 * Thread safety:
 *   All mutations go through [recordPlayback] which is synchronized.
 *   Reads via [history] StateFlow are safe from any thread.
 */
object PlaybackHistory {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_SONG_IDS = "playback_history_song_ids_v1"
    private const val KEY_ARTISTS = "playback_history_artists_v1"

    private const val MAX_HISTORY = 100

    data class HistoryEntry(
        val songId: Long,
        val artist: String
    )

    private lateinit var prefs: android.content.SharedPreferences

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val idsStr = prefs.getString(KEY_SONG_IDS, null) ?: return
        val artistsStr = prefs.getString(KEY_ARTISTS, null) ?: return

        val ids = idsStr.split(",").mapNotNull { it.toLongOrNull() }
        val artists = artistsStr.split("\u0001")  // use \u0001 as separator — artist names can contain commas

        if (ids.size != artists.size) return  // corrupted — bail out

        _history.value = ids.mapIndexed { i, id ->
            HistoryEntry(songId = id, artist = artists[i])
        }
    }

    /**
     * Record that [songId] by [artist] was just played.
     *
     * Bumps the song to the front of the history if it already exists
     * (replay), or adds it as a new entry. Trims to [MAX_HISTORY].
     */
    @Synchronized
    fun recordPlayback(songId: Long, artist: String) {
        if (songId <= 0L) return
        val current = _history.value.toMutableList()
        // Remove existing entry (if replaying the same song)
        current.removeAll { it.songId == songId }
        // Add to front (most recent first)
        current.add(0, HistoryEntry(songId = songId, artist = artist))
        // Trim to MAX_HISTORY
        val trimmed = current.take(MAX_HISTORY)
        _history.value = trimmed
        save(trimmed)
    }

    private fun save(entries: List<HistoryEntry>) {
        val idsStr = entries.joinToString(",") { it.songId.toString() }
        val artistsStr = entries.joinToString("\u0001") { it.artist }
        prefs.edit()
            .putString(KEY_SONG_IDS, idsStr)
            .putString(KEY_ARTISTS, artistsStr)
            .apply()
    }

    /**
     * Get the most-recently-played artist name.
     *
     * Used by the Speed Dial "Last Played" mode to bias 80% of the
     * grid toward songs by this artist.
     */
    fun lastPlayedArtist(): String? {
        return _history.value.firstOrNull()?.artist?.takeIf { it.isNotBlank() }
    }

    /**
     * Get the most-recently-played song ID.
     *
     * Used by the Speed Dial to exclude the currently-playing song
     * from the "Last Played" pool (since the user just heard it).
     */
    fun lastPlayedSongId(): Long? {
        return _history.value.firstOrNull()?.songId
    }

    /**
     * Clear all history. (Not currently exposed in the UI — could be
     * added to Settings → Privacy in the future.)
     */
    fun clear() {
        _history.value = emptyList()
        prefs.edit()
            .remove(KEY_SONG_IDS)
            .remove(KEY_ARTISTS)
            .apply()
    }
}
