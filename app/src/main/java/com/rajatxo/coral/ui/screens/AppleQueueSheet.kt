package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.delay
import com.rajatxo.coral.ui.util.consistentNavBarPadding

/**
 * AppleQueueSheet — Apple Music style "Playing Next" queue.
 *
 * Features:
 *  - Dark gradient background (matches player aesthetic)
 *  - "Playing Next" header at top
 *  - Currently playing song highlighted with accent text
 *  - Song rows: 48dp square album art, title + artist, tap to jump
 *  - CalSans font throughout
 *  - Smooth slide-up animation from bottom
 *  - Closes by tapping outside or pressing back
 *
 * Used by ALL player variants (Spiral, Spiral 2.0, Spiral 3.0).
 * The opening method differs per player:
 *   - Spiral: tap the Queue button in the bottom row
 *   - Spiral 2.0: drag up from the bottom OR tap queue button
 *   - Spiral 3.0: tap the queue button in the menu
 *
 * @param visible Whether the sheet is currently visible (controls animation)
 * @param mediaController The MediaController to read/write queue state
 * @param onDismiss Called when the user wants to close the queue
 * @param accentColor The accent color from the current song's palette
 */
@Composable
fun AppleQueueSheet(
    visible: Boolean,
    mediaController: MediaController?,
    onDismiss: () -> Unit,
    accentColor: Color = Color(0xFFFF6B6B)
) {
    val view = LocalView.current

    // Queue state
    var currentIndex by remember { mutableIntStateOf(0) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Poll the controller for queue state
    LaunchedEffect(mediaController, visible) {
        while (visible) {
            try {
                mediaController?.let { controller ->
                    currentIndex = controller.currentMediaItemIndex
                    val count = controller.mediaItemCount
                    mediaItems = (0 until count).map { controller.getMediaItemAt(it) }
                }
            } catch (_: Exception) { }
            delay(300L)
        }
    }

    // Slide-up animation
    val sheetOffset = remember { Animatable(1f) }
    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            sheetOffset.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    if (!visible && sheetOffset.value >= 1f) return

    // Dark scrim background — tap to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f * (1f - sheetOffset.value)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )

    // Queue sheet — slides up from bottom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = size.height * sheetOffset.value
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to accentColor.copy(alpha = 0.15f),
                            0.15f to Color(0xFF0A0A0F),
                            1.0f to Color(0xFF05050A)
                        )
                    )
                )
                .statusBarsPadding()
                .consistentNavBarPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ─── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Playing Next",
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
                    Icon(
                        imageVector = CoralIcons.ChevronDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ─── Song count ──
            Text(
                text = "${mediaItems.size} song${if (mediaItems.size != 1) "s" else ""} in queue",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                fontFamily = CalSansFamily,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ─── Song list ──
            val listState = rememberLazyListState()

            // Scroll to current song on open
            LaunchedEffect(visible, currentIndex) {
                if (visible && currentIndex > 0 && mediaItems.isNotEmpty()) {
                    delay(200)
                    listState.animateScrollToItem(currentIndex.coerceAtMost(mediaItems.lastIndex))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(mediaItems, key = { it.mediaId }) { item ->
                    val index = mediaItems.indexOf(item)
                    val isCurrent = index == currentIndex

                    QueueSongRow(
                        item = item,
                        isCurrent = isCurrent,
                        accentColor = accentColor,
                        onClick = {
                            mediaController?.let { controller ->
                                if (index != currentIndex) {
                                    controller.seekToDefaultPosition(index)
                                    controller.prepare()
                                    controller.play()
                                }
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SONG ROW — one row per song in the queue
// ════════════════════════════════════════════════════════════════════

@Composable
private fun QueueSongRow(
    item: MediaItem,
    isCurrent: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val title = item.mediaMetadata.title?.toString() ?: "Unknown"
    val artist = item.mediaMetadata.artist?.toString() ?: "Unknown Artist"
    val artUri = item.mediaMetadata.artworkUri

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) accentColor.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art (square, 40dp, rounded)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (artUri != null) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCurrent) accentColor else Color.White,
                fontSize = 15.sp,
                fontFamily = CalSansFamily,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Currently playing indicator
        if (isCurrent) {
            // Small equalizer bars or a dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}
