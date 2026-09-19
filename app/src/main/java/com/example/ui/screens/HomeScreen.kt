package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.local.ProjectEntity
import com.example.domain.model.*
import com.example.ui.AppScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.*
import com.example.ui.components.home.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val projects by viewModel.allProjects.collectAsState()
  val activeRecovery by viewModel.activeRecoverySession.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var showNewProjectDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf<ProjectEntity?>(null) }
  var selectedTab by remember { mutableStateOf("All Projects") } // "All Projects" or "Drafts"
  var activeHomeTab by rememberSaveable { mutableStateOf(HomeTab.PROJECTS) }

  val pickVideosForNewProjectLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.createProjectWithMedia("Video Project", uris.map { it.toString() }, isVideo = true)
    }
  }

  val pickPhotosForNewProjectLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.createProjectWithMedia("Photo Story", uris.map { it.toString() }, isVideo = false)
    }
  }

  val pickGalleryForNewProjectLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.createProjectWithMedia("Gallery Project", uris.map { it.toString() }, isVideo = true)
    }
  }

  val filteredProjects = remember(projects, searchQuery, selectedTab) {
    projects.filter { project ->
      val matchesSearch = searchQuery.isBlank() || project.name.contains(searchQuery, ignoreCase = true)
      val matchesTab = if (selectedTab == "Drafts") project.isDraft else true
      matchesSearch && matchesTab
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg),
    containerColor = StudioDarkBg,
    topBar = {
      if (activeHomeTab == HomeTab.PROJECTS) {
        StudioHeader(
          title = "AH Video Studio",
          subtitle = "Professional Mobile Editing Suite",
          showProBadge = true,
          onSearchClick = {},
          onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
        )
      }
    },
    bottomBar = {
      HomeBottomNavigationBar(
        activeTab = activeHomeTab,
        onTabSelected = { activeHomeTab = it }
      )
    }
  ) { padding ->
    when (activeHomeTab) {
      HomeTab.TEMPLATES -> {
        HomeTemplatesTabView(
          viewModel = viewModel,
          modifier = Modifier.padding(padding)
        )
      }
      HomeTab.MY_ACCOUNT -> {
        HomeAccountTabView(
          viewModel = viewModel,
          modifier = Modifier.padding(padding)
        )
      }
      HomeTab.PROJECTS -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
      // Crash Recovery Alert Banner
      activeRecovery?.let { recovery ->
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .border(1.dp, AmberAccent.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
              .testTag("crash_recovery_banner"),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AmberAccent.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Restore, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Timeline Recovered After Crash",
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary,
                      fontSize = 15.sp
                    )
                  )
                  Text(
                    text = "Unsaved edits in \"${recovery.projectName}\" were safely preserved.",
                    style = MaterialTheme.typography.bodySmall.copy(
                      color = TextSecondary,
                      fontSize = 12.sp
                    )
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                TextButton(
                  onClick = { viewModel.discardCrashRecoverySession() },
                  modifier = Modifier.testTag("dismiss_recovery_button")
                ) {
                  Text("Discard", color = TextTertiary, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = { viewModel.restoreCrashRecoverySession() },
                  colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("restore_recovery_button")
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Restore Timeline", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
              }
            }
          }
        }
      }

      // Hero Card: Start Creating
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { showNewProjectDialog = true }
            .testTag("hero_new_project_card"),
          colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  listOf(
                    Color(0xFF0F2038),
                    Color(0xFF1E1438),
                    Color(0xFF1A1F2C)
                  )
                )
              )
              .border(1.dp, Brush.linearGradient(listOf(CyanAccent, PurpleAccent)), RoundedCornerShape(20.dp))
              .padding(20.dp)
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(CyanAccent),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Default.VideoCall,
                      contentDescription = null,
                      tint = Color.Black,
                      modifier = Modifier.size(28.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(14.dp))
                  Column {
                    Text(
                      text = "Create New Project",
                      style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                      )
                    )
                    Text(
                      text = "Multi-track timeline • 4K HDR • AI Tools",
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = CyanAccent,
                        fontSize = 12.sp
                      )
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
              ) {
                item {
                  QuickActionChip(icon = Icons.Default.Collections, label = "Gallery") {
                    pickGalleryForNewProjectLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                  }
                }
                item {
                  QuickActionChip(icon = Icons.Default.VideoLibrary, label = "Videos") {
                    pickVideosForNewProjectLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                  }
                }
                item {
                  QuickActionChip(icon = Icons.Default.PhotoLibrary, label = "Photos") {
                    pickPhotosForNewProjectLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  }
                }
                item {
                  QuickActionChip(icon = Icons.Default.CameraAlt, label = "Camera") { showNewProjectDialog = true }
                }
                item {
                  QuickActionChip(icon = Icons.Default.AutoFixHigh, label = "AI Edit") { viewModel.navigateTo(AppScreen.AI_SUITE) }
                }
              }
            }
          }
        }
      }

      // Feature Hub Row (Templates, AI Suite, Exported, Settings)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FeatureHubTile(
            title = "Templates",
            icon = Icons.Default.Style,
            accentColor = PurpleAccent,
            modifier = Modifier.weight(1f),
            onClick = { viewModel.navigateTo(AppScreen.TEMPLATES) }
          )
          FeatureHubTile(
            title = "AI Suite",
            icon = Icons.Default.AutoAwesome,
            accentColor = CyanAccent,
            modifier = Modifier.weight(1f),
            onClick = { viewModel.navigateTo(AppScreen.AI_SUITE) }
          )
          FeatureHubTile(
            title = "Exported",
            icon = Icons.Default.FolderZip,
            accentColor = GreenAccent,
            modifier = Modifier.weight(1f),
            onClick = { viewModel.navigateTo(AppScreen.EXPORTED_LIBRARY) }
          )
        }
      }

      // Search Bar
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search projects or drafts...", color = TextTertiary) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
              }
            }
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_field"),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StudioSurface,
            unfocusedContainerColor = StudioSurface,
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          )
        )
      }

      // Section Tabs: Recent Projects & Drafts
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = selectedTab == "All Projects",
              onClick = { selectedTab = "All Projects" },
              label = { Text("Recent Projects (${projects.size})") },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = StudioSurfaceVariant,
                selectedLabelColor = CyanAccent,
                containerColor = Color.Transparent,
                labelColor = TextSecondary
              )
            )
            FilterChip(
              selected = selectedTab == "Drafts",
              onClick = { selectedTab = "Drafts" },
              label = { Text("Drafts") },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = StudioSurfaceVariant,
                selectedLabelColor = CyanAccent,
                containerColor = Color.Transparent,
                labelColor = TextSecondary
              )
            )
          }
        }
      }

      // Projects List or Empty State
      if (filteredProjects.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = CyanAccent.copy(alpha = 0.8f),
                modifier = Modifier.size(56.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = if (searchQuery.isNotBlank()) "No matching projects" else "No projects yet",
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Tap 'Create New Project' above to start your first video edit.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 13.sp)
              )
              Spacer(modifier = Modifier.height(16.dp))
              PrimaryPillButton(
                text = "Start Editing",
                icon = Icons.Default.Add,
                onClick = { showNewProjectDialog = true }
              )
            }
          }
        }
      } else {
        items(filteredProjects, key = { it.id }) { project ->
          ProjectItemCard(
            project = project,
            onClick = { viewModel.loadProject(project) },
            onRename = { showRenameDialog = project },
            onDuplicate = { viewModel.duplicateProject(project.id) },
            onDelete = { viewModel.deleteProject(project.id) }
          )
        }
      }

      item { Spacer(modifier = Modifier.height(64.dp)) }
        }
      }
    }
  }

  // New Project Configuration Dialog
  if (showNewProjectDialog) {
    NewProjectDialog(
      onDismiss = { showNewProjectDialog = false },
      onCreate = { name, aspect, res, fps ->
        showNewProjectDialog = false
        viewModel.createNewProject(name, aspect, res, fps, emptyList())
      }
    )
  }

  // Rename Dialog
  showRenameDialog?.let { project ->
    RenameProjectDialog(
      currentName = project.name,
      onDismiss = { showRenameDialog = null },
      onConfirm = { newName ->
        viewModel.renameProject(project.id, newName)
        showRenameDialog = null
      }
    )
  }
}

