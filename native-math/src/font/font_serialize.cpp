/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "font_types.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>

/* ═══════════════════════════════════════════════════════════════════════════
 * FONT MEMORY MANAGEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_font_t* sjf_font_create(const char* name, const char* author) {
    sjf_font_t* font = (sjf_font_t*)calloc(1, sizeof(sjf_font_t));
    if (!font) return nullptr;
    
    // Initialize header
    font->header.magic = SJF_MAGIC;
    font->header.version = SJF_VERSION;
    
    if (name) {
        strncpy(font->header.name, name, SJF_MAX_NAME_LEN - 1);
    }
    if (author) {
        strncpy(font->header.author, author, SJF_MAX_AUTHOR_LEN - 1);
    }
    
    font->header.em_size = SJF_DEFAULT_EM_SIZE;
    font->header.ascender = SJF_DEFAULT_ASCENDER;
    font->header.descender = SJF_DEFAULT_DESCENDER;
    font->header.line_gap = SJF_DEFAULT_LINE_GAP;
    font->header.default_thickness = 2.0f;
    
    uint64_t now = (uint64_t)time(nullptr) * 1000;
    font->header.created_timestamp = now;
    font->header.modified_timestamp = now;
    
    // Allocate glyph array
    font->glyph_capacity = SJF_MAX_GLYPHS;
    font->glyphs = (sjf_glyph_t*)calloc(font->glyph_capacity, sizeof(sjf_glyph_t));
    if (!font->glyphs) {
        free(font);
        return nullptr;
    }
    
    return font;
}

extern "C" void sjf_font_free(sjf_font_t* font) {
    if (!font) return;
    
    // Free all glyphs
    for (int32_t i = 0; i < font->glyph_count; i++) {
        sjf_glyph_t* glyph = &font->glyphs[i];
        for (int32_t s = 0; s < glyph->stroke_count; s++) {
            free(glyph->strokes[s].points);
        }
        free(glyph->strokes);
    }
    
    free(font->glyphs);
    free(font);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH MANAGEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_glyph_t* sjf_font_get_glyph(sjf_font_t* font, uint32_t codepoint) {
    if (!font) return nullptr;
    
    // Search for existing glyph
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].codepoint == codepoint) {
            return &font->glyphs[i];
        }
    }
    
    return nullptr;
}

extern "C" sjf_glyph_t* sjf_font_add_glyph(sjf_font_t* font, uint32_t codepoint) {
    if (!font) return nullptr;
    
    // Check if glyph already exists
    sjf_glyph_t* existing = sjf_font_get_glyph(font, codepoint);
    if (existing) return existing;
    
    // Grow array if needed
    if (font->glyph_count >= font->glyph_capacity) {
        int32_t new_cap = font->glyph_capacity * 2;
        sjf_glyph_t* new_glyphs = (sjf_glyph_t*)realloc(font->glyphs, new_cap * sizeof(sjf_glyph_t));
        if (!new_glyphs) return nullptr;
        font->glyphs = new_glyphs;
        font->glyph_capacity = new_cap;
        memset(&font->glyphs[font->glyph_count], 0, (new_cap - font->glyph_count) * sizeof(sjf_glyph_t));
    }
    
    // Initialize new glyph
    sjf_glyph_t* glyph = &font->glyphs[font->glyph_count++];
    memset(glyph, 0, sizeof(sjf_glyph_t));
    glyph->codepoint = codepoint;
    glyph->stroke_capacity = 8;
    glyph->strokes = (sjf_stroke_t*)calloc(glyph->stroke_capacity, sizeof(sjf_stroke_t));
    
    // Default metrics
    glyph->metrics.advance_width = font->header.em_size * 0.6f;
    glyph->metrics.bbox_width = font->header.em_size * 0.5f;
    glyph->metrics.bbox_height = font->header.em_size * 0.8f;
    
    font->header.glyph_count = font->glyph_count;
    font->header.modified_timestamp = (uint64_t)time(nullptr) * 1000;
    
    return glyph;
}

extern "C" int32_t sjf_glyph_add_stroke(sjf_glyph_t* glyph, const sjf_stroke_t* stroke) {
    if (!glyph || !stroke) return SJF_ERR_NULL_PTR;
    
    // Grow array if needed
    if (glyph->stroke_count >= glyph->stroke_capacity) {
        int32_t new_cap = glyph->stroke_capacity * 2;
        sjf_stroke_t* new_strokes = (sjf_stroke_t*)realloc(glyph->strokes, new_cap * sizeof(sjf_stroke_t));
        if (!new_strokes) return SJF_ERR_MEMORY;
        glyph->strokes = new_strokes;
        glyph->stroke_capacity = new_cap;
    }
    
    // Copy stroke
    sjf_stroke_t* dst = &glyph->strokes[glyph->stroke_count++];
    dst->point_count = stroke->point_count;
    dst->capacity = stroke->point_count;
    dst->thickness = stroke->thickness;
    dst->color = stroke->color;
    
    dst->points = (sjf_point_t*)malloc(stroke->point_count * sizeof(sjf_point_t));
    if (!dst->points) {
        glyph->stroke_count--;
        return SJF_ERR_MEMORY;
    }
    
    memcpy(dst->points, stroke->points, stroke->point_count * sizeof(sjf_point_t));
    glyph->defined = 1;
    
    return SJF_OK;
}

extern "C" void sjf_glyph_clear_strokes(sjf_glyph_t* glyph) {
    if (!glyph) return;
    
    for (int32_t i = 0; i < glyph->stroke_count; i++) {
        free(glyph->strokes[i].points);
    }
    glyph->stroke_count = 0;
    glyph->defined = 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BINARY SERIALIZATION - WRITE
 * ═══════════════════════════════════════════════════════════════════════════ */

