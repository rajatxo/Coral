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

            // --- Clothesline Lanterns grid (4 cards on screen, staggered hang) ---
            // A horizontal clothesline runs across the top of the grid. Cards
            // hang from it at varying lengths (staggered like wind chimes).
            if (visibleSongs.isNotEmpty()) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val availableHeight = maxHeight
                    // 2 rows + 1 gap (12dp) = availableHeight
                    // Each cell height = (availableHeight - 12dp) / 2
                    // (cell is taller than the card to leave room for the hang string)
                    val cellHeight = (availableHeight - 12.dp) / 2

                    // Track which card is "popped forward" (tapped). When set,
                    // that card scales up + brightens, others dim to 50%.
                    var activeCardId by remember { mutableStateOf<Long?>(null) }

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
                                onClick = {
                                    if (activeCardId == song.id) {
                                        // Already active → play it
                                        onSongClick(song)
                                    } else {
                                        // First tap: pop forward
                                        activeCardId = song.id
                                    }
                                },
                                cellHeight = cellHeight,
                                cardIndex = cardIndex,
                                windPhase = windPhase,
                                isActive = activeCardId == song.id,
                                anyActive = activeCardId != null
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
 * A single Clothesline Lantern card.
 *
 * Structure:
 *   Box (cell bounds, fixed height)
 *     Column (swaying container, rotates around TOP CENTER)
 *       Canvas (vertical string, length = hangLength)
 *       Box (the card itself, gradient + border + glossy + content)
 *
 * Visual:
 *   - Vertical string from top of cell down to the card (length varies per card → staggered)
 *   - Card hangs at the bottom of the string
 *   - Wind sway: gentle ±2.5deg rotation, different phase per card
 *   - Entry animation: card drops from -15deg + swings to settle
 *   - Pop-forward: when active, scales up 1.08 + full brightness; when another
 *     card is active, dims to 50% alpha
 *   - Vibrant gradient bg (boosted album palette)
 *   - Glossy diagonal reflection
 *   - White border
 */
@Composable
private fun PickCard(
    song: Song,
    onClick: () -> Unit,
    cellHeight: androidx.compose.ui.unit.Dp,
    cardIndex: Int,
    windPhase: Float,
    isActive: Boolean,
    anyActive: Boolean
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
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(song.id) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = 120f
            )
        )
    }

    // --- Tap response: card swings backward when tapped ---
    val tapResponse = remember { Animatable(0f) }

    // --- Varying hang length: staggered like wind chimes ---
    // Pattern: 0, 35, 15, 50, 25, 40, 10, 45 (dp) — repeating every 8 cards.
    // This creates the "lanterns at varying heights" look.
    val hangLengths = listOf(0.dp, 35.dp, 15.dp, 50.dp, 25.dp, 40.dp, 10.dp, 45.dp)
    val hangLength = hangLengths[cardIndex % hangLengths.size]

    // --- Compute total rotation ---
    val entryRotation = -15f * (1f - entryProgress.value)
    val windOffset = cardIndex * 0.8f
    val swayRotation = (sin(windPhase.toDouble() + windOffset).toFloat() * 2.5f) * entryProgress.value
    val tapRotation = -8f * tapResponse.value

    // --- Pop-forward scale + dim ---
    // Active: scale up 1.08. Another active (not this): scale 1.0, dim 50%.
    val popScale = if (isActive) 1.08f else 1f
    val dimAlpha = when {
        isActive -> 1f
        anyActive -> 0.5f
        else -> 1f
    }
    // Smooth the scale + alpha with animateFloatAsState
    val animatedPopScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = popScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "popScale"
    )
    val animatedDimAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dimAlpha,
        animationSpec = tween(300),
        label = "dimAlpha"
    )

    val tapScale = 1f - 0.04f * tapResponse.value
    val totalScale = animatedPopScale * tapScale
    val totalRotation = entryRotation + swayRotation + tapRotation

    // Gradient colors from boosted palette (fall back to coral brand colors)
    val gradientStart = palette?.primary ?: Color(0xFFFF6B6B)
    val gradientMid = palette?.secondary ?: Color(0xFFFF8E53)
    val gradientEnd = palette?.tertiary ?: Color(0xFF1A1A1A)

    // Outer Box = cell bounds (does NOT rotate — just holds the swaying content)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .graphicsLayer {
                alpha = animatedDimAlpha
                scaleX = totalScale
                scaleY = totalScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
    ) {
        // --- Swaying container: string + card, rotates around TOP CENTER ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = totalRotation
                    transformOrigin = TransformOrigin(0.5f, 0f)  // top center pivot
                }
        ) {
            // --- The vertical string: from top of cell down to the card ---
            // Length varies per card (staggered hang lengths).
            Canvas(modifier = Modifier.fillMaxWidth().height(hangLength + 8.dp)) {
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
                    .border(
                        1.dp,
                        if (isActive) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    )
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
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Album cover (rounded square, white-tinted border)
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Song name (Cal Sans, white, semi-bold)
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 13.sp,
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
                        fontSize = 10.sp,
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
