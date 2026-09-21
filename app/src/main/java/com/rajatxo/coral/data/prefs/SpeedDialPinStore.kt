package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the set of "pinned" song IDs for the Speed Dial.
 *
 * Users can long-press a Speed Dial card to pin a song (max 5).
 * Pinned songs are persisted across app restarts via SharedPreferences.
 *
 * The pin state is exposed as a [StateFlow] so Compose can observe
 * changes and re-render cards when a song is pinned/unpinned.
 */
object SpeedDialPinStore {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_PINNED = "speed_dial_pinned_song_ids"
    private const val MAX_PINS = 5

    private lateinit var prefs: android.content.SharedPreferences

    private val _pinnedIds = MutableStateFlow<Set<Long>>(emptySet())
    /** The current set of pinned song IDs. Observe this to react to pin/unpin. */
    val pinnedIds: StateFlow<Set<Long>> = _pinnedIds.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pinnedIds.value = prefs.getStringSet(KEY_PINNED, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /** Returns true if the song is currently pinned. */
    fun isPinned(songId: Long): Boolean = songId in _pinnedIds.value

    /**
     * Pin a song. Returns true if pinned (or already pinned).
     * Returns false if the max (5) has been reached and the song isn't
     * already pinned.
     */
    fun pin(songId: Long): Boolean {
        val current = _pinnedIds.value
        if (songId in current) return true
        if (current.size >= MAX_PINS) return false
        val updated = current + songId
        prefs.edit().putStringSet(KEY_PINNED, updated.map { it.toString() }.toSet()).apply()
        _pinnedIds.value = updated
        return true
    }

    /** Unpin a song. */
    fun unpin(songId: Long) {
        val current = _pinnedIds.value
        if (songId !in current) return
        val updated = current - songId
        prefs.edit().putStringSet(KEY_PINNED, updated.map { it.toString() }.toSet()).apply()
        _pinnedIds.value = updated
    }

    /** The maximum number of songs a user can pin. */
    const val MAX_PINNED = MAX_PINS
}
