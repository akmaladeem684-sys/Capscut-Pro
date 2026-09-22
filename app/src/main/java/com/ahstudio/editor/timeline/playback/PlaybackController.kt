package com.ahstudio.editor.timeline.playback

import android.view.Choreographer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All preview surfaces subscribe here. No competing sync loops. */
interface PreviewSink {
    fun onPlaybackStarted() {}
    fun onPlaybackPaused() {}
    fun onPlaybackEnded() {}
    fun onSeek(targetMicros: Long) {}
    fun onTimeChanged(micros: Long, playing: Boolean) {}
}

abstract class FrameDriver2 { abstract fun start(onFrame: (Long) -> Unit); abstract fun stop() }

class ChoreographerFrameDriver : FrameDriver2() {
    private var running = false
    private var lastNanos = 0L
    override fun start(onFrame: (Long) -> Unit) {
        if (running) return; running = true; lastNanos = 0L
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                val delta = if (lastNanos == 0L) 0L else frameTimeNanos - lastNanos
                lastNanos = frameTimeNanos
                onFrame(delta)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
    }
    override fun stop() { running = false }
}

/** Test driver — deterministic frames. */
class ManualFrameDriver : FrameDriver2() {
    private var cb: ((Long) -> Unit)? = null
    override fun start(onFrame: (Long) -> Unit) { cb = onFrame }
    override fun stop() { cb = null }
    fun emit(deltaNanos: Long) { cb?.invoke(deltaNanos) }
}

class PlaybackController(
    private val clock: MasterTimelineClock,
    private val durationProvider: () -> Long,
    private val driver: FrameDriver2,
    private val scope: CoroutineScope,
) {
    @Volatile var speed: Float = 1f
    @Volatile var isPlaying: Boolean = false; private set
    var previewSink: PreviewSink? = null
    private var pendingSeekMicros: Long? = null
    private var seekJob: Job? = null

    fun play() {
        if (isPlaying) return
        val dur = durationProvider()
        if (dur <= 0L) return
        if (clock.timeMicros >= dur) clock.seekTo(0L)
        isPlaying = true
        previewSink?.onPlaybackStarted()
        previewSink?.onSeek(clock.timeMicros)
        startFrameLoop()
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        stopFrameLoop()
        previewSink?.onPlaybackPaused()
    }

    fun togglePlay() = if (isPlaying) pause() else play()

    /** Immediate, exact seek. */
    fun seekTo(micros: Long) {
        val dur = durationProvider()
        val t = micros.coerceIn(0L, if (dur > 0) dur else micros.coerceAtLeast(0L))
        clock.seekTo(t)
        previewSink?.onSeek(t)
    }

    /** Scrub seeks: clock is immediate; player sink receives frame-aligned coalesced seeks. */
    fun requestScrubSeek(micros: Long) {
        clock.seekTo(micros)
        pendingSeekMicros = micros
        if (seekJob?.isActive != true) {
            seekJob = scope.launch {
                delay(16) // one display frame
                val target = pendingSeekMicros
                pendingSeekMicros = null
                target?.let { previewSink?.onSeek(it) }
            }
        }
    }

    private fun startFrameLoop() {
        stopFrameLoop()
        driver.start { deltaNanos ->
            if (!isPlaying) return@start
            val deltaMicros = (deltaNanos / 1000.0) * speed
            val dur = durationProvider()
            val next = clock.timeMicros + deltaMicros.toLong()
            if (dur > 0 && next >= dur) {
                clock.seekTo(dur)
                stopFrameLoop()
                isPlaying = false
                previewSink?.onPlaybackEnded()
            } else {
                clock.advanceBy(deltaMicros.toLong(), playing = true)
                previewSink?.onTimeChanged(clock.timeMicros, true)
            }
        }
    }

    private fun stopFrameLoop() {
        driver.stop()
    }
}
