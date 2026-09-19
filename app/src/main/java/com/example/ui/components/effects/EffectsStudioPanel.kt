package com.example.ui.components.effects

import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EffectClip
import com.example.domain.model.EffectType
import com.example.engine.SelectedTrackElement
import com.example.engine.composition.VideoEffectRenderer
import com.example.ui.StudioViewModel
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Global holder for Before/After compare preview toggle state.
 * When true, active visual effects are bypassed during rendering.
 */
var isBeforeAfterComparing by mutableStateOf(false)

/**
 * Navigation state for the Effects Studio system.
 */
enum class EffectsViewType {
  MAIN_HUB,
  VIDEO_EFFECTS,
  BODY_EFFECTS,
  PHOTO_EFFECTS,
  AI_EFFECTS
}

/**
 * Representation of each effect in the library catalog.
 */
data class EffectItem(
  val id: String,
  val name: String,
  val effectType: EffectType?, // null represents "None / Original"
  val category: String = "All",
  val tag: String = "", // "PRO", "AI", "HOT", "NEW", ""
  val accentColor: Color = Color(0xFF00C2FF),
  val defaultIntensity: Float = 0.8f
)

/**
 * Catalogs for each of the 4 dedicated effect categories.
 */
object EffectsCatalog {

  // 1. VIDEO EFFECTS
  val VIDEO_EFFECTS_CATEGORIES = listOf("All", "Trending / Viral", "Motions", "Light", "Texture", "Celebrate", "Party", "Glitch & Distortion", "Retro / Vintage", "Light / Glow Effects", "Blur / Focus Effects", "Color & Filter Effects", "Split / Mirror / 3D", "Transitions")

