package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.model.TimelineClip

/** Invariant: clips on a track never overlap (engine enforces on every mutation). */
class TrackClipIndex {
    private val list = ArrayList<TimelineClip>()
    val size: Int get() = list.size

    fun rebuild(clips: Collection<TimelineClip>) {
        list.clear(); list.addAll(clips); list.sortBy { it.startUs }
    }
    fun all(): List<TimelineClip> = list
    fun first(): TimelineClip? = list.firstOrNull()
    fun last(): TimelineClip? = list.lastOrNull()

    private fun lowerBound(startUs: Long): Int {
        var lo = 0; var hi = list.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (list[mid].startUs < startUs) lo = mid + 1 else hi = mid }
        return lo
    }

    /** O(log n). Returns the clip containing [timeUs], if any. */
    fun hitTest(timeUs: Long): TimelineClip? {
        var lo = 0; var hi = list.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].startUs <= timeUs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (ans < 0) return null
        val c = list[ans]
        return if (timeUs < c.endUs) c else null
    }

    fun clipsInWindow(fromUs: Long, toUs: Long): List<TimelineClip> {
        val out = ArrayList<TimelineClip>()
        val i = lowerBound(fromUs)
        if (i > 0) { val c = list[i - 1]; if (c.endUs > fromUs) out.add(c) }
        var j = i
        while (j < list.size && list[j].startUs < toUs) { out.add(list[j]); j++ }
        return out
    }

    fun insert(clip: TimelineClip) { list.add(lowerBound(clip.startUs), clip) }

    fun removeById(clipId: Long): TimelineClip? {
        val it = list.iterator()
        while (it.hasNext()) { val c = it.next(); if (c.id == clipId) { it.remove(); return c } }
        return null
    }

    fun replace(clip: TimelineClip) { removeById(clip.id); insert(clip) }
}
