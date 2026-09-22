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
            TimelineTrack(2L, TrackKind.OVERLAY, "Overlay 1"),
            TimelineTrack(3L, TrackKind.TEXT, "Audio & Text")
        )
        setInitialState(defaultTracks, emptyList(), emptyList())
    }

    fun addClipToTrack(trackId: Long, kind: ClipKind, durationUs: Long = 5_000_000L, label: String = "New Clip") {
        val existingClips = _state.value.clips.filter { it.trackId == trackId }
        val placement = PlaceCalculator.findFreePlacement(existingClips, clock.positionUs.value, durationUs)
        val newClipId = System.currentTimeMillis()
        val clip = TimelineClip(newClipId, trackId, kind, placement.startUs, placement.durationUs, label = label)
        execute(AddClipsCommand(listOf(clip)))
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

    fun clipsForTrack(trackId: Long): List<TimelineClip> =
        _state.value.clips.filter { it.trackId == trackId }.sortedBy { it.startUs }

    fun clipById(id: Long): TimelineClip? = _state.value.clips.find { it.id == id }

    fun setZoom(newZoom: Float) {
        val clamped = newZoom.coerceIn(TimelineConstants.ZOOM_MIN_PX_PER_SECOND, TimelineConstants.ZOOM_MAX_PX_PER_SECOND)
        _state.value = _state.value.copy(zoomPxPerSec = clamped)
    }

    fun selectClip(clipId: Long, add: Boolean = false) {
        val current = _state.value.selectedClipIds
        val newSet = if (add) {
            if (current.contains(clipId)) current - clipId else current + clipId
        } else {
            setOf(clipId)
        }
        _state.value = _state.value.copy(selectedClipIds = newSet)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedClipIds = emptySet())
    }

    internal fun applyRawState(newState: TimelineState) {
        _state.value = newState
        rebuildIndexes()
        updateDuration()
    }

    internal fun replaceAllInternal(newClips: List<TimelineClip>) {
        val clipsMap = _state.value.clips.associateBy { it.id }.toMutableMap()
        for (c in newClips) clipsMap[c.id] = c
        _state.value = _state.value.copy(clips = clipsMap.values.toList())
        rebuildIndexes()
        updateDuration()
    }

    internal fun insertClipInternal(clip: TimelineClip) {
        val list = _state.value.clips.toMutableList()
        if (list.none { it.id == clip.id }) {
            list.add(clip)
            _state.value = _state.value.copy(clips = list)
            rebuildIndexes()
            updateDuration()
        }
    }

    internal fun removeClipInternal(clipId: Long) {
        val list = _state.value.clips.filterNot { it.id == clipId }
        _state.value = _state.value.copy(clips = list)
        rebuildIndexes()
        updateDuration()
    }

    internal fun insertTrackInternal(track: TimelineTrack, index: Int) {
        val tracks = _state.value.tracks.toMutableList()
        val idx = index.coerceIn(0, tracks.size)
        tracks.add(idx, track)
        _state.value = _state.value.copy(tracks = tracks)
    }

    internal fun removeTrackInternal(trackId: Long) {
        val tracks = _state.value.tracks.filterNot { it.id == trackId }
        val clips = _state.value.clips.filterNot { it.trackId == trackId }
        _state.value = _state.value.copy(tracks = tracks, clips = clips)
        rebuildIndexes()
        updateDuration()
    }

    internal fun moveTrackInternal(from: Int, to: Int) {
        val tracks = _state.value.tracks.toMutableList()
        if (from in tracks.indices && to in tracks.indices) {
            val item = tracks.removeAt(from)
            tracks.add(to, item)
            _state.value = _state.value.copy(tracks = tracks)
        }
    }

    internal fun setTrackInternal(track: TimelineTrack) {
        val tracks = _state.value.tracks.map { if (it.id == track.id) track else it }
        _state.value = _state.value.copy(tracks = tracks)
    }

    internal fun insertMarkerInternal(marker: TimelineMarker) {
        val markers = _state.value.markers.toMutableList()
        if (markers.none { it.id == marker.id }) {
            markers.add(marker)
            _state.value = _state.value.copy(markers = markers)
        }
    }

    internal fun removeMarkerInternal(markerId: Long) {
        val markers = _state.value.markers.filterNot { it.id == markerId }
        _state.value = _state.value.copy(markers = markers)
    }
}
