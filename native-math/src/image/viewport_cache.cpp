/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file viewport_cache.cpp
 * @brief Native Viewport Image Cache for Efficient Scrolling
 * 
 * High-performance image caching optimized for scroll performance:
 * - LRU cache with configurable size and memory limits
 * - Viewport culling to skip offscreen images
 * - Fast blit operations for compositing
 * - Pre-scaling support for embedded images
 * 
 * This module is designed to be called from Java via Panama FFM
 * and provides efficient image caching to reduce stuttering when
 * scrolling through documents with embedded images.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <mutex>

/* ═══════════════════════════════════════════════════════════════════════════
 * VIEWPORT CACHE STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

struct ImgCacheEntry {
    int64_t image_id;
    uint32_t* pixels;
    int32_t width;
    int32_t height;
    uint64_t last_access;
    bool valid;
};

static ImgCacheEntry* g_img_cache = nullptr;
static int32_t g_max_entries = 0;
static int64_t g_max_memory_bytes = 0;
static int64_t g_current_memory = 0;
static uint64_t g_access_counter = 0;
static int64_t g_cache_hits = 0;
static int64_t g_cache_misses = 0;
static std::mutex g_cache_mutex;

/* ═══════════════════════════════════════════════════════════════════════════
 * INTERNAL HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline int64_t calc_entry_memory(int32_t width, int32_t height) {
    return static_cast<int64_t>(width) * height * sizeof(uint32_t);
}

static ImgCacheEntry* find_entry(int64_t image_id) {
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (g_img_cache[i].valid && g_img_cache[i].image_id == image_id) {
            g_img_cache[i].last_access = ++g_access_counter;
            return &g_img_cache[i];
        }
    }
    return nullptr;
}

static ImgCacheEntry* find_or_evict_slot() {
    int32_t lru_idx = -1;
    uint64_t min_access = UINT64_MAX;
    
    /* First try to find an empty slot */
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (!g_img_cache[i].valid) {
            return &g_img_cache[i];
        }
        if (g_img_cache[i].last_access < min_access) {
            min_access = g_img_cache[i].last_access;
            lru_idx = i;
        }
    }
    
    /* Evict LRU entry */
    if (lru_idx >= 0) {
        ImgCacheEntry* entry = &g_img_cache[lru_idx];
        if (entry->pixels) {
            g_current_memory -= calc_entry_memory(entry->width, entry->height);
            free(entry->pixels);
            entry->pixels = nullptr;
        }
        entry->valid = false;
        return entry;
    }
    
    return nullptr;
}

