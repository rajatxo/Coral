package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.rajatxo.coral.data.prefs.CoralFont
import com.rajatxo.coral.data.prefs.FontManager
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Font picker screen — opened from Settings → Appearance → Font.
 *
 * Layout:
 *  - Top bar: ChevronDown (back) | "FONT" label
 *  - List of fonts (excluding the screen title):
 *    - System default
 *    - Poppins (with the 9 weights from the user's zip)
 *    - Inter (variable font)
 *    - Manrope (variable font)
 *    - Nunito Sans (variable font)
 *    - Space Grotesk (variable font)
 *  - Each row renders its OWN NAME in its own font, so the user can
 *    preview the look before selecting.
 *  - Selected font has a coral check mark on the right.
 */
@Composable
fun FontPickerScreen(
    onBackClick: () -> Unit
) {
    val currentFont by FontManager.currentFont.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoralColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "FONT",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.size(40.dp))  // balance the back button
            }

            // Font list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(CoralFont.values(), key = { it.id }) { font ->
                    FontRow(
                        font = font,
                        isSelected = font.id == currentFont.id,
                        onClick = {
                            FontManager.setFont(font)
                            onBackClick()  // auto-close so the user sees the change
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FontRow(
    font: CoralFont,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CoralColors.SurfaceVariant else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Render the font's NAME in its OWN font so the user previews it
            Text(
                text = font.displayName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font.family
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "The quick brown fox jumps ${if (font.id == "system") "(system)" else "over the lazy dog"}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontFamily = font.family
            )
        }
        if (isSelected) {
            Text(
                text = "✓",
                color = CoralColors.Coral,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
