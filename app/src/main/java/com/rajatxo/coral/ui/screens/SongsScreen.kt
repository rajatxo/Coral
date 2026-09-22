package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items as lazyItems
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
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
    onRefresh: suspend () -> Unit = {},
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val context = LocalContext.current
    val sortedSongs = remember(songs) {
        songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    // ─── Format filter state ───────────────────────────────────────
    // Tracks which format capsule is currently selected (e.g. "FLAC").
    // null = no filter (show all songs). Tapping a selected capsule
    // again clears the filter (toggle behavior).
    var selectedFormat by remember { mutableStateOf<String?>(null) }

    // Compute the list of available formats from the songs, with the
    // count of each. Sorted by count descending (most common first) so
    // the user sees their dominant formats at the start of the line.
    val formatCounts = remember(songs) {
        songs.groupBy { it.format.ifEmpty { "Unknown" } }
            .map { (format, list) -> format to list.size }
            .sortedByDescending { it.second }
    }

    // The filtered song list — if a format is selected, only songs
    // matching that format are shown. Otherwise, all songs.
    val displayedSongs = remember(sortedSongs, selectedFormat) {
        if (selectedFormat != null) {
            sortedSongs.filter { it.format == selectedFormat }
        } else {
            sortedSongs
        }
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
                // ═══ Format capsules on a line ═══
                // A full-width thin line with faded ends, with format
                // capsules sitting on top. Capsules are horizontally
                // scrollable (LazyRow) when there are more formats than
                // fit on screen.
                item {
                    FormatCapsuleLine(
                        formatCounts = formatCounts,
                        selectedFormat = selectedFormat,
                        onFormatSelected = { format ->
                            // Tap to toggle: if already selected, clear.
                            // If a different format is selected, switch.
                            selectedFormat = if (selectedFormat == format) null else format
                        }
                    )
                }

                // ═══ 2×2 capsule grid ═══
                item {
                    CapsuleGrid(
                        onSongClick = onSongClick,
                        songs = displayedSongs,
                        backdrop = backdrop
                    )
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
                            text = if (selectedFormat != null) selectedFormat!! else "All songs",
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
                            text = "${displayedSongs.size}",
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
    onSongClick: (Song) -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
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
                backdrop = backdrop,
                onClick = {
                    if (songs.isNotEmpty()) {
                        onSongClick(songs.first())
                    }
                }
            )
            FilterCapsule(
                icon = CoralIcons.ListMusic,
                label = "Recent",
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
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
                backdrop = backdrop,
                onClick = {
                    // Placeholder — user will guide what this does.
                }
            )
            FilterCapsule(
                icon = CoralIcons.ShuffleLucide,
                label = "Shuffle all",
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                onClick = {
                    if (songs.isNotEmpty()) {
                        val shuffled = songs.shuffled()
                        onSongClick(shuffled.first())
                    }
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// FORMAT CAPSULE LINE — thin line with faded ends + format capsules
// ════════════════════════════════════════════════════════════════════
// A full-width thin horizontal line with both ends fading to transparent
// (same style as the bug-on-a-line PTR indicator). Format capsules sit
// ON TOP of the line, centered vertically.
//
// Each capsule shows a format label (FLAC, M4A, MP3, Atmos, etc.) and
// the count of songs in that format. Capsules are:
//   • Semi-transparent dark background with white border (glass look)
//   • Tap to select → filters the song list below
//   • Tap again to deselect (toggle)
//   • Selected capsule has a coral accent border + brighter text
//
// When there are more capsules than fit on screen, the LazyRow scrolls
// horizontally — swipe left/right to see more formats.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun FormatCapsuleLine(
    formatCounts: List<Pair<String, Int>>,
    selectedFormat: String?,
    onFormatSelected: (String) -> Unit
) {
    if (formatCounts.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        // ─── Layer 1: Thin line with faded ends ───
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val canvasWidth = size.width
            val centerY = size.height / 2f
            val lineHeight = 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(0f, centerY - lineHeight / 2f),
                size = Size(canvasWidth, lineHeight),
                cornerRadius = CornerRadius(lineHeight / 2f, lineHeight / 2f)
            )

            // Fade mask — both ends fade to transparent.
            val fadeBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1.00f to Color.Transparent
                )
            )
            drawRect(
                brush = fadeBrush,
                topLeft = Offset.Zero,
                size = size,
                blendMode = BlendMode.DstIn
            )
        }

        // ─── Layer 2: Format capsules (horizontally scrollable) ───
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lazyItems(formatCounts) { (format, count) ->
                val isSelected = format == selectedFormat
                FormatCapsule(
                    label = format,
                    count = count,
                    isSelected = isSelected,
                    onClick = { onFormatSelected(format) }
                )
            }
        }
    }
}

/**
 * A single format capsule — shows the format name + song count.
 *
 * Selected state: coral accent border + brighter text + filled background.
 * Unselected: semi-transparent dark + white border + dimmer text.
 *
 * Press animation: scale down to 0.94x (bouncy spring).
 */
@Composable
private fun FormatCapsule(
    label: String,
    count: Int,
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
        label = "formatCapsuleScale"
    )

    val capsuleShape: Shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .height(28.dp)
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
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Format label
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = CalSansFamily,
            maxLines = 1
        )
        // Count
        Text(
            text = "$count",
            color = if (isSelected) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = CalSansFamily
        )
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
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
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

    // Thinner (48dp vs old 64dp) + properly rounded (24dp corner = half of
    // 48dp height → fully rounded pill ends).
    val capsuleShape: Shape = RoundedCornerShape(24.dp)

    // Build the background modifier: real glass morphism via drawBackdrop
    // (AGSL real-time blur, same technique as the mini player + nav bar),
    // OR fall back to semi-transparent dark if no backdrop is available.
    val glassModifier = if (backdrop != null) {
        modifier
            .height(48.dp)
            .scale(scale)
            .clip(capsuleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.05f,
                        contrast = 1f,
                        saturation = 1.3f
                    )
                    blur(18f.dp.toPx())  // AGSL real-time backdrop blur
                },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.35f))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        modifier
            .height(48.dp)
            .scale(scale)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    }

    Row(
        modifier = glassModifier
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
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
