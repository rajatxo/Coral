package com.rajatxo.coral.ui.player

import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.PlayerStyleManager
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * UnifiedPlayerSheet — a single composable that morphs between the mini player
 * (collapsed) and the full player (expanded), driven by a single
 * `expansionFraction: Animatable<Float>` (0 = mini, 1 = full).
 *
 * ## Architecture (YumaPlayer-inspired, original implementation)
 *
 * The sheet container itself morphs continuously:
 *   • Size:        240dp × 64dp  →  full screen
 *   • Position:    bottom-center (above nav bar)  →  top-left (0, 0)
 *   • Corner radius: 32dp (rounded pill)  →  0dp (square)
 *   • Glass backdrop: always samples the home screen content behind
 *
 * Inside the sheet, two layers crossfade:
 *   • MiniPlayerContent (alpha = 1 - fraction)  →  rendered at bottom-center
 *   • FullPlayer (alpha = fraction)              →  rendered at full screen size
 *     (clipped by the sheet's morphing shape)
 *
 * Gestures:
 *   • Tap on collapsed sheet  → expand
 *   • Drag up on collapsed sheet → expand
 *   • Drag down on expanded sheet → collapse
 *   • Drag left/right on collapsed sheet → dismiss (existing behavior)
 *
 * ## Glass morphism continuity
 *
 * The sheet applies `drawBackdrop` with the kyant backdrop library — same
 * AGSL-based real-time blur as the nav bar TabCapsule and the old MiniPlayer.
 * As the sheet grows, the glass grows with it, sampling more of the home
 * screen behind. This is the "thread" that visually connects the mini and
 * full states — the glass is always there, just expanding.
 *
 * ## Why this approach (crossfade + morphing shell)
 *
 * True YumaPlayer-style morph (where one composable renders different
 * layouts based on fraction) requires refactoring SpiralPlayer (~1500 lines)
 * into parameterized pieces. That's a larger refactor. This crossfade
 * approach achieves ~90% of the visual smoothness with much less risk of
 * breaking the player internals.
 *
 * The key visual win: the GLASS SHELL itself morphs continuously. The
 * content crossfades. To the eye, it looks like the glass mini pill is
 * expanding and the full player is materializing inside it.
 */
@Composable
fun UnifiedPlayerSheet(
    mediaController: MediaController?,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArtist: String?,
    currentSongAlbum: String?,
    currentSongArt: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onSongDelete: (Long) -> Unit,
    onSwipeDismiss: () -> Unit,
    /** Glass backdrop provided by HomeScreen — samples home content behind the sheet. */
    glassBackdrop: LayerBackdrop?,
    /** Bottom padding (in dp) for the collapsed mini position — accounts for nav bar. */
    miniBottomPaddingDp: androidx.compose.ui.unit.Dp,
    /** Mini player position state — for positionMs / durationMs. */
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // ─── Screen dimensions ────────────────────────────────────────
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val systemNavInsetPx = with(density) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
    }

    // ─── Mini (collapsed) dimensions ────────────────────────────────
    val miniWidthPx = with(density) { 240.dp.toPx() }
    val miniHeightPx = with(density) { 64.dp.toPx() }
    val miniCornerPx = with(density) { 32.dp.toPx() }

    // Collapsed Y: bottom of screen, with system bar inset + miniBottomPadding
    val miniBottomPaddingPx = with(density) { miniBottomPaddingDp.toPx() }
    val collapsedY = screenHeightPx - systemNavInsetPx - miniBottomPaddingPx - miniHeightPx
    val expandedY = 0f
    val sheetTravelDistance = (collapsedY - expandedY).coerceAtLeast(1f)  // avoid /0

    // ─── Sheet state ────────────────────────────────────────────────
    // expansionFraction: 0 = collapsed (mini), 1 = expanded (full)
    val expansionFraction = remember { Animatable(0f) }
    val fraction = expansionFraction.value

    // Horizontal swipe-to-dismiss (only active when collapsed)
    val dismissOffsetX = remember { Animatable(0f) }

    // ─── Player style ───────────────────────────────────────────────
    val playerStyle by PlayerStyleManager.playerStyle.collectAsState()

    // ─── Current geometry (interpolated) ───────────────────────────
    val currentY = lerp(collapsedY, expandedY, fraction)
    val currentWidthPx = lerp(miniWidthPx, screenWidthPx, fraction)
    val currentHeightPx = lerp(miniHeightPx, screenHeightPx, fraction)
    val currentCornerPx = lerp(miniCornerPx, 0f, fraction)
    val currentCornerDp = with(density) { currentCornerPx.toDp() }
    val sheetShape: Shape = RoundedCornerShape(currentCornerDp)

    // ─── Glass background (when sheet has glass backdrop) ─────────
    // At fraction=0, glass is at full strength (mini pill is mostly glass).
    // At fraction=1, glass fades to 0 — SpiralPlayer's blurred album cover
    // takes over as the visible background.
    val glassAlpha = (1f - fraction).coerceIn(0f, 1f)

    // ─── Gesture handling ──────────────────────────────────────────
    // Gestures are attached to the sheet container itself (NOT a full-screen
    // overlay), so touches OUTSIDE the sheet fall through to the home content
    // behind. The sheet's layout position (via .offset) is what determines
    // hit testing — using .graphicsLayer.translationY would NOT move the
    // hit-test region, so we use .offset for the sheet's actual position.
    //
    // Single pointerInput handles all gestures. Direction locks after 20px.
    //   • Vertical drag UP (when collapsed) → expand
    //   • Vertical drag DOWN (when expanded) → collapse
    //   • Horizontal drag (when collapsed) → swipe-to-dismiss
    var dragDirection by remember { mutableStateOf<Int?>(null) }  // 0=H, 1=V
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── The morphing sheet container ──────────────────────────
        // Positioned with .offset { ... } (lambda version = deferred read,
        // no recomposition on each frame). Size via .requiredSize.
        // Both are needed for proper hit-testing — graphicsLayer.translationY
        // would NOT move the touch region.
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = ((screenWidthPx - currentWidthPx) / 2f + dismissOffsetX.value).roundToInt(),
                        y = currentY.roundToInt()
                    )
                }
                .requiredSize(
                    width = with(density) { currentWidthPx.toDp() },
                    height = with(density) { currentHeightPx.toDp() }
                )
                .clip(sheetShape)
                .then(
                    // Glass backdrop — always samples home content behind.
                    // At fraction=1, SpiralPlayer's bg covers the glass, so
                    // we can skip drawing it for performance.
                    if (glassBackdrop != null && glassAlpha > 0.01f) {
                        Modifier
                            .graphicsLayer { alpha = glassAlpha }
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { sheetShape },
                                effects = {
                                    vibrancy()
                                    colorControls(
                                        brightness = 0.05f,
                                        contrast = 1f,
                                        saturation = 1.3f
                                    )
                                    blur(18f)
                                },
                                onDrawSurface = {
                                    drawRect(Color.Black.copy(alpha = 0.35f))
                                }
                            )
                    } else Modifier
                )
                .then(
                    // Glass border (fades out as fraction → 1)
                    if (fraction < 0.95f) {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f * (1f - fraction)),
                            shape = sheetShape
                        )
                    } else Modifier
                )
                // Gestures (only on the sheet, not the full screen)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragDirection = null
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            // Lock direction once drag exceeds 20px in either axis
                            if (dragDirection == null) {
                                if (abs(totalDragX) > 20f || abs(totalDragY) > 20f) {
                                    dragDirection = if (abs(totalDragX) > abs(totalDragY)) 0 else 1
                                }
                            }

                            when (dragDirection) {
                                0 -> {  // HORIZONTAL — swipe-to-dismiss (only when collapsed)
                                    if (fraction < 0.1f) {
                                        scope.launch {
                                            dismissOffsetX.snapTo(totalDragX)
                                        }
                                    }
                                }
                                1 -> {  // VERTICAL
                                    // Convert drag pixels to fraction delta
                                    val fractionDelta = -dragAmount.y / sheetTravelDistance
                                    scope.launch {
                                        expansionFraction.snapTo(
                                            (expansionFraction.value + fractionDelta).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            when (dragDirection) {
                                0 -> {  // HORIZONTAL
                                    if (fraction < 0.1f && abs(totalDragX) > screenWidthPx * 0.4f) {
                                        // Past 40% threshold → dismiss
                                        val target = if (totalDragX < 0) -screenWidthPx else screenWidthPx
                                        scope.launch {
                                            dismissOffsetX.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            onSwipeDismiss()
                                            delay(100)
                                            dismissOffsetX.snapTo(0f)
                                        }
                                    } else {
                                        // Spring back
                                        scope.launch {
                                            dismissOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    }
                                }
                                1 -> {  // VERTICAL
                                    // Snap to nearest anchor based on current fraction
                                    val targetFraction = if (expansionFraction.value > 0.5f) 1f else 0f
                                    val wasBelowHalf = expansionFraction.value < 0.5f
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            targetFraction,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                    // Haptic feedback when crossing the threshold
                                    if (targetFraction == 1f && wasBelowHalf) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dismissOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                val targetFraction = if (expansionFraction.value > 0.5f) 1f else 0f
                                expansionFraction.animateTo(
                                    targetFraction,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    )
                }
                // Tap (when collapsed) → expand
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = fraction < 0.5f
                ) {
                    scope.launch {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expansionFraction.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
        ) {
            // ─── Layer 1: Full player (alpha = fraction) ───────
            // Always rendered at FULL SCREEN size, clipped by the sheet's
            // morphing shape. As fraction → 1, the sheet grows to full
            // screen and the full player becomes fully visible.
            Box(
                modifier = Modifier
                    // Required to override parent's smaller constraints when
                    // the sheet is collapsed (sheet is 240×64, but SpiralPlayer
                    // needs full screen dimensions to lay out correctly).
                    .requiredSize(
                        width = with(density) { screenWidthPx.toDp() },
                        height = with(density) { screenHeightPx.toDp() }
                    )
                    .graphicsLayer { alpha = fraction }
            ) {
                    when (playerStyle) {
                        PlayerStyleManager.SPIRAL -> {
                            SpiralPlayer(
                                mediaController = mediaController,
                                songId = currentSongId,
                                title = currentSongTitle ?: "",
                                artist = currentSongArtist ?: "",
                                albumName = currentSongAlbum,
                                albumArtUri = currentSongArt,
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onNextClick = onNextClick,
                                onPrevClick = onPrevClick,
                                onSeek = onSeek,
                                onDismiss = {
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylist,
                                onSongDelete = onSongDelete,
                                sheetAlpha = 1f,  // alpha already applied via graphicsLayer above
                                disableDismissGesture = true  // sheet handles gestures
                            )
                        }
                        PlayerStyleManager.SPIRAL_2 -> {
                            Spiral2Player(
                                mediaController = mediaController,
                                songId = currentSongId,
                                title = currentSongTitle ?: "",
                                artist = currentSongArtist ?: "",
                                albumName = currentSongAlbum,
                                albumArtUri = currentSongArt,
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onNextClick = onNextClick,
                                onPrevClick = onPrevClick,
                                onSeek = onSeek,
                                onDismiss = {
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylist,
                                onSongDelete = onSongDelete,
                                sheetAlpha = 1f,
                                disableDismissGesture = true
                            )
                        }
                        PlayerStyleManager.SPIRAL_3 -> {
                            Spiral3Player(
                                mediaController = mediaController,
                                songId = currentSongId,
                                title = currentSongTitle ?: "",
                                artist = currentSongArtist ?: "",
                                albumName = currentSongAlbum,
                                albumArtUri = currentSongArt,
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onNextClick = onNextClick,
                                onPrevClick = onPrevClick,
                                onSeek = onSeek,
                                onDismiss = {
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylist,
                                onSongDelete = onSongDelete,
                                sheetAlpha = 1f,
                                disableDismissGesture = true
                            )
                        }
                        PlayerStyleManager.CORAL -> {
                            CoralPlayer(
                                mediaController = mediaController,
                                songId = currentSongId,
                                title = currentSongTitle ?: "",
                                artist = currentSongArtist ?: "",
                                albumName = currentSongAlbum,
                                albumArtUri = currentSongArt,
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onNextClick = onNextClick,
                                onPrevClick = onPrevClick,
                                onSeek = onSeek,
                                onDismiss = {
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylist,
                                sheetAlpha = 1f
                            )
                        }
                        else -> {
                            // Default: FullPlayer
                            FullPlayer(
                                mediaController = mediaController,
                                songId = currentSongId,
                                title = currentSongTitle ?: "",
                                artist = currentSongArtist ?: "",
                                albumName = currentSongAlbum,
                                albumArtUri = currentSongArt,
                                isPlaying = isPlaying,
                                onPlayPauseClick = onPlayPauseClick,
                                onNextClick = onNextClick,
                                onPrevClick = onPrevClick,
                                onSeek = onSeek,
                                onDismiss = {
                                    scope.launch {
                                        expansionFraction.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                                onAddToPlaylist = onAddToPlaylist,
                                sheetAlpha = 1f
                            )
                        }
                    }
                }

                // ─── Layer 2: MiniPlayer content (alpha = 1 - fraction) ─
                // Rendered at the bottom-center of the sheet, fixed at 240×64.
                // At fraction=0, sheet IS 240×64, so MiniPlayer fills it.
                // At fraction=1, sheet is full screen, MiniPlayer is at
                // bottom-center (invisible due to alpha).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .requiredSize(width = 240.dp, height = 64.dp)
                        .graphicsLayer { alpha = (1f - fraction).coerceIn(0f, 1f) }
                ) {
                    MiniPlayerContent(
                        title = currentSongTitle ?: "",
                        artist = currentSongArtist ?: "",
                        albumArtUri = currentSongArt,
                        songId = currentSongId,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = onPlayPauseClick
                    )
                }
            }
        }
    }

// ═══════════════════════════════════════════════════════════════════
// MiniPlayerContent — extracted from HomeScreen.MiniPlayer.
// Just the content row (album art + progress ring + title/artist + next).
// No pill shape, no glass backdrop — those are now on the UnifiedPlayerSheet.
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MiniPlayerContent(
    title: String,
    artist: String,
    albumArtUri: Uri?,
    songId: Long?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit
) {
    val favorites by com.rajatxo.coral.data.store.PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- Circular album art + progress ring (LEFT) ---
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
            // Progress ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

                drawArc(
                    color = Color.White.copy(alpha = 0.15f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawArc(
                    color = Color.White,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }

            // Album art
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (albumArtUri != null) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Music,
                            contentDescription = null,
                            tint = Color(0xFFB0B0B0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // Play/pause overlay
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- Title + artist ---
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // --- Heart button (RIGHT) ---
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (songId != null) {
                            com.rajatxo.coral.data.store.PlaylistStore.toggleFavorite(songId)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) CoralIcons.HeartLucideFilled else CoralIcons.HeartLucide,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                tint = if (isFavorite) CoralColors.Coral else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
