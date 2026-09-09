package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    onExtend: () -> Unit = {},
    accentColor: Color = Color(0xFFF4B400)
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // --- Wheel/Grid toggle state ---
    var useWheel by remember { mutableStateOf(true) }

    // --- Wheel rotation state ---
    var isRotating by remember { mutableStateOf(false) }
    var centerPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // --- "All Playlist" pill text + 3-second timeout ---
    // Default: "All Playlist". When user rotates the wheel, the center
    // playlist name shows. After 3 seconds of no change, reverts to
    // "All Playlist". The capsule itself is ALWAYS visible (permanent).
    var playlistPillText by remember { mutableStateOf("All Playlist") }
    var playlistPillJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pillScope = rememberCoroutineScope()

    // When the center playlist changes, update the pill text and start
    // a 3-second timer to revert to "All Playlist".
    androidx.compose.runtime.LaunchedEffect(centerPlaylist) {
        if (centerPlaylist != null) {
            playlistPillText = centerPlaylist!!.name
            playlistPillJob?.cancel()
            playlistPillJob = pillScope.launch {
                kotlinx.coroutines.delay(3000L)
                playlistPillText = "All Playlist"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CoralColors.Surface)) {
        // Header Column: Row(capsule + title) + big capsule with inner items
        // zIndex(1f) keeps the header ABOVE the wheel so the Grid/Wheel
        // toggle capsule stays clickable (otherwise the wheel's pointerInput
        // would intercept touches over the header area).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 20.dp, top = 16.dp)
                .zIndex(1f)
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

            // --- TWO big capsules, stacked vertically ---
            // Big Capsule 1 (top):    [New] ........... [Grid/Wheel]
            // Big Capsule 2 (below):  [All Playlist] [All Tags]
            // Both capsules are the same size: 40dp height, 20dp rounded
            // corners, SurfaceVariant background.

            // === Big Capsule 1: New + Grid/Wheel ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CoralColors.SurfaceVariant)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "New" capsule (left side) — width matches "All Playlist"
                // capsule below so the two big capsules align visually.
                // Uses the double-Text trick: transparent "All Playlist"
                // text underneath sizes the capsule to match, the visible
                // "New" text + icon sit on top.
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(IntrinsicSize.Max)
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
                    Box(contentAlignment = Alignment.Center) {
                        // Invisible sizer: sizes the capsule to "All Playlist"
                        Text(
                            text = "All Playlist",
                            color = Color.Transparent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Visible content: icon + "New"
                        Row(
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
                    }
                }

                // Spacer to push Grid/Wheel to the right
                Spacer(modifier = Modifier.weight(1f))

                // Grid/Wheel toggle capsule (right side)
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { useWheel = !useWheel }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Wheel",
                            color = Color.Transparent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (useWheel) "Grid" else "Wheel",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(4.dp))

            // === Big Capsule 2: All Playlist + All Tags ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CoralColors.SurfaceVariant)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "All Playlist" capsule — shows playlist name on rotate, 3s timeout
                // Width is fixed to the DEFAULT text ("All Playlist") so it
                // doesn't resize when the playlist name changes. Uses the
                // double-Text trick (same as Grid/Wheel capsule).
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (playlistPillText != "All Playlist" && centerPlaylist != null) {
                                    centerPlaylist?.let { onPlaylistClick(it) }
                                }
                            }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Render "All Playlist" transparent underneath so the
                    // capsule is always sized to the default text width,
                    // regardless of the current playlist name.
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "All Playlist",
                            color = Color.Transparent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = playlistPillText,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Spacer pushes "All Tags" capsule to the very right,
                // so it sits exactly below the Grid/Wheel capsule above.
                Spacer(modifier = Modifier.weight(1f))

                // "All Tags" capsule — fixed at the right end
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Tags",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
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
                // === CLEAN SLATE: just the first arc in the bottom-left corner ===
                // All wheel code removed. We're rebuilding the arc placement from
                // scratch. This Canvas draws ONLY the arc line — no balls, no text,
                // no scrolling. Just the curve so we can verify its position.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 110.dp, bottom = 16.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Pivot: off-screen bottom-left
                    // X = -30% screen width (off-screen left)
                    // Y = 75% screen height (lower area — the arc curves upward)
                    val pivotX = w * -0.30f
                    val pivotY = h * 0.75f

                    // Radius — medium size
                    val arcRadius = w * 0.55f

                    // Arc: centered at 0° (pointing RIGHT from the left-side pivot)
                    // Sweep = 40°, so the arc spans from -20° to +20°
                    val arcSweepDeg = 40f
                    val arcStartDeg = -arcSweepDeg / 2f

                    // Draw the arc — thin white line, 1.5dp stroke
                    drawArc(
                        color = Color.White.copy(alpha = 0.6f),
                        startAngle = arcStartDeg,
                        sweepAngle = arcSweepDeg,
                        useCenter = false,
                        topLeft = Offset(pivotX - arcRadius, pivotY - arcRadius),
                        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
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
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFF4B400),
    onRotationStart: () -> Unit = {},
    onRotationEnd: () -> Unit = {},
    onCenterPlaylistChange: (Playlist) -> Unit = {}
) {
    if (playlists.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // --- Sound effect for wheel scroll (CC0, commercial-safe) ---
    // Kenney UI Audio pack — switch13.wav. Short, crisp, mechanical tick.
    // Loaded via SoundPool (low latency, can overlap). Plays on every ball
    // boundary crossing during scroll, paired with the haptic tick.
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)  // allow overlapping ticks during fast scroll
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    // Track whether the sound has finished loading (SoundPool.load is async).
    // If we play before loading completes, play() silently does nothing.
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) soundLoaded = true
        }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    /** Play the tick sound. Only fires after the sound has finished loading. */
    fun playTickSound() {
        if (!soundLoaded) return
        try {
            soundPool.play(
                tickSoundId,
                0.6f,   // left volume
                0.6f,   // right volume
                1,      // priority
                0,      // loop (0 = no loop)
                1f      // playback rate
            )
        } catch (_: Exception) {
            // Swallow — sound is non-critical, haptic should still fire
        }
    }

    // --- Vibrator fallback (used if View.performHapticFeedback returns false) ---
    // Compose's HapticFeedbackType.TextHandleMove is too subtle on Android 13
    // and on some OEMs like Realme. We use View.performHapticFeedback (the same
    // API buttons use) as the primary, and the Vibrator service as fallback.
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as? android.os.VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                    as? android.os.Vibrator
        }
    }

    /** Fire a button-press-style haptic tick + sound. Works on all Android. */
    fun tickHaptic() {
        // 1. HAPTIC (always fires first, so it never gets blocked by sound):
        //    View.performHapticFeedback — the exact API buttons use.
        var hapticPerformed = false
        try {
            hapticPerformed = view.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) {
            // Swallow — fall through to Vibrator fallback
        }

        // 2. HAPTIC FALLBACK: if View.performHapticFeedback returned false,
        //    use the direct Vibrator API with EFFECT_CLICK.
        if (!hapticPerformed) {
            try {
                val v = vibrator
                if (v != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.vibrate(
                            android.os.VibrationEffect.createPredefined(
                                android.os.VibrationEffect.EFFECT_CLICK
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(30)
                    }
                }
            } catch (_: Exception) {
                // Swallow — haptic is best-effort, not critical
            }
        }

        // 3. SOUND (plays after haptic, so even if sound fails, haptic already fired):
        playTickSound()
    }

    // --- Geometry constants ---
    // Angular spacing between adjacent items (degrees). Reduced from 8° → 6°
    // so more items fit in the smaller 35° sweep window.
    val angleStepDeg = 6f

    // Pixels of vertical drag required to advance the wheel by ONE item.
    val pxPerItem = with(density) { 64.dp.toPx() }

    // Scroll offset (in pixels). Each pxPerItem corresponds to angleStepDeg
    // of rotation. Positive = wheel rotates so items move DOWN visually
    // (finger swiped down); negative = items move UP.
    val scrollOffset = remember { Animatable(0f) }

    // Index of the item currently at the apex (selected).
    var lastSnappedIndex by remember { mutableStateOf(0) }

    // --- Selection helper ---
    // NOTE: NOT negated. Scroll DOWN = items move DOWN on visible arc.
    fun indexAtOffset(offset: Float): Int {
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % playlists.size
        return if (mod < 0) mod + playlists.size else mod
    }

    val centerIndex = remember(scrollOffset.value) { indexAtOffset(scrollOffset.value) }

    // Notify parent whenever the center playlist changes (during scroll + at rest)
    androidx.compose.runtime.LaunchedEffect(centerIndex, playlists) {
        if (playlists.isNotEmpty()) {
            onCenterPlaylistChange(playlists[centerIndex])
        }
    }

    // --- Gestures: rotational drag + physics fling + snap ---
    // NOTE: Wheel is NOT clickable. User opens playlists via the selection
    // capsule that appears below the header capsule.
    Box(
        modifier = modifier
            .pointerInput(playlists.size) {
                var velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker = VelocityTracker()
                        onRotationStart()
                    },
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
                            // Wait 1.5s so user can read the name + click the capsule
                            kotlinx.coroutines.delay(1500L)
                            onRotationEnd()
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

            // === 1. PIVOT (off-screen, left) ===
            // X = -30% screen width (less off-screen than before, so the
            // imaginary circle is smaller and the arc is MORE CURVED/visible)
            // Y = 60% screen height — centered on "Playlists" nav rail item,
            //    so the arc is symmetric: apex at Playlists, top at Songs,
            //    bottom at Artists.
            val pivotX = w * -0.30f
            val pivotY = h * 0.60f

            // === 2. DUAL CONCENTRIC RADII SYSTEM ===
            // Medium radius (0.55w) + larger sweep (40°) = visible curve
            // that spans from Songs to Artists on the nav rail.
            // Apex lands at ~25% from left edge (near the Playlists nav text).
            //
            // Radius A — Visible arc line (1.5px semi-transparent white).
            val arcRadius = w * 0.55f

            // Radius B — Text orbit. Sits 18dp OUTSIDE the arc line.
            val textRadius = arcRadius + with(density) { 18.dp.toPx() }

            // === 3. INDICATOR ARC ===
            // 40° sweep so the arc is clearly visible (not flat). Combined
            // with arcRadius = 0.55w, the vertical span is ~17% of screen
            // height — matching the Songs→Artists range on the nav rail.
            val arcSweepDeg = 40f
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
            // Scroll DOWN → wheel rotates ANTICLOCKWISE (balls move up on arc).
            // Scroll UP → wheel rotates CLOCKWISE (balls move down on arc).
            val rotationItems = scrollOffset.value / pxPerItem

            // Playfair Display Italic — premium high-contrast editorial serif.
            // ALL ITEMS SAME SIZE — active item distinguished only by color
            // (accentColor) and opacity (100%), not by size.
            val activeFontSp = 24f
            val inactiveFontSp = 24f

            // ±17.5° visible window at 6° step = ~3 items each side.
            // Tighter window matches the smaller arc sweep.
            val visibleSpan = 3

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

                // === 5. STYLING CURVES — per user spec ===
                // Opacity at each integer offset position (rest state):
                //   Position 0 (apex):   100%
                //   Position 1:           70%
                //   Position 2:           45%
                //   Position 3:           25%
                //   Position 4:            7%
                //   Position 5:            2%
                //   Position 6+:           0% (invisible)
                // Between positions, opacity interpolates smoothly.
                val alpha = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1.00f, 0.70f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.70f, 0.45f, (absOffset - 1.5f))
                    absOffset < 3.5f -> lerp(0.45f, 0.25f, (absOffset - 2.5f))
                    absOffset < 4.5f -> lerp(0.25f, 0.07f, (absOffset - 3.5f))
                    absOffset < 5.5f -> lerp(0.07f, 0.02f, (absOffset - 4.5f))
                    else -> lerp(0.02f, 0f, (absOffset - 5.5f).coerceIn(0f, 1f))
                }.coerceIn(0f, 1f)

                // Scale: 1.0 at apex → 0.70 → 0.55 → 0.45 (smooth shrink)
                val scale = when {
                    absOffset < 0.5f -> 1f
                    absOffset < 1.5f -> lerp(1f, 0.70f, (absOffset - 0.5f))
                    absOffset < 2.5f -> lerp(0.70f, 0.55f, (absOffset - 1.5f))
                    else -> lerp(0.55f, 0.45f, (absOffset - 2.5f).coerceIn(0f, 1f))
                }

                // Interpolated font size (active is ~2.6× larger than inactive).
                // Note: actual fontSp is computed in the auto-fit section below
                // (may be shrunk to fit available width).

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

                // Color: ACTIVE = accent color (default #F4B400, or auto-detected
                // from currently-playing song's album art); INACTIVE = white.
                val textColor = if (isActive) accentColor else Color.White

                // === 5b. BALL MARKER on the second arc (textRadius orbit) ===
                // One small filled circle per playlist, positioned at the
                // text anchor point on the second arc. Moves with the wheel.
                // ACTIVE ball = accent color; INACTIVE balls = white.
                // Diameter = 6dp (was 8dp) — smaller for two-wheel layout.
                val ballRadiusPx = with(density) { 3.dp.toPx() }
                val ballColor = if (isActive) accentColor else Color.White
                drawCircle(
                    color = ballColor,
                    radius = ballRadiusPx,
                    center = Offset(itemX, itemY),
                    alpha = alpha
                )

                // === 6. MEASURE TEXT (then auto-fit if too wide) ===
                //
                // Auto-fit: like the SleepTimerCapsule fits its container, the
                // main playlist text auto-shrinks to fit available width.
                // We measure at the target size, and if the text would extend
                // past the right edge of the screen, we scale the font size
                // down proportionally so it just fits.
                //
                // Available width = (screen width - textStartX - right margin).
                // Text starts at (ball edge + gap) and extends outward.
                // ballRadiusPx already defined above (ball marker section).
                val gapAfterBallPx = with(density) { 4.dp.toPx() }
                // Increased right margin (was 12dp → 100dp) to reserve space
                // on the right side for the upcoming second (smaller) wheel.
                val rightMarginPx = with(density) { 100.dp.toPx() }

                // Max available width for text = from (ball edge + gap) to right screen edge.
                val textStartX = itemX + ballRadiusPx + gapAfterBallPx
                val maxTextWidth = (w - rightMarginPx - textStartX).coerceAtLeast(50f)

                // First measure at target size
                var fontSp = lerp(activeFontSp, inactiveFontSp, (1f - scale).coerceIn(0f, 1f))
                var textLayout = textMeasurer.measure(
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
                        maxWidth = Int.MAX_VALUE,
                        maxHeight = Int.MAX_VALUE
                    )
                )

                // Auto-fit: if text is wider than available, shrink font size
                // proportionally so it just fits. This is the "sleep timer
                // capsule" behavior — short names stay big, long names shrink.
                val measuredWidth = textLayout.size.width.toFloat()
                if (measuredWidth > maxTextWidth && measuredWidth > 0f) {
                    val shrinkRatio = maxTextWidth / measuredWidth
                    fontSp = (fontSp * shrinkRatio).coerceAtLeast(10f)
                    textLayout = textMeasurer.measure(
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
                            maxWidth = Int.MAX_VALUE,
                            maxHeight = Int.MAX_VALUE
                        )
                    )
                }

                // === 7. TEXT PLACEMENT — IN FRONT OF BALL, PERPENDICULAR TO SLOPE ===
                //
                // Geometry:
                //   - Ball is at (itemX, itemY) on the second arc (textRadius orbit).
                //   - "Perpendicular to slope" = radial direction (perpendicular to
                //     the arc's tangent). Radial direction at angle θ is:
                //         direction = (cos θ, sin θ)
                //   - Text rotation = θ (radial). At apex (θ=0°), text is horizontal.
                //   - Text is placed in FRONT of the ball (outward from pivot),
                //     offset along the radial direction by:
                //         offset = ballRadius + gap + textWidth/2
                //     So the text's left edge starts `ballRadius + gap` past the
                //     ball, and text extends further outward.
                //   - Both ball and text share the same radial line, so text is
                //     "in front of" the ball (perpendicular to slope).
                //   - A new playlist adds a new ball, and its text is automatically
                //     placed in front of that new ball at the same offset.
                //
                // Math:
                //   textCenterX = itemX + (ballRadius + gap + textWidth/2) * cos(θ)
                //   textCenterY = itemY + (ballRadius + gap + textWidth/2) * sin(θ)
                val textW = textLayout.size.width.toFloat()
                val textH = textLayout.size.height.toFloat()
                // gapAfterBallPx already defined above in the measure section

                // Total distance from ball center to text center, along radial
                val textOffsetPx = ballRadiusPx + gapAfterBallPx + textW / 2f
                val textCenterX = itemX + textOffsetPx * cos(itemAngleRad)
                val textCenterY = itemY + textOffsetPx * sin(itemAngleRad)

                // Radial rotation (perpendicular to slope)
                val radialDeg = itemAngleDeg

                drawContext.canvas.save()
                // Translate to text center, rotate radially, draw text centered.
                drawContext.canvas.translate(textCenterX, textCenterY)
                drawContext.canvas.rotate(radialDeg)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(-textW / 2f, -textH / 2f),
                    alpha = alpha
                )
                drawContext.canvas.restore()
            }
        }
    }
}

