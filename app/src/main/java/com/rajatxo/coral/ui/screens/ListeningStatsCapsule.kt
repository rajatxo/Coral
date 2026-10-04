package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * ★ ListeningStatsCapsule — glass capsule with:
 *   LEFT: animated equalizer bars (react to isPlaying)
 *   RIGHT: listening time today ("2h 34m today")
 *
 * When playing: bars bounce with random heights (simulated equalizer)
 * When paused: bars are flat (minimal height)
 *
 * Uses CalSans font for the time text.
 * Glass morphism: semi-transparent dark background + border.
 */
@Composable
fun ListeningStatsCapsule(
    isPlaying: Boolean,
    listeningTimeSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(50)

    // ★ Format listening time: <60min → "Xm", ≥60min → "Xh Ym"
    val timeText = formatListeningTime(listeningTimeSeconds)

    // ★ Animated equalizer bars — 5 bars that bounce when playing
    val infiniteTransition = rememberInfiniteTransition(label = "eqBars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isPlaying) 1f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isPlaying) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (isPlaying) 0.95f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.7f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isPlaying) 0.9f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar5"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.02f))
            .border(
                width = 1.5.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.4f),  // top — glossy highlight
                        Color.White.copy(alpha = 0.1f),  // middle
                        Color.White.copy(alpha = 0.25f)   // bottom — subtle reflection
                    )
                ),
                shape = capsuleShape
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ★ Equalizer bars (left side)
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(20.dp)
            ) {
                EqualizerBar(bar1)
                EqualizerBar(bar2)
                EqualizerBar(bar3)
                EqualizerBar(bar4)
                EqualizerBar(bar5)
            }

            Spacer(modifier = Modifier.width(2.dp))

            // ★ Listening time text
            Text(
                text = timeText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily
            )

            // ★ Cat sits on the right side of the capsule
            Spacer(modifier = Modifier.weight(1f))

            // ★ Lottie cat animation — sits on the right side of the capsule
            LottieAnimation(
                composition = rememberLottieComposition(
                    LottieCompositionSpec.Asset("cat_animation.json")
                ).value,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun EqualizerBar(heightFraction: Float) {
    Canvas(
        modifier = Modifier
            .size(width = 3.dp, height = 20.dp)
    ) {
        val barHeight = size.height * heightFraction.coerceIn(0.1f, 1f)
        val startY = (size.height - barHeight) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.7f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f, size.width / 2f)
        )
    }
}

private fun formatListeningTime(seconds: Int): String {
    if (seconds <= 0) return "0m today"
    val minutes = seconds / 60
    return when {
        minutes < 60 -> "${minutes}m today"
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            "${hours}h ${mins}m today"
        }
    }
}
