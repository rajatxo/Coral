package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.rajatxo.coral.util.BlurTransformation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * QuickPicksScreen — "Editorial Gallery" edition.
 *
 * Light/dark theme-aware. Asymmetric hero grid + horizontal carousels.
 * Songs are randomized on each app launch. Carousels are infinite
 * (repeat the song list so you can swipe forever).
 *
 * Pull-to-refresh: pulling down on this page refreshes the Quick Picks
 * content (rolls a new random seed → new hero/recent/more selections,
 * same effect as tab-switching away and back). The indicator is a soft
 * wind animation, NOT the boring circular arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // ─── Background gradient (single dominant color → dark) ─────────
    // Uses ONLY the dominant palette color (palette.primary), not a mix
    // of primary + secondary. One color, fading from vibrant at the top
    // (behind the blur header) to near-black at the bottom.
    //
    // The transition from vibrant → dark is BUTTERY SMOOTH via many
    // closely-spaced color stops. Each stop smoothly steps the alpha
    // down, so there's no visible "band" or hard transition line.
    //
    // Layout:
    //   0.00 - 0.10  → vibrant (alpha 0.85)  [behind blur header]
    //   0.10 - 0.30  → buttery smooth fade   [transition zone]
    //   0.30 - 1.00  → dark gradient         [page content]
    //
    // All colors animate smoothly when the song changes (tween 800ms).
    val vibrantTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.85f),
        animationSpec = tween(800), label = "bgVibrantTop"
    )
    // Mid-transition stops — same hue, progressively lower alpha.
    // These create the "buttery" feel by stepping alpha down gradually
    // instead of one hard jump from 0.85 → 0.25.
    val fade1 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.65f),
        animationSpec = tween(800), label = "bgFade1"
    )
    val fade2 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.45f),
        animationSpec = tween(800), label = "bgFade2"
    )
    val fade3 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.28f),
        animationSpec = tween(800), label = "bgFade3"
    )
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.18f),
        animationSpec = tween(800), label = "bgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.08f),
        animationSpec = tween(800), label = "bgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),  // near-black at the bottom
        animationSpec = tween(800), label = "bgBottom"
    )

    // Dark text colors (always white on the dark gradient)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    // ─── Random seed — changes on every app launch OR pull-to-refresh ─
    // This ensures the song selection is different each time the user
    // opens the app OR pulls to refresh the Quick Picks page.
    // Keyed on refreshKey so bumping it (via PTR) rolls a new seed,
    // which cascades into heroSongs / recentSongs / moreSongs below.
    var refreshKey by remember { mutableIntStateOf(0) }
    val launchSeed = remember(refreshKey) { Random.nextInt() }

    // ─── Pull-to-refresh state ───────────────────────────────────────
    // Pulling down on this page refreshes the Quick Picks content
    // (rolls a new launchSeed → new random song selections, same
    // effect as tab-switching away and back).
    //
    // The wind indicator is rendered in the `indicator` slot of
    // PullToRefreshBox. It sits at the top, behind the fixed header's
    // glass blur (rendered in HomeScreen) — so the streaks appear
    // softened through the frosted glass, like wind through a window.
    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
    val recentSongs = remember(songs.size) {
        if (songs.isEmpty()) emptyList()
        else {
            // 50 most-recently-ADDED songs (newest first). dateAdded is
            // Unix epoch seconds from MediaStore.Audio.Media.DATE_ADDED.
            // Falls back to original insertion order if all dateAdded are 0
            // (cache saved before this field existed -- sort is then a no-op).
            songs.sortedByDescending { it.dateAdded }.take(50)
        }
    }
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 2)).take(50)
    }

    // Recent and More rows now have 50 unique songs each -- no need to
    // infinitely repeat the same 10 songs 3x. The LazyRow scrolls through
    // 50 distinct items directly. Kept the variable names for compat with
    // the LazyRow items() calls below.
    val infiniteRecent = recentSongs
    val infiniteMore = moreSongs

    // Solid dark base — the gradient's low-alpha palette colors composite
    // over this, so the background is always predominantly dark/black
    // EXCEPT in the top zone where the vibrant colors are opaque enough
    // to fully cover the darkBase.
    val darkBase = Color(0xFF05050A)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Refresh the Quick Picks page: bump refreshKey, which rolls
            // a new launchSeed → new heroSongs/recentSongs/moreSongs
            // selections.
            isRefreshing = true
            refreshKey++
            scope.launch {
                delay(900)
                isRefreshing = false
            }
        },
        state = ptrState,
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                // Single dominant color → dark gradient, buttery smooth.
                // 8 stops total — closely spaced for a continuous fade
                // with no visible banding or hard transitions.
                //   0.00  vibrant (0.85)   — behind blur header
                //   0.10  fade1  (0.65)   ┐
                //   0.15  fade2  (0.45)   │ transition zone (buttery)
                //   0.20  fade3  (0.28)   ┘
                //   0.30  top    (0.18)   — dark gradient kicks in
                //   0.55  mid    (0.08)
                //   1.00  bottom (black)
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f  to vibrantTop,
                        0.10f to fade1,
                        0.15f to fade2,
                        0.20f to fade3,
                        0.30f to animatedTop,
                        0.55f to animatedMid,
                        1.0f  to animatedBottom
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 108.dp,      // just below where the blur ends
                bottom = 200.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    textSecondary = textSecondary,
                    launchSeed = launchSeed
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
                    itemsIndexed(
                        items = infiniteRecent,
                        // Stable key = song id + occurrence index, so each
                        // repeated copy of a song has a unique key. Without
                        // a key, LazyRow recomposes ALL items on every
                        // songs update instead of reusing existing ones.
                        key = { index, song -> "${song.id}_$index" }
                    ) { _, song ->
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
                    itemsIndexed(
                        items = infiniteMore,
                        key = { index, song -> "${song.id}_$index" }
                    ) { _, song ->
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
                fontSize = 17.sp,
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
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(14.dp)
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
        // Layer 1 (bottom): BLURRED album art — pre-computed via Coil
        // BlurTransformation (cached in memory) for smooth scrolling.
        if (song.albumArtUri != null) {
            val blurredRequest = remember(song.albumArtUri) {
                ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .transformations(BlurTransformation(36.dp))
                    .build()
            }
            AsyncImage(
                model = blurredRequest,
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

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
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(14.dp)
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
        // Layer 1 (bottom): BLURRED album art — pre-computed via Coil
        // BlurTransformation (cached in memory) for smooth scrolling.
        if (song.albumArtUri != null) {
            val blurredRequest = remember(song.albumArtUri) {
                ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .transformations(BlurTransformation(36.dp))
                    .build()
            }
            AsyncImage(
                model = blurredRequest,
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
                .padding(10.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
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
    textSecondary: Color,
    launchSeed: Int
) {
    val scope = rememberCoroutineScope()
    var isRandomizing by remember { mutableStateOf(false) }

    // Observe pinned song IDs so cards re-render when a song is pinned/unpinned.
    val pinnedIds by com.rajatxo.coral.data.prefs.SpeedDialPinStore.pinnedIds.collectAsState()

    // ─── Build the Speed Dial song list ──
    // Pinned songs come FIRST (in the order they were pinned — Set preserves
    // insertion order for LinkedHashSet, which is what SharedPreferences
    // StringSet returns after our save). They sit at the top-left of the
    // grid and stay there across refresh/restart.
    //
    // Then random songs (excluding pinned) fill the rest. 3 pages × 9 slots
    // = 27, minus 1 for the dice = 26 song slots. Pinned + random = 26.
    //
    // Keyed on (songs.size, pinnedIds, launchSeed) so it recomputes when:
    //   - the library changes (songs.size)
    //   - a song is pinned/unpinned (pinnedIds)
    //   - the user pulls-to-refresh (launchSeed bumps, rolling a new random
    //     pool — pinned songs stay, the rest reshuffle)
    val speedDialSongs = remember(songs.size, pinnedIds, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            // 1. Pinned songs (in pin order), filtered to ones that still
            //    exist in the library (in case a pinned song was deleted).
            val pinnedSongs = pinnedIds.mapNotNull { id ->
                songs.firstOrNull { it.id == id }
            }
            // 2. Random songs, excluding already-pinned ones. Seeded with
            //    launchSeed so PTR refresh (which bumps launchSeed) reshuffles
            //    the non-pinned songs on every pull-to-refresh.
            val pool = songs.filter { it.id !in pinnedIds }.shuffled(Random(launchSeed + 100))
            // 3. Total = 26 (3 pages × 9 slots - 1 for dice)
            val targetCount = 26
            val randomCount = (targetCount - pinnedSongs.size).coerceAtLeast(0)
            pinnedSongs + pool.take(randomCount)
        }
    }

    if (speedDialSongs.isEmpty()) return

    // Section header — "Speed dial" text + chevron right beside it.
    // Aligned with the first card: the LazyColumn has start=20dp padding,
    // and each card has 4dp padding, so the first card's content starts at
    // 24dp. To align the text with the first card's content, add 4dp start
    // padding to the Row.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),  // align with first card's content
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Speed dial",
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        // Chevron right beside the text (no spacer, no weight).
        // 20dp — thicker to match the title's visual weight (was 16dp).
        Icon(
            imageVector = CoralIcons.ChevronRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
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
            // Pre-render 1 page on each side so swiping feels instant
            // (no pop-in when the next page appears). Default is 0 which
            // causes a visible "compose lag" on the first frame of a swipe.
            beyondViewportPageCount = 1,
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
                                        isPinned = song.id in pinnedIds,
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

        // ═══ Page indicator — thin line with center dot showing page number ═══
        // A short thin horizontal line with both ends fading to transparent,
        // and a white circle (dot) centered on the line. Inside the dot,
        // the current page number is shown (1, 2, 3).
        //
        // Replaces the previous LiquidBarPageIndicator (animated gradient
        // bars) per user request — simpler, cleaner, more legible.
        LineWithDotPageIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// LINE WITH DOT PAGE INDICATOR — thin line, faded ends, center dot with page number
// ════════════════════════════════════════════════════════════════════
// A horizontal line with:
//   • Both ends fading to transparent (via a horizontal gradient mask)
//   • A white filled circle centered on the line
//   • The current page number (1-indexed) drawn inside the circle
//
// The line is 80dp wide × 2dp tall. The dot is 18dp diameter.
// The dot sits centered on the line, vertically aligned.
//
// All drawn on a single Canvas with a Text overlay for the page number.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LineWithDotPageIndicator(
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 1) return

    // +1 because currentPage is 0-indexed, but we display 1-indexed.
    val currentPageDisplay = (pagerState.currentPage + 1).coerceIn(1, pageCount)

    Box(
        modifier = modifier
            .width(80.dp)
            .height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // ─── The line + dot, drawn on a Canvas ───
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Offscreen layer is required for BlendMode.DstIn to work —
                // without it, the edge-fade mask would punch through the
                // entire screen instead of just fading the line's ends.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            // ─── (1) Thin horizontal line ───
            val lineHeight = 2f  // thin
            val lineLeft = 0f
            val lineRight = canvasWidth

            // The line itself — a subtle white.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = androidx.compose.ui.geometry.Offset(lineLeft, centerY - lineHeight / 2f),
                size = androidx.compose.ui.geometry.Size(lineRight - lineLeft, lineHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(lineHeight / 2f, lineHeight / 2f)
            )

            // ─── (2) Fade the line's ends to transparent ───
            // Draw a horizontal-gradient mask: opaque (Color.Black) in the
            // middle 60%, fading to transparent at both ends (0-20% and
            // 80-100%). BlendMode.DstIn keeps the line where the mask is
            // opaque and erases it where the mask is transparent.
            //
            // The dot (drawn next) is NOT affected by this mask because
            // it's drawn AFTER the mask — DstIn only affects what's
            // already in the layer at draw time.
            val lineFadeBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.20f to Color.Black,
                    0.80f to Color.Black,
                    1.00f to Color.Transparent
                )
            )
            drawRect(
                brush = lineFadeBrush,
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = size,
                blendMode = BlendMode.DstIn
            )

            // ─── (3) The white dot (circle) in the center ───
            // Drawn AFTER the fade mask so it's fully opaque (not faded).
            val dotDiameter = 18.dp.toPx()
            val dotRadius = dotDiameter / 2f
            val dotCenterX = canvasWidth / 2f
            val dotCenterY = centerY

            // White filled circle.
            drawCircle(
                color = Color.White,
                radius = dotRadius,
                center = androidx.compose.ui.geometry.Offset(dotCenterX, dotCenterY)
            )
        }

        // ─── The page number text, centered on the dot ───
        // Drawn on top of the Canvas, centered via the Box's contentAlignment.
        Text(
            text = "$currentPageDisplay",
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalSansFamily,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// LIQUID BAR PAGE INDICATOR — REMOVED (replaced by LineWithDotPageIndicator)
// ════════════════════════════════════════════════════════════════════
// The old LiquidBarPageIndicator (animated gradient bars) was replaced
// per user request with the simpler LineWithDotPageIndicator above.
// The old code is intentionally removed — not commented out — to keep
// the file clean. The new indicator is a thin line + center dot with
// the page number inside the dot.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LiquidBarPageIndicatorRemoved(
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    // Intentionally empty — kept as a marker so git history shows the
    // removal cleanly. Use LineWithDotPageIndicator instead.
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL CARD — square card with album art + title
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialCard(
    song: Song,
    isCurrent: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(8.dp)

    // ─── Quick tap animation ──
    // Scale down while pressed, spring back on release. Only this card
    // animates — no effect on other cards.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "tapScale"
    )

    // ─── Long-press pin capsule state ──
    var showPinCapsule by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { showPinCapsule = true }
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend ═══
        // Layer 1 (bottom): BLURRED album art — pre-computed via Coil
        // BlurTransformation (cached in memory) for smooth scrolling.
        // Reduced blur (20dp, was 36dp) so the cover isn't cropped too much.
        if (song.albumArtUri != null) {
            val blurredRequest = remember(song.albumArtUri) {
                ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .transformations(BlurTransformation(20.dp))
                    .build()
            }
            AsyncImage(
                model = blurredRequest,
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

        // Layer 2 (top): SHARP album art — top 85%, DstIn blend at bottom.
        // The blend is only behind the text area (bottom 15%).
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 24.dp.toPx()
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

        // (border removed per user request)

        // Title at the bottom (on the blurred part)
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
                .padding(start = 8.dp, bottom = 4.dp)
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

        // Pinned indicator (top-left, small pin icon)
        if (isPinned) {
            Icon(
                imageVector = CoralIcons.Pin,
                contentDescription = "Pinned",
                tint = Color.White,
                modifier = Modifier
                    .padding(6.dp)
                    .size(12.dp)
                    .align(Alignment.TopStart)
            )
        }

        // ═══ Long-press pin capsule overlay ═══
        // Thin capsule with a pin icon, fades in on long-press, stays
        // 5 seconds, fades out on pin or timeout.
        SpeedDialPinCapsule(
            visible = showPinCapsule,
            isPinned = isPinned,
            onPin = {
                if (isPinned) {
                    com.rajatxo.coral.data.prefs.SpeedDialPinStore.unpin(song.id)
                } else {
                    com.rajatxo.coral.data.prefs.SpeedDialPinStore.pin(song.id)
                }
                showPinCapsule = false
            },
            onTimeout = { showPinCapsule = false }
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL PIN CAPSULE — thin capsule with pin icon, shown on long-press
// ════════════════════════════════════════════════════════════════════
// Fades in when [visible] becomes true, stays for 5 seconds, then
// calls [onTimeout]. If the user taps the pin icon, calls [onPin].
// The capsule is a thin pill centered over the card with a semi-transparent
// dark background and a pin icon inside.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialPinCapsule(
    visible: Boolean,
    isPinned: Boolean,
    onPin: () -> Unit,
    onTimeout: () -> Unit
) {
    // Auto-hide after 5 seconds
    if (visible) {
        androidx.compose.runtime.LaunchedEffect(visible) {
            kotlinx.coroutines.delay(5000)
            onTimeout()
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = tween(200)
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = tween(300)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        // Dim the card behind the capsule + center the capsule
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            // Thin capsule with pin icon
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPin
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Pin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) Color(0xFFFF6B6B) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
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
    // ═══ Particle burst randomize button ═══
    // Idle: a soft glowing dot in the center, gently pulsing.
    // On tap: 14 particles burst outward in all directions, each with a
    // gradient color (coral red → orange → pink → gold), shrinking +
    // fading as they fly. Lasts 600ms.
    //
    // The burst is triggered by isLoading going false→true (the parent
    // SpeedDialSection sets isRandomizing=true on tap, waits 800ms,
    // picks a random song, sets isRandomizing=false). The burst lasts
    // 600ms — the remaining 200ms is a "settle" moment where the idle
    // dot reappears before the song plays.

    // ─── Particle data (precomputed once, stable across recompositions) ──
    data class Particle(val angle: Double, val color: Color, val sizeMult: Float, val speedMult: Float)
    val particles = remember {
        val colors = listOf(
            Color(0xFFFF6B6B),  // coral red
            Color(0xFFFF9F40),  // orange
            Color(0xFFFF4081),  // pink
            Color(0xFFFFD700)   // gold
        )
        List(14) { i ->
            Particle(
                angle = (i * 360.0 / 14.0 + kotlin.random.Random.nextFloat() * 20.0) * (kotlin.math.PI / 180.0),
                color = colors[i % colors.size],
                sizeMult = 0.7f + kotlin.random.Random.nextFloat() * 0.6f,
                speedMult = 0.8f + kotlin.random.Random.nextFloat() * 0.4f
            )
        }
    }

    // ─── Idle pulse animation (always running) ──
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // ─── Burst animation (triggered by isLoading) ──
    val burstProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(isLoading) {
        if (isLoading) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val dotRadius = size.minDimension * 0.12f

            // ─── Idle glowing dot (always visible, pulsing) ──
            // 3 concentric circles for a soft glow effect
            val glowRadius = dotRadius * pulseScale
            drawCircle(
                color = Color(0xFFFF6B6B).copy(alpha = 0.08f),
                radius = glowRadius * 2.8f,
                center = center
            )
            drawCircle(
                color = Color(0xFFFF6B6B).copy(alpha = 0.15f),
                radius = glowRadius * 2.0f,
                center = center
            )
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = glowRadius,
                center = center
            )

            // ─── Burst particles (visible during burst animation) ──
            val progress = burstProgress.value
            if (progress > 0f && progress < 1f) {
                val maxDistance = size.minDimension * 0.45f
                particles.forEach { p ->
                    val distance = maxDistance * progress * p.speedMult
                    val x = (center.x + kotlin.math.cos(p.angle) * distance).toFloat()
                    val y = (center.y + kotlin.math.sin(p.angle) * distance).toFloat()
                    val particleSize = dotRadius * p.sizeMult * (1f - progress)
                    val alpha = (1f - progress) * 0.9f

                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = particleSize,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
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
        // Title + chevron beside it (left side).
        // Matches the Speed dial header layout: title, then chevron right,
        // no spacer between them.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                // 20dp — thicker to match the title's visual weight
                // (was 16dp, felt too thin next to the 20sp title text).
                modifier = Modifier.size(20.dp)
            )
        }
        // Count at the extreme right (no chevron after it — chevron
        // is now beside the title).
        Text(
            text = "$count",
            color = textSecondary,
            fontSize = 14.sp,
            fontFamily = CalSansFamily
        )
    }
}
