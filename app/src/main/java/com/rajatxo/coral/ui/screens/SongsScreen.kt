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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
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
import kotlinx.coroutines.launch

/**
 * Songs tab — simple list + circular glass tag capsules.
 *
 * The tag capsules use REAL glass morphism via the RenderEffect technique
 * (drawLayer + BlurEffect) — the SAME technique as the top blur header.
 * No drawBackdrop (which crashes inside screen composables).
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
    graphicsLayer: GraphicsLayer? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title.lowercase() } }

    // ─── Background palette + gradient ──────────────────────────────
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

    val vibrantTop by animateColorAsState(palette.primary.copy(alpha = 0.85f), tween(800), "sBgVT")
    val fade1 by animateColorAsState(palette.primary.copy(alpha = 0.65f), tween(800), "sBgF1")
    val fade2 by animateColorAsState(palette.primary.copy(alpha = 0.45f), tween(800), "sBgF2")
    val fade3 by animateColorAsState(palette.primary.copy(alpha = 0.28f), tween(800), "sBgF3")
    val animatedTop by animateColorAsState(palette.primary.copy(alpha = 0.18f), tween(800), "sBgT")
    val animatedMid by animateColorAsState(palette.primary.copy(alpha = 0.08f), tween(800), "sBgM")
    val animatedBottom by animateColorAsState(Color(0xFF05050A), tween(800), "sBgB")
    val darkBase = Color(0xFF05050A)

    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to vibrantTop, 0.10f to fade1, 0.15f to fade2,
                    0.20f to fade3, 0.30f to animatedTop, 0.55f to animatedMid,
                    1.0f to animatedBottom
                )
            ))
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { try { onRefresh() } finally { isRefreshing = false } }
            },
            state = ptrState,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 160.dp, bottom = 100.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // "All songs" header (scrolls + blurs behind header)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("All songs", color = Color.White, fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
                        Icon(CoralIcons.ChevronRight, null, tint = Color.White.copy(0.6f),
                            modifier = Modifier.size(20.dp))
                        BugLineRefreshIndicator(ptrState.distanceFraction, isRefreshing,
                            Modifier.weight(1f).height(20.dp))
                        Text("${songs.size}", color = Color.White.copy(0.6f),
                            fontSize = 14.sp, fontFamily = CalSansFamily)
                    }
                }
                // Song list
                items(sortedSongs, key = { it.id }) { song ->
                    SongRow(song, currentSongId == song.id) { onSongClick(song) }
                }
            }
        }

        // Fixed tag carousel overlay (OUTSIDE LazyColumn — no crash)
        TagCarousel(
            graphicsLayer = graphicsLayer,
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 112.dp, start = 20.dp, end = 20.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TAG CAROUSEL — circular capsule row with fixed centre
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TagCarousel(
    graphicsLayer: GraphicsLayer? = null,
    modifier: Modifier = Modifier
) {
    val rotatingTags = remember { listOf("Recent", "Favorites", "Most played", "On device", "Downloads") }
    val centreTag = "All Tags"
    var rotationOffset by remember { mutableStateOf(0) }
    val n = rotatingTags.size
    var selectedTag by remember { mutableStateOf(centreTag) }

    val visibleTags = remember(rotationOffset) {
        listOf(
            rotatingTags[((rotationOffset - 2) % n + n) % n],
            rotatingTags[((rotationOffset - 1) % n + n) % n],
            centreTag,
            rotatingTags[rotationOffset % n],
            rotatingTags[(rotationOffset + 1) % n]
        )
    }

    val dragThreshold = 40f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(n) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        acc += dragAmount
                        while (acc > dragThreshold) { rotationOffset++; acc -= dragThreshold }
                        while (acc < -dragThreshold) { rotationOffset--; acc += dragThreshold }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleTags.forEachIndexed { index, tag ->
            val isCentre = index == 2
            val isSelected = tag == selectedTag
            GlassTagCapsule(
                label = tag,
                isCentre = isCentre,
                isSelected = isSelected,
                graphicsLayer = graphicsLayer,
                onClick = { selectedTag = if (selectedTag == tag) centreTag else tag },
                modifier = if (isCentre) Modifier else Modifier.weight(1f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// GLASS TAG CAPSULE — real glass via RenderEffect (same as blur header)
// ════════════════════════════════════════════════════════════════════
// Uses the EXACT same technique as the top blur header:
//   graphicsLayer { renderEffect = BlurEffect(12dp, 12dp); clip = true }
//   drawWithContent { drawLayer(graphicsLayer); drawRect(dark tint) }
//
// No drawBackdrop → no crash. The graphicsLayer is the shared one from
// HomeScreen that captures the screen content via layerBackdrop().
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GlassTagCapsule(
    label: String,
    isCentre: Boolean,
    isSelected: Boolean,
    graphicsLayer: GraphicsLayer? = null,
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
    val useRenderEffect = graphicsLayer != null &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .height(32.dp)
            .scale(scale)
            .clip(capsuleShape)
            .then(
                if (useRenderEffect && graphicsLayer != null) {
                    Modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            clip = true
                            renderEffect = BlurEffect(12.dp.toPx(), 12.dp.toPx())
                        }
                        .drawWithContent {
                            drawLayer(graphicsLayer)
                            drawRect(Color.Black.copy(alpha = 0.3f))
                        }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.4f))
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                isCentre -> Color(0xFFFF6B6B)
                isSelected -> Color.White
                else -> Color.White.copy(0.6f)
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
// SONG ROW
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (isCurrent) CoralColors.SurfaceVariant else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
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
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(0.5f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val s = song.duration / 1000
        Text("${s / 60}:${String.format("%02d", s % 60)}", color = Color.White.copy(0.4f), fontSize = 13.sp)
    }
}
