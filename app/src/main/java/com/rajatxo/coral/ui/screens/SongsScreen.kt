package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.BugLineRefreshIndicator
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Songs tab — Niagara Launcher-inspired design.
 *
 * Layout (top to bottom):
 *   1. Background: single dominant color → dark gradient (same as Quick Picks)
 *   2. Blur header (rendered in HomeScreen) — profile + "Songs" + settings
 *   3. Fixed "All songs" header with chevron + bug PTR indicator + count
 *   4. Fixed horizontal letter scrubber bar — drag to jump to any letter.
 *      The bar follows the finger 1:1. Scrolling the list updates the
 *      active letter on the bar (bidirectional sync).
 *   5. LazyColumn with songs grouped by first letter (A, B, C...).
 *      Each group has a sticky letter header. Songs are listed as rows.
 *
 * The Niagara-style interaction:
 *   • Drag the scrubber bar LEFT/RIGHT → list scrolls VERTICALLY to that letter
 *   • Scroll the list VERTICALLY → scrubber bar updates the active letter
 *   • Both directions follow the finger exactly (1:1 mapping)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArt: android.net.Uri? = null,
    onSongClick: (Song) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onRefresh: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Sort + group songs by first letter ────────────────────────
    val sortedSongs = remember(songs) {
        songs.sortedBy { it.title.lowercase() }
    }

    // Group songs by their first letter (uppercase). Non-alpha → "#".
    // Returns a list of (letter, List<Song>) pairs, sorted by letter.
    val letterGroups = remember(sortedSongs) {
        sortedSongs.groupBy { song ->
            val firstChar = song.title.firstOrNull()?.uppercaseChar()
            if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
        }.toList().sortedBy { it.first }
    }

    // Build a flat list of items for the LazyColumn:
    //   [LetterHeader("A"), Song, Song, Song, LetterHeader("B"), Song, ...]
    // Each item has a unique key so Compose can reuse rows.
    // Using a sealed interface instead of data class (can't define data class
    // locally in Kotlin).
    val flatItems = remember(letterGroups) {
        val items = mutableListOf<Pair<String, Song?>>()  // (key, song?) — song==null means header
        letterGroups.forEach { (letter, songsInGroup) ->
            items.add("header_$letter" to null)
            songsInGroup.forEach { song ->
                items.add("song_${song.id}" to song)
            }
        }
        items
    }

    // Map letter → first item index in flatItems (for scrolling)
    val letterToIndex = remember(flatItems) {
        val map = mutableMapOf<String, Int>()
        flatItems.forEachIndexed { index, (key, song) ->
            // If song is null, this is a header — extract the letter from the key
            if (song == null) {
                val letter = key.removePrefix("header_")
                map[letter] = index
            }
        }
        map
    }

    val listState = rememberLazyListState()

    // Track which letter is currently visible at the top of the viewport.
    // We scan from the first visible item upward to find the most recent header.
    val activeLetter by remember {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            // Walk backwards from firstIndex to find the nearest header
            var idx = firstIndex
            while (idx >= 0) {
                val (key, song) = flatItems.getOrNull(idx) ?: break
                if (song == null) {
                    // This is a header — extract the letter
                    return@derivedStateOf key.removePrefix("header_")
                }
                idx--
            }
            letterGroups.firstOrNull()?.first ?: "A"
        }
    }

    // ─── Current song's palette → dark gradient background ──────────
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(currentSongArt) {
        if (currentSongArt != null) {
            PaletteCache.get(currentSongArt)?.let { palette = it }
            extractPalette(context, currentSongArt)?.let {
                palette = it
                PaletteCache.put(currentSongArt, it)
            }
        }
    }

    val vibrantTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.85f),
        animationSpec = tween(800), label = "songsBgVibrantTop"
    )
    val fade1 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.65f),
        animationSpec = tween(800), label = "songsBgFade1"
    )
    val fade2 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.45f),
        animationSpec = tween(800), label = "songsBgFade2"
    )
    val fade3 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.28f),
        animationSpec = tween(800), label = "songsBgFade3"
    )
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.18f),
        animationSpec = tween(800), label = "songsBgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.08f),
        animationSpec = tween(800), label = "songsBgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),
        animationSpec = tween(800), label = "songsBgBottom"
    )

    val darkBase = Color(0xFF05050A)

    // ─── Pull-to-refresh state ───────────────────────────────────────
    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Heights for the fixed overlays:
    //   130dp = below the blur header (120dp blur + 10dp gap)
    //   36dp  = "All songs" header row
    //   Total = 170dp top padding for the LazyColumn
    val allSongsHeaderTop = 130.dp
    val allSongsHeaderHeight = 36.dp
    val listTopPadding = allSongsHeaderTop + allSongsHeaderHeight + 4.dp

    // Available letters for the scrubber
    val letters = letterGroups.map { it.first }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f  to vibrantTop,
                        0.10f to fade1,
                        0.15f to fade2,
                        0.20f to fade3,
                        0.30f to animatedTop,
                        0.55f to animatedMid,
                        1.0f  to animatedBottom
                    )
                )
            )
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try {
                        onRefresh()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            state = ptrState,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = listTopPadding,
                    bottom = 100.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(flatItems, key = { it.first }) { (key, song) ->
                    if (song == null) {
                        // Header item
                        val letter = key.removePrefix("header_")
                        LetterHeader(letter = letter)
                    } else {
                        SongRow(
                            song = song,
                            isCurrent = currentSongId == song.id,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }

        // ─── Fixed "All songs" header (below the blur header) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = allSongsHeaderTop, start = 24.dp, end = 20.dp)
                .height(allSongsHeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "All songs",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            BugLineRefreshIndicator(
                progress = ptrState.distanceFraction,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
            )
            Text(
                text = "${songs.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
        }

        // ─── Vertical letter scrubber (Niagara-style, right edge) ───
        // Letters stacked vertically on the RIGHT edge of the screen.
        // NO background — letters float directly over the content.
        // Drag vertically → nearby letters bulge LEFT (elastic rope
        // effect with exponential decay + spring physics). The letter
        // at the finger position becomes active → list scrolls to it.
        if (letters.isNotEmpty()) {
            VerticalLetterScrubber(
                letters = letters,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    val index = letterToIndex[letter]
                    if (index != null) {
                        scope.launch {
                            listState.scrollToItem(index)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// VERTICAL LETTER SCRUBBER — Niagara-style elastic rope
// ════════════════════════════════════════════════════════════════════
// Letters stacked VERTICALLY on the right edge of the screen.
// NO background — letters float directly over the content behind them.
//
// Interaction:
//   • Drag finger UP/DOWN on the right edge
//   • Letters near the finger BULGE LEFT (elastic rope effect)
//   • The letter closest to the finger becomes "active" → list scrolls
//   • Exponential decay: letters far from the finger barely move
//   • Spring physics: letters spring back when the finger lifts
//
// Inspired by theSoberSobber/Open-Niagara implementation, adapted
// for Coral's Songs tab with bidirectional sync (scrolling the list
// also updates which letter is highlighted).
// ════════════════════════════════════════════════════════════════════

@Composable
private fun VerticalLetterScrubber(
    letters: List<String>,
    activeLetter: String,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track the finger's Y position (in pixels, relative to this composable).
    // null = not touching.
    var touchY by remember { mutableStateOf<Float?>(null) }

    // Track the total height of the letter column (for normalizing distances).
    var columnHeight by remember { mutableStateOf(0f) }

    // Track which letter index the finger is closest to (for selection).
    var lastSelectedIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = modifier
            .width(60.dp)  // touch zone width
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchY = offset.y
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touchY = change.position.y
                        // Calculate which letter the finger is over
                        if (columnHeight > 0 && letters.isNotEmpty()) {
                            val frac = (touchY!! / columnHeight).coerceIn(0f, 1f)
                            val idx = (frac * (letters.size - 1)).roundToInt()
                                .coerceIn(0, letters.lastIndex)
                            if (idx != lastSelectedIndex) {
                                lastSelectedIndex = idx
                                onLetterSelected(letters[idx])
                            }
                        }
                    },
                    onDragEnd = {
                        touchY = null
                        lastSelectedIndex = -1
                    },
                    onDragCancel = {
                        touchY = null
                        lastSelectedIndex = -1
                    }
                )
            }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    if (columnHeight > 0 && letters.isNotEmpty()) {
                        val frac = (offset.y / columnHeight).coerceIn(0f, 1f)
                        val idx = (frac * (letters.size - 1)).roundToInt()
                            .coerceIn(0, letters.lastIndex)
                        onLetterSelected(letters[idx])
                    }
                }
            }
    ) {
        // The letter column — aligned to CenterEnd (vertically CENTERED, not
        // full height). Letters are TIGHTLY PACKED (near-zero spacing) to
        // match Niagara Launcher exactly.
        // NO background — letters float over the content.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onGloballyPositioned { coordinates ->
                    columnHeight = coordinates.size.height.toFloat()
                },
            verticalArrangement = Arrangement.spacedBy(0.001.dp),
            horizontalAlignment = Alignment.End
        ) {
            letters.forEachIndexed { index, letter ->
                NiagaraLetter(
                    letter = letter,
                    isActive = letter == activeLetter,
                    touchY = touchY,
                    columnHeight = columnHeight,
                    letterIndex = index,
                    totalLetters = letters.size
                )
            }
        }
    }
}

