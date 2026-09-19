package com.example.engine.composition.gpu

import android.util.Log

enum class NativeBlendMode(val id: Int) { NORMAL(0), ADDITIVE(1), MULTIPLY(2), SCREEN(3), PREMULTIPLIED(4) }
enum class NativeLayerType(val id: Int) { BASE_VIDEO(0), VIDEO(1), IMAGE_STICKER(2), EFFECT_OVERLAY(3), TEXT(4) }

data class NativeLayer(
  val id: Long = 0L, val textureId: Int = 0, val type: NativeLayerType = NativeLayerType.BASE_VIDEO,
  val isVisible: Boolean = true, val zOrder: Int = 0, val posX: Float = 0f, val posY: Float = 0f,
  val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f,
  val width: Float = 1f, val height: Float = 1f, val opacity: Float = 1f,
  val uOffset: Float = 0f, val vOffset: Float = 0f, val uScale: Float = 1f, val vScale: Float = 1f,
  val blendMode: NativeBlendMode = NativeBlendMode.NORMAL, val useCustomMatrix: Boolean = false,
  val transformMatrix: FloatArray? = null
)

object NativeRenderBridge {
  private const val TAG = "NativeRenderBridge"
  private const val LAYER_STRIDE = 35
  private var isLibraryLoaded = false
  private var isInitialized = false
  val isLoaded: Boolean get() = isLibraryLoaded && isInitialized
  private var bufferCapacityLayers = 64
  private var renderBuffer = FloatArray(bufferCapacityLayers * LAYER_STRIDE)

  init { loadLibrary() }

  @Synchronized fun loadLibrary(): Boolean {
    if (isLibraryLoaded) return true
    return try { System.loadLibrary("ah_engine"); isLibraryLoaded = true; true }
    catch (e: Throwable) { Log.d(TAG, "Native ah_engine not loaded, fallback enabled", e); false }
  }

  @Synchronized fun init(width: Int, height: Int): Boolean {
    if (!isLibraryLoaded && !loadLibrary()) return false
    return try { isInitialized = nativeInit(width, height); isInitialized }
    catch (e: Throwable) { Log.e(TAG, "nativeInit failed", e); false }
  }

  @Synchronized fun resize(width: Int, height: Int) { if(isInitialized) runCatching { nativeResize(width,height) }.onFailure { Log.e(TAG,"nativeResize failed",it) } }

  /** Direct decoder SurfaceTexture/OES path. No Bitmap or CPU pixel readback is performed. */
  @Synchronized fun renderExternalTexture(textureId: Int, texMatrix: FloatArray? = null) {
    if (!isInitialized || textureId <= 0) return
    try { nativeRenderExternalTexture(textureId, texMatrix) } catch (e: Throwable) { Log.e(TAG,"nativeRenderExternalTexture failed",e) }
  }

  @Synchronized fun renderFrame(layers: List<NativeLayer>) {
    if (!isInitialized || layers.isEmpty()) return
    ensureBufferCapacity(layers.size)
    var o=0
    for (layer in layers) {
      renderBuffer[o]=layer.id.toFloat(); renderBuffer[o+1]=layer.textureId.toFloat(); renderBuffer[o+2]=layer.type.id.toFloat(); renderBuffer[o+3]=if(layer.isVisible)1f else 0f; renderBuffer[o+4]=layer.zOrder.toFloat()
      renderBuffer[o+5]=layer.posX; renderBuffer[o+6]=layer.posY; renderBuffer[o+7]=layer.scaleX; renderBuffer[o+8]=layer.scaleY; renderBuffer[o+9]=layer.rotation; renderBuffer[o+10]=layer.width; renderBuffer[o+11]=layer.height; renderBuffer[o+12]=layer.opacity
      renderBuffer[o+13]=layer.uOffset; renderBuffer[o+14]=layer.vOffset; renderBuffer[o+15]=layer.uScale; renderBuffer[o+16]=layer.vScale; renderBuffer[o+17]=layer.blendMode.id.toFloat(); renderBuffer[o+18]=if(layer.useCustomMatrix&&layer.transformMatrix!=null)1f else 0f
      if(layer.useCustomMatrix&&layer.transformMatrix!=null&&layer.transformMatrix.size>=16)System.arraycopy(layer.transformMatrix,0,renderBuffer,o+19,16)
      o+=LAYER_STRIDE
    }
    try { nativeRenderFrame(renderBuffer,layers.size) } catch(e:Throwable){ Log.e(TAG,"nativeRenderFrame failed",e) }
  }

  @Synchronized fun beginOffscreen(){ if(isInitialized)runCatching{nativeBeginOffscreen()} }
  @Synchronized fun endOffscreen():Int=if(isInitialized)runCatching{nativeEndOffscreen()}.getOrDefault(0) else 0
  @Synchronized fun onContextLost(){runCatching{nativeOnContextLost()};isInitialized=false}
  @Synchronized fun release(){if(isInitialized){runCatching{nativeRelease()};isInitialized=false}}
  private fun ensureBufferCapacity(required:Int){if(required>bufferCapacityLayers){bufferCapacityLayers=required+32;renderBuffer=FloatArray(bufferCapacityLayers*LAYER_STRIDE)}}

  private external fun nativeInit(width:Int,height:Int):Boolean
  private external fun nativeResize(width:Int,height:Int)
  private external fun nativeRenderFrame(layerData:FloatArray,layerCount:Int)
  private external fun nativeRenderExternalTexture(textureId:Int,texMatrix:FloatArray?)
  private external fun nativeBeginOffscreen()
  private external fun nativeEndOffscreen():Int
  private external fun nativeOnContextLost()
  private external fun nativeRelease()
}
