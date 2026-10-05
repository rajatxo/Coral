package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlayerStyleManager — holds the user's preferred player design style.
 *
 * Default: Coral (on first install). Coral is the new glass-morphism-first
 * UI built from scratch with proper kyant backdrop architecture.
 * Spiral/Spiral 2.0/Spiral 3.0 are the legacy UIs, kept as alternatives.
 */
object PlayerStyleManager {

    const val CORAL_GLASS = "Coral Glass"  // ★ NEW — glass morphism player UI
    const val CORAL = "Coral"
    const val PROFILE = "Profile"
    const val SPIRAL = "Spiral"
    const val SPIRAL_2 = "Spiral 2.0"
    const val SPIRAL_3 = "Spiral 3.0"

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_PLAYER_STYLE = "player_style_v5"  // bumped v4→v5 to add CORAL_GLASS

    private lateinit var prefs: android.content.SharedPreferences

    private val _playerStyle = MutableStateFlow(CORAL_GLASS)  // ★ Default = CORAL_GLASS (new glass player)
    val playerStyle: StateFlow<String> = _playerStyle.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PLAYER_STYLE, null)
        if (saved != null) {
            _playerStyle.value = saved
        }
    }

    fun setPlayerStyle(style: String) {
        _playerStyle.value = style
        prefs.edit().putString(KEY_PLAYER_STYLE, style).apply()
    }
}
