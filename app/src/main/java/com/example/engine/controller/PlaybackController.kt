package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Single authoritative owner of preview playback commands and the Media3 master clock. */
class PlaybackController(
  context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit = {},
  private val onPlaybackEnded: () -> Unit = {},
  private val onPlayerError: (PlaybackException) -> Unit = {}
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private val commandGeneration = AtomicLong(0L)
  private val _state = MutableStateFlow(EnginePlaybackState.IDLE)
  val state: StateFlow<EnginePlaybackState> = _state.asStateFlow()
  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()
  private val _lastCommandAtMs = MutableStateFlow(0L)
  val lastCommandAtMs: StateFlow<Long> = _lastCommandAtMs.asStateFlow()

  private lateinit var _playbackManager: PlaybackManager
  val playbackManager: PlaybackManager get() = _playbackManager
  val player get() = playbackManager.player
  private var currentLoadedUri: String? = null
  private var disposed = false
  private var pendingCommand: Job? = null

  init {
    _playbackManager = PlaybackManager(
      context = appContext,
      onPlaybackStateChanged = { state ->
        if (!disposed) {
          when (state) {
            Player.STATE_IDLE -> _state.value = EnginePlaybackState.IDLE
            Player.STATE_BUFFERING -> _state.value = EnginePlaybackState.BUFFERING
            Player.STATE_READY -> _state.value = if (playbackManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.READY
            Player.STATE_ENDED -> { _state.value = EnginePlaybackState.COMPLETED; onPlaybackEnded() }
          }
        }
      },
      onIsPlayingChanged = { playing -> if (!disposed) _state.value = if (playing) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED },
      onPlayerError = { error ->
        if (!disposed) _state.value = EnginePlaybackState.ERROR
        onPlayerError(error)
      }
    )
  }

  val isPlaying: Boolean get() = !disposed && playbackManager.isPlaying
  val currentPosition: Long get() = if (disposed) _timelinePositionMs.value else playbackManager.currentPosition
  val duration: Long get() = if (disposed) 0L else playbackManager.duration
  val bufferedPosition: Long get() = if (disposed) 0L else playbackManager.bufferedPosition

  fun loadMedia(uri: Uri, startPosMs: Long = 0L, autoPlay: Boolean = false) = enqueue("load") {
    if (disposed) return@enqueue
    val normalized = normalizeUri(uri)
    val key = normalized.toString()
    if (key == currentLoadedUri && playbackManager.playbackState != Player.STATE_IDLE) {
      playbackManager.seekTo(startPosMs)
      if (autoPlay) playbackManager.play()
      return@enqueue
    }
    currentLoadedUri = key
    _state.value = EnginePlaybackState.PREPARING
    playbackManager.loadMedia(normalized, startPosMs, autoPlay)
  }

  fun loadTrimPreview(uri: Uri, startMs: Long, endMs: Long, speed: Float, volume: Float, loop: Boolean) = enqueue("trimLoad") {
    if (disposed) return@enqueue
    val item = MediaItem.Builder()
      .setUri(normalizeUri(uri))
      .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(startMs.coerceAtLeast(0L))
          .setEndPositionMs(endMs.coerceAtLeast(startMs + 50L))
          .setStartsAtKeyFrame(false)
          .build()
      ).build()
    playbackManager.player.stop()
    playbackManager.player.clearMediaItems()
    playbackManager.player.setMediaItem(item)
    playbackManager.player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    playbackManager.player.playbackParameters = androidx.media3.common.PlaybackParameters(speed.coerceIn(0.1f, 10f))
    playbackManager.player.volume = volume.coerceIn(0f, 2f)
    playbackManager.player.prepare()
    playbackManager.player.play()
    currentLoadedUri = normalizeUri(uri).toString()
  }

  fun play() = enqueue("play") {
    if (disposed) return@enqueue
    if (playbackManager.playbackState == Player.STATE_IDLE && playbackManager.player.mediaItemCount > 0) _state.value = EnginePlaybackState.PREPARING
    playbackManager.play()
    _state.value = if (playbackManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.PREPARING
  }

  fun pause() = enqueue("pause") {
    if (disposed) return@enqueue
    playbackManager.pause()
    _state.value = EnginePlaybackState.PAUSED
  }

  fun seekTo(positionMs: Long, resumeAfter: Boolean = false, exact: Boolean = false, generation: Long = commandGeneration.incrementAndGet()) = enqueue("seek#$generation") {
    if (disposed || generation != commandGeneration.get()) return@enqueue
    _state.value = EnginePlaybackState.SEEKING
    if (exact) playbackManager.seekToExact(positionMs) else playbackManager.seekTo(positionMs)
    if (resumeAfter) { playbackManager.play(); _state.value = EnginePlaybackState.PLAYING }
    else _state.value = EnginePlaybackState.PAUSED
  }

  fun invalidatePendingSeeks(): Long = commandGeneration.incrementAndGet()
  fun setPlaybackSpeed(speed: Float) = enqueue("speed") { if (!disposed) playbackManager.setPlaybackSpeed(speed) }
  fun setVolume(volume: Float) = enqueue("volume") { if (!disposed) playbackManager.setVolume(volume) }
  fun setMuted(muted: Boolean) = enqueue("mute") { if (!disposed) playbackManager.setMuted(muted) }
  fun setSurface(surface: Surface?) = enqueue("surface") { if (!disposed) playbackManager.setSurface(surface) }
  fun clearSurface() = enqueue("clearSurface") { if (!disposed) playbackManager.clearSurface() }
  fun setRepeatMode(mode: Int) = enqueue("repeat") { if (!disposed) playbackManager.player.repeatMode = mode }

  fun updateTimelinePosition(positionMs: Long) {
    if (!disposed) {
      _timelinePositionMs.value = positionMs.coerceAtLeast(0L)
      onTimelinePositionChanged(_timelinePositionMs.value)
    }
  }

  fun sampleClockPositionMs(): Long = if (disposed) _timelinePositionMs.value else playbackManager.currentPosition

  fun release() {
    if (disposed) return
    disposed = true
    pendingCommand?.cancel()
    scope.cancel()
    playbackManager.release()
    _state.value = EnginePlaybackState.RELEASED
  }

  private fun enqueue(name: String, block: () -> Unit) {
    if (disposed) return
    pendingCommand = scope.launch {
      _lastCommandAtMs.value = SystemClock.elapsedRealtime()
      try { block() } catch (t: Throwable) {
        if (!disposed) {
          _state.value = EnginePlaybackState.ERROR
          android.util.Log.e("PlaybackController", "Command $name failed", t)
        }
      }
    }
  }

  private fun normalizeUri(uri: Uri): Uri = when {
    uri.scheme == "asset" -> {
      var path = uri.path ?: ""
      if (path.startsWith("/")) path = path.substring(1)
      if (path.isEmpty()) path = uri.authority ?: ""
      Uri.parse("asset:///$path")
    }
    uri.scheme == null || uri.scheme == "file" -> {
      val path = uri.path ?: uri.toString()
      val file = java.io.File(path)
      if (file.exists()) Uri.fromFile(file) else uri
    }
    else -> uri
  }
}
