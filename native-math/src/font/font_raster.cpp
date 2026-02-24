/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "font_types.h"
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <vector>

/* ═══════════════════════════════════════════════════════════════════════════
 * BITMAP MEMORY MANAGEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_bitmap_t* sjf_bitmap_create(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > SJF_RASTER_MAX_SIZE || height > SJF_RASTER_MAX_SIZE) {
        return nullptr;
    }
    
    sjf_bitmap_t* bmp = (sjf_bitmap_t*)calloc(1, sizeof(sjf_bitmap_t));
    if (!bmp) return nullptr;
    
    bmp->stride = (width + 3) & ~3; // 4-byte aligned rows
    bmp->pixels = (uint8_t*)calloc(bmp->stride * height, 1);
    if (!bmp->pixels) {
        free(bmp);
        return nullptr;
    }
    
    bmp->width = width;
    bmp->height = height;
    bmp->origin_x = 0.0f;
    bmp->origin_y = 0.0f;
    
    return bmp;
}

extern "C" void sjf_bitmap_free(sjf_bitmap_t* bmp) {
    if (bmp) {
        free(bmp->pixels);
        free(bmp);
    }
}

extern "C" void sjf_bitmap_clear(sjf_bitmap_t* bmp) {
    if (bmp && bmp->pixels) {
        memset(bmp->pixels, 0, bmp->stride * bmp->height);
    }
}

extern "C" int32_t sjf_bitmap_get_width(const sjf_bitmap_t* bmp) {
    return bmp ? bmp->width : 0;
}

extern "C" int32_t sjf_bitmap_get_height(const sjf_bitmap_t* bmp) {
    return bmp ? bmp->height : 0;
}

extern "C" int32_t sjf_bitmap_get_stride(const sjf_bitmap_t* bmp) {
    return bmp ? bmp->stride : 0;
}

extern "C" uint8_t* sjf_bitmap_get_pixels(sjf_bitmap_t* bmp) {
    return bmp ? bmp->pixels : nullptr;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ANTIALIASED LINE DRAWING - Xiaolin Wu's Algorithm
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline void plot_pixel(sjf_bitmap_t* bmp, int32_t x, int32_t y, float alpha) {
    if (x < 0 || x >= bmp->width || y < 0 || y >= bmp->height) return;
    
    int32_t idx = y * bmp->stride + x;
    int32_t new_val = bmp->pixels[idx] + (int32_t)(alpha * 255.0f);
    bmp->pixels[idx] = (uint8_t)std::min(255, new_val);
}

static inline float fpart(float x) {
    return x - floorf(x);
}

static inline float rfpart(float x) {
    return 1.0f - fpart(x);
}

static void draw_line_wu(sjf_bitmap_t* bmp, float x0, float y0, float x1, float y1, float thickness) {
    bool steep = fabsf(y1 - y0) > fabsf(x1 - x0);
    
    if (steep) {
        std::swap(x0, y0);
        std::swap(x1, y1);
    }
    if (x0 > x1) {
        std::swap(x0, x1);
        std::swap(y0, y1);
    }
    
    float dx = x1 - x0;
    float dy = y1 - y0;
    float gradient = (dx < 0.0001f) ? 1.0f : dy / dx;
    
    // Handle first endpoint
    float xend = roundf(x0);
    float yend = y0 + gradient * (xend - x0);
    float xgap = rfpart(x0 + 0.5f);
    int32_t xpxl1 = (int32_t)xend;
    int32_t ypxl1 = (int32_t)floorf(yend);
    
    if (steep) {
        plot_pixel(bmp, ypxl1, xpxl1, rfpart(yend) * xgap * thickness);
        plot_pixel(bmp, ypxl1 + 1, xpxl1, fpart(yend) * xgap * thickness);
    } else {
        plot_pixel(bmp, xpxl1, ypxl1, rfpart(yend) * xgap * thickness);
        plot_pixel(bmp, xpxl1, ypxl1 + 1, fpart(yend) * xgap * thickness);
    }
    
    float intery = yend + gradient;
    
    // Handle second endpoint
    xend = roundf(x1);
    yend = y1 + gradient * (xend - x1);
    xgap = fpart(x1 + 0.5f);
    int32_t xpxl2 = (int32_t)xend;
    int32_t ypxl2 = (int32_t)floorf(yend);
    
    if (steep) {
        plot_pixel(bmp, ypxl2, xpxl2, rfpart(yend) * xgap * thickness);
        plot_pixel(bmp, ypxl2 + 1, xpxl2, fpart(yend) * xgap * thickness);
    } else {
        plot_pixel(bmp, xpxl2, ypxl2, rfpart(yend) * xgap * thickness);
        plot_pixel(bmp, xpxl2, ypxl2 + 1, fpart(yend) * xgap * thickness);
    }
    
    // Main loop
    if (steep) {
        for (int32_t x = xpxl1 + 1; x < xpxl2; x++) {
            int32_t y = (int32_t)floorf(intery);
            plot_pixel(bmp, y, x, rfpart(intery) * thickness);
            plot_pixel(bmp, y + 1, x, fpart(intery) * thickness);
            intery += gradient;
        }
    } else {
        for (int32_t x = xpxl1 + 1; x < xpxl2; x++) {
            int32_t y = (int32_t)floorf(intery);
            plot_pixel(bmp, x, y, rfpart(intery) * thickness);
            plot_pixel(bmp, x, y + 1, fpart(intery) * thickness);
            intery += gradient;
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * THICK LINE DRAWING - Variable width stroke
 * ═══════════════════════════════════════════════════════════════════════════ */

