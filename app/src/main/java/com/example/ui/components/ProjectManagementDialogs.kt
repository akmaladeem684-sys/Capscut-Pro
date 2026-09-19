package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.MissingMediaItem
import com.example.domain.model.Resolution
import com.example.ui.theme.*

/**
 * Dialog enabling users to inspect missing media files and relink them from local storage.
 */
@Composable
fun RelinkMediaDialog(
  missingItems: List<MissingMediaItem>,
  onDismiss: () -> Unit,
  onRelink: (clipId: String, newUri: String) -> Unit
) {
  var selectedClipIdForRelink by remember { mutableStateOf<String?>(null) }

  val mediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    val clipId = selectedClipIdForRelink
    if (uri != null && clipId != null) {
      onRelink(clipId, uri.toString())
      selectedClipIdForRelink = null
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LinkOff, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Relink Missing Media",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "The following files could not be found at their original location. Relink each clip to restore playback.",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (missingItems.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("All media references are resolved!", color = CyanAccent, fontWeight = FontWeight.Bold)
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(missingItems, key = { it.clipId }) { item ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceVariant
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        if (item.mediaType == "AUDIO") Icons.Default.Audiotrack else Icons.Default.Movie,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = item.clipName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                        maxLines = 1
                      )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Track: ${item.trackType} • ${item.originalFilename}",
                      style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 11.sp),
                      maxLines = 1
                    )
                  }

                  Spacer(modifier = Modifier.width(8.dp))

                  Button(
                    onClick = {
                      selectedClipIdForRelink = item.clipId
                      mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                      )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("relink_item_${item.clipId}")
                  ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Locate", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("relink_dialog_done")
      ) {
        Text("Done", fontWeight = FontWeight.Bold)
      }
    },
    containerColor = StudioDarkBg
  )
}

/**
 * Dialog for comprehensive project configuration and settings inspection.
 */
@Composable
fun ProjectSettingsDialog(
  projectName: String,
  currentAspectRatio: AspectRatio,
  currentResolution: Resolution,
  currentFps: FrameRate,
  currentSampleRate: Int,
  currentCanvasColor: Long,
  totalDurationMs: Long,
  onDismiss: () -> Unit,
  onSaveSettings: (aspect: AspectRatio, res: Resolution, fps: FrameRate, sampleRate: Int, canvasColor: Long) -> Unit
) {
  var selectedAspect by remember { mutableStateOf(currentAspectRatio) }
  var selectedResolution by remember { mutableStateOf(currentResolution) }
  var selectedFps by remember { mutableStateOf(currentFps) }
  var selectedSampleRate by remember { mutableStateOf(currentSampleRate) }
  var selectedCanvasColor by remember { mutableStateOf(currentCanvasColor) }

  val canvasColors = listOf(
    0xFF000000 to "Black",
    0xFF1A1A1A to "Dark Gray",
    0xFFFFFFFF to "White",
    0xFF00FF00 to "Green Screen",
    0xFF0000FF to "Blue Screen"
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Project Settings",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Project Overview
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = StudioSurfaceVariant
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Duration: ${formatDuration(totalDurationMs)}", color = TextSecondary, fontSize = 12.sp)
            Text(projectName, color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
          }
        }

        // 1. Aspect Ratio
        Column {
          Text("Canvas Aspect Ratio", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            AspectRatio.values().forEach { aspect ->
              val isSelected = selectedAspect == aspect
              Surface(
                onClick = { selectedAspect = aspect },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) CyanAccent else StudioSurfaceVariant,
                modifier = Modifier
                  .weight(1f)
                  .height(36.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = aspect.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.Bold,
                      color = if (isSelected) Color.Black else TextPrimary
                    )
                  )
                }
              }
            }
          }
        }

        // 2. Resolution & FPS
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Resolution", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
            Spacer(modifier = Modifier.height(6.dp))
            Resolution.values().forEach { res ->
              val isSelected = selectedResolution == res
              Surface(
                onClick = { selectedResolution = res },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) CyanAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyanAccent) else null,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp)
                  .height(30.dp)
              ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                  Text(res.label, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
                }
              }
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Text("Timeline FPS", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
            Spacer(modifier = Modifier.height(6.dp))
            FrameRate.values().forEach { fps ->
              val isSelected = selectedFps == fps
              Surface(
                onClick = { selectedFps = fps },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) PurpleAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PurpleAccent) else null,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp)
                  .height(30.dp)
              ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                  Text("${fps.fps} fps", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
                }
              }
            }
          }
        }

        // 3. Audio Sample Rate
        Column {
          Text("Audio Sample Rate", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(44100 to "44.1 kHz (CD Audio)", 48000 to "48.0 kHz (Pro Video)").forEach { (hz, label) ->
              val isSelected = selectedSampleRate == hz
              Surface(
                onClick = { selectedSampleRate = hz },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) CyanAccent.copy(alpha = 0.25f) else StudioSurfaceVariant,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyanAccent) else null,
                modifier = Modifier
                  .weight(1f)
                  .height(34.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontSize = 11.sp))
                }
              }
            }
          }
        }

        // 4. Canvas Background Color
        Column {
          Text("Canvas Background Color", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            canvasColors.forEach { (colorVal, name) ->
              val isSelected = selectedCanvasColor == colorVal
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(colorVal))
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) CyanAccent else StudioBorder,
                    shape = CircleShape
                  )
                  .clickable { selectedCanvasColor = colorVal },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(Icons.Default.Check, contentDescription = name, tint = if (colorVal == 0xFFFFFFFF) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSaveSettings(selectedAspect, selectedResolution, selectedFps, selectedSampleRate, selectedCanvasColor)
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("save_project_settings_button")
      ) {
        Text("Apply Changes", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextTertiary)
      }
    },
    containerColor = StudioDarkBg
  )
}
