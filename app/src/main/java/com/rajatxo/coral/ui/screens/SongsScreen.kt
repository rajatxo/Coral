package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.BugLineRefreshIndicator
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.launch

/**
 * Songs tab — each song is a capsule shaped like the mini player.
 *
 * No drawBackdrop, no drawLayer, no graphicsLayer — no crash.
 * Just semi-transparent dark pills with circular album art.
 *
 * Layout (top to bottom):
 *   1. Background gradient (same as Quick Picks)
 *   2. Blur header (rendered in HomeScreen)
 *   3. LazyColumn: "All songs" header + song capsule rows
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArt: android.net.Uri? = null,
    onSongClick: (Song) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onRefresh: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title.lowercase() } }

    // Background palette + gradient
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(currentSongArt) {
        if (currentSongArt != null) {
            PaletteCache.get(currentSongArt)?.let { palette = it }
            extractPalette(context, currentSongArt)?.let {
                palette = it
                PaletteCache.put(currentSongArt, it)
            }
        }
    }

    val vibrantTop by animateColorAsState(palette.primary.copy(alpha = 0.85f), tween(800), "sBgVT")
    val animatedBottom by animateColorAsState(Color(0xFF05050A), tween(800), "sBgB")
    val darkBase = Color(0xFF05050A)

    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                // Simple gradient: 60% normal color → 40% blend to dark
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f  to vibrantTop,    // 0-60%: solid palette color
                        0.60f to vibrantTop,    // still solid at 60%
                        1.0f  to animatedBottom  // 60-100%: blend to near-black
                    )
                )
            )
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { try { onRefresh() } finally { isRefreshing = false } }
            },
            state = ptrState,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 108.dp, bottom = 100.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // "All songs" header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("All songs", color = Color.White, fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
                        Icon(CoralIcons.ChevronRight, null, tint = Color.White.copy(0.6f),
                            modifier = Modifier.size(20.dp))
                        BugLineRefreshIndicator(ptrState.distanceFraction, isRefreshing,
                            Modifier.weight(1f).height(20.dp))
                        Text("${songs.size}", color = Color.White.copy(0.6f),
                            fontSize = 14.sp, fontFamily = CalSansFamily)
                    }
                }
                // Song capsule rows
                items(sortedSongs, key = { it.id }) { song ->
                    SongCapsule(
                        song = song,
                        isCurrent = currentSongId == song.id,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SONG CAPSULE — mini player-shaped row
// ════════════════════════════════════════════════════════════════════
// Same shape + design language as the mini player:
//   • Full-width pill (RoundedCornerShape 32dp)
//   • Semi-transparent dark background (alpha 0.35)
//   • Thin white border (alpha 0.1) — frosted glass feel
//   • Circular album art (44dp, clipped CircleShape) on the left
//   • Song title (CalSans 14sp Medium) + artist (CalSans 12sp) center
//   • Duration (12sp) on the right
//   • Currently-playing song: coral accent on album art border
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SongCapsule(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(pillShape)
    ) {
        // ─── Glossy glass background ───
        // Semi-transparent dark base + subtle white gradient overlay on top
        // for a "glass sheen" effect. No blur — just visual gloss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.08f),  // subtle white sheen at top
                            0.5f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.03f)   // very subtle at bottom
                        )
                    )
                )
        )

        // ─── Content row ───
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ─── Circular album art (left) ───
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (isCurrent) Modifier.background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Music,
                            contentDescription = null,
                            tint = Color(0xFFB0B0B0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            // ─── Title + artist (center) ───
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) Color(0xFFFF6B6B) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ─── Duration (right) ───
            val totalSec = song.duration / 1000
            val mm = totalSec / 60
            val ss = totalSec % 60
            Text(
                text = "$mm:${String.format("%02d", ss)}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily
            )

            Spacer(Modifier.size(8.dp))
        }
    }
}
