package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.rajatxo.coral.data.prefs.NavBarConfig
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.components.TabCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * CynthiaHomeScreen — the NEW app UI.
 *
 * Built from scratch for glass morphism (GM). ONE root LayerBackdrop
 * captures the entire screen. All glass elements (nav bar, mini player,
 * player, etc.) sample from this single backdrop — no nesting, no crashes.
 *
 * Currently has:
 *   - Root glass backdrop
 *   - Glass nav bar (TabCapsule) with 3 tabs: Quick Picks, Songs, Playlists
 *   - Settings button (top-right gear) so user can switch to Astra
 *   - Tab content placeholder (just shows tab name)
 *
 * Coming next:
 *   - Glass mini player
 *   - Glass Quick Picks page content
 *   - Glass Songs page content
 *   - Glass Playlists page content
 *   - Glass player overlay
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    // 3 tabs only: Quick Picks, Songs, Playlists
    val cynthiaTabs = listOf(CoralTab.QuickPicks, CoralTab.Songs, CoralTab.Playlists)
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }

    // Settings overlay
    var showSettings by remember { mutableStateOf(false) }

    // Mini player dismissed state
    var miniPlayerDismissed by remember { mutableStateOf(false) }

    // Playback position polling (for mini player progress)
    var miniPlayerPositionMs by remember { mutableStateOf(0L) }
    var miniPlayerDurationMs by remember { mutableStateOf(0L) }

    androidx.compose.runtime.LaunchedEffect(mediaController) {
        mediaController?.let { controller ->
            while (true) {
                try {
                    miniPlayerPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    miniPlayerDurationMs = controller.duration.coerceAtLeast(0L)
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // Reset mini player dismissed when song changes
    androidx.compose.runtime.LaunchedEffect(currentSongId) {
        if (currentSongId != null && miniPlayerDismissed) {
            miniPlayerDismissed = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ROOT GLASS BACKDROP — matches Astra's exact pattern
    // ═══════════════════════════════════════════════════════════════
    val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val rootBackdrop: LayerBackdrop = rememberLayerBackdrop(
        graphicsLayer = graphicsLayer
    ) {
        drawContent()
    }

    // Outer Box: black background (NOT on the same chain as layerBackdrop)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Inner Box: layerBackdrop captures all page content
        // Glass elements (nav bar, mini player) sample from this backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(rootBackdrop)
        ) {
            // ─── Tab content ───
            Box(
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

            // ─── Top bar: settings gear (top-right) ───
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
                        .background(Color.White.copy(alpha = 0.1f))
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

            // ─── Glass nav bar (bottom) ───
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
                    onTabSelected = { tab ->
                        selectedTab = tab
                    },
                    backdrop = rootBackdrop
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
}
