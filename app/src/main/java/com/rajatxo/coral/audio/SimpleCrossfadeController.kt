package com.rajatxo.coral.audio

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rajatxo.coral.data.prefs.CrossfadeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dual-ExoPlayer crossfade controller with visual state broadcast.
 *
 * When the active song is within crossfadeSeconds of ending, the standby
 * player is loaded with the next song. Both play simultaneously — outgoing
 * fades out (cos) while incoming fades in (sin). At the crossover, the
 * session swaps and the outgoing player is STOPPED.
 *
 * Visual state is broadcast via [CrossfadeVisualState] so CoralPlayer can
 * render a dual-layer art dissolve synced to the same progress curve.
 *
 * Equal-power curve: sin²+cos²=1 → constant power, no dip.
 */
@UnstableApi
class SimpleCrossfadeController(
    private val scope: CoroutineScope,
    private val active: () -> ExoPlayer?,
    private val standby: () -> ExoPlayer?,
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit,
) {
    private var job: Job? = null
    private var transitioning = false

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                val crossfadeSeconds = CrossfadeManager.crossfadeDuration.value
                // If crossfade is OFF (0), do absolutely nothing. The
                // active player's REPEAT_MODE_ALL handles song-to-song
                // transitions automatically. We must NOT interfere —
                // no volume changes, no stop/clear, nothing.
                if (crossfadeSeconds <= 0) {
                    delay(1000)  // sleep 1s, re-check the setting
                    continue
                }
                if (!transitioning) {
                    val player = active() ?: run { delay(200); continue }
                    val duration = player.duration
                    val position = player.currentPosition
                    if (duration > 0 && position > 0 && player.isPlaying) {
                        val remaining = duration - position
                        val fadeMs = crossfadeSeconds * 1000L

                        if (remaining <= fadeMs && remaining > 0) {
                            startTransition(player, standby(), crossfadeSeconds)
                        }
                    }
                }
                delay(30)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        transitioning = false
    }

    private suspend fun startTransition(
        outgoing: ExoPlayer,
        incoming: ExoPlayer?,
        crossfadeSeconds: Int,
    ) {
        if (incoming == null) return
        transitioning = true

        try {
            val duration = outgoing.duration
            if (duration <= 0) return

            val position = outgoing.currentPosition
            val remaining = duration - position
            val fadeMs = crossfadeSeconds * 1000L
            val totalFadeMs = remaining.coerceAtMost(fadeMs).toFloat()

            // Load the next song on the standby player.
            //
            // REPEAT_MODE_ONE (loop song): load the SAME song — the
            // crossfade creates a seamless loop of the current song.
            //
            // REPEAT_MODE_ALL / REPEAT_MODE_OFF + SHUFFLE ON: pick a
            // RANDOM next index (excluding the current one) — this
            // matches ExoPlayer's built-in shuffle behavior.
            //
            // REPEAT_MODE_ALL / REPEAT_MODE_OFF + SHUFFLE OFF: advance
            // to the next song (wrapping around at the end of the queue).
            //
            // ★ BUG FIX: Previously this always used (current + 1) % count,
            //   which ignored shuffle mode entirely. When the user enabled
            //   shuffle + crossfade, songs played in sequential order
            //   instead of random.
            val nextIndex = when (outgoing.repeatMode) {
                Player.REPEAT_MODE_ONE -> outgoing.currentMediaItemIndex
                else -> {
                    if (outgoing.shuffleModeEnabled && outgoing.mediaItemCount > 1) {
                        // Pick a random index that's NOT the current one
                        var randomIdx: Int
                        do {
                            randomIdx = (0 until outgoing.mediaItemCount).random()
                        } while (randomIdx == outgoing.currentMediaItemIndex)
                        randomIdx
                    } else {
                        (outgoing.currentMediaItemIndex + 1) % outgoing.mediaItemCount
                    }
                }
            }
            val mediaItems = (0 until outgoing.mediaItemCount).map {
                outgoing.getMediaItemAt(it)
            }

            // ─── Sync the standby player's repeat mode with the active ──
            // BUG: After a crossfade handoff, the standby player becomes
            // the new active player. If the standby's repeatMode wasn't
            // synced, it would have the default (REPEAT_MODE_OFF). This
            // caused loop-one to break after a manual skip:
            //   • Song 1 loops fine (active has REPEAT_MODE_ONE)
            //   • User taps next → Song 2 plays on the same player
            //   • If that player was the former standby (REPEAT_MODE_OFF),
            //     the crossfade reads OFF → advances to Song 3
            //   • After that handoff, the other player (REPEAT_MODE_ONE)
            //     becomes active → Song 3 loops fine
            //   • Pattern: every other song loops
            //
            // Fix: sync the standby's repeatMode BEFORE loading media.
            // This ensures both players always have the same repeat mode,
            // so the crossfade always reads the correct one.
            //
            // ★ Also sync shuffleModeEnabled — same reasoning. Without
            //   this, the standby player doesn't know shuffle is on,
            //   so after handoff the new active player's shuffle flag
            //   is wrong, and the NEXT crossfade would ignore shuffle.
            incoming.repeatMode = outgoing.repeatMode
            incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled

            incoming.setMediaItems(mediaItems, nextIndex, 0)
            incoming.prepare()
            incoming.volume = 0f
            incoming.playWhenReady = true

            // Wait for READY
            var waitCount = 0
            while (incoming.playbackState != Player.STATE_READY && waitCount < 30) {
                delay(10)
                waitCount++
            }

            // Grab the incoming song's metadata for the visual crossfade
            val incomingItem = incoming.currentMediaItem
            val incomingMeta = incomingItem?.mediaMetadata
            val incomingArt = incomingMeta?.artworkUri
            val incomingTitle = incomingMeta?.title?.toString() ?: ""
            val incomingArtist = incomingMeta?.artist?.toString() ?: ""
            val incomingAlbum = incomingMeta?.albumTitle?.toString()

            // ★ Custom cover override — if the incoming song has a custom
            //   cover set in SongCoverManager (app-only override), use THAT
            //   instead of the file's metadata artwork URI. Otherwise the
            //   crossfade transition shows the ORIGINAL cover for the entire
            //   fade duration (6s by default), then snaps to the custom cover
            //   only after the song changes.
            //
            //   This is what the user means by "in player ui when the song
            //   is using crossfade the ending moment where the actual
            //   transition is happening, there the original cover is coming".
            val incomingSongId = incomingItem?.mediaId?.toLongOrNull()
            val effectiveIncomingArt =
                com.rajatxo.coral.util.SongCoverManager.getEffectiveCover(
                    incomingSongId,
                    incomingArt
                )

            // Broadcast: tell the UI a visual crossfade is starting.
            // ★ Pass the EFFECTIVE cover (custom override if set) so the
            //   incoming overlay shows the user's chosen cover art during
            //   the entire crossfade window.
            CrossfadeVisualState.beginTransition(
                artUri = effectiveIncomingArt,
                title = incomingTitle,
                artist = incomingArtist,
                album = incomingAlbum
            )

            // Start the incoming player
            incoming.play()

            // Crossfade immediately — no gap
            val startTime = System.currentTimeMillis()

            while (job?.isActive == true) {
                val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                val progress = (elapsed / totalFadeMs).coerceIn(0f, 1f)

                // Equal-power crossfade: cos² + sin² = 1
                val angle = progress * PI / 2
                val outVol = cos(angle).toFloat().coerceIn(0f, 1f)
                val inVol = sin(angle).toFloat().coerceIn(0f, 1f)

                outgoing.volume = outVol
                incoming.volume = inVol

                // Broadcast visual progress to the UI
                CrossfadeVisualState.updateProgress(progress)

                if (progress >= 1f || !outgoing.isPlaying) {
                    break
                }
                delay(8) // ~120fps
            }

            // Final state
            outgoing.volume = 0f
            incoming.volume = 1f
            outgoing.stop()
            outgoing.clearMediaItems()

            // End the visual crossfade
            CrossfadeVisualState.endTransition()

            // Handoff: swap roles
            onHandoff(outgoing, incoming)

        } catch (e: Exception) {
            Log.e("Crossfade", "Transition failed", e)
            CrossfadeVisualState.endTransition()
            val activePlayer = active()
            activePlayer?.volume = 1f
        } finally {
            transitioning = false
        }
    }
}
