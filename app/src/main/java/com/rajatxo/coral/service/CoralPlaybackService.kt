package com.rajatxo.coral.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rajatxo.coral.MainActivity
import com.rajatxo.coral.audio.SimpleCrossfadeController
import com.rajatxo.coral.audio.StudioClarityProcessor
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CoralPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val clarityProcessor = StudioClarityProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clarityObserver: Job? = null

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var crossfadeController: SimpleCrossfadeController? = null

    // Track which player is active vs standby
    private var activePlayer: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(clarityProcessor))
                    .build()
            }
        }

        playerA = buildPlayer(renderersFactory, ownsSession = true)
        playerB = buildPlayer(renderersFactory, ownsSession = false)

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

        clarityObserver = serviceScope.launch {
            SoundHapticsManager.studioClarityEnabled.collect { enabled ->
                clarityProcessor.flush()
            }
        }
    }

    private fun buildPlayer(
        renderersFactory: DefaultRenderersFactory,
        ownsSession: Boolean
    ): ExoPlayer {
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
        clarityObserver?.cancel()
        crossfadeController?.stop()
        playerA?.release()
        playerB?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
