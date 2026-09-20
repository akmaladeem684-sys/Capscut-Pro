package com.example

import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.domain.model.VideoClip
import com.example.engine.media.RealMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantNewProjectPickerAndAutoAspectTest {

  @Test
  fun testAspectRatioDetection_StandardRatios() {
    // 16:9 Landscape (Full HD, 4K, 720p)
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(1920, 1080))
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(3840, 2160))
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(1280, 720))

    // 9:16 Vertical (Reels / TikTok / Shorts)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(1080, 1920))
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(2160, 3840))
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(720, 1280))

    // 1:1 Square (Instagram Square)
    assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromDimensions(1080, 1080))
    assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromDimensions(2048, 2048))

    // 4:3 Landscape
    assertEquals(AspectRatio.RATIO_4_3, AspectRatio.fromDimensions(1440, 1080))
    assertEquals(AspectRatio.RATIO_4_3, AspectRatio.fromDimensions(1024, 768))

    // 3:4 Classic Portrait
    assertEquals(AspectRatio.RATIO_3_4, AspectRatio.fromDimensions(1080, 1440))
    assertEquals(AspectRatio.RATIO_3_4, AspectRatio.fromDimensions(768, 1024))

    // 4:5 Instagram Portrait
    assertEquals(AspectRatio.RATIO_4_5, AspectRatio.fromDimensions(1080, 1350))
  }

  @Test
  fun testOrientationAwareRotationCalculation() {
    // A video recorded in 1920x1080 but with 90° rotation metadata is actually vertical (1080x1920 -> 9:16)
    val rawWidth = 1920
    val rawHeight = 1080
    val rotation = 90
    val effectiveWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
    val effectiveHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight

    assertEquals(1080, effectiveWidth)
    assertEquals(1920, effectiveHeight)
    assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromDimensions(effectiveWidth, effectiveHeight))

    // A video recorded in 1080x1920 with 270° rotation is actually landscape (1920x1080 -> 16:9)
    val rot270Width = 1080
    val rot270Height = 1920
    val rotation270 = 270
    val effW270 = if (rotation270 == 90 || rotation270 == 270) rot270Height else rot270Width
    val effH270 = if (rotation270 == 90 || rotation270 == 270) rot270Width else rot270Height

    assertEquals(1920, effW270)
    assertEquals(1080, effH270)
    assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromDimensions(effW270, effH270))
  }

  @Test
  fun testRealMediaMetadata_detectedAspectRatio() {
    val metaLandscape = RealMediaMetadata(
      durationMs = 5000L,
      width = 1920,
      height = 1080,
      rotationDegrees = 0,
      frameRate = 60f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_16_9, metaLandscape.detectedAspectRatio)

    val metaVertical = RealMediaMetadata(
      durationMs = 8000L,
      width = 1080,
      height = 1920,
      rotationDegrees = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_9_16, metaVertical.detectedAspectRatio)

    val metaSquare = RealMediaMetadata(
      durationMs = 4000L,
      width = 1080,
      height = 1080,
      rotationDegrees = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true,
      isVideo = true
    )
    assertEquals(AspectRatio.RATIO_1_1, metaSquare.detectedAspectRatio)
  }

  @Test
  fun testVideoClipCreation_SetsMainTrackTimelineCorrectly() {
    val durationMs = 12500L
    val clip = VideoClip(
      uri = "content://media/external/video/media/42",
      name = "Main Video",
      timelineStartMs = 0L,
      durationMs = durationMs,
      sourceStartMs = 0L,
      sourceEndMs = durationMs,
      isVideo = true,
      width = 1080,
      height = 1920,
      naturalRotation = 0,
      frameRate = 30f,
      mimeType = "video/mp4",
      hasAudio = true
    )

    assertEquals(0L, clip.timelineStartMs)
    assertEquals(durationMs, clip.durationMs)
    assertEquals(0L, clip.sourceStartMs)
    assertEquals(durationMs, clip.sourceEndMs)
    assertTrue(clip.isVideo)
    assertEquals(1080, clip.width)
    assertEquals(1920, clip.height)
  }
}
