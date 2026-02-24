/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * COLOR UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* ARGB color packing */
static inline uint32_t argb(uint8_t a, uint8_t r, uint8_t g, uint8_t b) {
    return ((uint32_t)a << 24) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
}

/* Extract components */
static inline uint8_t get_a(uint32_t c) { return (c >> 24) & 0xFF; }
static inline uint8_t get_r(uint32_t c) { return (c >> 16) & 0xFF; }
static inline uint8_t get_g(uint32_t c) { return (c >> 8) & 0xFF; }
static inline uint8_t get_b(uint32_t c) { return c & 0xFF; }

/* Interpolate between two colors */
static uint32_t lerp_color(uint32_t c1, uint32_t c2, float t) {
    if (t <= 0.0f) return c1;
    if (t >= 1.0f) return c2;
    
    uint8_t a = (uint8_t)(get_a(c1) + (get_a(c2) - get_a(c1)) * t);
    uint8_t r = (uint8_t)(get_r(c1) + (get_r(c2) - get_r(c1)) * t);
    uint8_t g = (uint8_t)(get_g(c1) + (get_g(c2) - get_g(c1)) * t);
    uint8_t b = (uint8_t)(get_b(c1) + (get_b(c2) - get_b(c1)) * t);
    
    return argb(a, r, g, b);
}

/* Mood value to color (0-100 scale) */
static uint32_t mood_to_color(int32_t mood) {
    /* Color gradient: red (0) -> orange (25) -> yellow (50) -> lime (75) -> green (100) */
    if (mood <= 0) return argb(255, 220, 53, 69);     /* Red */
    if (mood >= 100) return argb(255, 40, 167, 69);   /* Green */
    
    float t = mood / 100.0f;
    
    if (t < 0.25f) {
        /* Red to orange */
        return lerp_color(argb(255, 220, 53, 69), argb(255, 253, 126, 20), t * 4);
    } else if (t < 0.50f) {
        /* Orange to yellow */
        return lerp_color(argb(255, 253, 126, 20), argb(255, 255, 193, 7), (t - 0.25f) * 4);
    } else if (t < 0.75f) {
        /* Yellow to lime */
        return lerp_color(argb(255, 255, 193, 7), argb(255, 130, 202, 50), (t - 0.5f) * 4);
    } else {
        /* Lime to green */
        return lerp_color(argb(255, 130, 202, 50), argb(255, 40, 167, 69), (t - 0.75f) * 4);
    }
}

