package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.components.SleepTimerCapsule
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Songs tab — with pinned header + wavy fade overlay.
 *
 * Layout:
 *  - Layer 1 (bottom): LazyColumn of songs — fills the ENTIRE screen and
 *    scrolls behind the header. Songs that scroll into the wavy fade area
 *    appear to dissolve into darkness.
 *
 *  - Layer 2 (top): Pinned header that NEVER scrolls:
 *      * "Songs" title (34sp, Quirk italic, top-right)
 *      * "{n} songs" subtitle
 *      * Capsule shape (placeholder — user will tell me what to do with it)
 *      * Wavy black fade — solid black from the top down to the wave line,
 *        then gradient fade from black to transparent below the wave
 *
 * The wavy fade creates the effect where songs scrolling up "disappear"
 * into the darkness instead of sliding past a hard edge.
 *
 * PERFORMANCE: LazyColumn items have stable keys (key = { it.id }) so
 * Compose reuses rows. isCurrent compared by song ID (Long, not String).
 */
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongTitle: String?,
    onSongClick: (Song) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {}
) {
    val sortedSongs = remember(songs) {
        songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    // Total height of the pinned header.
    // Structure (top to bottom):
    //   ~0-136dp: solid pure black (status bar + "Songs" title + capsule)
    //   ~136-170dp: wavy fade (34dp of fade — smaller gap, songs closer to capsule)
    val headerHeight = 170.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Layer 1: Song list (scrolls behind the header) ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = headerHeight,
                bottom = 100.dp  // space for mini player
            )
        ) {
            items(sortedSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = currentSongId == song.id,
                    onClick = { onSongClick(song) }
                )
            }
        }

        // --- Layer 2: Pinned header with wavy fade ---
        // This sits ON TOP of the song list. Songs scroll behind it.
        // The wavy black fade makes songs appear to dissolve into darkness.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .align(Alignment.TopCenter)
        ) {
            // Canvas: draws the wavy black shape with gradient fade
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // The solid black area covers the top ~80% (status bar +
                // title + capsule). Below that is the wavy fade.
                // 80% of 170dp = ~136dp solid black, then ~34dp fade.
                val waveStartY = canvasHeight * 0.80f
                val waveAmplitude = 12f  // gentle wave height in px
                val waveSegments = 3  // number of wave bumps

                // --- STEP 1: Draw SOLID black rectangle (fully opaque) ---
                // Covers from top to PAST the wave start (overlaps with the
                // wave path by waveAmplitude to eliminate any sub-pixel gap
                // between the two draws).
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(canvasWidth, waveStartY + waveAmplitude)
                )

                // --- STEP 2: Draw the wavy fade area below the wave ---
                // This is where songs gradually appear (fade from solid
                // black to transparent).
                val fadePath = Path().apply {
                    moveTo(0f, waveStartY)

                    // Wavy top edge (left to right) using cubic bezier
                    val segmentWidth = canvasWidth / waveSegments
                    for (i in 0 until waveSegments) {
                        val x1 = segmentWidth * i + segmentWidth * 0.25f
                        val y1 = waveStartY - waveAmplitude  // peak up
                        val x2 = segmentWidth * i + segmentWidth * 0.75f
                        val y2 = waveStartY + waveAmplitude  // valley down
                        val x3 = segmentWidth * (i + 1)
                        val y3 = waveStartY  // back to center
                        cubicTo(x1, y1, x2, y2, x3, y3)
                    }

                    // Continue down to bottom
                    lineTo(canvasWidth, canvasHeight)
                    lineTo(0f, canvasHeight)
                    close()
                }

                // Fill with gradient: solid black at the wave → transparent at bottom
                drawPath(
                    path = fadePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Transparent
                        ),
                        startY = waveStartY,
                        endY = canvasHeight
                    )
                )
            }

            // Content on top of the canvas: capsule + title in a Row
            // The capsule uses weight(1f) so it auto-fills the space
            // before the title. Title takes its natural width.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleep timer capsule (auto-fills space before title)
                SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
                if (capsuleVisible && capsuleRemaining > 0) {
                    Spacer(modifier = Modifier.size(12.dp))
                }
                // Big "Songs" title
                Text(
                    text = "Songs",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.rajatxo.coral.ui.theme.QuirkFontFamily
                )
            }
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