static int32_t write_u8(FILE* fp, uint8_t val) {
    return fwrite(&val, 1, 1, fp) == 1 ? 0 : -1;
}

static int32_t write_u16(FILE* fp, uint16_t val) {
    uint8_t buf[2] = { (uint8_t)(val & 0xFF), (uint8_t)((val >> 8) & 0xFF) };
    return fwrite(buf, 1, 2, fp) == 2 ? 0 : -1;
}

static int32_t write_u32(FILE* fp, uint32_t val) {
    uint8_t buf[4];
    for (int i = 0; i < 4; i++) buf[i] = (uint8_t)((val >> (i * 8)) & 0xFF);
    return fwrite(buf, 1, 4, fp) == 4 ? 0 : -1;
}

static int32_t write_u64(FILE* fp, uint64_t val) {
    uint8_t buf[8];
    for (int i = 0; i < 8; i++) buf[i] = (uint8_t)((val >> (i * 8)) & 0xFF);
    return fwrite(buf, 1, 8, fp) == 8 ? 0 : -1;
}

static int32_t write_f32(FILE* fp, float val) {
    return fwrite(&val, sizeof(float), 1, fp) == 1 ? 0 : -1;
}

static int32_t write_str(FILE* fp, const char* str, int32_t max_len) {
    char buf[256] = {0};
    strncpy(buf, str ? str : "", max_len - 1);
    return fwrite(buf, 1, max_len, fp) == (size_t)max_len ? 0 : -1;
}

