package com.rajatxo.coral.ui.sleeptimer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.premium.SleepTimer
import com.rajatxo.coral.data.premium.SleepTimerState
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Sleep timer sheet — Phase 7 premium feature.
 *
 * Layout:
 *  - Title: "Sleep timer"
 *  - If active: shows remaining time + "Cancel" + "+5 min" buttons
 *  - If inactive: shows 5/15/30/60 min options + "End of current song"
 */
@Composable
fun SleepTimerSheet(
    sleepTimer: SleepTimer,
    onDismiss: () -> Unit
) {
    val state by sleepTimer.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sleep timer",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.active) {
            // Active state — show remaining time + extend/cancel
            val remainingText = if (state.endOfSong) {
                "End of current song"
            } else {
                state.endAtMs?.let { endAt ->
                    val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
                    val min = remaining / 60_000
                    val sec = (remaining % 60_000) / 1000
                    String.format("%d:%02d remaining", min, sec)
                } ?: "Active"
            }

            Text(
                text = remainingText,
                color = Color(0xFFFF6B6B),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.endOfSong) {
                    ActionButton(
                        text = "+5 min",
                        onClick = { sleepTimer.extend(5) }
                    )
                }
                ActionButton(
                    text = "Cancel",
                    isPrimary = true,
                    onClick = { sleepTimer.cancel(); onDismiss() }
                )
            }
        } else {
            // Inactive — show preset durations
            Text(
                text = "Choose when to stop playback",
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            val durations = listOf(
                "5 min" to 5 * 60_000L,
                "15 min" to 15 * 60_000L,
                "30 min" to 30 * 60_000L,
                "60 min" to 60 * 60_000L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                durations.forEach { (label, ms) ->
                    ActionButton(
                        text = label,
                        onClick = { sleepTimer.startTimed(ms); onDismiss() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { sleepTimer.startEndOfSong(); onDismiss() }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "End of current song",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isPrimary) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.10f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
