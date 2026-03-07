package com.omega.intervaltimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.intervaltimer.domain.buildTimeline
import com.omega.intervaltimer.model.TimerState
import com.omega.intervaltimer.model.WorkoutConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state

    private var tickerJob: Job? = null

    init {
        rebuildFromConfig(_state.value.config)
    }

    fun applyConfig(config: WorkoutConfig) {
        rebuildFromConfig(config)
    }

    fun startPause() {
        val current = _state.value
        if (current.timeline.isEmpty()) return
        if (!current.isRunning || current.isPaused) {
            _state.update { it.copy(isRunning = true, isPaused = false, isFinished = false) }
            startTicker()
        } else {
            _state.update { it.copy(isPaused = true) }
            stopTicker()
        }
    }

    fun reset() {
        stopTicker()
        val config = _state.value.config
        rebuildFromConfig(config)
    }

    fun next() {
        val current = _state.value
        if (current.timeline.isEmpty()) return
        if (current.currentIndex >= current.timeline.lastIndex) {
            stopTicker()
            _state.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    isFinished = true,
                    remainingSec = 0,
                    totalRemainingSec = 0
                )
            }
            return
        }
        moveToIndex(current.currentIndex + 1)
    }

    private fun rebuildFromConfig(config: WorkoutConfig) {
        stopTicker()
        val timeline = buildTimeline(config)
        val firstDuration = timeline.firstOrNull()?.phase?.durationSec ?: 0
        val total = timeline.sumOf { it.phase.durationSec }
        _state.value = TimerState(
            config = config,
            timeline = timeline,
            isRunning = false,
            isPaused = false,
            isFinished = timeline.isEmpty(),
            currentIndex = 0,
            remainingSec = firstDuration,
            totalRemainingSec = total
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val s = _state.value
                if (!s.isRunning || s.isPaused || s.isFinished) continue

                if (s.remainingSec > 1) {
                    _state.update {
                        it.copy(
                            remainingSec = it.remainingSec - 1,
                            totalRemainingSec = (it.totalRemainingSec - 1).coerceAtLeast(0)
                        )
                    }
                } else {
                    next()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun moveToIndex(newIndex: Int) {
        val timeline = _state.value.timeline
        if (newIndex !in timeline.indices) return
        val elapsedBefore = timeline.take(newIndex).sumOf { it.phase.durationSec }
        val total = timeline.sumOf { it.phase.durationSec }
        _state.update {
            it.copy(
                currentIndex = newIndex,
                remainingSec = timeline[newIndex].phase.durationSec,
                totalRemainingSec = (total - elapsedBefore).coerceAtLeast(0)
            )
        }
    }

    override fun onCleared() {
        stopTicker()
        super.onCleared()
    }
}
