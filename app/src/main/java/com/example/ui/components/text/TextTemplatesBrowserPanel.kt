package com.example.ui.components.text

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.example.domain.model.TextClip
import com.example.engine.SelectedTrackElement
import com.example.engine.text.TextLayerRenderer
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import java.util.Locale
import java.util.UUID

enum class TextStudioMainTab(val label: String) {
  TEMPLATES("Templates"),
  FONTS("Fonts"),
  STYLES("Styles"),
  EFFECTS("Effects"),
  ANIMATIONS("Animations"),
  BUBBLE("Bubble")
}

enum class TemplateViewLayout {
  GRID_2COL, // 2-Column Large Preview Cards (Section 5 & 20)
  GRID_4COL, // 4-Column Compact Grid
  FEED       // Vertical List Showcase
}

/**
 * Checks if a string contains RTL (Urdu, Arabic, Persian) characters.
 */
fun isRtlText(text: String): Boolean {
  for (char in text) {
    val block = Character.UnicodeBlock.of(char)
    if (block == Character.UnicodeBlock.ARABIC ||
      block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
      block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
      block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
      block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
    ) {
      return true
    }
  }
  return false
}

@Composable
fun TextTemplatesBrowserPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("text_studio_prefs", Context.MODE_PRIVATE) }

  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()

  // Currently selected text clip if any
  val selectedTextClip = remember(timeline.textClips, selectedElement) {
    if (selectedElement is SelectedTrackElement.Text) {
      timeline.textClips.find { it.id == (selectedElement as SelectedTrackElement.Text).clipId }
    } else null
  }

  // Ensure a text clip exists and is selected on load
  LaunchedEffect(Unit) {
    if (selectedTextClip == null) {
      val playhead = viewModel.timelineEngine.currentPositionMs.value
      val existingAtPos = timeline.textClips.find { playhead >= it.timelineStartMs && playhead < it.timelineStartMs + it.durationMs }
      if (existingAtPos != null) {
        viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(existingAtPos.id))
      } else if (timeline.textClips.isNotEmpty()) {
        val last = timeline.textClips.last()
        viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(last.id))
      } else {
        val newClip = TextClip(
          id = java.util.UUID.randomUUID().toString(),
          text = "Your Text Here",
          timelineStartMs = playhead,
          durationMs = 3000L,
          fontSizeSp = 30f,
          textColor = 0xFFFFFFFF,
          animationType = "Pop"
        )
        viewModel.timelineEngine.addTextClipObject(newClip)
        viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(newClip.id))
      }
    }
  }

  // Active top tab (Templates, Fonts, Styles, Effects, Animations)
  var activeMainTab by remember { mutableStateOf(TextStudioMainTab.TEMPLATES) }

  // Persisted Favorites & Recent templates
  val favoriteTemplateIds = remember {
    val saved = prefs.getStringSet("favorite_templates", emptySet()) ?: emptySet()
    mutableStateListOf<String>().apply { addAll(saved) }
  }

  val recentTemplateIds = remember {
    val saved = prefs.getString("recent_templates", "") ?: ""
    val list = saved.split(",").filter { it.isNotBlank() }
    mutableStateListOf<String>().apply { addAll(list) }
  }

  val toggleFavorite: (String) -> Unit = { id ->
    if (favoriteTemplateIds.contains(id)) {
      favoriteTemplateIds.remove(id)
    } else {
      favoriteTemplateIds.add(id)
    }
    prefs.edit().putStringSet("favorite_templates", favoriteTemplateIds.toSet()).apply()
  }

  val addRecent: (String) -> Unit = { id ->
    recentTemplateIds.remove(id)
    recentTemplateIds.add(0, id)
    while (recentTemplateIds.size > 20) {
      recentTemplateIds.removeAt(recentTemplateIds.size - 1)
    }
    prefs.edit().putString("recent_templates", recentTemplateIds.joinToString(",")).apply()
  }

  // User input text (Section 23: Large editable field, default 'Your Text Here')
  var userText by remember(selectedTextClip?.id) {
    mutableStateOf(selectedTextClip?.text?.ifBlank { "Your Text Here" } ?: "Your Text Here")
  }

  var searchQuery by remember { mutableStateOf("") }
  var isSearchActive by remember { mutableStateOf(false) }
  var isExpandedTextEditorOpen by remember { mutableStateOf(false) }
  var selectedCategory by remember { mutableStateOf("Trending") }
  var selectedTemplateId by remember { mutableStateOf("capcut_default") }
  var globalSpeedMultiplier by remember { mutableFloatStateOf(1.0f) }

  // Modal inspection / large preview state
  var inspectingTemplate by remember { mutableStateOf<TextTemplateItem?>(null) }
  var inspectingCustomText by remember { mutableStateOf("") }
  var inspectingSpeedMultiplier by remember { mutableFloatStateOf(1.0f) }
  var inspectingIsPlaying by remember { mutableStateOf(true) }
  var inspectingReplayTrigger by remember { mutableIntStateOf(0) }

  val allTemplates = ALL_TEXT_TEMPLATES

  // Filter templates by category and search query
  val displayedTemplates = remember(
    selectedCategory,
    searchQuery,
    allTemplates,
    favoriteTemplateIds.toList(),
    recentTemplateIds.toList()
  ) {
    var filtered = allTemplates
    when (selectedCategory) {
      "Favorites" -> {
        filtered = filtered.filter { it.id in favoriteTemplateIds }
      }
      "Trending" -> {
        filtered = filtered.filter {
          it.category.equals("Trending", ignoreCase = true) || it.isTrending || it.tags.contains("Trending", ignoreCase = true)
        }
      }
      "KATSEYE👠" -> {
        filtered = filtered.filter {
          it.name.contains("KATSEYE", ignoreCase = true) || it.category.equals("Social", ignoreCase = true) || it.tags.contains("Trend", ignoreCase = true)
        }
      }
      "Whimsical" -> {
        filtered = filtered.filter {
          it.category.equals("Creative", ignoreCase = true) || it.category.equals("Cartoon", ignoreCase = true) || it.name.contains("True", ignoreCase = true) || it.tags.contains("Whimsical", ignoreCase = true)
        }
      }
      "Popular" -> {
        filtered = filtered.filter {
          it.isTrending || it.category.equals("Popular", ignoreCase = true) || it.isPremium
        }
      }
      "Title" -> {
        filtered = filtered.filter {
          it.category.equals("Title", ignoreCase = true) || it.category.equals("Intro", ignoreCase = true) || it.name.contains("Title", ignoreCase = true)
        }
      }
      "Social" -> {
        filtered = filtered.filter {
          it.category.equals("Social", ignoreCase = true) || it.name.contains("Subscribe", ignoreCase = true) || it.tags.contains("Youtube", ignoreCase = true)
        }
      }
      "Cyberpunk" -> {
        filtered = filtered.filter {
          it.category.equals("Cyberpunk", ignoreCase = true) || it.category.equals("Neon", ignoreCase = true) || it.hasGlow
        }
      }
      "Vlog" -> {
        filtered = filtered.filter {
          it.category.equals("Vlog", ignoreCase = true) || it.category.equals("Minimal", ignoreCase = true) || it.fontFamily.equals("Montserrat", ignoreCase = true)
        }
      }
      "Minimal" -> {
        filtered = filtered.filter {
          it.category.equals("Minimal", ignoreCase = true) || it.fontFamily.equals("Monospace", ignoreCase = true) || it.fontFamily.equals("Sans-Serif", ignoreCase = true)
        }
      }
      "Urdu & Arabic" -> {
        filtered = filtered.filter {
          it.category.equals("Urdu", ignoreCase = true) || it.category.equals("Arabic", ignoreCase = true) || isRtlText(it.sampleText)
        }
      }
      "Recently Used" -> {
        val idOrder = recentTemplateIds.mapIndexed { idx, id -> id to idx }.toMap()
        filtered = filtered.filter { it.id in recentTemplateIds }
          .sortedBy { idOrder[it.id] ?: 999 }
      }
      "All" -> {
        // Show all
      }
      else -> {
        filtered = filtered.filter { it.category.equals(selectedCategory, ignoreCase = true) || it.tags.contains(selectedCategory, ignoreCase = true) }
      }
    }

    if (searchQuery.isNotBlank()) {
      filtered = filtered.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
          it.category.contains(searchQuery, ignoreCase = true) ||
          it.sampleText.contains(searchQuery, ignoreCase = true) ||
          it.tags.contains(searchQuery, ignoreCase = true)
      }
    }
    filtered
  }

  // Action to add / apply template directly to the timeline
  val applyTemplateAction: (TextTemplateItem, String?, Float) -> Unit = { tpl, customTextOverride, speedMult ->
    addRecent(tpl.id)
    val textToApply = if (!customTextOverride.isNullOrBlank()) {
      customTextOverride
    } else if (userText.isNotBlank()) {
      userText
    } else tpl.sampleText

    val effectiveAnimDuration = (tpl.animDurationMs / speedMult).toLong().coerceIn(100L, 6000L)

    if (selectedTextClip != null) {
      viewModel.timelineEngine.updateTextClip(
        selectedTextClip.copy(
          text = textToApply,
          fontFamily = tpl.fontFamily,
          fontSizeSp = tpl.fontSizeSp,
          fontWeight = tpl.fontWeight,
          isItalic = tpl.isItalic,
          isUnderline = tpl.isUnderline,
          isAllCaps = tpl.isAllCaps,
          alignment = tpl.alignment,
          letterSpacing = tpl.letterSpacing,
          lineSpacing = tpl.lineSpacing,
          textColor = tpl.textColor,
          hasGradient = tpl.hasGradient,
          gradientColorStart = tpl.gradientColorStart,
          gradientColorEnd = tpl.gradientColorEnd,
          gradientDirection = tpl.gradientDirection,
          strokeWidth = tpl.strokeWidth,
          strokeColor = tpl.strokeColor,
          hasShadow = tpl.hasShadow,
          shadowColor = tpl.shadowColor,
          shadowBlur = tpl.shadowBlur,
          shadowOffsetX = tpl.shadowOffsetX,
          shadowOffsetY = tpl.shadowOffsetY,
          hasGlow = tpl.hasGlow,
          glowColor = tpl.glowColor,
          glowRadius = tpl.glowRadius,
          hasBackground = tpl.hasBackground,
          backgroundColor = tpl.backgroundColor,
          backgroundShape = tpl.backgroundShape,
          cornerRadius = tpl.cornerRadius,
          bgPadding = tpl.bgPadding,
          opacity = tpl.opacity,
          posX = tpl.posX,
          posY = tpl.posY,
          scale = tpl.scale,
          rotation = tpl.rotation,
          animationType = tpl.animationType,
          animationIn = tpl.animationIn,
          animationOut = tpl.animationOut,
          animDurationMs = effectiveAnimDuration,
          animationDelayMs = tpl.animationDelayMs,
          animationEasing = tpl.animationEasing
        )
      )
      if (tpl.secondaryLayers.isNotEmpty()) {
        val playhead = selectedTextClip.timelineStartMs
        val dur = selectedTextClip.durationMs
        tpl.secondaryLayers.forEach { sec ->
          val newSecClip = sec.toTextClip(playhead, dur).copy(
            animDurationMs = (sec.animDurationMs / speedMult).toLong().coerceIn(100L, 6000L)
          )
          viewModel.timelineEngine.addTextClipObject(newSecClip)
        }
      }
    } else {
      val playhead = viewModel.timelineEngine.currentPositionMs.value
      val clips = tpl.toTextClips(
        timelineStartMs = playhead,
        durationMs = 3000L,
        customTextOverride = textToApply
      )
      clips.forEach { clip ->
        val scaledClip = clip.copy(
          animDurationMs = (clip.animDurationMs / speedMult).toLong().coerceIn(100L, 6000L)
        )
        viewModel.timelineEngine.addTextClipObject(scaledClip)
      }
      if (clips.isNotEmpty()) {
        viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clips.first().id))
      }
    }
  }

  // Create & Add a new text layer (Section 22: + Add Text button)
  val addNewTextAction: () -> Unit = {
    val playhead = viewModel.timelineEngine.currentPositionMs.value
    val newClip = TextClip(
      id = UUID.randomUUID().toString(),
      text = if (userText.isNotBlank()) userText else "Your Text Here",
      timelineStartMs = playhead,
      durationMs = 3000L,
      fontSizeSp = 30f,
      textColor = 0xFFFFFFFF,
      animationType = "Pop"
    )
    viewModel.timelineEngine.addTextClipObject(newClip)
    viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(newClip.id))
  }

  val isRtl = isRtlText(userText)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF141519))
      .padding(bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    // 1. TOP BAR: Input field [ Enter text ⤢ ] + confirm checkmark ✓ (matches screenshot)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .height(40.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF22242B))
          .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          BasicTextField(
            value = userText,
            onValueChange = { newText ->
              userText = newText
              if (selectedTextClip != null) {
                viewModel.timelineEngine.updateTextClip(selectedTextClip.copy(text = newText))
              }
            },
            singleLine = true,
            textStyle = TextStyle(
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Content
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
              if (userText.isEmpty()) {
                Text(
                  "Enter text",
                  color = Color(0xFF7E828E),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Normal
                )
              }
              innerTextField()
            }
          )

          IconButton(
            onClick = { isExpandedTextEditorOpen = true },
            modifier = Modifier.size(24.dp)
          ) {
            Icon(
              imageVector = Icons.Default.OpenInFull,
              contentDescription = "Expand text editor",
              tint = Color(0xFF9E9E9E),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Checkmark button (✓) to confirm & dismiss
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Confirm",
          tint = Color(0xFFE2E4EA),
          modifier = Modifier.size(24.dp)
        )
      }
    }

    // 2. Drag handle bar
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .width(36.dp)
          .height(3.5.dp)
          .clip(CircleShape)
          .background(Color(0xFF383A42))
      )
    }

    // 3. Main Studio Tabs: Templates | Fonts | Styles | Effects | Animations | Bubble
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(TextStudioMainTab.values()) { tab ->
        val isSelected = activeMainTab == tab
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { activeMainTab = tab }
            .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = tab.label,
            fontSize = 14.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF8E929E)
          )
          Spacer(modifier = Modifier.height(4.dp))
          if (isSelected) {
            Box(
              modifier = Modifier
                .width(28.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF00E5FF))
            )
          } else {
            Spacer(modifier = Modifier.height(2.5.dp))
          }
        }
      }
    }

    // -------------------------------------------------------------
    // TAB CONTENTS
    // -------------------------------------------------------------
    when (activeMainTab) {
      TextStudioMainTab.TEMPLATES -> {
        // CapCut Sub-category Navigation row
        val categories = listOf(
          "Trending", "Whimsical", "KATSEYE👠", "Popular", "Title",
          "Social", "Cyberpunk", "Vlog", "Minimal", "Urdu & Arabic"
        )

        if (isSearchActive) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C1E24))
                .padding(horizontal = 10.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                  if (searchQuery.isEmpty()) {
                    Text("Search templates...", color = Color(0xFF6B7280), fontSize = 13.sp)
                  }
                  inner()
                }
              )
            }
            IconButton(
              onClick = {
                searchQuery = ""
                isSearchActive = false
              },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color(0xFF8E929E), modifier = Modifier.size(18.dp))
            }
          }
        } else {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Search icon button
            IconButton(
              onClick = { isSearchActive = true },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF8E929E),
                modifier = Modifier.size(19.dp)
              )
            }

            // Bookmark / Saved icon button
            IconButton(
              onClick = {
                selectedCategory = if (selectedCategory == "Favorites") "Trending" else "Favorites"
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = if (selectedCategory == "Favorites") Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Favorites",
                tint = if (selectedCategory == "Favorites") Color(0xFF00E5FF) else Color(0xFF8E929E),
                modifier = Modifier.size(19.dp)
              )
            }

            // Thin vertical divider line
            Box(
              modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(14.dp)
                .background(Color(0xFF2C2F36))
            )

            Spacer(modifier = Modifier.width(4.dp))

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              items(categories) { cat ->
                val isCatSelected = selectedCategory.equals(cat, ignoreCase = true)
                Text(
                  text = cat,
                  fontSize = 13.sp,
                  fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isCatSelected) Color.White else Color(0xFF8E929E),
                  modifier = Modifier
                    .clickable { selectedCategory = cat }
                    .padding(vertical = 4.dp)
                )
              }
            }
          }
        }

        // 4-Column Template Grid (Exact match to screenshot)
        val gridState = rememberLazyGridState()
        val visibleKeys by remember {
          derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
          }
        }

        if (displayedTemplates.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("No templates found", color = Color(0xFF8E929E), fontSize = 13.sp)
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 260.dp, max = 380.dp)
          ) {
            items(displayedTemplates, key = { it.id }) { tpl ->
              val isCardVisible = visibleKeys.isEmpty() || visibleKeys.contains(tpl.id)
              val isSelected = selectedTemplateId == tpl.id

              CapCutGridTemplateCard(
                tpl = tpl,
                customPreviewText = userText.ifBlank { null },
                isSelected = isSelected,
                isVisible = isCardVisible,
                onCardClick = {
                  selectedTemplateId = tpl.id
                  applyTemplateAction(tpl, userText.ifBlank { null }, 1.0f)
                  addRecent(tpl.id)
                }
              )
            }
          }
        }
      }

      TextStudioMainTab.FONTS -> {
        // Quick Font family selector
        FontsQuickPicker(
          selectedFont = selectedTextClip?.fontFamily ?: "Sans-Serif",
          onSelectFont = { fontName ->
            if (selectedTextClip != null) {
              viewModel.timelineEngine.updateTextClip(selectedTextClip.copy(fontFamily = fontName))
            }
          }
        )
      }

      TextStudioMainTab.STYLES -> {
        // Text styling quick panel
        StylesQuickPicker(
          clip = selectedTextClip,
          onUpdate = { updated -> viewModel.timelineEngine.updateTextClip(updated) }
        )
      }

      TextStudioMainTab.EFFECTS -> {
        // Glow / Shadow / Gradient quick panel
        EffectsQuickPicker(
          clip = selectedTextClip,
          onUpdate = { updated -> viewModel.timelineEngine.updateTextClip(updated) }
        )
      }

      TextStudioMainTab.ANIMATIONS -> {
        // Animation in/out quick panel
        AnimationsQuickPicker(
          clip = selectedTextClip,
          onUpdate = { updated -> viewModel.timelineEngine.updateTextClip(updated) }
        )
      }

      TextStudioMainTab.BUBBLE -> {
        // Speech bubbles & comic tags
        BubbleQuickPicker(
          clip = selectedTextClip,
          onUpdate = { updated -> viewModel.timelineEngine.updateTextClip(updated) }
        )
      }
    }
  }

  // Expanded text editor modal dialog
  if (isExpandedTextEditorOpen) {
    Dialog(onDismissRequest = { isExpandedTextEditorOpen = false }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2026)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2C2F36))
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Edit Text", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            IconButton(onClick = { isExpandedTextEditorOpen = false }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E929E))
            }
          }
          OutlinedTextField(
            value = userText,
            onValueChange = { newText ->
              userText = newText
              if (selectedTextClip != null) {
                viewModel.timelineEngine.updateTextClip(selectedTextClip.copy(text = newText))
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 120.dp, max = 200.dp),
            placeholder = { Text("Enter text...", color = Color(0xFF6B7280)) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00E5FF),
              unfocusedBorderColor = Color(0xFF2C2F36),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = Color(0xFF141519),
              unfocusedContainerColor = Color(0xFF141519)
            ),
            textStyle = TextStyle(
              fontSize = 15.sp,
              color = Color.White,
              textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Content
            )
          )
          Button(
            onClick = { isExpandedTextEditorOpen = false },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF00E5FF),
              contentColor = Color.Black
            )
          ) {
            Text("Done", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  // -------------------------------------------------------------
  // SECTION 12: FULLSCREEN / LARGE TEMPLATE PREVIEW MODAL
  // -------------------------------------------------------------
  inspectingTemplate?.let { tpl ->
    val isFav = tpl.id in favoriteTemplateIds

    Dialog(onDismissRequest = { inspectingTemplate = null }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(6.dp)
          .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Header: Name, Category, Close
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEF4444))
              )
              Text("LIVE PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
              Text("•", color = TextSecondary)
              Text(tpl.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            IconButton(
              onClick = { inspectingTemplate = null },
              modifier = Modifier.size(26.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
          }

          // Large Live Animated Vector Preview Canvas
          LargeLivePreviewCanvas(
            tpl = tpl,
            previewText = inspectingCustomText.ifBlank { tpl.sampleText },
            speedMultiplier = inspectingSpeedMultiplier,
            isPlaying = inspectingIsPlaying,
            replayTrigger = inspectingReplayTrigger,
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
          )

          // SECTION 12: CONTROLS (Play, Pause, Replay) + Speed Multiplier
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              // Play / Pause button
              FilledTonalIconButton(
                onClick = { inspectingIsPlaying = !inspectingIsPlaying },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  if (inspectingIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = if (inspectingIsPlaying) "Pause" else "Play",
                  modifier = Modifier.size(16.dp)
                )
              }

              // Replay button
              FilledTonalIconButton(
                onClick = {
                  inspectingReplayTrigger++
                  inspectingIsPlaying = true
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(Icons.Default.Replay, contentDescription = "Replay", modifier = Modifier.size(16.dp))
              }

              // Favorite toggle
              FilledTonalIconButton(
                onClick = { toggleFavorite(tpl.id) },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (isFav) Color(0xFFF43F5E) else TextSecondary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            // Speed multiplier indicator
            Surface(
              color = AmberAccent.copy(alpha = 0.2f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(0.5.dp, AmberAccent)
            ) {
              Text(
                text = "${String.format(Locale.US, "%.1f", inspectingSpeedMultiplier)}x Speed",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }

          // Quick Speed chips (0.5x, 1x, 1.5x, 2.5x)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val speedOptions = listOf(0.5f to "0.5x Slow", 1.0f to "1.0x Normal", 1.5f to "1.5x Fast", 2.5f to "2.5x Bounce")
            speedOptions.forEach { (sp, label) ->
              val isSel = kotlin.math.abs(inspectingSpeedMultiplier - sp) < 0.08f
              Surface(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { inspectingSpeedMultiplier = sp },
                color = if (isSel) AmberAccent else Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = label,
                  fontSize = 8.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSel) Color.Black else TextSecondary,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          // Edit text field inside modal
          OutlinedTextField(
            value = inspectingCustomText,
            onValueChange = { inspectingCustomText = it },
            label = { Text("Custom Preview Text", fontSize = 9.sp, color = AmberAccent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = AmberAccent,
              unfocusedBorderColor = Color(0xFF1E293B),
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              focusedContainerColor = Color(0xFF131826),
              unfocusedContainerColor = Color(0xFF131826)
            )
          )

          // Meta Info Pills
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
              Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ANIMATION", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(tpl.animationType, fontSize = 9.sp, color = AmberAccent, fontWeight = FontWeight.Bold, maxLines = 1)
              }
            }
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
              Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DURATION", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(tpl.durationBadge, fontSize = 9.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
              }
            }
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
              Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FONT", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(tpl.fontFamily, fontSize = 9.sp, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold, maxLines = 1)
              }
            }
          }

          // Apply Template Button
          Button(
            onClick = {
              applyTemplateAction(tpl, inspectingCustomText.ifBlank { null }, inspectingSpeedMultiplier)
              inspectingTemplate = null
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(42.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Text("Apply Template", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

/**
 * Clean CapCut-Style 4-Column Template Card (Exact match to screenshot)
 * Square aspect ratio, clean dark rounded tile, responsive text preview with template styles, cyan selection border.
 */
@Composable
private fun CapCutGridTemplateCard(
  tpl: TextTemplateItem,
  customPreviewText: String?,
  isSelected: Boolean,
  isVisible: Boolean,
  onCardClick: () -> Unit
) {
  val previewText = (customPreviewText?.takeIf { it.isNotBlank() } ?: tpl.sampleText).take(14)
  val border = if (isSelected) {
    BorderStroke(2.dp, Color(0xFF00E5FF))
  } else {
    BorderStroke(1.dp, Color(0xFF262830))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onCardClick),
    shape = RoundedCornerShape(8.dp),
    border = border,
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) Color(0xFF1E222D) else Color(0xFF1C1E24)
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp),
      contentAlignment = Alignment.Center
    ) {
      // Stylized text preview inside card
      val fontStyle = when (tpl.fontFamily) {
        "Cursive" -> FontFamily.Cursive
        "Monospace" -> FontFamily.Monospace
        "Serif" -> FontFamily.Serif
        else -> FontFamily.Default
      }
      val fontWeight = FontWeight(tpl.fontWeight.coerceIn(100, 900))
      val mainColor = Color(tpl.textColor)

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        Text(
          text = previewText,
          fontSize = 11.sp,
          fontWeight = fontWeight,
          fontFamily = fontStyle,
          color = mainColor,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 13.sp,
          modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = tpl.name.take(12),
          fontSize = 8.5.sp,
          fontWeight = FontWeight.Normal,
          color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF8E929E),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      // Small Pro / Trending badge if applicable
      if (tpl.category == "Trending" || tpl.category == "KATSEYE👠") {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .clip(RoundedCornerShape(bottomStart = 4.dp))
            .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
            .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
          Text("★", fontSize = 7.sp, color = Color(0xFF00E5FF))
        }
      }
    }
  }
}

/**
 * Bubble Quick Picker for Speech Bubbles & Comic Tags
 */
@Composable
private fun BubbleQuickPicker(
  clip: TextClip?,
  onUpdate: (TextClip) -> Unit
) {
  val bubblePresets = listOf(
    "None" to Pair(Color.Transparent, Color.Transparent),
    "Comic Cloud" to Pair(Color(0xFFFFEE58), Color.Black),
    "Speech Oval" to Pair(Color.White, Color.Black),
    "Cyber Tag" to Pair(Color(0xFF00E5FF), Color.Black),
    "Neon Pink" to Pair(Color(0xFFFF007F), Color.White),
    "Dark Card" to Pair(Color(0xFF1E293B), Color.White),
    "Retro Pixel" to Pair(Color(0xFF10B981), Color.Black),
    "Warning Box" to Pair(Color(0xFFF59E0B), Color.Black),
    "Minimal Glass" to Pair(Color(0x33FFFFFF), Color.White)
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      "Text Bubbles & Tags",
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
    )

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(bubblePresets) { (name, colors) ->
        val (bgColor, textColor) = colors
        val isSelected = clip?.hasBackground == true && clip.backgroundColor == bgColor.value.toLong()

        Card(
          modifier = Modifier
            .width(88.dp)
            .height(56.dp)
            .clickable {
              if (clip != null) {
                onUpdate(
                  clip.copy(
                    hasBackground = (name != "None"),
                    backgroundColor = bgColor.value.toLong(),
                    textColor = if (name == "None") 0xFFFFFFFF else textColor.value.toLong(),
                    bgPadding = if (name == "None") 0f else 14f,
                    cornerRadius = if (name == "None") 0f else 10f
                  )
                )
              }
            },
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF00E5FF) else Color(0xFF2C2F36)
          ),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C23))
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            if (name == "None") {
              Icon(Icons.Default.Close, contentDescription = "None", tint = Color(0xFF8E929E), modifier = Modifier.size(20.dp))
            } else {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(bgColor)
                  .padding(horizontal = 6.dp, vertical = 3.dp)
              ) {
                Text(
                  text = name,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = textColor,
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * 2-Column Modern Template Card (Section 5 Design)
 * Displays large animated vector preview, ▶ LIVE badge, Template Name + Heart, Category • Duration
 */
@Composable
private fun ModernGridTemplateCard(
  tpl: TextTemplateItem,
  customPreviewText: String?,
  speedMultiplier: Float,
  isFavorite: Boolean,
  isVisible: Boolean,
  onToggleFavorite: () -> Unit,
  onCardClick: () -> Unit,
  onQuickUse: () -> Unit
) {
  val context = LocalContext.current
  val effectivePreviewText = if (!customPreviewText.isNullOrBlank()) customPreviewText else tpl.sampleText

  val infiniteTransition = rememberInfiniteTransition(label = "card_anim_${tpl.id}_${effectivePreviewText.hashCode()}")
  val animLoopMs by if (isVisible) {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 2400f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "loop_ms"
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }

  val primaryConfig = remember(tpl, effectivePreviewText) {
    tpl.toPrimaryLayerConfig().copy(text = effectivePreviewText)
  }
  val primaryClip = remember(primaryConfig) {
    primaryConfig.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
  }

  val secondaryClips = remember(tpl.secondaryLayers) {
    tpl.secondaryLayers.map { sec ->
      sec.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
    }
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable { onCardClick() },
    colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
    border = BorderStroke(1.dp, if (tpl.isPremium) AmberAccent.copy(alpha = 0.5f) else Color(0xFF1E293B))
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // 1. Large Live Animated Canvas Viewport
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
          .background(Color(0xFF090D16))
          .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val nativeCanvas = drawContext.canvas.nativeCanvas
          val w = size.width.toInt()
          val h = size.height.toInt()
          if (w > 0 && h > 0) {
            val hasMultipleLayers = secondaryClips.isNotEmpty()
            val primaryPosY = if (hasMultipleLayers) -0.20f else 0.0f

            TextLayerRenderer.drawTemplatePreview(
              canvas = nativeCanvas,
              clip = primaryClip,
              previewLoopMs = animLoopMs.toLong(),
              width = w,
              height = h,
              context = context,
              overridePosY = primaryPosY,
              speedMultiplier = speedMultiplier
            )

            if (hasMultipleLayers) {
              secondaryClips.forEachIndexed { idx, secClip ->
                val secPosY = 0.25f + (idx * 0.25f)
                TextLayerRenderer.drawTemplatePreview(
                  canvas = nativeCanvas,
                  clip = secClip,
                  previewLoopMs = animLoopMs.toLong(),
                  width = w,
                  height = h,
                  context = context,
                  overridePosY = secPosY,
                  speedMultiplier = speedMultiplier
                )
              }
            }
          }
        }

        // Top-Left: ▶ LIVE badge
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
            )
            Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
          }
        }

        // Top-Right: HD / PRO badge & Duration
        Row(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          if (tpl.isPremium) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Text("PRO", fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
            }
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0x99000000))
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Text(tpl.durationBadge, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }

      // 2. Card Bottom Bar (Template Name, Favorite, Category • Style)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = tpl.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )

          IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(20.dp)
          ) {
            Icon(
              if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = "Favorite",
              tint = if (isFavorite) Color(0xFFF43F5E) else TextSecondary,
              modifier = Modifier.size(14.dp)
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${tpl.category} • ${tpl.animationType}",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )

          // Quick Use button
          Text(
            text = "+ Use",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = AmberAccent,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { onQuickUse() }
              .padding(horizontal = 4.dp, vertical = 1.dp)
          )
        }
      }
    }
  }
}

