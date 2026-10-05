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
 * ★ CynthiaMiniPlayer — simple glass capsule miniplayer for Cynthia UI.
 *
 * Same shape + size + behavior as Astra's miniplayer:
 *   [Cover circle + progress ring LEFT] [Song name + Artist name CENTER]
 *
 * Glass morphism via kyant backdrop. No bloom morph — just a simple
 * fade transition to the full player (like Astra).
 *
 * Gestures:
 *   - Tap → opens full player
 *   - Long press → opens customization panel
 *   - Swipe up → opens full player
 *   - Swipe left/right → dismiss (pause + hide)
 */
@Composable
fun CynthiaMiniPlayer(
    title: String,
    artist: String,
    albumArtUri: android.net.Uri?,
    songId: Long?,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
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

    // ★ Drag state — swipe up = open, swipe left/right = dismiss
    var dragDirection by remember { mutableStateOf<Int?>(null) }
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    val screenWidthPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val maxSwipeUpPx = with(density) { 60.dp.toPx() }

    LaunchedEffect(isFullPlayerOpen) {
        if (!isFullPlayerOpen) {
            scope.launch {
                offsetY.snapTo(0f)
                offsetX.snapTo(0f)
                scale.snapTo(1f)
            }
        }
    }

    // ★ Read customization from prefs
    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaMiniPlayerCustomization
        .customization.collectAsState()

    val miniWidth = cardCustom.widthDp.dp
    val miniHeight = cardCustom.heightDp.dp
    val miniShape = RoundedCornerShape(cardCustom.cornerRadiusDp.dp)
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Drag gesture handlers
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
                translationX = offsetX.value + cardCustom.offsetX
                translationY = offsetY.value.coerceAtLeast(-maxSwipeUpPx) + cardCustom.offsetY
                val s = scale.value
                scaleX = s
                scaleY = s
                alpha = (1f - abs(offsetX.value) / screenWidthPx).coerceIn(0f, 1f) *
                    (1f - (abs(offsetY.value) / maxSwipeUpPx)).coerceIn(0f, 1f)
            }
            .then(dragModifier)
    ) {
        // ─── Glass capsule body ─────────────────────────────────────────
        val bodyModifier = if (backdrop != null) {
            Modifier
                .width(miniWidth)
                .height(miniHeight)
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
                .border(1.dp, Color.White.copy(alpha = 0.18f), miniShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onShowCustomizationPanel() }
                    )
                }
        } else {
            Modifier
                .width(miniWidth)
                .height(miniHeight)
                .clip(miniShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), miniShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onShowCustomizationPanel() }
                    )
                }
        }
        Box(modifier = bodyModifier) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── LEFT: circular album art with progress ring ──────────
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = androidx.compose.ui.geometry.Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                        drawArc(
                            color = Color.White.copy(alpha = 0.15f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── RIGHT: favorite heart button (matches Astra's style) ────
                val favorites by com.rajatxo.coral.data.store.PlaylistStore.favorites.collectAsState()
                val isFavorite = songId != null && songId in favorites.songIds
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                songId?.let {
                                    com.rajatxo.coral.data.store.PlaylistStore.toggleFavorite(it)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) CoralIcons.HeartFilledMaterial
                                      else CoralIcons.HeartOutline,
                        contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                        tint = if (isFavorite) Color(0xFFFF6B6B) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