/* Alpha blend src over dst */
static uint32_t blend_over(uint32_t dst, uint32_t src) {
    uint8_t sa = get_a(src);
    if (sa == 255) return src;
    if (sa == 0) return dst;
    
    uint8_t da = get_a(dst);
    uint8_t sr = get_r(src), sg = get_g(src), sb = get_b(src);
    uint8_t dr = get_r(dst), dg = get_g(dst), db = get_b(dst);
    
    uint32_t a = sa + (da * (255 - sa)) / 255;
    if (a == 0) return 0;
    
    uint32_t r = (sr * sa + dr * da * (255 - sa) / 255) / a;
    uint32_t g = (sg * sa + dg * da * (255 - sa) / 255) / a;
    uint32_t b = (sb * sa + db * da * (255 - sa) / 255) / a;
    
    return argb((uint8_t)a, (uint8_t)r, (uint8_t)g, (uint8_t)b);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DRAWING PRIMITIVES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Set pixel with bounds checking */
static inline void set_pixel(uint32_t* buf, int32_t w, int32_t h, int32_t x, int32_t y, uint32_t c) {
    if (x >= 0 && x < w && y >= 0 && y < h) {
        buf[y * w + x] = blend_over(buf[y * w + x], c);
    }
}

/* Fill rectangle */
static void fill_rect(uint32_t* buf, int32_t w, int32_t h,
                      int32_t x, int32_t y, int32_t rw, int32_t rh, uint32_t c) {
    int32_t x1 = x < 0 ? 0 : x;
    int32_t y1 = y < 0 ? 0 : y;
    int32_t x2 = (x + rw > w) ? w : x + rw;
    int32_t y2 = (y + rh > h) ? h : y + rh;
    
    for (int32_t py = y1; py < y2; py++) {
        for (int32_t px = x1; px < x2; px++) {
            buf[py * w + px] = blend_over(buf[py * w + px], c);
        }
    }
}

/* Draw horizontal line */
static void draw_hline(uint32_t* buf, int32_t w, int32_t h,
                       int32_t x1, int32_t x2, int32_t y, uint32_t c) {
    if (y < 0 || y >= h) return;
    if (x1 > x2) { int32_t t = x1; x1 = x2; x2 = t; }
    if (x1 < 0) x1 = 0;
    if (x2 >= w) x2 = w - 1;
    
    for (int32_t x = x1; x <= x2; x++) {
        buf[y * w + x] = blend_over(buf[y * w + x], c);
    }
}

/* Draw vertical line */
static void draw_vline(uint32_t* buf, int32_t w, int32_t h,
                       int32_t x, int32_t y1, int32_t y2, uint32_t c) {
    if (x < 0 || x >= w) return;
    if (y1 > y2) { int32_t t = y1; y1 = y2; y2 = t; }
    if (y1 < 0) y1 = 0;
    if (y2 >= h) y2 = h - 1;
    
    for (int32_t y = y1; y <= y2; y++) {
        buf[y * w + x] = blend_over(buf[y * w + x], c);
    }
}

/* Draw anti-aliased line using Xiaolin Wu's algorithm */
static void draw_line_aa(uint32_t* buf, int32_t w, int32_t h,
                         float x0, float y0, float x1, float y1, uint32_t c) {
    int steep = fabsf(y1 - y0) > fabsf(x1 - x0);
    
    if (steep) {
        float t; t = x0; x0 = y0; y0 = t;
        t = x1; x1 = y1; y1 = t;
    }
    if (x0 > x1) {
        float t; t = x0; x0 = x1; x1 = t;
        t = y0; y0 = y1; y1 = t;
    }
    
    float dx = x1 - x0;
    float dy = y1 - y0;
    float gradient = dx == 0.0f ? 1.0f : dy / dx;
    
    /* First endpoint */
    float xend = roundf(x0);
    float yend = y0 + gradient * (xend - x0);
    float xgap = 1.0f - (x0 + 0.5f - floorf(x0 + 0.5f));
    int xpxl1 = (int)xend;
    int ypxl1 = (int)floorf(yend);
    
    uint8_t alpha = get_a(c);
    uint8_t r = get_r(c), g = get_g(c), b = get_b(c);
    
    if (steep) {
        set_pixel(buf, w, h, ypxl1, xpxl1, argb((uint8_t)(alpha * (1.0f - (yend - ypxl1)) * xgap), r, g, b));
        set_pixel(buf, w, h, ypxl1 + 1, xpxl1, argb((uint8_t)(alpha * (yend - ypxl1) * xgap), r, g, b));
    } else {
        set_pixel(buf, w, h, xpxl1, ypxl1, argb((uint8_t)(alpha * (1.0f - (yend - ypxl1)) * xgap), r, g, b));
        set_pixel(buf, w, h, xpxl1, ypxl1 + 1, argb((uint8_t)(alpha * (yend - ypxl1) * xgap), r, g, b));
    }
    
    float intery = yend + gradient;
    
    /* Second endpoint */
    xend = roundf(x1);
    yend = y1 + gradient * (xend - x1);
    xgap = x1 + 0.5f - floorf(x1 + 0.5f);
    int xpxl2 = (int)xend;
    int ypxl2 = (int)floorf(yend);
    
    if (steep) {
        set_pixel(buf, w, h, ypxl2, xpxl2, argb((uint8_t)(alpha * (1.0f - (yend - ypxl2)) * xgap), r, g, b));
        set_pixel(buf, w, h, ypxl2 + 1, xpxl2, argb((uint8_t)(alpha * (yend - ypxl2) * xgap), r, g, b));
    } else {
        set_pixel(buf, w, h, xpxl2, ypxl2, argb((uint8_t)(alpha * (1.0f - (yend - ypxl2)) * xgap), r, g, b));
        set_pixel(buf, w, h, xpxl2, ypxl2 + 1, argb((uint8_t)(alpha * (yend - ypxl2) * xgap), r, g, b));
    }
    
    /* Main loop */
    for (int x = xpxl1 + 1; x < xpxl2; x++) {
        int ipart = (int)floorf(intery);
        float fpart = intery - ipart;
        
        if (steep) {
            set_pixel(buf, w, h, ipart, x, argb((uint8_t)(alpha * (1.0f - fpart)), r, g, b));
            set_pixel(buf, w, h, ipart + 1, x, argb((uint8_t)(alpha * fpart), r, g, b));
        } else {
            set_pixel(buf, w, h, x, ipart, argb((uint8_t)(alpha * (1.0f - fpart)), r, g, b));
            set_pixel(buf, w, h, x, ipart + 1, argb((uint8_t)(alpha * fpart), r, g, b));
        }
        intery += gradient;
    }
}

/* Fill circle (anti-aliased edge) */
static void fill_circle(uint32_t* buf, int32_t w, int32_t h,
                        int32_t cx, int32_t cy, int32_t r, uint32_t c) {
    if (r <= 0) return;
    
    int32_t x1 = cx - r - 1, y1 = cy - r - 1;
    int32_t x2 = cx + r + 1, y2 = cy + r + 1;
    if (x1 < 0) x1 = 0;
    if (y1 < 0) y1 = 0;
    if (x2 >= w) x2 = w - 1;
    if (y2 >= h) y2 = h - 1;
    
    float r2 = (float)r * r;
    uint8_t alpha = get_a(c);
    uint8_t cr = get_r(c), cg = get_g(c), cb = get_b(c);
    
    for (int32_t py = y1; py <= y2; py++) {
        for (int32_t px = x1; px <= x2; px++) {
            float dx = px - cx;
            float dy = py - cy;
            float dist2 = dx * dx + dy * dy;
            
            if (dist2 <= r2 - r) {
                /* Fully inside */
                buf[py * w + px] = blend_over(buf[py * w + px], c);
            } else if (dist2 <= r2 + r) {
                /* Edge - anti-alias */
                float dist = sqrtf(dist2);
                float coverage = 1.0f - (dist - (r - 0.5f));
                if (coverage > 1.0f) coverage = 1.0f;
                if (coverage > 0.0f) {
                    uint32_t ac = argb((uint8_t)(alpha * coverage), cr, cg, cb);
                    buf[py * w + px] = blend_over(buf[py * w + px], ac);
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SPARKLINE RENDERING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Render a mood sparkline to a pixel buffer.
 * 
 * @param values Array of mood values (0-100)
 * @param count Number of values
 * @param width Output width in pixels
 * @param height Output height in pixels
 * @param out Output ARGB pixel buffer (width * height * 4 bytes)
 * @param bg_color Background color (ARGB)
 * @param line_thickness Line thickness (1-5)
 * @return 1 on success, 0 on failure
 */
int32_t simjot_mood_sparkline(const int32_t* values, int32_t count,
                               int32_t width, int32_t height,
                               uint32_t* out, uint32_t bg_color,
                               int32_t line_thickness) {
    if (!values || count <= 0 || !out || width <= 0 || height <= 0) return 0;
    if (line_thickness < 1) line_thickness = 1;
    if (line_thickness > 5) line_thickness = 5;
    
    /* Fill background */
    for (int32_t i = 0; i < width * height; i++) {
        out[i] = bg_color;
    }
    
    if (count == 1) {
        /* Single point - draw centered dot */
        int32_t y = height - 1 - (values[0] * (height - 4) / 100) - 2;
        fill_circle(out, width, height, width / 2, y, line_thickness + 1, mood_to_color(values[0]));
        return 1;
    }
    
    /* Calculate step size */
    float step = (float)(width - 4) / (count - 1);
    int32_t margin_y = 2;
    int32_t usable_h = height - margin_y * 2;
    
    /* Draw connecting lines */
    for (int32_t i = 0; i < count - 1; i++) {
        float x1 = 2 + i * step;
        float x2 = 2 + (i + 1) * step;
        
        int32_t v1 = values[i] < 0 ? 0 : (values[i] > 100 ? 100 : values[i]);
        int32_t v2 = values[i+1] < 0 ? 0 : (values[i+1] > 100 ? 100 : values[i+1]);
        
        float y1 = height - margin_y - 1 - (v1 * usable_h / 100.0f);
        float y2 = height - margin_y - 1 - (v2 * usable_h / 100.0f);
        
        /* Gradient color based on average */
        uint32_t c = mood_to_color((v1 + v2) / 2);
        
        /* Draw thick line by drawing multiple parallel lines */
        for (int32_t t = -line_thickness / 2; t <= line_thickness / 2; t++) {
            draw_line_aa(out, width, height, x1, y1 + t, x2, y2 + t, c);
        }
    }
    
    /* Draw dots at data points */
    for (int32_t i = 0; i < count; i++) {
        float x = 2 + i * step;
        int32_t v = values[i] < 0 ? 0 : (values[i] > 100 ? 100 : values[i]);
        float y = height - margin_y - 1 - (v * usable_h / 100.0f);
        
        uint32_t c = mood_to_color(v);
        fill_circle(out, width, height, (int32_t)x, (int32_t)y, line_thickness, c);
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BAR CHART RENDERING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Render a mood bar chart (daily averages).
 * 
 * @param values Array of mood values (0-100, or -1 for missing)
 * @param count Number of values (days)
 * @param width Output width in pixels
 * @param height Output height in pixels
 * @param out Output ARGB pixel buffer
 * @param bg_color Background color
 * @param bar_spacing Spacing between bars in pixels
 * @return 1 on success, 0 on failure
 */
int32_t simjot_mood_barchart(const int32_t* values, int32_t count,
                              int32_t width, int32_t height,
                              uint32_t* out, uint32_t bg_color,
                              int32_t bar_spacing) {
    if (!values || count <= 0 || !out || width <= 0 || height <= 0) return 0;
    if (bar_spacing < 1) bar_spacing = 1;
    
    /* Fill background */
    for (int32_t i = 0; i < width * height; i++) {
        out[i] = bg_color;
    }
    
    int32_t margin = 4;
    int32_t usable_w = width - margin * 2;
    int32_t usable_h = height - margin * 2;
    
    /* Calculate bar width */
    int32_t total_spacing = bar_spacing * (count - 1);
    int32_t bar_width = (usable_w - total_spacing) / count;
    if (bar_width < 2) bar_width = 2;
    
    /* Draw bars */
    for (int32_t i = 0; i < count; i++) {
        int32_t v = values[i];
        if (v < 0) continue; /* Skip missing data */
        if (v > 100) v = 100;
        
        int32_t x = margin + i * (bar_width + bar_spacing);
        int32_t bar_h = v * usable_h / 100;
        if (bar_h < 2) bar_h = 2;
        int32_t y = height - margin - bar_h;
        
        uint32_t c = mood_to_color(v);
        
        /* Draw rounded bar (fill then round corners) */
        fill_rect(out, width, height, x, y, bar_width, bar_h, c);
        
        /* Round top corners */
        int32_t radius = bar_width / 4;
        if (radius > 3) radius = 3;
        fill_circle(out, width, height, x + radius, y + radius, radius, c);
        fill_circle(out, width, height, x + bar_width - radius - 1, y + radius, radius, c);
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RADIAL/GAUGE CHART
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Render a mood gauge/radial chart.
 * 
 * @param value Mood value (0-100)
 * @param size Output size (width = height = size)
 * @param out Output ARGB pixel buffer (size * size)
 * @param bg_color Background color
 * @param track_color Track (unfilled) color
 * @param thickness Arc thickness in pixels
 * @return 1 on success, 0 on failure
 */
int32_t simjot_mood_gauge(int32_t value, int32_t size,
                           uint32_t* out, uint32_t bg_color,
                           uint32_t track_color, int32_t thickness) {
    if (!out || size <= 0) return 0;
    if (value < 0) value = 0;
    if (value > 100) value = 100;
    if (thickness < 2) thickness = 2;
    
    int32_t cx = size / 2;
    int32_t cy = size / 2;
    int32_t outer_r = size / 2 - 4;
    int32_t inner_r = outer_r - thickness;
    
    /* Fill background */
    for (int32_t i = 0; i < size * size; i++) {
        out[i] = bg_color;
    }
    
    /* Draw arc (from -135° to 135°, total 270°) */
    float start_angle = -135.0f * 3.14159f / 180.0f;
    float total_arc = 270.0f * 3.14159f / 180.0f;
    float fill_arc = total_arc * value / 100.0f;
    
    uint32_t fill_color = mood_to_color(value);
    
    for (int32_t py = 0; py < size; py++) {
        for (int32_t px = 0; px < size; px++) {
            float dx = px - cx;
            float dy = py - cy;
            float dist = sqrtf(dx * dx + dy * dy);
            
            if (dist >= inner_r - 0.5f && dist <= outer_r + 0.5f) {
                float angle = atan2f(dy, dx);
                /* Normalize angle to [0, 2π) starting from start_angle */
                float norm_angle = angle - start_angle;
                while (norm_angle < 0) norm_angle += 2 * 3.14159f;
                while (norm_angle >= 2 * 3.14159f) norm_angle -= 2 * 3.14159f;
                
                if (norm_angle <= total_arc) {
                    /* Within arc range */
                    float coverage = 1.0f;
                    
                    /* Anti-alias edges */
                    if (dist < inner_r) {
                        coverage = 1.0f - (inner_r - dist);
                    } else if (dist > outer_r) {
                        coverage = 1.0f - (dist - outer_r);
                    }
                    if (coverage > 1.0f) coverage = 1.0f;
                    if (coverage < 0.0f) coverage = 0.0f;
                    
                    uint32_t c;
                    if (norm_angle <= fill_arc) {
                        c = fill_color;
                    } else {
                        c = track_color;
                    }
                    
                    if (coverage < 1.0f) {
                        uint8_t a = (uint8_t)(get_a(c) * coverage);
                        c = argb(a, get_r(c), get_g(c), get_b(c));
                    }
                    
                    out[py * size + px] = blend_over(out[py * size + px], c);
                }
            }
        }
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HEATMAP RENDERING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Render a mood heatmap grid (like GitHub contribution graph).
 * 
 * @param values Array of mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param cols Number of columns
 * @param cell_size Size of each cell in pixels
 * @param cell_gap Gap between cells in pixels
 * @param out Output ARGB pixel buffer
 * @param out_width Output width
 * @param out_height Output height
 * @param bg_color Background color
 * @param empty_color Color for missing data
 * @return 1 on success, 0 on failure
 */
int32_t simjot_mood_heatmap(const int32_t* values, int32_t count,
                             int32_t cols, int32_t cell_size, int32_t cell_gap,
                             uint32_t* out, int32_t out_width, int32_t out_height,
                             uint32_t bg_color, uint32_t empty_color) {
    if (!values || count <= 0 || !out || out_width <= 0 || out_height <= 0) return 0;
    if (cols <= 0) cols = 7; /* Default to week view */
    if (cell_size < 4) cell_size = 4;
    if (cell_gap < 1) cell_gap = 1;
    
    /* Fill background */
    for (int32_t i = 0; i < out_width * out_height; i++) {
        out[i] = bg_color;
    }
    
    int32_t rows = (count + cols - 1) / cols;
    int32_t radius = cell_size / 4;
    if (radius < 2) radius = 2;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t col = i % cols;
        int32_t row = i / cols;
        
        int32_t x = col * (cell_size + cell_gap);
        int32_t y = row * (cell_size + cell_gap);
        
        int32_t v = values[i];
        uint32_t c = (v < 0) ? empty_color : mood_to_color(v);
        
        /* Draw rounded rectangle cell */
        fill_rect(out, out_width, out_height, x, y, cell_size, cell_size, c);
        
        /* Round all corners */
        fill_circle(out, out_width, out_height, x + radius, y + radius, radius, c);
        fill_circle(out, out_width, out_height, x + cell_size - radius - 1, y + radius, radius, c);
        fill_circle(out, out_width, out_height, x + radius, y + cell_size - radius - 1, radius, c);
        fill_circle(out, out_width, out_height, x + cell_size - radius - 1, y + cell_size - radius - 1, radius, c);
    }
    
    return 1;
}
