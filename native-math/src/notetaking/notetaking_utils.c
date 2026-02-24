/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file notetaking_utils.c
 * @brief Native utilities for notetaking: stroke smoothing, text formatting, math
 * 
 * Provides high-performance native implementations for:
 * - Stroke smoothing using Bezier curves and Catmull-Rom splines
 * - Pressure interpolation for natural handwriting feel
 * - Text highlighting and color manipulation
 * - Math equation formatting and symbol utilities
 * 
 * @author S1mplector
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE POINT STRUCTURE
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct StrokePoint {
    float x;
    float y;
    float pressure;  /* 0.0 - 1.0 */
    float tilt_x;    /* Optional tilt */
    float tilt_y;
    int64_t timestamp;
} StrokePoint;

/* ═══════════════════════════════════════════════════════════════════════════
 * BEZIER CURVE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float lerp(float a, float b, float t) {
    return a + t * (b - a);
}

static inline float cubic_bezier(float p0, float p1, float p2, float p3, float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    float mt = 1.0f - t;
    float mt2 = mt * mt;
    float mt3 = mt2 * mt;
    return mt3 * p0 + 3.0f * mt2 * t * p1 + 3.0f * mt * t2 * p2 + t3 * p3;
}

static inline float quadratic_bezier(float p0, float p1, float p2, float t) {
    float mt = 1.0f - t;
    return mt * mt * p0 + 2.0f * mt * t * p1 + t * t * p2;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CATMULL-ROM SPLINE
 * ═══════════════════════════════════════════════════════════════════════════ */

static float catmull_rom(float p0, float p1, float p2, float p3, float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return 0.5f * (
        (2.0f * p1) +
        (-p0 + p2) * t +
        (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2 +
        (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3
    );
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE SMOOTHING - MAIN API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Smooth stroke points using Catmull-Rom splines
 * 
 * @param input_x Input X coordinates
 * @param input_y Input Y coordinates
 * @param input_pressure Input pressure values (0-1)
 * @param input_count Number of input points
 * @param output_x Output smoothed X coordinates
 * @param output_y Output smoothed Y coordinates
 * @param output_pressure Output smoothed pressure values
 * @param output_capacity Maximum output points
 * @param smoothing_factor How many interpolated points per segment (1-10)
 * @return Number of output points, or -1 on error
 */
int32_t simjot_stroke_smooth_catmull(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t output_capacity,
    int32_t smoothing_factor
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 2) {
        return -1;
    }
    
    if (smoothing_factor < 1) smoothing_factor = 1;
    if (smoothing_factor > 10) smoothing_factor = 10;
    
    int32_t out_idx = 0;
    
    for (int32_t i = 0; i < input_count - 1 && out_idx < output_capacity; i++) {
        /* Get 4 control points (clamp at ends) */
        int32_t i0 = (i > 0) ? i - 1 : 0;
        int32_t i1 = i;
        int32_t i2 = i + 1;
        int32_t i3 = (i + 2 < input_count) ? i + 2 : input_count - 1;
        
        float x0 = input_x[i0], y0 = input_y[i0];
        float x1 = input_x[i1], y1 = input_y[i1];
        float x2 = input_x[i2], y2 = input_y[i2];
        float x3 = input_x[i3], y3 = input_y[i3];
        
        float p0 = input_pressure ? input_pressure[i0] : 0.5f;
        float p1 = input_pressure ? input_pressure[i1] : 0.5f;
        float p2 = input_pressure ? input_pressure[i2] : 0.5f;
        float p3 = input_pressure ? input_pressure[i3] : 0.5f;
        
        /* Interpolate along segment */
        int32_t steps = (i == input_count - 2) ? smoothing_factor + 1 : smoothing_factor;
        for (int32_t s = 0; s < steps && out_idx < output_capacity; s++) {
            float t = (float)s / (float)smoothing_factor;
            
            output_x[out_idx] = catmull_rom(x0, x1, x2, x3, t);
            output_y[out_idx] = catmull_rom(y0, y1, y2, y3, t);
            if (output_pressure) {
                output_pressure[out_idx] = catmull_rom(p0, p1, p2, p3, t);
            }
            out_idx++;
        }
    }
    
    return out_idx;
}

/**
 * @brief Smooth stroke with quadratic Bezier curves (simpler, faster)
 */
int32_t simjot_stroke_smooth_bezier(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t output_capacity,
    int32_t smoothing_factor
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 2) {
        return -1;
    }
    
    if (smoothing_factor < 1) smoothing_factor = 1;
    if (smoothing_factor > 10) smoothing_factor = 10;
    
    int32_t out_idx = 0;
    
    /* First point */
    output_x[out_idx] = input_x[0];
    output_y[out_idx] = input_y[0];
    if (output_pressure) output_pressure[out_idx] = input_pressure ? input_pressure[0] : 0.5f;
    out_idx++;
    
    for (int32_t i = 1; i < input_count - 1 && out_idx < output_capacity - 1; i++) {
        /* Control point is current point, endpoints are midpoints */
        float mx1 = (input_x[i-1] + input_x[i]) * 0.5f;
        float my1 = (input_y[i-1] + input_y[i]) * 0.5f;
        float mx2 = (input_x[i] + input_x[i+1]) * 0.5f;
        float my2 = (input_y[i] + input_y[i+1]) * 0.5f;
        
        float p1 = input_pressure ? (input_pressure[i-1] + input_pressure[i]) * 0.5f : 0.5f;
        float pc = input_pressure ? input_pressure[i] : 0.5f;
        float p2 = input_pressure ? (input_pressure[i] + input_pressure[i+1]) * 0.5f : 0.5f;
        
        for (int32_t s = 1; s <= smoothing_factor && out_idx < output_capacity; s++) {
            float t = (float)s / (float)smoothing_factor;
            
            output_x[out_idx] = quadratic_bezier(mx1, input_x[i], mx2, t);
            output_y[out_idx] = quadratic_bezier(my1, input_y[i], my2, t);
            if (output_pressure) {
                output_pressure[out_idx] = quadratic_bezier(p1, pc, p2, t);
            }
            out_idx++;
        }
    }
    
    /* Last point */
    if (out_idx < output_capacity) {
        output_x[out_idx] = input_x[input_count - 1];
        output_y[out_idx] = input_y[input_count - 1];
        if (output_pressure) {
            output_pressure[out_idx] = input_pressure ? input_pressure[input_count - 1] : 0.5f;
        }
        out_idx++;
    }
    
    return out_idx;
}

/**
 * @brief Apply moving average smoothing to stroke (simple, fast)
 */
int32_t simjot_stroke_smooth_average(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t window_size
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 1) {
        return -1;
    }
    
    if (window_size < 1) window_size = 1;
    if (window_size > input_count) window_size = input_count;
    
    int32_t half = window_size / 2;
    
    for (int32_t i = 0; i < input_count; i++) {
        int32_t start = (i - half > 0) ? i - half : 0;
        int32_t end = (i + half < input_count) ? i + half : input_count - 1;
        int32_t count = end - start + 1;
        
        float sum_x = 0, sum_y = 0, sum_p = 0;
        for (int32_t j = start; j <= end; j++) {
            sum_x += input_x[j];
            sum_y += input_y[j];
            if (input_pressure) sum_p += input_pressure[j];
        }
        
        output_x[i] = sum_x / count;
        output_y[i] = sum_y / count;
        if (output_pressure && input_pressure) {
            output_pressure[i] = sum_p / count;
        }
    }
    
    return input_count;
}

