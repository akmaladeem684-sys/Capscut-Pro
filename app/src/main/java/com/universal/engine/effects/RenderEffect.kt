package com.universal.engine.effects

import com.universal.engine.frame.GpuFrame
import com.universal.engine.rendergraph.RenderContext

/**
 * External Effects Engine plug-in point.
 */
interface RenderEffect {
    val id: String
    val variantKey: String

    fun apply(ctx: RenderContext, input: GpuFrame, timestampUs: Long, previousOutput: GpuFrame?): GpuFrame

    val needsFrameHistory: Boolean get() = false
    val historyFrames: Int get() = 0

    fun release(ctx: RenderContext) {}
}
