package com.example.ui.components.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlin.math.*

enum class AnimationCategoryTab(val label: String, val icon: ImageVector) {
  PRESETS("1-Tap Combos", Icons.Default.AutoAwesome),
  IN("In-Animation", Icons.Default.Login),
  OUT("Out-Animation", Icons.Default.Logout),
  COMBO("Loop / Combo", Icons.Default.Sync),
  CUSTOMIZE("Customise", Icons.Default.Tune)
}

data class QuickAnimationComboPreset(
  val id: String,
  val name: String,
  val description: String,
  val icon: ImageVector,
  val badgeColor: Color,
  val settings: ClipAnimationSettings
)

val QUICK_COMBO_PRESETS = listOf(
  QuickAnimationComboPreset(
    id = "cinematic",
    name = "Cinematic Intro",
    description = "Gentle zoom in with smooth breathing loop & soft fade out",
    icon = Icons.Default.MovieFilter,
    badgeColor = AmberAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.ZOOM_OUT,
      inDurationMs = 800L,
      comboType = ComboAnimationType.BREATHE,
      outType = OutAnimationType.FADE_OUT,
      outDurationMs = 600L,
      easing = AnimationEasing.EASE_OUT,
      intensity = 1.0f,
      speed = 0.8f
    )
  ),
  QuickAnimationComboPreset(
    id = "tiktok_pop",
    name = "TikTok Energy",
    description = "Dynamic Pop In, energetic pulse loop & bounce exit",
    icon = Icons.Default.FlashOn,
    badgeColor = PurpleAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.POP_IN,
      inDurationMs = 500L,
      comboType = ComboAnimationType.PULSE,
      outType = OutAnimationType.BOUNCE_OUT,
      outDurationMs = 500L,
      easing = AnimationEasing.OVERSHOOT,
      intensity = 1.2f,
      speed = 1.4f
    )
  ),
  QuickAnimationComboPreset(
    id = "vlog_smooth",
    name = "Vlog Elegance",
    description = "Clean upward slide entrance with floating loop",
    icon = Icons.Default.Videocam,
    badgeColor = CyanAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.SLIDE_UP,
      inDurationMs = 700L,
      comboType = ComboAnimationType.FLOAT,
      outType = OutAnimationType.SLIDE_DOWN_OUT,
      outDurationMs = 600L,
      easing = AnimationEasing.EASE_IN_OUT,
      intensity = 1.0f,
      speed = 1.0f
    )
  ),
  QuickAnimationComboPreset(
    id = "glitch_cyber",
    name = "Cyberpunk Glitch",
    description = "High-tech digital distortion entry and exit glitch",
    icon = Icons.Default.ElectricBolt,
    badgeColor = RedAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.GLITCH_IN,
      inDurationMs = 600L,
      comboType = ComboAnimationType.JITTER,
      outType = OutAnimationType.GLITCH_OUT,
      outDurationMs = 500L,
      easing = AnimationEasing.LINEAR,
      intensity = 1.5f,
      speed = 1.8f
    )
  ),
  QuickAnimationComboPreset(
    id = "bouncy_fun",
    name = "Bouncy Playful",
    description = "Joyful elastic bounce with pendulum wave loop",
    icon = Icons.Default.SentimentSatisfiedAlt,
    badgeColor = Color(0xFF10B981),
    settings = ClipAnimationSettings(
      inType = InAnimationType.BOUNCE_IN,
      inDurationMs = 800L,
      comboType = ComboAnimationType.WAVE,
      outType = OutAnimationType.POP_OUT,
      outDurationMs = 500L,
      easing = AnimationEasing.BOUNCE,
      intensity = 1.2f,
      speed = 1.2f
    )
  ),
  QuickAnimationComboPreset(
    id = "3d_spin",
    name = "3D Dynamic Spin",
    description = "Continuous revolving 360 rotation with flip transition",
    icon = Icons.Default.RotateRight,
    badgeColor = Color(0xFFEC4899),
    settings = ClipAnimationSettings(
      inType = InAnimationType.SPIN_IN,
      inDurationMs = 800L,
      comboType = ComboAnimationType.SPIN_360,
      outType = OutAnimationType.SPIN_OUT,
      outDurationMs = 800L,
      easing = AnimationEasing.EASE_IN_OUT,
      intensity = 1.0f,
      speed = 1.0f
    )
  ),
  QuickAnimationComboPreset(
    id = "flash_impact",
    name = "Impact Strobe",
    description = "Fast strobe beats with high-impact zoom scale",
    icon = Icons.Default.WbIncandescent,
    badgeColor = AmberAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.ZOOM_IN,
      inDurationMs = 400L,
      comboType = ComboAnimationType.FLASH_PULSE,
      outType = OutAnimationType.ZOOM_OUT,
      outDurationMs = 400L,
      easing = AnimationEasing.OVERSHOOT,
      intensity = 1.4f,
      speed = 2.0f
    )
  ),
  QuickAnimationComboPreset(
    id = "heartbeat_romance",
    name = "Heartbeat Rhythm",
    description = "Warm rhythmic pulse beat with soft blur entrance",
    icon = Icons.Default.Favorite,
    badgeColor = RedAccent,
    settings = ClipAnimationSettings(
      inType = InAnimationType.BLUR_IN,
      inDurationMs = 600L,
      comboType = ComboAnimationType.HEARTBEAT,
      outType = OutAnimationType.BLUR_OUT,
      outDurationMs = 600L,
      easing = AnimationEasing.EASE_OUT,
      intensity = 1.1f,
      speed = 1.0f
    )
  )
)

