package com.ahstudio.editor.timeline.core

import kotlin.math.roundToLong

/** Canonical time unit = Long microseconds. No float drift anywhere in the pipeline. */
@JvmInline
value class TimelineTime(val micros: Long) : Comparable<TimelineTime> {
    override fun compareTo(other: TimelineTime): Int = micros.compareTo(other.micros)
    operator fun plus(o: TimelineTime) = TimelineTime(micros + o.micros)
    operator fun minus(o: TimelineTime) = TimelineTime(micros - o.micros)
    val seconds: Double get() = micros / 1_000_000.0
    val millisF: Float get() = micros / 1000f
    fun coerceIn(min: Long, max: Long) = TimelineTime(micros.coerceIn(min, max))

    fun quantizeToFrame(fps: Double): TimelineTime {
        val f = TimelineTime.frameDurationMicros(fps)
        return TimelineTime((micros.toDouble() / f).roundToLong() * f)
    }

    companion object {
        val ZERO = TimelineTime(0L)
        fun ofMicros(v: Long) = TimelineTime(v)
        fun ofMillis(v: Long) = TimelineTime(v * 1000L)
        fun ofSeconds(v: Double) = TimelineTime((v * 1_000_000.0).roundToLong())
        fun frameDurationMicros(fps: Double) =
            (1_000_000.0 / fps.coerceAtLeast(0.001)).roundToLong().coerceAtLeast(1L)
    }
}

object TimeFormatter {
    /** "MM:SS.FF" (or "H:MM:SS.FF"); FF = frame number within the second. */
    fun clock(t: TimelineTime, fps: Double, withFrames: Boolean = true): String {
        val total = t.micros / 1_000_000L
        val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
        val f = ((t.micros % 1_000_000L).toDouble() * fps / 1_000_000.0).toInt()
        return if (h > 0)
            (if (withFrames) "%d:%02d:%02d.%02d" else "%d:%02d:%02d").format(h, m, s, f)
        else
            (if (withFrames) "%02d:%02d.%02d" else "%02d:%02d").format(m, s, f)
    }
}
