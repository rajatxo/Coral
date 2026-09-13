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
fun CoralPlayer(
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
    Box(modifier = Modifier.fillMaxSize().background(animatedBottomColor)) {

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
                modifier = Modifier
                    .fillMaxSize()
                    .blur(96.dp)
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
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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

        // Black gradient overlay at the bottom — darkens the lower
        // portion of the screen for text legibility (white controls
        // text needs contrast against the blurred album cover bg).
        // Transparent in the top 50% (sharp art area), gradually
        // darkens to ~92% black at the very bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.30f),
                            0.75f to Color.Black.copy(alpha = 0.65f),
                            0.90f to Color.Black.copy(alpha = 0.85f),
                            1.00f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

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

        // (4) Main content column — header overlays the art at top,
        //     controls sit on the solid color at bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.weight(0.75f))

            // ── Bottom controls column ────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {

                // (a) Song title + artist (centered) ───────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // (b) Lyrics strip — tap to open full lyrics ──────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLyrics = true }
                        .padding(vertical = 4.dp)
                ) {
                    Image(
                        imageVector = CoralIcons.Music,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (albumName.isNullOrBlank()) "Tap for lyrics" else albumName!!,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.weight(1f)
                    )
                    Image(
                        imageVector = CoralIcons.ChevronRight,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // (c) Seek bar ─────────────────────────────────────────
                val progress = if (durationMs > 0)
                    (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                else 0f
                var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
                // Drag state — when holding/dragging the timeline, the track
                // gets thicker (6dp → 12dp). Animates back to 6dp on release.
                var isDragging by remember { mutableStateOf(false) }
                val trackHeight by animateDpAsState(
                    targetValue = if (isDragging) 12.dp else 6.dp,
                    animationSpec = tween(200),
                    label = "trackHeight"
                )
                // BUTTERY SMOOTH SCRUBBING: during drag, the visual uses
                // dragFraction (local state, immediate — no media-seek lag).
                // onSeek is only called on drag END (one seek, not per-frame).
                // This eliminates the lag from per-frame media seeks.
                var dragFraction by remember { mutableStateOf<Float?>(null) }
                val displayProgress = dragFraction ?: progress
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
                                        if (durationMs > 0) {
                                            onSeek((frac * durationMs).toLong())
                                        }
                                    }
                                    dragFraction = null
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragFraction = null
                                },
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
                            .background(Color.White.copy(alpha = 0.25f))
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime((displayProgress * durationMs).toLong()),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        style = TextStyle(shadow = textShadow)
                    )
                    Text(
                        text = "-" + formatTime(((1f - displayProgress) * durationMs).toLong()),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        style = TextStyle(shadow = textShadow)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // (d) Transport — Rewind (prev) · glossy capsule (play/pause) · FastForward (next)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous — Rewind icon (two left-pointing triangles)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Glossy capsule — play/pause icon + text
                    // White pill with subtle vertical gradient (glossy effect).
                    // Inside: filled Play/PauseLucide icon + "Play"/"Pause" text.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                onPlayPauseClick()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            .padding(horizontal = 28.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontFamily = CalSansFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // Next — FastForward icon (two right-pointing triangles)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Glass capsule — liquid glass with swipe L/R to change songs.
                // Uses kyant backdrop library (same as TabCapsule nav bar) for
                // REAL real-time backdrop blur. Samples the album cover behind
                // it and applies AGSL blur. Swipe L -> next, R -> prev, with
                // haptics + tick sound.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = 0.05f,
                                    contrast = 1f,
                                    saturation = 1.5f
                                )
                                blur(12f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = 0.25f))
                            }
                        )
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = { dragAccumulator = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator < -dragThreshold) {
                                        onNextClick()
                                        tickHaptic()
                                        dragAccumulator = 0f
                                    } else if (dragAccumulator > dragThreshold) {
                                        onPrevClick()
                                        tickHaptic()
                                        dragAccumulator = 0f
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = CoralIcons.ChevronLeft,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Swipe",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily,
                            style = TextStyle(shadow = textShadow)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = CoralIcons.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.25f))
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
                onSeek = onSeek
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
