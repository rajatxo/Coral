package com.rajatxo.coral.audio

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 2nd-order biquad IIR filter.
 *
 * Ported from LastWave's C++ Biquad implementation. Supports:
 *   - High-pass (subsonic removal)
 *   - Peaking (boost/cut at a specific frequency)
 *   - High-shelf (air/detail boost)
 *
 * Each filter maintains state (x1, x2, y1, y2) per channel for
 * continuous processing without clicks/pops.
 *
 * Coefficients computed via the bilinear transform — standard
 * audio DSP approach used in professional EQ plugins.
 */
class Biquad(
    private val sampleRate: Double,
    val type: Type,
    val frequency: Double,
    val q: Double,
    val gainDb: Double = 0.0
) {
    enum class Type { HIGH_PASS, PEAKING, HIGH_SHELF }

    // Coefficients (computed once on init)
    private val a0: Double
    private val a1: Double
    private val a2: Double
    private val b0: Double
    private val b1: Double
    private val b2: Double

    // State (per channel — we support stereo)
    private var x1L = 0.0; private var x2L = 0.0
    private var y1L = 0.0; private var y2L = 0.0
    private var x1R = 0.0; private var x2R = 0.0
    private var y1R = 0.0; private var y2R = 0.0

    init {
        val w0 = 2.0 * Math.PI * frequency / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2.0 * q)

        when (type) {
            Type.HIGH_PASS -> {
                b0 = (1.0 + cosW) / 2.0
                b1 = -(1.0 + cosW)
                b2 = (1.0 + cosW) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha
            }
            Type.PEAKING -> {
                val a = Math.pow(10.0, gainDb / 40.0)
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW
                a2 = 1.0 - alpha / a
            }
            Type.HIGH_SHELF -> {
                val a = Math.pow(10.0, gainDb / 40.0)
                val sqrtA = Math.sqrt(a)
                b0 = a * ((a + 1.0) + (a - 1.0) * cosW + 2.0 * sqrtA * alpha)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                b2 = a * ((a + 1.0) + (a - 1.0) * cosW - 2.0 * sqrtA * alpha)
                a0 = (a + 1.0) - (a - 1.0) * cosW + 2.0 * sqrtA * alpha
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                a2 = (a + 1.0) - (a - 1.0) * cosW - 2.0 * sqrtA * alpha
            }
        }

        // Normalize so a0 = 1
        // (pre-divide b coefficients by a0 for efficiency)
        _b0n = b0 / a0
        _b1n = b1 / a0
        _b2n = b2 / a0
        _a1n = a1 / a0
        _a2n = a2 / a0
    }

    private val _b0n: Double
    private val _b1n: Double
    private val _b2n: Double
    private val _a1n: Double
    private val _a2n: Double

    /** Process one sample (left channel). */
    fun processL(x: Double): Double {
        val y = _b0n * x + _b1n * x1L + _b2n * x2L - _a1n * y1L - _a2n * y2L
        x2L = x1L; x1L = x
        y2L = y1L; y1L = y
        return y
    }

    /** Process one sample (right channel). */
    fun processR(x: Double): Double {
        val y = _b0n * x + _b1n * x1R + _b2n * x2R - _a1n * y1R - _a2n * y2R
        x2R = x1R; x1R = x
        y2R = y1R; y1R = y
        return y
    }

    /** Reset filter state (on seek/track change). */
    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }
}
