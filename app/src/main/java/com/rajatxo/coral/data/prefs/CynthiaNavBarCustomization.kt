package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA nav bar customization (size, corner radius, shape).
 * SEPARATE from Astra. Cynthia-only.
 *
 * Defaults:
 *   - Size: 150dp wide, 52dp tall (standard)
 *   - Corner radius: 26dp (pill-like)
 *   - Shape: PILL
 */
object CynthiaNavBarCustomization {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_WIDTH = "cynthia_nav_width_c2"
    private const val KEY_HEIGHT = "cynthia_nav_height_c2"
    private const val KEY_CORNER = "cynthia_nav_corner_c2"
    private const val KEY_SHAPE = "cynthia_nav_shape_c2"

    // ★ User-specified defaults: 153dp wide, 50dp tall, 50 corner, PILL shape.
    const val DEFAULT_WIDTH_DP = 153f
    const val DEFAULT_HEIGHT_DP = 50f
    const val DEFAULT_CORNER_DP = 50f
    val DEFAULT_SHAPE = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.PILL

    private lateinit var prefs: android.content.SharedPreferences

    private val _customization = MutableStateFlow(
        NavBarCustomization(DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP, DEFAULT_CORNER_DP, DEFAULT_SHAPE)
    )
    val customization: StateFlow<NavBarCustomization> = _customization.asStateFlow()

    data class NavBarCustomization(
        val widthDp: Float,
        val heightDp: Float,
        val cornerRadiusDp: Float,
        val shape: com.rajatxo.coral.ui.cynthia.CynthiaCustomShape
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _customization.value = NavBarCustomization(
            widthDp = prefs.getFloat(KEY_WIDTH, DEFAULT_WIDTH_DP),
            heightDp = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT_DP),
            cornerRadiusDp = prefs.getFloat(KEY_CORNER, DEFAULT_CORNER_DP),
            shape = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.entries
                .getOrElse(prefs.getInt(KEY_SHAPE, DEFAULT_SHAPE.ordinal)) { DEFAULT_SHAPE }
        )
    }

    fun setWidth(widthDp: Float) {
        prefs.edit().putFloat(KEY_WIDTH, widthDp).apply()
        _customization.value = _customization.value.copy(widthDp = widthDp)
    }

    fun setHeight(heightDp: Float) {
        prefs.edit().putFloat(KEY_HEIGHT, heightDp).apply()
        _customization.value = _customization.value.copy(heightDp = heightDp)
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
        setWidth(DEFAULT_WIDTH_DP)
        setHeight(DEFAULT_HEIGHT_DP)
        setCornerRadius(DEFAULT_CORNER_DP)
        setShape(DEFAULT_SHAPE)
    }
}
