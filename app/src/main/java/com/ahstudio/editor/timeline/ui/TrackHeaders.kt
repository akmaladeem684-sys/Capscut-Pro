package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.core.isAudioLike

@Composable
fun TrackHeaders(ctrl: TimelineUiController, m: TimelineMetrics, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val rowDp = with(LocalDensity.current) { m.rowHeightPx.toDp() }
    Box(modifier.background(TimelineTokens.RulerBg)) {
        Column(Modifier.fillMaxSize().graphicsLayer { translationY = -ctrl.scrollY }) { // §4: sync on vertical scroll
            ctrl.snapshot.tracks.forEachIndexed { index, track ->
                Row(
                    Modifier.fillMaxWidth().height(rowDp)
                        .background(TimelineTokens.TrackBg)
                        .pointerInput(track.id) {
                            detectTapGestures(onTap = { ctrl.onTimelineTouchBegan(); ctrl.selectTrack(track.id) })
                        }
                        .pointerInput(track.id, ctrl.snapshot.tracks.size) { // §13 long-press reorder
                            var accY = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    ctrl.onTimelineTouchBegan(); ctrl.beginTrackReorder(index)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    accY = 0f
                                },
                                onDrag = { change, amt ->
                                    accY += amt.y
                                    ctrl.updateTrackReorder(index * m.rowHeightPx + m.rowHeightPx / 2f + accY + ctrl.scrollY, m.rowHeightPx)
                                    change.consume()
                                },
                                onDragEnd = { ctrl.commitTrackReorder() },
                                onDragCancel = { ctrl.cancelTrackReorder() },
                            )
                        }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(4.dp, rowDp * 0.6f).background(TimelineTokens.trackColor(track.kind)))
                    Spacer(Modifier.width(6.dp))
                    Text(track.name, color = TimelineTokens.TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    HeaderToggle("◉", track.visible, "visibility") { ctrl.engine.updateTrack(track.id) { it.copy(visible = !it.visible) } }
                    HeaderToggle("L", track.locked, "lock") { ctrl.engine.updateTrack(track.id) { it.copy(locked = !it.locked) } }
                    if (track.kind.isAudioLike()) {
                        HeaderToggle("M", track.muted, "mute") { ctrl.engine.updateTrack(track.id) { it.copy(muted = !it.muted) } }
                        HeaderToggle("S", track.solo, "solo") { ctrl.engine.updateTrack(track.id) { it.copy(solo = !it.solo) } }
                    }
                    Text("≡", color = TimelineTokens.TextDim)
                }
            }
        }
    }
}

@Composable
private fun HeaderToggle(glyph: String, active: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(20.dp)
            .clip(CircleShape)
            .background(if (active) TimelineTokens.Playhead.copy(alpha = 0.25f) else TimelineTokens.TrackBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (active) TimelineTokens.Playhead else TimelineTokens.TextDim)
    }
}
