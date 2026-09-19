package com.example.ui.components.text

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TextClip
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.screens.CaptionsToolPanel
import com.example.ui.theme.*
import com.example.util.FontManager
import com.example.util.FontOption
import java.util.UUID

private val StudioAccentBlue = Color(0xFF00C2FF)
private val StudioTextPrimary = Color.White
private val StudioTextSecondary = Color(0xFF94A3B8)

/**
 * Text Panel Mode
 */
enum class TextToolsMode {
  TOOLS_MENU,   // One-row Text Tools: Add to Text, Auto Captions, Draw + Close
  ADD_TEXT,     // Dedicated Add Text window/panel with top input, ✓ check, ❌ close, and categories
  DRAW,         // Embedded Drawing canvas
  AUTO_CAPTIONS // Auto Captions panel
}

/**
 * Text Editing Categories (Horizontally scrollable)
 */
enum class TextCategory(val label: String) {
  TEMPLATES("Templates"),
  FONTS("Fonts"),
  STYLES("Styles"),
  EFFECTS("Effects"),
  ANIMATIONS("Animations"),
  THREE_D("3D Text")
}

/**
 * Font Categories (Horizontally scrollable)
 */
enum class FontCategory(val label: String) {
  MY_FONTS("My Fonts"),
  ALL_FONTS("All Fonts"),
  URDU("Urdu"),
  HINDI("Hindi"),
  CHINESE("Chinese"),
  ENGLISH("English"),
  CLASSIC("Classic"),
  NEW("New"),
  IMPORT("Import")
}

/**
 * Style Sub-Tools
 */
enum class StyleSubTool(val label: String) {
  BRAND_COLOR("Brand Color"),
  STROKE("Stroke"),
  GLOW("Glow"),
  SHADOW("Shadow"),
  CURVE("Curve"),
  SPACING("Spacing"),
  BOLD("Bold"),
  ITALIC("Italic"),
  CASE("Case"),
  BACKGROUND("Background")
}

/**
 * Animation Categories
 */
enum class AnimationCategory(val label: String) {
  IN("In"),
  OUT("Out"),
  LOOP("Loop"),
  THREE_D("3D Motion"),
  SLIDE("Slide"),
  UP_DOWN("Up Down")
}

/**
 * Preset Data Classes
 */
data class ModernTemplateItem(
  val id: String,
  val name: String,
  val previewText: String = "Aa",
  val textColor: Long = 0xFFFFFFFF,
  val hasGradient: Boolean = false,
  val gradientStart: Long = 0xFF00E5FF,
  val gradientEnd: Long = 0xFF8B5CF6,
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val hasGlow: Boolean = false,
  val glowColor: Long = 0xFF00E5FF,
  val hasShadow: Boolean = false,
  val shadowColor: Long = 0x88000000,
  val shadowOffset: Float = 3f,
  val hasBackground: Boolean = false,
  val backgroundColor: Long = 0xFFEF4444,
  val fontFamily: String = "Default",
  val is3D: Boolean = false,
  val depth3D: Float = 0f,
  val effectStyle: String = "none"
)

data class TextEffectItem(
  val id: String,
  val name: String,
  val previewColor: Color,
  val effectStyle: String = "none",
  val textColor: Long = 0xFFFFFFFF,
  val hasGradient: Boolean = false,
  val gradientStart: Long = 0xFF00E5FF,
  val gradientEnd: Long = 0xFF8B5CF6,
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val hasGlow: Boolean = false,
  val glowColor: Long = 0xFF00E5FF,
  val hasShadow: Boolean = false,
  val shadowColor: Long = 0x88000000,
  val shadowOffset: Float = 3f,
  val opacity: Float = 1.0f
)

data class TextAnimationItem(
  val id: String,
  val name: String,
  val animationType: String,
  val animation3D: String = "None",
  val iconEmoji: String = "✨"
)

data class Text3DItem(
  val id: String,
  val name: String,
  val previewFaceColor: Color,
  val previewShadowColor: Color,
  val textColor: Long = 0xFFFFFFFF,
  val is3D: Boolean = true,
  val depth3D: Float = 12f,
  val bevelAngle3D: Float = 0f,
  val color3D: Long = 0xFF1E293B,
  val animation3D: String = "None",
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val hasShadow: Boolean = true,
  val shadowColor: Long = 0xFF000000,
  val shadowOffset: Float = 6f,
  val hasGradient: Boolean = false,
  val gradientStart: Long = 0xFFFCD34D,
  val gradientEnd: Long = 0xFFB45309
)

/**
 * Modern, responsive, unified Text Tools Panel for the mobile video editor.
 */
@Composable
fun ModernTextToolsPanel(
  viewModel: StudioViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

  // Navigation mode inside Text Tools
  var mode by remember { mutableStateOf(TextToolsMode.TOOLS_MENU) }

  // Check if there is an active text layer selected
  val selectedTextClip = remember(selectedElement, timeline.textClips) {
    if (selectedElement is SelectedTrackElement.Text) {
      timeline.textClips.find { it.id == (selectedElement as SelectedTrackElement.Text).clipId }
    } else null
  }

  // Active working text clip state
  var activeClip by remember {
    mutableStateOf(
      selectedTextClip ?: TextClip(
        id = UUID.randomUUID().toString(),
        text = "Add Text",
        timelineStartMs = currentPosMs,
        durationMs = 3000L,
        fontSizeSp = 28f,
        fontWeight = 700,
        textColor = 0xFFFFFFFF,
        animationType = "Pop"
      )
    )
  }

  // Synchronize when selected clip changes
  LaunchedEffect(selectedTextClip) {
    if (selectedTextClip != null) {
      activeClip = selectedTextClip
    }
  }

  // Helper to commit or update active clip to timeline
  fun syncActiveClipToTimeline(clip: TextClip) {
    activeClip = clip
    val exists = timeline.textClips.any { it.id == clip.id }
    if (exists) {
      viewModel.timelineEngine.updateTextClip(clip)
    } else {
      viewModel.timelineEngine.addTextClipObject(clip)
    }
    viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(clip.id))
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(StudioDarkBg)
  ) {
    when (mode) {
      TextToolsMode.TOOLS_MENU -> {
        TextToolsMenuContent(
          onAddText = {
            // If user already has a text selected, edit it; otherwise create new
            val baseClip = selectedTextClip ?: TextClip(
              id = UUID.randomUUID().toString(),
              text = "Add Text",
              timelineStartMs = currentPosMs,
              durationMs = 3000L,
              fontSizeSp = 28f,
              fontWeight = 700,
              textColor = 0xFFFFFFFF,
              animationType = "Pop"
            )
            activeClip = baseClip
            syncActiveClipToTimeline(baseClip)
            mode = TextToolsMode.ADD_TEXT
          },
          onAutoCaptions = { mode = TextToolsMode.AUTO_CAPTIONS },
          onDraw = { mode = TextToolsMode.DRAW },
          onClose = onClose
        )
      }

      TextToolsMode.ADD_TEXT -> {
        AddTextEditorWindow(
          activeClip = activeClip,
          onUpdateClip = { updated ->
            syncActiveClipToTimeline(updated)
          },
          onCommit = {
            syncActiveClipToTimeline(activeClip)
            Toast.makeText(context, "Text layer saved ✓", Toast.LENGTH_SHORT).show()
            mode = TextToolsMode.TOOLS_MENU
          },
          onClose = {
            mode = TextToolsMode.TOOLS_MENU
          }
        )
      }

      TextToolsMode.DRAW -> {
        Column(modifier = Modifier.fillMaxSize()) {
          // Drawing Top Bar with Back/Close
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(StudioSurface)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { mode = TextToolsMode.TOOLS_MENU }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back to Text Tools",
                  tint = StudioTextPrimary
                )
              }
              Text(
                text = "Draw & Doodle",
                style = MaterialTheme.typography.titleMedium,
                color = StudioTextPrimary,
                fontWeight = FontWeight.Bold
              )
            }
            IconButton(onClick = { mode = TextToolsMode.TOOLS_MENU }) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Draw",
                tint = StudioTextSecondary
              )
            }
          }
          Box(modifier = Modifier.fillMaxSize()) {
            DrawToolPanel(
              viewModel = viewModel,
              onDismiss = { mode = TextToolsMode.TOOLS_MENU }
            )
          }
        }
      }

      TextToolsMode.AUTO_CAPTIONS -> {
        Column(modifier = Modifier.fillMaxSize()) {
          // Captions Top Bar with Back/Close
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(StudioSurface)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { mode = TextToolsMode.TOOLS_MENU }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back to Text Tools",
                  tint = StudioTextPrimary
                )
              }
              Text(
                text = "Auto Captions",
                style = MaterialTheme.typography.titleMedium,
                color = StudioTextPrimary,
                fontWeight = FontWeight.Bold
              )
            }
            IconButton(onClick = { mode = TextToolsMode.TOOLS_MENU }) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Captions",
                tint = StudioTextSecondary
              )
            }
          }
          Box(modifier = Modifier.fillMaxSize()) {
            CaptionsToolPanel(viewModel = viewModel)
          }
        }
      }
    }
  }
}