extern "C" int32_t sjf_font_save(const sjf_font_t* font, const char* path) {
    if (!font || !path) return SJF_ERR_NULL_PTR;
    
    FILE* fp = fopen(path, "wb");
    if (!fp) return SJF_ERR_IO;
    
    // Write header
    if (write_u32(fp, font->header.magic) < 0) goto fail;
    if (write_u32(fp, font->header.version) < 0) goto fail;
    if (write_str(fp, font->header.name, SJF_MAX_NAME_LEN) < 0) goto fail;
    if (write_str(fp, font->header.author, SJF_MAX_AUTHOR_LEN) < 0) goto fail;
    if (write_u32(fp, font->header.glyph_count) < 0) goto fail;
    if (write_u32(fp, font->header.flags) < 0) goto fail;
    if (write_f32(fp, font->header.em_size) < 0) goto fail;
    if (write_f32(fp, font->header.ascender) < 0) goto fail;
    if (write_f32(fp, font->header.descender) < 0) goto fail;
    if (write_f32(fp, font->header.line_gap) < 0) goto fail;
    if (write_f32(fp, font->header.default_thickness) < 0) goto fail;
    if (write_u64(fp, font->header.created_timestamp) < 0) goto fail;
    if (write_u64(fp, font->header.modified_timestamp) < 0) goto fail;
    if (fwrite(font->header.reserved, 1, 64, fp) != 64) goto fail;
    
    // Write glyphs
    for (int32_t g = 0; g < font->glyph_count; g++) {
        const sjf_glyph_t* glyph = &font->glyphs[g];
        
        if (write_u32(fp, glyph->codepoint) < 0) goto fail;
        if (write_u16(fp, (uint16_t)glyph->stroke_count) < 0) goto fail;
        if (write_u8(fp, glyph->defined) < 0) goto fail;
        if (write_u8(fp, 0) < 0) goto fail; // padding
        
        // Write metrics
        if (write_f32(fp, glyph->metrics.advance_width) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.left_bearing) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.right_bearing) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.bbox_x) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.bbox_y) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.bbox_width) < 0) goto fail;
        if (write_f32(fp, glyph->metrics.bbox_height) < 0) goto fail;
        
        // Write strokes
        for (int32_t s = 0; s < glyph->stroke_count; s++) {
            const sjf_stroke_t* stroke = &glyph->strokes[s];
            
            if (write_u16(fp, (uint16_t)stroke->point_count) < 0) goto fail;
            if (write_f32(fp, stroke->thickness) < 0) goto fail;
            if (write_u32(fp, stroke->color) < 0) goto fail;
            
            // Write points
            for (int32_t p = 0; p < stroke->point_count; p++) {
                const sjf_point_t* pt = &stroke->points[p];
                if (write_f32(fp, pt->x) < 0) goto fail;
                if (write_f32(fp, pt->y) < 0) goto fail;
                if (write_f32(fp, pt->pressure) < 0) goto fail;
                if (write_f32(fp, pt->timestamp) < 0) goto fail;
            }
        }
    }
    
    fclose(fp);
    return SJF_OK;
    
fail:
    fclose(fp);
    return SJF_ERR_IO;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BINARY SERIALIZATION - READ
 * ═══════════════════════════════════════════════════════════════════════════ */

static int32_t read_u8(FILE* fp, uint8_t* val) {
    return fread(val, 1, 1, fp) == 1 ? 0 : -1;
}

static int32_t read_u16(FILE* fp, uint16_t* val) {
    uint8_t buf[2];
    if (fread(buf, 1, 2, fp) != 2) return -1;
    *val = (uint16_t)buf[0] | ((uint16_t)buf[1] << 8);
    return 0;
}

static int32_t read_u32(FILE* fp, uint32_t* val) {
    uint8_t buf[4];
    if (fread(buf, 1, 4, fp) != 4) return -1;
    *val = (uint32_t)buf[0] | ((uint32_t)buf[1] << 8) | 
           ((uint32_t)buf[2] << 16) | ((uint32_t)buf[3] << 24);
    return 0;
}

static int32_t read_u64(FILE* fp, uint64_t* val) {
    uint8_t buf[8];
    if (fread(buf, 1, 8, fp) != 8) return -1;
    *val = 0;
    for (int i = 0; i < 8; i++) {
        *val |= ((uint64_t)buf[i] << (i * 8));
    }
    return 0;
}

static int32_t read_f32(FILE* fp, float* val) {
    return fread(val, sizeof(float), 1, fp) == 1 ? 0 : -1;
}

static int32_t read_str(FILE* fp, char* str, int32_t max_len) {
    if (fread(str, 1, max_len, fp) != (size_t)max_len) return -1;
    str[max_len - 1] = '\0';
    return 0;
}

