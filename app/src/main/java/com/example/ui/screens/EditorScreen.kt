package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.graphics.asImageBitmap
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.components.text.*
import com.example.ui.components.navigation.*
import coil.compose.AsyncImage
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.LayoutInflater
import com.example.R
import com.example.engine.composition.VideoEffectRenderer
import com.example.data.presets.StockMediaCatalog
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.SelectedTrackElement
import com.example.engine.export.ExportState
import com.example.engine.media.MediaRelinkManager
import com.example.engine.text.TextLayerRenderer
import com.example.ui.components.InteractiveTransformOverlay
import com.example.ui.AppScreen
import com.example.ui.EditorToolbarTab
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import com.example.ui.StudioViewModel
import com.example.ui.components.KeyframeAnimationPanel
import com.example.ui.components.animation.AnimationsToolPanel
import com.example.ui.components.TransitionsPanel
import com.example.ui.components.trim.VideoTrimmingToolPanel
import com.example.ui.components.formatDuration
import com.example.ui.components.formatDurationShort
import com.example.ui.components.timeline.*
import com.example.ui.components.diagnostics.DiagnosticOverlay
import com.example.ui.components.timeline.LayersDrawer
import com.example.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  viewModel: StudioViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val projectName by viewModel.activeProjectName.collectAsState()
  val aspectRatio by viewModel.activeAspectRatio.collectAsState()
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
  val isPlaying by viewModel.timelineEngine.isPlaying.collectAsState()
  val canUndo by viewModel.timelineEngine.canUndo.collectAsState()
  val canRedo by viewModel.timelineEngine.canRedo.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val activeTab by viewModel.activeToolbarTab.collectAsState()
  val isSnapping by viewModel.timelineEngine.isSnappingEnabled.collectAsState()
  val timelineZoom by viewModel.timelineEngine.timelineZoom.collectAsState()
  val selectedClipIds by viewModel.timelineEngine.selectedClipIds.collectAsState()
  val isMultiSelectMode by viewModel.timelineEngine.isMultiSelectMode.collectAsState()
  val snapIndicatorMs by viewModel.timelineEngine.snapIndicatorMs.collectAsState()
  val isMagnetic by viewModel.timelineEngine.isMagneticEnabled.collectAsState()
  val clipboardClips by viewModel.timelineEngine.clipboardClips.collectAsState()
  val selectedKeyframeIds by viewModel.timelineEngine.selectedKeyframeIds.collectAsState()
  val saveState by viewModel.saveState.collectAsState()
  val missingMediaList by viewModel.missingMediaList.collectAsState()
  val activeResolution by viewModel.activeResolution.collectAsState()
  val activeFps by viewModel.activeFps.collectAsState()
  val activeSampleRate by viewModel.activeSampleRate.collectAsState()
  val activeCanvasColor by viewModel.activeCanvasColor.collectAsState()
  val exportState by viewModel.videoExporter.exportState.collectAsState()
  val waveformStyle by viewModel.waveformStyle.collectAsState()
  val selectedTransitionCutIndex by viewModel.timelineEngine.selectedTransitionCutIndex.collectAsState()
  val timelineFps by viewModel.timelineEngine.timelineFps.collectAsState()
  val isFrameSnapping by viewModel.timelineEngine.isFrameSnapping.collectAsState()
  val isTracksSyncEnabled by viewModel.timelineEngine.isTracksSyncEnabled.collectAsState()

  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val coroutineScope = rememberCoroutineScope()
  var isLayersOpen by remember { mutableStateOf(false) }

  var showRenameDialog by remember { mutableStateOf(false) }
  var showSpeedDialog by remember { mutableStateOf(false) }
  var showProjectSettingsDialog by remember { mutableStateOf(false) }
  var showExportConfigDialog by remember { mutableStateOf(false) }
  var showRelinkMediaDialog by remember { mutableStateOf(false) }
  var isFullscreenPreview by remember { mutableStateOf(false) }
  var showDiagnosticOverlay by remember { mutableStateOf(false) }
  var showMoreToolsDialog by remember { mutableStateOf(false) }
  var pendingReplaceClipId by remember { mutableStateOf<String?>(null) }
  var draggedTransitionType by remember { mutableStateOf<TransitionType?>(null) }
  var activeTextSubTool by remember { mutableStateOf(TextSubTool.TEXT_TEMPLATES) }

  val replaceMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null && pendingReplaceClipId != null) {
      val clipId = pendingReplaceClipId!!
      coroutineScope.launch {
        val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
          context = context,
          sourceUriString = uri.toString(),
          suggestedName = "Replaced Media"
        )
        viewModel.timelineEngine.replaceMedia(
          clipId = clipId,
          newUri = persistentPath,
          newName = "Replaced Media"
        )
        pendingReplaceClipId = null
      }
    }
  }

  // Gallery / Media Picker Launcher for Timeline '+' Button (Videos & Images)
  val timelineMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        uris.forEach { uri ->
          val fileName = try {
            var result: String? = null
            if (uri.scheme == "content") {
              val cursor = context.contentResolver.query(uri, null, null, null, null)
              cursor?.use {
                if (it.moveToFirst()) {
                  val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                  if (index != -1) result = it.getString(index)
                }
              }
            }
            result ?: uri.lastPathSegment ?: "Imported Media"
          } catch (e: Exception) {
            uri.lastPathSegment ?: "Imported Media"
          }

          val persistentPath = com.example.engine.media.MediaPersistenceManager.persistMedia(
            context = context,
            sourceUriString = uri.toString(),
            suggestedName = fileName
          )

          val metadata = com.example.engine.media.MediaMetadataHelper.extractMetadata(
            context,
            persistentPath,
            defaultImageDurationMs = 3000L
          )

          viewModel.timelineEngine.addVideoClip(
            uri = persistentPath,
            name = fileName,
            isVideo = metadata.isVideo,
            durationMs = metadata.durationMs,
            atPlayhead = true,
            width = metadata.width,
            height = metadata.height,
            rotationDegrees = metadata.rotationDegrees,
            frameRate = metadata.frameRate,
            mimeType = metadata.mimeType,
            hasAudio = metadata.hasAudio
          )
        }
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .background(StudioDarkBg),
      containerColor = StudioDarkBg,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        EditorTopBar(
          activeResolution = activeResolution,
          exportState = exportState,
          canUndo = canUndo,
          canRedo = canRedo,
          isDiagnosticActive = showDiagnosticOverlay,
          onToggleDiagnostics = { showDiagnosticOverlay = !showDiagnosticOverlay },
          onUndoClick = { viewModel.timelineEngine.undo() },
          onRedoClick = { viewModel.timelineEngine.redo() },
          onBackClick = {
            viewModel.saveCurrentProject()
            viewModel.navigateTo(AppScreen.HOME)
          },
          onExportClick = {
            viewModel.saveCurrentProject()
            showExportConfigDialog = true
          }
        )
      }
  ) { padding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = padding.calculateTopPadding())
    ) {
      val configuration = LocalConfiguration.current
      val screenHeight = configuration.screenHeightDp.dp
      val screenWidth = configuration.screenWidthDp.dp
      val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
      
      // The old filmstrip thumbnail band has been removed from the editor layout.
      // Reclaim that vertical space for the actual video preview instead of leaving a gap.
      // The cap keeps the timeline and playback controls usable on compact screens.
      val basePreviewHeight = remember(screenHeight, screenWidth, isLandscape) {
        val reclaimedSpace = 80.dp
        val originalHeight = if (isLandscape) {
          when {
            screenHeight < 500.dp -> (screenHeight * 0.44f).coerceIn(150.dp, 210.dp)
            screenHeight < 700.dp -> (screenHeight * 0.48f).coerceIn(190.dp, 260.dp)
            else -> (screenHeight * 0.52f).coerceIn(240.dp, 350.dp)
          }
        } else {
          when {
            screenHeight < 650.dp -> (screenHeight * 0.35f).coerceIn(170.dp, 240.dp)
            screenHeight < 850.dp -> (screenHeight * 0.41f).coerceIn(240.dp, 330.dp)
            else -> (screenHeight * 0.45f).coerceIn(290.dp, 400.dp)
          }
        }
        (originalHeight + reclaimedSpace).coerceAtMost(screenHeight * 0.62f)
      }

      // When any bottom navigation tool/panel is opened, automatically reduce the video preview size by approx. 30%
      val targetPreviewHeight = if (activeTab != null) {
        basePreviewHeight * 0.70f
      } else {
        basePreviewHeight
      }

      // Smooth layout resizing animation when the navigation panel opens or closes
      val animatedPreviewHeight by animateDpAsState(
        targetValue = targetPreviewHeight,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "animated_preview_height"
      )

      val responsiveSpacerHeight = remember(screenHeight) {
        when {
          screenHeight < 650.dp -> 0.dp
          screenHeight < 850.dp -> 2.dp
          else -> 4.dp
        }
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
      ) {
      // Missing Media Warning Bar
      if (missingMediaList.isNotEmpty()) {
        Surface(
          color = AmberAccent.copy(alpha = 0.2f),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showRelinkMediaDialog = true }
            .testTag("missing_media_alert_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${missingMediaList.size} missing media clip${if (missingMediaList.size == 1) "" else "s"} detected",
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              )
            }
            Button(
              onClick = { showRelinkMediaDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              modifier = Modifier
                .height(26.dp)
                .testTag("relink_media_banner_button")
            ) {
              Text("Relink", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
          }
        }
      }

      // 1. VIDEO PREVIEW CONTAINER (Dynamically resizes with smooth animation when panel opens/closes)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(animatedPreviewHeight)
          .background(Color.Black)
          .testTag("video_preview_container"),
        contentAlignment = Alignment.Center
      ) {
        VideoPreviewSurface(
          timeline = timeline,
          currentPosMs = currentPosMs,
          aspectRatio = aspectRatio,
          selectedElement = selectedElement,
          onSelectElement = { viewModel.timelineEngine.selectElement(it) },
          onUpdateOverlay = { viewModel.timelineEngine.updateOverlayClip(it) },
          onUpdateText = { viewModel.timelineEngine.updateTextClip(it) },
          onUpdateSticker = { viewModel.timelineEngine.updateStickerClip(it) },
          onDeleteClip = { viewModel.timelineEngine.deleteClips(setOf(it)) },
          onDuplicateClip = { viewModel.timelineEngine.duplicateClips(setOf(it)) },
          onEditText = { clip ->
            viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
            activeTextSubTool = TextSubTool.TEXT_TEMPLATES
            viewModel.setActiveToolbarTab(EditorToolbarTab.TEXT)
          },
          player = viewModel.playbackEngine.player,
          onGetOverlayPlayer = { clipId -> viewModel.playbackEngine.getOverlayPlayer(clipId) },
          onToggleFullscreen = { isFullscreenPreview = true },
          onAddMedia = {
            timelineMediaPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          modifier = Modifier
            .fillMaxSize()
            .testTag("video_preview")
        )

        // Real-Time Hardware Diagnostic Overlay
        androidx.compose.animation.AnimatedVisibility(
          visible = showDiagnosticOverlay,
          enter = fadeIn() + slideInVertically { -it },
          exit = fadeOut() + slideOutVertically { -it },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
        ) {
          DiagnosticOverlay(
            timeline = timeline,
            isPlaying = isPlaying,
            onClose = { showDiagnosticOverlay = false }
          )
        }
      }

      // 2. PLAYBACK CONTROLS BAR (Moved below video preview, scaled-up play button)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.Black)
          .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Timecode Display (e.g. 00:04 / 00:12)
        Text(
          text = "${formatDurationShort(currentPosMs)} / ${formatDurationShort(timeline.totalDurationMs)}",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          ),
          modifier = Modifier.testTag("playback_timecode_display")
        )

        // Center: Step Back (⏮), Scaled-Up Play/Pause (⏯), Step Forward (⏭)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          IconButton(
            onClick = { viewModel.timelineEngine.stepBackwardOneFrame() },
            modifier = Modifier
              .size(36.dp)
              .testTag("playback_step_backward")
          ) {
            Icon(
              imageVector = Icons.Default.SkipPrevious,
              contentDescription = "Previous Frame",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }

          // Scaled Up Play/Pause Button with Cyan Ring Accent
          IconButton(
            onClick = { viewModel.timelineEngine.togglePlayPause() },
            modifier = Modifier
              .size(52.dp)
              .clip(CircleShape)
              .background(Color.Black)
              .border(2.5.dp, CyanAccent, CircleShape)
              .testTag("playback_play_pause")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              tint = Color.White,
              modifier = Modifier.size(30.dp)
            )
          }

          IconButton(
            onClick = { viewModel.timelineEngine.stepForwardOneFrame() },
            modifier = Modifier
              .size(36.dp)
              .testTag("playback_step_forward")
          ) {
            Icon(
              imageVector = Icons.Default.SkipNext,
              contentDescription = "Next Frame",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Above media track: Add Media button (White plus, Blue color)
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0080FF),
            modifier = Modifier
              .clickable {
                timelineMediaPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
              }
              .testTag("above_media_track_add_btn")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Media",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Add Media",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.5.sp
                )
              )
            }
          }
        }
      }

      // Responsive spacing between video preview and timeline
      Spacer(modifier = Modifier.height(responsiveSpacerHeight))

      // 1. DEFAULT EDITOR VIEW: Multi-track Timeline + Floating Bottom Navigation Bar
      AnimatedVisibility(
        visible = activeTab == null,
        enter = fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
          expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
          shrinkVertically(animationSpec = tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
          Column(modifier = Modifier.fillMaxSize()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
              var multiTrackZoom by remember { mutableFloatStateOf(1.0f) }

          if (draggedTransitionType != null) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
              shape = RoundedCornerShape(8.dp),
              color = PurpleAccent.copy(alpha = 0.95f),
              border = BorderStroke(1.dp, Color.White)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Transform, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Dragging \"${draggedTransitionType?.displayName}\" ➔ Tap any Cut diamond on timeline",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                  )
                }
                IconButton(
                  onClick = { draggedTransitionType = null },
                  modifier = Modifier.size(20.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Cancel Drag", tint = Color.White, modifier = Modifier.size(14.dp))
                }
              }
            }
          }

          // STUDIO MULTI-TRACK TIMELINE: Full multi-track video, audio, text, sticker, and effect tracks
          MultiTrackTimeline(
            timeline = timeline,
            currentPosMs = currentPosMs,
            isPlaying = isPlaying,
            onTogglePlayPause = { viewModel.timelineEngine.togglePlayPause() },
            zoom = multiTrackZoom,
            selectedElement = selectedElement,
            selectedClipIds = selectedClipIds,
            isMultiSelectMode = isMultiSelectMode,
            snapIndicatorMs = snapIndicatorMs,
            onSeek = {
              viewModel.onScrubProgress(it)
            },
            onScrubStart = { viewModel.onScrubStart() },
            onScrubStop = { viewModel.onScrubStop() },
            onSelectElement = { viewModel.timelineEngine.selectElement(it) },
            onToggleClipSelection = { viewModel.timelineEngine.toggleSelectClip(it) },
            onZoomChange = { multiTrackZoom = it },
            onReorderVideoClips = { from, to -> viewModel.reorderVideoClips(from, to) },
            onOpenTrimTool = { viewModel.setActiveToolbarTab(EditorToolbarTab.TRIM) },
            onOpenKeyframeTool = { viewModel.setActiveToolbarTab(EditorToolbarTab.KEYFRAME) },
            onOpenTransitionsTool = { viewModel.setActiveToolbarTab(EditorToolbarTab.TRANSITIONS) },
            selectedTransitionCutIndex = selectedTransitionCutIndex,
            onSelectTransitionCut = { cutIdx ->
              viewModel.timelineEngine.setSelectedTransitionCutIndex(cutIdx)
            },
            draggedTransitionType = draggedTransitionType,
            onDropTransition = { cutIdx, type ->
              viewModel.timelineEngine.setTransition(cutIdx, type)
              draggedTransitionType = null
            },
            onAddMedia = {
              try {
                timelineMediaPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
              } catch (e: Exception) {
                viewModel.setActiveToolbarTab(EditorToolbarTab.MEDIA)
              }
            },
            onAddAudio = {
              viewModel.setActiveToolbarTab(EditorToolbarTab.AUDIO)
            },
            onAddText = {
              activeTextSubTool = TextSubTool.TEXT_TEMPLATES
              viewModel.setActiveToolbarTab(EditorToolbarTab.TEXT)
            },
            onAddOverlay = {
              viewModel.setActiveToolbarTab(EditorToolbarTab.OVERLAY)
            },
            onAddSticker = {
              viewModel.setActiveToolbarTab(EditorToolbarTab.STICKERS)
            },
            onAddEffect = {
              viewModel.setActiveToolbarTab(EditorToolbarTab.EFFECTS)
            },
            isTracksSyncEnabled = isTracksSyncEnabled,
            onToggleTracksSync = { viewModel.timelineEngine.toggleTracksSync() },
            onMoveToPlayhead = { viewModel.timelineEngine.moveSelectedClipToPlayhead() },
            onSplitAllTracks = { viewModel.timelineEngine.splitAllTracksAtPlayhead() },
            onSplitClip = {
              viewModel.timelineEngine.splitAtPlayhead()
            },
            onTrimLeftToPlayhead = {
              viewModel.timelineEngine.trimClipLeftToPlayhead()
            },
            onTrimRightToPlayhead = {
              viewModel.timelineEngine.trimClipRightToPlayhead()
            },
            onDeleteClip = { viewModel.timelineEngine.deleteSelected() },
            onRippleDelete = { viewModel.timelineEngine.rippleDelete() },
            onNormalDelete = { viewModel.timelineEngine.normalDelete() },
            onDuplicateClip = { viewModel.timelineEngine.duplicateClips() },
            onCopyClip = { viewModel.timelineEngine.copySelectedClips() },
            onPasteClip = { viewModel.timelineEngine.pasteClipsAtPlayhead() },
            onToggleMultiSelect = { viewModel.timelineEngine.toggleMultiSelectMode() },
            onNextPeak = { viewModel.jumpToNextAudioPeak() },
            onPrevPeak = { viewModel.jumpToPrevAudioPeak() },
            onNextSilence = { viewModel.jumpToNextAudioSilence() },
            onPrevSilence = { viewModel.jumpToPrevAudioSilence() },
            onRemoveSilence = { viewModel.removeSilenceInSelectedAudioClip() },
            waveformStyle = waveformStyle,
            onToggleWaveformStyle = { viewModel.cycleWaveformStyle() },
            fps = timelineFps,
            isFrameSnapping = isFrameSnapping,
            onStepFrames = { delta ->
              viewModel.timelineEngine.stepFrames(delta)
              viewModel.playbackEngine.seekTo(viewModel.timelineEngine.currentPositionMs.value)
            },
            onSeekToPrevCut = {
              viewModel.timelineEngine.seekToPreviousCut()
              viewModel.playbackEngine.seekTo(viewModel.timelineEngine.currentPositionMs.value)
            },
            onSeekToNextCut = {
              viewModel.timelineEngine.seekToNextCut()
              viewModel.playbackEngine.seekTo(viewModel.timelineEngine.currentPositionMs.value)
            },
            onFpsChange = { viewModel.timelineEngine.setTimelineFps(it) },
            onToggleFrameSnapping = { viewModel.timelineEngine.toggleFrameSnapping() },
            onMoveClip = { clipId, delta -> viewModel.moveClipByDelta(clipId, delta) },
            onMoveClipStart = { clipId -> viewModel.beginMoveClip(clipId) },
            onMoveClipEnd = { _ -> viewModel.endMoveClip() },
            onTrimClipLeft = { clipId, delta -> viewModel.trimClipLeftByDelta(clipId, delta) },
            onTrimClipLeftStart = { clipId -> viewModel.beginTrimClipLeft(clipId) },
            onTrimClipLeftEnd = { _ -> viewModel.endTrimClipLeft() },
            onTrimClipRight = { clipId, delta -> viewModel.trimClipRightByDelta(clipId, delta) },
            onTrimClipRightStart = { clipId -> viewModel.beginTrimClipRight(clipId) },
            onTrimClipRightEnd = { _ -> viewModel.endTrimClipRight() },
            onToggleTrackLock = { viewModel.timelineEngine.toggleTrackLock(it) },
            onToggleTrackHide = { viewModel.timelineEngine.toggleTrackHide(it) },
            onToggleTrackMute = { viewModel.timelineEngine.toggleTrackMute(it) },
            onToggleTrackSolo = { viewModel.timelineEngine.toggleTrackSolo(it) },
            onCycleTrackHeight = { viewModel.timelineEngine.cycleTrackHeight(it) },
            onSelectKeyframe = { viewModel.timelineEngine.selectKeyframe(it) },
            onMoveKeyframe = { kfId, newTime -> viewModel.timelineEngine.moveKeyframe(kfId, newTime) },
            onAddAudioKeyframe = { clipId, relTime, vol ->
              viewModel.timelineEngine.addAudioVolumeKeyframe(clipId, relTime, vol)
            },
            onUpdateAudioKeyframe = { clipId, kfId, relTime, vol ->
              viewModel.timelineEngine.updateAudioVolumeKeyframe(clipId, kfId, relTime, vol)
            },
            onDeleteAudioKeyframe = { clipId, kfId ->
              viewModel.timelineEngine.deleteAudioVolumeKeyframe(clipId, kfId)
            },
            modifier = Modifier.fillMaxSize()
          )
        } // End of timeline Box

        // Bottom Navigation Bar is displayed at the bottom of the screen in normal editor mode
        EditorBottomToolbar(
          activeTab = activeTab,
          onTabSelected = { tab ->
            viewModel.setActiveToolbarTab(if (activeTab == tab) null else tab)
          },
          onMoreClick = { showMoreToolsDialog = true }
        )
      } // End of Column (Timeline + Bottom Navigation Bar)
    } // End of AnimatedVisibility(visible = activeTab == null)

    // 2. ACTIVE SUB-TOOL PANEL: Completely replaces bottom nav & timeline, expanding seamlessly to the bottom of the screen
    AnimatedVisibility(
      visible = activeTab != null,
      enter = fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
        expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom),
      exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
        shrinkVertically(animationSpec = tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color(0xFF1E283E)),
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) {} // Consume touch events
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding() // Ensures panel content stays safe from system gesture pill, while Surface background extends edge-to-edge
        ) {
          // Universal Tool Panel Header Bar with ❌ Cross Button (hidden for Audio Tools, Text Tools, Effects, and Elements which have their own sleek headers)
          if (activeTab != EditorToolbarTab.AUDIO && activeTab != EditorToolbarTab.TEXT && activeTab != EditorToolbarTab.EFFECTS && activeTab != EditorToolbarTab.ELEMENTS) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1523))
                .padding(horizontal = 14.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
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
                  Icon(
                    imageVector = when (activeTab) {
                      EditorToolbarTab.EFFECTS -> Icons.Default.AutoAwesome
                      EditorToolbarTab.TEXT -> Icons.Default.TextFields
                      EditorToolbarTab.FILTERS -> Icons.Default.FilterVintage
                      EditorToolbarTab.ADJUST -> Icons.Default.Tune
                      EditorToolbarTab.AUDIO -> Icons.Default.MusicNote
                      EditorToolbarTab.EDIT -> Icons.Default.Edit
                      EditorToolbarTab.TRIM -> Icons.Default.ContentCut
                      EditorToolbarTab.SPEED -> Icons.Default.Speed
                      EditorToolbarTab.TRANSITIONS -> Icons.Default.Transform
                      EditorToolbarTab.STICKERS -> Icons.Default.EmojiEmotions
                      EditorToolbarTab.OVERLAY -> Icons.Default.Layers
                      EditorToolbarTab.MEDIA -> Icons.Default.VideoLibrary
                      else -> Icons.Default.Build
                    },
                    contentDescription = null,
                    tint = Color(0xFF00C2FF),
                    modifier = Modifier.size(16.dp)
                  )
                }
                Text(
                  text = when (activeTab) {
                    EditorToolbarTab.EFFECTS -> "Effects Tools"
                    EditorToolbarTab.TEXT -> "Text Tools"
                    EditorToolbarTab.FILTERS -> "Filters"
                    EditorToolbarTab.ADJUST -> "Adjustments"
                    EditorToolbarTab.AUDIO -> "Audio Tools"
                    EditorToolbarTab.EDIT -> "Edit Clip"
                    EditorToolbarTab.TRIM -> "Trimming"
                    EditorToolbarTab.SPEED -> "Speed & Curve"
                    EditorToolbarTab.TRANSITIONS -> "Transitions"
                    EditorToolbarTab.STICKERS -> "Stickers"
                    EditorToolbarTab.OVERLAY -> "Overlay / PIP"
                    EditorToolbarTab.MEDIA -> "Import Media"
                    EditorToolbarTab.MASK -> "Mask & Blend"
                    EditorToolbarTab.AI -> "AI Suite"
                    EditorToolbarTab.AI_MATTING -> "AI Matting"
                    EditorToolbarTab.ASSET_STORE -> "Asset Store"
                    EditorToolbarTab.VOLUME -> "Volume Control"
                    EditorToolbarTab.CHROMA -> "Chroma Key"
                    EditorToolbarTab.CANVAS -> "Canvas Background"
                    EditorToolbarTab.KEYFRAME -> "Keyframe Animation"
                    EditorToolbarTab.CAPTIONS -> "Auto Captions"
                    EditorToolbarTab.ANIMATIONS -> "Animations"
                    EditorToolbarTab.BACKGROUND -> "Background"
                    EditorToolbarTab.AI_AVATAR -> "AI Avatar"
                    EditorToolbarTab.ELEMENTS -> "Elements"
                    null -> "Tools"
                  },
                  style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                  )
                )
              }

              // ❌ Prominent Cross Button to close/exit all tool panels
              IconButton(
                onClick = {
                  viewModel.setActiveToolbarTab(null)
                },
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF1E283E))
                  .testTag("close_tool_panel_cross_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close tool panel",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1E283E))
            )
          }

          // Sub-Tool Content
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
          ) {
            when (activeTab) {
              EditorToolbarTab.MEDIA -> MediaImportPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.OVERLAY -> OverlayToolPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.EDIT -> EditToolPanel(viewModel)
              EditorToolbarTab.TRIM -> VideoTrimmingToolPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.ADJUST -> AdjustToolPanel(viewModel)
              EditorToolbarTab.SPEED -> com.example.ui.components.SpeedCurveToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.MASK -> com.example.ui.components.MaskAndBlendToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.AI -> com.example.ui.components.AiSuiteToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.AI_MATTING -> com.example.ui.components.AiMattingToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.ASSET_STORE -> com.example.ui.components.AssetStoreToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.FILTERS -> FiltersToolPanel(viewModel)
              EditorToolbarTab.EFFECTS -> EffectsToolPanel(viewModel)
              EditorToolbarTab.TRANSITIONS -> TransitionsPanel(
                viewModel = viewModel,
                onStartDragTransition = { draggedTransitionType = it }
              )
              EditorToolbarTab.TEXT -> {
                com.example.ui.components.text.ModernTextToolsPanel(
                  viewModel = viewModel,
                  onClose = { viewModel.setActiveToolbarTab(null) }
                )
              }
              EditorToolbarTab.ELEMENTS -> com.example.ui.components.elements.ElementsToolPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.AUDIO -> com.example.ui.components.audio.AudioToolsContainerPanel(
                viewModel = viewModel,
                onClose = { viewModel.setActiveToolbarTab(null) }
              )
              EditorToolbarTab.VOLUME -> VolumeToolPanel(viewModel)
              EditorToolbarTab.STICKERS -> StickersToolPanel(viewModel)
              EditorToolbarTab.CHROMA -> ChromaKeyPanel(viewModel)
              EditorToolbarTab.CANVAS -> CanvasPanel(viewModel)
              EditorToolbarTab.KEYFRAME -> KeyframeAnimationPanel(viewModel)
              EditorToolbarTab.CAPTIONS -> CaptionsToolPanel(viewModel)
              EditorToolbarTab.AI_AVATAR -> AIAvatarToolPanel(viewModel)
              EditorToolbarTab.BACKGROUND -> BackgroundToolPanel(viewModel)
              EditorToolbarTab.ANIMATIONS -> AnimationsToolPanel(viewModel)
              null -> {}
            }
          }
        }
      }
    } // End of AnimatedVisibility (activeTab != null)

    // HIDDEN SIDEBAR (Old layers panel - keep code but hide: 0% width, takes no space)
    Box(
      modifier = Modifier
        .size(0.dp)
        .testTag("layers_panel")
    )
    } // End of Column (Video Preview, Controls, Timeline, Tool Panel)

    // 3. Sliding Multi-Layer Studio Drawer
    AnimatedVisibility(
      visible = isLayersOpen,
      enter = slideInHorizontally { -it } + fadeIn(),
      exit = slideOutHorizontally { -it } + fadeOut(),
      modifier = Modifier.align(Alignment.CenterStart)
    ) {
      com.example.ui.components.timeline.LayersDrawer(
        timeline = timeline,
        selectedElement = selectedElement,
        onSelectElement = { sel ->
          viewModel.timelineEngine.selectElement(sel)
        },
        onBringLayerForward = { clipId ->
          viewModel.timelineEngine.bringLayerForward(clipId)
        },
        onSendLayerBackward = { clipId ->
          viewModel.timelineEngine.sendLayerBackward(clipId)
        },
        onBringLayerToFront = { clipId ->
          viewModel.timelineEngine.bringLayerToFront(clipId)
        },
        onSendLayerToBack = { clipId ->
          viewModel.timelineEngine.sendLayerToBack(clipId)
        },
        onToggleClipLock = { clipId ->
          viewModel.timelineEngine.toggleClipLock(clipId)
        },
        onToggleClipHide = { clipId ->
          viewModel.timelineEngine.toggleClipHide(clipId)
        },
        onDuplicateClip = { clipId ->
          viewModel.timelineEngine.duplicateClips(setOf(clipId))
        },
        onDeleteClip = { clipId ->
          viewModel.timelineEngine.deleteClips(setOf(clipId))
        },
        onToggleTrackLock = { viewModel.timelineEngine.toggleTrackLock(it) },
        onToggleTrackHide = { viewModel.timelineEngine.toggleTrackHide(it) },
        onToggleTrackMute = { viewModel.timelineEngine.toggleTrackMute(it) },
        onToggleTrackSolo = { viewModel.timelineEngine.toggleTrackSolo(it) },
        onCycleTrackHeight = { viewModel.timelineEngine.cycleTrackHeight(it) },
        onClose = { isLayersOpen = false }
      )
    }
  }
}

  // Speed Dialog
  if (showSpeedDialog) {
    val activeSpeed = (selectedElement as? SelectedTrackElement.Video)?.let { sel ->
      timeline.videoClips.find { it.id == sel.clipId }?.speed
    } ?: 1.0f
    ClipSpeedDialog(
      currentSpeed = activeSpeed,
      onDismiss = { showSpeedDialog = false },
      onConfirm = { newSpeed ->
        viewModel.timelineEngine.setClipSpeed(speed = newSpeed)
        showSpeedDialog = false
      }
    )
  }

  // Rename Dialog
  if (showRenameDialog) {
    RenameProjectDialog(
      currentName = projectName,
      onDismiss = { showRenameDialog = false },
      onConfirm = {
        viewModel.renameProject(viewModel.activeProjectId.value, it)
        showRenameDialog = false
      }
    )
  }

  // Relink Missing Media Dialog
  if (showRelinkMediaDialog) {
    com.example.ui.components.RelinkMediaDialog(
      missingItems = missingMediaList,
      onDismiss = { showRelinkMediaDialog = false },
      onRelink = { clipId, newUri ->
        viewModel.relinkMedia(clipId, newUri)
      }
    )
  }

  // Project Settings Dialog
  if (showProjectSettingsDialog) {
    com.example.ui.components.ProjectSettingsDialog(
      projectName = projectName,
      currentAspectRatio = aspectRatio,
      currentResolution = activeResolution,
      currentFps = activeFps,
      currentSampleRate = activeSampleRate,
      currentCanvasColor = activeCanvasColor,
      totalDurationMs = timeline.totalDurationMs,
      onDismiss = { showProjectSettingsDialog = false },
      onSaveSettings = { aspect, res, fps, sampleRate, canvasColor ->
        viewModel.updateProjectSettings(aspect, res, fps, sampleRate, canvasColor)
      }
    )
  }

  // Export Configuration Dialog (Media3 Transformer)
  if (showExportConfigDialog) {
    com.example.ui.components.export.ExportConfigurationDialog(
      projectName = projectName,
      totalDurationMs = timeline.totalDurationMs,
      aspectRatio = aspectRatio,
      initialResolution = activeResolution,
      initialFps = activeFps,
      onDismiss = { showExportConfigDialog = false },
      onConfirmExport = { config ->
        showExportConfigDialog = false
        viewModel.saveCurrentProject()
        viewModel.startExport(config)
      }
    )
  }

  // More Tools Dialog
  if (showMoreToolsDialog) {
    MoreToolsDialog(
      onDismiss = { showMoreToolsDialog = false },
      onSelectTab = { tab ->
        showMoreToolsDialog = false
        viewModel.setActiveToolbarTab(tab)
      }
    )
  }

  // Immersive Fullscreen Video Preview Dialog
  if (isFullscreenPreview) {
    Dialog(
      onDismissRequest = { isFullscreenPreview = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        VideoPreviewSurface(
          timeline = timeline,
          currentPosMs = currentPosMs,
          aspectRatio = aspectRatio,
          selectedElement = selectedElement,
          onSelectElement = { viewModel.timelineEngine.selectElement(it) },
          onUpdateOverlay = { viewModel.timelineEngine.updateOverlayClip(it) },
          onUpdateText = { viewModel.timelineEngine.updateTextClip(it) },
          onUpdateSticker = { viewModel.timelineEngine.updateStickerClip(it) },
          onDeleteClip = { viewModel.timelineEngine.deleteClips(setOf(it)) },
          onDuplicateClip = { viewModel.timelineEngine.duplicateClips(setOf(it)) },
          onEditText = { clip ->
            isFullscreenPreview = false
            viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
            viewModel.setActiveToolbarTab(EditorToolbarTab.TEXT)
          },
          player = viewModel.playbackEngine.player,
          onGetOverlayPlayer = { clipId -> viewModel.playbackEngine.getOverlayPlayer(clipId) },
          onToggleFullscreen = { isFullscreenPreview = false },
          onAddMedia = {
            timelineMediaPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          modifier = Modifier.fillMaxSize()
        )

        // Top bar overlay
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { isFullscreenPreview = false },
            modifier = Modifier
              .size(40.dp)
              .background(Color.Black.copy(alpha = 0.6f), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close Fullscreen", tint = Color.White)
          }

          Text(
            text = projectName,
            style = MaterialTheme.typography.titleMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
          )

          Text(
            text = "${formatDuration(currentPosMs)} / ${formatDurationShort(timeline.totalDurationMs)}",
            style = MaterialTheme.typography.labelMedium.copy(
              color = CyanAccent,
              fontWeight = FontWeight.Bold
            )
          )
        }

        // Bottom playback bar overlay
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = 24.dp, vertical = 18.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { viewModel.timelineEngine.stepBackwardOneFrame() },
            modifier = Modifier.size(44.dp)
          ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "-1 Frame", tint = Color.White, modifier = Modifier.size(28.dp))
          }
          Spacer(modifier = Modifier.width(20.dp))
          IconButton(
            onClick = { viewModel.timelineEngine.togglePlayPause() },
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(CyanAccent)
          ) {
            Icon(
              if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = "Play/Pause",
              tint = Color.Black,
              modifier = Modifier.size(32.dp)
            )
          }
          Spacer(modifier = Modifier.width(20.dp))
          IconButton(
            onClick = { viewModel.timelineEngine.stepForwardOneFrame() },
            modifier = Modifier.size(44.dp)
          ) {
            Icon(Icons.Default.SkipNext, contentDescription = "+1 Frame", tint = Color.White, modifier = Modifier.size(28.dp))
          }
        }
      }
    }
  }
}
}

