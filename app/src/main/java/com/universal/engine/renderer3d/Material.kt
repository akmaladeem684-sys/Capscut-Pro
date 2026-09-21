package com.universal.engine.renderer3d

import com.universal.engine.gl.TextureManager

data class Material(
    val baseColorR: Float = 1f, val baseColorG: Float = 1f, val baseColorB: Float = 1f,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val opacity: Float = 1f,
    val baseColorTexture: TextureManager.TextureResource? = null,
    val normalMap: TextureManager.TextureResource? = null,
    val doubleSided: Boolean = false
)
