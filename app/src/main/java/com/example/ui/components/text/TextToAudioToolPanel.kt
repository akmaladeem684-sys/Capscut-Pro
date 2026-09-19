package com.example.ui.components.text

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

data class TTSVoiceOption(
  val id: String,
  val name: String,
  val language: String,
  val locale: Locale,
  val iconEmoji: String,
  val pitch: Float = 1.0f,
  val speed: Float = 1.0f
)

@Composable
fun TextToAudioToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  // Get active text if any is selected
  val selectedTextClip = remember(timeline.textClips, selectedElement) {
    if (selectedElement is SelectedTrackElement.Text) {
      timeline.textClips.find { it.id == (selectedElement as SelectedTrackElement.Text).clipId }
    } else null
  }

  var inputText by remember {
    mutableStateOf(selectedTextClip?.text ?: "Welcome to this video! Today we explore amazing things.")
  }

  val voices = listOf(
    TTSVoiceOption("en_us_female", "Natural Female", "English", Locale.US, "👩", pitch = 1.1f, speed = 1.0f),
    TTSVoiceOption("en_us_male", "Deep Male Voice", "English", Locale.US, "👨", pitch = 0.85f, speed = 0.95f),
    TTSVoiceOption("en_uk_narrator", "UK Documentary", "English", Locale.UK, "🎙️", pitch = 0.95f, speed = 0.9f),
    TTSVoiceOption("ur_pk_urdu", "اردو آواز (Urdu Voice)", "Urdu", Locale("ur", "PK"), "🇵🇰", pitch = 1.0f, speed = 0.95f),
    TTSVoiceOption("en_cheerful", "Cheerful Storyteller", "English", Locale.US, "✨", pitch = 1.25f, speed = 1.05f),
    TTSVoiceOption("en_cinematic", "Cinematic Trailer", "English", Locale.US, "🎬", pitch = 0.7f, speed = 0.85f)
  )

  var selectedVoice by remember { mutableStateOf(voices.first()) }
  var pitchVal by remember { mutableStateOf(1.0f) }
  var speedVal by remember { mutableStateOf(1.0f) }
  var isGenerating by remember { mutableStateOf(false) }

  // Android TextToSpeech engine
  var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

  DisposableEffect(Unit) {
    var tts: TextToSpeech? = null
    tts = TextToSpeech(context) { status ->
      if (status == TextToSpeech.SUCCESS) {
        ttsEngine = tts
      }
    }
    onDispose {
      tts?.stop()
      tts?.shutdown()
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Top Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyanAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
        }
        Column {
          Text("Text to Audio (AI Voiceover)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
          Text("Convert text to synchronized voice narration", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
        }
      }

      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(32.dp)
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Input Text Area
    OutlinedTextField(
      value = inputText,
      onValueChange = { inputText = it },
      label = { Text("Narration Script (Urdu / English)", fontSize = 11.sp, color = CyanAccent) },
      placeholder = { Text("Enter text to synthesize speech...", color = TextSecondary, fontSize = 12.sp) },
      modifier = Modifier.fillMaxWidth(),
      maxLines = 3,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = StudioSurfaceVariant,
        unfocusedContainerColor = StudioSurfaceVariant
      )
    )

    // Voice Selection Carousel
    Text("Select Voice Character", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(voices) { voice ->
        val isSelected = selectedVoice.id == voice.id
        Card(
          modifier = Modifier
            .width(135.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
              selectedVoice = voice
              pitchVal = voice.pitch
              speedVal = voice.speed
            },
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyanAccent.copy(alpha = 0.2f) else StudioSurfaceVariant
          ),
          border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) CyanAccent else StudioBorder
          )
        ) {
          Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(voice.iconEmoji, fontSize = 16.sp)
              if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
              }
            }
            Text(
              text = voice.name,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
              maxLines = 1
            )
            Text(
              text = voice.language,
              style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp)
            )
          }
        }
      }
    }

    // Sliders: Pitch & Speed
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Voice Pitch: ${(pitchVal * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
        Slider(
          value = pitchVal,
          onValueChange = { pitchVal = it },
          valueRange = 0.5f..2.0f,
          colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("Speech Speed: ${(speedVal * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
        Slider(
          value = speedVal,
          onValueChange = { speedVal = it },
          valueRange = 0.5f..2.0f,
          colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
        )
      }
    }

    // Action Buttons: Preview & Add to Timeline
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Preview Voice
      OutlinedButton(
        onClick = {
          ttsEngine?.let { engine ->
            engine.language = selectedVoice.locale
            engine.setPitch(pitchVal)
            engine.setSpeechRate(speedVal)
            engine.speak(inputText, TextToSpeech.QUEUE_FLUSH, null, "tts_preview_${System.currentTimeMillis()}")
          } ?: Toast.makeText(context, "TTS initializing...", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Preview Voice", fontSize = 12.sp)
      }

      // Generate & Add to Timeline
      Button(
        onClick = {
          if (inputText.isBlank()) {
            Toast.makeText(context, "Please enter narration text", Toast.LENGTH_SHORT).show()
            return@Button
          }
          isGenerating = true
          coroutineScope.launch {
            try {
              // Estimate duration: ~150 words per minute
              val wordCount = inputText.trim().split(Regex("\\s+")).size.coerceAtLeast(1)
              val estimatedDurationMs = ((wordCount / (2.5f * speedVal)) * 1000L).toLong().coerceAtLeast(2000L)

              val outputFile = File(context.filesDir, "voiceover_${System.currentTimeMillis()}.wav")
              ttsEngine?.let { engine ->
                engine.language = selectedVoice.locale
                engine.setPitch(pitchVal)
                engine.setSpeechRate(speedVal)
                engine.synthesizeToFile(inputText, null, outputFile, "tts_timeline_${System.currentTimeMillis()}")
              }

              viewModel.timelineEngine.addAudioClip(
                title = "Voiceover: ${selectedVoice.name}",
                durationMs = estimatedDurationMs,
                uri = outputFile.absolutePath
              )
              Toast.makeText(context, "Voiceover added to timeline!", Toast.LENGTH_SHORT).show()
              onDismiss()
            } catch (e: Exception) {
              Toast.makeText(context, "Error generating audio: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
              isGenerating = false
            }
          }
        },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
      ) {
        if (isGenerating) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add to Timeline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
