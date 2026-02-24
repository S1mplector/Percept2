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
#include <algorithm>

/* External declarations from font_strokes.cpp */
extern "C" void sjf_stroke_bounds(const sjf_stroke_t* stroke, 
                                   float* min_x, float* min_y, 
                                   float* max_x, float* max_y);

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH METRICS CALCULATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_glyph_compute_metrics(sjf_glyph_t* glyph, float em_size) {
    if (!glyph) return SJF_ERR_NULL_PTR;
    
    if (glyph->stroke_count == 0) {
        // Empty glyph - set default metrics
        glyph->metrics.advance_width = em_size * 0.3f; // Space width
        glyph->metrics.left_bearing = 0.0f;
        glyph->metrics.right_bearing = 0.0f;
        glyph->metrics.bbox_x = 0.0f;
        glyph->metrics.bbox_y = 0.0f;
        glyph->metrics.bbox_width = 0.0f;
        glyph->metrics.bbox_height = 0.0f;
        return SJF_OK;
    }
    
    // Compute bounding box across all strokes
    float min_x = 1e10f, min_y = 1e10f;
    float max_x = -1e10f, max_y = -1e10f;
    
    for (int32_t s = 0; s < glyph->stroke_count; s++) {
        float sx_min, sy_min, sx_max, sy_max;
        sjf_stroke_bounds(&glyph->strokes[s], &sx_min, &sy_min, &sx_max, &sy_max);
        
        min_x = std::min(min_x, sx_min);
        min_y = std::min(min_y, sy_min);
        max_x = std::max(max_x, sx_max);
        max_y = std::max(max_y, sy_max);
    }
    
    float width = max_x - min_x;
    float height = max_y - min_y;
    
    // Set bounding box
    glyph->metrics.bbox_x = min_x;
    glyph->metrics.bbox_y = min_y;
    glyph->metrics.bbox_width = width;
    glyph->metrics.bbox_height = height;
    
    // Calculate bearings and advance
    float side_bearing = em_size * 0.05f; // 5% of em for side bearing
    
    glyph->metrics.left_bearing = side_bearing;
    glyph->metrics.right_bearing = side_bearing;
    glyph->metrics.advance_width = width + side_bearing * 2.0f;
    
    return SJF_OK;
}

extern "C" int32_t sjf_font_compute_all_metrics(sjf_font_t* font) {
    if (!font) return SJF_ERR_NULL_PTR;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        sjf_glyph_compute_metrics(&font->glyphs[i], font->header.em_size);
    }
    
    return SJF_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH MEASUREMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_glyph_get_advance(const sjf_glyph_t* glyph) {
    return glyph ? glyph->metrics.advance_width : 0.0f;
}

extern "C" float sjf_glyph_get_width(const sjf_glyph_t* glyph) {
    return glyph ? glyph->metrics.bbox_width : 0.0f;
}

extern "C" float sjf_glyph_get_height(const sjf_glyph_t* glyph) {
    return glyph ? glyph->metrics.bbox_height : 0.0f;
}

