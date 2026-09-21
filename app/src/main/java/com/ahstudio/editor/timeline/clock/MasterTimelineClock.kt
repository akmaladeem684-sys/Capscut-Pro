package com.ahstudio.editor.timeline.clock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToLong

enum class SeekSource { USER_SCRUB, PLAYHEAD, COMMAND, PLAYBACK, PROGRAMMATIC }

/**
 * Single listener contract for the whole sync pipeline
 * (preview renderer, overlay compositor, audio mixer, export timer…).
 * Source flags prevent competing synchronization loops (§16).
 */
interface TimelineSyncListener {
    fun onSeek(us: Long, source: SeekSource) {}
    fun onFrame(us: Long, playing: Boolean) {}
    fun onPlaybackStateChanged(playing: Boolean) {}
}

class MasterTimelineClock {

    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    @Volatile var durationUs: Long = 0L            // kept updated by TimelineEngine
    @Volatile var frameRate: Float = 30f
    @Volatile var playbackRate: Float = 1f

    private val listeners = CopyOnWriteArrayList<TimelineSyncListener>()
    fun addListener(l: TimelineSyncListener) { listeners.add(l) }
    fun removeListener(l: TimelineSyncListener) { listeners.remove(l) }

    val isAtEnd: Boolean get() = durationUs in 1.._positionUs.value

    /** Authoritative seek. Synchronous, exact, no approximation. */
    fun seekTo(us: Long, source: SeekSource) {
        val clamped = us.coerceIn(0L, maxOf(durationUs, Long.MAX_VALUE / 2))
        if (clamped == _positionUs.value) return
        _positionUs.value = clamped
        listeners.forEach { it.onSeek(clamped, source) }
    }

    fun seekBy(deltaUs: Long, source: SeekSource) = seekTo(_positionUs.value + deltaUs, source)

    /** Frame pump called by PlaybackController when no backend position source exists. */
    fun onFrameTick(frameTimeNanos: Long, lastFrameNanos: Long?) {
        if (!_isPlaying.value) return
        val dtNanos = lastFrameNanos?.let { frameTimeNanos - it } ?: 0L
        var next = _positionUs.value + (dtNanos / 1000.0 * playbackRate).roundToLong()
        var ended = false
        if (durationUs > 0 && next >= durationUs) { next = durationUs; ended = true }
        _positionUs.value = next
        listeners.forEach { it.onFrame(next, true) }
        if (ended) pause()
    }

    /** Backend-authoritative update (e.g., ExoPlayer position polled per frame). */
    fun masterUpdate(us: Long) {
        if (!_isPlaying.value) return
        _positionUs.value = us
        listeners.forEach { it.onFrame(us, true) }
    }

    fun play() {
        if (_isPlaying.value) return
        if (isAtEnd) seekTo(0L, SeekSource.PROGRAMMATIC)
        _isPlaying.value = true
        listeners.forEach { it.onPlaybackStateChanged(true) }
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        listeners.forEach { it.onPlaybackStateChanged(false) }
    }

    fun toggle() = if (_isPlaying.value) pause() else play()
}
