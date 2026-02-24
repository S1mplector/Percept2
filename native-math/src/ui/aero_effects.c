/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file aero_effects.c
 * @brief Native Aero/Glass UI Effect Computations for Simjot
 * 
 * High-performance implementations of Windows Aero-style glass effects:
 * - Outer glow alpha computation (ease-out curve)
 * - Inner shadow alpha computation (linear fade)
 * - Gradient color interpolation
 * - Alpha blending for ARGB pixels
 * - Pre-computed lookup tables for common operations
 * 
 * These functions are called from Java via Panama FFM to accelerate
 * UI rendering of glass panels, glowing buttons, and shadow effects.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * LOOKUP TABLES FOR FAST ALPHA CALCULATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Pre-computed ease-out (quadratic) lookup table for outer glow */
static uint8_t g_ease_out_lut[256];
/* Pre-computed linear fade lookup table for inner shadow */
static uint8_t g_linear_fade_lut[256];
/* Initialization flag */
static int g_aero_luts_initialized = 0;

/**
 * Initialize lookup tables for fast alpha calculations.
 * Called automatically on first use.
 */
static void init_aero_luts(void) {
    if (g_aero_luts_initialized) return;
    
    for (int i = 0; i < 256; i++) {
        float t = (float)i / 255.0f;
        /* Ease-out quadratic: alpha = t^2 (used for outer glow) */
        g_ease_out_lut[i] = (uint8_t)(t * t * 255.0f + 0.5f);
        /* Linear fade: alpha = 1 - t (used for inner shadow) */
        g_linear_fade_lut[i] = (uint8_t)((1.0f - t) * 255.0f + 0.5f);
    }
    
    g_aero_luts_initialized = 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * OUTER GLOW ALPHA COMPUTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute alpha values for an outer glow effect.
 * 
 * The glow uses an ease-out quadratic curve: alpha = maxAlpha * (i/size)^2
 * This creates a soft falloff from the edge.
 * 
 * @param size Number of glow layers (typically 3-10)
 * @param max_alpha Maximum alpha value (0-255)
 * @param out_alphas Output array of size 'size' to store computed alphas
 */
void simjot_aero_outer_glow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas) {
    if (size <= 0 || max_alpha <= 0 || out_alphas == NULL) return;
    
    init_aero_luts();
    
    max_alpha = max_alpha > 255 ? 255 : (max_alpha < 0 ? 0 : max_alpha);
    
    for (int i = size; i >= 1; i--) {
        /* t = i / size, alpha = maxAlpha * t^2 */
        int lut_index = (i * 255) / size;
        int alpha = (g_ease_out_lut[lut_index] * max_alpha) / 255;
        out_alphas[size - i] = (uint8_t)(alpha > 255 ? 255 : alpha);
    }
}

/**
 * Compute a single outer glow alpha value.
 * 
 * @param layer Current layer (1 to size, where 1 is innermost)
 * @param size Total number of layers
 * @param max_alpha Maximum alpha (0-255)
 * @return Computed alpha value
 */
int32_t simjot_aero_outer_glow_alpha(int32_t layer, int32_t size, int32_t max_alpha) {
    if (size <= 0 || layer <= 0 || layer > size) return 0;
    
    init_aero_luts();
    
    max_alpha = max_alpha > 255 ? 255 : (max_alpha < 0 ? 0 : max_alpha);
    
    float t = (float)layer / (float)size;
    int alpha = (int)(max_alpha * t * t + 0.5f);
    return alpha > 255 ? 255 : alpha;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * INNER SHADOW ALPHA COMPUTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute alpha values for an inner shadow effect.
 * 
 * The shadow uses linear fade: alpha = maxAlpha * (1 - i/size)
 * This creates a sharp edge that fades toward the center.
 * 
 * @param size Number of shadow layers (typically 2-5)
 * @param max_alpha Maximum alpha value (0-255)
 * @param out_alphas Output array of size 'size' to store computed alphas
 */
void simjot_aero_inner_shadow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas) {
    if (size <= 0 || max_alpha <= 0 || out_alphas == NULL) return;
    
    init_aero_luts();
    
    max_alpha = max_alpha > 255 ? 255 : (max_alpha < 0 ? 0 : max_alpha);
    
    for (int i = 1; i <= size; i++) {
        /* t = i / size, alpha = maxAlpha * (1 - t) */
        int lut_index = (i * 255) / size;
        int alpha = (g_linear_fade_lut[lut_index] * max_alpha) / 255;
        out_alphas[i - 1] = (uint8_t)(alpha > 255 ? 255 : alpha);
    }
}

