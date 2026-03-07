#pragma once

#include "../../ports/renderer_port.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    Color bg_dark;
    Color bg_medium;
    Color bg_light;
    Color bg_highlight;
    
    Color panel_bg;
    Color panel_border;
    Color panel_header;
    
    Color text_primary;
    Color text_secondary;
    Color text_dim;
    
    Color accent_primary;
    Color accent_secondary;
    Color accent_warning;
    Color accent_error;
    
    Color channel_colors[8];
    
    Color grid_line;
    Color grid_beat;
    Color grid_bar;
    
    Color cursor;
    Color selection;
    Color playhead;
    
    Color vu_low;
    Color vu_mid;
    Color vu_high;
    Color vu_clip;
    Color vu_bg;
    
    Color button_bg;
    Color button_hover;
    Color button_active;
    Color button_text;
} Theme;

extern Theme g_theme;

void theme_init_dark(void);

#ifdef __cplusplus
}
#endif