  val VIDEO_EFFECTS: List<EffectItem> = listOf(
    EffectItem("ve_none", "None", null, "All", "", Color(0xFF64748B), 0.0f),
    
    // Motions
    EffectItem("ve_shake", "Camera Shake", EffectType.SHAKE, "Motions", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_zoom", "Zoom Pulse", EffectType.ZOOM, "Motions", "HOT", Color(0xFF8B5CF6), 0.8f),
    EffectItem("ve_skater", "Skater Zoom", EffectType.SKATER_ZOOM, "Motions", "PRO", Color(0xFF06B6D4), 0.85f),
    EffectItem("ve_vertigo", "Vertigo Dolly", EffectType.VERTIGO_DOLLY, "Motions", "PRO", Color(0xFFA855F7), 0.8f),
    EffectItem("ve_spin", "360 Spin", EffectType.SPIN, "Motions", "", Color(0xFF3B82F6), 0.75f),
    EffectItem("ve_wander", "Wander Pan", EffectType.CAMERA_MOVEMENT, "Motions", "", Color(0xFF10B981), 0.8f),
    EffectItem("ve_warp", "Warp Speed", EffectType.WARP_SPEED, "Motions", "PRO", Color(0xFFEC4899), 0.9f),
    EffectItem("ve_ripple", "Shockwave", EffectType.RIPPLE, "Motions", "", Color(0xFFF43F5E), 0.85f),
    EffectItem("ve_wave", "Wave Ripple", EffectType.WAVE, "Motions", "", Color(0xFF00C2FF), 0.8f),

    // Light
    EffectItem("ve_glow", "Glow", EffectType.GLOW, "Light", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_flare", "Lens Flare", EffectType.LENS_FLARE, "Light", "PRO", Color(0xFFF59E0B), 0.85f),
    EffectItem("ve_leak", "Light Leak", EffectType.LIGHT_LEAK, "Light", "HOT", Color(0xFFFB923C), 0.75f),
    EffectItem("ve_solar", "Solar Flare", EffectType.SOLAR_FLARE, "Light", "PRO", Color(0xFFFACC15), 0.9f),
    EffectItem("ve_bokeh", "Bokeh Dreams", EffectType.BOKEH, "Light", "PRO", Color(0xFFE879F9), 0.75f),
    EffectItem("ve_golden", "Golden Hour", EffectType.GOLDEN_HOUR, "Light", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_flash", "White Flash", EffectType.FLASH, "Light", "", Color(0xFFFFFFFF), 0.9f),
    EffectItem("ve_halo", "Halo Glow", EffectType.HALO_GLOW, "Light", "", Color(0xFF38BDF8), 0.8f),
    EffectItem("ve_laser", "Laser Grid", EffectType.LASER_GRID, "Light", "PRO", Color(0xFF22C55E), 0.9f),
    EffectItem("ve_strobe", "RGB Strobe", EffectType.STROBE, "Light", "PRO", Color(0xFF38BDF8), 0.9f),

    // Texture
    EffectItem("ve_vhs", "1998 VHS", EffectType.VHS_VINTAGE, "Texture", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_crt", "CRT Screen", EffectType.CRT_TV, "Texture", "", Color(0xFF10B981), 0.75f),
    EffectItem("ve_noise", "Noise Grain", EffectType.NOISE, "Texture", "", Color(0xFF94A3B8), 0.75f),
    EffectItem("ve_vignette", "Vignette Dark", EffectType.VIGNETTE, "Texture", "", Color(0xFF64748B), 0.8f),
    EffectItem("ve_blur", "Blur", EffectType.BLUR, "Texture", "", Color(0xFF38BDF8), 0.7f),
    EffectItem("ve_mblur", "Motion Blur", EffectType.MOTION_BLUR, "Texture", "", Color(0xFF6366F1), 0.75f),
    EffectItem("ve_soft", "Soft Focus", EffectType.SOFT_FOCUS, "Texture", "", Color(0xFFF472B6), 0.8f),
    EffectItem("ve_sharpen", "Sharpen", EffectType.SHARPEN, "Texture", "", Color(0xFF00E5FF), 0.85f),

    // Celebrate
    EffectItem("ve_confetti", "Golden Confetti", EffectType.CELEBRATE_CONFETTI, "Celebrate", "HOT", Color(0xFFFACC15), 0.9f),
    EffectItem("ve_fireworks", "Neon Fireworks", EffectType.CELEBRATE_FIREWORKS, "Celebrate", "PRO", Color(0xFFEC4899), 0.9f),
    EffectItem("ve_sparks", "Fire Sparks", EffectType.FIRE_SPARK, "Celebrate", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_halo_c", "Halo Sparkle", EffectType.HALO_GLOW, "Celebrate", "", Color(0xFF38BDF8), 0.8f),

    // Party
    EffectItem("ve_rgbsplit", "RGB Split", EffectType.RGB_SPLIT, "Party", "HOT", Color(0xFF3B82F6), 0.8f),
    EffectItem("ve_glitch", "Glitch Scan", EffectType.GLITCH, "Party", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_acid", "Acid Trip", EffectType.ACID_TRIP, "Party", "HOT", Color(0xFFD946EF), 0.85f),
    EffectItem("ve_mirror", "Mirror Prism", EffectType.MIRROR, "Party", "", Color(0xFFEC4899), 0.8f),
    EffectItem("ve_fisheye", "Fisheye Lens", EffectType.FISHEYE, "Party", "", Color(0xFF10B981), 0.8f),
    EffectItem("ve_prism", "Prism Rainbow", EffectType.PARTY_PRISM, "Party", "PRO", Color(0xFF8B5CF6), 0.85f),
    EffectItem("ve_confused", "Confused Wobble", EffectType.PARTY_CONFUSED, "Party", "", Color(0xFFF59E0B), 0.8f),

    // Extended professional 200-effect library
    EffectItem("vfx_viral_1", "Zoom Blur", EffectType.VFX_VIRAL_1, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_2", "Speed Ramp", EffectType.VFX_VIRAL_2, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_3", "Bullet Time", EffectType.VFX_VIRAL_3, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_4", "Glitch Pop", EffectType.VFX_VIRAL_4, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_5", "RGB Split", EffectType.VFX_VIRAL_5, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_6", "Shake Zoom", EffectType.VFX_VIRAL_6, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_7", "Freeze Frame", EffectType.VFX_VIRAL_7, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_8", "Time Warp", EffectType.VFX_VIRAL_8, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_9", "Datamosh", EffectType.VFX_VIRAL_9, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_10", "Pixel Sort", EffectType.VFX_VIRAL_10, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_11", "Flash Transition", EffectType.VFX_VIRAL_11, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_12", "Heartbeat Zoom", EffectType.VFX_VIRAL_12, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_13", "Echo Trail", EffectType.VFX_VIRAL_13, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_14", "Motion Blur Punch", EffectType.VFX_VIRAL_14, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_15", "Camera Shake", EffectType.VFX_VIRAL_15, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_16", "Zoom Punch In", EffectType.VFX_VIRAL_16, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_17", "Zoom Punch Out", EffectType.VFX_VIRAL_17, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_18", "Slide Reveal", EffectType.VFX_VIRAL_18, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_19", "Whip Pan", EffectType.VFX_VIRAL_19, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_20", "Spin Zoom", EffectType.VFX_VIRAL_20, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_21", "Aura Glow", EffectType.VFX_VIRAL_21, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_22", "VN Style Glow", EffectType.VFX_VIRAL_22, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_23", "Cinematic Flicker", EffectType.VFX_VIRAL_23, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_24", "Light Leak Pass", EffectType.VFX_VIRAL_24, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_25", "Particle Burst", EffectType.VFX_VIRAL_25, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_26", "Confetti Pop", EffectType.VFX_VIRAL_26, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_27", "Screen Crack", EffectType.VFX_VIRAL_27, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_28", "Kaleidoscope", EffectType.VFX_VIRAL_28, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_29", "Chromatic Aberration", EffectType.VFX_VIRAL_29, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_30", "Old TV Static", EffectType.VFX_VIRAL_30, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_31", "Signal Loss", EffectType.VFX_VIRAL_31, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_32", "Double Exposure", EffectType.VFX_VIRAL_32, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_33", "Ghost Trail", EffectType.VFX_VIRAL_33, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_34", "Speed Blur Streak", EffectType.VFX_VIRAL_34, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_35", "Bounce Zoom", EffectType.VFX_VIRAL_35, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_36", "Flash Bang", EffectType.VFX_VIRAL_36, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_37", "Neon Trace", EffectType.VFX_VIRAL_37, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_38", "Fireworks Burst", EffectType.VFX_VIRAL_38, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_39", "Rain Overlay", EffectType.VFX_VIRAL_39, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_40", "Snowfall Overlay", EffectType.VFX_VIRAL_40, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_1", "VHS Glitch", EffectType.VFX_GLITCH_1, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_2", "Digital Noise", EffectType.VFX_GLITCH_2, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_3", "Screen Tear", EffectType.VFX_GLITCH_3, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_4", "Wave Distortion", EffectType.VFX_GLITCH_4, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_5", "Liquid Melt", EffectType.VFX_GLITCH_5, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_6", "Static Interference", EffectType.VFX_GLITCH_6, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_7", "Broken Signal", EffectType.VFX_GLITCH_7, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_8", "Analog Glitch", EffectType.VFX_GLITCH_8, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_9", "Corrupted Frame", EffectType.VFX_GLITCH_9, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_10", "Pixel Stretch", EffectType.VFX_GLITCH_10, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_11", "Scan Line Flicker", EffectType.VFX_GLITCH_11, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_12", "Color Bleed", EffectType.VFX_GLITCH_12, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_13", "Frame Skip", EffectType.VFX_GLITCH_13, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_14", "Jitter Shake", EffectType.VFX_GLITCH_14, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_15", "Warp Distort", EffectType.VFX_GLITCH_15, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_16", "Bad Signal Roll", EffectType.VFX_GLITCH_16, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_17", "TV Turn Off", EffectType.VFX_GLITCH_17, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_18", "CRT Curve", EffectType.VFX_GLITCH_18, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_19", "Frame Drop Stutter", EffectType.VFX_GLITCH_19, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_20", "Data Corruption", EffectType.VFX_GLITCH_20, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_1", "VHS Tape", EffectType.VFX_RETRO_1, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_2", "Old Film Grain", EffectType.VFX_RETRO_2, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_3", "Sepia Fade", EffectType.VFX_RETRO_3, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_4", "Vintage 90s", EffectType.VFX_RETRO_4, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_5", "8mm Film", EffectType.VFX_RETRO_5, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_6", "Polaroid Frame", EffectType.VFX_RETRO_6, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_7", "Film Burn", EffectType.VFX_RETRO_7, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_8", "Dust & Scratches", EffectType.VFX_RETRO_8, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_9", "Faded Color", EffectType.VFX_RETRO_9, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_10", "Retro TV Frame", EffectType.VFX_RETRO_10, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_11", "Disco Ball", EffectType.VFX_RETRO_11, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_12", "Neon 80s", EffectType.VFX_RETRO_12, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_13", "Old Camera Border", EffectType.VFX_RETRO_13, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_14", "Y2K Filter", EffectType.VFX_RETRO_14, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_15", "Grainy B&W", EffectType.VFX_RETRO_15, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_16", "Cassette Rewind", EffectType.VFX_RETRO_16, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_17", "Sepia Grain", EffectType.VFX_RETRO_17, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_18", "Retro Wave", EffectType.VFX_RETRO_18, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_19", "Film Reel Flicker", EffectType.VFX_RETRO_19, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_20", "Old Newspaper Fade", EffectType.VFX_RETRO_20, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_1", "Light Leak", EffectType.VFX_LIGHT_1, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_2", "Lens Flare", EffectType.VFX_LIGHT_2, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_3", "Golden Hour Glow", EffectType.VFX_LIGHT_3, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_4", "Neon Glow Trail", EffectType.VFX_LIGHT_4, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_5", "Sun Ray Overlay", EffectType.VFX_LIGHT_5, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_6", "Bokeh Light", EffectType.VFX_LIGHT_6, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_7", "Glow Pulse", EffectType.VFX_LIGHT_7, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_8", "Flashlight Sweep", EffectType.VFX_LIGHT_8, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_9", "Spotlight Reveal", EffectType.VFX_LIGHT_9, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_10", "Rainbow Light", EffectType.VFX_LIGHT_10, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_11", "Laser Beam", EffectType.VFX_LIGHT_11, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_12", "Sparkle Overlay", EffectType.VFX_LIGHT_12, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_13", "Firefly Particles", EffectType.VFX_LIGHT_13, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_14", "Glow Burst", EffectType.VFX_LIGHT_14, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_15", "Candle Flicker", EffectType.VFX_LIGHT_15, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_16", "Neon Sign Flicker", EffectType.VFX_LIGHT_16, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_17", "Prism Light", EffectType.VFX_LIGHT_17, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_18", "Star Glow", EffectType.VFX_LIGHT_18, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_19", "Halo Light", EffectType.VFX_LIGHT_19, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_light_20", "Beam Sweep", EffectType.VFX_LIGHT_20, "Light / Glow Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_1", "Gaussian Blur", EffectType.VFX_BLUR_1, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_2", "Motion Blur", EffectType.VFX_BLUR_2, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_3", "Radial Blur", EffectType.VFX_BLUR_3, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_4", "Tilt Shift", EffectType.VFX_BLUR_4, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_5", "Zoom Blur Trail", EffectType.VFX_BLUR_5, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_6", "Background Blur", EffectType.VFX_BLUR_6, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_7", "Focus Pull", EffectType.VFX_BLUR_7, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_8", "Depth Blur", EffectType.VFX_BLUR_8, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_9", "Soft Focus", EffectType.VFX_BLUR_9, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_10", "Vignette Blur", EffectType.VFX_BLUR_10, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_11", "Cinematic Blur", EffectType.VFX_BLUR_11, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_12", "Dreamy Blur", EffectType.VFX_BLUR_12, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_13", "Spin Blur", EffectType.VFX_BLUR_13, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_14", "Directional Blur", EffectType.VFX_BLUR_14, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_blur_15", "Frosted Glass Blur", EffectType.VFX_BLUR_15, "Blur / Focus Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_1", "Cinematic LUT", EffectType.VFX_COLOR_1, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_2", "Teal & Orange", EffectType.VFX_COLOR_2, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_3", "Black & White", EffectType.VFX_COLOR_3, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_4", "High Contrast", EffectType.VFX_COLOR_4, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_5", "Warm Tone", EffectType.VFX_COLOR_5, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_6", "Cool Tone", EffectType.VFX_COLOR_6, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_7", "Moody Filter", EffectType.VFX_COLOR_7, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_8", "Pastel Filter", EffectType.VFX_COLOR_8, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_9", "HDR Boost", EffectType.VFX_COLOR_9, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_10", "Vintage Color Grade", EffectType.VFX_COLOR_10, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_11", "Vibrant Pop", EffectType.VFX_COLOR_11, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_12", "Muted Tone", EffectType.VFX_COLOR_12, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_13", "Cross Process", EffectType.VFX_COLOR_13, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_14", "Split Tone", EffectType.VFX_COLOR_14, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_15", "Duotone", EffectType.VFX_COLOR_15, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_16", "Infrared Effect", EffectType.VFX_COLOR_16, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_17", "Night Mode Grade", EffectType.VFX_COLOR_17, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_18", "Sunset Grade", EffectType.VFX_COLOR_18, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_19", "Film Noir", EffectType.VFX_COLOR_19, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_20", "Pastel Dream", EffectType.VFX_COLOR_20, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_21", "Aesthetic Grade", EffectType.VFX_COLOR_21, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_22", "Matte Finish", EffectType.VFX_COLOR_22, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_23", "Faded Film Grade", EffectType.VFX_COLOR_23, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_24", "Vibrance Boost", EffectType.VFX_COLOR_24, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_color_25", "Skin Tone Correction", EffectType.VFX_COLOR_25, "Color & Filter Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_1", "Mirror Split", EffectType.VFX_3D_1, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_2", "Kaleidoscope Mirror", EffectType.VFX_3D_2, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_3", "Split Screen 2-Way", EffectType.VFX_3D_3, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_4", "Split Screen 3-Way", EffectType.VFX_3D_4, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_5", "3D Pop Out", EffectType.VFX_3D_5, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_6", "Toon Outline", EffectType.VFX_3D_6, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_7", "Cel Shade", EffectType.VFX_3D_7, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_8", "Cube Rotate", EffectType.VFX_3D_8, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_9", "3D Zoom Depth", EffectType.VFX_3D_9, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_10", "Parallax 3D", EffectType.VFX_3D_10, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_11", "Cartoon Sketch", EffectType.VFX_3D_11, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_12", "Comic Book Effect", EffectType.VFX_3D_12, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_13", "Symmetry Mirror", EffectType.VFX_3D_13, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_14", "Face Symmetry", EffectType.VFX_3D_14, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_3d_15", "3D Card Flip", EffectType.VFX_3D_15, "Split / Mirror / 3D", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_1", "Swipe Transition", EffectType.VFX_TRANS_1, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_2", "Zoom Transition", EffectType.VFX_TRANS_2, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_3", "Spin Transition", EffectType.VFX_TRANS_3, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_4", "Fade Transition", EffectType.VFX_TRANS_4, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_5", "Slide Transition", EffectType.VFX_TRANS_5, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_6", "Ripple Transition", EffectType.VFX_TRANS_6, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_7", "Circle Reveal", EffectType.VFX_TRANS_7, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_8", "Shape Wipe", EffectType.VFX_TRANS_8, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_9", "Blur Transition", EffectType.VFX_TRANS_9, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_10", "Glitch Transition", EffectType.VFX_TRANS_10, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_11", "Flash Transition Pro", EffectType.VFX_TRANS_11, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_12", "Page Turn", EffectType.VFX_TRANS_12, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_13", "Cube Transition", EffectType.VFX_TRANS_13, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_14", "Liquid Transition", EffectType.VFX_TRANS_14, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_trans_15", "Match Cut Zoom", EffectType.VFX_TRANS_15, "Transitions", "PRO", Color(0xFF00C2FF), 0.82f),
  )

  // 2. BODY EFFECTS
  val BODY_EFFECTS_CATEGORIES = listOf("All", "Hits", "Stroke", "Portrait", "Dark", "Selfie", "Mask", "Funny Faces", "Background", "Body Effects")

  val BODY_EFFECTS: List<EffectItem> = listOf(
    EffectItem("be_none", "None", null, "All", "", Color(0xFF64748B), 0.0f),

    // Hits
    EffectItem("be_aura", "Neon Aura", EffectType.BODY_AURA, "Hits", "HOT", Color(0xFF00C2FF), 0.85f),
    EffectItem("be_muscle", "Muscle Glow", EffectType.MUSCLE_GLOW, "Hits", "PRO", Color(0xFF3B82F6), 0.85f),
    EffectItem("be_fire", "Super Saiyan", EffectType.FIRE_AURA, "Hits", "HOT", Color(0xFFF59E0B), 0.9f),
    EffectItem("be_thor", "Thor Lightning", EffectType.LIGHTNING_BODY, "Hits", "AI", Color(0xFF38BDF8), 0.9f),

    // Stroke
    EffectItem("be_outline", "Cyber Outline", EffectType.NEON_OUTLINE, "Stroke", "AI", Color(0xFF00E5FF), 0.9f),
    EffectItem("be_anime_sh", "Anime Stroke", EffectType.ANIME_SILHOUETTE, "Stroke", "PRO", Color(0xFFEC4899), 0.85f),

    // Portrait
    EffectItem("be_beauty", "AI Skin Smooth", EffectType.FACE_BEAUTY, "Portrait", "AI", Color(0xFFF472B6), 0.75f),
    EffectItem("be_slim", "Pro Contour", EffectType.SLIM_SHAPE, "Portrait", "", Color(0xFFA855F7), 0.8f),
    EffectItem("be_eyes", "Laser Eyes", EffectType.GLOW_EYES, "Portrait", "AI", Color(0xFFEF4444), 0.9f),
    EffectItem("be_wings", "Angel Wings", EffectType.ANGEL_WINGS, "Portrait", "HOT", Color(0xFFFACC15), 0.85f),
    EffectItem("be_cwings", "Cyber Wings", EffectType.CYBER_WINGS, "Portrait", "PRO", Color(0xFF8B5CF6), 0.9f),

    // Dark
    EffectItem("be_xray", "Neon Skeleton", EffectType.SKELETON_XRAY, "Dark", "PRO", Color(0xFF06B6D4), 0.9f),
    EffectItem("be_ghost", "Ghost Clone", EffectType.GHOST_CLONE, "Dark", "PRO", Color(0xFF94A3B8), 0.85f),
    EffectItem("be_dark_aura", "Dark Shadow", EffectType.DARK_SHADOW_AURA, "Dark", "AI", Color(0xFF6B21A8), 0.9f),

    // Selfie
    EffectItem("be_heart", "Cupid Hearts", EffectType.HEART_TRAIL, "Selfie", "HOT", Color(0xFFFF3366), 0.8f),
    EffectItem("be_crown", "Floral Crown", EffectType.FLORAL_CROWN, "Selfie", "", Color(0xFF10B981), 0.85f),
    EffectItem("be_sparkle", "Sparkle Cheeks", EffectType.NEON_SPARKLE_CHEEKS, "Selfie", "AI", Color(0xFFF472B6), 0.8f),

    // Mask
    EffectItem("be_cface", "Cyber Face HUD", EffectType.CYBER_FACE, "Mask", "AI", Color(0xFF00E5FF), 0.85f),
    EffectItem("be_visor", "Cyber Visor", EffectType.CYBER_VISOR, "Mask", "PRO", Color(0xFF8B5CF6), 0.9f),

    // Funny Faces
    EffectItem("be_big_eyes", "Big Eyes Lens", EffectType.FUNNY_BIG_EYES, "Funny Faces", "HOT", Color(0xFFF59E0B), 0.85f),
    EffectItem("be_alien_warp", "Alien Warp", EffectType.FUNNY_ALIEN_WARP, "Funny Faces", "", Color(0xFF22C55E), 0.85f),

    // Background
    EffectItem("be_dragon", "Dragon Flame", EffectType.DRAGON_FLAME, "Background", "PRO", Color(0xFFEF4444), 0.9f),
    EffectItem("be_bg_grid", "Neon Grid BG", EffectType.BACKGROUND_NEON_GRID, "Background", "AI", Color(0xFF00C2FF), 0.85f),

    // Extended Body / Face Effects
    EffectItem("vfx_body_1", "Slim Body", EffectType.VFX_BODY_1, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_2", "Muscle Boost", EffectType.VFX_BODY_2, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_3", "Funny Face", EffectType.VFX_BODY_3, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_4", "Big Head", EffectType.VFX_BODY_4, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_5", "Tiny Body", EffectType.VFX_BODY_5, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_6", "Selfie Smooth", EffectType.VFX_BODY_6, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_7", "Skin Glow", EffectType.VFX_BODY_7, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_8", "Face Warp", EffectType.VFX_BODY_8, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_9", "Stretch Body", EffectType.VFX_BODY_9, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_10", "Squeeze Face", EffectType.VFX_BODY_10, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_11", "Bobble Head", EffectType.VFX_BODY_11, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_12", "Glowing Body Outline", EffectType.VFX_BODY_12, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_13", "Body Mask Cutout", EffectType.VFX_BODY_13, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_14", "Mood Aura Overlay", EffectType.VFX_BODY_14, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_15", "Muscle X-Ray", EffectType.VFX_BODY_15, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_16", "Wide Shoulder", EffectType.VFX_BODY_16, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_17", "Long Legs", EffectType.VFX_BODY_17, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_18", "Face Melt", EffectType.VFX_BODY_18, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_19", "Rubber Face", EffectType.VFX_BODY_19, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_20", "Zombie Face", EffectType.VFX_BODY_20, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_21", "Cartoon Face Overlay", EffectType.VFX_BODY_21, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_22", "Eye Enlarge", EffectType.VFX_BODY_22, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_23", "Jaw Sharpen", EffectType.VFX_BODY_23, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_24", "Body Split Clone", EffectType.VFX_BODY_24, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_25", "Shadow Clone Body", EffectType.VFX_BODY_25, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_26", "Mirror Face", EffectType.VFX_BODY_26, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_27", "Fisheye Face", EffectType.VFX_BODY_27, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_28", "Anime Eyes Filter", EffectType.VFX_BODY_28, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_29", "Old Age Face", EffectType.VFX_BODY_29, "Body Effects", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_body_30", "Baby Face Filter", EffectType.VFX_BODY_30, "Body Effects", "", Color(0xFF00C2FF), 0.82f)
  )

  // 3. PHOTO EFFECTS
  val PHOTO_EFFECTS_CATEGORIES = listOf("All", "AI Painting", "Expression", "Motions", "Real", "Face Swap", "Screen Swap", "Portrait", "AI Images", "Old Images", "2000s")

  val PHOTO_EFFECTS: List<EffectItem> = listOf(
    EffectItem("pe_none", "None", null, "All", "", Color(0xFF64748B), 0.0f),

    // AI Painting
    EffectItem("pe_oil", "Van Gogh Oil", EffectType.OIL_PAINTING, "AI Painting", "HOT", Color(0xFFF59E0B), 0.85f),
    EffectItem("pe_watercolor", "Watercolor", EffectType.WATERCOLOR, "AI Painting", "AI", Color(0xFF06B6D4), 0.85f),
    EffectItem("pe_comic", "Comic Sketch", EffectType.COMIC_SKETCH, "AI Painting", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("pe_charcoal", "Charcoal Draw", EffectType.CHARCOAL_DRAW, "AI Painting", "", Color(0xFF94A3B8), 0.8f),

    // Expression
    EffectItem("pe_colorpop", "Color Pop", EffectType.COLOR_POP_SPLASH, "Expression", "PRO", Color(0xFFF59E0B), 0.8f),
    EffectItem("pe_pastel", "Pastel Dream", EffectType.PASTEL_DREAM, "Expression", "", Color(0xFFF472B6), 0.75f),

    // Motions
    EffectItem("pe_manga", "Manga Lines", EffectType.MANGA_LINE, "Motions", "HOT", Color(0xFF000000), 0.85f),
    EffectItem("pe_double", "Double Exposure", EffectType.DOUBLE_EXPOSURE, "Motions", "PRO", Color(0xFFA855F7), 0.85f),

    // Real
    EffectItem("pe_blueprint", "Blueprint CAD", EffectType.BLUEPRINT_CAD, "Real", "PRO", Color(0xFF38BDF8), 0.85f),
    EffectItem("pe_thermal", "Thermal Heat", EffectType.THERMAL_CAMERA, "Real", "HOT", Color(0xFFEF4444), 0.85f),

    // Face Swap
    EffectItem("pe_faceswap", "AI Face Swap", EffectType.FACE_SWAP_AI, "Face Swap", "AI", Color(0xFFEC4899), 0.9f),

    // Screen Swap
    EffectItem("pe_screenswap", "Hologram Screen", EffectType.SCREEN_SWAP_HOLO, "Screen Swap", "AI", Color(0xFF00E5FF), 0.9f),
    EffectItem("pe_halftone", "Halftone Dot", EffectType.HALFTONE_DOT, "Screen Swap", "", Color(0xFF64748B), 0.75f),

    // Portrait
    EffectItem("pe_polaroid", "Polaroid 1984", EffectType.POLAROID_VINTAGE, "Portrait", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("pe_popart", "Andy Warhol", EffectType.POP_ART_POSTER, "Portrait", "HOT", Color(0xFFEC4899), 0.85f),

    // AI Images
    EffectItem("pe_expansion", "AI Canvas Expand", EffectType.AI_EXPANSION, "AI Images", "AI", Color(0xFF8B5CF6), 0.9f),
    EffectItem("pe_style_morph", "AI Style Morph", EffectType.AI_STYLE_MORPH, "AI Images", "AI", Color(0xFF00C2FF), 0.9f),

    // Old Images
    EffectItem("pe_stamp", "Rubber Stamp", EffectType.STAMP_ART, "Old Images", "", Color(0xFFB45309), 0.8f),
    EffectItem("pe_sepia", "Sepia 1920", EffectType.SEPIA_VINTAGE, "Old Images", "HOT", Color(0xFFD97706), 0.85f),
    EffectItem("pe_worn_paper", "Worn Paper", EffectType.OLD_PAPER_TEXTURE, "Old Images", "", Color(0xFF78716C), 0.8f),

    // 2000s
    EffectItem("pe_y2k", "Y2K Chrome", EffectType.Y2K_CHROME, "2000s", "HOT", Color(0xFF38BDF8), 0.85f),
    EffectItem("pe_digicam", "Digicam 2004", EffectType.DIGICAM_2004, "2000s", "HOT", Color(0xFFF43F5E), 0.85f)
  )

  // 4. AI EFFECTS
  val AI_EFFECTS_CATEGORIES = listOf("All", "Cyberpunk", "Anime Style", "Dreamscape", "AI Glow", "Voxel 3D", "Fantasy", "Generative")

  val AI_EFFECTS: List<EffectItem> = listOf(
    EffectItem("ae_none", "None", null, "All", "", Color(0xFF64748B), 0.0f),

    // Cyberpunk
    EffectItem("ae_cyber", "AI Neon Matrix", EffectType.AI_CYBERPUNK_CITY, "Cyberpunk", "AI", Color(0xFFEC4899), 0.9f),
    EffectItem("ae_quantum", "Quantum Glitch", EffectType.AI_GLITCH_REALITY, "Cyberpunk", "AI", Color(0xFFD946EF), 0.95f),
    EffectItem("ae_bgswap", "Cyber City Swap", EffectType.AI_BG_SWAP, "Cyberpunk", "AI", Color(0xFF00E5FF), 0.9f),

    // Anime Style
    EffectItem("ae_anime_w", "Ghibli Fantasy", EffectType.AI_ANIME_WORLD, "Anime Style", "AI", Color(0xFF10B981), 0.9f),
    EffectItem("ae_manga_u", "AI Anime Char", EffectType.AI_MANGA_UNIVERSE, "Anime Style", "AI", Color(0xFFF59E0B), 0.9f),

    // Dreamscape
    EffectItem("ae_freeze", "Time Freeze", EffectType.AI_FREEZE_TIME, "Dreamscape", "AI", Color(0xFF06B6D4), 0.9f),
    EffectItem("ae_chrono", "Chrono Motion", EffectType.AI_GHOST_MOTION, "Dreamscape", "AI", Color(0xFF8B5CF6), 0.9f),
    EffectItem("ae_god", "Celestial Divinity", EffectType.AI_GOLDEN_GOD, "Dreamscape", "PRO", Color(0xFFFACC15), 0.9f),

    // AI Glow
    EffectItem("ae_trail", "Speed Force Trail", EffectType.AI_NEON_TRAIL, "AI Glow", "AI", Color(0xFF38BDF8), 0.9f),
    EffectItem("ae_hyper_light", "Hyper Lightning", EffectType.AI_SPEED_FORCE, "AI Glow", "AI", Color(0xFF00E5FF), 0.9f),
    EffectItem("ae_liquid", "Liquid Gold", EffectType.AI_LIQUID_GOLD, "AI Glow", "PRO", Color(0xFFEAB308), 0.85f),

    // Voxel 3D
    EffectItem("ae_disperse", "Thanos Snap", EffectType.AI_PARTICLE_DISPERSE, "Voxel 3D", "AI", Color(0xFFF59E0B), 0.95f),

    // Fantasy
    EffectItem("ae_portal", "Sci-Fi Portal", EffectType.AI_SCI_FI_PORTAL, "Fantasy", "PRO", Color(0xFF00E5FF), 0.85f),
    EffectItem("ae_kingdom", "Enchanted World", EffectType.AI_FANTASY_KINGDOM, "Fantasy", "AI", Color(0xFFEC4899), 0.9f),

    // Generative
    EffectItem("ae_expand", "AI Canvas Expand", EffectType.AI_EXPANSION, "Generative", "AI", Color(0xFF8B5CF6), 0.9f),
    EffectItem("ae_morph", "Universe Morph", EffectType.AI_STYLE_MORPH, "Generative", "AI", Color(0xFF00C2FF), 0.9f)
  )
}

/**
 * Main Effects Studio Panel Entry Point.
 */
@Composable
fun EffectsStudioPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val timeline by viewModel.timelineEngine.timeline.collectAsState()
  val selectedElement by viewModel.timelineEngine.selectedElement.collectAsState()
  val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()

  // Current active sub-screen within Effects
  var activeView by remember { mutableStateOf(EffectsViewType.MAIN_HUB) }

  // Target Video Clip reference
  val selectedVideoClip = remember(timeline.videoClips, selectedElement, currentPosMs) {
    when (selectedElement) {
      is SelectedTrackElement.Video -> {
        timeline.videoClips.find { it.id == (selectedElement as SelectedTrackElement.Video).clipId }
      }
      is SelectedTrackElement.Effect -> {
        val effect = timeline.effectClips.find { it.id == (selectedElement as SelectedTrackElement.Effect).clipId }
        effect?.targetClipId?.let { clipId -> timeline.videoClips.find { it.id == clipId } }
          ?: timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
      }
      else -> {
        timeline.videoClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
          ?: timeline.videoClips.firstOrNull()
      }
    }
  }

  // Active effect applied on the current clip / timeline position
  val activeEffectClip = remember(timeline.effectClips, selectedVideoClip, selectedElement, currentPosMs) {
    val sel = selectedElement
    if (sel is SelectedTrackElement.Effect) {
      timeline.effectClips.find { it.id == sel.clipId }
    } else {
      val clipId = selectedVideoClip?.id
      timeline.effectClips.find { it.targetClipId == clipId }
        ?: timeline.effectClips.find { currentPosMs >= it.timelineStartMs && currentPosMs < it.timelineStartMs + it.durationMs }
        ?: timeline.effectClips.lastOrNull()
    }
  }

  // 60fps clock provider for preview thumbnails
  val infiniteTransition = rememberInfiniteTransition(label = "FX_Studio_Clock")
  val animTimeMsProvider = infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 60000f,
    animationSpec = infiniteRepeatable(
      animation = tween(60000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "FX_Clock_Provider"
  )

  val onSelectEffect: (EffectItem) -> Unit = { item ->
    if (item.effectType == null) {
      // "None / Original" selected -> Remove effect from current clip
      viewModel.timelineEngine.removeEffectFromCurrentClip()
      val curPos = viewModel.timelineEngine.currentPositionMs.value
      viewModel.timelineEngine.seekTo(curPos)
    } else {
      // Instant real-time effect application
      viewModel.timelineEngine.applyEffectToCurrentClip(
        effectType = item.effectType,
        customName = item.name,
        intensity = item.defaultIntensity
      )
      val curPos = viewModel.timelineEngine.currentPositionMs.value
      viewModel.timelineEngine.seekTo(curPos)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(StudioSurface)
  ) {
    when (activeView) {
      EffectsViewType.MAIN_HUB -> {
        EffectsMainHubView(
          activeEffectClip = activeEffectClip,
          selectedVideoClipName = selectedVideoClip?.name,
          onOpenCategory = { viewType -> activeView = viewType },
          onClose = onDismiss,
          onIntensityChange = { newIntensity ->
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, newIntensity)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onResetIntensity = {
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, 0.8f)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onRemoveEffect = {
            if (activeEffectClip != null) {
              viewModel.timelineEngine.deleteEffectClip(activeEffectClip.id)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          }
        )
      }

      EffectsViewType.VIDEO_EFFECTS -> {
        EffectsSubPanelView(
          title = "Video Effects",
          searchPlaceholder = "Search Video Effects...",
          categories = EffectsCatalog.VIDEO_EFFECTS_CATEGORIES,
          effectsList = EffectsCatalog.VIDEO_EFFECTS,
          activeEffectClip = activeEffectClip,
          animTimeMsProvider = { animTimeMsProvider.value.toLong() },
          onSelectEffect = onSelectEffect,
          onIntensityChange = { newIntensity ->
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, newIntensity)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onClose = { activeView = EffectsViewType.MAIN_HUB },
          onApply = { activeView = EffectsViewType.MAIN_HUB }
        )
      }

      EffectsViewType.BODY_EFFECTS -> {
        EffectsSubPanelView(
          title = "Body Effects",
          searchPlaceholder = "Search Body Effects...",
          categories = EffectsCatalog.BODY_EFFECTS_CATEGORIES,
          effectsList = EffectsCatalog.BODY_EFFECTS,
          activeEffectClip = activeEffectClip,
          animTimeMsProvider = { animTimeMsProvider.value.toLong() },
          onSelectEffect = onSelectEffect,
          onIntensityChange = { newIntensity ->
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, newIntensity)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onClose = { activeView = EffectsViewType.MAIN_HUB },
          onApply = { activeView = EffectsViewType.MAIN_HUB }
        )
      }

      EffectsViewType.PHOTO_EFFECTS -> {
        EffectsSubPanelView(
          title = "Photo Effects",
          searchPlaceholder = "Search Photo Effects...",
          categories = EffectsCatalog.PHOTO_EFFECTS_CATEGORIES,
          effectsList = EffectsCatalog.PHOTO_EFFECTS,
          activeEffectClip = activeEffectClip,
          animTimeMsProvider = { animTimeMsProvider.value.toLong() },
          onSelectEffect = onSelectEffect,
          onIntensityChange = { newIntensity ->
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, newIntensity)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onClose = { activeView = EffectsViewType.MAIN_HUB },
          onApply = { activeView = EffectsViewType.MAIN_HUB }
        )
      }

      EffectsViewType.AI_EFFECTS -> {
        EffectsSubPanelView(
          title = "AI Effects",
          searchPlaceholder = "Search AI Effects...",
          categories = EffectsCatalog.AI_EFFECTS_CATEGORIES,
          effectsList = EffectsCatalog.AI_EFFECTS,
          activeEffectClip = activeEffectClip,
          animTimeMsProvider = { animTimeMsProvider.value.toLong() },
          onSelectEffect = onSelectEffect,
          onIntensityChange = { newIntensity ->
            if (activeEffectClip != null) {
              viewModel.timelineEngine.updateEffectIntensity(activeEffectClip.id, newIntensity)
              val curPos = viewModel.timelineEngine.currentPositionMs.value
              viewModel.timelineEngine.seekTo(curPos)
            }
          },
          onClose = { activeView = EffectsViewType.MAIN_HUB },
          onApply = { activeView = EffectsViewType.MAIN_HUB }
        )
      }
    }
  }
}

/**
 * Main Effects Hub Screen - Single-row / 4-Tile Category selection with close button and live active effect controller.
 */
@Composable
private fun EffectsMainHubView(
  activeEffectClip: EffectClip?,
  selectedVideoClipName: String?,
  onOpenCategory: (EffectsViewType) -> Unit,
  onClose: () -> Unit,
  onIntensityChange: (Float) -> Unit,
  onResetIntensity: () -> Unit,
  onRemoveEffect: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Top Header with Title and ❌ Close button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(listOf(Color(0xFF00C2FF), Color(0xFF8B5CF6)))
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }

        Column {
          Text(
            text = "Effects Tools",
            style = MaterialTheme.typography.titleMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
          )
          Text(
            text = "Choose an effect category to enhance your video",
            fontSize = 11.sp,
            color = TextSecondary
          )
        }
      }

      // ❌ Top Close button
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
          .testTag("effects_main_close_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Effects Panel",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // 2. Active Effect Status / Intensity Slider Bar (if any effect is active)
    if (activeEffectClip != null) {
      ActiveEffectControlBanner(
        activeEffectClip = activeEffectClip,
        targetVideoClipName = selectedVideoClipName,
        onIntensityChange = onIntensityChange,
        onReset = onResetIntensity,
        onRemove = onRemoveEffect
      )
    }

    // 3. Main 4 Effect Category Cards in a single row
    Text(
      text = "SELECT FX CATEGORY",
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF00C2FF),
      letterSpacing = 1.sp
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(130.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // 1. Video Effects
      MainCategoryActionCard(
        title = "Video Effects",
        icon = Icons.Default.Videocam,
        gradient = listOf(Color(0xFF00C2FF), Color(0xFF0066FF)),
        subtitle = "Motions, Light, Glitch, Party",
        modifier = Modifier.weight(1f),
        onClick = { onOpenCategory(EffectsViewType.VIDEO_EFFECTS) }
      )

      // 2. Body Effects
      MainCategoryActionCard(
        title = "Body Effects",
        icon = Icons.Default.AccessibilityNew,
        gradient = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF)),
        subtitle = "Aura, Wings, Stroke, Portrait",
        modifier = Modifier.weight(1f),
        onClick = { onOpenCategory(EffectsViewType.BODY_EFFECTS) }
      )

      // 3. Photo Effects
      MainCategoryActionCard(
        title = "Photo Effects",
        icon = Icons.Default.Image,
        gradient = listOf(Color(0xFFFF7A00), Color(0xFFFF0055)),
        subtitle = "AI Paint, Manga, 2000s, Y2K",
        modifier = Modifier.weight(1f),
        onClick = { onOpenCategory(EffectsViewType.PHOTO_EFFECTS) }
      )

      // 4. AI Effects
      MainCategoryActionCard(
        title = "AI Effects",
        icon = Icons.Default.Psychology,
        gradient = listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
        subtitle = "Cyberpunk, Morph, Particle, Portal",
        modifier = Modifier.weight(1f),
        onClick = { onOpenCategory(EffectsViewType.AI_EFFECTS) }
      )
    }
  }
}

/**
 * Individual Category Card in the Main Effects Hub.
 */
@Composable
private fun MainCategoryActionCard(
  title: String,
  icon: ImageVector,
  gradient: List<Color>,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFF111827),
    border = BorderStroke(1.dp, Color(0xFF1F293D)),
    modifier = modifier.fillMaxHeight()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = title,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center,
          maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 8.sp,
          color = TextSecondary,
          textAlign = TextAlign.Center,
          maxLines = 2,
          lineHeight = 10.sp,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

/**
 * Dedicated Sub-Panel View (Video Effects, Body Effects, Photo Effects, AI Effects).
 * Features:
 * - Top header: ❌ Close/Back on left, Search bar in center, ✓ Apply on right.
 * - Horizontally swipeable categories.
 * - 4-column effect grid with `None` as the first item.
 * - Real-time video thumbnails/previews.
 * - Instant effect selection & smooth vertical scrolling.
 */
@Composable
private fun EffectsSubPanelView(
  title: String,
  searchPlaceholder: String,
  categories: List<String>,
  effectsList: List<EffectItem>,
  activeEffectClip: EffectClip?,
  animTimeMsProvider: () -> Long,
  onSelectEffect: (EffectItem) -> Unit,
  onIntensityChange: (Float) -> Unit,
  onClose: () -> Unit,
  onApply: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "All") }
  val focusManager = LocalFocusManager.current

  // Filter effects based on category and search query
  val filteredEffects = remember(selectedCategory, searchQuery, effectsList) {
    val noneItem = effectsList.firstOrNull { it.effectType == null }
    val matching = effectsList.filter { item ->
      item.effectType != null &&
      (selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)) &&
      (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true))
    }

    if (searchQuery.isBlank()) {
      if (noneItem != null) listOf(noneItem) + matching else matching
    } else {
      matching
    }
  }

  val currentActiveEffectType = activeEffectClip?.effectType

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. TOP HEADER: ❌ Close on Left, Search Bar in Center, ✓ Apply on Right
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // ❌ Back / Close Button (Left)
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(0xFF1E283E))
          .testTag("sub_panel_close_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      }

      // Search Bar in Center
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
          .weight(1f)
          .height(34.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (searchQuery.isNotEmpty()) Color(0xFF00C2FF) else TextSecondary,
            modifier = Modifier.size(16.dp)
          )

          BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
              color = Color.White,
              fontSize = 12.sp
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            cursorBrush = SolidColor(Color(0xFF00C2FF)),
            decorationBox = { innerTextField ->
              if (searchQuery.isEmpty()) {
                Text(
                  text = searchPlaceholder,
                  color = TextSecondary.copy(alpha = 0.6f),
                  fontSize = 11.sp,
                  maxLines = 1
                )
              }
              innerTextField()
            },
            modifier = Modifier.weight(1f)
          )

          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { searchQuery = "" },
              modifier = Modifier.size(18.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }

      // ✓ Apply Button (Right)
      Surface(
        onClick = onApply,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF00C2FF),
        modifier = Modifier
          .height(34.dp)
          .testTag("sub_panel_apply_button")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Apply",
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = "Apply",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
          )
        }
      }
    }

    // 2. HORIZONTALLY SWIPEABLE CATEGORY ROW
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(categories) { cat ->
        val isCatSelected = cat.equals(selectedCategory, ignoreCase = true)
        Surface(
          onClick = { selectedCategory = cat },
          shape = RoundedCornerShape(16.dp),
          color = if (isCatSelected) Color(0xFF00C2FF).copy(alpha = 0.22f) else Color(0xFF111827),
          border = BorderStroke(
            1.dp,
            if (isCatSelected) Color(0xFF00C2FF) else Color(0xFF1F293D)
          )
        ) {
          Text(
            text = cat,
            fontSize = 11.sp,
            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isCatSelected) Color(0xFF00C2FF) else TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }
    }

    // 3. 4-COLUMN EFFECT GRID WITH REAL-TIME PREVIEWS (First item is None)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(
          items = filteredEffects,
          key = { it.id }
        ) { item ->
          val isSelected = if (item.effectType == null) {
            currentActiveEffectType == null
          } else {
            currentActiveEffectType == item.effectType
          }

          EffectGridCard4Col(
            item = item,
            isSelected = isSelected,
            animTimeMsProvider = animTimeMsProvider,
            onClick = { onSelectEffect(item) }
          )
        }
      }
    }

    // 4. DOCKED INTENSITY ADJUSTER BAR (when an effect is active)
    if (activeEffectClip != null) {
      DockedIntensityBar(
        activeEffectClip = activeEffectClip,
        onIntensityChange = onIntensityChange
      )
    }
  }
}

