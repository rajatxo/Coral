package com.rajatxo.coral.ui.screens

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.scanner.DuplicateDetector
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.launch

/**
 * DuplicateSongsSheet — premium glass morphism popup that shows the
 * duplicates found in the user's library after a pull-to-refresh on
 * the Songs tab.
 *
 * WHY THIS EXISTS
 * ---------------
 * Local libraries tend to accumulate the same song multiple times
 * (re-imported downloads, same song in two albums, etc). The pull-
 * to-refresh scan detects these and offers the user a one-tap way to
 * delete the dupes directly from internal storage.
 *
 * UI STRUCTURE
 * ------------
 *   • Full-screen dark backdrop (semi-transparent, swallows taps)
 *   • Centered glass sheet (rounded 28dp, frosted look via dark fill
 *     + white border + glossy vertical gradient overlay — same fake-
 *     glass pattern used in SongsScreen capsules + SearchScreen.
 *     We do NOT use drawBackdrop here because it crashes inside
 *     scrolling composables per earlier testing.)
 *
 *   Inside the sheet:
 *     • Title row: "Found N duplicates" + close button
 *     • Explainer: "Same artist + same title. Smart filter picks
 *       the longer / newer version to keep."
 *     • LazyColumn of DuplicateGroup cards. Each card shows:
 *         - Title + artist at top
 *         - One "KEEPER" capsule (green, with ✓)
 *         - N "DELETE" capsules (coral red, with ×) — tap to toggle
 *     • Bottom action bar: "Delete X selected" coral button
 *
 * SMART FILTER (see DuplicateDetector.pickKeeper)
 * -----------------------------------------------
 *   For each duplicate group, we recommend keeping the song with:
 *     1. Longest duration (full track beats radio edit)
 *     2. Newest dateAdded (recent import beats old copy)
 *
 *   The user can override the recommendation by tapping a capsule
 *   to flip its KEEP/DELETE state. The bottom bar always shows the
 *   current selection count.
 *
 * DELETION FLOW
 * -------------
 *   Uses MediaStore.createDeleteRequest (Android 10+). The system
 *   shows a confirmation dialog asking the user to approve. After
 *   approval, we dismiss the sheet and trigger onDuplicatesDeleted()
 *   so HomeScreen can refresh the library.
 *
 *   On Android 9 and below, deletes happen immediately (no dialog).
 *
 * USER CHOICE
 * -----------
 *   The user can:
 *     • Tap "Delete X selected" to confirm deletion
 *     • Tap the close (×) button to dismiss without deleting anything
 *     • Tap any capsule to flip its KEEP/DELETE state
 */
