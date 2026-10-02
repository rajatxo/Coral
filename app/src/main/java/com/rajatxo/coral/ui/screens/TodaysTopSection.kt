package com.rajatxo.coral.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ Today's Top — 3 capsule "stories" with ANIMATED gradient borders.
 */
@Composable
fun TodaysTopSection(
    songs: List<Song> = emptyList(),
    modifier: Modifier = Modifier,
    textPrimary: Color = Color.White,
    textSecondary: Color = Color.White.copy(alpha = 0.6f)
) {
    // ★ Force-load daily plays on first composition
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            com.rajatxo.coral.data.prefs.PlaybackHistory.getTopPlayedToday(6)
        } catch (_: Exception) { }
    }

    val dailyPlays by com.rajatxo.coral.data.prefs.PlaybackHistory.dailyPlays.collectAsState()

    val topSongs: List<Song> = remember(dailyPlays, songs) {
        dailyPlays.take(6).mapNotNull { entry ->
            songs.find { it.id == entry.songId }
        }
    }

    var slotColors by remember { mutableStateOf<List<Color>>(List(6) { Color(0xFF333333) }) }
    val slotSongs = remember(topSongs) {
        List(6) { index -> topSongs.getOrNull(index) }
    }

    LaunchedEffect(topSongs) {
        try {
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
        } catch (_: Exception) {
            slotColors = List(6) { getVibrantFallbackColor(it) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Top",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3 capsules
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (capsuleIndex in 0 until 3) {
                val slot1Index = capsuleIndex * 2
                val slot2Index = capsuleIndex * 2 + 1
                val song1 = slotSongs.getOrNull(slot1Index)
                val song2 = slotSongs.getOrNull(slot2Index)
                val color1 = slotColors.getOrNull(slot1Index) ?: Color(0xFF333333)
                val color2 = slotColors.getOrNull(slot2Index) ?: Color(0xFF333333)

                AnimatedCapsule(
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
 * A capsule with an ANIMATED rotating gradient border.
 */
@Composable
private fun AnimatedCapsule(
    song1: Song?,
    song2: Song?,
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier
) {
    // ★ ANIMATED gradient — rotates continuously for a "flowing" effect
    // ★ "Water through pipe" — ONE bright head flows from left to right,
    //   exits the right edge, then re-enters from the left. Continuous loop.
    //   The gradient extends beyond [0,1] so the bright head smoothly
    //   exits and re-enters without splitting.
    //   Animate from -0.3 to 1.3 so the head travels fully across and
    //   off-screen before looping back.
    val flowPosition by rememberInfiniteTransition(label = "capsuleGradient").animateFloat(
        initialValue = -0.3f,  // start off-screen left
        targetValue = 1.3f,    // end off-screen right
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = { it }),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPosition"
    )

    val capsuleShape = RoundedCornerShape(28.dp)
    val bgBrush = Brush.horizontalGradient(
        listOf(color1.copy(alpha = 0.25f), color2.copy(alpha = 0.25f))
    )

    // ★ Single bright head at `flowPosition`, with dim on both sides.
    //   No clamping — the head flows off-screen and re-enters seamlessly.
    val pipeGradient = Brush.horizontalGradient(
        colorStops = arrayOf(
            0.0f to color1.copy(alpha = 0.3f),                        // dim
            (flowPosition - 0.15f) to color1.copy(alpha = 0.3f),      // dim (before head)
            flowPosition to color1,                                    // ★ bright head
            (flowPosition + 0.15f) to color1.copy(alpha = 0.3f),      // dim (after head)
            1.0f to color2.copy(alpha = 0.3f)                          // dim
        )
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(capsuleShape)
            .background(bgBrush)
            // ★ 2.5dp border — water-flowing-through-pipe animated gradient
            .border(width = 2.5.dp, brush = pipeGradient, shape = capsuleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverSlot(song = song1, color = color1)
            CoverSlot(song = song2, color = color2)
        }
    }
}

@Composable
private fun CoverSlot(
    song: Song?,
    color: Color
) {
    val coverSize = 40.dp

    Box(
        modifier = Modifier
            .size(coverSize)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(color, color.copy(alpha = 0.7f))
                )
            )
            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (song != null && song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.size(coverSize)
            )
        } else if (song != null) {
            // Song exists but no album art — show first letter of title
            Text(
                text = song.title.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        } else {
            // Empty slot — show "+"
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        }
    }
}

private fun extractDominantColor(context: android.content.Context, uri: android.net.Uri): Color {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return getVibrantFallbackColor(0)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream) ?: return getVibrantFallbackColor(0)
        inputStream.close()

        val w = bitmap.width
        val h = bitmap.height
        val pixel = bitmap.getPixel(w / 2, h / 2)
        val r = android.graphics.Color.red(pixel) / 255f
        val g = android.graphics.Color.green(pixel) / 255f
        val b = android.graphics.Color.blue(pixel) / 255f

        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv
        )
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
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

private fun getVibrantFallbackColor(index: Int): Color {
    val vibrantColors = listOf(
        Color(0xFFFF6B6B),
        Color(0xFFFFD166),
        Color(0xFF6B9EFF),
        Color(0xFFFF8FAB),
        Color(0xFF51CF66),
        Color(0xFFB197FC)
    )
    return vibrantColors[index % vibrantColors.size]
}
