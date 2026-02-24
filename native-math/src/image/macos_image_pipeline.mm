/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Accelerate/Accelerate.h>
#include <stdlib.h>
#include <string.h>

static inline int32_t simjot_clampi32(int32_t v, int32_t lo, int32_t hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static inline uint8_t simjot_clamp_u8(int v) {
    return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v));
}

static int simjot_alloc_argb_planes(int32_t pixel_count, uint8_t** out_src, uint8_t** out_dst) {
    if (!out_src || !out_dst || pixel_count <= 0) return 0;
    size_t bytes = (size_t)pixel_count * 4u;
    *out_src = (uint8_t*)malloc(bytes);
    *out_dst = (uint8_t*)malloc(bytes);
    if (!*out_src || !*out_dst) {
        if (*out_src) free(*out_src);
        if (*out_dst) free(*out_dst);
        *out_src = NULL;
        *out_dst = NULL;
        return 0;
    }
    return 1;
}

static void simjot_argb_int_to_bytes(const int32_t* src_argb, uint8_t* dst_argb, int32_t pixel_count) {
    for (int32_t i = 0; i < pixel_count; i++) {
        int32_t p = src_argb[i];
        dst_argb[i * 4 + 0] = (uint8_t)((p >> 24) & 0xFF); // A
        dst_argb[i * 4 + 1] = (uint8_t)((p >> 16) & 0xFF); // R
        dst_argb[i * 4 + 2] = (uint8_t)((p >> 8) & 0xFF);  // G
        dst_argb[i * 4 + 3] = (uint8_t)(p & 0xFF);         // B
    }
}