@Composable
fun DuplicateSongsSheet(
    duplicateGroups: List<DuplicateDetector.DuplicateGroup>,
    onDismiss: () -> Unit,
    onDuplicatesDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Track which song is the "keeper" in each group — user can swap
    // by tapping any capsule to flip it. Map: groupId (title|artist)
    // → current keeper song ID. All OTHER songs in the group are
    // slated for deletion.
    val initialKeepers = remember(duplicateGroups) {
        duplicateGroups.associate { group ->
            group.title + "|" + group.artist to group.keeper.id
        }
    }
    var keeperOverrides by remember {
        mutableStateOf(initialKeepers.toMutableMap())
    }
    var isDeleting by remember { mutableStateOf(false) }

    // Compute the songs slated for deletion based on the user's
    // current keeper selections.
    val songsToDelete: List<Song> = remember(keeperOverrides, duplicateGroups) {
        duplicateGroups.flatMap { group ->
            val keeperId = keeperOverrides[group.title + "|" + group.artist] ?: group.keeper.id
            group.all.filter { it.id != keeperId }
        }
    }

    // Launcher for the system delete-confirmation dialog (Android 10+).
    // When the user approves in the system dialog, the OS deletes the
    // files from internal storage. After the result returns, we refresh.
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isDeleting = false
        if (result.resultCode == Activity.RESULT_OK) {
            // User approved deletion — system has deleted the files.
            // Refresh the library via callback.
            onDuplicatesDeleted()
        }
        // If user canceled (resultCode != RESULT_OK), we just keep the
        // sheet open so they can retry or modify their selection.
    }

    // Glass sheet overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow stray taps so they don't leak to the SongsScreen
            // behind (which would play the song under the dimmed area)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // ─── Centered glass sheet ───
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .fillMaxSize(0.85f)
                .clip(RoundedCornerShape(28.dp))
                // ★ Fake glass: dark base + glossy vertical gradient overlay
                // (Same pattern as SongsScreen capsules — no drawBackdrop
                //  because that crashes inside scrolling composables.)
                .background(Color.Black.copy(alpha = 0.65f))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.10f),  // sheen at top
                            0.4f to Color.Transparent,
                            1.0f to Color.White.copy(alpha = 0.04f)   // subtle bottom
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                // Block taps from leaking through to the backdrop
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            // ─── Header ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Found ${duplicateGroups.size} duplicate${if (duplicateGroups.size != 1) "s" else ""}",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily
                    )
                    Text(
                        text = "${songsToDelete.size} song${if (songsToDelete.size != 1) "s" else ""} selected to delete",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontFamily = CalSansFamily
                    )
                }
                // Close button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ─── Explainer strip ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Smart filter picks the longer / newer version to keep. Tap any capsule to flip its keep/delete state.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = CalSansFamily,
                    lineHeight = 15.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Duplicate group cards ───
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(duplicateGroups, key = { it.title + "|" + it.artist }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        currentKeeperId = keeperOverrides[group.title + "|" + group.artist] ?: group.keeper.id,
                        onSwapKeeper = { newKeeperId ->
                            val key = group.title + "|" + group.artist
                            keeperOverrides = keeperOverrides.toMutableMap().apply {
                                this[key] = newKeeperId
                            }
                        }
                    )
                }
            }

            // ─── Bottom action bar ───
            if (songsToDelete.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = CalSansFamily
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // Delete button
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF6B6B),
                                            Color(0xFFDC2626)
                                        )
                                    )
                                )
                                .clickable(
                                    enabled = !isDeleting,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (isDeleting) return@clickable
                                        isDeleting = true
                                        coroutineScope.launch {
                                            val request = DuplicateDetector.buildDeleteRequest(
                                                context = context,
                                                songs = songsToDelete
                                            )
                                            if (request.requiresUserConfirmation &&
                                                request.pendingIntent != null) {
                                                // Android 10+ — show system confirmation dialog
                                                val intentSender: IntentSender =
                                                    request.pendingIntent.intentSender
                                                val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                                                deleteLauncher.launch(intentSenderRequest)
                                            } else {
                                                // Android 9 and below — already deleted, refresh
                                                onDuplicatesDeleted()
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "Delete ${songsToDelete.size} song${if (songsToDelete.size != 1) "s" else ""}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = CalSansFamily
                                )
                            }
                        }
                    }
                }
            } else {
                // No songs selected for deletion — show a different CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = CalSansFamily
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DUPLICATE GROUP CARD — one section per duplicate set
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DuplicateGroupCard(
    group: DuplicateDetector.DuplicateGroup,
    currentKeeperId: Long,
    onSwapKeeper: (Long) -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .padding(14.dp)
    ) {
        // Group title + artist
        Text(
            text = group.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = group.artist,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))

        // All songs in the group (keeper first)
        group.all.forEach { song ->
            val isKeeper = song.id == currentKeeperId
            DuplicateSongRow(
                song = song,
                isKeeper = isKeeper,
                onClick = { onSwapKeeper(song.id) }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DUPLICATE SONG ROW — one row per song in a duplicate group
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DuplicateSongRow(
    song: Song,
    isKeeper: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(24.dp)
    val accentColor = if (isKeeper) Color(0xFF4ADE80) else Color(0xFFFF6B6B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(pillShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(
                width = if (isKeeper) 1.5.dp else 1.dp,
                color = accentColor.copy(alpha = if (isKeeper) 0.6f else 0.3f),
                shape = pillShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art (circular)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        // Song details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.album.ifBlank { "Unknown album" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Duration + dateAdded as the "smart filter" hint
            val totalSec = song.duration / 1000
            val mm = totalSec / 60
            val ss = totalSec % 60
            Text(
                text = String.format(java.util.Locale.US, "%d:%02d", mm, ss),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily
            )
        }
        // KEEP / DELETE badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.20f))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isKeeper) "KEEP" else "DELETE",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
        }
        Spacer(Modifier.size(6.dp))
    }
}

// ════════════════════════════════════════════════════════════════════
// (End of file — IntentSenderRequest.Builder from androidx.activity
//  is used to wrap the MediaStore.createDeleteRequest pending intent.)
// ════════════════════════════════════════════════════════════════════
