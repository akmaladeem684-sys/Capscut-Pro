package com.example.ui.components.text

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SelectedTrackElement
import com.example.ui.StudioViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StudioDark
import kotlin.math.sin

object DynamicTextTemplatesCatalog {
  val categories = listOf("All", "Trending", "Titles", "Social", "Lower Thirds", "Minimal", "Glow", "Neon")

  val templates = listOf(
    TextTemplateItem(id = "t_pop_trend", name = "Trending Pop", category = "Trending", sampleText = "TRENDING", fontFamily = "Default", animationType = "POP_IN", textColor = 0xFF00E5FF, fontSizeSp = 28f),
    TextTemplateItem(id = "t_pulse_beat", name = "Pulse Beat", category = "Trending", sampleText = "DROP THE BEAT", fontFamily = "Default", animationType = "PULSE", textColor = 0xFFFF0055, fontSizeSp = 26f),
    TextTemplateItem(id = "t_fade_cinema", name = "Cinematic Title", category = "Titles", sampleText = "CHAPTER ONE", fontFamily = "Default", animationType = "FADE_IN", textColor = 0xFFFFFFFF, fontSizeSp = 22f),
    TextTemplateItem(id = "t_slide_lower", name = "Lower Third", category = "Lower Thirds", sampleText = "ALEX RIVERA", fontFamily = "Default", animationType = "SLIDE_UP", textColor = 0xFFFFFFFF, hasBackground = true, backgroundColor = 0xCC111827, fontSizeSp = 18f),
    TextTemplateItem(id = "t_neon_glow", name = "Neon Glow", category = "Glow", sampleText = "CYBER VIBE", fontFamily = "Default", animationType = "GLOW", textColor = 0xFF00FFCC, hasGlow = true, glowColor = 0xFF00E5FF, fontSizeSp = 26f),
    TextTemplateItem(id = "t_social_follow", name = "Subscribe Now", category = "Social", sampleText = "SUBSCRIBE 🔔", fontFamily = "Default", animationType = "POP_IN", textColor = 0xFFFF3366, fontSizeSp = 20f, hasBackground = true, backgroundColor = 0xDD000000),
    TextTemplateItem(id = "t_bounce_action", name = "Action Hero", category = "Trending", sampleText = "LEVEL UP!", fontFamily = "Default", animationType = "BOUNCE", textColor = 0xFFFFD700, fontSizeSp = 30f),
    TextTemplateItem(id = "t_minimal_bold", name = "Minimal Bold", category = "Minimal", sampleText = "LESS IS MORE", fontFamily = "Default", animationType = "FADE_IN", textColor = 0xFFFFFFFF, fontSizeSp = 22f),
    TextTemplateItem(id = "t_neon_pink", name = "Neon Pink", category = "Neon", sampleText = "NIGHT DRIVE", fontFamily = "Default", animationType = "GLOW", textColor = 0xFFFF007F, hasGlow = true, glowColor = 0xFFFF007F, fontSizeSp = 24f)
  )
}

@Composable
fun AnimatedTemplatePreviewCard(
  template: TextTemplateItem,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "TemplateAnim")
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "TemplateProgress"
  )

  val scale = when (template.animationType) {
    "POP_IN" -> (0.5f + (progress * 0.5f)).coerceIn(0.5f, 1f)
    "PULSE" -> 1f + (sin(progress * Math.PI.toFloat() * 2f) * 0.1f)
    "BOUNCE" -> 0.85f + (sin(progress * Math.PI.toFloat() * 3f) * 0.15f)
    else -> 1f
  }
  val alpha = when (template.animationType) {
    "FADE_IN" -> progress.coerceIn(0.2f, 1f)
    else -> 1f
  }
  val offsetY = when (template.animationType) {
    "SLIDE_UP" -> (1f - progress) * 16f
    else -> 0f
  }

  Card(
    modifier = Modifier
      .size(width = 110.dp, height = 75.dp)
      .clickable { onClick() }
      .testTag("text_template_card_${template.id}")
      .then(
        if (isSelected) Modifier.border(2.dp, CyanAccent, RoundedCornerShape(8.dp))
        else Modifier.border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
      ),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = template.name,
        color = Color(template.textColor),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            translationY = offsetY
          }
      )
    }
  }
}

@Composable
fun TextTemplatesBrowserPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedCategory by remember { mutableStateOf("All") }
  var selectedTemplateId by remember { mutableStateOf<String?>(null) }

  val filteredTemplates = remember(selectedCategory) {
    if (selectedCategory == "All") DynamicTextTemplatesCatalog.templates
    else DynamicTextTemplatesCatalog.templates.filter { it.category == selectedCategory }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 180.dp, max = 240.dp)
      .background(StudioDark)
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    // Header Row with Title & Close Button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Title,
          contentDescription = null,
          tint = CyanAccent,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = "Text Templates",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(Color(0xFF222B38))
          .testTag("close_text_templates_panel")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Category Chips Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      DynamicTextTemplatesCatalog.categories.forEach { category ->
        val isCatSelected = category == selectedCategory
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isCatSelected) CyanAccent else Color(0xFF1E283E),
          modifier = Modifier
            .height(28.dp)
            .clickable { selectedCategory = category }
            .testTag("template_cat_$category")
        ) {
          Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = category,
              color = if (isCatSelected) Color.Black else Color.White,
              fontSize = 11.sp,
              fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }
    }

    // 2-Row Horizontal Scroll Grid of Animated Preview Cards
    LazyHorizontalGrid(
      rows = GridCells.Fixed(2),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(filteredTemplates, key = { it.id }) { template ->
        AnimatedTemplatePreviewCard(
          template = template,
          isSelected = selectedTemplateId == template.id,
          onClick = {
            selectedTemplateId = template.id
            val currentSelected = viewModel.timelineEngine.selectedElement.value
            if (currentSelected is SelectedTrackElement.Text) {
              viewModel.timelineEngine.updateTextClipPayload(
                clipId = currentSelected.clipId,
                newText = template.sampleText,
                fontFamily = template.fontFamily,
                textColor = template.textColor,
                fontSizeSp = template.fontSizeSp,
                animation = template.animationType
              )
            } else {
              viewModel.timelineEngine.addTextClip(
                text = template.sampleText,
                fontFamily = template.fontFamily,
                fontSizeSp = template.fontSizeSp,
                textColor = template.textColor,
                animationType = template.animationType,
                hasGlow = template.hasGlow,
                glowColor = template.glowColor
              )
            }
          }
        )
      }
    }
  }
}
