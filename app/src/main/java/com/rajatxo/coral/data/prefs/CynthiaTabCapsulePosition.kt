package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA nav bar (TabCapsule) position — SEPARATE from Astra's
 * TabCapsulePosition so changes in one UI do NOT bleed into the other.
 *
 * SharedPreferences keys are different (cynthia_* prefix + _c1 version tag)
 * so even though both UIs share the same coral_prefs file, their saved
 * positions are independent.
 *
 * Default: X = 0.5 (centered), Y = 0.89 (near bottom) — Cynthia's own
 * defaults, NOT Astra's (Astra uses 0.283 / 0.889).
 */
object CynthiaTabCapsulePosition {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "cynthia_tab_capsule_x_c2"
    private const val KEY_Y = "cynthia_tab_capsule_y_c2"
    // ★ Cynthia defaults — user-specified: X = 0.283 (LEFT side), Y = 0.889
    //   (near bottom). Nav bar is 52dp tall, sits on the LEFT side of the
    //   screen next to the search circle (which is on its RIGHT, same Y).
    private const val DEFAULT_X = 0.283f
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
        prefs.edit()
            .putFloat(KEY_X, xFraction)
            .putFloat(KEY_Y, yFraction)
            .apply()
        _position.value = Pair(xFraction, yFraction)
    }
}
