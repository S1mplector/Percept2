#include "theme.h"

Theme g_theme;

void theme_init_dark(void) {
    g_theme.bg_dark = (Color){18, 18, 22, 255};
    g_theme.bg_medium = (Color){28, 28, 34, 255};
    g_theme.bg_light = (Color){38, 38, 46, 255};
    g_theme.bg_highlight = (Color){48, 48, 58, 255};
    
    g_theme.panel_bg = (Color){24, 24, 30, 255};
    g_theme.panel_border = (Color){50, 50, 60, 255};
    g_theme.panel_header = (Color){32, 32, 40, 255};
    
    g_theme.text_primary = (Color){230, 230, 235, 255};
    g_theme.text_secondary = (Color){160, 160, 170, 255};
    g_theme.text_dim = (Color){90, 90, 100, 255};
    
    g_theme.accent_primary = (Color){200, 140, 60, 255};
    g_theme.accent_secondary = (Color){180, 120, 255, 255};
    g_theme.accent_warning = (Color){255, 200, 80, 255};
    g_theme.accent_error = (Color){255, 90, 90, 255};
    
    g_theme.channel_colors[0] = (Color){255, 100, 100, 255};
    g_theme.channel_colors[1] = (Color){255, 180, 80, 255};
    g_theme.channel_colors[2] = (Color){255, 255, 100, 255};
    g_theme.channel_colors[3] = (Color){100, 255, 100, 255};
    g_theme.channel_colors[4] = (Color){100, 255, 255, 255};
    g_theme.channel_colors[5] = (Color){100, 150, 255, 255};
    g_theme.channel_colors[6] = (Color){200, 100, 255, 255};
    g_theme.channel_colors[7] = (Color){255, 100, 200, 255};
    
    g_theme.grid_line = (Color){40, 40, 48, 255};
    g_theme.grid_beat = (Color){55, 55, 65, 255};
    g_theme.grid_bar = (Color){70, 70, 82, 255};
    
    g_theme.cursor = (Color){200, 140, 60, 200};
    g_theme.selection = (Color){200, 140, 60, 90};
    g_theme.playhead = (Color){100, 255, 150, 255};
    
    g_theme.vu_low = (Color){80, 200, 120, 255};
    g_theme.vu_mid = (Color){200, 220, 80, 255};
    g_theme.vu_high = (Color){255, 160, 60, 255};
    g_theme.vu_clip = (Color){255, 60, 60, 255};
    g_theme.vu_bg = (Color){20, 20, 26, 255};
    
    g_theme.button_bg = (Color){45, 45, 55, 255};
    g_theme.button_hover = (Color){55, 55, 68, 255};
    g_theme.button_active = (Color){70, 130, 180, 255};
    g_theme.button_text = (Color){220, 220, 225, 255};
}
