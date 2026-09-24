package com.example.domain.command

import com.ahstudio.animation.keyframes.KeyframeOps
import com.ahstudio.animation.keyframes.KeyframeTrackData
import com.example.domain.model.TimelineTrack

/**
 * Deletes clips from a track and optionally closes the resulting gap.
 *
 * Clip positions remain track-local. Ripple deletion therefore changes only
 * the local positions of clips after the deleted clip; the track offset and
 * all clip durations remain unchanged. Synchronously shifts associated automation keyframes.
 */
class RippleDeleteManager {
    /**
     * Deletes [clipId] from [track]. When [rippleEnabled] is true, clips that
     * follow the deleted clip are shifted left by the deleted duration.
     * Synchronously shifts automation keyframe data to prevent keyframe orphans.
     * Locked tracks are not modified.
     */
    fun deleteClip(
        track: TimelineTrack,
        clipId: String,
        rippleEnabled: Boolean,
        keyframeTracks: MutableMap<String, KeyframeTrackData>? = null
    ): Boolean {
        if (track.isLocked) return false

        val index = track.clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        val deletedClip = track.clips.removeAt(index)
        if (rippleEnabled) {
            val shiftAmount = deletedClip.durationMs
            val deletedStart = track.trackOffsetMs + deletedClip.localStartTimeMs
            val deletedEnd = track.trackOffsetMs + deletedClip.localEndTimeMs

            for (i in index until track.clips.size) {
                val current = track.clips[i]
                track.clips[i] = current.copy(
                    localStartTimeMs = current.localStartTimeMs - shiftAmount
                )
            }

            // Shift and prune automation keyframe tracks to prevent orphan keyframes
            if (keyframeTracks != null) {
                for ((trackKey, kfData) in keyframeTracks.entries) {
                    val keyframesToDelete = kfData.keyframes.filter { it.timeMs in deletedStart until deletedEnd }.map { it.id }.toSet()
                    var updatedData = kfData
                    if (keyframesToDelete.isNotEmpty()) {
                        updatedData = KeyframeOps.delete(updatedData, keyframesToDelete)
                    }
                    val keyframesToShift = updatedData.keyframes.filter { it.timeMs >= deletedEnd }.map { it.id }.toSet()
                    if (keyframesToShift.isNotEmpty()) {
                        updatedData = KeyframeOps.move(updatedData, keyframesToShift, -shiftAmount)
                    }
                    keyframeTracks[trackKey] = updatedData
                }
            }
        }
        return true
    }
}
