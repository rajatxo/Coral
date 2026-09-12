package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.SleepTimerCapsule
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var isRandomMode by remember { mutableStateOf(false) }

    val quickPicksSongs = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()
        if (isRandomMode) {
            songs.shuffled().take(15)
        } else {
            val currentSong = songs.firstOrNull { it.id == currentSongId }
            if (currentSong != null) {
                val sameArtist = songs.filter {
                    it.artist == currentSong.artist && it.id != currentSong.id
                }
                val sameAlbum = songs.filter {
                    it.album == currentSong.album && it.id != currentSong.id &&
                    it.id !in sameArtist.map { s -> s.id }
                }
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct().take(15)
                if (related.size < 5) {
                    val fillers = songs.filter { it.id !in related.map { s -> s.id } }
                        .shuffled().take(15 - related.size)
                    (related + fillers).distinct()
                } else related
            } else {
                songs.shuffled().take(15)
            }
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val scrollOffset = remember { Animatable(0f) }
    var lastActiveIndex by remember { mutableStateOf(0) }

    val totalSongs = quickPicksSongs.size
    val activeIndex = scrollOffset.value.toInt().coerceIn(0, (totalSongs - 1).coerceAtLeast(0))
    val activeSong = quickPicksSongs.getOrNull(activeIndex)

    LaunchedEffect(activeIndex) {
        if (activeIndex != lastActiveIndex && totalSongs > 0) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastActiveIndex = activeIndex
        }
    }

    var bgUri by remember { mutableStateOf<android.net.Uri?>(null) }
    LaunchedEffect(activeIndex, quickPicksSongs) {
        bgUri = quickPicksSongs.getOrNull(activeIndex)?.albumArtUri
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // --- Blurred album art bg (immersive, dynamic) ---
        if (bgUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(bgUri)
                    .crossfade(600)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.4f
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
            )
        }

        // --- Dark gradient overlay ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.6f),
                            0.4f to Color.Black.copy(alpha = 0.3f),
                            0.7f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // --- Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
                if (capsuleVisible && capsuleRemaining > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Text(
                    text = "Quick picks",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily
                )
            }

            // Toggle capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (!isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Based on last played",
                        color = if (!isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isRandomMode) Color.White else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isRandomMode = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Random picks",
                        color = if (isRandomMode) Color.Black else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // --- CoverFlow arc ---
            if (quickPicksSongs.isNotEmpty()) {
                CoverFlowArc(
                    songs = quickPicksSongs,
                    scrollOffset = scrollOffset,
                    onPlayClick = { onSongClick(it) },
                    modifier = Modifier.weight(1f)
                )

                // Footer: title + artist
                if (activeSong != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 120.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeSong.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = PlayfairItalicFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeSong.artist,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 14.sp,
                            fontFamily = NyghtSerifFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🎵", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No songs found",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverFlowArc(
    songs: List<Song>,
    scrollOffset: Animatable<Float, *>,
    onPlayClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val totalSongs = songs.size
    val currentOffset = scrollOffset.value
    var velocityTracker by remember { mutableStateOf(VelocityTracker()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(totalSongs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        scope.launch {
                            scrollOffset.animateDecay(
                                initialVelocity = -velocity * 0.5f,
                                animationSpec = exponentialDecay(frictionMultiplier = 0.95f)
                            )
                            val nearest = scrollOffset.value.toInt()
                                .coerceIn(0, totalSongs - 1).toFloat()
                            scrollOffset.animateTo(
                                targetValue = nearest,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        scope.launch {
                            scrollOffset.snapTo(
                                (scrollOffset.value - dragAmount / 200f)
                                    .coerceIn(0f, (totalSongs - 1).toFloat())
                            )
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    }
                )
            }
    ) {
        val activeIndex = currentOffset.toInt().coerceIn(0, (totalSongs - 1).coerceAtLeast(0))

        val coversToRender = (-2..2).mapNotNull { offset ->
            val index = activeIndex + offset
            if (index in songs.indices) {
                val fractionalOffset = currentOffset - index
                Triple(index, offset, fractionalOffset)
            } else null
        }.sortedByDescending { abs(it.third) }

        coversToRender.forEach { (index, _, fractionalOffset) ->
            val song = songs[index]
            val isActive = index == activeIndex

            val absOffset = abs(fractionalOffset)
            val scale = (1f - absOffset * 0.4f).coerceIn(0.4f, 1f)
            val xFraction = sin(fractionalOffset.toDouble() * 0.6).toFloat() * 0.45f
            val yFraction = -(1f - kotlin.math.cos(fractionalOffset.toDouble() * 0.6).toFloat()) * 0.12f
            val rotation = fractionalOffset * 25f
            val alpha = (1f - absOffset * 0.6f).coerceIn(0.2f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = xFraction * size.width
                        translationY = yFraction * size.height
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        this.alpha = alpha
                        rotationY = -fractionalOffset * 35f
                        cameraDistance = size.width * 2f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isActive) {
                                onPlayClick(song)
                            } else {
                                scope.launch {
                                    scrollOffset.animateTo(
                                        targetValue = index.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (isActive) 200.dp else 160.dp)
                        .shadow(
                            elevation = if (isActive) 24.dp else 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1A))
                ) {
                    if (song.albumArtUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.albumArtUri)
                                .crossfade(300)
                                .build(),
                            contentDescription = "Album art for ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🎵", fontSize = 48.sp)
                        }
                    }
                }
            }
        }
    }
}
