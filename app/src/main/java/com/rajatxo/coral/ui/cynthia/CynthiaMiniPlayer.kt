package com.rajatxo.coral.ui.cynthia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ★ CynthiaMiniPlayer — glass capsule miniplayer for Cynthia UI.
 *
 * Clean design:
 *   [Cover circle LEFT] [Song name + Artist name CENTER]
 *
 * Glass morphism via kyant backdrop (same as nav bar). No heart, no counter,
 * no extra labels — just cover + title + artist.
 *
 * BLOOM MORPH ANIMATION:
 *   When user drags up or taps, the miniplayer BLOOMS into the full player:
 *   - The pill scales up to fill the screen (scale 1.0 → ~12x)
 *   - Corner radius animates from pill (half height) → 0 (full rectangle)
 *   - Album art grows proportionally
 *   - Text fades out as it scales up
 *   - Full player fades in on top
 *
 * The expansion is driven by `expansionFraction` (0 = collapsed, 1 = fully
 * expanded). The caller reads this fraction to control the full player's
 * alpha.
 */
@Composable
fun CynthiaMiniPlayer(
    title: String,
    artist: String,
    albumArtUri: android.net.Uri?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    expansionFraction: Float,
    onExpansionChange: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDismiss: () -> Unit,
    isFullPlayerOpen: Boolean,
    onShowCustomizationPanel: () -> Unit = {},
    backdrop: LayerBackdrop? = null
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ★ Drag state — swipe up = open full player, swipe left/right = dismiss
    var dragDirection by remember { mutableStateOf<Int?>(null) }  // 0=H, 1=V
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    val screenWidthPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val maxSwipeUpPx = with(density) { 60.dp.toPx() }

    // Reset offsets when full player closes
    LaunchedEffect(isFullPlayerOpen) {
        if (!isFullPlayerOpen) {
            scope.launch {
                offsetY.snapTo(0f)
                offsetX.snapTo(0f)
                scale.snapTo(1f)
            }
        }
    }

    // ★ MORPH dimensions — the pill ACTUALLY grows to fill the screen,
    //   not scaled up. This way the border + glass blur redraw at the
    //   new size (sharp, not pixelated) and the pill's top edge actually
    //   expands upward (which the user wants to see).
    //
    //   At expansion=0: 240×64dp pill at the bottom
    //   At expansion=1: full screen (matches the full player's frame)
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaMiniPlayerCustomization
        .customization.collectAsState()

    val screenWidthDp = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    }
    val screenHeightDp = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    }
    // Lerp width: saved width → screen width
    val morphWidth = androidx.compose.ui.unit.lerp(
        cardCustom.widthDp.dp, screenWidthDp, expansionFraction
    )
    // Lerp height: saved height → screen height
    val morphHeight = androidx.compose.ui.unit.lerp(
        cardCustom.heightDp.dp, screenHeightDp, expansionFraction
    )
    // Lerp corner radius: saved corner → 0dp (rectangle)
    val cornerRadius = (cardCustom.cornerRadiusDp * (1f - expansionFraction)).coerceAtLeast(0f).dp
    val miniShape = RoundedCornerShape(cornerRadius)

    // ★ Content alpha — fades out so the full player shows through.
    //   Stays at 1.0 until 40% expansion (so the morph is visible),
    //   then fades to 0 by 90%.
    val contentAlpha = (1f - ((expansionFraction - 0.4f) / 0.5f)).coerceIn(0f, 1f)

    // ★ Upward translation — as the pill grows, it also moves UP so its
    //   center stays roughly in place (instead of growing downward off
    //   the screen). At expansion=0, no translation. At expansion=1,
    //   the pill has grown to full screen so translation = 0 (it fills
    //   the screen from top to bottom).
    //
    //   During the morph, translate up by: (grown height - original height) / 2
    //   so the pill's vertical center stays put. But we also want it to
    //   end up filling the screen, so cap the translation so the top
    //   edge reaches y=0 by expansion=1.
    val originalHeightPx = with(density) { cardCustom.heightDp.dp.toPx() }
    val morphHeightPx = with(density) { morphHeight.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    // Translation so the pill's BOTTOM stays anchored at its original position
    // while the TOP grows upward. At expansion=0: translation=0. At expansion=1:
    // translation = -(screenHeightPx - originalHeightPx) so the top edge
    // reaches y=0.
    val bloomTranslationY = -(morphHeightPx - originalHeightPx)

    // Drag gesture handlers:
    //   - Swipe UP → bloom morph into full player
    //   - Swipe LEFT/RIGHT → dismiss (slide off + fade, pause playback)
    //   - Tap → open full player (detectTapGestures on body)
    //   - Long press → customization panel (detectTapGestures on body)
    val dragModifier = if (!isFullPlayerOpen) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    dragDirection = null
                    totalDragX = 0f
                    totalDragY = 0f
                    scope.launch { scale.snapTo(0.96f) }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y
                    // Lock direction once drag exceeds 20px
                    if (dragDirection == null) {
                        if (abs(totalDragX) > 20f || abs(totalDragY) > 20f) {
                            dragDirection = if (abs(totalDragX) > abs(totalDragY)) 0 else 1
                        }
                    }
                    when (dragDirection) {
                        0 -> scope.launch { offsetX.snapTo(totalDragX) }
                        1 -> {
                            if (totalDragY < 0) {
                                scope.launch { offsetY.snapTo(totalDragY * 0.4f) }
                            }
                        }
                    }
                },
                onDragEnd = {
                    scope.launch {
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    when (dragDirection) {
                        0 -> {
                            if (abs(totalDragX) > screenWidthPx * 0.4f) {
                                val target = if (totalDragX < 0) -screenWidthPx else screenWidthPx
                                scope.launch {
                                    offsetX.animateTo(target, tween(200))
                                    onSwipeDismiss()
                                    delay(100)
                                    offsetX.snapTo(0f)
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        }
                        1 -> {
                            if (totalDragY < -30f) {
                                scope.launch {
                                    offsetY.animateTo(-maxSwipeUpPx * 2f, tween(200))
                                    onSwipeUp()
                                }
                            } else {
                                scope.launch {
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                // ★ NO offscreen compositing — we're morphing the actual
                //   dimensions (not scaling a buffer), so the border + glass
                //   blur redraw sharp at every size during the animation.
                // ★ NO scale — width/height are lerped directly (morphWidth/morphHeight)
                translationX = offsetX.value + cardCustom.offsetX
                translationY = offsetY.value.coerceAtLeast(-maxSwipeUpPx) + bloomTranslationY + cardCustom.offsetY
                // Drag scale (subtle, for press feedback) only — no bloom scale
                val s = scale.value
                scaleX = s
                scaleY = s
                // Fade during horizontal swipe dismiss OR content fade during morph
                alpha = (1f - abs(offsetX.value) / screenWidthPx).coerceIn(0f, 1f) *
                    (1f - (abs(offsetY.value) / maxSwipeUpPx)).coerceIn(0f, 1f) *
                    contentAlpha
            }
            .then(dragModifier)
    ) {
        // ─── Glass capsule body ─────────────────────────────────────────
        val bodyModifier = if (backdrop != null) {
            Modifier
                .width(morphWidth)
                .height(morphHeight)
                .clip(miniShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { miniShape },
                    effects = {
                        vibrancy()
                        colorControls(
                            brightness = 0.05f,
                            contrast = 1f,
                            saturation = 1.4f
                        )
                        blur(20f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.Black.copy(alpha = 0.35f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f * contentAlpha), miniShape)
                // ★ Tap → open full player. Long press (hold) → open customization panel.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onShowCustomizationPanel() }
                    )
                }
        } else {
            Modifier
                .width(morphWidth)
                .height(morphHeight)
                .clip(miniShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.18f * contentAlpha), miniShape)
                // ★ Tap → open full player. Long press (hold) → open customization panel.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onShowCustomizationPanel() }
                    )
                }
        }
        Box(modifier = bodyModifier) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── LEFT: circular album art with progress ring (SAME as Astra) ──
                val progress = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // ★ Progress ring (around album art — same as Astra)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = androidx.compose.ui.geometry.Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                        // Background ring
                        drawArc(
                            color = Color.White.copy(alpha = 0.15f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        // Progress ring
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                    // Album art circle
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumArtUri != null) {
                            AsyncImage(
                                model = albumArtUri,
                                contentDescription = "Album art",
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        // Play/pause overlay when paused
                        if (!isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CoralIcons.Play,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // ── CENTER: song name + artist name ────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
