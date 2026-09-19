package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.StudioPreferencesManager
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsState()
  var cacheSizeText by remember { mutableStateOf("48.5 MB") }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      TopAppBar(
        title = { Text("Studio Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioDarkBg)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Pro Subscription Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = StudioSurface),
          border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyanAccent, PurpleAccent)))
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CyanAccent, PurpleAccent))),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("AH Studio Pro", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                  Text(if (settings.isProSubscriber) "Active Unlimited License" else "Free Tier", color = CyanAccent, fontSize = 12.sp)
                }
              }

              Button(
                onClick = {
                  StudioPreferencesManager.toggleProSubscription()
                  Toast.makeText(context, if (!settings.isProSubscriber) "Pro Subscription Activated!" else "Switched to Free Tier", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp)
              ) {
                Text(if (settings.isProSubscriber) "Pro Active" else "Upgrade", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }

            Text(
              text = "• 4K HDR 60fps export rendering\n• Unlimited AI auto-captions & translation\n• Complete access to all visual filters and sound packs\n• Zero watermarks and priority render pipeline",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
            )
          }
        }
      }

      // Section: Editor Settings
      item {
        SettingsSection(title = "Editor Configuration") {
          SettingsSwitchRow(
            title = "Timeline Snapping",
            subtitle = "Magnetically snap playhead to cut points and clip borders",
            checked = settings.timelineSnapping,
            onCheckedChange = { StudioPreferencesManager.updateSnapping(it) }
          )
          Divider(color = StudioBorder)
          SettingsSwitchRow(
            title = "Proxy Preview Rendering",
            subtitle = "Uses lightweight 720p proxy frames during editing for extreme smoothness",
            checked = settings.previewQualityProxy,
            onCheckedChange = { StudioPreferencesManager.updateProxyMode(it) }
          )
          Divider(color = StudioBorder)
          SettingsInfoRow(
            title = "Auto-Save Interval",
            value = "${settings.autoSaveIntervalSec} seconds"
          )
        }
      }

      // Section: Storage & Cache
      item {
        SettingsSection(title = "Storage & Performance") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Temporary Render Cache", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium))
              Text("Current size: $cacheSizeText", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
            Button(
              onClick = {
                val cleared = StudioPreferencesManager.clearAppCache()
                cacheSizeText = "0.0 MB"
                Toast.makeText(context, "Cleared 48.5 MB of temporary cache!", Toast.LENGTH_SHORT).show()
              },
              colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant, contentColor = TextPrimary),
              shape = RoundedCornerShape(14.dp)
            ) {
              Text("Clear Cache", fontSize = 12.sp)
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(40.dp)) }
    }
  }
}

@Composable
private fun SettingsSection(
  title: String,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
    )
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = StudioSurface),
      border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.4f))))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
      )
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Medium))
      Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = StudioBorder)
    )
  }
}

@Composable
private fun SettingsInfoRow(
  title: String,
  value: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
    Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
  }
}

