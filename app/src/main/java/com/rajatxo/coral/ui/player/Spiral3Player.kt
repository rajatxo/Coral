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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
fun Spiral3Player(
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

    // ─── Drag-down-to-dismiss (fast, like back button) ─────────────
    // Drag down → player moves down with your finger.
    // As you drag, the background fades to TRANSPARENT so the songs list
    // (home screen behind the player) becomes visible through it.
    // Release past threshold → fast snap down + close.
    // Release before threshold → fast snap back to 0.
    val dismissDragY = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { LocalView.current.rootView.height.toFloat() }
    val dismissThreshold = screenHeightPx * 0.15f  // 15% of screen height = close
    // Background alpha: 1 (opaque) at rest, fades to 0 (transparent) as
    // you drag down. This reveals the songs list behind the player.
    val bgAlpha = (1f - (dismissDragY.value / dismissThreshold)).coerceIn(0f, 1f)

    // ─── Palette (extracted from album art, cached in PaletteCache) ───
    // Read from PaletteCache FIRST (instant — no black flash). The mini
    // player already extracted and cached the palette while the song was
    // playing. If not cached, extract async + store for next time.
    var palette by remember(albumArtUri) {
        mutableStateOf(PaletteCache.get(albumArtUri) ?: CoralPalette.Default)
    }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            // If already cached, skip extraction entirely
            val cached = PaletteCache.get(albumArtUri)
            if (cached == null) {
                // Preload into Coil cache (full size, for the blurred bg)
                try {
                    coil3.ImageLoader(context).execute(
                        coil3.request.ImageRequest.Builder(context)
                            .data(albumArtUri)
                            .build()
                    )
                } catch (_: Exception) { }
                // Extract palette + cache it for next time
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
    // ─── Song format detection (FLAC, MP3, etc) ─────────────────────
    var songFormat by remember { mutableStateOf("") }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try {
                mediaController?.let {
                    currentPositionMs = it.currentPosition.coerceAtLeast(0L)
                    durationMs = it.duration.coerceAtLeast(0L)
                    // Detect format from MIME type (content:// URIs have no extension)
                    if (songFormat.isEmpty()) {
                        val uri = it.currentMediaItem?.localConfiguration?.uri
                        if (uri != null) {
                            val mimeType = try {
                                context.contentResolver.getType(uri)
                            } catch (_: Exception) { null }
                            songFormat = when {
                                mimeType == null -> ""
                                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                                mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
                                mimeType.contains("mp4", ignoreCase = true) ||
                                    mimeType.contains("m4a", ignoreCase = true) -> "M4A"
                                mimeType.contains("aac", ignoreCase = true) -> "AAC"
                                mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                                mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                                mimeType.contains("wav", ignoreCase = true) -> "WAV"
                                mimeType.contains("wma", ignoreCase = true) ||
                                    mimeType.contains("x-ms-wma", ignoreCase = true) -> "WMA"
                                else -> ""
                            }
                        }
                    }
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
    // ─── Menu icon tap animation (rotate, stay rotated while menu open) ──
    val menuRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val menuCoroutineScope = rememberCoroutineScope()
    // ─── Shuffle + Repeat state (from MediaController) ────────────
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { androidx.compose.runtime.mutableIntStateOf(androidx.media3.common.Player.REPEAT_MODE_OFF) }
    LaunchedEffect(mediaController) {
        mediaController?.let {
            shuffleEnabled = it.shuffleModeEnabled
            repeatMode = it.repeatMode
        }
    }
    // ─── Sleep timer placeholder page ─────────────────────────────
    var showSleepTimerPage by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    fun toggleMenu() {
        menuCoroutineScope.launch {
            if (showMoreMenu) {
                // Closing: rotate back to 0
                showMoreMenu = false
                menuRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(200)
                )
            } else {
                // Opening: rotate to 90° and stay
                menuRotation.animateTo(
                    targetValue = 90f,
                    animationSpec = androidx.compose.animation.core.tween(200)
                )
                showMoreMenu = true
            }
        }
    }
    LaunchedEffect(showHeartPop) {
        if (showHeartPop) { delay(800); showHeartPop = false }
    }

    // ─── Lyrics (1-line synced preview, like Coral but single line) ──
    val lyricsRepository = remember { com.rajatxo.coral.data.lyrics.LyricsRepository(context) }
    var lyricData by remember { mutableStateOf<com.rajatxo.coral.data.lyrics.Lyric?>(null) }
    LaunchedEffect(title, artist, durationMs) {
        try {
            lyricData = lyricsRepository.getLyrics(
                track = title, artist = artist, album = albumName, durationMs = durationMs
            )
        } catch (_: Exception) { }
    }
    val activeLineIndex = if (lyricData != null && lyricData!!.synced && lyricData!!.lines.isNotEmpty()) {
        findActiveLineIndex(lyricData!!.lines, currentPositionMs)
    } else -1
    // The current lyric line text (1 line only)
    // - Synced lyrics available: show current line (or ♪ if line is blank)
    // - Lyrics available but not synced: show ♪ (lyrics exist)
    // - No lyrics at all: show "No Lyrics Available"
    val lyricLineText = when {
        lyricData != null && lyricData!!.synced && activeLineIndex >= 0 ->
            lyricData!!.lines[activeLineIndex].text.ifBlank { "♪" }
        lyricData != null && !lyricData!!.synced && lyricData!!.lines.isNotEmpty() -> "♪"
        lyricData != null && lyricData!!.synced -> "♪"  // synced but no active line yet
        else -> "No Lyrics Available"
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
    // Use the palette's dominant color as the base background. This matches
    // the album art (extracted from it) so there's no jarring flash — the
    // blurred art fills over it seamlessly once it loads.
    // The whole player is wrapped in a vertical drag gesture: drag down to
    // dismiss (fade + translate down), like ArchiveTune/Spotify.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background fades to transparent as you drag down, revealing
                // the songs list (home screen) behind the player.
                drawRect(color = animatedBottomColor, alpha = bgAlpha)
            }
            .graphicsLayer {
                translationY = dismissDragY.value
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissDragY.value > dismissThreshold) {
                            // Animate the player ALL the way down off-screen,
                            // THEN call onDismiss(). We keep translationY at
                            // screenHeightPx during the exit so the player
                            // stays off-screen — no reappear.
                            coroutineScope.launch {
                                dismissDragY.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = androidx.compose.animation.core.tween(200)
                                )
                                onDismiss()
                                // Don't reset dismissDragY here — keep it at
                                // screenHeightPx so the player stays off-screen
                                // during the exit transition. It'll reset when
                                // the player is re-opened (LaunchedEffect below).
                            }
                        } else {
                            // Fast snap back to 0
                            coroutineScope.launch {
                                dismissDragY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(150)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dismissDragY.animateTo(
                                targetValue = 0f,
                                animationSpec = androidx.compose.animation.core.tween(150)
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

        // ═══════════════════════════════════════════════════════════════
        // SPIRAL COVER (square, normal size) + SYMMETRIC BLUR BLEND
        // ═══════════════════════════════════════════════════════════════
        // Background: 96dp blurred album art fills whole screen (Spiral)
        // Cover: SQUARE (aspectRatio 1f, normal size — no zoom/crop). The
        //   cover has SYMMETRIC fades on BOTH top (64dp) and bottom (140dp)
        //   so the sharp art blends into the blur on both sides. The blur
        //   is positioned according to where the cover sits — more blur
        //   on the side where the cover edge is.

        // ─── Outgoing blurred bg (96dp) ──────────────────────────────
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

        // ─── Outgoing square cover (symmetric fades top + bottom) ────
        if (albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .align(Alignment.TopCenter)
                    .offset(y = 80.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = outAlpha }
                    .drawWithContent {
                        drawContent()
                        val topFadeHeightPx = 120.dp.toPx()
                        val bottomFadeHeightPx = 140.dp.toPx()
                        val imageHeight = size.height

                        // Top fade: transparent → opaque (64dp)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = topFadeHeightPx
                            ),
                            blendMode = BlendMode.DstIn
                        )

                        // Bottom fade: opaque → transparent (140dp)
                        val bottomFadeStartY = (imageHeight - bottomFadeHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
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
        }

        // ─── Incoming blurred bg + square cover (crossfade) ──────────
        if (showIncoming && xfIncomingArt != null) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .align(Alignment.TopCenter)
                    .offset(y = 80.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = inAlpha
                    }
                    .drawWithContent {
                        drawContent()
                        val topFade = 120.dp.toPx()
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

        // ─── 'Now Playing' text at top ───────────────────────────────
        Text(
            text = "NOW PLAYING",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        // Heart pop overlay (double-tap to favorite)
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

        // ─── Song name + Artist (LEFT-aligned, at the blend point) ───
        // Positioned where the cover's bottom fade begins.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.65f)  // slightly higher
                .padding(horizontal = 28.dp)
        ) {
            // ── Song title + Menu icon (LEFT-aligned title, menu on right) ──
            Box(modifier = Modifier.fillMaxWidth()) {
                if (xfActive && xfIncomingTitle.isNotEmpty()) {
                    val titleOutAlpha = kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    val titleInAlpha = kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = titleOutAlpha
                            renderEffect = blurRenderEffect(8f * (1f - titleOutAlpha))
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = titleInAlpha
                            renderEffect = blurRenderEffect(8f * (1f - titleInAlpha))
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = xfIncomingTitle,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            imageVector = CoralIcons.Ellipsis,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontFamily = CalSansFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
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
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            // ── Artist name (LEFT-aligned, white 60%, blend transition) ──
            Box(modifier = Modifier.fillMaxWidth()) {
                if (xfActive && xfIncomingArtist.isNotEmpty()) {
                    val artistOutAlpha = kotlin.math.cos(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    val artistInAlpha = kotlin.math.sin(xfProgress * kotlin.math.PI / 2).toFloat().coerceIn(0f, 1f)
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = artistOutAlpha
                            renderEffect = blurRenderEffect(6f * (1f - artistOutAlpha))
                        }
                    )
                    Text(
                        text = xfIncomingArtist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = artistInAlpha
                            renderEffect = blurRenderEffect(6f * (1f - artistInAlpha))
                        }
                    )
                } else {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ─── Menu capsule popup (vertical pill, right side, icons only) ─
        // A vertical capsule (like Coral's play-pause but vertical) with
        // only icons inside: Sleep Timer, Shuffle, Loop. Glass morphism
        // (drawBackdrop, same as prev/next buttons). Positioned on the
        // right side, below the menu button — doesn't cover it.
        if (showMoreMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { toggleMenu() }  // tap outside to close
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 350.dp)
                        .padding(end = 20.dp)
                        .width(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { RoundedCornerShape(26.dp) },
                            effects = {
                                vibrancy()
                                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                                blur(20f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.5f)) }
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sleep Timer (icon only)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                showSleepTimerPage = true
                                toggleMenu()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Timer,
                            contentDescription = "Sleep Timer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Shuffle (icon only)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                mediaController?.let {
                                    it.shuffleModeEnabled = !it.shuffleModeEnabled
                                    shuffleEnabled = it.shuffleModeEnabled
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) palette.accent else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Loop (icon only, cycles OFF → ALL → ONE)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false)
                            ) {
                                mediaController?.let {
                                    val newMode = when (it.repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                            androidx.media3.common.Player.REPEAT_MODE_ALL
                                        androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                            androidx.media3.common.Player.REPEAT_MODE_ONE
                                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                    }
                                    it.repeatMode = newMode
                                    repeatMode = newMode
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_OFF -> CoralIcons.RepeatOff
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> CoralIcons.Infinity
                                else -> CoralIcons.Repeat
                            },
                            contentDescription = "Loop",
                            tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) palette.accent else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ─── Sleep timer placeholder page ────────────────────────────
        if (showSleepTimerPage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSleepTimerPage = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = CoralIcons.Timer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sleep Timer",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Coming soon",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tap anywhere to close",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }

        // ─── Bottom section: Lyrics + Timeline + Format + Prev/Play/Next ─
        var seekbarWidthPx by remember { mutableFloatStateOf(1f) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 32.dp)
        ) {
            // Gap between artist name and lyrics line
            Spacer(modifier = Modifier.height(12.dp))

            // ─── Lyrics text (1-line synced, sitting on top of timeline) ──
            // Bigger (18sp), pure white, with auto-shadow for readability
            // over bright backgrounds. Tap to open the full lyrics page.
            Text(
                text = lyricLineText,
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.7f),
                    offset = Offset(1f, 1f),
                    blurRadius = 4f
                )),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showLyrics = true },
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(2.dp))

            // ─── CORAL SEEK BAR (exact copy from CoralPlayer) ──────────
            // Straight line, thickens on drag, no thumb, no animations.
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(trackHeight / 2))
                        .align(Alignment.CenterStart)
                        .background(Color.White)
                )
            }

            // Time labels + Format text (same line: 0:00  FLAC  -3:14)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime((displayProgress * durationMs).toLong()),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = CalSansFamily
                )
                // Format text — plain, no capsule, fixed (no shaking)
                if (songFormat.isNotEmpty()) {
                    Text(
                        text = songFormat,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = CalSansFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "-" + formatTime(((1f - displayProgress) * durationMs).toLong()),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = CalSansFamily
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Triple-circle control pod (Prev | Play/Pause | Next) ───
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

        // ─── Queue drag handle zone (bottom of screen, invisible) ────
        var queueDragAccum by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { queueDragAccum = 0f },
                        onDragEnd = { queueDragAccum = 0f },
                        onDragCancel = { queueDragAccum = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            queueDragAccum += dragAmount
                            if (queueDragAccum < -60f) {
                                showQueue = true
                                queueDragAccum = 0f
                            }
                        }
                    )
                }
        )

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

        // ─── Queue page (smooth slide up from bottom) ──────────────
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

