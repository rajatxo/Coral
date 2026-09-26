package com.rajatxo.coral.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.lyrics.LrcLibCandidate
import com.rajatxo.coral.data.lyrics.LyricsRepository
import androidx.compose.ui.platform.LocalContext
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * LyricsPicker — a full-screen overlay that shows ALL candidate lyric
 * sets returned by LrcLib for the currently playing song.
 *
 * WHY THIS EXISTS
 * ---------------
 * Some songs have multiple releases (single, album version, radio edit,
 * remaster, live). Each release can have a different timeline. Coral's
 * auto-fetch picks the closest match by duration, but sometimes that
 * match is wrong — the lyrics drift out of sync. This picker lets the
 * user see every candidate LrcLib has, with their durations and a
 * visual match indicator, and choose the one that actually fits.
 *
 * UI
 * --
 * Premium design matching Coral's language:
 *   - Dark base (#05050A) with vertical gradient
 *   - Glossy capsule rows (same shape as Songs tab + Search results)
 *   - Each capsule shows: track name, artist, album, duration, sync badge
 *   - Duration match indicator: green (±2s), yellow (±5s), red (off)
 *   - Bottom fade gradient
 *   - Search icon at top-left to go back
 *
 * Inspired by vivi-music's approach (duration-sorted candidates) but
 * with Coral's own UI — no code or design copied from vivi.
 */
@Composable
fun LyricsPicker(
    trackName: String,
    artistName: String,
    albumName: String?,
    durationMs: Long?,
    onDismiss: () -> Unit,
    onCandidateSelected: (LrcLibCandidate) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LyricsRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<LrcLibCandidate>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fire the search as soon as the picker opens
    LaunchedEffect(trackName, artistName) {
        isLoading = true
        errorMessage = null
        try {
            val results = repository.searchLyricsOnLrcLib(
                track = trackName,
                artist = artistName,
                album = albumName,
                durationMs = durationMs
            )
            candidates = results
            if (results.isEmpty()) {
                errorMessage = "No lyrics candidates found"
            }
        } catch (e: Exception) {
            errorMessage = "Search failed: ${e.message}"
        }
        isLoading = false
    }

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
            // Swallow stray taps so they don't leak through to the player behind
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Header ──────────────────────────────────────────────
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pick Lyrics",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily
                    )
                    Text(
                        text = "${candidates.size} candidates from LrcLib",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontFamily = CalSansFamily
                    )
                }

                // Song duration badge — the reference for picking
                if (durationMs != null && durationMs > 0) {
                    val mins = durationMs / 1000 / 60
                    val secs = (durationMs / 1000) % 60
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Your song: ${String.format(java.util.Locale.US, "%d:%02d", mins, secs)}",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = CalSansFamily
                        )
                    }
                }
            }

            // ─── Content ────────────────────────────────────────────
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF6B6B),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Searching LrcLib...",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = CalSansFamily
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    CoralIcons.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                errorMessage!!,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontFamily = CalSansFamily
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Try Fetch Lyrics instead, or import an .lrc file",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp,
                                fontFamily = CalSansFamily
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, bottom = 200.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Helper explainer
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Tap the candidate whose duration matches your song. Green = perfect match, Yellow = close, Red = different version.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontFamily = CalSansFamily,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        items(candidates, key = { it.id }) { candidate ->
                            CandidateCapsule(
                                candidate = candidate,
                                songDurationMs = durationMs,
                                onClick = {
                                    coroutineScope.launch {
                                        onCandidateSelected(candidate)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ─── Bottom fade gradient (matches Songs tab + Search) ───────
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
// CANDIDATE CAPSULE — one row per LrcLib candidate
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CandidateCapsule(
    candidate: LrcLibCandidate,
    songDurationMs: Long?,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(32.dp)

    // Duration delta in seconds (null = no song duration to compare).
    // songDurationMs is Long? → divide by 1000 → Long?. Convert to Int for compare.
    val songDurationSec: Int? = songDurationMs?.let { (it / 1000).toInt() }
    val deltaSec: Int? = songDurationSec?.let { abs(candidate.durationSec - it) }

    // Match quality: green (≤2s), yellow (≤5s), red (>5s or no duration)
    val matchColor = when {
        deltaSec == null -> Color.White.copy(alpha = 0.3f)
        deltaSec <= 2 -> Color(0xFF4ADE80) // green
        deltaSec <= 5 -> Color(0xFFFACC15) // yellow
        else -> Color(0xFFFF6B6B)          // coral red
    }
    val matchLabel = when {
        deltaSec == null -> "—"
        deltaSec == 0 -> "exact"
        deltaSec <= 2 -> "+${deltaSec}s"
        deltaSec <= 5 -> "±${deltaSec}s"
        else -> "−${deltaSec}s"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(pillShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, matchColor.copy(alpha = 0.25f), pillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Glossy overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to matchColor.copy(alpha = 0.05f),
                            0.5f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Duration circle (left) — shows candidate duration + match color
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(matchColor.copy(alpha = 0.12f))
                    .border(1.dp, matchColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val mins = candidate.durationSec / 60
                val secs = candidate.durationSec % 60
                Text(
                    text = String.format(java.util.Locale.US, "%d:%02d", mins, secs),
                    color = matchColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily
                )
            }
            Spacer(Modifier.size(12.dp))
            // Track info (middle)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.trackName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(candidate.artistName.ifBlank { "Unknown artist" })
                        if (!candidate.albumName.isNullOrBlank()) {
                            append(" • ")
                            append(candidate.albumName)
                        }
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Sync badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (candidate.hasSynced) Color(0xFF4ADE80).copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (candidate.hasSynced) "SYNCED" else "PLAIN",
                            color = if (candidate.hasSynced) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CalSansFamily
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = matchLabel,
                        color = matchColor,
                        fontSize = 10.sp,
                        fontFamily = CalSansFamily
                    )
                }
            }
            // Chevron (right)
            Icon(
                imageVector = CoralIcons.ChevronsRight,
                contentDescription = "Use these lyrics",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}
