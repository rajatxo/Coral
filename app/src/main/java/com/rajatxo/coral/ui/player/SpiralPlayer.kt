package com.rajatxo.coral.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.audio.CrossfadeVisualState
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.lyrics.LyricsSheet
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay

/**
 * Immersive player — BitChord-style.
 *
 * Layout (top → bottom):
 *   1.  Opaque black base (nothing shows through).
 *   2.  Vertical gradient using the album art's dominant colors
 *       (extracted via Palette). NOT the album art itself — just its colors.
 *   3.  Soft dark overlay at the bottom for text legibility.
 *   4.  Top header: chevron-down · NOW PLAYING · more-vertical.
 *   5.  Square album art — full width, 10dp rounded corners, big drop shadow.
 *   6.  Song title  +  heart  +  queue  (row).
 *   7.  Artist name.
 *   8.  Lyrics strip (♪ … ›) — tap to open full lyrics.
 *   9.  Seek bar with times either side.
 *  10.  Transport: prev · play/pause · next.
 *  11.  Volume slider: speaker-low · track · speaker-high.
 *  12.  Bottom utility: shuffle · repeat-1 · repeat-∞ · queue.
 */
@Composable
fun SpiralPlayer(
    mediaController: MediaController?,
    songId: Long?,
    title: String,
    artist: String,
    albumName: String?,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Subtle text shadow — makes white text readable on bright backgrounds
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(1f, 1f),
        blurRadius = 3f
    )

    // Sound + haptics for the glass capsule swipe (same as TabCapsule nav bar)
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    fun tickHaptic() {
        val hapticsOn = SoundHapticsManager.hapticsEnabled.value
        val soundsOn = SoundHapticsManager.soundsEnabled.value
        val volume = SoundHapticsManager.soundVolume.value / 100f
        if (hapticsOn) {
            try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(tickSoundId, volume, volume, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    // Liquid glass backdrop — captures the background so the glass capsule
    // can sample + blur it in real-time (same API as the nav bar TabCapsule).
    val graphicsLayer = rememberGraphicsLayer()
    val glassBackdrop = rememberLayerBackdrop(graphicsLayer = graphicsLayer) {
        drawContent()
    }

    // Drag state for the glass capsule swipe L/R
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f

    // ─── Palette (extracted from album art) ───────────────────────────
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) { extractPalette(context, albumArtUri)?.let { palette = it } }

    // Smooth crossfade when colors change on song switch (no grey flash).
    val animatedTopColor    by animateColorAsState(palette.primary,   tween(600), label = "top")
    val animatedMidColor   by animateColorAsState(palette.secondary,  tween(600), label = "mid")
    val animatedBottomColor by animateColorAsState(palette.tertiary,  tween(600), label = "bottom")
    val animatedAccentColor by animateColorAsState(palette.accent,    tween(600), label = "accent")

    // Visual crossfade state (broadcast by SimpleCrossfadeController)
    val xfActive by CrossfadeVisualState.isActive.collectAsState()
    val xfProgress by CrossfadeVisualState.progress.collectAsState()
    val xfIncomingArt by CrossfadeVisualState.incomingArtUri.collectAsState()
    val outAlpha = if (xfActive) kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f) else 1f
    // Hold incoming layers for 500ms after crossfade ends to give albumArtUri
    // time to update. Time-bounded to prevent "stuck at one cover" bug.
    var holdAfterEnd by remember { mutableStateOf(false) }
    LaunchedEffect(xfActive) {
        if (!xfActive) {
            holdAfterEnd = true
            delay(500L)
            holdAfterEnd = false
        } else {
            holdAfterEnd = false
        }
    }
    val showIncoming = xfIncomingArt != null && (xfActive || holdAfterEnd)
    val inAlpha = when {
        xfActive -> kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
        holdAfterEnd -> 1f
        else -> 0f
    }

    // Saturation boost for the blurred backgrounds — vivid colors.
    val bgSatFilter = remember {
        val s = 1.45f
        val r = 0.3086f; val g = 0.6094f; val b = 0.0820f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            r + (1 - r) * s, g * (1 - s), b * (1 - s), 0f, 0f,
            r * (1 - s), g + (1 - g) * s, b * (1 - s), 0f, 0f,
            r * (1 - s), g * (1 - s), b + (1 - b) * s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }

    // ─── Playback position polling ────────────────────────────────────
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let {
                    currentPositionMs = it.currentPosition.coerceAtLeast(0L)
                    durationMs = it.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    // ─── Favorites + lyrics sheet + more menu ────────────────────────
    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showLyrics by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(800); showHeartPop = false }
    }

    // ─── Seek bar state (buttery smooth, no thumb, thickens on drag) ──
    var isDragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = tween(200),
        label = "trackHeight"
    )
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = if (durationMs > 0)
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    else 0f
    val displayProgress = dragFraction ?: progress

    // ─── System volume (so the volume slider reflects hardware keys) ─
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableFloatStateOf(
            if (maxVolume > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            else 0f
        )
    }
    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = if (maxVolume > 0)
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                else 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("volume_music_speaker") ?: Settings.System.CONTENT_URI,
            true,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // ─── Root Box: 100% blurred album cover bg + sharp art moved down ───
    // Layout:
    //   (1) BLURRED album cover (100% blur = 96dp radius) fills entire
    //       screen as the background. RenderEffect on Android 12+.
    //   (2) SHARP album art in a SQUARE container (aspectRatio 1f)
    //       anchored TopCenter + offset 24dp down (creates a gap at
    //       the top where the status bar sits on the blurred bg, not on
    //       the sharp art). Has alpha masks at BOTH top (64dp fade) AND
    //       bottom (140dp fade) using graphicsLayer + Offscreen +
    //       drawWithContent + DstIn. Result: sharp in the middle, fading
    //       to transparent at both edges — smoothly revealing the blurred
    //       bg above (status bar area) and below (controls area).
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(animatedBottomColor)) {
        val center = maxHeight / 2

        // Background layer — wrapped with layerBackdrop so the glass capsule
        // can sample + blur the album cover behind it (liquid glass effect).
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {

        // (1) Blurred album cover — fills entire screen as the background.
        //     96dp blur radius = ~100% blur (very heavy, image becomes a
        //     smooth color wash with subtle variations). Modifier.blur
        //     uses RenderEffect on Android 12+ (hardware-accelerated).
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = bgSatFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(96.dp)
                    .graphicsLayer { alpha = outAlpha }
            )
        }

        // Medium-blur bridge layer (32dp blur) — sits between the heavy-blur
        // bg (96dp) and the sharp art (0dp). Has a bell-curve alpha mask
        // that makes it visible only in the transition zone (around the
        // sharp art's bottom edge, ~48% down the screen). This creates a
        // gradual blur: sharp → 32dp → 96dp. The texture change is spread
        // across two stages instead of one, so the transition looks
        // seamless — like one continuous image, not "sharp then blurred".
        if (albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = outAlpha }
                    .drawWithContent {
                        drawContent()
                        // Bell-curve alpha mask via DstIn:
                        //   0-40%  : transparent (sharp art area, medium-blur hidden)
                        //   40-48% : fade in (sharp art fading out, medium-blur fading in)
                        //   48-55% : fully opaque (medium-blur dominates)
                        //   55-85% : fade out (transitioning to heavy-blur)
                        //   85-100%: transparent (heavy-blur dominates)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.40f to Color.Transparent,
                                    0.48f to Color.Black,
                                    0.55f to Color.Black,
                                    0.70f to Color.Black.copy(alpha = 0.4f),
                                    0.85f to Color.Transparent,
                                    1.00f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = bgSatFilter,
                    modifier = Modifier.fillMaxSize().blur(32.dp)
                )
            }
        }

        // (2) Sharp album art — SQUARE container at top, moved down 24dp
        //     (so the status bar sits on the blurred bg, not on the art).
        //     Has alpha masks at BOTH top (64dp) and bottom (140dp) that
        //     fade opaque → transparent using BlendMode.DstIn. Result:
        //     sharp in the middle, fading to transparent at both edges —
        //     smoothly revealing the blurred bg above (status bar area)
        //     and below (controls area).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.TopCenter)
                .offset(y = 24.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = outAlpha }
                .drawWithContent {
                    drawContent()
                    val topFadeHeightPx = 64.dp.toPx()
                    val bottomFadeHeightPx = 140.dp.toPx()
                    val imageHeight = size.height

                    // Top fade: 64dp at the top, transparent → opaque.
                    // DstIn removes content where source is transparent
                    // (top of image) — sharp art's top edge fades out,
                    // revealing the blurred bg behind (status bar area).
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,   // y=0: remove content (top hidden)
                                Color.Black           // y=topFadeHeightPx: keep content
                            ),
                            startY = 0f,
                            endY = topFadeHeightPx
                        ),
                        blendMode = BlendMode.DstIn
                    )

                    // Bottom fade: 140dp at the bottom, opaque → transparent.
                    // DstIn removes content where source is transparent
                    // (bottom of image) — sharp art's bottom edge fades
                    // out, revealing the blurred bg behind (controls area).
                    val bottomFadeStartY = (imageHeight - bottomFadeHeightPx).coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black,         // bottomFadeStartY: keep content
                                Color.Transparent    // imageHeight: remove content
                            ),
                            startY = bottomFadeStartY,
                            endY = imageHeight
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .pointerInput(albumArtUri) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (songId != null) {
                                PlaylistStore.toggleFavorite(songId)
                                showHeartPop = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    )
                }
        ) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // VISUAL CROSSFADE: incoming art mixes in on top of outgoing.
        // Plain alpha crossfade (no diagonal wipe) for true color mixing.
        // Incoming layers stay rendered until albumArtUri catches up.
        if (showIncoming && xfIncomingArt != null) {
            // Incoming blurred bg — plain alpha, no diagonal mask.
            AsyncImage(
                model = xfIncomingArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = bgSatFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(96.dp)
                    .graphicsLayer { alpha = inAlpha }
            )

            // Incoming medium-blur bridge (32dp) with bell-curve mask
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = inAlpha
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.40f to Color.Transparent,
                                    0.48f to Color.Black,
                                    0.55f to Color.Black,
                                    0.70f to Color.Black.copy(alpha = 0.4f),
                                    0.85f to Color.Transparent,
                                    1.00f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = xfIncomingArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = bgSatFilter,
                    modifier = Modifier.fillMaxSize().blur(32.dp)
                )
            }

            // Incoming sharp art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.TopCenter)
                    .offset(y = 24.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = inAlpha
                    }
                    .drawWithContent {
                        drawContent()
                        val topFade = 64.dp.toPx()
                        val bottomFade = 140.dp.toPx()
                        val imgH = size.height
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f, endY = topFade
                            ),
                            blendMode = BlendMode.DstIn
                        )
                        val botStart = (imgH - bottomFade).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = botStart, endY = imgH
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = xfIncomingArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

        }

        } // end layerBackdrop Box

        // (3) Heart pop overlay (double-tap on album art to favorite)
        if (showHeartPop) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = CoralIcons.HeartFilled,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (isFavorite) palette.accent else Color.White
                    ),
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        // (4) 3-dot cover indicator — at EXACT center of screen
        // Each dot highlights like the old segments:
        //   Dot 1: Original cover (default, active)
        //   Dot 2: Custom image
        //   Dot 3: Animated video
        var coverMode by remember { mutableStateOf(0) }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = center - 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Dot 1
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (coverMode == 0) Color.White else Color.White.copy(alpha = 0.2f))
                    .clickable { coverMode = 0 }
            )
            // Dot 2
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (coverMode == 1) Color.White else Color.White.copy(alpha = 0.2f))
                    .clickable { coverMode = 1 }
            )
            // Dot 3
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (coverMode == 2) Color.White else Color.White.copy(alpha = 0.2f))
                    .clickable { coverMode = 2 }
            )
        }

        // (5) Content column — at bottom of screen, below the indicator
        var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .offset(y = center + 1.5.dp + 22.dp)
                .padding(horizontal = 24.dp)
        ) {

                // Song title (centered, below the 3 dots) ──────────────
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = textShadow),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Artist name (centered, 95% white)
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 16.sp,
                    fontFamily = CalSansFamily,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                                // Album name (with music icon, tappable → lyrics) ───────
                if (albumName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showLyrics = true }
                    ) {
                        Image(
                            imageVector = CoralIcons.Music,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = albumName,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Image(
                            imageVector = CoralIcons.ChevronRight,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Straight timeline (same as CoralPlayer — thickens on drag)
                var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .onSizeChanged { seekbarWidthPx = it.width.toFloat() }
                        .pointerInput(durationMs) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    dragFraction?.let { frac ->
                                        if (durationMs > 0) onSeek((frac * durationMs).toLong())
                                    }
                                    dragFraction = null
                                },
                                onDragCancel = { isDragging = false; dragFraction = null },
                                onDrag = { change, _ ->
                                    if (durationMs > 0 && seekbarWidthPx > 0) {
                                        val frac = (change.position.x / seekbarWidthPx).coerceIn(0f, 1f)
                                        dragFraction = frac
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .align(Alignment.CenterStart)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayProgress)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .align(Alignment.CenterStart)
                            .background(Color.White)
                    )
                }

                                // Time labels ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime((displayProgress * durationMs).toLong()),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "-" + formatTime(((1f - displayProgress) * durationMs).toLong()),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transport: Rewind | play/pause circle | FastForward ──────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous — Rewind icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onPrevClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Rewind,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // Play/pause circle (white, centered)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onPlayPauseClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Next — FastForward icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onNextClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.FastForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }



        if (showLyrics) {
            LyricsSheet(
                trackName = title,
                artistName = artist,
                albumName = albumName,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                onDismiss = { showLyrics = false },
                onSeek = onSeek,
                albumArtUri = albumArtUri
            )
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

/** m:ss formatter — used for both elapsed and remaining times. */
private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
