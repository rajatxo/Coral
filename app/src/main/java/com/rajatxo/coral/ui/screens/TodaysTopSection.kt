package com.rajatxo.coral.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * ★ Today's Top — a row of 3 capsule-shaped "stories" showing the user's
 *   6 most-played songs today (2 per capsule).
 *
 * Layout matches the Speed Dial / Recent headers: same text style, chevron,
 * and horizontal padding (no extra padding — aligns with the LazyColumn's
 * 20dp content padding).
 */
@Composable
fun TodaysTopSection(
    songs: List<Song> = emptyList(),
    modifier: Modifier = Modifier,
    textPrimary: Color = Color.White,
    textSecondary: Color = Color.White.copy(alpha = 0.6f)
) {
    val dailyPlays by com.rajatxo.coral.data.prefs.PlaybackHistory.dailyPlays.collectAsState()

    val topSongs: List<Song> = remember(dailyPlays, songs) {
        dailyPlays.take(6).mapNotNull { entry ->
            songs.find { it.id == entry.songId }
        }
    }

    val context = LocalContext.current
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
        // ★ Header — matches Speed Dial / Recent layout: title + chevron
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),  // align with Speed Dial header
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

        // ★ 3 capsules — thinner (56dp), full width, NO overlap (separate)
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

                val capsuleShape = RoundedCornerShape(28.dp)
                val borderBrush = Brush.horizontalGradient(listOf(color1, color2))
                val bgBrush = Brush.horizontalGradient(
                    listOf(color1.copy(alpha = 0.25f), color2.copy(alpha = 0.25f))
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(capsuleShape)
                        .background(bgBrush)
                        .border(width = 2.dp, brush = borderBrush, shape = capsuleShape)
                        .padding(4.dp),
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
        } else {
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
