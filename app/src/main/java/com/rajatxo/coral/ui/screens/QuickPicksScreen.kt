package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * QuickPicksScreen — "Editorial Gallery" edition.
 *
 * Light/dark theme-aware. Asymmetric hero grid + horizontal carousels.
 * Songs are randomized on each app launch. Carousels are infinite
 * (repeat the song list so you can swipe forever).
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongArt: android.net.Uri? = null,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // ─── Current song's palette → dark gradient background ──────────
    // The background is a dark gradient using the current song's palette
    // colors. If no song is playing, use a default dark palette.
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
    // Animate the gradient colors smoothly when the song changes.
    // Low alpha values — the palette colors are just subtle hints over
    // a dark base. The background stays predominantly dark/black so
    // it doesn't feel bright.
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.25f),
        animationSpec = tween(800), label = "bgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.secondary.copy(alpha = 0.15f),
        animationSpec = tween(800), label = "bgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),  // near-black at the bottom
        animationSpec = tween(800), label = "bgBottom"
    )

    // Dark text colors (always white on the dark gradient)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    // ─── Random seed — changes on every app launch ─────────────────
    // This ensures the song selection is different each time the user
    // opens the app. The seed is remembered for the lifetime of this
    // composable (which is tied to the app session).
    val launchSeed = remember { Random.nextInt() }

    // ─── Prepare song groups (randomized per launch) ────────────────
    // BUG FIX: previously used remember(songs, currentSongId) which
    // didn't recompute when songs loaded async after the screen was
    // already shown. Now we key on songs.size so it recomputes when
    // songs actually arrive. Also use launchSeed for randomization so
    // the selection changes on every app open.
    val heroSongs = remember(songs.size, currentSongId, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            val current = songs.firstOrNull { it.id == currentSongId }
            val pool = songs.shuffled(Random(launchSeed))
            if (current != null) {
                listOf(current, pool.firstOrNull { it.id != current.id } ?: songs.first())
            } else {
                pool.take(2)
            }
        }
    }
    val recentSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 1)).take(10)
    }
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 2)).take(10)
    }

    // Infinite-repeat versions for the carousels (repeat 10x so you
    // can swipe essentially forever)
    val infiniteRecent = remember(recentSongs) {
        if (recentSongs.isEmpty()) emptyList() else List(10) { recentSongs }.flatten()
    }
    val infiniteMore = remember(moreSongs) {
        if (moreSongs.isEmpty()) emptyList() else List(10) { moreSongs }.flatten()
    }

    // Solid dark base — the gradient's low-alpha palette colors composite
    // over this, so the background is always predominantly dark/black.
    val darkBase = Color(0xFF05050A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedTop, animatedMid, animatedBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 200.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // ═══ Header ═══
            // "Quick picks" title — centered, CalSans, not italic.
            // User icon is on the left (HomeScreen), settings on the
            // right (HomeScreen), this text is centered between them.
            item {
                Text(
                    text = "Quick picks",
                    color = textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ═══ Speed Dial (first row) ═══
            // A paginated grid of square song cards + a "randomize" dice
            // button as the last slot. Tap a card to play that song.
            // Tap the dice → plays a random song.
            // The hero grid (EditorialCard row) has been removed — the
            // Speed Dial is now the first row on Quick Picks.
            item {
                SpeedDialSection(
                    songs = songs,
                    currentSongId = currentSongId,
                    onSongClick = onSongClick,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            // ═══ Recent ═══
            item {
                SectionHeader(
                    title = "Recent",
                    count = recentSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(infiniteRecent) { song ->
                        SquareCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier
                                .width(140.dp)
                                .height(200.dp)
                        )
                    }
                }
            }

            // ═══ More ═══
            item {
                SectionHeader(
                    title = "More picks",
                    count = moreSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(infiniteMore) { song ->
                        LandscapeCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier
                                .width(160.dp)
                                .height(220.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// EDITORIAL CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun EditorialCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend ═══
        //
        // Layer 1 (bottom): BLURRED album art — fills the entire card.
        // This is the "down" part. Heavy blur so it's just soft colors.
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — covers the top ~75% of the card.
        // A DstIn gradient at its bottom edge dissolves the sharp image
        // into the blurred layer behind it (the "blend point").
        // Smaller blend height = less gap, more of the sharp cover shows.
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // DstIn mask: opaque at top → transparent at bottom.
                        // Smaller blend height (40dp) for a tighter blend.
                        val blendHeightPx = 40.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NOW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        // Text sits on top of the blurred bottom part
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SQUARE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SquareCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend ═══
        // Layer 1 (bottom): BLURRED album art — fills entire card
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — top 75%, DstIn blend at bottom
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 30.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        // Text on the blurred bottom part
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF007AFF))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// LANDSCAPE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LandscapeCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend ═══
        // Layer 1 (bottom): BLURRED album art — fills entire card
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — top 75%, DstIn blend at bottom
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 30.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        // Text on the blurred bottom part
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL SECTION
// ════════════════════════════════════════════════════════════════════
// A paginated grid of square song cards + a "randomize" dice button.
// Based on vivi-music's SpeedDial feature:
//   - HorizontalPager with pages of a 3-column grid
//   - Each page shows up to 9 songs (3x3)
//   - The last slot on the first page is a "randomize" dice button
//   - Tap a song card → plays that song
//   - Tap the dice → picks a random song and plays it
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialSection(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song) -> Unit,
    textPrimary: Color,
    textSecondary: Color
) {
    val scope = rememberCoroutineScope()
    var isRandomizing by remember { mutableStateOf(false) }

    // Use up to 17 songs for the speed dial (leaves room for the dice)
    val speedDialSongs = remember(songs.size) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled().take(17)
    }

    if (speedDialSongs.isEmpty()) return

    // Section header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Speed dial",
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${speedDialSongs.size}",
                color = textSecondary,
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Grid layout: 3 columns, paginated
    val targetItemSize = 110.dp
    val columns = 3
    val rows = 3
    val itemsPerPage = columns * rows // 9 per page
    val totalSlots = speedDialSongs.size + 1 // +1 for the dice
    val pageCount = (totalSlots + itemsPerPage - 1) / itemsPerPage
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })

    val itemWidth = targetItemSize

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemWidth * rows + 16.dp)  // 3 rows + padding
        ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until columns) {
                            val itemIndex = row * columns + col
                            val globalItemIndex = page * itemsPerPage + itemIndex

                            // The dice button is the last slot on the first page
                            val isDiceSlot = (globalItemIndex == itemsPerPage - 1)

                            if (isDiceSlot) {
                                RandomizeGridItem(
                                    isLoading = isRandomizing,
                                    onClick = {
                                        if (isRandomizing) {
                                            isRandomizing = false
                                        } else {
                                            isRandomizing = true
                                            scope.launch {
                                                kotlinx.coroutines.delay(800)  // dice animation
                                                val randomSong = songs.random()
                                                isRandomizing = false
                                                onSongClick(randomSong)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .height(itemWidth)
                                        .padding(4.dp)
                                )
                            } else {
                                val actualIndex = if (globalItemIndex < itemsPerPage - 1) {
                                    globalItemIndex
                                } else {
                                    globalItemIndex - 1
                                }
                                val song = speedDialSongs.getOrNull(actualIndex)
                                if (song != null) {
                                    SpeedDialCard(
                                        song = song,
                                        isCurrent = song.id == currentSongId,
                                        onClick = { onSongClick(song) },
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(itemWidth)
                                            .padding(4.dp)
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(itemWidth)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL CARD — square card with album art + title
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Album art
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0.5f
                    )
                )
        )

        // (border removed per user request)

        // Title at the bottom
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )

        // Now-playing dot
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(6.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFF6B6B))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// RANDOMIZE GRID ITEM — dice button with 5-dot pattern
// ════════════════════════════════════════════════════════════════════

@Composable
private fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When loading, dots collapse to center. When idle, dots spread to corners.
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val dotColor = Color.White
        val dotSize = 10.dp
        val padding = 16.dp

        // 5-dot dice pattern (top-left, top-right, center, bottom-left, bottom-right)
        // Top Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Top Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )

        // Loading spinner overlay (Coral accent color)
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFFF6B6B),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SECTION HEADER
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                color = textSecondary,
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