/**
 * Compact 4-Column Effect Thumbnail Grid Card with live canvas shader preview and badge.
 */
@Composable
private fun EffectGridCard4Col(
  item: EffectItem,
  isSelected: Boolean,
  animTimeMsProvider: () -> Long,
  onClick: () -> Unit
) {
  val borderColor by animateColorAsState(
    targetValue = if (isSelected) Color(0xFF00C2FF) else Color(0xFF1E293B),
    animationSpec = tween(180, easing = FastOutSlowInEasing),
    label = "Effect_Border_Anim"
  )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      )
      .testTag("effect_card_${item.id}")
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = Color(0xFF0D1424),
      border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (item.effectType == null) {
          // "None / Original" preview icon
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF131B2E)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotInterested,
              contentDescription = "None",
              tint = if (isSelected) Color(0xFF00C2FF) else Color(0xFF64748B),
              modifier = Modifier.size(24.dp)
            )
          }
        } else {
          // Live Animated Effect Shader Preview
          LiveEffectThumbnailView(
            item = item,
            animTimeMsProvider = animTimeMsProvider,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Tag Badge (HOT, PRO, AI, NEW)
        if (item.tag.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(bottomEnd = 6.dp),
            color = when (item.tag) {
              "HOT" -> Color(0xFFEF4444)
              "AI" -> Color(0xFF8B5CF6)
              "PRO" -> Color(0xFFF59E0B)
              else -> Color(0xFF00C2FF)
            },
            modifier = Modifier.align(Alignment.TopStart)
          ) {
            Text(
              text = item.tag,
              fontSize = 7.sp,
              fontWeight = FontWeight.Black,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
            )
          }
        }

        // Active Checkmark Indicator
        if (isSelected) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(3.dp)
              .size(16.dp)
              .clip(CircleShape)
              .background(Color(0xFF00C2FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = Color.Black,
              modifier = Modifier.size(11.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(3.dp))

    Text(
      text = item.name,
      fontSize = 9.5.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) Color(0xFF00C2FF) else Color(0xFFCBD5E1),
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * Docked bottom intensity control bar when an effect is active.
 */
@Composable
private fun DockedIntensityBar(
  activeEffectClip: EffectClip,
  onIntensityChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val intensity = activeEffectClip.intensity

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF0B1120),
    border = BorderStroke(1.dp, Color(0xFF1E293B)),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = "${activeEffectClip.customName.ifBlank { activeEffectClip.effectType.displayName }}",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier.width(70.dp)
      )

      Slider(
        value = intensity,
        onValueChange = onIntensityChange,
        valueRange = 0.05f..1.0f,
        colors = SliderDefaults.colors(
          thumbColor = Color(0xFF00C2FF),
          activeTrackColor = Color(0xFF00C2FF),
          inactiveTrackColor = Color(0xFF1E293B)
        ),
        modifier = Modifier.weight(1f)
      )

      Text(
        text = "${(intensity * 100).toInt()}%",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00C2FF),
        modifier = Modifier.width(36.dp),
        textAlign = TextAlign.End
      )
    }
  }
}

