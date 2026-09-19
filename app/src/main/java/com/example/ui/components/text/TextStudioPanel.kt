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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.TextClip
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.*
import com.example.util.FontManager
import com.example.util.FontOption
import java.util.UUID

enum class TextEditorSecondaryTab(val label: String) {
  TEMPLATES("Templates"),
  FONTS("Fonts"),
  STYLES("Styles"),
  EFFECTS("Effects"),
  ANIMATIONS("Animations"),
  BUBBLES("Bubbles")
}

/**
 * Utility to detect RTL languages like Urdu, Arabic, Persian, Pashto, etc.
 */
fun isRtlScript(text: String): Boolean {
  if (text.isBlank()) return false
  return text.any { char ->
    val block = Character.UnicodeBlock.of(char)
    block == Character.UnicodeBlock.ARABIC ||
      block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
      block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
      block == Character.UnicodeBlock.ARABIC_EXTENDED_B ||
      block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
      block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
      (char >= '\u0600' && char <= '\u06FF') ||
      (char >= '\u0750' && char <= '\u077F') ||
      (char >= '\u08A0' && char <= '\u08FF') ||
      (char >= '\uFB50' && char <= '\uFDFF') ||
      (char >= '\uFE70' && char <= '\uFEFF')
  }
}

/**
 * Visual Template Card Preset Definition
 */
data class VisualTemplatePreset(
  val id: String,
  val name: String,
  val category: String,
  val sampleText: String,
  val fontFamily: String = "Impact",
  val textColor: Long = 0xFFFFFFFF,
  val hasGradient: Boolean = false,
  val gradientColorStart: Long = 0xFF00E5FF,
  val gradientColorEnd: Long = 0xFF8B5CF6,
  val strokeWidth: Float = 0f,
  val strokeColor: Long = 0xFF000000,
  val hasShadow: Boolean = false,
  val shadowColor: Long = 0x88000000,
  val hasGlow: Boolean = false,
  val glowColor: Long = 0xFF00E5FF,
  val hasBackground: Boolean = false,
  val backgroundColor: Long = 0xFF000000,
  val backgroundShape: String = "Rounded",
  val animationType: String = "Pop",
  val isPro: Boolean = false,
  val visualBadgeType: VisualCardType = VisualCardType.DEFAULT
)

enum class VisualCardType {
  DEFAULT,
  SUBSCRIBE_CURSOR,
  THANKS_WATCHING_FRAME,
  EXPLORE_TAPE,
  CLOUD_GLOW,
  TRUE_3D,
  GEOMETRIC_BARS,
  NOOK_BOLD,
  TRENDY_RING_PRO,
  SUBSCRIBE_ARROW_PRO,
  YOUR_TITLE_BRUSH_PRO,
  HIGHLIGHT_NEON,
  W_EPISODE_TAG,
  UNLOCKED_BAR,
  NEW_POST_SCRIPT,
  THANKS_UNDERLINE,
  BEST_HIGHLIGHT,
  SUBSCRIBE_PLAY,
  SHOW_3D_YELLOW,
  YOUTUBE_BELL,
  KATSEYE_GLAM,
  WHIMSICAL_FAIRY,
  CYBERPUNK_GLITCH,
  VLOG_MINIMAL,
  CINEMATIC_GOLD,
  BREAKING_NEWS,
  URDU_CALLIGRAPHY,
  FIRE_FLAME,
  HOLOGRAM_CYAN,
  GLITCH_MATRIX,
  LUXURY_DIAMOND,
  RETRO_80S
}

/**
 * Built-in Visual Template Cards (Matching CapCut and Screenshot image previews)
 */
