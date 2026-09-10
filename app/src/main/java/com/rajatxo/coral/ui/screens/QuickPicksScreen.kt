package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import com.rajatxo.coral.ui.components.SaturnRings
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.extractPalette

/**
 * Quick Picks Screen — "Saturn" concept (3D tilted orbital ring).
 *
 * Layout:
 *   Column(fillMaxSize, no bg — parent paints dynamic color):
 *     Header (statusBarsPadding):
 *       Row: SleepCapsule (left) + "Quick picks" title (right, Quirk italic)
 *       Toggle capsule: Based on last played ↔ Random picks
 *     Main (weight 1f):
 *       SaturnRings (3D tilted ring of picks + starfield + glow)
 *       Position label (top right): "RING α · 03/15"
 *     Footer (bottom, above mini player):
 *       AnimatedContent: Pick title (Playfair Italic 24sp)
 *       AnimatedContent: Pick artist (NyghtSerif Light Italic 14sp, muted)
 *
 * Background contract:
 *   This screen does NOT paint its own background. The dynamic bg color
 *   (extracted from current pick's album art) is hoisted to HomeScreen via
 *   onBgColorChange. The parent paints the entire screen — including the
 *   nav rail area — so the bg color extends seamlessly under the rail.
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

    // --- Current pick index ---
    var currentPickIndex by remember { mutableStateOf(0) }
    LaunchedEffect(quickPicksSongs) { currentPickIndex = 0 }

    // --- Bg color (hoisted to parent) ---
    var currentBgColor by remember { mutableStateOf(Color(0xFF050810)) }
    LaunchedEffect(currentBgColor) { onBgColorChange(currentBgColor) }
    LaunchedEffect(currentPickIndex, quickPicksSongs) {
        val song = quickPicksSongs.getOrNull(currentPickIndex)
        if (song?.albumArtUri != null) {
            extractPalette(context, song.albumArtUri)?.let { palette ->
                currentBgColor = abyssalDarkenColor(palette.primary)
            }
        } else {
            currentBgColor = Color(0xFF050810)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // NO background — parent (HomeScreen) paints the entire screen,
            // including the nav rail area, with the hoisted dynamic color.
    ) {
        // ============================================================
        // HEADER (title + toggle) — unchanged
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
        // MAIN CONTENT — Saturn Rings (3D tilted orbital ring)
        // ============================================================
        if (quickPicksSongs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Position label (top right, mechanical style)
                Text(
                    text = "RING α · ${String.format("%02d", currentPickIndex + 1)}/${quickPicksSongs.size}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 20.dp, top = 8.dp)
                )

                // The ring + picks + starfield + glow
                SaturnRings(
                    songs = quickPicksSongs,
                    currentPickIndex = currentPickIndex,
                    onPickChange = { currentPickIndex = it },
                    onPlayCurrentPick = {
                        if (currentPickIndex in quickPicksSongs.indices) {
                            onSongClick(quickPicksSongs[currentPickIndex])
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ============================================================
            // FOOTER (current pick title + artist)
            // ============================================================
            val currentPick = quickPicksSongs.getOrNull(currentPickIndex)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title — Playfair Display Italic, large and elegant
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = PlayfairItalicFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Artist — NyghtSerif Light Italic, muted
                AnimatedContent(
                    targetState = currentPick,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "artistMorph"
                ) { pick ->
                    Text(
                        text = pick?.artist ?: "",
                        color = Color.White.copy(alpha = 0.65f),
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
                    text = "Tap a pick to bring it to the front · tap again to play",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // --- Empty state ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
 * Darkens a color for the cosmic bg, with a slight blue shift for deep-space feel.
 */
private fun abyssalDarkenColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val darkenFactor = 0.55f + 0.35f * luminance
    val darkened = androidx.compose.ui.graphics.lerp(color, Color.Black, darkenFactor)
    return Color(
        red = darkened.red * 0.85f,
        green = darkened.green * 0.92f,
        blue = (darkened.blue * 1.0f).coerceAtMost(1f),
        alpha = darkened.alpha
    )
}