static void draw_thick_line(sjf_bitmap_t* bmp, 
                            float x0, float y0, float x1, float y1,
                            float thickness0, float thickness1) {
    float dx = x1 - x0;
    float dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    
    if (len < 0.001f) {
        // Draw a dot
        int32_t cx = (int32_t)roundf(x0);
        int32_t cy = (int32_t)roundf(y0);
        float r = thickness0 * 0.5f;
        int32_t ri = (int32_t)ceilf(r);
        
        for (int32_t py = -ri; py <= ri; py++) {
            for (int32_t px = -ri; px <= ri; px++) {
                float dist = sqrtf((float)(px * px + py * py));
                if (dist <= r) {
                    float alpha = std::min(1.0f, r - dist + 0.5f);
                    plot_pixel(bmp, cx + px, cy + py, alpha);
                }
            }
        }
        return;
    }
    
    // Perpendicular vector
    float nx = -dy / len;
    float ny = dx / len;
    
    // Draw multiple parallel lines for thickness
    float max_thick = std::max(thickness0, thickness1);
    int32_t num_lines = (int32_t)ceilf(max_thick);
    
    for (int32_t i = -num_lines; i <= num_lines; i++) {
        float offset = (float)i * 0.5f;
        float alpha = 1.0f - fabsf(offset) / (max_thick * 0.5f + 0.5f);
        if (alpha <= 0.0f) continue;
        
        draw_line_wu(bmp, 
                     x0 + nx * offset, y0 + ny * offset,
                     x1 + nx * offset, y1 + ny * offset,
                     alpha);
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FILLED CIRCLE - For stroke caps
 * ═══════════════════════════════════════════════════════════════════════════ */

static void draw_filled_circle(sjf_bitmap_t* bmp, float cx, float cy, float radius) {
    int32_t min_x = (int32_t)floorf(cx - radius - 1);
    int32_t max_x = (int32_t)ceilf(cx + radius + 1);
    int32_t min_y = (int32_t)floorf(cy - radius - 1);
    int32_t max_y = (int32_t)ceilf(cy + radius + 1);
    
    for (int32_t y = min_y; y <= max_y; y++) {
        for (int32_t x = min_x; x <= max_x; x++) {
            float dx = (float)x - cx;
            float dy = (float)y - cy;
            float dist = sqrtf(dx * dx + dy * dy);
            
            if (dist <= radius + 0.5f) {
                float alpha = std::min(1.0f, radius - dist + 0.5f);
                if (alpha > 0.0f) {
                    plot_pixel(bmp, x, y, alpha);
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE RASTERIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_raster_stroke(sjf_bitmap_t* bmp, const sjf_stroke_t* stroke, 
                                      float scale, float offset_x, float offset_y) {
    if (!bmp || !stroke || stroke->point_count < 1) {
        return SJF_ERR_NULL_PTR;
    }
    
    float base_thickness = stroke->thickness * scale;
    
    if (stroke->point_count == 1) {
        // Single point - draw a dot
        float x = stroke->points[0].x * scale + offset_x;
        float y = stroke->points[0].y * scale + offset_y;
        float r = base_thickness * stroke->points[0].pressure * 0.5f;
        draw_filled_circle(bmp, x, y, r);
        return SJF_OK;
    }
    
    // Draw stroke segments
    for (int32_t i = 0; i < stroke->point_count - 1; i++) {
        const sjf_point_t& p0 = stroke->points[i];
        const sjf_point_t& p1 = stroke->points[i + 1];
        
        float x0 = p0.x * scale + offset_x;
        float y0 = p0.y * scale + offset_y;
        float x1 = p1.x * scale + offset_x;
        float y1 = p1.y * scale + offset_y;
        
        float thick0 = base_thickness * p0.pressure;
        float thick1 = base_thickness * p1.pressure;
        
        draw_thick_line(bmp, x0, y0, x1, y1, thick0, thick1);
    }
    
    // Draw round caps
    const sjf_point_t& first = stroke->points[0];
    const sjf_point_t& last = stroke->points[stroke->point_count - 1];
    
    draw_filled_circle(bmp, 
                       first.x * scale + offset_x,
                       first.y * scale + offset_y,
                       base_thickness * first.pressure * 0.5f);
    
    draw_filled_circle(bmp,
                       last.x * scale + offset_x,
                       last.y * scale + offset_y,
                       base_thickness * last.pressure * 0.5f);
    
    return SJF_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH RASTERIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_bitmap_t* sjf_raster_glyph(const sjf_glyph_t* glyph, 
                                           const sjf_raster_opts_t* opts,
                                           float em_size) {
    if (!glyph || glyph->stroke_count == 0) {
        return nullptr;
    }
    
    // Default options
    sjf_raster_opts_t default_opts = {
        32,                     // size
        SJF_RASTER_OVERSAMPLE,  // oversample
        1.0f,                   // gamma
        0,                      // subpixel
        0                       // hinting
    };
    
    const sjf_raster_opts_t* o = opts ? opts : &default_opts;
    
    int32_t target_size = o->size;
    int32_t oversample = std::max(1, std::min(8, o->oversample));
    int32_t render_size = target_size * oversample;
    
    // Calculate scale from em_size to render_size
    float scale = (float)render_size / em_size;
    
    // Find glyph bounds
    float min_x = 1e10f, min_y = 1e10f;
    float max_x = -1e10f, max_y = -1e10f;
    
    for (int32_t s = 0; s < glyph->stroke_count; s++) {
        const sjf_stroke_t* stroke = &glyph->strokes[s];
        for (int32_t p = 0; p < stroke->point_count; p++) {
            float x = stroke->points[p].x;
            float y = stroke->points[p].y;
            float r = stroke->thickness * stroke->points[p].pressure * 0.5f;
            
            min_x = std::min(min_x, x - r);
            min_y = std::min(min_y, y - r);
            max_x = std::max(max_x, x + r);
            max_y = std::max(max_y, y + r);
        }
    }
    
    // Add padding
    float padding = 2.0f;
    min_x -= padding;
    min_y -= padding;
    max_x += padding;
    max_y += padding;
    
    // Calculate bitmap size
    int32_t bmp_width = (int32_t)ceilf((max_x - min_x) * scale);
    int32_t bmp_height = (int32_t)ceilf((max_y - min_y) * scale);
    
    if (bmp_width <= 0 || bmp_height <= 0) return nullptr;
    if (bmp_width > SJF_RASTER_MAX_SIZE * oversample) bmp_width = SJF_RASTER_MAX_SIZE * oversample;
    if (bmp_height > SJF_RASTER_MAX_SIZE * oversample) bmp_height = SJF_RASTER_MAX_SIZE * oversample;
    
    // Create oversampled bitmap
    sjf_bitmap_t* hires = sjf_bitmap_create(bmp_width, bmp_height);
    if (!hires) return nullptr;
    
    // Rasterize all strokes
    float offset_x = -min_x * scale;
    float offset_y = -min_y * scale;
    
    for (int32_t s = 0; s < glyph->stroke_count; s++) {
        sjf_raster_stroke(hires, &glyph->strokes[s], scale, offset_x, offset_y);
    }
    
    // Downsample if needed
    if (oversample > 1) {
        int32_t final_width = (bmp_width + oversample - 1) / oversample;
        int32_t final_height = (bmp_height + oversample - 1) / oversample;
        
        sjf_bitmap_t* final_bmp = sjf_bitmap_create(final_width, final_height);
        if (!final_bmp) {
            sjf_bitmap_free(hires);
            return nullptr;
        }
        
        // Box filter downsample
        for (int32_t y = 0; y < final_height; y++) {
            for (int32_t x = 0; x < final_width; x++) {
                int32_t sum = 0;
                int32_t count = 0;
                
                for (int32_t sy = 0; sy < oversample; sy++) {
                    for (int32_t sx = 0; sx < oversample; sx++) {
                        int32_t hx = x * oversample + sx;
                        int32_t hy = y * oversample + sy;
                        
                        if (hx < bmp_width && hy < bmp_height) {
                            sum += hires->pixels[hy * hires->stride + hx];
                            count++;
                        }
                    }
                }
                
                final_bmp->pixels[y * final_bmp->stride + x] = 
                    (uint8_t)(count > 0 ? sum / count : 0);
            }
        }
        
        // Apply gamma correction
        if (o->gamma != 1.0f) {
            float inv_gamma = 1.0f / o->gamma;
            for (int32_t y = 0; y < final_height; y++) {
                for (int32_t x = 0; x < final_width; x++) {
                    float val = final_bmp->pixels[y * final_bmp->stride + x] / 255.0f;
                    val = powf(val, inv_gamma);
                    final_bmp->pixels[y * final_bmp->stride + x] = (uint8_t)(val * 255.0f);
                }
            }
        }
        
        final_bmp->origin_x = min_x * (float)target_size / em_size;
        final_bmp->origin_y = min_y * (float)target_size / em_size;
        
        sjf_bitmap_free(hires);
        return final_bmp;
    }
    
    hires->origin_x = min_x * (float)target_size / em_size;
    hires->origin_y = min_y * (float)target_size / em_size;
    
    return hires;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ATLAS BUILDING
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_atlas_t* sjf_atlas_create(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > SJF_ATLAS_MAX_SIZE || height > SJF_ATLAS_MAX_SIZE) {
        return nullptr;
    }
    
    sjf_atlas_t* atlas = (sjf_atlas_t*)calloc(1, sizeof(sjf_atlas_t));
    if (!atlas) return nullptr;
    
    atlas->pixels = (uint8_t*)calloc(width * height, 1);
    if (!atlas->pixels) {
        free(atlas);
        return nullptr;
    }
    
    atlas->width = width;
    atlas->height = height;
    atlas->entry_capacity = 128;
    atlas->entries = (sjf_atlas_entry_t*)calloc(atlas->entry_capacity, sizeof(sjf_atlas_entry_t));
    if (!atlas->entries) {
        free(atlas->pixels);
        free(atlas);
        return nullptr;
    }
    
    return atlas;
}

extern "C" void sjf_atlas_free(sjf_atlas_t* atlas) {
    if (atlas) {
        free(atlas->pixels);
        free(atlas->entries);
        free(atlas);
    }
}

// Simple row-based packing
struct AtlasPacker {
    int32_t current_x;
    int32_t current_y;
    int32_t row_height;
    int32_t atlas_width;
    int32_t atlas_height;
    
    AtlasPacker(int32_t w, int32_t h) 
        : current_x(SJF_ATLAS_PADDING), current_y(SJF_ATLAS_PADDING), 
          row_height(0), atlas_width(w), atlas_height(h) {}
    
    bool pack(int32_t glyph_width, int32_t glyph_height, int32_t* out_x, int32_t* out_y) {
        int32_t padded_width = glyph_width + SJF_ATLAS_PADDING;
        int32_t padded_height = glyph_height + SJF_ATLAS_PADDING;
        
        // Check if fits in current row
        if (current_x + padded_width > atlas_width) {
            // Move to next row
            current_x = SJF_ATLAS_PADDING;
            current_y += row_height + SJF_ATLAS_PADDING;
            row_height = 0;
        }
        
        // Check if fits vertically
        if (current_y + padded_height > atlas_height) {
            return false; // Atlas full
        }
        
        *out_x = current_x;
        *out_y = current_y;
        
        current_x += padded_width;
        row_height = std::max(row_height, padded_height);
        
        return true;
    }
};

extern "C" int32_t sjf_atlas_add_glyph(sjf_atlas_t* atlas, const sjf_bitmap_t* bmp,
                                        uint32_t codepoint, float advance,
                                        int32_t* out_x, int32_t* out_y) {
    if (!atlas || !bmp) return SJF_ERR_NULL_PTR;
    
    // Grow entry array if needed
    if (atlas->entry_count >= atlas->entry_capacity) {
        int32_t new_cap = atlas->entry_capacity * 2;
        sjf_atlas_entry_t* new_entries = (sjf_atlas_entry_t*)realloc(
            atlas->entries, new_cap * sizeof(sjf_atlas_entry_t));
        if (!new_entries) return SJF_ERR_MEMORY;
        atlas->entries = new_entries;
        atlas->entry_capacity = new_cap;
    }
    
    // Find position (simple linear search for existing row packing)
    static thread_local AtlasPacker* packer = nullptr;
    if (!packer || packer->atlas_width != atlas->width) {
        delete packer;
        packer = new AtlasPacker(atlas->width, atlas->height);
    }
    
    int32_t px, py;
    if (!packer->pack(bmp->width, bmp->height, &px, &py)) {
        return SJF_ERR_OVERFLOW; // Atlas full
    }
    
    // Copy bitmap to atlas
    for (int32_t y = 0; y < bmp->height; y++) {
        for (int32_t x = 0; x < bmp->width; x++) {
            int32_t src_idx = y * bmp->stride + x;
            int32_t dst_idx = (py + y) * atlas->width + (px + x);
            atlas->pixels[dst_idx] = bmp->pixels[src_idx];
        }
    }
    
    // Add entry
    sjf_atlas_entry_t* entry = &atlas->entries[atlas->entry_count++];
    entry->codepoint = codepoint;
    entry->x = px;
    entry->y = py;
    entry->width = bmp->width;
    entry->height = bmp->height;
    entry->origin_x = bmp->origin_x;
    entry->origin_y = bmp->origin_y;
    entry->advance = advance;
    
    if (out_x) *out_x = px;
    if (out_y) *out_y = py;
    
    return SJF_OK;
}

extern "C" const sjf_atlas_entry_t* sjf_atlas_find(const sjf_atlas_t* atlas, uint32_t codepoint) {
    if (!atlas) return nullptr;
    
    for (int32_t i = 0; i < atlas->entry_count; i++) {
        if (atlas->entries[i].codepoint == codepoint) {
            return &atlas->entries[i];
        }
    }
    
    return nullptr;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BITMAP TO ARGB CONVERSION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_bitmap_to_argb(const sjf_bitmap_t* bmp, uint32_t color, 
                                       uint32_t* out_argb, int32_t out_stride) {
    if (!bmp || !out_argb) return SJF_ERR_NULL_PTR;
    
    uint8_t r = (color >> 16) & 0xFF;
    uint8_t g = (color >> 8) & 0xFF;
    uint8_t b = color & 0xFF;
    
    for (int32_t y = 0; y < bmp->height; y++) {
        for (int32_t x = 0; x < bmp->width; x++) {
            uint8_t alpha = bmp->pixels[y * bmp->stride + x];
            out_argb[y * out_stride + x] = ((uint32_t)alpha << 24) | ((uint32_t)r << 16) | 
                                           ((uint32_t)g << 8) | (uint32_t)b;
        }
    }
    
    return SJF_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DIRECT RENDERING TO BUFFER
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_render_glyph_to_buffer(const sjf_glyph_t* glyph,
                                               uint32_t* buffer, int32_t buf_width, int32_t buf_height,
                                               int32_t x, int32_t y, int32_t size,
                                               uint32_t color, float em_size) {
    if (!glyph || !buffer) return SJF_ERR_NULL_PTR;
    
    sjf_raster_opts_t opts = { size, 2, 1.0f, 0, 0 };
    sjf_bitmap_t* bmp = sjf_raster_glyph(glyph, &opts, em_size);
    if (!bmp) return SJF_ERR_MEMORY;
    
    // Composite onto buffer
    uint8_t fg_r = (color >> 16) & 0xFF;
    uint8_t fg_g = (color >> 8) & 0xFF;
    uint8_t fg_b = color & 0xFF;
    
    int32_t render_x = x + (int32_t)bmp->origin_x;
    int32_t render_y = y + (int32_t)bmp->origin_y;
    
    for (int32_t by = 0; by < bmp->height; by++) {
        int32_t dst_y = render_y + by;
        if (dst_y < 0 || dst_y >= buf_height) continue;
        
        for (int32_t bx = 0; bx < bmp->width; bx++) {
            int32_t dst_x = render_x + bx;
            if (dst_x < 0 || dst_x >= buf_width) continue;
            
            uint8_t alpha = bmp->pixels[by * bmp->stride + bx];
            if (alpha == 0) continue;
            
            int32_t dst_idx = dst_y * buf_width + dst_x;
            uint32_t bg = buffer[dst_idx];
            
            // Alpha blend
            uint8_t bg_a = (bg >> 24) & 0xFF;
            uint8_t bg_r = (bg >> 16) & 0xFF;
            uint8_t bg_g = (bg >> 8) & 0xFF;
            uint8_t bg_b = bg & 0xFF;
            
            float a = alpha / 255.0f;
            float inv_a = 1.0f - a;
            
            uint8_t out_r = (uint8_t)(fg_r * a + bg_r * inv_a);
            uint8_t out_g = (uint8_t)(fg_g * a + bg_g * inv_a);
            uint8_t out_b = (uint8_t)(fg_b * a + bg_b * inv_a);
            uint8_t out_a = (uint8_t)(alpha + bg_a * inv_a);
            
            buffer[dst_idx] = ((uint32_t)out_a << 24) | ((uint32_t)out_r << 16) | 
                              ((uint32_t)out_g << 8) | (uint32_t)out_b;
        }
    }
    
    sjf_bitmap_free(bmp);
    return SJF_OK;
}
