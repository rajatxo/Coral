package com.rajatxo.coral.ui.cynthia

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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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

/**
 * CynthiaHomeScreen — the NEW app UI.
 *
 * Uses the EXACT same kyant backdrop pattern as Astra:
 *   - Outer Box: background
 *   - Inner Box: layerBackdrop captures page content
 *   - Nav bar + settings + mini player are SIBLINGS of the layerBackdrop Box
 *     (NOT children — being children causes recursive capture crash)
 *   - Glass elements use drawBackdrop(glassBackdrop) to sample the content
 */
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

    // ═══════════════════════════════════════════════════════════════
    // GLASS BACKDROP — EXACT same pattern as Astra (lines 310-320)
    // ═══════════════════════════════════════════════════════════════
    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
        graphicsLayer = graphicsLayer
    ) {
        drawContent()
    }

    // ═══════════════════════════════════════════════════════════════
    // OUTER BOX — background (same as Astra's Box at line 299)
    // ═══════════════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // INNER BOX — layerBackdrop captures page content (Astra line 387-393)
        // This Box's children are captured into glassBackdrop.graphicsLayer.
        // Glass elements (nav bar, settings) sample this via drawBackdrop.
        // ═══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop)
        ) {
            // ─── Tab content ───
            when (selectedTab) {
                CoralTab.QuickPicks -> com.rajatxo.coral.ui.screens.QuickPicksScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongArt = currentSongArt,
                    onSongClick = onSongClickWithReset
                )
                CoralTab.Songs -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Songs",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
                CoralTab.Playlists -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Playlists",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
                else -> {}
            }
        }  // ← layerBackdrop Box ENDS here — everything below is a SIBLING

        // ═══════════════════════════════════════════════════════════════
        // SIBLINGS of layerBackdrop Box — these sample glassBackdrop
        // ═══════════════════════════════════════════════════════════════

        // ─── Top fade blur (kyant drawBackdrop, fades to transparent at bottom) ───
        // Same as Astra's TopFadeBlur — real kyant glass at the top of every page.
        // 120dp tall, fades from opaque (top) → transparent (bottom).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // DstIn mask: opaque at top → transparent at bottom
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black,
                                0.35f to Color.Black,
                                0.7f to Color.Black.copy(alpha = 0.5f),
                                1.0f to Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
        ) {
            // The blurred backdrop — pure clean blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { androidx.compose.ui.graphics.RectangleShape },
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

        // ─── Settings gear (top-right, on top of the blur) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            colorControls(
                                brightness = 0.05f,
                                contrast = 1f,
                                saturation = 1.3f
                            )
                            blur(18f.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.35f))
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettings = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ─── Glass nav bar (bottom) — SIBLING of layerBackdrop ───
        // Same as Astra's DraggableTabCapsule call (line 706)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabCapsule(
                tabs = cynthiaTabs,
                activeTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab },
                backdrop = glassBackdrop  // ★ Same as Astra: samples glassBackdrop
            )
        }

        // ─── Settings overlay ───
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
