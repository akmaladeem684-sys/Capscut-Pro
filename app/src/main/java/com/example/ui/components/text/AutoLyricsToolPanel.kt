package com.example.ui.components.text

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.domain.model.TextClip
import com.example.domain.model.WordTiming
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

data class LyricThemeOption(
  val id: String,
  val name: String,
  val previewEmoji: String,
  val subtitleStyle: String,
  val textColor: Long,
  val highlightColor: Long,
  val fontFamily: String
)

@Composable
fun AutoLyricsToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()

  var lyricsText by remember {
    mutableStateOf(
      "Every night in my dreams\nI see you, I feel you\nThat is how I know you go on\nFar across the distance"
    )
  }

  val themes = listOf(
    LyricThemeOption("karaoke_glow", "Karaoke Glow", "🎤", "Karaoke", 0xFFFFFFFF, 0xFFFF007F, "Montserrat"),
    LyricThemeOption("neon_yellow", "Neon Pop Line", "✨", "HighlightWord", 0xFFFFFFFF, 0xFFFFEA00, "Impact"),
    LyricThemeOption("urdu_poetry", "اردو غزل و نغمہ", "🇵🇰", "Animated", 0xFFFFD700, 0xFF10B981, "jameel_nastaliq"),
    LyricThemeOption("kinetic_bold", "Kinetic Typography", "⚡", "Bold", 0xFF00E5FF, 0xFF8B5CF6, "Bebas"),
    LyricThemeOption("sub_clean", "Classic Subtitle", "💬", "Classic", 0xFFFFFFFF, 0xFF00E5FF, "Sans-Serif")
  )

  var selectedTheme by remember { mutableStateOf(themes.first()) }
  var isProcessing by remember { mutableStateOf(false) }

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
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PurpleAccent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.MusicNote, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
        }
        Column {
          Text("Auto Lyrics Generator", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
          Text("Generate synchronized karaoke lyrics on beat", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
        }
      }

      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(32.dp)
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    // Lyrics Input Box
    OutlinedTextField(
      value = lyricsText,
      onValueChange = { lyricsText = it },
      label = { Text("Paste Song Lyrics (one line per verse)", fontSize = 11.sp, color = PurpleAccent) },
      placeholder = { Text("Paste lyrics here...", color = TextSecondary, fontSize = 12.sp) },
      modifier = Modifier
        .fillMaxWidth()
        .height(95.dp),
      maxLines = 4,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PurpleAccent,
        unfocusedBorderColor = StudioBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = StudioSurfaceVariant,
        unfocusedContainerColor = StudioSurfaceVariant
      )
    )

    // Lyrics Styling Themes
    Text("Select Lyric Animation Theme", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(themes) { theme ->
        val isSelected = selectedTheme.id == theme.id
        Card(
          modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { selectedTheme = theme },
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PurpleAccent.copy(alpha = 0.2f) else StudioSurfaceVariant
          ),
          border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) PurpleAccent else StudioBorder
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
              Text(theme.previewEmoji, fontSize = 16.sp)
              if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(14.dp))
              }
            }
            Text(
              text = theme.name,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
              maxLines = 1
            )
            Text(
              text = theme.subtitleStyle,
              style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp)
            )
          }
        }
      }
    }

    // Generate Button
    Button(
      onClick = {
        if (lyricsText.isBlank()) {
          Toast.makeText(context, "Please enter lyrics lines", Toast.LENGTH_SHORT).show()
          return@Button
        }
        isProcessing = true
        coroutineScope.launch {
          val lines = lyricsText.split("\n").filter { it.isNotBlank() }
          val startPos = viewModel.timelineEngine.currentPositionMs.value
          val lineDurationMs = 2500L

          lines.forEachIndexed { index, line ->
            val clipStart = startPos + (index * lineDurationMs)
            val words = line.trim().split(Regex("\\s+"))
            val wordTimings = words.mapIndexed { wIdx, word ->
              val perWordDuration = lineDurationMs / words.size.coerceAtLeast(1)
              WordTiming(
                word = word,
                startMs = clipStart + (wIdx * perWordDuration),
                durationMs = perWordDuration
              )
            }

            val lyricClip = TextClip(
              id = UUID.randomUUID().toString(),
              text = line.trim(),
              timelineStartMs = clipStart,
              durationMs = lineDurationMs,
              fontFamily = selectedTheme.fontFamily,
              fontSizeSp = 28f,
              fontWeight = 900,
              textColor = selectedTheme.textColor,
              highlightColor = selectedTheme.highlightColor,
              subtitleStyle = selectedTheme.subtitleStyle,
              animationType = "Pop",
              posY = 0.35f,
              hasShadow = true,
              shadowColor = 0xFF000000,
              words = wordTimings
            )
            viewModel.timelineEngine.addTextClipObject(lyricClip)
          }

          Toast.makeText(context, "Added ${lines.size} synchronized lyric lines!", Toast.LENGTH_SHORT).show()
          isProcessing = false
          onDismiss()
        }
      },
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White)
    ) {
      if (isProcessing) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
      } else {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Generate Synced Lyrics on Timeline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
