#include "widgets.h"
#include <math.h>
#include <string.h>

void draw_panel(RendererPort* r, int x, int y, int w, int h) {
    r->draw_rect_filled(r, x, y, w, h, g_theme.panel_bg);
    r->draw_rect(r, x, y, w, h, g_theme.panel_border);
}

void draw_panel_header(RendererPort* r, int x, int y, int w, int h, const char* title) {
    (void)title;
    r->draw_rect_filled(r, x, y, w, h, g_theme.panel_header);
    r->draw_line(r, x, y + h - 1, x + w, y + h - 1, g_theme.panel_border);
}

void draw_button(RendererPort* r, int x, int y, int w, int h, const char* label, bool active) {
    (void)label;
    Color bg = active ? g_theme.button_active : g_theme.button_bg;
    r->draw_rect_filled(r, x, y, w, h, bg);
    r->draw_rect(r, x, y, w, h, g_theme.panel_border);
}

void draw_vu_meter_v(RendererPort* r, int x, int y, int w, int h, float level, float peak) {
    r->draw_rect_filled(r, x, y, w, h, g_theme.vu_bg);
    
    int level_h = (int)(level * h);
    int peak_y = y + h - (int)(peak * h);
    
    for (int i = 0; i < level_h; i++) {
        int py = y + h - 1 - i;
        float ratio = (float)i / h;
        Color c;
        if (ratio < 0.6f) {
            c = g_theme.vu_low;
        } else if (ratio < 0.85f) {
            c = g_theme.vu_mid;
        } else if (ratio < 0.95f) {
            c = g_theme.vu_high;
        } else {
            c = g_theme.vu_clip;
        }
        r->draw_line(r, x + 1, py, x + w - 2, py, c);
    }
    
    if (peak > 0.01f) {
        Color peak_color = peak > 0.95f ? g_theme.vu_clip : g_theme.text_primary;
        r->draw_line(r, x + 1, peak_y, x + w - 2, peak_y, peak_color);
    }
}

void draw_vu_meter_h(RendererPort* r, int x, int y, int w, int h, float level) {
    r->draw_rect_filled(r, x, y, w, h, g_theme.vu_bg);
    
    int level_w = (int)(level * w);
    
    for (int i = 0; i < level_w; i++) {
        float ratio = (float)i / w;
        Color c;
        if (ratio < 0.6f) {
            c = g_theme.vu_low;
        } else if (ratio < 0.85f) {
            c = g_theme.vu_mid;
        } else {
            c = g_theme.vu_clip;
        }
        r->draw_line(r, x + i, y + 1, x + i, y + h - 2, c);
    }
}

void draw_knob(RendererPort* r, int cx, int cy, int radius, float value, Color color) {
    for (int a = 0; a < 360; a += 10) {
        if (a < 45 || a > 315) continue;
        float rad = a * 3.14159f / 180.0f;
        int x1 = cx + (int)((radius - 2) * sinf(rad));
        int y1 = cy - (int)((radius - 2) * cosf(rad));
        r->draw_rect_filled(r, x1, y1, 2, 2, g_theme.bg_light);
    }
    
    r->draw_rect_filled(r, cx - radius/2, cy - radius/2, radius, radius, g_theme.button_bg);
    r->draw_rect(r, cx - radius/2, cy - radius/2, radius, radius, g_theme.panel_border);
    
    float angle = 135.0f + value * 270.0f;
    float rad = angle * 3.14159f / 180.0f;
    int x2 = cx + (int)((radius/2 - 3) * sinf(rad));
    int y2 = cy - (int)((radius/2 - 3) * cosf(rad));
    r->draw_line(r, cx, cy, x2, y2, color);
}

void draw_transport_button(RendererPort* r, int x, int y, int size, int type, bool active) {
    Color bg = active ? g_theme.button_active : g_theme.button_bg;
    r->draw_rect_filled(r, x, y, size, size, bg);
    r->draw_rect(r, x, y, size, size, g_theme.panel_border);
    
    int cx = x + size / 2;
    int cy = y + size / 2;
    int s = size / 4;
    
    Color icon = active ? g_theme.text_primary : g_theme.text_secondary;
    
    switch (type) {
        case TRANSPORT_PLAY:
            for (int i = 0; i < s; i++) {
                r->draw_line(r, cx - s/2 + i, cy - s + i, cx - s/2 + i, cy + s - i, icon);
            }
            break;
        case TRANSPORT_PAUSE:
            r->draw_rect_filled(r, cx - s, cy - s, s/2 + 1, s * 2, icon);
            r->draw_rect_filled(r, cx + s/2 - 1, cy - s, s/2 + 1, s * 2, icon);
            break;
        case TRANSPORT_STOP:
            r->draw_rect_filled(r, cx - s, cy - s, s * 2, s * 2, icon);
            break;
        case TRANSPORT_RECORD:
            for (int dy = -s; dy <= s; dy++) {
                for (int dx = -s; dx <= s; dx++) {
                    if (dx*dx + dy*dy <= s*s) {
                        r->draw_rect_filled(r, cx + dx, cy + dy, 1, 1, g_theme.accent_error);
                    }
                }
            }
            break;
        case TRANSPORT_LOOP:
            r->draw_rect(r, cx - s, cy - s/2, s * 2, s, icon);
            r->draw_line(r, cx + s - 2, cy - s/2 - 2, cx + s, cy - s/2, icon);
            r->draw_line(r, cx + s - 2, cy - s/2 + 2, cx + s, cy - s/2, icon);
            break;
    }
}

