package com.rajatxo.coral.ui.premium

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.premium.PremiumManager
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Premium info screen — shown when the user taps "Premium" in Settings.
 *
 * Layout:
 *  - Hero block with "Coral Premium" title + benefits
 *  - Feature list with checkmarks
 *  - Big "Unlock Premium" button (production: opens Play Billing flow;
 *    debug: long-press version row 7x in Settings instead)
 *
 * If already premium:
 *  - Shows "Premium active" hero with coral gradient
 *  - Shows "Manage subscription" placeholder button (Phase 7B will wire
 *    this to Play Billing's subscription management deep link)
 */
@Composable
fun PremiumScreen(
    onBackClick: () -> Unit
) {
    val isPremium by PremiumManager.isPremium.collectAsState()

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Top bar (back button only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isPremium) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF6B6B),
                                    Color(0xFFFF8E53)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            )
                        }
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = if (isPremium) "Premium Active ✓" else "🪸 Coral Premium",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isPremium) {
                            "All premium features unlocked. Thank you for supporting Coral."
                        } else {
                            "Unlock the full Coral experience with premium features designed for music lovers."
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    if (!isPremium) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFFF6B6B))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Phase 7B: open Play Billing flow */ }
                                )
                                .padding(horizontal = 32.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "Unlock Premium",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹149 / month · ₹1,499 / year",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feature list
            Text(
                text = "WHAT YOU GET",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val features = listOf(
                "5-band equalizer with presets" to "Bass Boost, Vocal, Treble Boost, more",
                "Sleep timer" to "Drift off to music without draining your battery",
                "Crossfade between songs" to "Smooth transitions, no awkward silence",
                "Lyrics on every song" to "Already free in Coral — always will be",
                "Priority support" to "Email us, we'll respond within 24 hours",
                "Future premium features" to "All premium features added after you subscribe"
            )

            features.forEach { (title, subtitle) ->
                FeatureRow(title = title, subtitle = subtitle)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Premium toggle hint (debug)
            if (!isPremium) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141414))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "💡 Tip: long-press the version row in Settings 7 times to unlock premium in debug builds.",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF6B6B).copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✓", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp
            )
        }
    }
}
