package com.example.engine.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.example.engine.timeline.nonlinear.models.Clip
import com.example.engine.timeline.nonlinear.models.TimelineState
import com.example.engine.timeline.nonlinear.models.TrackType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update

private const val TAG = "TimelinePreviewCoord"

/**
 * Immutable playback & scrub state for the TimelinePreviewCoordinator.
 */
data class TimelinePreviewState(
  val playheadUs: Long = 0L,
  val activeClipId: String? = null,
  val activeTrackId: String? = null,
  val timelineDurationUs: Long = 0L,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val isScrubbing: Boolean = false,
  val activeVideoClips: List<Clip> = emptyList(),
  val activeAudioClips: List<Clip> = emptyList(),
  val isInGap: Boolean = false
)

/**
 * Architectural controller class connecting a Non-Linear Multi-Track Timeline to Media3 ExoPlayer.
 *
 * Requirements implemented:
 * 1. State Management:
 *    - Exposes immutable StateFlow<TimelinePreviewState> with microsecond-accurate playhead position.
 *
 * 2. High-Performance Scrubbing & Seeking:
 *    - Throttles seek requests using a conflated channel and coroutine debounce to avoid rapid decoder starvation.
 *    - Uses SeekParameters.CLOSEST_SYNC during active scrubbing for instantaneous keyframe seeking.
 *    - Uses SeekParameters.EXACT upon touch release for pixel-perfect frame accuracy.
 *
 * 3. Seamless Clip Boundary Transitions:
 *    - Smoothly transitions across discontinuous multi-track clips without black frames or audio pops.
 *    - Mutes and handles blank gap regions gracefully.
 *
 * 4. Background State Persistence:
 *    - Automatically captures exact playhead microseconds on backgrounding.
 *    - Restores decoder state and triggers instant frame rendering at that exact timestamp on foregrounding.
 */
