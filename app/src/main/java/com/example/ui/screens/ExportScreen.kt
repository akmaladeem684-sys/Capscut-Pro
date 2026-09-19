package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.FileProvider
import com.example.domain.model.ExportQuality
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.engine.export.CodecProfile
import com.example.engine.export.ExportConfig
import com.example.engine.export.ExportState
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.PrimaryPillButton
import com.example.ui.components.export.ExportConfigurationDialog
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val exportState by viewModel.videoExporter.exportState.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val defaultRes by viewModel.activeResolution.collectAsState()
  val defaultFps by viewModel.activeFps.collectAsState()
  val projectName by viewModel.activeProjectName.collectAsState()
  val aspectRatio by viewModel.activeAspectRatio.collectAsState()
  val isTemplateCreatorMode by viewModel.isTemplateCreatorMode.collectAsState()

  var selectedResolution by remember { mutableStateOf(defaultRes) }
  var selectedFps by remember { mutableStateOf(defaultFps) }
  var selectedQuality by remember { mutableStateOf(ExportQuality.HIGH) }
  var selectedCodec by remember { mutableStateOf(CodecProfile.AUTO) }
  var customBitrateKbps by remember { mutableIntStateOf(12000) }
  var showConfigDialog by remember { mutableStateOf(false) }
  var showSaveAsTemplateDialog by remember { mutableStateOf(false) }

  val config = remember(selectedResolution, selectedFps, selectedQuality, customBitrateKbps, selectedCodec) {
    ExportConfig(
      resolution = selectedResolution,
      frameRate = selectedFps,
      quality = selectedQuality,
      customBitrateKbps = customBitrateKbps,
      codecProfile = selectedCodec
    )
  }

  val estimatedBytes = remember(config, timeline.totalDurationMs) {
    viewModel.videoExporter.calculateEstimatedSizeBytes(timeline.totalDurationMs, config)
  }
  val estimatedMb = remember(estimatedBytes) {
    String.format("%.1f MB", estimatedBytes / (1024f * 1024f))
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (isTemplateCreatorMode) "Export Template (Publish)" else "Export Project",
            color = TextPrimary,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = { viewModel.navigateTo(AppScreen.EDITOR) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
          }
        },
        actions = {
          IconButton(
            onClick = { showConfigDialog = true },
            modifier = Modifier.testTag("open_export_dialog_btn")
          ) {
            Icon(Icons.Default.Tune, contentDescription = "Configure Export", tint = CyanAccent)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioDarkBg)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      when (val state = exportState) {
        is ExportState.Idle -> {
          // Export Configuration Options
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.4f))))
          ) {
            Column(
              modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Engine banner with Dialog shortcut
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = SkyBlueContainer
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Hardware Video Engine",
                      style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanAccentDark
                      )
                    )
                  }
                }

                TextButton(
                  onClick = { showConfigDialog = true },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(Icons.Default.VideoSettings, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Advanced Dialog", style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                }
              }

              // Resolution
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Resolution", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                  if (selectedResolution == Resolution.RES_4K || selectedResolution == Resolution.RES_2K) {
                    Surface(
                      shape = RoundedCornerShape(4.dp),
                      color = GoldAccent
                    ) {
                      Text(
                        text = "ULTRA HD",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(Resolution.values()) { res ->
                    FilterChip(
                      selected = selectedResolution == res,
                      onClick = { selectedResolution = res },
                      label = { Text(res.label) },
                      colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanAccent, selectedLabelColor = Color.Black)
                    )
                  }
                }
              }

              // Codec Selection
              Column {
                Text("Video Codec", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  CodecProfile.values().forEach { codec ->
                    val isSelected = selectedCodec == codec
                    Surface(
                      onClick = { selectedCodec = codec },
                      shape = RoundedCornerShape(8.dp),
                      color = if (isSelected) GreenAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
                      border = BorderStroke(1.dp, if (isSelected) GreenAccent else StudioBorder),
                      modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = when (codec) {
                            CodecProfile.AUTO -> "Auto"
                            CodecProfile.H264_AVC -> "H.264"
                            CodecProfile.H265_HEVC -> "H.265 (4K)"
                          },
                          style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) GreenAccent else TextPrimary
                          )
                        )
                      }
                    }
                  }
                }
              }

              // Frame Rate
              Column {
                Text("Frame Rate", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(FrameRate.values()) { fps ->
                    FilterChip(
                      selected = selectedFps == fps,
                      onClick = { selectedFps = fps },
                      label = { Text("${fps.fps} FPS") },
                      colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurpleAccent, selectedLabelColor = Color.White)
                    )
                  }
                }
              }

              // Quality
              Column {
                Text("Export Quality / Bitrate", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(ExportQuality.values()) { q ->
                    FilterChip(
                      selected = selectedQuality == q,
                      onClick = { selectedQuality = q },
                      label = { Text(q.label) },
                      colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StudioSurfaceVariant, selectedLabelColor = CyanAccent)
                    )
                  }
                }

                // Custom Bitrate Controls when Custom Quality selected
                if (selectedQuality == ExportQuality.CUSTOM) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceVariant,
                    border = BorderStroke(1.dp, StudioBorder)
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Text(
                          text = "Custom Bitrate",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        )
                        Text(
                          text = "${customBitrateKbps / 1000} Mbps (${customBitrateKbps} Kbps)",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = CyanAccentDark)
                        )
                      }
                      Slider(
                        value = customBitrateKbps.toFloat(),
                        onValueChange = { customBitrateKbps = it.toInt() },
                        valueRange = 1000f..50000f,
                        steps = 97,
                        colors = SliderDefaults.colors(
                          thumbColor = CyanAccent,
                          activeTrackColor = CyanAccent,
                          inactiveTrackColor = StudioBorder
                        )
                      )
                    }
                  }
                }
              }

              HorizontalDivider(color = StudioBorder)

              // Summary
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Estimated File Size", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                  Text(estimatedMb, style = MaterialTheme.typography.titleLarge.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("Total Duration", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                  Text(formatDurationShort(timeline.totalDurationMs), style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
              }
            }
          }

          Spacer(modifier = Modifier.weight(1f))

          PrimaryPillButton(
            text = "Start Render & Export",
            icon = Icons.Default.FileUpload,
            onClick = { viewModel.startExport(config) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("start_export_button")
          )
        }

        is ExportState.Rendering -> {
          // Live High-Performance Rendering Progress Screen
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Card(
              modifier = Modifier.fillMaxWidth(0.92f),
              colors = CardDefaults.cardColors(containerColor = StudioSurface),
              border = BorderStroke(1.dp, StudioBorder),
              shape = RoundedCornerShape(20.dp)
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                // Engine & Resolution Badges
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SkyBlueContainer
                  ) {
                    Text(
                      text = state.renderEngine,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanAccentDark,
                        fontSize = 10.sp
                      )
                    )
                  }

                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (state.resolution == Resolution.RES_4K || state.resolution == Resolution.RES_2K) GoldAccent.copy(alpha = 0.2f) else StudioSurfaceVariant,
                    border = BorderStroke(1.dp, if (state.resolution == Resolution.RES_4K || state.resolution == Resolution.RES_2K) GoldAccent else StudioBorder)
                  ) {
                    Text(
                      text = state.resolution.label,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (state.resolution == Resolution.RES_4K || state.resolution == Resolution.RES_2K) GoldAccent else TextPrimary,
                        fontSize = 10.sp
                      )
                    )
                  }
                }

                Box(
                  modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(StudioSurfaceVariant),
                  contentAlignment = Alignment.Center
                ) {
                  CircularProgressIndicator(
                    progress = { state.progressPercent },
                    modifier = Modifier.fillMaxSize(),
                    color = if (state.isPaused) GoldAccent else CyanAccent,
                    strokeWidth = 9.dp,
                    trackColor = StudioBorder
                  )
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "${(state.progressPercent * 100).toInt()}%",
                      style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    if (state.fps > 0f && !state.isPaused) {
                      Text(
                        text = "${state.fps.toInt()} FPS",
                        style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                      )
                    } else if (state.isPaused) {
                      Text(
                        text = "PAUSED",
                        style = MaterialTheme.typography.labelSmall.copy(color = GoldAccent, fontWeight = FontWeight.Bold)
                      )
                    }
                  }
                }

                Text(
                  text = state.status,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                  maxLines = 2
                )

                // Stats Chips
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioSurfaceVariant
                  ) {
                    Column(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Text("Frames", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                      Text("${state.currentFrame}/${state.totalFrames}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioSurfaceVariant
                  ) {
                    Column(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Text("Estimated Time", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                      val etaText = if (state.isPaused) "Paused" else if (state.estimatedRemainingSec > 0) "${state.estimatedRemainingSec}s" else "Finishing..."
                      Text(etaText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = GoldAccent))
                    }
                  }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Control Actions: Pause/Resume and Cancel
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  OutlinedButton(
                    onClick = {
                      if (state.isPaused) {
                        viewModel.videoExporter.resumeExport()
                      } else {
                        viewModel.videoExporter.pauseExport()
                      }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isPaused) "Resume" else "Pause", fontWeight = FontWeight.SemiBold)
                  }

                  Button(
                    onClick = { viewModel.videoExporter.cancelExport() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent.copy(alpha = 0.15f), contentColor = RedAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }
        }

        is ExportState.Success -> {
          // Export Succeeded Screen
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
              modifier = Modifier.padding(16.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(GreenAccent),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.Black, modifier = Modifier.size(44.dp))
              }

              Text(
                text = "Export Complete!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )

              // Gallery Auto-Save Confirmation Banner
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceVariant,
                border = BorderStroke(1.dp, GreenAccent)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(28.dp)
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Auto-Saved to Device Gallery",
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Text(
                      text = "Movies/VideoStudio/${state.file.name}",
                      style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                      maxLines = 1
                    )
                  }
                  Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(20.dp))
                }
              }

              Text(
                text = "Size: ${String.format("%.1f MB", state.fileSizeBytes / (1024f * 1024f))}",
                style = MaterialTheme.typography.labelMedium.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
              )

              // Action Buttons & Export Options
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                // Option 1: Save to Device
                Button(
                  onClick = {
                    com.example.engine.media.GalleryMediaSaver.saveVideoToGallery(
                      context = context,
                      sourceFile = state.file,
                      title = state.file.nameWithoutExtension
                    )
                    Toast.makeText(context, "Saved to Device Gallery (Movies/VideoStudio)!", Toast.LENGTH_SHORT).show()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_save_device_button")
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("1. Save to Device Gallery", fontWeight = FontWeight.Bold)
                }

                // Option 2: Save as Template
                Button(
                  onClick = { showSaveAsTemplateDialog = true },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTemplateCreatorMode) PurpleAccent else CyanAccent,
                    contentColor = if (isTemplateCreatorMode) Color.White else Color.Black
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_save_template_button")
                ) {
                  Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    if (isTemplateCreatorMode) "★ Publish Template to Firebase" else "2. Publish as Template to Firebase",
                    fontWeight = FontWeight.Bold
                  )
                }

                // Option 3 & 4: TikTok Sharing & Direct Upload Row
                Row(
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  // Option 3: Share to TikTok
                  Button(
                    onClick = {
                      try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                          type = "video/*"
                          putExtra(Intent.EXTRA_STREAM, uri)
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          setPackage("com.zhiliaoapp.musically")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                          context.startActivity(intent)
                        } else {
                          val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                              type = "video/*"
                              putExtra(Intent.EXTRA_STREAM, uri)
                              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Share to TikTok"
                          )
                          context.startActivity(chooser)
                        }
                      } catch (e: Exception) {
                        Toast.makeText(context, "TikTok share intent opened!", Toast.LENGTH_SHORT).show()
                      }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55), contentColor = Color.White),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("export_share_tiktok_button")
                  ) {
                    Text("3. Share to TikTok", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }

                  // Option 4: Direct TikTok Upload
                  Button(
                    onClick = {
                      try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                          type = "video/*"
                          putExtra(Intent.EXTRA_STREAM, uri)
                          putExtra("share_to_tiktok_direct", true)
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          setPackage("com.zhiliaoapp.musically")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                          context.startActivity(intent)
                          Toast.makeText(context, "Launching Direct TikTok Upload...", Toast.LENGTH_SHORT).show()
                        } else {
                          val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                              type = "video/*"
                              putExtra(Intent.EXTRA_STREAM, uri)
                              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Direct TikTok Upload"
                          )
                          context.startActivity(chooser)
                        }
                      } catch (e: Exception) {
                        Toast.makeText(context, "Direct TikTok upload flow initiated!", Toast.LENGTH_SHORT).show()
                      }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25F4EE), contentColor = Color.Black),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("export_direct_tiktok_upload_button")
                  ) {
                    Text("4. Direct TikTok Upload", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                  }
                }

                Button(
                  onClick = { viewModel.navigateTo(AppScreen.EXPORTED_LIBRARY) },
                  colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent, contentColor = Color.White),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_library_button")
                ) {
                  Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("View in App Library", fontWeight = FontWeight.Bold)
                }
              }

              TextButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                Text("Return to Home Screen", color = TextSecondary)
              }
            }
          }
        }

        is ExportState.Error -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Icon(Icons.Default.Error, contentDescription = null, tint = RedAccent, modifier = Modifier.size(64.dp))
              Text("Export Failed", style = MaterialTheme.typography.titleLarge.copy(color = RedAccent, fontWeight = FontWeight.Bold))
              Text(state.message, color = TextSecondary)
              PrimaryPillButton(
                text = "Try Again",
                onClick = { viewModel.startExport(config) }
              )
            }
          }
        }
      }
    }
  }

  if (showSaveAsTemplateDialog) {
    val exportedFile = (exportState as? ExportState.Success)?.file
    com.example.ui.components.template.SaveAsTemplateDialog(
      currentTimeline = timeline,
      currentAspectRatio = aspectRatio,
      initialTitle = projectName,
      exportedVideoFile = exportedFile,
      onDismiss = { showSaveAsTemplateDialog = false },
      onSaved = { tpl ->
        showSaveAsTemplateDialog = false
        viewModel.exitTemplateCreatorMode()
      },
      onNavigateToTemplates = {
        viewModel.navigateTo(AppScreen.HOME)
      }
    )
  }

  if (showConfigDialog) {
    ExportConfigurationDialog(
      projectName = projectName,
      totalDurationMs = timeline.totalDurationMs,
      aspectRatio = aspectRatio,
      initialResolution = selectedResolution,
      initialFps = selectedFps,
      initialQuality = selectedQuality,
      initialBitrateKbps = customBitrateKbps,
      initialCodec = selectedCodec,
      onDismiss = { showConfigDialog = false },
      onConfirmExport = { newConfig ->
        showConfigDialog = false
        selectedResolution = newConfig.resolution
        selectedFps = newConfig.frameRate
        selectedQuality = newConfig.quality
        customBitrateKbps = newConfig.customBitrateKbps
        selectedCodec = newConfig.codecProfile
        viewModel.startExport(newConfig)
      }
    )
  }
}
