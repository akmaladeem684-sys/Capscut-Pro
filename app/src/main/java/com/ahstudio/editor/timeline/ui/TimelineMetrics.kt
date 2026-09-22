package com.ahstudio.editor.timeline.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.core.TrackKind

data class TimelineMetrics(
    val rowHeightPx: Float,
    val rulerHeightPx: Float,
    val headerWidthPx: Float,
    val handlePx: Float,
    val snapPx: Float,
    val edgeMarginPx: Float,
    val density: Density,
)

object TimelineTokens {
    val RowHeight = 56.dp
    val RulerHeight = 30.dp
    val HeaderWidth = 108.dp
    val TrimHandle = 20.dp
    val SnapHit = 10.dp
    val EdgeMargin = 56.dp

    val PanelBg = Color(0xFF0E1116)
    val RulerBg = Color(0xFF161B23)
    val TrackBg = Color(0xFF131820)
    val ClipStroke = Color(0xFF2A3240)
    val Selection = Color(0xFFFFFFFF)
    val Playhead = Color(0xFFFFC94D)
    val SnapLine = Color(0xFF7FE3A0)
    val TextPrimary = Color(0xFFE8EDF4)
    val TextDim = Color(0xFF8A94A6)

    fun trackColor(kind: TrackKind): Color = when (kind) {
        TrackKind.VIDEO      -> Color(0xFF2C5CC5)
        TrackKind.OVERLAY    -> Color(0xFF7A4FC9)
        TrackKind.TEXT       -> Color(0xFFC9802C)
        TrackKind.STICKER    -> Color(0xFF2FA36B)
        TrackKind.EFFECT     -> Color(0xFFB0489A)
        TrackKind.ADJUSTMENT -> Color(0xFF5A6472)
        TrackKind.AUDIO      -> Color(0xFF2E8FB0)
        TrackKind.MUSIC      -> Color(0xFF2E8FB0)
        TrackKind.VOICE      -> Color(0xFF3BA07F)
        TrackKind.SFX        -> Color(0xFF6E7FD1)
        TrackKind.CAPTION    -> Color(0xFFC9802C)
    }
}
