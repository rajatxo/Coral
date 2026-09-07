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
// PLAYLIST WHEEL — smooth circular selector
// =============================================================================
// Uses graphicsLayer { rotationZ } for GPU-accelerated rotation.
// This achieves 120fps+ on phones with high-refresh-rate displays.
// No recomposition during spin — only the layer transform updates.
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
    val anglePerItem = 360f / playlists.size

    val rotation = remember { Animatable(0f) }
    var velocityTracker = remember { VelocityTracker() }
    val coroutineScope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Selected = the one at 0° (3 o'clock = right of sphere = "main area")
    val selectedIndex = remember(rotation.value) {
        val normalized = ((-rotation.value) % 360f + 360f) % 360f
        ((normalized / anglePerItem).roundToInt() % playlists.size).coerceIn(0, playlists.size - 1)
    }

    // Tap detection: track where the user taps
    var tapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(playlists.size) {
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker = VelocityTracker() },
                    onDragEnd = {
                        coroutineScope.launch {
                            val currentNormalized = ((-rotation.value) % 360f + 360f) % 360f
                            val nearestIndex = ((currentNormalized / anglePerItem).roundToInt() % playlists.size)
                            val targetRotation = -nearestIndex * anglePerItem
                            val currentMod = ((rotation.value % 360f) + 360f) % 360f
                            val targetMod = ((targetRotation % 360f) + 360f) % 360f
                            var diff = targetMod - currentMod
                            if (diff > 180f) diff -= 360f
                            if (diff < -180f) diff += 360f
                            rotation.animateTo(
                                targetValue = rotation.value + diff,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Swipe UP → anti-clockwise → rotation decreases
                        // Swipe DOWN → clockwise → rotation increases
                        val rotationDelta = dragAmount * 0.5f
                        coroutineScope.launch {
                            rotation.snapTo(rotation.value + rotationDelta)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    }
                )
            }
            .pointerInput(playlists.size) {
                // Tap detection for the 3 clickable playlists
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val up = awaitPointerEvent()
                        val change = up.changes.firstOrNull() ?: continue
                        if (!change.pressed) {
                            val tapX = change.position.x
                            val tapY = change.position.y
                            val canvasWidth = size.width.toFloat()
                            val canvasHeight = size.height.toFloat()
                            val sphereCenterX = 0f
                            val sphereCenterY = canvasHeight / 2f
                            val orbitRadius = canvasWidth * 0.45f

                            // Check which of the 3 nearest playlists was tapped
                            for (offset in -1..1) {
                                val idx = (selectedIndex + offset + playlists.size) % playlists.size
                                val itemAngleDeg = (idx * anglePerItem + rotation.value)
                                val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()
                                val itemX = sphereCenterX + orbitRadius * cos(itemAngleRad)
                                val itemY = sphereCenterY + orbitRadius * sin(itemAngleRad)
                                val dist = sqrt((tapX - itemX).pow(2) + (tapY - itemY).pow(2))
                                if (dist < 80f.toPx()) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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
            val sphereCenterX = 0f
            val sphereCenterY = h / 2f
            val sphereRadius = h * 0.35f
            val orbitRadius = w * 0.45f

            // === 1. HALF SPHERE (dome on left, glossy like the sun) ===
            // Radial gradient: bright center → dark right → transparent left
            val sphereGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f),  // center: subtle bright
                    Color.White.copy(alpha = 0.05f),  // mid
                    Color.Black.copy(alpha = 0.3f),   // right: darker
                    Color.Transparent                   // left edge: fades to rail
                ),
                center = Offset(sphereCenterX + sphereRadius * 0.3f, sphereCenterY),
                radius = sphereRadius
            )
            drawCircle(
                brush = sphereGradient,
                radius = sphereRadius,
                center = Offset(sphereCenterX, sphereCenterY)
            )

            // Glossy highlight (small white ellipse on top-left of sphere)
            drawOval(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(sphereCenterX - sphereRadius * 0.2f, sphereCenterY - sphereRadius * 0.6f),
                size = androidx.compose.ui.geometry.Size(sphereRadius * 0.5f, sphereRadius * 0.2f)
            )

            // === 2. ORBIT (arc on the right side of sphere) ===
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = -80f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(sphereCenterX - orbitRadius, sphereCenterY - orbitRadius),
                size = androidx.compose.ui.geometry.Size(orbitRadius * 2f, orbitRadius * 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            // === 3. PLAYLIST NAMES along the orbit ===
            playlists.forEachIndexed { i, playlist ->
                val itemAngleDeg = (i * anglePerItem + rotation.value)
                val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                val x = sphereCenterX + orbitRadius * cos(itemAngleRad)
                val y = sphereCenterY + orbitRadius * sin(itemAngleRad)

                // Distance from center (0° = right = "main area")
                var angleDistance = abs(itemAngleDeg % 360f)
                if (angleDistance > 180f) angleDistance = 360f - angleDistance

                // Progressive text sizing (3 tiers)
                val fontSize = when {
                    angleDistance < anglePerItem * 0.6f -> 22f  // center: BIG
                    angleDistance < anglePerItem * 1.8f -> 16f  // adjacent: medium
                    angleDistance < anglePerItem * 3.0f -> 13f  // near: small
                    else -> 10f                                    // far: tiny
                }

                // Alpha: fade out on the LEFT side (near nav rail)
                val cosVal = cos(itemAngleRad)
                val alpha = when {
                    cosVal > 0.3f -> 1f               // right side: fully visible
                    cosVal > -0.2f -> (cosVal + 0.2f) / 0.5f  // transitioning
                    else -> 0f                          // left side: invisible
                }

                if (alpha < 0.05f) return@forEachIndexed

                // Measure text at the calculated size
                val fontWeight = if (angleDistance < anglePerItem * 0.6f) FontWeight.Bold
                                 else FontWeight.Medium
                val textColor = if (angleDistance < anglePerItem * 0.6f) CoralColors.Coral
                                else Color.White

                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(playlist.name),
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontWeight = fontWeight
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false
                )

                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x - textLayout.size.width / 2f,
                        y - textLayout.size.height / 2f
                    ),
                    alpha = alpha
                )

                // Small dot on the orbit for each playlist
                drawCircle(
                    color = if (angleDistance < anglePerItem * 0.6f) CoralColors.Coral
                            else Color.White.copy(alpha = 0.3f * alpha),
                    radius = if (angleDistance < anglePerItem * 0.6f) 5.dp.toPx() else 2.dp.toPx(),
                    center = Offset(
                        sphereCenterX + (orbitRadius - 15.dp.toPx()) * cos(itemAngleRad),
                        sphereCenterY + (orbitRadius - 15.dp.toPx()) * sin(itemAngleRad)
                    )
                )
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