@Composable
private fun EditorTopBar(
  activeResolution: Resolution,
  exportState: ExportState,
  canUndo: Boolean = false,
  canRedo: Boolean = false,
  isDiagnosticActive: Boolean = false,
  onToggleDiagnostics: () -> Unit = {},
  onUndoClick: () -> Unit = {},
  onRedoClick: () -> Unit = {},
  onBackClick: () -> Unit,
  onExportClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .height(40.dp)
      .background(Color.Black)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Close (X) + Undo + Redo + Diagnostics Speed HUD
    IconButton(
      onClick = onBackClick,
      modifier = Modifier
        .size(34.dp)
        .testTag("close_btn")
    ) {
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close",
        tint = Color.White,
        modifier = Modifier.size(20.dp)
      )
    }

    Spacer(modifier = Modifier.width(2.dp))

    IconButton(
      onClick = onUndoClick,
      enabled = canUndo,
      modifier = Modifier
        .size(34.dp)
        .testTag("top_undo_btn")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Undo,
        contentDescription = "Undo",
        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp)
      )
    }

    IconButton(
      onClick = onRedoClick,
      enabled = canRedo,
      modifier = Modifier
        .size(34.dp)
        .testTag("top_redo_btn")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Redo,
        contentDescription = "Redo",
        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(18.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Right: compact Export control, matching the Close/Undo/Redo footprint.
    // Keep the action discoverable while avoiding a wide pill that consumes editor chrome.
    val isRendering = exportState is ExportState.Rendering
    Surface(
      onClick = { if (!isRendering) onExportClick() },
      shape = CircleShape,
      color = if (!isRendering) Color(0xFF006FE6) else Color(0xFF263244),
      enabled = !isRendering,
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .testTag("export_btn")
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        if (isRendering) {
          val progress = (exportState as ExportState.Rendering).progressPercent
          CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White
          )
        } else {
          Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = "Export",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun VideoPreviewSurface(
  timeline: Timeline,
  currentPosMs: Long,
  aspectRatio: AspectRatio,
  selectedElement: SelectedTrackElement = SelectedTrackElement.None,
  onSelectElement: (SelectedTrackElement) -> Unit = {},
  onUpdateOverlay: (VideoClip) -> Unit = {},
  onUpdateText: (TextClip) -> Unit = {},
  onUpdateSticker: (StickerClip) -> Unit = {},
  onDeleteClip: (String) -> Unit = {},
  onDuplicateClip: (String) -> Unit = {},
  onEditText: ((TextClip) -> Unit)? = null,
  player: ExoPlayer? = null,
  onGetOverlayPlayer: ((String) -> ExoPlayer?)? = null,
  onToggleFullscreen: (() -> Unit)? = null,
  onAddMedia: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  // Find current active video clip
  val activeClip = remember(timeline.videoClips, currentPosMs) {
    timeline.videoClips.find {
      currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs
    } ?: timeline.videoClips.lastOrNull()
  }

  val activeOverlays = remember(timeline.overlayClips, currentPosMs) {
    timeline.overlayClips.filter {
      currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs
    }
  }

  val activeTexts = remember(timeline.textClips, currentPosMs, selectedElement) {
    val selectedId = (selectedElement as? SelectedTrackElement.Text)?.clipId
    timeline.textClips.filter {
      (it.id == selectedId) ||
      (currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs)
    }.sortedWith(compareBy({ it.trackIndex }, { it.timelineStartMs }))
  }

  val activeStickers = remember(timeline.stickerClips, currentPosMs) {
    timeline.stickerClips.filter {
      currentPosMs >= it.timelineStartMs && currentPosMs <= it.timelineStartMs + it.durationMs
    }
  }

  val activeEffects = remember(timeline.effectClips, currentPosMs, selectedElement, activeClip) {
    timeline.effectClips.filter { clip ->
      val isSelected = (selectedElement as? SelectedTrackElement.Effect)?.clipId == clip.id
      !clip.isHidden && (
        isSelected ||
        if (clip.targetClipId != null) {
          activeClip != null && activeClip.id == clip.targetClipId &&
            currentPosMs >= activeClip.timelineStartMs &&
            currentPosMs < activeClip.timelineStartMs + activeClip.durationMs
        } else {
          currentPosMs >= clip.timelineStartMs && currentPosMs < clip.timelineStartMs + clip.durationMs
        }
      )
    }.sortedBy { it.timelineStartMs }
  }

  // Calculate accumulated motion transform from active effects (shake, zoom, skater zoom, vertigo dolly, spin, pan, mirror)
  val effectMotion = remember(activeEffects, currentPosMs, com.example.ui.components.effects.isBeforeAfterComparing) {
    if (com.example.ui.components.effects.isBeforeAfterComparing || activeEffects.isEmpty()) {
      VideoEffectRenderer.EffectMotionTransform()
    } else {
      VideoEffectRenderer.calculateMotionTransform(activeEffects, currentPosMs)
    }
  }

  // Keyframe calculations
  val clipTransform = remember(activeClip, currentPosMs) {
    if (activeClip != null) {
      val rel = currentPosMs - activeClip.timelineStartMs
      KeyframeInterpolator.interpolate(activeClip, rel)
    } else null
  }

  // Color Matrix for video adjustments, filter presets, and active color effects matching export pipeline
  val androidCombinedMatrix = remember(timeline.adjustments, timeline.filter, activeClip?.filter, activeEffects, currentPosMs, com.example.ui.components.effects.isBeforeAfterComparing) {
    val baseMatrix = com.example.engine.composition.ColorFilterGenerator.createCombinedMatrix(
      timeline.adjustments,
      timeline.filter,
      activeClip?.filter
    )
    val resultMatrix = android.graphics.ColorMatrix(baseMatrix)
    if (!com.example.ui.components.effects.isBeforeAfterComparing && activeEffects.isNotEmpty()) {
      val effectMat = VideoEffectRenderer.calculateEffectColorMatrix(activeEffects, currentPosMs)
      if (effectMat != null) {
        resultMatrix.postConcat(effectMat)
      }
    }
    resultMatrix
  }

  val isIdentityFilter = remember(androidCombinedMatrix) {
    com.example.engine.composition.ColorFilterGenerator.isIdentityMatrix(androidCombinedMatrix)
  }

  val combinedColorFilter = remember(androidCombinedMatrix, isIdentityFilter) {
    if (isIdentityFilter) null else ColorFilter.colorMatrix(ColorMatrix(androidCombinedMatrix.array))
  }

  // Pinch-to-zoom & pan inspection state
  var previewZoomScale by remember { mutableFloatStateOf(1.0f) }
  var previewPanOffset by remember { mutableStateOf(Offset.Zero) }
  var showSafeAreas by remember { mutableStateOf(false) }
  var showGrid by remember { mutableStateOf(false) }
  var showCenterGuides by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .aspectRatio(aspectRatio.ratio, matchHeightConstraintsFirst = true)
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
    colors = CardDefaults.cardColors(containerColor = Color(timeline.canvasBackgroundColor))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = {
              if (previewZoomScale > 1.05f) {
                previewZoomScale = 1.0f
                previewPanOffset = Offset.Zero
              } else {
                previewZoomScale = 2.0f
              }
            }
          )
        }
        .pointerInput(selectedElement) {
          detectTransformGestures { _, pan, zoom, _ ->
            // Only allow preview frame zooming/panning when no overlay element is selected
            if (selectedElement == SelectedTrackElement.None && (zoom != 1.0f || previewZoomScale > 1.05f)) {
              val oldScale = previewZoomScale
              val newScale = (oldScale * zoom).coerceIn(1.0f, 5.0f)
              previewZoomScale = newScale
              if (newScale > 1.0f) {
                val maxX = (newScale - 1f) * 400f
                val maxY = (newScale - 1f) * 400f
                val newPanX = (previewPanOffset.x + pan.x).coerceIn(-maxX, maxX)
                val newPanY = (previewPanOffset.y + pan.y).coerceIn(-maxY, maxY)
                previewPanOffset = Offset(newPanX, newPanY)
              } else {
                previewPanOffset = Offset.Zero
              }
            }
          }
        }
    ) {
      // Zoomable and Pannable Frame Content Container
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = previewZoomScale
            scaleY = previewZoomScale
            translationX = previewPanOffset.x
            translationY = previewPanOffset.y
          }
      ) {
        // Background Video / Image Layer
        if (activeClip != null) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                val baseScaleX = clipTransform?.scaleX ?: 1f
                val baseScaleY = clipTransform?.scaleY ?: 1f
                val baseRot = clipTransform?.rotation ?: 0f
                val baseTransX = (clipTransform?.posX ?: 0f) * size.width
                val baseTransY = (clipTransform?.posY ?: 0f) * size.height
                val baseAlpha = clipTransform?.opacity ?: 1f

                scaleX = baseScaleX * effectMotion.scaleX
                scaleY = baseScaleY * effectMotion.scaleY
                rotationZ = baseRot + effectMotion.rotation
                translationX = baseTransX + effectMotion.translationX * size.width
                translationY = baseTransY + effectMotion.translationY * size.height
                alpha = (baseAlpha * effectMotion.alpha).coerceIn(0f, 1f)
              },
            contentAlignment = Alignment.Center
          ) {
            val isRealPlayable = remember(activeClip.uri) {
              MediaRelinkManager.isRealPlayableMedia(context, activeClip.uri)
            }
            if (activeClip.isVideo && isRealPlayable && player != null) {
              val realVideoFilterModifier = if (!isIdentityFilter && combinedColorFilter != null) {
                Modifier
                  .fillMaxSize()
                  .drawWithContent {
                    drawIntoCanvas { canvas ->
                      val paint = androidx.compose.ui.graphics.Paint().apply {
                        this.colorFilter = combinedColorFilter
                      }
                      canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
                      drawContent()
                      canvas.restore()
                    }
                  }
              } else {
                Modifier.fillMaxSize()
              }

              AndroidView(
                factory = { ctx ->
                  android.view.TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                      ViewGroup.LayoutParams.MATCH_PARENT,
                      ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player.setVideoTextureView(this)
                    android.util.Log.d("VideoPreviewSurface", "TextureView created and attached to ExoPlayer")
                  }
                },
                update = { tv ->
                  player.setVideoTextureView(tv)
                  val paint = if (isIdentityFilter) {
                    null
                  } else {
                    android.graphics.Paint().apply {
                      colorFilter = android.graphics.ColorMatrixColorFilter(androidCombinedMatrix)
                    }
                  }
                  tv.setLayerType(
                    if (paint != null) android.view.View.LAYER_TYPE_HARDWARE else android.view.View.LAYER_TYPE_NONE,
                    paint
                  )
                  tv.invalidate()
                },
                onReset = { /* Preserve texture view across recompositions */ },
                onRelease = { /* Keep player instance intact */ },
                modifier = realVideoFilterModifier
              )
            } else if (!activeClip.isVideo && activeClip.uri.isNotBlank() && !activeClip.uri.startsWith("stock://") && !activeClip.uri.startsWith("sample://")) {
              AsyncImage(
                model = activeClip.uri,
                contentDescription = activeClip.name,
                contentScale = ContentScale.Fit,
                colorFilter = combinedColorFilter,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              SyntheticClipPreview(
                clip = activeClip,
                currentPosMs = currentPosMs,
                colorFilter = combinedColorFilter,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        } else if (timeline.videoClips.isEmpty() && timeline.overlayClips.isEmpty() && timeline.textClips.isEmpty() && timeline.stickerClips.isEmpty() && timeline.audioClips.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clickable { onAddMedia?.invoke() }
              .testTag("empty_timeline_canvas"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF1E293B))
                  .border(1.dp, CyanAccent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.VideoLibrary,
                  contentDescription = "Add Media",
                  tint = CyanAccent,
                  modifier = Modifier.size(26.dp)
                )
              }
              Text(
                text = "Tap to add video or photo",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = TextPrimary,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp
                )
              )
              Text(
                text = "Clean blank timeline ready for your media",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = TextTertiary,
                  fontSize = 11.sp
                )
              )
            }
          }
        }

        // Active Visual Effects Overlay (procedural shaders, flares, sparks, scanlines, glitch, wings, grids)
        if (activeEffects.isNotEmpty() && !com.example.ui.components.effects.isBeforeAfterComparing) {
          androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
          ) {
            drawIntoCanvas { composeCanvas ->
              VideoEffectRenderer.renderEffectsOnCanvas(
                canvas = composeCanvas.nativeCanvas,
                activeEffects = activeEffects,
                currentPosMs = currentPosMs,
                width = size.width.toInt(),
                height = size.height.toInt()
              )
            }
          }
        }

        // Touch-Based Interactive Transformation Layer (Text, PIP Overlays, Stickers, Shapes)
        InteractiveTransformOverlay(
          activeTexts = activeTexts,
          activeOverlays = activeOverlays,
          activeStickers = activeStickers,
          selectedElement = selectedElement,
          currentPosMs = currentPosMs,
          onSelectElement = onSelectElement,
          onUpdateText = onUpdateText,
          onUpdateOverlay = onUpdateOverlay,
          onUpdateSticker = onUpdateSticker,
          onDeleteClip = onDeleteClip,
          onDuplicateClip = onDuplicateClip,
          onEditText = onEditText,
          getOverlayPlayer = onGetOverlayPlayer,
          modifier = Modifier.fillMaxSize()
        )

        // Safe Areas, Rule-of-Thirds Grid, and Center Crosshair Guidelines (Editor Only)
        if (showSafeAreas || showGrid || showCenterGuides) {
          androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Rule of Thirds Grid (3x3)
            if (showGrid) {
              val lineAlpha = 0.35f
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
              drawLine(Color.White.copy(alpha = lineAlpha), Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
            }

            // 2. Center Crosshair Guides
            if (showCenterGuides) {
              drawLine(CyanAccent.copy(alpha = 0.6f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.5.dp.toPx())
              drawLine(CyanAccent.copy(alpha = 0.6f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.5.dp.toPx())
              drawCircle(CyanAccent, radius = 3.dp.toPx(), center = Offset(w / 2f, h / 2f))
            }

            // 3. Title Safe Area (90% boundary: 5% inset) and Action Safe Area (80% boundary: 10% inset)
            if (showSafeAreas) {
              // Action Safe (80%) - Amber/Gold
              drawRect(
                color = GoldAccent.copy(alpha = 0.5f),
                topLeft = Offset(w * 0.10f, h * 0.10f),
                size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.80f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
              )
              // Title Safe (90%) - Cyan
              drawRect(
                color = CyanAccent.copy(alpha = 0.5f),
                topLeft = Offset(w * 0.05f, h * 0.05f),
                size = androidx.compose.ui.geometry.Size(w * 0.90f, h * 0.90f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
              )
            }
          }
        }
      }

      // Floating Zoom Scale Reset Badge (Top-Left overlay when zoomed in)
      if (previewZoomScale > 1.05f) {
        Surface(
          onClick = {
            previewZoomScale = 1.0f
            previewPanOffset = Offset.Zero
          },
          shape = RoundedCornerShape(16.dp),
          color = CyanAccent.copy(alpha = 0.95f),
          contentColor = Color.Black,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .testTag("reset_zoom_badge")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              Icons.Default.FitScreen,
              contentDescription = "Reset Zoom",
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "%.1fx (Reset)".format(previewZoomScale),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            )
          }
        }
      }

      // Fullscreen button overlay in video preview corner
      if (onToggleFullscreen != null) {
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .testTag("preview_fullscreen_overlay_btn")
        ) {
          Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Fullscreen",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun TimelineControlsBar(
  isPlaying: Boolean,
  currentPosMs: Long,
  totalDurationMs: Long,
  zoom: Float,
  canUndo: Boolean,
  canRedo: Boolean,
  isSnapping: Boolean,
  onTogglePlay: () -> Unit,
  onStop: () -> Unit,
  onStepBack: () -> Unit,
  onStepForward: () -> Unit,
  onUndoClick: () -> Unit,
  onRedoClick: () -> Unit,
  onToggleSnapping: () -> Unit,
  onSplit: () -> Unit,
  onDelete: () -> Unit,
  onAddKeyframe: () -> Unit,
  onAddMedia: () -> Unit,
  onZoomChange: (Float) -> Unit,
  onToggleFullscreen: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(StudioSurface)
      .drawBehind {
        drawLine(
          color = StudioBorder,
          start = Offset(0f, size.height),
          end = Offset(size.width, size.height),
          strokeWidth = 1.dp.toPx()
        )
      }
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    // Left: Fullscreen icon & Timecode (e.g., 00:07 / 00:29)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (onToggleFullscreen != null) {
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier
            .size(32.dp)
            .testTag("timeline_fullscreen_button")
        ) {
          Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Fullscreen",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Text(
        text = "${formatDurationShort(currentPosMs)} / ${formatDurationShort(totalDurationMs)}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = TextPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        ),
        modifier = Modifier.testTag("timeline_timecode_display")
      )
    }

    // Center: Frame Step Back, Prominent Sky Blue Play/Pause, Frame Step Forward
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      IconButton(onClick = onStepBack, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.SkipPrevious, contentDescription = "-1 Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }

      IconButton(
        onClick = onTogglePlay,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(CyanAccent)
          .testTag("timeline_play_pause")
      ) {
        Icon(
          if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = "Play/Pause",
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
      }

      IconButton(onClick = onStepForward, modifier = Modifier.size(30.dp)) {
        Icon(Icons.Default.SkipNext, contentDescription = "+1 Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
      }
    }

    // Right: Snapping, Undo, Redo, Quick Split, Delete
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      IconButton(
        onClick = onToggleSnapping,
        modifier = Modifier.size(30.dp).testTag("editor_snapping_button")
      ) {
        Icon(
          Icons.Default.Adjust,
          contentDescription = "Snapping",
          tint = if (isSnapping) CyanAccent else TextTertiary,
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onUndoClick,
        enabled = canUndo,
        modifier = Modifier.size(30.dp).testTag("editor_undo_button")
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Undo,
          contentDescription = "Undo",
          tint = if (canUndo) TextPrimary else TextTertiary.copy(alpha = 0.35f),
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onRedoClick,
        enabled = canRedo,
        modifier = Modifier.size(30.dp).testTag("editor_redo_button")
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Redo,
          contentDescription = "Redo",
          tint = if (canRedo) TextPrimary else TextTertiary.copy(alpha = 0.35f),
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onSplit,
        modifier = Modifier.size(30.dp).testTag("timeline_quick_split")
      ) {
        Icon(
          Icons.Default.CallSplit,
          contentDescription = "Split",
          tint = CyanAccent,
          modifier = Modifier.size(17.dp)
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(30.dp).testTag("timeline_quick_delete")
      ) {
        Icon(
          Icons.Default.Delete,
          contentDescription = "Delete",
          tint = RedAccent,
          modifier = Modifier.size(17.dp)
        )
      }
    }
  }
}

// TIMELINE WITH TIMESTAMPS: 40-50dp
@Composable
private fun TimelineTimestampsRow(
  currentPosMs: Long,
  totalDurationMs: Long,
  isMultiTrackView: Boolean = false,
  onToggleMultiTrackView: (() -> Unit)? = null
) {
  val total = totalDurationMs.coerceAtLeast(1000L)
  val safePos = currentPosMs.coerceIn(0L, total)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(45.dp)
      .background(Color.Black)
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("timeline_container"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "${formatDurationShort(safePos)} / ${formatDurationShort(total)}",
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.testTag("time_display")
    )

    Text(
      text = "•",
      color = Color.White.copy(alpha = 0.5f),
      modifier = Modifier.padding(horizontal = 6.dp)
    )

    // Timestamps around current time
    val step = (total / 5).coerceAtLeast(2000L)
    val times = listOf(
      (safePos - step).coerceAtLeast(0L),
      safePos,
      (safePos + step).coerceAtMost(total)
    )

    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      times.forEachIndexed { index, timeMs ->
        Text(
          text = formatDurationShort(timeMs),
          color = if (index == 1) CyanAccent else Color.White.copy(alpha = 0.6f),
          fontSize = 10.sp,
          fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Normal
        )
      }
    }

    // Toggle Multi-Track vs Compact View Pill
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = if (isMultiTrackView) StudioSurfaceVariant else StudioDarkBg,
      border = BorderStroke(1.dp, if (isMultiTrackView) CyanAccent else StudioBorder),
      modifier = Modifier
        .clickable { onToggleMultiTrackView?.invoke() }
        .testTag("timeline_mode_toggle")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (isMultiTrackView) Icons.Default.ViewAgenda else Icons.Default.Tune,
          contentDescription = null,
          tint = if (isMultiTrackView) CyanAccent else AudioTrackColor,
          modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (isMultiTrackView) "Multi-Track" else "Envelopes",
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        )
      }
    }
  }
}

