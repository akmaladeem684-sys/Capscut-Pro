package com.universal.engine.rendergraph

import com.universal.engine.frame.GpuFrame

/**
 * Base class for every graph node. Nodes declare their requirements up-front so the graph
 * can allocate/reuse intermediates and validate feasibility BEFORE execution begins.
 */
abstract class RenderNode(
    val id: String,
    val inputCount: Int,
    val stage: RenderDiagnosticsCompat.Stage
) {

    data class Requirements(
        val outputWidthFactor: Float = 1f,
        val outputHeightFactor: Float = 1f,
        val needsDepth: Boolean = false,
        val needsFloat: Boolean = false,
        val passes: Int = 1
    )

    abstract val requirements: Requirements

    open fun prepare(ctx: RenderContext, inputWidth: Int, inputHeight: Int) {}

    abstract fun render(ctx: RenderContext, inputs: List<GpuFrame>): GpuFrame

    open fun release(ctx: RenderContext) {}
}

object RenderDiagnosticsCompat {
    enum class Stage { IDLE, DECODE, EVALUATE, TRANSFORM, COLOR, LAYER_2D, LAYER_3D, TEXT, EFFECTS, MASKS, COMPOSITE, GRADE, TONE_MAP, OUTPUT, ENCODE }
}
