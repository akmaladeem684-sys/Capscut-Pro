package com.universal.engine.timeline

import com.universal.engine.animation.AnimatedTransform
import com.universal.engine.animation.KeyedProperty
import com.universal.engine.renderer2d.BlendMode
import com.universal.engine.renderer2d.Transform2D

/**
 * A media clip on the timeline. Times are TIMELINE microseconds.
 */
data class Clip(
    val id: String,
    val mediaId: String,
    val timelineStartUs: Long,
    val durationUs: Long,
    val sourceInUs: Long,
    val trackIndex: Int = 0,
    val baseTransform: Transform2D = Transform2D.IDENTITY,
    val animatedTransform: AnimatedTransform? = null,
    val animatedOpacity: KeyedProperty? = null,
    val blend: BlendMode = BlendMode.NORMAL,
    val speed: Float = 1f,
    val volume: Float = 1f
) {
    val timelineEndUs: Long get() = timelineStartUs + durationUs
    fun contains(timestampUs: Long): Boolean = timestampUs >= timelineStartUs && timestampUs < timelineEndUs
    fun sourceTimestampAt(timelineUs: Long): Long =
        sourceInUs + ((timelineUs - timelineStartUs).toFloat() * speed).toLong()
}

data class Track(val index: Int, val clips: List<Clip>)