val VISUAL_CARD_PRESETS = listOf(
  VisualTemplatePreset("default", "Default", "Trending", "Default", fontFamily = "Sans-Serif", visualBadgeType = VisualCardType.DEFAULT),
  VisualTemplatePreset("subscribe_cursor", "Subscribe", "Trending", "Subscribe", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFE50914, visualBadgeType = VisualCardType.SUBSCRIBE_CURSOR),
  VisualTemplatePreset("thanks_watching", "Thanks", "Trending", "THANKS FOR WATCHING", textColor = 0xFFFFFFFF, strokeWidth = 2f, strokeColor = 0xFFEF4444, visualBadgeType = VisualCardType.THANKS_WATCHING_FRAME),
  VisualTemplatePreset("explore_tape", "Explore", "Trending", "EXPLORE", textColor = 0xFF000000, hasBackground = true, backgroundColor = 0xFFFACC15, visualBadgeType = VisualCardType.EXPLORE_TAPE),
  VisualTemplatePreset("cloud_glow", "Cloud", "Trending", "CLOUD", textColor = 0xFFFFFFFF, hasGlow = true, glowColor = 0xFFFFFFFF, visualBadgeType = VisualCardType.CLOUD_GLOW),
  VisualTemplatePreset("true_3d", "True", "Trending", "TRUE", textColor = 0xFFFACC15, strokeWidth = 3f, strokeColor = 0xFF000000, hasShadow = true, shadowColor = 0xFFFF8800, visualBadgeType = VisualCardType.TRUE_3D),
  VisualTemplatePreset("geo_bars", "Minimal", "Whimsical", "||", textColor = 0xFFFFFFFF, visualBadgeType = VisualCardType.GEOMETRIC_BARS),
  VisualTemplatePreset("nook_bold", "Nook", "Whimsical", "NOOK", fontFamily = "Impact", textColor = 0xFFFFFFFF, visualBadgeType = VisualCardType.NOOK_BOLD),
  VisualTemplatePreset("trendy_ring", "Trendy", "KATSEYE", "TRENDY", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFDC2626, isPro = true, visualBadgeType = VisualCardType.TRENDY_RING_PRO),
  VisualTemplatePreset("subscribe_arrow", "Subscribe", "Promo", "SUBSCRIBE", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFDC2626, isPro = true, visualBadgeType = VisualCardType.SUBSCRIBE_ARROW_PRO),
  VisualTemplatePreset("your_title_brush", "Your Title", "Titles", "YOUR TITLE", textColor = 0xFF000000, hasBackground = true, backgroundColor = 0xFFEAB308, isPro = true, visualBadgeType = VisualCardType.YOUR_TITLE_BRUSH_PRO),
  VisualTemplatePreset("highlight_neon", "Highlight", "Promo", "Highlight", textColor = 0xFFFFFFFF, hasGlow = true, glowColor = 0xFF00E5FF, hasBackground = true, backgroundColor = 0xFF1E1B4B, visualBadgeType = VisualCardType.HIGHLIGHT_NEON),
  VisualTemplatePreset("w_episode", "W Episode", "Promo", "W EPISODE", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFDC2626, visualBadgeType = VisualCardType.W_EPISODE_TAG),
  VisualTemplatePreset("unlocked_bar", "Unlocked", "Promo", "UNLOCKED", textColor = 0xFF000000, hasBackground = true, backgroundColor = 0xFFE2E8F0, visualBadgeType = VisualCardType.UNLOCKED_BAR),
  VisualTemplatePreset("new_post", "New Post", "Social", "New Post", fontFamily = "Pacifico", textColor = 0xFFFFFFFF, strokeWidth = 2f, strokeColor = 0xFF000000, visualBadgeType = VisualCardType.NEW_POST_SCRIPT),
  VisualTemplatePreset("thanks_underline", "Thanks", "Social", "THANKS FOR WATCHING", textColor = 0xFFFFFFFF, strokeWidth = 1f, visualBadgeType = VisualCardType.THANKS_UNDERLINE),
  VisualTemplatePreset("best_highlight", "Highlight", "Social", "Best Highlight", textColor = 0xFFFFFFFF, visualBadgeType = VisualCardType.BEST_HIGHLIGHT),
  VisualTemplatePreset("subscribe_play", "Subscribe", "Social", "Subscribe", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFEF4444, visualBadgeType = VisualCardType.SUBSCRIBE_PLAY),
  VisualTemplatePreset("show_3d", "Show", "3D & Neon", "SHOW", textColor = 0xFFFDE047, strokeWidth = 3f, strokeColor = 0xFF991B1B, visualBadgeType = VisualCardType.SHOW_3D_YELLOW),
  VisualTemplatePreset("youtube_bell", "Subscribe", "Social", "SUBSCRIBE", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFDC2626, visualBadgeType = VisualCardType.YOUTUBE_BELL),
  VisualTemplatePreset("katseye_glam", "KATSEYE", "KATSEYE", "KATSEYE", textColor = 0xFFF43F5E, hasGlow = true, glowColor = 0xFFFB7185, isPro = true, visualBadgeType = VisualCardType.KATSEYE_GLAM),
  VisualTemplatePreset("whimsical_fairy", "Whimsical", "Whimsical", "WHIMSICAL", textColor = 0xFFA78BFA, hasGlow = true, glowColor = 0xFFC084FC, visualBadgeType = VisualCardType.WHIMSICAL_FAIRY),
  VisualTemplatePreset("cyberpunk_glitch", "Cyberpunk", "3D & Neon", "CYBERPUNK", textColor = 0xFF00E5FF, strokeWidth = 2f, strokeColor = 0xFFFF007A, visualBadgeType = VisualCardType.CYBERPUNK_GLITCH),
  VisualTemplatePreset("vlog_minimal", "Vlog", "Vlog", "VLOG DAILY", fontFamily = "Playfair", textColor = 0xFFFFFFFF, visualBadgeType = VisualCardType.VLOG_MINIMAL),
  VisualTemplatePreset("cinematic_gold", "Cinematic", "Titles", "CINEMATIC", textColor = 0xFFFCD34D, hasGradient = true, gradientColorStart = 0xFFFCD34D, gradientColorEnd = 0xFFB45309, visualBadgeType = VisualCardType.CINEMATIC_GOLD),
  VisualTemplatePreset("breaking_news", "News", "Titles", "BREAKING NEWS", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xFFB91C1C, visualBadgeType = VisualCardType.BREAKING_NEWS),
  VisualTemplatePreset("urdu_calligraphy", "Urdu", "Urdu", "خوش آمدید", fontFamily = "jameel_nastaliq", textColor = 0xFF38BDF8, hasShadow = true, visualBadgeType = VisualCardType.URDU_CALLIGRAPHY),
  VisualTemplatePreset("fire_flame", "Fire", "3D & Neon", "FIRE HOT", textColor = 0xFFF97316, hasGlow = true, glowColor = 0xFFEF4444, visualBadgeType = VisualCardType.FIRE_FLAME),
  VisualTemplatePreset("hologram_cyan", "Hologram", "3D & Neon", "HOLOGRAM", textColor = 0xFF22D3EE, hasGlow = true, glowColor = 0xFF06B6D4, visualBadgeType = VisualCardType.HOLOGRAM_CYAN),
  VisualTemplatePreset("glitch_matrix", "Matrix", "3D & Neon", "MATRIX", textColor = 0xFF22C55E, fontFamily = "Monospace", visualBadgeType = VisualCardType.GLITCH_MATRIX),
  VisualTemplatePreset("luxury_diamond", "Luxury", "Promo", "LUXURY", textColor = 0xFFE2E8F0, hasShadow = true, isPro = true, visualBadgeType = VisualCardType.LUXURY_DIAMOND),
  VisualTemplatePreset("retro_80s", "Retro 80s", "3D & Neon", "RETRO WAVE", textColor = 0xFFEC4899, hasGradient = true, gradientColorStart = 0xFFEC4899, gradientColorEnd = 0xFF8B5CF6, visualBadgeType = VisualCardType.RETRO_80S)
)

