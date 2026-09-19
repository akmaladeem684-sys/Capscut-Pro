package com.example.engine.controller

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates a user timeline tap across the CTI, playback clock, and preview. */
class TimelineTouchSyncHandler(
    private val masterClock: MasterPlaybackClock,
    private val playbackController: PlaybackController
) {
    private val tapMutex = Mutex()

    suspend fun onTimelineTapped(clickX: Float, pixelsPerMs: Float) {
        require(pixelsPerMs > 0f) { "pixelsPerMs must be greater than zero" }
        val targetTimeMs = (clickX / pixelsPerMs).toLong().coerceAtLeast(0L)
        tapMutex.withLock {
            playbackController.pause()
            playbackController.seekTo(targetTimeMs, resumeAfter = false)
            masterClock.seekTo(targetTimeMs)
        }
    }
}
