package com.rajatxo.coral.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Sleep Timer Capsule — a white glossy pill.
 *
 * Simplified design:
 *  - White pill background with glossy reflection (vertical gradient overlay)
 *  - Inside: countdown text (e.g., "14:59") in black
 *  - "+10" extend button: a CIRCLE with "+10" text inside
 *  - Auto-adjustable: uses weight(1f) in the parent Row so it fills
 *    the available space before the title text
 *  - Fade-in/fade-out animation when timer starts/stops
 *
 * @param visible     Whether the capsule should show
 * @param remainingMs Remaining time in milliseconds
 * @param onExtend    Called when user taps "+10"
 * @param modifier    Modifier for sizing (should use weight(1f))
 */
@Composable
fun SleepTimerCapsule(
    visible: Boolean,
    remainingMs: Long,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && remainingMs > 0,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
    ) {
        // Format remaining time as M:SS
        val remainingSec = remainingMs / 1000
        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        val timeText = String.format(Locale.US, "%d:%02d", minutes, seconds)

        Box(
            modifier = Modifier
                .height(34.dp)
                .fillMaxWidth()  // fills the space left by the end padding in HomeScreen
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White)
                .drawWithContent {
                    drawContent()
                    // Glossy reflection
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.03f)
                            )
                        ),
                        size = size
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Countdown text
                Text(
                    text = timeText,
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // "+10" extend button — CIRCLE with "+10" text inside
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExtend
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+10",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