/**
 * Built-in Visual Font Presets
 */
val VISUAL_FONT_PRESETS = listOf(
  FontOption("Impact", "Impact", "Trending", "IMPACT"),
  FontOption("Montserrat", "Montserrat", "Trending", "Montserrat"),
  FontOption("Bebas", "Bebas Neue", "Trending", "BEBAS"),
  FontOption("Sans-Serif", "Sans Clean", "Classic", "Modern Sans"),
  FontOption("Playfair", "Playfair Serif", "Classic", "Playfair"),
  FontOption("Pacifico", "Pacifico Script", "Whimsical", "Pacifico"),
  FontOption("jameel_nastaliq", "Jameel Urdu", "Urdu", "نستعلیق"),
  FontOption("nastaleeq", "Alvi Nastaleeq", "Urdu", "خطاطی"),
  FontOption("Monospace", "Console Code", "New", "MONO_01"),
  FontOption("Serif", "Editorial Serif", "Classic", "Editorial"),
  FontOption("Bangers", "Bangers Comic", "Whimsical", "BANG!"),
  FontOption("Orbitron", "Orbitron SciFi", "New", "ORBITRON")
)

/**
 * Built-in Visual Styles Presets
 */
data class VisualStylePreset(val id: String, val name: String, val textColor: Long, val gradientStart: Long? = null, val gradientEnd: Long? = null, val strokeColor: Long? = null, val glowColor: Long? = null)

val VISUAL_STYLE_PRESETS = listOf(
  VisualStylePreset("white", "Clean White", 0xFFFFFFFF),
  VisualStylePreset("cyan_neon", "Cyber Cyan", 0xFF00E5FF, glowColor = 0xFF00E5FF),
  VisualStylePreset("gold_foil", "Gold Foil", 0xFFFCD34D, gradientStart = 0xFFFCD34D, gradientEnd = 0xFFB45309),
  VisualStylePreset("fire_red", "Fire Red", 0xFFEF4444, gradientStart = 0xFFF97316, gradientEnd = 0xFFEF4444),
  VisualStylePreset("pink_candy", "Pink Candy", 0xFFF43F5E, glowColor = 0xFFFB7185),
  VisualStylePreset("purple_glow", "Purple Glow", 0xFFA855F7, glowColor = 0xFFC084FC),
  VisualStylePreset("green_matrix", "Matrix Green", 0xFF22C55E, glowColor = 0xFF16A34A),
  VisualStylePreset("sunset_amber", "Sunset Amber", 0xFFF59E0B, gradientStart = 0xFFF59E0B, gradientEnd = 0xFFEA580C),
  VisualStylePreset("silver_chrome", "Silver Chrome", 0xFFE2E8F0, strokeColor = 0xFF000000),
  VisualStylePreset("yellow_black", "Yellow Black", 0xFFFACC15, strokeColor = 0xFF000000),
  VisualStylePreset("blue_electric", "Electric Blue", 0xFF3B82F6, glowColor = 0xFF60A5FA),
  VisualStylePreset("rainbow_pop", "Rainbow Pop", 0xFFEC4899, gradientStart = 0xFFEC4899, gradientEnd = 0xFF38BDF8)
)

/**
 * Built-in Visual Effects Presets
 */
data class VisualEffectPreset(val id: String, val name: String, val shadow: Boolean = false, val glow: Boolean = false, val stroke: Boolean = false, val is3d: Boolean = false)

val VISUAL_EFFECT_PRESETS = listOf(
  VisualEffectPreset("none", "None"),
  VisualEffectPreset("drop_shadow", "Soft Shadow", shadow = true),
  VisualEffectPreset("neon_glow", "Neon Glow", glow = true),
  VisualEffectPreset("thick_stroke", "Thick Stroke", stroke = true),
  VisualEffectPreset("3d_depth", "3D Extrude", is3d = true, shadow = true),
  VisualEffectPreset("glow_stroke", "Glow + Stroke", glow = true, stroke = true),
  VisualEffectPreset("cyber_shadow", "Cyber Shadow", shadow = true, glow = true),
  VisualEffectPreset("metal_outline", "Metal Outline", stroke = true, is3d = true)
)

/**
 * Built-in Visual Animations Presets
 */
data class VisualAnimPreset(val id: String, val name: String, val type: String, val iconEmoji: String)

