package com.ahstudio.screeneditor.interaction

import android.graphics.PointF
import com.ahstudio.screeneditor.viewport.ScreenEditorViewport
import kotlin.math.hypot

object Gizmo {
    const val HANDLE_RADIUS_DP = 22f
    const val ROT_HANDLE_OFFSET_DP = 48f

    enum class Handle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        TOP_EDGE,
        BOTTOM_EDGE,
        LEFT_EDGE,
        RIGHT_EDGE,
        ROTATE,
        ANCHOR,
        BODY
    }

    fun hitHandle(
        screenX: Float,
        screenY: Float,
        corners: Array<PointF>,
        densityPxPerDp: Float
    ): Handle? {
        val r = HANDLE_RADIUS_DP * densityPxPerDp * 1.25f // generous touch target

        val rot = rotationHandlePos(corners, densityPxPerDp)
        if (hypot(screenX - rot.x, screenY - rot.y) <= r) return Handle.ROTATE

        val cornerMap = arrayOf(Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_RIGHT, Handle.BOTTOM_LEFT)
        for (i in 0..3) {
            if (hypot(screenX - corners[i].x, screenY - corners[i].y) <= r) {
                return cornerMap[i]
            }
        }

        val edges = arrayOf(
            midpoint(corners[0], corners[1]) to Handle.TOP_EDGE,
            midpoint(corners[1], corners[2]) to Handle.RIGHT_EDGE,
            midpoint(corners[2], corners[3]) to Handle.BOTTOM_EDGE,
            midpoint(corners[3], corners[0]) to Handle.LEFT_EDGE
        )
        for ((p, h) in edges) {
            if (hypot(screenX - p.x, screenY - p.y) <= r) return h
        }

        return null
    }

    fun rotationHandlePos(corners: Array<PointF>, dppx: Float): PointF {
        val topMid = midpoint(corners[0], corners[1])
        val ux = corners[0].y - corners[1].y
        val uy = corners[1].x - corners[0].x
        val len = hypot(ux, uy)
        if (len == 0f) return topMid
        val off = ROT_HANDLE_OFFSET_DP * dppx
        return PointF(topMid.x + (ux / len) * off, topMid.y + (uy / len) * off)
    }

    fun hitLayerAt(
        editorX: Float,
        editorY: Float,
        layersBackToFront: List<Pair<String, Array<PointF>>>,
        lockedVisible: (String) -> Boolean
    ): String? {
        for ((id, corners) in layersBackToFront.reversed()) { // front-to-back testing
            if (!lockedVisible(id)) continue
            if (pointInQuad(editorX, editorY, corners)) return id
        }
        return null
    }

    fun midpoint(p1: PointF, p2: PointF): PointF = PointF((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

    fun pointInQuad(px: Float, py: Float, corners: Array<PointF>): Boolean {
        if (corners.size < 4) return false
        var intersects = 0
        val n = 4
        for (i in 0 until n) {
            val p1 = corners[i]
            val p2 = corners[(i + 1) % n]
            if (((p1.y > py) != (p2.y > py)) &&
                (px < (p2.x - p1.x) * (py - p1.y) / (p2.y - p1.y + 0.00001f) + p1.x)
            ) {
                intersects++
            }
        }
        return (intersects % 2) != 0
    }
}
