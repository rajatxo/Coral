package com.rajatxo.coral.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    // Preload the album art via Coil FIRST, then extract palette. This
    // ensures the image is in Coil's cache when AsyncImage renders it,
    // eliminating the "solid color flash" on first-time song load.
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            // Preload into Coil cache (full size, for the blurred bg)
            try {
                coil3.ImageLoader(context).execute(
                    coil3.request.ImageRequest.Builder(context)
                        .data(albumArtUri)
                        .build()
                )
            } catch (_: Exception) { }
            // Now extract palette + the AsyncImage will load instantly
            extractPalette(context, albumArtUri)?.let { palette = it }
        }
    }

    // Smooth crossfade when colors change on song switch (no grey flash).
    val animatedTopColor    by animateColorAsState(palette.primary,   tween(600), label = "top")
    val animatedMidColor   by animateColorAsState(palette.secondary,  tween(600), label = "mid")
    val animatedBottomColor by animateColorAsState(palette.tertiary,  tween(600), label = "bottom")
    val animatedAccentColor by animateColorAsState(palette.accent,    tween(600), label = "accent")

    // Visual crossfade state (broadcast by SimpleCrossfadeController)
    val xfActive by CrossfadeVisualState.isActive.collectAsState()
    val xfProgress by CrossfadeVisualState.progress.collectAsState()
    val xfIncomingArt by CrossfadeVisualState.incomingArtUri.collectAsState()
    // Incoming title/artist — used to sync text transition with cover blend
    val xfIncomingTitle by CrossfadeVisualState.incomingTitle.collectAsState()
    val xfIncomingArtist by CrossfadeVisualState.incomingArtist.collectAsState()
    // Hold incoming layers visible after crossfade ends to give albumArtUri
    // time to catch up. During hold: outgoing=0 (hidden), incoming=1 (full).
    // After hold: both released — albumArtUri should have updated by now.
    // 800ms is enough for the MediaController transition to fire.
    var holdAfterEnd by remember { mutableStateOf(false) }
    LaunchedEffect(xfActive) {
        if (!xfActive) {
            holdAfterEnd = true
            delay(800L)
            holdAfterEnd = false
        } else {
            holdAfterEnd = false
        }
    }
    val outAlpha = when {
        xfActive -> kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
        holdAfterEnd -> 0f  // hide outgoing during hold so old art doesn't flash
        else -> 1f
    }
    val showIncoming = xfIncomingArt != null && (xfActive || holdAfterEnd)
    val inAlpha = when {
        xfActive -> kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
        holdAfterEnd -> 1f  // keep incoming at full during hold
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
    // Transparent background — no solid color flash. The blurred album art
    // fills the screen as soon as it loads (it's already in Coil's cache from
    // the mini player). Using any solid color here causes a flash before the
    // art renders.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

        // (5) Content column — centered below the 3-dot indicator
        //     Song name sits just below the dots, then artist, then the
        //     Timeline capsule (animated progress bar), then Lyrics capsule,
        //     then the triple-circle control pod.
        var timelinePillWidthPx by remember { mutableFloatStateOf(1f) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .offset(y = center + 18.dp)
                .padding(horizontal = 28.dp)
        ) {
            // Song title (centered, below the 3 dots) — synced with cover blend
            // During crossfade: outgoing title fades out (outAlpha) + incoming
            // title fades in (inAlpha), perfectly synced with the cover blend.
            // When not crossfading: just show the current title at full opacity.
            Box(modifier = Modifier.fillMaxWidth()) {
                if (xfActive && xfIncomingTitle.isNotEmpty()) {
                    // Outgoing title (fades out)
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = outAlpha },
                        textAlign = TextAlign.Center
                    )
                    // Incoming title (fades in)
                    Text(
                        text = xfIncomingTitle,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = inAlpha },
                        textAlign = TextAlign.Center
                    )
                } else {
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
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            // Artist name (centered, dimmer, NO shadow) — synced with cover blend
            Box(modifier = Modifier.fillMaxWidth()) {
                if (xfActive && xfIncomingArtist.isNotEmpty()) {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = outAlpha }
                    )
                    Text(
                        text = xfIncomingArtist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = inAlpha }
                    )
                } else {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── TIMELINE CAPSULE (reveal glass effect) ─────────────────
            // Think of it like glass covered by dry detergent bubbles:
            // - The UNFILLED portion (right) is covered by a faded/opaque
            //   white overlay — the glass is obscured (dulled).
            // - The FILLED portion (left) has NO overlay — the clear glass
            //   morphism shows through (vibrant, alive).
            // As you slide, the faded overlay retreats and the clear glass
            // is "revealed". "Timeline" text at extreme left, time on right.
            // Draggable to seek.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .onSizeChanged { timelinePillWidthPx = it.width.toFloat() }
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
                                if (durationMs > 0 && timelinePillWidthPx > 0) {
                                    val frac = (change.position.x / timelinePillWidthPx).coerceIn(0f, 1f)
                                    dragFraction = frac
                                }
                            }
                        )
                    }
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { RoundedCornerShape(26.dp) },
                        effects = {
                            vibrancy()
                            colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                            blur(12f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                    )
            ) {
                // ── Layer 1: Faded overlay on the UNFILLED portion (right) ──
                // This is the "detergent bubbles" — a semi-opaque white layer
                // that covers the right side, making the glass look dull/faded.
                // Rounded left edge so the boundary looks smooth as it retreats.
                if (displayProgress < 0.999f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(1f - displayProgress)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }

                // ── Layer 2: Content — "Timeline" at extreme left, time right ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Extreme left: "Timeline" label
                    Text(
                        text = "Timeline",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Medium
                    )
                    // Right: current time / total time
                    Text(
                        text = "${formatTime((displayProgress * durationMs).toLong())} / ${formatTime(durationMs)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Lyrics glass pill (tappable → lyrics sheet) ──────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { RoundedCornerShape(26.dp) },
                        effects = {
                            vibrancy()
                            colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                            blur(12f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showLyrics = true }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CoralIcons.Music,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lyrics",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) { i ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.3f + i * 0.12f))
                            )
                        }
                    }
                    Text(
                        text = "Open",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Triple-circle control pod ─────────────────────────────
            // Glass circle | White play circle | Glass circle
            // Small gap between each (removed overlap for breathing room).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left — Rewind (previous)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                                blur(12f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                        )
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
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Center — Play/Pause (solid white, slightly larger)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .zIndex(1f)
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
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Right — FastForward (next)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                                blur(12f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.25f)) }
                        )
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
                        modifier = Modifier.size(26.dp)
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
