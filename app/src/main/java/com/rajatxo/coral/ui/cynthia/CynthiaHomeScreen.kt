package com.rajatxo.coral.ui.cynthia

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.components.TabCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.theme.CalSansFamily

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun CynthiaHomeScreen(
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
    val cynthiaTabs = listOf(CoralTab.QuickPicks, CoralTab.Songs, CoralTab.Playlists)
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }
    var showSettings by remember { mutableStateOf(false) }
    // ★ Customization panel state — shown when user holds nav bar or search
    //   FAB for 5 seconds. `customizationPanelIsNavBar` = true → nav bar held,
    //   false → search FAB held. Controls which section the panel shows.
    var showCustomizationPanel by remember { mutableStateOf(false) }
    var customizationPanelIsNavBar by remember { mutableStateOf(true) }
    // ★ Profile panel state
    var showProfilePanel by remember { mutableStateOf(false) }
    // ★ Search screen state — Cynthia has its own billing-style search screen
    //   (separate from Astra's SearchScreen). Tapping the search FAB shows it.
    var showSearch by remember { mutableStateOf(false) }
    // ★ TodaysTopCard state — when user taps a Today's Top cover in
    //   QuickPicksScreen, the tapped song is stashed here and the
    //   floating miniplayer-style glass capsule is shown as a sibling
    //   of the layerBackdrop.
    var showTopCard by remember { mutableStateOf(false) }
    var topCardSong by remember { mutableStateOf<Song?>(null) }
    // ★ TopCard customization panel — opens when user taps the menu (•••)
    //   icon inside the TodaysTopCard. Same square glass panel as the
    //   nav bar's, but in TOP_CARD mode.
    var showTopCardCustomization by remember { mutableStateOf(false) }
    // ★ SpeedDialCard — floating glass capsule that lets the user swipe to
    //   change Speed Dial mode (Random / Last Played). Same shape + size +
    //   position as TodaysTopCard. Rendered outside layerBackdrop (same
    //   pattern). Opened by tapping the "Speed dial" text or the chevron
    //   beside it in QuickPicksScreen.
    var showSpeedDialCard by remember { mutableStateOf(false) }
    // ★ MiniPlayer customization panel — opens when user HOLDS the miniplayer.
    //   Same square glass panel as the nav bar's, but in MINI_PLAYER mode.
    var showMiniPlayerCustomization by remember { mutableStateOf(false) }
    // ★ MiniPlayer dismissed state — set to true when user swipes down on
    //   the miniplayer. Resets to false when a new song starts playing.
    var miniPlayerDismissed by remember { mutableStateOf(false) }
    // Reset dismissed state when the current song changes
    androidx.compose.runtime.LaunchedEffect(currentSongId) {
        if (currentSongId != null) miniPlayerDismissed = false
    }
    val onSongClickWithReset: (Song) -> Unit = { song -> onSongClick(song) }

    // ★ Record playback history + daily play count when the current song
    //   changes. This is the SAME tracking that Astra's HomeScreen has —
    //   without this, songs played in Cynthia don't show up in "Today's Top".
    androidx.compose.runtime.LaunchedEffect(currentSongId, currentSongArtist) {
        if (currentSongId != null && !currentSongArtist.isNullOrBlank()) {
            com.rajatxo.coral.data.prefs.PlaybackHistory.recordPlayback(
                songId = currentSongId,
                artist = currentSongArtist
            )
            com.rajatxo.coral.data.prefs.PlaybackHistory.recordDailyPlay(
                songId = currentSongId,
                artist = currentSongArtist
            )
        }
    }

    // ★ Track playback DURATION for "Today's Top" — accurate time tracking.
    //   Uses System.currentTimeMillis() to calculate actual elapsed time.
    var lastPollTime by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(mediaController, isPlaying, currentSongId) {
        lastPollTime = System.currentTimeMillis()
        while (true) {
            try {
                if (isPlaying && currentSongId != null && !currentSongArtist.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - lastPollTime
                    lastPollTime = now
                    if (elapsedMs in 100..2000L) {
                        val elapsedSeconds = (elapsedMs / 1000L).toInt()
                        if (elapsedSeconds > 0) {
                            com.rajatxo.coral.data.prefs.PlaybackHistory.addPlayDuration(
                                songId = currentSongId,
                                artist = currentSongArtist,
                                secondsToAdd = elapsedSeconds
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
            kotlinx.coroutines.delay(if (isPlaying) 1000L else 2000L)
        }
    }

    // ─── Glass backdrop (EXACT same as Astra) ───
    val graphicsLayer = rememberGraphicsLayer()
    val glassBackdrop = rememberLayerBackdrop(
        graphicsLayer = graphicsLayer
    ) {
        drawContent()
    }

    // ─── Dynamic header title (same as Astra) ───
    val headerTitle = when (selectedTab) {
        CoralTab.QuickPicks -> "Quick picks"
        CoralTab.Songs -> "Songs"
        CoralTab.Playlists -> "Playlists"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ═══ INNER BOX — layerBackdrop captures page content ═══
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop)
        ) {
            when (selectedTab) {
                CoralTab.QuickPicks -> com.rajatxo.coral.ui.screens.QuickPicksScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongArt = currentSongArt,
                    onSongClick = onSongClickWithReset,
                    isPlaying = isPlaying,
                    onPlayPauseClick = onPlayPauseClick,
                    onCapsuleClick = { tappedSong ->
                        topCardSong = tappedSong
                        showTopCard = true
                    },
                    onSpeedDialHeaderClick = { showSpeedDialCard = true }
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedTab.label,
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }  // ← layerBackdrop Box ENDS — everything below is a SIBLING

        // ═══ TOP FADE BLUR — EXACT copy from Astra (lines 546-700) ═══
        if (true) {  // always show on all tabs (Astra checks !showSearch)
            val useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            // 120dp tall blur box, aligned to TopCenter
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = true
                        if (useRenderEffect) {
                            renderEffect = BlurEffect(
                                radiusX = 20.dp.toPx(),
                                radiusY = 20.dp.toPx()
                            )
                        }
                    }
                    .drawWithContent {
                        if (useRenderEffect) {
                            // RenderEffect path (API 31+): draw captured content
                            drawLayer(graphicsLayer)
                        } else {
                            // Fallback path (API < 31): draw drawBackdrop child
                            drawContent()
                        }
                        // DstIn gradient mask — fades bottom to transparent
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
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                // Inner Box with drawBackdrop — only rendered on API < 31
                if (!useRenderEffect) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { RectangleShape },
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

            // ═══ HEADER ROW — profile icon + title + settings cog ═══
            // EXACT same as Astra (lines 640-700)
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
                            onClick = { showProfilePanel = true }
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

                // Title (center)
                Text(
                    text = headerTitle,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Settings cog (right) — NO drawBackdrop, just plain icon like Astra
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
        }

        // ═══ DRAGGABLE NAV BAR + SEARCH CIRCLE — siblings of layerBackdrop ═══
        //
        // ★ USER-SPECIFIED LAYOUT (Cynthia only, completely separate from Astra):
        //   - Nav bar default: X = 0.283, Y = 0.889 (LEFT side, near bottom)
        //   - Search FAB default: X = 0.565, Y = 0.889 (to the RIGHT of nav bar
        //     with a 10dp gap, on the SAME horizontal line)
        //   - Both 52dp tall/diameter
        //
        // ★ LINKED MOVEMENT (the new behavior the user asked for):
        //   When the user drags the nav bar AND the search FAB is "aligned"
        //   (same horizontal line — small Y misalignment up to ~5% is OK),
        //   the search FAB follows the nav bar — wherever the nav bar moves,
        //   the search FAB moves along with it, maintaining its relative
        //   position (gap + same Y).
        //
        // ★ SEPARATE FROM ASTRA — uses CynthiaTabCapsulePosition and
        //   CynthiaSearchFabPosition, NOT the shared Astra ones.
        val savedTabPos by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()
        val savedSearchPos by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()

        val tabY = savedTabPos.second
        val searchY = savedSearchPos.second
        val tabX = savedTabPos.first
        val searchX = savedSearchPos.first

        // ★ SIMPLE — no auto-center, no linked movement. The nav bar and
        //   search FAB each use their own SAVED position directly. The user
        //   moves them manually via the customization panel. Where the user
        //   sets it, there it stays. No magic, no confusion.

        // Nav bar uses its saved position directly
        val navBarEffectiveX = tabX
        val navBarEffectiveY = tabY

        // No-ops (kept for callback compatibility but do nothing now)
        val onNavBarDragged: (Float, Float) -> Unit = { _, _ -> }
        val onNavBarReleased: (Float, Float) -> Unit = { _, _ -> }

        // Use the customization width directly (no align/misalign animation)
        val navBarWidth = 150.dp

        CynthiaDraggableNavBar(
            tabs = cynthiaTabs,
            activeTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            backdrop = glassBackdrop,
            navBarWidth = navBarWidth,
            onShowCustomizationPanel = { isNavBar ->
                customizationPanelIsNavBar = isNavBar
                showCustomizationPanel = true
            },
            onNavBarDragged = onNavBarDragged,
            onNavBarReleased = onNavBarReleased,
            effectiveX = navBarEffectiveX,
            effectiveY = navBarEffectiveY
        )

        CynthiaDraggableSearchCircle(
            onSearchClick = { showSearch = true },
            backdrop = glassBackdrop,
            onShowCustomizationPanel = { isNavBar ->
                customizationPanelIsNavBar = isNavBar
                showCustomizationPanel = true
            }
        )

        // ═══ Cynthia search screen (temporary — delegates to Astra's) ═══
        // Tapping the search FAB opens this. For now it uses Astra's
        // SearchScreen so the user can search songs. A custom Cynthia
        // search UI will be built later.
        if (showSearch) {
            CynthiaSearchScreen(
                songs = songs,
                onSongClick = { song ->
                    showSearch = false
                    onSongClickWithReset(song)
                },
                onPlaylistClick = { _ ->
                    showSearch = false
                },
                onDismiss = { showSearch = false }
            )
        }

        // ★ TodaysTopCard — rendered OUTSIDE the layerBackdrop Box (same
        //   pattern as in Astra's HomeScreen). Shown when user taps a
        //   Today's Top cover in QuickPicksScreen. Miniplayer-style
        //   glass capsule (same shape & size as Astra's mini player).
        com.rajatxo.coral.ui.screens.TodaysTopCard(
            visible = showTopCard,
            song = topCardSong,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onDismiss = { showTopCard = false },
            onPlayPauseClick = { song ->
                if (song.id == currentSongId) {
                    onPlayPauseClick()
                } else {
                    onSongClickWithReset(song)
                }
            },
            onMenuClick = { _ ->
                showTopCardCustomization = true
            },
            backdrop = glassBackdrop
        )

        // ★ TodaysTopCard customization panel (square, same as nav bar's).
        //   Opened by tapping the menu (•••) icon inside the TodaysTopCard.
        //   Lets the user change Width/Height/Corner/Pos X/Pos Y/Shape of
        //   the TodaysTopCard. Changes apply live.
        CynthiaCustomizationPanel(
            visible = showTopCardCustomization,
            onDismiss = { showTopCardCustomization = false },
            backdrop = glassBackdrop,
            mode = CustomizationMode.TOP_CARD
        )

        // ★ MiniPlayer customization panel — opened by HOLDING the miniplayer.
        //   Same square glass panel, but in MINI_PLAYER mode. Lets the user
        //   change Width/Height/Corner/Pos X/Pos Y/Shape of the miniplayer.
        CynthiaCustomizationPanel(
            visible = showMiniPlayerCustomization,
            onDismiss = { showMiniPlayerCustomization = false },
            backdrop = glassBackdrop,
            mode = CustomizationMode.MINI_PLAYER
        )

        // ★ SpeedDialCard — floating glass capsule that lets the user swipe
        //   left/right to switch Speed Dial mode (Random / Last Played).
        //   Same shape + size + position as TodaysTopCard. Rendered OUTSIDE
        //   the layerBackdrop Box (same pattern — no glass recursion crash).
        //   Opened by tapping the "Speed dial" text or the chevron beside
        //   it in QuickPicksScreen.
        com.rajatxo.coral.ui.screens.SpeedDialCard(
            visible = showSpeedDialCard,
            onDismiss = { showSpeedDialCard = false },
            backdrop = glassBackdrop
        )

        // ════════════════════════════════════════════════════════════════
        // ★ MINIPLAYER + FULL PLAYER (simple fade transition — like Astra)
        // ════════════════════════════════════════════════════════════════

        // ★ Position/duration polling from mediaController
        var miniPlayerPositionMs by remember { mutableStateOf(0L) }
        var miniPlayerDurationMs by remember { mutableStateOf(0L) }
        androidx.compose.runtime.LaunchedEffect(mediaController, isPlaying) {
            while (true) {
                mediaController?.let { controller ->
                    miniPlayerPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    miniPlayerDurationMs = controller.duration.coerceAtLeast(0L)
                }
                kotlinx.coroutines.delay(500)
            }
        }

        // ★ Mini player — rendered when a song is playing AND not dismissed
        //   AND search screen is NOT open.
        if (currentSongId != null && !miniPlayerDismissed && !showSearch) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
            ) {
                CynthiaMiniPlayer(
                    title = currentSongTitle ?: "",
                    artist = currentSongArtist ?: "",
                    albumArtUri = currentSongArt,
                    isPlaying = isPlaying,
                    positionMs = miniPlayerPositionMs,
                    durationMs = miniPlayerDurationMs,
                    onPlayPauseClick = onPlayPauseClick,
                    onClick = { onMiniPlayerClick() },
                    onSwipeUp = { onMiniPlayerClick() },
                    onSwipeDismiss = {
                        // Pause playback + hide the mini player.
                        // The mini player reappears when a new song is selected.
                        if (isPlaying) onPlayPauseClick()
                        miniPlayerDismissed = true
                    },
                    isFullPlayerOpen = showFullPlayer,
                    onShowCustomizationPanel = { showMiniPlayerCustomization = true },
                    backdrop = glassBackdrop
                )
            }
        }

        // ★ Full player overlay — simple fade transition (like Astra)
        androidx.compose.animation.AnimatedVisibility(
            visible = showFullPlayer,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            val playerStyle by com.rajatxo.coral.data.prefs.PlayerStyleManager.playerStyle.collectAsState()
            if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.CORAL_GLASS) {
                    com.rajatxo.coral.ui.player.CoralGlassPlayer(
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
                        onAddToPlaylist = { }
                    )
                } else if (playerStyle == com.rajatxo.coral.data.prefs.PlayerStyleManager.CORAL) {
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
                        onAddToPlaylist = { }
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
                        onAddToPlaylist = { },
                        onSongDelete = { }
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
                        onAddToPlaylist = { },
                        onSongDelete = { }
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
                        onAddToPlaylist = { },
                        onSongDelete = { }
                    )
                } else {
                    com.rajatxo.coral.ui.player.FullPlayer(
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
                        onAddToPlaylist = { }
                    )
                }
        }

        // ═══ Settings overlay ═══
        if (showSettings) {
            SettingsScreen(
                onBackClick = { showSettings = false },
                onOpenPremium = { showSettings = false },
                onOpenEqualizer = { showSettings = false },
                onOpenSleepTimer = { showSettings = false },
                onOpenFontPicker = { showSettings = false },
                onOpenLyrics = { showSettings = false },
                onOpenSpiralPalette = { showSettings = false }
            )
        }

        // ═══ Customization panel (floating glass panel) ═══
        // Shown when user holds nav bar or search FAB for 5 seconds.
        // `customizationPanelIsNavBar` controls which section to show.
        CynthiaCustomizationPanel(
            visible = showCustomizationPanel,
            onDismiss = { showCustomizationPanel = false },
            backdrop = glassBackdrop,
            isNavBar = customizationPanelIsNavBar
        )

        // ★ Profile panel — opens when tapping the profile icon
        com.rajatxo.coral.ui.screens.ProfilePanel(
            visible = showProfilePanel,
            onDismiss = { showProfilePanel = false },
            backdrop = glassBackdrop
        )
    }
}
