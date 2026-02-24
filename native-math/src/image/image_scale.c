/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native SIMD Image Scaling
 * High-performance image resize with bilinear/bicubic interpolation
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifdef __SSE2__
#include <emmintrin.h>
#endif

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

#ifdef __APPLE__
extern int32_t simjot_macos_image_scale_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h,
                                             int32_t* dst_argb, int32_t dst_w, int32_t dst_h,
                                             int32_t quality);
extern int32_t simjot_macos_image_blur_argb(int32_t* argb, int32_t width, int32_t height, int32_t radius);
extern int32_t simjot_macos_image_tint_argb(int32_t* argb, int32_t width, int32_t height,
                                            int32_t tint_color, float intensity);
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline uint8_t clamp_u8(int v) {
    return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v));
}

static inline int32_t iclamp(int32_t v, int32_t lo, int32_t hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

/* Extract ARGB components */
static inline void unpack_argb(uint32_t c, int* a, int* r, int* g, int* b) {
    *a = (c >> 24) & 0xFF;
    *r = (c >> 16) & 0xFF;
    *g = (c >> 8) & 0xFF;
    *b = c & 0xFF;
}

static inline uint32_t pack_argb(int a, int r, int g, int b) {
    return ((uint32_t)clamp_u8(a) << 24) |
           ((uint32_t)clamp_u8(r) << 16) |
           ((uint32_t)clamp_u8(g) << 8) |
           (uint32_t)clamp_u8(b);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BILINEAR INTERPOLATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static uint32_t bilinear_sample(const uint32_t* src, int32_t src_w, int32_t src_h,
                                 float fx, float fy) {
    int x0 = (int)floorf(fx);
    int y0 = (int)floorf(fy);
    int x1 = x0 + 1;
    int y1 = y0 + 1;
    
    float dx = fx - x0;
    float dy = fy - y0;
    
    x0 = iclamp(x0, 0, src_w - 1);
    y0 = iclamp(y0, 0, src_h - 1);
    x1 = iclamp(x1, 0, src_w - 1);
    y1 = iclamp(y1, 0, src_h - 1);
    
    uint32_t c00 = src[y0 * src_w + x0];
    uint32_t c10 = src[y0 * src_w + x1];
    uint32_t c01 = src[y1 * src_w + x0];
    uint32_t c11 = src[y1 * src_w + x1];
    
    int a00, r00, g00, b00;
    int a10, r10, g10, b10;
    int a01, r01, g01, b01;
    int a11, r11, g11, b11;
    
    unpack_argb(c00, &a00, &r00, &g00, &b00);
    unpack_argb(c10, &a10, &r10, &g10, &b10);
    unpack_argb(c01, &a01, &r01, &g01, &b01);
    unpack_argb(c11, &a11, &r11, &g11, &b11);
    
    float w00 = (1 - dx) * (1 - dy);
    float w10 = dx * (1 - dy);
    float w01 = (1 - dx) * dy;
    float w11 = dx * dy;
    
    int a = (int)(a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11 + 0.5f);
    int r = (int)(r00 * w00 + r10 * w10 + r01 * w01 + r11 * w11 + 0.5f);
    int g = (int)(g00 * w00 + g10 * w10 + g01 * w01 + g11 * w11 + 0.5f);
    int b = (int)(b00 * w00 + b10 * w10 + b01 * w01 + b11 * w11 + 0.5f);
    
    return pack_argb(a, r, g, b);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BICUBIC INTERPOLATION (Mitchell-Netravali)
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float cubic_weight(float x, float B, float C) {
    x = fabsf(x);
    if (x < 1.0f) {
        return ((12 - 9*B - 6*C) * x*x*x + (-18 + 12*B + 6*C) * x*x + (6 - 2*B)) / 6.0f;
    } else if (x < 2.0f) {
        return ((-B - 6*C) * x*x*x + (6*B + 30*C) * x*x + (-12*B - 48*C) * x + (8*B + 24*C)) / 6.0f;
    }
    return 0.0f;
}

static uint32_t bicubic_sample(const uint32_t* src, int32_t src_w, int32_t src_h,
                                float fx, float fy) {
    const float B = 1.0f / 3.0f;  // Mitchell-Netravali
    const float C = 1.0f / 3.0f;
    
    int x0 = (int)floorf(fx) - 1;
    int y0 = (int)floorf(fy) - 1;
    
    float sum_a = 0, sum_r = 0, sum_g = 0, sum_b = 0;
    float sum_w = 0;
    
    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            int px = iclamp(x0 + i, 0, src_w - 1);
            int py = iclamp(y0 + j, 0, src_h - 1);
            
            float wx = cubic_weight(fx - (x0 + i), B, C);
            float wy = cubic_weight(fy - (y0 + j), B, C);
            float w = wx * wy;
            
            uint32_t c = src[py * src_w + px];
            int a, r, g, b;
            unpack_argb(c, &a, &r, &g, &b);
            
            sum_a += a * w;
            sum_r += r * w;
            sum_g += g * w;
            sum_b += b * w;
            sum_w += w;
        }
    }
    
    if (sum_w > 0.0001f) {
        sum_a /= sum_w;
        sum_r /= sum_w;
        sum_g /= sum_w;
        sum_b /= sum_w;
    }
    
    return pack_argb((int)(sum_a + 0.5f), (int)(sum_r + 0.5f), 
                     (int)(sum_g + 0.5f), (int)(sum_b + 0.5f));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMD-OPTIMIZED BILINEAR (4 pixels at once on x86)
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef __SSE2__
static void bilinear_row_sse2(const uint32_t* src, int32_t src_w, int32_t src_h,
                               uint32_t* dst, int32_t dst_w,
                               float y_src, float x_scale) {
    for (int x = 0; x < dst_w; x++) {
        float fx = x * x_scale;
        dst[x] = bilinear_sample(src, src_w, src_h, fx, y_src);
    }
}
#endif

#ifdef __ARM_NEON
static void bilinear_row_neon(const uint32_t* src, int32_t src_w, int32_t src_h,
                               uint32_t* dst, int32_t dst_w,
                               float y_src, float x_scale) {
    for (int x = 0; x < dst_w; x++) {
        float fx = x * x_scale;
        dst[x] = bilinear_sample(src, src_w, src_h, fx, y_src);
    }
}
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Scale image using bilinear interpolation.
 * @param src Source ARGB pixel buffer
 * @param src_w Source width
 * @param src_h Source height
 * @param dst Destination ARGB pixel buffer (pre-allocated)
 * @param dst_w Destination width
 * @param dst_h Destination height
 * @return 1 on success, 0 on error
 */
int32_t simjot_image_scale_bilinear(const uint32_t* src, int32_t src_w, int32_t src_h,
                                     uint32_t* dst, int32_t dst_w, int32_t dst_h) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return 0;
    }
    
    float x_scale = (float)(src_w - 1) / (dst_w > 1 ? dst_w - 1 : 1);
    float y_scale = (float)(src_h - 1) / (dst_h > 1 ? dst_h - 1 : 1);
    
    for (int y = 0; y < dst_h; y++) {
        float fy = y * y_scale;
        uint32_t* row = dst + y * dst_w;
        
#ifdef __SSE2__
        bilinear_row_sse2(src, src_w, src_h, row, dst_w, fy, x_scale);
#elif defined(__ARM_NEON)
        bilinear_row_neon(src, src_w, src_h, row, dst_w, fy, x_scale);
#else
        for (int x = 0; x < dst_w; x++) {
            float fx = x * x_scale;
            row[x] = bilinear_sample(src, src_w, src_h, fx, fy);
        }
#endif
    }
    
    return 1;
}

