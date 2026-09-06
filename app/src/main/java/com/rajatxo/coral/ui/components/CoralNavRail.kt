package com.rajatxo.coral.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Coral's vertical navigation rail — ViTune-style.
 *
 * Design contract (matches ViTune exactly):
 *  - Width: 56dp (narrower than Material's 72dp default)
 *  - Background: same as the app surface (#121212 dark gray) — no separate color
 *  - Content: rotated text labels only, NO icons
 *  - Rotation: -90° (text reads bottom-to-top)
 *  - Active state: pure White + Bold weight
 *  - Inactive state: muted gray (#888888) + Regular weight
 *  - No pill background, no underline, no indicator — color + weight only
 *  - Labels are stacked vertically with even spacing
 *  - Status bar padding handled by parent (statusBarsPadding)
 */
@Composable
fun CoralNavRail(
    selectedTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(CoralColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoralTab.values().forEach { tab ->
                RailLabel(
                    label = tab.label,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun RailLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (isSelected) Color.White else CoralColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .rotate(-90f)  // text reads bottom-to-top, like ViTune
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

/**
 * Coral's color palette — ViTune-style.
 *
 * ViTune uses dark gray (#121212) surfaces, NOT pure black. This makes the
 * UI feel warmer and less harsh on OLED screens (counterintuitive but true —
 * pure black removes depth, dark gray keeps it).
 */
object CoralColors {
    /** Main app surface — dark gray, same as ViTune's background. */
    val Surface: Color = Color(0xFF121212)

    /** Slightly lighter gray for cards / mini player / nav rail pills. */
    val SurfaceVariant: Color = Color(0xFF1F1F1F)

    /** Primary text — pure white. */
    val TextPrimary: Color = Color.White

    /** Secondary text — muted gray for inactive items, subtitles, etc. */
    val TextMuted: Color = Color(0xFF888888)

    /** Coral accent — the brand color, used for active states + buttons. */
    val Coral: Color = Color(0xFFFF6B6B)
}

/**
 * The 3 destinations reachable from the rail.
 */
enum class CoralTab(val label: String) {
    Songs("Songs"),
    Playlists("Playlists"),
    Settings("Settings")
}
