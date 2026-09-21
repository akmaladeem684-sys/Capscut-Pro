package com.universal.engine.playback

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MediaClock(private val timelineDurationUs: Long) {

    sealed class State {
        object Idle : State()
        object Playing : State()
        object Paused : State()
        object Ended : State()
    }

    private val stateRef = AtomicReference<State>(State.Idle)
    private val speedNumerator = AtomicLong(1000)
    private val mediaTimeUs = AtomicLong(0L)
    private val anchorMediaUs = AtomicLong(0L)
    private val anchorRealNanos = AtomicLong(0L)

    val state: State get() = stateRef.get()
    val speed: Float get() = speedNumerator.get() / 1000f

    fun play() {
        if (stateRef.get() is State.Playing) return
        anchor(mediaTimeUs.get())
        stateRef.set(State.Playing)
    }

    fun pause() {
        if (stateRef.getAndSet(State.Paused) is State.Playing) {
            mediaTimeUs.set(currentPositionUs())
        }
    }

    fun resume() = play()

    fun seekTo(targetUs: Long) {
        val clamped = targetUs.coerceIn(0L, timelineDurationUs)
        anchor(clamped)
        mediaTimeUs.set(clamped)
        if (clamped >= timelineDurationUs) stateRef.set(State.Ended)
    }

    fun setSpeed(multiplier: Float) {
        require(multiplier in 0.0625f..16f) { "speed out of range: $multiplier" }
        if (stateRef.get() is State.Playing) mediaTimeUs.set(currentPositionUs())
        speedNumerator.set((multiplier * 1000).toLong().coerceAtLeast(62L))
        anchor(mediaTimeUs.get())
    }

    fun stepFrames(frameDurationUs: Long, count: Int = 1) {
        pause()
        seekTo(mediaTimeUs.get() + frameDurationUs * count)
    }

    fun currentPositionUs(): Long {
        if (stateRef.get() !is State.Playing) return mediaTimeUs.get()
        val elapsedRealNanos = System.nanoTime() - anchorRealNanos.get()
        val elapsedMediaUs = elapsedRealNanos * speedNumerator.get() / 1_000_000L
        val pos = anchorMediaUs.get() + elapsedMediaUs
        if (pos >= timelineDurationUs) {
            mediaTimeUs.set(timelineDurationUs)
            stateRef.set(State.Ended)
            return timelineDurationUs
        }
        return pos
    }

    fun realNanosUntil(targetMediaUs: Long): Long {
        val mediaDeltaUs = (targetMediaUs - currentPositionUs()).coerceAtLeast(0L)
        return mediaDeltaUs * 1_000_000L / speedNumerator.get()
    }

    private fun anchor(mediaUs: Long) {
        anchorMediaUs.set(mediaUs)
        anchorRealNanos.set(System.nanoTime())
    }
}