@Composable
private fun QuickActionChip(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = StudioSurfaceVariant.copy(alpha = 0.8f),
    modifier = Modifier.height(36.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Medium))
    }
  }
}

@Composable
private fun FeatureHubTile(
  title: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    modifier = modifier
      .height(84.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = StudioSurface),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.4f))))
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
      )
    }
  }
}

@Composable
fun ProjectItemCard(
  project: ProjectEntity,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }
  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
  val formattedDate = remember(project.lastEditedTime) {
    dateFormat.format(Date(project.lastEditedTime))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("project_item_${project.id}"),
    colors = CardDefaults.cardColors(containerColor = StudioSurface),
    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StudioBorder, StudioBorder.copy(alpha = 0.3f))))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Thumbnail Box
      Box(
        modifier = Modifier
          .size(width = 84.dp, height = 64.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(
            Brush.linearGradient(
              listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A)
              )
            )
          )
          .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.Movie,
          contentDescription = null,
          tint = CyanAccent.copy(alpha = 0.7f),
          modifier = Modifier.size(32.dp)
        )
        // Aspect ratio tag
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
          Text(
            text = project.aspectRatio,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              color = CyanAccent,
              fontWeight = FontWeight.Bold
            )
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = project.name,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 16.sp
          ),
          maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = formatDurationShort(project.durationMs),
            style = MaterialTheme.typography.labelSmall.copy(
              color = CyanAccent,
              fontWeight = FontWeight.Bold
            )
          )
          Text(
            text = " • ${project.resolution} • ${project.fps}fps",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = formattedDate,
          style = MaterialTheme.typography.bodySmall.copy(
            color = TextTertiary,
            fontSize = 11.sp
          )
        )
        if (project.hasMissingMedia) {
          Spacer(modifier = Modifier.height(4.dp))
          Surface(
            color = AmberAccent.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(11.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = "Missing Media",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = AmberAccent,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              )
            }
          }
        }
      }

      // Action Menu
      Box {
        IconButton(
          onClick = { showMenu = true },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("project_menu_${project.id}")
        ) {
          Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
        }

        DropdownMenu(
          expanded = showMenu,
          onDismissRequest = { showMenu = false },
          modifier = Modifier.background(StudioSurfaceVariant)
        ) {
          DropdownMenuItem(
            text = { Text("Edit Project", color = TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyanAccent) },
            onClick = {
              showMenu = false
              onClick()
            }
          )
          DropdownMenuItem(
            text = { Text("Rename", color = TextPrimary) },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextSecondary) },
            onClick = {
              showMenu = false
              onRename()
            }
          )
          DropdownMenuItem(
            text = { Text("Duplicate", color = TextPrimary) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary) },
            onClick = {
              showMenu = false
              onDuplicate()
            }
          )
          DropdownMenuItem(
            text = { Text("Delete", color = RedAccent) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RedAccent) },
            onClick = {
              showMenu = false
              onDelete()
            }
          )
        }
      }
    }
  }
}

