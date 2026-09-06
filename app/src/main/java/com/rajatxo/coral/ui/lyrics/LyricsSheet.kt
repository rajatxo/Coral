package com.rajatxo.coral.ui.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.lyrics.Lyric
import com.rajatxo.coral.data.lyrics.LyricLine
import com.rajatxo.coral.data.lyrics.LyricsRepository
import com.rajatxo.coral.ui.icons.CoralIcons
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Full-screen lyrics sheet.
 *
 * Opens on top of the FullPlayer when the user taps the lyrics button.
 *
 * Layout:
 *  - Solid black background (so lyrics are maximally readable)
 *  - Top bar: ChevronDown (dismiss) | "LYRICS" label | Refresh button
 *  - Content:
 *      - Loading spinner while fetching
 *      - Error / empty state with retry
 *      - Synced lyrics: current line is bold + coral + 100% alpha;
 *        other lines are white at 30% alpha. Auto-scrolls to follow
 *        playback. Tap any synced line to seek there.
 *      - Unsynced lyrics: plain centered text, no scrolling.
 */
@Composable
fun LyricsSheet(
    trackName: String,
    artistName: String,
    albumName: String?,
    durationMs: Long?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LyricsRepository(context) }

    var lyric by remember { mutableStateOf<Lyric?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Fetch lyrics when the song changes OR when refreshTrigger changes
    LaunchedEffect(trackName, artistName, refreshTrigger) {
        if (trackName.isBlank() || artistName.isBlank()) {
            isLoading = false
            error = "No track info available"
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        try {
            val fetched = if (refreshTrigger == 0) {
                repository.getLyrics(
                    track = trackName,
                    artist = artistName,
                    album = albumName,
                    durationMs = durationMs
                )
            } else {
                repository.refreshLyrics(
                    track = trackName,
                    artist = artistName,
                    album = albumName,
                    durationMs = durationMs
                )
            }
            lyric = fetched
            if (fetched == null) error = "No lyrics found for this track"
        } catch (e: Exception) {
            error = "Failed to load lyrics: ${e.message ?: "unknown error"}"
        }
        isLoading = false
    }

    // Manual refresh — bumps refreshTrigger which re-runs the LaunchedEffect above
    fun refresh() {
        refreshTrigger++
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Close lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "LYRICS",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { refresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "↻", color = Color.White, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFFF6B6B))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Searching LrcLib...",
                                color = Color(0xFFB0B0B0),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🎵", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = error ?: "No lyrics found",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Coral searched LrcLib for \"$trackName\" by $artistName",
                                color = Color(0xFFB0B0B0),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFFF6B6B))
                                    .clickable { refresh() }
                                    .padding(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Try again",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                lyric != null -> {
                    LyricsContent(
                        lyric = lyric!!,
                        currentPositionMs = currentPositionMs,
                        onSeek = onSeek
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lyric: Lyric,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit
) {
    if (!lyric.synced) {
        // Plain lyrics — just show them centered
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 32.dp, vertical = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(lyric.lines) { line ->
                Text(
                    text = line.text,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    // Synced lyrics — find current line based on playback position
    val currentStateIndex = remember(lyric.lines, currentPositionMs) {
        findCurrentLineIndex(lyric.lines, currentPositionMs)
    }

    val listState = rememberLazyListState()

    // Auto-scroll to keep current line centered
    LaunchedEffect(currentStateIndex) {
        if (currentStateIndex >= 0 && currentStateIndex < lyric.lines.size) {
            // Scroll so the current line is roughly centered (offset -3 lines)
            val targetScroll = (currentStateIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 80.dp  // generous top/bottom so first/last lines can be centered
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(lyric.lines) { line ->
            val isActive = lyric.lines.indexOf(line) == currentStateIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.3f,
                label = "lineAlpha"
            )
            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            val fontSize = if (isActive) 20.sp else 17.sp

            Text(
                text = line.text.ifBlank { "♪" },
                color = if (isActive) Color(0xFFFF6B6B) else Color.White,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .clickable {
                        if (line.timeMs >= 0) onSeek(line.timeMs)
                    }
            )
        }
    }
}

/**
 * Find the index of the lyric line that should currently be highlighted.
 *
 * "Currently" means: the last line whose timestamp is <= currentPositionMs.
 * If we're before the first timestamp, returns -1 (nothing highlighted).
 */
private fun findCurrentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var result = -1
    for (i in lines.indices) {
        if (lines[i].timeMs in 0..positionMs) {
            result = i
        } else {
            break
        }
    }
    return result
}
