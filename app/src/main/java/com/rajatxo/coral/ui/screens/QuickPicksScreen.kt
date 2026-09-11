package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette

/**
 * Quick Picks Screen — Pinterest-style masonry grid.
 *
 * 6 cards in a 2-column × 3-row grid, filling the full screen (not the rail).
 * Each card has:
 *   - Vibrant gradient background (extracted from the song's album art palette)
 *   - Album cover (centered, rounded)
 *   - Artist name below the cover
 *   - White border (gives the "card" feel)
 *   - Glossy diagonal reflection (3D effect)
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

    // --- Song selection (6 picks for the grid) ---
    val quickPicksSongs = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()

        if (isRandomMode) {
            songs.shuffled().take(6)
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
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct().take(6)
                if (related.size < 4) {
                    val fillers = songs.filter { it.id !in related.map { s -> s.id } }
                        .shuffled()
                        .take(6 - related.size)
                    (related + fillers).distinct()
                } else {
                    related
                }
            } else {
                songs.shuffled().take(6)
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
                .padding(bottom = 100.dp)  // space for mini player
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
                    .background(Color.Black.copy(alpha = 0.4f))
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

            // --- Pinterest-style grid (2 cols × 3 rows = 6 cards) ---
            if (quickPicksSongs.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quickPicksSongs, key = { it.id }) { song ->
                        PickCard(
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
 * A single Pinterest-style pick card.
 *
 * Visual:
 *   - Vibrant gradient background (album palette: primary → accent → dark)
 *   - Album cover (rounded square, centered)
 *   - Artist name below cover (Playfair Italic)
 *   - Song title (small, secondary)
 *   - White border (1dp, 25% alpha — subtle card edge)
 *   - Glossy diagonal reflection overlay (top-left → bottom-right streak)
 *
 * The gradient + glossy reflection give the card a 3D, vibrant feel.
 */
@Composable
private fun PickCard(
    song: Song,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf<CoralPalette?>(null) }

    // Extract palette from album art for the vibrant gradient bg
    LaunchedEffect(song.id, song.albumArtUri) {
        if (song.albumArtUri != null) {
            palette = extractPalette(context, song.albumArtUri)
        }
    }

    // Gradient colors from palette (fall back to coral brand colors)
    val gradientStart = palette?.primary ?: Color(0xFFFF6B6B)
    val gradientMid = palette?.accent ?: Color(0xFFFF8E53)
    val gradientEnd = Color(0xFF1A1A1A)  // dark fade at bottom for depth

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)  // slightly taller than wide (Pinterest card feel)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        gradientStart,
                        gradientMid,
                        gradientEnd
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // --- Glossy diagonal reflection (3D effect) ---
        // A semi-transparent white diagonal streak from top-left to mid-right.
        // Gives the card a "shiny glass" feel.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
        )

        // --- Card content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Album cover (rounded square, subtle white-tinted border)
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎵", fontSize = 32.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Artist name (Playfair Italic, white, centered)
            Text(
                text = song.artist,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PlayfairItalicFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Song title (small, white 70% — secondary info)
            Text(
                text = song.title,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = NyghtSerifFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
