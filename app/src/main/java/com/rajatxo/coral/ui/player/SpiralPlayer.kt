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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.audio.CrossfadeVisualState
import androidx.media3.common.Player
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
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onAddToPlaylist: (Long) -> Unit = {},
    onSongDelete: (Long) -> Unit = {}
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

    // ─── Drag-down-to-dismiss (fast, like back button) ─────────────
    // Drag down → player moves down with your finger, fades to transparent,
    // AND shrinks slightly toward bottom-center so it visually "blends into"
    // the miniplayer position. This is the reverse of swiping UP the
    // miniplayer (which fades + slides up to reveal the player screen).
    // As you drag, the background page (songs list / playlist / quick pic)
    // becomes visible through the fading player.
    // Release past threshold → fast snap down + close.
    // Release before threshold → fast snap back to 0.
    val dismissDragY = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { LocalView.current.rootView.height.toFloat() }
    val dismissThreshold = screenHeightPx * 0.15f  // 15% of screen height = close
    // Drag progress 0 → 1. Drives the alpha fade (no scale, no TransformOrigin).
    val dismissProgress = (dismissDragY.value / dismissThreshold).coerceIn(0f, 1f)

    // ─── Palette (extracted from album art, cached in PaletteCache) ───
    // ★ remember WITHOUT albumArtUri key — so the palette state is NOT
    //   recreated when the song changes. This keeps the PREVIOUS song's
    //   palette visible until the new one is extracted. Without this,
    //   the palette would reset to CoralPalette.Default (dark grey) on
    //   every song change, causing a "darkish blur" flash before the
    //   new palette loads.
    var palette by remember { mutableStateOf(PaletteCache.get(albumArtUri) ?: CoralPalette.Default) }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            // If already cached, use it instantly (no flash)
            val cached = PaletteCache.get(albumArtUri)
            if (cached != null) {
                palette = cached
            } else {
                // Not cached — preload into Coil cache (full size, for the blurred bg)
                try {
                    coil3.ImageLoader(context).execute(
                        coil3.request.ImageRequest.Builder(context)
                            .data(albumArtUri)
                            .build()
                    )
                } catch (_: Exception) { }
                // Extract palette + cache it. The old palette stays visible
                // during extraction — no dark flash.
                extractPalette(context, albumArtUri)?.let {
                    palette = it
                    PaletteCache.put(albumArtUri, it)
                }
            }
        }
    }

    // Smooth crossfade when colors change on song switch (no grey flash).
    val animatedTopColor    by animateColorAsState(palette.primary,   tween(600), label = "top")
    val animatedMidColor   by animateColorAsState(palette.secondary,  tween(600), label = "mid")
    val animatedBottomColor by animateColorAsState(palette.tertiary,  tween(600), label = "bottom")
    val animatedAccentColor by animateColorAsState(palette.accent,    tween(600), label = "accent")

    // ─── Palette style (from Settings → Spiral Palette) ──────────
    // Controls blur radius + mesh overlay type. Default = BLUR (96dp, no overlay).
    val paletteStyle by com.rajatxo.coral.data.prefs.SpiralPaletteStyle.style.collectAsState()

    // Visual crossfade state (broadcast by SimpleCrossfadeController)
    val xfActive by CrossfadeVisualState.isActive.collectAsState()
    val xfProgress by CrossfadeVisualState.progress.collectAsState()
    val xfIncomingArt by CrossfadeVisualState.incomingArtUri.collectAsState()
    // Incoming title/artist — used to sync text transition with cover blend
    val xfIncomingTitle by CrossfadeVisualState.incomingTitle.collectAsState()
    val xfIncomingArtist by CrossfadeVisualState.incomingArtist.collectAsState()

    // ─── Incoming palette (for smooth timeline color blend) ────────
    // Extract the incoming song's palette so we can lerp the timeline
    // accent color during the crossfade. Try PaletteCache first (instant),
    // fall back to async extraction.
    var incomingPalette by remember { mutableStateOf<CoralPalette?>(null) }
    LaunchedEffect(xfIncomingArt) {
        val incomingArt = xfIncomingArt
        if (incomingArt != null) {
            // Try cache first (instant — no extraction needed)
            val cached = PaletteCache.get(incomingArt)
            if (cached != null) {
                incomingPalette = cached
            } else {
                extractPalette(context, incomingArt)?.let {
                    incomingPalette = it
                    PaletteCache.put(incomingArt, it)
                }
            }
        } else {
            incomingPalette = null
        }
    }

    // ─── Cover flash fix ───────────────────────────────────────────
    // Keep outgoing hidden / incoming visible until albumArtUri ACTUALLY
    // matches xfIncomingArt. Derived state — no timer, no race condition.
    val albumArtCaughtUp = xfIncomingArt != null && albumArtUri == xfIncomingArt
    val outAlpha = when {
        xfActive -> kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
        // After crossfade ends: keep outgoing hidden until albumArtUri catches up
        (xfIncomingArt != null && !albumArtCaughtUp) -> 0f
        else -> 1f
    }
    val showIncoming = xfIncomingArt != null && (xfActive || !albumArtCaughtUp)
    val inAlpha = when {
        xfActive -> kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
        // After crossfade ends: keep incoming at full until albumArtUri catches up
        !albumArtCaughtUp -> 1f
        else -> 0f
    }

    // ─── Timeline accent color (blends during crossfade, no flash) ──
    // During crossfade: lerp outgoing accent → incoming accent (xfProgress).
    // After crossfade (hold period): keep using the incoming color (fully
    // transitioned at xfProgress=1) until albumArtUri catches up. This
    // prevents the old color from flashing back when xfActive goes false.
    // Once albumArtUri catches up: use animatedAccentColor (which has had
    // time to animate to the new palette by then).
    val timelineAccentColor = when {
        xfActive && incomingPalette != null ->
            lerpColor(animatedAccentColor, incomingPalette!!.accent, xfProgress)
        // Hold period after crossfade: stay on the incoming color
        (xfIncomingArt != null && !albumArtCaughtUp && incomingPalette != null) ->
            incomingPalette!!.accent
        else -> animatedAccentColor
    }

    // ─── Timeline fade-in on new song ──────────────────────────────
    // When a new song starts (albumArtUri changes), the timeline fades in
    // from 0 → 1 over 500ms. This makes the new color feel like it's
    // smoothly arriving, not popping in.
    var timelineAlpha by remember { mutableStateOf(1f) }
    LaunchedEffect(albumArtUri) {
        timelineAlpha = 0f
        delay(100L)  // brief pause to let the color settle
        // Animate alpha 0 → 1 over 500ms
        val startTime = System.currentTimeMillis()
        val duration = 500L
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            timelineAlpha = progress
            if (progress >= 1f) break
            delay(16L)
        }
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
    var showQueue by remember { mutableStateOf(false) }  // ★ Apple Music queue sheet
    var showHeartPop by remember { mutableStateOf(false) }
    // ★ Menu icon rotation animation (rotates 90° when menu opens)
    val menuRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val menuCoroutineScope = rememberCoroutineScope()
    fun toggleMenu() {
        menuCoroutineScope.launch {
            if (showMoreMenu) {
                showMoreMenu = false
                menuRotation.animateTo(0f)
            } else {
                showMoreMenu = true
                menuRotation.animateTo(90f)
            }
        }
    }

    // ★ Shuffle + Repeat state (synced from MediaController)
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    LaunchedEffect(mediaController) {
        mediaController?.let { controller ->
            controller.shuffleModeEnabled.let { shuffleEnabled = it }
            controller.repeatMode.let { repeatMode = it }
        }
    }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(800); showHeartPop = false }
    }

    // ─── Seek bar state (buttery smooth, no thumb, thickens on drag) ──
    var isDragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 6.dp,  // idle 6dp = same as volume bar
        animationSpec = tween(200),
        label = "trackHeight"
    )
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val progress = if (durationMs > 0)
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    else 0f
    val displayProgress = dragFraction ?: progress

    // ─── Lyrics (1-line synced preview, same as Spiral 2.0) ──
    val lyricsRepository = remember { com.rajatxo.coral.data.lyrics.LyricsRepository(context) }
    var lyricData by remember { mutableStateOf<com.rajatxo.coral.data.lyrics.Lyric?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var embeddedLyrics by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(title, artist) {
        lyricData = null
        embeddedLyrics = null
        if (title.isBlank()) { isLyricsLoading = false; return@LaunchedEffect }
        isLyricsLoading = true
        val fetchDurationMs = durationMs
        val cached = withContext(kotlinx.coroutines.Dispatchers.IO) {
            lyricsRepository.getLyrics(title, artist, albumName, fetchDurationMs)
        }
        if (cached != null) {
            lyricData = cached
            isLyricsLoading = false
            return@LaunchedEffect
        }
        try {
            val fetched = withContext(kotlinx.coroutines.Dispatchers.IO) {
                lyricsRepository.fetchFromNetwork(title, artist, albumName, fetchDurationMs)
            }
            if (fetched != null) lyricData = fetched
        } catch (_: Exception) { }
        isLyricsLoading = false
    }
    val activeLineIndex = if (lyricData != null && lyricData!!.synced && lyricData!!.lines.isNotEmpty()) {
        findActiveLineIndex(lyricData!!.lines, currentPositionMs)
    } else -1
    val lyricLineText = when {
        isLyricsLoading && lyricData == null -> "Loading..."
        lyricData != null && lyricData!!.synced && activeLineIndex >= 0 ->
            lyricData!!.lines[activeLineIndex].text.ifBlank { "♪" }
        lyricData != null && lyricData!!.synced && lyricData!!.lines.isNotEmpty() -> {
            val nearestIndex = activeLineIndex.coerceAtLeast(0)
                .coerceAtMost(lyricData!!.lines.lastIndex)
            lyricData!!.lines[nearestIndex].text.ifBlank { "♪" }
        }
        lyricData != null && !lyricData!!.synced && lyricData!!.lines.isNotEmpty() -> "♪"
        else -> "No Lyrics Available"
    }
    // Build word-by-word annotated string for the lyrics strip
    val lyricStripText = if (lyricData != null && lyricData!!.synced && activeLineIndex >= 0) {
        val activeLine = lyricData!!.lines[activeLineIndex]
        if (activeLine.hasWordSync && activeLine.words != null) {
            androidx.compose.ui.text.buildAnnotatedString {
                activeLine.words.forEach { word ->
                    val isWordActiveOrPast = currentPositionMs >= word.startTime
                    if (isWordActiveOrPast) {
                        withStyle(androidx.compose.ui.text.SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )) { append(word.text) }
                    } else {
                        withStyle(androidx.compose.ui.text.SpanStyle(
                            color = Color.White.copy(alpha = 0.35f)
                        )) { append(word.text) }
                    }
                    append(" ")
                }
            }
        } else {
            androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = Color.White)) {
                    append(lyricLineText)
                }
            }
        }
    } else {
        androidx.compose.ui.text.buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(
                color = Color.White
            )) { append(lyricLineText) }
        }
    }

    // ─── System volume (so the volume slider reflects hardware keys) ─
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    // ★ isDraggingVolume flag — suppresses the ContentObserver during drag
    //   so it doesn't fight with dragVolume. Without this, setStreamVolume
    //   triggers the observer which updates systemVolume, which creates
    //   a feedback loop causing the glitch.
    var isDraggingVolume by remember { mutableStateOf(false) }
    var systemVolume by remember {
        mutableFloatStateOf(
            if (maxVolume > 0) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            else 0f
        )
    }
    var dragVolume by remember { mutableFloatStateOf(-1f) }  // -1 = not dragging
    val displayVolume = if (dragVolume >= 0f) dragVolume else systemVolume
    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // ★ Skip during drag — dragVolume is the source of truth
                if (!isDraggingVolume) {
                    systemVolume = if (maxVolume > 0)
                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    else 0f
                }
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("volume_music_speaker") ?: Settings.System.CONTENT_URI,
            true,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    // ★ Fallback: poll volume every 500ms when NOT dragging.
    //   The ContentObserver doesn't always fire for Bluetooth volume changes,
    //   so this ensures the bar updates even on BT.
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDraggingVolume) {
                val currentVol = if (maxVolume > 0)
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                else 0f
                if (kotlin.math.abs(currentVol - systemVolume) > 0.01f) {
                    systemVolume = currentVol
                }
            }
            delay(500)
        }
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
    // Use the palette's dominant color as the base background. This matches
    // the album art (extracted from it) so there's no jarring flash — the
    // blurred art fills over it seamlessly once it loads.
    // The whole player is wrapped in a vertical drag gesture: drag down to
    // dismiss (fade + translate down + shrink), like the reverse of the
    // miniplayer swipe-up-to-open gesture. As the player fades + shrinks
    // toward bottom-center, the background page (songs list / playlist /
    // quick pic) becomes visible through it — the player visually "blends
    // into" the miniplayer position at the bottom of the screen.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Follow finger: move down with drag.
                translationY = dismissDragY.value
                // Fade the WHOLE player (blurred bg + sharp art + controls)
                // to transparent as you drag — reveals the behind page.
                // No scale, no TransformOrigin — just a clean alpha fade.
                alpha = (1f - dismissProgress).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissDragY.value > dismissThreshold) {
                            // Smooth glide off-screen with FastOutSlowInEasing
                            // for buttery deceleration.
                            coroutineScope.launch {
                                dismissDragY.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 280,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                )
                                onDismiss()
                            }
                        } else {
                            // Smooth spring back to 0 with FastOutSlowInEasing.
                            coroutineScope.launch {
                                dismissDragY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 220,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dismissDragY.animateTo(
                                targetValue = 0f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 220,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            )
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            dismissDragY.snapTo((dismissDragY.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
    ) {
        val center = maxHeight / 2

        // Background layer — wrapped with layerBackdrop so the glass capsule
        // can sample + blur the album cover behind it (liquid glass effect).
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {

        // (1) MeshBackground — renders the blurred (or sharp) album cover
        //     + color mesh overlay based on the selected palette style.
        //     Blur radius + mesh type + color treatment all come from
        //     SpiralPaletteStyle (Settings → Spiral Palette).
        //     12 styles available: BLUR, VIBRANT_MESH, DOMINANT_WASH,
        //     NEON_PULSE, PASTEL_DREAM, MIDNIGHT, SUNSET, OCEAN,
        //     MONOCHROME, RAINBOW_MESH, VINTAGE, AURORA.
        MeshBackground(
            albumArtUri = albumArtUri,
            palette = palette,
            style = paletteStyle,
            modifier = Modifier.graphicsLayer { alpha = outAlpha }
        )

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

        // ★ Bottom black fade gradient — ON THE PLAYER'S BACKGROUND
        //   (behind the controls, on the player UI). Drawn BEFORE the
        //   content column so it sits BEHIND the controls, not on top of
        //   them. Provides readability for the bottom controls area without
        //   affecting the control icons (lyrics / connectivity / queue).
        //   - 400dp tall, anchored to bottom.
        //   - Transparent at the top (where the album cover + colors show).
        //   - Max ~20% black at the very bottom (subtle, not pure black).
        //   - Fades out along with the player during drag-down (because it's
        //     inside the root BoxWithConstraints which has the alpha
        //     graphicsLayer applied).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.3f to Color(0xFF05050A).copy(alpha = 0.05f),
                            0.6f to Color(0xFF05050A).copy(alpha = 0.10f),
                            0.85f to Color(0xFF05050A).copy(alpha = 0.15f),
                            1.0f to Color(0xFF05050A).copy(alpha = 0.20f)
                        )
                    )
                )
        )

        // (5) Content column — Apple Music / BitChord style controls
        //     LEFT-aligned title + artist (with 3-dot menu on RIGHT)
        //     ThinSlider seek bar (no glass, thickens on drag)
        //     Volume bar (speaker icons + ThinSlider)
        //     Transport: prev · play/pause · next (no glass)
        //     Bottom row: lyrics · shuffle · loop · queue
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .offset(y = center + 18.dp)
                .padding(horizontal = 28.dp)
        ) {
            // ─── Song title + 3-dot menu (LEFT-aligned title, menu RIGHT) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title (LEFT-aligned, takes remaining width)
                Box(modifier = Modifier.weight(1f)) {
                    if (xfActive && xfIncomingTitle.isNotEmpty()) {
                        val titleOutAlpha = kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                        val titleInAlpha = kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = titleOutAlpha
                                renderEffect = blurRenderEffect(8f * (1f - titleOutAlpha))
                            }
                        )
                        Text(
                            text = xfIncomingTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = titleInAlpha
                                renderEffect = blurRenderEffect(8f * (1f - titleInAlpha))
                            }
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
                            textAlign = TextAlign.Start
                        )
                    }
                }
                // 3-dot menu (RIGHT side)
                Icon(
                    imageVector = CoralIcons.Ellipsis,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = menuRotation.value }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { toggleMenu() }
                )
            }
            Spacer(modifier = Modifier.height(3.dp))

            // ─── Artist name (LEFT-aligned, same as Spiral 2.0) ──
            Box(modifier = Modifier.fillMaxWidth()) {
                if (xfActive && xfIncomingArtist.isNotEmpty()) {
                    val artistOutAlpha = kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    val artistInAlpha = kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 17.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = artistOutAlpha
                            renderEffect = blurRenderEffect(6f * (1f - artistOutAlpha))
                        }
                    )
                    Text(
                        text = xfIncomingArtist,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 17.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = artistInAlpha
                            renderEffect = blurRenderEffect(6f * (1f - artistInAlpha))
                        }
                    )
                } else {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 17.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        style = TextStyle(shadow = textShadow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─── Gap between artist name and lyrics strip (~16dp) ──
            Spacer(modifier = Modifier.height(17.dp))

            // ─── Lyrics strip (1-line synced, LEFT-aligned, marquee) ──
            // ★ FIXED HEIGHT — the Crossfade is wrapped in a Box with a
            //   fixed height (28dp = 19sp text line height) so that when
            //   the text changes from "Loading..." to actual lyrics (which
            //   may have different glyph heights for Japanese/Russian
            //   characters), the controls below DON'T shift up or down.
            //   Everything stays in its exact position.
            //   Tap to open the full lyrics page.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)  // fixed height = 19sp text + padding
            ) {
                Crossfade(
                    targetState = lyricStripText,
                    animationSpec = tween(durationMillis = 400),
                    label = "lyricsLineFade",
                    modifier = Modifier.fillMaxSize()
                ) { fadedText ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showLyrics = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(
                                    initialDelayMillis = 1_200,
                                    velocity = 40.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fadedText,
                                fontSize = 19.sp,
                                fontFamily = CalSansFamily,
                                maxLines = 1,
                                style = TextStyle(shadow = textShadow)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = CoralIcons.ChevronRightThick,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ─── Gap between lyrics and seek bar (~2dp — closer) ──
            Spacer(modifier = Modifier.height(2.dp))

            // ─── Seek bar (~5dp thick, rounded pill, thickens on drag) ──
            // Same as Spiral 2.0: Box-based, trackHeight animation.
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
                // Track (dim white)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(trackHeight / 2))
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                // Progress (bright white)
                // ★ Ensure the progress Box has a minimum width equal to
                //   trackHeight so the left rounded corner always shows —
                //   even at 0-1% progress (start of song). Without this,
                //   the fill is too narrow for the corner radius to render,
                //   making the left edge look like a sharp 90° angle.
                Box(
                    modifier = Modifier
                        .widthIn(min = trackHeight)
                        .fillMaxWidth(displayProgress.coerceAtLeast(0f))
                        .height(trackHeight)
                        .clip(RoundedCornerShape(trackHeight / 2))
                        .align(Alignment.CenterStart)
                        .background(Color.White)
                )
            }

            // Time labels (~0:32 / -2:33, small gray, left and right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime((displayProgress * durationMs).toLong()),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = CalSansFamily
                )
                Text(
                    text = "-" + formatTime(((1f - displayProgress) * durationMs).toLong()),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = CalSansFamily
                )
            }

            // ─── Gap between timestamps and transport (~32dp) ──
            Spacer(modifier = Modifier.height(15.dp))

            // ─── Transport: prev · play/pause · next (PLAIN icons, no circles) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                Icon(
                    imageVector = CoralIcons.SkipPrev,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = false)
                        ) {
                            onPrevClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                )
                Spacer(modifier = Modifier.width(35.dp))
                // Play/Pause (plain icon, larger, no circle)
                Icon(
                    imageVector = if (isPlaying) CoralIcons.PauseLucide else CoralIcons.PlayLucide,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = false)
                        ) {
                            onPlayPauseClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                )
                Spacer(modifier = Modifier.width(35.dp))
                // Next
                Icon(
                    imageVector = CoralIcons.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = false)
                        ) {
                            onNextClick()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                )
            }

            // ─── Gap between transport and volume bar (~28dp) ──
            Spacer(modifier = Modifier.height(16.dp))

            // ─── Volume bar (speaker icons + Box-based slider, same as timeline) ──
            // ★ Uses the SAME Box-based approach as the seek bar (not ThinSlider)
            //   for buttery smooth dragging. trackHeight animation thickens on drag.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoralIcons.VolumeLow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                // Box-based volume slider (same pattern as seek bar)
                var volumeDragging by remember { mutableStateOf(false) }
                val volTrackHeight by animateDpAsState(
                    targetValue = if (volumeDragging) 10.dp else 6.dp,
                    animationSpec = tween(200),
                    label = "volTrackHeight"
                )
                var volBarWidthPx by remember { mutableFloatStateOf(1f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .onSizeChanged { volBarWidthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isDraggingVolume = true
                                volumeDragging = true
                                down.consume()
                                if (volBarWidthPx > 0) {
                                    val frac = (down.position.x / volBarWidthPx).coerceIn(0f, 1f)
                                    dragVolume = frac
                                    val newVol = (frac * maxVolume).toInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed) {
                                        pointer.consume()
                                        break
                                    }
                                if (volBarWidthPx > 0) {
                                        val frac = (pointer.position.x / volBarWidthPx).coerceIn(0f, 1f)
                                        dragVolume = frac
                                        val newVol = (frac * maxVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    }
                                    pointer.consume()
                                }
                                volumeDragging = false
                                isDraggingVolume = false
                                // Sync systemVolume to the final drag value
                                systemVolume = dragVolume
                                dragVolume = -1f
                            }
                        }
                ) {
                    // Track (dim white)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(volTrackHeight)
                            .clip(RoundedCornerShape(volTrackHeight / 2))
                            .align(Alignment.CenterStart)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    // Progress (bright white)
                    Box(
                        modifier = Modifier
                            .widthIn(min = volTrackHeight)
                            .fillMaxWidth(displayVolume.coerceIn(0f, 1f))
                            .height(volTrackHeight)
                            .clip(RoundedCornerShape(volTrackHeight / 2))
                            .align(Alignment.CenterStart)
                            .background(Color.White)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = CoralIcons.VolumeHigh,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // ─── Gap between volume bar and bottom row (VERTICAL — increased) ──
            Spacer(modifier = Modifier.height(40.dp))

            // ─── Bottom row: lyrics (left) · connectivity (center) · queue (right) ──
            // Horizontal spacing unchanged — SpaceBetween with 20dp padding.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lyrics (left)
                Icon(
                    imageVector = CoralIcons.MessageSquareQuote,
                    contentDescription = "Lyrics",
                    tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLyrics = true }
                )
                // Connectivity / Bluetooth (center)
                Icon(
                    imageVector = CoralIcons.Radio,
                    contentDescription = "Connectivity",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // TODO: open audio output / bluetooth picker
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                )
                // Queue (right)
                Icon(
                    imageVector = CoralIcons.Logs,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showQueue = true }
                )
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

        // ─── Queue page (simple slide up from bottom) ──────────────
        val queueOffset = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(showQueue) {
            queueOffset.animateTo(
                targetValue = if (showQueue) 0f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )
        }
        if (showQueue || queueOffset.value < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = size.height * queueOffset.value
                        alpha = 1f - queueOffset.value * 0.3f
                    }
            ) {
                com.rajatxo.coral.ui.screens.QueueScreen(
                    mediaController = mediaController,
                    onDismiss = { showQueue = false }
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

/** Find the index of the lyric line that should currently be active. */
private fun findActiveLineIndex(lines: List<com.rajatxo.coral.data.lyrics.LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.lastIndex
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs in 0..positionMs) {
            result = mid
            lo = mid + 1
        } else if (lines[mid].timeMs > positionMs) {
            hi = mid - 1
        } else {
            lo = mid + 1
        }
    }
    return result
}

/** m:ss formatter — used for both elapsed and remaining times. */
private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Linear interpolation between two Colors (for timeline color blend). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

/**
 * Creates a Compose RenderEffect for blurring text during crossfade transitions.
 * Returns null on Android < 12 (API 31) or when blur radius is 0.
 * 0f = no blur (sharp), 20f = heavy blur.
 */
private fun blurRenderEffect(blurRadius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (blurRadius <= 0f) return null
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
    return androidx.compose.ui.graphics.BlurEffect(
        radiusX = blurRadius,
        radiusY = blurRadius
    )
}
