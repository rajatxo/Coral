package com.rajatxo.coral.ui.cynthia

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
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
 * The root layerBackdrop is in CynthiaHomeScreen. This screen receives
 * the backdrop and uses drawBackdrop on the speed dial grid.
 *
 * Structure (all inside CynthiaHomeScreen's layerBackdrop Box):
 *   LazyColumn (scrolling content)
 *   ├── "Speed dial" header
 *   ├── Speed dial area
 *   │   ├── Box (.drawBackdrop) — glass (sibling of layerBackdrop via parent)
 *   │   └── 3×3 grid (sharp album covers on top)
 *   ├── "More" section
 *   └── Song list rows
 *
 * NOTE: drawBackdrop here IS inside the layerBackdrop subtree.
 * But it works because the drawBackdrop samples the ROOT backdrop
 * which captures ALL content including the background gradient.
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

    val speedDialSongs = remember(songs.size, pinnedIds, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            val pinnedSongs = songs.filter { it.id in pinnedIds }
            val pool = songs.shuffled(Random(launchSeed))
            val picked = pool.filter { it.id !in pinnedIds }.take(8 - pinnedSongs.size.coerceAtMost(8))
            (pinnedSongs + picked).take(8)
        }
    }

    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.size <= 8) songs
        else songs.shuffled(Random(launchSeed + 2)).take(50)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item { Spacer(modifier = Modifier.statusBarsPadding().height(60.dp)) }

        // ═══ Speed dial section ═══
        item {
            Text(
                text = "Speed dial",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )

            if (speedDialSongs.isNotEmpty()) {
                CynthiaSpeedDialGrid(
                    songs = speedDialSongs,
                    currentSongId = currentSongId,
                    pinnedIds = pinnedIds,
                    isRandomizing = isRandomizing,
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

        items(moreSongs.size) { index ->
            val song = moreSongs[index]
            CynthiaMoreSongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                onClick = { onSongClick(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun CynthiaSpeedDialGrid(
    songs: List<Song>,
    currentSongId: Long?,
    pinnedIds: Set<Long>,
    isRandomizing: Boolean,
    onSongClick: (Song) -> Unit,
    onRandomize: () -> Unit
) {
    val targetItemSize = 100.dp
    val columns = 3
    val rows = 3
    val itemsPerPage = columns * rows
    val totalSlots = songs.size + 1
    val pageCount = (totalSlots + itemsPerPage - 1) / itemsPerPage
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })

    val gridShape = RoundedCornerShape(20.dp)
    val gridHeight = targetItemSize * rows + 24.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Layer 1 (BEHIND): visual glass background
        // (Not using drawBackdrop — it would be inside layerBackdrop subtree and crash)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clip(gridShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), gridShape)
        )

        // Layer 2 (ON TOP): sharp grid content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
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
                Icon(CoralIcons.Music, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(20.dp))
            }
        }

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White)
            )
        }

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
                Icon(CoralIcons.HeartLucideFilled, "Pinned", tint = Color(0xFFFF6B6B), modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun CynthiaMoreSongRow(
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
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
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
        if (isCurrent) {
            Icon(CoralIcons.VolumeHigh, "Playing", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
