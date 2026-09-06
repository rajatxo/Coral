package com.rajatxo.coral.audio

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android's built-in [Equalizer] audio effect for use with ExoPlayer.
 *
 * Android's Equalizer API:
 *  - 0..N bands (typically 5 on most phones: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz)
 *  - Each band has a level range, e.g. -1500..1500 millibels (= -15dB..+15dB)
 *  - One Equalizer instance can attach to one audio session ID
 *
 * Coral's wrapper:
 *  - Exposes the band list and current levels as a StateFlow so the UI can
 *    react to changes.
 *  - Provides preset shortcuts (Flat, Bass Boost, Treble Boost, Vocal).
 *  - Safely releases the Equalizer when [release] is called.
 *  - Falls back gracefully if the device doesn't expose an Equalizer.
 *
 * NOTE: The Equalizer only works on the audio session ExoPlayer is using.
 * If the player is recreated, [attachToSession] must be called again.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null

    private val _bands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /**
     * Attach to the audio session of the given ExoPlayer.
     *
     * Returns true if the equalizer was successfully enabled, false if the
     * device doesn't support equalization (rare, but happens on some emulators).
     */
    fun attachToSession(player: ExoPlayer): Boolean {
        release()
        val sessionId = player.audioSessionId
        return try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = true
            _enabled.value = true

            val bandList = (0 until eq.numberOfBands).map { i ->
                val freq = eq.getCenterFreq(i.toShort()) / 1000  // millihz -> Hz
                val minLevel = eq.bandLevelRange[0] / 100  // millibels -> centibels (which is just dB*10)
                val maxLevel = eq.bandLevelRange[1] / 100
                EqualizerBand(
                    index = i,
                    centerFreqHz = freq,
                    minLevelDb = minLevel / 10f,
                    maxLevelDb = maxLevel / 10f,
                    currentLevelDb = eq.getBandLevel(i.toShort()) / 100f / 10f
                )
            }
            _bands.value = bandList
            equalizer = eq
            true
        } catch (e: Exception) {
            // Equalizer not available on this device
            _enabled.value = false
            _bands.value = emptyList()
            false
        }
    }

    /** Set the gain of a specific band in dB. */
    fun setBandLevel(bandIndex: Int, levelDb: Float) {
        // Update local state first (always works — even with mock bands)
        _bands.value = _bands.value.map { band ->
            if (band.index == bandIndex) band.copy(currentLevelDb = levelDb) else band
        }
        // Then apply to the real Equalizer if attached
        val eq = equalizer ?: return
        if (bandIndex !in 0 until eq.numberOfBands) return
        try {
            // dB to millibels: 1 dB = 100 millibels
            val millibels = (levelDb * 100).toInt().toShort()
            eq.setBandLevel(bandIndex.toShort(), millibels)
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Apply a named preset across all bands. */
    fun applyPreset(preset: EqualizerPreset) {
        val currentBands = _bands.value
        if (currentBands.isEmpty()) return
        preset.levels.forEach { (bandIndex, level) ->
            setBandLevel(bandIndex, level)
        }
    }

    /** Enable or disable the equalizer effect (without losing band levels). */
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _enabled.value = enabled
    }

    /**
     * Load a default set of 5 typical bands without actually attaching to
     * a real audio session. Used so the UI can display a fully-functional
     * equalizer preview even when no player is yet attached (Phase 7B will
     * wire the real audio effect via CoralPlaybackService).
     */
    fun loadDefaultBands() {
        _bands.value = listOf(
            EqualizerBand(0, 60_000, -15f, 15f, 0f),
            EqualizerBand(1, 230_000, -15f, 15f, 0f),
            EqualizerBand(2, 910_000, -15f, 15f, 0f),
            EqualizerBand(3, 3_600_000, -15f, 15f, 0f),
            EqualizerBand(4, 14_000_000, -15f, 15f, 0f)
        )
        _enabled.value = true
    }

    /** Release native resources. Call when the player is being torn down. */
    fun release() {
        equalizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Exception) { /* best-effort */ }
        }
        equalizer = null
        _bands.value = emptyList()
        _enabled.value = false
    }
}

/**
 * One band of the equalizer.
 *
 * @param index            0-based band index.
 * @param centerFreqHz     Center frequency in Hz (e.g. 60000 for 60kHz, 3600000 for 3.6MHz).
 *                         Stored as Hz but display logic should format it (kHz/MHz).
 * @param minLevelDb       Minimum allowed gain in dB (usually -15).
 * @param maxLevelDb       Maximum allowed gain in dB (usually +15).
 * @param currentLevelDb   Current gain in dB.
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelDb: Float,
    val maxLevelDb: Float,
    val currentLevelDb: Float
)

/**
 * Named equalizer preset. Maps band index → desired dB level.
 */
data class EqualizerPreset(
    val name: String,
    val levels: Map<Int, Float>
) {
    companion object {
        /** All bands at 0 dB. */
        val Flat = EqualizerPreset("Flat", mapOf(
            0 to 0f, 1 to 0f, 2 to 0f, 3 to 0f, 4 to 0f
        ))
        /** Boost low frequencies. */
        val BassBoost = EqualizerPreset("Bass Boost", mapOf(
            0 to 8f, 1 to 5f, 2 to 2f, 3 to 0f, 4 to 0f
        ))
        /** Boost high frequencies. */
        val TrebleBoost = EqualizerPreset("Treble Boost", mapOf(
            0 to 0f, 1 to 0f, 2 to 2f, 3 to 5f, 4 to 8f
        ))
        /** Boost mid frequencies for clearer vocals. */
        val Vocal = EqualizerPreset("Vocal", mapOf(
            0 to -2f, 1 to 3f, 2 to 5f, 3 to 3f, 4 to -2f
        ))
        /** Cut lows and highs for a "mid scoop" feel. */
        val MidScoop = EqualizerPreset("Mid Scoop", mapOf(
            0 to 3f, 1 to 0f, 2 to -4f, 3 to 0f, 4 to 3f
        ))

        val All = listOf(Flat, BassBoost, TrebleBoost, Vocal, MidScoop)
    }
}