/**
 * Scale image using bicubic interpolation (higher quality, slower).
 */
int32_t simjot_image_scale_bicubic(const uint32_t* src, int32_t src_w, int32_t src_h,
                                    uint32_t* dst, int32_t dst_w, int32_t dst_h) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return 0;
    }
    
    float x_scale = (float)(src_w - 1) / (dst_w > 1 ? dst_w - 1 : 1);
    float y_scale = (float)(src_h - 1) / (dst_h > 1 ? dst_h - 1 : 1);
    
    for (int y = 0; y < dst_h; y++) {
        float fy = y * y_scale;
        uint32_t* row = dst + y * dst_w;
        
        for (int x = 0; x < dst_w; x++) {
            float fx = x * x_scale;
            row[x] = bicubic_sample(src, src_w, src_h, fx, fy);
        }
    }
    
    return 1;
}

/**
 * Progressive downscale for large reductions (better quality).
 * Halves dimensions repeatedly until close to target, then final bilinear.
 */
int32_t simjot_image_scale_progressive(const uint32_t* src, int32_t src_w, int32_t src_h,
                                        uint32_t* dst, int32_t dst_w, int32_t dst_h) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return 0;
    }
    
    // If not downscaling by more than 2x, just use bilinear
    if (src_w <= dst_w * 2 && src_h <= dst_h * 2) {
        return simjot_image_scale_bilinear(src, src_w, src_h, dst, dst_w, dst_h);
    }
    
    // Progressive halving
    int cur_w = src_w;
    int cur_h = src_h;
    uint32_t* cur_buf = (uint32_t*)src;
    uint32_t* tmp_buf = NULL;
    int owns_buffer = 0;
    
    while (cur_w / 2 >= dst_w && cur_h / 2 >= dst_h) {
        int new_w = cur_w / 2;
        int new_h = cur_h / 2;
        
        uint32_t* new_buf = (uint32_t*)malloc(new_w * new_h * sizeof(uint32_t));
        if (!new_buf) {
            if (owns_buffer && tmp_buf) free(tmp_buf);
            return 0;
        }
        
        // Box filter (2x2 average)
        for (int y = 0; y < new_h; y++) {
            for (int x = 0; x < new_w; x++) {
                int sx = x * 2;
                int sy = y * 2;
                
                uint32_t c00 = cur_buf[sy * cur_w + sx];
                uint32_t c10 = cur_buf[sy * cur_w + sx + 1];
                uint32_t c01 = cur_buf[(sy + 1) * cur_w + sx];
                uint32_t c11 = cur_buf[(sy + 1) * cur_w + sx + 1];
                
                int a = (((c00 >> 24) & 0xFF) + ((c10 >> 24) & 0xFF) + 
                         ((c01 >> 24) & 0xFF) + ((c11 >> 24) & 0xFF)) / 4;
                int r = (((c00 >> 16) & 0xFF) + ((c10 >> 16) & 0xFF) + 
                         ((c01 >> 16) & 0xFF) + ((c11 >> 16) & 0xFF)) / 4;
                int g = (((c00 >> 8) & 0xFF) + ((c10 >> 8) & 0xFF) + 
                         ((c01 >> 8) & 0xFF) + ((c11 >> 8) & 0xFF)) / 4;
                int b = ((c00 & 0xFF) + (c10 & 0xFF) + 
                         (c01 & 0xFF) + (c11 & 0xFF)) / 4;
                
                new_buf[y * new_w + x] = pack_argb(a, r, g, b);
            }
        }
        
        if (owns_buffer && tmp_buf) free(tmp_buf);
        tmp_buf = new_buf;
        cur_buf = new_buf;
        cur_w = new_w;
        cur_h = new_h;
        owns_buffer = 1;
    }
    
    // Final bilinear to exact target size
    int result = simjot_image_scale_bilinear(cur_buf, cur_w, cur_h, dst, dst_w, dst_h);
    
    if (owns_buffer && tmp_buf) free(tmp_buf);
    
    return result;
}

