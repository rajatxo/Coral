package com.rajatxo.coral.ui.screens

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ★ TodaysTopCard — floating GLASS card that opens when you tap a capsule.
 *
 * GLASS MORPHISM — completely different approach from kyant:
 *   Uses Android's built-in BlurEffect (API 31+) applied to the
 *   captured graphicsLayer from HomeScreen. No kyant drawBackdrop.
 *   On API < 31: falls back to semi-transparent dark background.
 *
 * No AnimatedVisibility — uses simple if(visible) to avoid the
 * AnimatedVisibility + backdrop crash.
 *
 * Tap outside to dismiss.
 */
@Composable
fun TodaysTopCard(
    visible: Boolean,
    song1: Song?,
    song2: Song?,
    color1: Color,
    color2: Color,
    currentSongId: Long?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayPauseClick: (Song) -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    if (visible) {
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
                    .padding(horizontal = 20.dp, vertical = 100.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                val cardShape = RoundedCornerShape(24.dp)
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
                        .padding(20.dp)
                ) {
                    CardContent(
                        song1 = song1,
                        song2 = song2,
                        color1 = color1,
                        color2 = color2,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick
                    )
                }
            }
        }
    }
}

// ★ Helper removed — using androidx.compose.ui.draw.drawWithContent directly

@Composable
private fun CardContent(
    song1: Song?,
    song2: Song?,
    color1: Color,
    color2: Color,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlayPauseClick: (Song) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ═══ LEFT SIDE: destination-style timeline ═══
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (song1 != null) {
                SongTimelineRow(
                    song = song1,
                    isPlaying = isPlaying && currentSongId == song1.id,
                    onPlayPauseClick = { onPlayPauseClick(song1) }
                )
            }
            if (song1 != null) {
                DashedLine(height = 20.dp)
            }
            if (song2 != null) {
                SongTimelineRow(
                    song = song2,
                    isPlaying = isPlaying && currentSongId == song2.id,
                    onPlayPauseClick = { onPlayPauseClick(song2) }
                )
            }
            if (song2 == null && song1 != null) {
                DashedLine(height = 20.dp)
                EmptyTimelineRow()
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // ═══ RIGHT SIDE: activity-tracker-style circle ═══
        ActivityCircle(
            color1 = color1,
            color2 = color2,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun SongTimelineRow(
    song: Song,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ★ Glowing white ball WITH a ring around it
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawCircle(color = Color.White.copy(alpha = 0.1f), radius = size.minDimension / 2f)
            }
            Canvas(modifier = Modifier.size(16.dp)) {
                drawCircle(color = Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
            }
            Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(color = Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2f)
            }
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = Color.White, radius = size.minDimension / 2f)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPauseClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyTimelineRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = Color.White.copy(alpha = 0.2f), radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
            }
        }
        Text(
            text = "Empty",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 14.sp,
            fontFamily = CalSansFamily
        )
    }
}

@Composable
private fun DashedLine(height: Dp) {
    Canvas(
        modifier = Modifier
            .width(24.dp)
            .height(height)
    ) {
        val centerX = size.width / 2f
        val dashCount = 5
        val dashLength = size.height / (dashCount * 2)
        for (i in 0 until dashCount) {
            val y = i * dashLength * 2
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(centerX, y),
                end = Offset(centerX, y + dashLength),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
private fun ActivityCircle(
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = size.minDimension / 2f

            drawArc(color = color1, startAngle = -90f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 3f))
            drawArc(
                color = color2, startAngle = -90f, sweepAngle = 180f, useCenter = false, style = Stroke(width = 3f),
                topLeft = Offset(centerX - maxRadius * 0.7f, centerY - maxRadius * 0.7f),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.4f, maxRadius * 1.4f)
            )

            val dotAngle = (-90f + 270f) * PI / 180f
            val dotX = centerX + cos(dotAngle).toFloat() * maxRadius
            val dotY = centerY + sin(dotAngle).toFloat() * maxRadius
            drawCircle(color = Color.White.copy(alpha = 0.3f), radius = 6f, center = Offset(dotX, dotY))
            drawCircle(color = Color.White, radius = 3f, center = Offset(dotX, dotY))
        }

        Canvas(modifier = Modifier.size(36.dp)) {
            drawCircle(color = Color.White, radius = size.minDimension / 2f)
        }
    }
}
