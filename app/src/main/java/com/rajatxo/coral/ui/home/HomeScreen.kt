package com.rajatxo.coral.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
            }
        }

        // Mini player — FULL-WIDTH overlay at the bottom (covers nav rail too,
        // like ViTune). Slides up only when a song is loaded. Extends into the
        // system nav bar area so the gesture indicator blends with the app.
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
                isPlaying = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                onClick = onMiniPlayerClick
            )
        }

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
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoralColors.SurfaceVariant)
            // Content extends into the system nav bar area — that's what
            // makes the gesture indicator blend with the app background
            // (the ViTune-style seamless look). We pad the content inside
            // with navigationBarsPadding so the buttons stay tappable.
            .clickable(onClick = onClick)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art 40x40 rounded
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color(0xFFB0B0B0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.size(8.dp))

        // Play/pause
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(8.dp))

        // Skip next
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onNextClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CoralIcons.SkipNext,
                contentDescription = "Skip to next",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

