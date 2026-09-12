package com.rajatxo.coral.ui.player

import android.net.Uri
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay

/**
 * FullPlayer — BitChord cover + Immersive gradient blend.
 *
 * WHAT THIS COMBINES:
 *
 * FROM BITCHORD (cover treatment):
 *   - Album art is NOT full-screen. It's a moderate size (~75% of screen width)
 *   - Quality stays high (not stretched)
 *   - Cover sits in the upper portion, centered, with rounded corners
 *
 * FROM IMMERSIVE (background color):
 *   - The background is a SOLID color derived from the album art's palette
 *   - Uses HSL color science (ported from ArchiveTune's deriveArtworkSurfaceColor)
 *   - The cover's bottom edge blends smoothly into the background via gradient
 *   - The color is darkened/saturated to look premium (not muddy like BitChord)
 *
 * RESULT:
 *   - Cover: moderate size, high quality, sits on top
 *   - Background: immersive color from the album art
 *   - Blend: smooth gradient from cover bottom → immersive color
 *   - No full-screen art stretching, no muddy colors
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

    // ---------- Palette extraction ----------
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        extractPalette(context, albumArtUri)?.let { palette = it }
    }

    // ---------- Immersive surface color (ArchiveTune approach) ----------
    // Uses HSL color science to derive a premium-looking dark color
    // from the album art's dominant color.
    val immersiveColor = remember(palette) {
        deriveArtworkSurfaceColor(
            sourceColor = palette.primary,
            darkLightness = 0.16f,
            darkSaturationRange = 0.32f..0.54f
        )
    }

    // ---------- Position polling ----------
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }
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
    val heartPopScale by animateFloatAsState(
        targetValue = if (showHeartPop) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heartPop"
    )
    LaunchedEffect(songId) { showHeartPop = false }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(600); showHeartPop = false }
    }

    // ---------- Lyrics ----------
    var showLyrics by remember { mutableStateOf(false) }

    // ---------- Layout ----------
    Box(modifier = Modifier.fillMaxSize().background(immersiveColor)) {

        // Layer 1: Blurred album art backdrop (very subtle, fills bg)
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.15f
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
            )
        }

        // Layer 2: Solid immersive color overlay (covers the blurred bg
        // except where the cover art sits — the cover is drawn on top)
        // This makes the bg a clean immersive color, not a blurred image.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(immersiveColor.copy(alpha = 0.92f))
        )

        // Layer 3: Content
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
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
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )

                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
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
                            Icon(
                                imageVector = CoralIcons.Heart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
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

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Album art (BitChord size — NOT full screen) ----
            // ~75% of screen width, centered, high quality, rounded corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1A))
                    .pointerInput(title) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (songId != null) {
                                    val nowFav = PlaylistStore.toggleFavorite(songId)
                                    if (nowFav) showHeartPop = true
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
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Heart pop
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
                                .size(80.dp)
                                .scale(heartPopScale)
                        )
                    }
                }
            }

            // ---- Gradient blend (cover bottom → immersive color) ----
            // A small gradient that transitions from the cover area to the
            // immersive background, so there's no hard edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to immersiveColor.copy(alpha = 0f),
                            1f to immersiveColor.copy(alpha = 1f)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PlayfairItalicFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontFamily = NyghtSerifFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- Progress bar ----
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(effectiveProgress)
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0xFFFF6B6B))
                )
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

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Transport controls ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Icon(
                        imageVector = CoralIcons.SkipPrev,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

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
                        imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

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
                    Icon(
                        imageVector = CoralIcons.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Bottom row ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // ---- Lyrics sheet ----
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
 * Derives a premium-looking dark surface color from the album art's
 * dominant color. Uses HSL color science (ported from ArchiveTune's
 * deriveArtworkSurfaceColor).
 *
 * This produces colors that are:
 *   - Dark (lightness ~0.16, good for white text)
 *   - Saturated (0.32-0.54 saturation range — not muddy gray)
 *   - Hue-preserving (keeps the album's color character)
 *
 * @param sourceColor The dominant color from the album art
 * @param darkLightness Target lightness for dark mode (0.0 = black, 1.0 = white)
 * @param darkSaturationRange Clamp saturation to this range (prevents neon/muddy)
 */
private fun deriveArtworkSurfaceColor(
    sourceColor: Color,
    darkLightness: Float = 0.16f,
    darkSaturationRange: ClosedFloatingPointRange<Float> = 0.32f..0.54f,
    monochromeSaturationThreshold: Float = 0.06f
): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(sourceColor.toArgb(), hsl)
    val isMonochrome = hsl[1] < monochromeSaturationThreshold
    hsl[2] = darkLightness
    hsl[1] = if (isMonochrome) {
        0f
    } else {
        hsl[1].coerceIn(darkSaturationRange.start, darkSaturationRange.endInclusive)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
