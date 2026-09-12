package com.rajatxo.coral.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rajatxo.coral.MainActivity
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

    override fun onCreate() {
        super.onCreate()

        // Build the ExoPlayer with the Studio Clarity audio processor.
        // @UnstableApi: ExoPlayer.Builder.setAudioProcessors is experimental.
        val player = ExoPlayer.Builder(this)
            .setAudioProcessors(arrayOf(clarityProcessor))
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // Observe the studio clarity toggle — when it changes, the processor
        // automatically picks it up on the next audio buffer (via isActive()).
        // No need to reconfigure the player — just flush the processor.
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
        mediaSession?.player?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