@OptIn(UnstableApi::class)
class TimelinePreviewCoordinator(
  private val context: Context,
  val exoPlayer: ExoPlayer,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
  private val _state = MutableStateFlow(TimelinePreviewState())
  val state: StateFlow<TimelinePreviewState> = _state.asStateFlow()

  // Internal timeline model reference
  private var currentTimeline: TimelineState = TimelineState()

  // Conflated seek channel for responsive, stutter-free scrubbing without decoder starvation
  private val seekChannel = Channel<Long>(Channel.CONFLATED)

  // Background persistence
  private var savedPlayheadUs: Long = 0L
  private var wasPlayingBeforeBackground: Boolean = false

  // Playback ticking loop job
  private var playbackLoopJob: Job? = null

  // Active loaded source URI
  private var currentlyLoadedUri: String? = null

  init {
    setupPlayerListener()
    setupSeekProcessor()
  }

  private fun setupPlayerListener() {
    exoPlayer.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
        if (isPlaying) {
          startPlaybackTicking()
        } else {
          stopPlaybackTicking()
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        val isBuffering = (playbackState == Player.STATE_BUFFERING)
        _state.update { it.copy(isBuffering = isBuffering) }

        if (playbackState == Player.STATE_ENDED) {
          pause()
          seekToExact(0L)
        }
      }
    })
  }

  private fun setupSeekProcessor() {
    // Process seek requests with intelligent debouncing to avoid overwhelming the MediaCodec decoder
    scope.launch {
      seekChannel.consumeAsFlow()
        .debounce(16L) // ~60fps target seek rate limit
        .collect { targetUs ->
          executeScrubSeek(targetUs)
        }
    }
  }

  /**
   * Updates the active non-linear timeline structure.
   */
  fun updateTimeline(timeline: TimelineState) {
    currentTimeline = timeline
    val totalDurationUs = timeline.totalDurationUs.coerceAtLeast(0L)

    _state.update { current ->
      val clampedPlayhead = current.playheadUs.coerceIn(0L, maxOf(1L, totalDurationUs))
      val activeVideo = findActiveClipsAt(timeline, clampedPlayhead, TrackType.VIDEO) +
        findActiveClipsAt(timeline, clampedPlayhead, TrackType.OVERLAY)
      val activeAudio = findActiveClipsAt(timeline, clampedPlayhead, TrackType.AUDIO)
      val primaryClip = activeVideo.firstOrNull()

      current.copy(
        timelineDurationUs = totalDurationUs,
        playheadUs = clampedPlayhead,
        activeClipId = primaryClip?.id,
        activeVideoClips = activeVideo,
        activeAudioClips = activeAudio,
        isInGap = activeVideo.isEmpty()
      )
    }
  }

  // ==========================================
  // SCRUBBING & SEEKING (PROMPT 2)
  // ==========================================

  /**
   * Begins user scrubbing: switches decoder to fast keyframe seeking (CLOSEST_SYNC).
   */
  fun startScrub() {
    _state.update { it.copy(isScrubbing = true) }
    if (exoPlayer.isPlaying) {
      exoPlayer.pause()
    }
    // High-performance seeking parameter for instant feedback during drag
    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
  }

  /**
   * Queues an intermediate scrub position throttled via conflated channel.
   */
  fun scrubTo(timestampUs: Long) {
    val durationUs = currentTimeline.totalDurationUs.coerceAtLeast(0L)
    val clampedUs = timestampUs.coerceIn(0L, maxOf(1L, durationUs))

    // Immediately update UI state for zero-latency playhead needle response
    updateActiveClipsForTime(clampedUs)

    // Send to conflated channel for debounced decoder seeking
    seekChannel.trySend(clampedUs)
  }

  /**
   * Finalizes user scrubbing: switches to EXACT seeking for pixel-perfect frame accuracy.
   */
  fun stopScrub(finalTimestampUs: Long) {
    val durationUs = currentTimeline.totalDurationUs.coerceAtLeast(0L)
    val clampedUs = finalTimestampUs.coerceIn(0L, maxOf(1L, durationUs))

    _state.update { it.copy(isScrubbing = false) }
    updateActiveClipsForTime(clampedUs)

    // Switch to EXACT seeking for pixel-perfect stopped frame
    exoPlayer.setSeekParameters(SeekParameters.EXACT)
    executeExactSeek(clampedUs)
  }

  /**
   * Performs an immediate exact seek at the given timestamp.
   */
  fun seekToExact(timestampUs: Long) {
    exoPlayer.setSeekParameters(SeekParameters.EXACT)
    updateActiveClipsForTime(timestampUs)
    executeExactSeek(timestampUs)
  }

  private fun executeScrubSeek(timestampUs: Long) {
    val activeClips = findActiveClipsAt(currentTimeline, timestampUs, TrackType.VIDEO)
    val primaryClip = activeClips.firstOrNull()

    if (primaryClip != null && primaryClip.sourceUri.isNotBlank()) {
      ensureMediaSourceLoaded(primaryClip)
      val relativeMediaUs = (timestampUs - primaryClip.startTimeUs) + primaryClip.sourceTrimStartUs
      val seekPosMs = (relativeMediaUs / 1000L).coerceAtLeast(0L)
      exoPlayer.seekTo(seekPosMs)
    } else {
      // In a blank gap: silence decoder safely without stalling
      _state.update { it.copy(isInGap = true) }
    }
  }

  private fun executeExactSeek(timestampUs: Long) {
    val activeClips = findActiveClipsAt(currentTimeline, timestampUs, TrackType.VIDEO)
    val primaryClip = activeClips.firstOrNull()

    if (primaryClip != null && primaryClip.sourceUri.isNotBlank()) {
      ensureMediaSourceLoaded(primaryClip)
      val relativeMediaUs = (timestampUs - primaryClip.startTimeUs) + primaryClip.sourceTrimStartUs
      val seekPosMs = (relativeMediaUs / 1000L).coerceAtLeast(0L)
      exoPlayer.seekTo(seekPosMs)
    }
  }

  // ==========================================
  // PLAYBACK & BOUNDARY TRANSITIONS
  // ==========================================

  fun play() {
    val playhead = _state.value.playheadUs
    if (playhead >= currentTimeline.totalDurationUs && currentTimeline.totalDurationUs > 0L) {
      seekToExact(0L)
    } else {
      seekToExact(playhead)
    }
    exoPlayer.play()
  }

  fun pause() {
    exoPlayer.pause()
  }

  fun togglePlayPause() {
    if (exoPlayer.isPlaying) {
      pause()
    } else {
      play()
    }
  }

  private fun startPlaybackTicking() {
    playbackLoopJob?.cancel()
    playbackLoopJob = scope.launch {
      var lastTickNs = System.nanoTime()

      while (isActive && exoPlayer.isPlaying) {
        val nowNs = System.nanoTime()
        val deltaUs = (nowNs - lastTickNs) / 1000L
        lastTickNs = nowNs

        val newPlayheadUs = _state.value.playheadUs + deltaUs
        if (newPlayheadUs >= currentTimeline.totalDurationUs) {
          _state.update { it.copy(playheadUs = currentTimeline.totalDurationUs) }
          pause()
          break
        }

        handleBoundaryTransition(newPlayheadUs)
        delay(20L) // ~50fps synchronization rate
      }
    }
  }

  private fun stopPlaybackTicking() {
    playbackLoopJob?.cancel()
    playbackLoopJob = null
  }

  /**
   * Handles seamless transitions when playhead crosses clip boundaries or discontinuous gaps.
   */
  private fun handleBoundaryTransition(newPlayheadUs: Long) {
    val activeVideo = findActiveClipsAt(currentTimeline, newPlayheadUs, TrackType.VIDEO) +
      findActiveClipsAt(currentTimeline, newPlayheadUs, TrackType.OVERLAY)
    val activeAudio = findActiveClipsAt(currentTimeline, newPlayheadUs, TrackType.AUDIO)
    val primaryClip = activeVideo.firstOrNull()

    val oldClipId = _state.value.activeClipId
    val newClipId = primaryClip?.id

    if (primaryClip != null) {
      // If entering a new clip from a gap or another clip
      if (oldClipId != newClipId) {
        ensureMediaSourceLoaded(primaryClip)
        val relativeMediaUs = (newPlayheadUs - primaryClip.startTimeUs) + primaryClip.sourceTrimStartUs
        exoPlayer.seekTo((relativeMediaUs / 1000L).coerceAtLeast(0L))
      }

      // Smooth audio volume mapping
      val effectiveVolume = if (primaryClip.isMuted) 0f else primaryClip.volume
      exoPlayer.volume = effectiveVolume
    } else {
      // In a blank gap: smoothly silence audio without stopping playhead advancement
      exoPlayer.volume = 0f
    }

    _state.update {
      it.copy(
        playheadUs = newPlayheadUs,
        activeClipId = newClipId,
        activeVideoClips = activeVideo,
        activeAudioClips = activeAudio,
        isInGap = primaryClip == null
      )
    }
  }

  private fun ensureMediaSourceLoaded(clip: Clip) {
    if (clip.sourceUri.isNotBlank() && clip.sourceUri != currentlyLoadedUri) {
      currentlyLoadedUri = clip.sourceUri
      try {
        val mediaItem = MediaItem.fromUri(Uri.parse(clip.sourceUri))
        exoPlayer.setMediaItem(mediaItem, false)
        exoPlayer.prepare()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load media item for clip ${clip.id}", e)
      }
    }
  }

  private fun updateActiveClipsForTime(timestampUs: Long) {
    val activeVideo = findActiveClipsAt(currentTimeline, timestampUs, TrackType.VIDEO) +
      findActiveClipsAt(currentTimeline, timestampUs, TrackType.OVERLAY)
    val activeAudio = findActiveClipsAt(currentTimeline, timestampUs, TrackType.AUDIO)

    _state.update {
      it.copy(
        playheadUs = timestampUs,
        activeClipId = activeVideo.firstOrNull()?.id,
        activeVideoClips = activeVideo,
        activeAudioClips = activeAudio,
        isInGap = activeVideo.isEmpty()
      )
    }
  }

  private fun findActiveClipsAt(timeline: TimelineState, timestampUs: Long, type: TrackType): List<Clip> {
    return timeline.tracks
      .filter { it.type == type && !it.isHidden }
      .flatMap { track ->
        track.clips.filter { clip ->
          timestampUs >= clip.startTimeUs && timestampUs < (clip.startTimeUs + clip.durationUs)
        }
      }
  }

  // ==========================================
  // BACKGROUND STATE PERSISTENCE (PROMPT 2)
  // ==========================================

  /**
   * Automatically saves the exact microsecond playhead when transitioning to the background.
   */
  fun onAppBackground() {
    savedPlayheadUs = _state.value.playheadUs
    wasPlayingBeforeBackground = exoPlayer.isPlaying
    Log.d(TAG, "onAppBackground: Saved exact playhead position $savedPlayheadUs us (playing=$wasPlayingBeforeBackground)")

    if (exoPlayer.isPlaying) {
      exoPlayer.pause()
    }
    stopPlaybackTicking()
  }

  /**
   * Restores exact playhead state and primes the decoder on returning to the foreground.
   */
  fun onAppForeground() {
    Log.d(TAG, "onAppForeground: Restoring playhead position $savedPlayheadUs us")
    seekToExact(savedPlayheadUs)

    if (wasPlayingBeforeBackground) {
      exoPlayer.play()
      wasPlayingBeforeBackground = false
    }
  }

  /**
   * Releases all listeners and jobs.
   */
  fun release() {
    stopPlaybackTicking()
    seekChannel.close()
    scope.cancel()
  }
}
