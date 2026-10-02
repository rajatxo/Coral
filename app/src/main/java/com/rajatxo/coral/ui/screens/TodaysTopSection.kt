package com.rajatxo.coral.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ Today's Top — a row of 3 capsule-shaped "stories" showing the user's
 *   6 most-played songs today (2 per capsule).
 *
 * Each capsule:
 *   - Contains 2 album covers (side by side, slightly overlapping)
 *   - Has a gradient border from Cover1's dominant color → Cover2's dominant color
 *   - The inside background is also a gradient blending the two colors
 *
 * Filling logic:
 *   - 6 songs → 3 full capsules
 *   - 5 songs → 2 full + 1 capsule with 1 song + 1 empty ("+")
 *   - 0 songs → all 3 capsules empty, 6 "+" icons on a continuous vibrant gradient
 *
 * Empty covers:
 *   - Show a "+" icon
 *   - Have a colorful gradient background that continues the flow across all 6 covers
 *
 * Time window: calendar day (resets at midnight).
 *
 * Capsules are NOT clickable yet.
 */
@Composable
fun TodaysTopSection(
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    // Get the top 6 most-played songs today
    val dailyPlays by com.rajatxo.coral.data.prefs.PlaybackHistory.dailyPlays.collectAsState()

    // Map the top played song IDs to actual Song objects (to get album art)
    val topSongs: List<Song> = remember(dailyPlays, songs) {
        dailyPlays.take(6).mapNotNull { entry ->
            songs.find { it.id == entry.songId }
        }
    }

    // Extract dominant colors for each of the 6 slots
    // Slots 1-6: filled with top songs, empty slots get vibrant fallback colors
    val context = LocalContext.current
    var slotColors by remember { mutableStateOf<List<Color>>(List(6) { Color.Gray }) }
    val slotSongs = remember(topSongs) {
        // 6 slots, each either filled with a song or null (empty)
        List(6) { index -> topSongs.getOrNull(index) }
    }

    // Extract dominant color for each filled slot
    LaunchedEffect(topSongs) {
        // ★ Run on IO dispatcher — bitmap decoding is disk I/O and must
        //   NOT run on the main thread (causes strict-mode crash).
        val colors = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val colors = mutableListOf<Color>()
            for (i in 0 until 6) {
                val song = topSongs.getOrNull(i)
                if (song != null && song.albumArtUri != null) {
                    val color = extractDominantColor(context, song.albumArtUri)
                    colors.add(color)
                } else {
                    colors.add(getVibrantFallbackColor(i))
                }
            }
            colors
        }
        slotColors = colors
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ★ Header — "Today's Top" with a cool subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Today's Top",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
                Text(
                    text = if (topSongs.isEmpty()) "Play a song to fill these up"
                           else "Your most played today",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ★ 3 capsules in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (capsuleIndex in 0 until 3) {
                val slot1Index = capsuleIndex * 2
                val slot2Index = capsuleIndex * 2 + 1
                val song1 = slotSongs.getOrNull(slot1Index)
                val song2 = slotSongs.getOrNull(slot2Index)
                val color1 = slotColors.getOrNull(slot1Index) ?: Color.Gray
                val color2 = slotColors.getOrNull(slot2Index) ?: Color.Gray

                TodaysTopCapsule(
                    song1 = song1,
                    song2 = song2,
                    color1 = color1,
                    color2 = color2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A single capsule — 2 album covers side by side, gradient border + background.
 */
@Composable
private fun TodaysTopCapsule(
    song1: Song?,
    song2: Song?,
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(50)  // full pill shape
    val borderBrush = Brush.horizontalGradient(listOf(color1, color2))
    val bgBrush = Brush.horizontalGradient(
        listOf(color1.copy(alpha = 0.3f), color2.copy(alpha = 0.3f))
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(capsuleShape)
            .background(bgBrush)
            .border(width = 2.dp, brush = borderBrush, shape = capsuleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover 1 (or empty +)
            CoverSlot(song = song1, color = color1)
            // Cover 2 (or empty +) — slightly overlapping
            CoverSlot(song = song2, color = color2, overlapStart = true)
        }
    }
}

/**
 * A single album cover slot. If the song is null, shows a "+" icon on a
 * gradient background.
 */
@Composable
private fun CoverSlot(
    song: Song?,
    color: Color,
    overlapStart: Boolean = false
) {
    val size = 56.dp
    val slotColor = if (song != null) color else getVibrantFallbackColor(0)

    Box(
        modifier = Modifier
            .size(size)
            .then(if (overlapStart) Modifier.padding(start = (-8).dp) else Modifier)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(slotColor, slotColor.copy(alpha = 0.7f))
                )
            )
            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (song != null && song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.size(size)
            )
        } else {
            // Empty slot — show "+" icon
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        }
    }
}

/**
 * Extract the dominant color from an album art URI.
 * Uses simple bitmap sampling + saturation boost.
 */
private fun extractDominantColor(context: android.content.Context, uri: Uri): Color {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return getVibrantFallbackColor(0)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream) ?: return getVibrantFallbackColor(0)
        inputStream.close()

        // Simple dominant color extraction — sample the center of the bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixel = bitmap.getPixel(w / 2, h / 2)
        val r = android.graphics.Color.red(pixel) / 255f
        val g = android.graphics.Color.green(pixel) / 255f
        val b = android.graphics.Color.blue(pixel) / 255f

        // Boost saturation a bit for vibrancy
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv
        )
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)  // boost saturation
        val boosted = android.graphics.Color.HSVToColor(hsv)
        Color(
            red = android.graphics.Color.red(boosted) / 255f,
            green = android.graphics.Color.green(boosted) / 255f,
            blue = android.graphics.Color.blue(boosted) / 255f
        )
    } catch (_: Exception) {
        getVibrantFallbackColor(0)
    }
}

/**
 * Get a vibrant fallback color for an empty slot.
 * Returns a different vibrant color based on the index.
 */
private fun getVibrantFallbackColor(index: Int): Color {
    val vibrantColors = listOf(
        Color(0xFFFF6B6B),  // coral red
        Color(0xFFFFD166),  // warm yellow
        Color(0xFF6B9EFF),  // ocean blue
        Color(0xFFFF8FAB),  // pink
        Color(0xFF51CF66),  // emerald
        Color(0xFFB197FC)   // purple
    )
    return vibrantColors[index % vibrantColors.size]
}
