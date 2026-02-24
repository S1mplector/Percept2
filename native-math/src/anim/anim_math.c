/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file anim_math.c
 * @brief Native Animation Math for Simjot
 * 
 * High-performance animation calculations:
 * - Easing functions (cosine, cubic, spring)
 * - Heart beat pulse with spring overshoot
 * - ECG waveform generation
 * - Fade transition alpha calculation
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * EASING FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Clamp value to [0, 1] range
 */
static inline float clamp01(float t) {
    return t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
}

/**
 * @brief Cosine ease-in-out: smooth acceleration/deceleration
 * Returns value in [0, 1] for t in [0, 1]
 */
float simjot_ease_cosine(float t) {
    t = clamp01(t);
    return (1.0f - cosf(t * (float)M_PI)) * 0.5f;
}

/**
 * @brief Smootherstep (Ken Perlin's improved smoothstep): 6t^5 - 15t^4 + 10t^3
 * Very smooth with zero 1st and 2nd derivatives at endpoints
 */
float simjot_ease_smootherstep(float t) {
    t = clamp01(t);
    return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
}

/**
 * @brief Smoothstep cubic: 3t^2 - 2t^3
 * Classic smooth easing function
 */
float simjot_ease_smoothstep(float t) {
    t = clamp01(t);
    return t * t * (3.0f - 2.0f * t);
}

/**
 * @brief Ease out cubic: 1 - (1-t)^3
 * Quick start, slow end
 */
float simjot_ease_out_cubic(float t) {
    t = clamp01(t);
    float inv = 1.0f - t;
    return 1.0f - inv * inv * inv;
}

/**
 * @brief Ease in cubic: t^3
 * Slow start, quick end
 */
float simjot_ease_in_cubic(float t) {
    t = clamp01(t);
    return t * t * t;
}

/**
 * @brief Ease in-out cubic
 */
float simjot_ease_in_out_cubic(float t) {
    t = clamp01(t);
    if (t < 0.5f) {
        return 4.0f * t * t * t;
    } else {
        float f = 2.0f * t - 2.0f;
        return 0.5f * f * f * f + 1.0f;
    }
}

/**
 * @brief Elastic ease out: overshoot with bounce-back
 */
float simjot_ease_elastic(float t) {
    t = clamp01(t);
    if (t == 0.0f || t == 1.0f) return t;
    float p = 0.3f;
    return powf(2.0f, -10.0f * t) * sinf((t - p / 4.0f) * (2.0f * (float)M_PI) / p) + 1.0f;
}

/**
 * @brief Spring decay: value *= damping each frame
 * @param current Current spring value
 * @param damping Decay factor (e.g., 0.90 for 10% decay per step)
 * @param threshold Below this, snap to zero
 * @return New spring value
 */
