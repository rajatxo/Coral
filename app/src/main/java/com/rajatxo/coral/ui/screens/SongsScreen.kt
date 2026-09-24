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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.launch

/**
 * Songs tab — simple list + horizontal glass tag capsules.
 *
 * Layout (top to bottom):
 *   1. Background: single dominant color → dark gradient (same as Quick Picks)
 *   2. Blur header (rendered in HomeScreen) — profile + "Songs" + settings
 *   3. Fixed horizontal row of 6-7 glass capsule tags (scrollable if needed)
 *   4. "All songs" header with bug PTR (inside LazyColumn — scrolls + blurs)
 *   5. LazyColumn of songs as rows
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
    val scope = rememberCoroutineScope()

    val sortedSongs = remember(songs) {
        songs.sortedBy { it.title.lowercase() }
    }

    // ─── Current song's palette → dark gradient background ──────────
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

    // ─── Tag capsules state ──────────────────────────────────────────
    // 6-7 tag capsules. User will explain what tags they want.
    // For now: placeholder tags that can be selected.
    val tags = remember { listOf("All", "Recent", "Favorites", "Most played", "On device", "Downloads") }
    var selectedTag by remember { mutableStateOf("All") }

    // Filtered songs based on selected tag
    // (For now, all tags show all songs — user will explain the logic)
    val displayedSongs = remember(sortedSongs, selectedTag) {
        sortedSongs  // Placeholder — no filtering yet
    }

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
                    try { onRefresh() } finally { isRefreshing = false }
                }
            },
            state = ptrState,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 160.dp,      // blur header (108dp) + tag capsules (44dp) + gap
                    bottom = 100.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ═══ "All songs" header (inside LazyColumn — scrolls + blurs) ═══
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
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
                items(displayedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSongId == song.id,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }

        // ─── Fixed tag capsules row (below the blur header) ───
        // Glass morphism capsules in a horizontal scrollable row.
        // Uses semi-transparent dark + white border (fake glass — drawBackdrop
        // crashes inside screen composables, but this looks identical).
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 112.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tags, key = { it }) { tag ->
                val isSelected = tag == selectedTag
                GlassTagCapsule(
                    label = tag,
                    isSelected = isSelected,
                    onClick = {
                        selectedTag = if (selectedTag == tag) "All" else tag
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// GLASS TAG CAPSULE — semi-transparent dark + white border (fake glass)
// ════════════════════════════════════════════════════════════════════
// Same visual feel as the mini player and nav bar — frosted glass look
// without drawBackdrop (which crashes inside screen composables).
//
// Design:
//   • Rounded pill (18dp corner = half of 36dp height → full pill)
//   • Semi-transparent dark background (alpha 0.4, selected = coral tint 0.15)
//   • Thin white border (alpha 0.12)
//   • CalSans label (12sp, SemiBold)
//   • Selected = white text + coral tint; unselected = dim white text
//   • Press animation: scale 0.94x (bouncy spring)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GlassTagCapsule(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tagScale"
    )

    val capsuleShape: Shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .height(36.dp)
            .scale(scale)
            .clip(capsuleShape)
            .background(
                if (isSelected) Color(0xFFFF6B6B).copy(alpha = 0.15f)
                else Color.Black.copy(alpha = 0.4f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = CalSansFamily,
            maxLines = 1
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SONG ROW — album art + title/artist + duration
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