val VISUAL_ANIMATION_PRESETS = listOf(
  VisualAnimPreset("none", "None", "None", "⏹️"),
  VisualAnimPreset("pop", "Pop In", "Pop", "💥"),
  VisualAnimPreset("fade", "Fade In", "Fade", "🌫️"),
  VisualAnimPreset("slide_up", "Slide Up", "Slide", "⬆️"),
  VisualAnimPreset("typewriter", "Typewriter", "Typewriter", "⌨️"),
  VisualAnimPreset("bounce", "Bounce", "Bounce", "🏀"),
  VisualAnimPreset("wave", "Wave", "Wave", "🌊"),
  VisualAnimPreset("flip_3d", "3D Flip", "Flip", "🔄"),
  VisualAnimPreset("zoom_in", "Zoom In", "Zoom", "🔍"),
  VisualAnimPreset("glitch", "Glitch", "Glitch", "⚡"),
  VisualAnimPreset("spin", "Spin In", "Spin", "💫"),
  VisualAnimPreset("pulse", "Pulse", "Pulse", "💓")
)

/**
 * Built-in Visual Bubbles Presets
 */
data class VisualBubblePreset(val id: String, val name: String, val shape: String, val bgColor: Long)

val VISUAL_BUBBLE_PRESETS = listOf(
  VisualBubblePreset("none", "None", "None", 0x00000000),
  VisualBubblePreset("cloud", "Cloud Bubble", "Cloud", 0xFF1E293B),
  VisualBubblePreset("speech_oval", "Speech Oval", "Speech", 0xFF0F172A),
  VisualBubblePreset("comic_shout", "Comic Shout", "Comic", 0xFFDC2626),
  VisualBubblePreset("rounded_box", "Rounded Box", "Rounded", 0xCC000000),
  VisualBubblePreset("tape_yellow", "Tape Banner", "Tape", 0xFFFACC15),
  VisualBubblePreset("cyber_hex", "Cyber Hex", "Cyber", 0xFF0D9488),
  VisualBubblePreset("red_tag", "Red Tag", "Tag", 0xFFB91C1C),
  VisualBubblePreset("ribbon_gold", "Ribbon Gold", "Ribbon", 0xFFCA8A04),
  VisualBubblePreset("pill_neon", "Pill Neon", "Pill", 0xFF4338CA),
  VisualBubblePreset("glass_dark", "Glass Dark", "Glass", 0x881E293B),
  VisualBubblePreset("gradient_bubble", "Gradient", "Gradient", 0xFF7C3AED)
)

/**
 * Lightweight, Clean Text Studio Panel (Matching Screenshot IMG_20260913_154408.jpg)
 */
