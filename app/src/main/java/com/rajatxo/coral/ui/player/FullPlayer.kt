package com.rajatxo.coral.ui.player

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * FullPlayer — Immersive design (matches PlaylistDetailScreen approach).
 *
 * The album art fills the top portion of the screen. A smoothstep gradient
 * bleeds the art into the dominant color extracted from it. The bottom
 * portion is solid immersive color where all controls live.
 *
 * Layout:
 *   ┌──────────────────────────┐
 *   │  ⌄            ⋮         │  top bar (transparent over art)
 *   │                          │
 *   │     ALBUM ART            │  fills top ~50% (no blur, no overlay)
 *   │     (full bleed,          │  smoothstep gradient at bottom edge
 *   │      clean, visible)      │  bleeds into immersive color
 *   │                          │
 *   │ ─── smoothstep fade ──── │  art → immersive color transition
 *   │                          │
 *   │  Song Title (Playfair)   │
 *   │  Artist (NyghtSerif)     │
 *   │                          │
 *   │  ▬▬▬▬▬●▬▬▬▬▬▬▬▬  scrub  │  thin progress line (drag to seek)
 *   │  0:42           3:18     │
 *   │                          │
 *   │     ⏮    ▶    ⏭         │  transport controls (clean, minimal)
 *   │                          │
 *   │  ❤️    📝    🔀          │  favorite / lyrics / shuffle
 *   └──────────────────────────┘
 *
 * No circle. No dial. Just immersive art + clean controls.
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

    // ---------- Palette extraction (for immersive bg) ----------
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        extractPalette(context, albumArtUri)?.let { palette = it }
    }

    // ---------- Immersive color (same as PlaylistDetailScreen) ----------
    val immersiveColor = remember(palette) {
        val base = palette.primary
        val luminance = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
        val darkenFactor = 0.55f + 0.35f * luminance
        lerp(base, Color.Black, darkenFactor)
    }

    // ---------- Position polling ----------
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }  // 0..1 fraction
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

    val effectiveProgress = if (isSeeking) {
        seekPosition
    } else if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // ---------- Favorite ----------
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showHeartPop by remember { mutableStateOf(false) }
    val heartPopScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showHeartPop) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "heartPop"
    )
    LaunchedEffect(songId) { showHeartPop = false }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(600); showHeartPop = false }
    }

    // ---------- Lyrics ----------
    var showLyrics by remember { mutableStateOf(false) }

    // ---------- Smoothstep scrim (same function as PlaylistDetailScreen) ----------
    fun smoothScrimBrush(
        color: Color,
        startFraction: Float = 0.35f,
        endFraction: Float = 0.65f,
        steps: Int = 32
    ): Brush {
        return Brush.verticalGradient(
            colorStops = Array(steps + 1) { i ->
                val t = i / steps.toFloat()
                val position = startFraction + (endFraction - startFraction) * t
                val eased = t * t * (3f - 2f * t)
                position to color.copy(alpha = eased)
            }
        )
    }

    // ---------- Layout ----------
    Box(modifier = Modifier.fillMaxSize().background(immersiveColor)) {

        // Layer 1: Album art (top portion, full bleed, no blur)
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = "Album art for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Scale slightly to avoid edge gaps
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
            )
        }

        // Layer 2: Smoothstep scrim (art → immersive color transition)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(smoothScrimBrush(immersiveColor, 0.35f, 0.65f))
        )

        // Layer 3: Solid immersive color below 65% (so controls are readable)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.65f to Color.Transparent,
                            0.66f to immersiveColor
                        )
                    )
                )
        )

        // Layer 4: Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- Top bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
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
                            .background(Color.White.copy(alpha = 0.15f))
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(CoralIcons.Heart, null, Color.White, Modifier.size(18.dp))
                            Text("Add to playlist", Color.White, 14.sp, FontWeight.Medium)
                        }
                    }
                }
            }

            // ---- Spacer (lets the art breathe) ----
            Spacer(modifier = Modifier.weight(0.3f))

            // ---- Title + Artist ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PlayfairItalicFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    fontFamily = NyghtSerifFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Progress bar (thin, draggable) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(24.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (durationMs > 0) {
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    onSeek((fraction * durationMs).toLong())
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        )
                    }
            ) {
                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )
                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(effectiveProgress)
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0xFFFF6B6B))
                )
                // Thumb
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = effectiveProgress * (size.width - 12.dp.toPx())
                        }
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B6B))
                )
            }

            // ---- Time labels ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isSeeking) (seekPosition * durationMs).toLong() else currentPositionMs),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Transport controls ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            onPrevClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(CoralIcons.SkipPrev, "Previous", Color.White, Modifier.size(32.dp))
                }

                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B6B))
                        .clickable {
                            onPlayPauseClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                        if (isPlaying) "Pause" else "Play",
                        Color.White,
                        Modifier.size(28.dp)
                    )
                }

                // Next
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            onNextClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(CoralIcons.SkipNext, "Next", Color.White, Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Bottom row: Favorite / Lyrics / Shuffle ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable {
                            if (songId != null) {
                                PlaylistStore.toggleFavorite(songId)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart,
                        if (isFavorite) "Unfavorite" else "Favorite",
                        if (isFavorite) palette.accent else Color.White,
                        Modifier.size(22.dp)
                    )
                }

                // Lyrics
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { showLyrics = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(CoralIcons.Queue, "Lyrics", Color.White, Modifier.size(22.dp))
                }

                // Shuffle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(CoralIcons.Shuffle, "Shuffle", Color.White.copy(alpha = 0.7f), Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))
        }

        // Heart pop overlay (centered on screen)
        if (heartPopScale > 0.01f) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Double-tap on album art to favorite
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(title) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (songId != null) {
                                        val nowFav = PlaylistStore.toggleFavorite(songId)
                                        if (nowFav) showHeartPop = true
                                    }
                                }
                            )
                        }
                )
                Icon(
                    CoralIcons.HeartFilled,
                    null,
                    Color.White.copy(alpha = heartPopScale * 0.9f),
                    Modifier.size(96.dp).scale(heartPopScale)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