/**
 * @brief Simplify stroke using Douglas-Peucker algorithm
 * Reduces point count while preserving shape
 */
int32_t simjot_nt_stroke_simplify(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float epsilon
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 2) {
        return -1;
    }
    
    if (epsilon <= 0) epsilon = 1.0f;
    
    /* Mark points to keep */
    int8_t* keep = (int8_t*)calloc(input_count, sizeof(int8_t));
    if (!keep) return -1;
    
    keep[0] = 1;
    keep[input_count - 1] = 1;
    
    /* Stack for iterative Douglas-Peucker */
    int32_t stack[256];
    int32_t stack_ptr = 0;
    stack[stack_ptr++] = 0;
    stack[stack_ptr++] = input_count - 1;
    
    while (stack_ptr >= 2) {
        int32_t end = stack[--stack_ptr];
        int32_t start = stack[--stack_ptr];
        
        if (end - start < 2) continue;
        
        /* Find point with max distance */
        float max_dist = 0;
        int32_t max_idx = start;
        
        float dx = input_x[end] - input_x[start];
        float dy = input_y[end] - input_y[start];
        float line_len_sq = dx * dx + dy * dy;
        
        for (int32_t i = start + 1; i < end; i++) {
            float dist;
            if (line_len_sq < 0.0001f) {
                /* Start and end are same point */
                float pdx = input_x[i] - input_x[start];
                float pdy = input_y[i] - input_y[start];
                dist = sqrtf(pdx * pdx + pdy * pdy);
            } else {
                float t = ((input_x[i] - input_x[start]) * dx + 
                          (input_y[i] - input_y[start]) * dy) / line_len_sq;
                t = fmaxf(0, fminf(1, t));
                float proj_x = input_x[start] + t * dx;
                float proj_y = input_y[start] + t * dy;
                float pdx = input_x[i] - proj_x;
                float pdy = input_y[i] - proj_y;
                dist = sqrtf(pdx * pdx + pdy * pdy);
            }
            
            if (dist > max_dist) {
                max_dist = dist;
                max_idx = i;
            }
        }
        
        if (max_dist > epsilon) {
            keep[max_idx] = 1;
            if (stack_ptr < 254) {
                stack[stack_ptr++] = start;
                stack[stack_ptr++] = max_idx;
                stack[stack_ptr++] = max_idx;
                stack[stack_ptr++] = end;
            }
        }
    }
    
    /* Copy kept points */
    int32_t out_idx = 0;
    for (int32_t i = 0; i < input_count && out_idx < output_capacity; i++) {
        if (keep[i]) {
            output_x[out_idx] = input_x[i];
            output_y[out_idx] = input_y[i];
            out_idx++;
        }
    }
    
    free(keep);
    return out_idx;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PRESSURE PROCESSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Smooth pressure values with exponential moving average
 */
int32_t simjot_pressure_smooth(
    const float* input, int32_t count,
    float* output, float alpha
) {
    if (!input || !output || count < 1) return -1;
    if (alpha < 0.01f) alpha = 0.01f;
    if (alpha > 1.0f) alpha = 1.0f;
    
    output[0] = input[0];
    for (int32_t i = 1; i < count; i++) {
        output[i] = alpha * input[i] + (1.0f - alpha) * output[i - 1];
    }
    
    return count;
}

/**
 * @brief Map pressure to stroke width with customizable curve
 */
