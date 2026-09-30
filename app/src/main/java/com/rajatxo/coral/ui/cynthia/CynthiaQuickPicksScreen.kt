package com.rajatxo.coral.ui.cynthia

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SpeedDialPinStore
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * CynthiaQuickPicksScreen — built from scratch for Cynthia.
 *
 * Architecture designed for glass morphism:
 *   - Background Box with layerBackdrop (captures ONLY the background)
 *   - Scrolling content is a SIBLING (not inside layerBackdrop)
 *   - Speed dial glass uses drawBackdrop — it's NOT a descendant of
 *     layerBackdrop, so it doesn't crash
 *
 * Structure:
 *   Box (parent)
 *   ├── Box (.layerBackdrop) — captures background gradient only
 *   │   └── Decorative background (gradient, colors)
 *   ├── LazyColumn — scrolling content (SIBLING, not inside layerBackdrop)
 *   │   ├── "Speed dial" header
 *   │   ├── Speed dial area
 *   │   │   ├── Box (.drawBackdrop) — glass ✅ sibling of layerBackdrop
 *   │   │   └── 3×3 grid (sharp album covers on top)
 *   │   ├── "Recent" section
 *   │   └── "More" section
 *   └── (nav bar + settings handled by CynthiaHomeScreen)
 */
@Composable
fun CynthiaQuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val launchSeed = remember { Random.nextInt() }
    var isRandomizing by remember { mutableStateOf(false) }
    val pinnedIds by SpeedDialPinStore.pinnedIds.collectAsState()

    // Speed dial songs — pick 8 random + pinned
    val speedDialSongs = remember(songs.size, pinnedIds, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            val pinnedSongs = songs.filter { it.id in pinnedIds }
            val pool = songs.shuffled(Random(launchSeed))
            val picked = pool.filter { it.id !in pinnedIds }.take(8 - pinnedSongs.size.coerceAtMost(8))
            (pinnedSongs + picked).take(8)
        }
    }

    // More songs for the "More" section
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.size <= 8) songs
        else songs.shuffled(Random(launchSeed + 2)).take(50)
    }

    // ═══════════════════════════════════════════════════════════════
    // GLASS BACKDROP — captures ONLY the background gradient
    // ═══════════════════════════════════════════════════════════════
    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val localBackdrop = rememberLayerBackdrop(graphicsLayer) {
        drawContent()
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // ─── Layer 1: Background (captured by layerBackdrop) ───
        // This is what glass elements will blur.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(localBackdrop)
        ) {
            // Decorative background — palette-style gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E).copy(alpha = 0.5f),
                                Color(0xFF0F0F1A).copy(alpha = 0.3f),
                                Color.Black
                            )
                        )
                    )
            )
            // Some decorative circles for visual texture (glass will blur them)
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-50).dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C5CE7).copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 400.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE17055).copy(alpha = 0.06f))
            )
        }

        // ─── Layer 2: Scrolling content (SIBLING of layerBackdrop) ───
        // NOT inside layerBackdrop — drawBackdrop here won't crash!
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Spacer for status bar
            item { Spacer(modifier = Modifier.statusBarsPadding().height(60.dp)) }

            // ═══ Speed dial section ═══
            item {
                // Section header
                Text(
                    text = "Speed dial",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )

                if (speedDialSongs.isNotEmpty()) {
                    // ★ Glass container — drawBackdrop is a SIBLING of layerBackdrop
                    // This is the key: NOT inside the layerBackdrop Box above.
                    SpeedDialGrid(
                        songs = speedDialSongs,
                        allSongs = songs,
                        currentSongId = currentSongId,
                        pinnedIds = pinnedIds,
                        isRandomizing = isRandomizing,
                        backdrop = localBackdrop,
                        onSongClick = onSongClick,
                        onRandomize = {
                            if (isRandomizing) {
                                isRandomizing = false
                            } else {
                                isRandomizing = true
                                scope.launch {
                                    delay(800)
                                    val randomSong = songs.random()
                                    isRandomizing = false
                                    onSongClick(randomSong)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ═══ More section ═══
            item {
                Text(
                    text = "More",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )
            }

            // Song list
            items(moreSongs.size) { index ->
                val song = moreSongs[index]
                MoreSongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    onClick = { onSongClick(song) }
                )
            }

            // Bottom padding for nav bar
            item { Spacer(modifier = Modifier.height(120.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Speed Dial Grid — 3×3 grid with glass behind, sharp covers on top
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialGrid(
    songs: List<Song>,
    allSongs: List<Song>,
    currentSongId: Long?,
    pinnedIds: Set<Long>,
    isRandomizing: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onSongClick: (Song) -> Unit,
    onRandomize: () -> Unit
) {
    val targetItemSize = 100.dp
    val columns = 3
    val rows = 3
    val itemsPerPage = columns * rows
    val totalSlots = songs.size + 1  // +1 for dice
    val pageCount = (totalSlots + itemsPerPage - 1) / itemsPerPage
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })

    val gridShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Layer 1 (BEHIND): glass box — drawBackdrop samples the background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetItemSize * rows + 24.dp)
                .clip(gridShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { gridShape },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = 0.05f,
                            contrast = 1f,
                            saturation = 1.3f
                        )
                        blur(18f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.35f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), gridShape)
        )

        // Layer 2 (ON TOP): sharp grid content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(targetItemSize * rows + 24.dp)
                .padding(8.dp)
        ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until columns) {
                            val itemIndex = row * columns + col
                            val globalItemIndex = page * itemsPerPage + itemIndex
                            val isDiceSlot = (globalItemIndex == itemsPerPage - 1)

                            if (isDiceSlot) {
                                // Dice button
                                Box(
                                    modifier = Modifier
                                        .size(targetItemSize)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable(onClick = onRandomize),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CoralIcons.Shuffle,
                                        contentDescription = "Randomize",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                val actualIndex = if (globalItemIndex < itemsPerPage - 1) {
                                    globalItemIndex
                                } else {
                                    globalItemIndex - 1
                                }
                                val song = songs.getOrNull(actualIndex)
                                if (song != null) {
                                    CynthiaSpeedDialCard(
                                        song = song,
                                        isCurrent = song.id == currentSongId,
                                        isPinned = song.id in pinnedIds,
                                        onClick = { onSongClick(song) },
                                        modifier = Modifier
                                            .size(targetItemSize)
                                            .padding(4.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(targetItemSize).padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Speed Dial Card — album art with play overlay + pin indicator
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CynthiaSpeedDialCard(
    song: Song,
    isCurrent: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "tapScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Album art
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color(0xFFB0B0B0),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Now playing indicator
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White)
            )
        }

        // Pin indicator
        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.HeartLucideFilled,
                    contentDescription = "Pinned",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// More Song Row — simple list item
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MoreSongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art thumbnail
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(CoralIcons.Music, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        // Now playing indicator
        if (isCurrent) {
            Icon(
                CoralIcons.VolumeHigh,
                "Playing",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
