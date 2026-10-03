package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.cynthia.CynthiaCustomShape
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CardMode { DISPLAY, CUSTOMIZE }

/**
 * ★ TodaysTopCard — floating glass card on TOP of the Speed Dial first row.
 *
 * Two modes:
 *   DISPLAY — shows balls + dissolving line (scales with card size)
 *   CUSTOMIZE — shows arc dial + capsule tabs (same as nav bar, no position X/Y)
 *
 * Position is SAVED — when user drags the card, its position persists.
 * Next tap on capsule shows the card at the last saved position.
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
    mode: CardMode = CardMode.DISPLAY,
    modifier: Modifier = Modifier
) {
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.customization
        .collectAsState()

    if (visible) {
        // ★ Position from saved prefs — card shows where user last placed it
        var cardOffsetX by remember { mutableStateOf(cardCustom.offsetX) }
        var cardOffsetY by remember { mutableStateOf(cardCustom.offsetY) }

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
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            cardOffsetX.toInt(),
                            cardOffsetY.toInt()
                        )
                    }
                    .width(cardCustom.widthDp.dp)
                    .height(cardCustom.heightDp.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
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
                    // ★ Drag handle — saves position on drag end
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
                                    },
                                    onDragEnd = {
                                        // ★ SAVE position to prefs
                                        com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                                            .setOffset(cardOffsetX, cardOffsetY)
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

                    if (mode == CardMode.DISPLAY) {
                        // ═══ DISPLAY MODE: balls + dissolving line ═══
                        // Scales with card size — balls and line use proportions
                        // of the card height, not fixed dp values.
                        val canvasHeight = (cardCustom.heightDp - 30f).dp
                        val canvasWidth = 20.dp

                        Canvas(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                                .width(canvasWidth)
                                .height(canvasHeight)
                        ) {
                            val ballCenterX = size.width / 2f
                            val ball1Y = size.height * 0.2f
                            val ball2Y = size.height * 0.8f

                            // Dissolving dashed line
                            val dashLengthPx = 3.dp.toPx()
                            val gapLengthPx = 3.dp.toPx()
                            val strokeWidthPx = 1.5.dp.toPx()
                            val maxAlpha = 0.55f

                            var y = 0f
                            while (y < size.height) {
                                val alpha = when {
                                    y < ball1Y -> maxAlpha * (y / ball1Y)
                                    y in ball1Y..ball2Y -> maxAlpha
                                    else -> maxAlpha * ((size.height - y) / (size.height - ball2Y))
                                }
                                drawLine(
                                    color = Color.White.copy(alpha = alpha),
                                    start = Offset(ballCenterX, y),
                                    end = Offset(ballCenterX, y + dashLengthPx),
                                    strokeWidth = strokeWidthPx
                                )
                                y += dashLengthPx + gapLengthPx
                            }

                            // Glowing balls (scale with card size)
                            val glowRadius = (size.height * 0.1f).coerceAtLeast(6f)
                            val coreRadius = glowRadius * 0.4f

                            // Ball 1
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
                            drawCircle(color = Color.White, radius = coreRadius, center = Offset(ballCenterX, ball1Y))

                            // Ball 2
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
                            drawCircle(color = Color.White, radius = coreRadius, center = Offset(ballCenterX, ball2Y))
                        }
                    } else {
                        // ═══ CUSTOMIZE MODE: all options including position X/Y ═══
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Width
                            QuickRow("Width", cardCustom.widthDp, 200f..400f, "dp") {
                                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setWidth(it)
                            }
                            // Height
                            QuickRow("Height", cardCustom.heightDp, 60f..200f, "dp") {
                                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setHeight(it)
                            }
                            // Corner
                            QuickRow("Corner", cardCustom.cornerRadiusDp, 0f..50f, "dp") {
                                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setCornerRadius(it)
                            }
                            // ★ Position X — so user can set exact default location
                            QuickRow("Pos X", cardCustom.offsetX, -300f..300f, "") {
                                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                                    .setOffset(it, cardCustom.offsetY)
                            }
                            // ★ Position Y — so user can set exact default location
                            QuickRow("Pos Y", cardCustom.offsetY, -200f..400f, "") {
                                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                                    .setOffset(cardCustom.offsetX, it)
                            }
                            // Shape picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                CynthiaCustomShape.entries.forEach { shape ->
                                    val isSelected = shape == cardCustom.shape
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.25f)
                                                else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setShape(shape)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shape.displayName,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = CalSansFamily
                                        )
                                    }
                                }
                            }
                            // Reset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.reset()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Reset",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = CalSansFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ${value.toInt()}$suffix",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = CalSansFamily
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickButton("-") { onValueChange((value - 5f).coerceIn(range.start, range.endInclusive)) }
            QuickButton("+") { onValueChange((value + 5f).coerceIn(range.start, range.endInclusive)) }
        }
    }
}

@Composable
private fun QuickButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalSansFamily
        )
    }
}
