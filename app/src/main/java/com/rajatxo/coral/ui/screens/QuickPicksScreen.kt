package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.VinylHalo
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.extractPalette

/**
 * Quick Picks Screen — Vinyl Halo selector.
 *
 * Layout:
 *   Column(fillMaxSize, no bg — parent paints dynamic color):
 *     Header (statusBarsPadding):
 *       Row: SleepCapsule (left, weight 1) + "Quick picks" title (right, Quirk italic 34sp)
 *       Spacer(8dp)
 *       Toggle capsule: "Based on last played" ↔ "Random picks"
 *     VinylHalo (weight 1f):
 *       Spinning vinyl disc (current pick's album art at center label)
 *       Halo ring of satellites (album art thumbnails around the vinyl)
 *       Active satellite: scaled + glowing ring + needle line to vinyl edge
 *     Footer (bottom, above mini player):
 *       AnimatedContent: Pick title (Playfair Italic 22sp)
 *       AnimatedContent: Pick artist (NyghtSerif Light Italic 14sp, muted)
 *       Hint: "Tap the record to play" (small, italic, 35% alpha)
 *
 * Background contract:
 *   This screen does NOT paint its own background. The dynamic bg color
 *   (extracted from current pick's album art) is hoisted to HomeScreen via
 *   onBgColorChange. The parent paints the entire screen — including the
 *   48dp nav rail area — so the bg color extends seamlessly under the rail.
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {},
    onBgColorChange: (Color) -> Unit = {}
) {
    val context = LocalContext.current
    var isRandomMode by remember { mutableStateOf(false) }

    // --- Song selection logic (unchanged) ---
    // "Based on last played": songs from same artist/album as currently playing
    // "Random picks": 15 random songs
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

    // --- Current pick index (replaces pagerState) ---
    var currentPickIndex by remember { mutableStateOf(0) }
    LaunchedEffect(quickPicksSongs) {
        currentPickIndex = 0
    }

    // --- Dynamic bg color (hoisted to parent) ---
    var currentBgColor by remember { mutableStateOf(Color(0xFF1A1A1A)) }
    LaunchedEffect(currentBgColor) {
        onBgColorChange(currentBgColor)
    }
    LaunchedEffect(currentPickIndex, quickPicksSongs) {
        val currentSong = quickPicksSongs.getOrNull(currentPickIndex)
        if (currentSong?.albumArtUri != null) {
            extractPalette(context, currentSong.albumArtUri)?.let { palette ->
                currentBgColor = darkenColor(palette.primary)
            }
        } else {
            currentBgColor = Color(0xFF1A1A1A)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // NO background — parent (HomeScreen) paints the entire screen,
            // including the nav rail area, with the hoisted dynamic color.
    ) {
        // ============================================================
        // HEADER (title + toggle)
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.rajatxo.coral.ui.components.SleepTimerCapsule(
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

            Spacer(modifier = Modifier.size(8.dp))

            // Toggle capsule: Based on last played ↔ Random picks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
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
        }

        // ============================================================
        // VINYL HALO (fills middle, weight 1f)
        // ============================================================
        if (quickPicksSongs.isNotEmpty()) {
            VinylHalo(
                songs = quickPicksSongs,
                currentPickIndex = currentPickIndex,
                onPickChange = { currentPickIndex = it },
                onPlayCurrentPick = {
                    if (currentPickIndex in quickPicksSongs.indices) {
                        onSongClick(quickPicksSongs[currentPickIndex])
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ============================================================
            // FOOTER (current pick title + artist + hint)
            // ============================================================
            val currentPick = quickPicksSongs.getOrNull(currentPickIndex)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pick title — Playfair Display Italic, big and elegant
                AnimatedContent(
                    targetState = currentPick,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "titleMorph"
                ) { pick ->
                    Text(
                        text = pick?.title ?: "",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = PlayfairItalicFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Pick artist — NyghtSerif Light Italic, muted
                AnimatedContent(
                    targetState = currentPick,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "artistMorph"
                ) { pick ->
                    Text(
                        text = pick?.artist ?: "",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontFamily = NyghtSerifFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Hint text
                Text(
                    text = "Tap the record to play",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
            }
        } else {
            // --- Empty state ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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

/**
 * Darkens a color for use as a background so white text stays readable.
 * Uses the same luminance-based approach as the playlist detail screen.
 */
private fun darkenColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val darkenFactor = 0.35f + 0.45f * luminance
    return androidx.compose.ui.graphics.lerp(color, Color.Black, darkenFactor)
}