static void simjot_bytes_to_argb_int(const uint8_t* src_argb, int32_t* dst_argb, int32_t pixel_count) {
    for (int32_t i = 0; i < pixel_count; i++) {
        int32_t a = src_argb[i * 4 + 0];
        int32_t r = src_argb[i * 4 + 1];
        int32_t g = src_argb[i * 4 + 2];
        int32_t b = src_argb[i * 4 + 3];
        dst_argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
}
#endif

extern "C" int32_t simjot_macos_image_scale_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h,
                                                    int32_t* dst_argb, int32_t dst_w, int32_t dst_h,
                                                    int32_t quality) {
#ifdef __APPLE__
    if (!src_argb || !dst_argb || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0) return 0;

    int32_t src_pixels = src_w * src_h;
    int32_t dst_pixels = dst_w * dst_h;
    uint8_t* src_buf = NULL;
    uint8_t* dst_buf = NULL;

    size_t src_bytes = (size_t)src_pixels * 4u;
    size_t dst_bytes = (size_t)dst_pixels * 4u;

    src_buf = (uint8_t*)malloc(src_bytes);
    dst_buf = (uint8_t*)malloc(dst_bytes);
    if (!src_buf || !dst_buf) {
        if (src_buf) free(src_buf);
        if (dst_buf) free(dst_buf);
        return 0;
    }

    simjot_argb_int_to_bytes(src_argb, src_buf, src_pixels);

    vImage_Buffer src = {
        .data = src_buf,
        .height = (vImagePixelCount)src_h,
        .width = (vImagePixelCount)src_w,
        .rowBytes = (size_t)src_w * 4u
    };
    vImage_Buffer dst = {
        .data = dst_buf,
        .height = (vImagePixelCount)dst_h,
        .width = (vImagePixelCount)dst_w,
        .rowBytes = (size_t)dst_w * 4u
    };

    vImage_Flags flags = kvImageNoFlags;
    if (quality >= 1) flags |= kvImageHighQualityResampling;

    vImage_Error err = vImageScale_ARGB8888(&src, &dst, NULL, flags);
    if (err != kvImageNoError) {
        free(src_buf);
        free(dst_buf);
        return 0;
    }

    simjot_bytes_to_argb_int(dst_buf, dst_argb, dst_pixels);
    free(src_buf);
    free(dst_buf);
    return 1;
#else
    (void)src_argb;
    (void)src_w;
    (void)src_h;
    (void)dst_argb;
    (void)dst_w;
    (void)dst_h;
    (void)quality;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_image_blur_argb(int32_t* argb, int32_t width, int32_t height, int32_t radius) {
#ifdef __APPLE__
    if (!argb || width <= 0 || height <= 0 || radius < 1) return 0;

    radius = simjot_clampi32(radius, 1, 64);
    uint32_t kernel = (uint32_t)(radius * 2 + 1);

    int32_t pixels = width * height;
    size_t bytes = (size_t)pixels * 4u;
    uint8_t* in_buf = (uint8_t*)malloc(bytes);
    uint8_t* mid_buf = (uint8_t*)malloc(bytes);
    uint8_t* out_buf = (uint8_t*)malloc(bytes);
    if (!in_buf || !mid_buf || !out_buf) {
        if (in_buf) free(in_buf);
        if (mid_buf) free(mid_buf);
        if (out_buf) free(out_buf);
        return 0;
    }

    simjot_argb_int_to_bytes(argb, in_buf, pixels);

    vImage_Buffer in = {
        .data = in_buf,
        .height = (vImagePixelCount)height,
        .width = (vImagePixelCount)width,
        .rowBytes = (size_t)width * 4u
    };
    vImage_Buffer mid = {
        .data = mid_buf,
        .height = (vImagePixelCount)height,
        .width = (vImagePixelCount)width,
        .rowBytes = (size_t)width * 4u
    };
    vImage_Buffer out = {
        .data = out_buf,
        .height = (vImagePixelCount)height,
        .width = (vImagePixelCount)width,
        .rowBytes = (size_t)width * 4u
    };

    vImage_Flags flags = kvImageEdgeExtend;
    vImage_Error err = kvImageNoError;

    // Three box passes approximate Gaussian blur at much lower CPU cost.
    err = vImageBoxConvolve_ARGB8888(&in, &mid, NULL, 0, 0, kernel, kernel, NULL, flags);
    if (err == kvImageNoError) {
        err = vImageBoxConvolve_ARGB8888(&mid, &out, NULL, 0, 0, kernel, kernel, NULL, flags);
    }
    if (err == kvImageNoError) {
        err = vImageBoxConvolve_ARGB8888(&out, &mid, NULL, 0, 0, kernel, kernel, NULL, flags);
    }

    if (err != kvImageNoError) {
        free(in_buf);
        free(mid_buf);
        free(out_buf);
        return 0;
    }

    simjot_bytes_to_argb_int(mid_buf, argb, pixels);

    free(in_buf);
    free(mid_buf);
    free(out_buf);
    return 1;
#else
    (void)argb;
    (void)width;
    (void)height;
    (void)radius;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_image_tint_argb(int32_t* argb, int32_t width, int32_t height,
                                                   int32_t tint_color, float intensity) {
#ifdef __APPLE__
    if (!argb || width <= 0 || height <= 0) return 0;

    if (intensity < 0.0f) intensity = 0.0f;
    if (intensity > 1.0f) intensity = 1.0f;

    int tr = (tint_color >> 16) & 0xFF;
    int tg = (tint_color >> 8) & 0xFF;
    int tb = tint_color & 0xFF;

    float inv = 1.0f - intensity;
    int32_t pixels = width * height;

    for (int32_t i = 0; i < pixels; i++) {
        int32_t p = argb[i];
        int a = (p >> 24) & 0xFF;
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;

        r = (int)(r * inv + tr * intensity + 0.5f);
        g = (int)(g * inv + tg * intensity + 0.5f);
        b = (int)(b * inv + tb * intensity + 0.5f);

        argb[i] = (a << 24) | (simjot_clamp_u8(r) << 16) | (simjot_clamp_u8(g) << 8) | simjot_clamp_u8(b);
    }

    return 1;
#else
    (void)argb;
    (void)width;
    (void)height;
    (void)tint_color;
    (void)intensity;
    return 0;
#endif
}