static void evict_for_memory(int64_t needed_bytes) {
    while (g_current_memory + needed_bytes > g_max_memory_bytes) {
        /* Find LRU entry */
        int32_t lru_idx = -1;
        uint64_t min_access = UINT64_MAX;
        
        for (int32_t i = 0; i < g_max_entries; i++) {
            if (g_img_cache[i].valid && g_img_cache[i].last_access < min_access) {
                min_access = g_img_cache[i].last_access;
                lru_idx = i;
            }
        }
        
        if (lru_idx < 0) break;
        
        ImgCacheEntry* entry = &g_img_cache[lru_idx];
        if (entry->pixels) {
            g_current_memory -= calc_entry_memory(entry->width, entry->height);
            free(entry->pixels);
            entry->pixels = nullptr;
        }
        entry->valid = false;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - LIFECYCLE
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

int32_t simjot_imgcache_init(int32_t max_entries, int32_t max_memory_mb) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (g_img_cache) {
        /* Already initialized */
        return 0;
    }
    
    if (max_entries <= 0 || max_memory_mb <= 0) {
        return 0;
    }
    
    g_img_cache = static_cast<ImgCacheEntry*>(calloc(max_entries, sizeof(ImgCacheEntry)));
    if (!g_img_cache) {
        return 0;
    }
    
    g_max_entries = max_entries;
    g_max_memory_bytes = static_cast<int64_t>(max_memory_mb) * 1024 * 1024;
    g_current_memory = 0;
    g_access_counter = 0;
    g_cache_hits = 0;
    g_cache_misses = 0;
    
    return 1;
}

void simjot_imgcache_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache) return;
    
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (g_img_cache[i].pixels) {
            free(g_img_cache[i].pixels);
        }
    }
    
    free(g_img_cache);
    g_img_cache = nullptr;
    g_max_entries = 0;
    g_max_memory_bytes = 0;
    g_current_memory = 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - CACHE OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_imgcache_put(int64_t image_id, const uint32_t* pixels, 
                            int32_t width, int32_t height) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache || !pixels || width <= 0 || height <= 0) {
        return 0;
    }
    
    int64_t needed = calc_entry_memory(width, height);
    
    /* Check if single image exceeds max memory */
    if (needed > g_max_memory_bytes) {
        return 0;
    }
    
    /* Check if already cached */
    ImgCacheEntry* existing = find_entry(image_id);
    if (existing) {
        /* Update existing entry */
        if (existing->width != width || existing->height != height) {
            g_current_memory -= calc_entry_memory(existing->width, existing->height);
            free(existing->pixels);
            existing->pixels = static_cast<uint32_t*>(malloc(needed));
            if (!existing->pixels) {
                existing->valid = false;
                return 0;
            }
            g_current_memory += needed;
        }
        existing->width = width;
        existing->height = height;
        memcpy(existing->pixels, pixels, needed);
        existing->last_access = ++g_access_counter;
        return 1;
    }
    
    /* Evict if needed for memory */
    evict_for_memory(needed);
    
    /* Find slot */
    ImgCacheEntry* slot = find_or_evict_slot();
    if (!slot) {
        return 0;
    }
    
    /* Allocate and copy */
    slot->pixels = static_cast<uint32_t*>(malloc(needed));
    if (!slot->pixels) {
        return 0;
    }
    
    memcpy(slot->pixels, pixels, needed);
    slot->image_id = image_id;
    slot->width = width;
    slot->height = height;
    slot->last_access = ++g_access_counter;
    slot->valid = true;
    g_current_memory += needed;
    
    return 1;
}

const uint32_t* simjot_imgcache_get(int64_t image_id, 
                                     int32_t* out_width, int32_t* out_height) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache) {
        g_cache_misses++;
        return nullptr;
    }
    
    ImgCacheEntry* entry = find_entry(image_id);
    if (entry) {
        g_cache_hits++;
        if (out_width) *out_width = entry->width;
        if (out_height) *out_height = entry->height;
        return entry->pixels;
    }
    
    g_cache_misses++;
    return nullptr;
}

int32_t simjot_imgcache_contains(int64_t image_id) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache) return 0;
    
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (g_img_cache[i].valid && g_img_cache[i].image_id == image_id) {
            return 1;
        }
    }
    return 0;
}

int32_t simjot_imgcache_remove(int64_t image_id) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache) return 0;
    
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (g_img_cache[i].valid && g_img_cache[i].image_id == image_id) {
            if (g_img_cache[i].pixels) {
                g_current_memory -= calc_entry_memory(g_img_cache[i].width, g_img_cache[i].height);
                free(g_img_cache[i].pixels);
                g_img_cache[i].pixels = nullptr;
            }
            g_img_cache[i].valid = false;
            return 1;
        }
    }
    return 0;
}

void simjot_imgcache_clear(void) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache) return;
    
    for (int32_t i = 0; i < g_max_entries; i++) {
        if (g_img_cache[i].pixels) {
            free(g_img_cache[i].pixels);
            g_img_cache[i].pixels = nullptr;
        }
        g_img_cache[i].valid = false;
    }
    g_current_memory = 0;
}

void simjot_imgcache_stats(int32_t* out_count, int64_t* out_memory_bytes,
                           int64_t* out_hits, int64_t* out_misses) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    int32_t count = 0;
    if (g_img_cache) {
        for (int32_t i = 0; i < g_max_entries; i++) {
            if (g_img_cache[i].valid) count++;
        }
    }
    
    if (out_count) *out_count = count;
    if (out_memory_bytes) *out_memory_bytes = g_current_memory;
    if (out_hits) *out_hits = g_cache_hits;
    if (out_misses) *out_misses = g_cache_misses;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - VIEWPORT OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_imgcache_cull_viewport(const int64_t* image_ids,
                                       const int32_t* image_y_positions,
                                       const int32_t* image_heights,
                                       int32_t count,
                                       int32_t viewport_y,
                                       int32_t viewport_height,
                                       int32_t* out_visible) {
    if (!image_ids || !image_y_positions || !image_heights || !out_visible || count <= 0) {
        return 0;
    }
    
    int32_t viewport_bottom = viewport_y + viewport_height;
    int32_t visible_count = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t img_top = image_y_positions[i];
        int32_t img_bottom = img_top + image_heights[i];
        
        /* Check if image overlaps viewport */
        bool visible = (img_bottom > viewport_y) && (img_top < viewport_bottom);
        out_visible[i] = visible ? 1 : 0;
        if (visible) visible_count++;
    }
    
    return visible_count;
}