// FILM STRIP THUMBNAILS: 80-100dp
@Composable
private fun FilmstripThumbnailsRow(
  timeline: Timeline,
  currentPosMs: Long,
  onSeek: (Long) -> Unit,
  onScrollLeft: () -> Unit,
  onAddMedia: () -> Unit
) {
  val totalDuration = timeline.totalDurationMs.coerceAtLeast(2000L)
  val frameIntervalMs = 500L
  val frameCount = ((totalDuration / frameIntervalMs) + 1).toInt().coerceIn(8, 60)

  val scrollState = rememberScrollState()

  LaunchedEffect(currentPosMs) {
    val progress = (currentPosMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val targetScroll = (scrollState.maxValue * progress).toInt()
    if (!scrollState.isScrollInProgress) {
      scrollState.scrollTo(targetScroll)
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(90.dp)
      .background(Color.Black)
      .testTag("filmstrip_scroll"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left scroll button
    IconButton(
      onClick = onScrollLeft,
      modifier = Modifier
        .width(48.dp)
        .fillMaxHeight()
        .testTag("scroll_left_btn")
    ) {
      Icon(
        imageVector = Icons.Default.ChevronLeft,
        contentDescription = "Scroll Left",
        tint = Color.White,
        modifier = Modifier.size(28.dp)
      )
    }

    // Scrollable Thumbnails container
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .horizontalScroll(scrollState)
        .padding(vertical = 4.dp)
        .testTag("filmstrip_container"),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (i in 0 until frameCount) {
        val frameTimeMs = (i * frameIntervalMs).coerceAtMost(totalDuration)
        val isCurrentFrame = kotlin.math.abs(currentPosMs - frameTimeMs) < frameIntervalMs

        val activeClip = timeline.videoClips.find { c ->
          frameTimeMs >= c.timelineStartMs && frameTimeMs < (c.timelineStartMs + c.durationMs)
        } ?: timeline.overlayClips.find { c ->
          frameTimeMs >= c.timelineStartMs && frameTimeMs < (c.timelineStartMs + c.durationMs)
        }

        FilmstripThumbnailCell(
          frameIndex = i,
          frameTimeMs = frameTimeMs,
          isCurrentFrame = isCurrentFrame,
          activeClip = activeClip,
          onSeek = onSeek
        )
      }
    }

    // Right add button
    IconButton(
      onClick = onAddMedia,
      modifier = Modifier
        .width(48.dp)
        .fillMaxHeight()
        .testTag("add_btn")
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add Media",
        tint = CyanAccent,
        modifier = Modifier.size(28.dp)
      )
    }
  }
}

@Composable
private fun FilmstripThumbnailCell(
  frameIndex: Int,
  frameTimeMs: Long,
  isCurrentFrame: Boolean,
  activeClip: VideoClip?,
  onSeek: (Long) -> Unit
) {
  val context = LocalContext.current
  val sourceTimeMs = remember(activeClip, frameTimeMs) {
    if (activeClip != null) {
      activeClip.timelineToSourceMs(frameTimeMs)
    } else frameTimeMs
  }

  var thumbnailBitmap by remember(activeClip?.uri, sourceTimeMs) {
    val key = VideoThumbnailManager.makeKey(activeClip?.uri ?: "", sourceTimeMs, 140, 140)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(activeClip?.uri, sourceTimeMs) {
    if (activeClip?.uri?.isNotEmpty() == true && thumbnailBitmap == null) {
      VideoThumbnailManager.requestThumbnail(
        context = context,
        uri = activeClip.uri,
        sourceTimeMs = sourceTimeMs,
        targetWidth = 140,
        targetHeight = 140,
        isVideo = activeClip.isVideo
      ) { bmp ->
        thumbnailBitmap = bmp
      }
    }
  }

  Box(
    modifier = Modifier
      .width(72.dp)
      .height(82.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF1E293B))
      .border(
        width = if (isCurrentFrame) 2.dp else 1.dp,
        color = if (isCurrentFrame) CyanAccent else Color(0xFF334155),
        shape = RoundedCornerShape(6.dp)
      )
      .clickable { onSeek(frameTimeMs) }
  ) {
    val currentBmp = thumbnailBitmap
    if (currentBmp != null && !currentBmp.isRecycled) {
      Image(
        bitmap = currentBmp.asImageBitmap(),
        contentDescription = "Frame ${frameIndex + 1}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else if (activeClip?.uri?.isNotEmpty() == true) {
      AsyncImage(
        model = activeClip.uri,
        contentDescription = "Frame ${frameIndex + 1}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(
                Color(0xFF0F172A),
                Color(0xFF1E293B),
                Color(0xFF0F172A)
              )
            )
          )
          .padding(4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.3f)))
          Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.3f)))
        }
        Icon(
          imageVector = Icons.Default.Movie,
          contentDescription = null,
          tint = if (isCurrentFrame) CyanAccent else Color.White.copy(alpha = 0.4f),
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = formatDurationShort(frameTimeMs),
          color = if (isCurrentFrame) CyanAccent else Color.White.copy(alpha = 0.6f),
          fontSize = 9.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    if (isCurrentFrame) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(3.dp)
          .background(CyanAccent)
      )
    }
  }
}

