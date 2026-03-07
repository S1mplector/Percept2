#pragma once

#include "../../ports/renderer_port.h"
#include "theme.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void draw_panel(RendererPort* r, int x, int y, int w, int h);
void draw_panel_header(RendererPort* r, int x, int y, int w, int h, const char* title);
void draw_button(RendererPort* r, int x, int y, int w, int h, const char* label, bool active);
void draw_vu_meter_v(RendererPort* r, int x, int y, int w, int h, float level, float peak);
void draw_vu_meter_h(RendererPort* r, int x, int y, int w, int h, float level);
void draw_knob(RendererPort* r, int cx, int cy, int radius, float value, Color color);
void draw_transport_button(RendererPort* r, int x, int y, int size, int type, bool active);
void draw_waveform_icon(RendererPort* r, int x, int y, int w, int h, int waveform);
void draw_note_cell(RendererPort* r, int x, int y, int w, int h, int note, bool has_note, Color ch_color);
void draw_number_2digit(RendererPort* r, int x, int y, int value, Color color);
void draw_number_3digit(RendererPort* r, int x, int y, int value, Color color);

#define TRANSPORT_PLAY   0
#define TRANSPORT_PAUSE  1
#define TRANSPORT_STOP   2
#define TRANSPORT_RECORD 3
#define TRANSPORT_LOOP   4

#ifdef __cplusplus
}
#endif
