package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/**
 * Owns the single Media3/ExoPlayer hardware playback pipeline.
 * It deliberately reuses the player and surface instead of recreating them per Play/Seek.
 */
@OptIn(UnstableApi::class)
class PlaybackManager(
  private val context: Context,
  private val onPlaybackStateChanged: (Int) -> Unit = {},
  private val onIsPlayingChanged: (Boolean) -> Unit = {},
  private val onPlayerError: (PlaybackException) -> Unit = {}
) {
  companion object { private const val TAG = "PlaybackManager" }

  val player: ExoPlayer = ExoPlayer.Builder(
    context.applicationContext,
    DefaultRenderersFactory(context.applicationContext)
      .setEnableDecoderFallback(true)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
  ).setLoadControl(
    DefaultLoadControl.Builder()
      .setBufferDurationsMs(1000, 5000, 200, 500)
      .build()
  ).setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply {
    playWhenReady = false
    repeatMode = Player.REPEAT_MODE_OFF
  }

  private var currentLoadedUri: String? = null

  val isPlaying: Boolean get() = player.isPlaying
  val currentPosition: Long get() = player.currentPosition
  val duration: Long get() = player.duration.coerceAtLeast(0L)
  val bufferedPosition: Long get() = player.bufferedPosition
  val playbackState: Int get() = player.playbackState

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_BUFFERING) Log.d(TAG, "BUFFERING at ${player.currentPosition}ms")
      if (state == Player.STATE_READY) Log.d(TAG, "READY at ${player.currentPosition}ms")
      if (state == Player.STATE_ENDED) Log.d(TAG, "ENDED")
      onPlaybackStateChanged(state)
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      Log.d(TAG, "isPlaying=$isPlaying pos=${player.currentPosition}ms")
      onIsPlayingChanged(isPlaying)
    }
    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
      Log.d(TAG, "videoSize=${videoSize.width}x${videoSize.height}")
    }
    override fun onRenderedFirstFrame() { Log.d(TAG, "first video frame rendered") }
    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "player error [${error.errorCodeName}] ${error.message}", error)
      onPlayerError(error)
    }
  }

  init { player.addListener(playerListener) }

  fun loadMedia(uri: Uri, startPosMs: Long = 0L, autoPlay: Boolean = false) {
    val uriString = uri.toString()
    if (uriString == currentLoadedUri && player.playbackState != Player.STATE_IDLE) {
      seekTo(startPosMs)
      if (autoPlay) play()
      return
    }
    currentLoadedUri = uriString
    val normalizedUri = normalizeUri(uri)
    player.setMediaItem(MediaItem.fromUri(normalizedUri), startPosMs.coerceAtLeast(0L))
    player.prepare()
    player.playWhenReady = autoPlay
    Log.d(TAG, "prepared media=$uriString start=${startPosMs}ms autoPlay=$autoPlay")
  }

  fun play() {
    if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
    player.play()
  }

  fun pause() { player.pause() }

  /** Fast seek used while scrubbing. */
  fun seekTo(positionMs: Long) {
    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    player.seekTo(positionMs.coerceAtLeast(0L))
  }

  /** Exact final seek used after scrub/reposition requests. */
  fun seekToExact(positionMs: Long) {
    player.setSeekParameters(SeekParameters.EXACT)
    player.seekTo(positionMs.coerceAtLeast(0L))
    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
  }

  fun setVolume(volume: Float) { player.volume = volume.coerceIn(0f, 2f) }
  fun setMuted(isMuted: Boolean) { player.volume = if (isMuted) 0f else 1f }

  fun setPlaybackSpeed(speed: Float) {
    val safe = speed.coerceIn(0.1f, 10f)
    if (player.playbackParameters.speed != safe) player.playbackParameters = PlaybackParameters(safe)
  }

  fun setSurface(surface: Surface?) {
    if (surface?.isValid == true) player.setVideoSurface(surface) else player.clearVideoSurface()
  }
  fun clearSurface() { player.clearVideoSurface() }
  fun addListener(listener: Player.Listener) { player.addListener(listener) }
  fun removeListener(listener: Player.Listener) { player.removeListener(listener) }

  fun release() {
    player.removeListener(playerListener)
    try { player.stop() } catch (_: Exception) { }
    player.clearVideoSurface()
    player.release()
    currentLoadedUri = null
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
