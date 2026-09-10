package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.rajatxo.coral.ui.components.UnderwaterBackground
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.extractPalette

/**
 * Quick Picks Screen — "Underwater" concept.
 *
 * Inspired by Rajat's reference: a deep ocean scene with god rays, caustics,
 * drifting particles, and a backlit focal point (the whale in the reference).
 *
 * Layout:
 *   Box(fillMaxSize):
 *     UnderwaterBackground (procedural Canvas: gradient + god rays + caustics
 *                            + particles + manta rays + tinted by album color)
 *     Column:
 *       Header: SleepCapsule + "Quick picks" title + toggle capsule
 *       LazyRow of large album-art cards (140dp, active scales to 1.3x = 180dp)
 *         Active card gets "whale treatment":
 *           - Bright cyan rim light around edge
 *           - Soft cyan glow halo behind it
 *           - Backlit focal point like the whale in the reference
 *         Inactive cards: alpha 0.6, slight desaturation
 *       Footer: Active title (Playfair Italic) + artist (NyghtSerif)
 *
 * Background contract:
 *   Unlike previous versions, this screen PAINTS its own background
 *   (the underwater canvas). The bg color is no longer hoisted — instead,
 *   we blend the album's palette color into the ocean gradient (70% navy,
 *   30% album color) so the underwater vibe stays consistent even as the
 *   active pick changes.
 *
 *   HomeScreen's root Box still has its own bg color (from album palette)
 *   which shows through under the nav rail area. To keep the rail area
 *   consistent with the screen, we report a deep navy color via
 *   onBgColorChange so the rail blends with the ocean.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    // --- Current pick index + LazyRow state ---
    var currentPickIndex by remember { mutableStateOf(0) }
    LaunchedEffect(quickPicksSongs) { currentPickIndex = 0 }

    val lazyListState = rememberLazyListState()

    // --- Tint color (from active album's palette) for the underwater bg ---
    var tintColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(currentPickIndex, quickPicksSongs) {
        val song = quickPicksSongs.getOrNull(currentPickIndex)
        if (song?.albumArtUri != null) {
            extractPalette(context, song.albumArtUri)?.let { palette ->
                tintColor = palette.primary
            }
        } else {
            tintColor = null
        }
    }

    // --- Report bg color to parent (deep navy, blended with tint) ---
    // This makes the nav rail area blend with the ocean bg.
    LaunchedEffect(tintColor) {
        val baseNavy = Color(0xFF0A1F3A)
        val blended = tintColor?.let { tint ->
            Color(
                red = (baseNavy.red * 0.7f + tint.red * 0.3f),
                green = (baseNavy.green * 0.7f + tint.green * 0.3f),
                blue = (baseNavy.blue * 0.7f + tint.blue * 0.3f),
                alpha = 1f
            )
        } ?: baseNavy
        onBgColorChange(blended)
    }

    // --- Track which card is centered in the LazyRow ---
    // We use derivedStateOf to avoid recomputing on every scroll frame.
    val centeredIndex by remember {
        derivedStateOf {
            val firstVisible = lazyListState.firstVisibleItemIndex
            val firstOffset = lazyListState.firstVisibleItemScrollOffset
            // If more than half of the first visible item is scrolled off,
            // the next item is the "centered" one.
            if (firstOffset > 200) firstVisible + 1 else firstVisible
        }
    }
    LaunchedEffect(centeredIndex) {
        if (centeredIndex in quickPicksSongs.indices) {
            currentPickIndex = centeredIndex
        }
    }

    // Scroll to a position when currentPickIndex changes (e.g. user tapped
    // a far-off card)
    LaunchedEffect(currentPickIndex) {
        if (currentPickIndex != centeredIndex) {
            lazyListState.animateScrollToItem(currentPickIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // NO background here — UnderwaterBackground paints it.
    ) {
        // ============================================================
        // LAYER 1: Underwater background (procedural Canvas)
        // ============================================================
        UnderwaterBackground(
            tintColor = tintColor,
            modifier = Modifier.fillMaxSize()
        )

        // ============================================================
        // LAYER 2: Header + cards + footer (over the bg)
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // --- Header ---
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

                // Toggle capsule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
            }

            // --- LazyRow of large album-art cards ---
            if (quickPicksSongs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LazyRow(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 80.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            count = quickPicksSongs.size,
                            key = { it }
                        ) { index ->
                            val song = quickPicksSongs[index]
                            val isActive = index == currentPickIndex
                            PickCard(
                                song = song,
                                isActive = isActive,
                                onClick = {
                                    if (isActive) {
                                        onSongClick(song)
                                    } else {
                                        currentPickIndex = index
                                    }
                                },
                                modifier = Modifier
                                    .size(140.dp)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }

                // --- Footer (current pick title + artist) ---
                val currentPick = quickPicksSongs.getOrNull(currentPickIndex)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentPick,
                        transitionSpec = {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
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
                    AnimatedContent(
                        targetState = currentPick,
                        transitionSpec = {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
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
                    Text(
                        text = "Swipe to drift · tap to play",
                        color = Color.White.copy(alpha = 0.4f),
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
}

/**
 * A large album-art card. When active, gets the "whale treatment":
 *   - Scale 1.3x
 *   - Bright cyan rim light around the edge (drawn as a glow border)
 *   - Soft cyan glow halo behind it (radial gradient, additive blend)
 *   - Full alpha (inactive = 0.6 alpha)
 *
 * The glow + rim light mimic the backlit whale in the reference image.
 */
@Composable
private fun PickCard(
    song: Song,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Animate scale and alpha smoothly
    val targetScale = if (isActive) 1.3f else 1.0f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "cardScale"
    )
    val targetAlpha = if (isActive) 1f else 0.55f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .alpha(animatedAlpha)
    ) {
        // --- Glow halo behind the card (only when active) ---
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Expand the glow beyond the card bounds
                        scaleX = 1.4f
                        scaleY = 1.4f
                        translationX = 0f
                        translationY = 0f
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFA8D5FF).copy(alpha = 0.5f),
                                Color(0xFFA8D5FF).copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // --- Card body (album art + rim light) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Album art
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
                    Text(
                        text = "🎵",
                        fontSize = 40.sp
                    )
                }
            }

            // Rim light overlay (only when active) — bright cyan edge glow
            // like the backlit whale in the reference image.
            if (isActive) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    val w = size.width
                    val h = size.height
                    // Draw a soft cyan ring around the inner edge of the card
                    drawRoundRect(
                        color = Color(0xFFA8D5FF).copy(alpha = 0.7f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}
