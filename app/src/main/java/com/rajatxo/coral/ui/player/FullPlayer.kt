package com.rajatxo.coral.ui.player

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.lyrics.LyricsSheet
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * FullPlayer — "The Turntable" design.
 *
 * Layout:
 *   ┌──────────────────────────────┐
 *   │  ⌄                    ⋮      │  top bar
 *   │                              │
 *   │      ┌──────────────┐        │
 *   │      │              │        │  album art (clean, static, no rotation)
 *   │      │   ALBUM ART  │        │  double-tap = favorite
 *   │      │              │        │
 *   │      └──────────────┘        │
 *   │                              │
 *   │      Song Title (Playfair)   │
 *   │       Artist (NyghtSerif)    │
 *   │                              │
 *   │         ╭────────╮           │
 *   │        │     ●      │         │  THE DIAL
 *   │        │   (play)   │         │  rotate = seek
 *   │         ╰────────╯           │  flick = skip
 *   │                              │  tap center = play/pause
 *   │   ❤️                        │  pinch = volume
 *   └──────────────────────────────┘
 *
 * The Dial:
 *   - Outer ring: thin white track (15% alpha)
 *   - Coral arc: fills clockwise as song progresses
 *   - Center: circular play/pause button
 *   - Rotate the ring = seek through the song
 *   - Flick left/right on the dial = previous/next song
 *   - Pinch = volume
 *
 * All interactions wired to MediaController.
 */
