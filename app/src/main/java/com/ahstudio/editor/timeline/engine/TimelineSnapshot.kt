package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.Marker
import com.ahstudio.editor.timeline.core.ProjectSettings
import com.ahstudio.editor.timeline.core.Track

/** One immutable authoritative timeline state. Undo/redo = swapping these refs. */
data class TimelineSnapshot(
    val tracks: List<Track> = emptyList(),
    val clips: Map<String, Clip> = emptyMap(),
    val markers: List<Marker> = emptyList(),
    val settings: ProjectSettings = ProjectSettings(),
) {
    fun durationMicros(): Long = clips.values.maxOfOrNull { it.endMicros } ?: 0L
}

/** Built once per publish — O(log n) queries for hit-test, windows, neighbors, snap. */
class TimelineIndexes(private val s: TimelineSnapshot) {

    val clipsByTrack: Map<String, List<Clip>> =
        s.tracks.associate { t -> t.id to s.clips.values.filter { it.trackId == t.id }.sortedBy { it.startMicros } }

    private val startsByTrack: Map<String, LongArray> =
        clipsByTrack.mapValues { (_, list) -> LongArray(list.size) { list[it].startMicros } }

    val endTimeMicros: Long = s.clips.values.maxOfOrNull { it.endMicros } ?: 0L

    /** Sorted boundaries for the SnapEngine + owning clipId (null for markers/zero). */
    val boundaries: LongArray
    val boundaryOwners: Array<String?>

    init {
        data class B(val t: Long, val owner: String?)
        val b = ArrayList<B>(s.clips.size * 2 + s.markers.size + 1)
        b.add(B(0L, null))
        for (c in s.clips.values) {
            b.add(B(c.startMicros, c.id)); b.add(B(c.endMicros, c.id))
            for (k in c.keyframes) b.add(B(c.startMicros + k.offsetMicros, c.id))
        }
        for (m in s.markers) b.add(B(m.timeMicros, null))
        b.sortBy { it.t }
        boundaries = LongArray(b.size) { b[it].t }
        boundaryOwners = Array(b.size) { b[it].owner }
    }

    fun clipAtTime(trackId: String, micros: Long): Clip? {
        val list = clipsByTrack[trackId] ?: return null
        val starts = startsByTrack[trackId] ?: return null
        val idx = starts.binarySearch(micros)
        val i = if (idx >= 0) idx else -idx - 2
        if (i < 0) return null
        val c = list[i]
        return if (micros >= c.startMicros && micros < c.endMicros) c else null
    }

    fun clipsOverlapping(trackId: String, fromMicros: Long, toMicros: Long): List<Clip> {
        val list = clipsByTrack[trackId] ?: return emptyList()
        val starts = startsByTrack[trackId] ?: return emptyList()
        val hi = starts.binarySearch(toMicros).let { if (it >= 0) it + 1 else -it - 1 }
        val out = ArrayList<Clip>()
        var i = hi - 1
        while (i >= 0 && list[i].startMicros < toMicros) {
            val c = list[i]
            if (c.endMicros > fromMicros) out.add(c)
            i--
        }
        out.reverse()
        return out
    }

    fun neighbors(trackId: String, clipId: String): Pair<Clip?, Clip?> {
        val list = clipsByTrack[trackId] ?: return null to null
        val self = list.firstOrNull { it.id == clipId } ?: return null to null
        var prev: Clip? = null; var next: Clip? = null
        for (c in list) {
            if (c.id == clipId) continue
            if (c.endMicros <= self.startMicros && (prev == null || c.startMicros > prev!!.startMicros)) prev = c
            if (c.startMicros >= self.endMicros && (next == null || c.startMicros < next!!.startMicros)) next = c
        }
        return prev to next
    }
}
