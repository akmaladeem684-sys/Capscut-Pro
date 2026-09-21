package com.universal.engine.frame

/** Color description carried end-to-end. Immutable. */
data class ColorInfo(
    val primaries: Primaries = Primaries.BT709,
    val transfer: Transfer = Transfer.SRGB,
    val matrix: MatrixCoefficients = MatrixCoefficients.BT709,
    val range: Range = Range.LIMITED,
    val maxContentLightLevel: Float = 0f,      // HDR metadata, 0 = SDR
    val maxFrameAverageLightLevel: Float = 0f
) {
    enum class Primaries { BT601, BT709, BT2020 }
    enum class Transfer { SRGB, GAMMA_2_2, PQ, HLG }
    enum class MatrixCoefficients { BT601, BT709, BT2020 }
    enum class Range { LIMITED, FULL }
    val isHdr: Boolean get() = transfer == Transfer.PQ || transfer == Transfer.HLG
}

/** Immutable per-frame metadata. Large buffers are NEVER copied — only referenced. */
data class FrameMetadata(
    val width: Int,
    val height: Int,
    val timestampUs: Long,
    val durationUs: Long,
    val frameIndex: Long,
    val pixelFormat: PixelFormat,
    val colorInfo: ColorInfo,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    /** 0, 90, 180, 270 — as reported by the container/metadata. */
    val rotationDegrees: Int = 0,
    val mirrored: Boolean = false,
    /** Sample transform (crop etc.) as GL texture-space matrix, row-major 4x4. */
    val transform: FloatArray = IDENTITY_4X4,
    val isValid: Boolean = true
) {
    enum class PixelFormat { TEXTURE_2D_RGBA, TEXTURE_EXTERNAL_OES, RGBA_FP16 }
    enum class AlphaMode { OPAQUE, PREMULTIPLIED, STRAIGHT }

    override fun equals(other: Any?): Boolean = this === other ||
        (other is FrameMetadata && other.width == width && other.height == height &&
         other.timestampUs == timestampUs && other.frameIndex == frameIndex &&
         other.pixelFormat == pixelFormat && other.rotationDegrees == rotationDegrees)

    override fun hashCode(): Int = (width * 31 + height) * 31 + timestampUs.hashCode()

    companion object { val IDENTITY_4X4 = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f) }
}
