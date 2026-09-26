package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LyricsAnimationManager — persists the user's preferred highlight
 * animation for exact-match lyrics candidates in the Lyrics Picker.
 *
 * Two options:
 *   • PULSE_RING — soft glowing ring expands outward from the duration
 *     circle, fades as it grows. Layered echo (two rings staggered by
 *     1s). Subtle, premium, "heartbeat confirmation".
 *
 *   • SUN_GLOW — the duration circle becomes a sun, with a soft
 *     dramatic radial glow radiating outward (like sunlight illuminating
 *     planets). Glow pulses gently (breathes in and out).
 *
 * Stored in SharedPreferences as a String. Survives app restarts.
 *
 * The Lyrics Picker reads this on every recomposition so the user can
 * switch animations and see the change instantly.
 */
object LyricsAnimationManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_LYRICS_ANIM = "lyrics_highlight_anim_v1"

    enum class LyricsAnimation(val displayName: String) {
        PULSE_RING("Pulse Ring"),
        SUN_GLOW("Sun Glow")
    }

    private lateinit var prefs: android.content.SharedPreferences

    private val _animation = MutableStateFlow(LyricsAnimation.PULSE_RING)
    val animation: StateFlow<LyricsAnimation> = _animation.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LYRICS_ANIM, null)
        _animation.value = try {
            saved?.let { LyricsAnimation.valueOf(it) } ?: LyricsAnimation.PULSE_RING
        } catch (_: Exception) {
            LyricsAnimation.PULSE_RING
        }
    }

    fun setAnimation(animation: LyricsAnimation) {
        _animation.value = animation
        prefs.edit().putString(KEY_LYRICS_ANIM, animation.name).apply()
    }
}
