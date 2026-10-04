package com.rajatxo.coral.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 * SearchScreen — billing/receipt styled search page.
 *
 * STRUCTURE (the "billing" skeleton — content inside the paper comes later):
 *   ┌───────────────────────────────────────┐
 *   │ Search bar  (back • field • clear)    │  ← screen header
 *   ├───────────────────────────────────────┤
 *   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  ← dark printer slot
 *   │  ──────────────────  (slot opening)    │
 *   │┌───────────────────────────────────┐  │
 *   ││                                   │  │  ← cream paper emerging
 *   ││   (content goes here — later)      │  │     from under the slot
 *   ││                                   │  │
 *   ││                                   │  │
 *   ││  ████  ████  ████  ████  ████  │  │  ← jagged torn bottom edge
 *   │└───────────────────────────────────┘  │
 *   └───────────────────────────────────────┘
 *
 * The paper slides DOWN out of the slot on screen entry
 * (spring animation), mirroring the reference video. The
 * dark printer slot stays fixed; the paper's top edge is
 * visually tucked under the slot.
 *
 * TODO (later): put search text + results + history inside
 * the paper. For now the inside is an empty placeholder.
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

    // ─── History save logic ─────────────────────────────────────────
    //
    // Bug fix: previously a LaunchedEffect(actualQuery) was saving every
    //   intermediate keystroke (R → Ra → Raj → Raja → Rajat all ended up
    //   in history).
    //
    // Now history is saved ONLY when:
    //   1. User explicitly submits — taps the search icon in the field OR
    //      presses the keyboard's IME Search action → saves the typed query.
    //   2. User clicks a result → saves the *result's full name* instead
    //      of the partial query. e.g. typing "Slo" and tapping "Slow Down"
    //      saves "Slow Down" (and the artist "Chase Atlantic") to history.
    //   3. User clicks a playlist result → saves the playlist's full name.
    //
    val submitSearch: () -> Unit = {
        val trimmed = actualQuery.trim()
        if (trimmed.isNotBlank()) {
            SearchHistory.addSearch(trimmed, isPlaylistSearch)
            keyboardController?.hide()
        }
    }

    val onSongResultClick: (Song) -> Unit = { song ->
        // Auto-complete: save the song's full title (not the partial query).
        SearchHistory.addSearch(song.title, isPlaylist = false)
        // Also save the artist name — if the user was searching for the
        // artist (e.g. typed "Cha" looking for "Chase Atlantic"), the
        // artist's full name ends up in history too.
        if (song.artist.isNotBlank()) {
            SearchHistory.addSearch(song.artist, isPlaylist = false)
        }
        onSongClick(song)
    }

    val onPlaylistResultClick: (Playlist) -> Unit = { playlist ->
        // Auto-complete: save the playlist's full name (not "p.vib" → "Vibes").
        SearchHistory.addSearch(playlist.name, isPlaylist = true)
        onPlaylistClick(playlist)
    }

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
                        // Search icon — tappable to submit the current query
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { submitSearch() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaylistSearch) CoralIcons.ListMusic else CoralIcons.Search,
                                contentDescription = "Search",
                                tint = if (isPlaylistSearch) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { submitSearch() }
                                ),
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

            // ═══ BILLING STRUCTURE ════════════════════════════════════════
            // Dark printer slot at top, cream paper emerging downward with
            // a jagged torn bottom edge. The inside of the paper is left
            // empty for now (placeholder) — search text / results / history
            // will go inside later.
            //
            // The paper slides DOWN out of the slot on screen entry,
            // mirroring the reference video. The slot stays put; only
            // the paper's visible height grows.
            BillingPaperStructure(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // ─── Content INSIDE the paper (placeholder for now) ─────
                // TODO (later): move the `when { ... }` content (guide /
                //   empty / no-results / results) in here, themed as ink
                //   on cream paper instead of dark capsule pills.
                PaperContentPlaceholder()
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

// ════════════════════════════════════════════════════════════════════
// BILLING STRUCTURE — printer slot + paper emerging + jagged bottom
// ════════════════════════════════════════════════════════════════════
// Visual layout (vertical):
//
//   ┌─────────────────────────────────────────────┐
//   │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  │ ← dark printer slot
//   │   ───────────────────────────────────────   │   (with slot opening line)
//   │ ┌─────────────────────────────────────────┐ │
//   │ │                                         │ │
//   │ │   (paper content area — empty for now)  │ │ ← cream paper
//   │ │                                         │ │
//   │ │  ▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼  │ │ ← jagged torn bottom
//   │ └─────────────────────────────────────────┘ │
//   └─────────────────────────────────────────────┘
//
// The paper slides DOWN out of the slot on first composition
// (spring animation, no bouncy). The slot stays fixed.

/**
 * Outer container for the billing-style search results area.
 *
 * Composes a [PrinterSlot] at the top (always visible) and a
 * [ReceiptPaper] below it that animates its reveal on entry.
 *
 * @param content  Composable rendered INSIDE the paper. For now
 *                  this is [PaperContentPlaceholder] — search
 *                  text + results will go here later.
 */
@Composable
private fun BillingPaperStructure(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Reveal fraction: 0 → paper fully tucked under slot, 1 → fully out.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        val slotHeight = 44.dp
        val slotHeightPx = with(LocalDensity.current) { slotHeight.toPx() }
        // Paper reveals from slotHeight downward; at reveal=0 paper has
        // ~0 visible height; at reveal=1 paper fills the rest of the box.
        val visiblePaperHeightPx = (fullHeightPx - slotHeightPx).coerceAtLeast(0f) * reveal.value

        Box(modifier = Modifier.fillMaxSize()) {
            // ─── Paper (under the slot, grows downward on reveal) ───
            // Anchored to the top so that as visible height grows from
            // 0, the paper appears to slide out from under the slot.
            ReceiptPaper(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = slotHeight)
                    .height(
                        with(LocalDensity.current) {
                            visiblePaperHeightPx.toDp()
                        }
                    ),
                content = content
            )

            // ─── Slot (drawn ON TOP of paper's top edge) ───
            PrinterSlot(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

// ─── Printer slot ──────────────────────────────────────────────────────

/**
 * The dark printer housing at the top of the billing structure.
 * Visually sits on top of the paper's top edge so the paper
 * appears to emerge from under it.
 *
 * Drawn as a dark rounded rectangle with:
 *   • A subtle top-to-bottom gradient (lighter at top edge).
 *   • A thin "slot opening" line near the bottom of the housing
 *     (the slit the paper comes out of).
 *   • A soft drop shadow underneath (cast onto the paper below).
 */
@Composable
private fun PrinterSlot(modifier: Modifier = Modifier) {
    val slotColor = Color(0xFF1A1A1F)
    val slotColorBottom = Color(0xFF0B0B10)
    val slotOpeningColor = Color(0xFF33333D)
    val shadowColor = Color.Black.copy(alpha = 0.45f)

    Box(modifier = modifier) {
        // Drop shadow cast onto the paper below (a thin band, fading down).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to shadowColor,
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        // Slot housing body (dark, rounded bottom corners only).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to slotColor,
                            0.6f to slotColor,
                            1.0f to slotColorBottom
                        )
                    )
                )
        ) {
            // Top edge highlight (subtle, suggests a 3D rounded top).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )

            // Slot opening — a thin darker slit near the bottom of the
            // housing, indicating where the paper emerges from.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(slotOpeningColor)
            )
        }
    }
}

