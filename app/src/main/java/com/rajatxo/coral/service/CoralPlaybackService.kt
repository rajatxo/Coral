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
import com.rajatxo.coral.audio.StudioClarityProcessor
import com.rajatxo.coral.data.prefs.CrossfadeManager
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CoralPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val clarityProcessor = StudioClarityProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clarityObserver: Job? = null
    private var crossfadeJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Build ExoPlayer with a custom RenderersFactory that injects the
        // Studio Clarity audio processor into the audio sink.
        // (Same approach as LastWave — override buildAudioSink)
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

        val player = ExoPlayer.Builder(this, renderersFactory).build()
        // REPEAT_MODE_ALL — playlist loops. Fixes:
        // 1. Auto-advance: when a song ends, the next song plays automatically
        //    (REPEAT_MODE_OFF stops after the last song)
        // 2. Next/prev buttons always work — they wrap around instead of
        //    doing nothing on the first/last song
        player.repeatMode = Player.REPEAT_MODE_ALL

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

        // Crossfade — fade out at the end of each song, fade in at the start.
        // Not a true crossfade (no overlap), but a smooth volume transition.
        // Polls every 100ms, reads crossfadeDuration from CrossfadeManager.
        crossfadeJob = serviceScope.launch {
            while (true) {
                val player = mediaSession?.player
                if (player == null) {
                    delay(200)
                    continue
                }
                val crossfadeDuration = CrossfadeManager.crossfadeDuration.value
                if (crossfadeDuration > 0) {
                    val duration = player.duration
                    val position = player.currentPosition
                    if (duration > 0 && position > 0) {
                        val fadeMs = crossfadeDuration * 1000L
                        val remaining = duration - position
                        when {
                            remaining < fadeMs && remaining > 0 -> {
                                player.volume = (remaining.toFloat() / fadeMs).coerceIn(0.02f, 1f)
                            }
                            position < fadeMs -> {
                                player.volume = (position.toFloat() / fadeMs).coerceIn(0.02f, 1f)
                            }
                            else -> {
                                if (player.volume != 1f) player.volume = 1f
                            }
                        }
                    }
                } else {
                    if (player.volume != 1f) player.volume = 1f
                }
                delay(100)
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
        crossfadeJob?.cancel()
        mediaSession?.player?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
