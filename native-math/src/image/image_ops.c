/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file image_ops.c
 * @brief Native Image Operations for Simjot
 * 
 * High-performance image processing:
 * - Fast bilinear/bicubic resize
 * - Memory-efficient scaling
 * - Direct RGBA buffer manipulation
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifdef __APPLE__
extern int32_t simjot_macos_image_scale_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h,
                                             int32_t* dst_argb, int32_t dst_w, int32_t dst_h,
                                             int32_t quality);
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * RGBA PIXEL HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    uint8_t r, g, b, a;
} RGBA;

static inline RGBA get_pixel(const uint8_t* data, int32_t width, int32_t x, int32_t y) {
    const uint8_t* p = data + (y * width + x) * 4;
    return (RGBA){ p[0], p[1], p[2], p[3] };
}

static inline void set_pixel(uint8_t* data, int32_t width, int32_t x, int32_t y, RGBA c) {
    uint8_t* p = data + (y * width + x) * 4;
    p[0] = c.r; p[1] = c.g; p[2] = c.b; p[3] = c.a;
}

static inline uint8_t clamp_u8(int32_t v) {
    return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BILINEAR INTERPOLATION RESIZE
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Fast bilinear resize of RGBA image
 * 
 * @param src Source RGBA data (4 bytes per pixel)
 * @param src_w Source width
 * @param src_h Source height
 * @param dst Destination buffer (must be pre-allocated: dst_w * dst_h * 4 bytes)
 * @param dst_w Target width
 * @param dst_h Target height
 * @return 0 on success, -1 on error
 */
int32_t simjot_image_resize_bilinear(
    const uint8_t* src, int32_t src_w, int32_t src_h,
    uint8_t* dst, int32_t dst_w, int32_t dst_h
) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return -1;
    }
    
    float x_ratio = (float)(src_w - 1) / (float)(dst_w > 1 ? dst_w - 1 : 1);
    float y_ratio = (float)(src_h - 1) / (float)(dst_h > 1 ? dst_h - 1 : 1);
    
    for (int32_t j = 0; j < dst_h; j++) {
        float y_src = j * y_ratio;
        int32_t y0 = (int32_t)y_src;
        int32_t y1 = y0 + 1;
        if (y1 >= src_h) y1 = src_h - 1;
        float y_frac = y_src - y0;
        
        for (int32_t i = 0; i < dst_w; i++) {
            float x_src = i * x_ratio;
            int32_t x0 = (int32_t)x_src;
            int32_t x1 = x0 + 1;
            if (x1 >= src_w) x1 = src_w - 1;
            float x_frac = x_src - x0;
            
            /* Sample 4 neighbors */
            RGBA p00 = get_pixel(src, src_w, x0, y0);
            RGBA p10 = get_pixel(src, src_w, x1, y0);
            RGBA p01 = get_pixel(src, src_w, x0, y1);
            RGBA p11 = get_pixel(src, src_w, x1, y1);
            
            /* Bilinear interpolation */
            float w00 = (1.0f - x_frac) * (1.0f - y_frac);
            float w10 = x_frac * (1.0f - y_frac);
            float w01 = (1.0f - x_frac) * y_frac;
            float w11 = x_frac * y_frac;
            
            RGBA result;
            result.r = clamp_u8((int32_t)(p00.r * w00 + p10.r * w10 + p01.r * w01 + p11.r * w11 + 0.5f));
            result.g = clamp_u8((int32_t)(p00.g * w00 + p10.g * w10 + p01.g * w01 + p11.g * w11 + 0.5f));
            result.b = clamp_u8((int32_t)(p00.b * w00 + p10.b * w10 + p01.b * w01 + p11.b * w11 + 0.5f));
            result.a = clamp_u8((int32_t)(p00.a * w00 + p10.a * w10 + p01.a * w01 + p11.a * w11 + 0.5f));
            
            set_pixel(dst, dst_w, i, j, result);
        }
    }
    
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BICUBIC INTERPOLATION (HIGHER QUALITY)
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float cubic_weight(float t) {
    /* Catmull-Rom spline */
    float at = fabsf(t);
    if (at <= 1.0f) {
        return (1.5f * at - 2.5f) * at * at + 1.0f;
    } else if (at < 2.0f) {
        return ((-0.5f * at + 2.5f) * at - 4.0f) * at + 2.0f;
    }
    return 0.0f;
}

/**
 * @brief High-quality bicubic resize of RGBA image
 */