@Composable
fun NewProjectDialog(
  onDismiss: () -> Unit,
  onCreate: (name: String, aspect: AspectRatio, res: Resolution, fps: FrameRate) -> Unit
) {
  var projectName by remember { mutableStateOf("") }
  var selectedAspect by remember { mutableStateOf(AspectRatio.RATIO_9_16) }
  var selectedResolution by remember { mutableStateOf(Resolution.RES_1080P) }
  var selectedFps by remember { mutableStateOf(FrameRate.FPS_30) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "New Video Project",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        OutlinedTextField(
          value = projectName,
          onValueChange = { projectName = it },
          label = { Text("Project Name") },
          placeholder = { Text("e.g., Summer Roadtrip Reel") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          )
        )

        // Aspect Ratio Picker
        Column {
          Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AspectRatio.values()) { ratio ->
              FilterChip(
                selected = selectedAspect == ratio,
                onClick = { selectedAspect = ratio },
                label = { Text(ratio.label) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = CyanAccent,
                  selectedLabelColor = Color.Black,
                  containerColor = StudioSurface,
                  labelColor = TextPrimary
                )
              )
            }
          }
        }

        // Resolution Picker
        Column {
          Text("Resolution", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Resolution.values()) { res ->
              FilterChip(
                selected = selectedResolution == res,
                onClick = { selectedResolution = res },
                label = { Text(res.label) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = PurpleAccent,
                  selectedLabelColor = Color.White,
                  containerColor = StudioSurface,
                  labelColor = TextPrimary
                )
              )
            }
          }
        }

        // Frame Rate Picker
        Column {
          Text("Frame Rate", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FrameRate.values()) { fps ->
              FilterChip(
                selected = selectedFps == fps,
                onClick = { selectedFps = fps },
                label = { Text("${fps.fps} FPS") },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = StudioBorder,
                  selectedLabelColor = CyanAccent,
                  containerColor = StudioSurface,
                  labelColor = TextPrimary
                )
              )
            }
          }
        }

        // Clean Blank Timeline Guarantee Note
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = StudioSurface,
          border = BorderStroke(1.dp, StudioBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = CyanAccent,
              modifier = Modifier.size(18.dp)
            )
            Column {
              Text(
                text = "Clean Blank Timeline",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = TextPrimary,
                  fontWeight = FontWeight.Bold
                )
              )
              Text(
                text = "Ready for your own video clips, audio tracks, and edits.",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = TextSecondary,
                  fontSize = 11.5.sp
                )
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onCreate(projectName, selectedAspect, selectedResolution, selectedFps)
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
        shape = RoundedCornerShape(20.dp)
      ) {
        Text("Create Project", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
    containerColor = StudioSurfaceVariant,
    shape = RoundedCornerShape(20.dp)
  )
}

@Composable
fun RenameProjectDialog(
  currentName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var text by remember { mutableStateOf(currentName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename Project", color = TextPrimary, fontWeight = FontWeight.Bold) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = CyanAccent,
          unfocusedBorderColor = StudioBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary
        )
      )
    },
    confirmButton = {
      Button(
        onClick = { if (text.isNotBlank()) onConfirm(text) },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
      ) {
        Text("Rename", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
    containerColor = StudioSurfaceVariant
  )
}