// BOTTOM TOOLBAR: 8 Main Tools + More Dialog
@Composable
private fun EditorBottomToolbar(
  activeTab: EditorToolbarTab?,
  onTabSelected: (EditorToolbarTab) -> Unit,
  onMoreClick: () -> Unit
) {
  val navItems = listOf(
    FuturisticNavItemData(
      id = "edit",
      label = "Edit",
      icon = Icons.Default.ContentCut,
      theme = NavItemThemes.Edit,
      isSelected = activeTab == EditorToolbarTab.EDIT,
      testTag = "edit_btn",
      onClick = { onTabSelected(EditorToolbarTab.EDIT) }
    ),
    FuturisticNavItemData(
      id = "audio",
      label = "Audio",
      icon = Icons.Default.MusicNote,
      theme = NavItemThemes.Audio,
      isSelected = activeTab == EditorToolbarTab.AUDIO,
      testTag = "audio_btn",
      onClick = { onTabSelected(EditorToolbarTab.AUDIO) }
    ),
    FuturisticNavItemData(
      id = "text",
      label = "Text",
      icon = Icons.Default.Title,
      theme = NavItemThemes.AddText,
      isSelected = activeTab == EditorToolbarTab.TEXT,
      testTag = "text_btn",
      onClick = { onTabSelected(EditorToolbarTab.TEXT) }
    ),
    FuturisticNavItemData(
      id = "elements",
      label = "Elements",
      icon = Icons.Default.Category,
      theme = NavItemThemes.Elements,
      isSelected = activeTab == EditorToolbarTab.ELEMENTS,
      testTag = "elements_btn",
      onClick = { onTabSelected(EditorToolbarTab.ELEMENTS) }
    ),
    FuturisticNavItemData(
      id = "effects",
      label = "Effects",
      icon = Icons.Default.StarBorder,
      theme = NavItemThemes.Effects,
      isSelected = activeTab == EditorToolbarTab.EFFECTS,
      testTag = "effects_btn",
      onClick = { onTabSelected(EditorToolbarTab.EFFECTS) }
    ),
    FuturisticNavItemData(
      id = "overlay",
      label = "Overlay",
      icon = Icons.Default.Layers,
      theme = NavItemThemes.Overlay,
      isSelected = activeTab == EditorToolbarTab.OVERLAY,
      testTag = "overlay_btn",
      onClick = { onTabSelected(EditorToolbarTab.OVERLAY) }
    ),
    FuturisticNavItemData(
      id = "captions",
      label = "Captions",
      icon = Icons.Default.Subtitles,
      theme = NavItemThemes.Captions,
      isSelected = activeTab == EditorToolbarTab.CAPTIONS,
      testTag = "captions_btn",
      onClick = { onTabSelected(EditorToolbarTab.CAPTIONS) }
    ),
    FuturisticNavItemData(
      id = "filters",
      label = "Filters",
      icon = Icons.Default.ColorLens,
      theme = NavItemThemes.Filters,
      isSelected = activeTab == EditorToolbarTab.FILTERS,
      testTag = "filters_btn",
      onClick = { onTabSelected(EditorToolbarTab.FILTERS) }
    ),
    FuturisticNavItemData(
      id = "more",
      label = "Adjust",
      icon = Icons.Default.Tune,
      theme = NavItemThemes.DefaultSlate,
      isSelected = false,
      testTag = "more_btn",
      onClick = onMoreClick
    )
  )

  FuturisticBottomNavBarContainer(
    items = navItems,
    showDividers = true
  )
}

