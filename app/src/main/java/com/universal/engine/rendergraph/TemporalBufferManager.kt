package com.universal.engine.rendergraph

import com.universal.engine.frame.GpuFrame

/**
 * Frame-history storage for temporal effects. History is invalidated (correctly) after
 * seek, clip change, effect change, or resolution change.
 */
class TemporalBufferManager(private val ctx: RenderContext) {

    private class Slot(val ring: Array<GpuFrame?>, var writeIndex: Int = 0, var filled: Int = 0)

    private val slots = HashMap<String, Slot>()
    private var currentWidth = 0
    private var currentHeight = 0

    fun push(effectId: String, frame: GpuFrame) {
        if (frame.width != currentWidth || frame.height != currentHeight) reset()
        currentWidth = frame.width
        currentHeight = frame.height
        val slot = slots.getOrPut(effectId) { Slot(arrayOfNulls(4)) }
        slot.ring[slot.writeIndex]?.release()
        slot.ring[slot.writeIndex] = frame.retain()
        slot.writeIndex = (slot.writeIndex + 1) % slot.ring.size
        if (slot.filled < slot.ring.size) slot.filled++
    }

    fun history(effectId: String): List<GpuFrame> {
        val slot = slots[effectId] ?: return emptyList()
        val out = mutableListOf<GpuFrame>()
        for (i in slot.filled downTo 1) {
            val idx = (slot.writeIndex - i + slot.ring.size) % slot.ring.size
            slot.ring[idx]?.let { out.add(it) }
        }
        return out
    }

    fun previousFrame(effectId: String): GpuFrame? = history(effectId).firstOrNull()

    fun reset() {
        slots.values.forEach { slot -> slot.ring.forEach { it?.release() } }
        slots.clear()
    }

    fun release() {
        reset()
        currentWidth = 0
        currentHeight = 0
    }
}
