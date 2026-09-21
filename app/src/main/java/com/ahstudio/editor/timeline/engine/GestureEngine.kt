package com.ahstudio.editor.timeline.engine

import androidx.compose.ui.geometry.Offset

enum class GestureState { IDLE, TAP, DRAG, ZOOM, SCROLL }

class GestureEngine {
    private var state = GestureState.IDLE
    private var startPos = Offset.Zero
    private var currentPos = Offset.Zero

    fun onDown(pos: Offset) {
        state = GestureState.IDLE
        startPos = pos
        currentPos = pos
    }

    fun onDrag(pos: Offset): GestureState {
        currentPos = pos
        val dx = abs(pos.x - startPos.x)
        val dy = abs(pos.y - startPos.y)
        if (state == GestureState.IDLE && (dx > 8f || dy > 8f)) {
            state = GestureState.DRAG
        }
        return state
    }

    fun onUp(): GestureState {
        val prev = state
        state = GestureState.IDLE
        return if (prev == GestureState.IDLE) GestureState.TAP else prev
    }

    private fun abs(v: Float) = if (v < 0f) -v else v
}
