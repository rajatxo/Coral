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
import com.rajatxo.coral.ui.theme.CalSansFamily

// ★ BLUR VALUE — easy to change. User can tell me the best value.
//   Higher = more blur. 12f is a good starting point (matches nav bar).
private const val SEARCH_BLUR_RADIUS = 12f

// ★ Tint overlay — how dark the glass looks. 0.3f = 30% black overlay.
private const val SEARCH_TINT_ALPHA = 0.3f

/**
 * ★ CynthiaSearchScreen — glass card that drops from the top of the screen.
 *
 * Design:
 *   - Card starts at the TOP of the screen (flush, no rounded top corners)
 *   - Bottom corners are rounded (24dp)
 *   - Kyant backdrop glass morphism (blurs whatever is behind it)
 *   - Card NEVER covers the nav bar, search FAB, or miniplayer
 *
 * Expansion logic:
 *   - When NO miniplayer: card extends from top of screen to just above nav bar
 *   - When miniplayer IS present: card shrinks vertically, sits above miniplayer
 *   - Nav bar + search FAB + miniplayer are ALWAYS visible below the card
 *
 * The card's bottom padding adjusts dynamically based on:
 *   - isMiniPlayerVisible: true if a song is playing and miniplayer is shown
 *
 * Content inside the card (search field, results, history) will be added later.
 * For now this is just the structure.
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

    // ★ Card bottom padding — adjusts based on miniplayer presence.
    //   When miniplayer is visible: card ends above the miniplayer (120dp from bottom)
    //   When no miniplayer: card ends above the nav bar (80dp from bottom)
    val bottomPadding = if (isMiniPlayerVisible) 120.dp else 80.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // ─── Search glass card ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .clip(cardShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = 0.05f,
                                    contrast = 1f,
                                    saturation = 1.4f
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
        ) {
            // ─── Card content (placeholder for now) ──────────────────
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
                    // Close button
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
}
