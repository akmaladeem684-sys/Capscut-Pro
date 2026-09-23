package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.transition.core.Easing
import com.ahstudio.transition.integration.TransitionAppBridge
import com.example.domain.model.TransitionType
import com.example.ui.StudioViewModel
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PinkAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RedAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.util.Locale

enum class TransitionCategory(val title: String) {
  ALL("All"),
  DISSOLVES("Dissolves & Fades"),
  MOVEMENT("Movement & Wipes"),
  DYNAMIC("Dynamic & 3D")
}

data class TransitionItemData(
  val type: TransitionType,
  val category: TransitionCategory,
  val description: String,
  val gradient: List<Color>,
  val icon: ImageVector
)

val TRANSITION_ITEMS = listOf(
  TransitionItemData(
    type = TransitionType.DISSOLVE,
    category = TransitionCategory.DISSOLVES,
    description = "Smooth alpha cross-dissolve",
    gradient = listOf(Color(0xFF6366F1), Color(0xFFA855F7)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.FADE,
    category = TransitionCategory.DISSOLVES,
    description = "Classic cinematic fade through black",
    gradient = listOf(Color(0xFF1E293B), Color(0xFF475569)),
    icon = Icons.Default.ViewCarousel
  ),
  TransitionItemData(
    type = TransitionType.WIPE,
    category = TransitionCategory.MOVEMENT,
    description = "Horizontal curtain wipe across screen",
    gradient = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.SLIDE_LEFT,
    category = TransitionCategory.MOVEMENT,
    description = "Slide incoming clip from right to left",
    gradient = listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.SLIDE_RIGHT,
    category = TransitionCategory.MOVEMENT,
    description = "Slide incoming clip from left to right",
    gradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.PUSH_UP,
    category = TransitionCategory.MOVEMENT,
    description = "Push upward reveal transition",
    gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.ZOOM_IN,
    category = TransitionCategory.DYNAMIC,
    description = "Fast impactful punch-in zoom",
    gradient = listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    icon = Icons.Default.AutoAwesome
  ),
  TransitionItemData(
    type = TransitionType.ZOOM_OUT,
    category = TransitionCategory.DYNAMIC,
    description = "Wide punch-out reveal zoom",
    gradient = listOf(Color(0xFFF97316), Color(0xFFFBBF24)),
    icon = Icons.Default.AutoAwesome
  ),
  TransitionItemData(
    type = TransitionType.SPIN,
    category = TransitionCategory.DYNAMIC,
    description = "360-degree rotational spin",
    gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)),
    icon = Icons.Default.Refresh
  ),
  TransitionItemData(
    type = TransitionType.FLASH,
    category = TransitionCategory.DISSOLVES,
    description = "High-energy white flare flash cut",
    gradient = listOf(Color(0xFFFBBF24), Color(0xFFFFFFFF)),
    icon = Icons.Default.FlashOn
  ),
  TransitionItemData(
    type = TransitionType.BLUR,
    category = TransitionCategory.DISSOLVES,
    description = "Dreamy optical blur dissolve",
    gradient = listOf(Color(0xFF38BDF8), Color(0xFF818CF8)),
    icon = Icons.Default.Waves
  ),
  TransitionItemData(
    type = TransitionType.GLITCH,
    category = TransitionCategory.DYNAMIC,
    description = "Cyberpunk digital glitch cut",
    gradient = listOf(Color(0xFF06B6D4), Color(0xFFEC4899)),
    icon = Icons.Default.AutoAwesome
  ),
  TransitionItemData(
    type = TransitionType.WHIP_PAN,
    category = TransitionCategory.MOVEMENT,
    description = "High-speed camera whip pan swipe",
    gradient = listOf(Color(0xFF3B82F6), Color(0xFF10B981)),
    icon = Icons.Default.Transform
  ),
  TransitionItemData(
    type = TransitionType.ZOOM_BLUR,
    category = TransitionCategory.DYNAMIC,
    description = "Directional zoom blur burst",
    gradient = listOf(Color(0xFFF43F5E), Color(0xFF8B5CF6)),
    icon = Icons.Default.AutoAwesome
  ),
  TransitionItemData(
    type = TransitionType.GLITCH_WIPE,
    category = TransitionCategory.DYNAMIC,
    description = "Digital noise displacement wipe",
    gradient = listOf(Color(0xFF14B8A6), Color(0xFFF59E0B)),
    icon = Icons.Default.AutoAwesome
  ),
  TransitionItemData(
    type = TransitionType.LIGHT_LEAK,
    category = TransitionCategory.DISSOLVES,
    description = "Warm anamorphic light leak flare",
    gradient = listOf(Color(0xFFF97316), Color(0xFFFACC15)),
    icon = Icons.Default.FlashOn
  )
)

