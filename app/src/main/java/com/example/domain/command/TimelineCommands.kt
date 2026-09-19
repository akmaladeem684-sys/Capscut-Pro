package com.example.domain.command

import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineTrack

/** Commands for editing track-local timeline coordinates. */
object TimelineCommands {

    /** Splits a clip at an absolute project-timeline position. */
    data class SplitClipCommand(
        val trackId: String,
        val clipId: String,
        val splitTimeMs: Long
    ) : TimelineCommand {
        override val description: String = "Split clip $clipId at $splitTimeMs ms"

        override fun execute(tracks: List<TimelineTrack>): List<TimelineTrack> =
            tracks.map { track ->
                if (track.id != trackId || track.isLocked) return@map track

                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex == -1) return@map track

                val clip = track.clips[clipIndex]
                val splitLocalTimeMs = splitTimeMs - track.trackOffsetMs
                val offsetMs = splitLocalTimeMs - clip.localStartTimeMs
                if (offsetMs <= 0L || offsetMs >= clip.durationMs) return@map track

                val firstHalf = clip.copy(durationMs = offsetMs)
                val secondHalf = clip.copy(
                    id = "${clip.id}_split_${splitTimeMs}",
                    localStartTimeMs = splitLocalTimeMs,
                    durationMs = clip.durationMs - offsetMs
                )
                val updatedClips = track.clips.toMutableList().apply {
                    removeAt(clipIndex)
                    add(clipIndex, firstHalf)
                    add(clipIndex + 1, secondHalf)
                }
                track.copy(clips = updatedClips)
            }

        override fun undo(tracks: List<TimelineTrack>): List<TimelineTrack> =
            tracks.map { track ->
                if (track.id != trackId || track.isLocked) return@map track

                val firstIndex = track.clips.indexOfFirst { it.id == clipId }
                if (firstIndex == -1 || firstIndex >= track.clips.lastIndex) return@map track

                val secondClip = track.clips[firstIndex + 1]
                val expectedLocalStart = splitTimeMs - track.trackOffsetMs
                if (secondClip.localStartTimeMs != expectedLocalStart ||
                    !secondClip.id.startsWith("${clipId}_split_")) {
                    return@map track
                }

                val firstClip = track.clips[firstIndex]
                val mergedClip = firstClip.copy(
                    durationMs = firstClip.durationMs + secondClip.durationMs
                )
                track.copy(clips = track.clips.toMutableList().apply {
                    removeAt(firstIndex + 1)
                    set(firstIndex, mergedClip)
                })
            }
    }

    /** Trims a clip using absolute project-timeline coordinates at the API boundary. */
    data class TrimClipCommand(
        val trackId: String,
        val clipId: String,
        val newStartMs: Long,
        val newDurationMs: Long,
        val oldStartMs: Long,
        val oldDurationMs: Long
    ) : TimelineCommand {
        override val description: String = "Trim clip $clipId"

        override fun execute(tracks: List<TimelineTrack>): List<TimelineTrack> =
            applyTrim(tracks, newStartMs, newDurationMs)

        override fun undo(tracks: List<TimelineTrack>): List<TimelineTrack> =
            applyTrim(tracks, oldStartMs, oldDurationMs)

        private fun applyTrim(
            tracks: List<TimelineTrack>,
            absoluteStartMs: Long,
            durationMs: Long
        ): List<TimelineTrack> = tracks.map { track ->
            if (track.id != trackId || track.isLocked) return@map track

            val localStartMs = absoluteStartMs - track.trackOffsetMs
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(
                        localStartTimeMs = localStartMs,
                        durationMs = durationMs.coerceAtLeast(0L)
                    )
                } else clip
            }.toMutableList())
        }
    }

    /** Moves one clip within its track without changing the track offset. */
    data class UpdateClipPosition(
        val trackId: String,
        val clipId: String,
        val deltaMs: Long
    ) : TimelineCommand {
        override val description: String = "Move clip $clipId by ${deltaMs}ms"

        override fun execute(tracks: List<TimelineTrack>): List<TimelineTrack> =
            updateClipPosition(tracks, deltaMs)

        override fun undo(tracks: List<TimelineTrack>): List<TimelineTrack> =
            updateClipPosition(tracks, -deltaMs)

        private fun updateClipPosition(
            tracks: List<TimelineTrack>,
            deltaMs: Long
        ): List<TimelineTrack> = tracks.map { track ->
            if (track.id != trackId || track.isLocked) return@map track
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(localStartTimeMs = (clip.localStartTimeMs + deltaMs).coerceAtLeast(0L))
                } else clip
            }.toMutableList())
        }
    }

    /** Moves an entire track while preserving every clip's local timing. */
    data class TranslateTrack(
        val trackId: String,
        val deltaMs: Long
    ) : TimelineCommand {
        override val description: String = "Move track $trackId by ${deltaMs}ms"

        override fun execute(tracks: List<TimelineTrack>): List<TimelineTrack> =
            translateTrack(tracks, deltaMs)

        override fun undo(tracks: List<TimelineTrack>): List<TimelineTrack> =
            translateTrack(tracks, -deltaMs)

        private fun translateTrack(
            tracks: List<TimelineTrack>,
            deltaMs: Long
        ): List<TimelineTrack> = tracks.map { track ->
            if (track.id == trackId && !track.isLocked) {
                track.copy(trackOffsetMs = (track.trackOffsetMs + deltaMs).coerceAtLeast(0L))
            } else track
        }
    }

    /** Applies a command directly to one mutable track. */
    fun applyCommand(
        track: TimelineTrack,
        clip: TimelineClip,
        command: TimelineCommand
    ): TimelineTrack {
        if (track.isLocked) return track
        return when (command) {
            is UpdateClipPosition -> {
                if (command.trackId != track.id || command.clipId != clip.id) return track
                val index = track.clips.indexOfFirst { it.id == clip.id }
                if (index == -1) return track
                track.apply {
                    clips[index] = clip.copy(
                        localStartTimeMs = (clip.localStartTimeMs + command.deltaMs).coerceAtLeast(0L)
                    )
                }
            }
            is TranslateTrack -> {
                if (command.trackId == track.id) {
                    track.trackOffsetMs = (track.trackOffsetMs + command.deltaMs).coerceAtLeast(0L)
                }
                track
            }
            else -> track
        }
    }
}
