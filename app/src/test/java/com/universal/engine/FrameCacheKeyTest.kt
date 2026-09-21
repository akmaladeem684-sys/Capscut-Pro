package com.universal.engine

import com.universal.engine.cache.FrameCache
import org.junit.Assert.*
import org.junit.Test

class FrameCacheKeyTest {
    @Test
    fun keys_differ_on_every_dimension() {
        val base = FrameCache.key("m1", 500L, 1920, 1080, "HIGH", listOf("blur"), "abc")
        assertNotEquals(base, FrameCache.key("m2", 500L, 1920, 1080, "HIGH", listOf("blur"), "abc"))
        assertNotEquals(base, FrameCache.key("m1", 501L, 1920, 1080, "HIGH", listOf("blur"), "abc"))
        assertNotEquals(base, FrameCache.key("m1", 500L, 3840, 2160, "HIGH", listOf("blur"), "abc"))
        assertNotEquals(base, FrameCache.key("m1", 500L, 1920, 1080, "DRAFT", listOf("blur"), "abc"))
        assertNotEquals(base, FrameCache.key("m1", 500L, 1920, 1080, "HIGH", listOf("blur2"), "abc"))
        assertNotEquals(base, FrameCache.key("m1", 500L, 1920, 1080, "HIGH", listOf("blur"), "xyz"))
    }

    @Test
    fun effect_variant_order_is_normalized() {
        assertEquals(
            FrameCache.key("m", 0L, 100, 100, "Q", listOf("a", "b"), "h"),
            FrameCache.key("m", 0L, 100, 100, "Q", listOf("b", "a"), "h")
        )
    }
}
