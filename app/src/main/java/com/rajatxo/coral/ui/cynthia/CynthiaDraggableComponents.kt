package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.components.TabCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// CynthiaDraggableNavBar
// =============================================================================
// EXACT verbatim copy of Astra's DraggableTabCapsule (HomeScreen.kt lines
// 2053-2344). The ONLY changes from the original:
//   1. Function renamed to CynthiaDraggableNavBar
//   2. Visibility changed from `private` to `internal`
//   3. Added parameter `navBarWidth: Dp = 240.dp` so the caller can control
//      the capsule width (Cynthia's nav bar width is configurable).
//   4. `capsuleWidth` now uses `navBarWidth.toPx()` instead of the hardcoded
//      `240.dp.toPx()`.
//
// Everything else — the 2-second hold → 3-2-1 countdown bubble → scientist
// grid overlay → delta-based smooth dragging → position persistence via
// TabCapsulePosition — is IDENTICAL to Astra.
// =============================================================================

@Composable
internal fun CynthiaDraggableNavBar(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    backdrop: LayerBackdrop?,
    navBarWidth: Dp = 240.dp
) {
    val savedPosition by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    var screenSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentXpx by remember { mutableStateOf(0f) }
    var currentYpx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActivated by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // --- Grid overlay state (scientist graph paper, for alignment) ---
    var showGrid by remember { mutableStateOf(false) }
    val gridAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showGrid) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "gridAlpha"
    )

    // --- Last touch position (for delta-based smooth dragging) ---
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }

    var showBubble by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }

    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "bubbleScale"
    )
    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "bubbleAlpha"
    )

    val capsuleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "capsuleScale"
    )

    val capsuleWidth = with(density) { navBarWidth.toPx() }
    val capsuleHeight = with(density) { 52.dp.toPx() }

    androidx.compose.runtime.LaunchedEffect(savedPosition, screenSize) {
        if (screenSize.width > 0 && screenSize.height > 0) {
            currentXpx = savedPosition.first * screenSize.width
            currentYpx = savedPosition.second * screenSize.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {

            // ★ TEMPORARY: Show coordinates on screen when dragging
            if (isDragging && screenSize.width > 0 && screenSize.height > 0) {
                val xFrac = (currentXpx / screenSize.width).coerceIn(0f, 1f)
                val yFrac = (currentYpx / screenSize.height).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "X: ${String.format("%.3f", xFrac)}  Y: ${String.format("%.3f", yFrac)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- Scientist grid overlay (fades in during drag mode) ---
            // Graph-paper style grid for precise alignment. Fades in when
            // drag mode starts, fades out when capsule is placed.
            if (gridAlpha > 0.01f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = gridAlpha }
                ) {
                    val gridSpacing = 40f  // px between grid lines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    val majorColor = Color.White.copy(alpha = 0.15f)
                    val majorEvery = 4  // every 4th line is brighter

                    // Vertical lines
                    var x = 0f
                    var i = 0
                    while (x <= size.width) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        x += gridSpacing
                        i++
                    }

                    // Horizontal lines
                    var y = 0f
                    i = 0
                    while (y <= size.height) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        y += gridSpacing
                        i++
                    }

                    // Center crosshair (brighter, for alignment reference)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx - 30f, cy),
                        end = Offset(cx + 30f, cy),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx, cy - 30f),
                        end = Offset(cx, cy + 30f),
                        strokeWidth = 2f
                    )
                }
            }

            // --- Countdown speech bubble (above the capsule) ---
            if (bubbleAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (currentXpx - with(density) { 60.dp.toPx() }).toInt(),
                                (currentYpx - capsuleHeight - with(density) { 50.dp.toPx() }).toInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            alpha = bubbleAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hold to move in",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countdownNumber.toString(),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // --- The capsule (positioned via offset, draggable) ---
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (currentXpx - capsuleWidth / 2f).toInt()
                                .coerceIn(0, (screenSize.width - capsuleWidth).toInt()),
                            (currentYpx - capsuleHeight / 2f).toInt()
                                .coerceIn(0, (screenSize.height - capsuleHeight).toInt())
                        )
                    }
                    .graphicsLayer {
                        scaleX = capsuleScale
                        scaleY = capsuleScale
                    }
                    .pointerInput(tabs, activeTab) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                isLongPressActivated = false
                                val initialX = down.position.x
                                val initialY = down.position.y
                                lastTouchX = down.position.x
                                lastTouchY = down.position.y

                                countdownJob?.cancel()
                                countdownJob = scope.launch {
                                    delay(2000L)
                                    showBubble = true
                                    countdownNumber = 3
                                    delay(1000L)
                                    countdownNumber = 2
                                    delay(1000L)
                                    countdownNumber = 1
                                    delay(1000L)
                                    showBubble = false
                                    isLongPressActivated = true
                                    isDragging = true
                                    // Show scientist grid when drag mode starts
                                    showGrid = true
                                    // Record current touch position as baseline for delta tracking
                                    lastTouchX = down.position.x
                                    lastTouchY = down.position.y
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        if (isLongPressActivated) {
                                            val newXFraction = (currentXpx / screenSize.width)
                                                .coerceIn(0.05f, 0.95f)
                                            val newYFraction = (currentYpx / screenSize.height)
                                                .coerceIn(0.05f, 0.95f)
                                            com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                                                .setPosition(newXFraction, newYFraction)
                                        }
                                        isDragging = false
                                        isLongPressActivated = false
                                        showBubble = false
                                        showGrid = false  // hide grid when released
                                        countdownJob?.cancel()
                                        break
                                    }

                                    // Cancel countdown if finger moved (swipe, not hold)
                                    if (!isDragging && !isLongPressActivated) {
                                        val movedX = kotlin.math.abs(change.position.x - initialX)
                                        val movedY = kotlin.math.abs(change.position.y - initialY)
                                        if (movedX > 20f || movedY > 20f) {
                                            countdownJob?.cancel()
                                            showBubble = false
                                        }
                                    }

                                    // SMOOTH DRAGGING via delta tracking:
                                    // Calculate how much the finger moved SINCE LAST FRAME,
                                    // then move the capsule by the same delta. This prevents
                                    // the jump on drag start (because the first delta is ~0).
                                    if (isDragging) {
                                        val deltaX = change.position.x - lastTouchX
                                        val deltaY = change.position.y - lastTouchY
                                        currentXpx = (currentXpx + deltaX)
                                            .coerceIn(capsuleWidth / 2f, screenSize.width - capsuleWidth / 2f)
                                        currentYpx = (currentYpx + deltaY)
                                            .coerceIn(capsuleHeight / 2f, screenSize.height - capsuleHeight / 2f)
                                        lastTouchX = change.position.x
                                        lastTouchY = change.position.y
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                com.rajatxo.coral.ui.components.TabCapsule(
                    tabs = tabs,
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    backdrop = backdrop,
                    modifier = Modifier
                )
            }
        }
    }
}

