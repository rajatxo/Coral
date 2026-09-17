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
 * Default: X = 0.5 (centered), Y = 0.88 (near the very bottom edge,
 * BELOW the mini player with a 15dp gap).
 *
 * Stacked vertical layout:
 *   search FAB (Y≈0.65)  ← above
 *   mini player (fixed)  ← middle, padding(bottom=108dp)
 *   nav bar (Y≈0.88)     ← below, 15dp gap below the mini player
 *
 * The mini player position is FIXED (not draggable) — only the search
 * FAB and nav bar can be dragged. The nav bar's default Y is tuned so
 * that on a typical 780dp screen the gap between the mini player's
 * bottom edge (~132dp from screen bottom) and the nav bar's top edge
 * is exactly 15dp.
 *
 * Math: 132 - 15 = 117dp (nav bar top). Nav bar is 52dp tall, so its
 * center sits at 117 - 26 = 91dp from bottom. Y = 1 - (91/780) ≈ 0.883.
 *
 * NOTE: Keys are versioned (_v4) so changes to defaults are picked up
 * by existing users who have stale saved positions from the old layout.
 *
 * Same pattern as SearchFabPosition — SharedPreferences backed,
 * read synchronously on init, written on drag end.
 */
object TabCapsulePosition {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "tab_capsule_x_v4"
    private const val KEY_Y = "tab_capsule_y_v4"
    private const val DEFAULT_X = 0.5f
    private const val DEFAULT_Y = 0.88f

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
