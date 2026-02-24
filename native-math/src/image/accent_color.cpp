/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file accent_color.cpp
 * @brief Native Accent Color Extraction for Simjot
 * 
 * High-performance accent color extraction from images using
 * hue histogram analysis with saturation/brightness weighting.
 * 
 * Algorithm:
 * 1. Downscale image to 64x64 for fast processing
 * 2. Build hue histogram (36 bins, 10° each) weighted by sat² × brightness
 * 3. Find dominant hue bin
 * 4. Compute weighted average RGB for that bin
 * 5. Boost saturation slightly for vibrant accent
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. MIT License.
 */

#include "../include/simjot_native.h"

#include <cstdint>
#include <cmath>
#include <cstring>
#include <algorithm>

namespace {

constexpr int HUE_BINS = 36;        // 10-degree bins
constexpr int SAMPLE_SIZE = 64;     // Downscale target

struct RGB {
    uint8_t r, g, b;
};

struct HSB {
    float h, s, b;
};

// RGB to HSB conversion (matches Java's Color.RGBtoHSB)
HSB rgb_to_hsb(uint8_t r, uint8_t g, uint8_t b) {
    float rf = r / 255.0f;
    float gf = g / 255.0f;
    float bf = b / 255.0f;
    
    float max_val = std::max({rf, gf, bf});
    float min_val = std::min({rf, gf, bf});
    float delta = max_val - min_val;
    
    HSB hsb;
    hsb.b = max_val;  // brightness
    
    if (max_val == 0.0f) {
        hsb.s = 0.0f;
        hsb.h = 0.0f;
        return hsb;
    }
    
    hsb.s = delta / max_val;  // saturation
    
    if (delta == 0.0f) {
        hsb.h = 0.0f;
        return hsb;
    }
    
    // Hue calculation
    float hue;
    if (rf == max_val) {
        hue = (gf - bf) / delta;
    } else if (gf == max_val) {
        hue = 2.0f + (bf - rf) / delta;
    } else {
        hue = 4.0f + (rf - gf) / delta;
    }
    
    hue /= 6.0f;
    if (hue < 0.0f) hue += 1.0f;
    
    hsb.h = hue;
    return hsb;
}

// HSB to RGB conversion (matches Java's Color.getHSBColor)
RGB hsb_to_rgb(float h, float s, float b) {
    RGB rgb;
    
    if (s == 0.0f) {
        uint8_t v = static_cast<uint8_t>(b * 255.0f + 0.5f);
        rgb.r = rgb.g = rgb.b = v;
        return rgb;
    }
    
    h = h - std::floor(h);  // Normalize to [0, 1)
    float hue6 = h * 6.0f;
    int i = static_cast<int>(hue6);
    float f = hue6 - i;
    float p = b * (1.0f - s);
    float q = b * (1.0f - s * f);
    float t = b * (1.0f - s * (1.0f - f));
    
    float rf, gf, bf;
    switch (i % 6) {
        case 0: rf = b; gf = t; bf = p; break;
        case 1: rf = q; gf = b; bf = p; break;
        case 2: rf = p; gf = b; bf = t; break;
        case 3: rf = p; gf = q; bf = b; break;
        case 4: rf = t; gf = p; bf = b; break;
        default: rf = b; gf = p; bf = q; break;
    }
    
    rgb.r = static_cast<uint8_t>(rf * 255.0f + 0.5f);
    rgb.g = static_cast<uint8_t>(gf * 255.0f + 0.5f);
    rgb.b = static_cast<uint8_t>(bf * 255.0f + 0.5f);
    return rgb;
}

inline uint8_t clamp_u8(int v) {
    return static_cast<uint8_t>(v < 0 ? 0 : (v > 255 ? 255 : v));
}

} // anonymous namespace

