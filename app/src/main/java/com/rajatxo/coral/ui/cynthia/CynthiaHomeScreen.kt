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
        // Both use kyant drawBackdrop for glass. Both are draggable (long-press
        // 3s + countdown + grid overlay + position persists).
        // When aligned (same Y), nav bar shrinks to make room for search circle.
        // When misaligned, nav bar grows back to full 240dp.
        
        // Read saved positions to check alignment
        val savedTabPos by com.rajatxo.coral.data.prefs.TabCapsulePosition.position.collectAsState()
        val savedSearchPos by com.rajatxo.coral.data.prefs.SearchFabPosition.position.collectAsState()
        
        // Check if nav bar and search circle are aligned (same Y, within tolerance)
        val tabYFrac = savedTabPos.second
        val searchYFrac = savedSearchPos.second
        val isAligned = kotlin.math.abs(tabYFrac - searchYFrac) < 0.02f  // ~2% tolerance
        
        // Nav bar width: shrinks when aligned, full when misaligned
        val navBarWidth = if (isAligned) 190.dp else 240.dp  // 190 + 40 circle + gaps = ~240
        
        CynthiaDraggableNavBar(
            tabs = cynthiaTabs,
            activeTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            backdrop = glassBackdrop,
            navBarWidth = navBarWidth
        )

        CynthiaDraggableSearchCircle(
            xOffset = if (isAligned) {
                // Positioned right of the nav bar
                // Nav bar center is at savedTabPos.first * screenWidth
                // Circle sits at navBarWidth/2 + gap + circleSize/2 from center
                130.dp  // offset from center — will be refined
            } else {
                // Default right side
                130.dp
            },
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
