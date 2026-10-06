package com.rajatxo.coral.ui.cynthia

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.screens.RippleDismissContainer
import com.rajatxo.coral.ui.screens.rippleFadeOut
import com.rajatxo.coral.ui.theme.CalSansFamily

// ★ BLUR VALUES — user can tell me the best values.
private const val SEARCH_BLUR_RADIUS = 40f      // blur radius in dp
private const val SEARCH_TINT_ALPHA = 0.33f      // 33% black (was 45%, now lighter)
private const val SEARCH_SATURATION = 1.6f       // 160% saturation (boosted)
private const val SEARCH_BRIGHTNESS = 0.08f       // 8% brightness

/**
 * ★ CynthiaSearchScreen — glass card that drops from the top of the screen.
 *
 * Design:
 *   - Card starts at TOP of screen (flush, no rounded top corners)
 *   - Bottom corners rounded (24dp)
 *   - Kyant backdrop glass morphism
 *   - Card NEVER covers nav bar, search FAB, or miniplayer
 *
 * Expansion logic:
 *   - When NO miniplayer: card extends from top to just above nav bar (small gap)
 *   - When miniplayer IS present: card shrinks, sits above miniplayer
 *   - Card follows miniplayer's saved Y offset (if user moved miniplayer up/down)
 *
 * Dismiss:
 *   - Tap outside → water ripple + card fades out (same as TodaysTopCard)
 */
@Composable
fun CynthiaSearchScreen(
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
    isMiniPlayerVisible: Boolean = false
) {
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

    // ★ Read miniplayer's saved offset so the card can follow it.
    //   If the user moved the miniplayer up/down via the customization panel,
    //   the search card adjusts its bottom padding accordingly.
    val miniPlayerCustom by com.rajatxo.coral.data.prefs.CynthiaMiniPlayerCustomization
        .customization.collectAsState()
    val density = LocalDensity.current

    // ★ Bottom padding — how much space to leave at the bottom of the screen
    //   for nav bar + search FAB + miniplayer.
    //
    //   When miniplayer visible: nav bar (~80dp) + miniplayer height (~64dp)
    //     + gap (~10dp) + miniplayer's saved offsetY = ~154dp + offset
    //   When no miniplayer: nav bar (~80dp) + small gap (~4dp) = ~84dp
    //
    //   The miniplayer's offsetY is added so if the user moved the miniplayer
    //   up (negative offset), the card extends further down. If moved down
    //   (positive offset), the card shrinks.
    val miniPlayerOffsetY = miniPlayerCustom.offsetY
    val bottomPadding = if (isMiniPlayerVisible) {
        // Nav bar (80dp) + miniplayer (64dp) + gap (10dp) + offset = 154dp + offset
        (154f + miniPlayerOffsetY).coerceAtLeast(80f).dp
    } else {
        // Just nav bar + tiny gap
        84.dp
    }

    // ★ RippleDismissContainer — water ripple dismiss + card fade-out.
    //   Same technique as TodaysTopCard: card fades + scales + drifts up
    //   in sync with the ripple animation.
    RippleDismissContainer(onDismiss = onDismiss) { progress ->
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // ─── Search glass card ───────────────────────────────────────
        //   Offscreen compositing so the glass blur renders evenly across
        //   the ENTIRE card (including edges — fixes the "edges not blurring"
        //   bug). Without this, kyant samples differently at the clip boundary.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(
                    androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                        .minus(bottomPadding)
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(cardShape)
                // ★ Background fallback — fills the card with a solid dark
                //   color BEFORE drawBackdrop. This ensures the edges have
                //   SOMETHING to blur even if kyant's sampling is weaker at
                //   the clip boundary. Fixes "edges still transparent" bug.
                .background(Color(0xFF0A0A0F))
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = SEARCH_BRIGHTNESS,
                                    contrast = 1f,
                                    saturation = SEARCH_SATURATION
                                )
                                blur(SEARCH_BLUR_RADIUS.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = SEARCH_TINT_ALPHA))
                            }
                        )
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.7f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), cardShape)
                // ★ Card fade-out — fades + scales + drifts up in sync
                //   with the ripple animation (same as TodaysTopCard).
                .rippleFadeOut(progress)
                .statusBarsPadding()
                // ★ Consume clicks inside the card so they don't trigger the
                //   ripple dismiss scrim behind it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header row: title + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CalSansFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Search content goes here",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontFamily = CalSansFamily
                )
            }
        }
    }
    }  // ← closes RippleDismissContainer
}