extern "C" sjf_font_t* sjf_font_load(const char* path) {
    if (!path) return nullptr;
    
    FILE* fp = fopen(path, "rb");
    if (!fp) return nullptr;
    
    sjf_font_t* font = (sjf_font_t*)calloc(1, sizeof(sjf_font_t));
    if (!font) {
        fclose(fp);
        return nullptr;
    }
    
    // Read header
    if (read_u32(fp, &font->header.magic) < 0) goto fail;
    if (font->header.magic != SJF_MAGIC) goto fail;
    
    if (read_u32(fp, &font->header.version) < 0) goto fail;
    if (font->header.version > SJF_VERSION) goto fail;
    
    if (read_str(fp, font->header.name, SJF_MAX_NAME_LEN) < 0) goto fail;
    if (read_str(fp, font->header.author, SJF_MAX_AUTHOR_LEN) < 0) goto fail;
    if (read_u32(fp, &font->header.glyph_count) < 0) goto fail;
    if (read_u32(fp, &font->header.flags) < 0) goto fail;
    if (read_f32(fp, &font->header.em_size) < 0) goto fail;
    if (read_f32(fp, &font->header.ascender) < 0) goto fail;
    if (read_f32(fp, &font->header.descender) < 0) goto fail;
    if (read_f32(fp, &font->header.line_gap) < 0) goto fail;
    if (read_f32(fp, &font->header.default_thickness) < 0) goto fail;
    if (read_u64(fp, &font->header.created_timestamp) < 0) goto fail;
    if (read_u64(fp, &font->header.modified_timestamp) < 0) goto fail;
    if (fread(font->header.reserved, 1, 64, fp) != 64) goto fail;
    
    // Allocate glyphs
    font->glyph_capacity = font->header.glyph_count > 0 ? font->header.glyph_count : SJF_MAX_GLYPHS;
    font->glyphs = (sjf_glyph_t*)calloc(font->glyph_capacity, sizeof(sjf_glyph_t));
    if (!font->glyphs) goto fail;
    
    // Read glyphs
    for (uint32_t g = 0; g < font->header.glyph_count; g++) {
        sjf_glyph_t* glyph = &font->glyphs[font->glyph_count++];
        
        if (read_u32(fp, &glyph->codepoint) < 0) goto fail;
        
        uint16_t stroke_count;
        if (read_u16(fp, &stroke_count) < 0) goto fail;
        
        uint8_t defined, padding;
        if (read_u8(fp, &defined) < 0) goto fail;
        if (read_u8(fp, &padding) < 0) goto fail;
        glyph->defined = defined;
        
        // Read metrics
        if (read_f32(fp, &glyph->metrics.advance_width) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.left_bearing) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.right_bearing) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.bbox_x) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.bbox_y) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.bbox_width) < 0) goto fail;
        if (read_f32(fp, &glyph->metrics.bbox_height) < 0) goto fail;
        
        // Allocate strokes
        glyph->stroke_capacity = stroke_count > 0 ? stroke_count : 8;
        glyph->strokes = (sjf_stroke_t*)calloc(glyph->stroke_capacity, sizeof(sjf_stroke_t));
        if (!glyph->strokes) goto fail;
        
        // Read strokes
        for (uint16_t s = 0; s < stroke_count; s++) {
            sjf_stroke_t* stroke = &glyph->strokes[glyph->stroke_count++];
            
            uint16_t point_count;
            if (read_u16(fp, &point_count) < 0) goto fail;
            if (read_f32(fp, &stroke->thickness) < 0) goto fail;
            if (read_u32(fp, &stroke->color) < 0) goto fail;
            
            stroke->capacity = point_count;
            stroke->point_count = point_count;
            stroke->points = (sjf_point_t*)malloc(point_count * sizeof(sjf_point_t));
            if (!stroke->points) goto fail;
            
            // Read points
            for (uint16_t p = 0; p < point_count; p++) {
                sjf_point_t* pt = &stroke->points[p];
                if (read_f32(fp, &pt->x) < 0) goto fail;
                if (read_f32(fp, &pt->y) < 0) goto fail;
                if (read_f32(fp, &pt->pressure) < 0) goto fail;
                if (read_f32(fp, &pt->timestamp) < 0) goto fail;
            }
        }
    }
    
    fclose(fp);
    return font;
    
