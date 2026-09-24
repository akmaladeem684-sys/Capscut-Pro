package com.example.ui.components.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip

/**
 * Supported timeline track kinds for the Multi-Track NLE Engine.
 */
enum class LaneKind(
  val displayName: String,
  val defaultHeightDp: Dp,
  val baseColor: Color,
  val accentColor: Color
) {
  MAIN_VIDEO("Main Track", 60.dp, Color(0xFF1E293B), Color(0xFF00E5FF)),
  OVERLAY("Overlay / PIP", 36.dp, Color(0xFF1E1B4B), Color(0xFF818CF8)),
  TEXT("Text", 36.dp, Color(0xFF312E81), Color(0xFFA78BFA)),
  AUDIO("Audio", 36.dp, Color(0xFF064E3B), Color(0xFF34D399)),
  EFFECT("Effect", 36.dp, Color(0xFF4C1D95), Color(0xFFC084FC)),
  FILTER("Filter", 36.dp, Color(0xFF701A75), Color(0xFFF472B6)),
  STICKER("Sticker", 36.dp, Color(0xFF78350F), Color(0xFFFBBF24))
}

/**
 * Normalized clip item representation inside a timeline lane.
 */
data class LaneClipItem(
  val id: String,
  val laneIndex: Int,
  val kind: LaneKind,
  val startMs: Long,
  val durationMs: Long,
  val title: String,
  val uri: String = "",
  val speed: Float = 1.0f,
  val volume: Float = 1.0f,
  val isMuted: Boolean = false,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val rawClip: Any? = null
) {
  val endMs: Long
    get() = startMs + durationMs

  fun overlapsWith(otherStartMs: Long, otherDurationMs: Long): Boolean {
    val otherEndMs = otherStartMs + otherDurationMs
    return startMs < otherEndMs && otherStartMs < endMs
  }
}

/**
 * Represents a single vertical row (Lane) in the CapCut-style Multi-Track Timeline.
 */
data class TimelineLane(
  val laneIndex: Int,
  val kind: LaneKind,
  val heightDp: Dp,
  val label: String,
  val icon: ImageVector,
  val clips: List<LaneClipItem>,
  val isMainLane: Boolean
) {
  val hasClips: Boolean
    get() = clips.isNotEmpty()
}

/**
 * Principal NLE Multi-Track Dynamic Lane Allocation Engine.
 *
 * Enforces:
 * 1. Main Track Isolation (Lane 0 exclusively for main video/media clips, 60dp height).
 * 2. True Dynamic Infinite Sub-Tracks (Lanes 1, 2, 3... at 36dp height).
 * 3. Zero-overlap collision management & exact 2mm (~6dp) vertical track spacing.
 * 4. Automatic Lane Compaction & Collapse when clips are removed.
 */
object TrackLaneManager {

  val MAIN_LANE_HEIGHT: Dp = 60.dp
  val SUB_LANE_HEIGHT: Dp = 36.dp
  val TRACK_VERTICAL_GAP: Dp = 6.dp // Exact 2mm (~6dp) visual separation gap

