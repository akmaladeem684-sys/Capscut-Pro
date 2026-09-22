package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import org.junit.Assert.*
import org.junit.Test

class MasterTimelineClockTest {
    @Test fun `seek is exact`() {
        val c = MasterTimelineClock()
        c.seekTo(s(4.2)); assertEquals(s(4.2), c.timeMicros)
        c.seekTo(-5); assertEquals(0L, c.timeMicros)
    }

    @Test fun `seek to same time does not notify`() {
        val c = MasterTimelineClock(); var n = 0
        c.addListener { _, _ -> n++ }
        assertEquals(1, n)
        c.seekTo(1000); assertEquals(2, n)
        c.seekTo(1000); assertEquals(2, n)
    }

    @Test fun `no drift over 1000 frames`() {
        val c = MasterTimelineClock()
        repeat(1000) { c.advanceBy(33_333, playing = true) }
        assertEquals(33_333_000L, c.timeMicros)
    }

    @Test fun `each listener notified exactly once per tick`() {
        val c = MasterTimelineClock(); val a = mutableListOf<Long>(); val b = mutableListOf<Long>()
        c.addListener { t, _ -> a.add(t) }; c.addListener { t, _ -> b.add(t) }
        a.clear(); b.clear()
        c.advanceBy(1000, true)
        assertEquals(listOf(1000L), a); assertEquals(listOf(1000L), b)
    }
}
