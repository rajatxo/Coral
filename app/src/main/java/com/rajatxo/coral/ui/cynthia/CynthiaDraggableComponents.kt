package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
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

// ★ Y CONSTRAINT — the nav bar can't move above this horizontal line.
//   User-specified: Y = 0.794. The nav bar's Y (in screen fractions) must
//   always be ≥ this value, so it stays in the bottom ~21% of the screen.
private const val CYNTHIA_NAV_BAR_MIN_Y_FRAC = 0.794f

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
    navBarWidth: Dp = 240.dp,
    // ★ Fires when the user holds for 5 seconds — opens the customization panel.
    //   Passes `true` to indicate the NAV BAR was held (so the panel shows only
    //   nav bar settings).
    onShowCustomizationPanel: (Boolean) -> Unit = {},
    // ★ Linked-movement callback — fires with the (deltaX, deltaY) in PIXELS
    //   whenever the nav bar is dragged. The caller (CynthiaHomeScreen) uses
    //   this to move the search FAB along with the nav bar when they're
    //   aligned (same horizontal line).
    onNavBarDragged: (Float, Float) -> Unit = { _, _ -> },
    // ★ Fires when the nav bar drag ENDS, with the final saved (X, Y)
    //   fractions. The caller uses this to recalculate the search FAB's
    //   position DIRECTLY from the nav bar's final position — eliminating
    //   any drift that accumulated during the delta-based linked movement.
    onNavBarReleased: (Float, Float) -> Unit = { _, _ -> },
    // ★ Effective position — what the nav bar DISPLAYS at. This is separate
    //   from the SAVED position so the caller can make the nav bar display at
    //   center when misaligned WITHOUT overwriting the saved position. Only
    //   the user's drag saves a new position. Defaults are placeholders; the
    //   caller always passes the real effective values.
    effectiveX: Float = 0.5f,
    effectiveY: Float = 0.889f
) {
    val savedPosition by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()
    // ★ Read customization (size, corner, shape) from CynthiaNavBarCustomization.
    val navCustom by com.rajatxo.coral.data.prefs.CynthiaNavBarCustomization.customization.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    // ★ CRITICAL: rememberUpdatedState ensures the pointerInput handler
    //   (which is keyed on tabs/activeTab and doesn't restart when
    //   onNavBarDragged/onNavBarReleased change) always calls the LATEST
    //   lambdas. Without this, the pointerInput captures STALE lambdas from
    //   the first composition.
    val currentOnNavBarDragged by rememberUpdatedState(onNavBarDragged)
    val currentOnNavBarReleased by rememberUpdatedState(onNavBarReleased)
    val currentOnShowCustomizationPanel by rememberUpdatedState(onShowCustomizationPanel)

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
    // ★ Choice menu — shown after 5-sec hold. User picks "Drag" or "Manual".
    var showChoiceMenu by remember { mutableStateOf(false) }

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

    // ★ Always use the customization width (the user controls it via the
    //   panel). The navBarWidth parameter from the caller is ignored for the
    //   capsule size — it was only used for the align/misalign animation,
    //   which we've removed in favor of manual control.
    val capsuleWidth = with(density) { navCustom.widthDp.dp.toPx() }
    val capsuleHeight = with(density) { navCustom.heightDp.dp.toPx() }

    // ★ Initialize currentXpx/currentYpx from the EFFECTIVE position.
    //   The effective position is computed by the caller (CynthiaHomeScreen):
    //   - When aligned: effective = saved (nav bar at its saved position)
    //   - When misaligned: effective = center (nav bar DISPLAYS at center but
    //     SAVED position is NOT overwritten — only the user's drag saves).
    //   Guard with !isDragging so this doesn't override the finger mid-drag.
    androidx.compose.runtime.LaunchedEffect(effectiveX, effectiveY, screenSize) {
        if (screenSize.width > 0 && screenSize.height > 0 && !isDragging) {
            currentXpx = effectiveX * screenSize.width
            currentYpx = effectiveY * screenSize.height
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
            // ★ Compute the nav bar shape from customization (corner radius + shape enum).
            val navBarShape: androidx.compose.ui.graphics.Shape =
                navCustom.shape.toComposeShape(navCustom.cornerRadiusDp, navCustom.widthDp)

            // ★ CHOICE MENU — shown after 5-sec hold. Two options: Drag / Manual.
            if (showChoiceMenu) {
                CynthiaChoiceMenu(
                    anchorX = currentXpx,
                    anchorY = currentYpx,
                    onDrag = {
                        showChoiceMenu = false
                        isLongPressActivated = true
                        isDragging = true
                        showGrid = true
                        lastTouchX = 0f
                        lastTouchY = 0f
                    },
                    onManual = {
                        showChoiceMenu = false
                        currentOnShowCustomizationPanel(true)
                    },
                    onDismiss = { showChoiceMenu = false }
                )
            }

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
                    // ★ Apply the customization shape (clip the TabCapsule to the
                    //   desired corner radius / shape).
                    .clip(navBarShape)
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
                                    // ★ 3-sec hold total: show countdown 3→2→1,
                                    //   then open the panel directly (no choice menu).
                                    showBubble = true
                                    countdownNumber = 3
                                    delay(1000L)
                                    countdownNumber = 2
                                    delay(1000L)
                                    countdownNumber = 1
                                    delay(1000L)
                                    showBubble = false
                                    // ★ Open the customization panel directly.
                                    currentOnShowCustomizationPanel(true)
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // ★ If the choice menu is showing, don't treat
                                        //   the release as a tap or drag — just consume
                                        //   it and let the menu stay visible.
                                        if (showChoiceMenu) {
                                            change.consume()
                                            break
                                        }
                                        if (isLongPressActivated) {
                                            val newXFraction = (currentXpx / screenSize.width)
                                                .coerceIn(0.05f, 0.95f)
                                            // ★ Y CONSTRAINT on release — clamp the saved Y
                                            //   so it can't go above 0.794.
                                            val newYFraction = (currentYpx / screenSize.height)
                                                .coerceIn(
                                                    CYNTHIA_NAV_BAR_MIN_Y_FRAC,
                                                    0.95f
                                                )
                                            com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                                                .setPosition(newXFraction, newYFraction)
                                            // ★ FIRE RELEASE CALLBACK — lets the caller
                                            //   recalculate the search FAB's position
                                            //   DIRECTLY from the nav bar's final saved
                                            //   position. This eliminates any drift from
                                            //   the delta-based linked movement (stale
                                            //   state, clamping, frame delays).
                                            currentOnNavBarReleased(newXFraction, newYFraction)
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
                                        // ★ Y CONSTRAINT — the nav bar can't move above the
                                        //   horizontal line at Y = 0.794. So the minimum Y
                                        //   (in pixels) is 0.794 * screen height. The max Y
                                        //   stays at the bottom edge.
                                        val minYpx = CYNTHIA_NAV_BAR_MIN_Y_FRAC * screenSize.height
                                        currentYpx = (currentYpx + deltaY)
                                            .coerceIn(
                                                minYpx,
                                                screenSize.height - capsuleHeight / 2f
                                            )
                                        lastTouchX = change.position.x
                                        lastTouchY = change.position.y
                                        // ★ LINKED MOVEMENT — fire the delta to the caller so the
                                        //   search FAB can follow the nav bar when they're aligned.
                                        //   Uses currentOnNavBarDragged (rememberUpdatedState) so
                                        //   the LATEST lambda is always called — not the stale one
                                        //   from when pointerInput was first set up.
                                        currentOnNavBarDragged(deltaX, deltaY)
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
                    modifier = Modifier,
                    // ★ Pass custom width/height from the customization prefs
                    //   so the TabCapsule itself sizes correctly (it has
                    //   hardcoded 240dp/52dp otherwise).
                    customWidth = navCustom.widthDp.dp,
                    customHeight = navCustom.heightDp.dp
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
    backdrop: LayerBackdrop? = null,
    // ★ Fires when the user holds for 5 seconds — opens the customization panel.
    //   Passes `false` to indicate the SEARCH FAB was held (so the panel shows
    //   only search FAB settings).
    onShowCustomizationPanel: (Boolean) -> Unit = {}
) {
    val savedPosition by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()
    val savedNavBarPosition by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()
    // ★ Read customization (size, corner, shape) from CynthiaSearchFabCustomization.
    val searchCustom by com.rajatxo.coral.data.prefs.CynthiaSearchFabCustomization.customization.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()

    // ★ rememberUpdatedState so the pointerInput always calls the latest lambda.
    val currentOnShowCustomizationPanel by rememberUpdatedState(onShowCustomizationPanel)

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
    // ★ Choice menu — shown after 5-sec hold. User picks "Drag" or "Manual".
    var showChoiceMenu by remember { mutableStateOf(false) }

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

    // ★ Use customization size instead of hardcoded 52.dp.
    val circleSize = searchCustom.sizeDp.dp
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

            // ★ COORDINATE DISPLAY — shows the live (X, Y) fractions while
            //   dragging the search circle. Same style as the nav bar's
            //   coordinate display. The user can read these values and tell
            //   me the exact position they want.
            if (isDragging && screenSize.width > 0 && screenSize.height > 0) {
                val xFrac = (currentXpx / screenSize.width).coerceIn(0f, 1f)
                val yFrac = (currentYpx / screenSize.height).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Search — X: ${String.format("%.3f", xFrac)}  Y: ${String.format("%.3f", yFrac)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
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
            //   ★ Shape from customization (Pill, Rectangle, Rounded, Circle, Squircle).
            val circleShape: androidx.compose.ui.graphics.Shape =
                searchCustom.shape.toComposeShape(searchCustom.cornerRadiusDp, searchCustom.sizeDp)

            // ★ CHOICE MENU — shown after 5-sec hold. Two options: Drag / Manual.
            if (showChoiceMenu) {
                CynthiaChoiceMenu(
                    anchorX = currentXpx,
                    anchorY = currentYpx,
                    onDrag = {
                        showChoiceMenu = false
                        isLongPressActivated = true
                        isDragging = true
                        showGrid = true
                        lastTouchX = 0f
                        lastTouchY = 0f
                    },
                    onManual = {
                        showChoiceMenu = false
                        currentOnShowCustomizationPanel(false)
                    },
                    onDismiss = { showChoiceMenu = false }
                )
            }
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

                                    // Phase 3: countdown done — hide bubble, show
                                    // the choice menu (instead of drag mode or panel).
                                    showBubble = false
                                    showChoiceMenu = true
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // Consume the up event too, so neither
                                        // the down nor the up leaks to siblings.
                                        change.consume()
                                        // ★ If the choice menu is showing, don't treat
                                        //   the release as a tap or drag — just break
                                        //   and let the menu stay visible.
                                        if (showChoiceMenu) {
                                            break
                                        }
                                        // Finger lifted
                                        if (isLongPressActivated) {
                                            val releasedX = (currentXpx / screenSize.width)
                                                .coerceIn(0.05f, 0.95f)
                                            val releasedY = (currentYpx / screenSize.height)
                                                .coerceIn(0.05f, 0.95f)
                                            // ★ DYNAMIC SNAP: the search FAB's "default" is
                                            //   NOT a fixed number — it's BESIDE the nav bar,
                                            //   calculated from the nav bar's SAVED position.
                                            //   If the search FAB is released close to the
                                            //   nav bar's Y (within ±0.08), snap it to beside
                                            //   the nav bar:
                                            //     searchFabX = navBarX + offset
                                            //     searchFabY = navBarY
                                            //   where offset = navBarWidth/2 + gap + fabWidth/2
                                            //   This triggers isAligned = true, which makes
                                            //   the nav bar display at its saved position.
                                            val navBarX = savedNavBarPosition.first
                                            val navBarY = savedNavBarPosition.second
                                            val closeToNavBarY =
                                                kotlin.math.abs(releasedY - navBarY) < 0.08f
                                            if (closeToNavBarY && screenSize.width > 0) {
                                                // Calculate the search FAB's position beside
                                                // the nav bar:
                                                //   navBarWidth/2 = 75dp (150dp aligned)
                                                //   gap = 120dp
                                                //   fabWidth/2 = 26dp (52dp)
                                                //   total offset = 75 + 120 + 26 = 221dp
                                                val offsetDp = 221.dp
                                                val offsetPx = with(density) { offsetDp.toPx() }
                                                val offsetFrac = offsetPx / screenSize.width
                                                val searchFabDefaultX =
                                                    (navBarX + offsetFrac).coerceIn(0.05f, 0.95f)
                                                com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                                                    .setPosition(searchFabDefaultX, navBarY)
                                            } else {
                                                com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                                                    .setPosition(releasedX, releasedY)
                                            }
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

// =============================================================================
// CynthiaChoiceMenu — small popup with "Drag" and "Manual" options
// =============================================================================
// Shown after 5-sec hold on nav bar or search FAB. Positioned above the
// held element. Has two buttons:
//   "Drag"   → enter drag mode (the old behavior)
//   "Manual" → open the customization panel
// Tapping outside dismisses it.
// =============================================================================

@Composable
internal fun CynthiaChoiceMenu(
    anchorX: Float,
    anchorY: Float,
    onDrag: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss
            )
    ) {
        Row(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (anchorX - with(density) { 90.dp.toPx() }).toInt()
                            .coerceIn(0, 10000),
                        (anchorY - with(density) { 80.dp.toPx() }).toInt()
                            .coerceIn(0, 10000)
                    )
                }
                .width(180.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1A1A1A).copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {} // consume click so it doesn't dismiss
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = onDrag
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Drag",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            // Manual button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = onManual
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Manual",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
