package com.rajatxo.coral.ui.cynthia

import androidx.compose.runtime.Composable
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.domain.model.Song

/**
 * CynthiaSearchScreen — TEMPORARY wrapper that delegates to Astra's SearchScreen.
 *
 * The user wants the same search functionality as Astra (search songs, type
 * "p." for playlists, history, pins) so they can actually search songs in
 * Cynthia UI. The custom billing-style search UI will be built later.
 *
 * This wrapper just passes through to Astra's SearchScreen with the right
 * callbacks so the search screen closes when a song/playlist is tapped.
 */
@Composable
fun CynthiaSearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onDismiss: () -> Unit
) {
    com.rajatxo.coral.ui.screens.SearchScreen(
        songs = songs,
        onSongClick = onSongClick,
        onPlaylistClick = onPlaylistClick,
        onDismiss = onDismiss
    )
}