@Composable
fun FullPlayer(
    mediaController: MediaController?,
    songId: Long?,
    title: String,
    artist: String,
    albumName: String?,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    // ---------- Palette ----------
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        extractPalette(context, albumArtUri)?.let { palette = it }
    }

    // ---------- Position polling ----------
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let { controller ->
                    currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    durationMs = controller.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    // ---------- Favorite ----------
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showHeartPop by remember { mutableStateOf(false) }
    LaunchedEffect(songId) { showHeartPop = false }

    // ---------- Lyrics ----------
    var showLyrics by remember { mutableStateOf(false) }

    // ---------- Heart pop animation ----------
    val heartPopScale by animateFloatAsState(
        targetValue = if (showHeartPop) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heartPop"
    )
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(600); showHeartPop = false }
    }

    // ---------- The Dial state ----------
    // Is the user currently rotating the dial?
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableStateOf(0L) }

    // The effective position (seek position if seeking, otherwise playback position)
    val effectivePositionMs = if (isSeeking) seekPositionMs else currentPositionMs
    val progress = if (durationMs > 0) {
        (effectivePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Volume state
    var currentVolume by remember { mutableStateOf(0.5f) }
    LaunchedEffect(mediaController) {
        try {
            mediaController?.let { currentVolume = it.volume / 1f }
        } catch (_: Exception) { }
    }

    // ---------- Layout ----------
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Blurred album art background
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp)
                )
            }
            // Layer 2: Gradient overlay
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to palette.primary.copy(alpha = 0.75f),
                            0.4f to palette.tertiary.copy(alpha = 0.85f),
                            0.85f to Color.Black.copy(alpha = 0.92f),
                            1.0f to Color.Black
                        )
                    )
                )
            )
        }

        // Layer 3: Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back (chevron down)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Collapse player",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )

                // 3-dot menu
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.MoreVertical,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1A1A))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    showMoreMenu = false
                                    songId?.let { onAddToPlaylist(it) }
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = CoralIcons.Heart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Add to playlist",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Album art (clean, static, double-tap to favorite) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1A))
                    .pointerInput(title, artist) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (songId != null) {
                                    val nowFavorite = PlaylistStore.toggleFavorite(songId)
                                    if (nowFavorite) showHeartPop = true
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (albumArtUri != null) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = "Album art for $title",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = CoralIcons.Music,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(80.dp)
                    )
                }

                // Heart pop overlay
                if (heartPopScale > 0.01f) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.HeartFilled,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = heartPopScale * 0.9f),
                            modifier = Modifier.size(96.dp).scale(heartPopScale)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Title + Artist ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PlayfairItalicFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    fontFamily = NyghtSerifFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- THE DIAL ----
            TheDial(
                progress = progress,
                isPlaying = isPlaying,
                positionMs = effectivePositionMs,
                durationMs = durationMs,
                onPlayPauseClick = {
                    onPlayPauseClick()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onSeek = { posMs ->
                    isSeeking = true
                    seekPositionMs = posMs
                },
                onSeekEnd = { posMs ->
                    isSeeking = false
                    onSeek(posMs)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onNext = {
                    onNextClick()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onPrev = {
                    onPrevClick()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onVolumeChange = { vol ->
                    currentVolume = vol
                    try { mediaController?.volume = (vol * 1f).toInt() } catch (_: Exception) { }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Bottom row: Favorite + Lyrics ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            if (songId != null) {
                                PlaylistStore.toggleFavorite(songId)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart,
                        contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                        tint = if (isFavorite) palette.accent else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Lyrics
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showLyrics = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Queue,
                        contentDescription = "Lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shuffle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ---- Lyrics sheet overlay ----
        if (showLyrics) {
            LyricsSheet(
                trackName = title,
                artistName = artist,
                albumName = albumName,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                onDismiss = { showLyrics = false },
                onSeek = onSeek
            )
        }
    }
}

/**
 * The Dial — a circular interaction zone for seek + skip + play/pause + volume.
 *
 * Interactions:
 *   - Rotate the ring (drag in a circular motion) = seek through the song
 *   - Tap the center = play/pause
 *   - Flick left/right on the dial area = prev/next song
 *   - Pinch = volume
 *
 * Visual:
 *   - Outer ring: thin white track (15% alpha)
 *   - Coral arc: fills clockwise as song progresses
 *   - Center: circular play/pause button (coral when playing, white when paused)
 *   - Time labels: current / total (small, monospace)
 */
@Composable
private fun TheDial(
    progress: Float,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragAccumulator by remember { mutableStateOf(0f) }
    var lastAngle by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(progress, durationMs) {
                var centerX = 0f
                var centerY = 0f

                detectTransformGestures(
                    onGesture = { centroid, pan, zoom, rotation ->
                        // Pinch (zoom) = volume
                        if (zoom != 1f) {
                            val newVol = (0.5f + (zoom - 1f) * 2f).coerceIn(0f, 1f)
                            onVolumeChange(newVol)
                        }

                        // Rotation = seek
                        if (kotlin.math.abs(rotation) > 0.01f) {
                            // Each radian of rotation = 1% of the song
                            val seekDelta = (rotation / (2 * Math.PI).toFloat() * durationMs).toLong()
                            val newPos = (positionMs + seekDelta).coerceIn(0, durationMs)
                            onSeek(newPos)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // Horizontal flick = skip
                detectDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator > 150f) {
                            onNext()
                        } else if (dragAccumulator < -150f) {
                            onPrev()
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        dragAccumulator += dragAmount
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                // Tap center = play/pause
                detectTapGestures(
                    onTap = { offset ->
                        // Check if tap is near the center (within 40dp radius)
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dist = sqrt(
                            (offset.x - centerX) * (offset.x - centerX) +
                            (offset.y - centerY) * (offset.y - centerY)
                        )
                        val touchRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
                        if (dist < touchRadius) {
                            onPlayPauseClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = minOf(size.width, size.height) / 2f - 16f

            // --- Outer track ring (white 15% alpha) ---
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )

            // --- Coral progress arc ---
            val sweepAngle = progress * 360f
            drawArc(
                color = Color(0xFFFF6B6B),
                startAngle = -90f,  // Start from top (12 o'clock)
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3.dp.toPx())
            )

            // --- Inner circle (subtle dark bg for the play button) ---
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                radius = radius * 0.45f,
                center = Offset(centerX, centerY)
            )

            // --- Inner ring (thin border) ---
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.45f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx())
            )

            // --- Progress indicator dot (coral, at the current position) ---
            val dotAngle = (-90f + sweepAngle) * (Math.PI / 180).toFloat()
            val dotX = centerX + radius * cos(dotAngle)
            val dotY = centerY + radius * sin(dotAngle)
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        // --- Center play/pause icon ---
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isPlaying) Color(0xFFFF6B6B)
                    else Color.White.copy(alpha = 0.15f)
                )
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // --- Time labels (below the dial) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTime(positionMs),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        // --- Current time (top left of dial) ---
        Text(
            text = formatTime(positionMs),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 8.dp)
        )

        // --- Total time (top right of dial) ---
        Text(
            text = formatTime(durationMs),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 8.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

// Need cos/sin for angle calculations
private fun cos(angle: Float): Float = kotlin.math.cos(angle)
private fun sin(angle: Float): Float = kotlin.math.sin(angle)