float simjot_pressure_to_width(float pressure, float min_width, float max_width, float curve) {
    if (pressure < 0) pressure = 0;
    if (pressure > 1) pressure = 1;
    if (curve <= 0) curve = 1.0f;
    
    float t = powf(pressure, curve);
    return min_width + t * (max_width - min_width);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT HIGHLIGHTING & COLOR UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Blend two ARGB colors
 */
int32_t simjot_color_blend(int32_t color1, int32_t color2, float t) {
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    
    int32_t a1 = (color1 >> 24) & 0xFF;
    int32_t r1 = (color1 >> 16) & 0xFF;
    int32_t g1 = (color1 >> 8) & 0xFF;
    int32_t b1 = color1 & 0xFF;
    
    int32_t a2 = (color2 >> 24) & 0xFF;
    int32_t r2 = (color2 >> 16) & 0xFF;
    int32_t g2 = (color2 >> 8) & 0xFF;
    int32_t b2 = color2 & 0xFF;
    
    int32_t a = (int32_t)(a1 + t * (a2 - a1));
    int32_t r = (int32_t)(r1 + t * (r2 - r1));
    int32_t g = (int32_t)(g1 + t * (g2 - g1));
    int32_t b = (int32_t)(b1 + t * (b2 - b1));
    
    return (a << 24) | (r << 16) | (g << 8) | b;
}

/**
 * @brief Create highlight color from base color (semi-transparent overlay)
 */
int32_t simjot_color_highlight(int32_t base_color, float intensity) {
    if (intensity < 0) intensity = 0;
    if (intensity > 1) intensity = 1;
    
    int32_t r = (base_color >> 16) & 0xFF;
    int32_t g = (base_color >> 8) & 0xFF;
    int32_t b = base_color & 0xFF;
    
    /* Typical highlight is ~40% opacity */
    int32_t a = (int32_t)(102 * intensity);
    
    return (a << 24) | (r << 16) | (g << 8) | b;
}

/**
 * @brief Adjust color brightness
 */
int32_t simjot_color_brightness(int32_t color, float factor) {
    int32_t a = (color >> 24) & 0xFF;
    int32_t r = (color >> 16) & 0xFF;
    int32_t g = (color >> 8) & 0xFF;
    int32_t b = color & 0xFF;
    
    r = (int32_t)(r * factor);
    g = (int32_t)(g * factor);
    b = (int32_t)(b * factor);
    
    if (r > 255) r = 255;
    if (g > 255) g = 255;
    if (b > 255) b = 255;
    if (r < 0) r = 0;
    if (g < 0) g = 0;
    if (b < 0) b = 0;
    
    return (a << 24) | (r << 16) | (g << 8) | b;
}

/**
 * @brief Convert HSL to RGB (ARGB format)
 */
int32_t simjot_hsl_to_rgb(float h, float s, float l, int32_t alpha) {
    if (s < 0) s = 0;
    if (s > 1) s = 1;
    if (l < 0) l = 0;
    if (l > 1) l = 1;
    
    float c = (1.0f - fabsf(2.0f * l - 1.0f)) * s;
    float hp = fmodf(h / 60.0f, 6.0f);
    float x = c * (1.0f - fabsf(fmodf(hp, 2.0f) - 1.0f));
    float m = l - c / 2.0f;
    
    float r, g, b;
    if (hp < 1) { r = c; g = x; b = 0; }
    else if (hp < 2) { r = x; g = c; b = 0; }
    else if (hp < 3) { r = 0; g = c; b = x; }
    else if (hp < 4) { r = 0; g = x; b = c; }
    else if (hp < 5) { r = x; g = 0; b = c; }
    else { r = c; g = 0; b = x; }
    
    int32_t ri = (int32_t)((r + m) * 255);
    int32_t gi = (int32_t)((g + m) * 255);
    int32_t bi = (int32_t)((b + m) * 255);
    
    if (ri > 255) ri = 255;
    if (gi > 255) gi = 255;
    if (bi > 255) bi = 255;
    
    return (alpha << 24) | (ri << 16) | (gi << 8) | bi;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * MATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Parse simple math expression and format for display
 * Handles fractions, exponents, square roots
 * 
 * Input: "1/2", "x^2", "sqrt(x)", "pi", "alpha"
 * Output: Unicode formatted string
 */
int32_t simjot_math_format_simple(const char* input, char* output, int32_t output_len) {
    if (!input || !output || output_len < 1) return -1;
    
    int32_t in_len = strlen(input);
    int32_t out_idx = 0;
    
    /* Unicode superscript digits */
    static const char* superscripts[] = {
        "\u2070", "\u00B9", "\u00B2", "\u00B3", "\u2074",
        "\u2075", "\u2076", "\u2077", "\u2078", "\u2079"
    };
    
    /* Unicode subscript digits */
    static const char* subscripts[] = {
        "\u2080", "\u2081", "\u2082", "\u2083", "\u2084",
        "\u2085", "\u2086", "\u2087", "\u2088", "\u2089"
    };
    
    for (int32_t i = 0; i < in_len && out_idx < output_len - 4; i++) {
        /* Check for special patterns */
        
        /* Fractions: common ones */
        if (i + 2 < in_len && input[i+1] == '/') {
            if (input[i] == '1' && input[i+2] == '2') {
                /* 1/2 -> ½ */
                const char* half = "\u00BD";
                int32_t len = strlen(half);
                if (out_idx + len < output_len) {
                    strcpy(output + out_idx, half);
                    out_idx += len;
                    i += 2;
                    continue;
                }
            } else if (input[i] == '1' && input[i+2] == '4') {
                const char* quarter = "\u00BC";
                int32_t len = strlen(quarter);
                if (out_idx + len < output_len) {
                    strcpy(output + out_idx, quarter);
                    out_idx += len;
                    i += 2;
                    continue;
                }
            } else if (input[i] == '3' && input[i+2] == '4') {
                const char* three_quarter = "\u00BE";
                int32_t len = strlen(three_quarter);
                if (out_idx + len < output_len) {
                    strcpy(output + out_idx, three_quarter);
                    out_idx += len;
                    i += 2;
                    continue;
                }
            }
        }
        
        /* Exponents: ^digit */
        if (input[i] == '^' && i + 1 < in_len && input[i+1] >= '0' && input[i+1] <= '9') {
            int32_t digit = input[i+1] - '0';
            const char* super = superscripts[digit];
            int32_t len = strlen(super);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, super);
                out_idx += len;
                i++;
                continue;
            }
        }
        
        /* Subscript: _digit */
        if (input[i] == '_' && i + 1 < in_len && input[i+1] >= '0' && input[i+1] <= '9') {
            int32_t digit = input[i+1] - '0';
            const char* sub = subscripts[digit];
            int32_t len = strlen(sub);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, sub);
                out_idx += len;
                i++;
                continue;
            }
        }
        
        /* Square root: sqrt -> √ */
        if (i + 3 < in_len && strncmp(input + i, "sqrt", 4) == 0) {
            const char* sqrt_sym = "\u221A";
            int32_t len = strlen(sqrt_sym);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, sqrt_sym);
                out_idx += len;
                i += 3;
                continue;
            }
        }
        
        /* Pi: pi -> π */
        if (i + 1 < in_len && strncmp(input + i, "pi", 2) == 0) {
            const char* pi = "\u03C0";
            int32_t len = strlen(pi);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, pi);
                out_idx += len;
                i += 1;
                continue;
            }
        }
        
        /* Infinity: inf -> ∞ */
        if (i + 2 < in_len && strncmp(input + i, "inf", 3) == 0) {
            const char* inf = "\u221E";
            int32_t len = strlen(inf);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, inf);
                out_idx += len;
                i += 2;
                continue;
            }
        }
        
        /* Plus-minus: +- -> ± */
        if (i + 1 < in_len && input[i] == '+' && input[i+1] == '-') {
            const char* pm = "\u00B1";
            int32_t len = strlen(pm);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, pm);
                out_idx += len;
                i += 1;
                continue;
            }
        }
        
        /* Multiplication: * -> × */
        if (input[i] == '*') {
            const char* mult = "\u00D7";
            int32_t len = strlen(mult);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, mult);
                out_idx += len;
                continue;
            }
        }
        
        /* Division: / -> ÷ (when between numbers) */
        if (input[i] == '/' && i > 0 && i + 1 < in_len &&
            (input[i-1] >= '0' && input[i-1] <= '9') &&
            (input[i+1] >= '0' && input[i+1] <= '9')) {
            const char* div = "\u00F7";
            int32_t len = strlen(div);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, div);
                out_idx += len;
                continue;
            }
        }
        
        /* Not equal: != -> ≠ */
        if (i + 1 < in_len && input[i] == '!' && input[i+1] == '=') {
            const char* neq = "\u2260";
            int32_t len = strlen(neq);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, neq);
                out_idx += len;
                i += 1;
                continue;
            }
        }
        
        /* Less or equal: <= -> ≤ */
        if (i + 1 < in_len && input[i] == '<' && input[i+1] == '=') {
            const char* leq = "\u2264";
            int32_t len = strlen(leq);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, leq);
                out_idx += len;
                i += 1;
                continue;
            }
        }
        
        /* Greater or equal: >= -> ≥ */
        if (i + 1 < in_len && input[i] == '>' && input[i+1] == '=') {
            const char* geq = "\u2265";
            int32_t len = strlen(geq);
            if (out_idx + len < output_len) {
                strcpy(output + out_idx, geq);
                out_idx += len;
                i += 1;
                continue;
            }
        }
        
        /* Default: copy character */
        output[out_idx++] = input[i];
    }
    
    output[out_idx] = '\0';
    return out_idx;
}