int32_t simjot_imgcache_blit(int64_t image_id,
                              uint32_t* dst_pixels, int32_t dst_width, int32_t dst_height,
                              int32_t dst_x, int32_t dst_y,
                              int32_t clip_y, int32_t clip_height) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    if (!g_img_cache || !dst_pixels || dst_width <= 0 || dst_height <= 0) {
        return 0;
    }
    
    ImgCacheEntry* entry = find_entry(image_id);
    if (!entry || !entry->pixels) {
        return 0;
    }
    
    const uint32_t* src = entry->pixels;
    int32_t src_w = entry->width;
    int32_t src_h = entry->height;
    
    /* Calculate visible region */
    int32_t clip_bottom = clip_y + clip_height;
    
    /* Source and dest bounds */
    int32_t src_start_y = 0;
    int32_t src_end_y = src_h;
    int32_t out_start_y = dst_y;
    
    /* Clip top */
    if (out_start_y < clip_y) {
        src_start_y = clip_y - out_start_y;
        out_start_y = clip_y;
    }
    
    /* Clip bottom */
    int32_t out_end_y = dst_y + src_h;
    if (out_end_y > clip_bottom) {
        src_end_y = src_h - (out_end_y - clip_bottom);
        out_end_y = clip_bottom;
    }
    
    /* Ensure within destination bounds */
    if (out_start_y >= dst_height || out_end_y <= 0) {
        return 1; /* Nothing to draw, but not an error */
    }
    
    /* Horizontal bounds */
    int32_t src_start_x = 0;
    int32_t src_end_x = src_w;
    int32_t out_start_x = dst_x;
    
    if (out_start_x < 0) {
        src_start_x = -out_start_x;
        out_start_x = 0;
    }
    
    int32_t out_end_x = dst_x + src_w;
    if (out_end_x > dst_width) {
        src_end_x = src_w - (out_end_x - dst_width);
    }
    
    if (src_start_x >= src_end_x || src_start_y >= src_end_y) {
        return 1;
    }
    
    /* Perform the blit with alpha blending */
    int32_t copy_width = src_end_x - src_start_x;
    
    for (int32_t sy = src_start_y, dy = out_start_y; sy < src_end_y && dy < out_end_y; sy++, dy++) {
        const uint32_t* src_row = src + sy * src_w + src_start_x;
        uint32_t* dst_row = dst_pixels + dy * dst_width + out_start_x;
        
        for (int32_t x = 0; x < copy_width; x++) {
            uint32_t sp = src_row[x];
            uint32_t sa = (sp >> 24) & 0xFF;
            
            if (sa == 255) {
                /* Fully opaque - direct copy */
                dst_row[x] = sp;
            } else if (sa > 0) {
                /* Alpha blend */
                uint32_t dp = dst_row[x];
                uint32_t da = 255 - sa;
                
                uint32_t r = (((sp >> 16) & 0xFF) * sa + ((dp >> 16) & 0xFF) * da) / 255;
                uint32_t g = (((sp >> 8) & 0xFF) * sa + ((dp >> 8) & 0xFF) * da) / 255;
                uint32_t b = ((sp & 0xFF) * sa + (dp & 0xFF) * da) / 255;
                
                dst_row[x] = (255 << 24) | (r << 16) | (g << 8) | b;
            }
            /* sa == 0: fully transparent, skip */
        }
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - PRE-SCALING
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_imgcache_prescale(int64_t image_id,
                                  const uint32_t* src_pixels, int32_t src_width, int32_t src_height,
                                  int32_t target_width, int32_t quality) {
    if (!src_pixels || src_width <= 0 || src_height <= 0 || target_width <= 0) {
        return 0;
    }
    
    /* Calculate target height maintaining aspect ratio */
    int32_t target_height = (int32_t)(((int64_t)src_height * target_width) / src_width);
    if (target_height <= 0) target_height = 1;
    
    int64_t needed = calc_entry_memory(target_width, target_height);
    uint32_t* scaled = static_cast<uint32_t*>(malloc(needed));
    if (!scaled) {
        return 0;
    }
    
    /* Perform scaling based on quality */
    if (quality == 0) {
        /* Fast: nearest neighbor */
        float x_ratio = static_cast<float>(src_width) / target_width;
        float y_ratio = static_cast<float>(src_height) / target_height;
        
        for (int32_t y = 0; y < target_height; y++) {
            int32_t sy = static_cast<int32_t>(y * y_ratio);
            if (sy >= src_height) sy = src_height - 1;
            
            for (int32_t x = 0; x < target_width; x++) {
                int32_t sx = static_cast<int32_t>(x * x_ratio);
                if (sx >= src_width) sx = src_width - 1;
                scaled[y * target_width + x] = src_pixels[sy * src_width + sx];
            }
        }
    } else {
        /* Balanced/Best: bilinear interpolation */
        float x_ratio = (src_width > 1) ? static_cast<float>(src_width - 1) / (target_width - 1 > 0 ? target_width - 1 : 1) : 0;
        float y_ratio = (src_height > 1) ? static_cast<float>(src_height - 1) / (target_height - 1 > 0 ? target_height - 1 : 1) : 0;
        
        for (int32_t j = 0; j < target_height; j++) {
            float y_src = j * y_ratio;
            int32_t y0 = static_cast<int32_t>(y_src);
            int32_t y1 = y0 + 1;
            if (y1 >= src_height) y1 = src_height - 1;
            float y_frac = y_src - y0;
            
            for (int32_t i = 0; i < target_width; i++) {
                float x_src = i * x_ratio;
                int32_t x0 = static_cast<int32_t>(x_src);
                int32_t x1 = x0 + 1;
                if (x1 >= src_width) x1 = src_width - 1;
                float x_frac = x_src - x0;
                
                /* Sample 4 neighbors */
                uint32_t p00 = src_pixels[y0 * src_width + x0];
                uint32_t p10 = src_pixels[y0 * src_width + x1];
                uint32_t p01 = src_pixels[y1 * src_width + x0];
                uint32_t p11 = src_pixels[y1 * src_width + x1];
                
                /* Bilinear weights */
                float w00 = (1.0f - x_frac) * (1.0f - y_frac);
                float w10 = x_frac * (1.0f - y_frac);
                float w01 = (1.0f - x_frac) * y_frac;
                float w11 = x_frac * y_frac;
                
                /* Interpolate each channel */
                uint32_t a = static_cast<uint32_t>(
                    ((p00 >> 24) & 0xFF) * w00 +
                    ((p10 >> 24) & 0xFF) * w10 +
                    ((p01 >> 24) & 0xFF) * w01 +
                    ((p11 >> 24) & 0xFF) * w11 + 0.5f
                );
                uint32_t r = static_cast<uint32_t>(
                    ((p00 >> 16) & 0xFF) * w00 +
                    ((p10 >> 16) & 0xFF) * w10 +
                    ((p01 >> 16) & 0xFF) * w01 +
                    ((p11 >> 16) & 0xFF) * w11 + 0.5f
                );
                uint32_t g = static_cast<uint32_t>(
                    ((p00 >> 8) & 0xFF) * w00 +
                    ((p10 >> 8) & 0xFF) * w10 +
                    ((p01 >> 8) & 0xFF) * w01 +
                    ((p11 >> 8) & 0xFF) * w11 + 0.5f
                );
                uint32_t b = static_cast<uint32_t>(
                    (p00 & 0xFF) * w00 +
                    (p10 & 0xFF) * w10 +
                    (p01 & 0xFF) * w01 +
                    (p11 & 0xFF) * w11 + 0.5f
                );
                
                scaled[j * target_width + i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }
    
    /* Store in cache */
    int32_t result = simjot_imgcache_put(image_id, scaled, target_width, target_height);
    free(scaled);
    
    return result;
}

} /* extern "C" */