/** Simple linear interpolation between two floats. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

// =============================================================================
// TAG WHEEL — smaller arc in top-right corner, renders tag balls
// =============================================================================
// Step 2: static rendering only (no scrolling/interaction yet).
//
// Geometry:
//   - Pivot: off-screen RIGHT at (x = +125% screen width, y = 25% screen height)
//   - The visible arc is on the LEFT side of the pivot = the 180° direction
//   - Arc center (apex) is at ~(90% screen width, 25% screen height) = top-right
//   - Smaller radius than the playlist wheel (0.35w vs 0.55w)
//   - Smaller sweep (30° vs 40°)
//
// Visual style matches the playlist wheel:
//   - Same ball size (3dp radius)
//   - Same text font (Playfair Display Italic)
//   - Same opacity curve (100% → 70% → 45% → 25% → 7% → 2% → 0%)
//   - Same arc line (1.5px, 60% white, fading at endpoints)
//
// The first ball is always "All" (shows all playlists). Subsequent balls
// are tag names, sorted alphabetically.
//
// Text extends to the LEFT of each ball (radially outward from the
// right-side pivot, which is leftward on screen).
// =============================================================================

@Composable
private fun TagWheel(
    tags: List<String>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Build the full tag list: "All" at position 0, then sorted tags
    val tagList = remember(tags) { listOf("All") + tags }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // === 1. PIVOT (off-screen, right, upper area) ===
        val pivotX = w * 1.25f
        val pivotY = h * 0.25f

        // === 2. RADII ===
        // Smaller than playlist wheel (0.35w vs 0.55w)
        val arcRadius = w * 0.35f
        val textRadius = arcRadius + with(density) { 16.dp.toPx() }

        // === 3. ARC GEOMETRY ===
        // Arc is centered at 180° (pointing LEFT from the right-side pivot).
        // startAngle = 180 - sweep/2, sweep = 30°
        val arcSweepDeg = 30f
        val arcCenterDeg = 180f
        val arcStartDeg = arcCenterDeg - arcSweepDeg / 2f

        // --- Draw the fading arc line (same helper pattern as playlist wheel) ---
        val arcSegments = 30
        val fadeRange = 0.35f
        val arcStrokePx = with(density) { 1.5.dp.toPx() }
        for (i in 0 until arcSegments) {
            val segStart = i / arcSegments.toFloat()
            val segEnd = (i + 1) / arcSegments.toFloat()
            val distFromEndpoint = minOf(segStart, 1f - segStart)
            val segAlpha = if (distFromEndpoint > fadeRange) {
                0.6f
            } else {
                0.6f * (distFromEndpoint / fadeRange)
            }
            if (segAlpha <= 0.01f) continue
            drawArc(
                color = Color.White.copy(alpha = segAlpha),
                startAngle = arcStartDeg + segStart * arcSweepDeg,
                sweepAngle = (segEnd - segStart) * arcSweepDeg,
                useCenter = false,
                topLeft = Offset(pivotX - arcRadius, pivotY - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = arcStrokePx)
            )
        }

        // === 4. TAG BALLS + TEXT ===
        // Angular spacing between items (same as playlist wheel: 6°)
        val angleStepDeg = 6f
        val fontSp = 18f  // smaller than playlist wheel (24sp) — it's the secondary wheel
        val maxVisible = 4  // show up to 4 items: "All" + 3 tags

        val count = minOf(tagList.size, maxVisible)
        for (i in 0 until count) {
            val tagName = tagList[i]
            // Offset from center: 0 = apex, positive = below (clockwise from 180°)
            val offset = i.toFloat()

            val absOffset = abs(offset)

            // Angular position: 180° at apex, positive offset goes downward
            val itemAngleDeg = arcCenterDeg + offset * angleStepDeg
            val itemAngleRad = (itemAngleDeg * PI / 180f).toFloat()

            // Position on the text orbit (Cartesian from pivot)
            val itemX = pivotX + textRadius * cos(itemAngleRad)
            val itemY = pivotY + textRadius * sin(itemAngleRad)

            // Skip if off-screen
            if (itemX < -200f || itemX > w + 200f) continue

            // === OPACITY (same curve as playlist wheel) ===
            val alpha = when {
                absOffset < 0.5f -> 1f
                absOffset < 1.5f -> lerp(1.00f, 0.70f, (absOffset - 0.5f))
                absOffset < 2.5f -> lerp(0.70f, 0.45f, (absOffset - 1.5f))
                absOffset < 3.5f -> lerp(0.45f, 0.25f, (absOffset - 2.5f))
                else -> lerp(0.25f, 0f, (absOffset - 3.5f).coerceIn(0f, 1f))
            }.coerceIn(0f, 1f)

            // === BALL ===
            val ballRadiusPx = with(density) { 3.dp.toPx() }
            // "All" ball = accent color; tag balls = white
            val ballColor = if (i == 0) accentColor else Color.White
            drawCircle(
                color = ballColor,
                radius = ballRadiusPx,
                center = Offset(itemX, itemY),
                alpha = alpha
            )

            // === TEXT (positioned to the LEFT of the ball, radially outward) ===
            // Text center = ball position + (ballRadius + gap + textWidth/2) * direction
            // direction = (cos θ, sin θ) — for 180° this is (-1, 0) = leftward
            val gapAfterBallPx = with(density) { 4.dp.toPx() }
            val textLayout = textMeasurer.measure(
                text = AnnotatedString(tagName),
                style = TextStyle(
                    color = Color.White,
                    fontSize = fontSp.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = com.rajatxo.coral.ui.theme.PlayfairItalicFamily
                ),
                overflow = TextOverflow.Visible,
                maxLines = 1,
                softWrap = false,
                constraints = androidx.compose.ui.unit.Constraints(
                    maxWidth = (w * 0.5f).toInt(),
                    maxHeight = Int.MAX_VALUE
                )
            )

            val textW = textLayout.size.width.toFloat()
            val textH = textLayout.size.height.toFloat()
            val textOffsetPx = ballRadiusPx + gapAfterBallPx + textW / 2f
            val textCenterX = itemX + textOffsetPx * cos(itemAngleRad)
            val textCenterY = itemY + textOffsetPx * sin(itemAngleRad)

            // Radial rotation (same as playlist wheel — text is perpendicular to slope)
            val radialDeg = itemAngleDeg - 180f  // normalize so apex is horizontal

            drawContext.canvas.save()
            drawContext.canvas.translate(textCenterX, textCenterY)
            drawContext.canvas.rotate(radialDeg)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(-textW / 2f, -textH / 2f),
                alpha = alpha
            )
            drawContext.canvas.restore()
        }
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

// =============================================================================
// SELECTION CAPSULE — glossy pill showing the currently-selected playlist name
// =============================================================================
// Design:
//   - Medium-sized rounded pill (not too long, not too short)
//   - Background = accent color (auto-detected from album art, default #F4B400)
//   - Glossy white stroke border (like the mini player)
//   - Glossy reflection overlay (vertical gradient: white top → transparent →
//     dark bottom)
//   - Text color auto-detected: black on bright colors, white on dark colors
//   - Clickable to open the playlist
// =============================================================================

@Composable
private fun SelectionCapsule(
    playlistName: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    // Auto-detect text color based on luminance (perceived brightness)
    // Formula: luminance = 0.299*R + 0.587*G + 0.114*B
    // If luminance > 0.5, color is "bright" → use black text
    // If luminance <= 0.5, color is "dark" → use white text
    val luminance = 0.299f * accentColor.red +
                    0.587f * accentColor.green +
                    0.114f * accentColor.blue
    val textColor = if (luminance > 0.5f) Color.Black else Color.White

    // Haptic feedback on click (same View.performHapticFeedback approach as
    // the wheel — works reliably on all devices including Realme)
    val view = androidx.compose.ui.platform.LocalView.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .drawWithContent {
                drawContent()
                // Glossy reflection overlay: white at top, transparent in middle,
                // subtle dark at bottom — gives the "glossy pill" look
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.08f)
                        )
                    ),
                    size = size
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // Fire haptic feedback
                    try {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    } catch (_: Exception) { }
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Smooth animated transition when playlist name changes.
        // New name slides in from the right while old name slides out to the left,
        // with a quick fade. Total duration ~180ms — smooth but fast.
        AnimatedContent(
            targetState = playlistName,
            transitionSpec = {
                // Slide horizontally + fade simultaneously
                (slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = androidx.compose.animation.core.tween(180)
                ) + fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(180)
                )) togetherWith (slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = androidx.compose.animation.core.tween(180)
                ) + fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(180)
                ))
            },
            contentAlignment = Alignment.Center,
            label = "playlistNameTransition"
        ) { targetName ->
            Text(
                text = targetName,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
