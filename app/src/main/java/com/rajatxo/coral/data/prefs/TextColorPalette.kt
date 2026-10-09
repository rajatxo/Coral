package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.graphics.Color

/**
 * TextColorPalette — manages the user's selected text color for the playlist wheel.
 *
 * Stores the selected color index in SharedPreferences.
 * Provides 24 curated vibrant colors that are readable on dark backgrounds.
 */
object TextColorPalette {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_COLOR_INDEX = "text_color_index_v1"

    /** 24 vibrant colors — all chosen for readability on dark backgrounds. */
    val colors = listOf(
        Color(0xFFF5EBD0),  // 0: Warm Cream (default)
        Color(0xFFFF6B6B),  // 1: Coral Red
        Color(0xFFFF9F43),  // 2: Orange
        Color(0xFFFFD93D),  // 3: Gold Yellow
        Color(0xFF6BCB77),  // 4: Emerald Green
        Color(0xFF4DABF7),  // 5: Sky Blue
        Color(0xFF7950F2),  // 6: Violet
        Color(0xFFFF6B9D),  // 7: Hot Pink
        Color(0xFF51CF66),  // 8: Bright Green
        Color(0xFF22D3EE),  // 9: Cyan
        Color(0xFFA78BFA),  // 10: Lavender
        Color(0xFFFBBF24),  // 11: Amber
        Color(0xFFFB7185),  // 12: Rose
        Color(0xFF34D399),  // 13: Teal Green
        Color(0xFF60A5FA),  // 14: Blue
        Color(0xFFC084FC),  // 15: Purple
        Color(0xFFFDE047),  // 16: Bright Yellow
        Color(0xFFEC4899),  // 17: Magenta
        Color(0xFF2DD4BF),  // 18: Turquoise
        Color(0xFFF97316),  // 19: Burnt Orange
        Color(0xFF818CF8),  // 20: Indigo
        Color(0xFFF0ABFC),  // 21: Fuchsia
        Color(0xFFE4D4F9),  // 22: Lilac
        Color(0xFFEF4444),  // 23: Red
    )

    val colorNames = listOf(
        "Warm Cream", "Coral Red", "Orange", "Gold", "Emerald",
        "Sky Blue", "Violet", "Hot Pink", "Bright Green", "Cyan",
        "Lavender", "Amber", "Rose", "Teal", "Blue",
        "Purple", "Yellow", "Magenta", "Turquoise", "Burnt Orange",
        "Indigo", "Fuchsia", "Lilac", "Red"
    )

    private lateinit var prefs: android.content.SharedPreferences

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    val selectedColor: Color get() = colors[_selectedIndex.value]

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _selectedIndex.value = prefs.getInt(KEY_COLOR_INDEX, 0)
    }

    fun setSelectedIndex(index: Int) {
        _selectedIndex.value = index
        prefs.edit().putInt(KEY_COLOR_INDEX, index).apply()
    }
}