/**
 * 4-Column Screenshot Grid Card (CapCut style compact view)
 */
@Composable
private fun GridShowcaseTemplateCard(
  tpl: TextTemplateItem,
  customPreviewText: String?,
  speedMultiplier: Float,
  isFavorite: Boolean,
  isSelected: Boolean,
  isVisible: Boolean,
  onToggleFavorite: () -> Unit,
  onCardClick: () -> Unit,
  onInspectClick: () -> Unit
) {
  val context = LocalContext.current
  val effectivePreviewText = if (!customPreviewText.isNullOrBlank()) customPreviewText else tpl.sampleText

  val infiniteTransition = rememberInfiniteTransition(label = "grid_anim_${tpl.id}_${effectivePreviewText.hashCode()}")
  val animLoopMs by if (isVisible) {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 2400f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "grid_loop_ms"
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }

  val primaryConfig = remember(tpl, effectivePreviewText) {
    tpl.toPrimaryLayerConfig().copy(text = effectivePreviewText)
  }
  val primaryClip = remember(primaryConfig) {
    primaryConfig.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
  }

  val secondaryClips = remember(tpl.secondaryLayers) {
    tpl.secondaryLayers.map { sec ->
      sec.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1.0f)
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF131826))
      .border(
        width = if (isSelected) 2.dp else 0.5.dp,
        color = if (isSelected) AmberAccent else Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp)
      )
      .clickable { onCardClick() },
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val nativeCanvas = drawContext.canvas.nativeCanvas
      val w = size.width.toInt()
      val h = size.height.toInt()
      if (w > 0 && h > 0) {
        val hasMultipleLayers = secondaryClips.isNotEmpty()
        val primaryPosY = if (hasMultipleLayers) -0.20f else 0.0f

        TextLayerRenderer.drawTemplatePreview(
          canvas = nativeCanvas,
          clip = primaryClip,
          previewLoopMs = animLoopMs.toLong(),
          width = w,
          height = h,
          context = context,
          overridePosY = primaryPosY,
          speedMultiplier = speedMultiplier
        )

        if (hasMultipleLayers) {
          secondaryClips.forEachIndexed { idx, secClip ->
            val secPosY = 0.25f + (idx * 0.25f)
            TextLayerRenderer.drawTemplatePreview(
              canvas = nativeCanvas,
              clip = secClip,
              previewLoopMs = animLoopMs.toLong(),
              width = w,
              height = h,
              context = context,
              overridePosY = secPosY,
              speedMultiplier = speedMultiplier
            )
          }
        }
      }
    }

    if (tpl.isPremium) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(3.dp)
          .size(13.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(Color(0xFF00E5FF).copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Diamond, contentDescription = "VIP", tint = Color(0xFF00E5FF), modifier = Modifier.size(9.dp))
      }
    }

    if (isFavorite) {
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(3.dp)
          .size(12.dp)
          .clip(CircleShape)
          .background(Color(0xFFF43F5E)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Favorite, contentDescription = "Fav", tint = Color.White, modifier = Modifier.size(8.dp))
      }
    }
  }
}

