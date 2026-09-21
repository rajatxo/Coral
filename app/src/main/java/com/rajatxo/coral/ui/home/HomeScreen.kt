package com.rajatxo.coral.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.data.store.PlaylistStore
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.components.CoralNavRail
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.player.FullPlayer
import com.rajatxo.coral.ui.screens.PlaceholderScreen
import com.rajatxo.coral.ui.screens.PlaylistDetailScreen
import com.rajatxo.coral.ui.screens.PlaylistsScreen
import com.rajatxo.coral.ui.screens.QuickPicksScreen
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.screens.SongPickerScreen
import com.rajatxo.coral.ui.screens.SongsScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Root composable for the post-launch experience.
 *
 * Layout:
 *   Row {
 *     CoralNavRail       // 72dp vertical rail on the left
 *     Column {
 *       Screen content   // SongsScreen / PlaylistsScreen / SettingsScreen
 *       MiniPlayer       // only if there's a current song
 *     }
 *   }
 *   FullPlayerOverlay    // expands on top when showFullPlayer = true
 *
 * This is the engine-room composable — all UI state lives here and is passed
 * down. Phase 4 will replace the FullPlayerOverlay with the real BitChord-style
 * now-playing screen.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songs: List<Song>,
    mediaController: MediaController?,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArtist: String?,
    currentSongAlbum: String?,
    currentSongArt: android.net.Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongClickWithQueue: (Song, List<Song>) -> Unit,
    onMiniPlayerClick: () -> Unit,
    showFullPlayer: Boolean,
    onFullPlayerDismiss: () -> Unit,
    onSongEnded: () -> Unit,
    onRefresh: suspend () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }
    var selectedPlaylist by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showSongPicker by remember { mutableStateOf(false) }
    var playlistForPicker by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showPremium by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }

    // --- Add to playlist from FullPlayer ---
    // When user taps "Add to playlist" in the FullPlayer 3-dot menu,
    // this stores the song ID and shows a playlist picker dialog.
    var songToAddToPlaylist by remember { mutableStateOf<Long?>(null) }

    // --- System back button handling ---
    // When the playlist detail overlay is open, the system back button
    // should dismiss it (set selectedPlaylist = null) instead of closing
    // the app. Same for the full player, song picker, and other overlays.
    androidx.activity.compose.BackHandler(
        enabled = selectedPlaylist != null || showFullPlayer || showSongPicker ||
                  showPremium || showSettings || showSearch || showEqualizer || showSleepTimer || showFontPicker
    ) {
        when {
            showFullPlayer -> onFullPlayerDismiss()
            showSearch -> { showSearch = false }
            showSongPicker -> { showSongPicker = false }
            showPremium -> { showPremium = false }
            showSettings -> { showSettings = false }
            showEqualizer -> { showEqualizer = false }
            showSleepTimer -> { showSleepTimer = false }
            showFontPicker -> { showFontPicker = false }
            selectedPlaylist != null -> { selectedPlaylist = null }
        }
    }

    // --- Mini player position polling ---
    // Polls the playback position every 500ms so the circular progress
    // ring around the album art in the mini player can show song timeline.
    var miniPlayerPositionMs by remember { mutableStateOf(0L) }
    var miniPlayerDurationMs by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let { controller ->
                    miniPlayerPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    miniPlayerDurationMs = controller.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(if (isPlaying) 500L else 2000L)
        }
    }

    // Equalizer controller + sleep timer — singletons for the home screen's
    // lifetime. Created here (not in the screen) so the equalizer state
    // survives config changes and isn't reset when the screen recomposes.
    val equalizerController = remember { com.rajatxo.coral.audio.EqualizerController() }
    // SleepTimer is now a SINGLETON — no need to create it here.
    // It survives Activity destruction (rotation, task manager kill).
    val sleepTimer = com.rajatxo.coral.data.premium.SleepTimer

    // When the current song changes, fire the sleep timer's end-of-song
    // trigger (in case the user set the timer to "end of current song").
    androidx.compose.runtime.LaunchedEffect(currentSongId) {
        if (currentSongId != null) {
            sleepTimer.onSongEnd()
        }
    }

    // --- Sleep Timer Capsule state ---
    val sleepTimerState by sleepTimer.state.collectAsState()
    var sleepRemainingMs by remember { mutableStateOf(0L) }

    // When the timer fires (active goes true→false), pause playback
    androidx.compose.runtime.LaunchedEffect(sleepTimerState.active) {
        if (!sleepTimerState.active && sleepRemainingMs > 0) {
            // Timer just expired — pause the player
            onSongEnded()
            sleepRemainingMs = 0
        }
    }

    // Poll remaining time every 1 second when timer is active
    androidx.compose.runtime.LaunchedEffect(sleepTimerState.active) {
        while (sleepTimerState.active) {
            val endAt = sleepTimerState.endAtMs
            if (endAt != null) {
                sleepRemainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            }
            delay(1000L)
        }
    }

    // Extract palette for the capsule's progress color + playlist wheel accent
    // Default = #F4B400 (warm amber/gold) when no song is playing.
    // When a song plays, auto-detects the album art's vibrant color.
    // Also caches the full palette in PaletteCache so the full player can
    // read it INSTANTLY when opened (no black flash).
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    var capsuleAccentColor by remember { mutableStateOf<Color>(Color(0xFFF4B400)) }
    androidx.compose.runtime.LaunchedEffect(currentSongArt) {
        com.rajatxo.coral.util.extractPalette(
            context = homeContext,
            artUri = currentSongArt
        )?.let {
            capsuleAccentColor = it.accent
            // Cache the full palette so the full player opens with no flash
            if (currentSongArt != null) {
                com.rajatxo.coral.util.PaletteCache.put(currentSongArt, it)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        // --- Sleep timer capsule state (top-level scope, accessible by all overlays) ---
        val capsuleVisible = sleepTimerState.active &&
            (sleepRemainingMs > 0 || sleepTimerState.endOfSong)
        val capsuleRemaining = if (sleepTimerState.endOfSong) {
            (miniPlayerDurationMs - miniPlayerPositionMs).coerceAtLeast(0L)
        } else {
            sleepRemainingMs
        }
        val onExtend: () -> Unit = { sleepTimer.extend(10) }

        // --- Liquid Glass backdrop (kyant backdrop library v1.0.0 API) ---
        // rememberLayerBackdrop takes a GraphicsLayer + onDraw lambda.
        // The page content applies the backdrop via layerBackdrop(backdrop).
        // The TabCapsule applies drawBackdrop(backdrop, blur) to sample + blur.
        val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
        val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
            graphicsLayer = graphicsLayer
        ) {
            drawContent()
        }

        // Main content — fills the WHOLE screen (no nav rail anymore)
        // Wrapped with layerBackdrop so the nav bar can sample + blur this.
        //
        // Pull-to-refresh is NO LONGER global. Each tab screen handles its
        // own PTR:
        //   - QuickPicksScreen: PTR refreshes the page contents (rolls a
        //     new random seed → new hero/recent/more selections). Custom
        //     wind indicator (WindRefreshIndicator).
        //   - SongsScreen: PTR triggers a library rescan (calls onRefresh).
        //     Same wind indicator for consistency.
        // Other tabs (Discover, Playlists, Artists, Albums) have no PTR.
        //
        // ─── Mini player dismissed state ─────────────────────────────
        // When the user swipes left/right on the mini player, it's
        // dismissed (fades out + slides away) and playback pauses.
        //
        // The mini player reappears when:
        //   • A new song is selected (currentSongId changes) — LaunchedEffect
        //   • The SAME song is replayed — handled by wrapping onSongClick
        //     below to reset miniPlayerDismissed before forwarding the call.
        //     Without this, replaying the same song (currentSongId stays
        //     the same) wouldn't trigger the LaunchedEffect, leaving the
        //     mini player hidden even though the song is playing.
        //
        // DECLARED HERE (before the content Box) so the wrapped callbacks
        // are in scope when the screens below use them.
        var miniPlayerDismissed by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(currentSongId) {
            if (currentSongId != null && miniPlayerDismissed) {
                miniPlayerDismissed = false
            }
        }
        val onSongClickWithReset: (Song) -> Unit = { song ->
            miniPlayerDismissed = false
            onSongClick(song)
        }
        val onSongClickWithQueueReset: (Song, List<Song>) -> Unit = { song, queue ->
            miniPlayerDismissed = false
            onSongClickWithQueue(song, queue)
        }

        // ─── Push back animation (Yuma-style) ──
        // When the FullPlayer opens, the main content dims (alpha → 0.6)
        // — like the home screen is being "pushed back" behind the player.
        //
        // NOTE: Previously also scaled down (0.93x) but that exposed black
        // bars on both sides of the screen during the animation (the
        // scaled-down content didn't cover the full width, showing the
        // parent's background). Removed the scale — alpha-only dimming
        // still gives the "push back" feel without the visual glitch.
        val pushBackFraction by animateFloatAsState(
            targetValue = if (showFullPlayer) 1f else 0f,
            animationSpec = tween(350),
            label = "pushBack"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - (0.4f * pushBackFraction)
                }
                .layerBackdrop(glassBackdrop)
        ) {
            when (selectedTab) {
                CoralTab.QuickPicks -> QuickPicksScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongArt = currentSongArt,
                    capsuleVisible = capsuleVisible,
                    capsuleRemaining = capsuleRemaining,
                    onExtend = onExtend,
                    onSongClick = onSongClickWithReset
                )
                CoralTab.Discover -> PlaceholderScreen(
                    tabName = "Discover",
                    description = "Random shuffle, hidden gems, and smart recommendations based on your listening. Coming soon.",
                    capsuleVisible = capsuleVisible,
                    capsuleRemaining = capsuleRemaining,
                    onExtend = onExtend
                )
                CoralTab.Songs -> SongsScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongTitle = currentSongTitle,
                    onSongClick = onSongClickWithReset,
                    capsuleVisible = capsuleVisible,
                    capsuleRemaining = capsuleRemaining,
                    onExtend = onExtend,
                    onRefresh = onRefresh
                )
                CoralTab.Playlists -> {
                    // Always show PlaylistsScreen. When a playlist is tapped,
                    // the detail screen appears as a full-screen overlay below.
                    PlaylistsScreen(
                        onPlaylistClick = { selectedPlaylist = it },
                        capsuleVisible = capsuleVisible,
                        capsuleRemaining = capsuleRemaining,
                        onExtend = onExtend,
                        accentColor = capsuleAccentColor
                    )
                }
                CoralTab.Artists -> PlaceholderScreen(
                    tabName = "Artists",
                    description = "Browse your library by artist. Coming soon.",
                    capsuleVisible = capsuleVisible,
                    capsuleRemaining = capsuleRemaining,
                    onExtend = onExtend
                )
                CoralTab.Albums -> PlaceholderScreen(
                    tabName = "Albums",
                    description = "Browse your library by album. Coming soon.",
                    capsuleVisible = capsuleVisible,
                    capsuleRemaining = capsuleRemaining,
                    onExtend = onExtend
                )
            }
        }

        // --- Mini player (bottom-center, TRACKS the nav bar) ---
        // The mini player sits a fixed 10dp ABOVE the TabCapsule (nav bar).
        // When the user drags the nav bar, the mini player recomposes and
        // follows — same 10dp gap maintained at all times.
        //
        // Math (in dp, from screen bottom):
        //   navBarCenterFromBottom = screenHeight * (1 - yFrac)
        //   navBarTopFromBottom    = navBarCenterFromBottom + 26dp (half of 52dp capsule)
        //   miniPlayerBottom       = navBarTopFromBottom + 10dp (gap)
        //   .padding(bottom = miniPlayerBottom - systemNavInset)
        //     (because .navigationBarsPadding() already adds the system nav inset)
        //
        // Default yFrac=0.89 → mini player bottom ≈ 122dp from screen bottom.
        val savedTabPos by com.rajatxo.coral.data.prefs.TabCapsulePosition.position.collectAsState()
        val tabYFrac = savedTabPos.second
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val systemNavInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val capsuleHeight = 52.dp
        val miniPlayerGap = 10.dp  // gap between mini player bottom and nav bar top
        val navBarCenterFromBottom = configuration.screenHeightDp.dp * (1f - tabYFrac)
        val navBarTopFromBottom = navBarCenterFromBottom + (capsuleHeight / 2)
        val miniPlayerBottomFromScreenBottom = navBarTopFromBottom + miniPlayerGap
        val miniPlayerPaddingBottom = (miniPlayerBottomFromScreenBottom - systemNavInset)
            .coerceAtLeast(0.dp)

        AnimatedVisibility(
            visible = currentSongTitle != null && !miniPlayerDismissed,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = miniPlayerPaddingBottom)
        ) {
            MiniPlayer(
                title = currentSongTitle ?: "",
                artist = currentSongArtist ?: "",
                albumArtUri = currentSongArt,
                songId = currentSongId,
                isPlaying = isPlaying,
                positionMs = miniPlayerPositionMs,
                durationMs = miniPlayerDurationMs,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                onClick = onMiniPlayerClick,
                onSwipeUp = onMiniPlayerClick,
                onSwipeDismiss = {
                    // Pause playback + hide the mini player.
                    // The mini player reappears when a new song is selected.
                    if (isPlaying) onPlayPauseClick()
                    miniPlayerDismissed = true
                },
                isFullPlayerOpen = showFullPlayer,
                backdrop = glassBackdrop
            )
        }

        // --- Draggable Floating Search Button ---
        DraggableSearchFab(
            onSearchClick = { showSearch = true }
        )

        // ─── FIXED HEADER (Quick Picks page only) ───────────────────
        // A fixed header bar that stays at the top when scrolling.
        // Contains: user icon (left) | "Quick picks" text (center) |
        // settings icon (right). All three are aligned and DON'T scroll.
        // A clean frosted-glass blur sits behind the header with a
        // smooth gradient edge (no hard line).
        //
        // ─── Uniform blur at the top (this build) ──
        // Previous builds had a weak blur at y=0 (behind the status bar)
        // because drawBackdrop's blur kernel samples above y=0 where the
        // graphicsLayer has no content → returns transparent → ~50%
        // strength. The user could see scrolling text ("Speed dial")
        // leaking through unblurred.
        //
        // Fix: on API 31+ (Android 12+), bypass drawBackdrop and use
        // drawLayer(graphicsLayer) to draw the captured screen content,
        // with a BlurEffect applied via the MODIFIER's graphicsLayer
        // block (NOT on the graphicsLayer object — that was #592's bug).
        //
        // BlurEffect's default edge treatment is CLAMP, which uses the
        // edge pixel for out-of-bounds samples → full-strength blur at
        // y=0, matching y=72 (mid). Uniform intensity across the header.
        //
        // The renderEffect on the modifier blurs the Box's COMPOSITED
        // output (the drawLayer content + DstIn mask) with CLAMP. This
        // is the standard, well-supported pattern for applying a blur
        // to a Composable in Compose.
        //
        // On API < 31: fall back to drawBackdrop (has the edge-sampling
        // weakness, but most devices are API 31+).
        if (selectedTab == CoralTab.QuickPicks && !showSearch) {
            val useRenderEffect =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            // 120dp tall, aligned to TopCenter.
            // Visible top    = screen y=0   (behind status bar)
            // Visible bottom = screen y=120 (where the fade ends)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        // Clip to the Box's bounds so drawLayer (which draws
                        // the full screen-sized graphicsLayer) only shows the
                        // top 120dp.
                        clip = true
                        if (useRenderEffect) {
                            // Apply CLAMP blur to the Box's composited output.
                            // 20dp → pixels (matches drawBackdrop's 20dp blur
                            // strength). CLAMP = full strength at y=0.
                            renderEffect = BlurEffect(
                                radiusX = 20.dp.toPx(),
                                radiusY = 20.dp.toPx()
                            )
                        }
                    }
                    .drawWithContent {
                        if (useRenderEffect) {
                            // ─── RenderEffect path (API 31+) ──
                            // Draw the captured screen content (the
                            // graphicsLayer populated by layerBackdrop on the
                            // main content Box). The modifier's renderEffect
                            // (BlurEffect with CLAMP) will blur this with
                            // full strength at y=0 — fixing the weak top.
                            drawLayer(graphicsLayer)
                        } else {
                            // ─── Fallback path (API < 31) ──
                            // drawContent() renders the drawBackdrop child
                            // Box below. Has the edge-sampling weakness at y=0
                            // but is the best we can do without RenderEffect.
                            drawContent()
                        }

                        // DstIn gradient mask — fades the bottom 40% to
                        // transparent for a smooth bottom edge (no hard line).
                        // Applied to BOTH paths so the bottom fade is identical.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.6f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
            ) {
                // Inner Box with drawBackdrop — only rendered in the fallback
                // path (API < 31), when drawContent() is called above.
                // The RenderEffect path doesn't call drawContent(), so this
                // child is never drawn on API 31+.
                if (!useRenderEffect) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { androidx.compose.ui.graphics.RectangleShape },
                                effects = {
                                    vibrancy()
                                    blur(20f.dp.toPx())
                                }
                            )
                    )
                }
            }

            // The header content — ON TOP of the blur, NOT blurred.
            // statusBarsPadding pushes the icons/text below the status
            // bar so they're visible, but the blur behind them extends
            // up behind the status bar (transparent).
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User icon (left)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* TODO: account screen */ }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.CircleUser,
                        contentDescription = "Account",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // "Quick picks" title (center)
                Text(
                    text = "Quick picks",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Settings icon (right)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showSettings = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Cog,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else if (!showSearch) {
            // --- Settings icon for non-QuickPicks pages (top-right) ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp, top = 16.dp)
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettings = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Cog,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ─── BOTTOM BLUR (behind system nav buttons) ──────────────────
        // A small frosted-glass blur at the very bottom of the screen,
        // covering just the system navigation area (gesture pill or
        // 3-button nav). 50dp tall — enough to cover the system nav
        // (24-48dp depending on device) with a seamless upward fade.
        //
        // Same technique as the top header blur (RenderEffect + CLAMP +
        // DstIn mask — see CORAL_BLUR_BLUEPRINT.md), but mirrored:
        //   • Aligned to BottomCenter
        //   • 50dp tall (vs 120dp for the top — no status bar to cover)
        //   • DstIn mask: transparent at top (seamless upward blend with
        //     content) → opaque at bottom (full-strength blur behind nav)
        //
        // CRITICAL: drawLayer(graphicsLayer) draws the captured screen
        // content starting from the Box's origin (0,0). For the TOP blur,
        // the Box is at screen y=0, so it shows the top of the screen.
        // But for the BOTTOM blur, the Box is at y=(screenHeight - 50dp).
        // Without translationY, drawLayer would show the TOP 50dp of the
        // screen (the album cover area), not the bottom 50dp.
        //
        // Fix: translationY = -(screenHeight - boxHeight) shifts the
        // graphicsLayer UP so its bottom edge aligns with the Box's
        // bottom. Now the Box shows the BOTTOM 50dp of the screen content.
        //
        // Does NOT touch the TabCapsule (Coral's nav bar). Default
        // capsule position is Y=0.89, 52dp tall → bottom edge at ~60dp
        // from screen bottom. The 50dp blur leaves a ~10dp gap.
        run {
            val useRenderEffect =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            // Screen height in pixels — needed for the translationY offset.
            val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                configuration.screenHeightDp.dp.toPx()
            }
            val blurHeightDp = 50.dp
            val blurHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                blurHeightDp.toPx()
            }
            // Shift the graphicsLayer up so its bottom aligns with the Box's bottom.
            val translationY = -(screenHeightPx - blurHeightPx)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(blurHeightDp)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        clip = true
                        if (useRenderEffect) {
                            // CLAMP blur — full strength at the very bottom edge.
                            renderEffect = BlurEffect(
                                radiusX = 20.dp.toPx(),
                                radiusY = 20.dp.toPx()
                            )
                        }
                    }
                    .drawWithContent {
                        if (useRenderEffect) {
                            // ─── RenderEffect path (API 31+) ──
                            // drawLayer draws the captured screen content.
                            // The graphicsLayer modifier's translationY (set
                            // below) shifts it up so the BOTTOM of the screen
                            // shows in this Box (not the top).
                            //
                            // We set translationY on the graphicsLayer OBJECT
                            // before drawing — this offsets the draw position
                            // without affecting the renderEffect or clip.
                            graphicsLayer.translationY = translationY
                            drawLayer(graphicsLayer)
                        } else {
                            drawContent()
                        }

                        // DstIn mask — seamless upward blend:
                        //   0.0 → Transparent (top edge, blends into content above)
                        //   0.6 → Black (fully opaque by 60% down)
                        //   1.0 → Black (bottom edge, full-strength blur)
                        // The top 60% fades from transparent to opaque, giving
                        // a buttery smooth upward blend. The bottom 40% is
                        // fully opaque (covers the system nav area).
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.6f to Color.Black,
                                    1.0f to Color.Black
                                ),
                                startY = 0f,
                                endY = size.height
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
            ) {
                if (!useRenderEffect) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { androidx.compose.ui.graphics.RectangleShape },
                                effects = {
                                    vibrancy()
                                    blur(20f.dp.toPx())
                                }
                            )
                    )
                }
            }
        }

        // --- Draggable Tab Capsule (nav bar — long-press to drag anywhere) ---
        // Same pattern as the search FAB: hold for 3 seconds → enter drag mode
        // → drag anywhere on screen → release to pin. Position persists.
        DraggableTabCapsule(
            tabs = CoralTab.values().toList(),
            activeTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                selectedPlaylist = null
            },
            backdrop = glassBackdrop
        )

        // Add bottom padding to the content area when mini player is visible,
        // so the song list doesn't hide behind the mini player. We do this by
        // adding a spacer that grows/shrinks with the mini player visibility.
        // NOTE: This is a no-op overlay that we leave here as a marker for
        // future polish — the screens themselves already scroll, so they'll
        // just clip the last few items. Phase 8.1 will add proper bottom
        // inset handling.

        // Song picker modal (slides up over the playlist detail)
        if (showSongPicker && playlistForPicker != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.slideOutVertically { it },
                modifier = Modifier.fillMaxSize()
            ) {
                SongPickerScreen(
                    playlist = playlistForPicker!!,
                    allSongs = songs,
                    onDone = {
                        showSongPicker = false
                        playlistForPicker = null
                    }
                )
            }
        }

        // Premium info screen
        if (showPremium) {
            com.rajatxo.coral.ui.premium.PremiumScreen(
                onBackClick = { showPremium = false }
            )
        }

        // Equalizer screen
        if (showEqualizer) {
            // Load mock bands so the UI is fully visible. Phase 7B will
            // replace this with real attachment via CoralPlaybackService.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (equalizerController.bands.value.isEmpty()) {
                    equalizerController.loadDefaultBands()
                }
            }
            com.rajatxo.coral.ui.equalizer.EqualizerScreen(
                controller = equalizerController,
                onBackClick = { showEqualizer = false }
            )
        }

        // Sleep timer sheet (bottom sheet — for now just a full-screen overlay)
        if (showSleepTimer) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null,
                onClick = { showSleepTimer = false }
            )) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    com.rajatxo.coral.ui.sleeptimer.SleepTimerSheet(
                        onDismiss = { showSleepTimer = false }
                    )
                }
            }
        }

        // Font picker screen
        if (showFontPicker) {
            com.rajatxo.coral.ui.screens.FontPickerScreen(
                onBackClick = { showFontPicker = false }
            )
        }

        // --- Search screen (full-screen overlay, opened by search FAB) ---
        if (showSearch) {
            com.rajatxo.coral.ui.screens.SearchScreen(
                songs = songs,
                onSongClick = { song ->
                    showSearch = false
                    onSongClickWithReset(song)
                },
                onDismiss = { showSearch = false }
            )
        }

        // --- Settings screen (full-screen overlay, opened by gear button) ---
        if (showSettings) {
            com.rajatxo.coral.ui.screens.SettingsScreen(
                onBackClick = { showSettings = false },
                onOpenPremium = {
                    showSettings = false
                    showPremium = true
                },
                onOpenEqualizer = {
                    showSettings = false
                    showEqualizer = true
                },
                onOpenSleepTimer = {
                    showSettings = false
                    showSleepTimer = true
                },
                onOpenFontPicker = {
                    showSettings = false
                    showFontPicker = true
                }
            )
        }

        // Full-screen now-playing screen
        // Conditionally renders CoralPlayer (immersive blurred-bg style)
        // or FullPlayer (dating-app profile style) based on the user's
        // Player Design Style preference in Settings → Appearance.
        val playerStyle by com.rajatxo.coral.data.prefs.PlayerStyleManager.playerStyle.collectAsState()
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.CORAL) {
                com.rajatxo.coral.ui.player.CoralPlayer(
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
                    onDismiss = onFullPlayerDismiss,
                    onAddToPlaylist = { songId ->
                        songToAddToPlaylist = songId
                    }
                )
            } else if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.SPIRAL) {
                com.rajatxo.coral.ui.player.SpiralPlayer(
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
                    onDismiss = onFullPlayerDismiss,
                    onAddToPlaylist = { songId ->
                        songToAddToPlaylist = songId
                    }
                )
            } else if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.SPIRAL_2) {
                com.rajatxo.coral.ui.player.Spiral2Player(
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
                    onDismiss = onFullPlayerDismiss,
                    onAddToPlaylist = { songId ->
                        songToAddToPlaylist = songId
                    }
                )
            } else if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.SPIRAL_3) {
                com.rajatxo.coral.ui.player.Spiral3Player(
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
                    onDismiss = onFullPlayerDismiss,
                    onAddToPlaylist = { songId ->
                        songToAddToPlaylist = songId
                    }
                )
            } else {
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
                    onDismiss = onFullPlayerDismiss,
                    onAddToPlaylist = { songId ->
                        songToAddToPlaylist = songId
                    }
                )
            }
        }

        // Full-screen playlist detail overlay (covers nav rail + everything)
        AnimatedVisibility(
            visible = selectedPlaylist != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            selectedPlaylist?.let { playlist ->
                PlaylistDetailScreen(
                    playlist = playlist,
                    allSongs = songs,
                    currentSongTitle = currentSongTitle,
                    onBackClick = { selectedPlaylist = null },
                    onPlayAll = { songList -> onSongClickWithQueueReset(songList.first(), songList) },
                    onShuffle = { songList ->
                        val shuffled = songList.shuffled()
                        if (shuffled.isNotEmpty()) onSongClickWithQueueReset(shuffled.first(), shuffled)
                    },
                    onSongClick = { song, songList -> onSongClickWithQueueReset(song, songList) },
                    onAddSongsClick = {
                        playlistForPicker = playlist
                        showSongPicker = true
                    },
                    onDeletePlaylist = { selectedPlaylist = null }
                )
            }
        }

        // --- Add to playlist dialog (from FullPlayer 3-dot menu) ---
        if (songToAddToPlaylist != null) {
            val playlistsState by com.rajatxo.coral.data.store.PlaylistStore.playlists.collectAsState()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                containerColor = com.rajatxo.coral.ui.components.CoralColors.SurfaceVariant,
                titleContentColor = Color.White,
                title = { Text("Add to playlist") },
                text = {
                    if (playlistsState.isEmpty()) {
                        Text("No playlists yet. Create one first.", color = Color(0xFF888888), fontSize = 14.sp)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(playlistsState) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            PlaylistStore.addSongToPlaylist(pl.id, songToAddToPlaylist!!)
                                            songToAddToPlaylist = null
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = com.rajatxo.coral.ui.icons.CoralIcons.ListMusic,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = pl.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { songToAddToPlaylist = null }) {
                        Text("Cancel", color = Color(0xFF888888))
                    }
                }
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    albumArtUri: android.net.Uri?,
    songId: Long?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit = {},
    onSwipeDismiss: () -> Unit = {},
    isFullPlayerOpen: Boolean = false,
    backdrop: LayerBackdrop? = null
) {
    // Notched mini player — pill with a U-shaped concave notch at the
    // bottom-center, and the search capsule nested inside that notch.
    //
    // Layout (top to bottom):
    //   [Album art + progress ring] [Title + artist] [Heart]
    //   [U-notch with search capsule nested inside]
    //
    // The NotchedPillShape clips the entire body so the bottom edge has
    // the U-curve cutout. The search capsule is positioned at the bottom,
    // overlapping the notch — top half recessed, bottom half hanging below.
    //
    // BACKGROUND: Real frosted-glass blur via the same kyant/backdrop
    // library the nav bar uses (drawBackdrop + AGSL blur). The mini player
    // now samples whatever is behind it on the home screen — song list,
    // album art, etc. — and blurs it in real time. Replaces the old
    // 'blurred album cover + dark tint' background. Shape is unchanged
    // (still a 32dp rounded pill, same border).
    //
    // GESTURES (Yuma-inspired, original implementation — not copied):
    //   • Tap → opens FullPlayer (existing behavior, kept)
    //   • Swipe UP → opens FullPlayer (same as tap, but gesture-based)
    //   • Swipe LEFT/RIGHT → dismisses the mini player (slides out + fades)
    //     and pauses playback. The mini player reappears when a new song
    //     is selected.
    //
    // The gesture uses a single detectDragGestures pointerInput. On drag
    // start, no direction is locked. Once the drag exceeds 20px in either
    // axis, the direction locks (horizontal or vertical). This prevents
    // diagonal drags from triggering both actions.

    val favorites by com.rajatxo.coral.data.store.PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds

    val pillShape: Shape = RoundedCornerShape(32.dp)

    // ─── Gesture state ──────────────────────────────────────────────
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    // Max distance the mini player can be dragged upward (in pixels).
    // 60dp — keeps the gesture short (no dragging all the way to the top).
    // The mini player fades out as it approaches this cap, so by 60dp up
    // it's fully dissolved — the full player then opens.
    val maxSwipeUpPx = with(density) { 60.dp.toPx() }

    // ─── Reset offsets when the full player closes ──────────────────
    // When the user swipes up to open the full player, the mini player
    // fades away (offsetY animates up + alpha fades). We DON'T reset
    // offsetY to 0 immediately — that would cause a "splash" flash
    // (mini player snapping back to position before the full player
    // covers it).
    //
    // Instead, we leave offsetY at the faded position while the full
    // player is open (the mini player is invisible anyway — it's under
    // the full player). When the full player CLOSES (isFullPlayerOpen
    // goes true → false), this LaunchedEffect fires and resets offsetY
    // to 0 so the mini player reappears at its normal position.
    androidx.compose.runtime.LaunchedEffect(isFullPlayerOpen) {
        if (!isFullPlayerOpen) {
            scope.launch {
                offsetY.snapTo(0f)
                offsetX.snapTo(0f)
                scale.snapTo(1f)
            }
        }
    }

    // Drag direction lock — once the drag exceeds the threshold, we
    // commit to either HORIZONTAL (dismiss) or VERTICAL (expand).
    var dragDirection: Int? by remember { mutableStateOf(null) }  // 0=H, 1=V
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .navigationBarsPadding()
            .graphicsLayer {
                translationX = offsetX.value
                // Cap upward drag — mini player only moves up to 60dp
                // worth of pixels. Prevents dragging it all the way to the
                // top of the screen. Beyond that, only the fade continues.
                translationY = offsetY.value.coerceAtLeast(-maxSwipeUpPx)
                val s = scale.value
                scaleX = s
                scaleY = s
                // Fade out as the mini player slides off-screen horizontally.
                alpha = (1f - abs(offsetX.value) / screenWidthPx).coerceIn(0f, 1f) *
                    // For vertical: fade out as the mini player moves up,
                    // proportional to drag distance. By 60dp up → fully faded.
                    (1f - (abs(offsetY.value) / maxSwipeUpPx)).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragDirection = null
                        totalDragX = 0f
                        totalDragY = 0f
                        scope.launch { scale.snapTo(0.96f) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        // Lock direction once the drag exceeds 20px
                        if (dragDirection == null) {
                            if (abs(totalDragX) > 20f || abs(totalDragY) > 20f) {
                                dragDirection = if (abs(totalDragX) > abs(totalDragY)) 0 else 1
                            }
                        }

                        when (dragDirection) {
                            0 -> {  // HORIZONTAL — move the mini player with the finger
                                scope.launch { offsetX.snapTo(totalDragX) }
                            }
                            1 -> {  // VERTICAL — only follow upward drags (swipe up)
                                if (totalDragY < 0) {
                                    // Dampened follow — mini player moves at 0.4x the drag speed
                                    // (was 0.5x — slower, so the 60dp cap is reached with a
                                    // more deliberate gesture). Combined with the alpha fade,
                                    // this gives a smooth "lift up and dissolve" feel.
                                    scope.launch { offsetY.snapTo(totalDragY * 0.4f) }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        when (dragDirection) {
                            0 -> {  // HORIZONTAL
                                if (abs(totalDragX) > screenWidthPx * 0.4f) {
                                    // Past the 40% threshold → dismiss
                                    val target = if (totalDragX < 0) -screenWidthPx else screenWidthPx
                                    scope.launch {
                                        offsetX.animateTo(target, tween(200))
                                        onSwipeDismiss()
                                        delay(100)
                                        offsetX.snapTo(0f)
                                    }
                                } else {
                                    // Not past threshold → spring back
                                    scope.launch {
                                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                            1 -> {  // VERTICAL
                                // If swiped up at least 30px → open FullPlayer.
                                // Lower threshold than before (was 100px) — since the
                                // visual is now a short lift + fade (not a long drag),
                                // a smaller swipe should trigger it.
                                if (totalDragY < -30f) {
                                    // Animate the mini player fading away smoothly,
                                    // then open the full player. The 200ms tween gives
                                    // a buttery dissolve before the player opens.
                                    //
                                    // NOTE: Do NOT reset offsetY to 0 here. The mini
                                    // player stays translated up (invisible) while
                                    // the full player is open. When the full player
                                    // closes, the LaunchedEffect(isFullPlayerOpen)
                                    // above resets offsetY to 0 so the mini player
                                    // reappears at its normal position.
                                    //
                                    // Previously, offsetY.snapTo(0f) here caused a
                                    // "splash" flash — the mini player snapped back
                                    // to position before the full player had fully
                                    // covered the screen.
                                    scope.launch {
                                        offsetY.animateTo(-maxSwipeUpPx * 2f, tween(200))
                                        onSwipeUp()
                                        // Don't reset offsetY — the LaunchedEffect
                                        // handles it when the full player closes.
                                    }
                                } else {
                                    // Not enough swipe → spring back
                                    scope.launch {
                                        offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                )
            }
    ) {
        // --- Main player body (standard pill) ---
        // Frosted-glass background: same drawBackdrop mechanism as the nav
        // bar. Samples the home-screen content behind the mini player and
        // applies AGSL-based blur + vibrancy + subtle dark tint for
        // readability. Falls back to a flat dark background if the backdrop
        // isn't available (shouldn't happen in practice — HomeScreen always
        // provides one).
        //
        // WIDTH: 240dp — matches the TabCapsule width exactly. Both are
        // centered horizontally (via Alignment.BottomCenter on the parent
        // AnimatedVisibility), so their rounded pill ends align perfectly.
        // Was fillMaxWidth + 12dp horizontal padding (way wider than the
        // TabCapsule below it — the ends didn't line up).
        val bodyModifier = if (backdrop != null) {
            Modifier
                .width(240.dp)
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
                        blur(18f.dp.toPx())  // AGSL real-time backdrop blur
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.35f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), pillShape)
                .clickable(onClick = onClick)
        } else {
            Modifier
                .width(240.dp)
                .height(64.dp)
                .clip(pillShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), pillShape)
                .clickable(onClick = onClick)
        }
        Box(
            modifier = bodyModifier
        ) {
            // Content row (album art + title + heart)
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
                // Both use CalSans (the same display font used elsewhere in
                // Coral for headings) — gives the mini player a distinctive
                // typographic identity, matching the rest of the app's
                // display type. Was the system default FontFamily before.
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily,
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
    }
}

// =============================================================================
// Draggable Floating Search Button
// =============================================================================
// Solid white rounded-square FAB with black search icon.
//
// GESTURES:
//  - Tap → opens search (TODO)
//  - Long-press (3 seconds) → enters drag mode (button scales up slightly
//    as visual feedback that it's now draggable)
//  - Drag → moves the button anywhere on the right half of the screen
//  - Release → button stays at the dropped position
//
// PERSISTENCE:
//  - Position saved as screen-size fractions (0.0-1.0) to SharedPreferences
//  - Restored on app restart via SearchFabPosition
//  - Works across all screen sizes (fractions scale correctly)
//
// CONSTRAINTS:
//  - X (horizontal): constrained to right half (0.5 - 0.97) so it doesn't
//    overlap the nav rail
//  - Y (vertical): constrained to 0.05 - 0.95 so it stays on-screen
// =============================================================================

@Composable
private fun DraggableSearchFab(
    onSearchClick: () -> Unit = {}
) {
    val savedPosition by com.rajatxo.coral.data.prefs.SearchFabPosition.position.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var screenSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentYpx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActivated by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }

    // --- Countdown speech bubble state ---
    var showBubble by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val countdownScope = rememberCoroutineScope()

    // Pop-up animation for the bubble (scale from 0 → 1, bouncy spring)
    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "bubbleScale"
    )
    // Fade animation (alpha 0 → 1 on show, 1 → 0 on hide)
    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "bubbleAlpha"
    )

    // FAB scale for drag-mode feedback
    val fabScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "fabScale"
    )

    val fabSize = 56.dp
    val fabSizePx = with(density) { fabSize.toPx() }
    val (savedX, _) = savedPosition
    val fixedXpx = if (screenSize.width > 0) savedX * screenSize.width else 0f

    androidx.compose.runtime.LaunchedEffect(savedPosition, screenSize) {
        if (screenSize.height > 0) {
            currentYpx = savedPosition.second * screenSize.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {

            // --- Comic-style speech bubble (LEFT of the FAB) ---
            // Shows "Hold to move in [3]" with a pointed tail → toward the FAB
            if (bubbleAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (fixedXpx - fabSizePx / 2f - with(density) { 210.dp.toPx() }).toInt(),
                                (currentYpx - with(density) { 24.dp.toPx() }).toInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            alpha = bubbleAlpha
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main pill bubble
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hold to move in",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Small inner capsule with the countdown number
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countdownNumber.toString(),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    // Pointed tail (triangle pointing right → toward the FAB)
                    Canvas(
                        modifier = Modifier
                            .size(width = 10.dp, height = 14.dp)
                            .graphicsLayer { alpha = bubbleAlpha }
                    ) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFF1A1A1A))
                    }
                }
            }

            // --- The FAB itself ---
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (fixedXpx - fabSizePx / 2f).toInt(),
                            (currentYpx - fabSizePx / 2f).toInt()
                                .coerceIn(0, (screenSize.height - fabSizePx).toInt())
                        )
                    }
                    .size(fabSize)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                pressStartTime = System.currentTimeMillis()
                                isLongPressActivated = false

                                // Show the bubble + start countdown
                                // First: hold for 2 seconds (no bubble visible)
                                // Then: bubble pops up showing 3→2→1 (3 seconds)
                                // Then: drag mode activated
                                countdownJob?.cancel()
                                countdownJob = countdownScope.launch {
                                    // Phase 1: hold for 2 seconds (no UI feedback)
                                    delay(2000L)

                                    // Phase 2: pop up the bubble with countdown
                                    showBubble = true
                                    countdownNumber = 3
                                    delay(1000L)

                                    countdownNumber = 2
                                    delay(1000L)

                                    countdownNumber = 1
                                    delay(1000L)

                                    // Phase 3: countdown done — hide bubble, enter drag mode
                                    showBubble = false
                                    isLongPressActivated = true
                                    isDragging = true
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // Finger lifted
                                        if (isLongPressActivated) {
                                            val newYFraction = (currentYpx / screenSize.height)
                                                .coerceIn(0.05f, 0.95f)
                                            com.rajatxo.coral.data.prefs.SearchFabPosition.setPosition(savedX, newYFraction)
                                        } else {
                                            // Short tap (before 2-second hold) → open search
                                            onSearchClick()
                                        }
                                        isDragging = false
                                        isLongPressActivated = false
                                        showBubble = false
                                        countdownJob?.cancel()
                                        break
                                    }

                                    if (isDragging) {
                                        val fabTopY = currentYpx - fabSizePx / 2f
                                        val newScreenY = fabTopY + change.position.y
                                        currentYpx = newScreenY.coerceIn(
                                            fabSizePx / 2f,
                                            screenSize.height - fabSizePx / 2f
                                        )
                                        change.consume()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Search,
                    contentDescription = "Search",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =============================================================================
// DraggableShuffleFab
// =============================================================================
// Same drag pattern as DraggableSearchFab:
//   1. Hold for 3 seconds → countdown bubble (3→2→1) → enter drag mode
//   2. In drag mode: FAB follows finger anywhere on screen
//   3. Release → pin to new position (persists via ShuffleFabPosition)
//
// The shuffle FAB plays a random song from the library on tap.
// Default position: right side, ABOVE the search FAB (Y=0.55 vs 0.75).
// =============================================================================

@Composable
private fun DraggableShuffleFab(
    onShuffle: () -> Unit
) {
    val savedPosition by com.rajatxo.coral.data.prefs.ShuffleFabPosition.position.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var screenSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentYpx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActivated by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }

    // --- Countdown speech bubble state ---
    var showBubble by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val countdownScope = rememberCoroutineScope()

    // Pop-up animation for the bubble
    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "shuffleBubbleScale"
    )
    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "shuffleBubbleAlpha"
    )

    // FAB scale for drag-mode feedback
    val fabScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "shuffleFabScale"
    )

    val fabSize = 56.dp
    val fabSizePx = with(density) { fabSize.toPx() }
    val (savedX, _) = savedPosition
    val fixedXpx = if (screenSize.width > 0) savedX * screenSize.width else 0f

    androidx.compose.runtime.LaunchedEffect(savedPosition, screenSize) {
        if (screenSize.height > 0) {
            currentYpx = savedPosition.second * screenSize.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {

            // --- Countdown speech bubble (LEFT of the FAB) ---
            if (bubbleAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (fixedXpx - fabSizePx / 2f - with(density) { 210.dp.toPx() }).toInt(),
                                (currentYpx - with(density) { 24.dp.toPx() }).toInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            alpha = bubbleAlpha
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1F1F1F))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (countdownNumber > 0) "Hold to move in $countdownNumber"
                                   else "Release to pin",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = CalSansFamily
                        )
                    }
                    // Tail (pointed right → toward the FAB)
                    Box(
                        modifier = Modifier
                            .size(0.dp)
                            .graphicsLayer {
                                translationX = -6f * density.density
                            }
                    )
                }
            }

            // --- The FAB itself ---
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (fixedXpx - fabSizePx / 2f).toInt(),
                            (currentYpx - fabSizePx / 2f).toInt()
                        )
                    }
                    .size(fabSize)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressStartTime = System.currentTimeMillis()
                                isLongPressActivated = false
                                showBubble = true
                                countdownNumber = 3
                                countdownJob?.cancel()
                                countdownJob = countdownScope.launch {
                                    for (i in 3 downTo 1) {
                                        countdownNumber = i
                                        delay(1000)
                                    }
                                    countdownNumber = 0
                                    isLongPressActivated = true
                                    showBubble = false
                                }
                                tryAwaitRelease()
                                countdownJob?.cancel()
                                showBubble = false
                                if (!isLongPressActivated) {
                                    // Short tap → shuffle
                                    onShuffle()
                                }
                            }
                        )
                    }
                    .pointerInput(isLongPressActivated) {
                        if (isLongPressActivated) {
                            detectDragGestures(
                                onDragEnd = {
                                    isDragging = false
                                    isLongPressActivated = false
                                    // Save position
                                    if (screenSize.height > 0) {
                                        val yFraction = currentYpx / screenSize.height
                                        com.rajatxo.coral.data.prefs.ShuffleFabPosition.setPosition(
                                            savedX, yFraction
                                        )
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    isLongPressActivated = false
                                },
                                onDrag = { _, dragAmount ->
                                    isDragging = true
                                    currentYpx = (currentYpx + dragAmount.y)
                                        .coerceIn(fabSizePx / 2f, screenSize.height - fabSizePx / 2f)
                                }
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(
                        if (isDragging) Color(0xFFFF6B6B)
                        else Color.White.copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        if (isDragging) Color.White else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.ShuffleLucide,
                    contentDescription = "Shuffle",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =============================================================================
// DraggableTabCapsule
// =============================================================================
// Same drag pattern as DraggableSearchFab:
//   1. Hold for 3 seconds → countdown bubble (3→2→1) → enter drag mode
//   2. In drag mode: capsule follows finger anywhere on screen
//   3. Release → capsule pins at current position, saved to SharedPreferences
//   4. Position restored on app restart
//
// When NOT in drag mode: capsule handles horizontal swipes for tab switching
// (the TabCapsule's own pointerInput handles that).
//
// The capsule can be placed ANYWHERE on screen — left, right, top, bottom.
// Both X and Y are saved as fractions of screen size.
// =============================================================================

@Composable
private fun DraggableTabCapsule(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?
) {
    val savedPosition by com.rajatxo.coral.data.prefs.TabCapsulePosition.position.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    var screenSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentXpx by remember { mutableStateOf(0f) }
    var currentYpx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActivated by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // --- Grid overlay state (scientist graph paper, for alignment) ---
    var showGrid by remember { mutableStateOf(false) }
    val gridAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showGrid) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "gridAlpha"
    )

    // --- Last touch position (for delta-based smooth dragging) ---
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }

    var showBubble by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }

    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "bubbleScale"
    )
    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "bubbleAlpha"
    )

    val capsuleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "capsuleScale"
    )

    val capsuleWidth = with(density) { 240.dp.toPx() }
    val capsuleHeight = with(density) { 52.dp.toPx() }

    androidx.compose.runtime.LaunchedEffect(savedPosition, screenSize) {
        if (screenSize.width > 0 && screenSize.height > 0) {
            currentXpx = savedPosition.first * screenSize.width
            currentYpx = savedPosition.second * screenSize.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {

            // --- Scientist grid overlay (fades in during drag mode) ---
            // Graph-paper style grid for precise alignment. Fades in when
            // drag mode starts, fades out when capsule is placed.
            if (gridAlpha > 0.01f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = gridAlpha }
                ) {
                    val gridSpacing = 40f  // px between grid lines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    val majorColor = Color.White.copy(alpha = 0.15f)
                    val majorEvery = 4  // every 4th line is brighter

                    // Vertical lines
                    var x = 0f
                    var i = 0
                    while (x <= size.width) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        x += gridSpacing
                        i++
                    }

                    // Horizontal lines
                    var y = 0f
                    i = 0
                    while (y <= size.height) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        y += gridSpacing
                        i++
                    }

                    // Center crosshair (brighter, for alignment reference)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx - 30f, cy),
                        end = Offset(cx + 30f, cy),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx, cy - 30f),
                        end = Offset(cx, cy + 30f),
                        strokeWidth = 2f
                    )
                }
            }

            // --- Countdown speech bubble (above the capsule) ---
            if (bubbleAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (currentXpx - with(density) { 60.dp.toPx() }).toInt(),
                                (currentYpx - capsuleHeight - with(density) { 50.dp.toPx() }).toInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            alpha = bubbleAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hold to move in",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countdownNumber.toString(),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // --- The capsule (positioned via offset, draggable) ---
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (currentXpx - capsuleWidth / 2f).toInt()
                                .coerceIn(0, (screenSize.width - capsuleWidth).toInt()),
                            (currentYpx - capsuleHeight / 2f).toInt()
                                .coerceIn(0, (screenSize.height - capsuleHeight).toInt())
                        )
                    }
                    .graphicsLayer {
                        scaleX = capsuleScale
                        scaleY = capsuleScale
                    }
                    .pointerInput(tabs, activeTab) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                isLongPressActivated = false
                                val initialX = down.position.x
                                val initialY = down.position.y
                                lastTouchX = down.position.x
                                lastTouchY = down.position.y

                                countdownJob?.cancel()
                                countdownJob = scope.launch {
                                    delay(2000L)
                                    showBubble = true
                                    countdownNumber = 3
                                    delay(1000L)
                                    countdownNumber = 2
                                    delay(1000L)
                                    countdownNumber = 1
                                    delay(1000L)
                                    showBubble = false
                                    isLongPressActivated = true
                                    isDragging = true
                                    // Show scientist grid when drag mode starts
                                    showGrid = true
                                    // Record current touch position as baseline for delta tracking
                                    lastTouchX = down.position.x
                                    lastTouchY = down.position.y
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        if (isLongPressActivated) {
                                            val newXFraction = (currentXpx / screenSize.width)
                                                .coerceIn(0.05f, 0.95f)
                                            val newYFraction = (currentYpx / screenSize.height)
                                                .coerceIn(0.05f, 0.95f)
                                            com.rajatxo.coral.data.prefs.TabCapsulePosition
                                                .setPosition(newXFraction, newYFraction)
                                        }
                                        isDragging = false
                                        isLongPressActivated = false
                                        showBubble = false
                                        showGrid = false  // hide grid when released
                                        countdownJob?.cancel()
                                        break
                                    }

                                    // Cancel countdown if finger moved (swipe, not hold)
                                    if (!isDragging && !isLongPressActivated) {
                                        val movedX = kotlin.math.abs(change.position.x - initialX)
                                        val movedY = kotlin.math.abs(change.position.y - initialY)
                                        if (movedX > 20f || movedY > 20f) {
                                            countdownJob?.cancel()
                                            showBubble = false
                                        }
                                    }

                                    // SMOOTH DRAGGING via delta tracking:
                                    // Calculate how much the finger moved SINCE LAST FRAME,
                                    // then move the capsule by the same delta. This prevents
                                    // the jump on drag start (because the first delta is ~0).
                                    if (isDragging) {
                                        val deltaX = change.position.x - lastTouchX
                                        val deltaY = change.position.y - lastTouchY
                                        currentXpx = (currentXpx + deltaX)
                                            .coerceIn(capsuleWidth / 2f, screenSize.width - capsuleWidth / 2f)
                                        currentYpx = (currentYpx + deltaY)
                                            .coerceIn(capsuleHeight / 2f, screenSize.height - capsuleHeight / 2f)
                                        lastTouchX = change.position.x
                                        lastTouchY = change.position.y
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                com.rajatxo.coral.ui.components.TabCapsule(
                    tabs = tabs,
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    backdrop = backdrop,
                    modifier = Modifier
                )
            }
        }
    }
}

