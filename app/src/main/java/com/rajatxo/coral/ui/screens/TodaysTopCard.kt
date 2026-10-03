package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
 * Glass morphism: kyant backdrop blur (same as nav bar + customization panel).
 * Tap outside to dismiss.
 *
 * LEFT SIDE (destination style):
 *   - Two glowing white balls, each with a ring around it
 *   - Ball glows MORE than the ring
 *   - Balls connected by a dashed vertical line "┊"
 *   - Beside each ball: song title (CalSans) + artist name (tight spacing)
 *   - Beside that: play/pause icon (Spiral player style)
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
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
                        onClick = {} // consume click so it doesn't dismiss
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
                                            brightness = 0.1f,
                                            contrast = 1f,
                                            saturation = 1.3f
                                        )
                                        blur(30f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color.Black.copy(alpha = 0.45f))
                                    }
                                )
                            } else {
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.92f))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Song 1 row
            if (song1 != null) {
                SongTimelineRow(
                    song = song1,
                    isPlaying = isPlaying && currentSongId == song1.id,
                    onPlayPauseClick = { onPlayPauseClick(song1) }
                )
            }

            // Dashed vertical line between the two balls
            if (song1 != null) {
                DashedLine()
            }

            // Song 2 row
            if (song2 != null) {
                SongTimelineRow(
                    song = song2,
                    isPlaying = isPlaying && currentSongId == song2.id,
                    onPlayPauseClick = { onPlayPauseClick(song2) }
                )
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

/**
 * A single song row: [glowing ball with ring] — [title + artist] — [play/pause]
 */
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
        //   Ball glows MORE than the ring
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow (soft, large)
            Canvas(modifier = Modifier.size(24.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension / 2f
                )
            }
            // Ring (less glowing)
            Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.5f)
                )
            }
            // Ball glow (medium)
            Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    radius = size.minDimension / 2f
                )
            }
            // Solid ball (most glowing)
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f
                )
            }
        }

        // ★ Song title + artist name — TIGHT spacing (no gap between them)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // ★ Tight spacing — only 1dp between title and artist
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

        // ★ Play/pause icon
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

/**
 * Dashed vertical line connecting the two balls.
 */
@Composable
private fun DashedLine() {
    Canvas(
        modifier = Modifier
            .width(24.dp)
            .height(16.dp)
    ) {
        val centerX = size.width / 2f
        val dashCount = 3
        val dashHeight = size.height / (dashCount * 2)
        for (i in 0 until dashCount) {
            val y = i * dashHeight * 2
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(centerX, y),
                end = Offset(centerX, y + dashHeight),
                strokeWidth = 1.5f
            )
        }
    }
}

/**
 * Activity-tracker-style circle — filled white circle with colored arcs.
 */
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

            // Outer arc (color1) — 270° sweep
            drawArc(
                color = color1,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 3f)
            )

            // Inner arc (color2) — 180° sweep
            drawArc(
                color = color2,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 3f),
                topLeft = Offset(centerX - maxRadius * 0.7f, centerY - maxRadius * 0.7f),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.4f, maxRadius * 1.4f)
            )

            // Glowing dot at the end of the outer arc
            val dotAngle = (-90f + 270f) * PI / 180f
            val dotX = centerX + cos(dotAngle).toFloat() * maxRadius
            val dotY = centerY + sin(dotAngle).toFloat() * maxRadius
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 6f,
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(dotX, dotY)
            )
        }

        // Filled white circle in the center
        Canvas(modifier = Modifier.size(36.dp)) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2f
            )
        }
    }
}