  /**
   * Computes the complete list of active, compacted timeline lanes from the current Timeline model.
   *
   * @param timeline The immutable Timeline snapshot.
   * @return A list of active [TimelineLane]s ordered consecutively from Lane 0 downwards.
   */
  fun computeLanes(timeline: Timeline): List<TimelineLane> {
    val resultLanes = mutableListOf<TimelineLane>()

    // 1. Lane 0: Main Video Track (Strictly isolated at the top)
    val mainVideoClips = timeline.videoClips.map { clip ->
      LaneClipItem(
        id = clip.id,
        laneIndex = 0,
        kind = LaneKind.MAIN_VIDEO,
        startMs = clip.timelineStartMs,
        durationMs = clip.durationMs,
        title = clip.name.ifBlank { "Main Video" },
        uri = clip.uri,
        speed = clip.speed,
        volume = clip.volume,
        isMuted = clip.isMuted,
        isLocked = clip.isLocked,
        isHidden = clip.isHidden,
        rawClip = clip
      )
    }.sortedBy { it.startMs }

    // Always include Lane 0 if project exists or has video clips
    resultLanes.add(
      TimelineLane(
        laneIndex = 0,
        kind = LaneKind.MAIN_VIDEO,
        heightDp = MAIN_LANE_HEIGHT,
        label = "Main Track",
        icon = Icons.Default.Movie,
        clips = mainVideoClips,
        isMainLane = true
      )
    )

    // 2. Dynamic Sub-Tracks (Overlay, Text, Audio, Effect, Sticker)
    var currentSubLaneIndex = 1

    // A. Overlays (PIP)
    val overlayLanes = partitionClipsIntoLanes(
      clips = timeline.overlayClips.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = clip.trackIndex.coerceAtLeast(1),
          kind = LaneKind.OVERLAY,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.name.ifBlank { "Overlay" },
          uri = clip.uri,
          speed = clip.speed,
          volume = clip.volume,
          isMuted = clip.isMuted,
          isLocked = clip.isLocked,
          isHidden = clip.isHidden,
          rawClip = clip
        )
      },
      kind = LaneKind.OVERLAY,
      icon = Icons.Default.Layers,
      baseLabel = "Overlay",
      startingLaneIndex = currentSubLaneIndex
    )
    resultLanes.addAll(overlayLanes)
    currentSubLaneIndex += overlayLanes.size

    // B. Text Tracks
    val textLanes = partitionClipsIntoLanes(
      clips = timeline.textClips.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = clip.trackIndex.coerceAtLeast(1),
          kind = LaneKind.TEXT,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.text.ifBlank { "Text" },
          rawClip = clip
        )
      },
      kind = LaneKind.TEXT,
      icon = Icons.Default.TextFields,
      baseLabel = "Text",
      startingLaneIndex = currentSubLaneIndex
    )
    resultLanes.addAll(textLanes)
    currentSubLaneIndex += textLanes.size

    // C. Audio Tracks
    val audioLanes = partitionClipsIntoLanes(
      clips = timeline.audioClips.map { clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = clip.trackIndex.coerceAtLeast(1),
          kind = LaneKind.AUDIO,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.title.ifBlank { if (clip.isVoiceOver) "Voiceover" else "Audio" },
          uri = clip.uri,
          speed = clip.speed,
          volume = clip.volume,
          isMuted = clip.isMuted,
          isLocked = clip.isLocked,
          isHidden = clip.isHidden,
          rawClip = clip
        )
      },
      kind = LaneKind.AUDIO,
      icon = Icons.Default.MusicNote,
      baseLabel = "Audio",
      startingLaneIndex = currentSubLaneIndex
    )
    resultLanes.addAll(audioLanes)
    currentSubLaneIndex += audioLanes.size

    // D. Effects / VFX Tracks
    val effectLanes = partitionClipsIntoLanes(
      clips = timeline.effectClips.mapIndexed { idx, clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = idx + 1,
          kind = LaneKind.EFFECT,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.customName.ifBlank { clip.effectType.displayName },
          rawClip = clip
        )
      },
      kind = LaneKind.EFFECT,
      icon = Icons.Default.AutoAwesome,
      baseLabel = "Effect",
      startingLaneIndex = currentSubLaneIndex
    )
    resultLanes.addAll(effectLanes)
    currentSubLaneIndex += effectLanes.size

    // E. Sticker Tracks
    val stickerLanes = partitionClipsIntoLanes(
      clips = timeline.stickerClips.mapIndexed { idx, clip ->
        LaneClipItem(
          id = clip.id,
          laneIndex = idx + 1,
          kind = LaneKind.STICKER,
          startMs = clip.timelineStartMs,
          durationMs = clip.durationMs,
          title = clip.emojiOrAsset.ifBlank { "Sticker" },
          rawClip = clip
        )
      },
      kind = LaneKind.STICKER,
      icon = Icons.Default.Face,
      baseLabel = "Sticker",
      startingLaneIndex = currentSubLaneIndex
    )
    resultLanes.addAll(stickerLanes)
    currentSubLaneIndex += stickerLanes.size

    // 3. Compact and re-index lanes consecutively so there are zero empty gaps
    return recompactLanes(resultLanes)
  }

  /**
   * Partitions clips into non-overlapping sub-lanes.
   * If two clips have overlapping time ranges (e.g. both start at 0.0s),
   * or user adds multiple overlays/texts, they are cleanly allocated onto separate consecutive sub-lanes.
   */
  private fun partitionClipsIntoLanes(
    clips: List<LaneClipItem>,
    kind: LaneKind,
    icon: ImageVector,
    baseLabel: String,
    startingLaneIndex: Int
  ): List<TimelineLane> {
    if (clips.isEmpty()) return emptyList()

    val distinctTracks = clips.map { it.laneIndex }.distinct().sorted()
    val lanesMap = mutableMapOf<Int, MutableList<LaneClipItem>>()
    var tierCounter = 0

    for (trackIdx in distinctTracks) {
      val trackClips = clips.filter { it.laneIndex == trackIdx }
        .sortedWith(compareBy({ it.startMs }, { -it.durationMs }))

      for (clip in trackClips) {
        var assignedTier = tierCounter
        while (true) {
          val existingInTier = lanesMap[assignedTier]
          val hasOverlap = existingInTier?.any { existing ->
            existing.overlapsWith(clip.startMs, clip.durationMs)
          } == true

          if (!hasOverlap) {
            lanesMap.getOrPut(assignedTier) { mutableListOf() }.add(clip)
            break
          }
          assignedTier++
        }
      }
      tierCounter = (lanesMap.keys.maxOrNull() ?: tierCounter) + 1
    }

    return lanesMap.entries.sortedBy { it.key }.mapIndexed { tierIndex, entry ->
      val laneIdx = startingLaneIndex + tierIndex
      val label = if (lanesMap.size > 1) "$baseLabel ${tierIndex + 1}" else baseLabel
      val updatedClips = entry.value.map { it.copy(laneIndex = laneIdx) }
      TimelineLane(
        laneIndex = laneIdx,
        kind = kind,
        heightDp = SUB_LANE_HEIGHT,
        label = label,
        icon = icon,
        clips = updatedClips,
        isMainLane = false
      )
    }
  }

  /**
   * Re-indexes lanes so that all active lanes are strictly continuous (0, 1, 2, ... N).
   * Automatically drops any sub-lane that contains 0 clips.
   */
  private fun recompactLanes(lanes: List<TimelineLane>): List<TimelineLane> {
    val compacted = mutableListOf<TimelineLane>()
    var nextIndex = 0

    for (lane in lanes) {
      if (lane.isMainLane || lane.hasClips) {
        val reindexedClips = lane.clips.map { it.copy(laneIndex = nextIndex) }
        compacted.add(
          lane.copy(
            laneIndex = nextIndex,
            clips = reindexedClips
          )
        )
        nextIndex++
      }
    }

    return compacted
  }

  /**
   * Calculates the exact wrapped height for the timeline container with zero dead space.
   */
  fun calculateTotalTimelineHeight(lanes: List<TimelineLane>): Dp {
    if (lanes.isEmpty()) return MAIN_LANE_HEIGHT
    val totalLanesHeight = lanes.sumOf { if (it.isMainLane) MAIN_LANE_HEIGHT.value.toDouble() else SUB_LANE_HEIGHT.value.toDouble() }
    val totalGapsHeight = (lanes.size - 1).coerceAtLeast(0) * TRACK_VERTICAL_GAP.value.toDouble()
    return (totalLanesHeight + totalGapsHeight).dp
  }

  /**
   * Allocates a brand new sub-lane index directly beneath all existing tracks.
   */
  fun allocateNewSubLane(timeline: Timeline): Int {
    val activeLanes = computeLanes(timeline)
    return (activeLanes.maxOfOrNull { it.laneIndex } ?: 0) + 1
  }

  /**
   * Checks if placing a clip at [startMs] with [durationMs] collides with any clip in [laneClips].
   */
  fun checkCollision(
    laneClips: List<LaneClipItem>,
    startMs: Long,
    durationMs: Long,
    excludeClipId: String? = null
  ): Boolean {
    val targetEndMs = startMs + durationMs
    return laneClips.any { item ->
      if (item.id == excludeClipId) false
      else item.overlapsWith(startMs, durationMs)
    }
  }
}
