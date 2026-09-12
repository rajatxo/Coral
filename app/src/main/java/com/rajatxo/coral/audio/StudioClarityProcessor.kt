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

    // The 8 biquad filters (created on configure)
    private var filters: List<Biquad> = emptyList()

    // Stereo width + makeup gain
    private val stereoWidth = 1.22
    private val makeupGain = 1.04

    // Limiter state
    private val ceiling = 0.944060876  // -0.5 dBFS
    private val kneeThreshold = 0.841395141  // -1.5 dBFS
    private var limiterGain = 1.0

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
            Biquad(sr, Biquad.Type.PEAKING, 72.0, 0.80, 5.0),      // boosted from 3.2 → 5.0
            Biquad(sr, Biquad.Type.PEAKING, 280.0, 0.90, -4.5),    // boosted from -3.0 → -4.5
            Biquad(sr, Biquad.Type.PEAKING, 750.0, 0.85, -2.0),   // boosted from -1.4 → -2.0
            Biquad(sr, Biquad.Type.PEAKING, 3400.0, 0.85, 5.5),    // boosted from 3.8 → 5.5
            Biquad(sr, Biquad.Type.HIGH_SHELF, 10500.0, 0.85, 6.5)  // boosted from 4.8 → 6.5
        )

        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        // ALWAYS return true — we check the toggle INSIDE queueInput() on every
        // buffer. This ensures the processor is always in the audio pipeline,
        // and the toggle takes effect immediately (no need to skip songs).
        return configured
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Check the toggle on EVERY buffer — if it's off, pass through
        // unchanged (bit-perfect). If on, run the full 8-band DSP chain.
        val clarityOn = SoundHapticsManager.studioClarityEnabled.value
        if (!clarityOn) {
            outputBuffer = inputBuffer
            return
        }

        val remaining = inputBuffer.remaining()
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocate(remaining).order(ByteOrder.nativeOrder())
        }
        outputBuffer.clear()

        val isStereo = inputChannels >= 2

        // Process samples: 16-bit PCM, interleaved L/R
        while (inputBuffer.remaining() >= 4) {
            val sampleL = inputBuffer.short.toFloat() / 32768f
            val sampleR = if (isStereo) inputBuffer.short.toFloat() / 32768f else sampleL
            // Also consume R if mono (shouldn't happen, but safe)
            if (!isStereo && inputBuffer.remaining() >= 2) {
                inputBuffer.short  // skip
            }

            var l = sampleL.toDouble()
            var r = sampleR.toDouble()

            // Apply 6 biquad filters in sequence
            for (filter in filters) {
                l = filter.processL(l)
                r = filter.processR(r)
            }

            // 7. Stereo width (M/S processing)
            val mid = (l + r) * 0.5
            val side = (r - l) * 0.5 * stereoWidth
            l = mid - side
            r = mid + side

            // 8. Makeup gain
            l *= makeupGain
            r *= makeupGain

            // Soft-knee limiter (prevents clipping from the boosts)
            l = limit(l)
            r = limit(r)

            // Convert back to 16-bit PCM
            outputBuffer.putShort(clampToShort(l))
            outputBuffer.putShort(clampToShort(r))
        }

        outputBuffer.flip()
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
