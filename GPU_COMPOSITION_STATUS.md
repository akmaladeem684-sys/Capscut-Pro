# AH Studio — Next-Gen GPU Composition Status

## Implemented

- Added `NextGenGpuCompositionEngine` as the active native composition backend.
- Reused the existing `RenderLayer`/JNI contract; no UI or timeline model rewrite.
- Persistent GLES 3 shader programs and VAO/VBO resources.
- Cached uniform handles; no per-layer `glGetUniformLocation` calls.
- Deterministic z-order rendering with stable layer IDs as the tie-breaker.
- Reusable triple offscreen FBO/texture targets with resolution-aware allocation.
- Offscreen state is preserved while `NativeRenderBridge.beginOffscreen()` → `renderFrame()` → `endOffscreen()` is active.
- Direct `samplerExternalOES` rendering entry point for decoder `SurfaceTexture` textures.
- GPU-side blend modes, transforms, UV mapping and opacity.
- Render instrumentation for frames, draw calls, shader switches, texture binds, FBO switches, skipped layers and frame time.
- Native bridge now routes preview/export composition calls through the next-generation backend.
- `app/build.gradle.kts` now explicitly wires `src/main/cpp/CMakeLists.txt` into the Android
  application, so `libah_engine.so` is packaged instead of silently falling back to Kotlin.
- The JNI layer uses a 52-float layer packet with SurfaceTexture transform data and external OES
  flags. Decoder textures can flow directly into the native compositor when no preprocessing is
  required.
- Added a guarded native effect chain for vignette, sharpen, blur/soft-focus, and flash/strobe.
  Unsupported effects continue through the existing Kotlin multi-pass implementation.
- Native clear-alpha state is synchronized with transparent chroma-key output.

## Existing architecture preserved

`GpuRenderEngine` remains in the repository for compatibility. The JNI bridge uses the new backend, while existing Kotlin composition/export APIs remain unchanged.

## Export path

The existing `VideoExporter` already creates the EGL recordable surface and `GpuCompositionRenderer`, so the native composition backend is now used by that composition path without introducing a second export UI or timeline system.

## Important limitations

- OES direct composition is selected only when adjustments, filters, chroma key, and color-matrix
  effects are inactive. Preprocessing remains the compatibility path otherwise.
- The native effect chain is intentionally conservative. Effects without a matching native shader
  are delegated to the existing Kotlin shader path rather than approximated.

## Verification

The local sandbox does not contain the Android/NDK toolchain, so
`./gradlew testDebugUnitTest assembleDebug --stacktrace` could not be executed here. GitHub Actions
is the required validation step for CMake/JNI compilation; physical-device validation is still
needed for vendor-specific OES shader and EGL behavior.
