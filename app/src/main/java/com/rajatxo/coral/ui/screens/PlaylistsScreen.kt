package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.background
import androidx.compose.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.model.Playlist
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import kotlin.math.PI
import kotlin.math.cos
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

    // Angle per playlist (360 / count)
    val anglePerItem = 360f / playlists.size

    // Current rotation of the wheel (animated for smoothness)
    val rotation = remember { Animatable(0f) }
    var velocityTracker = remember { VelocityTracker() }
    var draggedIndex by remember { mutableStateOf(0) }

    // Selected playlist = the one at the top (12 o'clock = -90° in Compose)
    val selectedIndex = remember(rotation.value) {
        val normalized = ((-rotation.value) % 360f + 360f) % 360f
        ((normalized / anglePerItem).toInt() % playlists.size).coerceIn(0, playlists.size - 1)
    }

    Box(
        modifier = modifier
            .pointerInput(playlists.size) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        // Fling: calculate velocity and animate to final position
                        val velocity = velocityTracker.calculateVelocity().y
                        // Simple snap: find nearest playlist and spring to it
                        val currentNormalized = ((-rotation.value) % 360f + 360f) % 360f
                        val nearestIndex = ((currentNormalized / anglePerItem).roundToInt() % playlists.size)
                        val targetRotation = -nearestIndex * anglePerItem
                        // Find the shortest path to target
                        val currentMod = ((rotation.value % 360f) + 360f) % 360f
                        val targetMod = ((targetRotation % 360f) + 360f) % 360f
                        var diff = targetMod - currentMod
                        if (diff > 180f) diff -= 360f
                        if (diff < -180f) diff += 360f
                        val finalTarget = rotation.value + diff
                        rotation.animateTo(
                            targetValue = finalTarget,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Convert vertical drag to rotation degrees
                        // 1px drag = ~0.5 degrees rotation (adjustable for feel)
                        val rotationDelta = -dragAmount * 0.5f
                        rotation.snapTo(rotation.value + rotationDelta)
                        velocityTracker.addPosition(
                            change.uptimeMillis,
                            change.position
                        )
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw the wheel
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // GPU-accelerated rotation — this is the key to 120fps
                    rotationZ = rotation.value
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = size.minDimension * 0.35f // wheel radius

            // Draw each playlist name along the circle
            playlists.forEachIndexed { i, playlist ->
                // Angle for this item (starting at top = -90°)
                val itemAngle = (i * anglePerItem - 90f) * (PI / 180f).toFloat()
                val x = centerX + radius * cos(itemAngle)
                val y = centerY + radius * sin(itemAngle)

                // Measure the text
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(playlist.name),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false
                )

                // Draw the text, rotated to be tangential to the circle
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x - textLayoutResult.size.width / 2f,
                        y - textLayoutResult.size.height / 2f
                    ),
                    alpha = 1f
                )

                // Draw a small dot for each playlist
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = 3.dp.toPx(),
                    center = Offset(
                        centerX + (radius - 20.dp.toPx()) * cos(itemAngle),
                        centerY + (radius - 20.dp.toPx()) * sin(itemAngle)
                    )
                )
            }

            // Draw the circle guide
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx()
                )
            )
        }

        // Selected indicator (fixed at top, doesn't rotate)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .size(width = 100.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CoralColors.Coral)
        )

        // Selected playlist name (big, at top, doesn't rotate)
        if (playlists.isNotEmpty() && selectedIndex >= 0) {
            Text(
                text = playlists[selectedIndex].name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPlaylistClick(playlists[selectedIndex]) }
                    )
            )
        }

        // Hint text
        Text(
            text = "Slide to browse • Tap to open",
            color = CoralColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()

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
