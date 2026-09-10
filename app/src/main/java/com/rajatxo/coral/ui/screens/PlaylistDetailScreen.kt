package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Playlist detail screen — SimpMusic-inspired immersive design.
 *
 * Layout (top to bottom):
 *   1. Blurred album art background (full screen)
 *   2. Dark gradient overlay for readability
 *   3. Top bar: back arrow (left) + 3-dot menu (right)
 *   4. Large square cover (centered, rounded corners, shadow)
 *   5. Playlist name (large, bold, centered)
 *   6. "Your Playlist" subtitle
 *   7. Creation date (grey, small)
 *   8. Action row: Shuffle (circle) | Play (white pill) | Search (circle)
 *   9. Sort bar pill: "Sort by: Custom Order" + song count badge
 *  10. Song list: album art | title + artist | duration
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
    onAddSongsClick: () -> Unit,
    onDeletePlaylist: () -> Unit
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    val livePlaylist = playlists.firstOrNull { it.id == playlist.id } ?: playlist

    val songsInPlaylist = remember(livePlaylist, allSongs) {
        val songMap = allSongs.associateBy { it.id }
        livePlaylist.songIds.mapNotNull { songMap[it] }
    }

    // Cover art: custom cover (from gallery) or first song's album art
    val coverArtUri = livePlaylist.coverUri ?: songsInPlaylist.firstOrNull()?.albumArtUri?.toString()

    val dateFormat = remember { SimpleDateFormat("HH:mm – dd MMMM yyyy", Locale.getDefault()) }
    val createdText = remember(livePlaylist.createdAtMs) {
        dateFormat.format(Date(livePlaylist.createdAtMs))
    }

    // --- Extract dominant color from cover image for immersive background ---
    val context = androidx.compose.ui.platform.LocalContext.current
    var dominantColor by remember { mutableStateOf<Color?>(null) }
    androidx.compose.runtime.LaunchedEffect(coverArtUri) {
        if (coverArtUri != null) {
            try {
                val uri = android.net.Uri.parse(coverArtUri)
                com.rajatxo.coral.util.extractPalette(context, uri)?.let { palette ->
                    dominantColor = palette.primary
                }
            } catch (_: Exception) { }
        }
    }

    // 3-dot menu popup state
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Image picker for custom playlist cover
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            PlaylistStore.setPlaylistCover(livePlaylist.id, uri.toString())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Layer 1: Cover image fills entire screen (sharp, not blurred) ---
        if (coverArtUri != null) {
            AsyncImage(
                model = coverArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Layer 2: Immersive gradient — cover → dominant color → near-black ---
        // Top 25%: cover image visible (transparent)
        // 25-50%: blends into dominant color
        // 50-90%: solid dominant color (for song list readability)
        // 90-100%: fades to near-black (for nav bar readability)
        val bgBase = dominantColor ?: Color(0xFF1A1A1A)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.20f to Color.Transparent,
                            0.35f to bgBase.copy(alpha = 0.5f),
                            0.50f to bgBase.copy(alpha = 0.9f),
                            0.60f to bgBase,
                            0.90f to bgBase,
                            1.0f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // --- Layer 3: Content (everything scrolls, including top bar) ---
        // No statusBarsPadding/navigationBarsPadding here — the LazyColumn
        // fills the ENTIRE screen so the blurred background extends behind
        // both the status bar and the navigation buttons.
        // Spacing for status bar / nav bar is handled via contentPadding.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 48.dp, bottom = 80.dp
            )
        ) {
            // Top bar (scrolls with content)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button — no circle background, ChevronLeft icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBackClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.ChevronLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // 3-dot menu — no circle background, Ellipsis icon
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showMenu = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CoralIcons.Ellipsis,
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Dropdown menu — small rounded square
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1A1A))
                        ) {
                            // Playlist cover option
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            showMenu = false
                                            coverPicker.launch("image/*")
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = CoralIcons.Music,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Playlist cover",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Delete playlist option
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = CoralIcons.Heart,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Delete playlist",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
                // Cover box removed — cover image is now the full-screen
                // immersive background. Just add spacing so the title
                // appears below the cover image area.
                item {
                    Spacer(modifier = Modifier.height(180.dp))
                }

                // Playlist name (Cal Sans) + subtitle/date (Poppins)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = livePlaylist.name,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your Playlist",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Created at $createdText",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Action row: Shuffle | Play (3D glossy pill) | Search
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle (circular grey button — new Lucide icon)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = songsInPlaylist.isNotEmpty(),
                                    onClick = { onShuffle(songsInPlaylist) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CoralIcons.ShuffleLucide,
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Play — narrow glossy pill (wider but not tall)
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .width(130.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.5f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.08f)
                                            )
                                        ),
                                        size = size
                                    )
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = songsInPlaylist.isNotEmpty(),
                                    onClick = { onPlayAll(songsInPlaylist) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = CoralIcons.PlayLucide,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Play",
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Add/Search (circular grey button)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onAddSongsClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CoralIcons.Search,
                                contentDescription = "Add songs",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Sort bar — narrower, shorter, proper capsule
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sort by: Custom Order",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        // Song count badge — proper capsule (fully rounded)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${songsInPlaylist.size}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Song list
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
                                text = "Tap the search icon to add songs.",
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

            // Delete confirmation dialog
        if (showDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = CoralColors.SurfaceVariant,
                titleContentColor = Color.White,
                title = { Text("Delete playlist") },
                text = {
                    Text(
                        text = "Are you sure you want to delete \"${livePlaylist.name}\"? This cannot be undone.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            PlaylistStore.deletePlaylist(livePlaylist.id)
                            onDeletePlaylist()
                        }
                    ) {
                        Text("Delete", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showDeleteConfirm = false }
                    ) {
                        Text("Cancel", color = Color(0xFF888888))
                    }
                }
            )
        }
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art thumbnail
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + artist — both Poppins, same size (14sp), white + gray
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        val totalSec = song.duration / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        Text(
            text = "$mm:${String.format("%02d", ss)}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
    }
}
