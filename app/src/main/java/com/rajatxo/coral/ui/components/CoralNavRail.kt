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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Coral's vertical navigation rail — ViTune-style.
 *
 * Has TWO modes:
 *
 *  - [RailMode.Main] (default): shows the 6 main tabs (Quick picks, Discover,
 *    Songs, Playlists, Artists, Albums). A gear icon sits at the TOP.
 *    Tapping the gear switches to Settings mode.
 *
 *  - [RailMode.Settings]: shows settings categories (Premium, Appearance,
 *    Playback, About). A back arrow sits at the TOP (replaces the gear).
 *    Tapping the back arrow returns to Main mode.
 *
 * Design contract (matches ViTune exactly):
 *  - Width: 40dp (narrow)
 *  - Background: same as the app surface (#121212 dark gray)
 *  - Content: rotated text labels only, NO icons (except the gear/back at top)
 *  - Rotation: -90° (text reads bottom-to-top)
 *  - Active state: pure White + Bold weight
 *  - Inactive state: muted gray (#888888) + Regular weight
 *
 * Label implementation note:
 *  Each label is a fixed-height Box (90dp) that wraps a rotated Text.
 *  The Box is 40dp wide (matches rail width) and tall enough to fit
 *  the longest rotated label ("Quick picks" = ~80dp when rotated).
 *  This is simpler than the previous custom layout modifier and
 *  prevents the cut-off text bug.
 */
@Composable
fun CoralNavRail(
    mode: RailMode,
    selectedMainTab: CoralTab?,
    selectedSettingsTab: CoralSettingsTab?,
    onMainTabSelected: (CoralTab) -> Unit,
    onSettingsTabSelected: (CoralSettingsTab) -> Unit,
    onGearClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .fillMaxHeight()
            .background(CoralColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Top icon: gear (Main mode) or back arrow (Settings mode) ----
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = if (mode == RailMode.Main) onGearClick else onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mode == RailMode.Main) CoralIcons.Settings else CoralIcons.ChevronDown,
                    contentDescription = if (mode == RailMode.Main) "Open settings" else "Back to tabs",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Rail labels ----
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mode == RailMode.Main) {
                    CoralTab.values().forEach { tab ->
                        RailLabel(
                            label = tab.label,
                            isSelected = tab == selectedMainTab,
                            onClick = { onMainTabSelected(tab) }
                        )
                    }
                } else {
                    CoralSettingsTab.values().forEach { tab ->
                        RailLabel(
                            label = tab.label,
                            isSelected = tab == selectedSettingsTab,
                            onClick = { onSettingsTabSelected(tab) }
                        )
                    }
                }
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

    // Each label is a fixed 90dp tall x 40dp wide slot.
    // The text inside is rotated -90°, so a horizontal text like "Quick picks"
    // (~80dp wide x 16dp tall) becomes visually 16dp wide x 80dp tall after
    // rotation — fits comfortably in the 40x90 slot.
    Box(
        modifier = Modifier
            .height(90.dp)
            .width(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = weight,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.rotate(-90f)
        )
    }
}

/**
 * Coral's color palette — ViTune-style.
 */
object CoralColors {
    val Surface: Color = Color(0xFF121212)
    val SurfaceVariant: Color = Color(0xFF1F1F1F)
    val TextPrimary: Color = Color.White
    val TextMuted: Color = Color(0xFF888888)
    val Coral: Color = Color(0xFFFF6B6B)
}

/**
 * Which rail is currently shown: main tabs or settings categories.
 */
enum class RailMode { Main, Settings }

/**
 * The 6 main destinations on the rail (no Settings — that's a separate
 * gear icon at the top now).
 */
enum class CoralTab(val label: String) {
    QuickPicks("Quick picks"),
    Discover("Discover"),
    Songs("Songs"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums")
}

/**
 * The settings categories shown when the gear icon is tapped.
 *
 * First entry is Premium (per the user's request).
 */
enum class CoralSettingsTab(val label: String) {
    Premium("Premium"),
    Appearance("Appearance"),
    Playback("Playback"),
    About("About")
}