/**
 * Banner on the Main Hub showing active effect details, slider, quick presets and remove button.
 */
@Composable
private fun ActiveEffectControlBanner(
  activeEffectClip: EffectClip,
  targetVideoClipName: String?,
  onIntensityChange: (Float) -> Unit,
  onReset: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentIntensity = activeEffectClip.intensity
  val effectTitle = activeEffectClip.customName.ifBlank {
    activeEffectClip.effectType.displayName
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF0D1424),
    border = BorderStroke(1.dp, Color(0xFF00C2FF).copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF00C2FF).copy(alpha = 0.2f)
          ) {
            Text(
              text = "ACTIVE FX",
              fontSize = 8.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF00C2FF),
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
          }

          Text(
            text = effectTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          // Reset button
          IconButton(onClick = onReset, modifier = Modifier.size(24.dp)) {
            Icon(
              imageVector = Icons.Default.RestartAlt,
              contentDescription = "Reset",
              tint = TextSecondary,
              modifier = Modifier.size(15.dp)
            )
          }

          // Delete button
          IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Remove",
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(15.dp)
            )
          }
        }
      }

      // Slider Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Slider(
          value = currentIntensity,
          onValueChange = onIntensityChange,
          valueRange = 0.05f..1.0f,
          colors = SliderDefaults.colors(
            thumbColor = Color(0xFF00C2FF),
            activeTrackColor = Color(0xFF00C2FF),
            inactiveTrackColor = Color(0xFF1E293B)
          ),
          modifier = Modifier.weight(1f)
        )

        Text(
          text = "${(currentIntensity * 100).toInt()}%",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF00C2FF),
          modifier = Modifier.width(36.dp),
          textAlign = TextAlign.End
        )
      }
    }
  }
}

