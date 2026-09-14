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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple dual-ExoPlayer crossfade controller.
 *
 * When the active song is within [crossfadeSeconds] of ending, the standby
 * player is loaded with the next song and started silently. Both players
 * play simultaneously — the outgoing fades out (cos curve) while the
 * incoming fades in (sin curve). At the crossover, the session swaps
 * to the incoming player.
 *
 * Inspired by BitChord's approach but stripped down: no smart analysis,
 * no transition filters, no spatial audio. Just a clean equal-power
 * crossfade using sin²+cos²=1.
 */
@UnstableApi
class SimpleCrossfadeController(
    private val scope: CoroutineScope,
    private val active: () -> ExoPlayer?,
    private val standby: () -> ExoPlayer?,
    private val onHandoff: (incoming: ExoPlayer) -> Unit,
) {
    private var job: Job? = null
    private var transitioning = false

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (kotlinx.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) {
                val crossfadeSeconds = CrossfadeManager.crossfadeDuration.value
                if (crossfadeSeconds > 0 && !transitioning) {
                    val player = active() ?: run { delay(200); continue }
                    val duration = player.duration
                    val position = player.currentPosition
                    if (duration > 0 && position > 0 && player.isPlaying) {
                        val remaining = duration - position
                        val fadeMs = crossfadeSeconds * 1000L

                        if (remaining <= fadeMs && remaining > 0) {
                            // Start the crossfade
                            startTransition(player, standby(), crossfadeSeconds)
                        }
                    }
                }
                delay(50) // Poll every 50ms for precise timing
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

            // Load the next song on the standby player
            val nextIndex = (outgoing.currentMediaItemIndex + 1) % outgoing.mediaItemCount
            val mediaItems = (0 until outgoing.mediaItemCount).map {
                outgoing.getMediaItemAt(it)
            }

            incoming.setMediaItems(mediaItems, nextIndex, 0)
            incoming.prepare()
            incoming.volume = 0f
            incoming.playWhenReady = true
            incoming.play()

            // Wait a tiny bit for the incoming player to start
            delay(100)

            // Crossfade: outgoing cos↓, incoming sin↑
            val startTime = System.currentTimeMillis()
            val totalFadeMs = remaining.coerceAtMost(fadeMs).toFloat()

            while (kotlinx.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) {
                val progress = (elapsed / totalFadeMs).coerceIn(0f, 1f)

                // Equal-power crossfade: cos² + sin² = 1
                val outVol = cos(progress * PI / 2).toFloat().coerceIn(0f, 1f)
                val inVol = sin(progress * PI / 2).toFloat().coerceIn(0f, 1f)

                outgoing.volume = outVol
                incoming.volume = inVol

                if (progress >= 1f || !outgoing.isPlaying) {
                    break
                }
                delay(16) // ~60fps
            }

            // Handoff: move session to incoming player
            outgoing.volume = 0f
            incoming.volume = 1f
            onHandoff(incoming)

        } catch (e: Exception) {
            Log.e("Crossfade", "Transition failed", e)
            // Fallback: just let the active player handle it normally
            val activePlayer = active()
            activePlayer?.volume = 1f
        } finally {
            transitioning = false
        }
    }
}
