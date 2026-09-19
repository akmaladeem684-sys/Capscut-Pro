package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Transition
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
    description = "Smooth alpha cross-dissolve between clips",
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
    description = "360-degree rotational spin transition",
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
    description = "Explosive directional zoom blur burst",
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
    description = "Vintage anamorphic warm light leak burst",
    gradient = listOf(Color(0xFFF97316), Color(0xFFFACC15)),
    icon = Icons.Default.FlashOn
  )
)

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
      .padding(12.dp)
      .testTag("transitions_panel")
  ) {
    // Top Bar: Header, Badge, and Close
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PurpleAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Transform, contentDescription = "Transitions", tint = PurpleAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = "Video Transitions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
          )
          Text(
            text = if (totalCuts > 0) "Drag onto timeline cuts or tap to apply" else "Add at least 2 video clips to apply transitions",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
        }
      }

      IconButton(onClick = { viewModel.setActiveToolbarTab(null) }) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (totalCuts == 0) {
      // Empty state when only 0 or 1 clip is on timeline
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(StudioSurfaceVariant)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.Info, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Transitions require 2 or more video clips on the main track.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium),
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Split a clip or import additional media to connect cuts with transitions.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            fontSize = 11.sp
          )
        }
      }
      return
    }

    // Cut Junction Target Selector & Current Status Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(StudioDarkBg)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Cut selector chips
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Cut Target:",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )

        for (cutIdx in 0 until totalCuts) {
          val isSelectedCut = cutIdx == currentCutIndex
          val trAtCut = timeline.transitions.find { it.clipIndexBefore == cutIdx }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelectedCut) PurpleAccent else StudioSurfaceVariant)
              .border(1.dp, if (isSelectedCut) Color.White else StudioBorder, RoundedCornerShape(6.dp))
              .clickable { viewModel.timelineEngine.setSelectedTransitionCutIndex(cutIdx) }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("cut_target_chip_$cutIdx")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Cut ${cutIdx + 1} (#${cutIdx + 1}➔#${cutIdx + 2})",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = if (isSelectedCut) Color.White else TextPrimary,
                  fontSize = 10.sp,
                  fontWeight = if (isSelectedCut) FontWeight.Bold else FontWeight.Normal
                )
              )
              if (trAtCut != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                  modifier = Modifier
                    .size(6.dp)
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
          modifier = Modifier.size(28.dp).testTag("delete_transition_btn")
        ) {
          Icon(Icons.Default.Delete, contentDescription = "Remove Transition", tint = RedAccent, modifier = Modifier.size(15.dp))
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Active Transition Summary & Duration Scrubber
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Selected: ",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
        )
        Text(
          text = currentTransition?.type?.displayName ?: "None (Hard Cut)",
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (currentTransition != null) CyanAccent else TextTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        )
      }

      // Duration readout & preset chips
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = String.format(Locale.US, "Duration: %.1fs", durationMs / 1000f),
          style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
      }
    }

    // Duration slider + Quick chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        modifier = Modifier.weight(1f).height(24.dp)
      )

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(300L, 500L, 800L, 1000L).forEach { presetMs ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (durationMs == presetMs) PurpleAccent else StudioSurfaceVariant)
              .clickable {
                durationMs = presetMs
                if (currentTransition != null) {
                  viewModel.timelineEngine.setTransitionDuration(currentCutIndex, presetMs)
                }
              }
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "${presetMs / 1000f}s",
              style = MaterialTheme.typography.bodySmall.copy(
                color = if (durationMs == presetMs) Color.White else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Transition Category Tabs
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCatSelected) StudioSurfaceVariant else Color.Transparent)
            .border(1.dp, if (isCatSelected) CyanAccent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { selectedCategory = cat }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = cat.title,
            style = MaterialTheme.typography.bodySmall.copy(
              color = if (isCatSelected) CyanAccent else TextSecondary,
              fontSize = 11.sp,
              fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
            )
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Filtered Transition Cards Grid
    val filteredItems = remember(selectedCategory) {
      if (selectedCategory == TransitionCategory.ALL) TRANSITION_ITEMS
      else TRANSITION_ITEMS.filter { it.category == selectedCategory }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
    ) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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

    Spacer(modifier = Modifier.height(8.dp))

    // Bottom Action Row: Apply To All Cuts & Auto SFX Whoosh Switch
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Auto Whoosh SFX Switch
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Whoosh SFX", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 11.sp))
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
          checked = autoWhooshSfx,
          onCheckedChange = { autoWhooshSfx = it },
          colors = SwitchDefaults.colors(
            checkedThumbColor = CyanAccent,
            checkedTrackColor = CyanAccent.copy(alpha = 0.4f),
            uncheckedTrackColor = StudioBorder
          ),
          modifier = Modifier.scale(0.7f)
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
        modifier = Modifier.height(30.dp).testTag("apply_all_transitions_btn")
      ) {
        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (showApplyAllSuccess) "Applied to All!" else "Apply to All Cuts",
          fontSize = 10.sp,
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
  var isDraggingThis by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(80.dp)
      .clip(RoundedCornerShape(8.dp))
      .border(
        width = if (isApplied) 2.dp else 1.dp,
        color = if (isApplied) CyanAccent else StudioBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .pointerInput(item.type) {
        detectDragGestures(
          onDragStart = {
            isDraggingThis = true
            onStartDrag()
          },
          onDragEnd = {
            isDraggingThis = false
          },
          onDragCancel = {
            isDraggingThis = false
          },
          onDrag = { change, _ ->
            change.consume()
          }
        )
      }
      .clickable { onApply() }
      .testTag("transition_card_${item.type.name.lowercase()}"),
    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Top Gradient Accent
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .background(Brush.horizontalGradient(item.gradient))
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(6.dp),
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
            modifier = Modifier.size(18.dp)
          )

          // Drag Indicator pill
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(StudioDarkBg)
              .padding(horizontal = 3.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = TextTertiary, modifier = Modifier.size(10.dp))
            Text("DRAG", fontSize = 7.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
          }
        }

        Text(
          text = item.type.displayName,
          style = MaterialTheme.typography.bodySmall.copy(
            color = if (isApplied) CyanAccent else TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          ),
          maxLines = 1
        )

        if (isApplied) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = "Applied", tint = CyanAccent, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("Active", fontSize = 8.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
          }
        } else {
          Text(
            text = "Tap or Drag",
            style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary, fontSize = 8.sp)
          )
        }
      }
    }
  }
}