/**
 * Live procedural video frame scene with realistic lighting and subject silhouette,
 * wrapped by the real video effect renderer.
 */
@Composable
private fun LiveEffectThumbnailView(
  item: EffectItem,
  animTimeMsProvider: () -> Long,
  modifier: Modifier = Modifier
) {
  val previewClip = remember(item.effectType, item.defaultIntensity) {
    item.effectType?.let { effType ->
      EffectClip(
        id = "preview_${item.id}",
        effectType = effType,
        intensity = item.defaultIntensity,
        timelineStartMs = 0L,
        durationMs = 2000L
      )
    }
  }

  val activeEffectsList = remember(previewClip) {
    if (previewClip != null) listOf(previewClip) else emptyList()
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    drawIntoCanvas { composeCanvas ->
      val canvas = composeCanvas.nativeCanvas
      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) return@drawIntoCanvas

      val cx = w / 2f
      val cy = h / 2f
      val currentRelTime = (animTimeMsProvider() % 2000L)

      if (previewClip == null) {
        LiveThumbnailVideoScene.drawSampleVideoFrame(
          canvas = canvas,
          w = w,
          h = h,
          cx = cx,
          cy = cy,
          relTime = currentRelTime,
          colorFilter = null
        )
      } else {
        // 1. Calculate motion transform
        val motion = VideoEffectRenderer.calculateMotionTransform(activeEffectsList, currentRelTime)
        val hasMotion = motion.scaleX != 1f || motion.scaleY != 1f || motion.rotation != 0f ||
                        motion.translationX != 0f || motion.translationY != 0f

        if (hasMotion) {
          canvas.save()
          canvas.translate(cx + motion.translationX * w, cy + motion.translationY * h)
          canvas.rotate(motion.rotation)
          canvas.scale(motion.scaleX, motion.scaleY)
          canvas.translate(-cx, -cy)
        }

        // 2. Calculate ColorMatrix
        val effectColorMat = VideoEffectRenderer.calculateEffectColorMatrix(activeEffectsList, currentRelTime)
        val colorFilter: ColorFilter? = if (effectColorMat != null) ColorMatrixColorFilter(effectColorMat) else null

        // 3. Draw background sample video frame
        LiveThumbnailVideoScene.drawSampleVideoFrame(
          canvas = canvas,
          w = w,
          h = h,
          cx = cx,
          cy = cy,
          relTime = currentRelTime,
          colorFilter = colorFilter
        )

        // 4. Render procedural effect
        VideoEffectRenderer.renderSingleEffect(
          canvas = canvas,
          effect = previewClip,
          intensity = item.defaultIntensity,
          relTime = currentRelTime,
          width = w.toInt(),
          height = h.toInt()
        )

        // 5. Blur diffusion if applicable
        if (item.effectType == EffectType.BLUR || item.effectType == EffectType.MOTION_BLUR || item.effectType == EffectType.SOFT_FOCUS) {
          LiveThumbnailVideoScene.drawBlurOverlay(canvas, w, h, item.defaultIntensity)
        }

        if (hasMotion) {
          canvas.restore()
        }
      }
    }
  }
}

