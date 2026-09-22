package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.core.TimeFormatter
import com.ahstudio.editor.timeline.core.TimelineTime

@Composable
fun TimelineToolbar(ctrl: TimelineUiController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.undo() }, enabled = ctrl.canUndo) { Text("↶") }
        TextButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.redo() }, enabled = ctrl.canRedo) { Text("↷") }
        FilledIconButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.playback.togglePlay() }) {
            Text(if (ctrl.isPlaying) "❚❚" else "▶")
        }
        Text(
            TimeFormatter.clock(TimelineTime(ctrl.playheadMicros), ctrl.fps),
            color = TimelineTokens.Playhead,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.splitAtPlayhead() }) { Text("✂") }
        TextButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.duplicateSelection() }, enabled = ctrl.selection.isNotEmpty()) { Text("⧉") }
        TextButton(onClick = { ctrl.onTimelineTouchBegan(); ctrl.deleteSelection() }, enabled = ctrl.selection.isNotEmpty()) { Text("🗑") }
        TextButton(onClick = { ctrl.addMarkerAtPlayhead() }) { Text("⚑") }
    }
}
