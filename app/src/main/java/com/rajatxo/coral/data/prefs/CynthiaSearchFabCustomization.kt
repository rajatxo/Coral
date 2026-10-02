package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA search FAB customization (size, corner radius, shape).
 * SEPARATE from Astra. Cynthia-only.
 *
 * Defaults:
 *   - Size: 52dp (diameter)
 *   - Corner radius: 16dp (rounded square)
 *   - Shape: ROUNDED
 */
object CynthiaSearchFabCustomization {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_SIZE = "cynthia_search_size_c2"
    private const val KEY_CORNER = "cynthia_search_corner_c2"
    private const val KEY_SHAPE = "cynthia_search_shape_c2"

    // ★ User-specified defaults: 51dp size, CIRCLE shape (corner irrelevant for circle).
    const val DEFAULT_SIZE_DP = 51f
    const val DEFAULT_CORNER_DP = 26f
    val DEFAULT_SHAPE = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.CIRCLE

    private lateinit var prefs: android.content.SharedPreferences

    private val _customization = MutableStateFlow(
        SearchFabCustomization(DEFAULT_SIZE_DP, DEFAULT_CORNER_DP, DEFAULT_SHAPE)
    )
    val customization: StateFlow<SearchFabCustomization> = _customization.asStateFlow()

    data class SearchFabCustomization(
        val sizeDp: Float,
        val cornerRadiusDp: Float,
        val shape: com.rajatxo.coral.ui.cynthia.CynthiaCustomShape
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _customization.value = SearchFabCustomization(
            sizeDp = prefs.getFloat(KEY_SIZE, DEFAULT_SIZE_DP),
            cornerRadiusDp = prefs.getFloat(KEY_CORNER, DEFAULT_CORNER_DP),
            shape = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.entries
                .getOrElse(prefs.getInt(KEY_SHAPE, DEFAULT_SHAPE.ordinal)) { DEFAULT_SHAPE }
        )
    }

    fun setSize(sizeDp: Float) {
        prefs.edit().putFloat(KEY_SIZE, sizeDp).apply()
        _customization.value = _customization.value.copy(sizeDp = sizeDp)
    }

    fun setCornerRadius(cornerRadiusDp: Float) {
        prefs.edit().putFloat(KEY_CORNER, cornerRadiusDp).apply()
        _customization.value = _customization.value.copy(cornerRadiusDp = cornerRadiusDp)
    }

    fun setShape(shape: com.rajatxo.coral.ui.cynthia.CynthiaCustomShape) {
        prefs.edit().putInt(KEY_SHAPE, shape.ordinal).apply()
        _customization.value = _customization.value.copy(shape = shape)
    }

    fun reset() {
        setSize(DEFAULT_SIZE_DP)
        setCornerRadius(DEFAULT_CORNER_DP)
        setShape(DEFAULT_SHAPE)
    }
}
