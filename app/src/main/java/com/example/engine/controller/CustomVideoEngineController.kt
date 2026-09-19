package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Project-level preview coordinator; PlaybackController is the only player command owner. */
class CustomVideoEngineController(
  private val context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit,
  private val onPlaybackEnded: () -> Unit = {}
) {
  companion object { private const val TAG = "CustomVideoEngineCtrl" }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  val decoderManager = DecoderManager()
  val renderCacheManager = RenderCacheManager()
  private val compositionEngine = VideoCompositionEngine(context)
  val gpuRenderManager = GpuRenderManager(context, compositionEngine, renderCacheManager)

  // Indirect reference avoids Kotlin's recursive initializer/type-inference cycle:
  // PlaybackController is created before TimelineSyncManager, but STATE_ENDED must
  // still be routed to the timeline transition handler once the manager exists.
  private var timelineSyncManagerRef: TimelineSyncManager? = null

  val playbackController = PlaybackController(
    context = context,
    onTimelinePositionChanged = onTimelinePositionChanged,
    onPlaybackEnded = { timelineSyncManagerRef?.handlePlayerEnded() },
    onPlayerError = { error -> handlePlayerError(error) }
  )

  val playbackManager: PlaybackManager get() = playbackController.playbackManager
  val player get() = playbackController.player
  val surfaceManager = SurfaceManager(playbackController)

  val timelineSyncManager = TimelineSyncManager(
    playbackController = playbackController,
    onTimelinePositionUpdated = { posMs ->
      _engineState.value = _engineState.value.copy(currentPosition = posMs, isPlaying = playbackController.isPlaying)
      onTimelinePositionChanged(posMs)
    },
    onClipTransition = { clip, pos, resumeAfter -> handleClipTransition(clip, pos, resumeAfter) },
    onPlaybackEnded = {
      _engineState.value = _engineState.value.copy(
        playbackState = EnginePlaybackState.COMPLETED,
        isPlaying = false
      )
      onPlaybackEnded()
    }
  )

  private val _engineState = MutableStateFlow(
    VideoEngineState(
      playbackState = EnginePlaybackState.IDLE,
      decoderState = decoderManager.decoderState,
      renderingState = RenderingState.IDLE
    )
  )
  val engineState: StateFlow<VideoEngineState> = _engineState.asStateFlow()

  private var currentTimeline = Timeline()
  private var activeClip: VideoClip? = null
  private var loadedClipId: String? = null
  private var loadedUri: String? = null
  private var currentPosMs = 0L
  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private val seekSequence = AtomicLong(0L)
  private var coalescedSeekJob: Job? = null
  private val _trimPlaybackPositionMs = MutableStateFlow(0L)
  val trimPlaybackPositionMs: StateFlow<Long> = _trimPlaybackPositionMs.asStateFlow()

  val isScrubbing: Boolean get() = isScrubbingMode
  val isPlaying: Boolean get() = playbackController.isPlaying
  val currentPosition: Long get() = currentPosMs

  init {
    timelineSyncManagerRef = timelineSyncManager
    surfaceManager.onSurfaceAvailabilityChanged = { available ->
      _engineState.value = _engineState.value.copy(surfaceAvailable = available)
      if (available && activeClip != null && !isPlaying) {
        val clip = activeClip!!
        ensureClipLoaded(clip)
        playbackController.seekTo(clip.timelineToSourceMs(currentPosMs), resumeAfter = false, exact = true)
      }
    }
  }

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    timelineSyncManager.updateTimeline(timeline)
    currentPosMs = currentPosMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    activeClip = timelineSyncManager.findClipAt(currentPosMs)
    timelineSyncManager.setActiveClip(activeClip)
    _engineState.value = _engineState.value.copy(duration = timeline.totalDurationMs)
    val clip = activeClip
    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      playbackController.setPlaybackSpeed(clip.speed)
      playbackController.setVolume(if (clip.isMuted) 0f else clip.volume)
      playbackController.seekTo(clip.timelineToSourceMs(currentPosMs), resumeAfter = isPlaying, exact = true)
      _engineState.value = _engineState.value.copy(isReady = true)
    } else {
      if (clip == null) playbackController.pause()
      _engineState.value = _engineState.value.copy(playbackState = EnginePlaybackState.READY, isReady = true)
    }
  }

  fun seekTo(timelinePosMs: Long) {
    // A user seek is an explicit pause + authoritative master-position update.
    timelineSyncManager.stopSyncLoop()
    playbackController.pause()
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    timelineSyncManager.setPosition(bounded)
    activeClip = timelineSyncManager.findClipAt(bounded)
    timelineSyncManager.setActiveClip(activeClip)
    val generation = playbackController.invalidatePendingSeeks()
    val clip = activeClip
    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      playbackController.setPlaybackSpeed(clip.speed)
      playbackController.seekTo(clip.timelineToSourceMs(bounded), resumeAfter = false, exact = true, generation = generation)
    }
    _engineState.value = _engineState.value.copy(currentPosition = bounded, playbackState = if (isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED)
  }

  fun startScrubbing() {
    isScrubbingMode = true
    wasPlayingBeforeScrub = isPlaying
    if (wasPlayingBeforeScrub) playbackController.pause()
  }

  fun scrubTo(timelinePosMs: Long) {
    if (!isScrubbingMode) startScrubbing()
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    timelineSyncManager.setPosition(bounded)
    activeClip = timelineSyncManager.findClipAt(bounded)
    timelineSyncManager.setActiveClip(activeClip)
    val seq = seekSequence.incrementAndGet()
    playbackController.invalidatePendingSeeks()
    coalescedSeekJob?.cancel()
    coalescedSeekJob = scope.launch {
      delay(8L)
      if (seq != seekSequence.get()) return@launch
      val clip = activeClip
      if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
        ensureClipLoaded(clip)
        playbackController.seekTo(clip.timelineToSourceMs(bounded), resumeAfter = false, exact = false)
      }
    }
  }

  fun stopScrubbing(finalPosMs: Long) {
    // Scrubbing/track touch is a seek gesture, not an implicit resume command.
    isScrubbingMode = false
    coalescedSeekJob?.cancel()
    wasPlayingBeforeScrub = false
    seekTo(finalPosMs)
  }

  fun play() {
    if (currentTimeline.totalDurationMs <= 0L) return
    if (currentPosMs >= currentTimeline.totalDurationMs) {
      currentPosMs = 0L
      timelineSyncManager.setPosition(0L)
    }

    activeClip = timelineSyncManager.findClipAt(currentPosMs)
    timelineSyncManager.setActiveClip(activeClip)

    // The master clock starts even for an image/gap. The media player is only
    // started when the current master position maps to a playable video source.
    val clip = activeClip
    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      playbackController.setPlaybackSpeed(clip.speed)
      playbackController.setVolume(if (clip.isMuted) 0f else clip.volume)
      playbackController.seekTo(clip.timelineToSourceMs(currentPosMs), resumeAfter = true, exact = false)
    } else {
      playbackController.pause()
    }

    timelineSyncManager.startSyncLoop()
    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.PLAYING,
      isPlaying = true,
      currentPosition = currentPosMs
    )
  }

  fun pause() {
    // Never derive the master timeline position from a stale player position.
    // The master clock has already published the authoritative current position.
    timelineSyncManager.stopSyncLoop()
    currentPosMs = timelineSyncManager.timelinePositionMs.value
    playbackController.pause()
    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.PAUSED,
      isPlaying = false,
      currentPosition = currentPosMs
    )
  }

  fun togglePlayPause() { if (isPlaying) pause() else play() }
  fun attachSurfaceView(surfaceView: SurfaceView) = surfaceManager.attachSurfaceView(surfaceView)
  fun attachTextureView(textureView: TextureView) = surfaceManager.attachTextureView(textureView)
  fun invalidateClip(clipId: String) { renderCacheManager.invalidateClip(clipId); gpuRenderManager.invalidateClip(clipId) }
  fun invalidateAll() { renderCacheManager.clear(); gpuRenderManager.invalidateAll() }

  private fun handleClipTransition(nextClip: VideoClip?, nextTimelinePos: Long, resumeAfter: Boolean = true) {
    activeClip = nextClip
    currentPosMs = nextTimelinePos
    if (nextClip != null && nextClip.isVideo && isPlayableInPlayer(nextClip.uri)) {
      ensureClipLoaded(nextClip)
      playbackController.setPlaybackSpeed(nextClip.speed)
      playbackController.setVolume(if (nextClip.isMuted) 0f else nextClip.volume)
      playbackController.seekTo(
        nextClip.timelineToSourceMs(nextTimelinePos),
        resumeAfter = resumeAfter && timelineSyncManager.isPlaying,
        exact = false
      )
    } else {
      // Image/gap: pause only the source player. Do NOT stop the master timeline.
      playbackController.pause()
    }
    _engineState.value = _engineState.value.copy(
      currentPosition = nextTimelinePos,
      isPlaying = timelineSyncManager.isPlaying,
      playbackState = if (timelineSyncManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED
    )
  }

  private fun ensureClipLoaded(clip: VideoClip) {
    val uriString = clip.uri
    if (clip.id == loadedClipId && uriString == loadedUri && playbackController.player.playbackState != Player.STATE_IDLE) return
    loadedClipId = clip.id
    loadedUri = uriString
    val uri = try { Uri.parse(uriString) } catch (e: Exception) {
      Log.e(TAG, "Invalid clip URI: $uriString", e)
      return
    }
    _engineState.value = _engineState.value.copy(playbackState = EnginePlaybackState.PREPARING)
    playbackController.loadMedia(uri, clip.sourceStartMs.coerceAtLeast(0L), autoPlay = false)
  }

  private fun isPlayableInPlayer(uriString: String): Boolean = MediaRelinkManager.isRealPlayableMedia(context, uriString)

  private fun handlePlayerError(error: PlaybackException) {
    Log.e(TAG, "Player exception: ${error.errorCodeName}: ${error.message}", error)
    decoderManager.handleCodecError(error)
    loadedClipId = null
    loadedUri = null
    _engineState.value = _engineState.value.copy(playbackState = EnginePlaybackState.ERROR, isPlaying = false, decoderState = decoderManager.decoderState, error = error.message ?: error.errorCodeName)
  }

  fun recoverFromError() {
    decoderManager.reset()
    _engineState.value = _engineState.value.copy(error = null, decoderState = decoderManager.decoderState)
    activeClip?.let(::ensureClipLoaded)
    seekTo(currentPosMs)
  }

  fun release() {
    timelineSyncManager.release()
    surfaceManager.release()
    playbackController.release()
    gpuRenderManager.release()
    renderCacheManager.clear()
    scope.cancel()
    _engineState.value = _engineState.value.copy(playbackState = EnginePlaybackState.RELEASED, isPlaying = false)
  }
}
