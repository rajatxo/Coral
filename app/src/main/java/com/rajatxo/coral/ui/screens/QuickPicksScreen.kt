package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.extractPalette
import kotlin.math.sin

/**
 * Quick Picks Screen — "Abyssal Pressure" concept.
 *
 * Visual metaphor: A vertical cross-section of the ocean. Each pick is a
 * "depth layer" — like geological strata or ocean thermoclines. The deeper
 * you go, the darker it gets. The active layer expands to ~48% of screen
 * height; the other 14 compress into thin 3.5% slivers.
 *
 * A vertical "depth gauge" runs down the right edge with tick marks for each
 * pick + an active-depth indicator. Looks like a submarine instrument.
 *
 * Each pick gets a real ocean depth in meters + a zone name
 * (EPIPELAGIC → MESOPELAGIC → BATHYPELAGIC → ABYSSOPELAGIC → HADALPELAGIC).
 *
 * The 14 inactive slivers gently breathe in opacity in a slow sine wave
 * (6-second period) — pressure pulses through the water column. The screen
 * never feels static.
 *
 * Background: dynamic color from active pick's album art palette (darkened),
 * hoisted to HomeScreen so the entire screen — including the nav rail area —
 * morphs to match. A vertical gradient overlay (transparent → pure black)
 * reinforces the abyss effect.
 *
 * Interactions:
 *   - Tap any layer → expands it (animates height with spring physics),
 *     haptic fires
 *   - Tap the active layer's album art → plays it
 *   - Swipe up/down → advances to next/previous pick (with haptic on each)
 *
 * Layout:
 *   Column(fillMaxSize) {
 *     Header: SleepCapsule + "Quick picks" title + toggle capsule
 *     Main (weight 1f, BoxWithConstraints):
 *       Abyssal layers (Column of 15 animating-height rows)
 *       Depth gauge (overlay on right edge)
 *       Vertical gradient overlay (abyss effect)
 *   }
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {},
    onBgColorChange: (Color) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    var isRandomMode by remember { mutableStateOf(false) }

    // --- Song selection logic (unchanged from previous version) ---
    val quickPicksSongs = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()

        if (isRandomMode) {
            songs.shuffled().take(15)
        } else {
            val currentSong = songs.firstOrNull { it.id == currentSongId }
            if (currentSong != null) {
                val sameArtist = songs.filter {
                    it.artist == currentSong.artist && it.id != currentSong.id
                }
                val sameAlbum = songs.filter {
                    it.album == currentSong.album && it.id != currentSong.id &&
                    it.id !in sameArtist.map { s -> s.id }
                }
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct().take(15)
                if (related.size < 5) {
                    val fillers = songs.filter { it.id !in related.map { s -> s.id } }
                        .shuffled()
                        .take(15 - related.size)
                    (related + fillers).distinct()
                } else {
                    related
                }
            } else {
                songs.shuffled().take(15)
            }
        }
    }

    // --- Active pick index ---
    var currentPickIndex by remember { mutableStateOf(0) }
    LaunchedEffect(quickPicksSongs) { currentPickIndex = 0 }

    // --- Bg color (hoisted to parent) ---
    var currentBgColor by remember { mutableStateOf(Color(0xFF050810)) }
    LaunchedEffect(currentBgColor) { onBgColorChange(currentBgColor) }
    LaunchedEffect(currentPickIndex, quickPicksSongs) {
        val song = quickPicksSongs.getOrNull(currentPickIndex)
        if (song?.albumArtUri != null) {
            extractPalette(context, song.albumArtUri)?.let { palette ->
                currentBgColor = abyssalDarkenColor(palette.primary)
            }
        } else {
            currentBgColor = Color(0xFF050810)
        }
    }

    // --- Pressure wave animation (inactive slivers breathe in opacity) ---
    val infiniteTransition = rememberInfiniteTransition(label = "pressure")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    // --- Vertical drag accumulator (for swipe-to-advance) ---
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // NO background — parent (HomeScreen) paints the entire screen,
            // including the nav rail area, with the hoisted dynamic color.
    ) {
        // ============================================================
        // HEADER (title + toggle) — unchanged from previous version
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.rajatxo.coral.ui.components.SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
                if (capsuleVisible && capsuleRemaining > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Text(
                    text = "Quick picks",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Based on last played",
                        color = if (!isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Random picks",
                        color = if (isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        // ============================================================
        // MAIN CONTENT — Abyssal layers + depth gauge
        // ============================================================
        if (quickPicksSongs.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val totalHeightDp = maxHeight
                // Active layer: 48% of available height
                // Inactive layers: 3.5% each
                // Total: 48 + 14*3.5 = 97% (leaves 3% breathing room)
                val activeHeight = totalHeightDp * 0.48f
                val inactiveHeight = totalHeightDp * 0.035f
                val totalPicks = quickPicksSongs.size

                Box(modifier = Modifier.fillMaxSize()) {
                    // --- Vertical gradient overlay (abyss effect) ---
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.6f to Color.Black.copy(alpha = 0.15f),
                                    1f to Color.Black.copy(alpha = 0.5f)
                                )
                            )
                    )

                    // --- Swipe-to-advance gesture layer ---
                    // Captures vertical drags anywhere in the main content area.
                    // Threshold-based: needs 50px of drag to advance one pick.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(totalPicks, currentPickIndex) {
                                detectVerticalDragGestures(
                                    onDragEnd = { dragAccumulator = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragAccumulator += dragAmount
                                        val threshold = 50f
                                        if (dragAccumulator > threshold) {
                                            // Drag down → previous pick (shallower)
                                            if (currentPickIndex > 0) {
                                                currentPickIndex--
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.CLOCK_TICK
                                                )
                                            }
                                            dragAccumulator = 0f
                                        } else if (dragAccumulator < -threshold) {
                                            // Drag up → next pick (deeper)
                                            if (currentPickIndex < totalPicks - 1) {
                                                currentPickIndex++
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.CLOCK_TICK
                                                )
                                            }
                                            dragAccumulator = 0f
                                        }
                                    }
                                )
                            }
                    )

                    // --- Layers (Column of animating-height rows) ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        quickPicksSongs.forEachIndexed { i, song ->
                            val isActive = i == currentPickIndex
                            val targetHeight = if (isActive) activeHeight else inactiveHeight
                            val animatedHeight by animateDpAsState(
                                targetValue = targetHeight,
                                animationSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = 200f
                                ),
                                label = "layer_h_$i"
                            )

                            // Pressure wave opacity for inactive layers
                            val layerOpacity = if (isActive) {
                                1f
                            } else {
                                // Use Double math throughout to avoid sin(Float) vs sin(Double)
                                // overload ambiguity, then convert to Float at the end.
                                val phase = wavePhase.toDouble() + i * 0.4
                                (0.55 + 0.18 * sin(phase))
                                    .coerceIn(0.4, 0.78)
                                    .toFloat()
                            }

                            AbyssalLayerRow(
                                song = song,
                                isActive = isActive,
                                opacity = layerOpacity,
                                pickIndex = i,
                                totalPicks = totalPicks,
                                onTap = {
                                    if (!isActive) {
                                        currentPickIndex = i
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK
                                        )
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedHeight)
                            )
                        }
                    }

                    // --- Depth gauge on the right edge ---
                    DepthGauge(
                        totalPicks = totalPicks,
                        activeIndex = currentPickIndex,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 6.dp)
                    )
                }
            }
        } else {
            // --- Empty state ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🎵", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No songs found",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * A single horizontal layer in the abyssal column. Animates between a thin
 * sliver (inactive) and a tall expanded row (active).
 *
 * Inactive: tiny circular album art (28dp) + tiny title (11sp)
 * Active: large album art (120dp, 24dp rounded corners) on the left +
 *         title (Playfair Italic 22sp) + artist (NyghtSerif 14sp) +
 *         album (10sp, muted) + depth/zone label (9sp, letter-spaced)
 *
 * Tap (when inactive) → expands the layer. Tap (when active) → plays it.
 */
