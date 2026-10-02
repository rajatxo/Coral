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
    private const val KEY_X = "cynthia_tab_capsule_x_c3"
    private const val KEY_Y = "cynthia_tab_capsule_y_c3"
    // ★ Cynthia defaults — user-specified (latest):
    //   X = 0.39 (39%), Y = 0.89 (89%) — near bottom-left.
    //   Width/height/corner/shape are in CynthiaNavBarCustomization.
    const val DEFAULT_X = 0.39f
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
        prefs.edit()
            .putFloat(KEY_X, xFraction)
            .putFloat(KEY_Y, yFraction)
            .apply()
        _position.value = Pair(xFraction, yFraction)
    }
}
