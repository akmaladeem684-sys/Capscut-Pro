package com.example.data.presets

import com.example.domain.model.*

enum class PlaceholderType {
  VIDEO,
  IMAGE
}

data class MediaPlaceholder(
  val slotId: String,
  val label: String,
  val placeholderType: PlaceholderType,
  val requiredDurationMs: Long,
  val targetClipId: String,
  val defaultName: String = "Placeholder Media",
  val isOverlay: Boolean = false
)

data class TextPlaceholder(
  val slotId: String,
  val label: String,
  val targetClipId: String,
  val defaultText: String
)

data class VideoTemplate(
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val aspectRatio: AspectRatio,
  val resolution: Resolution = Resolution.RES_1080P,
  val fps: FrameRate = FrameRate.FPS_30,
  val durationMs: Long,
  val thumbnailGradientStart: Long = 0xFF1E293B,
  val thumbnailGradientEnd: Long = 0xFF0F172A,
  val iconEmoji: String = "🎬",
  val mediaPlaceholders: List<MediaPlaceholder> = emptyList(),
  val textPlaceholders: List<TextPlaceholder> = emptyList(),
  val audioTitle: String = "Soundtrack",
  val isPro: Boolean = false,
  val savedTimeline: Timeline? = null,
  val creatorId: String = "system_official",
  val creatorName: String = "Motion Studio",
  val creatorHandle: String = "@motionstudio",
  val creatorAvatarUrl: String? = null,
  val previewVideoUrl: String? = null,
  val previewThumbnailUrl: String? = null,
  val viewsCount: Long = 1240L,
  val usesCount: Long = 380L,
  val createdAt: Long = System.currentTimeMillis(),
  val createTimeline: (
    mediaReplacements: Map<String, String>,
    textReplacements: Map<String, String>
  ) -> Timeline = { _, _ -> savedTimeline ?: Timeline() }
) {
  fun createDefaultTimeline(): Timeline = savedTimeline ?: createTimeline(emptyMap(), emptyMap())
}

object TemplatesCatalog {
  val categories = listOf(
    "All",
    "Reels",
    "TikTok-style short videos",
    "YouTube Shorts",
    "Instagram",
    "Cinematic",
    "Business"
  )

  val templates: List<VideoTemplate> by lazy {
    listOf(
      // 1. Reels
      VideoTemplate(
        id = "reels_trending_vibe",
        title = "Dynamic Fast-Cut Reel",
        category = "Reels",
        description = "High-energy beat sync cuts with pop-in titles and vibrant pacing.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 9000L,
        thumbnailGradientStart = 0xFF833AB4,
        thumbnailGradientEnd = 0xFFFD1D1D,
        iconEmoji = "⚡",
        audioTitle = "Energetic Beat Drop",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Opening Hook Clip", PlaceholderType.VIDEO, 2000L, "reels_vid_1"),
          MediaPlaceholder("v2", "Action Motion Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_2"),
          MediaPlaceholder("v3", "Climax Outro Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_3")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Main Hook Title", "reels_txt_1", "TRENDING NOW"),
          TextPlaceholder("t2", "Call to Action", "reels_txt_2", "LINK IN BIO")
        ),
        createTimeline = { media, texts ->
          val t1Text = texts["t1"] ?: "TRENDING NOW"
          val t2Text = texts["t2"] ?: "LINK IN BIO"
          Timeline(
            videoClips = listOf(
              VideoClip(id = "reels_vid_1", name = "Hook", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 2000L),
              VideoClip(id = "reels_vid_2", name = "Action", uri = media["v2"] ?: "", timelineStartMs = 2000L, durationMs = 2500L),
              VideoClip(id = "reels_vid_3", name = "Outro", uri = media["v3"] ?: "", timelineStartMs = 4500L, durationMs = 2500L)
            ),
            textClips = listOf(
              TextClip(id = "reels_txt_1", text = t1Text, timelineStartMs = 500L, durationMs = 2500L, animationType = "Pop"),
              TextClip(id = "reels_txt_2", text = t2Text, timelineStartMs = 5000L, durationMs = 2000L, animationType = "Fade")
            )
          )
        }
      ),

      // 2. Cinematic
      VideoTemplate(
        id = "cinematic_letterbox_film",
        title = "Cinematic Story",
        category = "Cinematic",
        description = "Widescreen anamorphic cinematic visual with smooth transitions.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 10000L,
        thumbnailGradientStart = 0xFF000000,
        thumbnailGradientEnd = 0xFF434343,
        iconEmoji = "🎞️",
        audioTitle = "Cinematic Drone",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Establishing Shot", PlaceholderType.VIDEO, 5000L, "cine_vid_1"),
          MediaPlaceholder("v2", "Character Close-up", PlaceholderType.VIDEO, 5000L, "cine_vid_2")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Film Title", "cine_txt_1", "THE HORIZON")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "cine_vid_1", name = "Establishing", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 5000L),
              VideoClip(id = "cine_vid_2", name = "Character", uri = media["v2"] ?: "", timelineStartMs = 5000L, durationMs = 5000L)
            ),
            textClips = listOf(
              TextClip(id = "cine_txt_1", text = texts["t1"] ?: "THE HORIZON", timelineStartMs = 1000L, durationMs = 4000L)
            )
          )
        }
      ),

      // 3. TikTok & Shorts
      VideoTemplate(
        id = "shorts_flash_cut",
        title = "Fast Viral Shorts",
        category = "TikTok-style short videos",
        description = "Quick punchy visual beats and high impact text placement.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 6000L,
        thumbnailGradientStart = 0xFF00E5FF,
        thumbnailGradientEnd = 0xFF7000FF,
        iconEmoji = "🔥",
        audioTitle = "Viral Beat",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Punch In Clip", PlaceholderType.VIDEO, 3000L, "short_vid_1"),
          MediaPlaceholder("v2", "Reaction Clip", PlaceholderType.VIDEO, 3000L, "short_vid_2")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Reaction Text", "short_txt_1", "WAIT FOR IT...")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "short_vid_1", name = "Punch", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 3000L),
              VideoClip(id = "short_vid_2", name = "Reaction", uri = media["v2"] ?: "", timelineStartMs = 3000L, durationMs = 3000L)
            ),
            textClips = listOf(
              TextClip(id = "short_txt_1", text = texts["t1"] ?: "WAIT FOR IT...", timelineStartMs = 500L, durationMs = 3000L, animationType = "Pop")
            )
          )
        }
      )
    )
  }
}
