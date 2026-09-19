package com.example.domain.command

import com.example.domain.model.TimelineTrack

/**
 * Deletes clips from a track and optionally closes the resulting gap.
 *
 * Clip positions remain track-local. Ripple deletion therefore changes only
 * the local positions of clips after the deleted clip; the track offset and
 * all clip durations remain unchanged.
 */
class RippleDeleteManager {
    /**
     * Deletes [clipId] from [track]. When [rippleEnabled] is true, clips that
     * follow the deleted clip are shifted left by the deleted duration.
     * Locked tracks are not modified.
     */
    fun deleteClip(
        track: TimelineTrack,
        clipId: String,
        rippleEnabled: Boolean
    ): Boolean {
        if (track.isLocked) return false

        val index = track.clips.indexOfFirst { it.id == clipId }
        if (index == -1) return false

        val deletedClip = track.clips.removeAt(index)
        if (rippleEnabled) {
            val shiftAmount = deletedClip.durationMs
            for (i in index until track.clips.size) {
                val current = track.clips[i]
                track.clips[i] = current.copy(
                    localStartTimeMs = current.localStartTimeMs - shiftAmount
                )
            }
        }
        return true
    }
}
