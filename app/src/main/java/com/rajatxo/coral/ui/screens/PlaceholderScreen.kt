package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoralColors.Surface)
    ) {
        // Big title at top-right (ViTune-style) — uses Quirk italic font
        Text(
            text = tabName,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = com.rajatxo.coral.ui.theme.QuirkFontFamily,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 20.dp, top = 16.dp)
        )

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
