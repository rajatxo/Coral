package com.rajatxo.coral.audio

import android.util.Log
import androidx.media3.common.C
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Studio Master Clarity — 8-band biquad DSP chain.
 *
 * Ported from LastWave's C++ DspProcessor.cpp. Applies the same
 * "Studio Master Clarity Acoustic Contouring" that makes audio sound
 * crisper, cleaner, and more detailed.
 *
 * The chain (in processing order):
 *   1. Subsonic highpass: 24 Hz, Q=0.707 (removes rumble)
 *   2. Bass foundation: 72 Hz peaking +3.2 dB (punchy bass body)
 *   3. Low-mid anti-mud: 280 Hz peaking -3.0 dB (unmasks vocals)
 *   4. Boxiness control: 750 Hz peaking -1.4 dB (removes boxiness)
 *   5. Vocal presence: 3400 Hz peaking +3.8 dB (articulate detail)
 *   6. Air shelf: 10500 Hz high shelf +4.8 dB (silky high-end)
 *   7. Stereo width: 1.22x (wider soundstage)
 *   8. Makeup gain: 1.04x (compensates for filter losses)
 *
 * Plus a soft-knee limiter at -1.5 dBFS to prevent clipping.
 *
 * The processor works on 16-bit PCM stereo (Media3's default format).
 * It's enabled/disabled via SoundHapticsManager.studioClarityEnabled.
 */
class StudioClarityProcessor : androidx.media3.common.audio.AudioProcessor {

    private var active = false
    private var inputSampleRate = 0
    private var inputChannels = 0
    private var configured = false

    // The 6 clarity biquad filters (created on configure)
    private var filters: List<Biquad> = emptyList()

    // Coral Reef advanced filters (created on configure)
    private var exciterFilter: Biquad? = null   // 6kHz highpass for harmonic exciter
    private var monoBassFilter: Biquad? = null  // 130Hz highpass for mono-bass

    // Stereo width + makeup gain
    private val stereoWidth = 1.22
    private val makeupGain = 1.04

    // Coral Reef constants (from LastWave)
    private val exciterAmount = 0.18  // 18% mix of harmonic sheen
    private val wetDryMix = 0.5       // 50% wet, 50% dry blend

    // Limiter state (envelope-based, from LastWave)
    private val ceiling = 0.944060876  // -0.5 dBFS
    private val kneeThreshold = 0.841395141  // -1.5 dBFS
    private val limiterEngageThreshold = 1.25  // engage limiter above this
    private var limiterGain = 1.0
    private var limiterRelease = 0.0  // computed on configure

    // Buffer for output
    private var outputBuffer: ByteBuffer = ByteBuffer.allocate(0)
    private var pendingInput: ByteBuffer = ByteBuffer.allocate(0)

    override fun configure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputSampleRate = inputAudioFormat.sampleRate
        this.inputChannels = inputAudioFormat.channelCount
        this.configured = true

        // Create the 8-band biquad chain at the input sample rate
        val sr = inputAudioFormat.sampleRate.toDouble()
        filters = listOf(
            Biquad(sr, Biquad.Type.HIGH_PASS, 24.0, 0.707),
            Biquad(sr, Biquad.Type.PEAKING, 72.0, 0.80, 5.0),
            Biquad(sr, Biquad.Type.PEAKING, 280.0, 0.90, -4.5),
            Biquad(sr, Biquad.Type.PEAKING, 750.0, 0.85, -2.0),
            Biquad(sr, Biquad.Type.PEAKING, 3400.0, 0.85, 5.5),
            Biquad(sr, Biquad.Type.HIGH_SHELF, 10500.0, 0.85, 6.5)
        )

        // Coral Reef advanced filters
        exciterFilter = Biquad(sr, Biquad.Type.HIGH_PASS, 6000.0, 0.707)
        monoBassFilter = Biquad(sr, Biquad.Type.HIGH_PASS, 130.0, 0.707)

        // Limiter release (150ms time constant, from LastWave)
        limiterRelease = 1.0 - Math.exp(-1.0 / (sr * 0.150))

        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        // ALWAYS return true — we check the toggle INSIDE queueInput() on every
        // buffer. This ensures the processor is always in the audio pipeline,
        // and the toggle takes effect immediately (no need to skip songs).
        return configured
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val clarityOn = SoundHapticsManager.studioClarityEnabled.value
        val reefOn = SoundHapticsManager.coralReefEnabled.value

        if (!clarityOn && !reefOn) {
            outputBuffer = inputBuffer
            return
        }

        val remaining = inputBuffer.remaining()
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocate(remaining).order(ByteOrder.nativeOrder())
        }
        outputBuffer.clear()

        val isStereo = inputChannels >= 2

        while (inputBuffer.remaining() >= 4) {
            val sampleL = inputBuffer.short.toFloat() / 32768f
            val sampleR = if (isStereo) inputBuffer.short.toFloat() / 32768f else sampleL
            if (!isStereo && inputBuffer.remaining() >= 2) {
                inputBuffer.short
            }

            // Save dry signal for wet/dry blend
            val dryL = sampleL.toDouble()
            val dryR = sampleR.toDouble()

            var l = dryL
            var r = dryR

            // === STUDIO MASTER CLARITY (6 biquad filters) ===
            if (clarityOn || reefOn) {
                for (filter in filters) {
                    l = filter.processL(l)
                    r = filter.processR(r)
                }
            }

            // === CORAL REEF advanced stages ===
            if (reefOn) {
                // 1. Harmonic Air Exciter (tape-style sheen)
                // Extract highs above 6kHz, apply cubic waveshaping, mix back at 18%
                val exciterL = exciterFilter?.processL(dryL) ?: 0.0
                val exciterR = exciterFilter?.processR(dryR) ?: exciterL
                val excitedL = exciterL - (exciterL * exciterL * exciterL * 0.25)
                val excitedR = exciterR - (exciterR * exciterR * exciterR * 0.25)
                l += excitedL * exciterAmount
                r += excitedR * exciterAmount

                // 2. Mono-Bass (anti-blur): pass side through 130Hz highpass
                // Bass stays centered mono, no stereo blur in low end
                if (isStereo) {
                    val mid = (l + r) * 0.5
                    val rawSide = (l - r) * 0.5
                    val sideHigh = monoBassFilter?.processL(rawSide) ?: rawSide
                    val wideSide = sideHigh * stereoWidth
                    l = mid + wideSide
                    r = mid - wideSide
                }
            } else if (clarityOn) {
                // Studio Clarity only: simple stereo width (no mono-bass)
                val mid = (l + r) * 0.5
                val side = (r - l) * 0.5 * stereoWidth
                l = mid - side
                r = mid + side
            }

            // Makeup gain
            l *= makeupGain
            r *= makeupGain

            // === Coral Reef: Wet/Dry blend ===
            // output = dry + (wet - dry) * mix
            // This sounds more natural than 100% wet
            if (reefOn) {
                l = dryL + (l - dryL) * wetDryMix
                r = dryR + (r - dryR) * wetDryMix
            }

            // === Coral Reef: tanh soft saturation (analog warmth) ===
            // LastWave uses tanh for smooth saturation, not linear knee
            if (reefOn) {
                l = softSaturate(l)
                r = softSaturate(r)
            } else {
                l = limit(l)
                r = limit(r)
            }

            // === Envelope limiter (from LastWave) ===
            // Tracks peak envelope, releases smoothly over 150ms
            val peak = maxOf(kotlin.math.abs(l), kotlin.math.abs(r))
            val requiredGain = if (peak > limiterEngageThreshold) {
                limiterEngageThreshold / peak
            } else {
                1.0
            }
            if (requiredGain < limiterGain) {
                limiterGain = requiredGain  // instant attack
            } else {
                limiterGain += (1.0 - limiterGain) * limiterRelease  // smooth release
                if (1.0 - limiterGain < 0.00001) limiterGain = 1.0
            }
            l *= limiterGain
            r *= limiterGain

            // Final clamp
            l = l.coerceIn(-ceiling, ceiling)
            r = r.coerceIn(-ceiling, ceiling)

            outputBuffer.putShort(clampToShort(l))
            outputBuffer.putShort(clampToShort(r))
        }

        outputBuffer.flip()
    }

    /** tanh soft saturation — smooth analog-style compression. */
    private fun softSaturate(x: Double): Double {
        val absX = kotlin.math.abs(x)
        if (absX <= kneeThreshold) return x
        val range = ceiling - kneeThreshold
        val excess = absX - kneeThreshold
        val ratio = kotlin.math.tanh(excess / range)
        val saturated = kneeThreshold + range * ratio
        return if (x >= 0) saturated else -saturated
    }

    /** Soft-knee limiter: linear below threshold, compressed above. */
    private fun limit(x: Double): Double {
        val absX = kotlin.math.abs(x)
        if (absX < kneeThreshold) return x  // below knee: linear
        if (absX >= ceiling) {
            return kotlin.math.sign(x) * ceiling  // at ceiling: hard clip
        }
        // Soft knee: gradual compression between threshold and ceiling
        val ratio = (absX - kneeThreshold) / (ceiling - kneeThreshold)
        val compressed = kneeThreshold + (ceiling - kneeThreshold) * ratio * 0.5
        return kotlin.math.sign(x) * compressed
    }

    private fun clampToShort(x: Double): Short {
        return x.coerceIn(-1.0, 1.0).let { (it * 32767).toInt().toShort() }
    }

    override fun queueEndOfStream() {
        // No buffered data — just signal end
    }

    override fun getOutput(): ByteBuffer {
        return outputBuffer
    }

    override fun isEnded(): Boolean {
        return false  // Continuous processing
    }

    override fun flush() {
        // CRITICAL: return an EMPTY buffer, not a cleared one.
        // outputBuffer.clear() sets position=0, limit=capacity → looks full
        // of zeros → audio pipeline plays silence.
        // Instead, allocate a 0-capacity buffer so getOutput() returns nothing
        // until the next queueInput() fills it with real processed data.
        outputBuffer = ByteBuffer.allocate(0)
        filters.forEach { it.reset() }
        limiterGain = 1.0
    }

    override fun reset() {
        active = false
        configured = false
        inputSampleRate = 0
        inputChannels = 0
        filters = emptyList()
        outputBuffer = ByteBuffer.allocate(0)
    }
}
