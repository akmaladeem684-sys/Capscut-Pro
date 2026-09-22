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
    val RowHeight = 52.dp
    val RulerHeight = 28.dp
    val HeaderWidth = 104.dp
    val TrimHandle = 20.dp
    val SnapHit = 10.dp
    val EdgeMargin = 56.dp

    val PanelBg = Color(0xFF0E0F12)
    val RulerBg = Color(0xFF0E0F12)
    val TrackBg = Color(0xFF141519)
    val TrackBgAlt = Color(0xFF16171D)
    val TrackLaneDivider = Color(0xFF22242B)
    val HeaderBg = Color(0xFF0E0F12)
    val ClipStroke = Color(0xFF2A3240)
    val Selection = Color(0xFFFFFFFF)
    val Playhead = Color(0xFFFFFFFF) // Crisp solid white CTI
    val SnapLine = Color(0xFF7FE3A0)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF8E95A3)

    val AudioColor = Color(0xFF1C6878)
    val TextColor = Color(0xFFE67300)
    val OverlayColor = Color(0xFF244430)
    val TransitionBadgeBg = Color(0xFF25272F)

    fun trackColor(kind: TrackKind): Color = when (kind) {
        TrackKind.VIDEO      -> Color(0xFF141923)
        TrackKind.OVERLAY    -> Color(0xFF1D5A34)
        TrackKind.TEXT       -> Color(0xFFE67300)
        TrackKind.STICKER    -> Color(0xFF2FA36B)
        TrackKind.EFFECT     -> Color(0xFF7A4FC9)
        TrackKind.ADJUSTMENT -> Color(0xFF5A6472)
        TrackKind.AUDIO, TrackKind.MUSIC, TrackKind.VOICE, TrackKind.SFX -> Color(0xFF1C6878)
        TrackKind.CAPTION    -> Color(0xFFE67300)
    }
}
