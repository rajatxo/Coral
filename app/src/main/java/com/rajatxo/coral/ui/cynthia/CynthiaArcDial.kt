package com.rajatxo.coral.ui.cynthia

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ★ CynthiaArcDial — semi-circular gauge with gradient ticks, like a coral dial.
 *
 * Inspired by the user's reference video:
 *   - 180° arc of tick marks at the bottom
 *   - Gradient on ticks: blue/purple → white → gold/yellow
 *   - White indicator needle pointing up from the center
 *   - Glowing dot that sweeps along the arc
 *   - Value text above (e.g., "153dp", "39%")
 *
 * Drag horizontally along the arc → value changes → needle + dot move →
 * tick sound + haptic on each step (respects SoundHapticsManager).
 */
@Composable
internal fun CynthiaArcDial(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
            as? android.os.Vibrator
    }

    // --- Sound + Haptic setup (same pattern as TabCapsule.kt) ---
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    fun tickHaptic() {
        val hapticsOn = SoundHapticsManager.hapticsEnabled.value
        val soundsOn = SoundHapticsManager.soundsEnabled.value
        val volume = SoundHapticsManager.soundVolume.value / 100f

        if (hapticsOn) {
            var hapticPerformed = false
            try {
                hapticPerformed = view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
            if (!hapticPerformed) {
                try {
                    val v = vibrator
                    if (v != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.vibrate(
                            android.os.VibrationEffect.createPredefined(
                                android.os.VibrationEffect.EFFECT_CLICK
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        }
        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(tickSoundId, volume, volume, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    // --- Value tracking ---
    val minVal = range.start
    val maxVal = range.endInclusive
    val valueRange = maxVal - minVal
    val fraction = ((value - minVal) / valueRange).coerceIn(0f, 1f)

    // Animated needle angle (smooth sweep when value changes)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "arcFraction"
    )

    // Track last integer value for tick-on-step
    var lastIntValue by remember { mutableStateOf(value.toInt()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Initialize tracking with the drag start position
                            updateValueFromTouch(
                                offset.x, size.width.toFloat(), minVal, maxVal
                            )?.let { newValue ->
                                onValueChange(newValue)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateValueFromTouch(
                                change.position.x, size.width.toFloat(), minVal, maxVal
                            )?.let { newValue ->
                                val newInt = newValue.toInt()
                                if (newInt != lastIntValue) {
                                    tickHaptic()
                                    lastIntValue = newInt
                                }
                                onValueChange(newValue)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h  // center of the arc circle (bottom center)
            val radius = (w / 2f).coerceAtMost(h * 1.2f)
            val tickCount = 41  // odd so one is exactly at center
            val arcStartDeg = 180f  // left
            val arcEndDeg = 360f    // right
            val arcSpanDeg = arcEndDeg - arcStartDeg  // 180°

            // --- Draw tick marks with gradient ---
            // Ticks go from 180° (left) to 360° (right), 180° total.
            // Each tick is a thin line radiating from the center (cx, cy).
            for (i in 0 until tickCount) {
                val t = i / (tickCount - 1).toFloat()  // 0..1
                val angleDeg = arcStartDeg + t * arcSpanDeg
                val angleRad = (angleDeg * PI / 180f).toFloat()
                val innerR = radius * 0.65f
                val outerR = radius * 0.95f
                val startX = cx + cos(angleRad) * innerR
                val startY = cy + sin(angleRad) * innerR
                val endX = cx + cos(angleRad) * outerR
                val endY = cy + sin(angleRad) * outerR

                // Gradient color: blue/purple (left) → white (center) → gold (right)
                val tickColor = when {
                    t < 0.5f -> {
                        // blue/purple → white
                        val localT = t * 2f  // 0..1
                        blendColor(Color(0xFF6B7BFF), Color.White, localT)
                    }
                    else -> {
                        // white → gold
                        val localT = (t - 0.5f) * 2f  // 0..1
                        blendColor(Color.White, Color(0xFFFFD166), localT)
                    }
                }

                // Ticks near the current value are brighter
                val distance = kotlin.math.abs(t - animatedFraction)
                val brightness = (1f - distance * 2f).coerceIn(0.3f, 1f)

                drawLine(
                    color = tickColor.copy(alpha = brightness),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // --- Draw the indicator needle (white, pointing up from center) ---
            val needleAngleDeg = arcStartDeg + animatedFraction * arcSpanDeg
            val needleAngleRad = (needleAngleDeg * PI / 180f).toFloat()
            val needleInnerR = radius * 0.55f
            val needleOuterR = radius * 0.98f
            val needleStart = Offset(
                cx + cos(needleAngleRad) * needleInnerR,
                cy + sin(needleAngleRad) * needleInnerR
            )
            val needleEnd = Offset(
                cx + cos(needleAngleRad) * needleOuterR,
                cy + sin(needleAngleRad) * needleOuterR
            )
            drawLine(
                color = Color.White,
                start = needleStart,
                end = needleEnd,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // --- Draw the glowing dot at the needle tip ---
            val dotCenter = needleEnd
            // Outer glow
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = 14f,
                center = dotCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = 9f,
                center = dotCenter
            )
            // Inner solid dot
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = dotCenter
            )
        }

        // ★ NUMBER inside the arc's half-circle (down, no label) + Reset button beside it.
        //   The number sits in the empty space inside the arc. The reset button
        //   is a small circular button right beside the number.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 24.dp),  // push down into the arc's half-circle
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The big number (no label, just the value + suffix)
            Text(
                text = "${value.toInt()}$suffix",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
            // Reset button (small circular arrow icon)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = { onReset() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↺",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        // ★ +1 / -1 buttons at the bottom-left and bottom-right of the arc.
        //   Added more space (24dp) between the arc and the buttons so they
        //   don't overlap the ticks.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 4.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {
                        val newValue = (value - 1f).coerceIn(minVal, maxVal)
                        if (newValue.toInt() != value.toInt()) {
                            tickHaptic()
                        }
                        onValueChange(newValue)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 4.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {
                        val newValue = (value + 1f).coerceIn(minVal, maxVal)
                        if (newValue.toInt() != value.toInt()) {
                            tickHaptic()
                        }
                        onValueChange(newValue)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        }
    }
}

/**
 * Map a touch X position to a value in [minVal, maxVal].
 * Returns null if the touch is outside the arc's horizontal range.
 */
private fun updateValueFromTouch(
    touchX: Float,
    width: Float,
    minVal: Float,
    maxVal: Float
): Float? {
    if (width <= 0f) return null
    val t = (touchX / width).coerceIn(0f, 1f)
    val value = minVal + t * (maxVal - minVal)
    return value
}

/**
 * Blend two colors linearly (for the gradient tick effect).
 */
private fun blendColor(c1: Color, c2: Color, t: Float): Color {
    return Color(
        red = c1.red + (c2.red - c1.red) * t,
        green = c1.green + (c2.green - c1.green) * t,
        blue = c1.blue + (c2.blue - c1.blue) * t,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * t
    )
}
