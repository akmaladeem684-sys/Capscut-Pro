package com.example.ui.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TrackHeight
import com.example.domain.model.TrackSettings
import com.example.domain.model.TrackType
import com.example.ui.theme.*

fun TrackHeight.toDp(): Dp = when (this) {
  TrackHeight.COMPACT -> 38.dp
  TrackHeight.NORMAL -> 54.dp
  TrackHeight.EXPANDED -> 78.dp
}

@Composable
fun TrackHeaderControl(
  trackType: TrackType,
  settings: TrackSettings,
  onToggleLock: () -> Unit,
  onToggleHide: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleSolo: () -> Unit,
  onCycleHeight: () -> Unit,
  modifier: Modifier = Modifier
) {
  val (label, icon, color) = when (trackType) {
    TrackType.MAIN_VIDEO -> Triple("V1 Main", Icons.Default.Movie, VideoTrackColor)
    TrackType.OVERLAY -> Triple("V2 Overlay", Icons.Default.Layers, OverlayTrackColor)
    TrackType.TEXT -> Triple("T1 Subtitle", Icons.Default.TextFields, TextTrackColor)
    TrackType.AUDIO -> Triple("A1 Master", Icons.Default.Audiotrack, AudioTrackColor)
    TrackType.STICKER -> Triple("S1 Sticker", Icons.Default.EmojiEmotions, StickerTrackColor)
    TrackType.EFFECT -> Triple("FX Filter", Icons.Default.AutoFixHigh, EffectTrackColor)
  }

  val hasAudio = trackType == TrackType.MAIN_VIDEO || trackType == TrackType.OVERLAY || trackType == TrackType.AUDIO
  val hasVisual = trackType != TrackType.AUDIO

  val heightDp = settings.height.toDp()

  Surface(
    modifier = modifier
      .width(108.dp)
      .height(heightDp)
      .border(0.5.dp, StudioBorder, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
    color = if (settings.isLocked) StudioSurface.copy(alpha = 0.6f) else StudioSurfaceVariant,
    tonalElevation = 2.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 4.dp, vertical = 2.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Row 1: Track title + Icon + Height toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(color.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = color,
              modifier = Modifier.size(11.dp)
            )
          }
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = if (settings.isLocked) TextTertiary else TextPrimary
            ),
            maxLines = 1
          )
        }

        // Height Cycle Button
        Box(
          modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable { onCycleHeight() }
            .testTag("track_height_${trackType.name.lowercase()}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when (settings.height) {
              TrackHeight.COMPACT -> Icons.Default.UnfoldMore
              TrackHeight.NORMAL -> Icons.Default.Height
              TrackHeight.EXPANDED -> Icons.Default.UnfoldLess
            },
            contentDescription = "Track Height",
            tint = TextSecondary,
            modifier = Modifier.size(12.dp)
          )
        }
      }

      // Row 2: Control Action Buttons (Lock, Hide/Eye, Mute, Solo)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        // Lock Button
        Box(
          modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (settings.isLocked) RedAccent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable { onToggleLock() }
            .testTag("track_lock_${trackType.name.lowercase()}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (settings.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = "Lock Track",
            tint = if (settings.isLocked) RedAccent else TextTertiary,
            modifier = Modifier.size(12.dp)
          )
        }

        // Hide Button (Eye)
        if (hasVisual) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (settings.isHidden) AmberAccent.copy(alpha = 0.25f) else Color.Transparent)
              .clickable { onToggleHide() }
              .testTag("track_hide_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (settings.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = "Hide Track",
              tint = if (settings.isHidden) AmberAccent else TextSecondary,
              modifier = Modifier.size(12.dp)
            )
          }
        }

        // Mute Button
        if (hasAudio) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (settings.isMuted) RedAccent.copy(alpha = 0.25f) else Color.Transparent)
              .clickable { onToggleMute() }
              .testTag("track_mute_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (settings.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Mute Track",
              tint = if (settings.isMuted) RedAccent else TextSecondary,
              modifier = Modifier.size(12.dp)
            )
          }

          // Solo Button
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (settings.isSolo) CyanAccent.copy(alpha = 0.35f) else Color.Transparent)
              .clickable { onToggleSolo() }
              .testTag("track_solo_${trackType.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "S",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (settings.isSolo) CyanAccent else TextTertiary
              )
            )
          }
        }
      }
    }
  }
}
