package com.rajatxo.coral.audio

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rajatxo.coral.data.prefs.CrossfadeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dual-ExoPlayer crossfade controller.
 *
 * When the active song is within crossfadeSeconds of ending, the standby
 * player is loaded with the next song. Both play simultaneously — outgoing
 * fades out (cos) while incoming fades in (sin). At the crossover, the
 * session swaps and the outgoing player is STOPPED (not just muted).
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
                if (crossfadeSeconds > 0 && !transitioning) {
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
                delay(30) // Poll every 30ms for precise trigger
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

            // Load the next song on the standby player
            val nextIndex = (outgoing.currentMediaItemIndex + 1) % outgoing.mediaItemCount
            val mediaItems = (0 until outgoing.mediaItemCount).map {
                outgoing.getMediaItemAt(it)
            }

            incoming.setMediaItems(mediaItems, nextIndex, 0)
            incoming.prepare()
            incoming.volume = 0f
            incoming.playWhenReady = true

            // Wait for the incoming player to be READY (not just started)
            // but cap the wait to avoid missing the fade window
            var waitCount = 0
            while (incoming.playbackState != Player.STATE_READY && waitCount < 30) {
                delay(10)
                waitCount++
            }

            // Start the incoming player
            incoming.play()

            // Crossfade immediately — no gap
            val startTime = System.currentTimeMillis()

            while (job?.isActive == true) {
                val elapsed = (System.currentTimeMillis() - startTime).toFloat()
                val progress = (elapsed / totalFadeMs).coerceIn(0f, 1f)

                // Equal-power crossfade: cos² + sin² = 1
                // At progress=0: outgoing=1, incoming=0
                // At progress=0.5: outgoing≈0.7, incoming≈0.7
                // At progress=1: outgoing=0, incoming=1
                val angle = progress * PI / 2
                val outVol = cos(angle).toFloat().coerceIn(0f, 1f)
                val inVol = sin(angle).toFloat().coerceIn(0f, 1f)

                outgoing.volume = outVol
                incoming.volume = inVol

                if (progress >= 1f || !outgoing.isPlaying) {
                    break
                }
                delay(8) // ~120fps for smooth volume ramp
            }

            // Final state: incoming at full, outgoing silent
            outgoing.volume = 0f
            incoming.volume = 1f

            // STOP the outgoing player — don't let it keep playing
            outgoing.stop()
            outgoing.clearMediaItems()

            // Handoff: swap roles
            onHandoff(outgoing, incoming)

        } catch (e: Exception) {
            Log.e("Crossfade", "Transition failed", e)
            val activePlayer = active()
            activePlayer?.volume = 1f
        } finally {
            transitioning = false
        }
    }
}
