package com.rajatxo.coral.ui.cynthia

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.components.TabCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.theme.CalSansFamily

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun CynthiaHomeScreen(
    songs: List<Song>,
    mediaController: MediaController?,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArtist: String?,
    currentSongAlbum: String?,
    currentSongArt: android.net.Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongClickWithQueue: (Song, List<Song>) -> Unit,
    onMiniPlayerClick: () -> Unit,
    showFullPlayer: Boolean,
    onFullPlayerDismiss: () -> Unit,
    onSongEnded: () -> Unit,
    onRefresh: suspend () -> Unit = {}
) {
    val cynthiaTabs = listOf(CoralTab.QuickPicks, CoralTab.Songs, CoralTab.Playlists)
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }
    var showSettings by remember { mutableStateOf(false) }
    val onSongClickWithReset: (Song) -> Unit = { song -> onSongClick(song) }

    // ─── Glass backdrop (EXACT same as Astra) ───
    val graphicsLayer = rememberGraphicsLayer()
    val glassBackdrop = rememberLayerBackdrop(
        graphicsLayer = graphicsLayer
    ) {
        drawContent()
    }

    // ─── Dynamic header title (same as Astra) ───
    val headerTitle = when (selectedTab) {
        CoralTab.QuickPicks -> "Quick picks"
        CoralTab.Songs -> "Songs"
        CoralTab.Playlists -> "Playlists"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ═══ INNER BOX — layerBackdrop captures page content ═══
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop)
        ) {
            when (selectedTab) {
                CoralTab.QuickPicks -> com.rajatxo.coral.ui.screens.QuickPicksScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongArt = currentSongArt,
                    onSongClick = onSongClickWithReset
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedTab.label,
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }  // ← layerBackdrop Box ENDS — everything below is a SIBLING

        // ═══ TOP FADE BLUR — EXACT copy from Astra (lines 546-700) ═══
        if (true) {  // always show on all tabs (Astra checks !showSearch)
            val useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            // 120dp tall blur box, aligned to TopCenter
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = true
                        if (useRenderEffect) {
                            renderEffect = BlurEffect(
                                radiusX = 20.dp.toPx(),
                                radiusY = 20.dp.toPx()
                            )
                        }
                    }
                    .drawWithContent {
                        if (useRenderEffect) {
                            // RenderEffect path (API 31+): draw captured content
                            drawLayer(graphicsLayer)
                        } else {
                            // Fallback path (API < 31): draw drawBackdrop child
                            drawContent()
                        }
                        // DstIn gradient mask — fades bottom to transparent
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.6f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                // Inner Box with drawBackdrop — only rendered on API < 31
                if (!useRenderEffect) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { RectangleShape },
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
                    )
                }
            }

            // ═══ HEADER ROW — profile icon + title + settings cog ═══
            // EXACT same as Astra (lines 640-700)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User icon (left)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* TODO: account screen */ }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.CircleUser,
                        contentDescription = "Account",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title (center)
                Text(
                    text = headerTitle,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Settings cog (right) — NO drawBackdrop, just plain icon like Astra
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showSettings = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Cog,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ═══ DRAGGABLE NAV BAR + SEARCH CIRCLE — siblings of layerBackdrop ═══
        //
        // ★ USER-SPECIFIED LAYOUT (Cynthia only, completely separate from Astra):
        //   - Nav bar default: X = 0.283, Y = 0.889 (LEFT side, near bottom)
        //   - Search FAB default: X = 0.565, Y = 0.889 (to the RIGHT of nav bar
        //     with a 10dp gap, on the SAME horizontal line)
        //   - Both 52dp tall/diameter
        //
        // ★ LINKED MOVEMENT (the new behavior the user asked for):
        //   When the user drags the nav bar AND the search FAB is "aligned"
        //   (same horizontal line — small Y misalignment up to ~5% is OK),
        //   the search FAB follows the nav bar — wherever the nav bar moves,
        //   the search FAB moves along with it, maintaining its relative
        //   position (gap + same Y).
        //
        // ★ SEPARATE FROM ASTRA — uses CynthiaTabCapsulePosition and
        //   CynthiaSearchFabPosition, NOT the shared Astra ones.
        val savedTabPos by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()
        val savedSearchPos by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()

        val tabY = savedTabPos.second
        val searchY = savedSearchPos.second
        val tabX = savedTabPos.first
        val searchX = savedSearchPos.first

        // Aligned = search FAB is on the same horizontal line as the nav bar
        // (small Y misalignment up to 5% is acceptable — "around the nav bar")
        val yDiff = kotlin.math.abs(tabY - searchY)
        val isAligned = yDiff < 0.05f

        // ★ MISALIGN → CENTER: when the search FAB moves OFF the nav bar's
        //   horizontal line (misaligned), the nav bar moves to CENTER (X=0.5)
        //   on the same horizontal line (Y stays the same). The width also
        //   animates from 150dp to 240dp (handled by navBarWidth below).
        //   This only fires when isAligned CHANGES (keyed on isAligned), so
        //   it doesn't fight the linked movement (which only happens when
        //   isAligned is true).
        androidx.compose.runtime.LaunchedEffect(isAligned) {
            if (!isAligned) {
                com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                    .setPosition(0.5f, tabY)
            }
        }

        // ★ LINKED MOVEMENT — when the nav bar is dragged AND the search FAB
        //   is aligned (same horizontal line), the search FAB follows the nav
        //   bar. We track the nav bar's drag delta and apply the SAME delta to
        //   the search FAB's saved position.
        //   Implementation: CynthiaDraggableNavBar takes an `onNavBarDragged`
        //   callback that fires with the delta (in pixels) whenever the nav bar
        //   moves during a drag. Here we convert that to fractions and apply
        //   to the search FAB's position IF they're aligned.
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val onNavBarDragged: (Float, Float) -> Unit = { deltaXpx, deltaYpx ->
            if (isAligned && screenWidthPx > 0 && screenHeightPx > 0) {
                // Move the search FAB by the same delta as the nav bar.
                // This keeps the search FAB at a fixed offset from the nav bar.
                val deltaXfrac = deltaXpx / screenWidthPx
                val deltaYfrac = deltaYpx / screenHeightPx
                val newSearchX = (searchX + deltaXfrac).coerceIn(0.05f, 0.95f)
                val newSearchY = (searchY + deltaYfrac).coerceIn(0.05f, 0.95f)
                com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                    .setPosition(newSearchX, newSearchY)
            }
        }

        // Animate nav bar width: 150dp (aligned) ↔ 240dp (misaligned)
        val navBarWidth by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (isAligned) 150.dp else 240.dp,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            ),
            label = "navBarWidth"
        )

        CynthiaDraggableNavBar(
            tabs = cynthiaTabs,
            activeTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            backdrop = glassBackdrop,
            navBarWidth = navBarWidth,
            onNavBarDragged = onNavBarDragged
        )

        CynthiaDraggableSearchCircle(
            onSearchClick = { /* TODO: search screen */ },
            backdrop = glassBackdrop
        )

        // ═══ Settings overlay ═══
        if (showSettings) {
            SettingsScreen(
                onBackClick = { showSettings = false },
                onOpenPremium = { showSettings = false },
                onOpenEqualizer = { showSettings = false },
                onOpenSleepTimer = { showSettings = false },
                onOpenFontPicker = { showSettings = false },
                onOpenLyrics = { showSettings = false },
                onOpenSpiralPalette = { showSettings = false }
            )
        }
    }
}
