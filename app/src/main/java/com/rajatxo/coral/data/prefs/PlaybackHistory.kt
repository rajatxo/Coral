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
        loadDailyPlays()
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

    // ════════════════════════════════════════════════════════════════
    //  DAILY PLAY DURATION — for "Today's Top" feature
    // ════════════════════════════════════════════════════════════════
    // Tracks how many SECONDS each song was actually played TODAY
    // (calendar day, resets at midnight). Used by the "Today's Top" row
    // on Quick Picks.
    //
    // FILLING LOGIC:
    //   Phase 1: First 6 unique songs tapped get the slots (even if
    //            played for 0 seconds). Fills the capsules quickly.
    //   Phase 2: After 6+ songs exist, ranking is by total duration.
    //            A song played longer ranks higher.
    //
    // Storage:
    //   - Key: "daily_plays_v2" → JSON: {"date": "2026-10-03",
    //       "durations": {"123": 300, "456": 120}, "artists": {...}}
    //   - Durations are in SECONDS (accumulated while playing).
    //   - On read: if the stored date != today, the data is cleared.
    // ════════════════════════════════════════════════════════════════

    private const val KEY_DAILY_PLAYS = "daily_plays_v3"

    data class DailyPlayCount(
        val songId: Long,
        val artist: String,
        val playCount: Int  // ★ Now represents DURATION IN SECONDS
    )

    private val _dailyPlays = MutableStateFlow<List<DailyPlayCount>>(emptyList())
    val dailyPlays: StateFlow<List<DailyPlayCount>> = _dailyPlays.asStateFlow()

    /**
     * Get today's date as "YYYY-MM-DD" string.
     */
    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Load daily play durations. If the stored date is not today, clear
     * the data (new day = fresh start). Fully guarded — never crashes.
     */
    private fun loadDailyPlays() {
        if (!::prefs.isInitialized) return
        try {
            val json = prefs.getString(KEY_DAILY_PLAYS, null) ?: return
            val root = org.json.JSONObject(json)
            val storedDate = root.optString("date", "")
            if (storedDate != todayString()) {
                // New day — clear the data
                prefs.edit().remove(KEY_DAILY_PLAYS).apply()
                _dailyPlays.value = emptyList()
                return
            }
            val durations = root.optJSONObject("durations") ?: return
            val artists = root.optJSONObject("artists") ?: return
            val list = mutableListOf<DailyPlayCount>()
            val keys = durations.keys()
            while (keys.hasNext()) {
                val idStr = keys.next()
                val id = idStr.toLongOrNull() ?: continue
                val duration = durations.optInt(idStr, 0)  // seconds
                val artist = artists.optString(idStr, "")
                if (duration >= 0) {
                    list.add(DailyPlayCount(id, artist, duration))
                }
            }
            // ★ Sort by duration descending (longest played = first)
            list.sortByDescending { it.playCount }
            _dailyPlays.value = list
        } catch (_: Exception) {
            _dailyPlays.value = emptyList()
        }
    }

    /**
     * ★ Record a song TAP — adds the song to the daily list with 0 seconds.
     *   Used for Phase 1 (fill the first 6 slots quickly).
     *   If the song is already in the list, does nothing (keeps its duration).
     */
    @Synchronized
    fun recordDailyPlay(songId: Long, artist: String) {
        if (songId <= 0L) return
        if (!::prefs.isInitialized) return
        try {
            val current = _dailyPlays.value.toMutableList()
            val existing = current.find { it.songId == songId }
            if (existing == null) {
                // New song — add with 0 seconds duration
                current.add(DailyPlayCount(songId, artist, 0))
                // Sort by duration descending (0 stays at bottom unless others are 0)
                current.sortByDescending { it.playCount }
                _dailyPlays.value = current
                saveDailyPlays(current)
            }
            // If already exists, do nothing — keep its accumulated duration
        } catch (_: Exception) { }
    }

    /**
     * ★ ADD playback duration to a song — called every second while playing.
     *   Accumulates the actual time the song was listened to.
     *   [secondsToAdd] = how many seconds to add (usually 1, called every
     *   second while the song is playing).
     */
    @Synchronized
    fun addPlayDuration(songId: Long, artist: String, secondsToAdd: Int) {
        if (songId <= 0L || secondsToAdd <= 0) return
        if (!::prefs.isInitialized) return
        try {
            val current = _dailyPlays.value.toMutableList()
            val existing = current.find { it.songId == songId }
            if (existing != null) {
                // Add to existing duration
                val idx = current.indexOf(existing)
                current[idx] = existing.copy(playCount = existing.playCount + secondsToAdd)
            } else {
                // New song — add with the duration
                current.add(DailyPlayCount(songId, artist, secondsToAdd))
            }
            // ★ Sort by duration descending (longest played = first)
            current.sortByDescending { it.playCount }
            _dailyPlays.value = current
            saveDailyPlays(current)
        } catch (_: Exception) { }
    }

    private fun saveDailyPlays(entries: List<DailyPlayCount>) {
        if (!::prefs.isInitialized) return
        try {
            val root = org.json.JSONObject()
            root.put("date", todayString())
            val durations = org.json.JSONObject()
            val artists = org.json.JSONObject()
            entries.forEach { entry ->
                durations.put(entry.songId.toString(), entry.playCount)  // duration in seconds
                artists.put(entry.songId.toString(), entry.artist)
            }
            root.put("durations", durations)
            root.put("artists", artists)
            prefs.edit().putString(KEY_DAILY_PLAYS, root.toString()).apply()
        } catch (_: Exception) { }
    }

    /**
     * Get the top [n] most-played songs today (by duration).
     * Returns at most [n] entries. Fully guarded — never crashes.
     */
    fun getTopPlayedToday(n: Int): List<DailyPlayCount> {
        if (!::prefs.isInitialized) return emptyList()
        return _dailyPlays.value.take(n)
    }
}