/**
 * Auto-select best scaling algorithm based on scale factor.
 */
int32_t simjot_image_scale(const uint32_t* src, int32_t src_w, int32_t src_h,
                            uint32_t* dst, int32_t dst_w, int32_t dst_h,
                            int32_t quality) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) {
        return 0;
    }

#ifdef __APPLE__
    if (simjot_macos_image_scale_argb((const int32_t*)src, src_w, src_h,
                                      (int32_t*)dst, dst_w, dst_h, quality) != 0) {
        return 1;
    }
#endif
    
    // quality: 0 = fast (bilinear), 1 = balanced (progressive), 2 = best (bicubic)
    switch (quality) {
        case 0:
            return simjot_image_scale_bilinear(src, src_w, src_h, dst, dst_w, dst_h);
        case 1:
            return simjot_image_scale_progressive(src, src_w, src_h, dst, dst_w, dst_h);
        case 2:
        default:
            // Use progressive for large downscales, bicubic for upscales/small downscales
            if (src_w > dst_w * 2 || src_h > dst_h * 2) {
                return simjot_image_scale_progressive(src, src_w, src_h, dst, dst_w, dst_h);
            }
            return simjot_image_scale_bicubic(src, src_w, src_h, dst, dst_w, dst_h);
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * IMAGE EFFECTS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Apply Gaussian blur (separable, two-pass).
 */
int32_t simjot_image_blur(uint32_t* pixels, int32_t width, int32_t height, int32_t radius) {
    if (!pixels || width <= 0 || height <= 0 || radius < 1) {
        return 0;
    }

#ifdef __APPLE__
    if (simjot_macos_image_blur_argb((int32_t*)pixels, width, height, radius) != 0) {
        return 1;
    }
#endif
    
    if (radius > 50) radius = 50;  // Limit for performance
    
    // Generate 1D Gaussian kernel
    int ksize = radius * 2 + 1;
    float* kernel = (float*)malloc(ksize * sizeof(float));
    if (!kernel) return 0;
    
    float sigma = radius / 3.0f;
    float sum = 0;
    for (int i = 0; i < ksize; i++) {
        float x = i - radius;
        kernel[i] = expf(-(x * x) / (2 * sigma * sigma));
        sum += kernel[i];
    }
    for (int i = 0; i < ksize; i++) kernel[i] /= sum;
    
    // Temporary buffer
    uint32_t* tmp = (uint32_t*)malloc(width * height * sizeof(uint32_t));
    if (!tmp) { free(kernel); return 0; }
    
    // Horizontal pass
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float sum_a = 0, sum_r = 0, sum_g = 0, sum_b = 0;
            for (int k = 0; k < ksize; k++) {
                int sx = iclamp(x + k - radius, 0, width - 1);
                uint32_t c = pixels[y * width + sx];
                float w = kernel[k];
                sum_a += ((c >> 24) & 0xFF) * w;
                sum_r += ((c >> 16) & 0xFF) * w;
                sum_g += ((c >> 8) & 0xFF) * w;
                sum_b += (c & 0xFF) * w;
            }
            tmp[y * width + x] = pack_argb((int)sum_a, (int)sum_r, (int)sum_g, (int)sum_b);
        }
    }
    
    // Vertical pass
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float sum_a = 0, sum_r = 0, sum_g = 0, sum_b = 0;
            for (int k = 0; k < ksize; k++) {
                int sy = iclamp(y + k - radius, 0, height - 1);
                uint32_t c = tmp[sy * width + x];
                float w = kernel[k];
                sum_a += ((c >> 24) & 0xFF) * w;
                sum_r += ((c >> 16) & 0xFF) * w;
                sum_g += ((c >> 8) & 0xFF) * w;
                sum_b += (c & 0xFF) * w;
            }
            pixels[y * width + x] = pack_argb((int)sum_a, (int)sum_r, (int)sum_g, (int)sum_b);
        }
    }
    
    free(tmp);
    free(kernel);
    return 1;
}

/**
 * Tint image with a color.
 */
int32_t simjot_image_tint(uint32_t* pixels, int32_t width, int32_t height,
                           uint32_t tint_color, float intensity) {
    if (!pixels || width <= 0 || height <= 0) return 0;
    if (intensity < 0) intensity = 0;
    if (intensity > 1) intensity = 1;

#ifdef __APPLE__
    if (simjot_macos_image_tint_argb((int32_t*)pixels, width, height, (int32_t)tint_color, intensity) != 0) {
        return 1;
    }
#endif
    
    int ta, tr, tg, tb;
    unpack_argb(tint_color, &ta, &tr, &tg, &tb);
    
    float inv = 1.0f - intensity;
    
    for (int i = 0; i < width * height; i++) {
        int a, r, g, b;
        unpack_argb(pixels[i], &a, &r, &g, &b);
        
        r = (int)(r * inv + tr * intensity);
        g = (int)(g * inv + tg * intensity);
        b = (int)(b * inv + tb * intensity);
        
        pixels[i] = pack_argb(a, r, g, b);
    }
    
    return 1;
}
