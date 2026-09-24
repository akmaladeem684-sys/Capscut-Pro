package com.example.data.presets

import com.example.domain.model.BadgeType
import com.example.domain.model.StickerAnimationType

data class StickerPresetItem(
  val id: String,
  val symbolOrAsset: String,
  val name: String,
  val category: String,
  val defaultAnimation: StickerAnimationType = StickerAnimationType.NONE,
  val badgeType: BadgeType? = null,
  val tags: List<String> = emptyList()
)

object StickersCatalog {

  val CATEGORIES = listOf(
    "Badges",
    "Animated Stickers",
    "Trending Stickers",
    "Emoji & Emotions",
    "Love & Hearts",
    "Funny & Memes",
    "Animals",
    "Food & Drinks",
    "Travel",
    "Nature",
    "Celebration",
    "Business",
    "Social Media",
    "Arrows & Shapes",
    "Speech Bubbles"
  )

  val BADGES by lazy {
    BadgeType.values().map { badge ->
      StickerPresetItem(
        id = "badge_${badge.name.lowercase()}",
        symbolOrAsset = badge.displayName,
        name = badge.displayName,
        category = "Badges",
        defaultAnimation = StickerAnimationType.NONE,
        badgeType = badge,
        tags = listOf("badge", badge.name.lowercase(), badge.subtitle.lowercase())
      )
    }
  }

  val ANIMATED_STICKERS by lazy {
    listOf(
      StickerPresetItem("anim_fire", "🔥", "Blazing Fire", "Animated Stickers", StickerAnimationType.PULSE, tags = listOf("fire", "hot")),
      StickerPresetItem("anim_sparkles", "✨", "Magic Sparkles", "Animated Stickers", StickerAnimationType.PULSE, tags = listOf("magic", "sparkle")),
      StickerPresetItem("anim_party", "🎉", "Party Popper", "Animated Stickers", StickerAnimationType.BOUNCE, tags = listOf("celebration")),
      StickerPresetItem("anim_rocket", "🚀", "Rocket Launch", "Animated Stickers", StickerAnimationType.FLOAT, tags = listOf("speed", "boost")),
      StickerPresetItem("anim_heart_pulse", "💖", "Beating Heart", "Animated Stickers", StickerAnimationType.HEARTBEAT, tags = listOf("love")),
      StickerPresetItem("anim_star", "⭐", "Spinning Star", "Animated Stickers", StickerAnimationType.SPIN, tags = listOf("star")),
      StickerPresetItem("anim_bell", "🔔", "Notification Bell", "Animated Stickers", StickerAnimationType.SHAKE, tags = listOf("alert", "subscribe")),
      StickerPresetItem("anim_trophy", "🏆", "Golden Cup", "Animated Stickers", StickerAnimationType.BOUNCE, tags = listOf("winner"))
    )
  }

  val TRENDING_STICKERS by lazy {
    listOf(
      StickerPresetItem("tr_100", "💯", "Hundred Points", "Trending Stickers", tags = listOf("perfect", "score")),
      StickerPresetItem("tr_fire", "🔥", "Lit", "Trending Stickers", StickerAnimationType.PULSE, tags = listOf("trending")),
      StickerPresetItem("tr_cool", "😎", "Cool Guy", "Trending Stickers", tags = listOf("cool")),
      StickerPresetItem("tr_mindblown", "🤯", "Mind Blown", "Trending Stickers", tags = listOf("shock")),
      StickerPresetItem("tr_crown", "👑", "Royal Crown", "Trending Stickers", tags = listOf("king", "queen")),
      StickerPresetItem("tr_money", "💸", "Flying Cash", "Trending Stickers", tags = listOf("money", "rich"))
    )
  }

