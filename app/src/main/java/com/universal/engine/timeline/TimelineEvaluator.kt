package com.universal.engine.timeline

import com.universal.engine.animation.AnimationEvaluator
import com.universal.engine.compositing.Layer
import com.universal.engine.frame.GpuFrame

/**
 * THE single evaluation path shared by preview and export.
 * Given a timeline timestamp, resolves which clips are active, asks FrameProviders for
 * their source frames, evaluates animations, and emits a deterministic layer list.
 */
class TimelineEvaluator(private val animationEvaluator: AnimationEvaluator = AnimationEvaluator()) {

    interface FrameProvider {
        fun frameAt(mediaId: String, sourceTimestampUs: Long): GpuFrame?
        fun release()
    }

    fun evaluate(
        timeline: Timeline,
        timestampUs: Long,
        providers: Map<String, FrameProvider>,
        canvasWidth: Int,
        canvasHeight: Int
    ): List<Layer> {
        val layers = mutableListOf<Layer>()
        for (track in timeline.tracks.sortedBy { it.index }) {
            for (clip in track.clips) {
                if (!clip.contains(timestampUs)) continue
                val provider = providers[clip.mediaId] ?: continue
                val sourceUs = clip.sourceTimestampAt(timestampUs)
                val frame = provider.frameAt(clip.mediaId, sourceUs) ?: continue
                val transform = animationEvaluator.evaluateTransform(clip.animatedTransform, timestampUs, clip.baseTransform)
                val opacity = clip.animatedOpacity?.evaluate(timestampUs) ?: transform.opacity
                layers.add(Layer(
                    source = frame,
                    transform = transform.copy(opacity = opacity),
                    blend = clip.blend,
                    zIndex = track.index
                ))
            }
        }
        return layers
    }
}
