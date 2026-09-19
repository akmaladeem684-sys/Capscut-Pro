package com.example.engine.audio

data class SoundEffectItem(
  val id: String,
  val title: String,
  val category: String,
  val durationMs: Long,
  val icon: String
)

object SoundEffectsCatalog {
  val effects = listOf(
    SoundEffectItem("sfx_whoosh", "Cinematic Whoosh", "Transitions", 1200L, "💨"),
    SoundEffectItem("sfx_pop", "Modern Bubble Pop", "UI & Hits", 450L, "🫧"),
    SoundEffectItem("sfx_camera", "DSLR Camera Shutter", "Camera", 650L, "📸"),
    SoundEffectItem("sfx_bass", "Sub Bass Impact", "Hits", 2200L, "💥"),
    SoundEffectItem("sfx_ding", "Notification Bell", "Chimes", 900L, "🔔"),
    SoundEffectItem("sfx_glitch", "Cyber Glitch Scratch", "Electronic", 800L, "⚡"),
    SoundEffectItem("sfx_typewriter", "Mechanical Keyboard", "Foley", 1500L, "⌨️"),
    SoundEffectItem("sfx_riser", "Tension Tension Riser", "Transitions", 3000L, "📈"),
    SoundEffectItem("sfx_vinyl", "Vinyl Needle Drop", "Retro", 1800L, "🎵"),
    SoundEffectItem("sfx_sparkle", "Magic Fairy Sparkle", "Chimes", 1600L, "✨")
  )

  val musicTracks = listOf(
    SoundEffectItem("mus_lofi", "Midnight Lofi Lounge", "Chill", 30000L, "🎧"),
    SoundEffectItem("mus_upbeat", "Energetic Tech Beat", "Electronic", 25000L, "⚡"),
    SoundEffectItem("mus_cinematic", "Epic Horizons Orchestral", "Cinematic", 35000L, "🎻"),
    SoundEffectItem("mus_acoustic", "Sunset Acoustic Groove", "Indie", 28000L, "🎸"),
    SoundEffectItem("mus_synthwave", "Neon Highway 1984", "Retro", 32000L, "🕹️")
  )

  fun generateWaveform(seed: String, count: Int = 30): List<Float> {
    val random = java.util.Random(seed.hashCode().toLong())
    val list = mutableListOf<Float>()
    for (i in 0 until count) {
      val base = 0.2f + random.nextFloat() * 0.75f
      list.add(base.coerceIn(0.1f, 1.0f))
    }
    return list
  }
}
