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
import com.rajatxo.coral.data.prefs.CrossfadeManager
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

    // Dual-player for crossfade
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var crossfadeController: SimpleCrossfadeController? = null

    override fun onCreate() {
        super.onCreate()

        // Build two ExoPlayers — one active, one standby for crossfade
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

        playerA = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        playerA?.repeatMode = Player.REPEAT_MODE_ALL

        playerB = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false  // Only active player handles focus
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        playerB?.repeatMode = Player.REPEAT_MODE_ALL

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, playerA!!)
            .setSessionActivity(pendingIntent)
            .build()

        // Crossfade controller — swaps session between playerA and playerB
        crossfadeController = SimpleCrossfadeController(
            scope = serviceScope,
            active = { mediaSession?.player as? ExoPlayer ?: playerA },
            standby = { playerB },
            onHandoff = { incoming ->
                // Swap the session to the incoming player
                mediaSession?.player = incoming
                // The old active player becomes the standby
                val outgoing = if (incoming == playerA) playerB else playerA
                outgoing?.volume = 1f
                // Swap roles: the standby is now the old active
                playerB = outgoing
                playerA = if (incoming == playerA) playerA else incoming
            }
        )
        crossfadeController?.start()

        clarityObserver = serviceScope.launch {
            SoundHapticsManager.studioClarityEnabled.collect { enabled ->
                clarityProcessor.flush()
            }
        }
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
