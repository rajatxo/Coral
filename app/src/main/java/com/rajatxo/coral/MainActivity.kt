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
import androidx.compose.runtime.DisposableEffect
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
import com.rajatxo.coral.audio.CrossfadeVisualState
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

    // ─── INSTANT LOAD: cached songs populate on the FIRST frame ──
    // Previous bug: `songs` started empty. On launch, a background
    // LaunchedEffect scanned MediaStore (200-500ms) and populated
    // `songs` after. During that window, QuickPicksScreen rendered
    // with an empty list → only section headers visible on a black
    // background → the "black screen on app open" bug.
    //
    // Fix: load the cached song list SYNCHRONOUSLY here (before the
    // first compose render). The cache is a JSON file in internal
    // storage (~5-20ms to read+parse for a typical library). The
    // background scan then runs and updates the list if MediaStore
    // has changed (new songs added, removed, etc.).
    //
    // On first launch (no cache yet), `load` returns empty → we fall
    // back to the original scan-then-populate behavior. Acceptable
    // because first launch is a one-time cost.
    val songs = remember {
        mutableStateListOf<Song>().apply {
            addAll(com.rajatxo.coral.data.scanner.SongCache.load(context))
        }
    }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentSongTitle by remember { mutableStateOf<String?>(null) }
    var currentSongArtist by remember { mutableStateOf<String?>(null) }
    var currentSongAlbum by remember { mutableStateOf<String?>(null) }
    var currentSongArt by remember { mutableStateOf<android.net.Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Check if permissions are already granted (e.g. returning user).
    // If granted, skip the permission screen entirely. If not, show it.
    var permissionChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        val musicOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        hasPermission = notifOk && musicOk
        permissionChecked = true

        // ─── Background scan + cache update ──
        // Even if we already have cached songs (loaded synchronously
        // above), we still scan MediaStore in the background to pick
        // up any changes (new songs added since last launch, songs
        // deleted, metadata edits, etc.). The scanned list replaces
        // the cached list AND is persisted back to the cache for the
        // NEXT launch.
        if (hasPermission) {
            scope.launch {
                val scannedSongs = withContext(Dispatchers.IO) {
                    MusicScanner.scanMusic(context.contentResolver)
                }
                // Only update + re-save if the scan result differs from
                // what we already have (avoids unnecessary UI flicker).
                if (scannedSongs != songs.toList()) {
                    songs.clear()
                    songs.addAll(scannedSongs)
                    // Persist to cache for next launch's instant load
                    withContext(Dispatchers.IO) {
                        com.rajatxo.coral.data.scanner.SongCache.save(context, scannedSongs)
                    }
                }
                // Also scan for lyrics files (independent of song cache)
                withContext(Dispatchers.IO) {
                    com.rajatxo.coral.data.lyrics.LyricsIndex.scan(context)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
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
                    currentSongId = mediaItem?.mediaId?.toLongOrNull()
                    // Play on song change — call play() for ALL transitions
                    // except PLAYLIST_CHANGED (caller already calls play()).
                    // Check isPlaying first to avoid the "1-2" restart bug
                    // (if already playing mid-transition, don't interfere).
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (!controller.isPlaying) {
                            controller.play()
                        }
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })

            // Restore the saved repeat mode (was hardcoded to REPEAT_MODE_ALL,
            // which reset the user's "loop one song" selection on every restart).
            controller.repeatMode = com.rajatxo.coral.data.prefs.PlaybackPrefs.repeatMode.value

            // Listen for repeat mode changes and persist them.
            controller.addListener(object : Player.Listener {
                override fun onRepeatModeChanged(repeatMode: Int) {
                    com.rajatxo.coral.data.prefs.PlaybackPrefs.setRepeatMode(repeatMode)
                }
            })

            // --- Persistent Queue: restore the queue after a full app kill ---
            // If the controller has no current media item (fresh service start),
            // and the Persistent Queue setting is enabled, restore the saved
            // queue from SharedPreferences.
            val currentMediaItem = controller.currentMediaItem
            if (currentMediaItem != null) {
                currentSongTitle = currentMediaItem.mediaMetadata.title?.toString()
                currentSongArtist = currentMediaItem.mediaMetadata.artist?.toString()
                currentSongAlbum = currentMediaItem.mediaMetadata.albumTitle?.toString()
                currentSongArt = currentMediaItem.mediaMetadata.artworkUri
                currentSongId = currentMediaItem.mediaId.toLongOrNull()
            } else if (com.rajatxo.coral.data.prefs.PlaybackPrefs.persistentQueueEnabled.value) {
                // Try to restore the saved queue.
                val saved = com.rajatxo.coral.data.prefs.PlaybackPrefs.loadQueue()
                if (saved != null && songs.isNotEmpty()) {
                    val (savedSongIds, savedIndex, savedPosition) = saved
                    // Map saved song IDs back to actual Song objects.
                    // If a song was deleted from the device, it's skipped.
                    val songMap = songs.associateBy { it.id }
                    val restoredSongs = savedSongIds.mapNotNull { songMap[it] }
                    if (restoredSongs.isNotEmpty()) {
                        val mediaItems = restoredSongs.map { s ->
                            MediaItem.Builder().setUri(s.uri).setMediaId(s.id.toString())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(s.title)
                                        .setArtist(s.artist)
                                        .setAlbumTitle(s.album)
                                        .setArtworkUri(s.albumArtUri)
                                        .build()
                                )
                                .build()
                        }
                        // Clamp the index to the restored list's bounds
                        val restoreIndex = savedIndex.coerceIn(0, restoredSongs.lastIndex)
                        controller.setMediaItems(mediaItems, restoreIndex, savedPosition)
                        controller.prepare()
                        // Don't auto-play — let the user press play.
                        // The mini player will show the restored song.
                    }
                }
            }
            isPlaying = controller.isPlaying
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected when the LaunchedEffect is cancelled (e.g. on rotation).
            // Don't rethrow — just clean up.
            // The new Activity instance will rebuild the controller.
        } catch (e: Exception) {
            // Any other error (service not running, timeout, etc.)
            // Don't crash the app — the user can still browse music without
            // a media controller, they just can't play it.
            android.util.Log.e("CoralApp", "MediaController setup failed", e)
        }
    }

    // FIX: Release the MediaController when the composable leaves the
    // composition (e.g. on rotation). This prevents controller leaks and
    // the "controller still attached to dead service" crash.
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaController?.release()
            } catch (_: Exception) { }
            mediaController = null
        }
    }

    // ─── Bluetooth resume receiver (#6) ──────────────────────────────
    // When the "Resume when connected to Bluetooth" toggle is ON:
    //   • BT device connects → if a song is loaded but paused, resume
    //   • BT device disconnects → if playing, pause
    //
    // Uses a BroadcastReceiver for ACTION_ACL_CONNECTED and
    // ACTION_ACL_DISCONNECTED. Registered/unregistered via DisposableEffect
    // so it only runs while the Activity is alive (avoids leaking the
    // receiver after the Activity is destroyed).
    //
    // The receiver reads the toggle state at fire-time (not at register-
    // time) so toggling the setting in Settings doesn't require re-
    // registering the receiver.
    DisposableEffect(mediaController) {
        val controller = mediaController ?: return@DisposableEffect onDispose { }

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                val enabled = com.rajatxo.coral.data.prefs.PlaybackPrefs.bluetoothResumeEnabled.value
                if (!enabled) return

                when (intent?.action) {
                    android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // BT device connected → resume if paused
                        if (controller.isPlaying.not() && controller.currentMediaItem != null) {
                            controller.play()
                        }
                    }
                    android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // BT device disconnected → pause if playing
                        if (controller.isPlaying) {
                            controller.pause()
                        }
                    }
                }
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    // When user grants permissions via PermissionScreen, scan music in the
    // background — no loading screen. HomeScreen renders instantly and the
    // song list populates within ~100-200ms as the MediaStore query returns.
    val onPermissionsGranted: () -> Unit = {
        hasPermission = true
        scope.launch {
            val scannedSongs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context.contentResolver) }
            songs.clear()
            songs.addAll(scannedSongs)
            // Persist to cache for next launch's instant load
            withContext(Dispatchers.IO) {
                com.rajatxo.coral.data.scanner.SongCache.save(context, scannedSongs)
            }
            // Scan for lyrics files (.lrc/.txt) on the device
            withContext(Dispatchers.IO) {
                com.rajatxo.coral.data.lyrics.LyricsIndex.scan(context)
            }
        }
    }

    // Pull-to-refresh handler — called when the user drags down on the
    // HomeScreen. Re-runs the MediaStore scan so newly added songs show up.
    // Also re-scans the lyrics index so new .lrc/.txt sidecars are picked up.
    // This is a suspend lambda so HomeScreen can await completion before
    // hiding the refresh spinner.
    val onRefreshSongs: suspend () -> Unit = {
        val scannedSongs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context.contentResolver) }
        songs.clear()
        songs.addAll(scannedSongs)
        // Persist refreshed list to cache
        withContext(Dispatchers.IO) {
            com.rajatxo.coral.data.scanner.SongCache.save(context, scannedSongs)
        }
        withContext(Dispatchers.IO) {
            com.rajatxo.coral.data.lyrics.LyricsIndex.scan(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        when {
            !permissionChecked -> {
                // Brief loading state while checking permissions
                Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface))
            }
            !hasPermission -> {
                // New premium permission screen
                com.rajatxo.coral.ui.screens.PermissionScreen(
                    onPermissionsGranted = onPermissionsGranted
                )
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
                    onNextClick = {
                        // Clear stale crossfade state — prevents "stuck at one
                        // cover" bug when manual next coincides with/replaces
                        // a pending visual crossfade.
                        CrossfadeVisualState.clearIncoming()
                        mediaController?.seekToNextMediaItem()
                    },
                    onPrevClick = {
                        CrossfadeVisualState.clearIncoming()
                        mediaController?.seekToPreviousMediaItem()
                    },
                    onSeek = { positionMs -> mediaController?.seekTo(positionMs) },
                    onSongClick = { song ->
                        CrossfadeVisualState.clearIncoming()
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
                            // Save the queue for Persistent Queue restoration
                            com.rajatxo.coral.data.prefs.PlaybackPrefs.saveQueue(
                                songs.map { it.id }, index, 0L
                            )
                        }
                    },
                    onSongClickWithQueue = { song, songList ->
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
                            // Save the queue for Persistent Queue restoration
                            com.rajatxo.coral.data.prefs.PlaybackPrefs.saveQueue(
                                songList.map { it.id }, index, 0L
                            )
                        }
                    },
                    onMiniPlayerClick = { showFullPlayer = true },
                    showFullPlayer = showFullPlayer,
                    onFullPlayerDismiss = { showFullPlayer = false },
                    onSongEnded = { mediaController?.pause() },
                    onRefresh = onRefreshSongs
                )
            }
        }
    }
}
