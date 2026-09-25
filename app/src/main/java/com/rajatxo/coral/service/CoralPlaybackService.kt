package com.rajatxo.coral.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rajatxo.coral.MainActivity
import com.rajatxo.coral.audio.SimpleCrossfadeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CoralPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var crossfadeController: SimpleCrossfadeController? = null

    // Track which player is active vs standby
    private var activePlayer: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // NOTE: The StudioClarityProcessor (8-band DSP) was previously
        // wired into both players' audio sinks via a custom renderers
        // factory. It was the root cause of:
        //   1. Crossfade glitches — the DSP's internal filter + limiter
        //      state didn't reset cleanly on player swap, causing
        //      audible discontinuities (clicks/pops) during handoff.
        //   2. Auto-next failing — the processor's flush() returned an
        //      empty ByteBuffer which briefly stalled the audio pipeline
        //      at end-of-track, preventing the next track from starting.
        // Both players now use the DEFAULT renderers factory (no custom
        // audio processors). The SoundLab tab has also been removed.
        playerA = buildPlayer(ownsSession = true)
        playerB = buildPlayer(ownsSession = false)

        activePlayer = playerA
        standbyPlayer = playerB

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, playerA!!)
            .setSessionActivity(pendingIntent)
            .build()

        crossfadeController = SimpleCrossfadeController(
            scope = serviceScope,
            active = { activePlayer },
            standby = { standbyPlayer },
            onHandoff = { outgoing, incoming ->
                // Swap the session to the incoming player
                mediaSession?.player = incoming

                // Swap roles — outgoing becomes the new standby
                activePlayer = incoming
                standbyPlayer = outgoing

                // The outgoing player was already stopped + cleared by the controller.
                // Just make sure its volume is reset for the next use.
                outgoing.volume = 1f
            }
        )
        crossfadeController?.start()
    }

    private fun buildPlayer(ownsSession: Boolean): ExoPlayer {
        // Enable decoder fallback — if the hardware decoder doesn't support
        // a codec (e.g. ALAC on some devices, Dolby Atmos, etc.), ExoPlayer
        // falls back to the next available decoder (software if possible).
        // This fixes the "shows playing but no sound" issue with ALAC files.
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ ownsSession
            )
            .setHandleAudioBecomingNoisy(ownsSession)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        return player
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        crossfadeController?.stop()
        playerA?.release()
        playerB?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
