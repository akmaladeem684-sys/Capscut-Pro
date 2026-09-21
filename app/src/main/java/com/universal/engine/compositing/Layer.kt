package com.universal.engine.compositing

import com.universal.engine.frame.GpuFrame
import com.universal.engine.renderer2d.BlendMode
import com.universal.engine.renderer2d.Transform2D

/** A resolved layer: source frame + placement + blend. Resolved by TimelineEvaluator per timestamp. */
data class Layer(
    val source: GpuFrame,
    val transform: Transform2D = Transform2D.IDENTITY,
    val blend: BlendMode = BlendMode.NORMAL,
    val maskFrame: GpuFrame? = null,
    val zIndex: Int = 0,
    val clipToCanvas: Boolean = true
)