/**
 * 1. Text Tools Bottom Panel (Tools Menu in one row)
 * Show tools: Add to Text (icon), Auto Captions (icon), Draw (icon) + ❌ close icon.
 */
@Composable
private fun TextToolsMenuContent(
  onAddText: () -> Unit,
  onAutoCaptions: () -> Unit,
  onDraw: () -> Unit,
  onClose: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Header with Title & Close Icon
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .background(StudioAccentBlue, CircleShape)
        )
        Text(
          text = "Text Tools",
          style = MaterialTheme.typography.titleMedium,
          color = StudioTextPrimary,
          fontWeight = FontWeight.Bold
        )
      }

      // ❌ Close icon in the panel
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(36.dp)
          .testTag("close_text_tools_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Text Tools",
          tint = StudioTextSecondary
        )
      }
    }

    HorizontalDivider(color = Color(0xFF1E283E), thickness = 1.dp)

    Spacer(modifier = Modifier.height(12.dp))

    // One-row tool options: Add to Text, Auto Captions, Draw
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      TextToolRowButton(
        label = "Add to Text",
        icon = Icons.Default.TextFields,
        accentColor = StudioAccentBlue,
        testTag = "btn_add_to_text",
        onClick = onAddText
      )

      TextToolRowButton(
        label = "Auto Captions",
        icon = Icons.Default.ClosedCaption,
        accentColor = Color(0xFF10B981),
        testTag = "btn_auto_captions",
        onClick = onAutoCaptions
      )

      TextToolRowButton(
        label = "Draw",
        icon = Icons.Default.Brush,
        accentColor = Color(0xFFF59E0B),
        testTag = "btn_text_draw",
        onClick = onDraw
      )
    }

    Spacer(modifier = Modifier.weight(1f))
  }
}

/**
 * Clean round icon + label tool button for the single row
 */
@Composable
private fun TextToolRowButton(
  label: String,
  icon: ImageVector,
  accentColor: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag(testTag)
  ) {
    Box(
      modifier = Modifier
        .size(52.dp)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              accentColor.copy(alpha = 0.25f),
              accentColor.copy(alpha = 0.08f)
            )
          ),
          shape = RoundedCornerShape(14.dp)
        )
        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accentColor,
        modifier = Modifier.size(28.dp)
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = StudioTextPrimary,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * 2. Dedicated Add Text Window / Panel
 * Top text input labeled "Add Text", ❌ on top-left, ✓ on right side.
 * Horizontally scrollable category row: Templates | Fonts | Styles | Effects | Animations | 3D Text
 */
