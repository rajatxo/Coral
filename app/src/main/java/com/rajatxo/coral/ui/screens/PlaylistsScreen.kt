package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
// PLAYLIST WHEEL v3 — glossy 3D sphere + orbit rings + vertical text
// =============================================================================
// Design (from user's PicsArt mockup + reference):
//   - Visible glossy half-sphere on LEFT (3D, radial gradient + highlight)
//   - Multiple concentric orbit rings around the sphere
//   - Text arranged VERTICALLY to the RIGHT (not on a circle)
//   - Progressive sizing: center = biggest, further = smaller
//   - 3D perspective: items further from center are smaller + faded
//   - Physics: fling with velocity, snap to nearest on release
//   - Haptic feedback: vibration on each item pass during spin
//   - Swipe UP = scroll up (anti-clockwise), Swipe DOWN = scroll down
//   - Only 3 tappable: center + 2 adjacent
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

    // Item height in dp (each playlist takes this much vertical space)
    val itemHeightDp = 56.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    // Scroll offset in pixels (animated for smoothness + physics)
    val scrollOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Track last snapped index for haptic feedback
    var lastSnappedIndex by remember { mutableStateOf(0) }

    // Selected = the one closest to center
    val centerIndex = remember(scrollOffset.value) {
        ((scrollOffset.value / itemHeightPx).roundToInt() % playlists.size).let {
            if (it < 0) it + playlists.size else it
        }
    }

    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RectangleShape)
            .pointerInput(playlists.size) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker = VelocityTracker() },
                    onDragEnd = {
                        // FLING: use velocity for physics-based deceleration
                        val velocity = velocityTracker.calculateVelocity().y
                        coroutineScope.launch {
                            // Fling with deceleration
                            scrollOffset.animateTo(
                                targetValue = scrollOffset.value + velocity * 0.3f,
                                animationSpec = androidx.compose.animation.core.exponentialDecay(
                                    friction = 0.9f
                                )
                            )
                            // SNAP to nearest item
                            val nearest = (scrollOffset.value / itemHeightPx).roundToInt()
                            val target = nearest * itemHeightPx
                            scrollOffset.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // dragAmount > 0 = finger moving DOWN = scroll down
                        // dragAmount < 0 = finger moving UP = scroll up
                        coroutineScope.launch {
                            scrollOffset.snapTo(scrollOffset.value + dragAmount)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        // Haptic: vibrate when passing each item
                        val currentIndex = ((scrollOffset.value / itemHeightPx).roundToInt() % playlists.size).let {
                            if (it < 0) it + playlists.size else it
                        }
                        if (currentIndex != lastSnappedIndex) {
                            lastSnappedIndex = currentIndex
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                            )
                        }

                        change.consume()
                    }
                )
            }
            .pointerInput(playlists.size) {
                // Tap detection for the 3 nearest playlists
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        val event = awaitPointerEvent(requireUnconsumed = false)
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed) {
                            val tapY = change.position.y
                            val centerY = size.height / 2f
                            // Check the 3 nearest playlists
                            for (offset in -1..1) {
                                val idx = (centerIndex + offset + playlists.size) % playlists.size
                                val itemY = centerY + (idx - centerIndex) * itemHeightPx - scrollOffset.value % itemHeightPx
                                val dist = abs(tapY - itemY)
                                if (dist < itemHeightPx / 2f) {
                                    haptics.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                    )
                                    onPlaylistClick(playlists[idx])
                                    break
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val sphereCenterX = w * 0.15f  // sphere sits at 15% from left
            val sphereRadius = h * 0.30f

            // === 1. HALF SPHERE (glossy 3D dome) ===
            // Layer 1: base sphere with strong radial gradient
            val sphereGradient = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.25f),  // bright center
                    0.3f to Color.White.copy(alpha = 0.12f),  // mid-bright
                    0.6f to Color.Black.copy(alpha = 0.4f),    // darker
                    0.85f to Color.Black.copy(alpha = 0.6f),   // dark edge
                    1.0f to Color.Transparent                     // fades to nothing
                ),
                center = Offset(sphereCenterX + sphereRadius * 0.2f, centerY - sphereRadius * 0.1f),
                radius = sphereRadius
            )
            drawCircle(
                brush = sphereGradient,
                radius = sphereRadius,
                center = Offset(sphereCenterX, centerY)
            )

            // Layer 2: glossy highlight (top-left, simulates light source)
            val highlightGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(sphereCenterX - sphereRadius * 0.15f, centerY - sphereRadius * 0.35f),
                radius = sphereRadius * 0.4f
            )
            drawCircle(
                brush = highlightGradient,
                radius = sphereRadius * 0.4f,
                center = Offset(sphereCenterX - sphereRadius * 0.15f, centerY - sphereRadius * 0.35f)
            )

            // === 2. ORBIT RINGS (concentric circles around sphere) ===
            val orbitRadii = listOf(sphereRadius * 1.3f, sphereRadius * 1.6f, sphereRadius * 1.9f, sphereRadius * 2.2f)
            orbitRadii.forEachIndexed { i, r ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f + (i * 0.01f)),
                    radius = r,
                    center = Offset(sphereCenterX, centerY),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // === 3. PLAYLIST NAMES (vertical column to the RIGHT of sphere) ===
            val textStartX = sphereCenterX + sphereRadius * 1.5f
            val totalScroll = scrollOffset.value

            playlists.forEachIndexed { i, _ ->
                val playlist = playlists[i]
                // Vertical position relative to center
                val itemY = centerY + (i * itemHeightPx) - totalScroll

                // Distance from center (in items)
                val distanceFromCenter = abs(itemY - centerY) / itemHeightPx

                // Skip items too far away (off-screen)
                if (distanceFromCenter > 4) return@forEachIndexed

                // Progressive sizing (smooth interpolation, not just tiers)
                val sizeFactor = (1f - (distanceFromCenter * 0.25f)).coerceIn(0.3f, 1f)
                val fontSize = (22f * sizeFactor)
                val fontWeight = if (distanceFromCenter < 0.5) FontWeight.Bold else FontWeight.Medium

                // Alpha: fade out as distance increases
                val alpha = (1f - (distanceFromCenter * 0.3f)).coerceIn(0.1f, 1f)

                // Color: coral for center, white for others
                val textColor = if (distanceFromCenter < 0.5) CoralColors.Coral else Color.White

                // Measure text
                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(playlist.name),
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontWeight = fontWeight
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = (w * 0.5f).toInt(),
                        maxHeight = Int.MAX_VALUE
                    )
                )

                // Draw text
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        textStartX,
                        itemY - textLayout.size.height / 2f
                    ),
                    alpha = alpha
                )

                // Dot on the nearest orbit ring for this item
                val orbitIndex = (distanceFromCenter.toInt()).coerceIn(0, orbitRadii.size - 1)
                drawCircle(
                    color = if (distanceFromCenter < 0.5) CoralColors.Coral
                            else Color.White.copy(alpha = alpha * 0.4f),
                    radius = if (distanceFromCenter < 0.5) 4.dp.toPx() else 2.dp.toPx(),
                    center = Offset(
                        sphereCenterX + orbitRadii[orbitIndex],
                        itemY
                    )
                )
            }

            // === 4. FADE GRADIENTS (top + bottom of text area) ===
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = 0f,
                    endY = itemHeightPx * 2
                ),
                topLeft = Offset(textStartX - 10.dp.toPx(), 0f),
                size = androidx.compose.ui.geometry.Size(w - textStartX, itemHeightPx * 2)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = h - itemHeightPx * 2,
                    endY = h
                ),
                topLeft = Offset(textStartX - 10.dp.toPx(), h - itemHeightPx * 2),
                size = androidx.compose.ui.geometry.Size(w - textStartX, itemHeightPx * 2)
            )
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
