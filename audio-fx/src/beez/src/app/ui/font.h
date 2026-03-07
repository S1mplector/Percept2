#pragma once

#include "../../ports/renderer_port.h"

#ifdef __cplusplus
extern "C" {
#endif

#define FONT_CHAR_WIDTH 6
#define FONT_CHAR_HEIGHT 8

void font_init(void);
void font_draw_char(RendererPort* r, int x, int y, char c, Color color);
void font_draw_string(RendererPort* r, int x, int y, const char* str, Color color);
void font_draw_string_centered(RendererPort* r, int x, int y, int width, const char* str, Color color);
int font_string_width(const char* str);

#ifdef __cplusplus
}
#endif
