#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint8_t r, g, b, a;
} Color;

#define COLOR_BLACK   ((Color){0, 0, 0, 255})
#define COLOR_WHITE   ((Color){255, 255, 255, 255})
#define COLOR_GREEN   ((Color){0, 255, 0, 255})
#define COLOR_AMBER   ((Color){255, 176, 0, 255})
#define COLOR_CYAN    ((Color){0, 255, 255, 255})

typedef struct RendererPort RendererPort;

struct RendererPort {
    bool (*initialize)(RendererPort* port, int width, int height, const char* title);
    void (*shutdown)(RendererPort* port);
    void (*begin_frame)(RendererPort* port);
    void (*end_frame)(RendererPort* port);
    void (*clear)(RendererPort* port, Color color);
    void (*draw_rect)(RendererPort* port, int x, int y, int w, int h, Color color);
    void (*draw_rect_filled)(RendererPort* port, int x, int y, int w, int h, Color color);
    void (*draw_line)(RendererPort* port, int x1, int y1, int x2, int y2, Color color);
    void (*draw_text)(RendererPort* port, int x, int y, const char* text, Color color);
    void (*get_size)(const RendererPort* port, int* width, int* height);
    bool (*should_close)(const RendererPort* port);
    void* impl;
};

#ifdef __cplusplus
}
#endif
