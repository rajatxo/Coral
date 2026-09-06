package com.rajatxo.coral.data.premium

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MutableStateFlow
import kotlinx.coroutines.StateFlow
import kotlinx.coroutines.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coral's sleep timer.
 *
 * When active, this counts down from [remainingMs] to 0. At 0, it fires
 * [onComplete] (which the caller wires up to MediaController.pause()).
 *
 * Two modes:
 *  - **Timed** — sleep after 5/15/30/60 minutes.
 *  - **End of current song** — sleep after the current song finishes. The
 *    caller will trigger this externally via [cancel] when the song ends.
 *
 * Exposed as a StateFlow so any UI (e.g. a sleep timer indicator on the
 * now-playing screen) can react to timer state changes.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onComplete: () -> Unit
) {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    /**
     * Start a timed countdown for [durationMs]. Cancels any previous timer.
     */
    fun startTimed(durationMs: Long) {
        cancel()
        val endTimeMs = System.currentTimeMillis() + durationMs
        _state.value = SleepTimerState(
            active = true,
            endAtMs = endTimeMs,
            endOfSong = false
        )
        countdownJob = scope.launch {
            while (true) {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    _state.value = SleepTimerState()
                    onComplete()
                    return@launch
                }
                _state.value = _state.value.copy(endAtMs = endTimeMs)
                delay(1000L)
            }
        }
    }

    /**
     * Mark the timer to fire when the current song ends. The caller is
     * responsible for calling [onSongEnd] when the player emits an
     * onMediaItemTransition event.
     */
    fun startEndOfSong() {
        cancel()
        _state.value = SleepTimerState(
            active = true,
            endAtMs = null,
            endOfSong = true
        )
    }

    /**
     * Called externally when a new song starts. If end-of-song mode is
     * active, this triggers completion.
     */
    fun onSongEnd() {
        if (_state.value.active && _state.value.endOfSong) {
            cancel()
            onComplete()
        }
    }

    /** Cancel any active timer. */
    fun cancel() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState()
    }

    /** Add minutes to a running timer (extends sleep). No-op if not active. */
    fun extend(minutes: Int) {
        val current = _state.value
        if (!current.active || current.endOfSong || current.endAtMs == null) return
        val newEnd = current.endAtMs + minutes * 60_000L
        _state.value = current.copy(endAtMs = newEnd)
        // Restart the timed countdown to pick up the new end time
        val remaining = newEnd - System.currentTimeMillis()
        if (remaining > 0) {
            startTimed(remaining)
        }
    }
}

/**
 * Snapshot of the sleep timer's state.
 *
 * @param active      True if a timer is running.
 * @param endAtMs     Wall-clock time when a timed timer ends (null if end-of-song).
 * @param endOfSong   True if the timer fires on song end (vs after N minutes).
 */
data class SleepTimerState(
    val active: Boolean = false,
    val endAtMs: Long? = null,
    val endOfSong: Boolean = false
)
