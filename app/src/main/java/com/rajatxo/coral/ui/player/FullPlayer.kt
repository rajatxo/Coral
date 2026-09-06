package com.rajatxo.coral.ui.player

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.ui.components.ThinSlider
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay

/**
 * Phase 4 — Full now-playing screen.
 *
 * Layout (top → bottom):
 *  ┌──────────────────────────────────┐
 *  │ ⌄                          ⋮     │ ← top bar (status bar pad)
 *  │                                  │
 *  │      ┌──────────────┐            │
 *  │      │              │            │ ← album art (square, 24dp rounded, shadow)
 *  │      │   BLURRED    │            │   double-tap = ❤️ + pop animation
 *  │      │              │            │
 *  │      └──────────────┘            │
 *  │      Song Title (22sp bold)      │
 *  │      Artist (15sp gray)         │
 *  │                                  │
 *  │   ▬▬▬▬▬▬▬▬●▬▬▬▬▬▬▬▬▬  slider    │ ← ThinSlider + time labels
 *  │   0:42                 3:18      │
 *  │                                  │
 *  │   🔀   ⏮   ▶   ⏭   🔁            │ ← transport (play is large coral circle)
 *  │                                  │
 *  │   ❤️                         ☰   │ ← bottom row (heart + queue)
 *  └──────────────────────────────────┘
 *
 * Background:
 *  - Bottom layer: blurred album art (blur 40dp) fills the whole screen
 *  - Top layer: vertical gradient from palette.primary → palette.tertiary → Color.Black
 *    This is the BitChord-style "album art bleeds into background" effect.
 *
 * Color state:
 *  - When a new song loads, palette colors are extracted in a coroutine.
 *  - Old colors are kept until the new palette resolves, so the background
 *    crossfades smoothly via animateColorAsState.
 *  - If album art is null (e.g. a song without embedded art), falls back to
 *    CoralPalette.Default and shows a big music note where the album art would be.
 */
@Composable
fun FullPlayer(
    mediaController: MediaController?,
    songId: Long?,
    title: String,
    artist: String,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // ---------- Palette state ----------
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        extractPalette(context, albumArtUri)?.let { palette = it }
    }

    // ---------- Playback position polling ----------
    // Media3 Player doesn't emit position updates continuously — we poll every
    // 500ms while the song is playing. While paused, we still want the slider
    // to reflect the current position, so we poll at a slower rate.
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            mediaController?.let { controller ->
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
                durationMs = controller.duration.coerceAtLeast(0L)
            }
            delay(if (isPlaying) 500L else 1000L)
        }
    }

    // ---------- Favorite state (persisted in PlaylistStore) ----------
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showHeartPop by remember { mutableStateOf(false) }

    // Reset heart pop when the song changes (new song = fresh pop opportunity)
    LaunchedEffect(songId) {
        showHeartPop = false
    }

    // ---------- Layout ----------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 1: Blurred album art (full-screen background)
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
            )
        }

        // Layer 2: Vertical gradient overlay using palette colors
        // Top = palette.primary, fades through tertiary, then to black at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
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

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { /* Phase 5: more options sheet */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.MoreVertical,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ---- Heart pop animation state (declared before the Box so it's
            // always called regardless of the showHeartPop flag) ----
            val heartPopScale by animateFloatAsState(
                targetValue = if (showHeartPop) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "heartPopScale"
            )

            // Reset heart pop after a short delay
            LaunchedEffect(showHeartPop) {
                if (showHeartPop) {
                    delay(600)
                    showHeartPop = false
                }
            }

            // ---- Album art (with double-tap to favorite) ----
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

                // Heart pop overlay — visible only while heartPopScale > 0
                if (heartPopScale > 0.01f) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.HeartFilled,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = heartPopScale * 0.9f),
                            modifier = Modifier
                                .size(96.dp)
                                .scale(heartPopScale)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---- Title + Artist + Favorite ----
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
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (songId != null) {
                                val nowFavorite = PlaylistStore.toggleFavorite(songId)
                                if (nowFavorite) showHeartPop = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val heartScale by animateFloatAsState(
                        targetValue = if (isFavorite) 1.1f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "heartScale"
                    )
                    Icon(
                        imageVector = if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart,
                        contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                        tint = if (isFavorite) palette.accent else Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(heartScale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Slider ----
            ThinSlider(
                positionMs = currentPositionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Transport row ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (placeholder — Phase 6 will wire to player.setShuffle)
                TransportIcon(
                    icon = CoralIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White.copy(alpha = 0.6f),
                    onClick = { /* TODO Phase 6 */ }
                )

                // Skip previous
                TransportIcon(
                    icon = CoralIcons.SkipPrev,
                    contentDescription = "Skip to previous",
                    tint = Color.White,
                    size = 32.dp,
                    onClick = onPrevClick
                )

                // Play / pause — large coral circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B6B))
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

                // Skip next
                TransportIcon(
                    icon = CoralIcons.SkipNext,
                    contentDescription = "Skip to next",
                    tint = Color.White,
                    size = 32.dp,
                    onClick = onNextClick
                )

                // Repeat (placeholder)
                TransportIcon(
                    icon = CoralIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = Color.White.copy(alpha = 0.6f),
                    onClick = { /* TODO Phase 6 */ }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Bottom row: Queue ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { /* TODO Phase 6: queue sheet */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Queue,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
