package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.core.*
import java.util.concurrent.atomic.AtomicLong

class TimelineValidationException(message: String) : Exception(message)

fun interface EngineListener { fun onChanged(snapshot: TimelineSnapshot, selection: Set<String>) }

class TimelineEngine(
    initial: TimelineSnapshot = TimelineSnapshot(),
    val history: CommandHistory = CommandHistory(),
) {
    private val idGen = AtomicLong(0)
    private fun newId(prefix: String) = "$prefix-${idGen.incrementAndGet()}"

    var snapshot: TimelineSnapshot = initial; private set
    var indexes: TimelineIndexes = TimelineIndexes(initial); private set
    var selection: Set<String> = emptySet(); private set

    private val listeners = ArrayList<EngineListener>()
    fun addListener(l: EngineListener) { listeners.add(l); l.onChanged(snapshot, selection) }
    fun removeListener(l: EngineListener) { listeners.remove(l) }

    // ---------- queries ----------
    fun trackById(id: String): Track? = snapshot.tracks.firstOrNull { it.id == id }
    fun trackIndex(id: String): Int = snapshot.tracks.indexOfFirst { it.id == id }
    fun clip(id: String): Clip? = snapshot.clips[id]
    fun clipsOn(trackId: String): List<Clip> = indexes.clipsByTrack[trackId] ?: emptyList()
    fun clipAtTime(trackId: String, micros: Long): Clip? = indexes.clipAtTime(trackId, micros)
    fun durationMicros(): Long = indexes.endTimeMicros
    fun isTrackAudible(track: Track): Boolean =
        if (snapshot.tracks.any { it.solo }) track.solo && !track.muted else !track.muted

    // ---------- transactions (drag = begin…commit; single ops auto-commit) ----------
    private var openTx: Pair<String, Pair<TimelineSnapshot, Set<String>>>? = null

    fun begin(label: String) {
        check(openTx == null) { "Transaction already open: ${openTx?.first}" }
        openTx = label to (snapshot to selection)
    }

    fun commit(): Boolean {
        val (label, before) = openTx ?: return false
        openTx = null
        val (beforeSnap, beforeSel) = before
        if (snapshot == beforeSnap && selection == beforeSel) return false
        history.push(SnapshotCommand(label, beforeSnap, beforeSel, snapshot, selection))
        publish()
        return true
    }

    fun cancel() {
        val (_, before) = openTx ?: return
        openTx = null
        restoreInternal(before.first, before.second)
    }

    private fun <T> tx(label: String, body: () -> T): T {
        val owned = openTx == null
        if (owned) begin(label)
        return try { val r = body(); if (owned) commit(); r } catch (t: Throwable) { if (owned) cancel(); throw t }
    }

    internal fun restoreInternal(s: TimelineSnapshot, sel: Set<String>) {
        snapshot = s; indexes = TimelineIndexes(s)
        selection = sel.filter { s.clips.containsKey(it) }.toSet()
        publish()
    }

    private fun publish() {
        indexes = TimelineIndexes(snapshot)
        val s = snapshot; val sel = selection
        for (l in listeners.toList()) l.onChanged(s, sel)
    }

    private fun requireTrack(trackId: String): Track =
        trackById(trackId) ?: throw TimelineValidationException("Missing track $trackId")

    private fun requireEditableTrack(trackId: String): Track {
        val t = requireTrack(trackId)
        if (t.locked) throw TimelineValidationException("Track '${t.name}' is locked")
        return t
    }

    private fun putClip(c: Clip) { snapshot = snapshot.copy(clips = snapshot.clips + (c.id to c)) }
    private fun dropClip(id: String) { snapshot = snapshot.copy(clips = snapshot.clips - id) }

    private fun validatePlacement(trackId: String, kind: ClipKind, start: Long, duration: Long, sourceIn: Long, src: Clip) {
        val t = requireTrack(trackId)
        if (!t.kind.accepts(kind)) throw TimelineValidationException("${t.kind} track rejects $kind clips")
        if (start < 0) throw TimelineValidationException("Negative start")
        if (duration < TimelineConstants.MIN_CLIP_MICROS) throw TimelineValidationException("Clip too short")
        if (sourceIn < 0) throw TimelineValidationException("Negative sourceIn")
        val limit = src.sourceDurationMicros
        if (limit != null && sourceIn + (duration * src.speed).toLong() > limit)
            throw TimelineValidationException("Trim exceeds media length")
    }

    // ---------- clip mutations (ALL go through commands) ----------
    fun addClip(c: Clip): Clip = tx("Add clip") {
        requireEditableTrack(c.trackId)
        validatePlacement(c.trackId, c.kind, c.startMicros, c.durationMicros, c.sourceInMicros, c)
        val clip = if (c.id.isBlank()) c.copy(id = newId("clip")) else c
        putClip(clip); clip
    }

    fun removeClip(id: String) = tx("Delete clip") {
        snapshot.clips[id] ?: return@tx
        dropClip(id)
        if (id in selection) { selection = selection - id }
    }

    fun moveClip(id: String, newStartMicros: Long, newTrackId: String? = null): Clip = tx("Move clip") {
        val c = snapshot.clips[id] ?: throw TimelineValidationException("Missing clip")
        val target = requireEditableTrack(newTrackId ?: c.trackId)
        validatePlacement(target.id, c.kind, newStartMicros, c.durationMicros, c.sourceInMicros, c)
        val moved = c.copy(startMicros = newStartMicros, trackId = target.id)
        putClip(moved); moved
    }

    fun trimClip(id: String, newStartMicros: Long, newDurationMicros: Long): Clip = tx("Trim clip") {
        val c = snapshot.clips[id] ?: throw TimelineValidationException("Missing clip")
        requireEditableTrack(c.trackId)
        val srcIn = c.sourceInMicros + ((newStartMicros - c.startMicros) * c.speed).toLong()
        validatePlacement(c.trackId, c.kind, newStartMicros, newDurationMicros, srcIn, c)
        val trimmed = c.copy(startMicros = newStartMicros, durationMicros = newDurationMicros, sourceInMicros = srcIn)
        putClip(trimmed); trimmed
    }

    /** Returns the new right-hand clip. Media mapping is speed-aware. (P2 fix applied) */
    fun splitClip(id: String, atMicros: Long): Clip = tx("Split clip") {
        val c = snapshot.clips[id] ?: throw TimelineValidationException("Missing clip")
        requireEditableTrack(c.trackId)
        val leftDur = atMicros - c.startMicros
        val rightDur = c.endMicros - atMicros
        if (leftDur < TimelineConstants.MIN_CLIP_MICROS || rightDur < TimelineConstants.MIN_CLIP_MICROS)
            throw TimelineValidationException("Split point too close to clip edge")
        val right = c.copy(
            id = newId("clip"),
            startMicros = atMicros,
            durationMicros = rightDur,
            sourceInMicros = c.sourceInMicros + (leftDur * c.speed).toLong(),
            keyframes = c.keyframes.mapNotNull { kf ->
                (kf.offsetMicros - leftDur).takeIf { it >= 0 }?.let { kf.copy(offsetMicros = it) }
            },
        )
        putClip(c.copy(durationMicros = leftDur, keyframes = c.keyframes.filter { it.offsetMicros <= leftDur }))
        putClip(right); right
    }

    fun duplicateClip(id: String, desiredStartMicros: Long? = null): Clip = tx("Duplicate clip") {
        val c = snapshot.clips[id] ?: throw TimelineValidationException("Missing clip")
        val copy = c.copy(id = newId("clip"), startMicros = desiredStartMicros ?: c.endMicros)
        validatePlacement(copy.trackId, copy.kind, copy.startMicros, copy.durationMicros, copy.sourceInMicros, copy)
        putClip(copy); copy
    }

    /** Generic escape hatch for future features (speed curves, labels, keyframes…). */
    fun updateClip(id: String, transform: (Clip) -> Clip): Clip = tx("Edit clip") {
        val c = snapshot.clips[id] ?: throw TimelineValidationException("Missing clip")
        val updated = transform(c)
        validatePlacement(updated.trackId, updated.kind, updated.startMicros, updated.durationMicros, updated.sourceInMicros, updated)
        putClip(updated); updated
    }

    // ---------- track mutations ----------
    fun addTrack(kind: TrackKind, name: String, atIndex: Int = snapshot.tracks.size): Track = tx("Add track") {
        val t = Track(id = newId("track"), kind = kind, name = name)
        val list = snapshot.tracks.toMutableList().apply { add(atIndex.coerceIn(0, size), t) }
        snapshot = snapshot.copy(tracks = list); t
    }

    fun removeTrack(trackId: String) = tx("Delete track") {
        val list = snapshot.tracks.filter { it.id != trackId }
        if (list.size == snapshot.tracks.size) return@tx
        snapshot = snapshot.copy(tracks = list, clips = snapshot.clips.filterValues { it.trackId != trackId })
        selection = selection.filter { snapshot.clips.containsKey(it) }.toSet()
    }

    fun moveTrack(trackId: String, insertionIndex: Int) = tx("Reorder track") {
        val from = trackIndex(trackId)
        if (from < 0) return@tx
        val list = snapshot.tracks.toMutableList().apply { removeAt(from) }
        val to = insertionIndex.coerceIn(0, list.size)
        list.add(to, snapshot.tracks[from])
        snapshot = snapshot.copy(tracks = list)
    }

    fun updateTrack(trackId: String, transform: (Track) -> Track): Track = tx("Edit track") {
        snapshot = snapshot.copy(tracks = snapshot.tracks.map { if (it.id == trackId) transform(it) else it })
        requireTrack(trackId)
    }

    // ---------- markers ----------
    fun addMarker(timeMicros: Long, label: String = "", kind: MarkerKind = MarkerKind.USER): Marker = tx("Add marker") {
        val m = Marker(newId("marker"), timeMicros, label, kind)
        snapshot = snapshot.copy(markers = (snapshot.markers + m).sortedBy { it.timeMicros }); m
    }
    fun removeMarker(id: String) = tx("Remove marker") {
        snapshot = snapshot.copy(markers = snapshot.markers.filter { it.id != id })
    }

    // ---------- selection (independent of Playhead — §11) ----------
    fun setSelection(ids: Set<String>) {
        val next = ids.filter { snapshot.clips.containsKey(it) }.toSet()
        if (next == selection) return
        selection = next; publish()
    }
    fun toggleSelection(id: String) { setSelection(if (id in selection) selection - id else selection + id) }
    fun clearSelection() { setSelection(emptySet()) }
    fun selectTrack(trackId: String) { setSelection(snapshot.clips.values.filter { it.trackId == trackId }.map { it.id }.toSet()) }
}
