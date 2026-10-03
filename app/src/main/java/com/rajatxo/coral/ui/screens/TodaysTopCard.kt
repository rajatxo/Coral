package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy

/**
 * ★ TodaysTopCard — floating glass card that opens when you tap a capsule.
 *
 * Size: matches the Speed Dial first row (3 × 110dp = 330dp wide, 110dp tall).
 * Draggable: drag handle on top, default position = speed dial first row area.
 * Glass morphism: kyant backdrop blur (same as nav bar).
 *
 * Content (for now): just a thick vertical dashed line on the left side.
 * We'll add the destination-style UI + activity circles later.
 *
 * Tap outside to dismiss.
 */
@Composable
fun TodaysTopCard(
    visible: Boolean,
    song1: com.rajatxo.coral.domain.model.Song?,
    song2: com.rajatxo.coral.domain.model.Song?,
    color1: Color,
    color2: Color,
    currentSongId: Long?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayPauseClick: (com.rajatxo.coral.domain.model.Song) -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    if (visible) {
        // ★ Draggable card state — same pattern as the customization panel.
        var cardOffsetX by remember { mutableStateOf(0f) }
        var cardOffsetY by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // ★ Card — 330dp wide × 110dp tall (matches Speed Dial first row).
            //   Draggable via offset (cardOffsetX, cardOffsetY).
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(cardOffsetX.toInt(), cardOffsetY.toInt()) }
                    .width(330.dp)
                    .height(110.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // consume click so it doesn't dismiss
                    )
            ) {
                val cardShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .clip(cardShape)
                        .then(
                            if (backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { cardShape },
                                    effects = {
                                        vibrancy()
                                        colorControls(
                                            brightness = 0.05f,
                                            contrast = 1f,
                                            saturation = 1.5f
                                        )
                                        blur(12f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color.Black.copy(alpha = 0.25f))
                                    }
                                )
                            } else {
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.88f))
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), cardShape)
                ) {
                    // ★ Drag handle — invisible strip on top that captures drags.
                    //   Same as the customization panel.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cardOffsetX += dragAmount.x
                                        cardOffsetY += dragAmount.y
                                    }
                                )
                            }
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center
                    ) {
                        // Small drag handle indicator (pill)
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                    }

                    // ★ THICK vertical dashed line on the LEFT side.
                    //   This is the only content for now. We'll add the
                    //   destination-style UI + activity circles later.
                    //
                    //   Details:
                    //   - Position: left side of the card, vertically centered
                    //   - Stroke width: 3px (was 1.5px — now thicker)
                    //   - Color: white at 0.4 alpha (visible but not overwhelming)
                    //   - Dashes: 8 segments stacked vertically
                    //   - Each dash: ~8dp long with ~4dp gap
                    //   - Total height: ~96dp (fits inside the 110dp card with padding)
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .width(6.dp)
                            .height(80.dp)
                    ) {
                        val centerX = size.width / 2f
                        val dashCount = 8
                        val totalHeight = size.height
                        val dashLength = totalHeight / (dashCount * 1.5f)
                        val gapLength = dashLength * 0.5f

                        for (i in 0 until dashCount) {
                            val y = i * (dashLength + gapLength)
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(centerX, y),
                                end = Offset(centerX, y + dashLength),
                                strokeWidth = 3f  // ★ THICK — was 1.5f
                            )
                        }
                    }
                }
            }
        }
    }
}
