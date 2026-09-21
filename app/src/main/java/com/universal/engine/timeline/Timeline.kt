package com.universal.engine.timeline

data class Timeline(
    val tracks: List<Track>,
    val durationUs: Long = tracks.flatMap { t -> t.clips.map { it.timelineEndUs } }.maxOrNull() ?: 0L,
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val outputFrameRate: Int = 30,
    val backgroundR: Float = 0f, val backgroundG: Float = 0f, val backgroundB: Float = 0f
) {
    val frameDurationUs: Long get() = 1_000_000L / outputFrameRate
    fun totalFrames(): Long = (durationUs + frameDurationUs - 1) / frameDurationUs
    fun timestampForFrame(frameIndex: Long): Long = frameIndex * frameDurationUs
}
