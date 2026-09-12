package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sound Lab — Coral's 3D Audio Controller.
 *
 * A spatial audio visualization + Studio Master Clarity toggle.
 *
 * Visual:
 *   - Dot-matrix sphere (wireframe globe of white dots)
 *   - Draggable sound-source orb (coral, glowing)
 *   - 3D human head silhouette (simplified, Canvas-drawn)
 *   - X/Y/Z coordinate readouts (real-time, updates as you drag the orb)
 *   - Directional labels (Top, Bottom, Left, Right)
 *   - Toggles: Studio Master Clarity, Auto Spatial Rotate
 *   - HRTF label (display only)
 *
 * Interactions:
 *   - Drag the coral orb → moves in 3D space around the head
 *   - X/Y/Z readouts update in real-time
 *   - Toggle Studio Master Clarity → enables/disables the DSP clarity chain
 *   - Auto Spatial Rotate → orb auto-rotates around the head
 *
 * Background: pure black (AMOLED)
 * Accent: coral (#FF6B6B)
 */
@Composable
fun SoundLabScreen() {
    val view = LocalView.current

    // --- State ---
    // Sound orb position in 3D space (normalized -1..1 for each axis)
    var orbX by remember { mutableStateOf(0f) }
    var orbY by remember { mutableStateOf(0f) }
    var orbZ by remember { mutableStateOf(0.5f) }  // Z = depth (positive = in front)

    // Toggle states
    var studioClarityEnabled by remember { mutableStateOf(false) }
    var coralReefEnabled by remember { mutableStateOf(false) }
    var autoRotateEnabled by remember { mutableStateOf(false) }

    // --- Auto rotation animation ---
    val infiniteTransition = rememberInfiniteTransition(label = "soundLab")
    val autoRotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "autoRotate"
    )

    // If auto-rotate is on, the orb follows a circular path
    val effectiveOrbX = if (autoRotateEnabled) cos(autoRotateAngle) * 0.8f else orbX
    val effectiveOrbY = if (autoRotateEnabled) sin(autoRotateAngle) * 0.3f else orbY
    val effectiveOrbZ = if (autoRotateEnabled) 0.5f + sin(autoRotateAngle) * 0.3f else orbZ

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Lab",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = CoralIcons.Settings,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "3D Audio Controller",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )

            // --- Toggle switches (at TOP so they're not covered by mini player) ---
            ToggleRow(
                title = "Studio Master Clarity",
                subtitle = "8-band DSP clarity chain (subsonic → air)",
                checked = studioClarityEnabled,
                onCheckedChange = {
                    studioClarityEnabled = it
                    SoundHapticsManager.setStudioClarity(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            )

            ToggleRow(
                title = "Coral Reef",
                subtitle = "Harmonic exciter + mono-bass + tanh saturation",
                checked = coralReefEnabled,
                onCheckedChange = {
                    coralReefEnabled = it
                    SoundHapticsManager.setCoralReef(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            )

            ToggleRow(
                title = "Auto Spatial Rotate",
                subtitle = "Orbit the sound source automatically",
                checked = autoRotateEnabled,
                onCheckedChange = {
                    autoRotateEnabled = it
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            )

            // --- Dot-matrix sphere + head + orb (fills center) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(autoRotateEnabled) {
                        if (!autoRotateEnabled) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                orbX = (orbX + dragAmount.x / 300f).coerceIn(-1f, 1f)
                                orbY = (orbY - dragAmount.y / 300f).coerceIn(-1f, 1f)
                                // Z changes with vertical drag too (push/pull effect)
                                orbZ = (orbZ - dragAmount.y / 600f).coerceIn(-1f, 1f)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
            ) {
                // --- Dot-matrix sphere (wireframe globe) ---
                SoundSphere(
                    modifier = Modifier.fillMaxSize(),
                    rotationAngle = autoRotateAngle * 0.5f
                )

                // --- 3D Head silhouette + sound orb ---
                SoundOrbAndHead(
                    orbX = effectiveOrbX,
                    orbY = effectiveOrbY,
                    orbZ = effectiveOrbZ,
                    modifier = Modifier.fillMaxSize()
                )

                // --- Directional labels ---
                DirectionalLabels(modifier = Modifier.fillMaxSize())
            }

            // --- X/Y/Z coordinate readouts ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CoordinateReadout(label = "X", value = effectiveOrbX)
                CoordinateReadout(label = "Y", value = effectiveOrbY)
                CoordinateReadout(label = "Z", value = effectiveOrbZ)
            }

            // --- HRTF label ---
            Text(
                text = "HRTF  +3dB  ~2kHz",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 24.dp, bottom = 120.dp)
            )
        }
    }
}

/**
 * Dot-matrix sphere — a denser wireframe globe of white dots.
 * Rotates slowly. Dots on the "front" are brighter, "back" dots dimmer.
 * Coral triangles at cardinal points (Top, Bottom, Left, Right).
 */
