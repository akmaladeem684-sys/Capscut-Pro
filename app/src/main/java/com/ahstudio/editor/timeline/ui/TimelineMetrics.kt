package com.ahstudio.editor.timeline.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.core.TrackKind

data class TimelineMetrics(
    val mainRowHeightPx: Float = 58f,
    val subRowHeightPx: Float = 36f,
    val mainToSubGapPx: Float = 8f,
    val subTrackGapPx: Float = 4f,
    val rulerHeightPx: Float = 28f,
    val headerWidthPx: Float = 104f,
    val handlePx: Float = 20f,
    val snapPx: Float = 10f,
    val edgeMarginPx: Float = 56f,
    val density: Density = Density(1f),
) {
    constructor(
        rowHeightPx: Float,
        rulerHeightPx: Float,
        headerWidthPx: Float,
        handlePx: Float,
        snapPx: Float,
        edgeMarginPx: Float,
        density: Density = Density(1f)
    ) : this(
        mainRowHeightPx = rowHeightPx,
        subRowHeightPx = (rowHeightPx * 0.68f).coerceAtLeast(32f),
        mainToSubGapPx = 8f,
        subTrackGapPx = 4f,
        rulerHeightPx = rulerHeightPx,
        headerWidthPx = headerWidthPx,
        handlePx = handlePx,
        snapPx = snapPx,
        edgeMarginPx = edgeMarginPx,
        density = density
    )

    // Backwards compatibility for single-height access
    val rowHeightPx: Float get() = mainRowHeightPx

    fun rowHeightPx(trackIndex: Int): Float =
        if (trackIndex == 0) mainRowHeightPx else subRowHeightPx

    fun trackTopPx(trackIndex: Int): Float {
        if (trackIndex <= 0) return 0f
        return (trackIndex - 1) * (subRowHeightPx + subTrackGapPx)
    }

    fun totalTracksHeightPx(trackCount: Int): Float {
        if (trackCount <= 0) return 0f
        if (trackCount == 1) return mainRowHeightPx
        return mainRowHeightPx + mainToSubGapPx + (trackCount - 1) * subRowHeightPx + ((trackCount - 2).coerceAtLeast(0)) * subTrackGapPx
    }

    fun trackIndexAtY(contentY: Float, trackCount: Int): Int {
        if (trackCount <= 0 || contentY < 0f) return -1
        if (contentY <= mainRowHeightPx) return 0
        if (contentY < mainRowHeightPx + mainToSubGapPx) return -1 // inside vertical separation gap
        val subContentY = contentY - (mainRowHeightPx + mainToSubGapPx)
        val subIndex = subTrackIndexAtY(subContentY, trackCount - 1)
        return if (subIndex >= 0) 1 + subIndex else -1
    }

    fun subTrackIndexAtY(subContentY: Float, subTrackCount: Int): Int {
        if (subTrackCount <= 0 || subContentY < 0f) return -1
        var currentY = 0f
        for (i in 0 until subTrackCount) {
            if (subContentY in currentY..(currentY + subRowHeightPx)) return i
            currentY += subRowHeightPx + subTrackGapPx
        }
        return -1
    }
}

object TimelineTokens {
    val MainRowHeight = 58.dp
    val SubRowHeight = 36.dp
    val MainToSubGap = 8.dp     // Exact 2mm visual separation gap
    val SubTrackGap = 4.dp      // Clean spacing between sub-tracks
    val RulerHeight = 28.dp
    val HeaderWidth = 104.dp
    val TrimHandle = 24.dp      // Easy touch hit width for resizing
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
    val SelectionYellow = Color(0xFFFFD54F)
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