int32_t simjot_image_resize_bicubic(
    const uint8_t* src, int32_t src_w, int32_t src_h,
    uint8_t* dst, int32_t dst_w, int32_t dst_h
) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return -1;
    }
    
    float x_scale = (float)src_w / (float)dst_w;
    float y_scale = (float)src_h / (float)dst_h;
    
    for (int32_t j = 0; j < dst_h; j++) {
        float y_src = (j + 0.5f) * y_scale - 0.5f;
        int32_t y_int = (int32_t)floorf(y_src);
        float y_frac = y_src - y_int;
        
        for (int32_t i = 0; i < dst_w; i++) {
            float x_src = (i + 0.5f) * x_scale - 0.5f;
            int32_t x_int = (int32_t)floorf(x_src);
            float x_frac = x_src - x_int;
            
            float r = 0, g = 0, b = 0, a = 0;
            float weight_sum = 0;
            
            /* 4x4 kernel */
            for (int32_t m = -1; m <= 2; m++) {
                float wy = cubic_weight(y_frac - m);
                int32_t y = y_int + m;
                if (y < 0) y = 0;
                if (y >= src_h) y = src_h - 1;
                
                for (int32_t n = -1; n <= 2; n++) {
                    float wx = cubic_weight(x_frac - n);
                    int32_t x = x_int + n;
                    if (x < 0) x = 0;
                    if (x >= src_w) x = src_w - 1;
                    
                    float w = wx * wy;
                    RGBA p = get_pixel(src, src_w, x, y);
                    
                    r += p.r * w;
                    g += p.g * w;
                    b += p.b * w;
                    a += p.a * w;
                    weight_sum += w;
                }
            }
            
            if (weight_sum > 0) {
                r /= weight_sum;
                g /= weight_sum;
                b /= weight_sum;
                a /= weight_sum;
            }
            
            RGBA result;
            result.r = clamp_u8((int32_t)(r + 0.5f));
            result.g = clamp_u8((int32_t)(g + 0.5f));
            result.b = clamp_u8((int32_t)(b + 0.5f));
            result.a = clamp_u8((int32_t)(a + 0.5f));
            
            set_pixel(dst, dst_w, i, j, result);
        }
    }
    
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST AREA AVERAGING (FOR DOWNSCALING)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Fast area-average downscale (best for shrinking images)
 */
int32_t simjot_image_resize_area(
    const uint8_t* src, int32_t src_w, int32_t src_h,
    uint8_t* dst, int32_t dst_w, int32_t dst_h
) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return -1;
    }
    
    /* For upscaling, fall back to bilinear */
    if (dst_w >= src_w && dst_h >= src_h) {
        return simjot_image_resize_bilinear(src, src_w, src_h, dst, dst_w, dst_h);
    }
    
    float x_scale = (float)src_w / (float)dst_w;
    float y_scale = (float)src_h / (float)dst_h;
    
    for (int32_t j = 0; j < dst_h; j++) {
        int32_t y0 = (int32_t)(j * y_scale);
        int32_t y1 = (int32_t)((j + 1) * y_scale);
        if (y1 > src_h) y1 = src_h;
        if (y0 >= y1) y1 = y0 + 1;
        
        for (int32_t i = 0; i < dst_w; i++) {
            int32_t x0 = (int32_t)(i * x_scale);
            int32_t x1 = (int32_t)((i + 1) * x_scale);
            if (x1 > src_w) x1 = src_w;
            if (x0 >= x1) x1 = x0 + 1;
            
            uint32_t r = 0, g = 0, b = 0, a = 0;
            int32_t count = 0;
            
            for (int32_t yy = y0; yy < y1; yy++) {
                for (int32_t xx = x0; xx < x1; xx++) {
                    RGBA p = get_pixel(src, src_w, xx, yy);
                    r += p.r;
                    g += p.g;
                    b += p.b;
                    a += p.a;
                    count++;
                }
            }
            
            if (count > 0) {
                RGBA result;
                result.r = (uint8_t)(r / count);
                result.g = (uint8_t)(g / count);
                result.b = (uint8_t)(b / count);
                result.a = (uint8_t)(a / count);
                set_pixel(dst, dst_w, i, j, result);
            }
        }
    }
    
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * AUTO-SELECT BEST RESIZE METHOD
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Automatically select best resize algorithm based on scale factor
 * 
 * @param quality 0=fast/bilinear, 1=high/bicubic, 2=auto
 */
