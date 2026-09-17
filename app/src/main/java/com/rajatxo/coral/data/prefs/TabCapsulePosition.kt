package com.rajatxo.coral.data.prefs

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the nav bar (TabCapsule) position across app restarts.
 *
 * Position is stored as fractions of screen size (0..1 for both X and Y).
 * Default: X = 0.5 (centered), Y = 0.92 (near the very bottom edge,
 * BELOW the mini player).
 *
 * Stacked vertically: search FAB (top) → mini player (middle) → nav bar
 * (bottom). Previous default Y=0.85 sat too close to the mini player
 * (which has ~72dp bottom padding) causing overlap. Moved down to 0.92
 * so the nav bar sits clearly below the mini player, just above the
 * system navigation bar.
 *
 * NOTE: Keys are versioned (_v2) so changes to defaults are picked up
 * by existing users who have stale saved positions from the old layout.
 *
 * Same pattern as SearchFabPosition — SharedPreferences backed,
 * read synchronously on init, written on drag end.
 */
object TabCapsulePosition {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "tab_capsule_x_v2"
    private const val KEY_Y = "tab_capsule_y_v2"
    private const val DEFAULT_X = 0.5f
    private const val DEFAULT_Y = 0.92f

    private lateinit var prefs: android.content.SharedPreferences

    private val _position = MutableStateFlow(Pair(DEFAULT_X, DEFAULT_Y))
    val position: StateFlow<Pair<Float, Float>> = _position.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _position.value = Pair(
            prefs.getFloat(KEY_X, DEFAULT_X),
            prefs.getFloat(KEY_Y, DEFAULT_Y)
        )
    }

    fun setPosition(xFraction: Float, yFraction: Float) {
        prefs.edit()
            .putFloat(KEY_X, xFraction)
            .putFloat(KEY_Y, yFraction)
            .apply()
        _position.value = Pair(xFraction, yFraction)
    }
}