@Composable
fun AnimationsToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val isPlaying by viewModel.timelineEngine.isPlaying.collectAsState()

  // Determine active target clip
  val selectedVideoClip = remember(selectedElement, timeline) {
    when (selectedElement) {
      is SelectedTrackElement.Video -> timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
      is SelectedTrackElement.Overlay -> timeline.overlayClips.find { it.id == (selectedElement as SelectedTrackElement.Overlay).clipId }
      else -> {
        // Default to active clip at playhead or first clip
        timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
          ?: timeline.videoClips.firstOrNull()
      }
    }
  }

  val selectedTextClip = remember(selectedElement, timeline) {
    if (selectedElement is SelectedTrackElement.Text) {
      timeline.textClips.find { it.id == (selectedElement as SelectedTrackElement.Text).clipId }
    } else null
  }

  val selectedStickerClip = remember(selectedElement, timeline) {
    if (selectedElement is SelectedTrackElement.Sticker) {
      timeline.stickerClips.find { it.id == (selectedElement as SelectedTrackElement.Sticker).clipId }
    } else null
  }

  var activeCategoryTab by remember { mutableStateOf(AnimationCategoryTab.PRESETS) }
  var showApplyAllNotice by remember { mutableStateOf(false) }

  // Auto-hide notice after 2.5s
  LaunchedEffect(showApplyAllNotice) {
    if (showApplyAllNotice) {
      kotlinx.coroutines.delay(2500)
      showApplyAllNotice = false
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .navigationBarsPadding()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Top Bar Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(AmberAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Animation,
            contentDescription = "Animations",
            tint = AmberAccent,
            modifier = Modifier.size(18.dp)
          )
        }
        Column {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Clip Animations",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
              )
            )
            // Active animation indicator badge
            if (selectedVideoClip?.animation?.hasAnimation == true) {
              Surface(
                color = AmberAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  text = "Active",
                  color = AmberAccent,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
            }
          }
          Text(
            text = if (selectedVideoClip != null) "Target: ${selectedVideoClip.name}"
                   else if (selectedTextClip != null) "Target Text: \"${selectedTextClip.text}\""
                   else if (selectedStickerClip != null) "Target: Sticker"
                   else "Select a clip to animate",
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextSecondary,
              fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Quick Apply to All Clips button
        if (selectedVideoClip != null && timeline.videoClips.size > 1) {
          IconButton(
            onClick = {
              viewModel.timelineEngine.applyAnimationToAllClips(selectedVideoClip.animation)
              showApplyAllNotice = true
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Apply to All Clips",
              tint = AmberAccent,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        // Quick Preview / Play from animation start
        if (selectedVideoClip != null) {
          IconButton(
            onClick = {
              val seekTarget = when (activeCategoryTab) {
                AnimationCategoryTab.IN -> selectedVideoClip.timelineStartMs
                AnimationCategoryTab.OUT -> (selectedVideoClip.timelineStartMs + selectedVideoClip.durationMs - selectedVideoClip.animation.outDurationMs).coerceAtLeast(selectedVideoClip.timelineStartMs)
                else -> selectedVideoClip.timelineStartMs
              }
              viewModel.timelineEngine.seekTo(seekTarget)
              if (!isPlaying) {
                viewModel.timelineEngine.togglePlayPause()
              }
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = "Preview Animation",
              tint = CyanAccent,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        IconButton(
          onClick = { viewModel.setActiveToolbarTab(null) },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
      }
    }

    // Apply-All Notification Banner
    AnimatedVisibility(visible = showApplyAllNotice) {
      Surface(
        color = AmberAccent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
          Text(
            text = "Applied animation settings to all ${timeline.videoClips.size} clips in timeline!",
            fontSize = 11.sp,
            color = AmberAccent,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Clip Selection Row if multiple video/overlay clips exist
    if (timeline.videoClips.size + timeline.overlayClips.size > 1) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(timeline.videoClips) { clip ->
          val isTarget = selectedVideoClip?.id == clip.id
          val hasAnim = clip.animation.hasAnimation
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isTarget) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
            border = BorderStroke(1.dp, if (isTarget) AmberAccent else Color.Transparent),
            modifier = Modifier.clickable {
              viewModel.timelineEngine.selectElement(SelectedTrackElement.Video(clip.id))
            }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = if (isTarget) AmberAccent else TextSecondary,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = clip.name,
                fontSize = 11.sp,
                fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                color = if (isTarget) AmberAccent else TextPrimary,
                maxLines = 1
              )
              if (hasAnim) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberAccent))
              }
            }
          }
        }
        items(timeline.overlayClips) { overlay ->
          val isTarget = selectedVideoClip?.id == overlay.id
          val hasAnim = overlay.animation.hasAnimation
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isTarget) PurpleAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
            border = BorderStroke(1.dp, if (isTarget) PurpleAccent else Color.Transparent),
            modifier = Modifier.clickable {
              viewModel.timelineEngine.selectElement(SelectedTrackElement.Overlay(overlay.id))
            }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = if (isTarget) PurpleAccent else TextSecondary,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = "PIP: ${overlay.name}",
                fontSize = 11.sp,
                fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                color = if (isTarget) PurpleAccent else TextPrimary,
                maxLines = 1
              )
              if (hasAnim) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PurpleAccent))
              }
            }
          }
        }
      }
    }

    // Animation Timing Bar Visualizer
    if (selectedVideoClip != null && selectedVideoClip.animation.hasAnimation) {
      val clipDur = selectedVideoClip.durationMs.coerceAtLeast(100L).toFloat()
      val inPct = (selectedVideoClip.animation.inDurationMs / clipDur).coerceIn(0f, 0.45f)
      val outPct = (selectedVideoClip.animation.outDurationMs / clipDur).coerceIn(0f, 0.45f)
      val sustainPct = (1f - inPct - outPct).coerceAtLeast(0.1f)

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E293B))
        ) {
          if (selectedVideoClip.animation.inType != InAnimationType.NONE) {
            Box(
              modifier = Modifier
                .weight(inPct.coerceAtLeast(0.01f))
                .fillMaxHeight()
                .background(AmberAccent)
            )
          }
          Box(
            modifier = Modifier
              .weight(sustainPct)
              .fillMaxHeight()
              .background(if (selectedVideoClip.animation.comboType != ComboAnimationType.NONE) CyanAccent.copy(alpha = 0.6f) else Color(0xFF334155))
          )
          if (selectedVideoClip.animation.outType != OutAnimationType.NONE) {
            Box(
              modifier = Modifier
                .weight(outPct.coerceAtLeast(0.01f))
                .fillMaxHeight()
                .background(RedAccent)
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "In: ${if (selectedVideoClip.animation.inType != InAnimationType.NONE) selectedVideoClip.animation.inType.displayName else "None"}",
            fontSize = 9.sp,
            color = if (selectedVideoClip.animation.inType != InAnimationType.NONE) AmberAccent else TextTertiary
          )
          Text(
            text = "Loop: ${if (selectedVideoClip.animation.comboType != ComboAnimationType.NONE) selectedVideoClip.animation.comboType.displayName else "None"}",
            fontSize = 9.sp,
            color = if (selectedVideoClip.animation.comboType != ComboAnimationType.NONE) CyanAccent else TextTertiary
          )
          Text(
            text = "Out: ${if (selectedVideoClip.animation.outType != OutAnimationType.NONE) selectedVideoClip.animation.outType.displayName else "None"}",
            fontSize = 9.sp,
            color = if (selectedVideoClip.animation.outType != OutAnimationType.NONE) RedAccent else TextTertiary
          )
        }
      }
    }

    // Main Category Tabs
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(StudioSurfaceVariant)
        .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      AnimationCategoryTab.values().forEach { tab ->
        val isActive = activeCategoryTab == tab
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) AmberAccent else Color.Transparent)
            .clickable { activeCategoryTab = tab }
            .padding(vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = tab.label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.Black else TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    // Content Body based on selected tab
    if (selectedVideoClip != null) {
      val animSettings = selectedVideoClip.animation

      when (activeCategoryTab) {
        AnimationCategoryTab.PRESETS -> {
          PresetsTabContent(
            currentSettings = animSettings,
            onApplyPreset = { preset ->
              viewModel.timelineEngine.updateClipAnimation(selectedVideoClip.id) { preset.settings }
            },
            onResetAll = {
              viewModel.timelineEngine.clearClipAnimation(selectedVideoClip.id)
            }
          )
        }
        AnimationCategoryTab.IN -> {
          InAnimationGrid(
            currentType = animSettings.inType,
            durationMs = animSettings.inDurationMs,
            onSelectType = { inType ->
              viewModel.timelineEngine.setClipInAnimation(selectedVideoClip.id, inType)
            },
            onDurationChange = { dur ->
              viewModel.timelineEngine.updateClipAnimation(selectedVideoClip.id) {
                it.copy(inDurationMs = dur)
              }
            }
          )
        }
        AnimationCategoryTab.OUT -> {
          OutAnimationGrid(
            currentType = animSettings.outType,
            durationMs = animSettings.outDurationMs,
            onSelectType = { outType ->
              viewModel.timelineEngine.setClipOutAnimation(selectedVideoClip.id, outType)
            },
            onDurationChange = { dur ->
              viewModel.timelineEngine.updateClipAnimation(selectedVideoClip.id) {
                it.copy(outDurationMs = dur)
              }
            }
          )
        }
        AnimationCategoryTab.COMBO -> {
          ComboAnimationGrid(
            currentType = animSettings.comboType,
            speed = animSettings.speed,
            onSelectType = { comboType ->
              viewModel.timelineEngine.setClipComboAnimation(selectedVideoClip.id, comboType)
            },
            onSpeedChange = { spd ->
              viewModel.timelineEngine.updateClipAnimation(selectedVideoClip.id) {
                it.copy(speed = spd)
              }
            }
          )
        }
        AnimationCategoryTab.CUSTOMIZE -> {
          AnimationCustomisePanel(
            settings = animSettings,
            onUpdate = { updated ->
              viewModel.timelineEngine.updateClipAnimation(selectedVideoClip.id) { updated }
            },
            onClear = {
              viewModel.timelineEngine.clearClipAnimation(selectedVideoClip.id)
            },
            onApplyToAll = {
              viewModel.timelineEngine.applyAnimationToAllClips(animSettings)
              showApplyAllNotice = true
            }
          )
        }
      }
    } else if (selectedTextClip != null) {
      TextAnimationPicker(
        clip = selectedTextClip,
        onUpdate = { updated ->
          viewModel.timelineEngine.updateTextClip(updated)
        }
      )
    } else if (selectedStickerClip != null) {
      StickerAnimationPicker(
        clip = selectedStickerClip,
        onSelectAnimation = { animType ->
          viewModel.timelineEngine.updateStickerAnimation(selectedStickerClip.id, animType)
        }
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(180.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.MovieFilter, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(40.dp))
          Text(text = "No clip selected", color = TextSecondary, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun PresetsTabContent(
  currentSettings: ClipAnimationSettings,
  onApplyPreset: (QuickAnimationComboPreset) -> Unit,
  onResetAll: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(230.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Ready-to-Use 1-Tap Styles",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
      if (currentSettings.hasAnimation) {
        TextButton(
          onClick = onResetAll,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text("Reset to None", color = RedAccent, fontSize = 11.sp)
        }
      }
    }

    QUICK_COMBO_PRESETS.chunked(2).forEach { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        pair.forEach { preset ->
          val isApplied = currentSettings.inType == preset.settings.inType &&
                          currentSettings.comboType == preset.settings.comboType &&
                          currentSettings.outType == preset.settings.outType
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isApplied) preset.badgeColor.copy(alpha = 0.18f) else StudioSurfaceVariant,
            border = BorderStroke(
              width = if (isApplied) 1.5.dp else 1.dp,
              color = if (isApplied) preset.badgeColor else Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier
              .weight(1f)
              .clickable { onApplyPreset(preset) }
              .testTag("preset_${preset.id}")
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(preset.badgeColor.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = preset.badgeColor,
                    modifier = Modifier.size(14.dp)
                  )
                }
                if (isApplied) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Applied",
                    tint = preset.badgeColor,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
              Text(
                text = preset.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isApplied) preset.badgeColor else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = preset.description,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InAnimationGrid(
  currentType: InAnimationType,
  durationMs: Long,
  onSelectType: (InAnimationType) -> Unit,
  onDurationChange: (Long) -> Unit
) {
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "Slide", "Zoom & Scale", "Rotate & 3D", "Special")

  val filteredTypes = remember(selectedFilter) {
    when (selectedFilter) {
      "Slide" -> InAnimationType.values().filter { it.name.contains("SLIDE") || it.name.contains("SWING") }
      "Zoom & Scale" -> InAnimationType.values().filter { it.name.contains("ZOOM") || it.name.contains("POP") || it.name.contains("BOUNCE") }
      "Rotate & 3D" -> InAnimationType.values().filter { it.name.contains("SPIN") || it.name.contains("FLIP") }
      "Special" -> InAnimationType.values().filter { it.name.contains("GLITCH") || it.name.contains("WIPE") || it.name.contains("BLUR") || it.name.contains("ELASTIC") || it.name.contains("FADE") }
      else -> InAnimationType.values().toList()
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Duration Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Entrance Duration",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary
      )
      Text(
        text = String.format("%.1fs", durationMs / 1000f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AmberAccent
      )
    }
    Slider(
      value = (durationMs / 1000f).coerceIn(0.1f, 3.0f),
      onValueChange = { onDurationChange((it * 1000f).toLong()) },
      valueRange = 0.1f..3.0f,
      colors = SliderDefaults.colors(
        thumbColor = AmberAccent,
        activeTrackColor = AmberAccent,
        inactiveTrackColor = StudioSurfaceVariant
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(20.dp)
        .testTag("in_anim_duration_slider")
    )

    // Category Filter Chips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(filters) { f ->
        val isSel = selectedFilter == f
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isSel) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSel) AmberAccent else Color.Transparent),
          modifier = Modifier.clickable { selectedFilter = f }
        ) {
          Text(
            text = f,
            fontSize = 11.sp,
            color = if (isSel) AmberAccent else TextSecondary,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }

    // Presets Grid
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      items(filteredTypes) { type ->
        val isSelected = currentType == type
        AnimationCard(
          label = type.displayName,
          isSelected = isSelected,
          onClick = { onSelectType(type) }
        ) {
          AnimatedInThumbnail(type = type)
        }
      }
    }
  }
}

@Composable
private fun OutAnimationGrid(
  currentType: OutAnimationType,
  durationMs: Long,
  onSelectType: (OutAnimationType) -> Unit,
  onDurationChange: (Long) -> Unit
) {
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "Slide", "Zoom & Scale", "Rotate & 3D", "Special")

  val filteredTypes = remember(selectedFilter) {
    when (selectedFilter) {
      "Slide" -> OutAnimationType.values().filter { it.name.contains("SLIDE") || it.name.contains("SWING") }
      "Zoom & Scale" -> OutAnimationType.values().filter { it.name.contains("ZOOM") || it.name.contains("POP") || it.name.contains("BOUNCE") }
      "Rotate & 3D" -> OutAnimationType.values().filter { it.name.contains("SPIN") || it.name.contains("FLIP") }
      "Special" -> OutAnimationType.values().filter { it.name.contains("GLITCH") || it.name.contains("WIPE") || it.name.contains("BLUR") || it.name.contains("FADE") }
      else -> OutAnimationType.values().toList()
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Duration Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Exit Duration",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary
      )
      Text(
        text = String.format("%.1fs", durationMs / 1000f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AmberAccent
      )
    }
    Slider(
      value = (durationMs / 1000f).coerceIn(0.1f, 3.0f),
      onValueChange = { onDurationChange((it * 1000f).toLong()) },
      valueRange = 0.1f..3.0f,
      colors = SliderDefaults.colors(
        thumbColor = AmberAccent,
        activeTrackColor = AmberAccent,
        inactiveTrackColor = StudioSurfaceVariant
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(20.dp)
        .testTag("out_anim_duration_slider")
    )

    // Category Filter Chips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(filters) { f ->
        val isSel = selectedFilter == f
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isSel) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSel) AmberAccent else Color.Transparent),
          modifier = Modifier.clickable { selectedFilter = f }
        ) {
          Text(
            text = f,
            fontSize = 11.sp,
            color = if (isSel) AmberAccent else TextSecondary,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }

    // Presets Grid
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      items(filteredTypes) { type ->
        val isSelected = currentType == type
        AnimationCard(
          label = type.displayName,
          isSelected = isSelected,
          onClick = { onSelectType(type) }
        ) {
          AnimatedOutThumbnail(type = type)
        }
      }
    }
  }
}

