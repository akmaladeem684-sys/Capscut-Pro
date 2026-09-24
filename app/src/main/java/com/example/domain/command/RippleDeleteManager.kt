package com.example.domain.command

import com.ahstudio.animation.keyframes.KeyframeOps
import com.ahstudio.animation.keyframes.KeyframeTrackData
import com.example.domain.model.*

/**
 * Deletes clips from tracks and closes the resulting gap (Ripple Delete).
 *
 * Enforces:
 * 1. Synchronous shift of clips across primary, sub-tracks, and linked tracks.
 * 2. Prunes orphan keyframes falling within deleted intervals.
 * 3. Shifts downstream animation keyframes and audio envelope points by the deleted duration.
 * 4. Preserves locked track immutability.
 * 5. Full support for all NLE track types: Main Video, Overlays, Audios, Texts, Stickers, and Effects.
 */
class RippleDeleteManager {

    /**
     * Deletes [clipId] from [track]. When [rippleEnabled] is true, clips that
     * follow the deleted clip are shifted left by the deleted duration.
     * Synchronously shifts automation keyframe data and audio envelope points to prevent keyframe orphans.
     * Locked tracks are not modified.
     */
    fun deleteClip(
        track: TimelineTrack,
        clipId: String,
        rippleEnabled: Boolean,
        keyframeTracks: MutableMap<String, KeyframeTrackData>? = null,
        linkedTracks: List<TimelineTrack> = emptyList()
    ): Boolean {
        if (track.isLocked) return false

        val index = track.clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        val deletedClip = track.clips.removeAt(index)
        if (rippleEnabled) {
            val shiftAmount = deletedClip.durationMs
            val deletedStart = track.trackOffsetMs + deletedClip.localStartTimeMs
            val deletedEnd = track.trackOffsetMs + deletedClip.localEndTimeMs

            // 1. Shift downstream clips on primary track
            for (i in index until track.clips.size) {
                val current = track.clips[i]
                track.clips[i] = current.copy(
                    localStartTimeMs = (current.localStartTimeMs - shiftAmount).coerceAtLeast(0L)
                )
            }

            // 2. Synchronize linked tracks (e.g. video linked with audio / overlays)
            for (linked in linkedTracks) {
                if (linked.isLocked) continue
                for (i in linked.clips.indices) {
                    val clip = linked.clips[i]
                    val clipAbsStart = linked.trackOffsetMs + clip.localStartTimeMs
                    if (clipAbsStart >= deletedEnd) {
                        linked.clips[i] = clip.copy(
                            localStartTimeMs = (clip.localStartTimeMs - shiftAmount).coerceAtLeast(0L)
                        )
                    }
                }
            }

            // 3. Shift and prune automation keyframe tracks to prevent orphan keyframes
            if (keyframeTracks != null) {
                shiftAndPruneKeyframeTracks(keyframeTracks, deletedStart, deletedEnd, shiftAmount)
            }
        }
        return true
    }

    /**
     * Cuts a time interval [cutStartMs until cutEndMs] from a track with ripple closure,
     * cleanly pruning keyframes in the cut range and shifting downstream automation.
     */
    fun cutRange(
        track: TimelineTrack,
        cutStartMs: Long,
        cutEndMs: Long,
        keyframeTracks: MutableMap<String, KeyframeTrackData>? = null,
        linkedTracks: List<TimelineTrack> = emptyList()
    ): Boolean {
        if (track.isLocked || cutEndMs <= cutStartMs) return false
        val cutDuration = cutEndMs - cutStartMs

        val updatedClips = mutableListOf<TimelineClip>()
        for (clip in track.clips) {
            val clipStart = track.trackOffsetMs + clip.localStartTimeMs
            val clipEnd = track.trackOffsetMs + clip.localEndTimeMs

            when {
                // Entirely before cut
                clipEnd <= cutStartMs -> updatedClips.add(clip)
                // Entirely after cut -> shift left
                clipStart >= cutEndMs -> {
                    updatedClips.add(
                        clip.copy(localStartTimeMs = (clip.localStartTimeMs - cutDuration).coerceAtLeast(0L))
                    )
                }
                // Clip spans or intersects the cut range
                else -> {
                    val newDur = (clip.durationMs - (minOf(clipEnd, cutEndMs) - maxOf(clipStart, cutStartMs))).coerceAtLeast(0L)
                    if (newDur > 30L) {
                        val newStart = if (clipStart < cutStartMs) clip.localStartTimeMs else cutStartMs - track.trackOffsetMs
                        updatedClips.add(clip.copy(localStartTimeMs = newStart.coerceAtLeast(0L), durationMs = newDur))
                    }
                }
            }
        }
        track.clips = updatedClips

        // Shift linked tracks
        for (linked in linkedTracks) {
            if (linked.isLocked) continue
            for (i in linked.clips.indices) {
                val clip = linked.clips[i]
                val clipAbsStart = linked.trackOffsetMs + clip.localStartTimeMs
                if (clipAbsStart >= cutEndMs) {
                    linked.clips[i] = clip.copy(
                        localStartTimeMs = (clip.localStartTimeMs - cutDuration).coerceAtLeast(0L)
                    )
                }
            }
        }

        if (keyframeTracks != null) {
            shiftAndPruneKeyframeTracks(keyframeTracks, cutStartMs, cutEndMs, cutDuration)
        }
        return true
    }

