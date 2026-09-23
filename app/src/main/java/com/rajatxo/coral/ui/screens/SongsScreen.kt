package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.BugLineRefreshIndicator
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Songs tab — dual-arc wheel design.
 *
 * Two concentric arcs around an off-screen left pivot:
 *   • INNER (small) arc: letters A-Z. Rotating this selects the active letter.
 *   • OUTER (big) arc: songs. Shows songs filtered by the selected letter.
 *     Each song is a glass morphism card (semi-transparent dark + border).
 *
 * Interaction:
 *   • Drag the right side → rotates the song arc (big)
 *   • Drag the left side → rotates the letter arc (small) in opposite direction
 *   • Tap a song card → plays that song
 *   • The letter at the apex of the small arc filters the songs on the big arc
 *
 * The "All songs" header with bug PTR indicator sits above the wheel.
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
            // ═══ Song wheel ═══
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
// SONG WHEEL — dual-arc rotary picker
// ════════════════════════════════════════════════════════════════════
// Adapted from PlaylistsScreen's PlaylistWheel:
//   • INNER arc (small radius): letters A-Z, counter-rotating balls
//   • OUTER arc (big radius): songs as glass cards
//   • The letter at the apex of the inner arc filters the songs on the outer arc
//   • Tap a song on the outer arc → plays it
//
// Two independent scroll offsets:
//   • letterScrollOffset — controls the letter wheel
//   • songScrollOffset — controls the song wheel (resets when letter changes)
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

    // --- Letters ---
    val letters = remember(songs) {
        val letterSet = songs.mapNotNull { song ->
            val c = song.title.firstOrNull()?.uppercaseChar()
            if (c != null && c.isLetter()) c.toString() else "#"
        }.toSet().sorted()
        if (letterSet.isEmpty()) listOf("#") else letterSet
    }

    // --- Active letter → filtered songs ---
    // null = show ALL songs. When a letter is at the apex, filter to that letter.
    var activeLetterIndex by remember { mutableStateOf(0) }
    val filteredSongs = remember(songs, activeLetterIndex) {
        if (letters.isEmpty()) songs
        else {
            val letter = letters[activeLetterIndex.coerceIn(0, letters.lastIndex)]
            if (letter == "#") {
                songs.filter { song ->
                    val c = song.title.firstOrNull()?.uppercaseChar()
                    c == null || !c.isLetter()
                }
            } else {
                songs.filter { song ->
                    song.title.firstOrNull()?.uppercaseChar()?.toString()?.equals(letter, ignoreCase = true) == true
                }
            }.ifEmpty { songs }  // fallback to all if no songs match
        }
    }

    // --- Geometry constants ---
    val angleStepDeg = 8f
    val pxPerItem = with(density) { 64.dp.toPx() }

    // --- Two independent scroll offsets ---
    val letterScrollOffset = remember { Animatable(0f) }
    val songScrollOffset = remember { Animatable(0f) }

    // --- Letter index at apex ---
    var lastLetterIndex by remember { mutableStateOf(0) }
    fun letterIndexAtOffset(offset: Float): Int {
        if (letters.isEmpty()) return 0
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % letters.size
        return if (mod < 0) mod + letters.size else mod
    }

    val centerLetterIdx = remember(letterScrollOffset.value) {
        letterIndexAtOffset(letterScrollOffset.value)
    }

    // When the center letter changes, update the active letter + reset song offset
    LaunchedEffect(centerLetterIdx) {
        if (centerLetterIdx != lastLetterIndex && letters.isNotEmpty()) {
            lastLetterIndex = centerLetterIdx
            activeLetterIndex = centerLetterIdx
            // Reset the song wheel to the start when the letter changes
            songScrollOffset.snapTo(0f)
        }
    }

    // --- Song index at apex ---
    var lastSongIndex by remember { mutableStateOf(0) }
    fun songIndexAtOffset(offset: Float): Int {
        if (filteredSongs.isEmpty()) return 0
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % filteredSongs.size
        return if (mod < 0) mod + filteredSongs.size else mod
    }

    val centerSongIdx = remember(songScrollOffset.value, filteredSongs) {
        songIndexAtOffset(songScrollOffset.value)
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

    // --- "All songs" header (fixed at top, scrolls with PTR) ---
    // Rendered OUTSIDE the Canvas, at the top of the wheel area.

    Box(
        modifier = modifier
            // LEFT ZONE drag → rotates LETTERS (inner arc, negated)
            .pointerInput(letters.size) {
                val halfScreen = size.width / 2
                var velocityTracker = VelocityTracker()
                var isLeftZoneDrag = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isLeftZoneDrag = offset.x < halfScreen
                        if (isLeftZoneDrag) {
                            velocityTracker = VelocityTracker()
                        }
                    },
                    onDragEnd = {
                        if (isLeftZoneDrag) {
                            val velocity = velocityTracker.calculateVelocity().y
                            coroutineScope.launch {
                                letterScrollOffset.animateDecay(
                                    initialVelocity = -velocity * 0.35f,
                                    animationSpec = exponentialDecay(frictionMultiplier = 0.9f)
                                )
                                val nearest = (letterScrollOffset.value / pxPerItem).roundToInt()
                                letterScrollOffset.animateTo(
                                    targetValue = nearest * pxPerItem,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                        isLeftZoneDrag = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (isLeftZoneDrag) {
                            coroutineScope.launch {
                                letterScrollOffset.snapTo(letterScrollOffset.value - dragAmount)
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val currentIdx = letterIndexAtOffset(letterScrollOffset.value)
                            if (currentIdx != lastLetterIndex) {
                                lastLetterIndex = currentIdx
                                tickHaptic()
                            }
                            change.consume()
                        }
                    }
                )
            }
            // RIGHT ZONE drag → rotates SONGS (outer arc, normal)
            .pointerInput(filteredSongs.size) {
                var velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        coroutineScope.launch {
                            songScrollOffset.animateDecay(
                                initialVelocity = velocity * 0.35f,
                                animationSpec = exponentialDecay(frictionMultiplier = 0.9f)
                            )
                            val nearest = (songScrollOffset.value / pxPerItem).roundToInt()
                            songScrollOffset.animateTo(
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
                            songScrollOffset.snapTo(songScrollOffset.value + dragAmount)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val currentIdx = songIndexAtOffset(songScrollOffset.value)
                        if (currentIdx != lastSongIndex) {
                            lastSongIndex = currentIdx
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

            // === DUAL RADII ===
            // Inner arc (letters) — smaller radius
            val letterArcRadius = w * 0.50f
            // Outer arc (songs) — bigger radius
            val songArcRadius = w * 0.75f
            // Text orbit for songs (outside the song arc)
            val songTextRadius = songArcRadius + with(density) { 30.dp.toPx() }

            // === ARC SWEEP ===
            val arcSweepDeg = 100f
            val arcStartDeg = -arcSweepDeg / 2f

            // === DRAW LETTER ARC (inner, small) ===
            drawFadingArc(
                radius = letterArcRadius,
                fullAlpha = 0.5f,
                strokePx = 1.5f,
                pivotX = pivotX,
                pivotY = pivotY,
                arcStartDeg = arcStartDeg,
                arcSweepDeg = arcSweepDeg
            )

            // === DRAW SONG ARC (outer, big) ===
            drawFadingArc(
                radius = songArcRadius,
                fullAlpha = 0.4f,
                strokePx = 1.0f,
                pivotX = pivotX,
                pivotY = pivotY,
                arcStartDeg = arcStartDeg,
                arcSweepDeg = arcSweepDeg
            )

            // === DRAW LETTERS ON INNER ARC ===
            if (letters.isNotEmpty()) {
                val letterRotationItems = letterScrollOffset.value / pxPerItem
                val letterVisibleSpan = 7
                for (offset in -letterVisibleSpan..letterVisibleSpan) {
                    val rawIdx = (letterRotationItems.roundToInt() + offset)
                    val modIdx = ((rawIdx % letters.size) + letters.size) % letters.size
                    val letter = letters[modIdx]

                    val fractionalOffset = letterRotationItems - letterRotationItems.roundToInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > letterVisibleSpan) continue

                    val itemAngleDeg = fractionalOffset * angleStepDeg
                    val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                    val letterX = pivotX + letterArcRadius * cos(itemAngleRad)
                    val letterY = pivotY + letterArcRadius * sin(itemAngleRad)

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
                    val ballColor = if (isActive) Color(0xFFFF6B6B) else Color.White
                    val ballRadius = if (isActive) 5.dp.toPx() else 3.dp.toPx()

                    // Ball
                    drawCircle(
                        color = ballColor,
                        radius = ballRadius,
                        center = Offset(letterX, letterY),
                        alpha = alpha
                    )

                    // Letter text (using textMeasurer to render)
                    val fontSize = if (isActive) 14.sp else 11.sp
                    val textResult = textMeasurer.measure(
                        text = androidx.compose.ui.text.AnnotatedString(letter),
                        style = androidx.compose.ui.text.TextStyle(
                            color = if (isActive) Color(0xFFFF6B6B) else Color.White.copy(alpha = alpha),
                            fontSize = fontSize,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = CalSansFamily
                        )
                    )
                    val textX = letterX + with(density) { 8.dp.toPx() }
                    val textY = letterY - textResult.size.height / 2f
                    drawText(textResult, topLeft = Offset(textX, textY))
                }
            }

            // === DRAW SONGS ON OUTER ARC ===
            if (filteredSongs.isNotEmpty()) {
                val songRotationItems = songScrollOffset.value / pxPerItem
                val songVisibleSpan = 7
                for (offset in -songVisibleSpan..songVisibleSpan) {
                    val rawIdx = (songRotationItems.roundToInt() + offset)
                    val modIdx = ((rawIdx % filteredSongs.size) + filteredSongs.size) % filteredSongs.size
                    val song = filteredSongs[modIdx]

                    val fractionalOffset = songRotationItems - songRotationItems.roundToInt() + offset
                    val absOffset = abs(fractionalOffset)
                    if (absOffset > songVisibleSpan) continue

                    val itemAngleDeg = fractionalOffset * angleStepDeg
                    val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

                    val songX = pivotX + songTextRadius * cos(itemAngleRad)
                    val songY = pivotY + songTextRadius * sin(itemAngleRad)

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

                    // Ball marker
                    val ballColor = if (isPlaying) Color(0xFFFF6B6B) else if (isActive) Color.White else Color.White.copy(alpha = 0.6f)
                    val ballRadius = if (isActive) 5.dp.toPx() else 3.dp.toPx()
                    drawCircle(
                        color = ballColor,
                        radius = ballRadius,
                        center = Offset(songX, songY),
                        alpha = alpha
                    )

                    // Song text — title + artist
                    val titleColor = if (isPlaying) Color(0xFFFF6B6B) else Color.White.copy(alpha = alpha)
                    val artistColor = Color.White.copy(alpha = alpha * 0.6f)

                    val titleResult = textMeasurer.measure(
                        text = androidx.compose.ui.text.AnnotatedString(song.title),
                        style = androidx.compose.ui.text.TextStyle(
                            color = titleColor,
                            fontSize = if (isActive) 14.sp else 11.sp,
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
                            fontSize = if (isActive) 11.sp else 9.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = CalSansFamily
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        softWrap = false
                    )

                    val textGap = with(density) { 8.dp.toPx() }
                    val textX = songX + textGap
                    val titleY = songY - (titleResult.size.height + artistResult.size.height) / 2f
                    val artistY = titleY + titleResult.size.height

                    drawText(titleResult, topLeft = Offset(textX, titleY))
                    drawText(artistResult, topLeft = Offset(textX, artistY))
                }
            }
        }

        // === "All songs" header (fixed overlay, scrolls with PTR) ===
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
        // Tapping the right side of the screen plays the song at the apex
        // of the outer (song) arc. Simple, no precise hit-testing needed.
        if (filteredSongs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(filteredSongs.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Only register taps on the right half (song arc zone)
                                if (offset.x > size.width / 2) {
                                    val idx = songIndexAtOffset(songScrollOffset.value)
                                    if (idx in filteredSongs.indices) {
                                        onSongClick(filteredSongs[idx])
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
// HELPER: draw a fading arc (same technique as PlaylistWheel)
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