// =============================================================================
// CynthiaDraggableSearchCircle
// =============================================================================
// A simplified, circular variant of Astra's DraggableSearchFab. Key
// differences from the original:
//   1. Function renamed to CynthiaDraggableSearchCircle, visibility `internal`.
//   2. Shape is a true circle (CircleShape) instead of a 16dp rounded square.
//   3. Size is 40dp instead of 56dp.
//   4. Uses kyant `drawBackdrop` glass morphism (same AGSL real-time blur as
//      the nav bar + mini player).
//   5. Same long-press + countdown bubble + scientist grid overlay + delta-
//      based drag + position persistence PATTERN as DraggableTabCapsule
//      (above) — NOT the left-side tail bubble from DraggableSearchFab.
//   6. Position persisted via SearchFabPosition (same as Astra).
//   7. Icon: CoralIcons.Search, white tint, 20dp.
//   8. The search circle's X position is controllable via the `xOffset: Dp`
//      parameter so the caller can pin it next to the nav bar. Only the Y
//      axis is user-draggable; X stays fixed at the parameter value.
//
// Interaction:
//   - Short tap (before the 2-second hold completes) → onSearchClick()
//   - Hold 2s → countdown bubble (3→2→1) above the circle → enter drag mode
//   - In drag mode: circle follows finger vertically (X stays fixed)
//   - Release → pin Y to SearchFabPosition (X is always the parameter)
// =============================================================================