fail:
    sjf_font_free(font);
    fclose(fp);
    return nullptr;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * MEMORY BUFFER SERIALIZATION (for FFM)
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_font_pack(const sjf_font_t* font, uint8_t* buffer, int32_t buffer_len) {
    if (!font) return SJF_ERR_NULL_PTR;
    
    // Calculate required size
    int32_t size = sizeof(sjf_header_t);
    
    for (int32_t g = 0; g < font->glyph_count; g++) {
        const sjf_glyph_t* glyph = &font->glyphs[g];
        size += 4 + 2 + 2 + sizeof(sjf_metrics_t); // codepoint, stroke_count, flags, metrics
        
        for (int32_t s = 0; s < glyph->stroke_count; s++) {
            const sjf_stroke_t* stroke = &glyph->strokes[s];
            size += 2 + 4 + 4; // point_count, thickness, color
            size += stroke->point_count * sizeof(sjf_point_t);
        }
    }
    
    if (!buffer) return size; // Return required size
    if (buffer_len < size) return SJF_ERR_BUFFER_TOO_SMALL;
    
    uint8_t* ptr = buffer;
    
    // Write header
    memcpy(ptr, &font->header, sizeof(sjf_header_t));
    ptr += sizeof(sjf_header_t);
    
    // Write glyphs
    for (int32_t g = 0; g < font->glyph_count; g++) {
        const sjf_glyph_t* glyph = &font->glyphs[g];
        
        memcpy(ptr, &glyph->codepoint, 4); ptr += 4;
        uint16_t sc = (uint16_t)glyph->stroke_count;
        memcpy(ptr, &sc, 2); ptr += 2;
        uint16_t flags = glyph->defined ? 1 : 0;
        memcpy(ptr, &flags, 2); ptr += 2;
        memcpy(ptr, &glyph->metrics, sizeof(sjf_metrics_t)); ptr += sizeof(sjf_metrics_t);
        
        for (int32_t s = 0; s < glyph->stroke_count; s++) {
            const sjf_stroke_t* stroke = &glyph->strokes[s];
            
            uint16_t pc = (uint16_t)stroke->point_count;
            memcpy(ptr, &pc, 2); ptr += 2;
            memcpy(ptr, &stroke->thickness, 4); ptr += 4;
            memcpy(ptr, &stroke->color, 4); ptr += 4;
            
            memcpy(ptr, stroke->points, stroke->point_count * sizeof(sjf_point_t));
            ptr += stroke->point_count * sizeof(sjf_point_t);
        }
    }
    
    return (int32_t)(ptr - buffer);
}

