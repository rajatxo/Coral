package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
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
    accentColor: Color = Color(0xFFF4B400),
    backdrop: LayerBackdrop? = null,  // ignored — we create our own local backdrop
    currentSongArt: android.net.Uri? = null  // ★ NEW: for dominant-color background
) {
    val playlists by PlaylistStore.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // --- Wheel/Grid toggle state ---
    var useWheel by remember { mutableStateOf(true) }

    // --- Wheel rotation state (kept for PlaylistWheel callback) ---
    var isRotating by remember { mutableStateOf(false) }
    var centerPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // ★ COLORS:
    //   Background = same as Quick Picks (song's dominant color from existing palette)
    //   Text color = user-selected (default #F5EBD0 cream when no song)
    //   When no song: background = #7F011F (wine red), text = #F5EBD0 (cream)
    val context = androidx.compose.ui.platform.LocalContext.current
    var palette by remember { mutableStateOf(com.rajatxo.coral.util.CoralPalette.Default) }
    androidx.compose.runtime.LaunchedEffect(currentSongArt) {
        if (currentSongArt != null) {
            com.rajatxo.coral.util.PaletteCache.get(currentSongArt)?.let { palette = it }
            com.rajatxo.coral.util.extractPalette(context, currentSongArt)?.let {
                palette = it
                com.rajatxo.coral.util.PaletteCache.put(currentSongArt, it)
            }
        }
    }

    // ★ Background: uses palette.primary (same as Quick Picks), or #7F011F when no song
    val bgTopTarget = if (currentSongArt != null) palette.primary else com.rajatxo.coral.util.DEFAULT_BG_COLOR
    val animatedTop by animateColorAsState(
        targetValue = bgTopTarget.copy(alpha = 0.85f),
        animationSpec = tween(800),
        label = "plBgVT"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),
        animationSpec = tween(800),
        label = "plBgB"
    )

    // ★ Text color: user-selected from palette picker, default #F5EBD0 cream
    //   (Palette picker + prefs will be added next — for now uses default)
    val textColor by remember { mutableStateOf(com.rajatxo.coral.util.DEFAULT_TEXT_COLOR) }
    val animatedAccent by animateColorAsState(
        targetValue = textColor,
        animationSpec = tween(800),
        label = "plAccent"
    )

    // ★ LOCAL kyant backdrop (same pattern as SettingsScreen).
    val bgGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f  to animatedTop,
            0.30f to animatedTop.copy(alpha = 0.28f),
            0.55f to animatedTop.copy(alpha = 0.08f),
            1.0f  to animatedBottom        // blend to black at bottom
        )
    )
    val localGraphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val localBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
        graphicsLayer = localGraphicsLayer
    ) {
        drawContent()
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF05050A))  // darkBase
    ) {
        // ─── Layer 1: Solid gradient background (visible, NOT clickable) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
        )

        // ─── Layer 2: Transparent capture layer (NOT scrollable, NOT clickable)
        //   Has the SAME gradient drawn inside it so layerBackdrop captures it.
        //   Zero interactivity — just exists to be captured by kyant.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .layerBackdrop(localBackdrop)
        )

        // ─── Layer 3: Content (capsules use drawBackdrop to blur Layer 2) ──
        // ═══ TOP ROW: 2 kyant backdrop capsules (New + Grid/Wheel toggle) ═══
        // Replaces the old big capsule. Just 2 small glass capsules at the top.
        // ★ Moved DOWN (vertical padding 16 → 80) so they sit BELOW the top blur
        //   header (which shows the "Playlists" title from the parent CynthiaHomeScreen).
        //   Without this offset, the capsules hide under the blur overlay.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 64.dp)
                .zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ★ "New" capsule — opens the create playlist dialog
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (true) {
                            Modifier.drawBackdrop(
                                backdrop = localBackdrop,
                                shape = { RoundedCornerShape(20.dp) },
                                effects = {
                                    vibrancy()
                                    colorControls(
                                        brightness = 0f,
                                        contrast = 1f,
                                        saturation = 1.1f
                                    )
                                    blur(20f.dp.toPx())
                                }
                            )
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.15f))
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showCreateDialog = true }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = CoralIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "New",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily
                )
            }

            // ★ Grid/Wheel toggle capsule
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (true) {
                            Modifier.drawBackdrop(
                                backdrop = localBackdrop,
                                shape = { RoundedCornerShape(20.dp) },
                                effects = {
                                    vibrancy()
                                    colorControls(
                                        brightness = 0f,
                                        contrast = 1f,
                                        saturation = 1.1f
                                    )
                                    blur(20f.dp.toPx())
                                }
                            )
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.15f))
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { useWheel = !useWheel }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (useWheel) "Grid" else "Wheel",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily
                )
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
                // ═══ WHEEL MODE: Row(vertical labels column + wheel) ═══
                // ★ Recreating the OLD nav rail's exact look (build 62a8b02):
                //   - 48dp wide column on the left
                //   - Vertical text labels (rotated -90°), 16sp, Bold
                //   - Labels: Quick picks, Discover, Songs, Playlists, Artists, Albums, Folders
                //   - 23dp gap between labels, vertically centered
                //   - NOT clickable (user said "just a text, we will work on this later")
                //   - This narrows the wheel's Canvas to screenWidth - 48dp,
                //     restoring the OLD geometry → no jitter
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 72.dp, bottom = 16.dp)
                ) {
                    // ─── Left: 48dp vertical labels column (same as old nav rail) ───
                    val labels = listOf("Quick picks", "Discover", "Songs", "Playlists", "Artists", "Albums", "Folders")
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(23.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        labels.forEach { label ->
                            VerticalRailLabel(label = label)
                        }
                    }

                    // ─── Right: wheel (fills remaining width) ───
                    PlaylistWheel(
                        playlists = playlists,
                        accentColor = animatedAccent,  // ★ vibrant text color
                        onRotationStart = { isRotating = true },
                        onRotationEnd = { isRotating = false },
                        onCenterPlaylistChange = { centerPlaylist = it },
                        onOpenPlaylist = { onPlaylistClick(it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 160.dp, bottom = 16.dp
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
    onCenterPlaylistChange: (Playlist) -> Unit = {},
    onOpenPlaylist: (Playlist) -> Unit = {}
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
    // Angular spacing between adjacent items (degrees). 8° gives ~22 visible
    // items across a 180° window — enough density without crowding the apex.
    val angleStepDeg = 8f

    // Pixels of vertical drag required to advance the wheel by ONE item.
    val pxPerItem = with(density) { 64.dp.toPx() }

    // Scroll offset (in pixels). Each pxPerItem corresponds to angleStepDeg
    // of rotation. Positive = wheel rotates so items move DOWN visually
    // (finger swiped down); negative = items move UP.
    val scrollOffset = remember { Animatable(0f) }

    // Index of the item currently at the apex (selected).
    var lastSnappedIndex by remember { mutableStateOf(0) }

    // --- Selection helper ---
    // ★ DIRECTION FIX: user reports swipe-up shows the WRONG playlist.
    //   Easiest fix: invert the dragAmount in the full-screen drag handler
    //   so swipe-up behaves like swipe-down. indexAtOffset + rotationItems
    //   use the ORIGINAL 62a8b02 sign convention (no negation) so they
    //   stay in sync.
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
    //
    // TWO drag zones:
    // 1. LEFT zone (first arc area) — for left-handed users. Dragging here
    //    rotates the first arc, which in turn rotates the second arc in the
    //    OPPOSITE direction (pully coupling).
    // 2. FULL screen — dragging anywhere rotates the second arc normally.
    //    The first arc automatically follows in the opposite direction.
    Box(
        modifier = modifier
            // LEFT ZONE drag handler — only fires when touch is on the left side
            .pointerInput(playlists.size) {
                val halfScreen = size.width / 2
                var velocityTracker = VelocityTracker()
                var isLeftZoneDrag = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isLeftZoneDrag = offset.x < halfScreen
                        if (isLeftZoneDrag) {
                            velocityTracker = VelocityTracker()
                            onRotationStart()
                        }
                    },
                    onDragEnd = {
                        if (isLeftZoneDrag) {
                            val velocity = velocityTracker.calculateVelocity().y
                            coroutineScope.launch {
                                scrollOffset.animateDecay(
                                    initialVelocity = -velocity * 0.35f,  // NEGATED = opposite
                                    animationSpec = androidx.compose.animation.core.exponentialDecay(
                                        frictionMultiplier = 0.9f
                                    )
                                )
                                val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                                scrollOffset.animateTo(
                                    targetValue = nearest * pxPerItem,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                kotlinx.coroutines.delay(1500L)
                                onRotationEnd()
                            }
                        }
                        isLeftZoneDrag = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (isLeftZoneDrag) {
                            coroutineScope.launch {
                                scrollOffset.snapTo(scrollOffset.value - dragAmount)  // NEGATED = opposite
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val currentIdx = indexAtOffset(scrollOffset.value)
                            if (currentIdx != lastSnappedIndex) {
                                lastSnappedIndex = currentIdx
                                tickHaptic()
                            }
                            change.consume()
                        }
                    }
                )
            }
            // FULL SCREEN drag handler — normal rotation (second arc drives first arc)
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
                            // ★ NO negation — fractionalOffset negation handles the visual flip.
                            //   Drag UP → scrollOffset decreases → below playlist rises to center.
                            //   Drag DOWN → scrollOffset increases → above playlist descends to center.
                            scrollOffset.animateDecay(
                                initialVelocity = velocity * 0.20f,
                                animationSpec = androidx.compose.animation.core.exponentialDecay(
                                    frictionMultiplier = 1.2f
                                )
                            )
                            val nearest = (scrollOffset.value / pxPerItem).roundToInt()
                            scrollOffset.animateTo(
                                targetValue = nearest * pxPerItem,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            kotlinx.coroutines.delay(1500L)
                            onRotationEnd()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        coroutineScope.launch {
                            // ★ NO negation — natural drag direction.
                            //   fractionalOffset negation handles the visual flip,
                            //   so the array index and visual position stay in sync.
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

            // === 1. PIVOT (off-screen, left) ===
            // X = -30% screen width (less off-screen than before, so the
            // imaginary circle is smaller and the arc is MORE CURVED/visible)
            // Y = 60% screen height — centered on "Playlists" nav rail item,
            //    so the arc is symmetric: apex at Playlists, top at Songs,
            //    bottom at Artists.
            val pivotX = w * -0.50f
            val pivotY = h * 0.50f

            // === 2. DUAL CONCENTRIC RADII SYSTEM ===
            // Medium radius (0.55w) + larger sweep (40°) = visible curve
            // that spans from Songs to Artists on the nav rail.
            // Apex lands at ~25% from left edge (near the Playlists nav text).
            //
            // Radius A — Visible arc line (1.5px semi-transparent white).
            val arcRadius = w * 0.65f

            // Radius B — Text orbit. Sits 30dp OUTSIDE the arc line.
            val textRadius = arcRadius + with(density) { 30.dp.toPx() }

            // === 3. INDICATOR ARC ===
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
            val activeFontSp = 42f
            val inactiveFontSp = 16f

            // ±50° visible window at 8° step = ~6 items each side.
            val visibleSpan = 7

            for (offset in -visibleSpan..visibleSpan) {
                // Index in playlist array for this slot
                // ★ THE REAL FIX (after deep thinking):
                //   At rest with [PL1, PL2, PL3]:
                //     - Visual below (offset=+1) should be PL3 (most recent, ready to rise up)
                //     - Visual above (offset=-1) should be PL2
                //   To achieve this: negate offset in the array index calculation.
                //     rawIdx = rotationItems.roundToInt() - offset
                //   At rest (rotationItems=0):
                //     - offset=+1 (below) → rawIdx = 0-1 = -1 → wraps to 2 → PL3 ✓
                //     - offset=-1 (above) → rawIdx = 0-(-1) = 1 → PL2 ✓
                //   Drag UP (rotationItems decreases to -1):
                //     - offset=0 (center) → rawIdx = -1 → PL3 (rises from below) ✓
                //     - offset=+1 (below) → rawIdx = -2 → PL2 ✓
                //     - offset=-1 (above) → rawIdx = 0 → PL1 ✓
                val rawIdx = (rotationItems.roundToInt() - offset)  // NEGATED offset
                val modIdx = ((rawIdx % playlists.size) + playlists.size) % playlists.size
                val playlist = playlists[modIdx]

                // Fractional offset from center (0 = apex). Negative = above.
                // ★ Original 62a8b02 behavior — NO negation.
                //   offset=+1 → fractionalOffset=+1 → positive angle → BELOW center (lower arc)
                //   offset=-1 → fractionalOffset=-1 → negative angle → ABOVE center (upper arc)
                //   This means big arc text moves UP when dragging UP (follows finger).
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

                // === 5a. FIRST ARC BALL (reverse rotation) ===
                // Balls on the first arc (arcRadius) rotate in the OPPOSITE
                // direction of the second arc. When second arc scrolls clockwise,
                // first arc scrolls anticlockwise (and vice versa).
                // This creates the "pully" coupling — like two meshed gears.
                // (Drawing happens AFTER alpha + isActive are computed below.)

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

                // Active = normal text (first letter capital, rest lowercase)
                // Inactive = also normal text (same treatment)
                val isActive = absOffset < 0.5f
                val displayText = playlist.name.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.uppercaseChar().toString() else ch.toString()
                }

                // Color: ACTIVE = accent color (default #F4B400, or auto-detected
                // from currently-playing song's album art); INACTIVE = white.
                val textColor = if (isActive) accentColor else Color.White

                // === 5a. FIRST ARC BALL (reverse rotation) ===
                // Balls on the first arc (arcRadius) rotate in the OPPOSITE
                // direction. Pully coupling — like two meshed gears.
                // ALWAYS WHITE — dynamic accent color is only for the second arc.
                val firstArcFractionalOffset = -fractionalOffset  // opposite to text (pully coupling preserved)
                val firstArcAngleDeg = firstArcFractionalOffset * angleStepDeg
                val firstArcAngleRad = (firstArcAngleDeg * PI / 180f).toFloat()
                val firstArcBallX = pivotX + arcRadius * cos(firstArcAngleRad)
                val firstArcBallY = pivotY + arcRadius * sin(firstArcAngleRad)
                val firstArcBallRadiusPx = with(density) { 3.dp.toPx() }
                // ★ BALL GRADIENT (#3): center ball = accentColor, adjacent balls
                //   = blend of accentColor → white, far balls = white.
                //   Matches the text gradient transition.
                val firstArcBallColor = when {
                    absOffset < 0.5f -> accentColor  // center = accent
                    absOffset < 1.5f -> androidx.compose.ui.graphics.lerp(accentColor, Color.White, (absOffset - 0.5f))  // adjacent = gradient
                    else -> Color.White  // far = white
                }
                drawCircle(
                    color = firstArcBallColor,
                    radius = firstArcBallRadiusPx,
                    center = Offset(firstArcBallX, firstArcBallY),
                    alpha = alpha
                )

                // === 5b. BALL MARKER on the second arc (textRadius orbit) ===
                // One small filled circle per playlist, positioned at the
                // text anchor point on the second arc. Moves with the wheel.
                // ACTIVE ball = accent color; INACTIVE balls = white.
                // Diameter = 8dp, gap between consecutive balls ≈ 29dp.
                // ★ BALL GRADIENT (#3): center = accentColor, adjacent = blend, far = white
                val ballRadiusPx = with(density) { 4.dp.toPx() }
                val ballColor = when {
                    isActive -> accentColor  // center = accent
                    absOffset < 1.5f -> androidx.compose.ui.graphics.lerp(accentColor, Color.White, (absOffset - 0.5f))  // adjacent = gradient
                    else -> Color.White  // far = white
                }
                drawCircle(
                    color = ballColor,
                    radius = ballRadiusPx,
                    center = Offset(itemX, itemY),
                    alpha = alpha
                )

                // === 6. MEASURE TEXT — SHRINK ONLY IF WIDER THAN 173dp ===
                //
                // RULE: No playlist name can exceed 173dp in width.
                // - Long names (wider than 173dp) → font SHRINKS to fit 173dp
                // - Short names (narrower than 173dp) → stay at natural size
                //   (no growing to fill the space)
                //
                // Font choice:
                // - ACTIVE (main) → Cal Sans SemiBold (geometric, structured)
                // - INACTIVE → NyghtSerif Light Italic (elegant, editorial)
                //
                // Text LEFT edge starts at ball + gap (on the second arc).
                // Text RIGHT edge is AT MOST the third arc (185dp from second arc).
                val gapAfterBallPx = with(density) { 6.dp.toPx() }
                val maxTextWidthPx = with(density) { 173.dp.toPx() }

                // Base font size (active=42sp, inactive=16sp, interpolated by scale)
                val baseFontSp = lerp(activeFontSp, inactiveFontSp, (1f - scale).coerceIn(0f, 1f))

                // Font family + weight depends on active/inactive state
                val textFontFamily = if (isActive) {
                    com.rajatxo.coral.ui.theme.CalSansFamily
                } else {
                    com.rajatxo.coral.ui.theme.NyghtSerifFamily
                }
                val textFontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Light
                val textFontStyle = if (isActive) {
                    androidx.compose.ui.text.font.FontStyle.Normal
                } else {
                    androidx.compose.ui.text.font.FontStyle.Italic
                }

                // First measure at base font size
                var fontSp = baseFontSp
                var textLayout = textMeasurer.measure(
                    text = AnnotatedString(displayText),
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSp.sp,
                        fontWeight = textFontWeight,
                        fontStyle = textFontStyle,
                        fontFamily = textFontFamily
                    ),
                    overflow = TextOverflow.Visible,
                    maxLines = 1,
                    softWrap = false,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = Int.MAX_VALUE,
                        maxHeight = Int.MAX_VALUE
                    )
                )

                // SHRINK ONLY: if text is wider than 173dp, scale font down
                // so it fits. Short names stay at their natural size.
                val measuredWidth = textLayout.size.width.toFloat()
                if (measuredWidth > maxTextWidthPx && measuredWidth > 0f) {
                    val shrinkRatio = maxTextWidthPx / measuredWidth
                    fontSp = (baseFontSp * shrinkRatio).coerceAtLeast(8f)
                    textLayout = textMeasurer.measure(
                        text = AnnotatedString(displayText),
                        style = TextStyle(
                            color = textColor,
                            fontSize = fontSp.sp,
                            fontWeight = textFontWeight,
                            fontStyle = textFontStyle,
                            fontFamily = textFontFamily
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

                // === 7. TEXT PLACEMENT — IN FRONT OF BALL ===
                //
                // Text LEFT edge = ball + gap (on second arc)
                // Text center = ball + gap + actualTextWidth/2 (along radial)
                // Text RIGHT edge = at most the third arc (185dp from second arc)
                //
                // Uses the ACTUAL measured text width (not a fixed target),
                // so short names sit closer to the ball and long names
                // extend further (up to the 173dp limit).
                val textW = textLayout.size.width.toFloat()
                val textH = textLayout.size.height.toFloat()

                val textOffsetPx = ballRadiusPx + gapAfterBallPx + textW / 2f
                val textCenterX = itemX + textOffsetPx * cos(itemAngleRad)
                val textCenterY = itemY + textOffsetPx * sin(itemAngleRad)

                // Radial rotation (perpendicular to slope)
                val radialDeg = itemAngleDeg

                // === GRADIENT TEXT for adjacent items ===
                // The two items immediately above/below the main playlist name
                // get a vertical gradient that blends white → accentColor.
                // This smooths the "sudden drop" from accent to white.
                //
                // Top adjacent (fractionalOffset < 0): white (top) → accentColor (bottom)
                // Bottom adjacent (fractionalOffset > 0): accentColor (top) → white (bottom)
                // All other items: null (use solid color from TextStyle)
                val isAdjacent = absOffset >= 0.5f && absOffset < 1.5f
                val textBrush: Brush? = if (isAdjacent) {
                    // Gradient coordinates are in the text layout's local space:
                    // 0 = top of text, textH = bottom of text
                    if (fractionalOffset < 0) {
                        // Upper item: white at top, accent at bottom
                        Brush.verticalGradient(
                            colors = listOf(Color.White, accentColor),
                            startY = 0f,
                            endY = textH
                        )
                    } else {
                        // Lower item: accent at top, white at bottom
                        Brush.verticalGradient(
                            colors = listOf(accentColor, Color.White),
                            startY = 0f,
                            endY = textH
                        )
                    }
                } else {
                    null
                }

                drawContext.canvas.save()
                // Translate to text center, rotate radially, draw text centered.
                drawContext.canvas.translate(textCenterX, textCenterY)
                drawContext.canvas.rotate(radialDeg)
                if (textBrush != null) {
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(-textW / 2f, -textH / 2f),
                        alpha = alpha,
                        brush = textBrush
                    )
                } else {
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(-textW / 2f, -textH / 2f),
                        alpha = alpha
                    )
                }
                drawContext.canvas.restore()
            }
        }

        // ★ #1 TAP ONLY THE CENTER TEXT TO OPEN:
        //   The overlay covers ONLY the center area (where the main text sits),
        //   NOT the whole screen. The center text is at the apex of the arc,
        //   which is at approximately 15% from left + 50% from top.
        //   We use align(Center) + a constrained size so only taps near the
        //   center text trigger the open.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(playlists.size) {
                    detectTapGestures(
                        onTap = { offset ->
                            // ★ Only open if tap is near the vertical center
                            //   (within 80dp of the center Y). This prevents
                            //   taps on the top/bottom arcs from opening.
                            val canvasHeight = size.height
                            val centerY = canvasHeight / 2f
                            val tapY = offset.y
                            val distFromCenter = kotlin.math.abs(tapY - centerY)
                            val maxDist = with(density) { 80.dp.toPx() }
                            if (distFromCenter < maxDist) {
                                val centerIdx = indexAtOffset(scrollOffset.value)
                                val centerPl = playlists.getOrNull(centerIdx)
                                if (centerPl != null) {
                                    onOpenPlaylist(centerPl)
                                }
                            }
                        }
                    )
                }
        )
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
    val minChars = 5
    val isValid = name.trim().length >= minChars
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
                isError = name.isNotEmpty() && !isValid,
                // ★ CRITICAL: Set explicit text color to white. Material3's
                //   OutlinedTextField defaults to onSurfaceVariant (dark gray)
                //   which is invisible against CoralColors.SurfaceVariant
                //   (#1A1A1A — near black). Without this, the user types but
                //   can't see what they're typing — even though the playlist
                //   gets created with the typed name when they tap Create.
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    cursorColor = Color.White,
                    focusedBorderColor = CoralColors.Coral,
                    unfocusedBorderColor = Color(0xFF888888),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color(0xFF888888)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (name.isNotEmpty() && !isValid) {
                Text(
                    text = "At least $minChars characters required.",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { if (isValid) onCreate(name) }
            ) {
                Text(
                    "Create",
                    color = if (isValid) CoralColors.Coral else Color(0xFF666666),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════
// VERTICAL RAIL LABEL — exact copy of old CoralNavRail's RailLabel (build 62a8b02)
// ═══════════════════════════════════════════════════════════════════════
// Draws a vertical (rotated -90°) text label, 16sp Bold white.
// NOT clickable (just visual — user wants the look, not functionality yet).
// Same Canvas + TextMeasurer + rotate approach as the old nav rail, so the
// text rendering is identical to the old build (no jitter from Compose's
// text layout system).
@Composable
private fun VerticalRailLabel(
    label: String
) {
    val textMeasurer = rememberTextMeasurer()
    val layoutResult = remember(label) {
        textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = androidx.compose.ui.unit.Constraints(
                minWidth = 0,
                minHeight = 0,
                maxWidth = Int.MAX_VALUE,
                maxHeight = Int.MAX_VALUE
            )
        )
    }

    val density = LocalDensity.current
    val textWidthDp = with(density) { layoutResult.size.width.toDp() }
    val textHeightPx = layoutResult.size.height.toFloat()

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(textWidthDp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val textWidthPx = layoutResult.size.width.toFloat()
            val textHeight = textHeightPx

            // Rotate the canvas -90° around its center, then draw the text centered.
            rotate(degrees = -90f, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)) {
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(
                        x = (canvasWidth - textWidthPx) / 2f,
                        y = (canvasHeight - textHeight) / 2f
                    )
                )
            }
        }
    }
}