@Composable
private fun ComboAnimationGrid(
  currentType: ComboAnimationType,
  speed: Float,
  onSelectType: (ComboAnimationType) -> Unit,
  onSpeedChange: (Float) -> Unit
) {
  var selectedFilter by remember { mutableStateOf("All") }
  val filters = listOf("All", "Rhythm & Pulse", "Movement & Wave", "Special FX")

  val filteredTypes = remember(selectedFilter) {
    when (selectedFilter) {
      "Rhythm & Pulse" -> ComboAnimationType.values().filter { it.name.contains("PULSE") || it.name.contains("HEARTBEAT") || it.name.contains("BREATHE") }
      "Movement & Wave" -> ComboAnimationType.values().filter { it.name.contains("FLOAT") || it.name.contains("PENDULUM") || it.name.contains("WAVE") || it.name.contains("SPIN") }
      "Special FX" -> ComboAnimationType.values().filter { it.name.contains("SHAKE") || it.name.contains("JITTER") || it.name.contains("FLASH") }
      else -> ComboAnimationType.values().toList()
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Speed Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Loop Speed",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary
      )
      Text(
        text = String.format("%.1fx", speed),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = CyanAccent
      )
    }
    Slider(
      value = speed.coerceIn(0.5f, 3.0f),
      onValueChange = onSpeedChange,
      valueRange = 0.5f..3.0f,
      colors = SliderDefaults.colors(
        thumbColor = CyanAccent,
        activeTrackColor = CyanAccent,
        inactiveTrackColor = StudioSurfaceVariant
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(20.dp)
        .testTag("combo_anim_speed_slider")
    )

    // Category Filter Chips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(filters) { f ->
        val isSel = selectedFilter == f
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isSel) CyanAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSel) CyanAccent else Color.Transparent),
          modifier = Modifier.clickable { selectedFilter = f }
        ) {
          Text(
            text = f,
            fontSize = 11.sp,
            color = if (isSel) CyanAccent else TextSecondary,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }

    // Presets Grid
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      items(filteredTypes) { type ->
        val isSelected = currentType == type
        AnimationCard(
          label = type.displayName,
          isSelected = isSelected,
          onClick = { onSelectType(type) }
        ) {
          AnimatedComboThumbnail(type = type)
        }
      }
    }
  }
}

