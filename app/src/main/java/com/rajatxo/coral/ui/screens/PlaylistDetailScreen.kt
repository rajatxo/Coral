package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusRequester
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as kyantBlur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
    // ★ CRASH FIX: if playlist was deleted, return immediately (don't access .id/.name)
    val livePlaylist = playlists.firstOrNull { it.id == playlist.id }
    if (livePlaylist == null) {
        // Playlist no longer exists — call onDeletePlaylist to dismiss this screen
        // Use LaunchedEffect to avoid calling during composition
        androidx.compose.runtime.LaunchedEffect(Unit) { onDeletePlaylist() }
        return
    }

    val songsInPlaylist = remember(livePlaylist, allSongs) {
        val songMap = allSongs.associateBy { it.id }
        livePlaylist.songIds.mapNotNull { songMap[it] }
    }

    // Cover art: custom cover (from gallery) or first song's album art
    val coverArtUri = livePlaylist.coverUri ?: songsInPlaylist.firstOrNull()?.albumArtUri?.toString()

    // ★ COLLAGE: first 4 songs' album art for the detail screen cover
    val collageThumbs = remember(songsInPlaylist) {
        songsInPlaylist.take(4).mapNotNull { it.albumArtUri?.toString() }
    }

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
    var showRenameDialog by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // ★ Auto-open keyboard when search is activated
    androidx.compose.runtime.LaunchedEffect(isSearching) {
        if (isSearching) {
            kotlinx.coroutines.delay(100)
            searchFocusRequester.requestFocus()
        }
    }

    // ★ Search filter
    val displaySongs = remember(songsInPlaylist, searchQuery, isSearching) {
        if (isSearching && searchQuery.isNotBlank()) {
            songsInPlaylist.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        } else songsInPlaylist
    }

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

    // --- Immersive background color ---
    // Darken the dominant color based on its luminance so white text stays readable.
    // Lighter artwork → darker background. This is the SimpMusic approach.
    val immersiveColor = remember(dominantColor) {
        val base = dominantColor ?: Color(0xFF1A1A1A)
        val luminance = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
        val darkenFactor = 0.35f + 0.45f * luminance
        androidx.compose.ui.graphics.lerp(base, Color.Black, darkenFactor)
    }

    // --- Smoothstep scrim brush ---
    // The gradient must be FULLY OPAQUE by the time it reaches the image's
    // bottom edge (42% of screen). The ramp goes from 5% to 42%.
    // Above 5%: transparent (cover visible). At 42%: fully opaque (image
    // edge completely hidden). Below 42%: solid immersive color.
    fun smoothScrimBrush(
        color: Color,
        startFraction: Float = 0.05f,
        endFraction: Float = 0.42f,
        steps: Int = 32
    ): Brush {
        val from = color.copy(alpha = 0f)
        return Brush.verticalGradient(
            colorStops = Array(steps + 1) { i ->
                val t = i / steps.toFloat()
                val position = startFraction + (endFraction - startFraction) * t
                val eased = t * t * (3f - 2f * t)  // smoothstep
                position to androidx.compose.ui.graphics.lerp(from, color, eased)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(immersiveColor)) {

        // ★ LOCAL kyant backdrop (same pattern as SettingsScreen)
        //   Captures the cover image + gradient so the 3-dot menu card
        //   can blur it with drawBackdrop.
        val detailGraphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
        val detailBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
            graphicsLayer = detailGraphicsLayer
        ) {
            drawContent()
        }

        // ★ Layer 1+2 wrapped in layerBackdrop Box for kyant capture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(detailBackdrop)
        ) {
        // --- Layer 1: Cover image at the top (fixed) ---
        // ★ HIDDEN when searching — search bar replaces the immersive layout
        if (!isSearching && coverArtUri != null) {
            AsyncImage(
                model = coverArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
            )
        }

        // --- Layer 2: Full-screen smoothstep scrim ---
        // ★ HIDDEN when searching — solid immersive color background instead
        if (!isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(smoothScrimBrush(immersiveColor, 0.05f, 0.42f, 32))
            )
        }

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
                        // ★ 3-dot menu button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showMenu = !showMenu }
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
                }
            }
                // ★ HIDE header items when searching — just show search bar + results
                if (!isSearching) {
                // Cover box removed — cover image is the immersive background
                // (top 42% of screen). Spacer pushes title below the cover area.
                item {
                    Spacer(modifier = Modifier.height(200.dp))
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
                                    onClick = {
                                        isSearching = !isSearching
                                        if (!isSearching) searchQuery = ""
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSearching) CoralIcons.Close else CoralIcons.Search,
                                contentDescription = if (isSearching) "Close search" else "Search",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                } // ← closes if (!isSearching) — header items hidden during search

                // ★ Search bar (visible only when searching)
                if (isSearching) {
                    item {
                        androidx.compose.material3.TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Search in playlist...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                // Sort bar — small, narrow, centered capsule
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Sort by: Custom Order",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Song count badge — tiny capsule
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${displaySongs.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Song list
                if (displaySongs.isEmpty()) {
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
                                text = if (isSearching && searchQuery.isNotBlank()) "No results" else "No songs yet",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!isSearching) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the search icon to add songs.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    items(displaySongs, key = { it.id }) { song ->
                        PlaylistSongRow(
                            song = song,
                            isCurrent = currentSongTitle == song.title,
                            onClick = { onSongClick(song, songsInPlaylist) }
                        )
                    }
                }
            }
        } // ← closes layerBackdrop Box

        // ★ Glass menu card (kyant backdrop) — shown when showMenu is true
        //   Positioned at top-right, below the 3-dot button.
        //   Uses drawBackdrop to blur the album art behind it.
        if (showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showMenu = false }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 60.dp, end = 16.dp)
                        .width(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .drawBackdrop(
                            backdrop = detailBackdrop,
                            shape = { RoundedCornerShape(16.dp) },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = 0.05f,
                                    contrast = 1f,
                                    saturation = 1.2f
                                )
                                kyantBlur(20f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.4f)) }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(vertical = 8.dp)
                ) {
                    // Rename option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = CoralIcons.FilePenLine, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(text = "Rename", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    // Playlist cover option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    showMenu = false
                                    coverPicker.launch("image/*")
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = CoralIcons.Wallpaper, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(text = "Playlist cover", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    // Delete playlist option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = CoralIcons.Heart, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                        Text(text = "Delete playlist", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

    } // ← closes outer Box

    // ★ Rename dialog
    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(livePlaylist.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = CoralColors.SurfaceVariant,
            titleContentColor = Color.White,
            title = { Text("Rename playlist") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            PlaylistStore.renamePlaylist(livePlaylist.id, renameText.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showRenameDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
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
                            // ★ CRASH FIX: dismiss THIS screen FIRST, then delete from store.
                            //   If we delete first, livePlaylist becomes null during
                            //   recomposition → crash accessing .id/.name.
                            onDeletePlaylist()
                            PlaylistStore.deletePlaylist(livePlaylist.id)
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