// ═══════════════════════════════════════════════════════════════════
// INK BACKGROUND — animated color blobs that drift like ink in water
// ═══════════════════════════════════════════════════════════════════
// 4 radial gradient blobs (primary, secondary, tertiary, accent) that
// slowly drift in organic paths. Each blob has a different speed and
// phase, creating a living, morphing color field. No hard edges — just
// soft color clouds bleeding into each other.
@Composable
private fun InkBackground(
    palette: CoralPalette,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ink")

    // 4 blobs, each with different speed + phase for organic motion
    val phase1 by transition.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "ink1"
    )
    val phase2 by transition.animateFloat(
        PI.toFloat(), 2f * PI.toFloat() + PI.toFloat(),
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "ink2"
    )
    val phase3 by transition.animateFloat(
        PI.toFloat() / 2, 2f * PI.toFloat() + PI.toFloat() / 2,
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "ink3"
    )
    val phase4 by transition.animateFloat(
        PI.toFloat() * 1.5f, 2f * PI.toFloat() + PI.toFloat() * 1.5f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "ink4"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Base: dark fill (tertiary color, the darkest)
        drawRect(color = palette.tertiary)

        // Blob 1 — primary, drifts in upper-left quadrant
        val r1 = w * 0.7f
        val b1x = cx + cos(phase1) * w * 0.3f
        val b1y = cy + sin(phase1) * h * 0.3f - h * 0.1f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.primary, Color.Transparent),
                center = Offset(b1x, b1y),
                radius = r1
            )
        )

        // Blob 2 — secondary, drifts in lower-right quadrant
        val r2 = w * 0.65f
        val b2x = cx + cos(phase2) * w * 0.35f + w * 0.1f
        val b2y = cy + sin(phase2) * h * 0.35f + h * 0.15f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.secondary, Color.Transparent),
                center = Offset(b2x, b2y),
                radius = r2
            )
        )

        // Blob 3 — accent, drifts in center (the pop color)
        val r3 = w * 0.5f
        val b3x = cx + cos(phase3) * w * 0.2f
        val b3y = cy + sin(phase3) * h * 0.2f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.accent, Color.Transparent),
                center = Offset(b3x, b3y),
                radius = r3
            )
        )

        // Blob 4 — primary again, drifts in lower-left (for coverage)
        val r4 = w * 0.55f
        val b4x = cx + cos(phase4) * w * 0.3f - w * 0.15f
        val b4y = cy + sin(phase4) * h * 0.3f + h * 0.1f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.primary, Color.Transparent),
                center = Offset(b4x, b4y),
                radius = r4
            )
        )
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

/** Binary search for the active lyric line at the given position. */
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
