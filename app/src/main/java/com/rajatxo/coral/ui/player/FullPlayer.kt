package com.rajatxo.coral.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.lyrics.LyricsSheet
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay

/**
 * Immersive player — BitChord-style.
 *
 * Layout (top → bottom):
 *   1.  Opaque black base (nothing shows through).
 *   2.  Vertical gradient using the album art's dominant colors
 *       (extracted via Palette). NOT the album art itself — just its colors.
 *   3.  Soft dark overlay at the bottom for text legibility.
 *   4.  Top header: chevron-down · NOW PLAYING · more-vertical.
 *   5.  Square album art — full width, 10dp rounded corners, big drop shadow.
 *   6.  Song title  +  heart  +  queue  (row).
 *   7.  Artist name.
 *   8.  Lyrics strip (♪ … ›) — tap to open full lyrics.
 *   9.  Seek bar with times either side.
 *  10.  Transport: prev · play/pause · next.
 *  11.  Volume slider: speaker-low · track · speaker-high.
 *  12.  Bottom utility: shuffle · repeat-1 · repeat-∞ · queue.
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

    // ─── Palette (extracted from album art) ───────────────────────────
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) { extractPalette(context, albumArtUri)?.let { palette = it } }

    // Smooth crossfade when colors change on song switch (no grey flash).
    val animatedTopColor    by animateColorAsState(palette.primary,   tween(600), label = "top")
    val animatedMidColor   by animateColorAsState(palette.secondary,  tween(600), label = "mid")
    val animatedBottomColor by animateColorAsState(palette.tertiary,  tween(600), label = "bottom")

    // ─── Playback position polling ────────────────────────────────────
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let {
                    currentPositionMs = it.currentPosition.coerceAtLeast(0L)
                    durationMs = it.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    // ─── Favorites + lyrics sheet + more menu ────────────────────────
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showLyrics by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(800); showHeartPop = false }
    }

    // ─── System volume (so the volume slider reflects hardware keys) ─
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableFloatStateOf(
            if (maxVolume > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            else 0f
        )
    }
    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = if (maxVolume > 0)
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                else 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("volume_music_speaker") ?: Settings.System.CONTENT_URI,
            true,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // ─── Root Box: black base + full-bleed album art ────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // (1) Full-bleed album art fills the entire viewport
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(albumArtUri) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (songId != null) {
                                    PlaylistStore.toggleFavorite(songId)
                                    showHeartPop = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        )
                    }
            )
        }

        // (2) Dark gradient overlay — transparent at top, opaque at bottom
        // for text legibility over the album art
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.15f),
                            0.40f to Color.Black.copy(alpha = 0.20f),
                            0.65f to Color.Black.copy(alpha = 0.50f),
                            0.85f to Color.Black.copy(alpha = 0.85f),
                            1.00f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // (3) Heart pop overlay (double-tap to favorite)
        if (showHeartPop) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = CoralIcons.HeartFilled,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (isFavorite) palette.accent else Color.White
                    ),
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        // (3) Main content column — bottom-aligned, statusBarsPadding at top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top header ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    "NOW PLAYING",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
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
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CoralIcons.Heart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Add to playlist",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Bottom controls column ────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {

                // (a) Song title + heart + queue ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                if (songId != null) {
                                    PlaylistStore.toggleFavorite(songId)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) palette.accent else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Queue,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // (b) Lyrics strip — tap to open full lyrics ──────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLyrics = true }
                        .padding(vertical = 4.dp)
                ) {
                    Image(
                        imageVector = CoralIcons.Music,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (albumName.isNullOrBlank()) "Tap for lyrics" else albumName!!,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Image(
                        imageVector = CoralIcons.ChevronRight,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // (c) Seek bar ─────────────────────────────────────────
                val progress = if (durationMs > 0)
                    (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                else 0f
                var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .onSizeChanged { seekbarWidthPx = it.width.toFloat() }
                        .pointerInput(durationMs) {
                            detectDragGestures(
                                onDragEnd = {},
                                onDrag = { change, _ ->
                                    if (durationMs > 0 && seekbarWidthPx > 0) {
                                        val frac = (change.position.x / seekbarWidthPx).coerceIn(0f, 1f)
                                        onSeek((frac * durationMs).toLong())
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .align(Alignment.CenterStart)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .align(Alignment.CenterStart)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.CenterStart)
                            .offset {
                                IntOffset(
                                    (progress * seekbarWidthPx - 6.dp.toPx()).coerceAtLeast(0f).toInt(),
                                    0
                                )
                            }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "-" + formatTime((durationMs - currentPositionMs).coerceAtLeast(0L)),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // (d) Transport ─ prev · play/pause · next ─────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onPrevClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.SkipPrev,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onPlayPauseClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onNextClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // (e) Volume slider ─ speaker-low · track · speaker-high
                var volumeBarWidthPx by remember { mutableFloatStateOf(1f) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = CoralIcons.VolumeLow,
                        contentDescription = "Volume low",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .onSizeChanged { volumeBarWidthPx = it.width.toFloat() }
                            .pointerInput(maxVolume) {
                                detectDragGestures(
                                    onDragEnd = {},
                                    onDrag = { change, _ ->
                                        if (maxVolume > 0 && volumeBarWidthPx > 0) {
                                            val frac = (change.position.x / volumeBarWidthPx).coerceIn(0f, 1f)
                                            val newVol = (frac * maxVolume).toInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC, newVol, 0
                                            )
                                            volume = frac
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .align(Alignment.CenterStart)
                                .background(Color.White.copy(alpha = 0.20f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(volume)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .align(Alignment.CenterStart)
                                .background(Color.White.copy(alpha = 0.75f))
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        (volume * volumeBarWidthPx - 5.dp.toPx()).coerceAtLeast(0f).toInt(),
                                        0
                                    )
                                }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = CoralIcons.VolumeHigh,
                        contentDescription = "Volume high",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // (f) Bottom utility ─ shuffle · repeat-1 · repeat-∞ · queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Shuffle,
                            contentDescription = "Shuffle",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("∞", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Normal)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) { showLyrics = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Queue,
                            contentDescription = "Queue",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

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

// ─── Helpers ────────────────────────────────────────────────────────

/** m:ss formatter — used for both elapsed and remaining times. */
private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