/**
 * Miniature video scene renderer for live effect thumbnail preview cards.
 */
private object LiveThumbnailVideoScene {
  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 2f
  }
  private val diffusePaint = Paint(Paint.ANTI_ALIAS_FLAG)

  fun drawSampleVideoFrame(
    canvas: android.graphics.Canvas,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    relTime: Long = 0L,
    colorFilter: ColorFilter? = null
  ) {
    val driftY = sin(relTime * 0.0018f) * (h * 0.04f)
    val bgShader = LinearGradient(
      0f, -driftY, 0f, h + driftY,
      intArrayOf(
        android.graphics.Color.rgb(20, 24, 46),
        android.graphics.Color.rgb(42, 38, 72),
        android.graphics.Color.rgb(180, 84, 52),
        android.graphics.Color.rgb(32, 20, 42)
      ),
      floatArrayOf(0f, 0.40f, 0.70f, 1f),
      Shader.TileMode.CLAMP
    )
    bgPaint.shader = bgShader
    bgPaint.colorFilter = colorFilter
    canvas.drawRect(0f, 0f, w, h, bgPaint)

    horizonPaint.colorFilter = colorFilter
    horizonPaint.color = android.graphics.Color.argb(55, 255, 175, 95)
    canvas.drawRect(0f, h * 0.69f, w, h * 0.715f, horizonPaint)

    subjectPaint.colorFilter = colorFilter
    subjectPaint.color = android.graphics.Color.rgb(12, 15, 24)

    val headRadius = w * 0.14f
    val headCenterY = cy - h * 0.09f
    canvas.drawCircle(cx, headCenterY, headRadius, subjectPaint)

    val path = Path()
    val neckWidth = w * 0.065f
    val neckBottomY = cy + h * 0.015f
    path.moveTo(cx - neckWidth, headCenterY + headRadius * 0.65f)
    path.lineTo(cx - neckWidth, neckBottomY)
    path.cubicTo(
      cx - w * 0.15f, cy + h * 0.045f,
      cx - w * 0.30f, cy + h * 0.12f,
      cx - w * 0.35f, h
    )
    path.lineTo(cx + w * 0.35f, h)
    path.cubicTo(
      cx + w * 0.30f, cy + h * 0.12f,
      cx + w * 0.15f, cy + h * 0.045f,
      cx + neckWidth, neckBottomY
    )
    path.lineTo(cx + neckWidth, headCenterY + headRadius * 0.65f)
    path.close()
    canvas.drawPath(path, subjectPaint)

    rimPaint.colorFilter = colorFilter
    rimPaint.color = android.graphics.Color.argb(80, 255, 195, 140)
    canvas.drawPath(path, rimPaint)
    canvas.drawCircle(cx, headCenterY, headRadius, rimPaint)
  }

  fun drawBlurOverlay(
    canvas: android.graphics.Canvas,
    w: Float,
    h: Float,
    intensity: Float
  ) {
    diffusePaint.shader = RadialGradient(
      w / 2f, h / 2f, w * 0.6f,
      intArrayOf(
        android.graphics.Color.argb((intensity * 140).toInt().coerceIn(0, 255), 220, 240, 255),
        android.graphics.Color.argb((intensity * 70).toInt().coerceIn(0, 255), 180, 200, 240)
      ),
      floatArrayOf(0f, 1f),
      Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, diffusePaint)
  }
}