// ─── Receipt paper ────────────────────────────────────────────────────

/**
 * The cream-colored receipt paper, clipped to a [JaggedBottomShape]
 * so the bottom edge has a torn/zigzag appearance.
 *
 * Content is rendered on top of the cream background — for now it's
 * just [PaperContentPlaceholder]; later this is where search text,
 * results, history etc. will live (themed as ink on paper).
 */
@Composable
private fun ReceiptPaper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val paperColor = Color(0xFFF5EFE0)        // warm cream
    val paperColorEdge = Color(0xFFEBE3CF)    // slightly darker cream
    val jaggedToothWidth = 14.dp
    val jaggedToothHeight = 9.dp

    val shape = remember(jaggedToothWidth, jaggedToothHeight) {
        JaggedBottomShape(
            toothWidthDp = jaggedToothWidth.value,
            toothHeightDp = jaggedToothHeight.value
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to paperColor,
                        0.85f to paperColor,
                        1.0f to paperColorEdge
                    )
                )
            )
    ) {
        // Soft side shadows (give the paper a bit of depth against the
        // dark background — like the paper is hovering slightly).
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.18f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.18f)
                        )
                    )
                )
        )

        // Actual paper content (placeholder for now).
        content()
    }
}

// ─── Jagged bottom edge shape ──────────────────────────────────────────

/**
 * A rectangle with the bottom edge replaced by a zigzag (torn paper)
 * pattern. The top, left and right edges are straight — only the
 * bottom is jagged.
 *
 * @param toothWidthDp   Width of each zigzag tooth in dp.
 * @param toothHeightDp  Height of each zigzag tooth in dp (how deep
 *                        the tear goes up into the paper).
 */
private class JaggedBottomShape(
    private val toothWidthDp: Float,
    private val toothHeightDp: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val toothW = with(density) { toothWidthDp.dp.toPx() }
        val toothH = with(density) { toothHeightDp.dp.toPx() }
        val w = size.width
        val h = size.height
        // Number of teeth that fit across the width (rounded up so the
        // last tooth always reaches the right edge cleanly).
        val toothCount = ((w / toothW).toInt().coerceAtLeast(1))
        // Recompute actual tooth width so the teeth distribute evenly
        // and end exactly at the right edge.
        val actualToothW = w / toothCount

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h - toothH)
            // Zigzag bottom from right → left.
            var x = w
            for (i in 0 until toothCount) {
                val nextX = x - actualToothW
                if (i % 2 == 0) {
                    // Tooth pointing DOWN (paper extends further down).
                    lineTo(nextX, h)
                } else {
                    // Notch pointing UP (tear cuts into the paper).
                    lineTo(nextX, h - toothH)
                }
                x = nextX
            }
            // Close back to start (left edge → top-left corner).
            lineTo(0f, h - toothH)
            close()
        }
        return Outline.Generic(path)
    }
}

// ─── Paper content placeholder ────────────────────────────────────────

/**
 * Empty placeholder rendered inside the receipt paper. Will be
 * replaced with the actual search content (text + results +
 * history) later — themed as ink on cream paper.
 */
@Composable
private fun PaperContentPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Subtle hint text — visible only as a placeholder so it's
        // obvious where content will go. Will be removed once real
        // search text + results are moved in.
        Text(
            text = "• paper content •",
            color = Color(0xFF8C8576),
            fontSize = 11.sp,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Light
        )
    }
}