/**
 * @brief Get Greek letter Unicode for common math symbols
 */
int32_t simjot_math_greek_letter(const char* name, char* output, int32_t output_len) {
    if (!name || !output || output_len < 4) return -1;
    
    /* Greek letters mapping */
    static const struct { const char* name; const char* unicode; } greeks[] = {
        {"alpha", "\u03B1"}, {"beta", "\u03B2"}, {"gamma", "\u03B3"}, {"delta", "\u03B4"},
        {"epsilon", "\u03B5"}, {"zeta", "\u03B6"}, {"eta", "\u03B7"}, {"theta", "\u03B8"},
        {"iota", "\u03B9"}, {"kappa", "\u03BA"}, {"lambda", "\u03BB"}, {"mu", "\u03BC"},
        {"nu", "\u03BD"}, {"xi", "\u03BE"}, {"omicron", "\u03BF"}, {"pi", "\u03C0"},
        {"rho", "\u03C1"}, {"sigma", "\u03C3"}, {"tau", "\u03C4"}, {"upsilon", "\u03C5"},
        {"phi", "\u03C6"}, {"chi", "\u03C7"}, {"psi", "\u03C8"}, {"omega", "\u03C9"},
        /* Uppercase */
        {"Alpha", "\u0391"}, {"Beta", "\u0392"}, {"Gamma", "\u0393"}, {"Delta", "\u0394"},
        {"Epsilon", "\u0395"}, {"Zeta", "\u0396"}, {"Eta", "\u0397"}, {"Theta", "\u0398"},
        {"Iota", "\u0399"}, {"Kappa", "\u039A"}, {"Lambda", "\u039B"}, {"Mu", "\u039C"},
        {"Nu", "\u039D"}, {"Xi", "\u039E"}, {"Omicron", "\u039F"}, {"Pi", "\u03A0"},
        {"Rho", "\u03A1"}, {"Sigma", "\u03A3"}, {"Tau", "\u03A4"}, {"Upsilon", "\u03A5"},
        {"Phi", "\u03A6"}, {"Chi", "\u03A7"}, {"Psi", "\u03A8"}, {"Omega", "\u03A9"},
        {NULL, NULL}
    };
    
    for (int i = 0; greeks[i].name != NULL; i++) {
        if (strcmp(name, greeks[i].name) == 0) {
            int32_t len = strlen(greeks[i].unicode);
            if (len < output_len) {
                strcpy(output, greeks[i].unicode);
                return len;
            }
        }
    }
    
    output[0] = '\0';
    return 0;
}

/**
 * @brief Get math operator Unicode symbol
 */