@Composable
private fun MoreToolsDialog(
  onDismiss: () -> Unit,
  onSelectTab: (EditorToolbarTab) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF0F1523),
    title = {
      Text(
        text = "More Editor Tools",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      val tools = listOf(
        Triple("Speed", Icons.Default.Speed, EditorToolbarTab.SPEED),
        Triple("Elements", Icons.Default.Category, EditorToolbarTab.ELEMENTS),
        Triple("Trim", Icons.Default.Crop, EditorToolbarTab.TRIM),
        Triple("Adjust", Icons.Default.Tune, EditorToolbarTab.ADJUST),
        Triple("Volume", Icons.Default.VolumeUp, EditorToolbarTab.VOLUME),
        Triple("Mask", Icons.Default.Layers, EditorToolbarTab.MASK),
        Triple("Animations", Icons.Default.Animation, EditorToolbarTab.ANIMATIONS),
        Triple("Keyframe", Icons.Default.Diamond, EditorToolbarTab.KEYFRAME),
        Triple("Transitions", Icons.Default.Transform, EditorToolbarTab.TRANSITIONS),
        Triple("Canvas", Icons.Default.CropSquare, EditorToolbarTab.CANVAS),
        Triple("Background", Icons.Default.Texture, EditorToolbarTab.BACKGROUND),
        Triple("Chroma", Icons.Default.FilterFrames, EditorToolbarTab.CHROMA),
        Triple("AI Media", Icons.Default.VideoLibrary, EditorToolbarTab.AI),
        Triple("AI Avatar", Icons.Default.AccountBox, EditorToolbarTab.AI_AVATAR),
        Triple("Asset Store", Icons.Default.Download, EditorToolbarTab.ASSET_STORE),
        Triple("Media", Icons.Default.AddPhotoAlternate, EditorToolbarTab.MEDIA)
      )

      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
          .fillMaxWidth()
          .height(320.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(tools) { (label, icon, tab) ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1B2233),
            border = BorderStroke(1.dp, Color(0xFF2E3852)),
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
              .clickable { onSelectTab(tab) }
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(imageVector = icon, contentDescription = label, tint = CyanAccent, modifier = Modifier.size(24.dp))
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = CyanAccent, fontWeight = FontWeight.Bold)
      }
    }
  )
}

