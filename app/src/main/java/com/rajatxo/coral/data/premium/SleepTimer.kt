package com.rajatxo.coral.data.premium

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coral's sleep timer — SINGLETON.
 *
 * WHY SINGLETON:
 *   The previous version created SleepTimer inside HomeScreen's remember{}.
 *   When the Activity was destroyed (task manager kill or rotation), the
 *   SleepTimer instance + its coroutine scope were destroyed too.
 *   The timer stopped, and the capsule vanished.
 *
 *   Now SleepTimer is a singleton with a GLOBAL SupervisorJob scope that
 *   survives Activity destruction. The timer keeps running even if the
 *   app is killed and restarted — because we persist the end time to
 *   SharedPreferences.
 *
 * PERSISTENCE:
 *   When a timer is started, the end time (wall-clock millis) is saved
 *   to SharedPreferences. On app restart, SleepTimer.init() reads the
 *   saved end time and resumes the countdown if it hasn't expired yet.
 *
 * ROTATION:
 *   Because SleepTimer is a singleton + global scope, rotation has no
 *   effect. The timer keeps running through the configuration change.
 */
object SleepTimer {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_END_AT_MS = "sleep_timer_end_at_ms"
    private const val KEY_TOTAL_MS = "sleep_timer_total_ms"
    private const val KEY_END_OF_SONG = "sleep_timer_end_of_song"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var onCompleteCallback: (() -> Unit)? = null

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context, onComplete: () -> Unit) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        onCompleteCallback = onComplete

        // Restore timer from SharedPreferences if it was running
        val savedEndAt = prefs.getLong(KEY_END_AT_MS, -1L)
        val savedTotal = prefs.getLong(KEY_TOTAL_MS, 0L)
        val savedEndOfSong = prefs.getBoolean(KEY_END_OF_SONG, false)

        if (savedEndOfSong) {
            // End-of-song timer — just restore the state
            _state.value = SleepTimerState(
                active = true,
                endAtMs = null,
                endOfSong = true
            )
        } else if (savedEndAt > 0) {
            val remaining = savedEndAt - System.currentTimeMillis()
            if (remaining > 0) {
                // Timer still active — resume it
                _state.value = SleepTimerState(
                    active = true,
                    endAtMs = savedEndAt,
                    endOfSong = false,
                    totalDurationMs = savedTotal
                )
                startCountdown(savedEndAt)
            } else {
                // Timer expired while app was dead — clear it
                clearPrefs()
            }
        }
    }

    private fun startCountdown(endTimeMs: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    _state.value = SleepTimerState()
                    clearPrefs()
                    onCompleteCallback?.invoke()
                    return@launch
                }
                _state.value = _state.value.copy(endAtMs = endTimeMs)
                delay(1000L)
            }
        }
    }

    fun startTimed(durationMs: Long) {
        cancel()
        val endTimeMs = System.currentTimeMillis() + durationMs
        _state.value = SleepTimerState(
            active = true,
            endAtMs = endTimeMs,
            endOfSong = false,
            totalDurationMs = durationMs
        )
        prefs.edit()
            .putLong(KEY_END_AT_MS, endTimeMs)
            .putLong(KEY_TOTAL_MS, durationMs)
            .putBoolean(KEY_END_OF_SONG, false)
            .apply()
        startCountdown(endTimeMs)
    }

    fun startEndOfSong() {
        cancel()
        _state.value = SleepTimerState(
            active = true,
            endAtMs = null,
            endOfSong = true
        )
        prefs.edit()
            .putBoolean(KEY_END_OF_SONG, true)
            .putLong(KEY_END_AT_MS, -1L)
            .apply()
    }

    fun onSongEnd() {
        if (_state.value.active && _state.value.endOfSong) {
            cancel()
            onCompleteCallback?.invoke()
        }
    }

    fun cancel() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState()
        clearPrefs()
    }

    fun extend(minutes: Int) {
        val current = _state.value
        if (!current.active || current.endOfSong || current.endAtMs == null) return
        val newEnd = current.endAtMs + minutes * 60_000L
        val newTotal = current.totalDurationMs + minutes * 60_000L
        _state.value = current.copy(endAtMs = newEnd, totalDurationMs = newTotal)
        prefs.edit()
            .putLong(KEY_END_AT_MS, newEnd)
            .putLong(KEY_TOTAL_MS, newTotal)
            .apply()
        startCountdown(newEnd)
    }

    private fun clearPrefs() {
        prefs.edit()
            .remove(KEY_END_AT_MS)
            .remove(KEY_TOTAL_MS)
            .remove(KEY_END_OF_SONG)
            .apply()
    }
}

data class SleepTimerState(
    val active: Boolean = false,
    val endAtMs: Long? = null,
    val endOfSong: Boolean = false,
    val totalDurationMs: Long = 0L
)
