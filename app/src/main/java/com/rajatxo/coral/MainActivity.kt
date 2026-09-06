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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.scanner.MusicScanner
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.service.CoralPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CoralApp()
        }
    }
}

@Composable
fun CoralApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val songs = remember { mutableStateListOf<Song>() }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var currentSongTitle by remember { mutableStateOf<String?>(null) }
    var currentSongArtist by remember { mutableStateOf<String?>(null) }
    var currentSongArt by remember { mutableStateOf<android.net.Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, CoralPlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        val controller = controllerFuture.await()
        mediaController = controller

        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentSongTitle = mediaItem?.mediaMetadata?.title?.toString()
                currentSongArtist = mediaItem?.mediaMetadata?.artist?.toString()
                currentSongArt = mediaItem?.mediaMetadata?.artworkUri
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) {
            isLoading = true
            scope.launch {
                val scannedSongs = withContext(Dispatchers.IO) {
                    MusicScanner.scanMusic(context.contentResolver)
                }
                songs.clear()
                songs.addAll(scannedSongs)
                isLoading = false
            }
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        val intent = android.content.Intent(context, CoralPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        when {
            !hasPermission -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "🪸", fontSize = 64.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Coral needs permission", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "To scan and play your music, Coral needs access to your audio files.", color = Color(0xFFB0B0B0), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFFF6B6B))
                            .clickable {
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(permissions)
                            }
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                    ) {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(text = "Songs", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    Text(text = "${songs.size} songs", color = Color(0xFFB0B0B0), fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(songs) { song ->
                            SongRow(
                                song = song,
                                isCurrent = currentSongTitle == song.title,
                                onClick = {
                                    mediaController?.let { controller ->
                                        val allMediaItems = songs.map { s ->
                                            MediaItem.Builder()
                                                .setUri(s.uri)
                                                .setMediaId(s.id.toString())
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
                                        val index = songs.indexOf(song)
                                        controller.setMediaItems(allMediaItems, index, 0)
                                        controller.prepare()
                                        controller.play()
                                    }
                                }
                            )
                        }
                    }

                    if (currentSongTitle != null) {
                        MiniPlayer(
                            title = currentSongTitle ?: "",
                            artist = currentSongArtist ?: "",
                            albumArtUri = currentSongArt,
                            isPlaying = isPlaying,
                            onPlayPauseClick = {
                                if (isPlaying) mediaController?.pause()
                                else mediaController?.play()
                            },
                            onNextClick = { mediaController?.seekToNextMediaItem() },
                            onClick = { showFullPlayer = true }
                        )
                    }
                }

                // Full Player Overlay (placeholder for now)
                if (showFullPlayer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { showFullPlayer = false }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🪸", fontSize = 64.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = currentSongTitle ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(text = currentSongArtist ?: "", color = Color(0xFFB0B0B0), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(text = "Full player coming soon!", color = Color(0xFFB0B0B0))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Tap anywhere to close", color = Color(0xFF666666), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) Color(0xFF1A1A1A) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(model = song.albumArtUri, contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(text = "🎵", color = Color(0xFFB0B0B0))
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, color = if (isCurrent) Color(0xFFFF6B6B) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist, color = Color(0xFFB0B0B0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val minutes = song.duration / 60000
        val seconds = (song.duration % 60000) / 1000
        Text(text = "$minutes:${String.format("%02d", seconds)}", color = Color(0xFFB0B0B0), fontSize = 13.sp)
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
            .background(Color(0xFF1A1A1A))
            .navigationBarsPadding() // <-- THIS FIXES THE NAV BAR OVERLAP
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (albumArtUri != null) {
                AsyncImage(model = albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(text = "🎵", color = Color(0xFFB0B0B0))
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = artist, color = Color(0xFFB0B0B0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onPlayPauseClick), contentAlignment = Alignment.Center) {
            Text(text = if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onNextClick), contentAlignment = Alignment.Center) {
            Text(text = "⏭", color = Color.White, fontSize = 20.sp)
        }
    }
}
