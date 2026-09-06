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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Coral's vertical navigation rail — ViTune-style.
 *
 * Design contract (matches ViTune exactly):
 *  - Width: 40dp (narrow — just enough for the rotated text height)
 *  - Background: same as the app surface (#121212 dark gray) — no separate color
 *  - Content: rotated text labels only, NO icons
 *  - Rotation: -90° (text reads bottom-to-top)
 *  - Active state: pure White + Bold weight
 *  - Inactive state: muted gray (#888888) + Regular weight
 *  - No pill background, no underline, no indicator — color + weight only
 *
 * Implementation note:
 *  Compose's Modifier.rotate(-90f) only rotates the VISUAL — the layout
 *  measurement still uses the text's unrotated width (e.g. "Quick picks"
 *  measures at ~110dp wide). Without intervention, the rail would be 110dp
 *  wide even though we set .width(40.dp) on the Box.
 *
 *  Fix: a custom [layout] modifier that swaps width and height after
 *  measurement, so a 110x16 text becomes a 16x110 layout slot. Combined
 *  with rotate(-90f), the text renders correctly vertical AND the rail
 *  stays narrow.
 */
@Composable
fun CoralNavRail(
    selectedTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(40.dp)  // narrow — ViTune-style
            .fillMaxHeight()
            .background(CoralColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.Top),
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
    val color = if (isSelected) Color.White else CoralColors.TextMuted
    val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        fontWeight = weight,
        maxLines = 1,
        modifier = Modifier
            // 1. Rotate the text -90° (reads bottom-to-top)
            .rotate(-90f)
            // 2. Swap the measured width and height so the layout
            //    slot is narrow (text height, ~16dp) instead of wide
            //    (text width, ~110dp). Without this, the rail Box
            //    would expand to fit the unrotated text width.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // Swap width <-> height in the reported size
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width - placeable.height) / 2,
                        y = -(placeable.height - placeable.width) / 2
                    )
                }
            }
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
 * The destinations reachable from the rail.
 *
 * Mirrors ViTune's rail order: Quick Picks, Discover, Songs, Playlists,
 * Artists, Albums. Settings stays at the bottom.
 */
enum class CoralTab(val label: String) {
    QuickPicks("Quick picks"),
    Discover("Discover"),
    Songs("Songs"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums"),
    Settings("Settings")
}
