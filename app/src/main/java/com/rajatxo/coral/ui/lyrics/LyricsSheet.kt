package com.rajatxo.coral.ui.lyrics

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.lyrics.Lyric
import com.rajatxo.coral.data.lyrics.LyricLine
import com.rajatxo.coral.data.lyrics.LyricsRepository
import com.rajatxo.coral.data.lyrics.WordTimestamp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cinematic lyrics sheet — ArchiveTune-inspired.
 *
 * Features:
 *  1. Blurred album art background (46dp blur, 0.62 alpha) + gradient scrim
 *  2. Word-by-word karaoke animation (sweep fill + bounce + glow)
 *  3. Progressive opacity by distance from active line
 *  4. Smooth auto-scroll
 *  5. Top/bottom fade gradients
 *  6. Tap-to-seek on any line
 */
@Composable
fun LyricsSheet(
    trackName: String,
    artistName: String,
    albumName: String?,
    durationMs: Long?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit,
    albumArtUri: Uri? = null
) {
    val context = LocalContext.current
    val repository = remember { LyricsRepository(context) }

    var lyric by remember { mutableStateOf<Lyric?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var palette by remember { mutableStateOf(CoralPalette.Default) }

    LaunchedEffect(trackName, artistName, refreshTrigger) {
        if (trackName.isBlank() || artistName.isBlank()) {
            isLoading = false
            error = "No track info available"
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        try {
            val fetched = if (refreshTrigger == 0) {
                repository.getLyrics(trackName, artistName, albumName, durationMs)
            } else {
                repository.refreshLyrics(trackName, artistName, albumName, durationMs)
            }
            lyric = fetched
            if (fetched == null) error = "No lyrics found for this track"
        } catch (e: Exception) {
            error = "Failed to load lyrics: ${e.message ?: "unknown error"}"
        }
        isLoading = false
    }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            extractPalette(context, albumArtUri)?.let { palette = it }
        }
    }

    fun refresh() { refreshTrigger++ }

    // ═══ Background: blurred album art + gradient scrim ═════════════
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred album art
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(46.dp)
                    .graphicsLayer { alpha = 0.62f }
            )
        }

        // Gradient scrim (palette colors or fallback)
        val scrimColors = listOf(
            palette.tertiary.copy(alpha = 0.88f),
            palette.secondary.copy(alpha = 0.76f),
            palette.tertiary.copy(alpha = 0.96f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(scrimColors))
        )

        // Black tint
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        // Bottom scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f))
                    )
                )
        )

        // ═══ Content ═════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ─── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                if (albumArtUri != null) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Title + artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = artistName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1
                    )
                }
                // Close button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ─── Lyrics content ──────────────────────────────────────
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Searching...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♪", fontSize = 48.sp, color = Color.White.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(error ?: "No lyrics", color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { refresh() }
                                    .padding(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text("Try again", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                lyric != null -> {
                    CinematicLyricsContent(
                        lyric = lyric!!,
                        currentPositionMs = currentPositionMs,
                        onSeek = onSeek
                    )
                }
            }
        }
    }
}

// ═══ Cinematic lyrics content with word-by-word karaoke ═════════════

