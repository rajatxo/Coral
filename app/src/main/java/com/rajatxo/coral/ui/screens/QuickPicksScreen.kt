package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.SleepTimerCapsule
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Quick Picks Screen — Arc Carousel.
 *
 * Album covers arranged along a horizontal arc (sibling to PlaylistWheel's
 * arc geometry, but with images instead of text). The active cover sits at
 * the center — large and bright. Neighbors arc upward on both sides,
 * getting smaller and dimmer (perspective falloff). Swipe left/right to
 * rotate. Haptic tick on each cover crossing the center.
 *
 * Layout:
 *   Box(fillMaxSize, bg=Black) {
 *     Column {
 *       Header: "Quick picks" title (Quirk italic) + SleepCapsule
 *       Toggle capsule: Based on last played ↔ Random picks
 *       ArcCarousel (weight 1f) — the rotational cover picker
 *       Footer: Active song title (Playfair Italic) + artist (NyghtSerif)
 *     }
 *   }
 *
 * Coral DNA:
 *   - Rotational geometry (arc + rotation + perspective falloff)
 *   - Bespoke typography (Playfair + NyghtSerif + Quirk)
 *   - Tactile haptics on rotation (same as PlaylistWheel)
 *   - No toys — clean, classy, restrained
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var isRandomMode by remember { mutableStateOf(false) }

    val quickPicksSongs = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()

        if (isRandomMode) {
            songs.shuffled().take(15)
        } else {
            val currentSong = songs.firstOrNull { it.id == currentSongId }
            if (currentSong != null) {
                val sameArtist = songs.filter {
                    it.artist == currentSong.artist && it.id != currentSong.id
                }
                val sameAlbum = songs.filter {
                    it.album == currentSong.album && it.id != currentSong.id &&
                    it.id !in sameArtist.map { s -> s.id }
                }
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct().take(15)
                if (related.size < 5) {
                    val fillers = songs.filter { it.id !in related.map { s -> s.id } }
                        .shuffled()
                        .take(15 - related.size)
                    (related + fillers).distinct()
                } else {
                    related
                }
            } else {
                songs.shuffled().take(15)
            }
        }
    }

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
                    .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
                if (capsuleVisible && capsuleRemaining > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Text(
                    text = "Quick picks",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily
                )
            }

            // --- Toggle capsule ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Based on last played",
                        color = if (!isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Random picks",
                        color = if (isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // --- Arc Carousel ---
            if (quickPicksSongs.isNotEmpty()) {
                ArcCarousel(
                    songs = quickPicksSongs,
                    onPlayClick = { onSongClick(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎵", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No songs found",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Arc Carousel — album covers arranged along a horizontal arc.
 *
 * Geometry:
 *   - Active cover at center (angle = 0): largest (240dp), full brightness
 *   - Neighbors arc upward on both sides: smaller + dimmer as |angle| grows
 *   - Each cover is at angle = (index - activeIndex) * stepAngle
 *   - position = (sin(angle), 1 - cos(angle)) → circular arc bulging upward
 *   - Only covers within ±3 steps are rendered (others too far to see)
 *
 * Interaction:
 *   - Drag horizontally → rotate the arc (change activeIndex continuously)
 *   - Snap to nearest cover on release (spring animation)
 *   - Haptic tick when activeIndex changes (crossing center)
 *   - Tap active cover → play
 */
@Composable
private fun ArcCarousel(
    songs: List<Song>,
    onPlayClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Continuous rotation: 0 = first cover at center, 1 = second cover at center, etc.
    // We animate fractionalIndex to snap to integer indices on release.
    val fractionalIndex = remember { Animatable(0f) }
    var lastIntIndex by remember { mutableStateOf(0) }

    val totalSongs = songs.size

    // Drag-to-rotate
    val dragState = remember { mutableStateOf(0f) }  // accumulated drag (px)

    LaunchedEffect(totalSongs) {
        fractionalIndex.snapTo(0f)
        lastIntIndex = 0
    }

    // The active cover (snapped to nearest integer)
    val activeIndex = fractionalIndex.value.roundToInt().coerceIn(0, totalSongs - 1)

    // Fire haptic when active index changes
    LaunchedEffect(activeIndex) {
        if (activeIndex != lastIntIndex) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastIntIndex = activeIndex
        }
    }

    // Active song's details (for the footer)
    val activeSong = songs.getOrNull(activeIndex)

    Box(
        modifier = modifier
            .pointerInput(totalSongs) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        // Snap to nearest cover with spring
                        val target = fractionalIndex.value.roundToInt().coerceIn(0, totalSongs - 1).toFloat()
                        scope.launch {
                            fractionalIndex.animateTo(
                                targetValue = target,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                            )
                        }
                    },
                    onDragCancel = {
                        val target = fractionalIndex.value.roundToInt().coerceIn(0, totalSongs - 1).toFloat()
                        scope.launch {
                            fractionalIndex.animateTo(
                                targetValue = target,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                            )
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Drag right → decrease index (previous cover comes to center)
                        // Drag left → increase index (next cover comes to center)
                        // Sensitivity: 120px per cover step
                        val delta = -dragAmount.x / 120f
                        scope.launch {
                            fractionalIndex.snapTo(
                                (fractionalIndex.value + delta).coerceIn(0f, (totalSongs - 1).toFloat())
                            )
                        }
                    }
                )
            }
    ) {
        // --- Render covers within ±3 steps of the active index ---
        // Sort by distance so closer covers render on top (painter's algorithm)
        val visibleRange = -3..3
        val coversToRender = visibleRange.mapNotNull { offset ->
            val index = activeIndex + offset
            if (index in songs.indices) {
                val fractionalOffset = (fractionalIndex.value - index)
                Triple(index, offset, fractionalOffset)
            } else null
        }.sortedByDescending { abs(it.third) }  // far first, near last (on top)

        coversToRender.forEach { (index, _, fractionalOffset) ->
            val song = songs[index]
            val angle = fractionalOffset * 0.5f  // radians per step
            val isActive = index == activeIndex

            // Position along the arc (bulging upward):
            //   x = sin(angle) * arcRadiusX (horizontal displacement)
            //   y = -cos(angle) * arcRadiusY (vertical: 0 at center, -arcRadiusY at edges)
            val arcRadiusX = 1f  // normalized, scaled by layout width
            val arcRadiusY = 0.35f  // how much the arc bulges upward

            // We use graphicsLayer to position + scale + alpha based on the arc
            ArcCover(
                song = song,
                isActive = isActive,
                angle = angle,
                arcRadiusX = arcRadiusX,
                arcRadiusY = arcRadiusY,
                onClick = {
                    if (isActive) {
                        onPlayClick(song)
                    } else {
                        // Snap to this cover
                        scope.launch {
                            fractionalIndex.animateTo(
                                targetValue = index.toFloat(),
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Footer: active song title + artist ---
        if (activeSong != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 100.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title — Playfair Display Italic
                Text(
                    text = activeSong.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PlayfairItalicFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Artist — NyghtSerif Light Italic
                Text(
                    text = activeSong.artist,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    fontFamily = NyghtSerifFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A single cover on the arc. Positioned, scaled, and dimmed based on its
 * angle relative to the center (0 = active, at center).
 */
@Composable
private fun ArcCover(
    song: Song,
    isActive: Boolean,
    angle: Float,
    arcRadiusX: Float,
    arcRadiusY: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Falloff based on |angle|:
    //   At angle=0: scale=1, alpha=1
    //   At angle=±0.5: scale=0.7, alpha=0.5
    //   At angle=±1.0: scale=0.5, alpha=0.3
    //   At angle=±1.5: scale=0.35, alpha=0.15
    val absAngle = abs(angle)
    val scale = (1f - absAngle * 0.5f).coerceIn(0.3f, 1f)
    val alpha = (1f - absAngle * 0.7f).coerceIn(0.1f, 1f)

    // Position along the arc (normalized 0..1 of layout size):
    //   x = sin(angle) → 0 at center, ±1 at edges
    //   y = (1 - cos(angle)) → 0 at center (top of arc), positive going down...
    //   But we want the arc to BULGE UPWARD, so y should be NEGATIVE at edges.
    //   y = -(1 - cos(angle)) * arcRadiusY → 0 at center, -arcRadiusY at edges
    val xFraction = sin(angle.toDouble()).toFloat() * arcRadiusX
    val yFraction = -(1f - kotlin.math.cos(angle.toDouble()).toFloat()) * arcRadiusY

    Box(
        modifier = modifier
            .graphicsLayer {
                // Translate by fraction of this composable's size
                translationX = xFraction * size.width
                translationY = yFraction * size.height
                scaleX = scale
                scaleY = scale
                alpha = alpha
                // Z-order: closer to center should be on top
                // (handled by render order in parent, but shadow helps too)
            }
    ) {
        // The album cover — large, square, rounded
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (isActive) 220.dp else 180.dp)  // active is bigger
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(300)
                        .build(),
                    contentDescription = "Album art for ${song.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎵", fontSize = 48.sp)
                }
            }

            // Subtle dark gradient at the bottom of the cover (for depth, not text)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
    }
}

// Helper extension (round to nearest int)
private fun Float.roundToInt(): Int = Math.round(this)
