package com.rajatxo.coral.ui.cynthia

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SearchCardCustomization
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.RippleDismissContainer
import com.rajatxo.coral.ui.screens.rippleFadeOut
import com.rajatxo.coral.ui.theme.CalSansFamily

private const val SEARCH_SATURATION = 1.6f
private const val SEARCH_BRIGHTNESS = 0.08f

/**
 * ★ CynthiaSearchScreen — glass card from top with inline customization panel.
 *
 * Features:
 *   - Card from top, rounded bottom corners
 *   - "Search" text centered, 3-dot menu icon on left
 *   - Tap menu → customization panel fades IN inside the card (no separate card)
 *   - Customization: card height (expand), blur value, darkness value, corner roundness
 *   - Arc dial + haptics + sound (same as nav bar customization)
 *   - Tap outside → ripple + card fades together
 */
@Composable
fun CynthiaSearchScreen(
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
    isMiniPlayerVisible: Boolean = false,
    songs: List<com.rajatxo.coral.domain.model.Song> = emptyList(),
    onSongClick: (com.rajatxo.coral.domain.model.Song) -> Unit = {}
) {
    val config by SearchCardCustomization.config.collectAsState()
    val miniPlayerCustom by com.rajatxo.coral.data.prefs.CynthiaMiniPlayerCustomization
        .customization.collectAsState()

    // ★ Read nav bar position to calculate the exact same gap as miniplayer-nav.

    var showCustomization by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = config.corner.dp, bottomEnd = config.corner.dp
    )

    // ★ Fixed card height — no longer adjusts based on miniplayer visibility.
    //   User wants a constant -171dp offset regardless of playback state.
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val cardHeight = screenHeight + config.heightExtra.dp

    RippleDismissContainer(onDismiss = onDismiss) { progress ->
    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Search glass card ───────────────────────────────────────
        //   ★ rippleFadeOut MUST come BEFORE clip + drawBackdrop + border
        //   so graphicsLayer wraps the ENTIRE card (glass + border + content).
        //   If placed after, only the inner content fades — the glass stays
        //   visible until the ripple finishes. Same lesson as TodaysTopCard.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(cardHeight)
                .rippleFadeOut(progress)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(cardShape)
                .background(Color(0xFF0A0A0F))
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = SEARCH_BRIGHTNESS,
                                    contrast = 1f,
                                    saturation = SEARCH_SATURATION
                                )
                                blur(config.blur.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = config.darkness))
                            }
                        )
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.7f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), cardShape)
                .statusBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ─── Header: menu icon (left) + Search text (center) ──
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 3-dot menu icon (left)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showCustomization = !showCustomization }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.MoreVertical,
                            contentDescription = "Customize",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Search text (center)
                    Text(
                        text = "Search",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Customization panel (fades in/out inside the card) ──
                AnimatedVisibility(
                    visible = showCustomization,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SearchCardCustomizationPanel(
                        config = config,
                        onBlurChange = { SearchCardCustomization.setBlur(it) },
                        onDarknessChange = { SearchCardCustomization.setDarkness(it) },
                        onHeightChange = { SearchCardCustomization.setHeightExtra(it) },
                        onCornerChange = { SearchCardCustomization.setCorner(it) },
                        onReset = { SearchCardCustomization.reset() }
                    )
                }

                // ─── Search content ──────────────────────────────────
                if (!showCustomization) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ★ Search field — auto-focuses keyboard on open
                    var query by remember { mutableStateOf("") }
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                    // ★ Auto-show keyboard when search opens (port from Astra)
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                            // Slight delay so focus completes before keyboard opens
                            kotlinx.coroutines.delay(50)
                            keyboardController?.show()
                        } catch (_: Exception) { }
                    }

                    // ★ Read search history + playlist data
                    val history by com.rajatxo.coral.data.prefs.SearchHistory.history.collectAsState()
                    val pinned by com.rajatxo.coral.data.prefs.SearchHistory.pinned.collectAsState()
                    val guideShown by com.rajatxo.coral.data.prefs.SearchHistory.guideShown.collectAsState()
                    val playlists by com.rajatxo.coral.data.store.PlaylistStore.playlists.collectAsState()

                    // ★ "p." prefix = playlist search. Otherwise = song search.
                    val isPlaylistSearch = query.startsWith("p.", ignoreCase = true)
                    val actualQuery = if (isPlaylistSearch) {
                        query.removePrefix("p.").removePrefix("P.").trim()
                    } else query.trim()

                    // ★ Submit handler — saves query to history + hides keyboard
                    val submitSearch: () -> Unit = {
                        val trimmed = actualQuery.trim()
                        if (trimmed.isNotBlank()) {
                            com.rajatxo.coral.data.prefs.SearchHistory.addSearch(trimmed, isPlaylistSearch)
                            keyboardController?.hide()
                        }
                    }

                    // ★ Song results — filter songs by query (only when NOT playlist search)
                    val songResults = remember(query, songs) {
                        if (isPlaylistSearch || actualQuery.isBlank()) emptyList()
                        else {
                            val lowerQuery = actualQuery.lowercase()
                            songs.filter {
                                it.title.lowercase().contains(lowerQuery) ||
                                it.artist.lowercase().contains(lowerQuery) ||
                                it.album.lowercase().contains(lowerQuery)
                            }.take(20)
                        }
                    }

                    // ★ Playlist results — filter playlists by query (only when playlist search)
                    val playlistResults = remember(query, playlists) {
                        if (!isPlaylistSearch || actualQuery.isBlank()) emptyList()
                        else {
                            val lowerQuery = actualQuery.lowercase()
                            playlists.filter {
                                it.name.lowercase().contains(lowerQuery) ||
                                it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
                            }.take(10)
                        }
                    }

                    // ★ Search field with icon, placeholder, clear button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search icon (left) — tap to submit
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
                            // ★ Placeholder text — long version before guide dismissed,
                            //   short version after.
                            if (query.isEmpty()) {
                                val placeholder = if (!guideShown)
                                    "Search songs... or type p. for playlists"
                                else
                                    "Search songs and playlists"
                                Text(
                                    text = placeholder,
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 13.sp,
                                    fontFamily = CalSansFamily
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = CalSansFamily
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF6B6B)),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = { submitSearch() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        // Clear button (right) — only shows when there's text
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // ★ "p." guide hint — shown once on first install
                    if (!guideShown && !isPlaylistSearch && query.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CoralIcons.ListMusic,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Type p. before your search to find playlists",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontFamily = CalSansFamily,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Got it",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CalSansFamily,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        com.rajatxo.coral.data.prefs.SearchHistory.markGuideShown()
                                    }
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ★ Pinned searches (only shown when query is empty)
                    if (query.isEmpty() && pinned.isNotEmpty()) {
                        Text(
                            text = "Pinned",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CalSansFamily,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp)
                        ) {
                            items(pinned, key = { it.query + it.isPlaylist }) { entry ->
                                PinnedChip(
                                    entry = entry,
                                    onClick = { query = if (entry.isPlaylist) "p. ${entry.query}" else entry.query }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ★ Recent searches (only when query is empty)
                    if (query.isEmpty() && history.isNotEmpty()) {
                        Text(
                            text = "Recent",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CalSansFamily,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // ★ No results message
                    if (query.isNotBlank() && songResults.isEmpty() && playlistResults.isEmpty()) {
                        Text(
                            text = "No results for \"$actualQuery\"",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                    }

                    // ★ Results list — songs + playlists
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Playlist results section
                        if (playlistResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Playlists",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = CalSansFamily,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(playlistResults, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                com.rajatxo.coral.data.prefs.SearchHistory.addSearch(
                                                    playlist.name, isPlaylist = true
                                                )
                                                onSongClick(songs.firstOrNull() ?: return@clickable)
                                                onDismiss()
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A1A1A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CoralIcons.ListMusic,
                                            contentDescription = null,
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontFamily = CalSansFamily,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.songIds.size} songs",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontFamily = CalSansFamily
                                        )
                                    }
                                }
                            }
                        }

                        // Songs section
                        if (songResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Songs",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = CalSansFamily,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(songResults, key = { it.id }) { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                com.rajatxo.coral.data.prefs.SearchHistory.addSearch(
                                                    song.title, isPlaylist = false
                                                )
                                                if (song.artist.isNotBlank()) {
                                                    com.rajatxo.coral.data.prefs.SearchHistory.addSearch(
                                                        song.artist, isPlaylist = false
                                                    )
                                                }
                                                onSongClick(song)
                                                onDismiss()
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1A1A1A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (song.albumArtUri != null) {
                                            coil3.compose.AsyncImage(
                                                model = song.albumArtUri,
                                                contentDescription = null,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = CoralIcons.Music,
                                                contentDescription = null,
                                                tint = Color(0xFFB0B0B0),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontFamily = CalSansFamily,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontFamily = CalSansFamily,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Recent searches (only when query is empty)
                        if (query.isEmpty()) {
                            items(history, key = { it.query + it.isPlaylist }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                query = if (entry.isPlaylist) "p. ${entry.query}" else entry.query
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.isPlaylist) CoralIcons.ListMusic else CoralIcons.Search,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    com.rajatxo.coral.data.prefs.SearchHistory.togglePin(
                                                        entry.query, entry.isPlaylist
                                                    )
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (entry in pinned) CoralIcons.HeartLucideFilled
                                                else CoralIcons.HeartLucide,
                                            contentDescription = "Pin",
                                            tint = if (entry in pinned) Color(0xFFFF6B6B)
                                                else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

// ════════════════════════════════════════════════════════════════════
// INLINE CUSTOMIZATION PANEL — fades inside the search card
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SearchCardCustomizationPanel(
    config: SearchCardCustomization.SearchCardConfig,
    onBlurChange: (Float) -> Unit,
    onDarknessChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onCornerChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // ★ SoundPool + haptics (same as CynthiaCustomizationPanel)
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) soundLoaded = true }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    fun tickHaptic() {
        if (SoundHapticsManager.hapticsEnabled.value) {
            try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
        if (SoundHapticsManager.soundsEnabled.value && soundLoaded) {
            try {
                val vol = SoundHapticsManager.soundVolume.value / 100f
                soundPool.play(tickSoundId, vol, vol, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    var selectedField by remember { mutableStateOf(0) }

    data class Field(
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val suffix: String,
        val onValueChange: (Float) -> Unit
    )

    val fields = listOf(
        Field("Blur", config.blur, 0f..80f, "dp") { onBlurChange(it); tickHaptic() },
        Field("Darkness", config.darkness * 100f, 0f..100f, "%") { onDarknessChange(it / 100f); tickHaptic() },
        Field("Height", config.heightExtra, -500f..300f, "dp") { onHeightChange(it); tickHaptic() },
        Field("Corner", config.corner, 0f..50f, "dp") { onCornerChange(it); tickHaptic() }
    )

    val currentField = fields[selectedField]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header: title + reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search Card",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onReset(); tickHaptic() }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Reset", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Field selector capsules (same style as nav bar customization)
        fields.forEachIndexed { index, field ->
            val isSelected = index == selectedField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { selectedField = index; tickHaptic() }
                    )
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = field.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = CalSansFamily
                )
                Text(
                    text = "${field.value.toInt()}${field.suffix}",
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // +/- buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // -1 button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value - 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) tickHaptic()
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
            }
            // +1 button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value + 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) tickHaptic()
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Arc dial (same as nav bar customization panel)
        CynthiaArcDial(
            label = currentField.label,
            value = currentField.value,
            range = currentField.range,
            suffix = currentField.suffix,
            onValueChange = currentField.onValueChange,
            onReset = { onReset(); tickHaptic() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * PinnedChip — small glass chip for a pinned search query.
 * Tap to fill the search field with this query.
 */
@Composable
private fun PinnedChip(
    entry: com.rajatxo.coral.data.prefs.SearchHistory.SearchEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isPlaylist) CoralIcons.ListMusic else CoralIcons.Search,
            contentDescription = null,
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = entry.query,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
}