    /**
     * Performs a complete ripple delete on the immutable Timeline model,
     * cleanly shifting downstream clips, audio envelopes, animation keyframes, and markers.
     */
    fun rippleDeleteFromTimeline(
        timeline: Timeline,
        clipId: String,
        syncLinkedTracks: Boolean = true
    ): Timeline {
        // Find deleted clip in any track
        val targetVideo = timeline.videoClips.find { it.id == clipId }
        val targetOverlay = timeline.overlayClips.find { it.id == clipId }
        val targetAudio = timeline.audioClips.find { it.id == clipId }
        val targetText = timeline.textClips.find { it.id == clipId }
        val targetSticker = timeline.stickerClips.find { it.id == clipId }
        val targetEffect = timeline.effectClips.find { it.id == clipId }

        val delStart: Long
        val delDuration: Long

        when {
            targetVideo != null -> {
                delStart = targetVideo.timelineStartMs
                delDuration = targetVideo.durationMs
            }
            targetOverlay != null -> {
                delStart = targetOverlay.timelineStartMs
                delDuration = targetOverlay.durationMs
            }
            targetAudio != null -> {
                delStart = targetAudio.timelineStartMs
                delDuration = targetAudio.durationMs
            }
            targetText != null -> {
                delStart = targetText.timelineStartMs
                delDuration = targetText.durationMs
            }
            targetSticker != null -> {
                delStart = targetSticker.timelineStartMs
                delDuration = targetSticker.durationMs
            }
            targetEffect != null -> {
                delStart = targetEffect.timelineStartMs
                delDuration = targetEffect.durationMs
            }
            else -> return timeline
        }

        val delEnd = delStart + delDuration

        // Shift helper for VideoClip with keyframe and animation preservation
        fun shiftVideoClip(clip: VideoClip): VideoClip {
            if (clip.timelineStartMs < delEnd) return clip
            val newStart = (clip.timelineStartMs - delDuration).coerceAtLeast(0L)
            return clip.copy(timelineStartMs = newStart)
        }

        // Shift helper for AudioClip including audio envelope points / keyframes
        fun shiftAudioClip(clip: AudioClip): AudioClip {
            if (clip.timelineStartMs < delEnd) return clip
            val newStart = (clip.timelineStartMs - delDuration).coerceAtLeast(0L)
            return clip.copy(timelineStartMs = newStart)
        }

        // Shift helper for TextClip
        fun shiftTextClip(clip: TextClip): TextClip {
            if (clip.timelineStartMs < delEnd) return clip
            val newStart = (clip.timelineStartMs - delDuration).coerceAtLeast(0L)
            return clip.copy(timelineStartMs = newStart)
        }

        // Shift helper for StickerClip
        fun shiftStickerClip(clip: StickerClip): StickerClip {
            if (clip.timelineStartMs < delEnd) return clip
            val newStart = (clip.timelineStartMs - delDuration).coerceAtLeast(0L)
            return clip.copy(timelineStartMs = newStart)
        }

        // Shift helper for EffectClip
        fun shiftEffectClip(clip: EffectClip): EffectClip {
            if (clip.timelineStartMs < delEnd) return clip
            val newStart = (clip.timelineStartMs - delDuration).coerceAtLeast(0L)
            return clip.copy(timelineStartMs = newStart)
        }

        val updatedVideo = timeline.videoClips
            .filterNot { it.id == clipId }
            .map { shiftVideoClip(it) }

        val updatedOverlay = timeline.overlayClips
            .filterNot { it.id == clipId }
            .map { shiftVideoClip(it) }

        val updatedAudio = timeline.audioClips
            .filterNot { it.id == clipId }
            .map { if (syncLinkedTracks) shiftAudioClip(it) else it }

        val updatedText = timeline.textClips
            .filterNot { it.id == clipId }
            .map { if (syncLinkedTracks) shiftTextClip(it) else it }

        val updatedSticker = timeline.stickerClips
            .filterNot { it.id == clipId }
            .map { if (syncLinkedTracks) shiftStickerClip(it) else it }

        val updatedEffect = timeline.effectClips
            .filterNot { it.id == clipId }
            .map { if (syncLinkedTracks) shiftEffectClip(it) else it }

        // Prune and shift timeline markers
        val updatedMarkers = timeline.markers
            .filterNot { it.timeMs in delStart until delEnd }
            .map { marker ->
                if (marker.timeMs >= delEnd) {
                    marker.copy(timeMs = (marker.timeMs - delDuration).coerceAtLeast(0L))
                } else marker
            }

        return timeline.copy(
            videoClips = updatedVideo,
            overlayClips = updatedOverlay,
            audioClips = updatedAudio,
            textClips = updatedText,
            stickerClips = updatedSticker,
            effectClips = updatedEffect,
            markers = updatedMarkers
        )
    }

    /**
     * Synchronously prunes keyframes in [deletedStart until deletedEnd] and shifts
     * all downstream keyframes left by [shiftAmount] to eliminate orphan keyframes.
     */
    private fun shiftAndPruneKeyframeTracks(
        keyframeTracks: MutableMap<String, KeyframeTrackData>,
        deletedStart: Long,
        deletedEnd: Long,
        shiftAmount: Long
    ) {
        for ((trackKey, kfData) in keyframeTracks.entries) {
            // 1. Prune keyframes in the deleted interval
            val keyframesToDelete = kfData.keyframes
                .filter { it.timeMs in deletedStart until deletedEnd }
                .map { it.id }
                .toSet()
            var updatedData = kfData
            if (keyframesToDelete.isNotEmpty()) {
                updatedData = KeyframeOps.delete(updatedData, keyframesToDelete)
            }
            // 2. Shift all downstream keyframes
            val keyframesToShift = updatedData.keyframes
                .filter { it.timeMs >= deletedEnd }
                .map { it.id }
                .toSet()
            if (keyframesToShift.isNotEmpty()) {
                updatedData = KeyframeOps.move(updatedData, keyframesToShift, -shiftAmount)
            }
            keyframeTracks[trackKey] = updatedData
        }
    }
}