extern "C" sjf_font_t* sjf_font_unpack(const uint8_t* buffer, int32_t buffer_len) {
    if (!buffer || buffer_len < (int32_t)sizeof(sjf_header_t)) return nullptr;
    
    sjf_font_t* font = (sjf_font_t*)calloc(1, sizeof(sjf_font_t));
    if (!font) return nullptr;
    
    const uint8_t* ptr = buffer;
    const uint8_t* end = buffer + buffer_len;
    
    // Helper macro for safe bounds checking
    #define CHECK_BOUNDS(needed) do { if (ptr + (needed) > end) goto fail; } while(0)
    
    // Read header
    CHECK_BOUNDS(sizeof(sjf_header_t));
    memcpy(&font->header, ptr, sizeof(sjf_header_t));
    ptr += sizeof(sjf_header_t);
    
    if (font->header.magic != SJF_MAGIC) {
        free(font);
        return nullptr;
    }
    
    // Validate glyph count
    if (font->header.glyph_count > SJF_MAX_GLYPHS) {
        free(font);
        return nullptr;
    }
    
    // Allocate glyphs
    font->glyph_capacity = font->header.glyph_count > 0 ? font->header.glyph_count : SJF_MAX_GLYPHS;
    font->glyphs = (sjf_glyph_t*)calloc(font->glyph_capacity, sizeof(sjf_glyph_t));
    if (!font->glyphs) {
        free(font);
        return nullptr;
    }
    
    // Read glyphs
    for (uint32_t g = 0; g < font->header.glyph_count; g++) {
        // Check minimum glyph header size: codepoint(4) + stroke_count(2) + flags(2) + metrics
        CHECK_BOUNDS(4 + 2 + 2 + sizeof(sjf_metrics_t));
        
        sjf_glyph_t* glyph = &font->glyphs[font->glyph_count++];
        
        memcpy(&glyph->codepoint, ptr, 4); ptr += 4;
        uint16_t stroke_count;
        memcpy(&stroke_count, ptr, 2); ptr += 2;
        uint16_t flags;
        memcpy(&flags, ptr, 2); ptr += 2;
        glyph->defined = (flags & 1) ? 1 : 0;
        memcpy(&glyph->metrics, ptr, sizeof(sjf_metrics_t)); ptr += sizeof(sjf_metrics_t);
        
        // Validate stroke count
        if (stroke_count > SJF_MAX_STROKES) {
            stroke_count = SJF_MAX_STROKES; // Clamp to max
        }
        
        glyph->stroke_capacity = stroke_count > 0 ? stroke_count : 8;
        glyph->strokes = (sjf_stroke_t*)calloc(glyph->stroke_capacity, sizeof(sjf_stroke_t));
        if (!glyph->strokes) goto fail;
        
        for (uint16_t s = 0; s < stroke_count; s++) {
            // Check stroke header size: point_count(2) + thickness(4) + color(4)
            CHECK_BOUNDS(2 + 4 + 4);
            
            sjf_stroke_t* stroke = &glyph->strokes[glyph->stroke_count++];
            
            uint16_t point_count;
            memcpy(&point_count, ptr, 2); ptr += 2;
            memcpy(&stroke->thickness, ptr, 4); ptr += 4;
            memcpy(&stroke->color, ptr, 4); ptr += 4;
            
            // Validate point count
            if (point_count > SJF_MAX_POINTS) {
                point_count = SJF_MAX_POINTS; // Clamp to max
            }
            
            // Check if we have enough data for all points
            size_t points_size = (size_t)point_count * sizeof(sjf_point_t);
            CHECK_BOUNDS(points_size);
            
            stroke->capacity = point_count;
            stroke->point_count = point_count;
            if (point_count > 0) {
                stroke->points = (sjf_point_t*)malloc(points_size);
                if (!stroke->points) goto fail;
                memcpy(stroke->points, ptr, points_size);
                ptr += points_size;
            } else {
                stroke->points = nullptr;
            }
        }
    }
    
    #undef CHECK_BOUNDS
    
    return font;
    
fail:
    #undef CHECK_BOUNDS
    sjf_font_free(font);
    return nullptr;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FONT METADATA
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t sjf_font_get_name(const sjf_font_t* font, char* out, int32_t out_len) {
    if (!font || !out || out_len < 1) return SJF_ERR_NULL_PTR;
    strncpy(out, font->header.name, out_len - 1);
    out[out_len - 1] = '\0';
    return (int32_t)strlen(out);
}

extern "C" int32_t sjf_font_set_name(sjf_font_t* font, const char* name) {
    if (!font || !name) return SJF_ERR_NULL_PTR;
    strncpy(font->header.name, name, SJF_MAX_NAME_LEN - 1);
    font->header.name[SJF_MAX_NAME_LEN - 1] = '\0';
    font->header.modified_timestamp = (uint64_t)time(nullptr) * 1000;
    return SJF_OK;
}

extern "C" int32_t sjf_font_get_author(const sjf_font_t* font, char* out, int32_t out_len) {
    if (!font || !out || out_len < 1) return SJF_ERR_NULL_PTR;
    strncpy(out, font->header.author, out_len - 1);
    out[out_len - 1] = '\0';
    return (int32_t)strlen(out);
}

extern "C" int32_t sjf_font_set_author(sjf_font_t* font, const char* author) {
    if (!font || !author) return SJF_ERR_NULL_PTR;
    strncpy(font->header.author, author, SJF_MAX_AUTHOR_LEN - 1);
    font->header.author[SJF_MAX_AUTHOR_LEN - 1] = '\0';
    font->header.modified_timestamp = (uint64_t)time(nullptr) * 1000;
    return SJF_OK;
}

extern "C" int32_t sjf_font_glyph_count(const sjf_font_t* font) {
    return font ? font->glyph_count : 0;
}

extern "C" int32_t sjf_font_defined_glyph_count(const sjf_font_t* font) {
    if (!font) return 0;
    int32_t count = 0;
    for (int32_t i = 0; i < font->glyph_count; i++) {
        if (font->glyphs[i].defined) count++;
    }
    return count;
}
