package com.universal.engine

import com.universal.engine.memory.MemoryBudgetManager
import org.junit.Assert.*
import org.junit.Test

class MemoryBudgetTest {
    @Test
    fun rejects_over_hard_limit() {
        val m = MemoryBudgetManager(100L, 150L)
        assertTrue(m.allocate("t", 100L))
        assertFalse(m.allocate("t", 100L))
        assertEquals(100L, m.totalBytes())
    }

    @Test
    fun soft_limit_triggers_eviction() {
        val m = MemoryBudgetManager(100L, 200L)
        var evicted = false
        m.registerEvictor(object : MemoryBudgetManager.Evictable {
            override fun evict(): Long {
                evicted = true
                m.release("t", 60L)
                return 60L
            }
        })
        assertTrue(m.allocate("t", 90L))
        assertTrue(m.allocate("t", 30L))
        assertTrue(evicted)
    }

    @Test
    fun category_accounting() {
        val m = MemoryBudgetManager(1000L, 2000L)
        m.allocate("tex", 100)
        m.allocate("fbo", 50)
        m.release("tex", 100)
        assertEquals(50L, m.totalBytes())
        assertEquals(0L, m.bytesIn("tex"))
        assertEquals(50L, m.bytesIn("fbo"))
    }
}
