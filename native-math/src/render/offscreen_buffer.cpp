/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

/**
 * @file offscreen_buffer.cpp
 * @brief Native double-buffering for smooth scrolling
 *
 * High-performance offscreen buffer management:
 * - SIMD-optimized memory operations where available
 * - Efficient scroll with memmove (avoids per-pixel copy)
 * - Alpha-blended compositing for overlay content
 * - Thread-safe handle management
 *
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <cmath>
#include <mutex>
#include <unordered_map>
#include <atomic>

/* ═══════════════════════════════════════════════════════════════════════════
 * BUFFER STRUCTURE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct OffscreenBuffer {
    uint32_t* pixels;
    int32_t width;
    int32_t height;
    size_t capacity; // in pixels, for resize optimization
};

/* ═══════════════════════════════════════════════════════════════════════════
 * GLOBAL STATE
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::mutex g_buffer_mutex;
static std::unordered_map<int64_t, OffscreenBuffer*> g_buffers;
static std::atomic<int64_t> g_next_handle{1};

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static OffscreenBuffer* get_buffer(int64_t handle) {
    auto it = g_buffers.find(handle);
    return (it != g_buffers.end()) ? it->second : nullptr;
}

// Fast alpha blend: dst = src over dst
static inline uint32_t alpha_blend(uint32_t src, uint32_t dst) {
    uint32_t sa = (src >> 24) & 0xFF;
    if (sa == 0) return dst;
    if (sa == 255) return src;
    
    uint32_t da = (dst >> 24) & 0xFF;
    uint32_t sr = (src >> 16) & 0xFF;
    uint32_t sg = (src >> 8) & 0xFF;
    uint32_t sb = src & 0xFF;
    uint32_t dr = (dst >> 16) & 0xFF;
    uint32_t dg = (dst >> 8) & 0xFF;
    uint32_t db = dst & 0xFF;
    
    uint32_t inv_sa = 255 - sa;
    uint32_t out_a = sa + ((da * inv_sa) >> 8);
    uint32_t out_r = ((sr * sa) + (dr * inv_sa)) >> 8;
    uint32_t out_g = ((sg * sa) + (dg * inv_sa)) >> 8;
    uint32_t out_b = ((sb * sa) + (db * inv_sa)) >> 8;
    
    return (out_a << 24) | (out_r << 16) | (out_g << 8) | out_b;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

int64_t simjot_buffer_create(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
        return 0;
    }
    
    size_t pixel_count = (size_t)width * (size_t)height;
    uint32_t* pixels = (uint32_t*)calloc(pixel_count, sizeof(uint32_t));
    if (!pixels) return 0;
    
    OffscreenBuffer* buf = new (std::nothrow) OffscreenBuffer();
    if (!buf) {
        free(pixels);
        return 0;
    }
    
    buf->pixels = pixels;
    buf->width = width;
    buf->height = height;
    buf->capacity = pixel_count;
    
    int64_t handle = g_next_handle.fetch_add(1);
    
    {
        std::lock_guard<std::mutex> lock(g_buffer_mutex);
        g_buffers[handle] = buf;
    }
    
    return handle;
}

int32_t simjot_buffer_resize(int64_t handle, int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
        return 0;
    }
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    size_t new_count = (size_t)width * (size_t)height;
    
    if (new_count > buf->capacity) {
        // Need to reallocate
        uint32_t* new_pixels = (uint32_t*)calloc(new_count, sizeof(uint32_t));
        if (!new_pixels) return 0;
        
        // Copy existing content (as much as fits)
        int32_t copy_w = (width < buf->width) ? width : buf->width;
        int32_t copy_h = (height < buf->height) ? height : buf->height;
        
        for (int32_t y = 0; y < copy_h; y++) {
            memcpy(new_pixels + y * width,
                   buf->pixels + y * buf->width,
                   copy_w * sizeof(uint32_t));
        }
        
        free(buf->pixels);
        buf->pixels = new_pixels;
        buf->capacity = new_count;
    } else {
        // Reuse existing allocation, just clear if growing
        if (width > buf->width || height > buf->height) {
            memset(buf->pixels, 0, new_count * sizeof(uint32_t));
            
            // Copy existing content
            int32_t copy_w = (width < buf->width) ? width : buf->width;
            int32_t copy_h = (height < buf->height) ? height : buf->height;
            
            // Temporary copy for overlap safety
            uint32_t* temp = (uint32_t*)malloc(copy_w * copy_h * sizeof(uint32_t));
            if (temp) {
                for (int32_t y = 0; y < copy_h; y++) {
                    memcpy(temp + y * copy_w,
                           buf->pixels + y * buf->width,
                           copy_w * sizeof(uint32_t));
                }
                for (int32_t y = 0; y < copy_h; y++) {
                    memcpy(buf->pixels + y * width,
                           temp + y * copy_w,
                           copy_w * sizeof(uint32_t));
                }
                free(temp);
            }
        }
    }
    
    buf->width = width;
    buf->height = height;
    return 1;
}

void simjot_buffer_destroy(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    auto it = g_buffers.find(handle);
    if (it != g_buffers.end()) {
        OffscreenBuffer* buf = it->second;
        free(buf->pixels);
        delete buf;
        g_buffers.erase(it);
    }
}

void simjot_buffer_clear(int64_t handle, uint32_t argb) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return;
    
    size_t count = (size_t)buf->width * (size_t)buf->height;
    
    if (argb == 0) {
        memset(buf->pixels, 0, count * sizeof(uint32_t));
    } else {
        // Fill with color
        uint32_t* p = buf->pixels;
        for (size_t i = 0; i < count; i++) {
            p[i] = argb;
        }
    }
}

int32_t simjot_buffer_write(int64_t handle, const int32_t* pixels,
                            int32_t src_width, int32_t src_height,
                            int32_t dst_x, int32_t dst_y) {
    if (!pixels || src_width <= 0 || src_height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (dst_x < 0) ? -dst_x : 0;
    int32_t start_y = (dst_y < 0) ? -dst_y : 0;
    int32_t end_x = src_width;
    int32_t end_y = src_height;
    
    if (dst_x + end_x > buf->width) end_x = buf->width - dst_x;
    if (dst_y + end_y > buf->height) end_y = buf->height - dst_y;
    
    if (start_x >= end_x || start_y >= end_y) return 1; // Nothing to copy
    
    int32_t copy_width = end_x - start_x;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t src_row = y;
        int32_t dst_row = dst_y + y;
        if (dst_row < 0 || dst_row >= buf->height) continue;
        
        const int32_t* src = pixels + src_row * src_width + start_x;
        uint32_t* dst = buf->pixels + dst_row * buf->width + dst_x + start_x;
        
        memcpy(dst, src, copy_width * sizeof(uint32_t));
    }
    
    return 1;
}

int32_t simjot_buffer_read(int64_t handle, int32_t* out_pixels,
                           int32_t src_x, int32_t src_y,
                           int32_t width, int32_t height) {
    if (!out_pixels || width <= 0 || height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (src_x < 0) ? -src_x : 0;
    int32_t start_y = (src_y < 0) ? -src_y : 0;
    int32_t end_x = width;
    int32_t end_y = height;
    
    if (src_x + end_x > buf->width) end_x = buf->width - src_x;
    if (src_y + end_y > buf->height) end_y = buf->height - src_y;
    
    // Clear output first (for out-of-bounds areas)
    memset(out_pixels, 0, (size_t)width * (size_t)height * sizeof(int32_t));
    
    if (start_x >= end_x || start_y >= end_y) return 1;
    
    int32_t copy_width = end_x - start_x;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t buf_row = src_y + y;
        if (buf_row < 0 || buf_row >= buf->height) continue;
        
        const uint32_t* src = buf->pixels + buf_row * buf->width + src_x + start_x;
        int32_t* dst = out_pixels + y * width + start_x;
        
        memcpy(dst, src, copy_width * sizeof(int32_t));
    }
    
    return 1;
}

void simjot_buffer_scroll(int64_t handle, int32_t dx, int32_t dy, uint32_t fill_argb) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return;
    
    int32_t w = buf->width;
    int32_t h = buf->height;
    
    if (dx == 0 && dy == 0) return;
    
    // Handle vertical scroll with memmove for efficiency
    if (dy != 0) {
        if (dy > 0) {
            // Scrolling down: move rows up, fill bottom
            if (dy < h) {
                memmove(buf->pixels,
                        buf->pixels + dy * w,
                        (size_t)(h - dy) * w * sizeof(uint32_t));
            }
            // Fill exposed bottom rows
            int32_t fill_start = (dy >= h) ? 0 : (h - dy);
            for (int32_t y = fill_start; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                for (int32_t x = 0; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        } else {
            // Scrolling up: move rows down, fill top
            int32_t abs_dy = -dy;
            if (abs_dy < h) {
                memmove(buf->pixels + abs_dy * w,
                        buf->pixels,
                        (size_t)(h - abs_dy) * w * sizeof(uint32_t));
            }
            // Fill exposed top rows
            int32_t fill_end = (abs_dy >= h) ? h : abs_dy;
            for (int32_t y = 0; y < fill_end; y++) {
                uint32_t* row = buf->pixels + y * w;
                for (int32_t x = 0; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        }
    }
    
    // Handle horizontal scroll
    if (dx != 0) {
        if (dx > 0) {
            // Scrolling right: move pixels left, fill right edge
            for (int32_t y = 0; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                if (dx < w) {
                    memmove(row, row + dx, (w - dx) * sizeof(uint32_t));
                }
                int32_t fill_start = (dx >= w) ? 0 : (w - dx);
                for (int32_t x = fill_start; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        } else {
            // Scrolling left: move pixels right, fill left edge
            int32_t abs_dx = -dx;
            for (int32_t y = 0; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                if (abs_dx < w) {
                    memmove(row + abs_dx, row, (w - abs_dx) * sizeof(uint32_t));
                }
                int32_t fill_end = (abs_dx >= w) ? w : abs_dx;
                for (int32_t x = 0; x < fill_end; x++) {
                    row[x] = fill_argb;
                }
            }
        }
    }
}

int32_t simjot_buffer_composite(int64_t handle, const int32_t* pixels,
                                 int32_t src_width, int32_t src_height,
                                 int32_t dst_x, int32_t dst_y) {
    if (!pixels || src_width <= 0 || src_height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (dst_x < 0) ? -dst_x : 0;
    int32_t start_y = (dst_y < 0) ? -dst_y : 0;
    int32_t end_x = src_width;
    int32_t end_y = src_height;
    
    if (dst_x + end_x > buf->width) end_x = buf->width - dst_x;
    if (dst_y + end_y > buf->height) end_y = buf->height - dst_y;
    
    if (start_x >= end_x || start_y >= end_y) return 1;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t dst_row = dst_y + y;
        if (dst_row < 0 || dst_row >= buf->height) continue;
        
        const uint32_t* src_row = (const uint32_t*)pixels + y * src_width;
        uint32_t* dst_row_ptr = buf->pixels + dst_row * buf->width;
        
        for (int32_t x = start_x; x < end_x; x++) {
            int32_t dst_col = dst_x + x;
            if (dst_col < 0 || dst_col >= buf->width) continue;
            
            uint32_t src_pixel = src_row[x];
            uint32_t dst_pixel = dst_row_ptr[dst_col];
            dst_row_ptr[dst_col] = alpha_blend(src_pixel, dst_pixel);
        }
    }
    
    return 1;
}

int32_t simjot_buffer_get_size(int64_t handle, int32_t* out_width, int32_t* out_height) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    if (out_width) *out_width = buf->width;
    if (out_height) *out_height = buf->height;
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE RENDERING - High-performance anti-aliased line drawing
 * ═══════════════════════════════════════════════════════════════════════════ */