// =============================================================================
// TopFadeBlur — blended-edge blur at the top of every page
// =============================================================================

@Composable
private fun TopFadeBlur(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    modifier: Modifier = Modifier
) {
    if (backdrop == null) return

    val totalHeight = 128.dp
    val rectShape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape

    // Clean frosted glass blur — NO dark tint, NO smokey overlay.
    // The blur samples whatever is behind it and shows the colors
    // through, just softened. A very light white tint (5%) gives the
    // "frosted" quality without darkening. The DstIn gradient fades
    // the blur smoothly to zero — no hard edge.
    Box(
        modifier = modifier
            .height(totalHeight)
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // DstIn mask: opaque at top → transparent at bottom.
                // Smooth fade — NO hard edge.
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black,
                            0.35f to Color.Black,
                            0.7f to Color.Black.copy(alpha = 0.5f),
                            1.0f to Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    ) {
        // The blurred backdrop — pure clean blur, NO tint at all.
        // The onDrawSurface is removed entirely to prevent the dark
        // hard block that appeared when the backdrop couldn't sample
        // content (e.g. during tab switches or empty states).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { rectShape },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = 0f,
                            contrast = 1f,
                            saturation = 1.1f
                        )
                        blur(20f.dp.toPx())
                    }
                )
        )
    }
}

