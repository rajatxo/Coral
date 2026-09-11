package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Quick Picks Screen — Editorial Column.
 *
 * A single-column vertical list of full-width album art cards. Clean,
 * restrained, classy. Lets the album art be the hero.
 *
 * Each card:
 *   - Full-width square album art (no gradient bg, no border, no toys)
 *   - Dark gradient overlay at the BOTTOM of the image (for text readability)
 *   - Song title in Playfair Display Italic (overlaid on image, bottom-left)
 *   - Artist in NyghtSerif Light Italic (muted, below title)
 *   - Thin coral accent line (1dp) under the title
 *
 * Motion: subtle parallax — album art scrolls slightly slower than the
 * text. No bouncy springs, no wind, no strings.
 *
 * Background: pure black (AMOLED-friendly).
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

    // --- Base song list ---
    val baseSongs = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()

        if (isRandomMode) {
            songs.shuffled()
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
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct()
                val rest = songs.filter { it.id !in related.map { s -> s.id } }.shuffled()
                (related + rest)
            } else {
                songs.shuffled()
            }
        }
    }

    // --- Infinite scroll ---
    val visibleSongs = remember(baseSongs) { mutableStateListOf<Song>().apply { addAll(baseSongs) } }
    val listState = rememberLazyListState()

    LaunchedEffect(baseSongs) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems - lastVisible
        }
            .distinctUntilChanged()
            .collect { remaining ->
                if (remaining <= 5 && baseSongs.isNotEmpty()) {
                    visibleSongs.addAll(baseSongs.shuffled())
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

            // --- Editorial column (single column, full-width cards) ---
            if (visibleSongs.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),  // space for mini player
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(visibleSongs, key = { it.id.toString() + "-" + visibleSongs.indexOf(it) }) { song ->
                        EditorialCard(
                            song = song,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
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
 * A single editorial card — full-width album art with text overlay.
 *
 * Visual:
 *   - Full-width square album art (ContentScale.Crop)
 *   - Dark gradient overlay at the bottom (for text readability)
 *   - Song title in Playfair Display Italic (large, overlaid bottom-left)
 *   - Artist in NyghtSerif Light Italic (muted, below title)
 *   - Thin coral accent line (1dp) under the title
 *
 * Motion: subtle parallax — album art scrolls slightly slower than the
 * list. No springs, no wind, no bouncy toys.
 */
@Composable
private fun EditorialCard(
    song: Song,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)  // square — full-width album art
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // --- Album art ---
        if (song.albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(400)
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

        // --- Dark gradient overlay at the bottom (for text readability) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.85f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // --- Text overlay (bottom-left) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Song title (Playfair Display Italic, large, white)
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PlayfairItalicFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Coral accent line (1dp) — the only color pop, restrained
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 2.dp)
                    .background(Color(0xFFFF6B6B))
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Artist (NyghtSerif Light Italic, muted)
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontFamily = NyghtSerifFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