// Wu's anti-aliased line algorithm helper
static inline void plot_aa_pixel(uint32_t* pixels, int32_t width, int32_t height,
                                  int32_t x, int32_t y, uint32_t color, float brightness) {
    if (x < 0 || x >= width || y < 0 || y >= height) return;
    if (brightness <= 0.0f) return;
    
    uint32_t alpha = (color >> 24) & 0xFF;
    uint32_t new_alpha = (uint32_t)(alpha * brightness);
    if (new_alpha == 0) return;
    
    uint32_t src = (new_alpha << 24) | (color & 0x00FFFFFF);
    uint32_t dst = pixels[y * width + x];
    pixels[y * width + x] = alpha_blend(src, dst);
}

// Fast integer part
static inline int32_t ipart(float x) { return (int32_t)x; }
static inline float fpart(float x) { return x - (float)ipart(x); }
static inline float rfpart(float x) { return 1.0f - fpart(x); }

// Draw anti-aliased line segment with thickness using Wu's algorithm
static void draw_thick_line_aa(uint32_t* pixels, int32_t width, int32_t height,
                                float x0, float y0, float x1, float y1,
                                float thickness, uint32_t color) {
    float dx = x1 - x0;
    float dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    if (len < 0.001f) {
        // Single point
        int32_t px = (int32_t)(x0 + 0.5f);
        int32_t py = (int32_t)(y0 + 0.5f);
        int32_t r = (int32_t)(thickness * 0.5f + 0.5f);
        for (int32_t oy = -r; oy <= r; oy++) {
            for (int32_t ox = -r; ox <= r; ox++) {
                if (ox*ox + oy*oy <= r*r) {
                    plot_aa_pixel(pixels, width, height, px + ox, py + oy, color, 1.0f);
                }
            }
        }
        return;
    }
    
    // Normalize direction
    float nx = -dy / len;
    float ny = dx / len;
    float half_t = thickness * 0.5f;
    
    // For thin lines, use Wu's algorithm directly
    if (thickness <= 2.0f) {
        bool steep = fabsf(dy) > fabsf(dx);
        if (steep) {
            float tmp = x0; x0 = y0; y0 = tmp;
            tmp = x1; x1 = y1; y1 = tmp;
        }
        if (x0 > x1) {
            float tmp = x0; x0 = x1; x1 = tmp;
            tmp = y0; y0 = y1; y1 = tmp;
        }
        
        dx = x1 - x0;
        dy = y1 - y0;
        float gradient = (dx == 0.0f) ? 1.0f : dy / dx;
        
        // First endpoint
        int32_t xend = (int32_t)(x0 + 0.5f);
        float yend = y0 + gradient * (xend - x0);
        float xgap = rfpart(x0 + 0.5f);
        int32_t xpxl1 = xend;
        int32_t ypxl1 = ipart(yend);
        
        if (steep) {
            plot_aa_pixel(pixels, width, height, ypxl1, xpxl1, color, rfpart(yend) * xgap);
            plot_aa_pixel(pixels, width, height, ypxl1 + 1, xpxl1, color, fpart(yend) * xgap);
        } else {
            plot_aa_pixel(pixels, width, height, xpxl1, ypxl1, color, rfpart(yend) * xgap);
            plot_aa_pixel(pixels, width, height, xpxl1, ypxl1 + 1, color, fpart(yend) * xgap);
        }
        float intery = yend + gradient;
        
        // Second endpoint
        xend = (int32_t)(x1 + 0.5f);
        yend = y1 + gradient * (xend - x1);
        xgap = fpart(x1 + 0.5f);
        int32_t xpxl2 = xend;
        int32_t ypxl2 = ipart(yend);
        
        if (steep) {
            plot_aa_pixel(pixels, width, height, ypxl2, xpxl2, color, rfpart(yend) * xgap);
            plot_aa_pixel(pixels, width, height, ypxl2 + 1, xpxl2, color, fpart(yend) * xgap);
        } else {
            plot_aa_pixel(pixels, width, height, xpxl2, ypxl2, color, rfpart(yend) * xgap);
            plot_aa_pixel(pixels, width, height, xpxl2, ypxl2 + 1, color, fpart(yend) * xgap);
        }
        
        // Main loop
        for (int32_t x = xpxl1 + 1; x < xpxl2; x++) {
            if (steep) {
                plot_aa_pixel(pixels, width, height, ipart(intery), x, color, rfpart(intery));
                plot_aa_pixel(pixels, width, height, ipart(intery) + 1, x, color, fpart(intery));
            } else {
                plot_aa_pixel(pixels, width, height, x, ipart(intery), color, rfpart(intery));
                plot_aa_pixel(pixels, width, height, x, ipart(intery) + 1, color, fpart(intery));
            }
            intery += gradient;
        }
    } else {
        // For thick lines, draw filled polygon (quad)
        float p0x = x0 + nx * half_t, p0y = y0 + ny * half_t;
        float p1x = x0 - nx * half_t, p1y = y0 - ny * half_t;
        float p2x = x1 - nx * half_t, p2y = y1 - ny * half_t;
        float p3x = x1 + nx * half_t, p3y = y1 + ny * half_t;
        
        // Bounding box
        float minX = fminf(fminf(p0x, p1x), fminf(p2x, p3x));
        float maxX = fmaxf(fmaxf(p0x, p1x), fmaxf(p2x, p3x));
        float minY = fminf(fminf(p0y, p1y), fminf(p2y, p3y));
        float maxY = fmaxf(fmaxf(p0y, p1y), fmaxf(p2y, p3y));
        
        int32_t x_start = (int32_t)fmaxf(0.0f, minX - 1.0f);
        int32_t x_end = (int32_t)fminf((float)(width - 1), maxX + 1.0f);
        int32_t y_start = (int32_t)fmaxf(0.0f, minY - 1.0f);
        int32_t y_end = (int32_t)fminf((float)(height - 1), maxY + 1.0f);
        
        // Scanline fill with distance-based antialiasing
        for (int32_t py = y_start; py <= y_end; py++) {
            for (int32_t px = x_start; px <= x_end; px++) {
                // Distance to line segment
                float t = ((px - x0) * dx + (py - y0) * dy) / (len * len);
                t = fmaxf(0.0f, fminf(1.0f, t));
                float closestX = x0 + t * dx;
                float closestY = y0 + t * dy;
                float dist = sqrtf((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
                
                if (dist <= half_t + 1.0f) {
                    float coverage = fmaxf(0.0f, fminf(1.0f, half_t + 0.5f - dist));
                    if (coverage > 0.0f) {
                        plot_aa_pixel(pixels, width, height, px, py, color, coverage);
                    }
                }
            }
        }
    }
    
    // Draw round caps
    int32_t cap_r = (int32_t)(half_t + 1.5f);
    for (int32_t i = 0; i < 2; i++) {
        float cx = (i == 0) ? x0 : x1;
        float cy = (i == 0) ? y0 : y1;
        int32_t icx = (int32_t)cx;
        int32_t icy = (int32_t)cy;
        
        for (int32_t oy = -cap_r; oy <= cap_r; oy++) {
            for (int32_t ox = -cap_r; ox <= cap_r; ox++) {
                float dist = sqrtf((float)(ox * ox + oy * oy));
                if (dist <= half_t + 1.0f) {
                    float coverage = fmaxf(0.0f, fminf(1.0f, half_t + 0.5f - dist));
                    if (coverage > 0.0f) {
                        plot_aa_pixel(pixels, width, height, icx + ox, icy + oy, color, coverage);
                    }
                }
            }
        }
    }
}

int32_t simjot_draw_stroke(uint32_t* pixels, int32_t width, int32_t height,
                            const float* points_x, const float* points_y,
                            int32_t point_count, float thickness, uint32_t argb_color,
                            float offset_x, float offset_y) {
    if (!pixels || !points_x || !points_y || point_count < 1) return 0;
    if (width <= 0 || height <= 0) return 0;
    
    if (point_count == 1) {
        // Single point - draw dot
        float x = points_x[0] - offset_x;
        float y = points_y[0] - offset_y;
        draw_thick_line_aa(pixels, width, height, x, y, x, y, thickness, argb_color);
        return 1;
    }
    
    // Draw line segments
    for (int32_t i = 0; i < point_count - 1; i++) {
        float x0 = points_x[i] - offset_x;
        float y0 = points_y[i] - offset_y;
        float x1 = points_x[i + 1] - offset_x;
        float y1 = points_y[i + 1] - offset_y;
        
        draw_thick_line_aa(pixels, width, height, x0, y0, x1, y1, thickness, argb_color);
    }
    
    return 1;
}

int32_t simjot_draw_strokes_batch(uint32_t* pixels, int32_t width, int32_t height,
                                   const float* all_points_x, const float* all_points_y,
                                   const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                   const float* thicknesses, const uint32_t* colors,
                                   int32_t stroke_count,
                                   float offset_x, float offset_y) {
    if (!pixels || !all_points_x || !all_points_y) return 0;
    if (!stroke_starts || !stroke_lengths || !thicknesses || !colors) return 0;
    if (stroke_count < 1 || width <= 0 || height <= 0) return 0;
    
    for (int32_t s = 0; s < stroke_count; s++) {
        int32_t start = stroke_starts[s];
        int32_t len = stroke_lengths[s];
        if (len < 1) continue;
        
        simjot_draw_stroke(pixels, width, height,
                           all_points_x + start, all_points_y + start,
                           len, thicknesses[s], colors[s],
                           offset_x, offset_y);
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ERASER HIT TESTING - Fast point-to-segment distance for all strokes
 * ═══════════════════════════════════════════════════════════════════════════ */

// Distance squared from point to line segment
static inline float dist_point_to_segment_sq(float px, float py,
                                              float ax, float ay, float bx, float by) {
    float vx = bx - ax, vy = by - ay;
    float wx = px - ax, wy = py - ay;
    float c1 = vx * wx + vy * wy;
    if (c1 <= 0.0f) {
        return (px - ax) * (px - ax) + (py - ay) * (py - ay);
    }
    float c2 = vx * vx + vy * vy;
    if (c2 <= c1) {
        return (px - bx) * (px - bx) + (py - by) * (py - by);
    }
    float t = c1 / c2;
    float projx = ax + t * vx;
    float projy = ay + t * vy;
    float dx = px - projx, dy = py - projy;
    return dx * dx + dy * dy;
}

// Check if eraser at (px, py) with radius hits any stroke segment
// Returns index of first hit stroke, or -1 if no hit
int32_t simjot_eraser_hit_test(float px, float py, float radius_sq,
                                const float* all_points_x, const float* all_points_y,
                                const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                int32_t stroke_count) {
    if (!all_points_x || !all_points_y || !stroke_starts || !stroke_lengths) return -1;
    
    for (int32_t s = 0; s < stroke_count; s++) {
        int32_t start = stroke_starts[s];
        int32_t len = stroke_lengths[s];
        if (len < 2) continue;
        
        const float* xs = all_points_x + start;
        const float* ys = all_points_y + start;
        
        for (int32_t i = 0; i < len - 1; i++) {
            float dist_sq = dist_point_to_segment_sq(px, py, xs[i], ys[i], xs[i+1], ys[i+1]);
            if (dist_sq <= radius_sq) {
                return s; // Hit!
            }
        }
    }
    
    return -1; // No hit
}

// Batch hit test - returns bitmask of which strokes were hit (up to 64 strokes)
// For more than 64 strokes, use simjot_eraser_hit_test_array
int64_t simjot_eraser_hit_test_batch(float px, float py, float radius_sq,
                                      const float* all_points_x, const float* all_points_y,
                                      const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                      int32_t stroke_count) {
    if (!all_points_x || !all_points_y || !stroke_starts || !stroke_lengths) return 0;
    
    int64_t hit_mask = 0;
    int32_t max_strokes = (stroke_count < 64) ? stroke_count : 64;
    
    for (int32_t s = 0; s < max_strokes; s++) {
        int32_t start = stroke_starts[s];
        int32_t len = stroke_lengths[s];
        if (len < 2) continue;
        
        const float* xs = all_points_x + start;
        const float* ys = all_points_y + start;
        
        for (int32_t i = 0; i < len - 1; i++) {
            float dist_sq = dist_point_to_segment_sq(px, py, xs[i], ys[i], xs[i+1], ys[i+1]);
            if (dist_sq <= radius_sq) {
                hit_mask |= (1LL << s);
                break; // Move to next stroke
            }
        }
    }
    
    return hit_mask;
}

// Hit test for unlimited strokes - writes results to output array
int32_t simjot_eraser_hit_test_array(float px, float py, float radius_sq,
                                      const float* all_points_x, const float* all_points_y,
                                      const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                      int32_t stroke_count,
                                      int32_t* hit_indices, int32_t hit_capacity) {
    if (!all_points_x || !all_points_y || !stroke_starts || !stroke_lengths) return 0;
    if (!hit_indices || hit_capacity < 1) return 0;
    
    int32_t hit_count = 0;
    
    for (int32_t s = 0; s < stroke_count && hit_count < hit_capacity; s++) {
        int32_t start = stroke_starts[s];
        int32_t len = stroke_lengths[s];
        if (len < 2) continue;
        
        const float* xs = all_points_x + start;
        const float* ys = all_points_y + start;
        
        for (int32_t i = 0; i < len - 1; i++) {
            float dist_sq = dist_point_to_segment_sq(px, py, xs[i], ys[i], xs[i+1], ys[i+1]);
            if (dist_sq <= radius_sq) {
                hit_indices[hit_count++] = s;
                break;
            }
        }
    }
    
    return hit_count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE BOUNDS - Fast bounding box calculation
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_stroke_calc_bounds(const float* points_x, const float* points_y, int32_t count,
                                   float* out_min_x, float* out_min_y,
                                   float* out_max_x, float* out_max_y) {
    if (!points_x || !points_y || count < 1) return 0;
    
    float minX = points_x[0], maxX = points_x[0];
    float minY = points_y[0], maxY = points_y[0];
    
    for (int32_t i = 1; i < count; i++) {
        float x = points_x[i], y = points_y[i];
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }
    
    if (out_min_x) *out_min_x = minX;
    if (out_min_y) *out_min_y = minY;
    if (out_max_x) *out_max_x = maxX;
    if (out_max_y) *out_max_y = maxY;
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * POINT ARRAY UTILITIES - Fast operations on point arrays
 * ═══════════════════════════════════════════════════════════════════════════ */

// Convert int array to float array (for Java int[] -> native float*)
void simjot_points_int_to_float(const int32_t* src_x, const int32_t* src_y, int32_t count,
                                 float* dst_x, float* dst_y) {
    if (!src_x || !src_y || !dst_x || !dst_y) return;
    for (int32_t i = 0; i < count; i++) {
        dst_x[i] = (float)src_x[i];
        dst_y[i] = (float)src_y[i];
    }
}

// Calculate total stroke length
float simjot_stroke_calc_length(const float* points_x, const float* points_y, int32_t count) {
    if (!points_x || !points_y || count < 2) return 0.0f;
    
    float total = 0.0f;
    for (int32_t i = 0; i < count - 1; i++) {
        float dx = points_x[i+1] - points_x[i];
        float dy = points_y[i+1] - points_y[i];
        total += sqrtf(dx * dx + dy * dy);
    }
    return total;
}

// Simplify stroke using Ramer-Douglas-Peucker algorithm
static void rdp_simplify(const float* xs, const float* ys, int32_t start, int32_t end,
                          float epsilon_sq, uint8_t* keep) {
    if (end <= start + 1) return;
    
    float dx = xs[end] - xs[start];
    float dy = ys[end] - ys[start];
    float len_sq = dx * dx + dy * dy;
    
    float max_dist_sq = 0.0f;
    int32_t max_idx = start;
    
    for (int32_t i = start + 1; i < end; i++) {
        float dist_sq;
        if (len_sq < 0.0001f) {
            // Start and end are same point
            float px = xs[i] - xs[start];
            float py = ys[i] - ys[start];
            dist_sq = px * px + py * py;
        } else {
            float t = ((xs[i] - xs[start]) * dx + (ys[i] - ys[start]) * dy) / len_sq;
            t = fmaxf(0.0f, fminf(1.0f, t));
            float projx = xs[start] + t * dx;
            float projy = ys[start] + t * dy;
            float px = xs[i] - projx;
            float py = ys[i] - projy;
            dist_sq = px * px + py * py;
        }
        
        if (dist_sq > max_dist_sq) {
            max_dist_sq = dist_sq;
            max_idx = i;
        }
    }
    
    if (max_dist_sq > epsilon_sq) {
        keep[max_idx] = 1;
        rdp_simplify(xs, ys, start, max_idx, epsilon_sq, keep);
        rdp_simplify(xs, ys, max_idx, end, epsilon_sq, keep);
    }
}

int32_t simjot_stroke_simplify_rdp(const float* in_x, const float* in_y, int32_t in_count,
                                    float* out_x, float* out_y, int32_t out_capacity,
                                    float epsilon) {
    if (!in_x || !in_y || !out_x || !out_y || in_count < 2 || out_capacity < 2) {
        return 0;
    }
    
    // Allocate keep array
    uint8_t* keep = (uint8_t*)calloc(in_count, 1);
    if (!keep) return 0;
    
    keep[0] = 1;
    keep[in_count - 1] = 1;
    
    float epsilon_sq = epsilon * epsilon;
    rdp_simplify(in_x, in_y, 0, in_count - 1, epsilon_sq, keep);
    
    // Copy kept points
    int32_t out_count = 0;
    for (int32_t i = 0; i < in_count && out_count < out_capacity; i++) {
        if (keep[i]) {
            out_x[out_count] = in_x[i];
            out_y[out_count] = in_y[i];
            out_count++;
        }
    }
    
    free(keep);
    return out_count;
}

// Clear a rectangular region of a pixel buffer
void simjot_buffer_clear_rect(uint32_t* pixels, int32_t width, int32_t height,
                               int32_t x, int32_t y, int32_t w, int32_t h, uint32_t color) {
    if (!pixels || width <= 0 || height <= 0) return;
    
    // Clip to bounds
    if (x < 0) { w += x; x = 0; }
    if (y < 0) { h += y; y = 0; }
    if (x + w > width) w = width - x;
    if (y + h > height) h = height - y;
    if (w <= 0 || h <= 0) return;
    
    for (int32_t py = y; py < y + h; py++) {
        uint32_t* row = pixels + py * width + x;
        if (color == 0) {
            memset(row, 0, w * sizeof(uint32_t));
        } else {
            for (int32_t px = 0; px < w; px++) {
                row[px] = color;
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * REAL-TIME STROKE SMOOTHING - Lightweight EMA filter for live input
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Smooth a single incoming point using exponential moving average.
 * This is designed to be called per-point as input arrives.
 * 
 * @param new_x, new_y    - The new raw input point
 * @param prev_x, prev_y  - The previous smoothed point (output from last call)
 * @param out_x, out_y    - Output smoothed point
 * @param alpha           - Smoothing factor (0.0-1.0). Higher = less smoothing, more responsive.
 *                          Recommended: 0.5-0.8 for stylus input
 */
void simjot_smooth_point_ema(float new_x, float new_y,
                              float prev_x, float prev_y,
                              float* out_x, float* out_y,
                              float alpha) {
    if (!out_x || !out_y) return;
    // EMA: smoothed = alpha * new + (1 - alpha) * prev
    *out_x = alpha * new_x + (1.0f - alpha) * prev_x;
    *out_y = alpha * new_y + (1.0f - alpha) * prev_y;
}

/**
 * Smooth an entire stroke in-place using a single-pass EMA filter.
 * Very fast O(n) algorithm suitable for real-time use.
 * 
 * @param points_x, points_y - Point arrays (modified in-place)
 * @param count              - Number of points
 * @param alpha              - Smoothing factor (0.3-0.7 recommended)
 * @return 1 on success, 0 on error
 */
int32_t simjot_smooth_stroke_ema(float* points_x, float* points_y,
                                  int32_t count, float alpha) {
    if (!points_x || !points_y || count < 2) return 0;
    if (alpha <= 0.0f) alpha = 0.5f;
    if (alpha > 1.0f) alpha = 1.0f;
    
    // Forward pass - smooth from start to end
    for (int32_t i = 1; i < count; i++) {
        points_x[i] = alpha * points_x[i] + (1.0f - alpha) * points_x[i-1];
        points_y[i] = alpha * points_y[i] + (1.0f - alpha) * points_y[i-1];
    }
    
    // Optional: backward pass for bidirectional smoothing (reduces lag)
    // This creates a zero-phase filter effect
    for (int32_t i = count - 2; i >= 0; i--) {
        points_x[i] = alpha * points_x[i] + (1.0f - alpha) * points_x[i+1];
        points_y[i] = alpha * points_y[i] + (1.0f - alpha) * points_y[i+1];
    }
    
    return 1;
}

/**
 * Smooth stroke with adaptive alpha based on segment velocity.
 * Faster movements get less smoothing (more responsive), 
 * slower movements get more smoothing (steadier lines).
 * 
 * @param points_x, points_y - Point arrays (modified in-place)
 * @param count              - Number of points  
 * @param base_alpha         - Base smoothing factor (0.3-0.6 recommended)
 * @param velocity_scale     - How much velocity affects alpha (0.01-0.05 recommended)
 * @return 1 on success, 0 on error
 */
int32_t simjot_smooth_stroke_adaptive(float* points_x, float* points_y,
                                       int32_t count, float base_alpha,
                                       float velocity_scale) {
    if (!points_x || !points_y || count < 2) return 0;
    if (base_alpha <= 0.0f) base_alpha = 0.4f;
    if (base_alpha > 1.0f) base_alpha = 1.0f;
    if (velocity_scale <= 0.0f) velocity_scale = 0.02f;
    
    for (int32_t i = 1; i < count; i++) {
        // Calculate velocity (distance from previous point)
        float dx = points_x[i] - points_x[i-1];
        float dy = points_y[i] - points_y[i-1];
        float velocity = sqrtf(dx * dx + dy * dy);
        
        // Adaptive alpha: faster = higher alpha (less smoothing)
        float alpha = base_alpha + velocity * velocity_scale;
        if (alpha > 0.95f) alpha = 0.95f;
        
        // Apply EMA
        points_x[i] = alpha * points_x[i] + (1.0f - alpha) * points_x[i-1];
        points_y[i] = alpha * points_y[i] + (1.0f - alpha) * points_y[i-1];
    }
    
    return 1;
}

} /* extern "C" */