int32_t simjot_math_operator(const char* name, char* output, int32_t output_len) {
    if (!name || !output || output_len < 4) return -1;
    
    static const struct { const char* name; const char* unicode; } operators[] = {
        {"sum", "\u2211"}, {"prod", "\u220F"}, {"integral", "\u222B"},
        {"partial", "\u2202"}, {"nabla", "\u2207"}, {"forall", "\u2200"},
        {"exists", "\u2203"}, {"empty", "\u2205"}, {"in", "\u2208"},
        {"notin", "\u2209"}, {"subset", "\u2282"}, {"superset", "\u2283"},
        {"union", "\u222A"}, {"intersect", "\u2229"}, {"and", "\u2227"},
        {"or", "\u2228"}, {"not", "\u00AC"}, {"implies", "\u21D2"},
        {"iff", "\u21D4"}, {"therefore", "\u2234"}, {"because", "\u2235"},
        {"approx", "\u2248"}, {"propto", "\u221D"}, {"degree", "\u00B0"},
        {"prime", "\u2032"}, {"dprime", "\u2033"}, {"infinity", "\u221E"},
        {"sqrt", "\u221A"}, {"cbrt", "\u221B"}, {"fourthrt", "\u221C"},
        {NULL, NULL}
    };
    
    for (int i = 0; operators[i].name != NULL; i++) {
        if (strcmp(name, operators[i].name) == 0) {
            int32_t len = strlen(operators[i].unicode);
            if (len < output_len) {
                strcpy(output, operators[i].unicode);
                return len;
            }
        }
    }
    
    output[0] = '\0';
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * REAL-TIME STROKE SMOOTHING FOR DRAWING (Optimized for iPad/tablet input)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Chaikin's corner-cutting algorithm - fast, smooth results
 * Applies subdivision smoothing ideal for handwriting
 * 
 * @param input_x/y Input coordinates
 * @param input_count Number of input points
 * @param output_x/y Output coordinates (needs capacity for ~2x input points per iteration)
 * @param output_capacity Max output capacity
 * @param iterations Number of smoothing passes (1-3 recommended)
 * @return Number of output points, or -1 on error
 */
int32_t simjot_stroke_chaikin(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    int32_t iterations
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 2) {
        return -1;
    }
    
    if (iterations < 1) iterations = 1;
    if (iterations > 4) iterations = 4;
    
    /* Work buffers - use output as temp, allocate second buffer */
    float* buf_x = (float*)malloc(output_capacity * sizeof(float));
    float* buf_y = (float*)malloc(output_capacity * sizeof(float));
    if (!buf_x || !buf_y) {
        free(buf_x);
        free(buf_y);
        return -1;
    }
    
    /* Copy input to first buffer */
    const float* src_x = input_x;
    const float* src_y = input_y;
    int32_t count = input_count;
    
    for (int32_t iter = 0; iter < iterations; iter++) {
        float* dst_x = (iter % 2 == 0) ? buf_x : output_x;
        float* dst_y = (iter % 2 == 0) ? buf_y : output_y;
        int32_t out_idx = 0;
        
        /* Keep first point */
        dst_x[out_idx] = src_x[0];
        dst_y[out_idx] = src_y[0];
        out_idx++;
        
        /* Chaikin subdivision: Q0 = 3/4*P0 + 1/4*P1, Q1 = 1/4*P0 + 3/4*P1 */
        for (int32_t i = 0; i < count - 1 && out_idx + 1 < output_capacity; i++) {
            float x0 = src_x[i], y0 = src_y[i];
            float x1 = src_x[i + 1], y1 = src_y[i + 1];
            
            dst_x[out_idx] = 0.75f * x0 + 0.25f * x1;
            dst_y[out_idx] = 0.75f * y0 + 0.25f * y1;
            out_idx++;
            
            dst_x[out_idx] = 0.25f * x0 + 0.75f * x1;
            dst_y[out_idx] = 0.25f * y0 + 0.75f * y1;
            out_idx++;
        }
        
        /* Keep last point */
        if (out_idx < output_capacity) {
            dst_x[out_idx] = src_x[count - 1];
            dst_y[out_idx] = src_y[count - 1];
            out_idx++;
        }
        
        src_x = dst_x;
        src_y = dst_y;
        count = out_idx;
    }
    
    /* Copy result to output if needed */
    if (src_x != output_x) {
        memcpy(output_x, src_x, count * sizeof(float));
        memcpy(output_y, src_y, count * sizeof(float));
    }
    
    free(buf_x);
    free(buf_y);
    
    return count;
}

/**
 * @brief Velocity-based adaptive smoothing
 * Smooths more in fast movements (where sampling is sparse)
 * 
 * @param x/y Coordinates
 * @param timestamps Timestamps in milliseconds
 * @param count Number of points
 * @param smoothed_x/y Output smoothed coordinates
 * @param velocity_threshold Velocity above which extra smoothing is applied (pixels/ms)
 * @param max_smooth_factor Maximum smoothing weight (0.0-1.0)
 */
int32_t simjot_stroke_velocity_smooth(
    const float* x, const float* y, const float* timestamps,
    int32_t count,
    float* smoothed_x, float* smoothed_y,
    float velocity_threshold, float max_smooth_factor
) {
    if (!x || !y || !timestamps || !smoothed_x || !smoothed_y || count < 2) {
        return -1;
    }
    
    if (velocity_threshold <= 0) velocity_threshold = 0.5f;
    if (max_smooth_factor < 0) max_smooth_factor = 0;
    if (max_smooth_factor > 0.9f) max_smooth_factor = 0.9f;
    
    /* First point unchanged */
    smoothed_x[0] = x[0];
    smoothed_y[0] = y[0];
    
    for (int32_t i = 1; i < count; i++) {
        float dt = timestamps[i] - timestamps[i - 1];
        if (dt < 0.001f) dt = 0.001f; /* Avoid division by zero */
        
        float dx = x[i] - x[i - 1];
        float dy = y[i] - y[i - 1];
        float dist = sqrtf(dx * dx + dy * dy);
        float velocity = dist / dt;
        
        /* Calculate adaptive smoothing factor based on velocity */
        float smooth_factor = 0.0f;
        if (velocity > velocity_threshold) {
            /* Higher velocity = more smoothing */
            float excess = (velocity - velocity_threshold) / velocity_threshold;
            smooth_factor = fminf(excess * 0.3f, max_smooth_factor);
        }
        
        /* Apply exponential moving average with adaptive factor */
        smoothed_x[i] = (1.0f - smooth_factor) * x[i] + smooth_factor * smoothed_x[i - 1];
        smoothed_y[i] = (1.0f - smooth_factor) * y[i] + smooth_factor * smoothed_y[i - 1];
    }
    
    return count;
}

/**
 * @brief Interpolate sparse points to fill gaps (for fast strokes)
 * 
 * @param input_x/y Input coordinates
 * @param input_count Number of input points
 * @param output_x/y Output with interpolated points
 * @param output_capacity Maximum output capacity
 * @param max_gap Maximum allowed distance between points
 * @return Number of output points
 */
int32_t simjot_stroke_interpolate_gaps(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float max_gap
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 1) {
        return -1;
    }
    
    if (max_gap <= 0) max_gap = 8.0f;
    
    int32_t out_idx = 0;
    
    /* First point */
    output_x[out_idx] = input_x[0];
    output_y[out_idx] = input_y[0];
    out_idx++;
    
    for (int32_t i = 1; i < input_count && out_idx < output_capacity; i++) {
        float dx = input_x[i] - input_x[i - 1];
        float dy = input_y[i] - input_y[i - 1];
        float dist = sqrtf(dx * dx + dy * dy);
        
        if (dist > max_gap) {
            /* Interpolate intermediate points */
            int32_t steps = (int32_t)ceilf(dist / max_gap);
            for (int32_t s = 1; s < steps && out_idx < output_capacity; s++) {
                float t = (float)s / (float)steps;
                output_x[out_idx] = input_x[i - 1] + t * dx;
                output_y[out_idx] = input_y[i - 1] + t * dy;
                out_idx++;
            }
        }
        
        if (out_idx < output_capacity) {
            output_x[out_idx] = input_x[i];
            output_y[out_idx] = input_y[i];
            out_idx++;
        }
    }
    
    return out_idx;
}

