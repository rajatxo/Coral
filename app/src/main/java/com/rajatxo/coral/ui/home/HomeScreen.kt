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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.components.CoralNavRail
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.icons.CoralIcons
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
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }
    var selectedPlaylist by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showSongPicker by remember { mutableStateOf(false) }
    var playlistForPicker by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }
    var showPremium by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
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
                  showPremium || showSettings || showEqualizer || showSleepTimer || showFontPicker
    ) {
        when {
            showFullPlayer -> onFullPlayerDismiss()
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
    val homeScope = androidx.compose.runtime.rememberCoroutineScope()
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
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    var capsuleAccentColor by remember { mutableStateOf<Color>(Color(0xFFF4B400)) }
    androidx.compose.runtime.LaunchedEffect(currentSongArt) {
        com.rajatxo.coral.util.extractPalette(
            context = homeContext,
            artUri = currentSongArt
        )?.let { capsuleAccentColor = it.accent }
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

        // --- Liquid Glass backdrop (same approach as SimpMusic) ---
        // A LayerBackdrop is created here (the parent), and shared between:
        //   - The page content Box (applies Modifier.layerBackdrop(backdrop))
        //   - The TabCapsule (applies Modifier.drawBackdrop(backdrop, blur))
        // This creates a TRUE real-time backdrop blur — the nav bar samples
        // the page content behind it and blurs it via AGSL shaders.
        val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop {
            drawContent()
        }

        // Main content — fills the WHOLE screen (no nav rail anymore)
        // Wrapped with layerBackdrop so the nav bar can sample + blur this content.
        Box(modifier = Modifier.fillMaxSize().then(com.kyant.backdrop.backdrops.layerBackdrop(glassBackdrop))) {

                when (selectedTab) {
                    CoralTab.QuickPicks -> QuickPicksScreen(
                        songs = songs,
                        currentSongId = currentSongId,
                        capsuleVisible = capsuleVisible,
                        capsuleRemaining = capsuleRemaining,
                        onExtend = onExtend,
                        onSongClick = onSongClick
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
                        onSongClick = onSongClick,
                        capsuleVisible = capsuleVisible,
                        capsuleRemaining = capsuleRemaining,
                        onExtend = onExtend
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
                    CoralTab.Folders -> PlaceholderScreen(
                        tabName = "Folders",
                        description = "Browse your music by folder. Coming soon.",
                        capsuleVisible = capsuleVisible,
                        capsuleRemaining = capsuleRemaining,
                        onExtend = onExtend
                    )
                }
            }

        // --- Mini player (bottom, above the tab capsule) ---
        AnimatedVisibility(
            visible = currentSongTitle != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp)  // sit above the tab capsule (52dp + 16dp + 4dp gap)
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
        DraggableSearchFab()

        // --- Floating Settings Button (top-left, on every page) ---
        // Lucide "settings" gear icon in a small white-tinted circle.
        // Tapping it opens the SettingsScreen as a full-screen overlay.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showSettings = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CoralIcons.Gear,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // --- Tab Capsule (nav bar — Coral's tab switcher) ---
        // Glossy white pill with black string + black text. Center is ~85dp
        // from the bottom of the screen (above the system navigation buttons).
        com.rajatxo.coral.ui.components.TabCapsule(
            tabs = CoralTab.values().toList(),
            activeTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                selectedPlaylist = null
            },
            backdrop = glassBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 27.dp)  // center ~85dp from bottom
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
                onDismiss = onFullPlayerDismiss,
                onAddToPlaylist = { songId ->
                    songToAddToPlaylist = songId
                }
            )
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
                    onPlayAll = { songList -> onSongClickWithQueue(songList.first(), songList) },
                    onShuffle = { songList ->
                        val shuffled = songList.shuffled()
                        if (shuffled.isNotEmpty()) onSongClickWithQueue(shuffled.first(), shuffled)
                    },
                    onSongClick = { song, songList -> onSongClickWithQueue(song, songList) },
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

