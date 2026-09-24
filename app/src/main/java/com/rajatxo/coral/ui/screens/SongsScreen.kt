package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.BugLineRefreshIndicator
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Songs tab — single-arc wheel with glass capsule cards.
 *
 * ONE big arc. All songs sit on it. Each song has:
 *   • A ball marker on the arc line
 *   • A transparent thin capsule card beside the ball containing:
 *     - Circular album art (left)
 *     - Song title + artist name (right, CalSans)
 *
 * Interaction:
 *   • Drag up/down → rotates the wheel (songs scroll through the arc)
 *   • Tap the right side → plays the song at the apex
 *
 * The "All songs" header with bug PTR sits at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArt: android.net.Uri? = null,
    onSongClick: (Song) -> Unit,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onRefresh: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sortedSongs = remember(songs) {
        songs.sortedBy { it.title.lowercase() }
    }

    // ─── Current song's palette → dark gradient background ──────────
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(currentSongArt) {
        if (currentSongArt != null) {
            PaletteCache.get(currentSongArt)?.let { palette = it }
            extractPalette(context, currentSongArt)?.let {
                palette = it
                PaletteCache.put(currentSongArt, it)
            }
        }
    }

    val vibrantTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.85f),
        animationSpec = tween(800), label = "songsBgVibrantTop"
    )
    val fade1 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.65f),
        animationSpec = tween(800), label = "songsBgFade1"
    )
    val fade2 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.45f),
        animationSpec = tween(800), label = "songsBgFade2"
    )
    val fade3 by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.28f),
        animationSpec = tween(800), label = "songsBgFade3"
    )
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.18f),
        animationSpec = tween(800), label = "songsBgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.08f),
        animationSpec = tween(800), label = "songsBgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),
        animationSpec = tween(800), label = "songsBgBottom"
    )

    val darkBase = Color(0xFF05050A)

    // ─── Pull-to-refresh state ───────────────────────────────────────
    val ptrState: PullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f  to vibrantTop,
                        0.10f to fade1,
                        0.15f to fade2,
                        0.20f to fade3,
                        0.30f to animatedTop,
                        0.55f to animatedMid,
                        1.0f  to animatedBottom
                    )
                )
            )
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try { onRefresh() } finally { isRefreshing = false }
                }
            },
            state = ptrState,
            indicator = {},
            modifier = Modifier.fillMaxSize()
        ) {
            SongWheel(
                songs = sortedSongs,
                currentSongId = currentSongId,
                onSongClick = onSongClick,
                ptrState = ptrState,
                isRefreshing = isRefreshing,
                songCount = songs.size,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SONG WHEEL — single arc, all songs, glass capsule cards
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongWheel(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song) -> Unit,
    ptrState: PullToRefreshState,
    isRefreshing: Boolean,
    songCount: Int,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    // --- Album art bitmaps cache ---
    // We load album art as ImageBitmap and cache them so the Canvas can draw them.
    // Only load for songs that are likely visible (±10 from center).
    val artCache = remember { mutableMapOf<Long, ImageBitmap>() }

    // --- Geometry constants (same as PlaylistWheel) ---
    val angleStepDeg = 8f
    val pxPerItem = with(density) { 64.dp.toPx() }
    val scrollOffset = remember { Animatable(0f) }
    var lastSnappedIndex by remember { mutableStateOf(0) }

    fun indexAtOffset(offset: Float): Int {
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % songs.size
        return if (mod < 0) mod + songs.size else mod
    }

    val centerIndex = remember(scrollOffset.value) { indexAtOffset(scrollOffset.value) }

    // --- Load album art for visible songs ---
    LaunchedEffect(centerIndex, songs) {
        val startIdx = (centerIndex - 10).coerceAtLeast(0)
        val endIdx = min(centerIndex + 10, songs.lastIndex)
        for (i in startIdx..endIdx) {
            val song = songs[i]
            if (song.albumArtUri != null && !artCache.containsKey(song.id)) {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeStream(
                            context.contentResolver.openInputStream(song.albumArtUri)
                        )
                    }
                    if (bitmap != null) {
                        // Downscale to 48dp for performance
                        val targetPx = with(density) { 48.dp.toPx() }.toInt()
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            bitmap, targetPx, targetPx, true
                        )
                        artCache[song.id] = scaled.asImageBitmap()
                        if (bitmap != scaled) bitmap.recycle()
                    }
                } catch (_: Exception) { }
            }
        }
    }

    // --- Haptic tick ---
    fun tickHaptic() {
        try {
            view.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) { }
    }

    Box(
        modifier = modifier
            .pointerInput(songs.size) {
                var velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        coroutineScope.launch {
                            scrollOffset.animateDecay(
                                initialVelocity = velocity * 0.35f,
                                animationSpec = exponentialDecay(frictionMultiplier = 0.9f)
                            )
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
                        val currentIdx = indexAtOffset(scrollOffset.value)
                        if (currentIdx != lastSnappedIndex) {
                            lastSnappedIndex = currentIdx
                            tickHaptic()
                        }
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // === PIVOT (off-screen left) ===
            val pivotX = w * -0.50f
            val pivotY = h * 0.50f

            // === ARC RADIUS ===
            val arcRadius = w * 0.65f
            // Card orbit — sits OUTSIDE the arc, where capsule cards are drawn
            val cardRadius = arcRadius + with(density) { 20.dp.toPx() }

            // === ARC SWEEP ===
            val arcSweepDeg = 100f
            val arcStartDeg = -arcSweepDeg / 2f

            // === DRAW FADING ARC ===
            drawFadingArc(
                radius = arcRadius,
                fullAlpha = 0.4f,
                strokePx = 1.0f,
                pivotX = pivotX,
                pivotY = pivotY,
                arcStartDeg = arcStartDeg,
                arcSweepDeg = arcSweepDeg
            )

            // === DRAW SONGS ON ARC ===
            val rotationItems = scrollOffset.value / pxPerItem
            val visibleSpan = 7

            for (offset in -visibleSpan..visibleSpan) {
                val rawIdx = (rotationItems.roundToInt() + offset)
                val modIdx = ((rawIdx % songs.size) + songs.size) % songs.size
                val song = songs[modIdx]

                val fractionalOffset = rotationItems - rotationItems.roundToInt() + offset
                val absOffset = abs(fractionalOffset)
                if (absOffset > visibleSpan) continue

                val itemAngleDeg = fractionalOffset * angleStepDeg
                val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                // Ball position ON the arc
                val ballX = pivotX + arcRadius * cos(itemAngleRad)
                val ballY = pivotY + arcRadius * sin(itemAngleRad)

                // Card position — OUTSIDE the arc (radially outward)
                val cardX = pivotX + cardRadius * cos(itemAngleRad)
                val cardY = pivotY + cardRadius * sin(itemAngleRad)

                // Alpha curve
                val alpha = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> 0.7f
                    absOffset < 2.5f -> 0.45f
                    absOffset < 3.5f -> 0.25f
                    absOffset < 4.5f -> 0.12f
                    absOffset < 5.5f -> 0.05f
                    else -> 0f
                }
                if (alpha <= 0.01f) continue

                val isActive = absOffset < 0.5f
                val isPlaying = isActive && song.id == currentSongId

                // === 1. BALL MARKER on the arc ===
                val ballColor = if (isPlaying) Color(0xFFFF6B6B)
                    else if (isActive) Color.White
                    else Color.White.copy(alpha = 0.6f)
                val ballRadiusPx = if (isActive) 5.dp.toPx() else 3.dp.toPx()
                drawCircle(
                    color = ballColor,
                    radius = ballRadiusPx,
                    center = Offset(ballX, ballY),
                    alpha = alpha
                )

                // === 2. CAPSULE CARD beside the ball ===
                // Transparent thin capsule: semi-transparent dark bg + thin white border
                val cardWidthPx = with(density) { 200.dp.toPx() }
                val cardHeightPx = with(density) { 48.dp.toPx() }
                val cardCornerRadiusPx = with(density) { 24.dp.toPx() }

                // Card top-left position (card extends to the RIGHT from the ball)
                val cardLeft = ballX + with(density) { 10.dp.toPx() }
                val cardTop = cardY - cardHeightPx / 2f

                // Draw card background (semi-transparent dark)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.35f * alpha),
                    topLeft = Offset(cardLeft, cardTop),
                    size = Size(cardWidthPx, cardHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        cardCornerRadiusPx, cardCornerRadiusPx
                    )
                )
                // Draw card border (thin white)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.1f * alpha),
                    topLeft = Offset(cardLeft, cardTop),
                    size = Size(cardWidthPx, cardHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        cardCornerRadiusPx, cardCornerRadiusPx
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )

                // === 3. CIRCULAR ALBUM ART inside the card ===
                val artSizePx = with(density) { 36.dp.toPx() }
                val artCenterX = cardLeft + with(density) { 6.dp.toPx() } + artSizePx / 2f
                val artCenterY = cardY
                val artRadius = artSizePx / 2f

                // Clip to circle: draw a dark circle bg first
                drawCircle(
                    color = Color(0xFF1A1A1A).copy(alpha = alpha),
                    radius = artRadius,
                    center = Offset(artCenterX, artCenterY)
                )

                // Draw album art bitmap if cached
                val artBitmap = artCache[song.id]
                if (artBitmap != null) {
                    // Draw the bitmap clipped to a circle
                    // We clip by drawing a circle path then the image inside
                    val srcSize = artBitmap.width.toFloat()
                    val srcLeft = (srcSize - artBitmap.width.toFloat()) / 2f
                    drawImage(
                        image = artBitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                        srcSize = androidx.compose.ui.unit.IntSize(
                            artBitmap.width, artBitmap.height
                        ),
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            (artCenterX - artRadius).toInt(),
                            (artCenterY - artRadius).toInt()
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(
                            artSizePx.toInt(), artSizePx.toInt()
                        ),
                        alpha = alpha
                    )
                    // Draw a circle border around the art
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f * alpha),
                        radius = artRadius,
                        center = Offset(artCenterX, artCenterY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // === 4. SONG TITLE + ARTIST inside the card ===
                val textStartX = cardLeft + with(density) { 6.dp.toPx() } + artSizePx + with(density) { 8.dp.toPx() }
                val titleColor = if (isPlaying) Color(0xFFFF6B6B) else Color.White.copy(alpha = alpha)
                val artistColor = Color.White.copy(alpha = alpha * 0.6f)

                val titleResult = textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString(song.title),
                    style = androidx.compose.ui.text.TextStyle(
                        color = titleColor,
                        fontSize = if (isActive) 13.sp else 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = CalSansFamily
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false
                )
                val artistResult = textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString(song.artist),
                    style = androidx.compose.ui.text.TextStyle(
                        color = artistColor,
                        fontSize = if (isActive) 10.sp else 9.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = CalSansFamily
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false
                )

                val titleY = cardY - (titleResult.size.height + artistResult.size.height) / 2f - 1f
                val artistY = titleY + titleResult.size.height + 1f

                drawText(titleResult, topLeft = Offset(textStartX, titleY))
                drawText(artistResult, topLeft = Offset(textStartX, artistY))
            }
        }

        // === "All songs" header (fixed overlay) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 108.dp, start = 24.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All songs",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            BugLineRefreshIndicator(
                progress = ptrState.distanceFraction,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
            )
            Text(
                text = "$songCount",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
        }

        // === Tap zone for playing the center song ===
        if (songs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(songs.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (offset.x > size.width / 2) {
                                    val idx = indexAtOffset(scrollOffset.value)
                                    if (idx in songs.indices) {
                                        onSongClick(songs[idx])
                                        tickHaptic()
                                    }
                                }
                            }
                        )
                    }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HELPER: draw a fading arc
// ════════════════════════════════════════════════════════════════════

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFadingArc(
    radius: Float,
    fullAlpha: Float,
    strokePx: Float,
    pivotX: Float,
    pivotY: Float,
    arcStartDeg: Float,
    arcSweepDeg: Float
) {
    val arcSegments = 40
    val fadeRange = 0.35f
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
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokePx)
        )
    }
}
