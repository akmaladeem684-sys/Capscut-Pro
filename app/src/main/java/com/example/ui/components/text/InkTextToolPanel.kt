package com.example.ui.components.text

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

data class InkStylePreset(
  val id: String,
  val name: String,
  val textColor: Color,
  val outlineColor: Color,
  val shadowColor: Color,
  val fontStyle: FontStyle = FontStyle.Normal,
  val previewEmoji: String
)

@Composable
fun InkTextToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  var inkText by remember { mutableStateOf("Ink Calligraphy") }
  
  val presets = remember {
    listOf(
      InkStylePreset("brush_black", "Black Ink", Color(0xFF1A1A1A), Color(0xFF000000), Color(0x66000000), FontStyle.Italic, "✒️"),
      InkStylePreset("gold_ink", "Gold Ink", Color(0xFFFFD700), Color(0xFFB8860B), Color(0x99FFD700), FontStyle.Italic, "👑"),
      InkStylePreset("neon_pink", "Neon Ink", Color(0xFFFF007F), Color(0xFFD500F9), Color(0xFFFF007F), FontStyle.Normal, "✨"),
      InkStylePreset("cyan_glow", "Aqua Ink", Color(0xFF00E5FF), Color(0xFF0080FF), Color(0x9900E5FF), FontStyle.Italic, "🌊"),
      InkStylePreset("crimson_ink", "Blood Ink", Color(0xFFFF1744), Color(0xFFB71C1C), Color(0x66FF1744), FontStyle.Italic, "🖋️"),
      InkStylePreset("silver_metallic", "Silver Ink", Color(0xFFE0E0E0), Color(0xFF757575), Color(0x66FFFFFF), FontStyle.Normal, "💎")
    )
  }
  
  var selectedPreset by remember { mutableStateOf(presets[0]) }
  var selectedColor by remember { mutableStateOf(selectedPreset.textColor) }
  var strokeWidth by remember { mutableStateOf(4f) }

  val colorPalette = listOf(
    Color(0xFF1A1A1A),
    Color(0xFFFFD700),
    Color(0xFFFF007F),
    Color(0xFF00E5FF),
    Color(0xFFFF1744),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFFFFFFF)
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF4A1525)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Create,
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(18.dp)
          )
        }
        Text(
          text = "Ink Text Studio",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        )
      }

      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White.copy(alpha = 0.7f)
        )
      }
    }

    // Text Input Field
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF161922))
        .border(1.dp, Color(0xFF2A2E3D), RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      BasicTextField(
        value = inkText,
        onValueChange = { inkText = it },
        textStyle = TextStyle(
          color = selectedColor,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          fontStyle = selectedPreset.fontStyle,
          fontFamily = FontFamily.Cursive
        ),
        cursorBrush = SolidColor(CyanAccent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
          if (inkText.isEmpty()) {
            Text(
              text = "Type ink text here...",
              style = TextStyle(
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 16.sp
              )
            )
          }
          innerTextField()
        }
      )
    }

    // Preset Style Chips
    Text(
      text = "Ink Styles",
      style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f))
    )
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(presets) { preset ->
        val isSelected = selectedPreset.id == preset.id
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isSelected) Color(0xFF4A1525) else Color(0xFF1F2430),
          border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFFF4081) else Color(0xFF2E3547)
          ),
          modifier = Modifier.clickable {
            selectedPreset = preset
            selectedColor = preset.textColor
          }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(text = preset.previewEmoji, fontSize = 14.sp)
            Text(
              text = preset.name,
              style = MaterialTheme.typography.labelMedium.copy(
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            )
          }
        }
      }
    }

    // Color Palette & Add Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f)
      ) {
        items(colorPalette) { color ->
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(color)
              .border(
                width = if (selectedColor == color) 2.dp else 1.dp,
                color = if (selectedColor == color) Color.White else Color.Transparent,
                shape = CircleShape
              )
              .clickable { selectedColor = color }
          )
        }
      }

      Button(
        onClick = {
          if (inkText.isNotBlank()) {
            val currentPos = viewModel.timelineEngine.currentPositionMs.value
            viewModel.timelineEngine.addTextClip(
              text = inkText,
              timelineStartMs = currentPos,
              durationMs = 3000L
            )
            onDismiss()
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFFFF4081),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Add Ink Text", fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
