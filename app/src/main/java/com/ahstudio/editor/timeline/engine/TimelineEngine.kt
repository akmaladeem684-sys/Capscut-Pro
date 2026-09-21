package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.clock.MasterTimelineClock
import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

data class TimelineState(
    val tracks: List<TimelineTrack> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    val markers: List<TimelineMarker> = emptyList(),
    val zoomPxPerSec: Float = TimelineConstants.ZOOM_DEFAULT_PX_PER_SECOND,
    val selectedClipIds: Set<Long> = emptySet(),
    val selectedTrackId: Long? = null
)

class TimelineEngine(val clock: MasterTimelineClock) {
    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state

    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()

    private val trackClipIndexes = HashMap<Long, TrackClipIndex>()

    init {
        val defaultTracks = listOf(
            TimelineTrack(1L, TrackKind.VIDEO_MAIN, "Video 1"),
            TimelineTrack(2L, TrackKind.AUDIO, "Audio 1")
        )
        val defaultClips = listOf(
            TimelineClip(101L, 1L, ClipKind.VIDEO, 0L, 5_000_000L, label = "Master Intro Video"),
            TimelineClip(102L, 2L, ClipKind.AUDIO, 0L, 5_000_000L, label = "Master Audio Track")
        )
        setInitialState(defaultTracks, defaultClips, emptyList())
    }

    fun setInitialState(tracks: List<TimelineTrack>, clips: List<TimelineClip>, markers: List<TimelineMarker>) {
        _state.value = TimelineState(tracks = tracks, clips = clips, markers = markers)
        rebuildIndexes()
        updateDuration()
    }

    private fun rebuildIndexes() {
        trackClipIndexes.clear()
        val grouped = _state.value.clips.groupBy { it.trackId }
        for ((trackId, cList) in grouped) {
            val idx = TrackClipIndex()
            idx.rebuild(cList)
            trackClipIndexes[trackId] = idx
        }
    }

    private fun updateDuration() {
        val maxEnd = _state.value.clips.maxOfOrNull { it.endUs } ?: 0L
        clock.durationUs = maxEnd.coerceAtLeast(10_000_000L)
    }

    fun execute(cmd: TimelineCommand) {
        cmd.apply(this)
        undoStack.push(cmd)
        if (undoStack.size > TimelineConstants.UNDO_LIMIT) {
            val temp = ArrayDeque<TimelineCommand>()
            while (undoStack.size > 1) temp.push(undoStack.pop())
            undoStack.clear()
            while (temp.isNotEmpty()) undoStack.push(temp.pop())
        }
        redoStack.clear()
        updateDuration()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val cmd = undoStack.pop()
        cmd.undo(this)
        redoStack.push(cmd)
        updateDuration()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val cmd = redoStack.pop()
        cmd.apply(this)
        undoStack.push(cmd)
        updateDuration()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun replaceAllInternal(newClips: List<TimelineClip>) {
        val cur = _state.value.clips.toMutableList()
        for (nc in newClips) {
            val idx = cur.indexOfFirst { it.id == nc.id }
            if (idx >= 0) cur[idx] = nc else cur.add(nc)
        }
        _state.value = _state.value.copy(clips = cur)
        rebuildIndexes()
    }

    fun removeClipInternal(clipId: Long) {
        val cur = _state.value.clips.filterNot { it.id == clipId }
        _state.value = _state.value.copy(clips = cur, selectedClipIds = _state.value.selectedClipIds - clipId)
        rebuildIndexes()
    }

    fun insertClipInternal(clip: TimelineClip) {
        val cur = _state.value.clips + clip
        _state.value = _state.value.copy(clips = cur)
        rebuildIndexes()
    }

    fun insertTrackInternal(track: TimelineTrack, index: Int) {
        val tracks = _state.value.tracks.toMutableList()
        tracks.add(index.coerceIn(0, tracks.size), track)
        _state.value = _state.value.copy(tracks = tracks)
    }

    fun removeTrackInternal(trackId: Long) {
        val tracks = _state.value.tracks.filterNot { it.id == trackId }
        val clips = _state.value.clips.filterNot { it.trackId == trackId }
        _state.value = _state.value.copy(tracks = tracks, clips = clips)
        rebuildIndexes()
    }

    fun moveTrackInternal(from: Int, to: Int) {
        val tracks = _state.value.tracks.toMutableList()
        if (from in tracks.indices && to in tracks.indices) {
            val item = tracks.removeAt(from)
            tracks.add(to, item)
            _state.value = _state.value.copy(tracks = tracks)
        }
    }

    fun setTrackInternal(track: TimelineTrack) {
        val tracks = _state.value.tracks.map { if (it.id == track.id) track else it }
        _state.value = _state.value.copy(tracks = tracks)
    }

    fun insertMarkerInternal(marker: TimelineMarker) {
        _state.value = _state.value.copy(markers = _state.value.markers + marker)
    }

    fun removeMarkerInternal(markerId: Long) {
        _state.value = _state.value.copy(markers = _state.value.markers.filterNot { it.id == markerId })
    }

    fun clipById(id: Long): TimelineClip? = _state.value.clips.find { it.id == id }

    fun clipsForTrack(trackId: Long): List<TimelineClip> =
        trackClipIndexes[trackId]?.all() ?: emptyList()

    fun selectClip(clipId: Long, additive: Boolean) {
        val sel = if (additive) {
            if (_state.value.selectedClipIds.contains(clipId)) _state.value.selectedClipIds - clipId
            else _state.value.selectedClipIds + clipId
        } else {
            setOf(clipId)
        }
        _state.value = _state.value.copy(selectedClipIds = sel)
    }

    fun setZoom(zoom: Float) {
        val clamped = zoom.coerceIn(TimelineConstants.ZOOM_MIN_PX_PER_SECOND, TimelineConstants.ZOOM_MAX_PX_PER_SECOND)
        _state.value = _state.value.copy(zoomPxPerSec = clamped)
    }
}
