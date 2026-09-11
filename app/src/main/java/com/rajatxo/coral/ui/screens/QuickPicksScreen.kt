package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.PoppinsFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Quick Picks Screen — Pinterest-style infinite scroll masonry grid.
 *
 * 2-column grid that scrolls vertically. Shows all songs (e.g. 500) in
 * randomized order. When the user scrolls near the end, the list is
 * extended with another shuffled batch — creating infinite scroll like
 * Pinterest. Each card is bigger now (4 visible cards = 2 cols × 2 rows).
 *
 * Each card:
 *   - Vibrant gradient background (extracted from album art palette,
 *     saturation boosted for vibrancy)
 *   - Album cover (rounded square, centered)
 *   - Song name below cover (Cal Sans font)
 *   - Artist name (Poppins font, muted)
 *   - White border (subtle card edge)
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

    // --- Base song list (depending on mode) ---
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
                // Related songs first, then the rest (shuffled)
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct()
                val rest = songs.filter { it.id !in related.map { s -> s.id } }.shuffled()
                (related + rest)
            } else {
                songs.shuffled()
            }
        }
    }

    // --- Infinite scroll: append shuffled batches when near the end ---
    // The visible list starts as `baseSongs`. When the user scrolls to within
    // 20 items of the end, we append another shuffled batch (the same songs
    // re-shuffled). This creates the Pinterest-style infinite scroll.
    val visibleSongs = remember(baseSongs) { mutableStateListOf<Song>().apply { addAll(baseSongs) } }
    val gridState = rememberLazyGridState()

    // --- Wind phase: drives the gentle sway of all cards ---
    // A single infinite transition provides a phase value 0 → 2π over 5 seconds.
    // Each card computes its own rotation as sin(phase + cardIndex * offset),
    // so every card sways at a slightly different phase — organic, not synced.
    val windTransition = rememberInfiniteTransition(label = "wind")
    val windPhase by windTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "windPhase"
    )

    // Detect when user is near the end of the list, then append more songs
    LaunchedEffect(baseSongs) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            totalItems - lastVisible
        }
            .distinctUntilChanged()
            .collect { remaining ->
                if (remaining <= 20 && baseSongs.isNotEmpty()) {
                    // Append another shuffled batch — infinite scroll!
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

            // --- Pinterest-style infinite scroll grid (4 cards on screen) ---
            // Each card is sized to exactly half the available height (minus
            // spacing) so only 2 rows × 2 cols = 4 cards fit on screen.
            if (visibleSongs.isNotEmpty()) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val availableHeight = maxHeight
                    // 2 rows + 1 gap (12dp) = availableHeight
                    // Each card height = (availableHeight - 12dp) / 2
                    val cardHeight = (availableHeight - 12.dp) / 2

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleSongs, key = { it.id.toString() + "-" + visibleSongs.indexOf(it) }) { song ->
                            val cardIndex = visibleSongs.indexOf(song)
                            PickCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                cardHeight = cardHeight,
                                cardIndex = cardIndex,
                                windPhase = windPhase
                            )
                        }
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
 *   - Vibrant gradient background (album palette primary → secondary → tertiary)
 *     — saturation boosted for vibrant feel
 *   - Album cover (rounded square, centered, with subtle white border)
 *   - Song name below cover (Cal Sans, white, bold)
 *   - Artist name (Poppins, white 70%, regular)
 *   - White border (1dp, 30% alpha)
 *   - Glossy diagonal reflection (top-left → bottom-right streak)
 */
@Composable
private fun PickCard(
    song: Song,
    onClick: () -> Unit,
    cardHeight: androidx.compose.ui.unit.Dp,
    cardIndex: Int,
    windPhase: Float
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var palette by remember { mutableStateOf<CoralPalette?>(null) }

    // Extract palette from album art for the vibrant gradient bg
    LaunchedEffect(song.id, song.albumArtUri) {
        if (song.albumArtUri != null) {
            palette = extractPalette(context, song.albumArtUri)
        }
    }

    // --- Entry animation: card "drops" from above + swings to settle ---
    // Starts at progress=0 (tilted -15deg, translated up) and animates to 1 (settled).
    // Plays once when the card enters composition (like someone released it).
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(song.id) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.55f,  // bouncy settle
                stiffness = 120f
            )
        )
    }

    // --- Tap response: card swings backward when tapped (like a gust hit it) ---
    // 0 = resting, 1 = swung back. Animates to 1 then back to 0 on tap.
    val tapResponse = remember { Animatable(0f) }

    // --- Compute total rotation ---
    // Entry: starts at -15deg, settles to 0deg as entryProgress → 1
    val entryRotation = -15f * (1f - entryProgress.value)

    // Wind sway: gentle sine wave, ±2.5deg, each card has a different phase.
    // Only applies fully once the card has settled (entryProgress → 1).
    val windOffset = cardIndex * 0.8f  // different phase per card
    val swayRotation = (sin(windPhase.toDouble() + windOffset).toFloat() * 2.5f) * entryProgress.value

    // Tap: swings backward up to -8deg
    val tapRotation = -8f * tapResponse.value

    // Slight scale-down on tap for "pushed back" feel
    val tapScale = 1f - 0.04f * tapResponse.value

    val totalRotation = entryRotation + swayRotation + tapRotation

    // Gradient colors from boosted palette (fall back to coral brand colors)
    val gradientStart = palette?.primary ?: Color(0xFFFF6B6B)
    val gradientMid = palette?.secondary ?: Color(0xFFFF8E53)
    val gradientEnd = palette?.tertiary ?: Color(0xFF1A1A1A)

    // Outer Box = cell bounds (does NOT rotate — just holds the swaying content)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
    ) {
        // --- Swaying container: string + card, rotates around TOP CENTER ---
        // This is the pendulum — pivot is at the top where the string attaches.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = totalRotation
                    transformOrigin = TransformOrigin(0.5f, 0f)  // top center pivot
                    scaleX = tapScale
                    scaleY = tapScale
                }
        ) {
            // --- The string: thin vertical line above the card ---
            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                val x = size.width / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // --- The card itself ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            // Trigger the wind-back tap animation, then call onClick
                            scope.launch {
                                tapResponse.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f)
                                )
                                tapResponse.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 150f)
                                )
                            }
                            onClick()
                        }
                    )
            ) {
                // --- Glossy diagonal reflection (3D effect) ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.1f),
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
                    // Album cover (rounded square, white-tinted border)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
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
                                Text(text = "🎵", fontSize = 36.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Song name (Cal Sans, white, semi-bold)
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artist name (Poppins, white 70%, regular)
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = PoppinsFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
