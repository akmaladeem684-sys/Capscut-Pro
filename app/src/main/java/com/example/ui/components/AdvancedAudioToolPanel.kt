package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.domain.model.AudioEffectsSettings
import com.example.domain.model.VoiceEffect
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@Composable
fun AdvancedAudioToolPanel(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier,
  onClose: () -> Unit = {}
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()

  val activeVideoClip = remember(timeline) {
    viewModel.getSelectedVideoClip()
  }

  val activeAudioClip = remember(timeline) {
    timeline.audioClips.firstOrNull { it.id in viewModel.timelineEngine.selectedClipIds.value }
      ?: timeline.audioClips.firstOrNull()
  }

  val currentFx = remember(activeVideoClip, activeAudioClip) {
    activeVideoClip?.audioEffects ?: activeAudioClip?.audioEffects ?: AudioEffectsSettings()
  }

  var noiseDb by remember(currentFx) { mutableStateOf(currentFx.noiseReductionDb) }
  var lowGain by remember(currentFx) { mutableStateOf(currentFx.lowGainDb) }
  var midGain by remember(currentFx) { mutableStateOf(currentFx.midGainDb) }
  var highGain by remember(currentFx) { mutableStateOf(currentFx.highGainDb) }
  var selectedVoiceFx by remember(currentFx) { mutableStateOf(currentFx.voiceEffect) }
  var pitchShift by remember(currentFx) { mutableStateOf(currentFx.pitchShiftSemitones) }
  var normalize by remember(currentFx) { mutableStateOf(currentFx.normalizeVolume) }

  fun pushEffectsUpdate() {
    val updated = AudioEffectsSettings(
      noiseReductionDb = noiseDb,
      lowGainDb = lowGain,
      midGainDb = midGain,
      highGainDb = highGain,
      voiceEffect = selectedVoiceFx,
      pitchShiftSemitones = pitchShift,
      normalizeVolume = normalize
    )
    viewModel.timelineEngine.setClipAudioEffects(audioEffects = updated)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = StudioPrimary)
        Text(
          text = "Audio Processing & EQ Suite",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Noise Reduction (Spectral Gate)
      Text("Real-Time Noise Reduction: ${noiseDb.toInt()} dB", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
      Slider(
        value = noiseDb,
        onValueChange = { db ->
          noiseDb = db
          pushEffectsUpdate()
        },
        valueRange = 0f..24f
      )

      Divider(color = StudioBorder)

      // 3-Band Equalizer
      Text("3-Band Graphic Equalizer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Bass (Low): ${lowGain.toInt()}dB", fontSize = 11.sp, color = TextSecondary)
          Slider(
            value = lowGain,
            onValueChange = { g -> lowGain = g; pushEffectsUpdate() },
            valueRange = -12f..12f
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Mid: ${midGain.toInt()}dB", fontSize = 11.sp, color = TextSecondary)
          Slider(
            value = midGain,
            onValueChange = { g -> midGain = g; pushEffectsUpdate() },
            valueRange = -12f..12f
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Treble (High): ${highGain.toInt()}dB", fontSize = 11.sp, color = TextSecondary)
          Slider(
            value = highGain,
            onValueChange = { g -> highGain = g; pushEffectsUpdate() },
            valueRange = -12f..12f
          )
        }
      }

      Divider(color = StudioBorder)

      // Voice Effects Selector
      Text("Voice Effects Engine", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        VoiceEffect.values().forEach { fx ->
          val isSel = selectedVoiceFx == fx
          FilterChip(
            selected = isSel,
            onClick = {
              selectedVoiceFx = fx
              pushEffectsUpdate()
            },
            label = { Text(fx.name.replace("_", " "), fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = StudioPrimary,
              selectedLabelColor = Color.White
            )
          )
        }
      }

      // Pitch Shift
      Text("Pitch Shift: ${pitchShift.toInt()} semitones", fontSize = 12.sp, color = TextSecondary)
      Slider(
        value = pitchShift,
        onValueChange = { p ->
          pitchShift = p
          pushEffectsUpdate()
        },
        valueRange = -12f..12f
      )

      Divider(color = StudioBorder)

      // Volume Normalization Switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("Volume Normalization", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
          Text("Peak normalize audio level to 95%", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
          checked = normalize,
          onCheckedChange = { norm ->
            normalize = norm
            pushEffectsUpdate()
          },
          colors = SwitchDefaults.colors(checkedThumbColor = StudioPrimary)
        )
      }
    }
  }
}
