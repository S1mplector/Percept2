#pragma once

#include "../../ports/renderer_port.h"
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define TOAST_MAX 8

typedef struct {
    char message[128];
    float duration;
    float timer;
    bool active;
} Toast;

typedef struct {
    Toast items[TOAST_MAX];
    float default_duration;
} ToastQueue;

void toast_queue_init(ToastQueue* queue);
void toast_queue_clear(ToastQueue* queue);
void toast_push(ToastQueue* queue, const char* message);
void toast_pushf(ToastQueue* queue, const char* fmt, ...);
void toast_update(ToastQueue* queue, float dt);
void toast_render(const ToastQueue* queue, RendererPort* renderer, int width, int height);

#ifdef __cplusplus
}
#endif
