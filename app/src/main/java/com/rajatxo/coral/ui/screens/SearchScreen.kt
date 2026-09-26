package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.data.prefs.SearchHistory
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * SearchScreen — premium search with playlist commands + history + pins.
 *
 * Features:
 *   • Type "p." prefix to search playlists instead of songs
 *   • Search history (last 20 searches, persisted)
 *   • Pin up to 5 searches (persisted)
 *   • Onboarding guide on first launch
 *   • Results as glossy capsule pills (same design as Songs tab)
 *   • Bottom fade gradient
 */
@Composable
fun SearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val history by SearchHistory.history.collectAsState()
    val pinned by SearchHistory.pinned.collectAsState()
    val guideShown by SearchHistory.guideShown.collectAsState()
    var showGuide by remember { mutableStateOf(!guideShown) }

    val playlists by PlaylistStore.playlists.collectAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // ─── Determine search mode ──────────────────────────────────────
    // "p." prefix = playlist search. Otherwise = song search.
    val isPlaylistSearch = query.startsWith("p.", ignoreCase = true)
    val actualQuery = if (isPlaylistSearch) query.removePrefix("p.").removePrefix("P.").trim() else query.trim()

    val songResults = remember(query, songs) {
        if (isPlaylistSearch || actualQuery.isBlank()) emptyList()
        else {
            val lowerQuery = actualQuery.lowercase()
            songs.filter {
                it.title.lowercase().contains(lowerQuery) ||
                it.artist.lowercase().contains(lowerQuery) ||
                it.album.lowercase().contains(lowerQuery)
            }
        }
    }

    val playlistResults = remember(query, playlists) {
        if (!isPlaylistSearch || actualQuery.isBlank()) emptyList()
        else {
            val lowerQuery = actualQuery.lowercase()
            playlists.filter {
                it.name.lowercase().contains(lowerQuery) ||
                it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
            }
        }
    }

    val hasQuery = actualQuery.isNotBlank()
    val hasResults = songResults.isNotEmpty() || playlistResults.isNotEmpty()

    val darkBase = Color(0xFF05050A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF1A1A2E).copy(alpha = 0.6f),
                        0.3f to darkBase.copy(alpha = 0.8f),
                        1.0f to darkBase
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Search bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.size(12.dp))

                // Search field — changes border color in playlist mode
                val fieldBorderColor = if (isPlaylistSearch) Color(0xFFFF6B6B).copy(alpha = 0.4f)
                    else Color.White.copy(alpha = 0.12f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, fieldBorderColor, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Icon changes based on mode
                        Icon(
                            imageVector = if (isPlaylistSearch) CoralIcons.ListMusic else CoralIcons.Search,
                            contentDescription = null,
                            tint = if (isPlaylistSearch) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search songs... or type p. for playlists",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 13.sp,
                                    fontFamily = CalSansFamily
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = CalSansFamily
                                ),
                                cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        // Clear button
                        if (query.isNotEmpty()) {
                            Spacer(Modifier.size(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { query = "" }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ─── Mode badge ─────────────────────────────────────────
            if (isPlaylistSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PLAYLIST MODE",
                            color = Color(0xFFFF6B6B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CalSansFamily
                        )
                    }
                }
            }

            // ─── Content ────────────────────────────────────────────
            when {
                // ─── Onboarding guide (first launch) ───────────────
                !hasQuery && showGuide -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
                    ) {
                        item { GuideCard(onGotIt = {
                            showGuide = false
                            SearchHistory.markGuideShown()
                        }) }
                        item { Spacer(Modifier.height(20.dp)) }
                        // Pinned searches
                        if (pinned.isNotEmpty()) {
                            item {
                                Text("Pinned", color = Color.White.copy(0.5f), fontSize = 13.sp,
                                    fontFamily = CalSansFamily, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            items(pinned) { entry ->
                                HistoryChip(entry, onClick = { query = if (entry.isPlaylist) "p.${entry.query}" else entry.query })
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                        // Recent history
                        if (history.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recent", color = Color.White.copy(0.5f), fontSize = 13.sp, fontFamily = CalSansFamily)
                                    Spacer(Modifier.weight(1f))
                                    Text("Clear all", color = Color(0xFFFF6B6B).copy(0.7f), fontSize = 12.sp,
                                        fontFamily = CalSansFamily,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null
                                        ) { SearchHistory.clearHistory() }
                                    )
                                }
                            }
                            items(history) { entry ->
                                HistoryChip(entry, onClick = { query = if (entry.isPlaylist) "p.${entry.query}" else entry.query })
                            }
                        }
                    }
                }

                // ─── Empty state (no query, guide already shown) ───
                !hasQuery -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
                    ) {
                        // Pinned
                        if (pinned.isNotEmpty()) {
                            item {
                                Text("Pinned", color = Color.White.copy(0.5f), fontSize = 13.sp,
                                    fontFamily = CalSansFamily, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            items(pinned) { entry ->
                                HistoryChip(entry, onClick = { query = if (entry.isPlaylist) "p.${entry.query}" else entry.query })
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                        // Recent
                        if (history.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recent", color = Color.White.copy(0.5f), fontSize = 13.sp, fontFamily = CalSansFamily)
                                    Spacer(Modifier.weight(1f))
                                    Text("Clear all", color = Color(0xFFFF6B6B).copy(0.7f), fontSize = 12.sp,
                                        fontFamily = CalSansFamily,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null
                                        ) { SearchHistory.clearHistory() }
                                    )
                                }
                            }
                            items(history) { entry ->
                                HistoryChip(entry, onClick = { query = if (entry.isPlaylist) "p.${entry.query}" else entry.query })
                            }
                        }
                        // Empty hint
                        if (history.isEmpty() && pinned.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                                .background(Color.Black.copy(0.3f))
                                                .border(1.dp, Color.White.copy(0.1f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(CoralIcons.Search, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(32.dp))
                                        }
                                        Spacer(Modifier.height(20.dp))
                                        Text("Search your library", color = Color.White.copy(0.5f),
                                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Type p. to search playlists", color = Color.White.copy(0.3f),
                                            fontSize = 13.sp, fontFamily = CalSansFamily)
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── No results ────────────────────────────────────
                hasQuery && !hasResults -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No results", color = Color.White.copy(0.5f),
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
                            Spacer(Modifier.height(6.dp))
                            Text(if (isPlaylistSearch) "No playlists match \"${actualQuery}\"" else "No songs match \"${actualQuery}\"",
                                color = Color.White.copy(0.3f), fontSize = 13.sp, fontFamily = CalSansFamily)
                        }
                    }
                }

                // ─── Results ────────────────────────────────────────
                hasQuery && hasResults -> {
                    // Save to history when results are shown
                    LaunchedEffect(actualQuery, isPlaylistSearch) {
                        SearchHistory.addSearch(actualQuery, isPlaylistSearch)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Count header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val count = if (isPlaylistSearch) playlistResults.size else songResults.size
                                Text("$count result${if (count != 1) "s" else ""}",
                                    color = Color.White.copy(0.5f), fontSize = 13.sp, fontFamily = CalSansFamily)
                                Spacer(Modifier.weight(1f))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF6B6B)))
                            }
                        }

                        if (isPlaylistSearch) {
                            items(playlistResults, key = { it.id }) { playlist ->
                                PlaylistResultCapsule(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist) }
                                )
                            }
                        } else {
                            items(songResults, key = { it.id }) { song ->
                                SearchResultCapsule(
                                    song = song,
                                    onClick = { onSongClick(song) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Bottom fade gradient ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.3f to darkBase.copy(alpha = 0.6f),
                            0.7f to darkBase.copy(alpha = 0.95f),
                            1.0f to darkBase.copy(alpha = 1f)
                        )
                    )
                )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// GUIDE CARD — onboarding for first launch
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GuideCard(onGotIt: () -> Unit) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), cardShape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(CoralIcons.Search, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(10.dp))
            Text("Search Tips", color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
        }
        Spacer(Modifier.height(16.dp))

        // Tip 1: Song search
        GuideTipRow(
            icon = CoralIcons.Music,
            title = "Search songs",
            subtitle = "Type anything — title, artist, or album name",
            example = "e.g. \"autumn\" or \"zleepyfred\""
        )
        Spacer(Modifier.height(14.dp))

        // Tip 2: Playlist search
        GuideTipRow(
            icon = CoralIcons.ListMusic,
            title = "Search playlists",
            subtitle = "Type p. followed by your query",
            example = "e.g. \"p. workout\" or \"p. vibes\""
        )
        Spacer(Modifier.height(14.dp))

        // Tip 3: Pin searches
        GuideTipRow(
            icon = CoralIcons.Pin,
            title = "Pin searches",
            subtitle = "Long-press a search to pin it for quick access",
            example = "Up to 5 pinned searches"
        )
        Spacer(Modifier.height(20.dp))

        // Got it button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onGotIt
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Got it", color = Color(0xFFFF6B6B), fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = CalSansFamily)
        }
    }
}

