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
  val settings by viewModel.settings.collectAsState()
  val isCreating by viewModel.isCreatingProject.collectAsState()
  val activeRecovery by viewModel.activeRecoverySession.collectAsState()
  var searchQuery by remember { mutableStateOf("") }
  var showNewProjectDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf<ProjectEntity?>(null) }
  var selectedTab by remember { mutableStateOf("All Projects") } // "All Projects" or "Drafts"
  var activeHomeTab by rememberSaveable { mutableStateOf(HomeTab.PROJECTS) }

  val instantNewProjectVideoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.createProjectWithMedia("Video Project", uris.map { it.toString() }, isVideo = true)
    }
  }

  val startNewProject = {
    if (settings.openEditorDirectlyOnNewProject) {
      viewModel.createNewProject(
        name = "New Project",
        aspectRatio = AspectRatio.RATIO_16_9,
        resolution = Resolution.RES_1080P,
        fps = FrameRate.FPS_30,
        initialMediaClips = emptyList()
      )
    } else {
      instantNewProjectVideoPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
      )
    }
  }

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

  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color(0xFFF6F9FE),
      topBar = {
      if (activeHomeTab == HomeTab.PROJECTS) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
        ) {
          StudioHeader(
            title = "AH Video Studio",
            subtitle = "Professional Mobile Editing Suite",
            showProBadge = true,
            onSearchClick = {},
            onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
          )
        }
      }
    },
    bottomBar = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp)
      ) {
        HomeBottomNavigationBar(
          activeTab = activeHomeTab,
          onTabSelected = { activeHomeTab = it }
        )
      }
    }
  ) { padding ->
    val contentPadding = PaddingValues(
      top = padding.calculateTopPadding() + 18.dp,
      bottom = padding.calculateBottomPadding(),
      start = padding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
      end = padding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    )
    when (activeHomeTab) {
      HomeTab.TEMPLATES -> {
        HomeTemplatesTabView(
          viewModel = viewModel,
          modifier = Modifier.padding(contentPadding)
        )
      }
      HomeTab.MY_ACCOUNT -> {
        HomeAccountTabView(
          viewModel = viewModel,
          modifier = Modifier.padding(contentPadding)
        )
      }
      HomeTab.PROJECTS -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
      // Crash Recovery Alert Banner
      if (activeRecovery != null) {
        val recovery = activeRecovery!!
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
            .clickable { startNewProject() }
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
                      text = "Clean empty timeline • Add media in editor • 4K HDR",
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = CyanAccent,
                        fontSize = 12.sp
                      )
                    )
                  }
                }

                IconButton(
                  onClick = { showNewProjectDialog = true },
                  modifier = Modifier.testTag("custom_project_setup_button")
                ) {
                  Icon(
                    Icons.Default.Tune,
                    contentDescription = "Custom Blank Canvas Setup",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
              ) {
                item {
                  QuickActionChip(icon = Icons.Default.Add, label = "New Project") {
                    startNewProject()
                  }
                }
                item {
                  QuickActionChip(icon = Icons.Default.Tune, label = "Blank Canvas") { showNewProjectDialog = true }
                }
                item {
                  QuickActionChip(icon = Icons.Default.VideoLibrary, label = "Instant Video") {
                    instantNewProjectVideoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                  }
                }
                item {
                  QuickActionChip(icon = Icons.Default.Collections, label = "Gallery") {
                    pickGalleryForNewProjectLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
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
                onClick = { startNewProject() }
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
    com.example.ui.components.RenameProjectDialog(
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
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
      )
    }
  }
}

@Composable
private fun ProjectItemCard(
  project: ProjectEntity,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }
  val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
  val dateStr = dateFormat.format(Date(project.lastEditedTime))

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
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(StudioSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Movie,
          contentDescription = null,
          tint = CyanAccent,
          modifier = Modifier.size(26.dp)
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = project.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp),
            maxLines = 1
          )
          if (project.isDraft) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = AmberAccent.copy(alpha = 0.2f)
            ) {
              Text(
                text = "Draft",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(color = AmberAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "${project.aspectRatio} • ${project.resolution} • $dateStr",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
        )
      }

      Box {
        IconButton(
          onClick = { showMenu = true },
          modifier = Modifier.testTag("project_menu_${project.id}")
        ) {
          Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = TextSecondary)
        }
        DropdownMenu(
          expanded = showMenu,
          onDismissRequest = { showMenu = false },
          containerColor = StudioSurfaceVariant
        ) {
          DropdownMenuItem(
            text = { Text("Rename", color = TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyanAccent) },
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


