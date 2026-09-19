package com.example.ui.components.text

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.navigation.FuturisticBottomNavBarContainer
import com.example.ui.components.navigation.FuturisticNavItemData
import com.example.ui.components.navigation.NavItemThemes

enum class TextSubTool(val label: String, val icon: ImageVector, val tag: String) {
  TEXT_TEMPLATES("Text Template", Icons.Default.AutoAwesome, "subtool_text_templates"),
  TEXT_TO_AUDIO("Text to Audio", Icons.Default.RecordVoiceOver, "subtool_text_to_audio"),
  AUTO_LYRICS("Auto Lyrics", Icons.Default.MusicNote, "subtool_auto_lyrics")
}

@Composable
fun TextToolsSubBar(
  activeSubTool: TextSubTool?,
  onSelectSubTool: (TextSubTool) -> Unit,
  onBackToMainMenu: () -> Unit,
  modifier: Modifier = Modifier
) {
  val navItems = TextSubTool.values().map { subTool ->
    val theme = when (subTool) {
      TextSubTool.TEXT_TEMPLATES -> NavItemThemes.TextTemplateTheme
      TextSubTool.TEXT_TO_AUDIO -> NavItemThemes.Effects
      TextSubTool.AUTO_LYRICS -> NavItemThemes.Filters
    }

    FuturisticNavItemData(
      id = subTool.name,
      label = subTool.label,
      icon = subTool.icon,
      theme = theme,
      isSelected = activeSubTool == subTool,
      testTag = subTool.tag,
      onClick = { onSelectSubTool(subTool) }
    )
  }

  FuturisticBottomNavBarContainer(
    onBackClick = onBackToMainMenu,
    items = navItems,
    modifier = modifier,
    showDividers = true
  )
}