@Composable
private fun GuideTipRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    example: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, fontFamily = CalSansFamily)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                fontFamily = CalSansFamily)
            Text(example, color = Color(0xFFFF6B6B).copy(alpha = 0.6f), fontSize = 11.sp,
                fontFamily = CalSansFamily, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HISTORY CHIP — recent/pinned search entry
// ════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryChip(
    entry: SearchHistory.SearchEntry,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(20.dp)
    val isPinned = SearchHistory.isPinned(entry.query, entry.isPlaylist)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(chipShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), chipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon based on search type
        Icon(
            imageVector = if (entry.isPlaylist) CoralIcons.ListMusic else CoralIcons.Music,
            contentDescription = null,
            tint = if (entry.isPlaylist) Color(0xFFFF6B6B).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = entry.query,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontFamily = CalSansFamily,
            modifier = Modifier.weight(1f)
        )
        // Pin/unpin toggle
        if (isPinned) {
            Icon(
                imageVector = CoralIcons.Pin,
                contentDescription = "Unpin",
                tint = Color(0xFFFF6B6B),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { SearchHistory.togglePin(entry.query, entry.isPlaylist) }
                    )
            )
        } else {
            Icon(
                imageVector = CoralIcons.Pin,
                contentDescription = "Pin",
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { SearchHistory.togglePin(entry.query, entry.isPlaylist) }
                    )
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SEARCH RESULT CAPSULE (songs)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultCapsule(
    song: Song,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(pillShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Glossy overlay
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.06f),
                        0.5f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.02f)
                    )
                ))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular album art
            Box(modifier = Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                    )
                } else {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center) {
                        Icon(CoralIcons.Music, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, fontFamily = CalSansFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} • ${song.album}", color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp, fontFamily = CalSansFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val mins = song.duration / 1000 / 60
            val secs = (song.duration / 1000) % 60
            Text(String.format(java.util.Locale.US, "%d:%02d", mins, secs),
                color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = CalSansFamily)
            Spacer(Modifier.size(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PLAYLIST RESULT CAPSULE
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistResultCapsule(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(pillShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.15f), pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Glossy overlay
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFFF6B6B).copy(alpha = 0.04f),
                        0.5f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.02f)
                    )
                ))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist icon circle
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(CoralIcons.ListMusic, null, tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, fontFamily = CalSansFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${playlist.songIds.size} song${if (playlist.songIds.size != 1) "s" else ""}" +
                        if (playlist.tags.isNotEmpty()) " • ${playlist.tags.joinToString(", ")}" else "",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    fontFamily = CalSansFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
