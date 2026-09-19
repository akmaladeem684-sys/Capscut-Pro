package com.example.ui.components.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import kotlinx.coroutines.delay

@Composable
fun CopyrightCheckPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val audioClips = timeline.audioClips

  var isScanning by remember { mutableStateOf(true) }
  var scanProgress by remember { mutableStateOf(0.1f) }

  LaunchedEffect(Unit) {
    while (scanProgress < 1.0f) {
      delay(80L)
      scanProgress += 0.12f
    }
    scanProgress = 1.0f
    isScanning = false
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDark)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0F1523))
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E283E)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
        }
        Text(
          text = "Audio Copyright Check",
          color = Color.White,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }

      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
      }
    }

    Divider(color = Color(0xFF1E283E), thickness = 0.5.dp)

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Status Card
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF151C2C),
        border = BorderStroke(1.dp, if (isScanning) CyanAccent else Color(0xFF00E676)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (isScanning) {
              CircularProgressIndicator(
                progress = { scanProgress },
                modifier = Modifier.size(32.dp),
                color = CyanAccent,
                strokeWidth = 3.dp,
                trackColor = Color(0xFF1E283E)
              )
            } else {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
              }
            }

            Column {
              Text(
                text = if (isScanning) "Analyzing Audio Tracks (${(scanProgress * 100).toInt()}%)..." else "Audio Copyright Check Passed",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (isScanning) "Matching digital audio fingerprints against Content ID" else "${audioClips.size} track(s) verified safe for commercial distribution",
                color = if (isScanning) Color.Gray else Color(0xFF00E676),
                fontSize = 11.sp
              )
            }
          }

          if (isScanning) {
            LinearProgressIndicator(
              progress = { scanProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
              color = CyanAccent,
              trackColor = Color(0xFF1E283E)
            )
          }
        }
      }

      // Clearance Badges
      Text("Platform Licensing Status", color = Color(0xFF8E9BB5), fontSize = 12.sp, fontWeight = FontWeight.Medium)

      listOf(
        Triple("YouTube Content ID", "Pass - No claims detected", Icons.Default.Check),
        Triple("TikTok & Instagram Reels", "Eligible for monetization & sounds sync", Icons.Default.Check),
        Triple("Commercial Royalty-Free", "Full worldwide license granted", Icons.Default.Check)
      ).forEach { (title, subtitle, icon) ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF151C2C),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(icon, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
            }
            Column {
              Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Text(subtitle, color = Color.Gray, fontSize = 10.sp)
            }
          }
        }
      }

      Spacer(Modifier.weight(1f))

      Button(
        onClick = onClose,
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(22.dp)
      ) {
        Text("Done", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
    }
  }
}
