package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.components.CoralColors

/**
 * Placeholder screen for tabs that aren't built out yet
 * (Quick Picks, Discover, Artists, Albums).
 *
 * Shows a centered message explaining what the tab will be when built.
 * The user said they'll work on the offline logic for these later.
 *
 * Layout mirrors ViTune's empty states: big title at the top-right,
 * centered icon + message in the middle of the screen.
 *
 * @param tabName  Display name of the tab (e.g. "Quick Picks", "Discover").
 * @param description  Brief explanation of what this tab will eventually do.
 */
@Composable
fun PlaceholderScreen(
    tabName: String,
    description: String,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05050A))  // darkBase
            .background(
                // Same gradient style as QuickPicks/Songs — vibrant top
                // fading to dark bottom. Uses a coral accent as the
                // vibrant top (PlaceholderScreen has no album art palette).
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f  to Color(0xFFFF6B6B).copy(alpha = 0.45f),
                        0.30f to Color(0xFFFF6B6B).copy(alpha = 0.15f),
                        0.55f to Color(0xFFFF6B6B).copy(alpha = 0.04f),
                        1.0f  to Color(0xFF05050A)
                    )
                )
            )
    ) {
        // Header Column: Row(capsule + title) + capsule placeholder below
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 16.dp)
        ) {
            // Header Row: Sleep timer capsule (if visible) — no big title text
            // (the blur header from HomeScreen already shows the tab name)
            // ★ Keep a 40dp height where the old title text was so the
            //   capsule below stays at the same vertical position as before.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),  // match the old title height
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.rajatxo.coral.ui.components.SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Capsule shape placeholder (below title — same as Songs tab)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CoralColors.SurfaceVariant)
            )
        }

        // Centered placeholder message
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🪸", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$tabName is coming",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = CoralColors.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
