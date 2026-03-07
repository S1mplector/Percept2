#pragma once

#include "../../ports/renderer_port.h"
#include "../../ports/input_port.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    RendererPort* renderer;
    InputPort* input;
    void* window;
    void* view;
    void* app_delegate;
} MacOSPlatform;

MacOSPlatform* macos_platform_create(void);
void macos_platform_destroy(MacOSPlatform* platform);
char* macos_open_midi_file_dialog(void);

#ifdef __cplusplus
}
#endif
