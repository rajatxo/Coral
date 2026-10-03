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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * ★ TodaysTopCard — floating glass card that opens when you tap a capsule.
 *
 * Layout (inspired by destination + activity tracker reference images):
 *
 * LEFT SIDE (destination style):
 *   - Two glowing white balls connected by a dashed vertical line
 *   - Beside each ball: song title (CalSans) + artist name below
 *   - Beside that: play/pause icon (Spiral player style)
 *
 * RIGHT SIDE (activity tracker style):
 *   - Filled white circle
 *   - Colored arc around it (song's dominant color) that represents
 *     playback progress
 *
 * Glass morphism: kyant backdrop blur (same as nav bar).
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
            // ★ Card — sits at the top, above the Speed Dial first row
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
                                        drawRect(Color.Black.copy(alpha = 0.5f))
                                    }
                                )
                            } else {
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.9f))
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), cardShape)
                        .padding(16.dp)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Song 1 row
            if (song1 != null) {
                SongTimelineRow(
                    song = song1,
                    color = color1,
                    isPlaying = isPlaying && currentSongId == song1.id,
                    onPlayPauseClick = { onPlayPauseClick(song1) }
                )
            }

            // Dashed line between the two songs
            if (song1 != null && song2 != null) {
                DashedLine()
            }

            // Song 2 row
            if (song2 != null) {
                SongTimelineRow(
                    song = song2,
                    color = color2,
                    isPlaying = isPlaying && currentSongId == song2.id,
                    onPlayPauseClick = { onPlayPauseClick(song2) }
                )
            }

            // Empty slot placeholder if song2 is null
            if (song2 == null) {
                DashedLine()
                EmptyTimelineRow()
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // ═══ RIGHT SIDE: activity-tracker-style circle + arc ═══
        ActivityCircle(
            color1 = color1,
            color2 = color2,
            modifier = Modifier.size(80.dp)
        )
    }
}

/**
 * A single song row in the destination-style timeline:
 * [glowing ball] — [song title + artist] — [play/pause icon]
 */
@Composable
private fun SongTimelineRow(
    song: Song,
    color: Color,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ★ Glowing white ball
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = size.minDimension / 2f
                )
            }
            // Inner solid ball
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f
                )
            }
        }

        // ★ Song title + artist name
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
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ★ Play/pause icon (Spiral player style)
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
 * Empty timeline row (when there's no second song).
 */
@Composable
private fun EmptyTimelineRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hollow ball
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.5f)
                )
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

/**
 * Dashed vertical line between the two song rows.
 */
@Composable
private fun DashedLine() {
    Canvas(
        modifier = Modifier
            .width(12.dp)
            .height(20.dp)
    ) {
        val dashCount = 4
        val dashHeight = size.height / (dashCount * 2)
        for (i in 0 until dashCount) {
            val y = i * dashHeight * 2
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(size.width / 2f, y),
                end = Offset(size.width / 2f, y + dashHeight),
                strokeWidth = 2f
            )
        }
    }
}

/**
 * Activity-tracker-style circle — filled white circle with colored arcs
 * around it (using the two songs' dominant colors).
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

            // ★ Outer arc (color1) — 270° sweep
            drawArc(
                color = color1,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // ★ Inner arc (color2) — 180° sweep
            drawArc(
                color = color2,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
                topLeft = Offset(centerX - maxRadius * 0.7f, centerY - maxRadius * 0.7f),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.4f, maxRadius * 1.4f)
            )

            // ★ Glowing dot at the end of the outer arc
            val dotAngle = (-90f + 270f) * PI / 180f
            val dotX = centerX + cos(dotAngle).toFloat() * maxRadius
            val dotY = centerY + sin(dotAngle).toFloat() * maxRadius
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 8f,
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(dotX, dotY)
            )
        }

        // ★ Filled white circle in the center
        Canvas(modifier = Modifier.size(40.dp)) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2f
            )
        }
    }
}
