package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA TodaysTopCard customization (size, corner radius, shape).
 * Same pattern as CynthiaNavBarCustomization.
 *
 * Defaults will be set by the user after first build.
 */
object CynthiaTodaysTopCardCustomization {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_WIDTH = "cynthia_top_card_width_c1"
    private const val KEY_HEIGHT = "cynthia_top_card_height_c1"
    private const val KEY_CORNER = "cynthia_top_card_corner_c1"
    private const val KEY_SHAPE = "cynthia_top_card_shape_c1"

    // ★ Temporary defaults — user will tell me the exact values
    const val DEFAULT_WIDTH_DP = 330f
    const val DEFAULT_HEIGHT_DP = 110f
    const val DEFAULT_CORNER_DP = 20f
    val DEFAULT_SHAPE = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.PILL

    private lateinit var prefs: android.content.SharedPreferences

    private val _customization = MutableStateFlow(
        CardCustomization(DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP, DEFAULT_CORNER_DP, DEFAULT_SHAPE)
    )
    val customization: StateFlow<CardCustomization> = _customization.asStateFlow()

    data class CardCustomization(
        val widthDp: Float,
        val heightDp: Float,
        val cornerRadiusDp: Float,
        val shape: com.rajatxo.coral.ui.cynthia.CynthiaCustomShape
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _customization.value = CardCustomization(
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
