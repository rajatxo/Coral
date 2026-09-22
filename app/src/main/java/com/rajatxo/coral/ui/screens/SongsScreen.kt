package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import com.rajatxo.coral.ui.components.BugLineRefreshIndicator
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
 *      Rendered OUTSIDE the LazyColumn (fixed position) so drawBackdrop
 *      doesn't crash (drawBackdrop inside LazyColumn item crashes on
 *      item recycle — known issue from the Speed Dial mode capsule).
 *   4. Song list — LazyColumn of all songs, scrolls below the capsules.
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
    // Same bug-on-a-line indicator as Quick Picks — the bug crawls along
    // a thin line beside the chevron next to "All songs" text.
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
            // Suppress the default Material3 circular arrow indicator —
            // we use the custom BugLineRefreshIndicator in the "All songs"
            // header instead (positioned right next to the chevron).
            indicator = {},
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
                        // ─── Bug-on-a-line pull-to-refresh indicator ──
                        // Same as Quick Picks: a thin line with faded ends,
                        // bug crawls left → right as you pull. Fades in/out.
                        BugLineRefreshIndicator(
                            progress = ptrState.distanceFraction,
                            isRefreshing = isRefreshing,
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        )
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
