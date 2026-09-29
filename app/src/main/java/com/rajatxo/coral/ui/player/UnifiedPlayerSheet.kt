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
import androidx.compose.runtime.snapshotFlow
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
    /**
     * Called whenever the sheet's expansion fraction changes (0 = collapsed,
     * 1 = expanded). HomeScreen uses this to slide the nav bar down + hide
     * the search FAB when the player is expanded (Yuma-style).
     */
    onExpansionFractionChanged: (Float) -> Unit = {},
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
    val miniHeightPx = with(density) { 64.dp.toPx() }

    // ─── Growing sheet (Yuma's actual approach) ────────────────────
    // The sheet's HEIGHT lerps from miniHeight (collapsed) to full screen
    // (expanded). The TOP edge moves UP as the sheet grows. The BOTTOM
    // edge stays PINNED at the same screen Y throughout — this is the
    // key insight from Yuma: bottom pinned, top grows upward.
    //
    //   fraction=0: top = collapsedY, height = miniHeight
    //                → only a 64dp pill at the bottom is visible
    //   fraction=1: top = 0, height = screenHeight
    //                → full screen
    val miniBottomPaddingPx = with(density) { miniBottomPaddingDp.toPx() }
    val collapsedY = screenHeightPx - systemNavInsetPx - miniBottomPaddingPx - miniHeightPx
    val expandedY = 0f
    val sheetTravelDistance = (collapsedY - expandedY).coerceAtLeast(1f)  // avoid /0

    // ─── Sheet state ────────────────────────────────────────────────
    // expansionFraction: 0 = collapsed (mini), 1 = expanded (full)
    val expansionFraction = remember { Animatable(0f) }
    val fraction = expansionFraction.value

    // ─── Hoist expansion fraction to parent ──────────────────────
    // snapshotFlow collects fraction changes without recomposing on every frame.
    // HomeScreen uses this to slide the nav bar down + hide the search FAB.
    LaunchedEffect(expansionFraction) {
        snapshotFlow { expansionFraction.value }
            .collect { onExpansionFractionChanged(it) }
    }

    // Horizontal swipe-to-dismiss (only active when collapsed)
    val dismissOffsetX = remember { Animatable(0f) }

    // ─── Player style ───────────────────────────────────────────────
    val playerStyle by PlayerStyleManager.playerStyle.collectAsState()

    // ─── Current geometry (interpolated) ───────────────────────────
    // Sheet's top edge moves UP as fraction increases.
    // Sheet's HEIGHT grows from miniHeight to screenHeight.
    // Bottom edge stays PINNED at (collapsedY + miniHeight) throughout.
    val currentTopY = lerp(collapsedY, expandedY, fraction)
    val currentHeightPx = lerp(miniHeightPx, screenHeightPx, fraction)
    val currentHeightDp = with(density) { currentHeightPx.toDp() }
    val currentCornerDp = with(density) { lerp(32.dp.toPx(), 0.dp.toPx(), fraction).toDp() }
    val sheetShape: Shape = RoundedCornerShape(currentCornerDp)

    // ─── Glass background ─────────────────────────────────────────
    val glassAlpha = (1f - fraction).coerceIn(0f, 1f)

    // ─── Yuma's spring spec (exactly matching) ────────────────────
    val sheetSpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow
    )

    // ─── Gesture handling ──────────────────────────────────────────
    var dragDirection by remember { mutableStateOf<Int?>(null) }  // 0=H, 1=V
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        // ─── The growing sheet ──
        // Height lerps from 64dp (collapsed) to full screen (expanded).
        // Top edge moves up (offset Y = currentTopY).
        // Bottom edge pinned at (currentTopY + currentHeight).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeightDp)
                .offset {
                    IntOffset(
                        x = dismissOffsetX.value.roundToInt(),
                        y = currentTopY.roundToInt()
                    )
                }
                .clip(sheetShape)
                .then(
                    // Glass backdrop — samples home content behind.
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
                                    blur(18f.dp.toPx())
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
                // Drag gestures — does NOT consume taps (taps pass through)
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
                                1 -> {  // VERTICAL — translationY follows finger
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
                                        val target = if (totalDragX < 0) -screenWidthPx else screenWidthPx
                                        scope.launch {
                                            dismissOffsetX.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            onSwipeDismiss()
                                            delay(100)
                                            dismissOffsetX.snapTo(0f)
                                        }
                                    } else {
                                        scope.launch {
                                            dismissOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    }
                                }
                                1 -> {  // VERTICAL — snap to nearest anchor (Yuma's velocity-based logic)
                                    // Yuma: if fraction > 0.5 → expand, else → collapse
                                    // (Yuma also uses velocity, but for simplicity we use the 50% threshold)
                                    val targetFraction = if (expansionFraction.value > 0.5f) 1f else 0f
                                    val wasBelowHalf = expansionFraction.value < 0.5f
                                    scope.launch {
                                        expansionFraction.animateTo(targetFraction, sheetSpring)
                                    }
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
                                expansionFraction.animateTo(targetFraction, sheetSpring)
                            }
                        }
                    )
                }
        ) {
            // ════════════════════════════════════════════════════════════
            // LAYER 1: Full player (alpha = fraction)
            // ════════════════════════════════════════════════════════════
            // CRITICAL: Use requiredSize with FULL SCREEN dimensions, NOT
            // fillMaxSize. The sheet's height is dynamic (lerps from 64dp to
            // full screen). If we used fillMaxSize, the player would lay out
            // at the sheet's CURRENT height (64dp when collapsed) — causing
            // layout issues and potential crashes when the player tries to
            // position elements that don't fit.
            //
            // requiredSize OVERRIDES the parent's constraints — the player
            // always lays out at full screen size. The sheet's clip then
            // exposes only the visible portion (top-to-bottom reveal).
            Box(
                modifier = Modifier
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

                // ════════════════════════════════════════════════════════════
                // LAYER 2: MiniPlayer content (alpha = 1 - fraction)
                // ════════════════════════════════════════════════════════════
                // Yuma-style: mini content is FULL WIDTH with 16dp horizontal
                // padding (not 240dp fixed width). This matches the sheet's
                // visible glass area — no mismatch between glass width and
                // content width.
                //
                // Rendered at the TOP of the sheet, on TOP of the full player.
                // Moves UP with the sheet as it expands — follows the finger.
                //
                // Tap on the mini content → expand (only enabled when collapsed).
                // This is on the MINI CONTENT only, not the full sheet — so
                // taps on the home content behind pass through.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer {
                            alpha = (1f - fraction).coerceIn(0f, 1f)
                        }
                        // Tap to expand — ONLY on the mini content area
                        // (not the full sheet, so home content behind is tappable)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = fraction < 0.5f
                        ) {
                            scope.launch {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                expansionFraction.animateTo(1f, sheetSpring)
                            }
                        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),  // Yuma-style: 16dp horizontal padding
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
