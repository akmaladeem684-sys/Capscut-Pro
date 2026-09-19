package com.example.engine.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Atomic timeline clock shared by playback projections and timeline audio. */
class MasterPlaybackClock(private val scope: CoroutineScope) {
  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val mutex = Mutex()
  private var anchorPositionMs = 0L
  private var anchorTimeNs = 0L
  private var ticker: Job? = null

  suspend fun play(positionMs: Long = _positionMs.value) = mutex.withLock {
    if (_isPlaying.value) return
    rebase(positionMs)
    _isPlaying.value = true
    ticker?.cancel()
    ticker = scope.launch(Dispatchers.Default) {
      while (isActive && _isPlaying.value) {
        _positionMs.value = mutex.withLock { currentPositionLocked() }
        delay(10L)
      }
    }
  }

  suspend fun pause() = mutex.withLock {
    if (!_isPlaying.value) return
    rebase(currentPositionLocked())
    _isPlaying.value = false
    ticker?.cancel()
    ticker = null
  }

  suspend fun seekTo(positionMs: Long) = mutex.withLock { rebase(positionMs) }

  private fun rebase(positionMs: Long) {
    anchorPositionMs = positionMs.coerceAtLeast(0L)
    anchorTimeNs = System.nanoTime()
    _positionMs.value = anchorPositionMs
  }

  private fun currentPositionLocked(): Long {
    val elapsedNs = (System.nanoTime() - anchorTimeNs).coerceAtLeast(0L)
    return anchorPositionMs + TimeUnit.NANOSECONDS.toMillis(elapsedNs)
  }
}
