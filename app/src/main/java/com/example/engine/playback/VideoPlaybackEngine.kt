package com.example.engine.playback

import android.content.Context
import android.graphics.ColorMatrix
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.ColorFilterGenerator
import com.example.engine.controller.CustomVideoEngineController
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Public playback facade retained for the existing editor UI.
 * All primary playback/seek/surface commands are delegated to one CustomVideoEngineController -> PlaybackController.
 * No independent elapsed-realtime playback clock is used here.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackEngine(
  private val context: Context,
  onTimelinePositionChanged: (Long) -> Unit,
  onPlaybackEnded: () -> Unit,
  private val proxyEngine: ProxyMediaEngine? = null
) {
  companion object { private const val TAG = "VideoPlaybackEngine"; private const val UI_TICK_MS = 16L }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var trimPollJob: Job? = null
  private val seekGeneration = AtomicLong(0L)

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
  private val _activeClip = MutableStateFlow<VideoClip?>(null)
  val activeClip: StateFlow<VideoClip?> = _activeClip.asStateFlow()
  private val _playerError = MutableStateFlow<String?>(null)
  val playerError: StateFlow<String?> = _playerError.asStateFlow()
  private val _trimPlaybackPositionMs = MutableStateFlow(0L)
  val trimPlaybackPositionMs: StateFlow<Long> = _trimPlaybackPositionMs.asStateFlow()

  private var currentTimeline = Timeline()
  private var currentPosMs = 0L
  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private var isTrimPreviewMode = false
  private var trimPreviewClip: VideoClip? = null
  private var trimRangeStartMs = 0L
  private var trimRangeEndMs = 0L

  val engineController: CustomVideoEngineController = CustomVideoEngineController(
    context = context,
    onTimelinePositionChanged = { pos ->
      currentPosMs = pos
      _currentPositionMs.value = pos
      _activeClip.value = findClipAt(pos)
      syncOverlayPlayers(pos)
      onTimelinePositionChanged(pos)
    },
    onPlaybackEnded = {
      _isPlaying.value = false
      onPlaybackEnded()
    }
  )

  val player: ExoPlayer get() = engineController.player
  val isScrubbing: Boolean get() = isScrubbingMode
  val isTrimPreview: Boolean get() = isTrimPreviewMode

  private val overlayPlayers = ConcurrentHashMap<String, ExoPlayer>()
  private val overlayLoadedUris = ConcurrentHashMap<String, String>()

  init {
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isTrimPreviewMode && isPlaying) startTrimPolling() else trimPollJob?.cancel()
      }
      override fun onPlayerError(error: PlaybackException) {
        _playerError.value = "${error.errorCodeName}: ${error.message}"
        Log.e(TAG, "Preview player error", error)
      }
    })
  }

  fun isPlayableInPlayer(uriString: String?): Boolean = MediaRelinkManager.isRealPlayableMedia(context, uriString)

  fun applyVideoFilter(colorMatrix: ColorMatrix?) {
    // Existing GPU composition layer remains responsible for realtime filter/shader application.
  }

  fun getOverlayPlayer(clipId: String): ExoPlayer? = overlayPlayers[clipId]

  fun syncOverlayPlayers(posMs: Long) {
    if (isTrimPreviewMode) return
    val activeOverlays = currentTimeline.overlayClips.filter { it.isVideo && !it.isHidden && isPlayableInPlayer(it.uri) }
    val activeIds = activeOverlays.map { it.id }.toSet()
    overlayPlayers.keys.toList().filter { it !in activeIds }.forEach { id ->
      overlayPlayers.remove(id)?.let { p -> try { p.stop(); p.release() } catch (_: Exception) {} }
      overlayLoadedUris.remove(id)
    }
    for (overlay in activeOverlays) {
      val effectiveUri = proxyEngine?.getProxyUri(overlay) ?: overlay.uri
      var p = overlayPlayers[overlay.id]
      if (p == null) {
        p = try {
          ExoPlayer.Builder(context.applicationContext, DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1000, 5000, 200, 500).build())
            .setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
        } catch (e: Exception) {
          Log.w(TAG, "Overlay player creation failed", e); continue
        }
        overlayPlayers[overlay.id] = p
      }
      if (overlayLoadedUris[overlay.id] != effectiveUri || p.mediaItemCount == 0 || p.playbackState == Player.STATE_IDLE) {
        try { p.setMediaItem(MediaItem.fromUri(Uri.parse(effectiveUri))); p.prepare(); overlayLoadedUris[overlay.id] = effectiveUri } catch (e: Exception) { Log.w(TAG, "Overlay prepare failed", e) }
      }
      p.playbackParameters = androidx.media3.common.PlaybackParameters(overlay.speed.coerceAtLeast(0.01f))
      p.volume = if (overlay.isMuted) 0f else overlay.volume
      val active = posMs >= overlay.timelineStartMs && posMs < overlay.timelineStartMs + overlay.durationMs
      val source = overlay.timelineToSourceMs(posMs)
      if (active && engineController.timelineSyncManager.isPlaying) {
        if (kotlin.math.abs(p.currentPosition - source) > 100L || !p.isPlaying) { p.seekTo(source); p.play() }
      } else {
        if (p.isPlaying) p.pause()
        p.seekTo(source.coerceAtLeast(0L))
      }
    }
  }

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    engineController.updateTimeline(timeline)
    currentPosMs = currentPosMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    _currentPositionMs.value = currentPosMs
    _activeClip.value = findClipAt(currentPosMs)
    applyVideoFilter(ColorFilterGenerator.createCombinedMatrix(timeline.adjustments, timeline.filter, _activeClip.value?.filter))
    syncOverlayPlayers(currentPosMs)
  }

  fun startScrubbing() {
    isScrubbingMode = true
    wasPlayingBeforeScrub = _isPlaying.value
    engineController.startScrubbing()
  }

  fun scrubTo(timelinePosMs: Long) {
    if (!isScrubbingMode) startScrubbing()
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    _currentPositionMs.value = bounded
    _activeClip.value = findClipAt(bounded)
    val generation = seekGeneration.incrementAndGet()
    proxyEngine?.requestFrameAsync(_activeClip.value ?: return, _activeClip.value!!.timelineToSourceMs(bounded), generation) { _, _ -> }
    engineController.scrubTo(bounded)
  }

  fun stopScrubbing(finalPosMs: Long? = null) {
    val target = finalPosMs ?: currentPosMs
    isScrubbingMode = false
    engineController.stopScrubbing(target)
    _currentPositionMs.value = target
    if (wasPlayingBeforeScrub) wasPlayingBeforeScrub = false
  }

  fun seekTo(timelinePosMs: Long) {
    isScrubbingMode = false
    val bounded = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = bounded
    _currentPositionMs.value = bounded
    _activeClip.value = findClipAt(bounded)
    val generation = seekGeneration.incrementAndGet()
    _activeClip.value?.let { clip ->
      proxyEngine?.requestFrameAsync(clip, clip.timelineToSourceMs(bounded), generation) { _, _ -> }
    }
    engineController.seekTo(bounded)
  }

  fun play() {
    if (isTrimPreviewMode) { playTrimPreview(); return }
    _playerError.value = null
    engineController.play()
  }

  fun pause() {
    if (isTrimPreviewMode) { pauseTrimPreview(); return }
    engineController.pause()
    _isPlaying.value = false
  }

  fun togglePlayPause() {
    if (isTrimPreviewMode) { toggleTrimPlayPause(); return }
    engineController.togglePlayPause()
  }

  fun stepFrame(forward: Boolean, fps: Int = 30) {
    pause()
    val frameMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(1L)
    seekTo(if (forward) currentPosMs + frameMs else currentPosMs - frameMs)
  }

  fun previewTrimRange(clip: VideoClip, startMs: Long, endMs: Long, loop: Boolean = true) {
    isTrimPreviewMode = true
    trimPreviewClip = clip
    trimRangeStartMs = startMs.coerceAtLeast(0L)
    trimRangeEndMs = endMs.coerceAtLeast(trimRangeStartMs + 50L)
    _trimPlaybackPositionMs.value = trimRangeStartMs
    if (!isPlayableInPlayer(clip.uri)) return
    engineController.playbackController.loadTrimPreview(
      Uri.parse(clip.uri), trimRangeStartMs, trimRangeEndMs,
      clip.speed, if (clip.isMuted) 0f else clip.volume, loop
    )
  }

  fun seekTrimPreview(offsetFromStartMs: Long) {
    if (!isTrimPreviewMode) return
    val offset = offsetFromStartMs.coerceIn(0L, trimRangeEndMs - trimRangeStartMs)
    engineController.playbackController.seekTo(offset, resumeAfter = false, exact = true)
    _trimPlaybackPositionMs.value = trimRangeStartMs + offset
  }

  fun seekTrimPreviewToSourceMs(sourceTimeMs: Long) {
    if (!isTrimPreviewMode) return
    val target = sourceTimeMs.coerceIn(trimRangeStartMs, trimRangeEndMs)
    engineController.playbackController.seekTo(target - trimRangeStartMs, resumeAfter = false, exact = true)
    _trimPlaybackPositionMs.value = target
  }

  fun stepTrimFrame(forward: Boolean, fps: Int = 30) {
    if (!isTrimPreviewMode) return
    pauseTrimPreview()
    val delta = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(1L)
    seekTrimPreviewToSourceMs(if (forward) _trimPlaybackPositionMs.value + delta else _trimPlaybackPositionMs.value - delta)
  }

  fun pauseTrimPreview() { if (isTrimPreviewMode) engineController.playbackController.pause() }
  fun playTrimPreview() { if (isTrimPreviewMode) engineController.playbackController.play() }
  fun toggleTrimPlayPause() { if (isTrimPreviewMode) engineController.playbackController.let { if (it.isPlaying) it.pause() else it.play() } }

  fun exitTrimPreview() {
    if (!isTrimPreviewMode) return
    isTrimPreviewMode = false
    trimPreviewClip = null
    trimPollJob?.cancel()
    engineController.playbackController.setRepeatMode(Player.REPEAT_MODE_OFF)
    engineController.seekTo(currentPosMs)
  }

  private fun startTrimPolling() {
    trimPollJob?.cancel()
    trimPollJob = scope.launch {
      while (isActive && isTrimPreviewMode) {
        val p = player.currentPosition
        val source = (trimRangeStartMs + p).coerceIn(trimRangeStartMs, trimRangeEndMs)
        _trimPlaybackPositionMs.value = source
        delay(UI_TICK_MS)
      }
    }
  }

  private fun findClipAt(posMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
  }

  fun release() {
    trimPollJob?.cancel()
    scope.cancel()
    overlayPlayers.values.forEach { try { it.stop(); it.release() } catch (_: Exception) {} }
    overlayPlayers.clear()
    overlayLoadedUris.clear()
    engineController.release()
  }
}