@Composable
private fun CinematicLyricsContent(
    lyric: Lyric,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit
) {
    if (!lyric.synced) {
        // Plain lyrics
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(lyric.lines.size) { index ->
                Text(
                    text = lyric.lines[index].text,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 18.sp,
                    fontFamily = CalSansFamily,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    val activeIndex = remember(lyric.lines, currentPositionMs) {
        findActiveLineIndex(lyric.lines, currentPositionMs)
    }

    val listState = rememberLazyListState()

    // Auto-scroll: active line at ~1/3 from top
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyric.lines.size) {
            val targetScroll = (activeIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(lyric.lines.size) { index ->
                val line = lyric.lines[index]
                val distance = kotlin.math.abs(index - activeIndex)
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                CinematicLine(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    distanceFromActive = distance,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek
                )
            }
        }

        // Top fade gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // Bottom fade gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )
    }
}

/**
 * A single cinematic lyric line.
 * - Active line: full opacity, 26sp, bold, word-by-word karaoke animation
 * - Past lines: 52% opacity (1 away), 30% (2), 18% (3), 10% (4+)
 * - Future lines: same opacity scale
 */
@Composable
private fun CinematicLine(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit
) {
    // Progressive opacity by distance
    val targetAlpha = when {
        isActive -> 1f
        distanceFromActive == 1 -> 0.52f
        distanceFromActive == 2 -> 0.30f
        distanceFromActive == 3 -> 0.18f
        else -> 0.10f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(if (isActive) 330 else 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "lineAlpha"
    )

    // Scale: active = 1.0, inactive = 0.95
    val targetScale = if (isActive) 1f else 0.95f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(166, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "lineScale"
    )

    val fontSize = 26.sp
    val fontWeight = if (isActive || isPast) FontWeight.ExtraBold else FontWeight.SemiBold

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (line.timeMs >= 0) onSeek(line.timeMs) }
    ) {
        if (isActive && line.hasWordSync && line.words != null) {
            // ─── Word-by-word karaoke line ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                line.words.forEach { word ->
                    AnimatedWord(
                        word = word,
                        currentPositionMs = currentPositionMs,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        } else {
            // ─── Regular line (no word sync or inactive) ────────────
            Text(
                text = line.text.ifBlank { "♪" },
                color = Color.White,
                fontSize = fontSize,
                fontFamily = CalSansFamily,
                fontWeight = fontWeight,
                lineHeight = (fontSize.value * 1.35f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A single animated word with karaoke sweep fill + bounce + glow.
 *
 * Animation details (from ArchiveTune LyricsV2.AnimatedWordV2):
 * - Sweep fill 0→1 with soft 8dp edge (BlendMode.DstIn)
 * - Bounce: scale 1 + 0.015 * sin(π * progress)
 * - Float: -4px * sin(π * progress)
 * - Glow: shadow alpha 0.45, radius 12
 * - Min sweep duration: 180ms, LinearEasing
 */
@Composable
private fun AnimatedWord(
    word: WordTimestamp,
    currentPositionMs: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val wordStartMs = word.startTime
    val wordEndMs = word.endTime
    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs >= wordStartMs && currentPositionMs < wordEndMs

    // Sweep fill 0→1
    val sweepAnimatable = remember(word) { Animatable(if (isWordComplete) 1f else 0f) }
    LaunchedEffect(isWordActive, isWordComplete, wordStartMs, wordEndMs, currentPositionMs) {
        when {
            isWordComplete && sweepAnimatable.value < 1f -> {
                sweepAnimatable.animateTo(1f, tween(80, easing = LinearEasing))
            }
            isWordActive -> {
                val remainingMs = (wordEndMs - currentPositionMs).coerceAtLeast(1L)
                sweepAnimatable.animateTo(1f, tween(maxOf(remainingMs, 180L).toInt(), easing = LinearEasing))
            }
            !isWordActive && !isWordComplete -> {
                sweepAnimatable.snapTo(0f)
            }
        }
    }
    val progress = if (isWordComplete) 1f else sweepAnimatable.value
    val sinProgress = sin(progress * PI).toFloat()

    // BOUNCE: scale 1.0 → 1.015 peak mid-word
    val wordScale = 1f + (0.015f * sinProgress)
    // FLOAT: -4px peak mid-word
    val targetFloat = if (isWordActive) -4f * sinProgress else 0f
    val floatOffset by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = tween(if (isWordActive) 50 else 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "wordFloat"
    )

    // GLOW: peaks at half-sweep, then holds
    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f else 0f

    val glowPadding = 10.dp
    val fillTransitionWidth = 8f

    val baseTextStyle = TextStyle(
        color = Color.White.copy(alpha = 0.35f),
        fontSize = fontSize,
        fontFamily = CalSansFamily,
        fontWeight = fontWeight
    )

    val fillShadow = if (isWordActive && glowAlpha > 0f) Shadow(
        color = Color.White.copy(alpha = glowAlpha),
        offset = androidx.compose.ui.geometry.Offset.Zero,
        blurRadius = glowRadius.coerceAtLeast(1f)
    ) else null

    val fillTextStyle = TextStyle(
        color = Color.White.copy(alpha = 1f),
        fontSize = fontSize,
        fontFamily = CalSansFamily,
        fontWeight = fontWeight
    ).let { if (fillShadow != null) it.copy(shadow = fillShadow) else it }

    Box(
        modifier = modifier
            .graphicsLayer {
                clip = false
                translationY = floatOffset * density.density
                scaleX = wordScale
                scaleY = wordScale
            }
    ) {
        // Base layer (dim)
        Text(
            text = word.text,
            style = baseTextStyle,
            modifier = Modifier.padding(glowPadding)
        )

        // Overlay layer (fill) — only if word is active/complete/past
        if (progress > 0f) {
            if (isWordActive && !isWordComplete) {
                // Karaoke sweep wipe with soft edge
                Text(
                    text = word.text,
                    style = fillTextStyle,
                    modifier = Modifier
                        .padding(glowPadding)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeWidth = fillTransitionWidth.dp.toPx()
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            } else {
                // Complete or past — full fill
                Text(
                    text = word.text,
                    style = fillTextStyle,
                    modifier = Modifier.padding(glowPadding)
                )
            }
        }
    }
}

/**
 * Find the active line index using binary search.
 */
private fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.lastIndex
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs in 0..positionMs) {
            result = mid
            lo = mid + 1
        } else if (lines[mid].timeMs > positionMs) {
            hi = mid - 1
        } else {
            lo = mid + 1
        }
    }
    return result
}
