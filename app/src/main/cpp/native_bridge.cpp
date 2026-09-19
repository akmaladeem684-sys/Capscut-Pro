#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <vector>
#include "render/NextGenGpuCompositionEngine.h"

#define NATIVE_RENDER_LOG_TAG "NativeRenderBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, NATIVE_RENDER_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, NATIVE_RENDER_LOG_TAG, __VA_ARGS__)

static constexpr int LAYER_STRIDE = 35;
static std::mutex gEngineMutex;
static ah_engine::NextGenGpuCompositionEngine* gRenderEngine = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeInit(JNIEnv*, jobject, jint width, jint height) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gRenderEngine) gRenderEngine = new ah_engine::NextGenGpuCompositionEngine();
    if (!gRenderEngine->isInitialized() && !gRenderEngine->init()) return JNI_FALSE;
    gRenderEngine->resize(width, height);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeResize(JNIEnv*, jobject, jint width, jint height) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gRenderEngine && gRenderEngine->isInitialized()) gRenderEngine->resize(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRenderFrame(JNIEnv* env, jobject, jfloatArray data, jint count) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gRenderEngine || !gRenderEngine->isInitialized() || !data || count <= 0) return;
    if (env->GetArrayLength(data) < count * LAYER_STRIDE) return;
    jfloat* p = env->GetFloatArrayElements(data, nullptr); if (!p) return;
    std::vector<ah_engine::RenderLayer> layers; layers.reserve(count);
    for (int i=0;i<count;i++) {
        const int b=i*LAYER_STRIDE; ah_engine::RenderLayer l;
        l.id=static_cast<uint32_t>(p[b]); l.textureId=static_cast<GLuint>(p[b+1]); if(!l.textureId) continue;
        l.type=static_cast<ah_engine::LayerType>(static_cast<int>(p[b+2])); l.isVisible=p[b+3]>0.5f; l.zOrder=static_cast<int>(p[b+4]);
        l.posX=p[b+5]; l.posY=p[b+6]; l.scaleX=p[b+7]; l.scaleY=p[b+8]; l.rotation=p[b+9]; l.width=p[b+10]; l.height=p[b+11]; l.opacity=p[b+12];
        l.uOffset=p[b+13]; l.vOffset=p[b+14]; l.uScale=p[b+15]; l.vScale=p[b+16]; l.blendMode=static_cast<ah_engine::BlendMode>(static_cast<int>(p[b+17]));
        l.useCustomMatrix=p[b+18]>0.5f; if(l.useCustomMatrix) for(int m=0;m<16;m++) l.transformMatrix[m]=p[b+19+m];
        layers.push_back(l);
    }
    env->ReleaseFloatArrayElements(data,p,JNI_ABORT); gRenderEngine->renderFrame(layers);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRenderExternalTexture(JNIEnv* env, jobject, jint textureId, jfloatArray matrixArray) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if(!gRenderEngine || !gRenderEngine->isInitialized() || textureId<=0) return;
    float matrix[16]; const float* ptr=nullptr;
    if(matrixArray && env->GetArrayLength(matrixArray)>=16){ jfloat* p=env->GetFloatArrayElements(matrixArray,nullptr); if(p){ for(int i=0;i<16;i++)matrix[i]=p[i]; env->ReleaseFloatArrayElements(matrixArray,p,JNI_ABORT); ptr=matrix; } }
    gRenderEngine->renderExternalTexture(static_cast<GLuint>(textureId), ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeBeginOffscreen(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(gEngineMutex); if(gRenderEngine&&gRenderEngine->isInitialized())gRenderEngine->beginOffscreen(); }
extern "C" JNIEXPORT jint JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeEndOffscreen(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(gEngineMutex); return (gRenderEngine&&gRenderEngine->isInitialized())?static_cast<jint>(gRenderEngine->endOffscreen()):0; }
extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeOnContextLost(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(gEngineMutex); if(gRenderEngine)gRenderEngine->onContextLost(); }
extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRelease(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(gEngineMutex); if(gRenderEngine){gRenderEngine->release();delete gRenderEngine;gRenderEngine=nullptr;} }
