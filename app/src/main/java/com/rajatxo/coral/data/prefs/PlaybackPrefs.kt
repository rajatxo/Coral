package com.rajatxo.coral.data.prefs

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackPrefs — Coral's playback preference singleton.
 *
 * Holds 3 pieces of state, all persisted to SharedPreferences:
 *
 *   1. persistentQueueEnabled (default: false)
 *      When enabled, the current queue (list of song IDs) + current
 *      song index + playback position are saved periodically and
 *      restored on app launch. This survives full app kills (swipe
 *      away from recents, system OOM kill, etc.) — not just
 *      activity recreation.
 *
 *      When disabled, the queue is only held in the Media3 service's
 *      memory (which survives activity recreation but NOT a full kill).
 *
 *   2. repeatMode (default: REPEAT_MODE_ALL — auto-advance)
 *      Persists the user's loop selection (off / all / one). Previously
 *      hardcoded to REPEAT_MODE_ALL on every app launch, which reset
 *      the user's "loop one song" (infinity icon) selection on restart.
 *
 *   3. bluetoothResumeEnabled (default: false)
 *      When enabled, if a song is paused and the user connects a
 *      Bluetooth audio device (headphones, car, speaker), playback
 *      resumes automatically. When Bluetooth disconnects, playback
 *      pauses. This mirrors the behavior of most music apps.
 *
 * Persistence: SharedPreferences ('coral_prefs').
 */
object PlaybackPrefs {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_PERSISTENT_QUEUE = "persistent_queue_enabled"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_BLUETOOTH_RESUME = "bluetooth_resume_enabled"

    // --- Queue persistence (for restoring the queue after a full kill) ---
    private const val KEY_SAVED_QUEUE = "saved_queue_song_ids"
    private const val KEY_SAVED_QUEUE_INDEX = "saved_queue_index"
    private const val KEY_SAVED_QUEUE_POSITION = "saved_queue_position_ms"

    private lateinit var prefs: android.content.SharedPreferences

    // --- Persistent Queue toggle ---
    private val _persistentQueueEnabled = MutableStateFlow(false)
    val persistentQueueEnabled: StateFlow<Boolean> = _persistentQueueEnabled.asStateFlow()

    fun setPersistentQueueEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENT_QUEUE, enabled).apply()
        _persistentQueueEnabled.value = enabled
    }

    // --- Repeat mode ---
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    fun setRepeatMode(mode: Int) {
        prefs.edit().putInt(KEY_REPEAT_MODE, mode).apply()
        _repeatMode.value = mode
    }

    // --- Bluetooth resume toggle ---
    private val _bluetoothResumeEnabled = MutableStateFlow(false)
    val bluetoothResumeEnabled: StateFlow<Boolean> = _bluetoothResumeEnabled.asStateFlow()

    fun setBluetoothResumeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLUETOOTH_RESUME, enabled).apply()
        _bluetoothResumeEnabled.value = enabled
    }

    // --- Saved queue (for Persistent Queue feature) ---
    // Stores the queue as a comma-separated list of song IDs.
    // This is simpler than JSON and fast enough for a few hundred songs.
    fun saveQueue(songIds: List<Long>, currentIndex: Int, positionMs: Long) {
        if (!_persistentQueueEnabled.value) return
        prefs.edit()
            .putString(KEY_SAVED_QUEUE, songIds.joinToString(","))
            .putInt(KEY_SAVED_QUEUE_INDEX, currentIndex)
            .putLong(KEY_SAVED_QUEUE_POSITION, positionMs)
            .apply()
    }

    fun loadQueue(): Triple<List<Long>, Int, Long>? {
        if (!_persistentQueueEnabled.value) return null
        val queueStr = prefs.getString(KEY_SAVED_QUEUE, null) ?: return null
        if (queueStr.isEmpty()) return null
        val songIds = queueStr.split(",").mapNotNull { it.toLongOrNull() }
        if (songIds.isEmpty()) return null
        val index = prefs.getInt(KEY_SAVED_QUEUE_INDEX, 0).coerceIn(0, songIds.lastIndex)
        val position = prefs.getLong(KEY_SAVED_QUEUE_POSITION, 0L)
        return Triple(songIds, index, position)
    }

    fun clearSavedQueue() {
        prefs.edit()
            .remove(KEY_SAVED_QUEUE)
            .remove(KEY_SAVED_QUEUE_INDEX)
            .remove(KEY_SAVED_QUEUE_POSITION)
            .apply()
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _persistentQueueEnabled.value = prefs.getBoolean(KEY_PERSISTENT_QUEUE, false)
        _repeatMode.value = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_ALL)
        _bluetoothResumeEnabled.value = prefs.getBoolean(KEY_BLUETOOTH_RESUME, false)
    }
}
