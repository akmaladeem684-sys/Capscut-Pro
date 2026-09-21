package com.vfx.engine.core.blend

enum class BlendMode {
  NORMAL,
  MULTIPLY,
  SCREEN,
  OVERLAY,
  COLOR_DODGE,
  COLOR_BURN,
  HARD_LIGHT,
  SOFT_LIGHT,
  DIFFERENCE,
  ADDITIVE
}

enum class PorterDuffMode {
  SRC_OVER,
  DST_OVER,
  SRC_IN,
  DST_IN,
  SRC_OUT,
  DST_OUT,
  CLEAR
}
