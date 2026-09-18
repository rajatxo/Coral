package com.rajatxo.coral.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.audio.CrossfadeVisualState
import com.rajatxo.coral.data.lyrics.Lyric
import com.rajatxo.coral.data.lyrics.LyricLine
import com.rajatxo.coral.data.lyrics.LyricsRepository
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * SpiralExpandedPlayer — a UNIFIED player sheet that expands from mini player
 * to full screen. Built from scratch to support the expand animation properly.
 *
 * KEY DIFFERENCE from Spiral2Player:
 * This is ONE composable that serves as BOTH the mini player AND the full
 * player. The container animates its height from ~64dp (collapsed/mini) to
 * full screen (expanded). Content crossfades: mini player content fades out
 * as full player content fades in. No separate AnimatedVisibility overlays.
 *
 * LAYOUT (expanded state — matches Spiral 2.0):
 *   1. Full-screen gradient background (from album art palette)
 *   2. Status bar spacer
 *   3. Top bar: back chevron + heart + queue
 *   4. Square album art (full width, 10dp rounded corners)
 *   5. Song title + artist
 *   6. Lyrics strip (tap to open full lyrics)
 *   7. Seek bar with times
 *   8. Transport: prev · play/pause · next
 *   9. Volume slider
 *  10. Bottom utility: shuffle · repeat · queue
 *
 * LAYOUT (collapsed state — mini player):
 *   Frosted-glass pill at the bottom with album art + progress ring +
 *   title/artist + play/pause + next. Same glass effect as the nav bar.
 *
 * ANIMATION:
 *   Tap mini player → container grows upward to full screen (tween 380ms,
 *   FastOutSlowInEasing — smooth ease, NO bounce, NO spring).
 *   Drag down on full player → container shrinks back to mini player.
 *   Content crossfades during the height animation.
 */
