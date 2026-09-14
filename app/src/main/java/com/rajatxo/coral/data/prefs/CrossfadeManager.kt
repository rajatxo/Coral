package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CrossfadeManager — holds the user's crossfade duration preference.
 *
 * Range: 0 (off) to 12 seconds.
 * 0 = no crossfade (gapless or gap between songs)
 * 1-12 = crossfade duration in seconds
 *
 * Default: 0 (off)
 *
 * Persists to SharedPreferences ('coral_prefs').
 */
object CrossfadeManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_CROSSFADE = "crossfade_duration"

    private var prefs: android.content.SharedPreferences? = null

    private val _crossfadeDuration = MutableStateFlow(0)
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    /**
     * Call once from Application.onCreate() to load the persisted value.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _crossfadeDuration.value = prefs?.getInt(KEY_CROSSFADE, 0) ?: 0
    }

    fun setCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _crossfadeDuration.value = clamped
        prefs?.edit()?.putInt(KEY_CROSSFADE, clamped)?.apply()
    }
}