@Composable
private fun SoundSphere(
    modifier: Modifier = Modifier,
    rotationAngle: Float
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.34f

        // Denser grid for a more 3D-looking sphere
        val latSteps = 16  // was 12
        val lonSteps = 24  // was 16

        for (lat in 0 until latSteps) {
            val latAngle = (lat.toFloat() / (latSteps - 1)) * Math.PI.toFloat() - (Math.PI / 2).toFloat()
            val ringRadius = radius * cos(latAngle)
            val y = centerY + radius * sin(latAngle)

            for (lon in 0 until lonSteps) {
                val lonAngle = (lon.toFloat() / lonSteps) * (2 * Math.PI).toFloat() + rotationAngle
                val x = centerX + ringRadius * cos(lonAngle)
                val z = sin(lonAngle)  // -1 (back) to 1 (front)

                // Dots on the front hemisphere are brighter + bigger
                val alpha = ((z + 1f) / 2f * 0.55f + 0.03f).coerceIn(0.02f, 0.58f)
                val dotRadius = 1.2f + (z + 1f) * 1.2f

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
            }
        }

        // Outer ring (equator outline)
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1f)
        )

        // --- Coral directional triangles at cardinal points ---
        val triSize = 8f
        val triColor = Color(0xFFFF6B6B).copy(alpha = 0.7f)
        val triPath = androidx.compose.ui.graphics.Path()

        // Top triangle (pointing up)
        triPath.reset()
        triPath.moveTo(centerX, centerY - radius - triSize)
        triPath.lineTo(centerX - triSize * 0.6f, centerY - radius - triSize * 2)
        triPath.lineTo(centerX + triSize * 0.6f, centerY - radius - triSize * 2)
        triPath.close()
        drawPath(triPath, triColor)

        // Bottom triangle (pointing down)
        triPath.reset()
        triPath.moveTo(centerX, centerY + radius + triSize)
        triPath.lineTo(centerX - triSize * 0.6f, centerY + radius + triSize * 2)
        triPath.lineTo(centerX + triSize * 0.6f, centerY + radius + triSize * 2)
        triPath.close()
        drawPath(triPath, triColor)

        // Left triangle (pointing left)
        triPath.reset()
        triPath.moveTo(centerX - radius - triSize, centerY)
        triPath.lineTo(centerX - radius - triSize * 2, centerY - triSize * 0.6f)
        triPath.lineTo(centerX - radius - triSize * 2, centerY + triSize * 0.6f)
        triPath.close()
        drawPath(triPath, triColor)

        // Right triangle (pointing right)
        triPath.reset()
        triPath.moveTo(centerX + radius + triSize, centerY)
        triPath.lineTo(centerX + radius + triSize * 2, centerY - triSize * 0.6f)
        triPath.lineTo(centerX + radius + triSize * 2, centerY + triSize * 0.6f)
        triPath.close()
        drawPath(triPath, triColor)
    }
}

/**
 * The sound-source orb + 3D head silhouette.
 * The orb is a glowing coral circle that can be dragged around the head.
 */
@Composable
private fun SoundOrbAndHead(
    orbX: Float,
    orbY: Float,
    orbZ: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // --- 3D Head image (original, darkened + faded, no pixel removal) ---
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                com.rajatxo.coral.R.drawable.head_3d
            ),
            contentDescription = "3D head model",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
        )

        // --- Sound-source orb + glow + connection line (on Canvas) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val headRadius = minOf(size.width, size.height) * 0.06f

            // --- Sound-source orb (coral, glowing) ---
            val orbRadius = headRadius * 0.8f * (1f + orbZ * 0.5f)
            val orbCenterX = centerX + orbX * minOf(size.width, size.height) * 0.22f
            val orbCenterY = centerY + orbY * minOf(size.width, size.height) * 0.22f
            val orbAlpha = (0.5f + (orbZ + 1f) * 0.25f).coerceIn(0.3f, 1f)

            // Glow (radial gradient behind orb)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF6B6B).copy(alpha = orbAlpha * 0.6f),
                        Color(0xFFFF6B6B).copy(alpha = orbAlpha * 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(orbCenterX, orbCenterY),
                    radius = orbRadius * 3.5f
                ),
                center = Offset(orbCenterX, orbCenterY),
                radius = orbRadius * 3.5f
            )

            // Orb itself
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF8E8E),
                        Color(0xFFFF6B6B),
                        Color(0xFFE04545)
                    ),
                    center = Offset(orbCenterX - orbRadius * 0.3f, orbCenterY - orbRadius * 0.3f),
                    radius = orbRadius
                ),
                center = Offset(orbCenterX, orbCenterY),
                radius = orbRadius
            )

            // Connection line from orb to head center
            drawLine(
                color = Color(0xFFFF6B6B).copy(alpha = 0.25f),
                start = Offset(orbCenterX, orbCenterY),
                end = Offset(centerX, centerY),
                strokeWidth = 1.5f
            )
        }
    }
}

/**
 * Directional labels (Top, Bottom, Left, Right) around the sphere.
 */
@Composable
private fun DirectionalLabels(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = "Top",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
        )
        Text(
            text = "Bottom",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )
        Text(
            text = "Left",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        )
        Text(
            text = "Right",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        )
    }
}

/**
 * X/Y/Z coordinate readout (monospace, real-time).
 */
@Composable
private fun CoordinateReadout(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFFFF6B6B),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = String.format("%+.2f", value),
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * A toggle row (title + subtitle + Switch).
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF6B6B),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}
