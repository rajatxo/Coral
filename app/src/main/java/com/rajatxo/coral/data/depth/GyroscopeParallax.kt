package com.rajatxo.coral.data.depth

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * GyroscopeParallax — listens to the phone's orientation sensors and
 * returns a smooth (x, y) tilt offset that can be used to shift layers
 * in a 3D depth composition.
 *
 * HOW IT WORKS:
 * - Uses the accelerometer + magnetometer to compute the phone's
 *   orientation (rotation vector sensor is preferred if available)
 * - The roll (left-right tilt) and pitch (up-down tilt) are smoothed
 *   via a low-pass filter to avoid jitter
 * - Returns values in the range [-1, 1] for each axis, where:
 *     x = -1 = tilted fully left,  x = +1 = tilted fully right
 *     y = -1 = tilted fully down,  y = +1 = tilted fully up
 * - The UI multiplies these by a max-shift-in-px to translate layers
 *
 * Usage in a Composable:
 *   val tilt by rememberGyroscopeTilt()
 *   // Foreground layer: shift by tilt * maxShiftPx (moves WITH tilt)
 *   // Background layer: shift by -tilt * maxShiftPx (moves OPPOSITE)
 */
@Composable
fun rememberGyroscopeTilt(smoothing: Float = 0.15f): State<TiltOffset> {
    val context = LocalContext.current
    val tiltState = remember { mutableStateOf(TiltOffset(0f, 0f)) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Prefer the rotation vector sensor (fuses accel + mag + gyro)
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (rotationSensor == null) {
            // No sensor available — return zero tilt (static figure)
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        // Rotation vector → rotation matrix → orientation
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientation)

                        // orientation[1] = pitch (front-back tilt), radians
                        // orientation[2] = roll (left-right tilt), radians
                        val rollRad = orientation[2]
                        val pitchRad = orientation[1]

                        // Convert to [-1, 1] range. ~0.5 rad ≈ 28° is a
                        // comfortable max tilt for a phone in hand.
                        val maxRad = 0.5f
                        val targetX = (rollRad / maxRad).coerceIn(-1f, 1f)
                        val targetY = (-pitchRad / maxRad).coerceIn(-1f, 1f)

                        // Low-pass filter for smooth motion
                        val current = tiltState.value
                        tiltState.value = TiltOffset(
                            x = current.x + (targetX - current.x) * smoothing,
                            y = current.y + (targetY - current.y) * smoothing
                        )
                    } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        // Fallback: use accelerometer values directly
                        val x = event.values[0] // left-right (m/s²)
                        val y = event.values[1] // up-down (m/s²)
                        val maxAccel = 9.81f // gravity
                        val targetX = (x / maxAccel).coerceIn(-1f, 1f)
                        val targetY = (y / maxAccel).coerceIn(-1f, 1f)

                        val current = tiltState.value
                        tiltState.value = TiltOffset(
                            x = current.x + (targetX - current.x) * smoothing,
                            y = current.y + (targetY - current.y) * smoothing
                        )
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
            }

            sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return tiltState
}

/** Tilt offset in the range [-1, 1] for each axis */
data class TiltOffset(val x: Float, val y: Float)