/**
 * Main Effects Studio View Panel Composable.
 */
@Composable
fun VideoEffectsToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit
) {
  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }

  val allEffects = EffectsCatalog.VIDEO_EFFECTS + EffectsCatalog.BODY_EFFECTS

  val filteredEffects = remember(selectedCategory, searchQuery) {
    allEffects.filter { effect ->
      val matchesCategory = if (selectedCategory == "All") true else effect.category.contains(selectedCategory, ignoreCase = true)
      val matchesSearch = if (searchQuery.isBlank()) true else effect.name.contains(searchQuery, ignoreCase = true)
      matchesCategory && matchesSearch
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp)
      .background(StudioSurface)
      .padding(top = 10.dp)
  ) {
    // Header Handle & Title
    Box(
      modifier = Modifier
        .width(36.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(Color(0xFF444444))
        .align(Alignment.CenterHorizontally)
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Top Action Row (Title & Close)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Visual Effects",
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
      IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Category Tabs (Horizontal Scrollable)
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(EffectsCatalog.VIDEO_EFFECTS_CATEGORIES) { cat ->
        val isSelected = cat == selectedCategory
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color(0xFF222222))
            .clickable { selectedCategory = cat }
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Text(
            text = cat,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 4-Column Grid of 200 Effects
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      items(filteredEffects) { effect ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clickable {
              effect.effectType?.let { type ->
                // Apply effect via ViewModel engine
                viewModel.timelineEngine.applyEffectToCurrentClip(
                  effectType = type,
                  customName = effect.name,
                  intensity = effect.defaultIntensity
                )
              }
            }
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(72.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF252525))
              .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
          ) {
            Text(
              text = effect.name.take(2).uppercase(),
              color = effect.accentColor,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold
            )

            if (effect.tag.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .padding(4.dp)
                  .align(Alignment.TopStart)
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (effect.tag == "AI") Color(0xFF9C27B0) else Color(0xFFEF4444))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text(
                  text = effect.tag,
                  color = Color.White,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = effect.name,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
