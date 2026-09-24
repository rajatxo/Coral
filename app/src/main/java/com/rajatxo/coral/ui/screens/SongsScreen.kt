package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

/**
 * Songs tab — simple list + circular glass tag capsules.
 *
 * Layout (top to bottom):
 *   1. Background gradient (same as Quick Picks)
 *   2. Blur header (rendered in HomeScreen)
 *   3. LazyColumn containing:
 *      a. "All songs" header with bug PTR (scrolls + blurs behind header)
 *      b. Circular tag capsule carousel (scrolls + blurs behind header)
 *         — Fixed centre "All Tags" capsule
 *         — Other capsules rotate around it on swipe left/right
 *         — Each capsule has REAL glass morphism via drawBackdrop
 *      c. Song rows
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
    val scope = rememberCoroutineScope()

    val sortedSongs = remember(songs) {
        songs.sortedBy { it.title.lowercase() }
    }

    // ─── Background palette + gradient (same as Quick Picks) ─────────
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
                    top = 160.dp,      // blur header (108dp) + tag carousel (40dp) + gap (12dp)
                    bottom = 100.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ═══ "All songs" header (scrolls + blurs behind header) ═══
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
                items(sortedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSongId == song.id,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }

        // ─── Fixed tag carousel overlay (OUTSIDE LazyColumn) ───
        // drawBackdrop crashes inside LazyColumn item (recycle issue).
        // Fix: render the TagCarousel as a FIXED overlay positioned right
        // below the blur header. The song list scrolls behind it. The
        // drawBackdrop composable stays alive for the screen's lifetime
        // — no recycle, no crash.
        //
        // Same pattern as the Quick Picks blur header (which is also a
        // fixed overlay above the LazyColumn, not inside it).
        TagCarousel(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 112.dp, start = 20.dp, end = 20.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TAG CAROUSEL — circular capsule row with fixed centre
// ════════════════════════════════════════════════════════════════════
// Layout: 5 visible capsules in a Row.
//   [tag] [tag] [ALL TAGS] [tag] [tag]
//
// The centre position (index 2) is ALWAYS "All Tags" — it never moves.
// The other 4 positions are filled from a circular list of tags.
// Swipe LEFT → tags rotate left (next tag enters from right)
// Swipe RIGHT → tags rotate right (next tag enters from left)
//
// Example with tags [A, B, D, E] (C = "All Tags" is centre):
//   Start:     A B [C] D E
//   Swipe right: E A [C] B D
//   Swipe right: D E [C] A B
//   Swipe left:  A B [C] D E  (back to start)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TagCarousel(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    // Non-centre tags (these rotate around the fixed centre)
    val rotatingTags = remember {
        listOf("Recent", "Favorites", "Most played", "On device", "Downloads")
    }
    val centreTag = "All Tags"

    // Rotation offset — how many positions the rotating tags have shifted.
    // Swipe right → offset increases. Swipe left → offset decreases.
    var rotationOffset by remember { mutableStateOf(0) }
    val n = rotatingTags.size

    // Compute the 4 visible rotating tags (2 on each side of centre)
    // Position 0 (far left):   rotatingTags[(offset - 2 + n*2) % n]
    // Position 1 (left):        rotatingTags[(offset - 1 + n*2) % n]
    // Position 2 (CENTRE):      "All Tags" (FIXED)
    // Position 3 (right):       rotatingTags[offset % n]
    // Position 4 (far right):   rotatingTags[(offset + 1) % n]
    val visibleTags = remember(rotationOffset) {
        listOf(
            rotatingTags[((rotationOffset - 2) % n + n) % n],
            rotatingTags[((rotationOffset - 1) % n + n) % n],
            centreTag,  // FIXED
            rotatingTags[rotationOffset % n],
            rotatingTags[(rotationOffset + 1) % n]
        )
    }

    // Track which tag is selected (for visual highlight)
    var selectedTag by remember { mutableStateOf(centreTag) }

    // Drag gesture: swipe left/right to rotate
    val dragThreshold = 40f  // pixels of drag needed to advance by 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(n) {
                var accumulatedDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        // Swipe right (positive dragAmount) → offset increases
                        // Swipe left (negative dragAmount) → offset decreases
                        while (accumulatedDrag > dragThreshold) {
                            rotationOffset++
                            accumulatedDrag -= dragThreshold
                        }
                        while (accumulatedDrag < -dragThreshold) {
                            rotationOffset--
                            accumulatedDrag += dragThreshold
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Render 5 capsules: [rotating] [rotating] [FIXED CENTRE] [rotating] [rotating]
        visibleTags.forEachIndexed { index, tag ->
            val isCentre = index == 2
            val isSelected = tag == selectedTag

            GlassTagCapsule(
                label = tag,
                isCentre = isCentre,
                isSelected = isSelected,
                backdrop = backdrop,
                onClick = {
                    selectedTag = if (selectedTag == tag) centreTag else tag
                },
                modifier = if (isCentre) Modifier else Modifier.weight(1f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// GLASS TAG CAPSULE — real glass morphism via drawBackdrop
// ════════════════════════════════════════════════════════════════════
// Each capsule has REAL glass morphism (drawBackdrop with AGSL blur),
// matching the nav bar and mini player technique.
//
// If backdrop is null (shouldn't happen), falls back to semi-transparent
// dark background (fake glass).
//
// Design:
//   • Rounded pill (16dp corner = half of 32dp height → full pill)
//   • 32dp tall (small, compact)
//   • Real glass blur (drawBackdrop + vibrancy + colorControls + blur 12dp)
//   • Dark tint overlay (alpha 0.3) for readability
//   • Centre capsule: coral accent tint
//   • Selected capsule: brighter text + coral ball
//   • CalSans font, 11sp
//   • Press animation: scale 0.94x (bouncy spring)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GlassTagCapsule(
    label: String,
    isCentre: Boolean,
    isSelected: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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

    val capsuleShape: Shape = RoundedCornerShape(16.dp)

    // Build the glass modifier: REAL drawBackdrop if available, fake glass fallback
    val glassModifier = if (backdrop != null) {
        modifier
            .height(32.dp)
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
                        saturation = 1.2f
                    )
                    blur(12f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.Black.copy(alpha = 0.3f))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        modifier
            .height(32.dp)
            .scale(scale)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    }

    Box(
        modifier = glassModifier
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                isCentre -> Color(0xFFFF6B6B)
                isSelected -> Color.White
                else -> Color.White.copy(alpha = 0.6f)
            },
            fontSize = 11.sp,
            fontWeight = when {
                isCentre -> FontWeight.Bold
                isSelected -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
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