@Composable
private fun AnimationCustomisePanel(
  settings: ClipAnimationSettings,
  onUpdate: (ClipAnimationSettings) -> Unit,
  onClear: () -> Unit,
  onApplyToAll: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(230.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Intensity Slider
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Intensity / Amplitude", fontSize = 12.sp, color = TextSecondary)
        Text(String.format("%.1fx", settings.intensity), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
      }
      Slider(
        value = settings.intensity.coerceIn(0.2f, 2.0f),
        onValueChange = { onUpdate(settings.copy(intensity = it)) },
        valueRange = 0.2f..2.0f,
        colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
      )
    }

    // Easing Picker
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Motion Easing Curve", fontSize = 12.sp, color = TextSecondary)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(AnimationEasing.values()) { easing ->
          val isSelected = settings.easing == easing
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
            border = BorderStroke(1.dp, if (isSelected) AmberAccent else Color.Transparent),
            modifier = Modifier.clickable { onUpdate(settings.copy(easing = easing)) }
          ) {
            Text(
              text = easing.displayName,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) AmberAccent else TextPrimary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
          }
        }
      }
    }

    // Status Summary Card
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = StudioSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Active Configuration", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
        Text("• In: ${settings.inType.displayName} (${settings.inDurationMs}ms)", fontSize = 11.sp, color = TextSecondary)
        Text("• Out: ${settings.outType.displayName} (${settings.outDurationMs}ms)", fontSize = 11.sp, color = TextSecondary)
        Text("• Loop: ${settings.comboType.displayName} (Speed: ${settings.speed}x)", fontSize = 11.sp, color = TextSecondary)
        Text("• Easing: ${settings.easing.displayName}", fontSize = 11.sp, color = TextSecondary)
      }
    }

    // Action Buttons Row: Apply to All + Clear All
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onApplyToAll,
        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent.copy(alpha = 0.2f), contentColor = AmberAccent),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Apply to All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }

      Button(
        onClick = onClear,
        colors = ButtonDefaults.buttonColors(containerColor = RedAccent.copy(alpha = 0.2f), contentColor = RedAccent),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Remove All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun AnimationCard(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  content: @Composable () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (isSelected) AmberAccent.copy(alpha = 0.15f) else StudioSurfaceVariant,
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) AmberAccent else Color.White.copy(alpha = 0.08f)
    ),
    modifier = Modifier
      .height(82.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .testTag("anim_item_${label.replace(" ", "_").lowercase()}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(if (isSelected) AmberAccent.copy(alpha = 0.25f) else Color(0xFF1E293B)),
        contentAlignment = Alignment.Center
      ) {
        content()
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) AmberAccent else TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )
    }
  }
}

