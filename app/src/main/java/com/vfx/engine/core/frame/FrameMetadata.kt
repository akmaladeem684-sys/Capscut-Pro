package com.vfx.engine.core.frame

import com.vfx.engine.core.Microseconds

enum class PixelFormat {
  RGBA_8888,
  RGBA_F16,
  YUV_420_888,
  OES_EXTERNAL
}

enum class ColorSpace {
  BT709,
  BT2020,
  DISPLAY_P3,
  SRGB
}

enum class AlphaMode {
  STRAIGHT,
  PREMULTIPLIED
}

enum class Orientation(val degrees: Int, val isMirrored: Boolean = false) {
  UP(0),
  RIGHT(90),
  DOWN(180),
  LEFT(270),
  UP_MIRRORED(0, true),
  RIGHT_MIRRORED(90, true),
  DOWN_MIRRORED(180, true),
  LEFT_MIRRORED(270, true)
}

data class HdrMetadata(
  val isHdr: Boolean = false,
  val maxLuminanceNits: Float = 1000f,
  val minLuminanceNits: Float = 0.005f,
  val eotf: String = "PQ" // PQ or HLG
)

data class FrameMetadata(
  val pts: Microseconds,
  val duration: Microseconds,
  val sequenceIndex: Long,
  val width: Int,
  val height: Int,
  val format: PixelFormat = PixelFormat.RGBA_8888,
  val colorSpace: ColorSpace = ColorSpace.BT709,
  val alphaMode: AlphaMode = AlphaMode.PREMULTIPLIED,
  val orientation: Orientation = Orientation.UP,
  val hdrMetadata: HdrMetadata = HdrMetadata()
)
