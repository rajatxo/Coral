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
    private const val KEY_X = "cynthia_search_fab_x_c3"
    private const val KEY_Y = "cynthia_search_fab_y_c3"
    // ★ Cynthia defaults — user-specified (latest):
    //   X = 0.70 (70%), Y = 0.89 (89%) — to the right of the nav bar, same Y.
    //   Size/shape are in CynthiaSearchFabCustomization.
    const val DEFAULT_X = 0.70f
    const val DEFAULT_Y = 0.89f

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

    /** Reset to default position. */
    fun reset() {
        setPosition(DEFAULT_X, DEFAULT_Y)
    }
}
