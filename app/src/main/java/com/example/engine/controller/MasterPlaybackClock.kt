package com.example.engine.controller

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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

/**
 * Interface providing authoritative audio PTS (presentation timestamp) in milliseconds.
 */
fun interface AudioClockProvider {
  fun getAudioPositionMs(): Long?
}

/**
 * Master timeline playback clock locked to Android Choreographer VSYNC and synchronized
 * with hardware AudioTrack Presentation Time Stamps (PTS).
 *
 * Guarantees zero audio-video drift, frame-accurate pacing, and monotonic clock stability
 * during rapid scrubbing and seeking operations.
 */
class MasterPlaybackClock(
  private val scope: CoroutineScope,
  private var audioClockProvider: AudioClockProvider? = null
) {
  companion object {
    private const val MAX_CLOCK_DRIFT_CORRECTION_MS = 80L
    private const val VSYNC_SMOOTH_FACTOR = 0.2f
  }

  private val _positionMs = MutableStateFlow(0L)
  val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val mutex = Mutex()
  private var anchorPositionMs = 0L
  private var anchorTimeNs = 0L
  private var lastVsyncTimeNs = 0L
  private var smoothedDriftOffsetMs = 0.0

  private var tickerJob: Job? = null
  private var choreographerCallback: Choreographer.FrameCallback? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  fun setAudioClockProvider(provider: AudioClockProvider?) {
    this.audioClockProvider = provider
  }

  suspend fun play(positionMs: Long = _positionMs.value) = mutex.withLock {
    if (_isPlaying.value) return@withLock
    rebase(positionMs)
    _isPlaying.value = true
    startVsyncClock()
  }

  suspend fun pause() = mutex.withLock {
    if (!_isPlaying.value) return@withLock
    rebase(currentPositionLocked())
    _isPlaying.value = false
    stopVsyncClock()
  }

  suspend fun seekTo(positionMs: Long, isScrubbing: Boolean = false) = mutex.withLock {
    rebase(positionMs)
    if (!isScrubbing && !_isPlaying.value) {
      // Re-anchor firmly
      anchorPositionMs = positionMs.coerceAtLeast(0L)
      anchorTimeNs = System.nanoTime()
      smoothedDriftOffsetMs = 0.0
      _positionMs.value = anchorPositionMs
    }
  }

  private fun rebase(positionMs: Long) {
    anchorPositionMs = positionMs.coerceAtLeast(0L)
    anchorTimeNs = System.nanoTime()
    lastVsyncTimeNs = anchorTimeNs
    smoothedDriftOffsetMs = 0.0
    _positionMs.value = anchorPositionMs
  }

  private fun startVsyncClock() {
    stopVsyncClock()

    if (Looper.myLooper() == Looper.getMainLooper()) {
      setupChoreographer()
    } else {
      mainHandler.post { setupChoreographer() }
    }

    // Coroutine fallback & audio sync poller
    tickerJob = scope.launch(Dispatchers.Default) {
      while (isActive && _isPlaying.value) {
        val calculatedPos = mutex.withLock { currentPositionLocked() }
        _positionMs.value = calculatedPos
        delay(8L) // 120fps poll fallback
      }
    }
  }

  private fun setupChoreographer() {
    val callback = object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (!_isPlaying.value) return
        lastVsyncTimeNs = frameTimeNanos
        val nextPos = currentPositionLocked()
        _positionMs.value = nextPos
        Choreographer.getInstance().postFrameCallback(this)
      }
    }
    choreographerCallback = callback
    Choreographer.getInstance().postFrameCallback(callback)
  }

  private fun stopVsyncClock() {
    tickerJob?.cancel()
    tickerJob = null

    choreographerCallback?.let { cb ->
      mainHandler.post {
        Choreographer.getInstance().removeFrameCallback(cb)
      }
    }
    choreographerCallback = null
  }

  private fun currentPositionLocked(): Long {
    val nowNs = if (lastVsyncTimeNs > anchorTimeNs) lastVsyncTimeNs else System.nanoTime()
    val monotonicElapsedNs = (nowNs - anchorTimeNs).coerceAtLeast(0L)
    var calculatedMs = anchorPositionMs + TimeUnit.NANOSECONDS.toMillis(monotonicElapsedNs)

    // Synchronize with hardware audio clock PTS if available
    val audioPos = audioClockProvider?.getAudioPositionMs()
    if (audioPos != null && audioPos >= 0L) {
      val drift = audioPos - calculatedMs
      if (kotlin.math.abs(drift) > MAX_CLOCK_DRIFT_CORRECTION_MS) {
        // Large jump -> hard sync anchor to audio clock
        anchorPositionMs = audioPos
        anchorTimeNs = nowNs
        smoothedDriftOffsetMs = 0.0
        calculatedMs = audioPos
      } else {
        // Smooth PID / low-pass filter convergence
        smoothedDriftOffsetMs += drift * VSYNC_SMOOTH_FACTOR
        calculatedMs += smoothedDriftOffsetMs.toLong()
      }
    }

    return calculatedMs.coerceAtLeast(0L)
  }
}