  val EMOJI_EMOTIONS by lazy {
    listOf(
      StickerPresetItem("em_smile", "😊", "Smiling", "Emoji & Emotions"),
      StickerPresetItem("em_laugh", "😂", "Laughing Tears", "Emoji & Emotions"),
      StickerPresetItem("em_rofl", "🤣", "ROFL", "Emoji & Emotions"),
      StickerPresetItem("em_heart_eyes", "😍", "Heart Eyes", "Emoji & Emotions"),
      StickerPresetItem("em_kiss", "😘", "Blowing Kiss", "Emoji & Emotions"),
      StickerPresetItem("em_wink", "😉", "Winking Face", "Emoji & Emotions"),
      StickerPresetItem("em_thinking", "🤔", "Thinking Face", "Emoji & Emotions"),
      StickerPresetItem("em_shock", "😱", "Screaming Shock", "Emoji & Emotions")
    )
  }

  val LOVE_HEARTS by lazy {
    listOf(
      StickerPresetItem("lh_red", "❤️", "Red Heart", "Love & Hearts", StickerAnimationType.HEARTBEAT),
      StickerPresetItem("lh_sparkle", "💖", "Sparkling Heart", "Love & Hearts", StickerAnimationType.PULSE),
      StickerPresetItem("lh_arrow", "💘", "Heart with Arrow", "Love & Hearts"),
      StickerPresetItem("lh_two", "💕", "Two Hearts", "Love & Hearts"),
      StickerPresetItem("lh_fire", "❤️‍🔥", "Heart on Fire", "Love & Hearts")
    )
  }

  val FUNNY_MEMES by lazy {
    listOf(
      StickerPresetItem("fm_skull", "💀", "Dead Skull", "Funny & Memes"),
      StickerPresetItem("fm_clown", "🤡", "Clown", "Funny & Memes"),
      StickerPresetItem("fm_eyes", "👀", "Shifty Eyes", "Funny & Memes"),
      StickerPresetItem("fm_monkey", "🙈", "See No Evil", "Funny & Memes")
    )
  }

  val ANIMALS by lazy {
    listOf(
      StickerPresetItem("an_cat", "🐱", "Cute Cat", "Animals"),
      StickerPresetItem("an_dog", "🐶", "Happy Puppy", "Animals"),
      StickerPresetItem("an_lion", "🦁", "Lion King", "Animals"),
      StickerPresetItem("an_panda", "🐼", "Panda Bear", "Animals"),
      StickerPresetItem("an_eagle", "🦅", "Flying Eagle", "Animals")
    )
  }

  val FOOD_DRINKS by lazy {
    listOf(
      StickerPresetItem("fd_pizza", "🍕", "Pizza Slice", "Food & Drinks"),
      StickerPresetItem("fd_burger", "🍔", "Burger", "Food & Drinks"),
      StickerPresetItem("fd_coffee", "☕", "Hot Coffee", "Food & Drinks"),
      StickerPresetItem("fd_boba", "🧋", "Boba Tea", "Food & Drinks")
    )
  }

  val TRAVEL by lazy {
    listOf(
      StickerPresetItem("tr_plane", "✈️", "Airplane", "Travel"),
      StickerPresetItem("tr_beach", "🏖️", "Sunny Beach", "Travel"),
      StickerPresetItem("tr_mountain", "🏔️", "Snow Mountain", "Travel"),
      StickerPresetItem("tr_luggage", "🧳", "Luggage", "Travel")
    )
  }

  val NATURE by lazy {
    listOf(
      StickerPresetItem("nt_sun", "☀️", "Bright Sun", "Nature"),
      StickerPresetItem("nt_rainbow", "🌈", "Rainbow", "Nature"),
      StickerPresetItem("nt_flower", "🌸", "Cherry Blossom", "Nature"),
      StickerPresetItem("nt_tree", "🌴", "Palm Tree", "Nature")
    )
  }

