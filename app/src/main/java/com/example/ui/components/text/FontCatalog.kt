package com.example.ui.components.text

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.util.FontManager
import com.example.util.FontOption

@Immutable
data class BrandFontPreset(
  val id: String,
  val name: String,
  val fontFamily: String,
  val customFontPath: String? = null,
  val defaultColor: Long = 0xFFFFFFFF,
  val fontWeight: Int = 800,
  val letterSpacing: Float = 0f
)

object FontCatalog {

  val FONT_CATEGORIES = listOf(
    "My Fonts",
    "Brand Fonts",
    "Trending",
    "Urdu",
    "English",
    "Classic",
    "New",
    "Whimsical"
  )

  val TRENDING_FONTS = listOf(
    FontOption("Impact", "Impact Heavy", "Trending", "TRENDING HEADLINE"),
    FontOption("Montserrat", "Montserrat Bold", "Trending", "Montserrat Geometric"),
    FontOption("Bebas", "Bebas Neue", "Trending", "BEBAS TITLE DISPLAY"),
    FontOption("Sans-Serif", "Modern Clean Sans", "Trending", "Modern Clean Display")
  )

  val URDU_FONTS = listOf(
    FontOption("jameel_nastaliq", "Jameel Noori Nastaliq", "Urdu", "جمیل نوری نستعلیق خطاطی"),
    FontOption("nastaleeq", "Urdu Calligraphy Nastaleeq", "Urdu", "اردو خطاطی و خوبصورت عنوان"),
    FontOption("alvi_nastaliq", "Alvi Nastaleeq", "Urdu", "علوی نستعلیق پاکستان"),
    FontOption("urdu_naskh", "Urdu Naskh Modern", "Urdu", "نسخ اردو جدید سرخیاں"),
    FontOption("gulzar", "Gulzar Urdu Display", "Urdu", "گلزار اردو شاہکار"),
    FontOption("scheherazade", "Scheherazade Urdu", "Urdu", "شہربانو اردو فونٹس"),
    FontOption("lateef", "Lateef Urdu Script", "Urdu", "لطیف اردو خط"),
    FontOption("kasheeda", "Kasheeda Calligraphy", "Urdu", "کشیدہ خطاطی شاہی")
  )

  val ENGLISH_FONTS = listOf(
    FontOption("Sans-Serif", "Modern Sans", "English", "Modern Clean Sans"),
    FontOption("Serif", "Editorial Serif", "English", "Editorial Headline"),
    FontOption("Monospace", "Monospace Code", "English", "0101_CODE_CONSOLE"),
    FontOption("Impact", "Impact Block", "English", "IMPACT BOLD DISPLAY"),
    FontOption("Bebas", "Bebas Tall", "English", "BEBAS CONDENSED"),
    FontOption("Montserrat", "Montserrat Sans", "English", "Montserrat Minimal"),
    FontOption("Playfair", "Playfair Display", "English", "Playfair Luxury Serif"),
    FontOption("Cinematic", "Cinematic Wide", "English", "CINEMATIC SCOPE")
  )

  val CLASSIC_FONTS = listOf(
    FontOption("Playfair", "Playfair Editorial", "Classic", "Classic Literature"),
    FontOption("Serif", "Times Classic Serif", "Classic", "Timeless Elegance"),
    FontOption("Cinematic", "Imperial Roman Serif", "Classic", "IMPERIAL CHRONICLE")
  )

  val NEW_FONTS = listOf(
    FontOption("Futuristic", "Cyberpunk 2088", "New", "CYBER MATRIX TECH"),
    FontOption("Monospace", "Neon Terminal Glitch", "New", ">_ TERMINAL_NEW"),
    FontOption("Impact", "Acid Pop Display", "New", "ACID POP POSTER")
  )

  val WHIMSICAL_FONTS = listOf(
    FontOption("Cursive", "Creative Romantic Script", "Whimsical", "Whimsical & Magical ✨"),
    FontOption("Cursive", "Handwritten Signature", "Whimsical", "Lovely Handcraft"),
    FontOption("Sans-Serif", "Playful Bubble Comic", "Whimsical", "Playful Fun Vibes 🎉")
  )

  // In-memory brand fonts repository
  private val _brandFonts = mutableListOf(
    BrandFontPreset("brand_primary", "Studio Brand Primary", "Montserrat", defaultColor = 0xFF00E5FF, fontWeight = 900),
    BrandFontPreset("brand_secondary", "Studio Brand Serif", "Playfair", defaultColor = 0xFFFFD700, fontWeight = 700),
    BrandFontPreset("brand_urdu", "برانڈ اردو خطاطی", "jameel_nastaliq", defaultColor = 0xFF10B981, fontWeight = 800)
  )

  fun getBrandFonts(): List<BrandFontPreset> = _brandFonts.toList()

  fun addBrandFont(preset: BrandFontPreset) {
    _brandFonts.add(preset)
  }

  fun getFontsForCategory(
    category: String,
    allAvailableFonts: List<FontOption>
  ): List<FontOption> {
    return when (category) {
      "My Fonts" -> allAvailableFonts.filter { it.isCustom || it.category == "Custom" }
      "Brand Fonts" -> {
        _brandFonts.map { bf ->
          FontOption(
            id = bf.fontFamily,
            name = "🏷️ ${bf.name}",
            category = "Brand Fonts",
            nativeSample = bf.name,
            isCustom = bf.customFontPath != null,
            filePath = bf.customFontPath
          )
        }
      }
      "Trending" -> TRENDING_FONTS
      "Urdu" -> allAvailableFonts.filter { it.category == "Urdu" || it.id.lowercase().contains("urdu") || it.id.lowercase().contains("nastaliq") || it.id.lowercase().contains("nastaleeq") }
      "English" -> ENGLISH_FONTS
      "Classic" -> CLASSIC_FONTS
      "New" -> NEW_FONTS
      "Whimsical" -> WHIMSICAL_FONTS
      else -> allAvailableFonts
    }
  }
}
