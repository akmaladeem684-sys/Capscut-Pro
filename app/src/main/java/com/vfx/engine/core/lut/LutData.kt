package com.vfx.engine.core.lut

class LutData(
  val title: String,
  val size: Int,
  val tableData: FloatArray
)

object CubeLutParser {
  /**
   * Parses standard Adobe .cube 3D LUT string content.
   */
  fun parse(cubeContent: String): LutData {
    var title = "LUT"
    var size = 32
    val dataList = mutableListOf<Float>()

    cubeContent.lines().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.startsWith("TITLE")) {
        title = line.removePrefix("TITLE").trim().removeSurrounding("\"")
      } else if (line.startsWith("LUT_3D_SIZE")) {
        size = line.removePrefix("LUT_3D_SIZE").trim().toIntOrNull() ?: 32
      } else if (line.isNotEmpty() && !line.startsWith("#")) {
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 3) {
          parts.take(3).forEach { p ->
            p.toFloatOrNull()?.let { dataList.add(it) }
          }
        }
      }
    }

    return LutData(title = title, size = size, tableData = dataList.toFloatArray())
  }
}

object HaldClutParser {
  fun parseHaldSize(width: Int, height: Int): Int {
    return Math.round(Math.cbrt((width * height).toDouble())).toInt()
  }
}
