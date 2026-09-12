package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SoundHapticsManager — Coral's sound + haptics preference singleton.
 *
 * Holds 3 pieces of state:
 *   - hapticsEnabled: whether haptic feedback fires app-wide
 *   - soundsEnabled: whether sound effects fire app-wide
 *   - soundVolume: 0..100, only meaningful when soundsEnabled = true
 *
 * Rules (user's spec):
 *   - If both disabled → no haptics, no sounds anywhere
 *   - If only haptics enabled → haptics fire, sounds silent
 *   - If only sounds enabled → sounds fire at soundVolume, no haptics
 *   - If both enabled → both fire, sound at soundVolume
 *
 * Persistence: SharedPreferences ('coral_prefs').
 */
object SoundHapticsManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_SOUNDS = "sounds_enabled"
    private const val KEY_VOLUME = "sound_volume"
    private const val KEY_STUDIO_CLARITY = "studio_clarity_enabled"
    private const val KEY_CORAL_REEF = "coral_reef_enabled"

    private const val DEFAULT_VOLUME = 60  // 0..100

    private lateinit var prefs: android.content.SharedPreferences

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _soundsEnabled = MutableStateFlow(true)
    val soundsEnabled: StateFlow<Boolean> = _soundsEnabled.asStateFlow()

    private val _soundVolume = MutableStateFlow(DEFAULT_VOLUME)
    val soundVolume: StateFlow<Int> = _soundVolume.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _hapticsEnabled.value = prefs.getBoolean(KEY_HAPTICS, true)
        _soundsEnabled.value = prefs.getBoolean(KEY_SOUNDS, true)
        _soundVolume.value = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)
        _studioClarityEnabled.value = prefs.getBoolean(KEY_STUDIO_CLARITY, false)
        _coralReefEnabled.value = prefs.getBoolean(KEY_CORAL_REEF, false)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    fun setSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUNDS, enabled).apply()
        _soundsEnabled.value = enabled
    }

    fun setSoundVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        prefs.edit().putInt(KEY_VOLUME, clamped).apply()
        _soundVolume.value = clamped
    }

    // --- Studio Master Clarity (8-band DSP chain) ---
    private val _studioClarityEnabled = MutableStateFlow(false)
    val studioClarityEnabled: StateFlow<Boolean> = _studioClarityEnabled.asStateFlow()

    fun setStudioClarity(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STUDIO_CLARITY, enabled).apply()
        _studioClarityEnabled.value = enabled
    }

    // --- Coral Reef (advanced DSP: exciter + mono-bass + wet/dry + tanh) ---
    private val _coralReefEnabled = MutableStateFlow(false)
    val coralReefEnabled: StateFlow<Boolean> = _coralReefEnabled.asStateFlow()

    fun setCoralReef(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CORAL_REEF, enabled).apply()
        _coralReefEnabled.value = enabled
    }
}
