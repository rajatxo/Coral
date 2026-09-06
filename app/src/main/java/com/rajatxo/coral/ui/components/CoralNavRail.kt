package com.rajatxo.coral.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * The 3 destinations reachable from the rail.
 *
 * Keeping this enum (instead of raw ints) makes future changes — like adding a
 * "Search" tab — a compile-time refactor instead of a silent bug.
 */
enum class CoralTab(val label: String, val icon: ImageVector) {
    Songs("Songs", CoralIcons.Music),
    Playlists("Playlists", CoralIcons.ListMusic),
    Settings("Settings", CoralIcons.Settings)
}

/**
 * Coral's vertical navigation rail.
 *
 * Visual contract:
 *  - 72dp wide, full height, dark (#0F0F10) background
 *  - A small coral emoji logo sits at the very top
 *  - Three nav items stacked vertically, centered horizontally
 *  - The selected item has a frosted-glass pill behind it (white 8 % alpha)
 *  - The selected icon turns coral (#FF6B6B); unselected icons are #888
 *  - Selection animates: pill scale + color crossfade (~220ms)
 *
 * Designed to evoke the ViTune / SimpMusic rail without copying their assets.
 */
@Composable
fun CoralNavRail(
    selectedTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F0F10))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Coral logo / brand mark
            Text(
                text = "🪸",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            CoralTab.values().forEach { tab ->
                RailItem(
                    icon = tab.icon,
                    label = tab.label,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFF6B6B) else Color(0xFF888888),
        animationSpec = tween(220),
        label = "iconColor"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(220),
        label = "pillAlpha"
    )

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glass pill — only visible when selected
        if (pillAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = iconColor,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
