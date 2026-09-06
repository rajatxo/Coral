package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import kotlinx.coroutines.launch

/**
 * Songs tab — vertical list of every track Coral scanned from MediaStore,
 * now with alphabet scrollbar + sticky-letter headers.
 *
 * Layout:
 *  - Big "Songs" title at top-RIGHT (ViTune-style)
 *  - "{n} songs" subtitle below it, right-aligned
 *  - LazyColumn with sticky header items for each letter (A, B, C, ...)
 *  - Songs grouped under their first letter
 *  - Alphabet scrollbar on the RIGHT edge — drag to jump to a letter
 *
 * The alphabet scrollbar is the killer feature for big libraries. Without
 * it, scrolling through 500+ songs is painful.
 */
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongTitle: String?,
    onSongClick: (Song) -> Unit
) {
    // Group songs by first letter. Numbers/symbols get '#'.
    val groupedSongs = remember(songs) {
        songs.groupBy { song ->
            val firstChar = song.title.firstOrNull { it.isLetterOrDigit() } ?: '#'
            if (firstChar.isDigit()) '#' else firstChar.uppercaseChar()
        }.toSortedMap()
    }

    // Flatten into a list of items (headers + song rows) so LazyColumn
    // can render them in order. Each item is either a Header or a Song.
    val flatItems = remember(groupedSongs) {
        val items = mutableListOf<SongListItem>()
        groupedSongs.forEach { (letter, songList) ->
            items.add(SongListItem.Header(letter))
            songList.forEach { items.add(SongListItem.SongItem(it)) }
        }
        items
    }

    // Map: letter -> flatItems index (for scrollbar to jump to)
    val letterToIndex = remember(flatItems) {
        flatItems.withIndex()
            .filter { it.value is SongListItem.Header }
            .associate { (it.value as SongListItem.Header).letter to it.index }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Track which letter is currently visible (for highlighting the scrollbar)
    val currentLetter by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            // Find the most recent header at or before firstVisibleIndex
            var letter = '#'
            for (i in 0..firstVisibleIndex) {
                if (flatItems.getOrNull(i) is SongListItem.Header) {
                    letter = (flatItems[i] as SongListItem.Header).letter
                }
            }
            letter
        }
    }

    // Show a big overlay letter when scrolling via the scrollbar
    var overlayLetter by remember { mutableStateOf<Char?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Big title at top-RIGHT (ViTune style) + song count below it
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 20.dp, top = 16.dp)
        ) {
            Text(
                text = "Songs",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                color = CoralColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // Song list with letter headers
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 100.dp, end = 32.dp)
        ) {
            flatItems.forEach { item ->
                when (item) {
                    is SongListItem.Header -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = item.letter.toString(),
                                color = CoralColors.Coral,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is SongListItem.SongItem -> item {
                        SongRow(
                            song = item.song,
                            isCurrent = currentSongTitle == item.song.title,
                            onClick = { onSongClick(item.song) }
                        )
                    }
                }
            }
        }

        // Alphabet scrollbar on the right edge
        AlphabetScrollbar(
            letters = letterToIndex.keys.toList(),
            currentLetter = currentLetter,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 100.dp, bottom = 80.dp),
            onLetterSelected = { letter ->
                val targetIndex = letterToIndex[letter] ?: return@AlphabetScrollbar
                scope.launch {
                    overlayLetter = letter
                    listState.animateScrollToItem(targetIndex)
                }
            },
            onDragStart = { overlayLetter = it },
            onDragEnd = { overlayLetter = null }
        )

        // Big overlay letter (shown while dragging the scrollbar)
        if (overlayLetter != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = overlayLetter.toString(),
                        color = CoralColors.Coral,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Vertical alphabet scrollbar — drag to jump to a letter.
 *
 * Each letter is a small clickable Text. The currently-active letter
 * (the one the song list is scrolled to) is highlighted in coral.
 */
@Composable
private fun AlphabetScrollbar(
    letters: List<Char>,
    currentLetter: Char,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit,
    onDragStart: (Char) -> Unit,
    onDragEnd: () -> Unit
) {
    if (letters.isEmpty()) return

    var currentDragIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .width(24.dp)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val itemHeight = size.height.toFloat() / letters.size
                        val index = (offset.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                        currentDragIndex = index
                        onDragStart(letters[index])
                        onLetterSelected(letters[index])
                    },
                    onDragEnd = {
                        currentDragIndex = null
                        onDragEnd()
                    },
                    onDragCancel = {
                        currentDragIndex = null
                        onDragEnd()
                    },
                    onVerticalDrag = { change, _ ->
                        val itemHeight = size.height.toFloat() / letters.size
                        val newIndex = (change.position.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                        if (newIndex != currentDragIndex) {
                            currentDragIndex = newIndex
                            onDragStart(letters[newIndex])
                            onLetterSelected(letters[newIndex])
                        }
                        change.consume()
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val isActive = letter == currentLetter
            Text(
                text = letter.toString(),
                color = if (isActive) CoralColors.Coral else CoralColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onLetterSelected(letter)
                            onDragStart(letter)
                            onDragEnd()
                        }
                    )
                    .padding(vertical = 1.dp)
            )
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
                color = if (isCurrent) CoralColors.Coral else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = CoralColors.TextMuted,
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
            color = CoralColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

/**
 * Internal: items in the flat list (either a letter header or a song).
 */
private sealed class SongListItem {
    data class Header(val letter: Char) : SongListItem()
    data class SongItem(val song: Song) : SongListItem()
}