extern "C" void sjf_glyph_get_bounds(const sjf_glyph_t* glyph,
                                      float* x, float* y, float* w, float* h) {
    if (!glyph) {
        if (x) *x = 0.0f;
        if (y) *y = 0.0f;
        if (w) *w = 0.0f;
        if (h) *h = 0.0f;
        return;
    }
    
    if (x) *x = glyph->metrics.bbox_x;
    if (y) *y = glyph->metrics.bbox_y;
    if (w) *w = glyph->metrics.bbox_width;
    if (h) *h = glyph->metrics.bbox_height;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT MEASUREMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_font_measure_text(const sjf_font_t* font, const char* text, int32_t size) {
    if (!font || !text) return 0.0f;
    
    float scale = (float)size / font->header.em_size;
    float total_width = 0.0f;
    
    const uint8_t* ptr = (const uint8_t*)text;
    while (*ptr) {
        // Simple UTF-8 decoding
        uint32_t codepoint;
        int32_t bytes = 1;
        
        if ((*ptr & 0x80) == 0) {
            codepoint = *ptr;
        } else if ((*ptr & 0xE0) == 0xC0) {
            codepoint = (*ptr & 0x1F) << 6;
            if (ptr[1]) codepoint |= (ptr[1] & 0x3F);
            bytes = 2;
        } else if ((*ptr & 0xF0) == 0xE0) {
            codepoint = (*ptr & 0x0F) << 12;
            if (ptr[1]) codepoint |= (ptr[1] & 0x3F) << 6;
            if (ptr[2]) codepoint |= (ptr[2] & 0x3F);
            bytes = 3;
        } else if ((*ptr & 0xF8) == 0xF0) {
            codepoint = (*ptr & 0x07) << 18;
            if (ptr[1]) codepoint |= (ptr[1] & 0x3F) << 12;
            if (ptr[2]) codepoint |= (ptr[2] & 0x3F) << 6;
            if (ptr[3]) codepoint |= (ptr[3] & 0x3F);
            bytes = 4;
        } else {
            codepoint = '?';
        }
        
        ptr += bytes;
        
        // Find glyph
        const sjf_glyph_t* glyph = nullptr;
        for (int32_t i = 0; i < font->glyph_count; i++) {
            if (font->glyphs[i].codepoint == codepoint) {
                glyph = &font->glyphs[i];
                break;
            }
        }
        
        if (glyph && glyph->defined) {
            total_width += glyph->metrics.advance_width;
        } else {
            // Use default advance for undefined glyphs
            total_width += font->header.em_size * 0.6f;
        }
    }
    
    return total_width * scale;
}

extern "C" float sjf_font_measure_char(const sjf_font_t* font, uint32_t codepoint, int32_t size) {
    if (!font) return 0.0f;
    
    float scale = (float)size / font->header.em_size;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].codepoint == codepoint) {
            return font->glyphs[i].metrics.advance_width * scale;
        }
    }
    
    // Default advance for undefined glyph
    return font->header.em_size * 0.6f * scale;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FONT METRICS
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_font_get_ascender(const sjf_font_t* font, int32_t size) {
    if (!font) return 0.0f;
    return font->header.ascender * (float)size / font->header.em_size;
}

extern "C" float sjf_font_get_descender(const sjf_font_t* font, int32_t size) {
    if (!font) return 0.0f;
    return font->header.descender * (float)size / font->header.em_size;
}

extern "C" float sjf_font_get_line_height(const sjf_font_t* font, int32_t size) {
    if (!font) return 0.0f;
    float scale = (float)size / font->header.em_size;
    return (font->header.ascender + font->header.descender + font->header.line_gap) * scale;
}

