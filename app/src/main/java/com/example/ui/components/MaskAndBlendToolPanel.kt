package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MaskSettings
import com.example.domain.model.MaskShape
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@Composable
fun MaskAndBlendToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  // Find active selected clip or first video clip
  val activeClip = remember(timeline, selectedElement) {
    viewModel.getSelectedVideoClip()
  }

  var currentMask by remember(activeClip) {
    mutableStateOf(activeClip?.mask ?: MaskSettings())
  }
  var currentBlendMode by remember(activeClip) {
    mutableStateOf(activeClip?.blendMode ?: "Normal")
  }
  var selectedTab by remember { mutableStateOf(0) } // 0=Mask, 1=Blending

  val blendModes = listOf(
    "Normal", "Multiply", "Screen", "Overlay", "Darken",
    "Lighten", "Color Dodge", "Soft Light", "Hard Light", "Difference", "Add"
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Layers, contentDescription = null, tint = StudioPrimary)
        Text(
          text = "Masking & Blending Engine",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Tab Switcher (Mask vs Blend)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(StudioSurfaceVariant)
        .padding(4.dp)
    ) {
      listOf("GPU Masking", "Blend Modes").forEachIndexed { index, label ->
        val isSel = selectedTab == index
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSel) StudioPrimary else Color.Transparent)
            .clickable { selectedTab = index }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
            color = if (isSel) Color.White else TextSecondary
          )
        }
      }
    }

    if (selectedTab == 0) {
      // GPU Masking Controls
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Mask Shape", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          MaskShape.values().forEach { shape ->
            val isSelected = currentMask.shape == shape && currentMask.enabled
            val label = when (shape) {
              MaskShape.NONE -> "None"
              MaskShape.RECTANGLE -> "Rectangle"
              MaskShape.CIRCLE -> "Circle"
              MaskShape.LINEAR -> "Linear"
              MaskShape.MIRROR -> "Mirror"
              MaskShape.STAR -> "Star"
              MaskShape.HEART -> "Heart"
            }
            FilterChip(
              selected = isSelected,
              onClick = {
                val newMask = currentMask.copy(shape = shape, enabled = shape != MaskShape.NONE)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              label = { Text(label, fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = StudioPrimary,
                selectedLabelColor = Color.White
              )
            )
          }
        }

        if (currentMask.shape != MaskShape.NONE && currentMask.enabled) {
          // Invert Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Invert Mask", fontSize = 13.sp, color = TextPrimary)
            Switch(
              checked = currentMask.isInverted,
              onCheckedChange = { inv ->
                val newMask = currentMask.copy(isInverted = inv)
                currentMask = newMask
                viewModel.timelineEngine.setClipMask(mask = newMask)
              },
              colors = SwitchDefaults.colors(checkedThumbColor = StudioPrimary)
            )
          }

          // Size / Scale
          Text("Mask Size: ${(currentMask.width * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
          Slider(
            value = currentMask.width,
            onValueChange = { sz ->
              val newMask = currentMask.copy(width = sz, height = sz)
              currentMask = newMask
              viewModel.timelineEngine.setClipMask(mask = newMask)
            },
            valueRange = 0.1f..2.0f
          )

          // Feather
          Text("Feather Softness: ${(currentMask.feather * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
          Slider(
            value = currentMask.feather,
            onValueChange = { f ->
              val newMask = currentMask.copy(feather = f)
              currentMask = newMask
              viewModel.timelineEngine.setClipMask(mask = newMask)
            },
            valueRange = 0.0f..1.0f
          )

          // Rotation
          Text("Rotation: ${currentMask.rotation.toInt()}°", fontSize = 12.sp, color = TextSecondary)
          Slider(
            value = currentMask.rotation,
            onValueChange = { r ->
              val newMask = currentMask.copy(rotation = r)
              currentMask = newMask
              viewModel.timelineEngine.setClipMask(mask = newMask)
            },
            valueRange = -180f..180f
          )

          // Opacity
          Text("Opacity: ${(currentMask.opacity * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
          Slider(
            value = currentMask.opacity,
            onValueChange = { op ->
              val newMask = currentMask.copy(opacity = op)
              currentMask = newMask
              viewModel.timelineEngine.setClipMask(mask = newMask)
            },
            valueRange = 0.0f..1.0f
          )
        }
      }
    } else {
      // Blend Modes
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("GPU Composite Blend Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        blendModes.chunked(3).forEach { rowModes ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rowModes.forEach { mode ->
              val isSel = currentBlendMode.equals(mode, ignoreCase = true)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSel) StudioPrimary else StudioSurfaceVariant)
                  .border(
                    width = if (isSel) 2.dp else 1.dp,
                    color = if (isSel) StudioPrimary else StudioBorder,
                    shape = RoundedCornerShape(10.dp)
                  )
                  .clickable {
                    currentBlendMode = mode
                    viewModel.timelineEngine.setClipBlendMode(blendMode = mode)
                  }
                  .padding(vertical = 12.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = mode,
                  fontSize = 12.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSel) Color.White else TextPrimary
                )
              }
            }
          }
        }
      }
    }
  }
}
