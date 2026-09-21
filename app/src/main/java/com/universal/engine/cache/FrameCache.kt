package com.universal.engine.cache

import com.universal.engine.frame.GpuFrame
import java.security.MessageDigest

class FrameCache(private val maxEntries: Int = 32) {

    private val map = object : LinkedHashMap<String, GpuFrame>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GpuFrame>): Boolean {
            if (size > maxEntries) { eldest.value.release(); return true }
            return false
        }
    }

    fun key(mediaId: String, timestampUs: Long, width: Int, height: Int, quality: String, effectVariants: List<String>, timelineHash: String): String {
        return Companion.key(mediaId, timestampUs, width, height, quality, effectVariants, timelineHash)
    }

    @Synchronized fun get(key: String): GpuFrame? = map[key]?.takeIf { !it.isReleased }?.also { it.retain() }
    @Synchronized fun put(key: String, frame: GpuFrame) { map[key] = frame.retain() }

    @Synchronized fun invalidateAll() { map.values.forEach { it.release() }; map.clear() }

    companion object {
        fun key(mediaId: String, timestampUs: Long, width: Int, height: Int, quality: String, effectVariants: List<String>, timelineHash: String): String {
            val variants = effectVariants.sorted().joinToString(",")
            return "$timelineHash|$mediaId|$timestampUs|${width}x$height|$quality|$variants"
        }

        fun timelineHashOf(content: String): String =
            MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
    }
}
