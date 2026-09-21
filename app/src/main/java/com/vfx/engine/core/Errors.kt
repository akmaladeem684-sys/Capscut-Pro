package com.vfx.engine.core

sealed class VfxEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ShaderCompileError(val shaderName: String, val compileLog: String) :
  VfxEngineException("Shader compilation failed for $shaderName: $compileLog")

class GraphCycleError(val cyclePath: List<String>) :
  VfxEngineException("RenderGraph cycle detected: ${cyclePath.joinToString(" -> ")}")

class OutOfVramException(val requestedBytes: Long, val availableBytes: Long) :
  VfxEngineException("Insufficient VRAM: requested $requestedBytes bytes, available $availableBytes bytes")

class InvalidEffectException(val effectId: String, val reason: String) :
  VfxEngineException("Invalid effect $effectId: $reason")
