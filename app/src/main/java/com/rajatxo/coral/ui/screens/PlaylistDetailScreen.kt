package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Playlist detail screen — SimpMusic-style immersive.
 *
 * Layout:
 *  - Layer 1: full-screen blurred background using the first song's album art
 *  - Layer 2: vertical gradient (primary -> black) so text stays readable
 *  - Layer 3: content column:
 *      - top bar: ChevronDown (back), playlist name (small), MoreVertical
 *      - large cover (square, 24dp rounded, shadow)
 *      - playlist name (28sp bold)
 *      - "{n} songs" subtitle
 *      - row of action buttons: Play all | Shuffle | Add songs
 *      - song list
 *
 * Empty state: shows a friendly "Add songs" call-to-action.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allSongs: List<Song>,
    currentSongTitle: String?,
    onBackClick: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddSongsClick: () -> Unit
) {
    // Re-fetch the playlist from the store so we get updates when songs are added
    val playlists by PlaylistStore.playlists.collectAsState()
    val livePlaylist = playlists.firstOrNull { it.id == playlist.id } ?: playlist

    val songsInPlaylist = remember(livePlaylist, allSongs) {
        val songMap = allSongs.associateBy { it.id }
        livePlaylist.songIds.mapNotNull { songMap[it] }
    }

    val backgroundArtUri = songsInPlaylist.firstOrNull()?.albumArtUri?.toString()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Layer 1: blurred background
        if (backgroundArtUri != null) {
            AsyncImage(
                model = backgroundArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
            )
        }

        // Layer 2: dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.55f),
                            0.4f to Color.Black.copy(alpha = 0.75f),
                            1.0f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Layer 3: content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "PLAYLIST",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { /* TODO: rename / delete menu */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.MoreVertical,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrollable content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
                )
            ) {
                // Cover
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .shadow(20.dp, RoundedCornerShape(20.dp))
                            .background(CoralColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (livePlaylist.coverUri != null) {
                            AsyncImage(
                                model = livePlaylist.coverUri,
                                contentDescription = "Playlist cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (songsInPlaylist.isNotEmpty()) {
                            // Use the first song's album art as the cover
                            val firstSongArt = songsInPlaylist.first().albumArtUri
                            if (firstSongArt != null) {
                                AsyncImage(
                                    model = firstSongArt,
                                    contentDescription = "Playlist cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = CoralIcons.Music,
                                    contentDescription = null,
                                    tint = Color(0xFF444444),
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = CoralIcons.Music,
                                contentDescription = null,
                                tint = Color(0xFF444444),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }
                }

                // Title + count
                item {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        Text(
                            text = livePlaylist.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${songsInPlaylist.size} ${if (songsInPlaylist.size == 1) "song" else "songs"}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }

                // Action row: Play all | Shuffle | Add songs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionButton(
                            text = "Play all",
                            icon = CoralIcons.Play,
                            isPrimary = true,
                            enabled = songsInPlaylist.isNotEmpty(),
                            onClick = { onPlayAll(songsInPlaylist) },
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            text = "Shuffle",
                            icon = CoralIcons.Shuffle,
                            isPrimary = false,
                            enabled = songsInPlaylist.isNotEmpty(),
                            onClick = { onShuffle(songsInPlaylist) },
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            text = "Add",
                            icon = CoralIcons.Queue,
                            isPrimary = false,
                            enabled = true,
                            onClick = onAddSongsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Songs list
                if (songsInPlaylist.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🎵", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No songs yet",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap \"Add\" to pick songs from your library.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(songsInPlaylist, key = { it.id }) { song ->
                        PlaylistSongRow(
                            song = song,
                            isCurrent = currentSongTitle == song.title,
                            onClick = { onSongClick(song, songsInPlaylist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isPrimary) Color(0xFFFF6B6B).copy(alpha = if (enabled) 1f else 0.4f)
                else Color.White.copy(alpha = if (enabled) 0.12f else 0.05f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isPrimary) Color.White else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = if (isPrimary) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PlaylistSongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) Color(0xFFFF6B6B) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val totalSec = song.duration / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        Text(
            text = "$mm:${String.format("%02d", ss)}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