private fun formatDurationShort(timeMs: Long): String {
  val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun SyntheticClipPreview(
  clip: VideoClip,
  currentPosMs: Long,
  colorFilter: ColorFilter? = null,
  modifier: Modifier = Modifier
) {
  val stockItem = remember(clip.uri) {
    val stockId = clip.uri.removePrefix("stock://")
    StockMediaCatalog.stockItems.find { it.id == stockId }
  }

  val (startColor, endColor, iconEmoji) = remember(clip.id, clip.name, stockItem) {
    if (stockItem != null) {
      Triple(Color(stockItem.gradientStart), Color(stockItem.gradientEnd), stockItem.iconEmoji)
    } else if (clip.name.contains("Mountain", ignoreCase = true) || clip.name.contains("Stream", ignoreCase = true) || clip.uri.contains("nature", ignoreCase = true)) {
      Triple(Color(0xFF0077B6), Color(0xFF00B4D8), "🏔️")
    } else if (clip.name.contains("Skyline", ignoreCase = true) || clip.name.contains("Sunset", ignoreCase = true) || clip.name.contains("Golden", ignoreCase = true) || clip.uri.contains("urban", ignoreCase = true)) {
      Triple(Color(0xFFE85D04), Color(0xFF7209B7), "🌇")
    } else {
      Triple(Color(0xFF1E293B), Color(0xFF0F172A), "🎬")
    }
  }

  val relativeClipPosMs = (currentPosMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
  val progress = if (clip.durationMs > 0) relativeClipPosMs.toFloat() / clip.durationMs else 0f

  val filterAppliedModifier = if (colorFilter != null) {
    modifier.drawWithContent {
      drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply {
          this.colorFilter = colorFilter
        }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        canvas.restore()
      }
    }
  } else {
    modifier
  }

  Box(
    modifier = filterAppliedModifier
      .background(Brush.linearGradient(listOf(startColor, endColor)))
      .drawBehind {
        val scanY = size.height * ((progress * 3f) % 1f)
        drawLine(
          color = Color.White.copy(alpha = 0.08f),
          start = Offset(0f, scanY),
          end = Offset(size.width, scanY),
          strokeWidth = 3f
        )
      },
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(16.dp)
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.35f))
          .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(text = iconEmoji, fontSize = 24.sp)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = clip.name,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = "${formatDurationShort(relativeClipPosMs)} / ${formatDurationShort(clip.durationMs)}",
          color = CyanAccent,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
      }
    }
  }
}
