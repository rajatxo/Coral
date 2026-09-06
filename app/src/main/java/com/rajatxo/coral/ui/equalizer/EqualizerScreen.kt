package com.rajatxo.coral.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.audio.EqualizerBand
import com.rajatxo.coral.audio.EqualizerController
import com.rajatxo.coral.audio.EqualizerPreset
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Equalizer screen — Phase 7 premium feature.
 *
 * Layout:
 *  - Top bar: ChevronDown (back) | "EQUALIZER" label | enable/disable toggle
 *  - Presets row: Flat, Bass Boost, Treble Boost, Vocal, Mid Scoop
 *  - Band sliders: vertical custom-drawn sliders, each labeled with its frequency
 *
 * If the device doesn't support equalization, shows a friendly error state.
 */
@Composable
fun EqualizerScreen(
    controller: EqualizerController,
    onBackClick: () -> Unit
) {
    val bands by controller.bands.collectAsState()
    val enabled by controller.enabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "EQUALIZER",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )

                // Enable / disable toggle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) Color(0xFFFF6B6B)
                            else Color.White.copy(alpha = 0.10f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { controller.setEnabled(!enabled) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Play,
                        contentDescription = if (enabled) "Disable" else "Enable",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (bands.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎵", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Equalizer not available",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This device doesn't expose an equalizer API.\nTry a real phone instead of an emulator.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@Column
            }

            // Presets row
            Text(
                text = "PRESETS",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EqualizerPreset.All) { preset ->
                    PresetChip(
                        name = preset.name,
                        onClick = { controller.applyPreset(preset) }
                    )
                }
            }

            // Band sliders
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "BANDS",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, bottom = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                bands.forEach { band ->
                    BandSlider(
                        band = band,
                        onLevelChange = { newLevel -> controller.setBandLevel(band.index, newLevel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BandSlider(
    band: EqualizerBand,
    onLevelChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        VerticalBandSlider(
            value = band.currentLevelDb,
            minValue = band.minLevelDb,
            maxValue = band.maxLevelDb,
            onValueChange = onLevelChange,
            modifier = Modifier
                .height(200.dp)
                .padding(bottom = 8.dp)
        )
        Text(
            text = formatFreq(band.centerFreqHz),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = String.format("%.1f dB", band.currentLevelDb),
            color = Color(0xFFFF6B6B),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun VerticalBandSlider(
    value: Float,
    minValue: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = maxValue - minValue
    val normalized = ((value - minValue) / range).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .width(40.dp)
            .pointerInput(bandIndex = value) {
                detectVerticalDragGestures { change, dragAmount ->
                    val sliderHeight = size.height.toFloat()
                    if (sliderHeight == 0f) return@detectVerticalDragGestures
                    // Dragging UP should INCREASE the value (so drag amount is negated)
                    val delta = -dragAmount / sliderHeight
                    val newNormalized = (normalized + delta).coerceIn(0f, 1f)
                    onValueChange(minValue + newNormalized * range)
                    change.consume()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 4.dp.toPx()
            val trackHeight = size.height
            val centerX = size.width / 2f
            val cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
            val thumbRadius = 8.dp.toPx()
            val thumbY = trackHeight * (1 - normalized)

            // Track background (full)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(centerX - trackWidth / 2f, 0f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = cornerRadius
            )

            // Filled portion (from thumb down to bottom)
            drawRoundRect(
                color = Color(0xFFFF6B6B),
                topLeft = Offset(centerX - trackWidth / 2f, thumbY),
                size = Size(trackWidth, trackHeight - thumbY),
                cornerRadius = cornerRadius
            )

            // Thumb
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(centerX, thumbY)
            )
        }
    }
}

private fun formatFreq(hz: Int): String {
    return when {
        hz >= 1_000_000 -> "${hz / 1_000_000} MHz"
        hz >= 1000 -> {
            val khz = hz / 1000f
            if (khz % 1 == 0f) "${khz.toInt()} kHz" else "${"%.1f".format(khz)} kHz"
        }
        else -> "$hz Hz"
    }
}
