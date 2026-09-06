package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Apple Music-style thin scrubber.
 *
 *  - 3dp tall track with rounded corners
 *  - Filled portion uses coral (#FF6B6B)
 *  - Background portion uses white at 20 % alpha
 *  - 6dp radius white thumb that follows the position
 *  - Tap anywhere to jump there
 *  - Drag to scrub; commit seek on release
 *
 * Phase 4 will polish this with a spring animation on release and haptic
 * feedback on tap. For now it's functional.
 */
@Composable
fun ThinSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // While dragging, we keep the dragged position locally and only commit it
    // to the player on drag end. This lets the user scrub ahead of the actual
    // playback position smoothly without the position fighting back.
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    val displayPosition = dragPositionMs ?: positionMs

    val progress = if (durationMs > 0) {
        (displayPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        val newPos = (ratio * durationMs).toLong()
                        onSeek(newPos)
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            dragPositionMs = (ratio * durationMs).toLong()
                        },
                        onDragEnd = {
                            dragPositionMs?.let { onSeek(it) }
                            dragPositionMs = null
                        },
                        onDragCancel = { dragPositionMs = null },
                        onHorizontalDrag = { change, _ ->
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragPositionMs = (ratio * durationMs).toLong()
                            change.consume()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val trackWidth = size.width
                val trackY = size.height / 2f
                val trackHeight = 3.dp.toPx()
                val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val thumbRadius = 6.dp.toPx()
                val thumbX = trackWidth * progress

                // Background track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = cornerRadius
                )

                // Filled portion (coral)
                drawRoundRect(
                    color = Color(0xFFFF6B6B),
                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = cornerRadius
                )

                // Thumb (white dot)
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackY)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayPosition),
                color = Color(0xFFB0B0B0),
                fontSize = 11.sp
            )
            Text(
                text = formatTime(durationMs),
                color = Color(0xFFB0B0B0),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val mm = totalSec / 60
    val ss = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mm, ss)
}