int32_t simjot_image_resize(
    const uint8_t* src, int32_t src_w, int32_t src_h,
    uint8_t* dst, int32_t dst_w, int32_t dst_h,
    int32_t quality
) {
    if (!src || !dst) return -1;
    
    if (quality == 0) {
        /* Fast bilinear */
        return simjot_image_resize_bilinear(src, src_w, src_h, dst, dst_w, dst_h);
    } else if (quality == 1) {
        /* High quality bicubic */
        return simjot_image_resize_bicubic(src, src_w, src_h, dst, dst_w, dst_h);
    } else {
        /* Auto: use area averaging for downscale, bicubic for upscale */
        if (dst_w < src_w || dst_h < src_h) {
            return simjot_image_resize_area(src, src_w, src_h, dst, dst_w, dst_h);
        } else {
            return simjot_image_resize_bicubic(src, src_w, src_h, dst, dst_w, dst_h);
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CALCULATE TARGET SIZE (PRESERVE ASPECT RATIO)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate target dimensions to fit within max bounds while preserving aspect ratio
 * 
 * @param src_w Source width
 * @param src_h Source height
 * @param max_w Maximum target width (0 = no limit)
 * @param max_h Maximum target height (0 = no limit)
 * @param out_w Output: calculated width
 * @param out_h Output: calculated height
 */
void simjot_image_calc_fit_size(
    int32_t src_w, int32_t src_h,
    int32_t max_w, int32_t max_h,
    int32_t* out_w, int32_t* out_h
) {
    if (!out_w || !out_h || src_w <= 0 || src_h <= 0) {
        if (out_w) *out_w = src_w;
        if (out_h) *out_h = src_h;
        return;
    }
    
    float scale = 1.0f;
    
    if (max_w > 0 && src_w > max_w) {
        scale = (float)max_w / (float)src_w;
    }
    
    if (max_h > 0) {
        float h_scale = (float)max_h / (float)src_h;
        if (h_scale < scale) scale = h_scale;
    }
    
    *out_w = (int32_t)(src_w * scale + 0.5f);
    *out_h = (int32_t)(src_h * scale + 0.5f);
    
    if (*out_w < 1) *out_w = 1;
    if (*out_h < 1) *out_h = 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RGBA <-> ARGB CONVERSION (Java BufferedImage compatibility)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Convert ARGB (Java BufferedImage TYPE_INT_ARGB) to RGBA
 */
void simjot_image_argb_to_rgba(const int32_t* argb, uint8_t* rgba, int32_t pixel_count) {
    if (!argb || !rgba || pixel_count <= 0) return;
    
    for (int32_t i = 0; i < pixel_count; i++) {
        int32_t p = argb[i];
        rgba[i * 4 + 0] = (p >> 16) & 0xFF; /* R */
        rgba[i * 4 + 1] = (p >> 8) & 0xFF;  /* G */
        rgba[i * 4 + 2] = p & 0xFF;         /* B */
        rgba[i * 4 + 3] = (p >> 24) & 0xFF; /* A */
    }
}

/**
 * @brief Convert RGBA to ARGB (Java BufferedImage TYPE_INT_ARGB)
 */
void simjot_image_rgba_to_argb(const uint8_t* rgba, int32_t* argb, int32_t pixel_count) {
    if (!rgba || !argb || pixel_count <= 0) return;
    
    for (int32_t i = 0; i < pixel_count; i++) {
        int32_t r = rgba[i * 4 + 0];
        int32_t g = rgba[i * 4 + 1];
        int32_t b = rgba[i * 4 + 2];
        int32_t a = rgba[i * 4 + 3];
        argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * COMBINED RESIZE FOR JAVA (ARGB IN/OUT)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Resize ARGB image (direct Java BufferedImage compatibility)
 * 
 * This is the main entry point for Java. Takes ARGB int array, resizes, returns ARGB.
 * 
 * @param src_argb Source pixels as ARGB int array
 * @param src_w Source width
 * @param src_h Source height  
 * @param dst_argb Destination buffer (must be pre-allocated: dst_w * dst_h ints)
 * @param dst_w Target width
 * @param dst_h Target height
 * @param quality 0=fast, 1=high, 2=auto
 * @return 0 on success, -1 on error
 */
int32_t simjot_image_resize_argb(
    const int32_t* src_argb, int32_t src_w, int32_t src_h,
    int32_t* dst_argb, int32_t dst_w, int32_t dst_h,
    int32_t quality
) {
    if (!src_argb || !dst_argb || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return -1;
    }

#ifdef __APPLE__
    if (simjot_macos_image_scale_argb(src_argb, src_w, src_h, dst_argb, dst_w, dst_h, quality) != 0) {
        return 0;
    }
#endif
    
    /* Allocate temporary RGBA buffers */
    int32_t src_pixels = src_w * src_h;
    int32_t dst_pixels = dst_w * dst_h;
    
    uint8_t* src_rgba = (uint8_t*)malloc(src_pixels * 4);
    uint8_t* dst_rgba = (uint8_t*)malloc(dst_pixels * 4);
    
    if (!src_rgba || !dst_rgba) {
        free(src_rgba);
        free(dst_rgba);
        return -1;
    }
    
    /* Convert ARGB -> RGBA */
    simjot_image_argb_to_rgba(src_argb, src_rgba, src_pixels);
    
    /* Resize */
    int32_t result = simjot_image_resize(src_rgba, src_w, src_h, dst_rgba, dst_w, dst_h, quality);
    
    if (result == 0) {
        /* Convert RGBA -> ARGB */
        simjot_image_rgba_to_argb(dst_rgba, dst_argb, dst_pixels);
    }
    
    free(src_rgba);
    free(dst_rgba);
    
    return result;
}
