package com.example.ui.components.elements

object ElementsCatalog {

  val allElements: List<ElementItem> = listOf(
    // ==========================================
    // 1. SHAPES (Circles, Squares, Rectangles, Lines, Arrows, Basic geometric shapes, Variations)
    // ==========================================
    ElementItem(
      id = "shape_circle_solid",
      title = "Circle",
      subtitle = "Solid geometric circle",
      category = ElementCategory.SHAPES,
      tags = listOf("circle", "shape", "round", "solid", "geometry", "disc"),
      iconSymbol = "⚪",
      vectorType = "circle_solid",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF0091FF
    ),
    ElementItem(
      id = "shape_circle_outline",
      title = "Circle Ring",
      subtitle = "Clean outline ring",
      category = ElementCategory.SHAPES,
      tags = listOf("circle", "ring", "outline", "shape", "hollow"),
      iconSymbol = "⭕",
      vectorType = "circle_outline",
      primaryColor = 0xFFFF0055,
      secondaryColor = 0xFFFF7700
    ),
    ElementItem(
      id = "shape_circle_gradient",
      title = "Glow Sphere",
      subtitle = "Radial gradient circle",
      category = ElementCategory.SHAPES,
      tags = listOf("circle", "sphere", "glow", "radial", "gradient"),
      iconSymbol = "🟣",
      vectorType = "circle_gradient",
      primaryColor = 0xFF9D00FF,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "shape_square_solid",
      title = "Square",
      subtitle = "Solid rounded box",
      category = ElementCategory.SHAPES,
      tags = listOf("square", "box", "solid", "shape", "rectangle"),
      iconSymbol = "⬛",
      vectorType = "square_solid",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF0055FF
    ),
    ElementItem(
      id = "shape_square_outline",
      title = "Square Frame",
      subtitle = "Bordered square outline",
      category = ElementCategory.SHAPES,
      tags = listOf("square", "border", "frame", "outline", "box"),
      iconSymbol = "🔲",
      vectorType = "square_outline",
      primaryColor = 0xFFFFCC00,
      secondaryColor = 0xFFFF6600
    ),
    ElementItem(
      id = "shape_rect_banner",
      title = "Rectangle",
      subtitle = "Horizontal pill banner",
      category = ElementCategory.SHAPES,
      tags = listOf("rectangle", "rect", "banner", "pill", "bar", "strip"),
      iconSymbol = "▬",
      vectorType = "rect_banner",
      defaultScale = 1.2f,
      primaryColor = 0xFF00FF88,
      secondaryColor = 0xFF0088FF
    ),
    ElementItem(
      id = "shape_rect_card",
      title = "Card Box",
      subtitle = "Rounded corner rectangle",
      category = ElementCategory.SHAPES,
      tags = listOf("rectangle", "card", "box", "rect", "panel"),
      iconSymbol = "▭",
      vectorType = "rect_card",
      primaryColor = 0xFF3D5AFE,
      secondaryColor = 0xFF651FFF
    ),
    ElementItem(
      id = "shape_line_solid",
      title = "Solid Line",
      subtitle = "Clean divider line",
      category = ElementCategory.SHAPES,
      tags = listOf("line", "divider", "separator", "rule", "straight"),
      iconSymbol = "―",
      vectorType = "line_solid",
      defaultScale = 1.3f,
      primaryColor = 0xFFFFFFFF,
      secondaryColor = 0xFF888888
    ),
    ElementItem(
      id = "shape_line_dashed",
      title = "Dashed Line",
      subtitle = "Dashed segment divider",
      category = ElementCategory.SHAPES,
      tags = listOf("line", "dashed", "divider", "dash"),
      iconSymbol = "┄",
      vectorType = "line_dashed",
      defaultScale = 1.3f,
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "shape_arrow_right",
      title = "Right Arrow",
      subtitle = "Pointer arrow line",
      category = ElementCategory.SHAPES,
      tags = listOf("arrow", "right", "pointer", "direction", "forward"),
      iconSymbol = "➔",
      vectorType = "arrow_right",
      defaultScale = 1.2f,
      primaryColor = 0xFFFF3366,
      secondaryColor = 0xFFFF9933
    ),
    ElementItem(
      id = "shape_arrow_curve",
      title = "Curved Arrow",
      subtitle = "Dynamic bezier swoosh",
      category = ElementCategory.SHAPES,
      tags = listOf("arrow", "curved", "swoosh", "turn", "pointer"),
      iconSymbol = "➥",
      vectorType = "arrow_curve",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF4500
    ),
    ElementItem(
      id = "shape_triangle",
      title = "Triangle",
      subtitle = "Equilateral triangle",
      category = ElementCategory.SHAPES,
      tags = listOf("triangle", "polygon", "geometric", "pyramid", "shape"),
      iconSymbol = "▲",
      vectorType = "triangle",
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFF7F00FF
    ),
    ElementItem(
      id = "shape_star_5",
      title = "Star (5-Pt)",
      subtitle = "Golden 5-pointed star",
      category = ElementCategory.SHAPES,
      tags = listOf("star", "favorite", "rating", "gold", "shape"),
      iconSymbol = "★",
      vectorType = "star_5",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFFA500
    ),
    ElementItem(
      id = "shape_hexagon",
      title = "Hexagon",
      subtitle = "Honeycomb 6-sided polygon",
      category = ElementCategory.SHAPES,
      tags = listOf("hexagon", "polygon", "honeycomb", "tech", "geometry"),
      iconSymbol = "⬡",
      vectorType = "hexagon",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF18FFFF
    ),
    ElementItem(
      id = "shape_heart",
      title = "Heart",
      subtitle = "Smooth vector heart",
      category = ElementCategory.SHAPES,
      tags = listOf("heart", "love", "like", "romance", "shape"),
      iconSymbol = "♥",
      vectorType = "heart",
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFF5252
    ),
    ElementItem(
      id = "shape_speech_bubble",
      title = "Speech Bubble",
      subtitle = "Chat callout bubble",
      category = ElementCategory.SHAPES,
      tags = listOf("bubble", "speech", "chat", "talk", "dialog", "callout"),
      iconSymbol = "💬",
      vectorType = "speech_bubble",
      primaryColor = 0xFF2979FF,
      secondaryColor = 0xFF00E5FF
    ),

    // ==========================================
    // 2. GRAPHICS (Illustrations, Decorative graphics, Stickers, Graphic objects)
    // ==========================================
    ElementItem(
      id = "graphic_sunburst",
      title = "Sunburst",
      subtitle = "Radiant rays burst",
      category = ElementCategory.GRAPHICS,
      tags = listOf("sunburst", "rays", "sun", "energy", "graphic", "burst"),
      iconSymbol = "☀️",
      vectorType = "sunburst",
      primaryColor = 0xFFFFB300,
      secondaryColor = 0xFFFF6D00
    ),
    ElementItem(
      id = "graphic_sparkles",
      title = "Sparkles",
      subtitle = "Magic star clusters",
      category = ElementCategory.GRAPHICS,
      tags = listOf("sparkles", "stars", "magic", "glow", "glitter", "sparkle"),
      iconSymbol = "✨",
      vectorType = "sparkles",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "graphic_ribbon",
      title = "Ribbon Banner",
      subtitle = "Award golden ribbon",
      category = ElementCategory.GRAPHICS,
      tags = listOf("ribbon", "banner", "award", "badge", "decoration"),
      iconSymbol = "🎗️",
      vectorType = "ribbon",
      defaultScale = 1.2f,
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF8F00
    ),
    ElementItem(
      id = "graphic_sale_tag",
      title = "Sale Tag",
      subtitle = "Hot discount sticker",
      category = ElementCategory.GRAPHICS,
      tags = listOf("sale", "tag", "discount", "offer", "sticker", "promo"),
      iconSymbol = "🏷️",
      vectorType = "sale_tag",
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFF9100
    ),
    ElementItem(
      id = "graphic_verified_stamp",
      title = "Verified Badge",
      subtitle = "Official seal checkmark",
      category = ElementCategory.GRAPHICS,
      tags = listOf("verified", "check", "badge", "trust", "seal", "stamp"),
      iconSymbol = "🛡️",
      vectorType = "verified_stamp",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF00B0FF
    ),
    ElementItem(
      id = "graphic_trophy",
      title = "Trophy Cup",
      subtitle = "Winner champion trophy",
      category = ElementCategory.GRAPHICS,
      tags = listOf("trophy", "cup", "winner", "first", "champion", "award"),
      iconSymbol = "🏆",
      vectorType = "trophy",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF6F00
    ),
    ElementItem(
      id = "graphic_flame",
      title = "Fire Flame",
      subtitle = "Trending hot flame",
      category = ElementCategory.GRAPHICS,
      tags = listOf("fire", "flame", "hot", "trending", "burn"),
      iconSymbol = "🔥",
      vectorType = "fire_flame",
      primaryColor = 0xFFFF3D00,
      secondaryColor = 0xFFFFEA00
    ),
    ElementItem(
      id = "graphic_target",
      title = "Bullseye Target",
      subtitle = "Precision concentric target",
      category = ElementCategory.GRAPHICS,
      tags = listOf("target", "bullseye", "goal", "aim", "focus"),
      iconSymbol = "🎯",
      vectorType = "target",
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFFFFFF
    ),

    // ==========================================
    // 3. FRAMES (Image frames, Video frames, Different frame shapes/styles)
    // ==========================================
    ElementItem(
      id = "frame_polaroid",
      title = "Polaroid Frame",
      subtitle = "Classic instant photo",
      category = ElementCategory.FRAMES,
      tags = listOf("polaroid", "photo", "frame", "retro", "image", "instant"),
      iconSymbol = "📷",
      vectorType = "frame_polaroid",
      defaultScale = 1.15f,
      primaryColor = 0xFFEEEEEE,
      secondaryColor = 0xFF212121
    ),
    ElementItem(
      id = "frame_phone",
      title = "Phone Mockup",
      subtitle = "Smartphone bezel frame",
      category = ElementCategory.FRAMES,
      tags = listOf("phone", "smartphone", "mockup", "mobile", "device", "frame"),
      iconSymbol = "📱",
      vectorType = "frame_phone",
      defaultScale = 1.2f,
      primaryColor = 0xFF263238,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "frame_cinema",
      title = "Film Reel",
      subtitle = "35mm filmstrip border",
      category = ElementCategory.FRAMES,
      tags = listOf("film", "reel", "cinema", "movie", "video", "strip", "frame"),
      iconSymbol = "🎞️",
      vectorType = "frame_film",
      defaultScale = 1.25f,
      primaryColor = 0xFF1A1A1A,
      secondaryColor = 0xFFFFD700
    ),
    ElementItem(
      id = "frame_circular",
      title = "Avatar Ring",
      subtitle = "Gradient profile circle",
      category = ElementCategory.FRAMES,
      tags = listOf("avatar", "profile", "circle", "ring", "story", "frame"),
      iconSymbol = "⭕",
      vectorType = "frame_circle",
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "frame_neon",
      title = "Neon Glow Frame",
      subtitle = "Cyberpunk glowing border",
      category = ElementCategory.FRAMES,
      tags = listOf("neon", "cyber", "glow", "futuristic", "border", "frame"),
      iconSymbol = "🟩",
      vectorType = "frame_neon",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFFD500F9
    ),
    ElementItem(
      id = "frame_minimal",
      title = "Minimal Glass",
      subtitle = "Clean frosted border",
      category = ElementCategory.FRAMES,
      tags = listOf("glass", "minimal", "clean", "frosted", "card", "frame"),
      iconSymbol = "🪟",
      vectorType = "frame_minimal",
      primaryColor = 0xFFFFFFFF,
      secondaryColor = 0xFF90CAF9
    ),

    // ==========================================
    // 4. TABLES (Different table layouts, Rows/columns variations, Editable tables)
    // ==========================================
    ElementItem(
      id = "table_2x2",
      title = "2x2 Grid",
      subtitle = "Quick comparison table",
      category = ElementCategory.TABLES,
      tags = listOf("table", "grid", "matrix", "2x2", "data", "rows", "columns"),
      iconSymbol = "⊞",
      vectorType = "table_2x2",
      defaultScale = 1.25f,
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF1E293B
    ),
    ElementItem(
      id = "table_3x3",
      title = "3x3 Data Sheet",
      subtitle = "Header & matrix rows",
      category = ElementCategory.TABLES,
      tags = listOf("table", "data", "sheet", "3x3", "matrix", "rows"),
      iconSymbol = "▦",
      vectorType = "table_3x3",
      defaultScale = 1.3f,
      primaryColor = 0xFF38EF7D,
      secondaryColor = 0xFF11998E
    ),
    ElementItem(
      id = "table_pricing",
      title = "Pricing Plan",
      subtitle = "Tiered feature table",
      category = ElementCategory.TABLES,
      tags = listOf("table", "pricing", "plan", "subscription", "features"),
      iconSymbol = "💳",
      vectorType = "table_pricing",
      defaultScale = 1.25f,
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFF7928CA
    ),
    ElementItem(
      id = "table_scoreboard",
      title = "Scoreboard",
      subtitle = "Match score stats",
      category = ElementCategory.TABLES,
      tags = listOf("table", "scoreboard", "score", "match", "vs", "stats"),
      iconSymbol = "⏱️",
      vectorType = "table_scoreboard",
      defaultScale = 1.2f,
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFF141926
    ),

    // ==========================================
    // 5. 3D (3D objects, 3D shapes, 3D graphics, 3D decorative elements)
    // ==========================================
    ElementItem(
      id = "3d_cube",
      title = "3D Cube",
      subtitle = "Isometric shaded cube",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "cube", "box", "isometric", "geometry", "depth"),
      iconSymbol = "🧊",
      vectorType = "3d_cube",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF0072FF
    ),
    ElementItem(
      id = "3d_sphere",
      title = "3D Sphere",
      subtitle = "Specular orb sphere",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "sphere", "orb", "ball", "glossy", "volume"),
      iconSymbol = "🔮",
      vectorType = "3d_sphere",
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFF7928CA
    ),
    ElementItem(
      id = "3d_pyramid",
      title = "3D Pyramid",
      subtitle = "Faceted shaded pyramid",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "pyramid", "facets", "triangle", "depth"),
      iconSymbol = "🔺",
      vectorType = "3d_pyramid",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF6D00
    ),
    ElementItem(
      id = "3d_cylinder",
      title = "3D Cylinder",
      subtitle = "Volumetric column pillar",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "cylinder", "pillar", "can", "tube"),
      iconSymbol = "🥫",
      vectorType = "3d_cylinder",
      primaryColor = 0xFF00FF88,
      secondaryColor = 0xFF0088FF
    ),
    ElementItem(
      id = "3d_gem",
      title = "3D Diamond",
      subtitle = "Cut crystal jewel gem",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "diamond", "gem", "crystal", "jewel", "luxury"),
      iconSymbol = "💎",
      vectorType = "3d_gem",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFFE040FB
    ),
    ElementItem(
      id = "3d_coin",
      title = "3D Gold Coin",
      subtitle = "Embossed crypto coin",
      category = ElementCategory.THREE_D,
      tags = listOf("3d", "coin", "gold", "crypto", "money", "token"),
      iconSymbol = "🪙",
      vectorType = "3d_coin",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF8F00
    ),

    // ==========================================
    // 6. ICONS (Social icons, Business icons, UI icons, General-purpose icons)
    // ==========================================
    ElementItem(
      id = "icon_youtube",
      title = "YouTube",
      subtitle = "Social media play",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "youtube", "social", "video", "play", "media"),
      iconSymbol = "▶️",
      vectorType = "icon_youtube",
      primaryColor = 0xFFFF0000,
      secondaryColor = 0xFFFFFFFF
    ),
    ElementItem(
      id = "icon_instagram",
      title = "Instagram",
      subtitle = "Camera social gradient",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "instagram", "social", "photo", "ig", "media"),
      iconSymbol = "📸",
      vectorType = "icon_instagram",
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFFFFC107
    ),
    ElementItem(
      id = "icon_tiktok",
      title = "TikTok",
      subtitle = "Music note icon",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "tiktok", "social", "music", "video"),
      iconSymbol = "🎵",
      vectorType = "icon_tiktok",
      primaryColor = 0xFF00F2FE,
      secondaryColor = 0xFFFE0979
    ),
    ElementItem(
      id = "icon_briefcase",
      title = "Business Case",
      subtitle = "Commerce & enterprise",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "business", "briefcase", "work", "job", "career"),
      iconSymbol = "💼",
      vectorType = "icon_briefcase",
      primaryColor = 0xFF795548,
      secondaryColor = 0xFFFFD700
    ),
    ElementItem(
      id = "icon_growth",
      title = "Growth Trend",
      subtitle = "Rising market graph",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "growth", "trend", "chart", "business", "profit"),
      iconSymbol = "📈",
      vectorType = "icon_growth",
      primaryColor = 0xFF00E676,
      secondaryColor = 0xFF00B0FF
    ),
    ElementItem(
      id = "icon_bell",
      title = "Alert Bell",
      subtitle = "Notification alert",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "bell", "notification", "alert", "ui", "ring"),
      iconSymbol = "🔔",
      vectorType = "icon_bell",
      primaryColor = 0xFFFFD700,
      secondaryColor = 0xFFFF9100
    ),
    ElementItem(
      id = "icon_settings",
      title = "Settings Gear",
      subtitle = "Configuration cog",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "settings", "gear", "cog", "options", "ui"),
      iconSymbol = "⚙️",
      vectorType = "icon_settings",
      primaryColor = 0xFF90A4AE,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "icon_mappin",
      title = "Map Pin",
      subtitle = "GPS location marker",
      category = ElementCategory.ICONS,
      tags = listOf("icon", "map", "pin", "location", "gps", "place"),
      iconSymbol = "📍",
      vectorType = "icon_mappin",
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFF8A80
    ),

    // ==========================================
    // 7. ANIMALS (Animal illustrations, shapes, graphics)
    // ==========================================
    ElementItem(
      id = "animal_lion",
      title = "Lion",
      subtitle = "Majestic king lion",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "lion", "wild", "cat", "king", "safari"),
      iconSymbol = "🦁",
      vectorType = "animal_lion",
      primaryColor = 0xFFFFB300,
      secondaryColor = 0xFFD84315
    ),
    ElementItem(
      id = "animal_tiger",
      title = "Tiger",
      subtitle = "Striped fierce tiger",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "tiger", "striped", "wild", "safari"),
      iconSymbol = "🐯",
      vectorType = "animal_tiger",
      primaryColor = 0xFFFF6D00,
      secondaryColor = 0xFF212121
    ),
    ElementItem(
      id = "animal_elephant",
      title = "Elephant",
      subtitle = "Gentle giant elephant",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "elephant", "giant", "safari", "nature"),
      iconSymbol = "🐘",
      vectorType = "animal_elephant",
      primaryColor = 0xFF78909C,
      secondaryColor = 0xFFB0BEC5
    ),
    ElementItem(
      id = "animal_horse",
      title = "Horse",
      subtitle = "Galloping wild horse",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "horse", "equine", "gallop", "speed"),
      iconSymbol = "🐎",
      vectorType = "animal_horse",
      primaryColor = 0xFF8D6E63,
      secondaryColor = 0xFFD7CCC8
    ),
    ElementItem(
      id = "animal_dog",
      title = "Dog",
      subtitle = "Loyal companion puppy",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "dog", "puppy", "pet", "canine", "loyal"),
      iconSymbol = "🐕",
      vectorType = "animal_dog",
      primaryColor = 0xFFFFB74D,
      secondaryColor = 0xFF8D6E63
    ),
    ElementItem(
      id = "animal_cat",
      title = "Cat",
      subtitle = "Curious feline cat",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "cat", "kitten", "pet", "feline"),
      iconSymbol = "🐈",
      vectorType = "animal_cat",
      primaryColor = 0xFFFF8A65,
      secondaryColor = 0xFF37474F
    ),
    ElementItem(
      id = "animal_rabbit",
      title = "Rabbit",
      subtitle = "Playful cute bunny",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "rabbit", "bunny", "cute", "nature"),
      iconSymbol = "🐇",
      vectorType = "animal_rabbit",
      primaryColor = 0xFFECEFF1,
      secondaryColor = 0xFFFF80AB
    ),
    ElementItem(
      id = "animal_dolphin",
      title = "Dolphin",
      subtitle = "Ocean leaping dolphin",
      category = ElementCategory.ANIMALS,
      tags = listOf("animal", "dolphin", "ocean", "sea", "marine", "water"),
      iconSymbol = "🐬",
      vectorType = "animal_dolphin",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF0288D1
    ),

    // ==========================================
    // 8. BIRDS (Different bird types, flying birds, bird illustrations)
    // ==========================================
    ElementItem(
      id = "bird_eagle",
      title = "Eagle",
      subtitle = "Soaring bald eagle",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "eagle", "fly", "flying", "soar", "wings", "raptor"),
      iconSymbol = "🦅",
      vectorType = "bird_eagle",
      primaryColor = 0xFF8D6E63,
      secondaryColor = 0xFFFFD700
    ),
    ElementItem(
      id = "bird_falcon",
      title = "Falcon",
      subtitle = "Speed hunting falcon",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "falcon", "fly", "flying", "fast", "wings"),
      iconSymbol = "🪶",
      vectorType = "bird_falcon",
      primaryColor = 0xFF5D4037,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "bird_owl",
      title = "Wise Owl",
      subtitle = "Nocturnal night owl",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "owl", "wise", "night", "nature"),
      iconSymbol = "🦉",
      vectorType = "bird_owl",
      primaryColor = 0xFF795548,
      secondaryColor = 0xFFFFB300
    ),
    ElementItem(
      id = "bird_parrot",
      title = "Parrot",
      subtitle = "Tropical colorful parrot",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "parrot", "colorful", "tropical", "wings"),
      iconSymbol = "🦜",
      vectorType = "bird_parrot",
      primaryColor = 0xFF00E676,
      secondaryColor = 0xFFFF1744
    ),
    ElementItem(
      id = "bird_hummingbird",
      title = "Hummingbird",
      subtitle = "Hovering swift bird",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "hummingbird", "fly", "flying", "hover", "nature"),
      iconSymbol = "🐦",
      vectorType = "bird_hummingbird",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF76FF03
    ),
    ElementItem(
      id = "bird_flamingo",
      title = "Flamingo",
      subtitle = "Graceful pink flamingo",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "flamingo", "pink", "graceful", "tropical"),
      iconSymbol = "🦩",
      vectorType = "bird_flamingo",
      primaryColor = 0xFFFF4081,
      secondaryColor = 0xFFFF80AB
    ),
    ElementItem(
      id = "bird_swan",
      title = "Swan",
      subtitle = "Serene lake swan",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "swan", "lake", "water", "white", "elegant"),
      iconSymbol = "🦢",
      vectorType = "bird_swan",
      primaryColor = 0xFFFFFFFF,
      secondaryColor = 0xFF90CAF9
    ),
    ElementItem(
      id = "bird_flock",
      title = "Flying Flock",
      subtitle = "Birds flying in silhouette",
      category = ElementCategory.BIRDS,
      tags = listOf("bird", "flock", "flying", "sky", "freedom", "fly"),
      iconSymbol = "🕊️",
      vectorType = "bird_flock",
      defaultScale = 1.2f,
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFFFFFFFF
    ),

    // ==========================================
    // 9. CHARACTERS (Male, Female, Children, Old men, Old women, Urban, Rural, Poses)
    // ==========================================
    ElementItem(
      id = "char_male_casual",
      title = "Male (Casual)",
      subtitle = "Young smiling man",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "male", "man", "guy", "boy", "person", "casual"),
      iconSymbol = "👨",
      vectorType = "char_male_casual",
      primaryColor = 0xFF2979FF,
      secondaryColor = 0xFFFFD180
    ),
    ElementItem(
      id = "char_male_suit",
      title = "Male (Business)",
      subtitle = "Professional suit executive",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "male", "man", "business", "suit", "executive"),
      iconSymbol = "👔",
      vectorType = "char_male_suit",
      primaryColor = 0xFF1A237E,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "char_female_casual",
      title = "Female (Casual)",
      subtitle = "Young creator woman",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "female", "woman", "girl", "lady", "person"),
      iconSymbol = "👩",
      vectorType = "char_female_casual",
      primaryColor = 0xFFFF4081,
      secondaryColor = 0xFFFFE0B2
    ),
    ElementItem(
      id = "char_female_exec",
      title = "Female (Executive)",
      subtitle = "Business leader lady",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "female", "woman", "executive", "business", "leader"),
      iconSymbol = "💼",
      vectorType = "char_female_exec",
      primaryColor = 0xFF651FFF,
      secondaryColor = 0xFFFF80AB
    ),
    ElementItem(
      id = "char_child_boy",
      title = "Child (Boy)",
      subtitle = "Playful kid waving",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "child", "children", "kid", "boy", "young"),
      iconSymbol = "👦",
      vectorType = "char_child_boy",
      primaryColor = 0xFFFF9100,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "char_child_girl",
      title = "Child (Girl)",
      subtitle = "Happy school student",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "child", "children", "kid", "girl", "student"),
      iconSymbol = "👧",
      vectorType = "char_child_girl",
      primaryColor = 0xFFFF4081,
      secondaryColor = 0xFFFFFF00
    ),
    ElementItem(
      id = "char_old_man",
      title = "Elder Man",
      subtitle = "Wise grandfather avatar",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "old", "man", "elder", "grandfather", "senior"),
      iconSymbol = "👴",
      vectorType = "char_old_man",
      primaryColor = 0xFF78909C,
      secondaryColor = 0xFFD7CCC8
    ),
    ElementItem(
      id = "char_old_woman",
      title = "Elder Woman",
      subtitle = "Kind grandmother avatar",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "old", "woman", "elder", "grandmother", "senior"),
      iconSymbol = "👵",
      vectorType = "char_old_woman",
      primaryColor = 0xFF8D6E63,
      secondaryColor = 0xFFFFCDD2
    ),
    ElementItem(
      id = "char_urban_skater",
      title = "Urban Skater",
      subtitle = "Streetwear city character",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "urban", "city", "skater", "street", "modern"),
      iconSymbol = "🛹",
      vectorType = "char_urban_skater",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFFFFEA00
    ),
    ElementItem(
      id = "char_rural_farmer",
      title = "Rural Farmer",
      subtitle = "Country agriculture avatar",
      category = ElementCategory.CHARACTERS,
      tags = listOf("character", "rural", "farmer", "country", "agriculture"),
      iconSymbol = "🌾",
      vectorType = "char_rural_farmer",
      primaryColor = 0xFF689F38,
      secondaryColor = 0xFFFFB300
    ),

    // ==========================================
    // 10. VEHICLES (Cars, Motorcycles, Trucks, Jeeps, Tractors, Trailers, Buses, Others)
    // ==========================================
    ElementItem(
      id = "vehicle_sedan",
      title = "City Car",
      subtitle = "Modern compact sedan",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "car", "sedan", "auto", "drive", "transport"),
      iconSymbol = "🚗",
      vectorType = "vehicle_sedan",
      defaultScale = 1.2f,
      primaryColor = 0xFF2979FF,
      secondaryColor = 0xFF1565C0
    ),
    ElementItem(
      id = "vehicle_supercar",
      title = "GT Supercar",
      subtitle = "High-speed sports coupe",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "car", "sports", "supercar", "fast", "speed", "race"),
      iconSymbol = "🏎️",
      vectorType = "vehicle_supercar",
      defaultScale = 1.25f,
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFFD700
    ),
    ElementItem(
      id = "vehicle_motorcycle",
      title = "Motorcycle",
      subtitle = "Sport road motorbike",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "motorcycle", "bike", "moto", "ride"),
      iconSymbol = "🏍️",
      vectorType = "vehicle_motorcycle",
      primaryColor = 0xFFFF9100,
      secondaryColor = 0xFF212121
    ),
    ElementItem(
      id = "vehicle_truck",
      title = "Cargo Truck",
      subtitle = "Heavy transport semi truck",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "truck", "cargo", "haul", "transport", "freight"),
      iconSymbol = "🚚",
      vectorType = "vehicle_truck",
      defaultScale = 1.25f,
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF0D47A1
    ),
    ElementItem(
      id = "vehicle_jeep",
      title = "4x4 Jeep",
      subtitle = "Off-road safari SUV",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "jeep", "4x4", "suv", "offroad", "safari"),
      iconSymbol = "🚙",
      vectorType = "vehicle_jeep",
      primaryColor = 0xFF2E7D32,
      secondaryColor = 0xFFFFD700
    ),
    ElementItem(
      id = "vehicle_tractor",
      title = "Farm Tractor",
      subtitle = "Agricultural harvest machine",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "tractor", "farm", "agriculture", "harvest"),
      iconSymbol = "🚜",
      vectorType = "vehicle_tractor",
      primaryColor = 0xFFD84315,
      secondaryColor = 0xFFFFEA00
    ),
    ElementItem(
      id = "vehicle_trailer",
      title = "Camper Trailer",
      subtitle = "Travel RV road trailer",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "trailer", "camper", "rv", "camping", "travel"),
      iconSymbol = "🏕️",
      vectorType = "vehicle_trailer",
      primaryColor = 0xFF00BCD4,
      secondaryColor = 0xFFFFFFFF
    ),
    ElementItem(
      id = "vehicle_bus",
      title = "City Bus",
      subtitle = "Public transit metro bus",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "bus", "transit", "public", "metro", "transport"),
      iconSymbol = "🚌",
      vectorType = "vehicle_bus",
      defaultScale = 1.2f,
      primaryColor = 0xFFFFD600,
      secondaryColor = 0xFF212121
    ),
    ElementItem(
      id = "vehicle_ambulance",
      title = "Ambulance",
      subtitle = "Emergency paramedic unit",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "ambulance", "emergency", "medical", "rescue"),
      iconSymbol = "🚑",
      vectorType = "vehicle_ambulance",
      primaryColor = 0xFFFF1744,
      secondaryColor = 0xFFFFFFFF
    ),
    ElementItem(
      id = "vehicle_airplane",
      title = "Commercial Jet",
      subtitle = "Passenger airline airplane",
      category = ElementCategory.VEHICLES,
      tags = listOf("vehicle", "airplane", "plane", "jet", "fly", "flight", "air"),
      iconSymbol = "✈️",
      vectorType = "vehicle_airplane",
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFFFFFFFF
    ),

    // ==========================================
    // 11. CHARTS (Bar charts, Line charts, Pie charts, Graphs, Data charts)
    // ==========================================
    ElementItem(
      id = "chart_bar",
      title = "Bar Chart",
      subtitle = "Multi-column metric bar",
      category = ElementCategory.CHARTS,
      tags = listOf("chart", "bar", "graph", "metric", "data", "stats", "columns"),
      iconSymbol = "📊",
      vectorType = "chart_bar",
      defaultScale = 1.25f,
      primaryColor = 0xFF00E5FF,
      secondaryColor = 0xFF7000FF
    ),
    ElementItem(
      id = "chart_line",
      title = "Line Chart",
      subtitle = "Smooth metric trend line",
      category = ElementCategory.CHARTS,
      tags = listOf("chart", "line", "trend", "graph", "curve", "growth"),
      iconSymbol = "📈",
      vectorType = "chart_line",
      defaultScale = 1.25f,
      primaryColor = 0xFF00FF88,
      secondaryColor = 0xFF0088FF
    ),
    ElementItem(
      id = "chart_pie",
      title = "Pie Chart",
      subtitle = "3-slice proportional pie",
      category = ElementCategory.CHARTS,
      tags = listOf("chart", "pie", "slices", "ratio", "percentage", "data"),
      iconSymbol = "🥧",
      vectorType = "chart_pie",
      primaryColor = 0xFFFF007F,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "chart_donut",
      title = "Donut Gauge",
      subtitle = "Circular percentage ring",
      category = ElementCategory.CHARTS,
      tags = listOf("chart", "donut", "ring", "gauge", "percentage", "progress"),
      iconSymbol = "🍩",
      vectorType = "chart_donut",
      primaryColor = 0xFF76FF03,
      secondaryColor = 0xFF00E5FF
    ),
    ElementItem(
      id = "chart_area",
      title = "Area Gradient",
      subtitle = "Filled volume area graph",
      category = ElementCategory.CHARTS,
      tags = listOf("chart", "area", "gradient", "graph", "stats"),
      iconSymbol = "📉",
      vectorType = "chart_area",
      defaultScale = 1.2f,
      primaryColor = 0xFFFF9100,
      secondaryColor = 0xFFFF1744
    )
  )

  fun getByCategory(category: ElementCategory): List<ElementItem> {
    return allElements.filter { it.category == category }
  }

  fun search(query: String): List<ElementItem> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return allElements
    return allElements.filter { item ->
      item.title.lowercase().contains(q) ||
      item.subtitle.lowercase().contains(q) ||
      item.category.displayName.lowercase().contains(q) ||
      item.tags.any { it.lowercase().contains(q) }
    }
  }

  fun findById(id: String): ElementItem? {
    return allElements.find { it.id == id }
  }
}