  val CELEBRATION by lazy {
    listOf(
      StickerPresetItem("cl_cake", "🎂", "Birthday Cake", "Celebration"),
      StickerPresetItem("cl_gift", "🎁", "Gift Box", "Celebration"),
      StickerPresetItem("cl_balloon", "🎈", "Red Balloon", "Celebration"),
      StickerPresetItem("cl_confetti", "🎊", "Confetti Ball", "Celebration")
    )
  }

  val BUSINESS by lazy {
    listOf(
      StickerPresetItem("bz_briefcase", "💼", "Briefcase", "Business"),
      StickerPresetItem("bz_chart_up", "📈", "Growth Chart", "Business"),
      StickerPresetItem("bz_bulb", "💡", "Idea Lightbulb", "Business"),
      StickerPresetItem("bz_target", "🎯", "Target Hit", "Business")
    )
  }

  val SOCIAL_MEDIA by lazy {
    listOf(
      StickerPresetItem("sm_thumbsup", "👍", "Thumbs Up Like", "Social Media"),
      StickerPresetItem("sm_subscribe", "🔔", "Subscribe Bell", "Social Media", StickerAnimationType.SHAKE),
      StickerPresetItem("sm_share", "🔗", "Share Link", "Social Media"),
      StickerPresetItem("sm_pin", "📌", "Pinned", "Social Media")
    )
  }

  val ARROWS_SHAPES by lazy {
    listOf(
      StickerPresetItem("ar_right", "➡️", "Right Arrow", "Arrows & Shapes"),
      StickerPresetItem("ar_down", "⬇️", "Down Arrow", "Arrows & Shapes"),
      StickerPresetItem("ar_curved", "⤵️", "Curved Arrow", "Arrows & Shapes"),
      StickerPresetItem("ar_check", "✅", "Green Check", "Arrows & Shapes")
    )
  }

  val SPEECH_BUBBLES by lazy {
    listOf(
      StickerPresetItem("sb_bubble", "💬", "Speech Bubble", "Speech Bubbles"),
      StickerPresetItem("sb_thought", "💭", "Thought Cloud", "Speech Bubbles"),
      StickerPresetItem("sb_anger", "🗯️", "Anger Bubble", "Speech Bubbles")
    )
  }

  fun getItemsForCategory(category: String): List<StickerPresetItem> {
    return when (category) {
      "Badges" -> BADGES
      "Animated Stickers" -> ANIMATED_STICKERS
      "Trending Stickers" -> TRENDING_STICKERS
      "Emoji & Emotions" -> EMOJI_EMOTIONS
      "Love & Hearts" -> LOVE_HEARTS
      "Funny & Memes" -> FUNNY_MEMES
      "Animals" -> ANIMALS
      "Food & Drinks" -> FOOD_DRINKS
      "Travel" -> TRAVEL
      "Nature" -> NATURE
      "Celebration", "Birthday", "Wedding" -> CELEBRATION
      "Business", "Shopping", "Sale & Discount" -> BUSINESS
      "Social Media" -> SOCIAL_MEDIA
      "Arrows & Shapes", "Decorative Elements" -> ARROWS_SHAPES
      "Speech Bubbles" -> SPEECH_BUBBLES
      else -> BADGES
    }
  }

  fun getAllStickers(): List<StickerPresetItem> {
    val result = mutableListOf<StickerPresetItem>()
    result.addAll(BADGES)
    result.addAll(ANIMATED_STICKERS)
    result.addAll(TRENDING_STICKERS)
    result.addAll(EMOJI_EMOTIONS)
    result.addAll(LOVE_HEARTS)
    result.addAll(FUNNY_MEMES)
    result.addAll(ANIMALS)
    result.addAll(FOOD_DRINKS)
    result.addAll(TRAVEL)
    result.addAll(NATURE)
    result.addAll(CELEBRATION)
    result.addAll(BUSINESS)
    result.addAll(SOCIAL_MEDIA)
    result.addAll(ARROWS_SHAPES)
    result.addAll(SPEECH_BUBBLES)
    return result.distinctBy { it.id }
  }
}
