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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.screens.WaterRippleScrim
import com.rajatxo.coral.ui.theme.CalSansFamily

// ★ BLUR VALUES — user can tell me the best values.
private const val SEARCH_BLUR_RADIUS = 40f      // blur radius in dp (was 12, now 40)
private const val SEARCH_TINT_ALPHA = 0.45f      // 45% black (was 30%, now matches nav bar hold card)
private const val SEARCH_SATURATION = 1.6f       // 160% saturation (was 140%, now boosted)
private const val SEARCH_BRIGHTNESS = 0.1f       // 10% brightness (was 5%, now matches nav bar hold card)

/**
 * ★ CynthiaSearchScreen — glass card that drops from the top of the screen.
 *
 * Design:
 *   - Card starts at the TOP of the screen (flush, no rounded top corners)
 *   - Bottom corners are rounded (24dp)
 *   - Kyant backdrop glass morphism (blur=40, tint=45%, saturation=160%)
 *   - Card NEVER covers the nav bar, search FAB, or miniplayer
 *
 * Expansion logic:
 *   - When NO miniplayer: card extends from top to just above nav bar
 *   - When miniplayer IS present: card shrinks, sits above miniplayer
 *
 * Dismiss:
 *   - Tap outside the card → water ripple effect + dismiss
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

    val bottomPadding = if (isMiniPlayerVisible) 120.dp else 80.dp

    // ★ Water ripple scrim — tap outside the card → ripple + dismiss.
    //   The scrim covers the ENTIRE screen but the card is rendered ON TOP
    //   of it, so taps inside the card are consumed by the card.
    com.rajatxo.coral.ui.screens.RippleDismissContainer(onDismiss = onDismiss) { progress ->
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // ─── Search glass card ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(
                    androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                        .minus(bottomPadding)
                )
                .clip(cardShape)
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
                .statusBarsPadding()
                .padding(bottom = bottomPadding)
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
