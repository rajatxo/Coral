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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy

/**
 * ★ TodaysTopCard — floating glass card on TOP of the Speed Dial first row.
 *
 * Size: customizable (width, height, corner, shape) — like the nav bar card.
 * Draggable: drag handle on top.
 * Glass morphism: kyant backdrop blur.
 *
 * Content: two glowing white balls connected by a DISSOLVING dashed line.
 * The line FADES IN at the top, is BRIGHT between the two balls,
 * then FADES OUT at the bottom — like dissolving into the card.
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
    // ★ Read customization from prefs (same as nav bar)
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.customization
        .collectAsState()

    if (visible) {
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
            Box(
                modifier = Modifier
                    .padding(top = 140.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(cardOffsetX.toInt(), cardOffsetY.toInt()) }
                    .width(cardCustom.widthDp.dp)
                    .height(cardCustom.heightDp.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                // ★ Shape from customization
                val cardShape = cardCustom.shape.toComposeShape(
                    cardCustom.cornerRadiusDp, cardCustom.widthDp
                )
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
                    // ★ Drag handle — invisible strip on top
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
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                    }

                    // ═══ DISSOLVING DASHED LINE + GLOWING BALLS ═══
                    //
                    // The line DISSOLVES:
                    //   top → fades in (alpha 0 → 0.55)
                    //   ball 1 (bright, glowing)
                    //   between balls → BRIGHT (alpha 0.55, full)
                    //   ball 2 (bright, glowing)
                    //   bottom → fades out (alpha 0.55 → 0)
                    //
                    // Like:  ·····•──────•·····
                    //        (fade in) (bright) (fade out)

                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .width(20.dp)
                            .height(80.dp)
                    ) {
                        val ballCenterX = size.width / 2f
                        val ball1Y = size.height * 0.2f
                        val ball2Y = size.height * 0.8f

                        // ★ DISSOLVING DASHED LINE
                        //   3 zones:
                        //   1. Top zone (0 → ball1Y): fade IN (alpha 0 → 0.55)
                        //   2. Middle zone (ball1Y → ball2Y): BRIGHT (alpha 0.55)
                        //   3. Bottom zone (ball2Y → end): fade OUT (alpha 0.55 → 0)
                        val dashLengthPx = 3.dp.toPx()
                        val gapLengthPx = 3.dp.toPx()
                        val strokeWidthPx = 1.5.dp.toPx()
                        val maxAlpha = 0.55f  // ★ brighter than before (was 0.35f)

                        var y = 0f
                        while (y < size.height) {
                            // Calculate alpha based on position
                            val alpha = when {
                                // Top zone: fade in (0 → ball1Y)
                                y < ball1Y -> {
                                    val t = y / ball1Y
                                    maxAlpha * t
                                }
                                // Middle zone: bright (ball1Y → ball2Y)
                                y >= ball1Y && y <= ball2Y -> {
                                    maxAlpha
                                }
                                // Bottom zone: fade out (ball2Y → end)
                                else -> {
                                    val t = (size.height - y) / (size.height - ball2Y)
                                    maxAlpha * t
                                }
                            }

                            drawLine(
                                color = Color.White.copy(alpha = alpha),
                                start = Offset(ballCenterX, y),
                                end = Offset(ballCenterX, y + dashLengthPx),
                                strokeWidth = strokeWidthPx
                            )
                            y += dashLengthPx + gapLengthPx
                        }

                        // ★ GLOWING BALL 1 (top)
                        val glowRadius = 8.dp.toPx()
                        val coreRadius = 3.5.dp.toPx()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0f)
                                ),
                                center = Offset(ballCenterX, ball1Y),
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = Offset(ballCenterX, ball1Y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = coreRadius,
                            center = Offset(ballCenterX, ball1Y)
                        )

                        // ★ GLOWING BALL 2 (bottom)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0f)
                                ),
                                center = Offset(ballCenterX, ball2Y),
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = Offset(ballCenterX, ball2Y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = coreRadius,
                            center = Offset(ballCenterX, ball2Y)
                        )
                    }
                }
            }
        }
    }
}
