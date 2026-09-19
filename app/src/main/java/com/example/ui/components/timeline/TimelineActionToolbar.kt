package com.example.ui.components.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun TimelineActionToolbar(
  hasSelection: Boolean,
  isMultiSelectMode: Boolean,
  selectedCount: Int,
  canPaste: Boolean,
  isMagnetic: Boolean,
  onSplit: () -> Unit,
  onTrimLeft: () -> Unit,
  onTrimRight: () -> Unit,
  onRippleDelete: () -> Unit,
  onNormalDelete: () -> Unit,
  onDuplicate: () -> Unit,
  onCopy: () -> Unit,
  onPaste: () -> Unit,
  onSpeedClick: () -> Unit,
  onReverse: () -> Unit,
  onFreezeFrame: () -> Unit,
  onReplaceMedia: () -> Unit,
  onToggleMultiSelect: () -> Unit,
  onToggleMagnetic: () -> Unit,
  isTracksSyncEnabled: Boolean = true,
  onToggleTracksSync: (() -> Unit)? = null,
  onMoveToPlayhead: (() -> Unit)? = null,
  canEditAtPlayhead: Boolean = true,
  onSplitAllTracks: (() -> Unit)? = null,
  onOpenTrimTool: (() -> Unit)? = null,
  onOpenKeyframeTool: (() -> Unit)? = null,
  onOpenTransitionsTool: (() -> Unit)? = null,
  onNextPeak: (() -> Unit)? = null,
  onPrevPeak: (() -> Unit)? = null,
  onNextSilence: (() -> Unit)? = null,
  onPrevSilence: (() -> Unit)? = null,
  onRemoveSilence: (() -> Unit)? = null,
  onToggleWaveformStyle: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .border(0.5.dp, StudioBorder),
    color = StudioSurface
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .horizontalScroll(scrollState)
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // 1. Split At Playhead
      TimelineActionButton(
        icon = Icons.AutoMirrored.Filled.CallSplit,
        label = "Split",
        enabled = hasSelection || canEditAtPlayhead,
        accentColor = CyanAccent,
        testTag = "action_split",
        onClick = onSplit
      )

      // 2. Trim Left to Playhead (Clip starts at CTI)
      TimelineActionButton(
        icon = Icons.Default.West,
        label = "Trim L",
        enabled = hasSelection || canEditAtPlayhead,
        accentColor = CyanAccent,
        testTag = "action_trim_left",
        onClick = onTrimLeft
      )

      // 3. Trim Right to Playhead (Clip ends at CTI)
      TimelineActionButton(
        icon = Icons.Default.East,
        label = "Trim R",
        enabled = hasSelection || canEditAtPlayhead,
        accentColor = CyanAccent,
        testTag = "action_trim_right",
        onClick = onTrimRight
      )

      // 4. Align / Move to CTI
      if (onMoveToPlayhead != null) {
        TimelineActionButton(
          icon = Icons.Default.PinDrop,
          label = "Align CTI",
          enabled = hasSelection || canEditAtPlayhead,
          accentColor = AmberAccent,
          testTag = "action_align_cti",
          onClick = onMoveToPlayhead
        )
      }

      if (onOpenTrimTool != null) {
        TimelineActionButton(
          icon = Icons.Default.ContentCut,
          label = "Trim Tool",
          enabled = hasSelection,
          accentColor = AmberAccent,
          testTag = "action_trim_tool",
          onClick = onOpenTrimTool
        )
      }

      if (onOpenKeyframeTool != null) {
        TimelineActionButton(
          icon = Icons.Default.Diamond,
          label = "Keyframe",
          enabled = hasSelection,
          accentColor = PurpleAccent,
          testTag = "action_keyframe",
          onClick = onOpenKeyframeTool
        )
      }

      if (onOpenTransitionsTool != null) {
        TimelineActionButton(
          icon = Icons.Default.Transform,
          label = "Transition",
          enabled = true,
          accentColor = PurpleAccent,
          testTag = "action_transition",
          onClick = onOpenTransitionsTool
        )
      }

      if (onPrevPeak != null) {
        TimelineActionButton(
          icon = Icons.Default.SkipPrevious,
          label = "Prev Beat",
          enabled = true,
          accentColor = AmberAccent,
          testTag = "action_prev_peak",
          onClick = onPrevPeak
        )
      }

      if (onNextPeak != null) {
        // Audio Beat / Peak alignment jump
        TimelineActionButton(
          icon = Icons.Default.GraphicEq,
          label = "Next Beat",
          enabled = true,
          accentColor = AmberAccent,
          testTag = "action_next_peak",
          onClick = onNextPeak
        )
      }

      if (onNextSilence != null) {
        TimelineActionButton(
          icon = Icons.Default.VolumeOff,
          label = "Silence",
          enabled = true,
          accentColor = TextSecondary,
          testTag = "action_next_silence",
          onClick = onNextSilence
        )
      }

      if (onRemoveSilence != null) {
        TimelineActionButton(
          icon = Icons.Default.AutoFixHigh,
          label = "Auto-Cut Silence",
          enabled = hasSelection,
          accentColor = CyanAccent,
          testTag = "action_remove_silence",
          onClick = onRemoveSilence
        )
      }

      if (onToggleWaveformStyle != null) {
        TimelineActionButton(
          icon = Icons.Default.Tune,
          label = "Waveform",
          enabled = true,
          accentColor = CyanAccent,
          testTag = "action_toggle_waveform_style",
          onClick = onToggleWaveformStyle
        )
      }

      VerticalDivider(
        color = StudioBorder,
        modifier = Modifier.height(22.dp).padding(horizontal = 2.dp)
      )

      // 4. Ripple Delete
      TimelineActionButton(
        icon = Icons.Default.DeleteSweep,
        label = "Ripple Del",
        enabled = hasSelection,
        accentColor = AmberAccent,
        testTag = "action_ripple_delete",
        onClick = onRippleDelete
      )

      // 5. Normal Delete
      TimelineActionButton(
        icon = Icons.Default.Delete,
        label = "Delete",
        enabled = hasSelection,
        accentColor = RedAccent,
        testTag = "action_normal_delete",
        onClick = onNormalDelete
      )

      // 6. Duplicate
      TimelineActionButton(
        icon = Icons.Default.ContentCopy,
        label = "Duplicate",
        enabled = hasSelection,
        accentColor = TextPrimary,
        testTag = "action_duplicate",
        onClick = onDuplicate
      )

      // 7. Copy
      TimelineActionButton(
        icon = Icons.Default.CopyAll,
        label = "Copy",
        enabled = hasSelection,
        accentColor = TextSecondary,
        testTag = "action_copy",
        onClick = onCopy
      )

      // 8. Paste
      TimelineActionButton(
        icon = Icons.Default.ContentPaste,
        label = "Paste",
        enabled = canPaste,
        accentColor = if (canPaste) CyanAccent else TextTertiary,
        testTag = "action_paste",
        onClick = onPaste
      )

      VerticalDivider(
        color = StudioBorder,
        modifier = Modifier.height(22.dp).padding(horizontal = 2.dp)
      )

      // 9. Speed
      TimelineActionButton(
        icon = Icons.Default.Speed,
        label = "Speed",
        enabled = hasSelection,
        accentColor = CyanAccent,
        testTag = "action_speed",
        onClick = onSpeedClick
      )

      // 10. Reverse
      TimelineActionButton(
        icon = Icons.Default.SwapHoriz,
        label = "Reverse",
        enabled = hasSelection,
        accentColor = PinkAccent,
        testTag = "action_reverse",
        onClick = onReverse
      )

      // 11. Freeze Frame
      TimelineActionButton(
        icon = Icons.Default.AcUnit,
        label = "Freeze",
        enabled = hasSelection,
        accentColor = CyanAccent,
        testTag = "action_freeze",
        onClick = onFreezeFrame
      )

      // 12. Replace Media
      TimelineActionButton(
        icon = Icons.Default.FindReplace,
        label = "Replace",
        enabled = hasSelection,
        accentColor = AmberAccent,
        testTag = "action_replace",
        onClick = onReplaceMedia
      )

      VerticalDivider(
        color = StudioBorder,
        modifier = Modifier.height(22.dp).padding(horizontal = 2.dp)
      )

      // 13. Multi-Select Toggle
      TimelineActionButton(
        icon = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
        label = if (isMultiSelectMode) "Multi ($selectedCount)" else "Multi-Sel",
        enabled = true,
        isActive = isMultiSelectMode,
        accentColor = if (isMultiSelectMode) AmberAccent else TextSecondary,
        testTag = "action_multi_select",
        onClick = onToggleMultiSelect
      )

      // 14. Magnetic Timeline Toggle
      TimelineActionButton(
        icon = Icons.Default.Compress,
        label = if (isMagnetic) "Magnet ON" else "Magnet OFF",
        enabled = true,
        isActive = isMagnetic,
        accentColor = if (isMagnetic) CyanAccent else TextSecondary,
        testTag = "action_magnetic",
        onClick = onToggleMagnetic
      )

      // 15. Track Sync Toggle (All tracks move and edit together synchronously)
      if (onToggleTracksSync != null) {
        TimelineActionButton(
          icon = Icons.Default.Link,
          label = if (isTracksSyncEnabled) "Sync ON" else "Sync OFF",
          enabled = true,
          isActive = isTracksSyncEnabled,
          accentColor = if (isTracksSyncEnabled) GreenAccent else TextSecondary,
          testTag = "action_sync_tracks",
          onClick = onToggleTracksSync
        )
      }

      // 16. Split All Tracks at CTI
      if (onSplitAllTracks != null) {
        TimelineActionButton(
          icon = Icons.Default.Layers,
          label = "Split All",
          enabled = true,
          accentColor = PurpleAccent,
          testTag = "action_split_all_tracks",
          onClick = onSplitAllTracks
        )
      }
    }
  }
}

@Composable
private fun TimelineActionButton(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  accentColor: Color,
  testTag: String,
  onClick: () -> Unit,
  isActive: Boolean = false
) {
  val contentColor = when {
    !enabled -> TextTertiary.copy(alpha = 0.4f)
    isActive -> accentColor
    else -> TextPrimary
  }

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(if (isActive) accentColor.copy(alpha = 0.2f) else StudioSurfaceVariant.copy(alpha = 0.5f))
      .clickable(enabled = enabled) { onClick() }
      .padding(horizontal = 6.dp, vertical = 3.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = contentColor,
        modifier = Modifier.size(13.dp)
      )
      Spacer(modifier = Modifier.width(3.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 10.sp,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
          color = contentColor
        )
      )
    }
  }
}
