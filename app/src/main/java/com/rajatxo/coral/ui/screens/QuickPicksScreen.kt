package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * QuickPicksScreen — "Liquid Aurora" edition.
 *
 * A premium, full-screen living background with:
 *   1. Animated mesh gradient aurora (slowly morphing color blobs)
 *   2. Drifting light orbs (large, blurred, slow)
 *   3. Particle dust (tiny, slow-moving specks)
 *   4. Glass song cards floating over the aurora (editorial stagger)
 *
 * The aurora's colors shift based on the current song's palette — warm
 * songs lean warm, cool songs lean cool. Ties the background to the
 * music without being literal.
 *
 * No vinyl. No toys. No small thing with big background. Full-screen,
 * dark, premium, always gently moving.
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // ─── Current song's album art → palette ─────────────────────────
    // The aurora's hue shifts based on the current song. If no song is
    // playing, use a sophisticated default (deep midnight + warm amber).
    val currentSong = remember(songs, currentSongId) {
        songs.firstOrNull { it.id == currentSongId }
    }
    var palette by remember {
        mutableStateOf(CoralPalette.Default)
    }
    LaunchedEffect(currentSong?.albumArtUri) {
        currentSong?.albumArtUri?.let { uri ->
            PaletteCache.get(uri)?.let { palette = it }
            extractPalette(context, uri)?.let {
                palette = it
                PaletteCache.put(uri, it)
            }
        }
    }

    // ─── Quick picks songs (top 8 by recent play, fallback to random) ──
    val quickPicks = remember(songs, currentSongId) {
        if (songs.isEmpty()) emptyList()
        else {
            val current = songs.firstOrNull { it.id == currentSongId }
            if (current != null) {
                val sameArtist = songs.filter { it.artist == current.artist && it.id != current.id }
                val others = songs.filter { it.id != current.id && it.artist != current.artist }
                (listOf(current) + sameArtist.take(3) + others.shuffled().take(5)).distinct().take(8)
            } else {
                songs.shuffled().take(8)
            }
        }
    }

    // ─── Infinite animation timeline ────────────────────────────────
    // One master transition drives all the motion. Different elements
    // use different fractions of the timeline for organic, non-uniform
    // movement (nothing moves in sync — that looks mechanical).
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05050A))  // near-black base
    ) {
        // ═══════════════════════════════════════════════════════════════
        // LAYER 1: Animated aurora mesh gradient (full screen, behind everything)
        // ═══════════════════════════════════════════════════════════════
        AuroraBackground(
            palette = palette,
            time = time,
            modifier = Modifier.fillMaxSize()
        )

        // ═══════════════════════════════════════════════════════════════
        // LAYER 2: Drifting light orbs (large, blurred, slow)
        // ═══════════════════════════════════════════════════════════════
        LightOrbs(
            palette = palette,
            time = time,
            modifier = Modifier.fillMaxSize()
        )

        // ═══════════════════════════════════════════════════════════════
        // LAYER 3: Particle dust (tiny, slow-moving specks)
        // ═══════════════════════════════════════════════════════════════
        ParticleDust(
            time = time,
            modifier = Modifier.fillMaxSize()
        )

        // ═══════════════════════════════════════════════════════════════
        // LAYER 4: Glass song cards (scrollable, on top of the aurora)
        // ═══════════════════════════════════════════════════════════════
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 80.dp,      // leave room for the settings button
                bottom = 180.dp,  // leave room for the mini player + nav bar
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section title
            item {
                Text(
                    text = "Quick picks",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily
                )
            }
            item {
                Text(
                    text = if (currentSong != null) "Based on what you're playing"
                           else "A fresh mix for you",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = CalSansFamily
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Glass song cards
            items(quickPicks) { song ->
                GlassSongCard(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    accentColor = palette.accent,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// AURORA BACKGROUND — animated mesh gradient
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AuroraBackground(
    palette: CoralPalette,
    time: Float,
    modifier: Modifier = Modifier
) {
    // The aurora is built from 3 large radial gradient blobs that drift
    // around the screen on different orbits. Their colors come from the
    // current song's palette. The whole thing is drawn behind a dark
    // overlay so the colors are subtle, not garish.

    val colors = listOf(palette.primary, palette.secondary, palette.tertiary)

    Box(
        modifier = modifier
            .drawBehind {
                val w = size.width
                val h = size.height

                // Base fill — deep near-black
                drawRect(Color(0xFF05050A))

                // 3 drifting blobs. Each has a different orbit radius,
                // speed multiplier, and starting angle so they never
                // sync up. The blobs are drawn with radial gradients
                // and heavy blur for the soft aurora look.
                for (i in 0..2) {
                    val angle = Math.toRadians((time * (1 + i * 0.3) + i * 120.0).toDouble())
                    val orbitX = w * (0.3 + 0.4 * i / 2)
                    val orbitY = h * (0.25 + 0.3 * i / 2)
                    val cx = (w * 0.5 + cos(angle) * orbitX).toFloat()
                    val cy = (h * 0.4 + sin(angle) * orbitY).toFloat()
                    val radius = (w * 0.6f).coerceAtLeast(h * 0.4f)

                    drawCircle(
                        color = colors[i].copy(alpha = 0.45f),
                        radius = radius,
                        center = Offset(cx, cy)
                    )
                }

                // Dark overlay to keep it subtle and premium
                drawRect(Color(0xFF05050A).copy(alpha = 0.55f))
            }
            .blur(80.dp)  // heavy blur for the soft aurora look
    )
}

// ════════════════════════════════════════════════════════════════════
// LIGHT ORBS — large, blurred, slow-drifting
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LightOrbs(
    palette: CoralPalette,
    time: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 3 orbs, each a different color from the palette, drifting on
        // different orbits. Heavy blur makes them soft glows.
        val orbColors = listOf(palette.accent, palette.primary, palette.secondary)

        orbColors.forEachIndexed { i, color ->
            val angle = Math.toRadians((time * (0.5 + i * 0.2) + i * 90.0).toDouble())
            val offsetX = (cos(angle) * 80).toFloat()
            val offsetY = (sin(angle) * 60).toFloat()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX * density
                        translationY = offsetY * density
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(color.copy(alpha = 0.15f))
                        .blur(60.dp)
                        .align(if (i == 0) Alignment.TopStart
                               else if (i == 1) Alignment.CenterEnd
                               else Alignment.BottomCenter)
                        .padding(
                            start = if (i == 0) 40.dp else 0.dp,
                            top = if (i == 0) 120.dp else 0.dp,
                            end = if (i == 1) 40.dp else 0.dp,
                            bottom = if (i == 2) 200.dp else 0.dp
                        )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PARTICLE DUST — tiny slow-moving specks
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ParticleDust(
    time: Float,
    modifier: Modifier = Modifier
) {
    // 40 dust particles, each with a fixed seed position, drifting
    // slowly upward and swaying slightly. Drawn as tiny white dots
    // with low alpha.
    val particleCount = 40
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height
            for (i in 0 until particleCount) {
                // Seeded pseudo-random position for each particle
                val seed = i * 137.5
                val baseX = ((seed % w)).toFloat().coerceIn(0f, w)
                val baseY = ((seed * 1.7) % h).toFloat().coerceIn(0f, h)

                // Drift upward, wrap around
                val driftY = (baseY - time * (0.5f + (i % 3) * 0.2f)) % h
                val y = if (driftY < 0) driftY + h else driftY
                val x = baseX + sin(Math.toRadians((time + i * 30).toDouble())).toFloat() * 15f

                val alpha = 0.15f + 0.1f * sin(Math.toRadians((time * 2 + i * 45).toDouble())).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = alpha.coerceIn(0.05f, 0.25f)),
                    radius = 1.5f,
                    center = Offset(x, y)
                )
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════
// GLASS SONG CARD — frosted glass with album art + title + artist
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GlassSongCard(
    song: Song,
    isCurrent: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = song.id in favorites.songIds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (isCurrent) accentColor.copy(alpha = 0.4f)
                        else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
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

        Spacer(modifier = Modifier.width(14.dp))

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) accentColor else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Favorite heart (if favorited)
        if (isFavorite) {
            Icon(
                imageVector = CoralIcons.HeartLucideFilled,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Now-playing indicator (if current song)
        if (isCurrent) {
            Icon(
                imageVector = CoralIcons.VolumeHigh,
                contentDescription = "Now playing",
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Need collectAsState for the favorites flow
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsState() =
    androidx.compose.runtime.collectAsState(this)
