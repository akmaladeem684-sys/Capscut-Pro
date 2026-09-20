package com.example.engine.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.test.core.app.ApplicationProvider
import com.example.engine.composition.gl.OpenGLRecoveryTextureView
import com.example.engine.timeline.nonlinear.models.Clip
import com.example.engine.timeline.nonlinear.models.TimelineState
import com.example.engine.timeline.nonlinear.models.Track
import com.example.engine.timeline.nonlinear.models.TrackType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimelinePlaybackComponentsTest {

  private lateinit var context: Context
  private lateinit var fakePlayer: FakeExoPlayerController

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    fakePlayer = createFakeExoPlayer()
  }

  // =========================================================================
  // PROMPT 2: TimelinePreviewCoordinator Tests
  // =========================================================================

  @Test
  fun testCoordinator_initialStateAndTimelineUpdate() {
    val coordinator = TimelinePreviewCoordinator(context, fakePlayer)

    val clip1 = Clip(
      id = "c1",
      sourceUri = "content://media/1",
      startTimeUs = 0L,
      durationUs = 5_000_000L,
      sourceTrimStartUs = 0L,
      sourceDurationUs = 5_000_000L
    )
    val clip2 = Clip(
      id = "c2",
      sourceUri = "content://media/2",
      startTimeUs = 8_000_000L, // Gap between 5s and 8s
      durationUs = 4_000_000L,
      sourceTrimStartUs = 0L,
      sourceDurationUs = 4_000_000L
    )

    val timeline = TimelineState(
      tracks = listOf(
        Track(id = "video_main", type = TrackType.VIDEO, clips = listOf(clip1, clip2))
      ),
      playheadUs = 0L
    )

    coordinator.updateTimeline(timeline)

    val state = coordinator.state.value
    assertEquals(12_000_000L, state.timelineDurationUs)
    assertEquals("c1", state.activeClipId)
    assertFalse(state.isInGap)
    assertEquals(1, state.activeVideoClips.size)

    coordinator.release()
  }

  @Test
  fun testCoordinator_scrubbingSwitchesSeekParameters() {
    val coordinator = TimelinePreviewCoordinator(context, fakePlayer)

    val clip = Clip(
      id = "c1",
      sourceUri = "content://media/1",
      startTimeUs = 0L,
      durationUs = 10_000_000L
    )
    val timeline = TimelineState(
      tracks = listOf(Track(id = "v1", type = TrackType.VIDEO, clips = listOf(clip)))
    )
    coordinator.updateTimeline(timeline)

    // 1. User touches down to start scrubbing: should switch to CLOSEST_SYNC
    coordinator.startScrub()
    assertTrue("Coordinator should be in scrubbing state", coordinator.state.value.isScrubbing)
    assertEquals(SeekParameters.CLOSEST_SYNC, fakePlayer.lastSeekParameters)

    // 2. User moves finger across timeline
    coordinator.scrubTo(4_000_000L)
    assertEquals(4_000_000L, coordinator.state.value.playheadUs)

    // 3. User releases touch: should switch to EXACT seek parameters for pixel-perfect frame
    coordinator.stopScrub(6_500_000L)
    assertFalse("Coordinator should exit scrubbing state", coordinator.state.value.isScrubbing)
    assertEquals(SeekParameters.EXACT, fakePlayer.lastSeekParameters)
    assertEquals(6_500_000L, coordinator.state.value.playheadUs)

    coordinator.release()
  }

  @Test
  fun testCoordinator_discontinuousClipsAndGaps() {
    val coordinator = TimelinePreviewCoordinator(context, fakePlayer)

    val clip1 = Clip(
      id = "c1",
      sourceUri = "content://media/1",
      startTimeUs = 1_000_000L,
      durationUs = 3_000_000L // 1s to 4s
    )
    val clip2 = Clip(
      id = "c2",
      sourceUri = "content://media/2",
      startTimeUs = 7_000_000L, // Gap between 4s and 7s
      durationUs = 3_000_000L // 7s to 10s
    )

    val timeline = TimelineState(
      tracks = listOf(Track(id = "v1", type = TrackType.VIDEO, clips = listOf(clip1, clip2)))
    )
    coordinator.updateTimeline(timeline)

    // Test time inside first clip (2s)
    coordinator.seekToExact(2_000_000L)
    assertEquals("c1", coordinator.state.value.activeClipId)
    assertFalse(coordinator.state.value.isInGap)

    // Test time inside gap (5s)
    coordinator.seekToExact(5_000_000L)
    assertNull(coordinator.state.value.activeClipId)
    assertTrue("Should detect gap region between clips", coordinator.state.value.isInGap)

    // Test time inside second clip (8s)
    coordinator.seekToExact(8_000_000L)
    assertEquals("c2", coordinator.state.value.activeClipId)
    assertFalse(coordinator.state.value.isInGap)

    coordinator.release()
  }

  @Test
  fun testCoordinator_backgroundPersistenceAndRestoration() {
    val coordinator = TimelinePreviewCoordinator(context, fakePlayer)

    val clip = Clip(
      id = "c1",
      sourceUri = "content://media/1",
      startTimeUs = 0L,
      durationUs = 10_000_000L
    )
    val timeline = TimelineState(
      tracks = listOf(Track(id = "v1", type = TrackType.VIDEO, clips = listOf(clip)))
    )
    coordinator.updateTimeline(timeline)

    // Seek to specific microsecond position and test background persistence
    coordinator.seekToExact(4_823_110L)
    val expectedSavedPos = coordinator.state.value.playheadUs

    // App transitions to background
    coordinator.onAppBackground()
    assertFalse("Player must pause when app backgrounds", fakePlayer.isPlaying)

    // App returns to foreground: should restore exact microsecond timestamp
    coordinator.onAppForeground()
    assertEquals(expectedSavedPos, coordinator.state.value.playheadUs)
    assertEquals(SeekParameters.EXACT, fakePlayer.lastSeekParameters)

    coordinator.release()
  }

  // =========================================================================
  // PROMPT 3: OpenGL Recovery Pipeline Tests
  // =========================================================================

  @Test
  fun testOpenGLRecoveryView_lifecycleControl() {
    val view = OpenGLRecoveryTextureView(context)
    // Lifecycle pauses and resumes without crashing or NPE
    view.onPause()
    view.onResume()
    view.onDestroy()
  }

  // =========================================================================
  // FAKE EXOPLAYER FOR TEST VERIFICATION
  // =========================================================================

  interface FakeExoPlayerController : ExoPlayer {
    var lastSeekParameters: SeekParameters
    var lastSeekPosMs: Long
    var currentVolume: Float
  }

  companion object {
    fun createFakeExoPlayer(): FakeExoPlayerController {
      var isPlaying = false
      var playbackState = Player.STATE_READY
      val listeners = mutableListOf<Player.Listener>()
      var lastSeekParameters: SeekParameters = SeekParameters.DEFAULT
      var lastSeekPosMs: Long = 0L
      var currentVolume: Float = 1.0f

      val handler = java.lang.reflect.InvocationHandler { _, method, args ->
        when (method.name) {
          "isPlaying" -> isPlaying
          "getPlaybackState" -> playbackState
          "play" -> {
            isPlaying = true
            listeners.forEach { it.onIsPlayingChanged(true) }
            null
          }
          "pause" -> {
            isPlaying = false
            listeners.forEach { it.onIsPlayingChanged(false) }
            null
          }
          "setSeekParameters" -> {
            val param = args?.get(0) as? SeekParameters
            if (param != null) lastSeekParameters = param
            null
          }
          "seekTo" -> {
            val pos = args?.get(0) as? Long ?: 0L
            lastSeekPosMs = pos
            null
          }
          "setVolume" -> {
            val vol = args?.get(0) as? Float ?: 1f
            currentVolume = vol
            null
          }
          "addListener" -> {
            val listener = args?.get(0) as? Player.Listener
            if (listener != null) listeners.add(listener)
            null
          }
          "removeListener" -> {
            val listener = args?.get(0) as? Player.Listener
            if (listener != null) listeners.remove(listener)
            null
          }
          "setMediaItem" -> null
          "prepare" -> {
            playbackState = Player.STATE_READY
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
            null
          }
          "clearVideoSurface" -> null
          "getLastSeekParameters" -> lastSeekParameters
          "setLastSeekParameters" -> {
            lastSeekParameters = args[0] as SeekParameters
            null
          }
          "getLastSeekPosMs" -> lastSeekPosMs
          "setLastSeekPosMs" -> {
            lastSeekPosMs = args[0] as Long
            null
          }
          "getCurrentVolume" -> currentVolume
          "setCurrentVolume" -> {
            currentVolume = args[0] as Float
            null
          }
          "equals" -> false
          "hashCode" -> 42
          "toString" -> "FakeExoPlayer"
          else -> null
        }
      }

      return java.lang.reflect.Proxy.newProxyInstance(
        FakeExoPlayerController::class.java.classLoader,
        arrayOf(FakeExoPlayerController::class.java),
        handler
      ) as FakeExoPlayerController
    }
  }
}
