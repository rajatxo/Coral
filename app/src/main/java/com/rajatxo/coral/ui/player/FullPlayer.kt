package com.rajatxo.coral.ui.player

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) { extractPalette(context, albumArtUri)?.let { palette = it } }

    val immersiveColor = remember(palette) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(palette.primary.toArgb(), hsl)
        hsl[2] = 0.16f
        hsl[1] = if (hsl[1] < 0.06f) 0f else hsl[1].coerceIn(0.32f, 0.54f)
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try { mediaController?.let { currentPositionMs = it.currentPosition.coerceAtLeast(0L); durationMs = it.duration.coerceAtLeast(0L) } } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showLyrics by remember { mutableStateOf(false) }

    val wheelRotation = remember { Animatable(0f) }
    fun spinWheel(direction: Int) {
        scope.launch {
            wheelRotation.animateTo(wheelRotation.value + direction * 120f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(immersiveColor)) {
        if (albumArtUri != null) {
            AsyncImage(model = albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        Canvas(modifier = Modifier.fillMaxSize(), contentDescription = "background") {
            val w = size.width; val h = size.height; val arcY = h * 0.58f
            val bumpCount = 7; val bumpRadius = w / (bumpCount * 2f)
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, arcY)
                for (i in 0 until bumpCount) {
                    val cx = bumpRadius + i * bumpRadius * 2f
                    arcTo(androidx.compose.ui.geometry.Rect(cx - bumpRadius, arcY - bumpRadius, cx + bumpRadius, arcY + bumpRadius), 0f, 180f, false)
                }
                lineTo(w, h); close()
            }
            drawPath(p, immersiveColor)
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(imageVector = CoralIcons.ChevronDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("NOW PLAYING", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { showMoreMenu = true }, contentAlignment = Alignment.Center) {
                        Icon(imageVector = CoralIcons.MoreVertical, contentDescription = "More", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A1A))) {
                        Row(modifier = Modifier.clickable { showMoreMenu = false; songId?.let { onAddToPlaylist(it) } }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = CoralIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(text = "Add to playlist", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = PlayfairItalicFamily, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))
                Text(text = artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontFamily = NyghtSerifFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(100.dp),
                    contentDescription = "wheel"
                ) {
                    val cx = size.width / 2f; val cy = size.height / 2f; val r = minOf(size.width, size.height) / 2f - 4f
                    drawCircle(Color.White.copy(alpha = 0.15f), r, Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                    rotate(wheelRotation.value) {
                        drawCircle(Color(0xFFFF6B6B), 6.dp.toPx(), Offset(cx, cy - r))
                        drawCircle(Color(0xFFFF6B6B).copy(alpha = 0.3f), 10.dp.toPx(), Offset(cx, cy - r))
                    }
                    drawCircle(Color.White.copy(alpha = 0.3f), 3.dp.toPx(), Offset(cx, cy))
                }
            }

            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(20.dp).pointerInput(durationMs) { detectDragGestures(onDragEnd = {}, onDrag = { change, _ -> if (durationMs > 0) { onSeek(((change.position.x / 1000f).coerceIn(0f, 1f) * durationMs).toLong()) } } }) }) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).align(Alignment.CenterStart).background(Color.White.copy(alpha = 0.15f)))
                Box(modifier = Modifier.fillMaxWidth(progress).height(2.dp).clip(RoundedCornerShape(1.dp)).align(Alignment.CenterStart).background(Color(0xFFFF6B6B)))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatTime(currentPositionMs), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                Text(text = formatTime(durationMs), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable { spinWheel(-1); onPrevClick() }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.SkipPrev, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFFF6B6B)).clickable { onPlayPauseClick(); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) { Icon(imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(28.dp)) }
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable { spinWheel(1); onNextClick() }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.Shuffle, contentDescription = "Shuffle", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { showLyrics = true }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.Queue, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { if (songId != null) { PlaylistStore.toggleFavorite(songId); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }, contentAlignment = Alignment.Center) { Icon(imageVector = if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart, contentDescription = "Favorite", tint = if (isFavorite) palette.accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
            }
        }

        if (showLyrics) {
            LyricsSheet(trackName = title, artistName = artist, albumName = albumName, durationMs = durationMs, currentPositionMs = currentPositionMs, isPlaying = isPlaying, onDismiss = { showLyrics = false }, onSeek = onSeek)
        }
    }
}

private fun formatTime(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
