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
 * Default: X = 0.5 (centered), Y = 0.91 (near the very bottom edge,
 * closer to the system nav buttons than before).
 *
 * Layout (mini player TRACKS the nav bar — no longer fixed):
 *   search FAB            ← independent, draggable
 *   mini player           ← dynamic, sits a fixed 6dp above the nav bar
 *   nav bar (Y≈0.91)      ← draggable, default closer to system nav
 *
 * Why 0.91 (was 0.87):
 *   On a 780dp screen, capsule center at Y=0.91 = 780 * (1-0.91) = 70dp
 *   from bottom. Capsule is 52dp tall → bottom edge at 70-26 = 44dp
 *   from screen bottom. With gesture nav (~24dp), gap to system nav
 *   ≈ 20dp. Was 51dp at Y=0.87. Tighter, but not cramped.
 *
 * Mini player tracking math (in HomeScreen.kt):
 *   navBarTopFromBottom = screenHeight * (1 - yFrac) + 26dp
 *   miniPlayerBottom    = navBarTopFromBottom + 6dp  (gap)
 *   .padding(bottom = miniPlayerBottom - systemNavInset)
 *
 * When the user drags the nav bar, the mini player recomposes and
 * follows — same 6dp gap maintained at all times.
 *
 * NOTE: Keys are versioned (_v6) so changes to defaults are picked up
 * by existing users who have stale saved positions from the old layout.
 *
 * Same pattern as SearchFabPosition — SharedPreferences backed,
 * read synchronously on init, written on drag end.
 */
object TabCapsulePosition {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_X = "tab_capsule_x_v6"
    private const val KEY_Y = "tab_capsule_y_v6"
    private const val DEFAULT_X = 0.5f
    private const val DEFAULT_Y = 0.91f

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
