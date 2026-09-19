package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ThemeManager — holds the user's preferred app theme.
 *
 * Three themes:
 *   - DARK:    Whole app is dark black (default, the original Coral look)
 *   - LIGHT:   Whole app is light white (text colors invert, backgrounds
 *              become light)
 *   - DYNAMIC: Placeholder for a future theme that adapts to the current
 *              song's palette or system setting. For now it falls back
 *              to DARK until the user specifies what they want.
 *
 * The theme is observed app-wide via [theme] StateFlow. UI components
 * read [colors] to get the current theme's color palette.
 *
 * Persistence: SharedPreferences ('coral_prefs').
 */
object ThemeManager {

    const val DARK = "Dark"
    const val LIGHT = "Light"
    const val DYNAMIC = "Dynamic"

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_THEME = "app_theme_v1"

    private lateinit var prefs: android.content.SharedPreferences

    private val _theme = MutableStateFlow(DARK)
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, DARK) ?: DARK
    }

    fun setTheme(theme: String) {
        val clamped = when (theme) {
            DARK, LIGHT, DYNAMIC -> theme
            else -> DARK
        }
        prefs.edit().putString(KEY_THEME, clamped).apply()
        _theme.value = clamped
    }

    /** Convenience: is the current theme LIGHT? */
    fun isLight(): Boolean = _theme.value == LIGHT

    /** Convenience: is the current theme DARK? */
    fun isDark(): Boolean = _theme.value == DARK || _theme.value == DYNAMIC
}

/**
 * ThemeColors — the color palette for the current theme.
 *
 * UI components read these instead of hardcoding colors, so the whole
 * app can switch between dark and light by changing one value.
 */
data class ThemeColors(
    val background: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val surfaceVariant: androidx.compose.ui.graphics.Color,
    val textPrimary: androidx.compose.ui.graphics.Color,
    val textSecondary: androidx.compose.ui.graphics.Color,
    val textTertiary: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val overlay: androidx.compose.ui.graphics.Color,
    val cardBorder: androidx.compose.ui.graphics.Color
) {
    companion object {
        val Dark = ThemeColors(
            background = androidx.compose.ui.graphics.Color(0xFF0A0A12),
            surface = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
            textPrimary = androidx.compose.ui.graphics.Color.White,
            textSecondary = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
            textTertiary = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
            accent = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
            overlay = androidx.compose.ui.graphics.Color.Black,
            cardBorder = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
        )

        val Light = ThemeColors(
            background = androidx.compose.ui.graphics.Color(0xFFF2F2F7),
            surface = androidx.compose.ui.graphics.Color.White,
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
            textPrimary = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            textSecondary = androidx.compose.ui.graphics.Color(0xFF8E8E93),
            textTertiary = androidx.compose.ui.graphics.Color(0xFFAEAEB2),
            accent = androidx.compose.ui.graphics.Color(0xFF007AFF),
            overlay = androidx.compose.ui.graphics.Color.Black,
            cardBorder = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f)
        )
    }
}

/** Get the current ThemeColors based on the ThemeManager's state. */
fun currentThemeColors(): ThemeColors {
    return if (ThemeManager.isLight()) ThemeColors.Light else ThemeColors.Dark
}
