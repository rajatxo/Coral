package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the search card customization (blur, darkness, height, corner).
 * Same pattern as CynthiaMiniPlayerCustomization.
 */
object SearchCardCustomization {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_BLUR = "search_card_blur_c2"
    private const val KEY_DARKNESS = "search_card_darkness_c2"
    private const val KEY_HEIGHT = "search_card_height_c2"
    private const val KEY_CORNER = "search_card_corner_c2"

    // ★ Defaults — user-specified values
    const val DEFAULT_BLUR = 49f        // blur radius in dp
    const val DEFAULT_DARKNESS = 0.15f   // 15% black tint
    const val DEFAULT_HEIGHT_EXTRA = -171f // -171dp fixed height (no miniplayer adjustment)
    const val DEFAULT_CORNER = 32f       // bottom corner radius in dp

    private lateinit var prefs: android.content.SharedPreferences

    data class SearchCardConfig(
        val blur: Float,
        val darkness: Float,
        val heightExtra: Float,
        val corner: Float
    )

    private val _config = MutableStateFlow(
        SearchCardConfig(DEFAULT_BLUR, DEFAULT_DARKNESS, DEFAULT_HEIGHT_EXTRA, DEFAULT_CORNER)
    )
    val config: StateFlow<SearchCardConfig> = _config.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _config.value = SearchCardConfig(
            blur = prefs.getFloat(KEY_BLUR, DEFAULT_BLUR),
            darkness = prefs.getFloat(KEY_DARKNESS, DEFAULT_DARKNESS),
            heightExtra = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT_EXTRA),
            corner = prefs.getFloat(KEY_CORNER, DEFAULT_CORNER)
        )
    }

    fun setBlur(value: Float) {
        prefs.edit().putFloat(KEY_BLUR, value).apply()
        _config.value = _config.value.copy(blur = value)
    }

    fun setDarkness(value: Float) {
        prefs.edit().putFloat(KEY_DARKNESS, value).apply()
        _config.value = _config.value.copy(darkness = value)
    }

    fun setHeightExtra(value: Float) {
        prefs.edit().putFloat(KEY_HEIGHT, value).apply()
        _config.value = _config.value.copy(heightExtra = value)
    }

    fun setCorner(value: Float) {
        prefs.edit().putFloat(KEY_CORNER, value).apply()
        _config.value = _config.value.copy(corner = value)
    }

    fun reset() {
        setBlur(DEFAULT_BLUR)
        setDarkness(DEFAULT_DARKNESS)
        setHeightExtra(DEFAULT_HEIGHT_EXTRA)
        setCorner(DEFAULT_CORNER)
    }
}
