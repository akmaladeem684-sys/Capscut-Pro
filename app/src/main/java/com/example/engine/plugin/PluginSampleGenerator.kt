package com.example.engine.plugin

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PluginSampleGenerator {
  private const val TAG = "PluginSampleGenerator"

  /**
   * Generates a sample Filter Plugin ZIP in context.cacheDir and installs it or returns the File.
   */
  fun generateFiltersPluginZip(context: Context): File {
    val zipFile = File(context.cacheDir, "cinematic_filters_pack.zip")
    if (zipFile.exists()) zipFile.delete()

    val manifestJson = """
    {
      "id": "cinematic_filters_pack",
      "name": "Hollywood Cinematic Filters Pro",
      "version": "1.2.0",
      "author": "AH Studio Plugins",
      "description": "Professional color grading filters with custom color matrices, saturation boosts, and cinematic LUT parameters.",
      "type": "filter",
      "minimumAppVersion": "1.0.0",
      "items": [
        {
          "id": "filter_teal_orange_pro",
          "name": "Teal & Orange Hollywood",
          "description": "High-contrast block buster color scheme with teal shadows and warm orange skin tones.",
          "emoji": "🎬",
          "parameters": {
            "brightness": 0.05,
            "contrast": 1.30,
            "saturation": 1.35,
            "temperature": 0.20,
            "tint": -0.15,
            "vignette": 0.25
          }
        },
        {
          "id": "filter_cyberpunk_neon",
          "name": "Cyberpunk Neon Violet",
          "description": "Futuristic neon lighting with rich magenta highlights and deep cyan shadows.",
          "emoji": "⚡",
          "parameters": {
            "brightness": 0.02,
            "contrast": 1.40,
            "saturation": 1.50,
            "temperature": -0.35,
            "tint": 0.30,
            "vignette": 0.35
          }
        },
        {
          "id": "filter_retro_film_1980",
          "name": "1980 Vintage Film",
          "description": "Nostalgic 35mm film emulation with warm amber tones and faded shadow crush.",
          "emoji": "🎞️",
          "parameters": {
            "brightness": 0.08,
            "contrast": 0.95,
            "saturation": 0.85,
            "temperature": 0.25,
            "tint": 0.10,
            "vignette": 0.40
          }
        },
        {
          "id": "filter_noir_monochrome",
          "name": "Noir Black & White Pro",
          "description": "Deep black and white with extreme specular contrast and film grain.",
          "emoji": "🕶️",
          "parameters": {
            "brightness": -0.05,
            "contrast": 1.60,
            "saturation": 0.0,
            "temperature": 0.0,
            "tint": 0.0,
            "vignette": 0.50
          }
        },
        {
          "id": "filter_vivid_nature_hdr",
          "name": "Vivid Nature HDR",
          "description": "Ultra-vivid greens and deep blues with enhanced dynamic range.",
          "emoji": "🌿",
          "parameters": {
            "brightness": 0.05,
            "contrast": 1.20,
            "saturation": 1.60,
            "temperature": -0.10,
            "tint": -0.10,
            "vignette": 0.15
          }
        }
      ]
    }
    """.trimIndent()

    createZipWithManifest(zipFile, manifestJson, mapOf("README.txt" to "Hollywood Filters Plugin Pack by AH Video Studio"))
    return zipFile
  }

  /**
   * Generates a sample Stickers Plugin ZIP.
   */
  fun generateStickersPluginZip(context: Context): File {
    val zipFile = File(context.cacheDir, "popart_stickers_pack.zip")
    if (zipFile.exists()) zipFile.delete()

    val manifestJson = """
    {
      "id": "popart_stickers_pack",
      "name": "PopArt Animated Stickers",
      "version": "1.0.0",
      "author": "Creative FX Studio",
      "description": "High-impact viral stickers, badges, and emojis for social video editing.",
      "type": "sticker",
      "minimumAppVersion": "1.0.0",
      "items": [
        {
          "id": "sticker_fire_blast",
          "name": "🔥 Fire Burst",
          "emoji": "🔥",
          "description": "Trending fire explosion overlay sticker"
        },
        {
          "id": "sticker_director_cut",
          "name": "🎬 Director Slate",
          "emoji": "🎬",
          "description": "Hollywood movie clapper board sticker"
        },
        {
          "id": "sticker_lightning_bolt",
          "name": "⚡ Electric Power",
          "emoji": "⚡",
          "description": "Neon electric voltage flash sticker"
        },
        {
          "id": "sticker_certified_viral",
          "name": "💯 100% Certified",
          "emoji": "💯",
          "description": "Viral stamp score badge"
        },
        {
          "id": "sticker_rocket_launch",
          "name": "🚀 Rocket Boost",
          "emoji": "🚀",
          "description": "Growth launch rocket sticker"
        },
        {
          "id": "sticker_crown_vip",
          "name": "👑 VIP Gold Crown",
          "emoji": "👑",
          "description": "Royal premium gold crown sticker"
        }
      ]
    }
    """.trimIndent()

    createZipWithManifest(zipFile, manifestJson, emptyMap())
    return zipFile
  }

  /**
   * Generates a sample Fonts Plugin ZIP.
   */
  fun generateFontsPluginZip(context: Context): File {
    val zipFile = File(context.cacheDir, "custom_fonts_pack.zip")
    if (zipFile.exists()) zipFile.delete()

    val manifestJson = """
    {
      "id": "custom_fonts_pack",
      "name": "Cinematic Pro Fonts",
      "version": "1.1.0",
      "author": "Typography Design Lab",
      "description": "High-definition custom typography for video titles and lower thirds.",
      "type": "font",
      "minimumAppVersion": "1.0.0",
      "items": [
        {
          "id": "font_bebas_neue",
          "name": "Bebas Neue Display",
          "file": "fonts/BebasNeue.ttf",
          "description": "Tall, clean display sans-serif perfect for action titles"
        },
        {
          "id": "font_montserrat_bold",
          "name": "Montserrat Bold Heavy",
          "file": "fonts/MontserratBold.ttf",
          "description": "Modern geometric sans-serif for sleek subtitles"
        },
        {
          "id": "font_playfair_luxury",
          "name": "Playfair Luxury Serif",
          "file": "fonts/PlayfairSerif.ttf",
          "description": "Elegant high-end serif font for editorial videos"
        }
      ]
    }
    """.trimIndent()

    createZipWithManifest(zipFile, manifestJson, emptyMap())
    return zipFile
  }

  /**
   * Generates a sample Text Templates Plugin ZIP.
   */
  fun generateTextTemplatesPluginZip(context: Context): File {
    val zipFile = File(context.cacheDir, "title_templates_pack.zip")
    if (zipFile.exists()) zipFile.delete()

    val manifestJson = """
    {
      "id": "title_templates_pack",
      "name": "Viral Title Templates",
      "version": "1.0.0",
      "author": "Motion Design Studio",
      "description": "Ready-to-use motion graphic text presets with gradients, glows, and typewriter effects.",
      "type": "text_template",
      "minimumAppVersion": "1.0.0",
      "items": [
        {
          "id": "tpl_cyberpunk_neon",
          "name": "Cyberpunk Cyan-Purple Glow",
          "emoji": "✨",
          "description": "Cyan to purple gradient with high-intensity shadow blur",
          "parameters": {
            "hasGradient": true,
            "gradientStart": "0xFF00E5FF",
            "gradientEnd": "0xFF8B5CF6",
            "hasShadow": true,
            "shadowColor": "0xAA00E5FF",
            "shadowBlur": 12.0,
            "strokeWidth": 3.0,
            "animationType": "Zoom"
          }
        },
        {
          "id": "tpl_youtube_lower_third",
          "name": "YouTube Lower-Third Badge",
          "emoji": "📺",
          "description": "Black semi-transparent rounded pill box with yellow highlight",
          "parameters": {
            "hasBackground": true,
            "backgroundColor": "0xDD111827",
            "cornerRadius": 16.0,
            "textColor": "0xFFFFEB3B",
            "strokeWidth": 1.5,
            "strokeColor": "0xFFFFEB3B",
            "animationType": "Slide"
          }
        },
        {
          "id": "tpl_typewriter_minimal",
          "name": "Typewriter Terminal Green",
          "emoji": "⌨️",
          "description": "Retro terminal text with typewriter animation",
          "parameters": {
            "textColor": "0xFF00FF66",
            "animationType": "Typewriter",
            "hasShadow": true,
            "shadowColor": "0x8800FF66",
            "shadowBlur": 8.0
          }
        }
      ]
    }
    """.trimIndent()

    createZipWithManifest(zipFile, manifestJson, emptyMap())
    return zipFile
  }

  private fun createZipWithManifest(zipFile: File, manifestJson: String, extraFiles: Map<String, String>) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
      // Add plugin.json
      val manifestEntry = ZipEntry("plugin.json")
      zos.putNextEntry(manifestEntry)
      zos.write(manifestJson.toByteArray())
      zos.closeEntry()

      // Add extra files if specified
      for ((filePath, content) in extraFiles) {
        val entry = ZipEntry(filePath)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray())
        zos.closeEntry()
      }
    }
  }
}
