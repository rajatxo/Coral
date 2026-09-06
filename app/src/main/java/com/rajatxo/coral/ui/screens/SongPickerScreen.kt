package com.rajatxo.coral.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
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
 * Modal song picker for adding songs to a playlist.
 *
 * Layout:
 *  - Full screen, dark background (slide-up modal in Phase 5.1)
 *  - Header: title + Done button + selected count
 *  - LazyColumn of all songs not already in the playlist, each row shows:
 *      - checkbox state (filled heart if selected, empty circle if not)
 *      - song title + artist
 *  - Multi-select; tap row to toggle selection
 *  - Done button calls PlaylistStore.addSongsToPlaylist() and dismisses
 */
@Composable
fun SongPickerScreen(
    playlist: Playlist,
    allSongs: List<Song>,
    onDone: () -> Unit
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    val livePlaylist = playlists.firstOrNull { it.id == playlist.id } ?: playlist

    val initialSelected = remember(livePlaylist.id) { livePlaylist.songIds.toSet() }
    var selected by remember { mutableStateOf(initialSelected) }

    val songsNotInPlaylist = remember(allSongs, livePlaylist) {
        val inPlaylist = livePlaylist.songIds.toSet()
        allSongs.filter { it.id !in inPlaylist }
    }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add songs",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF6B6B))
                        .clickable {
                            val newlyAdded = selected - initialSelected
                            if (newlyAdded.isNotEmpty()) {
                                PlaylistStore.addSongsToPlaylist(
                                    playlistId = playlist.id,
                                    songIds = newlyAdded.toList()
                                )
                            }
                            onDone()
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Done",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val added = selected.size - initialSelected.size
                    if (added > 0) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "+$added",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text(
                text = "${songsNotInPlaylist.size} ${if (songsNotInPlaylist.size == 1) "song" else "songs"} available",
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Song list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songsNotInPlaylist, key = { it.id }) { song ->
                    PickerRow(
                        song = song,
                        isSelected = song.id in selected,
                        onToggle = {
                            selected = if (song.id in selected) {
                                selected - song.id
                            } else {
                                selected + song.id
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isSelected) Color(0xFFFF6B6B) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Selection indicator
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isSelected) Color(0xFFFF6B6B)
                    else Color.White.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = CoralIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