@Composable
fun TextStudioPanel(
  viewModel: StudioViewModel,
  selectedTextClip: TextClip? = null,
  onDismiss: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  // Manage active text clip state
  val initialClip = remember(selectedTextClip) {
    selectedTextClip ?: TextClip(
      id = UUID.randomUUID().toString(),
      text = "Enter text",
      fontFamily = "Impact",
      fontSizeSp = 32f,
      fontWeight = 800,
      textColor = 0xFFFFFFFF,
      hasShadow = true,
      shadowColor = 0xFF000000,
      shadowBlur = 6f,
      animationType = "Pop",
      animDurationMs = 500L
    )
  }

  var activeClip by remember(initialClip) { mutableStateOf(initialClip) }
  var textInput by remember(activeClip.text) { mutableStateOf(activeClip.text) }

  val onUpdateClip: (TextClip) -> Unit = { updated ->
    activeClip = updated
    if (selectedTextClip != null) {
      viewModel.timelineEngine.updateTextClip(updated)
    }
  }

  var activeTab by remember { mutableStateOf(TextEditorSecondaryTab.TEMPLATES) }
  var selectedCategoryChip by remember { mutableStateOf("Trending") }
  var showSearchField by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showExpandedDialog by remember { mutableStateOf(false) }

  var fontOptionsList by remember { mutableStateOf(FontManager.getAvailableFonts(context)) }

  val fontPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val imported = FontManager.importFont(context, uri)
      if (imported != null) {
        fontOptionsList = FontManager.getAvailableFonts(context)
        onUpdateClip(
          activeClip.copy(fontFamily = imported.id, customFontPath = imported.filePath)
        )
        Toast.makeText(context, "Imported font: ${imported.name}", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, "Failed to load font file", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Auto-apply text change handler
  val applyAndConfirm: () -> Unit = {
    val finalText = if (textInput.isBlank()) "Your Text" else textInput
    val finalClip = activeClip.copy(text = finalText)
    if (selectedTextClip != null) {
      viewModel.timelineEngine.updateTextClip(finalClip)
    } else {
      val currentPos = viewModel.timelineEngine.currentPositionMs.value
      val totalDuration = viewModel.timelineEngine.timeline.value.totalDurationMs.coerceAtLeast(1000L)
      val calculatedDuration = 3000L.coerceAtMost(maxOf(1000L, totalDuration - currentPos))
      val newClip = finalClip.copy(
        id = UUID.randomUUID().toString(),
        timelineStartMs = currentPos,
        durationMs = calculatedDuration
      )
      viewModel.timelineEngine.addTextClipObject(newClip)
      viewModel.timelineEngine.selectElement(SelectedTrackElement.Text(newClip.id))
    }
    focusManager.clearFocus()
    if (onDismiss != null) onDismiss() else viewModel.setActiveToolbarTab(null)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF0F1118))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. TOP DRAG HANDLE
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 2.dp),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .width(36.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(Color.White.copy(alpha = 0.25f))
      )
    }

    // 2. TEXT INPUT ROW: [ Enter text           ⤢ ]   [ ✓ ]
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Input Container with expand icon inside
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1B1F2B),
        border = BorderStroke(1.dp, Color(0xFF2B3245)),
        modifier = Modifier.weight(1f).height(44.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          BasicTextField(
            value = textInput,
            onValueChange = { newText ->
              textInput = newText
              onUpdateClip(activeClip.copy(text = newText))
            },
            singleLine = true,
            textStyle = TextStyle(
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { applyAndConfirm() }),
            cursorBrush = SolidColor(CyanAccent),
            decorationBox = { innerTextField ->
              if (textInput.isEmpty()) {
                Text(
                  text = "Enter text",
                  color = Color.White.copy(alpha = 0.4f),
                  fontSize = 14.sp
                )
              }
              innerTextField()
            },
            modifier = Modifier.weight(1f)
          )

          IconButton(
            onClick = { showExpandedDialog = true },
            modifier = Modifier.size(28.dp)
          ) {
            Icon(
              imageVector = Icons.Default.OpenInFull,
              contentDescription = "Expand Text Editor",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Checkmark Confirm Button
      IconButton(
        onClick = { applyAndConfirm() },
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF1E68F6))
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = "Apply Text",
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
      }
    }

    // 3. TOP HORIZONTAL NAVIGATION TABS: Templates | Fonts | Styles | Effects | Animations | Bubbles
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
      items(TextEditorSecondaryTab.values()) { tab ->
        val isSelected = activeTab == tab
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clickable { activeTab = tab }
            .padding(vertical = 4.dp)
        ) {
          Text(
            text = tab.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
          )
          Spacer(modifier = Modifier.height(4.dp))
          // Cyan underline indicator
          Box(
            modifier = Modifier
              .width(if (isSelected) 28.dp else 0.dp)
              .height(2.5.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(if (isSelected) CyanAccent else Color.Transparent)
          )
        }
      }
    }

    // 4. SUB-CATEGORY FILTER CHIPS (Search, Shield, Bookmark, Trending, Whimsical, KATSEYE, Promo, Social, Titles, etc.)
    val categoryChips = listOf(
      "Trending" to "Trending",
      "Whimsical" to "Whimsical",
      "KATSEYE" to "KATSEYE 👠",
      "Promo" to "Promo",
      "Social" to "Social",
      "Titles" to "Titles",
      "Vlog" to "Vlog",
      "3D & Neon" to "3D & Neon",
      "Urdu" to "Urdu 🇵🇰",
      "All" to "All"
    )

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
      // 1. Search icon chip
      item {
        Surface(
          shape = CircleShape,
          color = if (showSearchField) CyanAccent else Color(0xFF1A1E29),
          border = BorderStroke(1.dp, Color(0xFF2B3142)),
          modifier = Modifier
            .size(30.dp)
            .clickable { showSearchField = !showSearchField }
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "Search",
              tint = if (showSearchField) Color.Black else Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }

      // 2. Shield / Pro Filter Chip
      item {
        Surface(
          shape = CircleShape,
          color = if (selectedCategoryChip == "Pro") CyanAccent else Color(0xFF1A1E29),
          border = BorderStroke(1.dp, Color(0xFF2B3142)),
          modifier = Modifier
            .size(30.dp)
            .clickable { selectedCategoryChip = if (selectedCategoryChip == "Pro") "Trending" else "Pro" }
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Verified,
              contentDescription = "Pro Templates",
              tint = if (selectedCategoryChip == "Pro") Color.Black else Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }

      // 3. Bookmark / Favorites Chip
      item {
        Surface(
          shape = CircleShape,
          color = if (selectedCategoryChip == "Favorites") CyanAccent else Color(0xFF1A1E29),
          border = BorderStroke(1.dp, Color(0xFF2B3142)),
          modifier = Modifier
            .size(30.dp)
            .clickable { selectedCategoryChip = if (selectedCategoryChip == "Favorites") "Trending" else "Favorites" }
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.Bookmark,
              contentDescription = "Saved",
              tint = if (selectedCategoryChip == "Favorites") Color.Black else Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }

      // 4. Text Category Chips
      items(categoryChips) { (catId, catLabel) ->
        val isSelected = selectedCategoryChip == catId
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) Color(0xFF283046) else Color(0xFF161922),
          border = BorderStroke(1.dp, if (isSelected) CyanAccent else Color(0xFF262C3D)),
          modifier = Modifier.clickable { selectedCategoryChip = catId }
        ) {
          Text(
            text = catLabel,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }
    }

    // Quick Search Input if expanded
    if (showSearchField) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF161A24),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().height(36.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
            decorationBox = { inner ->
              if (searchQuery.isEmpty()) Text("Search templates, fonts...", color = Color.Gray, fontSize = 12.sp)
              inner()
            },
            modifier = Modifier.weight(1f)
          )
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }

    // 5. 4-COLUMN VISUAL IMAGE GRID (Replacing heavy text panels with graphical thumbnail cards)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 200.dp, max = 290.dp)
    ) {
      when (activeTab) {
        TextEditorSecondaryTab.TEMPLATES -> {
          VisualTemplatesGrid(
            selectedCategory = selectedCategoryChip,
            searchQuery = searchQuery,
            activeClip = activeClip,
            onSelectPreset = { preset ->
              val textToUse = if (activeClip.text.isBlank() || activeClip.text == "Enter text") preset.sampleText else activeClip.text
              val updated = activeClip.copy(
                text = textToUse,
                fontFamily = preset.fontFamily,
                textColor = preset.textColor,
                hasGradient = preset.hasGradient,
                gradientColorStart = preset.gradientColorStart,
                gradientColorEnd = preset.gradientColorEnd,
                strokeWidth = preset.strokeWidth,
                strokeColor = preset.strokeColor,
                hasShadow = preset.hasShadow,
                shadowColor = preset.shadowColor,
                hasGlow = preset.hasGlow,
                glowColor = preset.glowColor,
                hasBackground = preset.hasBackground,
                backgroundColor = preset.backgroundColor,
                backgroundShape = preset.backgroundShape,
                animationType = preset.animationType
              )
              onUpdateClip(updated)
            }
          )
        }
        TextEditorSecondaryTab.FONTS -> {
          VisualFontsGrid(
            activeClip = activeClip,
            onSelectFont = { fontId, customPath ->
              onUpdateClip(activeClip.copy(fontFamily = fontId, customFontPath = customPath))
            },
            onImportFont = { fontPickerLauncher.launch(arrayOf("*/*")) }
          )
        }
        TextEditorSecondaryTab.STYLES -> {
          VisualStylesGrid(
            activeClip = activeClip,
            onSelectStyle = { style ->
              val updated = activeClip.copy(
                textColor = style.textColor,
                hasGradient = style.gradientStart != null,
                gradientColorStart = style.gradientStart ?: style.textColor,
                gradientColorEnd = style.gradientEnd ?: style.textColor,
                strokeWidth = if (style.strokeColor != null) 3f else 0f,
                strokeColor = style.strokeColor ?: 0xFF000000,
                hasGlow = style.glowColor != null,
                glowColor = style.glowColor ?: 0xFF00E5FF
              )
              onUpdateClip(updated)
            }
          )
        }
        TextEditorSecondaryTab.EFFECTS -> {
          VisualEffectsGrid(
            activeClip = activeClip,
            onSelectEffect = { effect ->
              val updated = activeClip.copy(
                hasShadow = effect.shadow,
                shadowBlur = if (effect.shadow) 8f else 0f,
                hasGlow = effect.glow,
                glowRadius = if (effect.glow) 12f else 0f,
                strokeWidth = if (effect.stroke) 3.5f else 0f
              )
              onUpdateClip(updated)
            }
          )
        }
        TextEditorSecondaryTab.ANIMATIONS -> {
          VisualAnimationsGrid(
            activeClip = activeClip,
            onSelectAnim = { animType ->
              onUpdateClip(activeClip.copy(animationType = animType))
            }
          )
        }
        TextEditorSecondaryTab.BUBBLES -> {
          VisualBubblesGrid(
            activeClip = activeClip,
            onSelectBubble = { bubble ->
              val updated = activeClip.copy(
                hasBackground = bubble.shape != "None",
                backgroundShape = bubble.shape,
                backgroundColor = bubble.bgColor
              )
              onUpdateClip(updated)
            }
          )
        }
      }
    }
  }

  // Expanded multiline text editor modal if opened via ⤢ icon
  if (showExpandedDialog) {
    Dialog(
      onDismissRequest = { showExpandedDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F121C),
        border = BorderStroke(1.dp, Color(0xFF262C3E)),
        modifier = Modifier
          .fillMaxWidth(0.92f)
          .padding(16.dp)
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
            Text("Edit Text Content", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            IconButton(onClick = { showExpandedDialog = false }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
            }
          }

          BasicTextField(
            value = textInput,
            onValueChange = { newText ->
              textInput = newText
              onUpdateClip(activeClip.copy(text = newText))
            },
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(CyanAccent),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 100.dp, max = 160.dp)
              .background(Color(0xFF1A1F2D), RoundedCornerShape(8.dp))
              .border(1.dp, Color(0xFF2C3448), RoundedCornerShape(8.dp))
              .padding(12.dp)
          )

          Button(
            onClick = {
              showExpandedDialog = false
              applyAndConfirm()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E68F6)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Apply Text", fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }
    }
  }
}

