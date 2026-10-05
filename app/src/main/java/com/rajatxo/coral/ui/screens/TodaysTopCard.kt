package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

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
 * prefs — user can adjust via the panel OR by dragging the card itself.
 *
 * DRAG-TO-MOVE:
 *   User can drag the card body anywhere on screen. The drag updates
 *   offsetX/offsetY live and persists to prefs on drag end. NO
 *   clamping — the card can go anywhere the finger goes. This lets
 *   the user position the card at extreme right / extreme bottom / etc.
 *
 *   The cover, title, play/pause, and menu icons still work — taps
 *   are distinguished from drags by movement distance (<20px = tap).
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
    //   changes values in the customization panel or drags the card).
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
        .customization.collectAsState()

    // ★ Live drag offset — starts at the saved pref, updates during drag,
    //   saved back to prefs on drag end.
    var liveOffsetX by remember { mutableStateOf(cardCustom.offsetX) }
    var liveOffsetY by remember { mutableStateOf(cardCustom.offsetY) }
    val scope = rememberCoroutineScope()

    // Re-sync live offset when the pref changes from elsewhere (panel).
    androidx.compose.runtime.LaunchedEffect(cardCustom.offsetX, cardCustom.offsetY) {
        liveOffsetX = cardCustom.offsetX
        liveOffsetY = cardCustom.offsetY
    }

    val cornerRadius = cardCustom.cornerRadiusDp.dp
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

    // ★ RippleDismissContainer — wraps the scrim + AGSL ripple + card.
    //   The card content receives `progress` (0 → 1) so it can fade/scale
    //   in sync with the ripple animation. Card fades out + scales down
    //   slightly + drifts upward — feels like it's being absorbed into
    //   the ripple, not just disappearing.
    RippleDismissContainer(onDismiss = onDismiss) { progress ->
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        liveOffsetX.toInt(),
                        liveOffsetY.toInt()
                    )
                }
                .width(cardCustom.widthDp.dp)
                .height(cardCustom.heightDp.dp)
                // ★ Card fade-out — MUST come BEFORE .clip() and .drawBackdrop()
                //   so graphicsLayer wraps the ENTIRE capsule (glass + border +
                //   content). If placed after, only the inner content fades
                //   and the glass capsule stays fully visible until it
                //   suddenly vanishes.
                .rippleFadeOut(progress)
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
                // ★ Drag-to-move: drag the card body anywhere. No clamping.
                //   Live updates liveOffsetX/Y; saves to prefs on drag end.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                                .setOffset(liveOffsetX, liveOffsetY)
                        },
                        onDragCancel = {
                            // Revert to last saved pref on cancel.
                            liveOffsetX = cardCustom.offsetX
                            liveOffsetY = cardCustom.offsetY
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            liveOffsetX += dragAmount.x
                            liveOffsetY += dragAmount.y
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── Cover circle (LEFT) ────────────────────────────────────
                // Cover size scales with card height — keeps the cover
                // proportional when the user changes Height in the panel.
                // ★ A small pin icon sits at the top-right of the cover.
                //   Tap the pin → toggles pin state for this song in the
                //   Speed Dial. Pinned songs go to the front of the Speed
                //   Dial pin list (max 5 — oldest gets kicked out).
                val coverSize = (cardCustom.heightDp - 18f).coerceAtLeast(28f).dp

                // ★ Pin state for this song (live updates when pin toggled).
                val pinnedIds by com.rajatxo.coral.data.prefs.SpeedDialPinStore.pinnedIds
                    .collectAsState()
                val isSongPinned = song.id in pinnedIds

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(coverSize)
                ) {
                    // Album cover (fills the circle)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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

                    // ★ Pin icon — top-right of the cover, small badge.
                    //   Tap to toggle pin state. Filled coral color when
                    //   pinned; translucent white when not.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSongPinned) Color(0xFFFF6B6B)
                                else Color.Black.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSongPinned) Color.White
                                        else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false)
                            ) {
                                com.rajatxo.coral.data.prefs.SpeedDialPinStore.togglePin(song.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Pin,
                            contentDescription = if (isSongPinned) "Unpin from Speed Dial" else "Pin to Speed Dial",
                            tint = if (isSongPinned) Color.White else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(10.dp)
                        )
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
                // Small size (22dp) — smaller per user request so it fits
                // in the narrower default card width (188dp).
                val isCurrent = song.id == currentSongId
                Icon(
                    imageVector = if (isCurrent && isPlaying) CoralIcons.PauseLucide
                                  else CoralIcons.PlayLucide,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
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
                        .size(22.dp)
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
