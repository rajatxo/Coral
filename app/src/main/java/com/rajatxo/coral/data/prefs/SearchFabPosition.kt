package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the search FAB's position across app restarts.
 *
 * Stores X and Y as fractions of screen size (0.0 - 1.0) so the position
 * scales correctly across different screen resolutions.
 *
 * Default position: (0.85, 0.65) — right side, ABOVE the mini player.
 * Stacked vertically: search FAB (top) → mini player (middle) → nav bar
 * (bottom). Previous default (0.75) overlapped with the mini player;
 * moved up to 0.65 for clear vertical separation.
 *
 * NOTE: Keys are versioned (_v2) so changes to defaults are picked up
 * by existing users who have stale saved positions from the old layout.
 */
object SearchFabPosition {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "search_fab_x_v2"
    private const val KEY_Y = "search_fab_y_v2"

    /** Default: 85% from left (right side), 65% from top (above mini player) */
    private const val DEFAULT_X = 0.85f
    private const val DEFAULT_Y = 0.65f

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

    /**
     * Save a new position (as fractions of screen size, 0.0-1.0).
     * X is constrained to the right half (0.5 - 1.0).
     * Y is constrained to a usable range (0.1 - 0.95).
     */
    fun setPosition(xFraction: Float, yFraction: Float) {
        val clampedX = xFraction.coerceIn(0.5f, 0.97f)  // right half only
        val clampedY = yFraction.coerceIn(0.05f, 0.95f)
        _position.value = Pair(clampedX, clampedY)
        prefs.edit()
            .putFloat(KEY_X, clampedX)
            .putFloat(KEY_Y, clampedY)
            .apply()
    }

    /** Reset to default position. */
    fun reset() {
        setPosition(DEFAULT_X, DEFAULT_Y)
    }
}
