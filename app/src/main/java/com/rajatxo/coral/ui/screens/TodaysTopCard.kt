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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradient
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
 * Size: 330dp × 110dp (matches Speed Dial first row: 3 × 110dp).
 * Position: sits over the speed dial grid (blurs it).
 * Draggable: drag handle on top.
 * Glass morphism: kyant backdrop blur.
 *
 * Content (for now): two glowing white balls on the left, connected by a
 * dashed vertical line that goes THROUGH their centers. Sizing per the
 * Gemini spec:
 *   - Active ball core: 7dp solid white
 *   - Active ball glow: 16dp soft radial gradient halo
 *   - Connecting line: 1.5dp stroke, 3dp dash, 3dp gap
 *   - Ball spacing: 28dp between centers
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
            // ★ Card — 330dp × 110dp, positioned over the speed dial first row.
            //   padding(top = 140dp) pushes it down to overlap the speed dial grid.
            //   (LazyColumn contentPadding top is 108dp + speed dial header ~30dp ≈ 138dp)
            Box(
                modifier = Modifier
                    .padding(top = 140.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(cardOffsetX.toInt(), cardOffsetY.toInt()) }
                    .width(330.dp)
                    .height(110.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
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

                    // ═══ GLOWING BALLS + DASHED LINE (left side) ═══
                    // Per Gemini spec:
                    //   - Active ball core: 7dp solid white
                    //   - Active ball glow: 16dp soft radial gradient halo
                    //   - Connecting line: 1.5dp stroke, 3dp dash, 3dp gap
                    //   - Ball spacing: 28dp between centers
                    //   - Line goes THROUGH the center of each ball
                    //
                    // The balls and line are drawn on ONE Canvas so the line
                    // passes through the ball centers seamlessly.

                    val ballSpacingDp = 28.dp  // distance between ball centers
                    val ballCenterY1Dp = 55.dp  // center of card vertically (110/2)
                    val ballCenterY2Dp = ballCenterY1Dp + ballSpacingDp - 16.dp  // second ball

                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .width(20.dp)
                            .height(80.dp)
                    ) {
                        val ballCenterX = size.width / 2f
                        val ball1Y = size.height * 0.2f  // top ball
                        val ball2Y = size.height * 0.8f  // bottom ball

                        // ★ DRAW DASHED LINE FIRST (behind balls) — goes through
                        //   the center of each ball.
                        //   1.5dp stroke, 3dp dash, 3dp gap, white at 0.35 alpha
                        val dashLengthPx = 3.dp.toPx()
                        val gapLengthPx = 3.dp.toPx()
                        val strokeWidthPx = 1.5.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.35f),
                                start = Offset(ballCenterX, y),
                                end = Offset(ballCenterX, y + dashLengthPx),
                                strokeWidth = strokeWidthPx
                            )
                            y += dashLengthPx + gapLengthPx
                        }

                        // ★ DRAW BALL 1 (top) — glowing white ball
                        //   Glow: 16dp radial gradient (white center → transparent edge)
                        //   Core: 7dp solid white
                        val glowRadius1 = 8.dp.toPx()  // 16dp diameter
                        val coreRadius1 = 3.5.dp.toPx()  // 7dp diameter

                        // Glow halo (soft radial gradient)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0f)
                                ),
                                center = Offset(ballCenterX, ball1Y),
                                radius = glowRadius1
                            ),
                            radius = glowRadius1,
                            center = Offset(ballCenterX, ball1Y)
                        )
                        // Solid white core
                        drawCircle(
                            color = Color.White,
                            radius = coreRadius1,
                            center = Offset(ballCenterX, ball1Y)
                        )

                        // ★ DRAW BALL 2 (bottom) — glowing white ball
                        val glowRadius2 = 8.dp.toPx()
                        val coreRadius2 = 3.5.dp.toPx()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0f)
                                ),
                                center = Offset(ballCenterX, ball2Y),
                                radius = glowRadius2
                            ),
                            radius = glowRadius2,
                            center = Offset(ballCenterX, ball2Y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = coreRadius2,
                            center = Offset(ballCenterX, ball2Y)
                        )
                    }
                }
            }
        }
    }
}
