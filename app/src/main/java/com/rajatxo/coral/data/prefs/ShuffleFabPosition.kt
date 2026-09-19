package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the shuffle FAB's position across app restarts.
 *
 * Same pattern as SearchFabPosition — stores X and Y as fractions of
 * screen size (0.0 - 1.0). Default position: (0.85, 0.55) — right
 * side, ABOVE the search FAB (which sits at 0.75).
 *
 * Keys are versioned (_v1) so changes to defaults are picked up by
 * existing users.
 */
object ShuffleFabPosition {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "shuffle_fab_x_v1"
    private const val KEY_Y = "shuffle_fab_y_v1"

    /** Default: 85% from left (right side), 55% from top (above search FAB) */
    private const val DEFAULT_X = 0.85f
    private const val DEFAULT_Y = 0.55f

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
     * Y is constrained to a usable range (0.05 - 0.95).
     */
    fun setPosition(xFraction: Float, yFraction: Float) {
        val clampedX = xFraction.coerceIn(0.5f, 0.97f)
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
