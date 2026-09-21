package com.universal.engine.memory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks GPU-side memory across all resource managers. Managers register allocations with
 * owners; when pressure exceeds [softLimitBytes], registered evictors are invoked oldest-first.
 */
class MemoryBudgetManager(
    /** e.g. 512 MB on 4K devices, derived from device heap at engine construction. */
    private val softLimitBytes: Long,
    private val hardLimitBytes: Long
) {

    interface Evictable { fun evict(): Long } // returns bytes actually freed

    private val totalBytes = AtomicLong(0)
    private val byCategory = ConcurrentHashMap<String, AtomicLong>()
    private val evictors = mutableListOf<Evictable>()

    fun registerEvictor(e: Evictable) { synchronized(evictors) { evictors.add(e) } }

    /** @return true if allocation accepted under the hard limit. */
    fun allocate(category: String, bytes: Long): Boolean {
        val projected = totalBytes.addAndGet(bytes)
        byCategory.getOrPut(category) { AtomicLong() }.addAndGet(bytes)
        if (projected > hardLimitBytes) {
            release(category, bytes)
            return false
        }
        if (projected > softLimitBytes) evictToSoftLimit()
        return true
    }

    fun release(category: String, bytes: Long) {
        totalBytes.addAndGet(-bytes)
        byCategory.getOrPut(category) { AtomicLong() }.addAndGet(-bytes)
    }

    fun evictToSoftLimit(): Long {
        var freed = 0L
        synchronized(evictors) {
            while (totalBytes.get() > softLimitBytes && evictors.isNotEmpty()) {
                var bestFree = 0L
                var best: Evictable? = null
                for (e in evictors) {
                    val f = e.evict()
                    freed += f
                    if (f > bestFree) { bestFree = f; best = e }
                }
                if (bestFree == 0L) break // nothing more to free
            }
        }
        return freed
    }

    fun totalBytes(): Long = totalBytes.get()
    fun bytesIn(category: String): Long = byCategory[category]?.get() ?: 0L
    fun isUnderPressure(): Boolean = totalBytes.get() > softLimitBytes

    companion object {
        fun defaultBudgetFor(maxDimension: Int): MemoryBudgetManager {
            val soft = if (maxDimension >= 3840) 768L * 1048576 else 384L * 1048576
            val hard = soft * 2
            return MemoryBudgetManager(soft, hard)
        }
    }
}
