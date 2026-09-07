package com.rajatxo.coral.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.components.CoralNavRail
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.player.FullPlayer
import com.rajatxo.coral.ui.screens.PlaylistDetailScreen
import com.rajatxo.coral.ui.screens.PlaylistsScreen
import com.rajatxo.coral.ui.screens.PlaceholderScreen
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.screens.SongPickerScreen
import com.rajatxo.coral.ui.screens.SongsScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onSongEnded: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(CoralTab.Songs) }
    var railMode by remember { mutableStateOf(com.rajatxo.coral.ui.components.RailMode.Main) }
    var selectedSettingsTab by remember { mutableStateOf<com.rajatxo.coral.ui.components.CoralSettingsTab?>(null) }
    var selectedPlaylist by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showSongPicker by remember { mutableStateOf(false) }
    var playlistForPicker by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showPremium by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }

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
    val homeScope = androidx.compose.runtime.rememberCoroutineScope()
    val equalizerController = remember { com.rajatxo.coral.audio.EqualizerController() }
    val sleepTimer = remember {
        com.rajatxo.coral.data.premium.SleepTimer(
            scope = homeScope,
            onComplete = onSongEnded
        )
    }

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

    // Extract palette for the capsule's progress color
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    var capsuleAccentColor by remember { mutableStateOf<Color>(CoralColors.Coral) }
    androidx.compose.runtime.LaunchedEffect(currentSongArt) {
        com.rajatxo.coral.util.extractPalette(
            context = homeContext,
            artUri = currentSongArt
        )?.let { capsuleAccentColor = it.accent }
    }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        // Main content + nav rail — fills the whole screen
        Row(modifier = Modifier.fillMaxSize()) {
            CoralNavRail(
                mode = railMode,
                selectedMainTab = selectedTab,
                selectedSettingsTab = selectedSettingsTab,
                onMainTabSelected = {
                    selectedTab = it
                    selectedPlaylist = null
                },
                onSettingsTabSelected = { tab ->
                    selectedSettingsTab = tab
                    when (tab) {
                        com.rajatxo.coral.ui.components.CoralSettingsTab.Premium -> showPremium = true
                        com.rajatxo.coral.ui.components.CoralSettingsTab.Appearance -> showFontPicker = true  // opens appearance (font for now)
                        com.rajatxo.coral.ui.components.CoralSettingsTab.Playback -> showSleepTimer = true
                        com.rajatxo.coral.ui.components.CoralSettingsTab.About -> showPremium = true
                    }
                    // Stay on settings rail until user explicitly goes back
                },
                onGearClick = { railMode = com.rajatxo.coral.ui.components.RailMode.Settings },
                onBackClick = { railMode = com.rajatxo.coral.ui.components.RailMode.Main }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (selectedTab) {
                    CoralTab.QuickPicks -> PlaceholderScreen(
                        tabName = "Quick picks",
                        description = "Your most-played tracks and recently added songs will appear here. Coming soon."
                    )
                    CoralTab.Discover -> PlaceholderScreen(
                        tabName = "Discover",
                        description = "Random shuffle, hidden gems, and smart recommendations based on your listening. Coming soon."
                    )
                    CoralTab.Songs -> SongsScreen(
                        songs = songs,
                        currentSongId = currentSongId,
                        currentSongTitle = currentSongTitle,
                        onSongClick = onSongClick
                    )
                    CoralTab.Playlists -> {
                        val playlist = selectedPlaylist
                        if (playlist != null) {
                            PlaylistDetailScreen(
                                playlist = playlist,
                                allSongs = songs,
                                currentSongTitle = currentSongTitle,
                                onBackClick = { selectedPlaylist = null },
                                onPlayAll = { songList -> onSongClickWithQueue(songList.first(), songList) },
                                onShuffle = { songList ->
                                    val shuffled = songList.shuffled()
                                    if (shuffled.isNotEmpty()) onSongClickWithQueue(shuffled.first(), shuffled)
                                },
                                onSongClick = { song, songList -> onSongClickWithQueue(song, songList) },
                                onAddSongsClick = {
                                    playlistForPicker = playlist
                                    showSongPicker = true
                                }
                            )
                        } else {
                            PlaylistsScreen(
                                onPlaylistClick = { selectedPlaylist = it }
                            )
                        }
                    }
                    CoralTab.Artists -> PlaceholderScreen(
                        tabName = "Artists",
                        description = "Browse your library by artist. Coming soon."
                    )
                    CoralTab.Albums -> PlaceholderScreen(
                        tabName = "Albums",
                        description = "Browse your library by album. Coming soon."
                    )
                    CoralTab.Folders -> PlaceholderScreen(
                        tabName = "Folders",
                        description = "Browse your music by folder. Coming soon."
                    )
                }

                // --- Sleep Timer Capsule (auto-fills space before title) ---
                // Uses a Row with weight(1f) to auto-fill the space before
                // the big title text on the right. The capsule expands/shrinks
                // depending on how wide the title text is on each tab.
                if (sleepTimerState.active) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 20.dp, top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val capsuleRemaining = if (sleepTimerState.endOfSong) {
                            (miniPlayerDurationMs - miniPlayerPositionMs).coerceAtLeast(0L)
                        } else {
                            sleepRemainingMs
                        }
                        com.rajatxo.coral.ui.components.SleepTimerCapsule(
                            visible = sleepTimerState.active && capsuleRemaining > 0,
                            remainingMs = capsuleRemaining,
                            onExtend = { sleepTimer.extend(10) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // --- Mini player (bottom, full-width) ---
        AnimatedVisibility(
            visible = currentSongTitle != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
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
                onClick = onMiniPlayerClick
            )
        }

        // --- Draggable Floating Search Button ---
        // Solid white rounded-square with black search icon.
        // Long-press (3 seconds) → enters drag mode → drag anywhere on right
        // half of screen → release → stays fixed at that position.
        // Position persists across app restarts via SharedPreferences.
        DraggableSearchFab()

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
                        sleepTimer = sleepTimer,
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

        // Full-screen now-playing screen
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
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
                onDismiss = onFullPlayerDismiss
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
    onClick: () -> Unit
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

    val favorites by com.rajatxo.coral.data.store.PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .navigationBarsPadding()
    ) {
        // --- Main player body (standard pill) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                .clickable(onClick = onClick)
        ) {
            // Blurred album cover background
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(25.dp)
                )
            }
            // Dark tint overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )

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
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
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
private fun DraggableSearchFab() {
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

