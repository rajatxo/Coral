package com.rajatxo.coral.ui.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song

@Composable
fun HomeScreen(
    songs: List<Song>,
    mediaController: MediaController?,
    currentSongTitle: String?,
    currentSongArtist: String?,
    currentSongArt: android.net.Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onMiniPlayerClick: () -> Unit,
    showFullPlayer: Boolean,
    onFullPlayerDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        CoralNavRail(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> SongsTab(songs = songs, currentSongTitle = currentSongTitle, onSongClick = onSongClick)
                    1 -> PlaylistsTab()
                    2 -> SettingsTab()
                }
            }

            if (currentSongTitle != null) {
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
        }
    }

    if (showFullPlayer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable(onClick = onFullPlayerDismiss)
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

@Composable
private fun CoralNavRail(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Songs", "Playlists", "Settings")
    val icons = listOf("🎵", "📋", "⚙️")

    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Color(0xFF111111))
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabs.forEachIndexed { index, label ->
                NavItem(
                    icon = icons[index],
                    label = label,
                    isSelected = selectedTab == index,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp, color = if (isSelected) Color(0xFFFF6B6B) else Color(0xFF888888))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF888888),
            modifier = Modifier.graphicsLayer { rotationZ = -90f }
        )
    }
}

@Composable
private fun SongsTab(songs: List<Song>, currentSongTitle: String?, onSongClick: (Song) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Songs", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Text(text = "${songs.size} songs", color = Color(0xFFB0B0B0), fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(songs) { song ->
                SongRow(song = song, isCurrent = currentSongTitle == song.title, onClick = { onSongClick(song) })
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
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
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
private fun PlaylistsTab() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = "📋", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Playlists", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Coming soon", color = Color(0xFFB0B0B0), fontSize = 14.sp)
    }
}

@Composable
private fun SettingsTab() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = "⚙️", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Coming soon", color = Color(0xFFB0B0B0), fontSize = 14.sp)
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
            .navigationBarsPadding()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
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