// Live interactive miniature thumbnails
@Composable
private fun AnimatedInThumbnail(type: InAnimationType) {
  val infiniteTransition = rememberInfiniteTransition()
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    )
  )

  var scale = 1.0f
  var alpha = 1.0f
  var rot = 0f
  var transX = 0f
  var transY = 0f

  when (type) {
    InAnimationType.NONE -> {}
    InAnimationType.FADE_IN -> {
      alpha = progress
    }
    InAnimationType.ZOOM_IN -> {
      scale = 0.2f + 0.8f * progress
      alpha = progress
    }
    InAnimationType.ZOOM_OUT -> {
      scale = 1.6f - 0.6f * progress
      alpha = progress
    }
    InAnimationType.SLIDE_UP -> {
      transY = (1f - progress) * 12f
      alpha = progress
    }
    InAnimationType.SLIDE_DOWN -> {
      transY = -(1f - progress) * 12f
      alpha = progress
    }
    InAnimationType.SLIDE_LEFT -> {
      transX = (1f - progress) * 12f
      alpha = progress
    }
    InAnimationType.SLIDE_RIGHT -> {
      transX = -(1f - progress) * 12f
      alpha = progress
    }
    InAnimationType.SPIN_IN -> {
      rot = (1f - progress) * 360f
      scale = progress
    }
    InAnimationType.BOUNCE_IN -> {
      scale = (1f - cos(progress * PI.toFloat() * 2f) * exp(-progress * 2.5f)).coerceIn(0f, 1.2f)
    }
    InAnimationType.POP_IN -> {
      scale = if (progress < 0.7f) progress / 0.7f * 1.2f else 1.2f - (progress - 0.7f) / 0.3f * 0.2f
    }
    InAnimationType.FLIP_X -> {
      scale = abs(cos((1f - progress) * PI.toFloat() * 0.5f))
    }
    InAnimationType.FLIP_Y -> {
      scale = abs(cos((1f - progress) * PI.toFloat() * 0.5f))
    }
    InAnimationType.SWING_IN -> {
      rot = sin((1f - progress) * 5f) * 20f
    }
    InAnimationType.ELASTIC_IN -> {
      val p = progress - 1f
      scale = (p * p * (2.7f * p + 1.7f) + 1f).coerceIn(0f, 1.3f)
    }
    InAnimationType.GLITCH_IN -> {
      transX = sin(progress * 20f) * 3f
      alpha = if ((progress * 10).toInt() % 2 == 0) 0.5f else 1f
    }
    InAnimationType.WIPE_IN -> {
      scale = progress
    }
    InAnimationType.BLUR_IN -> {
      alpha = progress
    }
  }

  Box(
    modifier = Modifier
      .size(18.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rot
        translationX = transX
        translationY = transY
        this.alpha = alpha
      }
      .clip(RoundedCornerShape(3.dp))
      .background(AmberAccent)
  )
}

