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
            // Previously this always advanced to the next song, which
            // broke loop-one mode (the song moved to the next instead
            // of looping).
            //
            // REPEAT_MODE_ALL / REPEAT_MODE_OFF: advance to the next
            // song (wrapping around at the end of the queue).
            val nextIndex = when (outgoing.repeatMode) {
                Player.REPEAT_MODE_ONE -> outgoing.currentMediaItemIndex
                else -> (outgoing.currentMediaItemIndex + 1) % outgoing.mediaItemCount
            }
            val mediaItems = (0 until outgoing.mediaItemCount).map {
                outgoing.getMediaItemAt(it)
            }

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

            // Broadcast: tell the UI a visual crossfade is starting
            CrossfadeVisualState.beginTransition(
                artUri = incomingArt,
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
