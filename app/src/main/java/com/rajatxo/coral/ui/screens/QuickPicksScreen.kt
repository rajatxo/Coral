package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * QuickPicksScreen — "Editorial Gallery" edition.
 *
 * Light/dark theme-aware. Asymmetric hero grid + horizontal carousels.
 * Songs are randomized on each app launch. Carousels are infinite
 * (repeat the song list so you can swipe forever).
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongArt: android.net.Uri? = null,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {},
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val context = LocalContext.current

    // ─── Current song's palette → dark gradient background ──────────
    // The background is a dark gradient using the current song's palette
    // colors. If no song is playing, use a default dark palette.
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
    // Animate the gradient colors smoothly when the song changes.
    // Low alpha values — the palette colors are just subtle hints over
    // a dark base. The background stays predominantly dark/black so
    // it doesn't feel bright.
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.25f),
        animationSpec = tween(800), label = "bgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.secondary.copy(alpha = 0.15f),
        animationSpec = tween(800), label = "bgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),  // near-black at the bottom
        animationSpec = tween(800), label = "bgBottom"
    )

    // Dark text colors (always white on the dark gradient)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    // ─── Random seed — changes on every app launch ─────────────────
    // This ensures the song selection is different each time the user
    // opens the app. The seed is remembered for the lifetime of this
    // composable (which is tied to the app session).
    val launchSeed = remember { Random.nextInt() }

    // ─── Prepare song groups (randomized per launch) ────────────────
    // BUG FIX: previously used remember(songs, currentSongId) which
    // didn't recompute when songs loaded async after the screen was
    // already shown. Now we key on songs.size so it recomputes when
    // songs actually arrive. Also use launchSeed for randomization so
    // the selection changes on every app open.
    val heroSongs = remember(songs.size, currentSongId, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            val current = songs.firstOrNull { it.id == currentSongId }
            val pool = songs.shuffled(Random(launchSeed))
            if (current != null) {
                listOf(current, pool.firstOrNull { it.id != current.id } ?: songs.first())
            } else {
                pool.take(2)
            }
        }
    }
    val recentSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 1)).take(10)
    }
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 2)).take(10)
    }

    // Infinite-repeat versions for the carousels.
    //
    // PERFORMANCE: reduced from 10x repeat (100 items) to 3x repeat
    // (30 items). 100 items forced LazyRow to manage 100 item slots
    // + 100 key entries on every recomposition. 30 items still feels
    // infinite (you'd have to swipe 30 cards to reach the end) but
    // cuts the slot management overhead by ~3x.
    //
    // Keys are added in the items() calls below so LazyRow can reuse
    // composables across recompositions (was: no key → every item
    // recomposed on every songs update).
    val infiniteRecent = remember(recentSongs) {
        if (recentSongs.isEmpty()) emptyList() else List(3) { recentSongs }.flatten()
    }
    val infiniteMore = remember(moreSongs) {
        if (moreSongs.isEmpty()) emptyList() else List(3) { moreSongs }.flatten()
    }

    // Solid dark base — the gradient's low-alpha palette colors composite
    // over this, so the background is always predominantly dark/black.
    val darkBase = Color(0xFF05050A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBase)
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedTop, animatedMid, animatedBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 108.dp,      // just below where the blur ends
                bottom = 200.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // ═══ Speed Dial (first row) ═══
            // A paginated grid of square song cards + a "randomize" dice
            // button as the last slot. Tap a card to play that song.
            // Tap the dice → plays a random song.
            // The hero grid (EditorialCard row) has been removed — the
            // Speed Dial is now the first row on Quick Picks.
            item {
                SpeedDialSection(
                    songs = songs,
                    currentSongId = currentSongId,
                    onSongClick = onSongClick,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    backdrop = backdrop
                )
            }

            // ═══ Recent ═══
            item {
                SectionHeader(
                    title = "Recent",
                    count = recentSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    itemsIndexed(
                        items = infiniteRecent,
                        // Stable key = song id + occurrence index, so each
                        // repeated copy of a song has a unique key. Without
                        // a key, LazyRow recomposes ALL items on every
                        // songs update instead of reusing existing ones.
                        key = { index, song -> "${song.id}_$index" }
                    ) { _, song ->
                        SquareCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier
                                .width(140.dp)
                                .height(200.dp)
                        )
                    }
                }
            }

            // ═══ More ═══
            item {
                SectionHeader(
                    title = "More picks",
                    count = moreSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    itemsIndexed(
                        items = infiniteMore,
                        key = { index, song -> "${song.id}_$index" }
                    ) { _, song ->
                        LandscapeCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier
                                .width(160.dp)
                                .height(220.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// EDITORIAL CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun EditorialCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend ═══
        //
        // Layer 1 (bottom): BLURRED album art — fills the entire card.
        // This is the "down" part. Heavy blur so it's just soft colors.
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — covers the top ~75% of the card.
        // A DstIn gradient at its bottom edge dissolves the sharp image
        // into the blurred layer behind it (the "blend point").
        // Smaller blend height = less gap, more of the sharp cover shows.
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // DstIn mask: opaque at top → transparent at bottom.
                        // Smaller blend height (40dp) for a tighter blend.
                        val blendHeightPx = 40.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NOW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        // Text sits on top of the blurred bottom part
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SQUARE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SquareCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend (ORIGINAL .blur() modifier) ═══
        // Layer 1 (bottom): BLURRED album art — fills entire card
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — top 75%, DstIn blend at bottom
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 30.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        // Text on the blurred bottom part
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF007AFF))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// LANDSCAPE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LandscapeCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend (ORIGINAL .blur() modifier) ═══
        // Layer 1 (bottom): BLURRED album art — fills entire card
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — top 75%, DstIn blend at bottom
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 30.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Layer 3: (border removed per user request)

        // Text on the blurred bottom part
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL MODE CAPSULE — thin swipeable text capsule
// ════════════════════════════════════════════════════════════════════
// A small glass capsule that sits below the "Speed dial" header.
// Swipe left/right to cycle through 3 modes:
//   1. "Based on most played songs"
//   2. "Based on last Played song"
//   3. "Based on Random songs"
// Then loops back to #1.
//
// Same interaction model as the nav bar TabCapsule:
//   • detectHorizontalDragGestures with 60px threshold
//   • One swipe = one step (dragAccumulator resets after each snap)
//   • AnimatedContent slides the text in/out horizontally
//   • Haptic + sound feedback on each snap
//
// Sizing: thin (36dp tall, vs nav bar's 52dp). Width wraps the text
// with horizontal padding so the capsule is just big enough for the
// current mode string.
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialModeCapsule(
    textPrimary: Color,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current

    val modes = remember {
        listOf(
            "Based on most played songs",
            "Based on last Played song",
            "Based on Random songs"
        )
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var slideDirection by remember { mutableIntStateOf(1) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    // TRUE one-swipe-per-touch: once a snap happens, no more snaps until
    // the finger lifts (onDragEnd resets this flag).
    var hasSnappedThisDrag by remember { mutableStateOf(false) }
    val dragThreshold = 60f

    // ─── Haptics + Sound (same as nav bar TabCapsule) ──
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
        val hapticsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.hapticsEnabled.value
        val soundsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundsEnabled.value
        val volume = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundVolume.value / 100f

        if (hapticsOn) {
            var hapticPerformed = false
            try {
                hapticPerformed = view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
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
                } catch (_: Exception) { }
            }
        }
        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(tickSoundId, volume, volume, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    // Thinner than nav bar (36dp vs 52dp). Fixed width via weight(1f)
    // passed from the caller.
    val capsuleShape = RoundedCornerShape(18.dp)

    val glassModifier = if (backdrop != null) {
        modifier
            .height(36.dp)
            .clip(capsuleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape },
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
    } else {
        modifier
            .height(36.dp)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    }

    Box(
        modifier = glassModifier
            .pointerInput(modes) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // Reset the one-snap lock at the start of each touch
                        hasSnappedThisDrag = false
                        dragAccumulator = 0f
                    },
                    onDragEnd = {
                        dragAccumulator = 0f
                        hasSnappedThisDrag = false
                    },
                    onDragCancel = {
                        dragAccumulator = 0f
                        hasSnappedThisDrag = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        // If we already snapped this touch, ignore all further
                        // drag until the finger lifts. This is the TRUE
                        // one-swipe-per-touch behavior.
                        if (hasSnappedThisDrag) return@detectHorizontalDragGestures

                        dragAccumulator += dragAmount
                        if (dragAccumulator < -dragThreshold) {
                            // Swipe left → next mode
                            slideDirection = 1
                            currentIndex = (currentIndex + 1) % modes.size
                            tickHaptic()
                            hasSnappedThisDrag = true  // lock until finger lifts
                            dragAccumulator = 0f
                        } else if (dragAccumulator > dragThreshold) {
                            // Swipe right → previous mode
                            slideDirection = -1
                            currentIndex = if (currentIndex - 1 < 0) modes.size - 1 else currentIndex - 1
                            tickHaptic()
                            hasSnappedThisDrag = true  // lock until finger lifts
                            dragAccumulator = 0f
                        }
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                if (slideDirection == 1) {
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth }
                } else {
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth }
                }
            },
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
            label = "modeText"
        ) { index ->
            Text(
                text = modes[index],
                color = textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL SECTION
// ════════════════════════════════════════════════════════════════════
// A paginated grid of square song cards + a "randomize" dice button.
// Based on vivi-music's SpeedDial feature:
//   - HorizontalPager with pages of a 3-column grid
//   - Each page shows up to 9 songs (3x3)
//   - The last slot on the first page is a "randomize" dice button
//   - Tap a song card → plays that song
//   - Tap the dice → picks a random song and plays it
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialSection(
    songs: List<Song>,
    currentSongId: Long?,
    onSongClick: (Song) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
) {
    val scope = rememberCoroutineScope()
    var isRandomizing by remember { mutableStateOf(false) }

    // Use up to 17 songs for the speed dial (leaves room for the dice)
    val speedDialSongs = remember(songs.size) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled().take(17)
    }

    if (speedDialSongs.isEmpty()) return

    // Section header — "Speed dial" text + thin swipeable capsule + chevron.
    // The capsule sits BETWEEN the text and the chevron, with a fixed width
    // (weight 1f fills available space) and glass morphism (same as nav bar).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Speed dial",
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        // Thin swipeable glass capsule — cycles through 3 modes.
        // weight(1f) gives it a fixed width (fills available space).
        SpeedDialModeCapsule(
            textPrimary = textPrimary,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
        // Chevron (song count number removed per user request)
        Icon(
            imageVector = CoralIcons.ChevronRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Grid layout: 3 columns, paginated
    val targetItemSize = 110.dp
    val columns = 3
    val rows = 3
    val itemsPerPage = columns * rows // 9 per page
    val totalSlots = speedDialSongs.size + 1 // +1 for the dice
    val pageCount = (totalSlots + itemsPerPage - 1) / itemsPerPage
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })

    val itemWidth = targetItemSize

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 12.dp,
            // Pre-render 1 page on each side so swiping feels instant
            // (no pop-in when the next page appears). Default is 0 which
            // causes a visible "compose lag" on the first frame of a swipe.
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemWidth * rows + 16.dp)  // 3 rows + padding
        ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until columns) {
                            val itemIndex = row * columns + col
                            val globalItemIndex = page * itemsPerPage + itemIndex

                            // The dice button is the last slot on the first page
                            val isDiceSlot = (globalItemIndex == itemsPerPage - 1)

                            if (isDiceSlot) {
                                RandomizeGridItem(
                                    isLoading = isRandomizing,
                                    onClick = {
                                        if (isRandomizing) {
                                            isRandomizing = false
                                        } else {
                                            isRandomizing = true
                                            scope.launch {
                                                kotlinx.coroutines.delay(800)  // dice animation
                                                val randomSong = songs.random()
                                                isRandomizing = false
                                                onSongClick(randomSong)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .height(itemWidth)
                                        .padding(4.dp)
                                )
                            } else {
                                val actualIndex = if (globalItemIndex < itemsPerPage - 1) {
                                    globalItemIndex
                                } else {
                                    globalItemIndex - 1
                                }
                                val song = speedDialSongs.getOrNull(actualIndex)
                                if (song != null) {
                                    SpeedDialCard(
                                        song = song,
                                        isCurrent = song.id == currentSongId,
                                        onClick = { onSongClick(song) },
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(itemWidth)
                                            .padding(4.dp)
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(itemWidth)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SPEED DIAL CARD — square card with album art + title
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                clip = false
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // ═══ Spiral 2.0-style album art blur-blend (ORIGINAL .blur() modifier) ═══
        // Layer 1 (bottom): BLURRED album art — fills entire card.
        // Reduced blur (20dp, was 36dp) so the cover isn't cropped too much.
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Layer 2 (top): SHARP album art — top 85%, DstIn blend at bottom.
        // The blend is only behind the text area (bottom 15%).
        if (song.albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val blendHeightPx = 24.dp.toPx()
                        val imageHeight = size.height
                        val blendStartY = (imageHeight - blendHeightPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = blendStartY,
                                endY = imageHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // (border removed per user request)

        // Title at the bottom (on the blurred part)
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 4.dp)
        )

        // Now-playing dot
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(6.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFF6B6B))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// RANDOMIZE GRID ITEM — dice button with 5-dot pattern
// ════════════════════════════════════════════════════════════════════

@Composable
private fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When loading, dots collapse to center. When idle, dots spread to corners.
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val dotColor = Color.White
        val dotSize = 10.dp
        val padding = 16.dp

        // 5-dot dice pattern (top-left, top-right, center, bottom-left, bottom-right)
        // Top Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Top Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )

        // Loading spinner overlay (Coral accent color)
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFFF6B6B),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SECTION HEADER
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                color = textSecondary,
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
