package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.Track
import kotlin.math.roundToInt

@Composable
fun TracksArea(ctrl: TimelineUiController, m: TimelineMetrics) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Column(Modifier.graphicsLayer { translationY = -ctrl.scrollY }) {
            val tracks = ctrl.snapshot.tracks
            val dragged = ctrl.dragPreview
            val shift = dragged?.trackShift ?: 0
            tracks.indices.forEach { row -> TrackRow(ctrl, row, tracks, dragged, shift, m) }
        }
    }
}

@Composable
private fun TrackRow(
    ctrl: TimelineUiController, row: Int, tracks: List<Track>,
    dragged: DragPreview?, shift: Int, m: TimelineMetrics,
) {
    val rowDp = with(LocalDensity.current) { m.rowHeightPx.toDp() }
    // §15 virtualization window (±400px margin)
    val fromT = ctrl.viewport.timeAtContentPx(ctrl.scrollX - 400f)
    val toT = ctrl.viewport.timeAtContentPx(ctrl.scrollX + ctrl.viewport.viewportWidthPx + 400f)
    val dragIds = dragged?.clipIds ?: emptySet()
    val track = tracks[row]

    val static = ctrl.engine.indexes.clipsByTrack[track.id].orEmpty()
        .filter { it.id !in dragIds && it.endMicros > fromT && it.startMicros < toT }
    val draggedHere = dragged?.originRow?.entries
        ?.filter { it.value + shift == row }
        ?.mapNotNull { ctrl.engine.clip(it.key) }
        .orEmpty()

    Box(
        Modifier.fillMaxWidth().height(rowDp)
            .background(if (row % 2 == 0) TimelineTokens.TrackBg else TimelineTokens.TrackBg.copy(alpha = 0.85f))
    ) {
        (static + draggedHere).forEach { clip -> ClipBox(ctrl, clip, track, m) }
    }
}

@Composable
fun ClipBox(ctrl: TimelineUiController, clip: Clip, track: Track, m: TimelineMetrics) {
    val shown = ctrl.effectiveTrim(clip) ?: clip
    val selected = clip.id in ctrl.selection
    val dragging = ctrl.dragPreview?.clipIds?.contains(clip.id) == true
    val color = clip.colorArgb?.let { Color(it) } ?: TimelineTokens.trackColor(track.kind)
    val startPx = ctrl.viewport.contentPxAtTime(ctrl.effectiveClipStart(shown)) - ctrl.scrollX
    val widthPx = ctrl.viewport.contentPxAtTime(shown.durationMicros).coerceAtLeast(2f)
    val wI = widthPx.roundToInt().coerceAtLeast(1)

    Box(
        Modifier
            .offset { IntOffset(startPx.roundToInt(), 0) }          // px-exact x
            .layout { measurable, _ ->                               // px-exact width
                val p = measurable.measure(Constraints(minWidth = wI, maxWidth = wI))
                layout(wI, p.height) { p.place(0, 0) }
            }
            .fillMaxHeight()
            .padding(horizontal = 1.dp, vertical = 3.dp)
            .alpha(if (track.visible) 1f else 0.3f)
            .graphicsLayer { if (dragging) { scaleX = 1.02f; scaleY = 1.02f } }
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.85f))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val wf = ctrl.visualProvider?.waveformFor(clip)
            if (wf != null && wf.isNotEmpty()) {
                val bars = (size.width / 3f).toInt().coerceAtLeast(1)
                val step = wf.size.toFloat() / bars
                for (i in 0 until bars) {
                    val v = wf[(i * step).toInt()].coerceIn(0f, 1f)
                    val bh = size.height * 0.7f * v
                    drawLine(Color(0xCCFFFFFF), Offset(i * 3f + 1.5f, (size.height - bh) / 2f),
                        Offset(i * 3f + 1.5f, (size.height + bh) / 2f), 1.5f)
                }
            } else {
                var bx = 6f
                while (bx < size.width - 6f) {
                    drawLine(Color(0x33FFFFFF), Offset(bx, size.height * 0.25f),
                        Offset(bx, size.height * 0.75f), 2f)
                    bx += 14f
                }
            }
            if (selected) {
                drawRoundRect(TimelineTokens.Selection, cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()))
                drawRoundRect(TimelineTokens.Selection, Offset(1f, size.height * 0.2f),
                    Size(5.dp.toPx(), size.height * 0.6f), CornerRadius(3f))
                drawRoundRect(TimelineTokens.Selection,
                    Offset(size.width - 1f - 5.dp.toPx(), size.height * 0.2f),
                    Size(5.dp.toPx(), size.height * 0.6f), CornerRadius(3f))
            }
        }
        Text(
            clip.label.ifBlank { clip.kind.name.lowercase().replaceFirstChar { it.uppercase() } },
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp),
        )
    }
}
