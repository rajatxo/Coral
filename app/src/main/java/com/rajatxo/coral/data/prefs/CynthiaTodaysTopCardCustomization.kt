package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the CYNTHIA TodaysTopCard customization (size, corner, shape,
 * position offset). Same pattern as CynthiaNavBarCustomization.
 */
object CynthiaTodaysTopCardCustomization {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_WIDTH = "cynthia_top_card_width_c2"
    private const val KEY_HEIGHT = "cynthia_top_card_height_c2"
    private const val KEY_CORNER = "cynthia_top_card_corner_c2"
    private const val KEY_SHAPE = "cynthia_top_card_shape_c2"
    private const val KEY_OFFSET_X = "cynthia_top_card_offset_x_c1"
    private const val KEY_OFFSET_Y = "cynthia_top_card_offset_y_c1"

    const val DEFAULT_WIDTH_DP = 320f
    const val DEFAULT_HEIGHT_DP = 110f
    const val DEFAULT_CORNER_DP = 10f
    val DEFAULT_SHAPE = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.PILL

    // ★ Default position: 0,0 = centered over speed dial first row
    const val DEFAULT_OFFSET_X = 0f
    const val DEFAULT_OFFSET_Y = 0f

    private lateinit var prefs: android.content.SharedPreferences

    private val _customization = MutableStateFlow(
        CardCustomization(
            DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP, DEFAULT_CORNER_DP, DEFAULT_SHAPE,
            DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y
        )
    )
    val customization: StateFlow<CardCustomization> = _customization.asStateFlow()

    data class CardCustomization(
        val widthDp: Float,
        val heightDp: Float,
        val cornerRadiusDp: Float,
        val shape: com.rajatxo.coral.ui.cynthia.CynthiaCustomShape,
        val offsetX: Float,  // card drag position X (px)
        val offsetY: Float   // card drag position Y (px)
    )

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _customization.value = CardCustomization(
            widthDp = prefs.getFloat(KEY_WIDTH, DEFAULT_WIDTH_DP),
            heightDp = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT_DP),
            cornerRadiusDp = prefs.getFloat(KEY_CORNER, DEFAULT_CORNER_DP),
            shape = com.rajatxo.coral.ui.cynthia.CynthiaCustomShape.entries
                .getOrElse(prefs.getInt(KEY_SHAPE, DEFAULT_SHAPE.ordinal)) { DEFAULT_SHAPE },
            offsetX = prefs.getFloat(KEY_OFFSET_X, DEFAULT_OFFSET_X),
            offsetY = prefs.getFloat(KEY_OFFSET_Y, DEFAULT_OFFSET_Y)
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

    fun setOffset(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_OFFSET_X, x)
            .putFloat(KEY_OFFSET_Y, y)
            .apply()
        _customization.value = _customization.value.copy(offsetX = x, offsetY = y)
    }

    fun reset() {
        setWidth(DEFAULT_WIDTH_DP)
        setHeight(DEFAULT_HEIGHT_DP)
        setCornerRadius(DEFAULT_CORNER_DP)
        setShape(DEFAULT_SHAPE)
        setOffset(DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y)
    }
}
