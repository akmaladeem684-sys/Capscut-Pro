package com.example.engine.controller

import android.os.SystemClock
import android.util.Log
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single master-timeline clock for editor preview.
 *
 * Media3/player state is a source renderer only. A player reaching STATE_ENDED is a
 * clip boundary event, never the end of the project timeline. The master clock keeps
 * advancing through video, image, gaps and media-type transitions.
 */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long, Boolean) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object {
    private const val TAG = "TimelineSyncManager"
    private const val SYNC_INTERVAL_MS = 16L
  }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var syncJob: Job? = null
  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var masterPlaying = false
  private var lastTickElapsedMs = 0L

  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  val isPlaying: Boolean get() = masterPlaying

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    val bounded = _timelinePositionMs.value.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    _timelinePositionMs.value = bounded
    playbackController.updateTimelinePosition(bounded)
    onTimelinePositionUpdated(bounded)
    activeClip = findClipAt(bounded)
  }

  fun setActiveClip(clip: VideoClip?) { activeClip = clip }

  fun setPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    _timelinePositionMs.value = bounded
    playbackController.updateTimelinePosition(bounded)
    onTimelinePositionUpdated(bounded)
    activeClip = findClipAt(bounded)
  }

  /** Start the master clock. This is valid even when the current clip is an image. */
  fun startSyncLoop() {
    stopSyncLoop()
    if (currentTimeline.totalDurationMs <= 0L) return
    masterPlaying = true
    lastTickElapsedMs = SystemClock.elapsedRealtime()
    syncJob = scope.launch {
      while (isActive && masterPlaying) {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsedMs).coerceIn(0L, 100L)
        lastTickElapsedMs = now
        val next = (_timelinePositionMs.value + delta)
          .coerceAtMost(currentTimeline.totalDurationMs)
        publishPosition(next)

        val nextClip = findClipAt(next)
        if (nextClip?.id != activeClip?.id) {
          activeClip = nextClip
          onClipTransition(nextClip, next, true)
        }

        if (next >= currentTimeline.totalDurationMs) {
          finishPlayback()
          break
        }
        delay(SYNC_INTERVAL_MS)
      }
    }
  }

  private fun publishPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    if (_timelinePositionMs.value != bounded) {
      _timelinePositionMs.value = bounded
      playbackController.updateTimelinePosition(bounded)
      onTimelinePositionUpdated(bounded)
    }
  }

  /** Media3 STATE_ENDED is only a source-clip boundary. */
  fun handlePlayerEnded() {
    if (!masterPlaying) return
    val position = _timelinePositionMs.value
    val nextClip = findClipAt(position + 1L)
      ?: currentTimeline.videoClips
        .asSequence()
        .filter { it.timelineStartMs > position }
        .minByOrNull { it.timelineStartMs }

    if (nextClip != null) {
      if (activeClip?.id != nextClip.id) {
        activeClip = nextClip
        publishPosition(nextClip.timelineStartMs)
        onClipTransition(nextClip, nextClip.timelineStartMs, true)
      }
    } else if (position >= currentTimeline.totalDurationMs) {
      finishPlayback()
    }
  }

  private fun finishPlayback() {
    if (!masterPlaying) return
    masterPlaying = false
    syncJob?.cancel()
    syncJob = null
    Log.d(TAG, "Timeline playback completed at ${currentTimeline.totalDurationMs}ms")
    playbackController.pause()
    publishPosition(currentTimeline.totalDurationMs)
    activeClip = findClipAt((currentTimeline.totalDurationMs - 1L).coerceAtLeast(0L))
    onPlaybackEnded()
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }

  fun stopSyncLoop() {
    masterPlaying = false
    syncJob?.cancel()
    syncJob = null
  }

  fun release() {
    stopSyncLoop()
    scope.cancel()
  }
}
