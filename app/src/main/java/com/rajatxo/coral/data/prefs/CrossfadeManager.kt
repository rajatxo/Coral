package com.rajatxo.coral.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CrossfadeManager — holds the user's crossfade duration preference.
 *
 * Range: 0 (off) to 12 seconds.
 * 0 = no crossfade (gapless or gap between songs)
 * 1-12 = crossfade duration in seconds
 *
 * Default: 0 (off)
 */
object CrossfadeManager {

    private val _crossfadeDuration = MutableStateFlow(0)
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDuration.value = seconds.coerceIn(0, 12)
    }
}
