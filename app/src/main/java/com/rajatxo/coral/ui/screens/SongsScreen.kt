package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.components.SleepTimerCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.launch

/**
 * Songs tab — redesigned to match Quick Picks visual language.
 *
 * Layout (top to bottom):
 *   1. Background: single dominant color → dark gradient (same as Quick
 *      Picks — buttery smooth transition, vibrant behind the blur header).
 *   2. Blur header (rendered in HomeScreen, shared with Quick Picks) —
 *      profile icon + "Songs" title + settings icon. 120dp tall, fades
 *      at the bottom into the page content.
 *   3. 2×2 capsule grid — 4 frosted-glass capsules right below where the
 *      blur ends (~120dp from top). Each capsule has an icon + label.
 *   4. Song list — LazyColumn of all songs, scrolls below the capsules.
 *
 * The old design (black wavy fade + italic "Songs" text + placeholder
 * capsule) has been completely removed.
 *
 * Pull-to-refresh: pulling down triggers a library rescan via [onRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArt: android.net.Uri? = null,
    onSongClick: (Song) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onRefresh: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val sortedSongs = remember(songs) {
        songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    // ─── Current song's palette → dark gradient background ──────────
    // Same as Quick Picks: extract the dominant color from the current
    // song's album art, then create a single-color gradient that fades
    // from vibrant (behind the blur header) to near-black (bottom).
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
    // Exact same gradient as Quick Picks — one color, fading from vibrant
    // at the top to near-black at the bottom. Buttery smooth transition
    // via closely-spaced color stops.
    val vibrantTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.85f),
        animationSpec = tween(800), label = "songsBgVibrantTop"
    )
    val fade1 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.65f),
        animationSpec = tween(800), label = "songsBgFade1"
    )
    val fade2 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.45f),
        animationSpec = tween(800), label = "songsBgFade2"
    )
    val fade3 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.28f),
        animationSpec = tween(800), label = "songsBgFade3"
    )
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.18f),
        animationSpec = tween(800), label = "songsBgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.08f),
        animationSpec = tween(800), label = "songsBgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),
        animationSpec = tween(800), label = "songsBgBottom"
    )

    val darkBase = Color(0xFF05050A)

    // ─── Pull-to-refresh state ───────────────────────────────────────
    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try {
                        onRefresh()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            state = ptrState,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 130.dp,      // just below where the blur header ends (120dp + 10dp gap)
                    bottom = 100.dp,   // space for mini player
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ═══ 2×2 capsule grid ═══
                item {
                    CapsuleGrid(onSongClick = onSongClick, songs = songs)
                }

                // ═══ Section header for the song list ═══
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All songs",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CalSansFamily
                        )
                        Icon(
                            imageVector = CoralIcons.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${songs.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily
                        )
                    }
                }

                // ═══ Song list ═══
                items(sortedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSongId == song.id,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// CAPSULE GRID — 2×2 frosted-glass capsules below the blur header
// ════════════════════════════════════════════════════════════════════
// Four capsules in a 2×2 grid. Each capsule:
//   • Half the screen width (with 12dp gap between them)
//   • 64dp tall (chunky pill, matching the mini player height)
//   • Glass-blur background (frosted glass look)
//   • Icon on the left + label on the right
//   • Tappable (scale-down on press, haptic on release)
//
// The 4 capsules are:
//   1. "All songs"    → Music icon       → plays all songs from the top
//   2. "Recent"       → ListMusic icon   → (placeholder — user will guide)
//   3. "Favorites"    → HeartLucide icon → (placeholder)
//   4. "Shuffle all" → ShuffleLucide    → shuffles all songs + plays
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CapsuleGrid(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: All songs | Recent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterCapsule(
                icon = CoralIcons.Music,
                label = "All songs",
                modifier = Modifier.weight(1f),
                onClick = {
                    // Play the first song in the sorted list (which starts
                    // the full queue from the beginning).
                    if (songs.isNotEmpty()) {
                        onSongClick(songs.first())
                    }
                }
            )
            FilterCapsule(
                icon = CoralIcons.ListMusic,
                label = "Recent",
                modifier = Modifier.weight(1f),
                onClick = {
                    // Placeholder — user will guide what this does.
                }
            )
        }
        // Row 2: Favorites | Shuffle all
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterCapsule(
                icon = CoralIcons.HeartLucide,
                label = "Favorites",
                modifier = Modifier.weight(1f),
                onClick = {
                    // Placeholder — user will guide what this does.
                }
            )
            FilterCapsule(
                icon = CoralIcons.ShuffleLucide,
                label = "Shuffle all",
                modifier = Modifier.weight(1f),
                onClick = {
                    // Shuffle all songs and play the first one.
                    if (songs.isNotEmpty()) {
                        val shuffled = songs.shuffled()
                        onSongClick(shuffled.first())
                    }
                }
            )
        }
    }
}

/**
 * A single frosted-glass filter capsule.
 *
 * Design:
 *   • Rounded pill shape (26dp corner radius — matches TabCapsule)
 *   • 64dp tall (matches mini player height)
 *   • Semi-transparent dark background with a subtle white border
 *   • Icon on the left (24dp, coral accent color)
 *   • Label on the right (CalSans, 15sp, SemiBold, white)
 *   • Scale-down animation on press (0.96x)
 *
 * NOTE: This doesn't use drawBackdrop (real glass blur) because that
 * would require a LayerBackdrop to be passed down from HomeScreen.
 * Instead, it uses a semi-transparent dark background which gives a
 * similar visual feel without the blur. If the user wants real glass
 * blur later, we can wire the backdrop through.
 */
@Composable
private fun FilterCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "capsuleScale"
    )

    val capsuleShape: Shape = RoundedCornerShape(26.dp)

    Row(
        modifier = modifier
            .height(64.dp)
            .scale(scale)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SONG ROW — same as before (album art + title/artist + duration)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) CoralColors.SurfaceVariant else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color(0xFFB0B0B0),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val totalSec = song.duration / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        Text(
            text = "$mm:${String.format("%02d", ss)}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp
        )
    }
}
