#ifndef NEXT_GEN_GPU_COMPOSITION_ENGINE_H
#define NEXT_GEN_GPU_COMPOSITION_ENGINE_H

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <cstdint>
#include <vector>
#include "GpuRenderEngine.h"

namespace ah_engine {
struct GpuRenderStats { uint64_t frames=0,drawCalls=0,shaderSwitches=0,textureBinds=0,fboSwitches=0,skippedLayers=0; float lastFrameMs=0.0f; };
class NextGenGpuCompositionEngine {
public:
    NextGenGpuCompositionEngine(); ~NextGenGpuCompositionEngine();
    bool init(); void resize(int width,int height); void renderFrame(const std::vector<RenderLayer>& layers); void renderExternalTexture(GLuint textureId,const float* texMatrix);
    void beginOffscreen(); GLuint endOffscreen(); void onContextLost(); void release();
    bool isInitialized() const { return initialized_; } const GpuRenderStats& stats() const { return stats_; }
private:
    bool buildPrograms(); GLuint compile(GLenum type,const char* source); void destroyPrograms(); void ensureTargets(int width,int height); void destroyTargets();
    void drawTexture(GLuint texture,const float* mvp,const float* st,float opacity,BlendMode mode,bool external); void identity(float* m) const; void multiply(float* out,const float* a,const float* b) const; void layerMatrix(const RenderLayer& layer,float* out) const; void blend(BlendMode mode); void bindTarget(GLuint fbo,int width,int height);
    bool initialized_=false, offscreenActive_=false; int width_=0,height_=0; GLuint vao_=0,vbo_=0,program2d_=0,programOes_=0;
    GLint mvp2d_=-1,st2d_=-1,opacity2d_=-1,sampler2d_=-1,mvpOes_=-1,stOes_=-1,opacityOes_=-1,samplerOes_=-1;
    GLuint fbo_[3]={0,0,0},target_[3]={0,0,0}; int targetWidth_=0,targetHeight_=0,activeTarget_=0; GpuRenderStats stats_; float projection_[16]{}; GLuint lastProgram_=0,lastTexture_=0;
};
}
#endif
