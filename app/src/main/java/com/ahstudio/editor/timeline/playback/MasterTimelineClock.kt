package com.ahstudio.editor.timeline.playback

/** The ONE clock. PlaybackController is the only writer during playback. */
class MasterTimelineClock(initialMicros: Long = 0L) {

    fun interface ClockListener { fun onTimeChanged(micros: Long, playing: Boolean) }

    @Volatile var timeMicros: Long = initialMicros; private set
    private val listeners = ArrayList<ClockListener>()

    fun addListener(l: ClockListener) { listeners.add(l); l.onTimeChanged(timeMicros, false) }
    fun removeListener(l: ClockListener) { listeners.remove(l) }

    /** Immediate, exact seek — used by scrub, taps and auto-follow. Never approximate. */
    fun seekTo(micros: Long) {
        val t = micros.coerceAtLeast(0L)
        if (t == timeMicros) return
        timeMicros = t
        val snapshotListeners = listeners.toList()
        for (l in snapshotListeners) l.onTimeChanged(t, false)
    }

    /** Frame driver only (PlaybackController). */
    fun advanceBy(deltaMicros: Long, playing: Boolean) {
        if (deltaMicros == 0L) return
        timeMicros += deltaMicros
        val snapshotListeners = listeners.toList()
        for (l in snapshotListeners) l.onTimeChanged(timeMicros, playing)
    }
}