@Composable
fun SpiralExpandedPlayer(
    mediaController: MediaController?,
    songId: Long?,
    title: String,
    artist: String,
    albumName: String?,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit = {},
    // Mini player params
    positionMs: Long,
    durationMs: Long,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp

    // ─── Expand fraction: 0 = collapsed, 1 = expanded ───────────────
    // tween with FastOutSlowInEasing = smooth ease, NO bounce, NO spring.
    val expandFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "expandFraction"
    )

    // ─── Palette extraction (same as Spiral2Player) ─────────────────
    var palette by remember(albumArtUri) {
        mutableStateOf(PaletteCache.get(albumArtUri) ?: CoralPalette.Default)
    }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            extractPalette(context, albumArtUri)?.let {
                palette = it
                PaletteCache.put(albumArtUri, it)
            }
        }
    }
    val animatedTopColor by animateColorAsState(palette.primary, tween(600), label = "top")
    val animatedMidColor by animateColorAsState(palette.secondary, tween(600), label = "mid")
    val animatedBottomColor by animateColorAsState(palette.tertiary, tween(600), label = "bottom")
    val animatedAccentColor by animateColorAsState(palette.accent, tween(600), label = "accent")

    // ─── Position polling ───────────────────────────────────────────
    var currentPositionMs by remember { mutableStateOf(0L) }
    var currentDurationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let {
                    currentPositionMs = it.currentPosition.coerceAtLeast(0L)
                    currentDurationMs = it.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    // ─── Favorites ──────────────────────────────────────────────────
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds

    // ─── Container height: collapsed (64dp) → expanded (full screen) ─
    val collapsedHeight = 64.dp
    val expandedHeight = screenHeightDp
    val containerHeight = lerp(collapsedHeight, expandedHeight, expandFraction)

    // ─── Drag-to-collapse gesture (only when expanded) ──────────────
    var dragOffsetY by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    // ─── Container ──────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clipToBounds()
    ) {
        // ══════════════════════════════════════════════════════════════
        // COLLAPSED STATE — Mini player with glass effect
        // Alpha fades out as expandFraction → 1
        // ══════════════════════════════════════════════════════════════
        if (expandFraction < 0.99f) {
            MiniPlayerContent(
                title = title,
                artist = artist,
                albumArtUri = albumArtUri,
                songId = songId,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                isFavorite = isFavorite,
                accentColor = animatedAccentColor,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                onClick = { onExpandChange(true) },
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - expandFraction).coerceIn(0f, 1f) }
            )
        }

        // ══════════════════════════════════════════════════════════════
        // EXPANDED STATE — Full player (Spiral 2.0 style)
        // Alpha fades in as expandFraction → 1
        // ══════════════════════════════════════════════════════════════
        if (expandFraction > 0.01f) {
            FullPlayerContent(
                title = title,
                artist = artist,
                albumArtUri = albumArtUri,
                albumName = albumName,
                isPlaying = isPlaying,
                isFavorite = isFavorite,
                positionMs = currentPositionMs,
                durationMs = currentDurationMs.coerceAtLeast(durationMs),
                palette = palette,
                animatedTopColor = animatedTopColor,
                animatedMidColor = animatedMidColor,
                animatedBottomColor = animatedBottomColor,
                animatedAccentColor = animatedAccentColor,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                onPrevClick = onPrevClick,
                onSeek = onSeek,
                onDismiss = { onExpandChange(false) },
                onToggleFavorite = { songId?.let { PlaylistStore.toggleFavorite(it) } },
                onOpenQueue = { },
                mediaController = mediaController,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = expandFraction.coerceIn(0f, 1f) }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// MINI PLAYER CONTENT — glass pill with album art + controls
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MiniPlayerContent(
    title: String,
    artist: String,
    albumArtUri: Uri?,
    songId: Long?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    accentColor: Color,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    modifier: Modifier = Modifier
) {
    val pillShape: Shape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .navigationBarsPadding()
    ) {
        val bodyModifier = if (backdrop != null) {
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(pillShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = 0.05f,
                            contrast = 1f,
                            saturation = 1.3f
                        )
                        blur(18f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.35f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), pillShape)
                .clickable(onClick = onClick)
        } else {
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(pillShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), pillShape)
                .clickable(onClick = onClick)
        }

        Box(modifier = bodyModifier) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art + progress ring
                val progress = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        val arcSize = Size(diameter, diameter)
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                    if (albumArtUri != null) {
                        AsyncImage(
                            model = albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                // Title + artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play/pause + next buttons
                Icon(
                    imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        )
                        .padding(8.dp)
                )
                Icon(
                    imageVector = CoralIcons.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNextClick
                        )
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// FULL PLAYER CONTENT — Spiral 2.0 style layout
// ════════════════════════════════════════════════════════════════════

@Composable
private fun FullPlayerContent(
    title: String,
    artist: String,
    albumArtUri: Uri?,
    albumName: String?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    positionMs: Long,
    durationMs: Long,
    palette: CoralPalette,
    animatedTopColor: Color,
    animatedMidColor: Color,
    animatedBottomColor: Color,
    animatedAccentColor: Color,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    mediaController: MediaController?,
    modifier: Modifier = Modifier
) {
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(1f, 1f), blurRadius = 3f)

    // Shuffle + repeat state
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    LaunchedEffect(mediaController) {
        mediaController?.let {
            shuffleEnabled = it.shuffleModeEnabled
            repeatMode = it.repeatMode
        }
    }

    // Drag-to-dismiss
    var dragOffsetY by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissThreshold = screenHeightPx * 0.25f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedTopColor, animatedMidColor, animatedBottomColor)
                )
            )
            .graphicsLayer { translationY = dragOffsetY }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > dismissThreshold) {
                            onDismiss()
                        }
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            // ─── Top bar: back + heart + queue ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoralIcons.ChevronDown,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(10.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFavorite) CoralIcons.HeartLucideFilled else CoralIcons.HeartLucide,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) animatedAccentColor else Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleFavorite
                            )
                            .padding(10.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = CoralIcons.Queue,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenQueue
                            )
                            .padding(10.dp)
                    )
                }
            }

            // ─── Album art (square, full width, rounded) ──────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                if (albumArtUri != null) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Title + artist ────────────────────────────────────────
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Seek bar ──────────────────────────────────────────────
            var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val progress = if (durationMs > 0)
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else 0f
            val displayProgress = dragFraction ?: progress

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .onSizeChanged { seekbarWidthPx = it.width.toFloat() }
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                dragFraction?.let { frac ->
                                    if (durationMs > 0) onSeek((frac * durationMs).toLong())
                                }
                                dragFraction = null
                            },
                            onDragCancel = { isDragging = false; dragFraction = null },
                            onDrag = { change, _ ->
                                if (durationMs > 0 && seekbarWidthPx > 0) {
                                    val frac = (change.position.x / seekbarWidthPx).coerceIn(0f, 1f)
                                    dragFraction = frac
                                }
                            }
                        )
                    }
            ) {
                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .align(Alignment.CenterStart)
                        .background(Color.White)
                )
            }

            // Times
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(positionMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Transport: prev · play/pause · next ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoralIcons.SkipPrev,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPrevClick
                        )
                        .padding(12.dp)
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Icon(
                    imageVector = CoralIcons.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNextClick
                        )
                        .padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Bottom utility: shuffle · repeat · queue ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoralIcons.ShuffleLucide,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) animatedAccentColor else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                mediaController?.shuffleModeEnabled = !shuffleEnabled
                                shuffleEnabled = !shuffleEnabled
                            }
                        )
                        .padding(10.dp)
                )
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) CoralIcons.Repeat else CoralIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) animatedAccentColor else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val newMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                mediaController?.repeatMode = newMode
                                repeatMode = newMode
                            }
                        )
                        .padding(10.dp)
                )
                Icon(
                    imageVector = CoralIcons.Queue,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenQueue
                        )
                        .padding(10.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val mm = totalSec / 60
    val ss = totalSec % 60
    return String.format(java.util.Locale.US, "%d:%02d", mm, ss)
}