@Composable
private fun AnimatedOutThumbnail(type: OutAnimationType) {
  val infiniteTransition = rememberInfiniteTransition()
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    )
  )

  var scale = 1.0f
  var alpha = 1.0f
  var rot = 0f
  var transX = 0f
  var transY = 0f

  when (type) {
    OutAnimationType.NONE -> {}
    OutAnimationType.FADE_OUT -> {
      alpha = 1f - progress
    }
    OutAnimationType.ZOOM_OUT -> {
      scale = 1f - 0.7f * progress
      alpha = 1f - progress
    }
    OutAnimationType.ZOOM_IN_OUT -> {
      scale = 1f + 0.8f * progress
      alpha = 1f - progress
    }
    OutAnimationType.SLIDE_UP_OUT -> {
      transY = -progress * 12f
      alpha = 1f - progress
    }
    OutAnimationType.SLIDE_DOWN_OUT -> {
      transY = progress * 12f
      alpha = 1f - progress
    }
    OutAnimationType.SLIDE_LEFT_OUT -> {
      transX = -progress * 12f
      alpha = 1f - progress
    }
    OutAnimationType.SLIDE_RIGHT_OUT -> {
      transX = progress * 12f
      alpha = 1f - progress
    }
    OutAnimationType.SPIN_OUT -> {
      rot = progress * 360f
      scale = 1f - progress
      alpha = 1f - progress
    }
    OutAnimationType.BOUNCE_OUT -> {
      scale = (1f - progress) * (1f + sin(progress * 6f) * 0.2f)
      alpha = 1f - progress
    }
    OutAnimationType.POP_OUT -> {
      scale = 1f - progress * 0.8f
      alpha = 1f - progress
    }
    OutAnimationType.FLIP_X_OUT -> {
      scale = abs(cos(progress * PI.toFloat() * 0.5f))
      alpha = 1f - progress
    }
    OutAnimationType.SWING_OUT -> {
      rot = sin(progress * 5f) * 20f
      alpha = 1f - progress
    }
    OutAnimationType.GLITCH_OUT -> {
      transX = sin(progress * 20f) * 3f
      alpha = (1f - progress)
    }
    OutAnimationType.WIPE_OUT -> {
      scale = 1f - progress
      alpha = 1f - progress
    }
    OutAnimationType.BLUR_OUT -> {
      alpha = 1f - progress
    }
  }

  Box(
    modifier = Modifier
      .size(18.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rot
        translationX = transX
        translationY = transY
        this.alpha = alpha
      }
      .clip(RoundedCornerShape(3.dp))
      .background(AmberAccent)
  )
}

