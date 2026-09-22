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
import kotlinx.coroutines.delay
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

    // ─── Bluetooth resume (#6) ──────────────────────────────────────
    // When the "Resume when connected to Bluetooth" toggle is ON:
    //   • Audio device connects → if a song is loaded but paused, resume
    //   • Audio device disconnects → if playing, pause
    //
    // Implementation uses TWO mechanisms for reliability:
    //
    //   1. AudioDeviceCallback (API 23+) — the official way to listen
    //      for audio device changes. No permission required. Fires for
    //      Bluetooth, wired headphones, USB audio — any audio device.
    //      This is the primary mechanism. The previous BroadcastReceiver
    //      using ACTION_ACL_CONNECTED/DISCONNECTED didn't fire on
    //      reconnect on Android 12+ (needs BLUETOOTH_CONNECT runtime
    //      permission, which the app doesn't request).
    //
    //   2. ACTION_AUDIO_BECOMING_NOISY — fires when audio is about to
    //      become noisy (e.g. headphone unplugged, BT disconnected).
    //      This is the backup disconnect signal — covers cases where
    //      AudioDeviceCallback's onAudioDevicesRemoved might not fire
    //      (rare, but possible during system audio routing changes).
    //
    // Both mechanisms check the toggle state at fire-time (not register-
    // time), so toggling the setting in Settings doesn't require re-
    // registering.
    DisposableEffect(mediaController) {
        val controller = mediaController ?: return@DisposableEffect onDispose { }
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

        // --- 1. AudioDeviceCallback (primary, API 23+) ---
        // android.media.AudioDeviceCallback is a top-level class (not
        // nested in AudioManager). The fully-qualified name is
        // android.media.AudioDeviceCallback.
        val deviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<android.media.AudioDeviceInfo>?) {
                val enabled = com.rajatxo.coral.data.prefs.PlaybackPrefs.bluetoothResumeEnabled.value
                if (!enabled) return
                // Check if any ADDED device is a Bluetooth A2DP type.
                // TYPE_BLUETOOTH_A2DP = high-quality audio streaming.
                val hasBluetooth = addedDevices?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                } == true
                if (hasBluetooth) {
                    // BT device connected → resume if paused + has a song loaded
                    if (!controller.isPlaying && controller.currentMediaItem != null) {
                        controller.play()
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<android.media.AudioDeviceInfo>?) {
                val enabled = com.rajatxo.coral.data.prefs.PlaybackPrefs.bluetoothResumeEnabled.value
                if (!enabled) return
                val hasBluetooth = removedDevices?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                } == true
                if (hasBluetooth) {
                    // BT device disconnected → pause if playing
                    if (controller.isPlaying) {
                        controller.pause()
                    }
                }
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)

        // --- 2. ACTION_AUDIO_BECOMING_NOISY (backup disconnect signal) ---
        val noisyReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val enabled = com.rajatxo.coral.data.prefs.PlaybackPrefs.bluetoothResumeEnabled.value
                if (!enabled) return
                if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    if (controller.isPlaying) {
                        controller.pause()
                    }
                }
            }
        }
        val noisyFilter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, noisyFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(noisyReceiver, noisyFilter)
        }

        onDispose {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            try { context.unregisterReceiver(noisyReceiver) } catch (_: Exception) { }
        }
    }

    // ─── Persistent Queue: periodic save + save on transitions ─────
    // Saves the current queue (song IDs) + current song index + playback
    // position to SharedPreferences every 5 seconds while a song is
    // loaded. Also saves on song transition (onMediaItemTransition).
    //
    // WHY PERIODIC: The previous ON_STOP-only save was unreliable. When
    // the user swipes the app from recents (force kill), the process is
    // killed immediately — ON_STOP might not fire, or the SharedPreferences
    // write might not complete. The saved position would stay at 0L (from
    // the song click), so the song would restart from the beginning on
    // reopen. With a 5-second periodic save, the position is at most 5
    // seconds stale.
    //
    // The ON_STOP save (in the LifecycleEventObserver below) is still kept
    // as a backup — it captures the exact position at the moment of
    // backgrounding.
    LaunchedEffect(mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        while (true) {
            // Only save if persistent queue is enabled AND there's a song loaded.
            val enabled = com.rajatxo.coral.data.prefs.PlaybackPrefs.persistentQueueEnabled.value
            if (enabled) {
                val player: Player = controller
                val itemCount = player.mediaItemCount
                if (itemCount > 0 && player.currentMediaItem != null) {
                    val songIds = (0 until itemCount).mapNotNull {
                        player.getMediaItemAt(it).mediaId.toLongOrNull()
                    }
                    val currentIndex = player.currentMediaItemIndex
                    val position = player.currentPosition.coerceAtLeast(0L)
                    com.rajatxo.coral.data.prefs.PlaybackPrefs.saveQueue(
                        songIds, currentIndex, position
                    )
                }
            }
            delay(5000)  // save every 5 seconds
        }
    }

    // ─── Persistent Queue: save position on app background ──────
    // Saves the current queue + position when the app goes to background
    // (ON_STOP). This is the "best effort" save — captures the exact
    // position at the moment of backgrounding. The periodic save above
    // is the reliable fallback for force kills where ON_STOP doesn't fire.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(mediaController, lifecycleOwner) {
        val controller = mediaController ?: return@DisposableEffect onDispose { }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val player: Player = controller
                val itemCount = player.mediaItemCount
                if (itemCount > 0) {
                    val songIds = (0 until itemCount).mapNotNull {
                        player.getMediaItemAt(it).mediaId.toLongOrNull()
                    }
                    val currentIndex = player.currentMediaItemIndex
                    val position = player.currentPosition.coerceAtLeast(0L)
                    com.rajatxo.coral.data.prefs.PlaybackPrefs.saveQueue(
                        songIds, currentIndex, position
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