/**
 * Vertical Showcase Card (Feed layout)
 */
@Composable
private fun VerticalShowcaseTemplateCard(
  tpl: TextTemplateItem,
  customPreviewText: String?,
  speedMultiplier: Float,
  isFavorite: Boolean,
  isVisible: Boolean,
  onToggleFavorite: () -> Unit,
  onCardClick: () -> Unit,
  onUseTemplate: () -> Unit
) {
  val context = LocalContext.current
  val effectivePreviewText = if (!customPreviewText.isNullOrBlank()) customPreviewText else tpl.sampleText

  val infiniteTransition = rememberInfiniteTransition(label = "feed_anim_${tpl.id}_${effectivePreviewText.hashCode()}")
  val animLoopMs by if (isVisible) {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 2400f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "loop_ms"
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }

  val primaryConfig = remember(tpl, effectivePreviewText) {
    tpl.toPrimaryLayerConfig().copy(text = effectivePreviewText)
  }
  val primaryClip = remember(primaryConfig) {
    primaryConfig.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
  }

  val secondaryClips = remember(tpl.secondaryLayers) {
    tpl.secondaryLayers.map { sec ->
      sec.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
    }
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable { onCardClick() },
    colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
    border = BorderStroke(1.dp, if (tpl.isPremium) AmberAccent.copy(alpha = 0.5f) else Color(0xFF1E293B))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(tpl.badgeEmoji, fontSize = 14.sp)
          Text(tpl.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp))
          Surface(
            color = Color(0xFF6366F1).copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = tpl.animationType,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF818CF8),
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
          }
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
          Icon(
            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Favorite",
            tint = if (isFavorite) Color(0xFFF43F5E) else TextSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF090D16))
          .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val nativeCanvas = drawContext.canvas.nativeCanvas
          val w = size.width.toInt()
          val h = size.height.toInt()
          if (w > 0 && h > 0) {
            val hasMultipleLayers = secondaryClips.isNotEmpty()
            val primaryPosY = if (hasMultipleLayers) -0.20f else 0.0f

            TextLayerRenderer.drawTemplatePreview(
              canvas = nativeCanvas,
              clip = primaryClip,
              previewLoopMs = animLoopMs.toLong(),
              width = w,
              height = h,
              context = context,
              overridePosY = primaryPosY,
              speedMultiplier = speedMultiplier
            )

            if (hasMultipleLayers) {
              secondaryClips.forEachIndexed { idx, secClip ->
                val secPosY = 0.25f + (idx * 0.25f)
                TextLayerRenderer.drawTemplatePreview(
                  canvas = nativeCanvas,
                  clip = secClip,
                  previewLoopMs = animLoopMs.toLong(),
                  width = w,
                  height = h,
                  context = context,
                  overridePosY = secPosY,
                  speedMultiplier = speedMultiplier
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${tpl.category} • ${tpl.durationBadge}",
          style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp)
        )

        Button(
          onClick = onUseTemplate,
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
          modifier = Modifier.height(26.dp)
        ) {
          Text("Use Template", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

/**
 * Large Live Preview Canvas for modal inspection with play/pause and replay controls
 */
@Composable
private fun LargeLivePreviewCanvas(
  tpl: TextTemplateItem,
  previewText: String,
  speedMultiplier: Float = 1.0f,
  isPlaying: Boolean = true,
  replayTrigger: Int = 0,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val infiniteTransition = rememberInfiniteTransition(label = "modal_anim_${tpl.id}_${previewText.hashCode()}_$replayTrigger")
  val animLoopMs by if (isPlaying) {
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 2400f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2400, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      ),
      label = "modal_loop"
    )
  } else {
    remember { mutableFloatStateOf(600f) }
  }

  val primaryConfig = remember(tpl, previewText) {
    tpl.toPrimaryLayerConfig().copy(text = previewText)
  }
  val primaryClip = remember(primaryConfig) {
    primaryConfig.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
  }

  val secondaryClips = remember(tpl.secondaryLayers) {
    tpl.secondaryLayers.map { sec ->
      sec.toTextClip(timelineStartMs = 0L, durationMs = 3000L)
    }
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF070B14))
      .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val nativeCanvas = drawContext.canvas.nativeCanvas
      val w = size.width.toInt()
      val h = size.height.toInt()
      if (w > 0 && h > 0) {
        val hasMultipleLayers = secondaryClips.isNotEmpty()
        val primaryPosY = if (hasMultipleLayers) -0.20f else 0.0f

        TextLayerRenderer.drawTemplatePreview(
          canvas = nativeCanvas,
          clip = primaryClip,
          previewLoopMs = animLoopMs.toLong(),
          width = w,
          height = h,
          context = context,
          overridePosY = primaryPosY,
          speedMultiplier = speedMultiplier
        )

        if (hasMultipleLayers) {
          secondaryClips.forEachIndexed { idx, secClip ->
            val secPosY = 0.25f + (idx * 0.25f)
            TextLayerRenderer.drawTemplatePreview(
              canvas = nativeCanvas,
              clip = secClip,
              previewLoopMs = animLoopMs.toLong(),
              width = w,
              height = h,
              context = context,
              overridePosY = secPosY,
              speedMultiplier = speedMultiplier
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// COMPATIBLE SECONDARY TAB PICKERS (Fonts, Styles, Effects, Animations)
// -------------------------------------------------------------
@Composable
private fun FontsQuickPicker(
  selectedFont: String,
  onSelectFont: (String) -> Unit
) {
  val fonts = listOf(
    "Impact", "Montserrat", "Playfair Display", "Bebas Neue",
    "Pacifico", "Sans-Serif", "Serif", "Monospace",
    "Courier New", "Georgia", "Comic Sans", "Brush Script"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 200.dp, max = 280.dp)
      .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("SELECT FONT FAMILY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(fonts) { font ->
        val isSel = selectedFont.equals(font, ignoreCase = true)
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelectFont(font) },
          color = if (isSel) AmberAccent else Color(0xFF131826),
          border = BorderStroke(1.dp, if (isSel) AmberAccent else Color(0xFF1E293B)),
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(font, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else TextPrimary)
            if (isSel) {
              Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StylesQuickPicker(
  clip: TextClip?,
  onUpdate: (TextClip) -> Unit
) {
  if (clip == null) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
      Text("Add or select a text layer to customize styles", color = TextSecondary, fontSize = 11.sp)
    }
    return
  }

  val colors = listOf(
    0xFFFFFFFF, 0xFFFACC15, 0xFFF43F5E, 0xFF38BDF8, 0xFF4ADE80, 0xFFA855F7, 0xFFFB923C, 0xFF000000
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 200.dp, max = 280.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("TEXT COLOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      colors.forEach { colLong ->
        val isSel = clip.textColor == colLong
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(colLong))
            .border(if (isSel) 2.dp else 1.dp, if (isSel) AmberAccent else Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable { onUpdate(clip.copy(textColor = colLong)) }
        )
      }
    }

    Text("FONT SIZE: ${clip.fontSizeSp.toInt()} sp", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
    Slider(
      value = clip.fontSizeSp,
      onValueChange = { onUpdate(clip.copy(fontSizeSp = it)) },
      valueRange = 12f..72f,
      colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = clip.fontWeight >= 700,
        onClick = { onUpdate(clip.copy(fontWeight = if (clip.fontWeight >= 700) 400 else 800)) },
        label = { Text("Bold", fontSize = 11.sp) }
      )
      FilterChip(
        selected = clip.isItalic,
        onClick = { onUpdate(clip.copy(isItalic = !clip.isItalic)) },
        label = { Text("Italic", fontSize = 11.sp) }
      )
      FilterChip(
        selected = clip.isUnderline,
        onClick = { onUpdate(clip.copy(isUnderline = !clip.isUnderline)) },
        label = { Text("Underline", fontSize = 11.sp) }
      )
    }
  }
}

@Composable
private fun EffectsQuickPicker(
  clip: TextClip?,
  onUpdate: (TextClip) -> Unit
) {
  if (clip == null) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
      Text("Add or select a text layer to customize effects", color = TextSecondary, fontSize = 11.sp)
    }
    return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 200.dp, max = 280.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("SHADOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Switch(
        checked = clip.hasShadow,
        onCheckedChange = { onUpdate(clip.copy(hasShadow = it)) }
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("GLOW ACCENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Switch(
        checked = clip.hasGlow,
        onCheckedChange = { onUpdate(clip.copy(hasGlow = it)) }
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("GRADIENT TEXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Switch(
        checked = clip.hasGradient,
        onCheckedChange = { onUpdate(clip.copy(hasGradient = it)) }
      )
    }
  }
}

@Composable
private fun AnimationsQuickPicker(
  clip: TextClip?,
  onUpdate: (TextClip) -> Unit
) {
  if (clip == null) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
      Text("Add or select a text layer to customize animation", color = TextSecondary, fontSize = 11.sp)
    }
    return
  }

  val animTypes = listOf(
    "Fade In", "Slide Up", "Slide Left", "Slide Right",
    "Zoom In", "Zoom Out", "Bounce", "Typewriter",
    "Word Reveal", "Letter Reveal", "Neon Pulse", "Cinematic Reveal"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 200.dp, max = 280.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("CURRENT ANIMATION: ${clip.animationType}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(animTypes) { animName ->
        val isSel = clip.animationType.equals(animName, ignoreCase = true)
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onUpdate(clip.copy(animationType = animName)) },
          color = if (isSel) AmberAccent else Color(0xFF131826),
          border = BorderStroke(1.dp, if (isSel) AmberAccent else Color(0xFF1E293B)),
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(animName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else TextPrimary)
            if (isSel) {
              Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }
  }
}