extern "C" float sjf_font_get_em_size(const sjf_font_t* font) {
    return font ? font->header.em_size : SJF_DEFAULT_EM_SIZE;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * KERNING (Basic pair kerning)
 * ═══════════════════════════════════════════════════════════════════════════ */

// Simple heuristic kerning based on glyph bounds
extern "C" float sjf_font_get_kerning(const sjf_font_t* font, 
                                       uint32_t left_codepoint, 
                                       uint32_t right_codepoint) {
    if (!font) return 0.0f;
    
    const sjf_glyph_t* left = nullptr;
    const sjf_glyph_t* right = nullptr;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].codepoint == left_codepoint) left = &font->glyphs[i];
        if (font->glyphs[i].codepoint == right_codepoint) right = &font->glyphs[i];
    }
    
    if (!left || !right || !left->defined || !right->defined) {
        return 0.0f;
    }
    
    // Simple kerning: adjust for common pairs
    // This could be extended with a kerning table
    
    // Check if left glyph has right-side whitespace
    float left_right_edge = left->metrics.bbox_x + left->metrics.bbox_width;
    float left_advance = left->metrics.advance_width;
    float left_space = left_advance - left_right_edge - left->metrics.right_bearing;
    
    // Check if right glyph has left-side whitespace
    float right_left_edge = right->metrics.bbox_x;
    float right_space = right_left_edge - right->metrics.left_bearing;
    
    // If both have extra space, tighten slightly
    if (left_space > font->header.em_size * 0.1f && 
        right_space > font->header.em_size * 0.1f) {
        return -font->header.em_size * 0.03f; // Small negative kerning
    }
    
    return 0.0f;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH NORMALIZATION (Fit to em square)
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_glyph_normalize(sjf_glyph_t* glyph, float em_size, float margin) {
    if (!glyph || glyph->stroke_count == 0) return SJF_ERR_NULL_PTR;
    
    // Compute current bounds
    float min_x = 1e10f, min_y = 1e10f;
    float max_x = -1e10f, max_y = -1e10f;
    
    for (int32_t s = 0; s < glyph->stroke_count; s++) {
        for (int32_t p = 0; p < glyph->strokes[s].point_count; p++) {
            float x = glyph->strokes[s].points[p].x;
            float y = glyph->strokes[s].points[p].y;
            min_x = std::min(min_x, x);
            min_y = std::min(min_y, y);
            max_x = std::max(max_x, x);
            max_y = std::max(max_y, y);
        }
    }
    
    float width = max_x - min_x;
    float height = max_y - min_y;
    
    if (width < 0.001f && height < 0.001f) return SJF_OK;
    
    // Calculate target area (with margin)
    float target_size = em_size - margin * 2.0f;
    float scale = std::min(target_size / width, target_size / height);
    
    // Transform all points
    float center_x = (min_x + max_x) * 0.5f;
    float center_y = (min_y + max_y) * 0.5f;
    float target_center = em_size * 0.5f;
    
    for (int32_t s = 0; s < glyph->stroke_count; s++) {
        for (int32_t p = 0; p < glyph->strokes[s].point_count; p++) {
            sjf_point_t* pt = &glyph->strokes[s].points[p];
            pt->x = (pt->x - center_x) * scale + target_center;
            pt->y = (pt->y - center_y) * scale + target_center;
        }
        // Scale thickness too
        glyph->strokes[s].thickness *= scale;
    }
    
    // Recompute metrics
    sjf_glyph_compute_metrics(glyph, em_size);
    
    return SJF_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * AUTO BASELINE DETECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_font_detect_baseline(const sjf_font_t* font) {
    if (!font || font->glyph_count == 0) return SJF_DEFAULT_ASCENDER;
    
    // Find lowercase letters to detect baseline
    float baseline_sum = 0.0f;
    int32_t baseline_count = 0;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        const sjf_glyph_t* g = &font->glyphs[i];
        if (!g->defined) continue;
        
        // Use flat-bottomed lowercase letters
        if (g->codepoint >= 'a' && g->codepoint <= 'z' && 
            g->codepoint != 'g' && g->codepoint != 'j' && 
            g->codepoint != 'p' && g->codepoint != 'q' && g->codepoint != 'y') {
            float bottom = g->metrics.bbox_y + g->metrics.bbox_height;
            baseline_sum += bottom;
            baseline_count++;
        }
    }
    
    if (baseline_count > 0) {
        return baseline_sum / baseline_count;
    }
    
    return font->header.ascender;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * X-HEIGHT AND CAP-HEIGHT DETECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_font_detect_x_height(const sjf_font_t* font) {
    if (!font) return SJF_DEFAULT_EM_SIZE * 0.5f;
    
    // Look for 'x' glyph
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].codepoint == 'x' && font->glyphs[i].defined) {
            return font->glyphs[i].metrics.bbox_height;
        }
    }
    
    // Fallback: average height of lowercase letters
    float height_sum = 0.0f;
    int32_t count = 0;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        const sjf_glyph_t* g = &font->glyphs[i];
        if (g->defined && g->codepoint >= 'a' && g->codepoint <= 'z' &&
            g->codepoint != 'b' && g->codepoint != 'd' && g->codepoint != 'f' &&
            g->codepoint != 'h' && g->codepoint != 'k' && g->codepoint != 'l' &&
            g->codepoint != 't') {
            height_sum += g->metrics.bbox_height;
            count++;
        }
    }
    
    return count > 0 ? height_sum / count : font->header.em_size * 0.5f;
}

extern "C" float sjf_font_detect_cap_height(const sjf_font_t* font) {
    if (!font) return SJF_DEFAULT_EM_SIZE * 0.7f;
    
    // Look for 'H' or 'I' glyph
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if ((font->glyphs[i].codepoint == 'H' || font->glyphs[i].codepoint == 'I') && 
            font->glyphs[i].defined) {
            return font->glyphs[i].metrics.bbox_height;
        }
    }
    
    // Fallback: average height of uppercase letters
    float height_sum = 0.0f;
    int32_t count = 0;
    
    for (int32_t i = 0; i < font->glyph_count; i++) {
        const sjf_glyph_t* g = &font->glyphs[i];
        if (g->defined && g->codepoint >= 'A' && g->codepoint <= 'Z') {
            height_sum += g->metrics.bbox_height;
            count++;
        }
    }
    
    return count > 0 ? height_sum / count : font->header.em_size * 0.7f;
}
