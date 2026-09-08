package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {}
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // --- Wheel/Grid toggle state ---
    var useWheel by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        // Header Column: Row(capsule + title) + big capsule with inner items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 16.dp)
        ) {
            // Header Row: capsule (weight=1f) + title text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.rajatxo.coral.ui.components.SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
                if (capsuleVisible && capsuleRemaining > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Text(
                    text = "Playlists",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.rajatxo.coral.ui.theme.QuirkFontFamily
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // --- Big capsule with inner items ---
            // [small create capsule] ........... [wheel/grid toggle]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CoralColors.SurfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small inner capsule: "New" (left side)
                Row(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showCreateDialog = true }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = CoralIcons.Play,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "New",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Flexible space between
                Spacer(modifier = Modifier.weight(1f))

                // Toggle capsule: Wheel / Grid (right side)
                Row(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { useWheel = !useWheel }
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (useWheel) "Grid" else "Wheel",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- Content: Wheel or Grid ---
        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🪸", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No playlists yet",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap \"New\" to create your first playlist.",
                    color = CoralColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            if (useWheel) {
                PlaylistWheel(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 110.dp, bottom = 16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 110.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                PlaylistStore.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

// =============================================================================
// PLAYLIST WHEEL v4 — Paris-style curved vertical rotary picker
// =============================================================================
// Geometry (per spec):
//   - Pivot Center: anchored OFF-SCREEN to the LEFT at (x = -40% screen width,
//     y = 50% screen height). All circles are concentric on this point.
//   - Radius: large (~80% of screen height) so the visible right side of the
//     arc sweeps a broad, smooth vertical curve across the screen.
//
// Visual layers:
//   1. Indicator arc: a single thin (1.5px) solid white arc concentric with
//      the text path, sitting just INSIDE the text items. No other orbits.
//   2. Text items: every label sits on an outer concentric radial path,
//      rotated to align with the tangent angle of the arc at its angular
//      position. Items are evenly spaced by a fixed degree increment.
//   3. Active item (center apex, 0° relative): Playfair Display Italic,
//      UPPERCASE, pure white #FFFFFF, max size (~3x inactive), 100% opacity.
//   4. Inactive items: Playfair Display Italic, Title Case, with smooth
//      opacity + size falloff based on angular distance from center.
//        step 0: 100% / max
//        step 1: ~60% / ~0.7x
//        step 2: ~35% / ~0.55x
//        step 3+: ~10–15% / ~0.45x, fading to nothing near screen edges.
//
// Motion:
//   - Vertical drag rotates the wheel around the off-screen pivot.
//   - Smooth real-time interpolation of font size, opacity, angle, case.
//   - On release: physics-based fling decay, then snap to nearest item with
//     spring deceleration bringing the selected item to the apex.
//   - Haptic tick on every item pass during scroll.
//   - Tap on the active (center) item opens it.
// =============================================================================

@Composable
private fun PlaylistWheel(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // --- Geometry constants ---
    // Angular spacing between adjacent items (degrees). 8° gives ~22 visible
    // items across a 180° window — enough density without crowding the apex.
    val angleStepDeg = 8f

    // Pixels of vertical drag required to advance the wheel by ONE item.
    // Translates finger travel into angular rotation around the pivot.
    val pxPerItem = with(density) { 64.dp.toPx() }

    // Scroll offset (in pixels). Each pxPerItem corresponds to angleStepDeg
    // of rotation. Positive = wheel rotates so items move DOWN visually
    // (finger swiped down); negative = items move UP.
    val scrollOffset = remember { Animatable(0f) }

    // Index of the item currently at the apex (selected).
    var lastSnappedIndex by remember { mutableStateOf(0) }

    // --- Selection helper ---
    fun indexAtOffset(offset: Float): Int {
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % playlists.size
        return if (mod < 0) mod + playlists.size else mod
    }

    val centerIndex = remember(scrollOffset.value) { indexAtOffset(scrollOffset.value) }

    // --- Gestures: rotational drag + physics fling + snap + tap ---
    Box(
        modifier = modifier
            .pointerInput(playlists.size) {
                var velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker = VelocityTracker() },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        coroutineScope.launch {
                            // Physics fling: exponential decay
                            scrollOffset.animateDecay(
                                initialVelocity = velocity * 0.35f,
                                animationSpec = androidx.compose.animation.core.exponentialDecay(
                                    frictionMultiplier = 0.9f
                                )
                            )
                            // Snap to nearest item with spring
                            val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                            scrollOffset.animateTo(
                                targetValue = nearest * pxPerItem,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        coroutineScope.launch {
                            scrollOffset.snapTo(scrollOffset.value + dragAmount)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        // Haptic tick on every item boundary crossing
                        val currentIdx = indexAtOffset(scrollOffset.value)
                        if (currentIdx != lastSnappedIndex) {
                            lastSnappedIndex = currentIdx
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                            )
                        }
                        change.consume()
                    }
                )
            }
            .pointerInput(playlists.size) {
                // Tap to open the currently-selected item (apex)
                detectTapGestures(
                    onTap = {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        onPlaylistClick(playlists[centerIndex])
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // === 1. PIVOT (off-screen, left, vertically centered) ===
            // Single absolute center point for the entire wheel.
            // X = -50% screen width, Y = 50% screen height.
            val pivotX = w * -0.50f
            val pivotY = h * 0.50f

            // === 2. DUAL CONCENTRIC RADII SYSTEM ===
            // The arc line and text live on two different orbits that share
            // the SAME off-screen pivot point. This prevents the arc from
            // cutting through the text.
            //
            // Radius A — Visible arc line (1.5px semi-transparent white).
            val arcRadius = w * 0.65f

            // Radius B — Text orbit. Sits 30px OUTSIDE the arc line so text
            // never intersects the visible line. Text anchor is the LEFT edge
            // of each label, pinned to this radius.
            val textRadius = arcRadius + with(density) { 30.dp.toPx() }

            // === 3. INDICATOR ARC (single thin white semi-transparent line,
            //         with endpoints fading to 0 so the arc dissolves into the
            //         navigation rail text on both ends) ===
            // Visible arc window: ±50° from horizontal apex. Total sweep = 100°.
            // This puts the arc on the LEFT HALF of screen (apex at ~30% from
            // left, endpoints near the left edge).
            val arcSweepDeg = 100f
            val arcStartDeg = -arcSweepDeg / 2f

            // Helper: draw an arc with endpoints fading to 0 over [fadeRange]
            // fraction of each end. Used for both the main arc and the
            // temporary alignment arc so they share the same dissolve style.
            fun drawFadingArc(radius: Float, fullAlpha: Float, strokePx: Float) {
                val arcSegments = 40
                val fadeRange = 0.35f  // last 35% of arc on each end fades to 0
                for (i in 0 until arcSegments) {
                    val segStart = i / arcSegments.toFloat()
                    val segEnd = (i + 1) / arcSegments.toFloat()
                    val distFromEndpoint = minOf(segStart, 1f - segStart)
                    val segAlpha = if (distFromEndpoint > fadeRange) {
                        fullAlpha
                    } else {
                        fullAlpha * (distFromEndpoint / fadeRange)
                    }
                    if (segAlpha <= 0.01f) continue

                    drawArc(
                        color = Color.White.copy(alpha = segAlpha),
                        startAngle = arcStartDeg + segStart * arcSweepDeg,
                        sweepAngle = (segEnd - segStart) * arcSweepDeg,
                        useCenter = false,
                        topLeft = Offset(pivotX - radius, pivotY - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokePx)
                    )
                }
            }

            // Main indicator arc — solid white, 1.5px, 60% alpha at center.
            drawFadingArc(
                radius = arcRadius,
                fullAlpha = 0.6f,
                strokePx = with(density) { 1.5.dp.toPx() }
            )

            // ──────────────────────────────────────────────────────────────
            // ⚠️ TEMPORARY ALIGNMENT ARC — REMOVE LATER
            // User asked for a second arc just outside the main one, used as a
            // visual alignment guide for text placement. Same fade style.
            // Gap between arcs = 30dp (matches textRadius offset).
            // To remove: delete this block (and the helper if no longer used).
            // ──────────────────────────────────────────────────────────────
            drawFadingArc(
                radius = textRadius,
                fullAlpha = 0.35f,
                strokePx = with(density) { 1.0.dp.toPx() }
            )

            // === 4. TEXT ITEMS on the outer (invisible) text orbit ===
            // scrollOffset / pxPerItem = how many "items" the wheel has rotated.
            val rotationItems = scrollOffset.value / pxPerItem

            // Playfair Display Italic — premium high-contrast editorial serif.
            val activeFontSp = 46f
            val inactiveFontSp = 18f

            // ±50° visible window at 8° step = ~6 items each side.
            val visibleSpan = 6

            for (offset in -visibleSpan..visibleSpan) {
                // Index in playlist array for this slot
                val rawIdx = (rotationItems.roundToInt() + offset)
                val modIdx = ((rawIdx % playlists.size) + playlists.size) % playlists.size
                val playlist = playlists[modIdx]

                // Fractional offset from center (0 = apex). Negative = above.
                val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                val absOffset = abs(fractionalOffset)

                if (absOffset > visibleSpan) continue

                // Angular position: apex = 0°, items above go negative, below positive.
                // Negative angle = upper arc; positive = lower arc.
                val itemAngleDeg = fractionalOffset * angleStepDeg
                val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                // Position on the arc (Cartesian from pivot)
                val itemX = pivotX + textRadius * cos(itemAngleRad)
                val itemY = pivotY + textRadius * sin(itemAngleRad)

                // Skip if off-screen horizontally
                if (itemX < -200f || itemX > w + 200f) continue

                // === 5. STYLING CURVES (per AI spec) ===
                // Opacity: 100% at apex → 50% (step 1) → 25% (step 2) → 5% (step 3+) → 0
                val alpha = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1f, 0.50f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.50f, 0.25f, (absOffset - 1.5f))
                    absOffset < 3.5f -> lerp(0.25f, 0.05f, (absOffset - 2.5f))
                    else -> lerp(0.05f, 0f, (absOffset - 3.5f).coerceIn(0f, 1f))
                }.coerceIn(0f, 1f)

                // Scale: 1.0 at apex → 0.70 → 0.55 → 0.45 (smooth shrink)
                val scale = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1f, 0.70f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.70f, 0.55f, (absOffset - 1.5f))
                    else -> lerp(0.55f, 0.45f, (absOffset - 2.5f).coerceIn(0f, 1f))
                }

                // Interpolated font size (active is 2.5× larger than inactive)
                val fontSp = lerp(activeFontSp, inactiveFontSp, (1f - scale).coerceIn(0f, 1f))

                // Active = UPPERCASE, inactive = Title Case
                val isActive = absOffset < 0.5f
                val displayText = if (isActive) {
                    playlist.name.uppercase()
                } else {
                    // Title Case: lowercase everything, then capitalize first char
                    playlist.name.lowercase().replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.uppercaseChar().toString() else ch.toString()
                    }
                }

                // Color: active = pure white; inactive = white with reduced alpha
                val textColor = Color.White

                // === 6. MEASURE TEXT ===
                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(displayText),
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSp.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontFamily = com.rajatxo.coral.ui.theme.PlayfairItalicFamily
                    ),
                    overflow = TextOverflow.Visible,
                    maxLines = 1,
                    softWrap = false,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = (w * 0.9f).toInt(),
                        maxHeight = Int.MAX_VALUE
                    )
                )

                // === 7. DRAW WITH LEFT-EDGE ANCHOR + TANGENT ROTATION ===
                // Per spec:
                //   - Anchor: LEFT-CENTER edge of text (not center).
                //   - Pin the left edge to coordinates on Radius B (textRadius).
                //   - Because anchor is on the left, text flows outward to the
                //     right, leaving a uniform ~30px gap between the first
                //     letter of every word and the visible arc line.
                //   - Rotation: tangent to the curve at θ → rotation angle = θ + 90°.
                //   - At apex (θ=0°): rotation = 90° → text is horizontal.
                //
                // Tangent math:
                //   point on circle = (cos θ, sin θ)
                //   tangent direction = (-sin θ, cos θ)
                //   tangent angle (from +X axis, clockwise in screen coords) = θ + 90°
                val tangentDeg = itemAngleDeg + 90f

                // Anchor: text's LEFT-CENTER edge sits at (itemX, itemY).
                // After translate+rotate, the local origin is at (itemX, itemY).
                // topLeft = (0, -textH/2) pins the LEFT edge (x=0) and vertically
                // centers (y=-textH/2 → text extends from -textH/2 to +textH/2).
                val textH = textLayout.size.height.toFloat()

                drawContext.canvas.save()
                // Translate to the anchor point on the text orbit, rotate to
                // tangent, then draw text with left-edge anchor.
                drawContext.canvas.translate(itemX, itemY)
                drawContext.canvas.rotate(tangentDeg)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(0f, -textH / 2f),
                    alpha = alpha
                )
                drawContext.canvas.restore()
            }
        }

        // Hint text
        Text(
            text = "Slide to spin • Tap to open",
            color = CoralColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )
    }
}

/** Simple linear interpolation between two floats. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUri != null) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = "Cover for ${playlist.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = CoralIcons.ListMusic,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B).copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.songIds.size} ${if (playlist.songIds.size == 1) "song" else "songs"}",
            color = Color(0xFFB0B0B0),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CoralColors.SurfaceVariant,
        titleContentColor = Color.White,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name", color = Color(0xFF888888)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) {
                Text("Create", color = CoralColors.Coral, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}