@Composable
private fun AnimatedComboThumbnail(type: ComboAnimationType) {
  val infiniteTransition = rememberInfiniteTransition()
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    )
  )

  var scale = 1.0f
  var alpha = 1.0f
  var rot = 0f
  var transX = 0f
  var transY = 0f

  val cycle = progress * 2f * PI.toFloat()

  when (type) {
    ComboAnimationType.NONE -> {}
    ComboAnimationType.PULSE -> {
      scale = 1.0f + 0.2f * sin(cycle)
    }
    ComboAnimationType.HEARTBEAT -> {
      val ph = progress
      val beat = if (ph < 0.2f) sin(ph / 0.2f * PI.toFloat()) * 0.3f else if (ph in 0.25f..0.45f) sin((ph - 0.25f) / 0.2f * PI.toFloat()) * 0.2f else 0f
      scale = 1.0f + beat
    }
    ComboAnimationType.PENDULUM -> {
      rot = sin(cycle) * 18f
    }
    ComboAnimationType.FLOAT -> {
      transY = sin(cycle) * 5f
      transX = cos(cycle) * 3f
    }
    ComboAnimationType.SHAKE -> {
      transX = sin(progress * 25f) * 3f
      transY = cos(progress * 25f) * 3f
      rot = sin(progress * 20f) * 6f
    }
    ComboAnimationType.JITTER -> {
      transX = ((progress * 13) % 1f - 0.5f) * 6f
      transY = ((progress * 17) % 1f - 0.5f) * 6f
    }
    ComboAnimationType.FLASH_PULSE -> {
      alpha = 0.4f + 0.6f * (0.5f + 0.5f * sin(cycle))
    }
    ComboAnimationType.WAVE -> {
      rot = sin(cycle) * 12f
      scale = 1.0f + sin(cycle) * 0.1f
    }
    ComboAnimationType.SPIN_360 -> {
      rot = progress * 360f
    }
    ComboAnimationType.BREATHE -> {
      scale = 1.0f + sin(cycle) * 0.15f
    }
    ComboAnimationType.ZOOM_PULSE -> {
      scale = 0.9f + abs(sin(cycle)) * 0.25f
    }
  }

  Box(
    modifier = Modifier
      .size(18.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rot
        translationX = transX
        translationY = transY
        this.alpha = alpha
      }
      .clip(RoundedCornerShape(3.dp))
      .background(CyanAccent)
  )
}

