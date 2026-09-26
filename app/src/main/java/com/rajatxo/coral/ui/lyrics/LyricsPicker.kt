package com.rajatxo.coral.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
    // ★ Signature change: pass back BOTH the candidate AND the offset to apply.
    //   The caller (LyricsSheet) will pass offsetMs to candidateToLyric() so
    //   the lyrics timeline shifts to perfectly match the user's local song.
    onCandidateSelected: (candidate: LrcLibCandidate, offsetMs: Long) -> Unit
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
                                        // ★ Compute the offset: (song duration) − (candidate duration)
                                        //   Positive = candidate is shorter → push lyrics later.
                                        //   Negative = candidate is longer → pull lyrics earlier.
                                        //   Zero = perfect timeline match, no offset needed.
                                        val candidateDurationMs = (candidate.durationSec * 1000).toLong()
                                        val offsetMs = if (durationMs != null && durationMs > 0) {
                                            durationMs - candidateDurationMs
                                        } else 0L
                                        onCandidateSelected(candidate, offsetMs)
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

    // Duration delta in seconds (null = no song duration to compare)
    val songDurationSec: Int? = songDurationMs?.let { (it / 1000).toInt() }
    val deltaSec: Int? = songDurationSec?.let { abs(candidate.durationSec - it) }
    val isExactMatch = deltaSec != null && deltaSec == 0 && candidate.hasSynced

    // Vibrant match colors (saturated, used for gradient + accent)
    // Each branch provides: primary color, secondary color (deeper),
    // match label, and the gradient color stops for the capsule fill.
    data class MatchStyle(
        val primary: Color,
        val secondary: Color,
        val label: String,
        val gradientStops: Array<Pair<Float, Color>>
    ) {
        // Array equals by reference is fine here — we only compare by identity
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    val matchStyle = when {
        deltaSec == null -> MatchStyle(
            primary = Color.White.copy(alpha = 0.4f),
            secondary = Color.White.copy(alpha = 0.15f),
            label = "—",
            gradientStops = arrayOf(
                0.0f to Color.White.copy(alpha = 0.18f),
                1.0f to Color.White.copy(alpha = 0.04f)
            )
        )
        deltaSec == 0 -> MatchStyle(
            primary = Color(0xFF4ADE80),         // saturated green
            secondary = Color(0xFF16A34A),       // deep green
            label = "exact",
            gradientStops = arrayOf(
                0.0f to Color(0xFF4ADE80).copy(alpha = 0.42f),
                0.5f to Color(0xFF16A34A).copy(alpha = 0.30f),
                1.0f to Color(0xFF052E16).copy(alpha = 0.55f)
            )
        )
        deltaSec <= 2 -> MatchStyle(
            primary = Color(0xFFA3E635),         // lime-yellow
            secondary = Color(0xFF65A30D),       // olive
            label = "+${deltaSec}s",
            gradientStops = arrayOf(
                0.0f to Color(0xFFA3E635).copy(alpha = 0.38f),
                0.5f to Color(0xFF65A30D).copy(alpha = 0.26f),
                1.0f to Color(0xFF1A2E05).copy(alpha = 0.55f)
            )
        )
        deltaSec <= 5 -> MatchStyle(
            primary = Color(0xFFFACC15),         // vivid yellow
            secondary = Color(0xFFCA8A04),       // amber
            label = "±${deltaSec}s",
            gradientStops = arrayOf(
                0.0f to Color(0xFFFACC15).copy(alpha = 0.34f),
                0.5f to Color(0xFFCA8A04).copy(alpha = 0.22f),
                1.0f to Color(0xFF422006).copy(alpha = 0.55f)
            )
        )
        else -> MatchStyle(
            primary = Color(0xFFFF6B6B),         // coral red
            secondary = Color(0xFFDC2626),       // deep red
            label = "−${deltaSec}s",
            gradientStops = arrayOf(
                0.0f to Color(0xFFFF6B6B).copy(alpha = 0.34f),
                0.5f to Color(0xFFDC2626).copy(alpha = 0.24f),
                1.0f to Color(0xFF2E0505).copy(alpha = 0.55f)
            )
        )
    }
    val primaryColor = matchStyle.primary
    val secondaryColor = matchStyle.secondary
    val matchLabel = matchStyle.label
    val matchGradient = matchStyle.gradientStops

    // ★ Pulse Ring animation — only on exact match (delta == 0 AND synced).
    //   A soft glowing ring expands outward from the duration circle and
    //   fades as it grows. Loops every ~2s. Premium, subtle "heartbeat"
    //   confirmation that this candidate fits the song perfectly.
    val showPulse = isExactMatch
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    // Ring 1 — expands 0.6 → 1.0 over 2s, alpha 0.7 → 0.0
    val pulseProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "pulse1"
    )
    // Ring 2 — same animation, offset by 1s for a layered "echo" feel
    val pulseProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                delayMillis = 1000,
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "pulse2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(pillShape)
            // ★ Vibrant gradient fill — no border. Saturated, premium look.
            .background(Color.Black.copy(alpha = 0.4f))
            .background(Brush.verticalGradient(colorStops = matchGradient))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Glossy top highlight (gives the glass feel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.10f),
                            0.4f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.15f)
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
            // ─── Duration circle (left) ───
            // On exact match: pulse ring expands outward (radar ping style)
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulse ring overlay (drawn first, behind the circle)
                if (showPulse) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.matchParentSize()
                    ) {
                        val canvasSize = size.minDimension
                        val center = androidx.compose.ui.geometry.Offset(canvasSize / 2f, canvasSize / 2f)
                        // Ring starts at the circle's edge (radius ≈ 24dp in px → 0.43 of canvas)
                        // and expands to 0.95 of canvas. Alpha fades 0.7 → 0.0.
                        val startRadius = canvasSize * 0.43f
                        val maxRadius = canvasSize * 0.95f

                        // Ring 1
                        val r1 = startRadius + (maxRadius - startRadius) * pulseProgress1
                        val a1 = (1f - pulseProgress1) * 0.7f
                        if (a1 > 0.01f) {
                            drawCircle(
                                color = Color(0xFF4ADE80).copy(alpha = a1),
                                radius = r1,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = canvasSize * 0.04f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }

                        // Ring 2 (offset for layered echo)
                        val r2 = startRadius + (maxRadius - startRadius) * pulseProgress2
                        val a2 = (1f - pulseProgress2) * 0.5f
                        if (a2 > 0.01f) {
                            drawCircle(
                                color = Color(0xFF4ADE80).copy(alpha = a2),
                                radius = r2,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = canvasSize * 0.04f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }
                }

                // The duration circle itself — solid vibrant color
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryColor, secondaryColor),
                                radius = 1.2f
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val mins = candidate.durationSec / 60
                    val secs = candidate.durationSec % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily
                    )
                }
            }
            Spacer(Modifier.size(12.dp))

            // ─── Track info (middle) ───
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
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                // ─── Combined SYNCED + delta small capsule pill ───
                // Replaces the old split badge + plain text.
                // One pill, two segments joined: [SYNCED  +2s] or [PLAIN  −12s]
                val badgeShape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, primaryColor.copy(alpha = 0.5f), badgeShape)
                ) {
                    // Segment 1: SYNCED / PLAIN
                    Box(
                        modifier = Modifier
                            .background(primaryColor.copy(alpha = 0.20f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (candidate.hasSynced) "SYNCED" else "PLAIN",
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CalSansFamily
                        )
                    }
                    // Segment 2: delta label
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = matchLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = CalSansFamily
                        )
                    }
                }
            }

            // ─── Chevron (right) ───
            Icon(
                imageVector = CoralIcons.ChevronsRight,
                contentDescription = "Use these lyrics",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** Tiny 4-tuple helper (Kotlin's built-in Triple only goes to 3). */
private data class Quad<T>(val a: T, val b: T, val c: T, val d: T)