/**
 * Compute a single inner shadow alpha value.
 * 
 * @param layer Current layer (1 to size, where 1 is outermost)
 * @param size Total number of layers
 * @param max_alpha Maximum alpha (0-255)
 * @return Computed alpha value
 */
int32_t simjot_aero_inner_shadow_alpha(int32_t layer, int32_t size, int32_t max_alpha) {
    if (size <= 0 || layer <= 0 || layer > size) return 0;
    
    max_alpha = max_alpha > 255 ? 255 : (max_alpha < 0 ? 0 : max_alpha);
    
    float t = (float)layer / (float)size;
    int alpha = (int)(max_alpha * (1.0f - t) + 0.5f);
    return alpha > 255 ? 255 : (alpha < 0 ? 0 : alpha);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GRADIENT COLOR INTERPOLATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Interpolate between two ARGB colors.
 * 
 * @param color1 First color (ARGB format)
 * @param color2 Second color (ARGB format)
 * @param t Interpolation factor (0.0 = color1, 1.0 = color2)
 * @return Interpolated color in ARGB format
 */
int32_t simjot_aero_lerp_color(int32_t color1, int32_t color2, float t) {
    if (t <= 0.0f) return color1;
    if (t >= 1.0f) return color2;
    
    int a1 = (color1 >> 24) & 0xFF;
    int r1 = (color1 >> 16) & 0xFF;
    int g1 = (color1 >> 8) & 0xFF;
    int b1 = color1 & 0xFF;
    
    int a2 = (color2 >> 24) & 0xFF;
    int r2 = (color2 >> 16) & 0xFF;
    int g2 = (color2 >> 8) & 0xFF;
    int b2 = color2 & 0xFF;
    
    int a = (int)(a1 + (a2 - a1) * t + 0.5f);
    int r = (int)(r1 + (r2 - r1) * t + 0.5f);
    int g = (int)(g1 + (g2 - g1) * t + 0.5f);
    int b = (int)(b1 + (b2 - b1) * t + 0.5f);
    
    return (a << 24) | (r << 16) | (g << 8) | b;
}

/**
 * Generate a vertical gradient color array.
 * 
 * @param top_color Top color (ARGB format)
 * @param bottom_color Bottom color (ARGB format)
 * @param height Number of rows
 * @param out_colors Output array of 'height' colors
 */
void simjot_aero_gradient_colors(int32_t top_color, int32_t bottom_color, 
                                  int32_t height, int32_t* out_colors) {
    if (height <= 0 || out_colors == NULL) return;
    
    if (height == 1) {
        out_colors[0] = top_color;
        return;
    }
    
    for (int y = 0; y < height; y++) {
        float t = (float)y / (float)(height - 1);
        out_colors[y] = simjot_aero_lerp_color(top_color, bottom_color, t);
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ALPHA BLENDING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Blend a color with alpha over a background color.
 * 
 * Uses standard Porter-Duff "over" compositing.
 * 
 * @param fg Foreground color with alpha (ARGB)
 * @param bg Background color (ARGB, alpha ignored)
 * @return Blended color (ARGB with alpha=255)
 */
int32_t simjot_aero_blend_over(int32_t fg, int32_t bg) {
    int fg_a = (fg >> 24) & 0xFF;
    if (fg_a == 255) return fg | 0xFF000000;
    if (fg_a == 0) return bg | 0xFF000000;
    
    int fg_r = (fg >> 16) & 0xFF;
    int fg_g = (fg >> 8) & 0xFF;
    int fg_b = fg & 0xFF;
    
    int bg_r = (bg >> 16) & 0xFF;
    int bg_g = (bg >> 8) & 0xFF;
    int bg_b = bg & 0xFF;
    
    /* Standard alpha blend: out = fg * alpha + bg * (1 - alpha) */
    int inv_a = 255 - fg_a;
    int r = (fg_r * fg_a + bg_r * inv_a + 127) / 255;
    int g = (fg_g * fg_a + bg_g * inv_a + 127) / 255;
    int b = (fg_b * fg_a + bg_b * inv_a + 127) / 255;
    
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}

/**
 * Apply alpha to a color (premultiply).
 * 
 * @param color Original color (ARGB)
 * @param alpha Alpha to apply (0-255)
 * @return Color with new alpha applied
 */
int32_t simjot_aero_apply_alpha(int32_t color, int32_t alpha) {
    if (alpha <= 0) return color & 0x00FFFFFF;
    if (alpha >= 255) return color;
    
    int orig_a = (color >> 24) & 0xFF;
    int new_a = (orig_a * alpha + 127) / 255;
    
    return (new_a << 24) | (color & 0x00FFFFFF);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * GLASS PANEL EFFECT COLORS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute glass panel overlay colors for a given height.
 * 
 * Creates the classic Windows Aero glass effect:
 * - Top half: bright white fade (sheen)
 * - Bottom half: subtle dark fade (depth)
 * 
 * @param height Panel height in pixels
 * @param out_sheen_alphas Output array for top half alphas (size = height/2)
 * @param out_shadow_alphas Output array for bottom half alphas (size = height - height/2)
 */
void simjot_aero_glass_overlay(int32_t height, uint8_t* out_sheen_alphas, uint8_t* out_shadow_alphas) {
    if (height <= 0) return;
    
    int half = height / 2;
    
    /* Top half: sheen (white fading from 110 to 20) */
    if (out_sheen_alphas != NULL && half > 0) {
        for (int y = 0; y < half; y++) {
            float t = (float)y / (float)half;
            int alpha = (int)(110.0f + (20.0f - 110.0f) * t + 0.5f);
            out_sheen_alphas[y] = (uint8_t)(alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha));
        }
    }
    
    /* Bottom half: shadow (black fading from 10 to 35) */
    int bottom_height = height - half;
    if (out_shadow_alphas != NULL && bottom_height > 0) {
        for (int y = 0; y < bottom_height; y++) {
            float t = (float)y / (float)bottom_height;
            int alpha = (int)(10.0f + (35.0f - 10.0f) * t + 0.5f);
            out_shadow_alphas[y] = (uint8_t)(alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha));
        }
    }
}

/**
 * Compute frosted glass panel colors.
 * 
 * Creates a frosted glass effect with:
 * - Base gradient (white to light gray, semi-transparent)
 * - Sheen overlay (bright top fade)
 * - Shadow overlay (dark bottom fade)
 * 
 * @param height Panel height
 * @param base_top_alpha Alpha for top of base (e.g., 205)
 * @param base_bottom_alpha Alpha for bottom of base (e.g., 150)
 * @param out_base_alphas Output array for base gradient alphas
 */
void simjot_aero_frosted_glass(int32_t height, int32_t base_top_alpha, 
                                int32_t base_bottom_alpha, uint8_t* out_base_alphas) {
    if (height <= 0 || out_base_alphas == NULL) return;
    
    for (int y = 0; y < height; y++) {
        float t = (height > 1) ? (float)y / (float)(height - 1) : 0.0f;
        int alpha = (int)(base_top_alpha + (base_bottom_alpha - base_top_alpha) * t + 0.5f);
        out_base_alphas[y] = (uint8_t)(alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha));
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE WIDTH CALCULATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute stroke widths for outer glow layers.
 * 
 * @param size Number of glow layers
 * @param out_widths Output array for stroke widths (as floats scaled by 100)
 */
void simjot_aero_glow_stroke_widths(int32_t size, int32_t* out_widths) {
    if (size <= 0 || out_widths == NULL) return;
    
    for (int i = size; i >= 1; i--) {
        /* width = i * 2.0 (stored as int * 100 for precision) */
        out_widths[size - i] = i * 200;
    }
}

/**
 * Compute inset amounts for inner shadow layers.
 * 
 * @param size Number of shadow layers
 * @param out_insets Output array for inset amounts (1, 2, 3, ...)
 */
void simjot_aero_shadow_insets(int32_t size, int32_t* out_insets) {
    if (size <= 0 || out_insets == NULL) return;
    
    for (int i = 1; i <= size; i++) {
        out_insets[i - 1] = i;
    }
}
