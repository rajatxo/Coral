package com.rajatxo.coral

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rajatxo.coral.data.scanner.MusicScanner
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.service.CoralPlaybackService
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.home.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @androidx.compose.foundation.ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        // Force BOTH system bars (status + navigation) to be FULLY TRANSPARENT
        // with NO scrim. This is what makes the system gesture indicator blend
        // into the app background — the ViTune-style seamless look.
        //
        // Why SystemBarStyle.dark(0) and not SystemBarStyle.auto(0, 0)?
        //   auto(0, 0) lets the system pick a scrim based on the system theme.
        //   If the user's system is in light mode, Android applies a dark scrim
        //   on top of our transparent background — that's the visible "fade"
        //   behind the nav buttons.
        //
        //   dark(0) explicitly tells Android: 'this app is dark-themed, apply
        //   NO scrim, ever, regardless of system theme.' This is what ViTune
        //   does to get the seamless edge-to-edge look.
        //
        //   The `0` parameter is the explicit transparent color.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0),
            navigationBarStyle = SystemBarStyle.dark(0)
        )
        super.onCreate(savedInstanceState)
        setContent {
            // Observe the user's font choice. When it changes (e.g. they
            // pick "Inter" in Settings → Appearance → Font), this
            // recomposes and the MaterialTheme rebuilds with the new
            // typography, instantly applying the new font to every
            // Text() in the app.
            val currentFont by com.rajatxo.coral.data.prefs.FontManager.currentFont.collectAsState()
            val typography = remember(currentFont) {
                com.rajatxo.coral.ui.theme.coralTypographyFor(currentFont)
            }

            androidx.compose.material3.MaterialTheme(
                typography = typography
            ) {
                CoralApp()
            }
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun CoralApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val songs = remember { mutableStateListOf<Song>() }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentSongTitle by remember { mutableStateOf<String?>(null) }
    var currentSongArtist by remember { mutableStateOf<String?>(null) }
    var currentSongAlbum by remember { mutableStateOf<String?>(null) }
    var currentSongArt by remember { mutableStateOf<android.net.Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, CoralPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        val controller = controllerFuture.await()
        mediaController = controller

        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentSongTitle = mediaItem?.mediaMetadata?.title?.toString()
                currentSongArtist = mediaItem?.mediaMetadata?.artist?.toString()
                currentSongAlbum = mediaItem?.mediaMetadata?.albumTitle?.toString()
                currentSongArt = mediaItem?.mediaMetadata?.artworkUri
                // Resolve the song's MediaStore ID from the mediaId we set when building MediaItems.
                // This lets us look it up for favorites / playlist membership.
                currentSongId = mediaItem?.mediaId?.toLongOrNull()
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        })

        // --- BUG FIX: Restore mini player state after app restart ---
        // When the app is killed from the task manager and reopened, the
        // playback service is still running (the song keeps playing), but
        // our state variables (currentSongTitle, etc.) are all null because
        // they were re-initialized in this composable.
        //
        // The Player.Listener above only fires on STATE CHANGES — it
        // doesn't fire for the currently-playing song. So we have to
        // explicitly read the current state from the controller here.
        //
        // Without this fix, the mini player wouldn't appear after restarting
        // the app, even though the song was still playing.
        val currentMediaItem = controller.currentMediaItem
        if (currentMediaItem != null) {
            currentSongTitle = currentMediaItem.mediaMetadata.title?.toString()
            currentSongArtist = currentMediaItem.mediaMetadata.artist?.toString()
            currentSongAlbum = currentMediaItem.mediaMetadata.albumTitle?.toString()
            currentSongArt = currentMediaItem.mediaMetadata.artworkUri
            currentSongId = currentMediaItem.mediaId.toLongOrNull()
        }
        isPlaying = controller.isPlaying
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) {
            isLoading = true
            scope.launch {
                val scannedSongs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context.contentResolver) }
                songs.clear()
                songs.addAll(scannedSongs)
                isLoading = false
            }
        } else { Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else { arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) }
        permissionLauncher.launch(permissions)
    }

    // NOTE: We intentionally do NOT call startForegroundService() here.
    //
    // Previous bug: calling startForegroundService() on app launch started
    // a 5-second countdown. If no song was playing within 5 seconds (which
    // is the normal case — the user is browsing their song list), Android
    // threw ForegroundServiceDidNotStartInTimeException and crashed the app.
    // This was the "crash after 8-10 seconds" bug.
    //
    // Fix: Don't start the service explicitly. Media3's MediaController
    // will bind to the service automatically when MediaController.Builder()
    // .buildAsync() is called (in the LaunchedEffect above). The service
    // starts as a regular bound service. When the user taps a song and
    // playback begins, MediaSessionService promotes itself to foreground
    // automatically and shows the media notification — no timing issues.

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        when {
            !hasPermission -> {
                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = "🪸", fontSize = 64.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Coral needs permission", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "To scan and play your music, Coral needs access to your audio files.", color = Color(0xFFB0B0B0), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFFFF6B6B)).clickable {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                        } else { arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) }
                        permissionLauncher.launch(permissions)
                    }.padding(horizontal = 32.dp, vertical = 12.dp)) {
                        Text(text = "Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            isLoading -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF6B6B))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Scanning your music...", color = Color(0xFFB0B0B0))
                }
            }
            songs.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = "🎵", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No music found", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Add some music to your device and try again.", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
            }
            else -> {
                HomeScreen(
                    songs = songs,
                    mediaController = mediaController,
                    currentSongId = currentSongId,
                    currentSongTitle = currentSongTitle,
                    currentSongArtist = currentSongArtist,
                    currentSongAlbum = currentSongAlbum,
                    currentSongArt = currentSongArt,
                    isPlaying = isPlaying,
                    onPlayPauseClick = {
                        if (isPlaying) mediaController?.pause() else mediaController?.play()
                    },
                    onNextClick = { mediaController?.seekToNextMediaItem() },
                    onPrevClick = { mediaController?.seekToPreviousMediaItem() },
                    onSeek = { positionMs -> mediaController?.seekTo(positionMs) },
                    onSongClick = { song ->
                        mediaController?.let { controller ->
                            val allMediaItems = songs.map { s ->
                                MediaItem.Builder().setUri(s.uri).setMediaId(s.id.toString())
                                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).setArtworkUri(s.albumArtUri).build())
                                    .build()
                            }
                            val index = songs.indexOf(song)
                            controller.setMediaItems(allMediaItems, index, 0)
                            controller.prepare()
                            controller.play()
                        }
                    },
                    onSongClickWithQueue = { song, songList ->
                        // Used by PlaylistDetailScreen — sets the queue to the playlist's
                        // songs (in their playlist order) and starts from the tapped song.
                        mediaController?.let { controller ->
                            val mediaItems = songList.map { s ->
                                MediaItem.Builder().setUri(s.uri).setMediaId(s.id.toString())
                                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).setArtworkUri(s.albumArtUri).build())
                                    .build()
                            }
                            val index = songList.indexOf(song).coerceAtLeast(0)
                            controller.setMediaItems(mediaItems, index, 0)
                            controller.prepare()
                            controller.play()
                        }
                    },
                    onMiniPlayerClick = { showFullPlayer = true },
                    showFullPlayer = showFullPlayer,
                    onFullPlayerDismiss = { showFullPlayer = false },
                    onSongEnded = { mediaController?.pause() }
                )
            }
        }
    }
}