/**
 * @brief Combined real-time stroke processing pipeline
 * Interpolates gaps, applies velocity smoothing, then Chaikin smoothing
 * 
 * This is the recommended function for processing tablet/stylus input
 */
int32_t simjot_stroke_process_realtime(
    const float* input_x, const float* input_y, const float* timestamps,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float max_gap,
    int32_t chaikin_iterations
) {
    if (!input_x || !input_y || !output_x || !output_y || input_count < 2) {
        return -1;
    }
    
    if (max_gap <= 0) max_gap = 8.0f;
    if (chaikin_iterations < 0) chaikin_iterations = 2;
    if (chaikin_iterations > 3) chaikin_iterations = 3;
    
    /* Allocate work buffers */
    int32_t buf_size = input_count * 4; /* Account for interpolation expansion */
    if (buf_size > output_capacity) buf_size = output_capacity;
    
    float* temp_x = (float*)malloc(buf_size * sizeof(float));
    float* temp_y = (float*)malloc(buf_size * sizeof(float));
    float* temp_t = NULL;
    
    if (!temp_x || !temp_y) {
        free(temp_x);
        free(temp_y);
        return -1;
    }
    
    int32_t count = input_count;
    const float* src_x = input_x;
    const float* src_y = input_y;
    
    /* Step 1: Interpolate gaps */
    count = simjot_stroke_interpolate_gaps(src_x, src_y, count, temp_x, temp_y, buf_size, max_gap);
    if (count < 0) {
        free(temp_x);
        free(temp_y);
        return -1;
    }
    
    /* Step 2: Apply velocity-based smoothing if timestamps provided */
    if (timestamps != NULL && count >= 2) {
        /* Generate approximate timestamps for interpolated points */
        temp_t = (float*)malloc(count * sizeof(float));
        if (temp_t) {
            float total_time = timestamps[input_count - 1] - timestamps[0];
            for (int32_t i = 0; i < count; i++) {
                temp_t[i] = timestamps[0] + (total_time * i) / (count - 1);
            }
            
            simjot_stroke_velocity_smooth(temp_x, temp_y, temp_t, count,
                                          output_x, output_y, 0.5f, 0.5f);
            
            /* Copy back for next step */
            memcpy(temp_x, output_x, count * sizeof(float));
            memcpy(temp_y, output_y, count * sizeof(float));
            
            free(temp_t);
        }
    }
    
    /* Step 3: Apply Chaikin smoothing */
    if (chaikin_iterations > 0 && count >= 3) {
        count = simjot_stroke_chaikin(temp_x, temp_y, count, output_x, output_y,
                                       output_capacity, chaikin_iterations);
    } else {
        memcpy(output_x, temp_x, count * sizeof(float));
        memcpy(output_y, temp_y, count * sizeof(float));
    }
    
    free(temp_x);
    free(temp_y);
    
    return count;
}

/**
 * @brief Compute quadratic Bezier control points for smooth curve rendering
 * Given a series of points, computes control points for quadTo() calls
 * 
 * Output format: For N input points, outputs (N-1)*3 floats:
 * [cp1_x, cp1_y, end1_x, end1_y, cp2_x, cp2_y, end2_x, end2_y, ...]
 */
int32_t simjot_stroke_bezier_control_points(
    const float* x, const float* y,
    int32_t count,
    float* control_points,
    int32_t control_capacity
) {
    if (!x || !y || !control_points || count < 3) {
        return -1;
    }
    
    int32_t needed = (count - 2) * 4; /* Each segment needs: cpX, cpY, endX, endY */
    if (control_capacity < needed) {
        return -1;
    }
    
    int32_t out_idx = 0;
    
    for (int32_t i = 1; i < count - 1; i++) {
        /* Control point is the actual point */
        control_points[out_idx++] = x[i];
        control_points[out_idx++] = y[i];
        
        /* End point is midpoint to next */
        control_points[out_idx++] = (x[i] + x[i + 1]) * 0.5f;
        control_points[out_idx++] = (y[i] + y[i + 1]) * 0.5f;
    }
    
    return out_idx;
}

/**
 * @brief One-Euro filter for real-time low-latency smoothing
 * Excellent for stylus/pen input - adapts smoothing based on speed
 */
typedef struct {
    float x_prev, y_prev;
    float dx_prev, dy_prev;
    float t_prev;
    float min_cutoff;
    float beta;
    float d_cutoff;
    int initialized;
} OneEuroFilter;

static float one_euro_alpha(float cutoff, float dt) {
    float tau = 1.0f / (2.0f * 3.14159265f * cutoff);
    return 1.0f / (1.0f + tau / dt);
}

int32_t simjot_stroke_one_euro_init(
    void** filter_out,
    float min_cutoff,
    float beta,
    float d_cutoff
) {
    if (!filter_out) return -1;
    
    OneEuroFilter* f = (OneEuroFilter*)calloc(1, sizeof(OneEuroFilter));
    if (!f) return -1;
    
    f->min_cutoff = (min_cutoff > 0) ? min_cutoff : 1.0f;
    f->beta = (beta >= 0) ? beta : 0.007f;
    f->d_cutoff = (d_cutoff > 0) ? d_cutoff : 1.0f;
    f->initialized = 0;
    
    *filter_out = f;
    return 0;
}

int32_t simjot_stroke_one_euro_filter(
    void* filter,
    float x, float y, float t,
    float* out_x, float* out_y
) {
    if (!filter || !out_x || !out_y) return -1;
    
    OneEuroFilter* f = (OneEuroFilter*)filter;
    
    if (!f->initialized) {
        f->x_prev = x;
        f->y_prev = y;
        f->dx_prev = 0;
        f->dy_prev = 0;
        f->t_prev = t;
        f->initialized = 1;
        *out_x = x;
        *out_y = y;
        return 0;
    }
    
    float dt = t - f->t_prev;
    if (dt <= 0) dt = 0.001f;
    
    /* Compute derivative */
    float dx = (x - f->x_prev) / dt;
    float dy = (y - f->y_prev) / dt;
    
    /* Filter derivative */
    float alpha_d = one_euro_alpha(f->d_cutoff, dt);
    float dx_hat = alpha_d * dx + (1.0f - alpha_d) * f->dx_prev;
    float dy_hat = alpha_d * dy + (1.0f - alpha_d) * f->dy_prev;
    
    /* Compute cutoff based on derivative magnitude (speed) */
    float speed = sqrtf(dx_hat * dx_hat + dy_hat * dy_hat);
    float cutoff = f->min_cutoff + f->beta * speed;
    
    /* Filter position */
    float alpha = one_euro_alpha(cutoff, dt);
    *out_x = alpha * x + (1.0f - alpha) * f->x_prev;
    *out_y = alpha * y + (1.0f - alpha) * f->y_prev;
    
    /* Update state */
    f->x_prev = *out_x;
    f->y_prev = *out_y;
    f->dx_prev = dx_hat;
    f->dy_prev = dy_hat;
    f->t_prev = t;
    
    return 0;
}