/**
 * 4-Column Visual Templates Grid (Rendering visual graphical cards)
 */
@Composable
private fun VisualTemplatesGrid(
  selectedCategory: String,
  searchQuery: String,
  activeClip: TextClip,
  onSelectPreset: (VisualTemplatePreset) -> Unit
) {
  val filteredPresets = remember(selectedCategory, searchQuery) {
    VISUAL_CARD_PRESETS.filter { preset ->
      val matchesCat = when (selectedCategory) {
        "All" -> true
        "Pro" -> preset.isPro
        "Favorites" -> preset.isPro || preset.category == "Trending"
        else -> preset.category.equals(selectedCategory, ignoreCase = true)
      }
      val matchesQuery = if (searchQuery.isBlank()) true else {
        preset.name.contains(searchQuery, ignoreCase = true) ||
          preset.sampleText.contains(searchQuery, ignoreCase = true) ||
          preset.category.contains(searchQuery, ignoreCase = true)
      }
      matchesCat && matchesQuery
    }
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    items(filteredPresets, key = { it.id }) { preset ->
      val isSelected = activeClip.fontFamily == preset.fontFamily && activeClip.textColor == preset.textColor
      VisualThumbnailCard(
        preset = preset,
        isSelected = isSelected,
        onClick = { onSelectPreset(preset) }
      )
    }
  }
}