@Composable
private fun TextAnimationPicker(
  clip: TextClip,
  onUpdate: (TextClip) -> Unit
) {
  val textAnimPresets = listOf("None", "Fade", "Slide", "Zoom", "Pop", "Bounce", "Typewriter", "Shake")

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Text Entrance Animation",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary
    )

    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
    ) {
      items(textAnimPresets) { preset ->
        val isSelected = clip.animationType.equals(preset, ignoreCase = true)
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isSelected) AmberAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSelected) AmberAccent else Color.Transparent),
          modifier = Modifier
            .height(54.dp)
            .clickable { onUpdate(clip.copy(animationType = preset)) }
        ) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.TextFields,
              contentDescription = null,
              tint = if (isSelected) AmberAccent else TextSecondary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = preset,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) AmberAccent else TextPrimary
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StickerAnimationPicker(
  clip: StickerClip,
  onSelectAnimation: (StickerAnimationType) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Sticker Motion Animation",
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary
    )

    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
    ) {
      items(StickerAnimationType.values()) { animType ->
        val isSelected = clip.animationType == animType
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isSelected) PurpleAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
          border = BorderStroke(1.dp, if (isSelected) PurpleAccent else Color.Transparent),
          modifier = Modifier
            .height(50.dp)
            .clickable { onSelectAnimation(animType) }
        ) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = animType.displayName,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) PurpleAccent else TextPrimary,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}