void simjot_stroke_one_euro_free(void* filter) {
    free(filter);
}

/**
 * @brief Apply One-Euro filter to entire stroke (batch processing)
 */
int32_t simjot_stroke_one_euro_batch(
    const float* input_x, const float* input_y, const float* timestamps,
    int32_t count,
    float* output_x, float* output_y,
    float min_cutoff, float beta
) {
    if (!input_x || !input_y || !timestamps || !output_x || !output_y || count < 1) {
        return -1;
    }
    
    void* filter = NULL;
    if (simjot_stroke_one_euro_init(&filter, min_cutoff, beta, 1.0f) < 0) {
        return -1;
    }
    
    for (int32_t i = 0; i < count; i++) {
        simjot_stroke_one_euro_filter(filter, input_x[i], input_y[i], timestamps[i],
                                       &output_x[i], &output_y[i]);
    }
    
    simjot_stroke_one_euro_free(filter);
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GEOMETRY HELPERS FOR DRAWING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate stroke bounds (bounding box)
 */
int32_t simjot_nt_stroke_bounds(
    const float* x, const float* y, int32_t count,
    float* out_min_x, float* out_min_y, float* out_max_x, float* out_max_y
) {
    if (!x || !y || count < 1) return -1;
    
    float min_x = x[0], max_x = x[0];
    float min_y = y[0], max_y = y[0];
    
    for (int32_t i = 1; i < count; i++) {
        if (x[i] < min_x) min_x = x[i];
        if (x[i] > max_x) max_x = x[i];
        if (y[i] < min_y) min_y = y[i];
        if (y[i] > max_y) max_y = y[i];
    }
    
    if (out_min_x) *out_min_x = min_x;
    if (out_min_y) *out_min_y = min_y;
    if (out_max_x) *out_max_x = max_x;
    if (out_max_y) *out_max_y = max_y;
    
    return 0;
}

/**
 * @brief Calculate stroke length (sum of segment lengths)
 */
float simjot_nt_stroke_length(const float* x, const float* y, int32_t count) {
    if (!x || !y || count < 2) return 0;
    
    float length = 0;
    for (int32_t i = 1; i < count; i++) {
        float dx = x[i] - x[i-1];
        float dy = y[i] - y[i-1];
        length += sqrtf(dx * dx + dy * dy);
    }
    
    return length;
}

/**
 * @brief Calculate perpendicular offset points for stroke width
 * Used for creating stroke outlines with variable width
 */
int32_t simjot_nt_stroke_outline(
    const float* x, const float* y, const float* width,
    int32_t count,
    float* left_x, float* left_y,
    float* right_x, float* right_y
) {
    if (!x || !y || !width || count < 2) return -1;
    if (!left_x || !left_y || !right_x || !right_y) return -1;
    
    for (int32_t i = 0; i < count; i++) {
        float nx, ny;
        
        if (i == 0) {
            /* First point: use direction to next */
            float dx = x[1] - x[0];
            float dy = y[1] - y[0];
            float len = sqrtf(dx * dx + dy * dy);
            if (len > 0.0001f) {
                nx = -dy / len;
                ny = dx / len;
            } else {
                nx = 0; ny = 1;
            }
        } else if (i == count - 1) {
            /* Last point: use direction from prev */
            float dx = x[i] - x[i-1];
            float dy = y[i] - y[i-1];
            float len = sqrtf(dx * dx + dy * dy);
            if (len > 0.0001f) {
                nx = -dy / len;
                ny = dx / len;
            } else {
                nx = 0; ny = 1;
            }
        } else {
            /* Middle point: average normals */
            float dx1 = x[i] - x[i-1];
            float dy1 = y[i] - y[i-1];
            float dx2 = x[i+1] - x[i];
            float dy2 = y[i+1] - y[i];
            
            float len1 = sqrtf(dx1 * dx1 + dy1 * dy1);
            float len2 = sqrtf(dx2 * dx2 + dy2 * dy2);
            
            if (len1 > 0.0001f && len2 > 0.0001f) {
                float nx1 = -dy1 / len1;
                float ny1 = dx1 / len1;
                float nx2 = -dy2 / len2;
                float ny2 = dx2 / len2;
                nx = (nx1 + nx2) * 0.5f;
                ny = (ny1 + ny2) * 0.5f;
                float nlen = sqrtf(nx * nx + ny * ny);
                if (nlen > 0.0001f) {
                    nx /= nlen;
                    ny /= nlen;
                }
            } else {
                nx = 0; ny = 1;
            }
        }
        
        float half_w = width[i] * 0.5f;
        left_x[i] = x[i] + nx * half_w;
        left_y[i] = y[i] + ny * half_w;
        right_x[i] = x[i] - nx * half_w;
        right_y[i] = y[i] - ny * half_w;
    }
    
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LIST FORMATTING UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Check if a line starts with a bullet point
 * @param line The line to check (null-terminated)
 * @return 1 if the line starts with a bullet, 0 otherwise
 */
int32_t simjot_list_is_bulleted(const char* line) {
    if (!line) return 0;
    
    /* Skip leading whitespace */
    while (*line == ' ' || *line == '\t') line++;
    
    /* Check for common bullet characters: •, -, *, ● */
    if (*line == '-' || *line == '*') {
        return (line[1] == ' ' || line[1] == '\t') ? 1 : 0;
    }
    
    /* Check for Unicode bullet • (UTF-8: 0xE2 0x80 0xA2) */
    if ((unsigned char)line[0] == 0xE2 && 
        (unsigned char)line[1] == 0x80 && 
        (unsigned char)line[2] == 0xA2) {
        return 1;
    }
    
    /* Check for Unicode bullet ● (UTF-8: 0xE2 0x97 0x8F) */
    if ((unsigned char)line[0] == 0xE2 && 
        (unsigned char)line[1] == 0x97 && 
        (unsigned char)line[2] == 0x8F) {
        return 1;
    }
    
    return 0;
}

/**
 * @brief Check if a line starts with a number (numbered list)
 * @param line The line to check (null-terminated)
 * @return The number if found (1-999), 0 if not a numbered line
 */
int32_t simjot_list_get_number(const char* line) {
    if (!line) return 0;
    
    /* Skip leading whitespace */
    while (*line == ' ' || *line == '\t') line++;
    
    /* Parse number */
    int32_t num = 0;
    int digits = 0;
    while (*line >= '0' && *line <= '9' && digits < 3) {
        num = num * 10 + (*line - '0');
        line++;
        digits++;
    }
    
    if (digits == 0) return 0;
    
    /* Check for period or parenthesis followed by space */
    if ((*line == '.' || *line == ')') && 
        (line[1] == ' ' || line[1] == '\t' || line[1] == '\0')) {
        return num;
    }
    
    return 0;
}

/**
 * @brief Add bullet points to text lines
 * @param input Input text (newline-separated lines)
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @param bullet_char The bullet character to use (UTF-8 encoded)
 * @return Number of bytes written, or -1 on error
 */
int32_t simjot_list_add_bullets(const char* input, char* output, int32_t output_len, const char* bullet_char) {
    if (!input || !output || output_len < 1) return -1;
    
    const char* default_bullet = "\xE2\x80\xA2 "; /* • followed by space */
    const char* bullet = (bullet_char && bullet_char[0]) ? bullet_char : default_bullet;
    int32_t bullet_len = 0;
    while (bullet[bullet_len]) bullet_len++;
    
    int32_t out_pos = 0;
    int32_t line_start = 1;
    
    while (*input && out_pos < output_len - 1) {
        if (line_start) {
            /* Skip empty lines */
            int is_empty = (*input == '\n' || *input == '\0');
            const char* check = input;
            while (*check == ' ' || *check == '\t') check++;
            if (*check == '\n' || *check == '\0') is_empty = 1;
            
            if (!is_empty && !simjot_list_is_bulleted(input)) {
                /* Add bullet */
                for (int i = 0; i < bullet_len && out_pos < output_len - 1; i++) {
                    output[out_pos++] = bullet[i];
                }
            }
            line_start = 0;
        }
        
        if (*input == '\n') {
            line_start = 1;
        }
        
        if (out_pos < output_len - 1) {
            output[out_pos++] = *input++;
        } else {
            break;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Add numbers to text lines
 * @param input Input text (newline-separated lines)
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @param start_num Starting number (typically 1)
 * @return Number of bytes written, or -1 on error
 */
int32_t simjot_list_add_numbers(const char* input, char* output, int32_t output_len, int32_t start_num) {
    if (!input || !output || output_len < 1) return -1;
    if (start_num < 1) start_num = 1;
    
    int32_t out_pos = 0;
    int32_t current_num = start_num;
    int32_t line_start = 1;
    
    while (*input && out_pos < output_len - 1) {
        if (line_start) {
            /* Skip empty lines */
            int is_empty = (*input == '\n' || *input == '\0');
            const char* check = input;
            while (*check == ' ' || *check == '\t') check++;
            if (*check == '\n' || *check == '\0') is_empty = 1;
            
            if (!is_empty && simjot_list_get_number(input) == 0) {
                /* Add number */
                char num_buf[16];
                int num_len = 0;
                int32_t n = current_num++;
                
                /* Convert number to string */
                if (n == 0) {
                    num_buf[num_len++] = '0';
                } else {
                    char temp[16];
                    int temp_len = 0;
                    while (n > 0) {
                        temp[temp_len++] = '0' + (n % 10);
                        n /= 10;
                    }
                    while (temp_len > 0) {
                        num_buf[num_len++] = temp[--temp_len];
                    }
                }
                num_buf[num_len++] = '.';
                num_buf[num_len++] = ' ';
                num_buf[num_len] = '\0';
                
                for (int i = 0; i < num_len && out_pos < output_len - 1; i++) {
                    output[out_pos++] = num_buf[i];
                }
            }
            line_start = 0;
        }
        
        if (*input == '\n') {
            line_start = 1;
        }
        
        if (out_pos < output_len - 1) {
            output[out_pos++] = *input++;
        } else {
            break;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Strip list prefixes (bullets or numbers) from text
 * @param input Input text (newline-separated lines)
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Number of bytes written, or -1 on error
 */
int32_t simjot_list_strip_prefix(const char* input, char* output, int32_t output_len) {
    if (!input || !output || output_len < 1) return -1;
    
    int32_t out_pos = 0;
    int32_t line_start = 1;
    
    while (*input && out_pos < output_len - 1) {
        if (line_start) {
            /* Skip leading whitespace but remember position */
            const char* line_begin = input;
            while (*input == ' ' || *input == '\t') input++;
            
            /* Check and skip bullet */
            if (simjot_list_is_bulleted(input)) {
                /* Skip bullet character */
                if (*input == '-' || *input == '*') {
                    input++;
                } else if ((unsigned char)*input == 0xE2) {
                    input += 3; /* Skip UTF-8 bullet */
                }
                /* Skip trailing space */
                if (*input == ' ' || *input == '\t') input++;
            }
            /* Check and skip number */
            else if (simjot_list_get_number(input) > 0) {
                /* Skip digits */
                while (*input >= '0' && *input <= '9') input++;
                /* Skip period/paren */
                if (*input == '.' || *input == ')') input++;
                /* Skip space */
                if (*input == ' ' || *input == '\t') input++;
            }
            /* If no list prefix found, restore leading whitespace */
            else {
                while (line_begin < input) {
                    if (out_pos < output_len - 1) {
                        output[out_pos++] = *line_begin++;
                    }
                }
            }
            
            line_start = 0;
        }
        
        if (*input == '\n') {
            line_start = 1;
        }
        
        if (*input && out_pos < output_len - 1) {
            output[out_pos++] = *input++;
        } else if (*input) {
            break;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}