float simjot_spring_decay(float current, float damping, float threshold) {
    current *= damping;
    if (current < threshold && current > -threshold) {
        return 0.0f;
    }
    return current;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HEART BEAT ANIMATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate heart beat scale factor
 * 
 * @param phase Current animation phase (0 to 2π, wraps)
 * @param baseAmplitude Base pulsing amplitude (e.g., 0.06)
 * @param spring Current spring overshoot value
 * @return Scale factor (around 1.0)
 */
float simjot_heartbeat_scale(float phase, float baseAmplitude, float spring) {
    /* Cosine ease for smooth beat: (1 - cos(phase)) * 0.5 gives 0..1 */
    float eased = (1.0f - cosf(phase)) * 0.5f;
    /* Convert to -1..1 range and apply amplitude */
    float beat = baseAmplitude * (eased * 2.0f - 1.0f);
    return 1.0f + beat + spring;
}

/**
 * @brief Detect if we just hit peak of heartbeat
 * 
 * @param currentEased Current eased value (0..1)
 * @param lastEased Previous eased value
 * @param threshold Peak threshold (e.g., 0.98)
 * @return 1 if peak just occurred, 0 otherwise
 */
int32_t simjot_heartbeat_is_peak(float currentEased, float lastEased, float threshold) {
    return (currentEased > threshold && lastEased <= threshold) ? 1 : 0;
}

/**
 * @brief Full heartbeat state update
 * 
 * @param phase Pointer to current phase (will be updated)
 * @param phaseStep Amount to advance phase each call
 * @param spring Pointer to spring value (will be updated)
 * @param springDamping Damping factor for spring
 * @param springKick Spring value to add on peak
 * @param lastEased Pointer to last eased value (for peak detection)
 * @param baseAmplitude Base pulse amplitude
 * @param outScale Output: calculated scale factor
 * @param outPeaked Output: 1 if peaked this frame
 */
void simjot_heartbeat_update(
    float* phase, float phaseStep,
    float* spring, float springDamping, float springKick,
    float* lastEased, float baseAmplitude,
    float* outScale, int32_t* outPeaked
) {
    /* Advance phase */
    *phase += phaseStep;
    
    /* Calculate eased beat value */
    float eased = (1.0f - cosf(*phase)) * 0.5f;
    
    /* Detect peak */
    int32_t peaked = simjot_heartbeat_is_peak(eased, *lastEased, 0.98f);
    if (peaked) {
        *spring = springKick;
    }
    
    /* Decay spring */
    *spring = simjot_spring_decay(*spring, springDamping, 0.001f);
    
    /* Calculate scale */
    *outScale = 1.0f + baseAmplitude * (eased * 2.0f - 1.0f) + *spring;
    *outPeaked = peaked;
    *lastEased = eased;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ECG WAVEFORM GENERATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Gaussian function for ECG peaks
 */
static inline float gaussian(float x, float mean, float sigma, float amplitude) {
    float z = (x - mean) / sigma;
    return amplitude * expf(-0.5f * z * z);
}

/**
 * @brief Sample ECG waveform at given phase (0..1)
 * 
 * Generates realistic ECG shape with:
 * - P wave (small bump)
 * - QRS complex (sharp spike)
 * - T wave (recovery bump)
 * 
 * @param phase Position in heartbeat cycle (0..1)
 * @return Waveform amplitude (-1 to 1 range approximately)
 */
float simjot_ecg_sample(float phase) {
    /* Wrap phase to 0..1 */
    phase = phase - floorf(phase);
    
    /* P wave: small positive bump before QRS */
    float p = gaussian(phase, 0.20f, 0.02f, 0.15f);
    
    /* Q wave: small negative dip */
    float q = gaussian(phase, 0.45f, 0.012f, -0.15f);
    
    /* R wave: main positive spike */
    float r = gaussian(phase, 0.50f, 0.006f, 1.0f);
    
    /* S wave: negative dip after R */
    float s = gaussian(phase, 0.55f, 0.012f, -0.25f);
    
    /* T wave: recovery bump */
    float t = gaussian(phase, 0.70f, 0.035f, 0.35f);
    
    /* Small baseline wander/noise */
    float noise = 0.02f * sinf(2.0f * (float)M_PI * phase * 3.0f);
    
    return p + q + r + s + t + noise;
}

/**
 * @brief Generate ECG waveform samples into buffer
 * 
 * @param startPhase Starting phase
 * @param phaseStep Step per sample
 * @param output Output buffer
 * @param count Number of samples to generate
 */
void simjot_ecg_generate(float startPhase, float phaseStep, float* output, int32_t count) {
    if (!output || count <= 0) return;
    
    float phase = startPhase;
    for (int32_t i = 0; i < count; i++) {
        output[i] = simjot_ecg_sample(phase);
        phase += phaseStep;
        if (phase >= 1.0f) phase -= 1.0f;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FADE TRANSITION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate fade alpha for transition
 * 
 * @param elapsedMs Time elapsed since start
 * @param durationMs Total duration
 * @param fadeOut True for fade-out (0→1), false for fade-in (1→0)
 * @param easingType 0=linear, 1=smoothstep, 2=smootherstep, 3=cosine
 * @return Alpha value (0..1), clamped
 */
float simjot_fade_alpha(int64_t elapsedMs, int64_t durationMs, int32_t fadeOut, int32_t easingType) {
    if (durationMs <= 0) return fadeOut ? 1.0f : 0.0f;
    
    float t = (float)elapsedMs / (float)durationMs;
    t = clamp01(t);
    
    float eased;
    switch (easingType) {
        case 1: /* smoothstep */
            eased = t * t * (3.0f - 2.0f * t);
            break;
        case 2: /* smootherstep */
            eased = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
            break;
        case 3: /* cosine */
            eased = (1.0f - cosf(t * (float)M_PI)) * 0.5f;
            break;
        default: /* linear */
            eased = t;
            break;
    }
    
    return fadeOut ? eased : (1.0f - eased);
}

/**
 * @brief Check if fade animation is complete
 * 
 * @param elapsedMs Time elapsed
 * @param durationMs Total duration
 * @return 1 if complete, 0 if still running
 */
int32_t simjot_fade_is_complete(int64_t elapsedMs, int64_t durationMs) {
    return elapsedMs >= durationMs ? 1 : 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * COLOR INTERPOLATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Linearly interpolate between two colors
 * 
 * @param color1 First color (ARGB)
 * @param color2 Second color (ARGB)
 * @param t Interpolation factor (0..1)
 * @return Interpolated color (ARGB)
 */
int32_t simjot_color_lerp(int32_t color1, int32_t color2, float t) {
    t = clamp01(t);
    
    int32_t a1 = (color1 >> 24) & 0xFF;
    int32_t r1 = (color1 >> 16) & 0xFF;
    int32_t g1 = (color1 >> 8) & 0xFF;
    int32_t b1 = color1 & 0xFF;
    
    int32_t a2 = (color2 >> 24) & 0xFF;
    int32_t r2 = (color2 >> 16) & 0xFF;
    int32_t g2 = (color2 >> 8) & 0xFF;
    int32_t b2 = color2 & 0xFF;
    
    int32_t a = (int32_t)(a1 + t * (a2 - a1) + 0.5f);
    int32_t r = (int32_t)(r1 + t * (r2 - r1) + 0.5f);
    int32_t g = (int32_t)(g1 + t * (g2 - g1) + 0.5f);
    int32_t b = (int32_t)(b1 + t * (b2 - b1) + 0.5f);
    
    return (a << 24) | (r << 16) | (g << 8) | b;
}

/**
 * @brief Apply easing to color interpolation
 */
int32_t simjot_color_lerp_eased(int32_t color1, int32_t color2, float t, int32_t easingType) {
    t = clamp01(t);
    
    float eased;
    switch (easingType) {
        case 1: eased = simjot_ease_smoothstep(t); break;
        case 2: eased = simjot_ease_smootherstep(t); break;
        case 3: eased = simjot_ease_cosine(t); break;
        default: eased = t; break;
    }
    
    return simjot_color_lerp(color1, color2, eased);
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * DISAPPEAR ANIMATION (for deleted items)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate disappear animation parameters
 * 
 * Creates a smooth disappear effect with:
 * - Fade out (alpha goes 1 → 0)
 * - Shrink (scale goes 1 → 0)
 * - Slide left (offset increases)
 * 
 * @param t Progress 0..1 (0=start, 1=fully gone)
 * @param outAlpha Output: opacity (1..0)
 * @param outScale Output: scale factor (1..0)
 * @param outOffsetX Output: horizontal slide offset
 */
void simjot_disappear_anim(float t, float* outAlpha, float* outScale, float* outOffsetX) {
    t = clamp01(t);
    
    /* Use smootherstep for very smooth animation */
    float eased = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    
    /* Alpha: fade out with slight delay at start */
    float alphaT = clamp01((t - 0.1f) / 0.7f);
    *outAlpha = 1.0f - (alphaT * alphaT);
    
    /* Scale: shrink with ease-out cubic */
    float scaleT = clamp01(t / 0.9f);
    float inv = 1.0f - scaleT;
    *outScale = 0.3f + 0.7f * (inv * inv); /* shrink to 30% then gone */
    
    /* Slide: accelerating slide to the left */
    *outOffsetX = eased * 50.0f; /* max 50px slide */
}

/**
 * @brief Calculate combined disappear value for simple use
 * Returns a single 0..1 value representing "visibility"
 */
float simjot_disappear_value(float t) {
    t = clamp01(t);
    /* Smootherstep with slight overshoot feel */
    float eased = t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    return 1.0f - eased;
}

/**
 * @brief Calculate height multiplier for collapsing animation
 * Used to smoothly collapse the space after item disappears
 */
float simjot_collapse_height(float t) {
    t = clamp01(t);
    /* Delayed start - collapse after fade begins */
    float delayedT = clamp01((t - 0.3f) / 0.7f);
    /* Ease out - fast at first, slow at end */
    float inv = 1.0f - delayedT;
    return inv * inv;
}
