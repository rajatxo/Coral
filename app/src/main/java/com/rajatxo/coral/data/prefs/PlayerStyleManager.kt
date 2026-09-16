package com.rajatxo.coral.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlayerStyleManager — holds the user's preferred player design style.
 *
 * Two styles:
 *   - CORAL: the immersive blurred-bg player (100% blur + medium-blur
 *     bridge + sharp art with symmetric fades + glass capsule + smooth
 *     seekbar + glossy capsule play/pause)
 *   - PROFILE: the dating-app profile style player (full-bleed art 65% +
 *     vertical action pill + left-aligned text + info chips)
 *
 * Default: Spiral 2.0
 */
object PlayerStyleManager {

    const val CORAL = "Coral"
    const val PROFILE = "Profile"
    const val SPIRAL = "Spiral"
    const val SPIRAL_2 = "Spiral 2.0"
    const val SPIRAL_3 = "Spiral 3.0"

    private val _playerStyle = MutableStateFlow(SPIRAL_2)
    val playerStyle: StateFlow<String> = _playerStyle.asStateFlow()

    fun setPlayerStyle(style: String) {
        _playerStyle.value = style
    }
}
