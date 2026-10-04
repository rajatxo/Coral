package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the set of "pinned" song IDs for the Speed Dial.
 *
 * Users can pin songs via long-press on a Speed Dial card OR via the
 * pin icon on the TodaysTopCard. Pinned songs are persisted across
 * app restarts via SharedPreferences.
 *
 * ORDER MATTERS:
 *   - The pinned list is a List, NOT a Set — order is preserved.
 *   - When a new song is pinned, it goes to the FRONT (index 0).
 *   - If the list already has MAX_PINS (5) songs, the LAST one
 *     (index 4) is dropped — i.e. the oldest pin gets kicked out.
 *   - Example: list is [A, B, C, D, E]. User pins F.
 *     New list becomes [F, A, B, C, D]. E is removed.
 *
 *   - If the user pins a song that's already in the list, it gets
 *     MOVED to the front (no duplicates added). This is intentional —
 *     re-pinning = "make this my #1 pinned song".
 *
 * The pin state is exposed as a [StateFlow] so Compose can observe
 * changes and re-render cards when a song is pinned/unpinned.
 */
object SpeedDialPinStore {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_PINNED = "speed_dial_pinned_song_ids_v2"
    private const val MAX_PINS = 5

    private lateinit var prefs: android.content.SharedPreferences

    private val _pinnedIds = MutableStateFlow<List<Long>>(emptyList())
    /** The current ordered list of pinned song IDs. Index 0 = newest pin. */
    val pinnedIds: StateFlow<List<Long>> = _pinnedIds.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pinnedIds.value = prefs.getString(KEY_PINNED, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.take(MAX_PINS)
            ?: emptyList()
    }

    /** Returns true if the song is currently pinned. */
    fun isPinned(songId: Long): Boolean = songId in _pinnedIds.value

    /**
     * Pin a song.
     *
     * - If already pinned: moves it to the FRONT (index 0).
     * - If new and list is full (5): drops the last (oldest) and
     *   prepends the new one.
     * - If new and list has room: just prepends.
     *
     * Always returns true (pin always succeeds — we either add or
     * move-to-front, never reject).
     */
    fun pin(songId: Long): Boolean {
        val current = _pinnedIds.value
        val withoutSong = current - songId
        val updated = (listOf(songId) + withoutSong).take(MAX_PINS)
        prefs.edit().putString(KEY_PINNED, updated.joinToString(",")).apply()
        _pinnedIds.value = updated
        return true
    }

    /** Unpin a song. */
    fun unpin(songId: Long) {
        val current = _pinnedIds.value
        if (songId !in current) return
        val updated = current - songId
        prefs.edit().putString(KEY_PINNED, updated.joinToString(",")).apply()
        _pinnedIds.value = updated
    }

    /** Toggle pin state. Returns the new pinned state. */
    fun togglePin(songId: Long): Boolean {
        return if (isPinned(songId)) {
            unpin(songId)
            false
        } else {
            pin(songId)
            true
        }
    }

    /** The maximum number of songs a user can pin. */
    const val MAX_PINNED = MAX_PINS
}
