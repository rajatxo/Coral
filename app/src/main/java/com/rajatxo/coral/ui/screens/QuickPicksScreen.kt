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
import com.rajatxo.coral.ui.components.AstroidRefreshIndicator
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
    val recentSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 1)).take(10)
    }
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 2)).take(10)
    }

    // Infinite-repeat versions for the carousels.
    //
    // PERFORMANCE: reduced from 10x repeat (100 items) to 3x repeat
    // (30 items). 100 items forced LazyRow to manage 100 item slots
    // + 100 key entries on every recomposition. 30 items still feels
    // infinite (you'd have to swipe 30 cards to reach the end) but
    // cuts the slot management overhead by ~3x.
    //
    // Keys are added in the items() calls below so LazyRow can reuse
    // composables across recompositions (was: no key → every item
    // recomposed on every songs update).
    val infiniteRecent = remember(recentSongs) {
        if (recentSongs.isEmpty()) emptyList() else List(3) { recentSongs }.flatten()
    }
    val infiniteMore = remember(moreSongs) {
        if (moreSongs.isEmpty()) emptyList() else List(3) { moreSongs }.flatten()
    }

    // Solid dark base — the gradient's low-alpha palette colors composite
    // over this, so the background is always predominantly dark/black.
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
                Brush.verticalGradient(
                    colors = listOf(animatedTop, animatedMid, animatedBottom)
                )
            ),
        indicator = {
            // Astroid icon — replaces the default Material3 circular arrow.
            // Positioned just below the fixed header (HomeScreen's glass
            // header covers y=0..120dp). Drawing at y=0 would hide it
            // behind the blur. y=120dp places it in the LazyColumn's
            // top content-padding area (which is empty), so the astroid
            // is visible without overlapping song cards.
            AstroidRefreshIndicator(
                progress = ptrState.distanceFraction,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 120.dp)
            )
        }
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
    textSecondary: Color
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
    // Keyed on (songs.size, pinnedIds) so it recomputes when either the
    // library changes or a song is pinned/unpinned.
    val speedDialSongs = remember(songs.size, pinnedIds) {
        if (songs.isEmpty()) emptyList()
        else {
            // 1. Pinned songs (in pin order), filtered to ones that still
            //    exist in the library (in case a pinned song was deleted).
            val pinnedSongs = pinnedIds.mapNotNull { id ->
                songs.firstOrNull { it.id == id }
            }
            // 2. Random songs, excluding already-pinned ones.
            val pool = songs.filter { it.id !in pinnedIds }.shuffled()
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
        // Chevron right beside the text (no spacer, no weight)
        Icon(
            imageVector = CoralIcons.ChevronRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(16.dp)
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

        // ═══ Liquid bar page indicator ═══
        // 3 bars below the grid. The active bar has a flowing animated
        // gradient (coral → orange → pink, cycling per page) that moves
        // left-to-right over time. Inactive bars are dim gray.
        //
        // Both ends of the row fade to transparent — the leftmost bar's
        // left edge and the rightmost bar's right edge "blend into the
        // screen", no hard edges.
        //
        // As you swipe between pages, the gradient cycle shifts: page 0
        // shows the coral→orange phase, page 1 shows orange→pink,
        // page 2 shows pink→coral. The gradient's POSITION follows
        // the page.
        LiquidBarPageIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// LIQUID BAR PAGE INDICATOR — flowing-gradient bars that fade at both ends
// ════════════════════════════════════════════════════════════════════
// Three thin bars (rounded pills) sit in a row below the Speed Dial grid.
//
// Why this looks "liquid", not just another dot indicator:
//   1. ANIMATED GRADIENT — the active bar's gradient FLOWS left-to-right
//      continuously, never static. Uses rememberInfiniteTransition.
//   2. PAGE-DRIVEN COLOR CYCLE — each bar gets its own gradient pair
//      from the palette. Page 0 = coral→orange, page 1 = orange→pink,
//      page 2 = pink→coral. As the active page changes, the gradient
//      you see is different.
//   3. EDGE FADE — the whole row fades to transparent at both ends
//      via a BlendMode.DstOut mask. The leftmost bar's left edge and
//      the rightmost bar's right edge "blend into the screen".
//   4. ACTIVITY-WEIGHTED OPACITY — when a bar is far from the current
//      scroll position, its alpha drops so the active one stands out.
//
// All drawn on a single Canvas — no nested Boxes, no overlays outside.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LiquidBarPageIndicator(
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier
) {
    val pageCount = pagerState.pageCount
    if (pageCount <= 1) return

    val currentPage = pagerState.currentPage
    val offsetFraction = pagerState.currentPageOffsetFraction
    // Continuous float: 0.0 = page 0, 1.0 = page 1, 0.5 = halfway swipe
    val scrollPosition = currentPage + offsetFraction

    // ─── Animated flow — gradient shifts left↔right, loops without snap ──
    // Restart + LinearEasing causes a visible snap when the value resets
    // from 1.0 → 0.0 on every loop. Reverse + FastOutSlowInEasing gives
    // a buttery back-and-forth motion: gradient flows left, decelerates,
    // reverses, flows right, decelerates, reverses again. Never snaps.
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "liquidFlow")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flowOffset"
    )

    // ─── Per-page color cycle ────────────────────────────────────────
    // Page 0 → coral→orange,  page 1 → orange→pink,  page 2 → pink→coral
    val palette = listOf(
        Color(0xFFFF6B6B),  // coral
        Color(0xFFFFB36B),  // orange
        Color(0xFFFF6BE5),  // pink
        Color(0xFFFF6B6B)   // back to coral (seamless loop)
    )

    Canvas(
        modifier = modifier
            .width(120.dp)
            .height(6.dp)
            // Offscreen layer is required for BlendMode.DstOut to work —
            // without it, the edge-fade mask would punch a hole through
            // the entire screen instead of just through the bars.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val slotWidth = size.width / pageCount
        val barWidth = slotWidth * 0.7f           // each bar is 70% of its slot
        val barHeight = size.height
        val gap = (slotWidth - barWidth) / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f, barHeight / 2f)

        // (1) DRAW ALL BARS ─────────────────────────────────────────────
        for (i in 0 until pageCount) {
            val barLeft = slotWidth * i + gap
            val barRight = barLeft + barWidth

            // Distance from this bar to the current scroll position.
            // 0.0 = this is the active bar, 1.0+ = far away.
            val distance = kotlin.math.abs(i - scrollPosition)
            val activity = (1f - distance.coerceIn(0f, 1f)).coerceIn(0f, 1f)

            // The bar's gradient colors — each bar gets a different pair
            // from the palette, so as you swipe, the active gradient is
            // different on each page.
            val c1 = palette[i % palette.size]
            val c2 = palette[(i + 1) % palette.size]

            // The animated gradient position — shifts left-to-right over
            // time, then snaps back (RepeatMode.Restart) to loop seamlessly.
            // 3-stop gradient (c1, c2, c1) so the loop has no visible seam.
            val shift = flowOffset * barWidth     // 0 → barWidth over the 2800ms cycle
            val barBrush = Brush.horizontalGradient(
                colors = listOf(c1, c2, c1),
                startX = barLeft - shift,
                endX = barRight - shift + barWidth  // gradient is 2x bar width so it slides visibly
            )

            // Activity-modulated alpha — active bar is fully opaque,
            // inactive bars fade down. Drives the visual "spotlight"
            // effect on the current page.
            val drawAlpha = 0.25f + 0.75f * activity

            drawRoundRect(
                brush = barBrush,
                topLeft = androidx.compose.ui.geometry.Offset(barLeft, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
                alpha = drawAlpha
            )

            // Subtle inner highlight on active bar — a thin lighter
            // stripe along the top edge to make it feel "glassy"
            if (activity > 0.5f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f * (activity - 0.5f) * 2f),
                    topLeft = androidx.compose.ui.geometry.Offset(barLeft, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight * 0.5f),
                    cornerRadius = cornerRadius
                )
            }
        }

        // (2) APPLY EDGE FADE MASK ──────────────────────────────────────
        // The whole row fades to transparent at both ends. We achieve
        // this by drawing a horizontal-gradient rect that is opaque
        // (Color.Black, alpha=1) at the left and right edges, and
        // transparent in the middle, using BlendMode.DstOut.
        //
        // DstOut subtracts the source (our mask) from the destination
        // (the bars already drawn). Where the mask is opaque, the bars
        // become fully transparent → "blend into the screen" effect.
        //
        // Requires CompositingStrategy.OffscreenLayer (set above),
        // otherwise DstOut would punch through the entire screen.
        val edgeFadeBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to Color.Black,            // opaque cover at left
                0.08f to Color.Black,            // solid up to 8%
                0.20f to Color.Transparent,      // fades out by 20%
                0.80f to Color.Transparent,      // stays clear until 80%
                0.92f to Color.Black,            // fades back in
                1.00f to Color.Black             // opaque cover at right
            )
        )
        drawRect(
            brush = edgeFadeBrush,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = size,
            blendMode = BlendMode.DstOut
        )
    }
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
