package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ TodaysTopCard — miniplayer-inspired floating glass capsule.
 *
 * Shown when user taps a Today's Top cover in QuickPicksScreen.
 * Same shape and size as Astra's mini player (capsule, 32dp rounded
 * corners, kyant backdrop blur). Inside:
 *
 *   [Cover circle] [Title + artist] [Play/Pause icon] [Menu icon]
 *
 * Play/Pause icon style matches Spiral player (plain Lucide icon,
 * no circle, with ripple).
 *
 * Card size/shape/position all read from CynthiaTodaysTopCardCustomization
 * prefs — user can open the square customization panel via the menu icon
 * and adjust Width/Height/Corner/Pos X/Pos Y/Shape live.
 *
 * SCREEN-AWARE CLAMPING:
 *   Even if the user sets Width=400 AND Pos X=300 (max), the card
 *   never goes off-screen horizontally. The rendered width is capped
 *   to `screenWidth - 32dp` (16dp margin each side), and the offset
 *   is clamped so the card's edges stay within screen bounds.
 */
@Composable
fun TodaysTopCard(
    visible: Boolean,
    song: Song?,
    currentSongId: Long?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlayPauseClick: (Song) -> Unit,
    onMenuClick: (Song) -> Unit = {},
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    if (!visible || song == null) return

    // ★ Card customization — read from prefs (live updates when the user
    //   changes values in the customization panel).
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
        .customization.collectAsState()

    val density = LocalDensity.current

    // ★ Outer BoxWithConstraints — gives us the parent (screen) width so
    //   we can clamp the card's width + offset to keep it on-screen.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        val screenMaxWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        // ─── Cap rendered width to (screenWidth - 32dp) so the card
        //   always fits horizontally with a 16dp margin on each side.
        //   Even if the user sets Width=400 on a narrow phone, the
        //   card will be capped to fit the screen.
        val marginPx = with(density) { 16.dp.toPx() }
        val maxAllowedWidthPx = (screenMaxWidthPx - marginPx * 2f).coerceAtLeast(0f)
        val requestedWidthPx = with(density) { cardCustom.widthDp.dp.toPx() }
        val effectiveWidthPx = requestedWidthPx.coerceAtMost(maxAllowedWidthPx)
        val effectiveWidthDp = with(density) { effectiveWidthPx.toDp() }

        // ─── Clamp offset X so the card stays within screen bounds.
        //   Card is centered by Alignment.Center, then offset by
        //   (offsetX, offsetY). The card's center after offset is at
        //   (screenWidth/2 + offsetX). For the card's edges to stay
        //   on-screen:
        //     left edge  = centerX - width/2 ≥ 0
        //     right edge = centerX + width/2 ≤ screenWidth
        //   → -halfWidth + screenHalf ≤ offsetX ≤ halfWidth - screenHalf
        //   (in absolute pixel terms from the center)
        val halfCardPx = effectiveWidthPx / 2f
        val halfScreenPx = screenMaxWidthPx / 2f
        val maxOffsetX = (halfScreenPx - halfCardPx).coerceAtLeast(0f)
        val minOffsetX = -maxOffsetX
        val clampedOffsetX = cardCustom.offsetX.coerceIn(minOffsetX, maxOffsetX)

        // ─── Clamp offset Y similarly so the card never goes off the
        //   top or bottom of the screen.
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val requestedHeightPx = with(density) { cardCustom.heightDp.dp.toPx() }
        val effectiveHeightPx = requestedHeightPx.coerceAtMost(screenHeightPx)
        val halfCardHeightPx = effectiveHeightPx / 2f
        val halfScreenHeightPx = screenHeightPx / 2f
        val maxOffsetY = (halfScreenHeightPx - halfCardHeightPx).coerceAtLeast(0f)
        val minOffsetY = -maxOffsetY
        val clampedOffsetY = cardCustom.offsetY.coerceIn(minOffsetY, maxOffsetY)

        val cornerRadius = cardCustom.cornerRadiusDp.dp
        val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        clampedOffsetX.toInt(),
                        clampedOffsetY.toInt()
                    )
                }
                .width(effectiveWidthDp)
                .height(with(density) { effectiveHeightPx.toDp() })
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
                                    saturation = 1.5f
                                )
                                blur(12f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = 0.25f))
                            }
                        )
                    } else {
                        Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.88f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), cardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    // Consume click so tapping inside the capsule doesn't dismiss.
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── Cover circle (LEFT) ────────────────────────────────────
                // Cover size scales with card height — keeps the cover
                // proportional when the user changes Height in the panel.
                val coverSize = (cardCustom.heightDp - 18f).coerceAtLeast(28f).dp
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(coverSize)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CoralIcons.Music,
                                contentDescription = null,
                                tint = Color(0xFFB0B0B0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ─── Title + artist (MIDDLE) ───────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ─── Play/Pause (Spiral player style) ──────────────────────
                // Plain Lucide icon, no circle background, ripple on tap.
                // Smaller size (28dp) per user request.
                val isCurrent = song.id == currentSongId
                Icon(
                    imageVector = if (isCurrent && isPlaying) CoralIcons.PauseLucide
                                  else CoralIcons.PlayLucide,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) {
                            onPlayPauseClick(song)
                        }
                )

                // ─── Menu icon ────────────────────────────────────────────
                Icon(
                    imageVector = CoralIcons.MoreVertical,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        ) {
                            onMenuClick(song)
                        }
                )
            }
        }
    }
}