@Composable
private fun AbyssalLayerRow(
    song: Song,
    isActive: Boolean,
    opacity: Float,
    pickIndex: Int,
    totalPicks: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val depthMeters = (pickIndex.toFloat() / totalPicks * 11000).toInt()
    val zone = getDepthZone(pickIndex, totalPicks)

    Box(
        modifier = modifier
            .alpha(opacity)
            .pointerInput(isActive) {
                detectTapGestures(onTap = { onTap() })
            }
    ) {
        if (isActive) {
            // --- ACTIVE LAYER: full layout ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art (large, rounded)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1A))
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.albumArtUri)
                                .crossfade(400)
                                .build(),
                            contentDescription = "Album art for ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Text column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Depth/zone label (small, letter-spaced, mechanical)
                    Text(
                        text = "−${depthMeters}m  ·  ${zone.name}  ·  ${String.format("%02d", pickIndex + 1)}/${totalPicks}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Title (Playfair Italic, large, elegant)
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "titleMorph"
                    ) { s ->
                        Text(
                            text = s.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = PlayfairItalicFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Artist (NyghtSerif Light Italic, muted)
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "artistMorph"
                    ) { s ->
                        Text(
                            text = s.artist,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 14.sp,
                            fontFamily = NyghtSerifFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Album (very small, very muted)
                    Text(
                        text = song.album,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // --- INACTIVE LAYER: thin sliver ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tiny circular album art
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.albumArtUri)
                                .crossfade(200)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Tiny title (single line, ellipsized)
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Tiny depth readout on the right
                Text(
                    text = "−${depthMeters}m",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
            // Thin separator line below
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
    }
}

/**
 * The depth gauge — a vertical instrument on the right edge of the screen.
 * Shows 15 tick marks (one per pick) and a highlighted active marker.
 *
 * Visual:
 *   - Vertical line: 1dp wide, white at 30% alpha
 *   - Inactive ticks: 4dp wide horizontal marks
 *   - Active tick: 12dp wide, white at 90% alpha
 *   - Active marker: a small triangle pointing left (toward the layers)
 *
 * This is purely decorative — it visually reinforces the "submarine
 * instrument" feel. The actual depth/zone text is displayed inside the
 * active layer itself.
 */
@Composable
private fun DepthGauge(
    totalPicks: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.End
        ) {
            for (i in 0 until totalPicks) {
                val isActive = i == activeIndex
                val tickWidth by animateFloatAsState(
                    targetValue = if (isActive) 12f else 4f,
                    animationSpec = tween(250),
                    label = "tick_$i"
                )
                val tickAlpha by animateFloatAsState(
                    targetValue = if (isActive) 0.95f else 0.3f,
                    animationSpec = tween(250),
                    label = "tick_a_$i"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Active tick: small triangle marker pointing left
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(width = 0.dp, height = 0.dp)
                        )
                        // Use a tiny Canvas-drawn triangle next tick
                    }
                    // Tick mark
                    Box(
                        modifier = Modifier
                            .width(tickWidth.dp)
                            .height(if (isActive) 2.dp else 1.dp)
                            .background(Color.White.copy(alpha = tickAlpha))
                    )
                }
            }
        }
        // Active indicator triangle (overlay, positioned at active tick)
        // We use a Canvas to draw a small left-pointing triangle at the
        // active tick's vertical position.
        val activePosFraction = if (totalPicks > 1) {
            activeIndex.toFloat() / (totalPicks - 1)
        } else 0.5f
        CanvasTriangle(
            positionFraction = activePosFraction,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CanvasTriangle(
    positionFraction: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val y = size.height * positionFraction
        val x = size.width - 14f
        // Small left-pointing triangle
        val triangleSize = 4.dp.toPx()
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x + triangleSize, y - triangleSize)
                lineTo(x, y)
                lineTo(x + triangleSize, y + triangleSize)
                close()
            },
            color = Color.White.copy(alpha = 0.95f)
        )
    }
}