extern "C" {

/**
 * @brief Extract dominant accent color from RGBA image data
 * 
 * @param pixels RGBA pixel data (4 bytes per pixel, row-major)
 * @param width Image width
 * @param height Image height
 * @param stride Bytes per row (typically width * 4)
 * @return Packed RGB color (0x00RRGGBB), or 0 on error
 */
int32_t simjot_image_extract_accent(
    const uint8_t* pixels,
    int32_t width,
    int32_t height,
    int32_t stride
) {
    if (!pixels || width <= 0 || height <= 0 || stride < width * 4) {
        return 0;
    }
    
    // Hue histogram weighted by saturation² × brightness
    float hue_weight[HUE_BINS] = {0};
    float sum_rgb[HUE_BINS][3] = {{0}};
    int count[HUE_BINS] = {0};
    
    // Sampling step to effectively downscale to ~64x64
    int step_x = std::max(1, width / SAMPLE_SIZE);
    int step_y = std::max(1, height / SAMPLE_SIZE);
    
    for (int y = 0; y < height; y += step_y) {
        const uint8_t* row = pixels + y * stride;
        
        for (int x = 0; x < width; x += step_x) {
            const uint8_t* p = row + x * 4;
            uint8_t r = p[0];
            uint8_t g = p[1];
            uint8_t b = p[2];
            uint8_t a = p[3];
            
            // Skip transparent pixels
            if (a < 20) continue;
            
            // Skip near-grey (low chroma) and extremes
            int max_c = std::max({r, g, b});
            int min_c = std::min({r, g, b});
            if (max_c - min_c < 18) continue;  // Low chroma
            if (max_c < 24 || min_c > 232) continue;  // Too dark/bright
            
            HSB hsb = rgb_to_hsb(r, g, b);
            
            // Avoid dull colors
            if (hsb.s < 0.2f || hsb.b < 0.25f) continue;
            
            // Bin selection (0-35 for 36 bins)
            int bin = std::min(35, std::max(0, static_cast<int>(hsb.h * 36.0f)));
            
            // Weight: saturation² × (0.5 + 0.5 × brightness)
            float w = hsb.s * hsb.s * (0.5f + 0.5f * hsb.b);
            
            hue_weight[bin] += w;
            sum_rgb[bin][0] += r * w;
            sum_rgb[bin][1] += g * w;
            sum_rgb[bin][2] += b * w;
            count[bin]++;
        }
    }
    
    // Find dominant hue bin
    int best_bin = -1;
    float best_weight = 0.0f;
    for (int i = 0; i < HUE_BINS; i++) {
        if (hue_weight[i] > best_weight) {
            best_weight = hue_weight[i];
            best_bin = i;
        }
    }
    
    // Default accent color (Windows blue: 0, 120, 215)
    if (best_bin < 0 || best_weight <= 0.0f) {
        return 0x000078D7;  // Default blue
    }
    
    // Compute weighted average RGB for dominant bin
    float rf = sum_rgb[best_bin][0] / best_weight;
    float gf = sum_rgb[best_bin][1] / best_weight;
    float bf = sum_rgb[best_bin][2] / best_weight;
    
    uint8_t r = clamp_u8(static_cast<int>(rf + 0.5f));
    uint8_t g = clamp_u8(static_cast<int>(gf + 0.5f));
    uint8_t b = clamp_u8(static_cast<int>(bf + 0.5f));
    
    // Boost saturation slightly for vibrant accent
    HSB hsb = rgb_to_hsb(r, g, b);
    hsb.s = std::min(1.0f, hsb.s * 1.1f + 0.05f);
    hsb.b = std::min(1.0f, hsb.b * 1.02f);
    
    RGB result = hsb_to_rgb(hsb.h, hsb.s, hsb.b);
    
    return (result.r << 16) | (result.g << 8) | result.b;
}

/**
 * @brief Extract accent from pre-downscaled 64x64 RGBA image
 * 
 * Optimized version when caller has already downscaled the image.
 * 
 * @param pixels64 RGBA pixel data for 64x64 image (16384 bytes)
 * @return Packed RGB color (0x00RRGGBB), or 0 on error
 */
int32_t simjot_image_extract_accent_64(const uint8_t* pixels64) {
    if (!pixels64) return 0;
    return simjot_image_extract_accent(pixels64, 64, 64, 64 * 4);
}

} // extern "C"
