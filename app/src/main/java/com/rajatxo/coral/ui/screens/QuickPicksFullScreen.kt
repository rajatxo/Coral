package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily

/**
 * Quick Picks Full Screen — placeholder page showing ALL songs.
 *
 * This overlay covers the ENTIRE screen, including the nav rail area.
 * Triggered by the "VIEW ALL" button on the editorial Quick Picks card.
 * Slides in from the bottom (animation handled by HomeScreen's
 * AnimatedVisibility).
 *
 * For now, this is a simple vertical list of all songs. Phase 2 will
 * replace this with a richer layout (filtering, sorting, search, etc).
 *
 * Layout:
 *   Column(fillMaxSize, bg = #0A0E1A) {
 *     Header (statusBarsPadding):
 *       Row: ChevronDown back button (left) + "All picks" title (Playfair Italic)
 *     LazyColumn (weight 1f):
 *       One row per song: 56dp album art + title + artist
 *   }
 */
@Composable
fun QuickPicksFullScreen(
    songs: List<Song>,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button (chevron down)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.ChevronDown,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Title
            Text(
                text = "All picks",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PlayfairItalicFamily
            )
            Spacer(modifier = Modifier.weight(1f))
            // Song count badge
            Text(
                text = "${songs.size}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- Song list ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            items(songs) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