/**
 * The 5 ocean depth zones (real oceanography terminology).
 * Picks are evenly distributed across zones.
 */
private data class DepthZone(val name: String, val startM: Int, val endM: Int)

private val DEPTH_ZONES = listOf(
    DepthZone("EPIPELAGIC", 0, 200),
    DepthZone("MESOPELAGIC", 200, 1000),
    DepthZone("BATHYPELAGIC", 1000, 4000),
    DepthZone("ABYSSOPELAGIC", 4000, 6000),
    DepthZone("HADALPELAGIC", 6000, 11000)
)

private fun getDepthZone(pickIndex: Int, totalPicks: Int): DepthZone {
    val picksPerZone = (totalPicks.toFloat() / DEPTH_ZONES.size).coerceAtLeast(1f)
    val zoneIndex = (pickIndex / picksPerZone).toInt().coerceIn(0, DEPTH_ZONES.size - 1)
    return DEPTH_ZONES[zoneIndex]
}

/**
 * Darkens a color for the abyssal bg, with a slight blue shift to evoke
 * deep water. Uses luminance-based darkening (same approach as before)
 * but pushes darker for that "midnight ocean" feel.
 */
private fun abyssalDarkenColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val darkenFactor = 0.55f + 0.35f * luminance
    val darkened = androidx.compose.ui.graphics.lerp(color, Color.Black, darkenFactor)
    // Slight blue shift for deep-water feel
    return Color(
        red = darkened.red * 0.85f,
        green = darkened.green * 0.92f,
        blue = (darkened.blue * 1.0f).coerceAtMost(1f),
        alpha = darkened.alpha
    )
}
