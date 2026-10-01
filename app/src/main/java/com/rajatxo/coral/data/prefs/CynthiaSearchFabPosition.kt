package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA search FAB position — SEPARATE from Astra's
 * SearchFabPosition so changes in one UI do NOT bleed into the other.
 *
 * SharedPreferences keys are different (cynthia_* prefix + _c1 version tag)
 * so even though both UIs share the same coral_prefs file, their saved
 * positions are independent.
 *
 * Default: X = 0.84 (right side), Y = 0.89 (near bottom) — Cynthia's own
 * defaults, NOT Astra's. Cynthia positions the search FAB on the right
 * next to the centered nav bar.
 */
object CynthiaSearchFabPosition {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "cynthia_search_fab_x_c2"
    private const val KEY_Y = "cynthia_search_fab_y_c2"
    // ★ Cynthia defaults — user-specified layout:
    //   - Y = 0.889 (SAME horizontal line as nav bar — they share Y)
    //   - X = 0.565 (to the RIGHT of the nav bar with a gap)
    //   Math (typical 393dp-wide phone):
    //     Nav bar center X = 0.283 * 393 = 111dp
    //     Nav bar right edge = 111 + 75 (half of 150dp width) = 186dp
    //     Gap = 10dp
    //     Search FAB center X = 186 + 10 + 26 (half of 52dp) = 222dp
    //     As fraction = 222 / 393 ≈ 0.565
    //   This puts the search FAB immediately to the right of the nav bar
    //   with a 10dp gap, both on the same Y line (0.889).
    private const val DEFAULT_X = 0.565f
    private const val DEFAULT_Y = 0.889f

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
        val clampedX = xFraction.coerceIn(0.05f, 0.95f)
        val clampedY = yFraction.coerceIn(0.05f, 0.95f)
        prefs.edit()
            .putFloat(KEY_X, clampedX)
            .putFloat(KEY_Y, clampedY)
            .apply()
        _position.value = Pair(clampedX, clampedY)
    }
}
