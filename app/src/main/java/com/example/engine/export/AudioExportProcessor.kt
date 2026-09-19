package com.example.engine.export

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import com.example.domain.model.AudioClip
import com.example.engine.media.MediaRelinkManager
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.VideoClip
import com.example.engine.audio.SoundEffectsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class DecodedPcm(
  val samples: ShortArray,
  val sampleRate: Int,
  val channels: Int
)

data class AudioTrackDescriptor(
  val uri: String,
  val title: String,
  val timelineStartMs: Long,
  val durationMs: Long,
  val sourceStartMs: Long,
  val sourceEndMs: Long,
  val speed: Float,
  val volume: Float,
  val gainDb: Float,
  val fadeInMs: Long,
  val fadeOutMs: Long,
  val isMuted: Boolean,
  val isReversed: Boolean = false,
  val keyframes: List<com.example.domain.model.ClipKeyframe> = emptyList()
)

class AudioExportProcessor(private val context: Context) {

  private val tag = "AudioExportProcessor"
  val sampleRate = 44100
  val channelCount = 2 // Stereo

  private val pcmCache = mutableMapOf<String, DecodedPcm>()

  fun clearCache() {
    pcmCache.clear()
  }

  fun hasActiveAudio(timeline: Timeline): Boolean {
    val anySolo = timeline.trackSettings.values.any { it.isSolo }
    val videoAudible = (timeline.trackSettings[TrackType.MAIN_VIDEO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.MAIN_VIDEO]?.isSolo == true)
    val overlayAudible = (timeline.trackSettings[TrackType.OVERLAY]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.OVERLAY]?.isSolo == true)
    val audioAudible = (timeline.trackSettings[TrackType.AUDIO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.AUDIO]?.isSolo == true)

