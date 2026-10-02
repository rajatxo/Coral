package com.rajatxo.coral.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ Today's Top — a row of 3 capsule-shaped "stories".
 *
 * MINIMAL VERSION — no data, no PlaybackHistory, no color extraction.
 * Just 3 empty capsules with "+" icons on vibrant gradient backgrounds.
 * Used to isolate the crash cause.
 */
@Composable
fun TodaysTopSection(
    songs: List<com.rajatxo.coral.domain.model.Song> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 6 vibrant fallback colors
    val colors = listOf(
        Color(0xFFFF6B6B),  // coral red
        Color(0xFFFFD166),  // warm yellow
        Color(0xFF6B9EFF),  // ocean blue
        Color(0xFFFF8FAB),  // pink
        Color(0xFF51CF66),  // emerald
        Color(0xFFB197FC)   // purple
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Today's Top",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
            Text(
                text = "Play a song to fill these up",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3 capsules in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (capsuleIndex in 0 until 3) {
                val color1 = colors[capsuleIndex * 2]
                val color2 = colors[capsuleIndex * 2 + 1]

                val capsuleShape = RoundedCornerShape(50)
                val borderBrush = Brush.horizontalGradient(listOf(color1, color2))
                val bgBrush = Brush.horizontalGradient(
                    listOf(color1.copy(alpha = 0.3f), color2.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
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
                        // Cover 1 (empty +)
                        EmptyCoverSlot(color = color1)
                        // Cover 2 (empty +) — slightly overlapping
                        EmptyCoverSlot(color = color2, overlapStart = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCoverSlot(
    color: Color,
    overlapStart: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .then(if (overlapStart) Modifier.padding(start = (-8).dp) else Modifier)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(color, color.copy(alpha = 0.7f))
                )
            )
            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalSansFamily
        )
    }
}
