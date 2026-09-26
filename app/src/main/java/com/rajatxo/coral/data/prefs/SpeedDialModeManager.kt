package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpeedDialModeManager — stores the user's preferred Speed Dial mode.
 *
 * Two modes:
 *   - RANDOM      — completely random songs (the original behavior)
 *   - LAST_PLAYED — biased toward the most-recently-played artist
 *                   (80% songs from that artist, 20% from other artists)
 *
 * Stored in SharedPreferences as a String. Survives app restarts.
 */
object SpeedDialModeManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_MODE = "speed_dial_mode_v1"

    enum class SpeedDialMode(val displayName: String) {
        RANDOM("Random songs"),
        LAST_PLAYED("Last played")
    }

    private lateinit var prefs: android.content.SharedPreferences

    private val _mode = MutableStateFlow(SpeedDialMode.RANDOM)
    val mode: StateFlow<SpeedDialMode> = _mode.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODE, null)
        _mode.value = try {
            saved?.let { SpeedDialMode.valueOf(it) } ?: SpeedDialMode.RANDOM
        } catch (_: Exception) {
            SpeedDialMode.RANDOM
        }
    }

    fun setMode(mode: SpeedDialMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}