/**
 * Visual Graphical Thumbnail Card Component (Rendering image/badge preview)
 */
@Composable
private fun VisualThumbnailCard(
  preset: VisualTemplatePreset,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Color(0xFF151821),
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) CyanAccent else Color(0xFF222634)
    ),
    modifier = Modifier
      .aspectRatio(1f)
      .clickable { onClick() }
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      // Pro diamond badge at top right
      if (preset.isPro) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF00E5FF))
        )
      }

      // Graphical Content per card type
      when (preset.visualBadgeType) {
        VisualCardType.DEFAULT -> {
          Text(
            text = "Default",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }
        VisualCardType.SUBSCRIBE_CURSOR -> {
          Box(
            modifier = Modifier
              .padding(4.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFFDC2626))
              .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("Subscribe", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
              Icon(Icons.Default.NearMe, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
            }
          }
        }
        VisualCardType.THANKS_WATCHING_FRAME -> {
          Box(
            modifier = Modifier
              .padding(4.dp)
              .border(1.5.dp, Color(0xFFDC2626), RoundedCornerShape(2.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "THANKS FOR\nWATCHING",
              fontSize = 7.sp,
              fontWeight = FontWeight.Black,
              color = Color.White,
              textAlign = TextAlign.Center,
              lineHeight = 9.sp
            )
          }
        }
        VisualCardType.EXPLORE_TAPE -> {
          Box(
            modifier = Modifier
              .padding(4.dp)
              .background(Color(0xFFFACC15), RoundedCornerShape(2.dp))
              .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "EXPLORE",
              fontSize = 9.sp,
              fontWeight = FontWeight.Black,
              color = Color.Black
            )
          }
        }
        VisualCardType.CLOUD_GLOW -> {
          Box(
            modifier = Modifier
              .padding(4.dp)
              .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
              .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "CLOUD",
              fontSize = 9.sp,
              fontWeight = FontWeight.Black,
              color = Color.White
            )
          }
        }
        VisualCardType.TRUE_3D -> {
          Text(
            text = "TRUE",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFACC15),
            modifier = Modifier.shadow(4.dp)
          )
        }
        VisualCardType.GEOMETRIC_BARS -> {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White))
            Box(modifier = Modifier.width(2.dp).height(14.dp).background(Color.White))
          }
        }
        VisualCardType.NOOK_BOLD -> {
          Text(
            text = "NOOK",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
          )
        }
        VisualCardType.TRENDY_RING_PRO -> {
          Box(
            modifier = Modifier
              .size(36.dp)
              .border(1.5.dp, Color(0xFFEF4444), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Text("TRENDY", fontSize = 6.5.sp, fontWeight = FontWeight.Black, color = Color.White)
          }
        }
        VisualCardType.SUBSCRIBE_ARROW_PRO -> {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
            Box(
              modifier = Modifier
                .background(Color(0xFFDC2626), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text("SUBSCRIBE", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
        VisualCardType.YOUR_TITLE_BRUSH_PRO -> {
          Box(
            modifier = Modifier
              .background(Color(0xFFEAB308), RoundedCornerShape(2.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Text("YOUR TITLE", fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color.Black)
          }
        }
        VisualCardType.HIGHLIGHT_NEON -> {
          Box(
            modifier = Modifier
              .background(
                Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFFEC4899))),
                RoundedCornerShape(3.dp)
              )
              .padding(horizontal = 5.dp, vertical = 2.dp)
          ) {
            Text("Highlight", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
        VisualCardType.W_EPISODE_TAG -> {
          Row(
            modifier = Modifier
              .background(Color(0xFFDC2626), RoundedCornerShape(2.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Text("W EPISODE", fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color.White)
          }
        }
        VisualCardType.UNLOCKED_BAR -> {
          Row(
            modifier = Modifier
              .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text("UNLOCKED", fontSize = 6.5.sp, fontWeight = FontWeight.Black, color = Color.Black)
          }
        }
        VisualCardType.NEW_POST_SCRIPT -> {
          Text(
            text = "New Post",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = Color.White
          )
        }
        VisualCardType.THANKS_UNDERLINE -> {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("THANKS FOR\nWATCHING", fontSize = 6.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            Box(modifier = Modifier.width(24.dp).height(2.dp).background(Color(0xFFDC2626)))
          }
        }
        VisualCardType.BEST_HIGHLIGHT -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(12.dp).background(Color(0xFFDC2626)))
            Spacer(Modifier.width(2.dp))
            Text("Best Highlight", fontSize = 6.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
        VisualCardType.SUBSCRIBE_PLAY -> {
          Row(
            modifier = Modifier
              .background(Color(0xFFDC2626), RoundedCornerShape(3.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
            Text("Subscribe", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
        VisualCardType.SHOW_3D_YELLOW -> {
          Text(
            text = "SHOW",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFACC15)
          )
        }
        VisualCardType.YOUTUBE_BELL -> {
          Row(
            modifier = Modifier
              .background(Color(0xFFDC2626), RoundedCornerShape(3.dp))
              .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
            Spacer(Modifier.width(1.dp))
            Text("SUB", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
        VisualCardType.KATSEYE_GLAM -> {
          Text(
            text = "KATSEYE ✨",
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFF43F5E)
          )
        }
        VisualCardType.WHIMSICAL_FAIRY -> {
          Text(
            text = "WHIMSICAL",
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA78BFA)
          )
        }
        VisualCardType.CYBERPUNK_GLITCH -> {
          Text(
            text = "CYBER",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF00E5FF)
          )
        }
        VisualCardType.VLOG_MINIMAL -> {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VLOG", fontSize = 9.sp, fontWeight = FontWeight.Light, color = Color.White)
            Box(modifier = Modifier.width(16.dp).height(1.dp).background(Color.White.copy(alpha = 0.6f)))
          }
        }
        VisualCardType.CINEMATIC_GOLD -> {
          Text(
            text = "CINEMA",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFCD34D)
          )
        }
        VisualCardType.BREAKING_NEWS -> {
          Box(
            modifier = Modifier
              .background(Color(0xFFB91C1C), RoundedCornerShape(2.dp))
              .padding(horizontal = 3.dp, vertical = 1.dp)
          ) {
            Text("NEWS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
          }
        }
        VisualCardType.URDU_CALLIGRAPHY -> {
          Text(
            text = "خوش آمدید",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8)
          )
        }
        VisualCardType.FIRE_FLAME -> {
          Text(
            text = "FIRE 🔥",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFF97316)
          )
        }
        VisualCardType.HOLOGRAM_CYAN -> {
          Text(
            text = "HOLO",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF22D3EE)
          )
        }
        VisualCardType.GLITCH_MATRIX -> {
          Text(
            text = "MATRIX",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF22C55E)
          )
        }
        VisualCardType.LUXURY_DIAMOND -> {
          Text(
            text = "LUXURY 💎",
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E8F0)
          )
        }
        VisualCardType.RETRO_80S -> {
          Text(
            text = "RETRO",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFEC4899)
          )
        }
      }
    }
  }
}

/**
 * 4-Column Visual Fonts Grid
 */
@Composable
private fun VisualFontsGrid(
  activeClip: TextClip,
  onSelectFont: (String, String?) -> Unit,
  onImportFont: () -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    // Import Font Button
    item {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B2234),
        border = BorderStroke(1.dp, Color(0xFF2B3A5A)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onImportFont() }
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
          Text("Import", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    }

    items(VISUAL_FONT_PRESETS, key = { it.id }) { font ->
      val isSelected = activeClip.fontFamily == font.id
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151821),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanAccent else Color(0xFF222634)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onSelectFont(font.id, null) }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "Aa",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) CyanAccent else Color.White
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = font.name,
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * 4-Column Visual Styles Grid (Color swatches & gradients)
 */
@Composable
private fun VisualStylesGrid(
  activeClip: TextClip,
  onSelectStyle: (VisualStylePreset) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    items(VISUAL_STYLE_PRESETS, key = { it.id }) { style ->
      val isSelected = activeClip.textColor == style.textColor
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151821),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanAccent else Color(0xFF222634)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onSelectStyle(style) }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(
                if (style.gradientStart != null && style.gradientEnd != null) {
                  Brush.horizontalGradient(listOf(Color(style.gradientStart), Color(style.gradientEnd)))
                } else {
                  SolidColor(Color(style.textColor))
                }
              )
              .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = style.name,
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * 4-Column Visual Effects Grid
 */
@Composable
private fun VisualEffectsGrid(
  activeClip: TextClip,
  onSelectEffect: (VisualEffectPreset) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    items(VISUAL_EFFECT_PRESETS, key = { it.id }) { effect ->
      val isSelected = (effect.shadow == activeClip.hasShadow) && (effect.glow == activeClip.hasGlow)
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151821),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanAccent else Color(0xFF222634)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onSelectEffect(effect) }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "FX",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = if (effect.glow) CyanAccent else if (effect.shadow) Color(0xFFFCD34D) else Color.White
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = effect.name,
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * 4-Column Visual Animations Grid
 */
@Composable
private fun VisualAnimationsGrid(
  activeClip: TextClip,
  onSelectAnim: (String) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    items(VISUAL_ANIMATION_PRESETS, key = { it.id }) { anim ->
      val isSelected = activeClip.animationType.equals(anim.type, ignoreCase = true)
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151821),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanAccent else Color(0xFF222634)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onSelectAnim(anim.type) }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = anim.iconEmoji,
            fontSize = 16.sp
          )
          Spacer(Modifier.height(3.dp))
          Text(
            text = anim.name,
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) CyanAccent else Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * 4-Column Visual Bubbles Grid
 */
@Composable
private fun VisualBubblesGrid(
  activeClip: TextClip,
  onSelectBubble: (VisualBubblePreset) -> Unit
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(vertical = 4.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    items(VISUAL_BUBBLE_PRESETS, key = { it.id }) { bubble ->
      val isSelected = activeClip.backgroundShape.equals(bubble.shape, ignoreCase = true)
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF151821),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanAccent else Color(0xFF222634)),
        modifier = Modifier
          .aspectRatio(1f)
          .clickable { onSelectBubble(bubble) }
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (bubble.bgColor != 0x00000000L) Color(bubble.bgColor) else Color.Transparent)
              .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
          ) {
            Text("💬", fontSize = 10.sp)
          }
          Spacer(Modifier.height(4.dp))
          Text(
            text = bubble.name,
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