enum class EasingPreset(val label: String, val type: Easing.Type) {
  EASE_IN_OUT("Smooth", Easing.Type.EASE_IN_OUT),
  LINEAR("Linear", Easing.Type.LINEAR),
  CUBIC("Cubic", Easing.Type.CUBIC_IN_OUT),
  BOUNCE("Bounce", Easing.Type.BOUNCE_OUT),
  ELASTIC("Elastic", Easing.Type.ELASTIC_OUT)
}

@Composable
fun TransitionsPanel(
  viewModel: StudioViewModel,
  onStartDragTransition: ((TransitionType) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedCutIndex by viewModel.timelineEngine.selectedTransitionCutIndex.collectAsState()

  val videoClips = timeline.videoClips
  val totalCuts = (videoClips.size - 1).coerceAtLeast(0)

  val currentCutIndex = selectedCutIndex.coerceIn(0, (totalCuts - 1).coerceAtLeast(0))
  val currentTransition = timeline.transitions.find { it.clipIndexBefore == currentCutIndex }

  var selectedCategory by remember { mutableStateOf(TransitionCategory.ALL) }
  var selectedEasing by remember { mutableStateOf(EasingPreset.EASE_IN_OUT) }
  var durationMs by remember(currentTransition) {
    mutableLongStateOf(currentTransition?.durationMs ?: 500L)
  }
  var autoWhooshSfx by remember { mutableStateOf(true) }
  var showApplyAllSuccess by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .border(1.dp, StudioBorder)
      .padding(10.dp)
      .testTag("transitions_panel")
  ) {
    // 1. Top Bar: Header, Live Preview simulation badge & Close
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(PurpleAccent.copy(alpha = 0.25f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Transform, contentDescription = "Transitions", tint = PurpleAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Transitions Engine",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(CyanAccent.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text("GPU Accelerated", color = CyanAccent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          Text(
            text = if (totalCuts > 0) "Tap any transition or drag to cut" else "Add 2+ clips on track to apply transitions",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
          )
        }
      }

      IconButton(
        onClick = { viewModel.setActiveToolbarTab(null) },
        modifier = Modifier.size(28.dp).testTag("close_transitions_panel_btn")
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    if (totalCuts == 0) {
      // Empty state when only 0 or 1 clip is on timeline
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(StudioSurfaceVariant)
          .padding(14.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.Info, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Transitions require 2 or more video clips.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium),
            fontSize = 12.sp
          )
          Text(
            text = "Split a clip or import more media to place seamless transitions.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            fontSize = 10.sp
          )
        }
      }
      return
    }

    // 2. Cut Junction Selector Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .background(StudioDarkBg)
        .padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Cut:",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )

        for (cutIdx in 0 until totalCuts) {
          val isSelectedCut = cutIdx == currentCutIndex
          val trAtCut = timeline.transitions.find { it.clipIndexBefore == cutIdx }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelectedCut) PurpleAccent else StudioSurfaceVariant)
              .border(1.dp, if (isSelectedCut) Color.White else StudioBorder, RoundedCornerShape(4.dp))
              .clickable { viewModel.timelineEngine.setSelectedTransitionCutIndex(cutIdx) }
              .padding(horizontal = 6.dp, vertical = 3.dp)
              .testTag("cut_target_chip_$cutIdx")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Cut #${cutIdx + 1}",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = if (isSelectedCut) Color.White else TextPrimary,
                  fontSize = 9.sp,
                  fontWeight = if (isSelectedCut) FontWeight.Bold else FontWeight.Normal
                )
              )
              if (trAtCut != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(CyanAccent)
                )
              }
            }
          }
        }
      }

      // Quick Delete Cut Transition
      if (currentTransition != null) {
        IconButton(
          onClick = { viewModel.timelineEngine.removeTransition(currentCutIndex) },
          modifier = Modifier.size(24.dp).testTag("delete_transition_btn")
        ) {
          Icon(Icons.Default.Delete, contentDescription = "Remove Transition", tint = RedAccent, modifier = Modifier.size(13.dp))
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 3. Compact Controls: Duration slider + Easing chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Active: ",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
        )
        Text(
          text = currentTransition?.type?.displayName ?: "None (Cut)",
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (currentTransition != null) CyanAccent else TextTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
          )
        )
      }

      Text(
        text = String.format(Locale.US, "%.1fs (%d ms)", durationMs / 1000f, durationMs),
        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
      )
    }

    // Duration slider + Quick chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Slider(
        value = durationMs.toFloat(),
        onValueChange = { newMs ->
          durationMs = newMs.toLong()
          if (currentTransition != null) {
            viewModel.timelineEngine.setTransitionDuration(currentCutIndex, durationMs)
          }
        },
        valueRange = 100f..2000f,
        colors = SliderDefaults.colors(
          thumbColor = PurpleAccent,
          activeTrackColor = PurpleAccent,
          inactiveTrackColor = StudioBorder
        ),
        modifier = Modifier.weight(1f).height(20.dp)
      )

      Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(300L, 500L, 800L, 1000L).forEach { presetMs ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(if (durationMs == presetMs) PurpleAccent else StudioSurfaceVariant)
              .clickable {
                durationMs = presetMs
                if (currentTransition != null) {
                  viewModel.timelineEngine.setTransitionDuration(currentCutIndex, presetMs)
                }
              }
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Text(
              text = "${presetMs / 1000f}s",
              style = MaterialTheme.typography.bodySmall.copy(
                color = if (durationMs == presetMs) Color.White else TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }
      }
    }

    // Easing Presets Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Curve:",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
      )
      EasingPreset.values().forEach { preset ->
        val isSelected = selectedEasing == preset
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) CyanAccent.copy(alpha = 0.2f) else StudioSurfaceVariant)
            .border(1.dp, if (isSelected) CyanAccent else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { selectedEasing = preset }
            .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
          Text(
            text = preset.label,
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isSelected) CyanAccent else TextSecondary,
              fontSize = 9.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // 4. Category Tabs
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      TransitionCategory.values().forEach { cat ->
        val isCatSelected = selectedCategory == cat
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isCatSelected) StudioSurfaceVariant else Color.Transparent)
            .border(1.dp, if (isCatSelected) CyanAccent else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { selectedCategory = cat }
            .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
          Text(
            text = cat.title,
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isCatSelected) CyanAccent else TextSecondary,
              fontSize = 10.sp,
              fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 5. Transition Cards Grid (Lightweight & Smooth)
    val filteredItems = remember(selectedCategory) {
      if (selectedCategory == TransitionCategory.ALL) TRANSITION_ITEMS
      else TRANSITION_ITEMS.filter { it.category == selectedCategory }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
    ) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filteredItems) { item ->
          val isAppliedToCurrentCut = currentTransition?.type == item.type

          TransitionCard(
            item = item,
            isApplied = isAppliedToCurrentCut,
            onApply = {
              viewModel.timelineEngine.setTransition(currentCutIndex, item.type, durationMs)
              if (autoWhooshSfx) {
                viewModel.timelineEngine.addTransitionSoundEffect(currentCutIndex, "${item.type.displayName} Whoosh")
              }
            },
            onStartDrag = {
              onStartDragTransition?.invoke(item.type)
            }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 6. Bottom Actions: Apply to All & Sound Effect Toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Auto Whoosh SFX Switch
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text("Whoosh SFX", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 10.sp))
        Spacer(modifier = Modifier.width(2.dp))
        Switch(
          checked = autoWhooshSfx,
          onCheckedChange = { autoWhooshSfx = it },
          colors = SwitchDefaults.colors(
            checkedThumbColor = CyanAccent,
            checkedTrackColor = CyanAccent.copy(alpha = 0.4f),
            uncheckedTrackColor = StudioBorder
          ),
          modifier = Modifier.scale(0.65f)
        )
      }

      // Apply to all cuts button
      Button(
        onClick = {
          val activeType = currentTransition?.type ?: TransitionType.DISSOLVE
          viewModel.timelineEngine.applyTransitionToAllCuts(activeType, durationMs)
          showApplyAllSuccess = true
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = PurpleAccent,
          contentColor = Color.White
        ),
        modifier = Modifier.height(28.dp).testTag("apply_all_transitions_btn")
      ) {
        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text = if (showApplyAllSuccess) "Applied to All!" else "Apply to All Cuts",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun TransitionCard(
  item: TransitionItemData,
  isApplied: Boolean,
  onApply: () -> Unit,
  onStartDrag: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .clip(RoundedCornerShape(6.dp))
      .border(
        width = if (isApplied) 2.dp else 1.dp,
        color = if (isApplied) CyanAccent else StudioBorder,
        shape = RoundedCornerShape(6.dp)
      )
      .pointerInput(item.type) {
        detectDragGestures(
          onDragStart = { onStartDrag() },
          onDragEnd = {},
          onDragCancel = {},
          onDrag = { change, _ -> change.consume() }
        )
      }
      .clickable { onApply() }
      .testTag("transition_card_${item.type.name.lowercase()}"),
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Top Gradient Stripe
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .background(Brush.horizontalGradient(item.gradient))
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(5.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = item.icon,
            contentDescription = item.type.displayName,
            tint = if (isApplied) CyanAccent else TextPrimary,
            modifier = Modifier.size(15.dp)
          )

          // Drag Indicator pill
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(2.dp))
              .background(StudioDarkBg)
              .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = TextTertiary, modifier = Modifier.size(8.dp))
            Text("DRAG", fontSize = 6.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
          }
        }

        Text(
          text = item.type.displayName,
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (isApplied) CyanAccent else TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
          ),
          maxLines = 1
        )

        if (isApplied) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = "Applied", tint = CyanAccent, modifier = Modifier.size(9.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("Active", fontSize = 8.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
          }
        } else {
          Text(
            text = "Tap / Drag",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 8.sp)
          )
        }
      }
    }
  }
}