@Composable
private fun AddTextEditorWindow(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit,
  onCommit: () -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  var textInput by remember(activeClip.id) { mutableStateOf(activeClip.text) }
  var activeCategory by remember { mutableStateOf(TextCategory.TEMPLATES) }

  // Sync text input with active clip
  LaunchedEffect(textInput) {
    if (textInput != activeClip.text) {
      onUpdateClip(activeClip.copy(text = textInput))
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(StudioDarkBg)
  ) {
    // TOP INPUT AREA: ❌ Close icon top-left, Text Input labeled "Add Text", ✓ Check icon right
    Surface(
      color = StudioSurface,
      tonalElevation = 4.dp,
      border = BorderStroke(1.dp, Color(0xFF1E283E)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // ❌ Close icon on top-left
        IconButton(
          onClick = {
            focusManager.clearFocus()
            onClose()
          },
          modifier = Modifier
            .size(38.dp)
            .background(Color(0xFF1E293B), CircleShape)
            .testTag("add_text_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Add Text",
            tint = StudioTextSecondary,
            modifier = Modifier.size(20.dp)
          )
        }

        // Text input area labeled "Add Text"
        OutlinedTextField(
          value = textInput,
          onValueChange = { textInput = it },
          label = { Text("Add Text", fontSize = 12.sp) },
          placeholder = { Text("Enter text...", color = StudioTextSecondary) },
          modifier = Modifier
            .weight(1f)
            .testTag("add_text_input_field"),
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioAccentBlue,
            unfocusedBorderColor = Color(0xFF334155),
            focusedContainerColor = Color(0xFF0F172A),
            unfocusedContainerColor = Color(0xFF0F172A),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = StudioAccentBlue,
            unfocusedLabelColor = StudioTextSecondary
          ),
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        // ✓ Check icon on right side (Adds/commits text layer to timeline)
        IconButton(
          onClick = {
            focusManager.clearFocus()
            onCommit()
          },
          modifier = Modifier
            .size(40.dp)
            .background(StudioAccentBlue, CircleShape)
            .testTag("add_text_check_button")
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Confirm & Add Text Layer",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }

    // 3. HORIZONTALLY SCROLLABLE CATEGORY ROW:
    // Templates | Fonts | Styles | Effects | Animations | 3D Text
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0B101B))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(TextCategory.values()) { category ->
        val isSelected = activeCategory == category
        Surface(
          onClick = { activeCategory = category },
          shape = RoundedCornerShape(20.dp),
          color = if (isSelected) StudioAccentBlue else Color(0xFF1E283E),
          border = BorderStroke(
            1.dp,
            if (isSelected) StudioAccentBlue else Color(0xFF2A3752)
          ),
          modifier = Modifier.testTag("cat_${category.name.lowercase()}")
        ) {
          Text(
            text = category.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else StudioTextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
          )
        }
      }
    }

    HorizontalDivider(color = Color(0xFF1E283E), thickness = 1.dp)

    // 4–8. CATEGORY CONTENT VIEWS (Scrollable vertically)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      when (activeCategory) {
        TextCategory.TEMPLATES -> TemplatesCategoryContent(activeClip, onUpdateClip)
        TextCategory.FONTS -> FontsCategoryContent(activeClip, onUpdateClip)
        TextCategory.STYLES -> StylesCategoryContent(activeClip, onUpdateClip)
        TextCategory.EFFECTS -> EffectsCategoryContent(activeClip, onUpdateClip)
        TextCategory.ANIMATIONS -> AnimationsCategoryContent(activeClip, onUpdateClip)
        TextCategory.THREE_D -> ThreeDTextCategoryContent(activeClip, onUpdateClip)
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 4. TEMPLATES CATEGORY (4-column grid, first option None, scrolls vertically)
// ------------------------------------------------------------------------------------------------

private val TEMPLATE_PRESETS = listOf(
  ModernTemplateItem(id = "none", name = "None", previewText = "None"),
  ModernTemplateItem(id = "subscribe", name = "Subscribe", previewText = "SUB", backgroundColor = 0xFFDC2626, hasBackground = true),
  ModernTemplateItem(id = "cyberpunk", name = "Cyberpunk", previewText = "CYBER", textColor = 0xFF00E5FF, strokeWidth = 2f, strokeColor = 0xFFFF007A, hasGlow = true, glowColor = 0xFF00E5FF),
  ModernTemplateItem(id = "gold", name = "Luxury Gold", previewText = "GOLD", hasGradient = true, gradientStart = 0xFFFDE047, gradientEnd = 0xFFB45309, hasShadow = true, shadowColor = 0xAA78350F),
  ModernTemplateItem(id = "neon", name = "Neon Glow", previewText = "NEON", textColor = 0xFF00FF66, hasGlow = true, glowColor = 0xFF10B981, strokeWidth = 1f, strokeColor = 0xFF064E3B),
  ModernTemplateItem(id = "fire", name = "Fire Flame", previewText = "FIRE", hasGradient = true, gradientStart = 0xFFF97316, gradientEnd = 0xFFDC2626, hasGlow = true, glowColor = 0xFFFF5722),
  ModernTemplateItem(id = "retro", name = "Retro 80s", previewText = "RETRO", hasGradient = true, gradientStart = 0xFFEC4899, gradientEnd = 0xFF8B5CF6, strokeWidth = 1.5f, strokeColor = 0xFF3B0764),
  ModernTemplateItem(id = "hologram", name = "Hologram", previewText = "HOLO", textColor = 0xFF22D3EE, hasGlow = true, glowColor = 0xFF06B6D4),
  ModernTemplateItem(id = "urdu_royal", name = "Urdu Royal", previewText = "خوش", textColor = 0xFF38BDF8, fontFamily = "jameel_nastaliq", hasShadow = true),
  ModernTemplateItem(id = "comic", name = "Comic Pop", previewText = "POP", textColor = 0xFFFACC15, strokeWidth = 3f, strokeColor = 0xFF000000, hasShadow = true, shadowColor = 0xFF000000),
  ModernTemplateItem(id = "breaking", name = "News Flash", previewText = "NEWS", hasBackground = true, backgroundColor = 0xFFB91C1C, textColor = 0xFFFFFFFF),
  ModernTemplateItem(id = "tape", name = "Highlight Tape", previewText = "TAPE", hasBackground = true, backgroundColor = 0xFFFACC15, textColor = 0xFF000000),
  ModernTemplateItem(id = "matrix", name = "Matrix Code", previewText = "0101", textColor = 0xFF22C55E, fontFamily = "Monospace"),
  ModernTemplateItem(id = "sunset", name = "Sunset", previewText = "SUN", hasGradient = true, gradientStart = 0xFFE11D48, gradientEnd = 0xFFF97316),
  ModernTemplateItem(id = "bubble", name = "Bubble", previewText = "BUBBLE", hasBackground = true, backgroundColor = 0xFF0284C7, textColor = 0xFFFFFFFF),
  ModernTemplateItem(id = "shadow_heavy", name = "Deep 3D", previewText = "SHADOW", hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 5f)
)

@Composable
private fun TemplatesCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = Modifier
      .fillMaxSize()
      .padding(8.dp)
      .testTag("templates_grid"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(TEMPLATE_PRESETS) { template ->
      val isSelected = when (template.id) {
        "none" -> !activeClip.hasGradient && !activeClip.hasGlow && !activeClip.hasBackground && activeClip.strokeWidth == 0f
        else -> activeClip.textColor == template.textColor && activeClip.hasGradient == template.hasGradient
      }

      Surface(
        onClick = {
          if (template.id == "none") {
            onUpdateClip(
              activeClip.copy(
                hasGradient = false,
                hasGlow = false,
                hasBackground = false,
                strokeWidth = 0f,
                hasShadow = false,
                textColor = 0xFFFFFFFF
              )
            )
          } else {
            onUpdateClip(
              activeClip.copy(
                textColor = template.textColor,
                hasGradient = template.hasGradient,
                gradientColorStart = template.gradientStart,
                gradientColorEnd = template.gradientEnd,
                strokeWidth = template.strokeWidth,
                strokeColor = template.strokeColor,
                hasGlow = template.hasGlow,
                glowColor = template.glowColor,
                hasShadow = template.hasShadow,
                shadowColor = template.shadowColor,
                hasBackground = template.hasBackground,
                backgroundColor = template.backgroundColor,
                fontFamily = if (template.fontFamily != "Default") template.fontFamily else activeClip.fontFamily
              )
            )
          }
        },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161F30),
        border = BorderStroke(
          1.5.dp,
          if (isSelected) StudioAccentBlue else Color(0xFF222F48)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(76.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          // Visual preview card
          Box(
            modifier = Modifier
              .size(width = 46.dp, height = 28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(
                if (template.hasBackground) Color(template.backgroundColor)
                else Color(0xFF0F172A)
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = template.previewText,
              color = Color(template.textColor),
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = template.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) StudioAccentBlue else StudioTextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 5. FONTS CATEGORY (Category row + 3-column grid + Import option)
// ------------------------------------------------------------------------------------------------

private val URDU_FONT_OPTIONS = listOf(
  FontOption("jameel_nastaliq", "Jameel Noori Nastaliq", "Urdu", "جمیل نوری نستعلیق"),
  FontOption("nastaleeq", "Nastaleeq Calligraphy", "Urdu", "اردو خطاطی"),
  FontOption("alvi_nastaliq", "Alvi Nastaleeq", "Urdu", "علوی نستعلیق"),
  FontOption("urdu_naskh", "Urdu Naskh Modern", "Urdu", "نسخ اردو"),
  FontOption("gulzar", "Gulzar Urdu Display", "Urdu", "گلزار اردو"),
  FontOption("scheherazade", "Scheherazade Urdu", "Urdu", "شہربانو اردو"),
  FontOption("lateef", "Lateef Urdu Script", "Urdu", "لطیف اردو"),
  FontOption("kasheeda", "Kasheeda Calligraphy", "Urdu", "کشیدہ خطاطی"),
  FontOption("mehr_nastaliq", "Mehr Nastaliq", "Urdu", "مہر نستعلیق"),
  FontOption("nafees_nastaliq", "Nafees Nastaliq", "Urdu", "نفیس نستعلیق")
)

private val HINDI_FONT_OPTIONS = listOf(
  FontOption("hindi_devanagari_bold", "Hindi Devanagari Bold", "Hindi", "नमस्ते"),
  FontOption("hindi_modern_sans", "Hindi Modern Sans", "Hindi", "आधुनिक"),
  FontOption("hindi_classic_serif", "Hindi Classic Serif", "Hindi", "क्लासिक"),
  FontOption("hindi_calligraphy", "Hindi Calligraphy", "Hindi", "कलात्मक"),
  FontOption("hindi_akshar", "Hindi Akshar", "Hindi", "अक्षर"),
  FontOption("hindi_yatra", "Hindi Yatra", "Hindi", "यात्रा")
)

private val CHINESE_FONT_OPTIONS = listOf(
  FontOption("chinese_sans", "Chinese Sans (黑体)", "Chinese", "你好"),
  FontOption("chinese_serif", "Chinese Songti (宋体)", "Chinese", "经典"),
  FontOption("chinese_kaiti", "Chinese Kaiti (楷体)", "Chinese", "书法"),
  FontOption("chinese_modern", "Chinese Modern (现代)", "Chinese", "字幕"),
  FontOption("chinese_bold", "Chinese Bold (大黑)", "Chinese", "标题")
)

private val ENGLISH_FONT_OPTIONS = listOf(
  FontOption("Impact", "Impact Heavy", "English", "IMPACT"),
  FontOption("Montserrat", "Montserrat Bold", "English", "Montserrat"),
  FontOption("Bebas", "Bebas Neue", "English", "BEBAS"),
  FontOption("Sans-Serif", "Modern Sans", "English", "Modern"),
  FontOption("Serif", "Classic Serif", "English", "Serif"),
  FontOption("Monospace", "Monospace Code", "English", "Mono_01"),
  FontOption("Playfair", "Playfair Display", "English", "Playfair"),
  FontOption("Cinematic", "Cinematic Wide", "English", "CINEMA"),
  FontOption("Cursive", "Pacifico Cursive", "English", "Cursive"),
  FontOption("Futuristic", "Cyber Tech", "English", "CYBER")
)

private val CLASSIC_FONT_OPTIONS = listOf(
  FontOption("Playfair", "Playfair Editorial", "Classic", "Editorial"),
  FontOption("Serif", "Times Classic", "Classic", "Times Serif"),
  FontOption("Cinematic", "Imperial Roman", "Classic", "Imperial"),
  FontOption("Garamond", "Garamond Book", "Classic", "Garamond")
)

private val NEW_FONT_OPTIONS = listOf(
  FontOption("Futuristic", "Cyberpunk 2088", "New", "CYBER 2088"),
  FontOption("Monospace", "Neon Terminal", "New", ">_ Terminal"),
  FontOption("Impact", "Acid Pop Display", "New", "ACID POP"),
  FontOption("Orbitron", "Orbitron SciFi", "New", "ORBITRON")
)

@Composable
private fun FontsCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  val context = LocalContext.current
  var activeFontCategory by remember { mutableStateOf(FontCategory.ALL_FONTS) }
  var customFonts by remember { mutableStateOf(listOf<FontOption>()) }

  // Load existing custom fonts
  LaunchedEffect(Unit) {
    customFonts = FontManager.getAvailableFonts(context).filter { it.isCustom }
  }

  // Font import launcher
  val fontImportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val imported = FontManager.importFont(context, uri)
      if (imported != null) {
        customFonts = customFonts + imported
        activeFontCategory = FontCategory.MY_FONTS
        onUpdateClip(activeClip.copy(fontFamily = imported.id, customFontPath = imported.filePath))
        Toast.makeText(context, "Font ${imported.name} imported!", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, "Failed to import font", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Filter fonts based on category
  val displayFonts = remember(activeFontCategory, customFonts) {
    when (activeFontCategory) {
      FontCategory.MY_FONTS -> customFonts
      FontCategory.ALL_FONTS -> customFonts + URDU_FONT_OPTIONS + HINDI_FONT_OPTIONS + CHINESE_FONT_OPTIONS + ENGLISH_FONT_OPTIONS
      FontCategory.URDU -> URDU_FONT_OPTIONS
      FontCategory.HINDI -> HINDI_FONT_OPTIONS
      FontCategory.CHINESE -> CHINESE_FONT_OPTIONS
      FontCategory.ENGLISH -> ENGLISH_FONT_OPTIONS
      FontCategory.CLASSIC -> CLASSIC_FONT_OPTIONS
      FontCategory.NEW -> NEW_FONT_OPTIONS
      FontCategory.IMPORT -> emptyList()
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Horizontal Font-Category Row: My Fonts | All Fonts | Urdu | English | Classic | New | Import
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(FontCategory.values()) { cat ->
        val isSelected = activeFontCategory == cat
        Surface(
          onClick = {
            if (cat == FontCategory.IMPORT) {
              fontImportLauncher.launch(arrayOf("*/*", "font/*", "application/x-font-ttf", "application/x-font-opentype"))
            } else {
              activeFontCategory = cat
            }
          },
          shape = RoundedCornerShape(16.dp),
          color = if (cat == FontCategory.IMPORT) Color(0xFF065F46) else if (isSelected) StudioAccentBlue else Color(0xFF1E283E),
          border = BorderStroke(
            1.dp,
            if (isSelected) StudioAccentBlue else Color(0xFF2A3752)
          ),
          modifier = Modifier.testTag("font_cat_${cat.name.lowercase()}")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            if (cat == FontCategory.IMPORT) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Import",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            }
            Text(
              text = cat.label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = Color.White
            )
          }
        }
      }
    }

    // 3-Column Font Grid (Scrolls vertically)
    if (displayFonts.isEmpty() && activeFontCategory == FontCategory.MY_FONTS) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "No custom fonts imported yet.",
            color = StudioTextSecondary,
            style = MaterialTheme.typography.bodyMedium
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = {
              fontImportLauncher.launch(arrayOf("*/*", "font/*"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioAccentBlue)
          ) {
            Icon(imageVector = Icons.Default.Upload, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import TTF / OTF Font")
          }
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("fonts_grid"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(displayFonts) { fontOption ->
          val isSelected = activeClip.fontFamily == fontOption.id

          Surface(
            onClick = {
              onUpdateClip(
                activeClip.copy(
                  fontFamily = fontOption.id,
                  customFontPath = fontOption.filePath
                )
              )
            },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(
              1.5.dp,
              if (isSelected) StudioAccentBlue else Color(0xFF222F48)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(72.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = fontOption.nativeSample,
                color = if (isSelected) StudioAccentBlue else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = fontOption.name,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 6. STYLES CATEGORY (Horizontal sub-tools + Circular Color Palette + Size slider + Opacity slider)
// ------------------------------------------------------------------------------------------------

private val COLOR_PALETTE = listOf(
  0xFFFFFFFF, 0xFF000000, 0xFFF43F5E, 0xFFEF4444, 0xFFF97316, 0xFFF59E0B,
  0xFFEAB308, 0xFF84CC16, 0xFF10B981, 0xFF06B6D4, 0xFF0EA5E9, 0xFF3B82F6,
  0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899, 0xFF78716C, 0xFF94A3B8,
  0xFF00E5FF, 0xFFFF007F, 0xFF00FF66, 0xFFFFD700, 0xFFFF5722, 0xFF673AB7
)

@Composable
private fun StylesCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  var selectedSubTool by remember { mutableStateOf(StyleSubTool.BRAND_COLOR) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp)
  ) {
    // Horizontally scrollable row:
    // Brand Color | Stroke | Glow | Shadow | Curve | Spacing | Bold | Italic | Case | Background
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(StyleSubTool.values()) { tool ->
        val isSelected = selectedSubTool == tool
        Surface(
          onClick = {
            selectedSubTool = tool
            // Quick toggles for immediate action items
            when (tool) {
              StyleSubTool.BOLD -> {
                val newWeight = if (activeClip.fontWeight >= 700) 400 else 800
                onUpdateClip(activeClip.copy(fontWeight = newWeight))
              }
              StyleSubTool.ITALIC -> {
                onUpdateClip(activeClip.copy(isItalic = !activeClip.isItalic))
              }
              StyleSubTool.CASE -> {
                onUpdateClip(activeClip.copy(isAllCaps = !activeClip.isAllCaps))
              }
              StyleSubTool.STROKE -> {
                if (activeClip.strokeWidth == 0f) {
                  onUpdateClip(activeClip.copy(strokeWidth = 2f, strokeColor = 0xFF000000))
                }
              }
              StyleSubTool.GLOW -> {
                onUpdateClip(activeClip.copy(hasGlow = !activeClip.hasGlow))
              }
              StyleSubTool.SHADOW -> {
                onUpdateClip(activeClip.copy(hasShadow = !activeClip.hasShadow))
              }
              StyleSubTool.BACKGROUND -> {
                onUpdateClip(activeClip.copy(hasBackground = !activeClip.hasBackground))
              }
              else -> {}
            }
          },
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) StudioAccentBlue else Color(0xFF1E283E),
          border = BorderStroke(
            1.dp,
            if (isSelected) StudioAccentBlue else Color(0xFF2A3752)
          ),
          modifier = Modifier.testTag("style_tool_${tool.name.lowercase()}")
        ) {
          Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else StudioTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // FULL COLOR PALETTE: Circular color buttons displaying actual colors
    Text(
      text = "Color Palette",
      style = MaterialTheme.typography.labelMedium,
      color = StudioTextPrimary,
      fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(COLOR_PALETTE) { colorLong ->
        val isCurrentColor = when (selectedSubTool) {
          StyleSubTool.STROKE -> activeClip.strokeColor == colorLong
          StyleSubTool.GLOW -> activeClip.glowColor == colorLong
          StyleSubTool.SHADOW -> activeClip.shadowColor == colorLong
          StyleSubTool.BACKGROUND -> activeClip.backgroundColor == colorLong
          else -> activeClip.textColor == colorLong
        }

        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(colorLong))
            .border(
              width = if (isCurrentColor) 2.5.dp else 1.dp,
              color = if (isCurrentColor) StudioAccentBlue else Color(0xFF475569),
              shape = CircleShape
            )
            .clickable {
              when (selectedSubTool) {
                StyleSubTool.STROKE -> onUpdateClip(activeClip.copy(strokeColor = colorLong, strokeWidth = maxOf(activeClip.strokeWidth, 2f)))
                StyleSubTool.GLOW -> onUpdateClip(activeClip.copy(glowColor = colorLong, hasGlow = true))
                StyleSubTool.SHADOW -> onUpdateClip(activeClip.copy(shadowColor = colorLong, hasShadow = true))
                StyleSubTool.BACKGROUND -> onUpdateClip(activeClip.copy(backgroundColor = colorLong, hasBackground = true))
                else -> onUpdateClip(activeClip.copy(textColor = colorLong, hasGradient = false))
              }
            },
          contentAlignment = Alignment.Center
        ) {
          if (isCurrentColor) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = if (colorLong == 0xFFFFFFFF) Color.Black else Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // TEXT SIZE SLIDER: 0 to 100 with draggable control dot
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Size",
        style = MaterialTheme.typography.labelMedium,
        color = StudioTextPrimary,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${((activeClip.fontSizeSp - 12f) / 60f * 100f).toInt().coerceIn(0, 100)}",
        style = MaterialTheme.typography.labelSmall,
        color = StudioAccentBlue
      )
    }

    val currentSizeProgress = ((activeClip.fontSizeSp - 12f) / 60f * 100f).coerceIn(0f, 100f)

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("0", color = StudioTextSecondary, fontSize = 11.sp)
      Slider(
        value = currentSizeProgress,
        onValueChange = { progress ->
          val newSp = 12f + (progress / 100f) * 60f
          onUpdateClip(activeClip.copy(fontSizeSp = newSp))
        },
        valueRange = 0f..100f,
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp)
          .testTag("text_size_slider"),
        colors = SliderDefaults.colors(
          thumbColor = StudioAccentBlue,
          activeTrackColor = StudioAccentBlue,
          inactiveTrackColor = Color(0xFF1E283E)
        )
      )
      Text("100", color = StudioTextSecondary, fontSize = 11.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    // OPACITY SLIDER: 0 to 100 with draggable control dot
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Opacity",
        style = MaterialTheme.typography.labelMedium,
        color = StudioTextPrimary,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${(activeClip.opacity * 100f).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = StudioAccentBlue
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("0", color = StudioTextSecondary, fontSize = 11.sp)
      Slider(
        value = (activeClip.opacity * 100f).coerceIn(0f, 100f),
        onValueChange = { progress ->
          onUpdateClip(activeClip.copy(opacity = progress / 100f))
        },
        valueRange = 0f..100f,
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp)
          .testTag("text_opacity_slider"),
        colors = SliderDefaults.colors(
          thumbColor = StudioAccentBlue,
          activeTrackColor = StudioAccentBlue,
          inactiveTrackColor = Color(0xFF1E283E)
        )
      )
      Text("100", color = StudioTextSecondary, fontSize = 11.sp)
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 7. EFFECTS CATEGORY (4-column grid, first option None, visual preview cards)
// ------------------------------------------------------------------------------------------------

private val EFFECT_PRESETS = listOf(
  TextEffectItem(id = "none", name = "None", previewColor = Color.White, effectStyle = "none"),
  TextEffectItem(id = "cyber_glitch", name = "Glitch 3D", previewColor = Color(0xFFFF007A), effectStyle = "glitch", textColor = 0xFF00E5FF, hasShadow = true, shadowColor = 0xFFFF007A),
  TextEffectItem(id = "neon_glow", name = "Neon Bloom", previewColor = Color(0xFF00E5FF), effectStyle = "neon", textColor = 0xFFFFFFFF, hasGlow = true, glowColor = 0xFF00E5FF),
  TextEffectItem(id = "chrome_silver", name = "Chrome Metal", previewColor = Color(0xFFE2E8F0), effectStyle = "chrome", textColor = 0xFFE2E8F0),
  TextEffectItem(id = "fire_blaze", name = "Fire Blaze", previewColor = Color(0xFFF97316), effectStyle = "fire", textColor = 0xFFFFF000, hasGlow = true, glowColor = 0xFFFF5722),
  TextEffectItem(id = "rainbow_prism", name = "Rainbow", previewColor = Color(0xFFFF00FF), effectStyle = "rainbow", textColor = 0xFFFFFFFF),
  TextEffectItem(id = "hologram", name = "Holographic", previewColor = Color(0xFF22D3EE), effectStyle = "holographic", textColor = 0xFF67E8F9, hasGlow = true, glowColor = 0xFF06B6D4),
  TextEffectItem(id = "gold_shimmer", name = "Gold Luxury", previewColor = Color(0xFFFDE047), effectStyle = "gold", textColor = 0xFFFFF7C2, hasShadow = true, shadowColor = 0xAA78350F),
  TextEffectItem(id = "comic_pop", name = "Comic Pop", previewColor = Color(0xFFFACC15), effectStyle = "comic", textColor = 0xFFFACC15, strokeWidth = 3.5f, strokeColor = 0xFF000000),
  TextEffectItem(id = "ice_frost", name = "Ice Frost", previewColor = Color(0xFF38BDF8), textColor = 0xFFE0F2FE, hasGlow = true, glowColor = 0xFF38BDF8),
  TextEffectItem(id = "retro_wave", name = "Retro Wave", previewColor = Color(0xFFEC4899), hasGradient = true, gradientStart = 0xFFEC4899, gradientEnd = 0xFF8B5CF6, hasShadow = true, shadowColor = 0xAA4C1D95),
  TextEffectItem(id = "acid_lime", name = "Acid Lime", previewColor = Color(0xFFCCFF00), textColor = 0xFFCCFF00, strokeWidth = 2f, strokeColor = 0xFF000000),
  TextEffectItem(id = "chrome_silver", name = "Chrome Silver", previewColor = Color(0xFFE2E8F0), hasGradient = true, gradientStart = 0xFFFFFFFF, gradientEnd = 0xFF64748B, strokeWidth = 1.5f, strokeColor = 0xFF0F172A),
  TextEffectItem(id = "comic_pop", name = "Comic Pop", previewColor = Color(0xFFFACC15), textColor = 0xFFFACC15, strokeWidth = 3f, strokeColor = 0xFF000000, hasShadow = true, shadowColor = 0xFF000000),
  TextEffectItem(id = "deep_shadow", name = "Deep Shadow", previewColor = Color(0xFF94A3B8), textColor = 0xFFFFFFFF, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 6f),
  TextEffectItem(id = "sunset", name = "Sunset", previewColor = Color(0xFFE11D48), hasGradient = true, gradientStart = 0xFFE11D48, gradientEnd = 0xFFF97316),
  TextEffectItem(id = "ghost_glow", name = "Ghost Glow", previewColor = Color.White, textColor = 0xFFFFFFFF, hasGlow = true, glowColor = 0xFFFFFFFF),
  TextEffectItem(id = "emerald", name = "Emerald", previewColor = Color(0xFF10B981), hasGradient = true, gradientStart = 0xFF34D399, gradientEnd = 0xFF047857),
  TextEffectItem(id = "blood_moon", name = "Blood Moon", previewColor = Color(0xFFEF4444), textColor = 0xFFEF4444, hasGlow = true, glowColor = 0xFF7F1D1D)
)

@Composable
private fun EffectsCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = Modifier
      .fillMaxSize()
      .padding(8.dp)
      .testTag("effects_grid"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(EFFECT_PRESETS) { effect ->
      val isSelected = when (effect.id) {
        "none" -> activeClip.effectStyle == "none" && !activeClip.hasGradient && !activeClip.hasGlow && activeClip.strokeWidth == 0f && !activeClip.hasShadow
        else -> (activeClip.effectStyle == effect.effectStyle && effect.effectStyle != "none") ||
            (activeClip.textColor == effect.textColor && activeClip.hasGlow == effect.hasGlow && activeClip.hasGradient == effect.hasGradient)
      }

      Surface(
        onClick = {
          if (effect.id == "none") {
            // Completely removes applied text effect
            onUpdateClip(
              activeClip.copy(
                effectStyle = "none",
                hasGradient = false,
                hasGlow = false,
                strokeWidth = 0f,
                hasShadow = false,
                textColor = 0xFFFFFFFF,
                opacity = 1.0f
              )
            )
          } else {
            onUpdateClip(
              activeClip.copy(
                effectStyle = effect.effectStyle,
                textColor = effect.textColor,
                hasGradient = effect.hasGradient,
                gradientColorStart = effect.gradientStart,
                gradientColorEnd = effect.gradientEnd,
                strokeWidth = effect.strokeWidth,
                strokeColor = effect.strokeColor,
                hasGlow = effect.hasGlow,
                glowColor = effect.glowColor,
                hasShadow = effect.hasShadow,
                shadowColor = effect.shadowColor,
                opacity = effect.opacity
              )
            )
          }
        },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161F30),
        border = BorderStroke(
          1.5.dp,
          if (isSelected) StudioAccentBlue else Color(0xFF222F48)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(76.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(
                if (effect.id == "none") Color(0xFF334155)
                else effect.previewColor.copy(alpha = 0.2f)
              )
              .border(
                1.dp,
                if (effect.id == "none") Color(0xFF64748B) else effect.previewColor,
                CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (effect.id == "none") "∅" else "FX",
              color = if (effect.id == "none") Color.White else effect.previewColor,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = effect.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) StudioAccentBlue else StudioTextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 8. ANIMATIONS CATEGORY (In | Out | Loop | Slide | Up Down row + 4-column grid + first None)
// ------------------------------------------------------------------------------------------------

private val ANIM_IN_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "∅"),
  TextAnimationItem("fade_in", "Fade In", "Fade", "🌅"),
  TextAnimationItem("pop_in", "Pop In", "Pop", "💥"),
  TextAnimationItem("zoom_in", "Zoom In", "Zoom", "🔍"),
  TextAnimationItem("slide_up", "Slide Up", "Slide", "⬆️"),
  TextAnimationItem("typewriter", "Typewriter", "Typewriter", "⌨️"),
  TextAnimationItem("bounce_in", "Bounce In", "Bounce", "🏀"),
  TextAnimationItem("flip_in", "Flip In", "Flip", "🔄")
)

private val ANIM_OUT_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "∅"),
  TextAnimationItem("fade_out", "Fade Out", "Fade", "🌇"),
  TextAnimationItem("pop_out", "Pop Out", "Pop", "💨"),
  TextAnimationItem("zoom_out", "Zoom Out", "Zoom", "🔎"),
  TextAnimationItem("slide_down", "Slide Down", "Slide", "⬇️"),
  TextAnimationItem("dissolve", "Dissolve", "Fade", "🌫️")
)

private val ANIM_LOOP_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "∅"),
  TextAnimationItem("pulse", "Pulse", "Pulse", "💓"),
  TextAnimationItem("glow_pulse", "Glow Pulse", "Glow", "✨"),
  TextAnimationItem("shake", "Shake", "Shake", "📳"),
  TextAnimationItem("wave", "Wave", "Wave", "🌊"),
  TextAnimationItem("glitch", "Glitch", "Glitch", "⚡")
)

private val ANIM_SLIDE_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "∅"),
  TextAnimationItem("slide_left", "Slide Left", "Slide", "⬅️"),
  TextAnimationItem("slide_right", "Slide Right", "Slide", "➡️"),
  TextAnimationItem("slide_up", "Slide Up", "Slide", "⬆️"),
  TextAnimationItem("slide_down", "Slide Down", "Slide", "⬇️")
)

private val ANIM_UP_DOWN_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "None", "∅"),
  TextAnimationItem("bounce_up_down", "Bounce Up Down", "Bounce", "None", "↕️"),
  TextAnimationItem("float_up_down", "Float", "Float", "None", "🎈"),
  TextAnimationItem("vertical_wave", "Wave", "Wave", "None", "〰️")
)

private val ANIM_3D_PRESETS = listOf(
  TextAnimationItem("none", "None", "None", "None", "∅"),
  TextAnimationItem("3d_flip", "3D Flip", "3D Flip", "3D Flip", "🔄"),
  TextAnimationItem("3d_spin", "3D Spin", "3D Spin", "3D Spin", "💫"),
  TextAnimationItem("3d_tilt", "3D Tilt", "3D Tilt", "3D Tilt", "📐"),
  TextAnimationItem("3d_float", "3D Float", "3D Float", "3D Float", "🪐"),
  TextAnimationItem("3d_wave", "3D Wave", "3D Wave", "3D Wave", "🌊"),
  TextAnimationItem("3d_extrude", "3D Extrude", "3D Extrude", "3D Extrude", "🧱")
)

@Composable
private fun AnimationsCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  var activeAnimCategory by remember { mutableStateOf(AnimationCategory.IN) }

  val presets = remember(activeAnimCategory) {
    when (activeAnimCategory) {
      AnimationCategory.IN -> ANIM_IN_PRESETS
      AnimationCategory.OUT -> ANIM_OUT_PRESETS
      AnimationCategory.LOOP -> ANIM_LOOP_PRESETS
      AnimationCategory.THREE_D -> ANIM_3D_PRESETS
      AnimationCategory.SLIDE -> ANIM_SLIDE_PRESETS
      AnimationCategory.UP_DOWN -> ANIM_UP_DOWN_PRESETS
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Horizontally scrollable row: In | Out | Loop | 3D Motion | Slide | Up Down
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      items(AnimationCategory.values()) { animCat ->
        val isSelected = activeAnimCategory == animCat
        Surface(
          onClick = { activeAnimCategory = animCat },
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) StudioAccentBlue else Color(0xFF1E283E),
          border = BorderStroke(
            1.dp,
            if (isSelected) StudioAccentBlue else Color(0xFF2A3752)
          ),
          modifier = Modifier.testTag("anim_cat_${animCat.name.lowercase()}")
        ) {
          Text(
            text = animCat.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else StudioTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    // 4-column animation preset grid (First is None)
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
        .testTag("animations_grid"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(presets) { preset ->
        val isSelected = if (preset.animationType == "None" && preset.animation3D == "None") {
          activeClip.animationType == "None" && activeClip.animation3D == "None"
        } else if (preset.animation3D != "None") {
          activeClip.animation3D == preset.animation3D
        } else {
          activeClip.animationType == preset.animationType
        }

        Surface(
          onClick = {
            onUpdateClip(
              activeClip.copy(
                animationType = preset.animationType,
                animationIn = preset.animationType,
                animation3D = preset.animation3D,
                is3D = if (preset.animation3D != "None") true else activeClip.is3D,
                depth3D = if (preset.animation3D != "None" && activeClip.depth3D < 6f) 12f else activeClip.depth3D
              )
            )
          },
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF161F30),
          border = BorderStroke(
            1.5.dp,
            if (isSelected) StudioAccentBlue else Color(0xFF222F48)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = preset.iconEmoji,
              fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = preset.name,
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) StudioAccentBlue else StudioTextSecondary,
              fontSize = 10.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------
// 9. 3D TEXT CATEGORY (4 Columns grid)
// ------------------------------------------------------------------------------------------------

private val THREE_D_PRESETS = listOf(
  Text3DItem("none", "None", Color.White, Color.Transparent, is3D = false, depth3D = 0f, textColor = 0xFFFFFFFF, hasShadow = false),
  Text3DItem("extrude", "3D Extrude", Color.White, Color(0xFF0F172A), is3D = true, depth3D = 14f, color3D = 0xFF0F172A, textColor = 0xFFFFFFFF, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 6f),
  Text3DItem("gold_3d", "Gold 3D", Color(0xFFFDE047), Color(0xFF78350F), is3D = true, depth3D = 16f, bevelAngle3D = 10f, color3D = 0xFF78350F, hasGradient = true, gradientStart = 0xFFFDE047, gradientEnd = 0xFFB45309, hasShadow = true, shadowColor = 0xFF451A03, shadowOffset = 7f),
  Text3DItem("chrome_3d", "Chrome 3D", Color(0xFFE2E8F0), Color(0xFF0F172A), is3D = true, depth3D = 15f, bevelAngle3D = -10f, color3D = 0xFF334155, hasGradient = true, gradientStart = 0xFFFFFFFF, gradientEnd = 0xFF64748B, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 6f),
  Text3DItem("neon_3d", "Neon 3D", Color(0xFF00E5FF), Color(0xFF002244), is3D = true, depth3D = 12f, color3D = 0xFF003366, textColor = 0xFF00E5FF, strokeWidth = 1.5f, strokeColor = 0xFF003366, hasShadow = true, shadowColor = 0xFF001122, shadowOffset = 6f),
  Text3DItem("isometric", "Isometric", Color(0xFF38BDF8), Color(0xFF0C4A6E), is3D = true, depth3D = 20f, bevelAngle3D = 25f, color3D = 0xFF0C4A6E, textColor = 0xFF38BDF8, hasShadow = true, shadowColor = 0xFF082F49, shadowOffset = 8f),
  Text3DItem("bevel", "Bevel 3D", Color(0xFFFACC15), Color(0xFF713F12), is3D = true, depth3D = 12f, bevelAngle3D = -20f, color3D = 0xFF713F12, textColor = 0xFFFACC15, strokeWidth = 2f, strokeColor = 0xFF451A03, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 5f),
  Text3DItem("cyber_3d", "Cyber 3D", Color(0xFFFF007A), Color(0xFF00E5FF), is3D = true, depth3D = 16f, color3D = 0xFF00E5FF, textColor = 0xFFFF007A, hasShadow = true, shadowColor = 0xFF00E5FF, shadowOffset = 6f),
  Text3DItem("red_block", "Red Block", Color(0xFFEF4444), Color(0xFF450A0A), is3D = true, depth3D = 18f, bevelAngle3D = 15f, color3D = 0xFF450A0A, textColor = 0xFFEF4444, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 7f),
  Text3DItem("stone_3d", "Stone 3D", Color(0xFF94A3B8), Color(0xFF1E293B), is3D = true, depth3D = 14f, color3D = 0xFF1E293B, textColor = 0xFFCBD5E1, hasShadow = true, shadowColor = 0xFF0F172A, shadowOffset = 6f),
  Text3DItem("floating", "Floating 3D", Color.White, Color(0x66000000), is3D = true, depth3D = 10f, animation3D = "3D Float", textColor = 0xFFFFFFFF, hasShadow = true, shadowColor = 0x66000000, shadowOffset = 10f),
  Text3DItem("cartoon_3d", "Cartoon 3D", Color(0xFFFDE047), Color(0xFF000000), is3D = true, depth3D = 15f, color3D = 0xFF000000, textColor = 0xFFFDE047, strokeWidth = 3f, strokeColor = 0xFF000000, hasShadow = true, shadowColor = 0xFF000000, shadowOffset = 6f)
)

private val THREE_D_COLOR_SWATCHES = listOf(
  0xFF0F172A to "Slate Dark",
  0xFF000000 to "Pure Black",
  0xFF78350F to "Gold Bronze",
  0xFF003366 to "Deep Navy",
  0xFF450A0A to "Crimson Dark",
  0xFF3B0764 to "Deep Purple",
  0xFF064E3B to "Forest Green",
  0xFF334155 to "Graphite"
)

@Composable
private fun ThreeDTextCategoryContent(
  activeClip: TextClip,
  onUpdateClip: (TextClip) -> Unit
) {
  var active3DTab by remember { mutableStateOf(0) } // 0 = Presets, 1 = Fine-Tune

  Column(modifier = Modifier.fillMaxSize()) {
    // Mode Switch: Presets vs Fine-Tune
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(
        onClick = { active3DTab = 0 },
        shape = RoundedCornerShape(14.dp),
        color = if (active3DTab == 0) StudioAccentBlue else Color(0xFF1E283E),
        modifier = Modifier.weight(1f).height(32.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            "3D Presets",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active3DTab == 0) FontWeight.Bold else FontWeight.Medium,
            color = if (active3DTab == 0) Color.White else StudioTextSecondary
          )
        }
      }

      Surface(
        onClick = { active3DTab = 1 },
        shape = RoundedCornerShape(14.dp),
        color = if (active3DTab == 1) StudioAccentBlue else Color(0xFF1E283E),
        modifier = Modifier.weight(1f).height(32.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            "3D Fine-Tune",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active3DTab == 1) FontWeight.Bold else FontWeight.Medium,
            color = if (active3DTab == 1) Color.White else StudioTextSecondary
          )
        }
      }
    }

    if (active3DTab == 0) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
          .fillMaxSize()
          .padding(8.dp)
          .testTag("three_d_text_grid"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(THREE_D_PRESETS) { preset ->
          val isSelected = if (preset.id == "none") {
            !activeClip.is3D && activeClip.depth3D == 0f
          } else {
            activeClip.is3D && (activeClip.color3D == preset.color3D || activeClip.depth3D == preset.depth3D)
          }

          Surface(
            onClick = {
              if (preset.id == "none") {
                onUpdateClip(
                  activeClip.copy(
                    is3D = false,
                    depth3D = 0f,
                    bevelAngle3D = 0f,
                    animation3D = "None",
                    hasShadow = false,
                    hasGradient = false,
                    strokeWidth = 0f,
                    textColor = 0xFFFFFFFF
                  )
                )
              } else {
                onUpdateClip(
                  activeClip.copy(
                    is3D = true,
                    depth3D = preset.depth3D,
                    bevelAngle3D = preset.bevelAngle3D,
                    color3D = preset.color3D,
                    animation3D = preset.animation3D,
                    textColor = preset.textColor,
                    hasGradient = preset.hasGradient,
                    gradientColorStart = preset.gradientStart,
                    gradientColorEnd = preset.gradientEnd,
                    hasShadow = preset.hasShadow,
                    shadowColor = preset.shadowColor,
                    shadowOffsetX = preset.shadowOffset,
                    shadowOffsetY = preset.shadowOffset,
                    strokeWidth = preset.strokeWidth,
                    strokeColor = preset.strokeColor
                  )
                )
              }
            },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(
              1.5.dp,
              if (isSelected) StudioAccentBlue else Color(0xFF222F48)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(76.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Box(
                modifier = Modifier
                  .size(width = 44.dp, height = 26.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (preset.id == "none") "∅" else "3D",
                  color = preset.previewFaceColor,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 13.sp
                )
              }

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = preset.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) StudioAccentBlue else StudioTextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    } else {
      // 3D Fine-Tune Controls
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Depth Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("3D Depth", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          Text("${activeClip.depth3D.toInt()} px", color = StudioAccentBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
          value = activeClip.depth3D,
          onValueChange = { newDepth ->
            onUpdateClip(activeClip.copy(depth3D = newDepth, is3D = newDepth > 0f))
          },
          valueRange = 0f..30f,
          colors = SliderDefaults.colors(
            thumbColor = StudioAccentBlue,
            activeTrackColor = StudioAccentBlue,
            inactiveTrackColor = Color(0xFF1E283E)
          ),
          modifier = Modifier.fillMaxWidth()
        )

        // Bevel Angle Slider
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("3D Angle / Bevel", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
          Text("${activeClip.bevelAngle3D.toInt()}°", color = StudioAccentBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
          value = activeClip.bevelAngle3D,
          onValueChange = { newAngle ->
            onUpdateClip(activeClip.copy(bevelAngle3D = newAngle, is3D = true))
          },
          valueRange = -45f..45f,
          colors = SliderDefaults.colors(
            thumbColor = StudioAccentBlue,
            activeTrackColor = StudioAccentBlue,
            inactiveTrackColor = Color(0xFF1E283E)
          ),
          modifier = Modifier.fillMaxWidth()
        )

        // 3D Extrusion Color Swatches
        Text("3D Extrusion Color", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          items(THREE_D_COLOR_SWATCHES) { (colorLong, name) ->
            val isSelected = activeClip.color3D == colorLong
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(colorLong))
                .border(
                  width = if (isSelected) 2.5.dp else 1.dp,
                  color = if (isSelected) StudioAccentBlue else Color(0xFF475569),
                  shape = CircleShape
                )
                .clickable {
                  onUpdateClip(activeClip.copy(color3D = colorLong, is3D = true))
                }
            )
          }
        }
      }
    }
  }
}
