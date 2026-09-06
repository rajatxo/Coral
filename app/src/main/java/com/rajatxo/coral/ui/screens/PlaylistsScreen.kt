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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Playlists tab.
 *
 * Phase 5 wiring:
 *  - Observes [PlaylistStore.playlists] as a StateFlow.
 *  - Shows a 2-column grid of glass-morphism cards.
 *  - Each card shows: cover (or music-note collage placeholder), name, song count.
 *  - Tap a card -> opens the playlist detail (callback).
 *  - Long-press a card -> delete confirmation (Phase 5.1, not yet wired).
 *  - Floating "+" button at top-right -> shows create dialog.
 *
 * Empty state: shows the glass icon badge + friendly copy.
 */
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        if (playlists.isEmpty()) {
            EmptyPlaylistsState(onCreateClick = { showCreateDialog = true })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 80.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }

            // Header (positioned on top of the grid)
            PlaylistHeader(
                onCreateClick = { showCreateDialog = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                PlaylistStore.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistHeader(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Playlists",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // Create button — glass capsule
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .clickable(onClick = onCreateClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CoralIcons.Play,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "New",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover box (square, glass-tinted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUri != null) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = "Cover for ${playlist.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.ListMusic,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B).copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.songIds.size} ${if (playlist.songIds.size == 1) "song" else "songs"}",
            color = Color(0xFFB0B0B0),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EmptyPlaylistsState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CoralIcons.ListMusic,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Create your first playlist",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Group your favorite tracks into playlists.\nGlass cards, custom covers, the works.",
            color = Color(0xFFB0B0B0),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFF6B6B))
                .clickable(onClick = onCreateClick)
                .padding(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Create Playlist",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CoralColors.SurfaceVariant,
        titleContentColor = Color.White,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name", color = Color(0xFF888888)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) }
            ) {
                Text("Create", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}
