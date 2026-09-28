package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.prefs.SpiralPaletteStyle
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * SpiralPaletteSettingsScreen — Settings → Spiral Palette Style.
 *
 * Lets the user pick from 4 different palette extraction strategies
 * for the Spiral player's background gradient. Each produces a
 * different look from the same album art.
 */
@Composable
fun SpiralPaletteSettingsScreen(
    onBackClick: () -> Unit
) {
    val currentStyle by SpiralPaletteStyle.style.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Top bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    text = "Spiral Palette",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }

            Spacer(Modifier.height(8.dp))

            // ─── Style cards ───
            SpiralPaletteStyle.PaletteStyle.values().forEach { style ->
                PaletteStyleCard(
                    style = style,
                    isSelected = style == currentStyle,
                    onClick = { SpiralPaletteStyle.setStyle(style) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))

            // ─── Explainer ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Each style extracts different colors from the album art and applies different saturation boosts. Try all four and see which one you like best.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PaletteStyleCard(
    style: SpiralPaletteStyle.PaletteStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)

    // Preview gradient — shows what this style looks like
    val previewGradient = when (style) {
        SpiralPaletteStyle.PaletteStyle.BLUR -> arrayOf(
            0.0f to Color(0xFF1A1A2E),
            0.5f to Color(0xFF16213E),
            1.0f to Color(0xFF05050A)
        )
        SpiralPaletteStyle.PaletteStyle.VIBRANT -> arrayOf(
            0.0f to Color(0xFF6C5CE7),
            0.5f to Color(0xFFA29BFE),
            1.0f to Color(0xFF05050A)
        )
        SpiralPaletteStyle.PaletteStyle.DOMINANT -> arrayOf(
            0.0f to Color(0xFF2D3436),
            0.5f to Color(0xFF636E72),
            1.0f to Color(0xFF05050A)
        )
        SpiralPaletteStyle.PaletteStyle.MUTED -> arrayOf(
            0.0f to Color(0xFF636E72),
            0.5f to Color(0xFFB2BEC3),
            1.0f to Color(0xFF05050A)
        )
        SpiralPaletteStyle.PaletteStyle.DEEP -> arrayOf(
            0.0f to Color(0xFF1A1A2E),
            0.5f to Color(0xFF16213E),
            1.0f to Color(0xFF05050A)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(cardShape)
            .background(Color(0xFF1A1A1A))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.1f),
                shape = cardShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Preview gradient circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(colorStops = previewGradient)
                )
        )
        Spacer(Modifier.size(16.dp))
        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.displayName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = style.description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                lineHeight = 14.sp
            )
        }
        // Selected check
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