@Composable
internal fun CynthiaDraggableSearchCircle(
    onSearchClick: () -> Unit = {},
    backdrop: LayerBackdrop? = null
) {
    val savedPosition by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    var screenSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentXpx by remember { mutableStateOf(0f) }
    var currentYpx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActivated by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // --- Grid overlay state (scientist graph paper, for alignment) ---
    var showGrid by remember { mutableStateOf(false) }
    val gridAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showGrid) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "searchGridAlpha"
    )

    // --- Last touch position (for delta-based smooth dragging) ---
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }

    var showBubble by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }

    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "searchBubbleScale"
    )
    val bubbleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "searchBubbleAlpha"
    )

    // Circle scale for drag-mode feedback
    val circleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "searchCircleScale"
    )

    val circleSize = 52.dp
    val circleSizePx = with(density) { circleSize.toPx() }

    androidx.compose.runtime.LaunchedEffect(savedPosition, screenSize) {
        if (screenSize.width > 0 && screenSize.height > 0) {
            currentXpx = savedPosition.first * screenSize.width
            currentYpx = savedPosition.second * screenSize.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        if (screenSize.width > 0 && screenSize.height > 0) {

            // --- Scientist grid overlay (fades in during drag mode) ---
            // Graph-paper style grid for precise alignment. Fades in when
            // drag mode starts, fades out when the circle is placed.
            if (gridAlpha > 0.01f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = gridAlpha }
                ) {
                    val gridSpacing = 40f  // px between grid lines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    val majorColor = Color.White.copy(alpha = 0.15f)
                    val majorEvery = 4  // every 4th line is brighter

                    // Vertical lines
                    var x = 0f
                    var i = 0
                    while (x <= size.width) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        x += gridSpacing
                        i++
                    }

                    // Horizontal lines
                    var y = 0f
                    i = 0
                    while (y <= size.height) {
                        drawLine(
                            color = if (i % majorEvery == 0) majorColor else gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (i % majorEvery == 0) 1.5f else 0.8f
                        )
                        y += gridSpacing
                        i++
                    }

                    // Center crosshair (brighter, for alignment reference)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx - 30f, cy),
                        end = Offset(cx + 30f, cy),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                        start = Offset(cx, cy - 30f),
                        end = Offset(cx, cy + 30f),
                        strokeWidth = 2f
                    )
                }
            }

            // --- Countdown speech bubble (above the circle) ---
            if (bubbleAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (currentXpx - with(density) { 60.dp.toPx() }).toInt(),
                                (currentYpx - circleSizePx - with(density) { 50.dp.toPx() }).toInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            alpha = bubbleAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hold to move in",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countdownNumber.toString(),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // --- The search circle itself ---
            // Glass morphism: uses drawBackdrop (same AGSL real-time blur as
            //   the nav bar + mini player) when a backdrop is provided.
            //   Falls back to a dark translucent background when no backdrop.
            val circleShape: androidx.compose.ui.graphics.Shape = CircleShape
            val circleModifier = if (backdrop != null) {
                Modifier
                    .clip(circleShape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { circleShape },
                        effects = {
                            vibrancy()
                            colorControls(
                                brightness = 0.05f,
                                contrast = 1f,
                                saturation = 1.5f
                            )
                            blur(18f.dp.toPx())  // AGSL real-time backdrop blur (same as nav bar)
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.35f))
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), circleShape)
            } else {
                Modifier
                    .clip(circleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), circleShape)
            }
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (currentXpx - circleSizePx / 2f).toInt()
                                .coerceIn(0, (screenSize.width - circleSizePx).toInt()),
                            (currentYpx - circleSizePx / 2f).toInt()
                                .coerceIn(0, (screenSize.height - circleSizePx).toInt())
                        )
                    }
                    .size(circleSize)
                    .graphicsLayer {
                        scaleX = circleScale
                        scaleY = circleScale
                    }
                    .then(circleModifier)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                // Consume the down event so the tap does
                                // NOT leak through to the content visually
                                // behind the circle.
                                down.consume()
                                isLongPressActivated = false
                                val initialX = down.position.x
                                val initialY = down.position.y
                                lastTouchX = down.position.x
                                lastTouchY = down.position.y

                                countdownJob?.cancel()
                                countdownJob = scope.launch {
                                    // Phase 1: hold for 2 seconds (no UI feedback)
                                    delay(2000L)

                                    // Phase 2: pop up the bubble with countdown
                                    showBubble = true
                                    countdownNumber = 3
                                    delay(1000L)

                                    countdownNumber = 2
                                    delay(1000L)

                                    countdownNumber = 1
                                    delay(1000L)

                                    // Phase 3: countdown done — hide bubble, enter drag mode
                                    showBubble = false
                                    isLongPressActivated = true
                                    isDragging = true
                                    // Show scientist grid when drag mode starts
                                    showGrid = true
                                    // Record current touch position as baseline for delta tracking
                                    lastTouchX = down.position.x
                                    lastTouchY = down.position.y
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // Consume the up event too, so neither
                                        // the down nor the up leaks to siblings.
                                        change.consume()
                                        // Finger lifted
                                        if (isLongPressActivated) {
                                            val newXFraction = (currentXpx / screenSize.width)
                                                .coerceIn(0.05f, 0.95f)
                                            val newYFraction = (currentYpx / screenSize.height)
                                                .coerceIn(0.05f, 0.95f)
                                            com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                                                .setPosition(newXFraction, newYFraction)
                                        } else {
                                            // Short tap (before 2-second hold) → open search
                                            onSearchClick()
                                        }
                                        isDragging = false
                                        isLongPressActivated = false
                                        showBubble = false
                                        showGrid = false  // hide grid when released
                                        countdownJob?.cancel()
                                        break
                                    }

                                    // Cancel countdown if finger moved (swipe, not hold)
                                    if (!isDragging && !isLongPressActivated) {
                                        val movedX = kotlin.math.abs(change.position.x - initialX)
                                        val movedY = kotlin.math.abs(change.position.y - initialY)
                                        if (movedX > 20f || movedY > 20f) {
                                            countdownJob?.cancel()
                                            showBubble = false
                                        }
                                    }

                                    // SMOOTH DRAGGING — use positionChange() for real-time finger tracking
                                    if (isDragging) {
                                        val delta = change.positionChange()
                                        currentXpx = (currentXpx + delta.x)
                                            .coerceIn(circleSizePx / 2f, screenSize.width - circleSizePx / 2f)
                                        currentYpx = (currentYpx + delta.y)
                                            .coerceIn(circleSizePx / 2f, screenSize.height - circleSizePx / 2f)
                                        change.consume()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