void draw_waveform_icon(RendererPort* r, int x, int y, int w, int h, int waveform) {
    Color c = g_theme.text_secondary;
    int cy = y + h / 2;
    int amp = h / 3;
    
    switch (waveform) {
        case 0: // Square
            r->draw_line(r, x, cy - amp, x + w/4, cy - amp, c);
            r->draw_line(r, x + w/4, cy - amp, x + w/4, cy + amp, c);
            r->draw_line(r, x + w/4, cy + amp, x + w/2, cy + amp, c);
            r->draw_line(r, x + w/2, cy + amp, x + w/2, cy - amp, c);
            r->draw_line(r, x + w/2, cy - amp, x + 3*w/4, cy - amp, c);
            r->draw_line(r, x + 3*w/4, cy - amp, x + 3*w/4, cy + amp, c);
            r->draw_line(r, x + 3*w/4, cy + amp, x + w, cy + amp, c);
            break;
        case 2: // Triangle
            r->draw_line(r, x, cy, x + w/4, cy - amp, c);
            r->draw_line(r, x + w/4, cy - amp, x + w/2, cy, c);
            r->draw_line(r, x + w/2, cy, x + 3*w/4, cy + amp, c);
            r->draw_line(r, x + 3*w/4, cy + amp, x + w, cy, c);
            break;
        case 4: // Sawtooth
            r->draw_line(r, x, cy + amp, x + w/2, cy - amp, c);
            r->draw_line(r, x + w/2, cy - amp, x + w/2, cy + amp, c);
            r->draw_line(r, x + w/2, cy + amp, x + w, cy - amp, c);
            break;
        default: // Noise
            for (int i = 0; i < w; i += 3) {
                int y1 = cy + (i * 17 % amp) - amp/2;
                int y2 = cy + ((i + 1) * 23 % amp) - amp/2;
                r->draw_line(r, x + i, y1, x + i + 2, y2, c);
            }
            break;
    }
}

static const char* note_names[] = {"C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-"};

void draw_note_cell(RendererPort* r, int x, int y, int w, int h, int note, bool has_note, Color ch_color) {
    if (has_note && note > 0 && note < 128) {
        int note_val = note % 12;
        int octave = (note / 12) - 1;
        (void)note_names;
        (void)octave;
        (void)note_val;
        
        Color note_bg = {ch_color.r / 4, ch_color.g / 4, ch_color.b / 4, 255};
        r->draw_rect_filled(r, x + 1, y + 1, w - 2, h - 2, note_bg);
        
        int bar_w = (note_val + 1) * (w - 4) / 12;
        r->draw_rect_filled(r, x + 2, y + h - 4, bar_w, 2, ch_color);
    }
}

static void draw_digit(RendererPort* r, int x, int y, int digit, Color color) {
    int w = 4, h = 6;
    bool segments[10][7] = {
        {1,1,1,0,1,1,1}, // 0
        {0,0,1,0,0,1,0}, // 1
        {1,0,1,1,1,0,1}, // 2
        {1,0,1,1,0,1,1}, // 3
        {0,1,1,1,0,1,0}, // 4
        {1,1,0,1,0,1,1}, // 5
        {1,1,0,1,1,1,1}, // 6
        {1,0,1,0,0,1,0}, // 7
        {1,1,1,1,1,1,1}, // 8
        {1,1,1,1,0,1,1}, // 9
    };
    
    if (digit < 0 || digit > 9) return;
    
    if (segments[digit][0]) r->draw_line(r, x, y, x + w, y, color);
    if (segments[digit][1]) r->draw_line(r, x, y, x, y + h/2, color);
    if (segments[digit][2]) r->draw_line(r, x + w, y, x + w, y + h/2, color);
    if (segments[digit][3]) r->draw_line(r, x, y + h/2, x + w, y + h/2, color);
    if (segments[digit][4]) r->draw_line(r, x, y + h/2, x, y + h, color);
    if (segments[digit][5]) r->draw_line(r, x + w, y + h/2, x + w, y + h, color);
    if (segments[digit][6]) r->draw_line(r, x, y + h, x + w, y + h, color);
}

void draw_number_2digit(RendererPort* r, int x, int y, int value, Color color) {
    if (value < 0) value = 0;
    if (value > 99) value = 99;
    draw_digit(r, x, y, value / 10, color);
    draw_digit(r, x + 6, y, value % 10, color);
}

void draw_number_3digit(RendererPort* r, int x, int y, int value, Color color) {
    if (value < 0) value = 0;
    if (value > 999) value = 999;
    draw_digit(r, x, y, value / 100, color);
    draw_digit(r, x + 6, y, (value / 10) % 10, color);
    draw_digit(r, x + 12, y, value % 10, color);
}
