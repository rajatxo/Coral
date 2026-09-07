package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import java.util.Locale

/**
 * Sleep Timer Capsule — a white glossy pill that appears at the top-left
 * of each tab's header area when a sleep timer is active.
 *
 * Design:
 *  - White pill background with glossy reflection (vertical gradient overlay)
 *  - Progress border: thick arc around the pill border
 *    - Active (elapsed): dynamic color from current song's palette
 *    - Inactive (remaining): black
 *  - Inside: countdown text (e.g., "14:59") in black
 *  - "+10" extend button with a small + icon
 *  - Gentle gap between capsule's right end and the big title text
 *
 * Auto-adjustable: uses weight(1f) in the parent Row so it fills the
 * available space before the title text.
 *
 * @param remainingMs   Remaining time in milliseconds
 * @param totalMs        Total timer duration in milliseconds
 * @param progressColor Dynamic color from current song's palette (for active progress)
 * @param onExtend      Called when user taps "+10"
 */
@Composable
fun SleepTimerCapsule(
    remainingMs: Long,
    totalMs: Long,
    progressColor: Color,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (remainingMs <= 0 || totalMs <= 0) return

    // Progress: 0.0 = just started, 1.0 = timer done
    val progress = (1f - (remainingMs.toFloat() / totalMs.toFloat())).coerceIn(0f, 1f)

    // Format remaining time as M:SS
    val remainingSec = remainingMs / 1000
    val minutes = remainingSec / 60
    val seconds = remainingSec % 60
    val timeText = String.format(Locale.US, "%d:%02d", minutes, seconds)

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .drawWithContent {
                drawContent()

                // Glossy reflection: white-to-transparent vertical gradient
                // on the top half (simulates light hitting the top of the pill)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.05f)
                        )
                    ),
                    size = size
                )

                // Progress border: drawn as a thick arc around the pill's edge
                val borderWidth = 3.dp.toPx()
                val arcDiameter = size.minDimension - borderWidth
                val topLeft = Offset(
                    (size.width - arcDiameter) / 2f,
                    (size.height - arcDiameter) / 2f
                )
                val arcSize = Size(arcDiameter, arcDiameter)

                // Inactive (remaining) — black arc, full circle minus progress
                drawArc(
                    color = Color.Black,
                    startAngle = -90f + (360f * progress),
                    sweepAngle = 360f * (1f - progress),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = borderWidth, cap = StrokeCap.Round)
                )

                // Active (elapsed) — dynamic palette color
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = borderWidth, cap = StrokeCap.Round)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Countdown text
            Text(
                text = timeText,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(2.dp))

            // "+10" extend button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExtend
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "+10",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
