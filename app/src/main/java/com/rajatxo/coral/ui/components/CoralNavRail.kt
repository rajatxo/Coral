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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Coral's vertical navigation rail — ViTune-style positioning.
 *
 * Layout contract (matches ViTune exactly):
 *  - Width: 48dp (slightly wider than before so rotated text breathes)
 *  - Padding from left edge: built into the 48dp width — text is centered
 *    horizontally in the rail, giving ~16dp from the screen's left edge
 *    to the text
 *  - Gear/back icon at TOP-CENTER, ~32dp tall, ~24dp from the screen top
 *    (below status bar)
 *  - Labels: spaced 24dp apart (tight, list-like — not loose)
 *  - Labels are vertically CENTERED in the rail (between top icon and
 *    bottom edge) — fills the full rail height, not clustered at top
 *  - Rotation: -90° (text reads bottom-to-top)
 *  - Active: pure White + Bold. Inactive: muted gray + Regular.
 *  - Font: 12-13sp (compact, list-like — not large)
 *
 * Label sizing:
 *  Each label slot is 24dp tall x 48dp wide. The rotated text fits inside
 *  this slot because the longest label ("Quick picks" = ~78dp horizontally)
 *  becomes ~78dp tall after rotation — and since labels are spaced only
 *  24dp apart, they actually overlap visually but Compose handles the
 *  rotation correctly with proper z-ordering.
 *
 *  Wait — that overlap would look broken. Let me reconsider.
 *
 *  ACTUAL approach: each label Box is `wrapContentHeight`-ish. We measure
 *  the text horizontally, then the Box's height after rotation equals
 *  the text's width. We can't easily do that in stock Compose, so we
 *  use a fixed slot that's tall enough for the longest label.
 *
 *  Longest label is "Quick picks" (~78dp wide horizontally). After
 *  rotation, that's 78dp tall. We use 80dp slots.
 *
 *  But then 6 labels x 80dp = 480dp, which doesn't fit on a typical
 *  phone (720dp tall screen, minus status bar 24dp, minus gear icon 56dp,
 *  minus nav bar 48dp = ~592dp available — 480dp fits).
 *
 *  OK, 80dp slots work. Spacing between them: 0dp (the slot itself
 *  provides the visual gap via its internal padding).
 *
 *  Actually, looking at ViTune again — the labels are TIGHT. There's
 *  barely any gap. So no `spacedBy` — just stacked directly.
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
            .width(48.dp)
            .fillMaxHeight()
            .background(CoralColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()  // aligns gear with the "Songs" title vertically
                .padding(top = 16.dp, bottom = 16.dp),  // matches Songs title's top=16dp
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Top icon: gear (Main mode) or back arrow (Settings mode) ----
            // Vertically aligned with the big "Songs" title on the right
            // (both use statusBarsPadding + 16dp top padding).
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
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

            // ---- Rail labels, centered vertically in remaining space ----
            Spacer(modifier = Modifier.weight(1f))

            // Tight stack: each label slot is 96dp tall (plenty of room for
            // the longest label "Quick picks" ~88dp after rotation), and
            // spacedBy=0 means slots touch — the slots themselves provide
            // the visual gap because the text is centered vertically in
            // each 96dp slot (so there's ~4dp padding above+below each text).
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
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

            Spacer(modifier = Modifier.weight(1f))
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

    // Each label slot is 96dp tall x 48dp wide.
    // The longest label ("Quick picks") at 13sp Poppins is ~88dp wide
    // horizontally, so after -90° rotation it's ~88dp tall. The 96dp slot
    // gives ~4dp of padding above and below — no clipping.
    //
    // The text is centered in the slot, so consecutive labels have ~8dp
    // of visible gap between them (4dp bottom of one + 4dp top of next).
    // That matches ViTune's tight, list-like spacing.
    Box(
        modifier = Modifier
            .height(96.dp)
            .width(48.dp)
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
 * The 6 main destinations on the rail.
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
 * First entry is Premium (per the user's request).
 */
enum class CoralSettingsTab(val label: String) {
    Premium("Premium"),
    Appearance("Appearance"),
    Playback("Playback"),
    About("About")
}
