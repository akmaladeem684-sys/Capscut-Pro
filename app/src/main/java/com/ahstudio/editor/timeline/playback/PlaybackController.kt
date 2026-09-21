package com.ahstudio.editor.timeline.playback

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.ahstudio.editor.timeline.clock.MasterTimelineClock
import com.ahstudio.editor.timeline.clock.SeekSource
import com.ahstudio.editor.timeline.clock.TimelineSyncListener

interface FrameTicker {
    fun start(tick: (frameTimeNanos: Long) -> Unit)
    fun stop()
}

class ChoreographerTicker : FrameTicker {
    private var running = false
    private var callback: ((Long) -> Unit)? = null
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            callback?.invoke(frameTimeNanos)
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }
    override fun start(tick: (Long) -> Unit) {
        callback = tick
        if (!running) { running = true; Choreographer.getInstance().postFrameCallback(frameCb) }
    }
    override fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        callback = null
    }
}

class ManualTicker : FrameTicker {
    private var cb: ((Long) -> Unit)? = null
    override fun start(tick: (Long) -> Unit) { cb = tick }
    override fun stop() { cb = null }
    fun emit(nanos: Long) { cb?.invoke(nanos) }
}

class PlaybackController(
    private val clock: MasterTimelineClock,
    private val ticker: FrameTicker,
) : TimelineSyncListener {

    interface MediaBackend {
        fun onPlaybackStarted() {}
        fun onPlaybackPaused() {}
        fun onSeekTo(us: Long, precise: Boolean) {}
        fun onRateChanged(rate: Float) {}
    }

    var backend: MediaBackend? = null
    var positionSource: (() -> Long?)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFrameNanos: Long? = null
    private var pendingSeekUs: Long? = null
    private var pendingSeekPrecise = true
    private var seekPosted = false

    init { clock.addListener(this) }

    fun play() = clock.play()
    fun pause() = clock.pause()
    fun togglePlay() = clock.toggle()
    fun seekTo(us: Long, source: SeekSource = SeekSource.PROGRAMMATIC) = clock.seekTo(us, source)
    fun seekByPx(deltaPxForward: Float, pxPerUs: Float) =
        clock.seekTo(clock.positionUs.value + (deltaPxForward / pxPerUs).toLong(), SeekSource.PLAYHEAD)

    fun onTimelineTouched() { if (clock.isPlaying.value) clock.pause() }

    override fun onSeek(us: Long, source: SeekSource) {
        pendingSeekUs = us
        pendingSeekPrecise = source != SeekSource.USER_SCRUB && source != SeekSource.PLAYHEAD
        postSeekFlush()
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        if (playing) { lastFrameNanos = null; ticker.start(::onTick); backend?.onPlaybackStarted() }
        else { ticker.stop(); backend?.onPlaybackPaused() }
    }

    private fun postSeekFlush() {
        if (seekPosted) return
        seekPosted = true
        mainHandler.post {
            seekPosted = false
            pendingSeekUs?.let { backend?.onSeekTo(it, pendingSeekPrecise) }
            pendingSeekUs = null
        }
    }

    private fun onTick(frameNanos: Long) {
        if (!clock.isPlaying.value) { lastFrameNanos = null; return }
        val backendPos = positionSource?.invoke()
        if (backendPos != null) clock.masterUpdate(backendPos)
        else clock.onFrameTick(frameNanos, lastFrameNanos)
        lastFrameNanos = frameNanos
    }
}