/**
 * A single letter in the vertical scrubber.
 *
 * When the finger is near (touchY is set), the letter bulges LEFT
 * (negative X offset) with exponential decay. Letters closest to
 * the finger move the most; far letters barely move.
 *
 * Spring physics animate the offset for an elastic feel.
 */
@Composable
private fun NiagaraLetter(
    letter: String,
    isActive: Boolean,
    touchY: Float?,
    columnHeight: Float,
    letterIndex: Int,
    totalLetters: Int
) {
    // Track this letter's center Y position (for distance calculation)
    var letterCenterY by remember { mutableStateOf(0f) }

    // Calculate the elastic offset based on distance from touch.
    // Matches the Open-Niagara reference: maxOffset=120, decayFactor=10.
    val targetOffset = remember(touchY, letterCenterY, columnHeight) {
        if (touchY == null || columnHeight == 0f) {
            0f
        } else {
            // Distance from finger to this letter's center
            val distance = touchY - letterCenterY
            // Normalize by column height
            val normalizedDistance = distance / columnHeight
            // Exponential decay — letters near finger move more
            val decayFactor = 10f
            val influence = kotlin.math.exp(
                -(normalizedDistance * normalizedDistance) * decayFactor
            )
            // Max bulge: 120dp to the left (matches reference)
            120f * influence
        }
    }

    // Animate with spring physics for elastic feel
    val animatedOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "letterOffset_$letter"
    )

    // 12sp font for ALL letters (matches reference). Active letter is
    // coral + bold; others are white at 0.5 alpha.
    Text(
        text = letter,
        color = if (isActive) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        fontFamily = CalSansFamily,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .offset(x = -animatedOffset.dp)  // Move LEFT (negative X)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.positionInRoot()
                letterCenterY = rect.y + (coordinates.size.height / 2f)
            }
    )
}

// ════════════════════════════════════════════════════════════════════
// LETTER HEADER — sticky-style letter section header
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LetterHeader(letter: String) {
    Text(
        text = letter,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = CalSansFamily,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}

// ════════════════════════════════════════════════════════════════════
// SONG ROW — album art + title/artist + duration
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) CoralColors.SurfaceVariant else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album art",
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val totalSec = song.duration / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        Text(
            text = "$mm:${String.format("%02d", ss)}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp
        )
    }
}
