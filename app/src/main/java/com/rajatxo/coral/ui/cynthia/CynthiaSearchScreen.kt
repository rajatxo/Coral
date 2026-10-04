package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * CynthiaSearchScreen — simple placeholder page.
 *
 * For now this is just a back button + "Search coming soon" text.
 * The billing structure (printer slot + paper) was reverted per user
 * request. Will be rebuilt properly later when the design is finalized.
 */
@Composable
fun CynthiaSearchScreen(
    onDismiss: () -> Unit
) {
    val cynthiaBg = Color(0xFF050507)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cynthiaBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Header: back button ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
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
            }

            // ─── Body: placeholder ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search coming soon",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CalSansFamily
                )
            }
        }
    }
}
