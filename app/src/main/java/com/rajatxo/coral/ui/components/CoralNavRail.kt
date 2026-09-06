package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Coral's vertical navigation rail — ViTune-style positioning.
 *
 * Layout contract (matches ViTune):
 *  - Width: 48dp
 *  - Gear icon at top, vertically aligned with the "Songs" title
 *    (both use statusBarsPadding + 16dp top padding)
 *  - Labels drawn on Canvas using TextMeasurer — slot sized EXACTLY
 *    to the text's natural width, so there's ZERO clipping
 *  - Labels stacked with 8dp gap (tight, list-like — ViTune style)
 *  - Vertically centered in the rail (Spacer weight(1f) above and below)
 *
 * WHY Canvas + TextMeasurer:
 *   Compose's Modifier.rotate(-90f) only rotates the VISUAL — the layout
 *   measurement still uses the text's UNROTATED width. So if the parent
 *   slot is 48dp wide and "Quick picks" wants to be 88dp wide, the Text
 *   is clipped to 48dp horizontally BEFORE rotation. That's why "Quick"
 *   was visible but "picks" was cut off.
 *
 *   The Canvas + TextMeasurer approach lets us:
 *     1. Measure the text at its natural width (no parent constraint)
 *     2. Size the slot to EXACTLY the text's natural width (rotated height)
 *     3. Draw the text rotated -90° around the slot's center
 *
 *   Result: "Quick picks" renders fully, no clipping, ever.
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
                .statusBarsPadding()  // aligns gear icon with the "Songs" title vertically
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Top icon: gear (Main mode) or back arrow (Settings mode) ----
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

            // ---- Rail labels ----
            // ViTune's labels start right below the gear icon and flow
            // downward with a tight gap. Any leftover vertical space stays
            // empty at the BOTTOM (not centered).
            // Previous bug: I had Spacer(weight=1f) ABOVE the labels, which
            // pushed them to the vertical center — that's why they looked
            // "spread out" compared to ViTune.
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

            // Leftover space goes to the BOTTOM (matches ViTune)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A single rotated label, drawn on Canvas with TextMeasurer.
 *
 * The slot width is 48dp (rail width). The slot height is sized EXACTLY
 * to the text's natural width (so after rotation, the text fits vertically
 * with no clipping). The text is drawn centered in the slot, rotated -90°
 * around the slot's center.
 */
@Composable
private fun RailLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) Color.White else CoralColors.TextMuted
    val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    // Step 1: measure the text at its natural size (no parent constraints).
    val textMeasurer = rememberTextMeasurer()
    val layoutResult = remember(label, color, weight) {
        textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = color,
                fontSize = 13.sp,
                fontWeight = weight
            ),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(
                minWidth = 0,
                minHeight = 0,
                maxWidth = Int.MAX_VALUE,
                maxHeight = Int.MAX_VALUE
            )
        )
    }

    // Step 2: convert text size from px to dp.
    val density = LocalDensity.current
    val textWidthDp = with(density) { layoutResult.size.width.toDp() }
    val textHeightPx = layoutResult.size.height.toFloat()

    // Step 3: build the slot.
    // Width = 48dp (rail width). Height = text natural width (so after rotation,
    // the text fits vertically with zero clipping).
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(textWidthDp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Step 4: draw the rotated text on a Canvas centered in the slot.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val textWidthPx = layoutResult.size.width.toFloat()
            val textHeight = textHeightPx

            // Rotate the canvas -90° around its center, then draw the text
            // centered. After rotation, the text fits perfectly within the
            // (canvasWidth, canvasHeight) bounds because:
            //   - canvasHeight was set to textWidthDp (= textWidthPx in px)
            //   - textWidthPx <= canvasHeight, so vertical fit
            //   - textHeight (~14dp) < canvasWidth (48dp), so horizontal fit
            rotate(degrees = -90f, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)) {
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(
                        x = (canvasWidth - textWidthPx) / 2f,
                        y = (canvasHeight - textHeight) / 2f
                    )
                )
            }
        }
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
    Albums("Albums"),
    Folders("Folders")
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
