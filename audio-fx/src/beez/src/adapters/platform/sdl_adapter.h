#pragma once

#include "../../ports/renderer_port.h"
#include "../../ports/input_port.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    RendererPort* renderer;
    InputPort* input;
} SDLPlatform;

SDLPlatform* sdl_platform_create(void);
void sdl_platform_destroy(SDLPlatform* platform);

#ifdef __cplusplus
}
#endif
