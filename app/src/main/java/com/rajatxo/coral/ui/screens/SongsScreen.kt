package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Songs tab — vertical list of every track Coral scanned from MediaStore.
 *
 * Layout:
 *  - 28sp "Songs" header
 *  - "{n} songs" subtitle in muted gray
 *  - LazyColumn of SongRow
 *  - The currently-playing song row gets a coral accent on its title and a
 *    subtle #1A1A1A tinted background so you can see where you are.
 */
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongTitle: String?,
    onSongClick: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header block
        Column(modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)) {
            Text(
                text = "Songs",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                SongRow(
                    song = song,
                    isCurrent = currentSongTitle == song.title,
                    onClick = { onSongClick(song) }
                )
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
            .background(if (isCurrent) CoralColors.SurfaceVariant else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 48x48 album art with rounded corners
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art for ${song.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color(0xFFB0B0B0),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))

        // Title + artist
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
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration mm:ss
        val totalSec = song.duration / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        Text(
            text = "$mm:${String.format("%02d", ss)}",
            color = Color(0xFFB0B0B0),
            fontSize = 13.sp
        )
    }
}