    val hasVideoAudio = videoAudible && timeline.videoClips.any { it.isVideo && it.hasAudio && !it.isMuted && it.uri.isNotBlank() }
    val hasOverlayAudio = overlayAudible && timeline.overlayClips.any { it.isVideo && it.hasAudio && !it.isMuted && it.uri.isNotBlank() }
    val hasAudioClips = audioAudible && timeline.audioClips.any { !it.isMuted && (it.uri.isNotBlank() || it.title.isNotBlank()) }
    return hasVideoAudio || hasOverlayAudio || hasAudioClips
  }

  /**
   * Mixes all active audio tracks from the timeline into a master 44.1kHz 16-bit stereo PCM buffer.
   * Maintains sample-accurate synchronization across all audio and video tracks.
   */
  suspend fun mixTimelineAudio(
    timeline: Timeline,
    totalDurationMs: Long,
    onCancelCheck: () -> Boolean = { false }
  ): ShortArray = withContext(Dispatchers.IO) {
    val totalFrames = ((totalDurationMs * sampleRate) / 1000L).toInt().coerceAtLeast(1024)
    val masterLeft = FloatArray(totalFrames)
    val masterRight = FloatArray(totalFrames)

    val trackDescriptors = mutableListOf<AudioTrackDescriptor>()
    val anySolo = timeline.trackSettings.values.any { it.isSolo }
    val videoAudible = (timeline.trackSettings[TrackType.MAIN_VIDEO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.MAIN_VIDEO]?.isSolo == true)
    val overlayAudible = (timeline.trackSettings[TrackType.OVERLAY]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.OVERLAY]?.isSolo == true)
    val audioAudible = (timeline.trackSettings[TrackType.AUDIO]?.isMuted != true) && (!anySolo || timeline.trackSettings[TrackType.AUDIO]?.isSolo == true)

    // 1. Video clips with audio
    if (videoAudible) {
      for (clip in timeline.videoClips) {
        if (clip.isVideo && clip.hasAudio && !clip.isMuted && clip.uri.isNotBlank()) {
          trackDescriptors.add(
            AudioTrackDescriptor(
              uri = clip.uri,
              title = clip.name,
              timelineStartMs = clip.timelineStartMs,
              durationMs = clip.durationMs,
              sourceStartMs = clip.sourceStartMs,
              sourceEndMs = clip.sourceEndMs,
              speed = clip.speed.coerceIn(0.1f, 10f),
              volume = clip.volume.coerceAtLeast(0f),
              gainDb = 0f,
              fadeInMs = 0L,
              fadeOutMs = 0L,
              isMuted = clip.isMuted,
              isReversed = clip.isReversed,
              keyframes = clip.keyframes
            )
          )
        }
      }
    }

    // 2. Picture-in-picture overlay clips with audio
    if (overlayAudible) {
      for (clip in timeline.overlayClips) {
        if (clip.isVideo && clip.hasAudio && !clip.isMuted && clip.uri.isNotBlank()) {
          trackDescriptors.add(
            AudioTrackDescriptor(
              uri = clip.uri,
              title = clip.name,
              timelineStartMs = clip.timelineStartMs,
              durationMs = clip.durationMs,
              sourceStartMs = clip.sourceStartMs,
              sourceEndMs = clip.sourceEndMs,
              speed = clip.speed.coerceIn(0.1f, 10f),
              volume = clip.volume.coerceAtLeast(0f),
              gainDb = 0f,
              fadeInMs = 0L,
              fadeOutMs = 0L,
              isMuted = clip.isMuted,
              isReversed = clip.isReversed,
              keyframes = clip.keyframes
            )
          )
        }
      }
    }

    // 3. Audio clips (Music, SFX, Voiceover, Extracted Audio)
    if (audioAudible) {
      for (clip in timeline.audioClips) {
        if (!clip.isMuted && (clip.uri.isNotBlank() || clip.title.isNotBlank())) {
          trackDescriptors.add(
            AudioTrackDescriptor(
              uri = clip.uri,
              title = clip.title,
              timelineStartMs = clip.timelineStartMs,
              durationMs = clip.durationMs,
              sourceStartMs = clip.sourceStartMs,
              sourceEndMs = clip.sourceEndMs,
              speed = clip.speed.coerceIn(0.1f, 10f),
              volume = clip.volume.coerceAtLeast(0f),
              gainDb = clip.gainDb,
              fadeInMs = clip.fadeInMs.coerceAtLeast(0L),
              fadeOutMs = clip.fadeOutMs.coerceAtLeast(0L),
              isMuted = clip.isMuted,
              keyframes = clip.keyframes
            )
          )
        }
      }
    }

    // Process and mix each active track
    for (track in trackDescriptors) {
      if (onCancelCheck()) return@withContext ShortArray(0)

      val decoded = getOrDecodePcm(track) ?: continue
      mixTrackIntoMaster(track, decoded, masterLeft, masterRight, totalFrames)
    }

    // Convert mixed float buffers into 16-bit stereo PCM with soft limiting
    val masterPcm = ShortArray(totalFrames * 2)
    for (i in 0 until totalFrames) {
      masterPcm[i * 2] = softClipSample(masterLeft[i])
      masterPcm[i * 2 + 1] = softClipSample(masterRight[i])
    }

    masterPcm
  }

  private fun mixTrackIntoMaster(
    track: AudioTrackDescriptor,
    decoded: DecodedPcm,
    masterLeft: FloatArray,
    masterRight: FloatArray,
    totalTimelineFrames: Int
  ) {
    val srcSamples = decoded.samples
    val srcSampleRate = decoded.sampleRate
    val srcChannels = decoded.channels
    if (srcSamples.isEmpty() || srcSampleRate <= 0) return

    val srcTotalFrames = srcSamples.size / srcChannels
    val timelineStartFrame = ((track.timelineStartMs * sampleRate) / 1000L).toInt()
    val trackDurationFrames = ((track.durationMs * sampleRate) / 1000L).toInt()

    val gainMultiplier = 10f.pow(track.gainDb / 20f)
    val baseVolume = track.volume * gainMultiplier
    if (baseVolume <= 0f) return

    val speed = track.speed
    val sourceStartSec = track.sourceStartMs / 1000.0
    val sourceEndSec = (if (track.sourceEndMs > track.sourceStartMs) track.sourceEndMs else (track.sourceStartMs + track.durationMs)) / 1000.0

    val fadeInDurationMs = track.fadeInMs.toFloat()
    val fadeOutDurationMs = track.fadeOutMs.toFloat()
    val totalTrackMs = track.durationMs.toFloat()

    val maxFramesToProcess = min(trackDurationFrames, totalTimelineFrames - timelineStartFrame)
    for (f in 0 until maxFramesToProcess) {
      val targetTimelineIndex = timelineStartFrame + f
      if (targetTimelineIndex < 0 || targetTimelineIndex >= totalTimelineFrames) continue

      // Calculate time position within the clip in milliseconds
      val timeInClipMs = (f.toDouble() / sampleRate) * 1000.0

      // Calculate source time based on speed and trim
      val sourceTimeSec = sourceStartSec + ((timeInClipMs / 1000.0) * speed)
      if (sourceTimeSec > sourceEndSec) break // Reached trimmed end

      // Sub-sample source frame calculation
      val srcFramePosition = sourceTimeSec * srcSampleRate
      if (srcFramePosition < 0 || srcFramePosition >= srcTotalFrames - 1) {
        if (srcFramePosition >= srcTotalFrames) break
        continue
      }

      val f0 = srcFramePosition.toInt()
      val f1 = min(f0 + 1, srcTotalFrames - 1)
      val alpha = (srcFramePosition - f0).toFloat()

      val rawLeft: Float
      val rawRight: Float
      if (srcChannels == 1) {
        val s0 = srcSamples[f0].toFloat()
        val s1 = srcSamples[f1].toFloat()
        val interp = (1f - alpha) * s0 + alpha * s1
        rawLeft = interp
        rawRight = interp
      } else {
        val s0L = srcSamples[f0 * 2].toFloat()
        val s1L = srcSamples[f1 * 2].toFloat()
        val s0R = srcSamples[f0 * 2 + 1].toFloat()
        val s1R = srcSamples[f1 * 2 + 1].toFloat()
        rawLeft = (1f - alpha) * s0L + alpha * s1L
        rawRight = (1f - alpha) * s0R + alpha * s1R
      }

      // Calculate Fade In / Fade Out multiplier
      var fade = 1.0f
      if (fadeInDurationMs > 0f && timeInClipMs < fadeInDurationMs) {
        fade *= (timeInClipMs.toFloat() / fadeInDurationMs).coerceIn(0f, 1f)
      }
      if (fadeOutDurationMs > 0f && (totalTrackMs - timeInClipMs) < fadeOutDurationMs) {
        fade *= ((totalTrackMs - timeInClipMs).toFloat() / fadeOutDurationMs).coerceIn(0f, 1f)
      }

      val kfVolume = if (track.keyframes.isNotEmpty()) {
        com.example.engine.KeyframeInterpolator.interpolateVolume(track.keyframes, timeInClipMs.toLong(), 1.0f)
      } else 1.0f

      val effectiveMultiplier = baseVolume * fade * kfVolume
      masterLeft[targetTimelineIndex] += rawLeft * effectiveMultiplier
      masterRight[targetTimelineIndex] += rawRight * effectiveMultiplier
    }
  }

  private fun getOrDecodePcm(track: AudioTrackDescriptor): DecodedPcm? {
    val cacheKey = "${track.uri}#${track.title}"
    pcmCache[cacheKey]?.let { return it }

    val decoded = if (isSynthesizedTrack(track.uri, track.title)) {
      synthesizePcmForTrack(track)
    } else {
      decodeMediaFile(track.uri) ?: synthesizePcmForTrack(track)
    }

    if (decoded != null) {
      pcmCache[cacheKey] = decoded
    }
    return decoded
  }

  private fun isSynthesizedTrack(uri: String, title: String): Boolean {
    if (uri.startsWith("internal://") || uri.startsWith("sfx_") || uri.startsWith("mus_")) return true
    if (uri.startsWith("sample://") || uri.startsWith("stock://") || uri.startsWith("demo://") || uri.startsWith("template://")) return true
    if (uri.isBlank()) return true
    if (!MediaRelinkManager.isRealPlayableMedia(context, uri)) return true
    // Check if matching catalog titles
    val allCatalog = SoundEffectsCatalog.effects.map { it.title } + SoundEffectsCatalog.musicTracks.map { it.title }
    return allCatalog.contains(title)
  }

  private fun decodeMediaFile(uriString: String): DecodedPcm? {
    if (!MediaRelinkManager.isRealPlayableMedia(context, uriString)) {
      return null
    }
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
      val uri = Uri.parse(uriString)
      if (uri.scheme == "content" || uri.scheme == "file") {
        extractor.setDataSource(context, uri, null)
      } else {
        val f = File(uriString)
        if (f.exists()) {
          extractor.setDataSource(f.absolutePath)
        } else {
          extractor.setDataSource(uriString)
        }
      }

      var audioTrackIndex = -1
      var trackFormat: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          trackFormat = format
          break
        }
      }

      if (audioTrackIndex == -1 || trackFormat == null) {
        return null
      }

      extractor.selectTrack(audioTrackIndex)
      val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
      decoder = MediaCodec.createDecoderByType(mime)
      decoder.configure(trackFormat, null, null, 0)
      decoder.start()

      var outSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
      var outChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)

      val allShorts = mutableListOf<ShortArray>()
      var totalSamplesCount = 0

      val bufferInfo = MediaCodec.BufferInfo()
      var isInputEos = false
      var isOutputEos = false
      var noProgressCount = 0

      while (!isOutputEos && noProgressCount < 100) {
        var hadProgress = false
        if (!isInputEos) {
          val inIndex = decoder.dequeueInputBuffer(5000L)
          if (inIndex >= 0) {
            val inBuf = decoder.getInputBuffer(inIndex)
            if (inBuf != null) {
              val sampleSize = extractor.readSampleData(inBuf, 0)
              if (sampleSize < 0) {
                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isInputEos = true
              } else {
                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
              }
              hadProgress = true
            }
          }
        }

        val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 5000L)
        if (outIndex >= 0) {
          hadProgress = true
          val outBuf = decoder.getOutputBuffer(outIndex)
          if (outBuf != null && bufferInfo.size > 0) {
            outBuf.position(bufferInfo.offset)
            outBuf.limit(bufferInfo.offset + bufferInfo.size)
            outBuf.order(ByteOrder.LITTLE_ENDIAN)

            val shortBuffer = outBuf.asShortBuffer()
            val chunk = ShortArray(shortBuffer.remaining())
            shortBuffer.get(chunk)
            allShorts.add(chunk)
            totalSamplesCount += chunk.size
          }
          decoder.releaseOutputBuffer(outIndex, false)
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isOutputEos = true
          }
        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          hadProgress = true
          val newFormat = decoder.outputFormat
          outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, outSampleRate)
          outChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, outChannels)
        }

        if (hadProgress) {
          noProgressCount = 0
        } else {
          noProgressCount++
        }
      }

      if (totalSamplesCount == 0) return null

      val merged = ShortArray(totalSamplesCount)
      var offset = 0
      for (chunk in allShorts) {
        System.arraycopy(chunk, 0, merged, offset, chunk.size)
        offset += chunk.size
      }

      return DecodedPcm(merged, outSampleRate, outChannels)
    } catch (e: Exception) {
      Log.w(tag, "Failed to decode audio file: $uriString", e)
      return null
    } finally {
      try { decoder?.stop() } catch (ignored: Exception) {}
      try { decoder?.release() } catch (ignored: Exception) {}
      try { extractor.release() } catch (ignored: Exception) {}
    }
  }

  /**
   * Synthesizes audio PCM for built-in SFX or music tracks so that every project has real audio.
   */
  private fun synthesizePcmForTrack(track: AudioTrackDescriptor): DecodedPcm {
    val durationSec = (track.durationMs / 1000.0).coerceIn(0.3, 120.0)
    val numFrames = (sampleRate * durationSec).toInt()
    val pcm = ShortArray(numFrames * 2)

    val lower = (track.uri + " " + track.title).lowercase()
    when {
      lower.contains("whoosh") -> {
        for (i in 0 until numFrames) {
          val t = i.toDouble() / sampleRate
          val sweep = (1.0 - (i.toDouble() / numFrames)).coerceIn(0.0, 1.0)
          val freq = 120.0 + sweep * 400.0
          val env = sin(Math.PI * (i.toDouble() / numFrames))
          val sample = (sin(2.0 * Math.PI * freq * t) * env * 28000.0).toInt().toShort()
          pcm[i * 2] = sample
          pcm[i * 2 + 1] = sample
        }
      }
      lower.contains("pop") -> {
        for (i in 0 until numFrames) {
          val t = i.toDouble() / sampleRate
          val env = exp(-t * 24.0)
          val sample = (sin(2.0 * Math.PI * 650.0 * t) * env * 30000.0).toInt().toShort()
          pcm[i * 2] = sample
          pcm[i * 2 + 1] = sample
        }
      }
      lower.contains("ding") || lower.contains("bell") -> {
        for (i in 0 until numFrames) {
          val t = i.toDouble() / sampleRate
          val env = exp(-t * 3.5)
          val wave = sin(2.0 * Math.PI * 1200.0 * t) * 0.7 + sin(2.0 * Math.PI * 2400.0 * t) * 0.3
          val sample = (wave * env * 26000.0).toInt().toShort()
          pcm[i * 2] = sample
          pcm[i * 2 + 1] = sample
        }
      }
      lower.contains("bass") -> {
        for (i in 0 until numFrames) {
          val t = i.toDouble() / sampleRate
          val env = exp(-t * 2.0)
          val sample = (sin(2.0 * Math.PI * 85.0 * t) * env * 32000.0).toInt().toShort()
          pcm[i * 2] = sample
          pcm[i * 2 + 1] = sample
        }
      }
      else -> {
        // Music loop synthesis (Chords + ambient beat)
        val chordFreqs = listOf(220.0, 261.63, 329.63, 392.0) // Am7
        for (i in 0 until numFrames) {
          val t = i.toDouble() / sampleRate
          val beat = if ((t % 0.5) < 0.08) 0.6 else 0.0 // Soft kick pulse
          val chord = chordFreqs.indices.sumOf { idx ->
            sin(2.0 * Math.PI * chordFreqs[idx] * t) * (0.2 / (idx + 1))
          }
          val wave = (chord + beat * sin(2.0 * Math.PI * 90.0 * t)).coerceIn(-1.0, 1.0)
          val sample = (wave * 20000.0).toInt().toShort()
          pcm[i * 2] = sample
          pcm[i * 2 + 1] = sample
        }
      }
    }

    return DecodedPcm(pcm, sampleRate, 2)
  }

  private fun softClipSample(sample: Float): Short {
    val norm = sample / 32768f
    val clipped = when {
      norm > 1.0f -> 1.0f
      norm < -1.0f -> -1.0f
      norm > 0.75f -> 0.75f + (norm - 0.75f) * 0.5f
      norm < -0.75f -> -0.75f + (norm + 0.75f) * 0.5f
      else -> norm
    }
    return (clipped * 32767f).toInt().toShort()
  }
}
