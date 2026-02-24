/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

/**
 * @file stroke_engine.cpp
 * @brief Professional-grade stroke rendering engine
 *
 * - Catmull-Rom spline interpolation for smooth curves
 * - Velocity-based thickness variation (pressure simulation)
 * - Distance-based point sampling to eliminate jitter
 * - High-quality anti-aliased Bezier curve rendering
 * - Chaikin corner-cutting for post-stroke smoothing
 * - Stroke caching with dirty-region tracking
 *
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <cmath>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <atomic>
#include <algorithm>

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS
 * ═══════════════════════════════════════════════════════════════════════════ */

static constexpr float MIN_POINT_DISTANCE = 2.0f;      // Minimum distance between sampled points
static constexpr float MIN_POINT_DISTANCE_SQ = 4.0f;
static constexpr float VELOCITY_SMOOTHING = 0.3f;      // EMA for velocity calculation
static constexpr float THICKNESS_MIN_FACTOR = 0.4f;    // Min thickness at max velocity
static constexpr float THICKNESS_MAX_FACTOR = 1.2f;    // Max thickness at min velocity
static constexpr float VELOCITY_REFERENCE = 800.0f;    // Reference velocity for thickness mapping
static constexpr int BEZIER_SUBDIVISION = 8;           // Bezier curve subdivision level
static constexpr int CHAIKIN_ITERATIONS = 2;           // Chaikin smoothing iterations

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE POINT WITH METADATA
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StrokePointEx {
    float x, y;
    float pressure;     // 0.0-1.0, computed from velocity if not provided
    float velocity;     // Pixels per ms
    int64_t timestamp;  // Milliseconds
};

/* ═══════════════════════════════════════════════════════════════════════════
 * LIVE STROKE STATE - For real-time input processing
 * ═══════════════════════════════════════════════════════════════════════════ */

struct LiveStroke {
    std::vector<StrokePointEx> rawPoints;      // Raw input points
    std::vector<StrokePointEx> smoothedPoints; // After real-time smoothing
    
    // Smoothing state
    float smoothX, smoothY;
    float smoothVelocity;
    int64_t lastTimestamp;
    
    // Configuration
    float baseThickness;
    uint32_t color;
    bool usePressure;
    
    // Dirty tracking
    int dirtyStartIdx;
    bool isDirty;
};

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE CACHE - For completed strokes
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StrokeCache {
    uint32_t* pixels;
    int32_t width, height;
    int32_t offsetX, offsetY;  // Document coordinates of top-left
    bool valid;
};

struct StrokeManager {
    std::vector<LiveStroke> strokes;
    StrokeCache cache;
    std::mutex mutex;
    
    // Viewport state
    int32_t viewportX, viewportY;
    int32_t viewportW, viewportH;
    
    // Document dimensions
    int32_t docWidth, docHeight;
    
    // Global dirty region
    int32_t dirtyX, dirtyY, dirtyW, dirtyH;
    bool hasDirtyRegion;
};

static std::mutex g_manager_mutex;
static std::unordered_map<int64_t, StrokeManager*> g_managers;
static std::atomic<int64_t> g_next_manager_id{1};

/* ═══════════════════════════════════════════════════════════════════════════
 * MATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float distance_sq(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1, dy = y2 - y1;
    return dx * dx + dy * dy;
}

static inline float distance(float x1, float y1, float x2, float y2) {
    return sqrtf(distance_sq(x1, y1, x2, y2));
}

static inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static inline float lerpf(float a, float b, float t) {
    return a + t * (b - a);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CATMULL-ROM SPLINE
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Evaluate Catmull-Rom spline at parameter t (0-1).
 * Requires 4 control points: p0, p1, p2, p3
 * The curve passes through p1 and p2.
 */
static void catmull_rom(float p0x, float p0y, float p1x, float p1y,
                        float p2x, float p2y, float p3x, float p3y,
                        float t, float* outX, float* outY) {
    float t2 = t * t;
    float t3 = t2 * t;
    
    // Catmull-Rom basis functions
    float b0 = -0.5f * t3 + t2 - 0.5f * t;
    float b1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
    float b2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
    float b3 = 0.5f * t3 - 0.5f * t2;
    
    *outX = b0 * p0x + b1 * p1x + b2 * p2x + b3 * p3x;
    *outY = b0 * p0y + b1 * p1y + b2 * p2y + b3 * p3y;
}

/**
 * Interpolate stroke points using Catmull-Rom splines.
 * Creates smooth curves through the control points.
 */
static void interpolate_catmull_rom(const std::vector<StrokePointEx>& input,
                                     std::vector<StrokePointEx>& output,
                                     int subdivisions) {
    if (input.size() < 2) {
        output = input;
        return;
    }
    
    output.clear();
    output.reserve(input.size() * subdivisions);
    
    int n = (int)input.size();
    
    for (int i = 0; i < n - 1; i++) {
        // Get 4 control points with clamping at boundaries
        int i0 = std::max(0, i - 1);
        int i1 = i;
        int i2 = i + 1;
        int i3 = std::min(n - 1, i + 2);
        
        const StrokePointEx& p0 = input[i0];
        const StrokePointEx& p1 = input[i1];
        const StrokePointEx& p2 = input[i2];
        const StrokePointEx& p3 = input[i3];
        
        // Add first point of segment
        if (i == 0) {
            output.push_back(p1);
        }
        
        // Subdivide
        for (int j = 1; j <= subdivisions; j++) {
            float t = (float)j / (float)subdivisions;
            
            StrokePointEx pt;
            catmull_rom(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, t, &pt.x, &pt.y);
            pt.pressure = lerpf(p1.pressure, p2.pressure, t);
            pt.velocity = lerpf(p1.velocity, p2.velocity, t);
            pt.timestamp = (int64_t)lerpf((float)p1.timestamp, (float)p2.timestamp, t);
            
            output.push_back(pt);
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CHAIKIN CORNER-CUTTING ALGORITHM
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Apply Chaikin's corner-cutting algorithm for smooth curves.
 * Each iteration replaces each segment with two new points at 1/4 and 3/4 positions.
 */
static void chaikin_smooth(std::vector<StrokePointEx>& points, int iterations) {
    if (points.size() < 3) return;
    
    for (int iter = 0; iter < iterations; iter++) {
        std::vector<StrokePointEx> smoothed;
        smoothed.reserve(points.size() * 2);
        
        // Keep first point
        smoothed.push_back(points[0]);
        
        for (size_t i = 0; i < points.size() - 1; i++) {
            const StrokePointEx& p0 = points[i];
            const StrokePointEx& p1 = points[i + 1];
            
            // Q point at 1/4
            StrokePointEx q;
            q.x = 0.75f * p0.x + 0.25f * p1.x;
            q.y = 0.75f * p0.y + 0.25f * p1.y;
            q.pressure = 0.75f * p0.pressure + 0.25f * p1.pressure;
            q.velocity = 0.75f * p0.velocity + 0.25f * p1.velocity;
            q.timestamp = (int64_t)(0.75f * p0.timestamp + 0.25f * p1.timestamp);
            
            // R point at 3/4
            StrokePointEx r;
            r.x = 0.25f * p0.x + 0.75f * p1.x;
            r.y = 0.25f * p0.y + 0.75f * p1.y;
            r.pressure = 0.25f * p0.pressure + 0.75f * p1.pressure;
            r.velocity = 0.25f * p0.velocity + 0.75f * p1.velocity;
            r.timestamp = (int64_t)(0.25f * p0.timestamp + 0.75f * p1.timestamp);
            
            smoothed.push_back(q);
            smoothed.push_back(r);
        }
        
        // Keep last point
        smoothed.push_back(points.back());
        
        points = std::move(smoothed);
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * VELOCITY-BASED PRESSURE SIMULATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute pressure from velocity.
 * Slower movements = higher pressure (thicker line)
 * Faster movements = lower pressure (thinner line)
 */
static float velocity_to_pressure(float velocity) {
    // Map velocity to 0-1 pressure
    // velocity = 0 -> pressure = 1.0 (thick)
    // velocity = VELOCITY_REFERENCE -> pressure ~= 0.3 (thin)
    float normalized = velocity / VELOCITY_REFERENCE;
    normalized = clampf(normalized, 0.0f, 2.0f);
    
    // Exponential decay for natural feel
    float pressure = expf(-normalized * 1.5f);
    return clampf(pressure, 0.2f, 1.0f);
}

/**
 * Convert pressure to thickness multiplier.
 */
static float pressure_to_thickness(float pressure, float baseThickness) {
    float factor = THICKNESS_MIN_FACTOR + pressure * (THICKNESS_MAX_FACTOR - THICKNESS_MIN_FACTOR);
    return baseThickness * factor;
}

static inline float resolve_thickness(const LiveStroke& stroke, float pressure) {
    return stroke.usePressure ? pressure_to_thickness(pressure, stroke.baseThickness)
                              : stroke.baseThickness;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DISTANCE-BASED POINT SAMPLING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Filter points to ensure minimum distance between consecutive points.
 * This eliminates jitter from mouse/touch noise.
 */
static void distance_sample_points(const std::vector<StrokePointEx>& input,
                                    std::vector<StrokePointEx>& output,
                                    float minDistSq) {
    if (input.empty()) {
        output.clear();
        return;
    }
    
    output.clear();
    output.reserve(input.size());
    output.push_back(input[0]);
    
    for (size_t i = 1; i < input.size(); i++) {
        const StrokePointEx& last = output.back();
        const StrokePointEx& curr = input[i];
        
        float distSq = distance_sq(last.x, last.y, curr.x, curr.y);
        if (distSq >= minDistSq) {
            output.push_back(curr);
        }
    }
    
    // Always include last point
    if (output.size() > 1 && input.size() > 1) {
        const StrokePointEx& inputLast = input.back();
        StrokePointEx& outputLast = output.back();
        if (outputLast.x != inputLast.x || outputLast.y != inputLast.y) {
            output.push_back(inputLast);
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HIGH-QUALITY ANTI-ALIASED RENDERING
 * ═══════════════════════════════════════════════════════════════════════════ */

// Alpha blending helper
static inline uint32_t alpha_blend(uint32_t src, uint32_t dst) {
    uint32_t sa = (src >> 24) & 0xFF;
    if (sa == 0) return dst;
    if (sa == 255) return src;
    
    uint32_t da = (dst >> 24) & 0xFF;
    uint32_t sr = (src >> 16) & 0xFF;
    uint32_t sg = (src >> 8) & 0xFF;
    uint32_t sb = src & 0xFF;
    uint32_t dr = (dst >> 16) & 0xFF;
    uint32_t dg = (dst >> 8) & 0xFF;
    uint32_t db = dst & 0xFF;
    
    uint32_t inv_sa = 255 - sa;
    uint32_t out_a = sa + ((da * inv_sa) >> 8);
    uint32_t out_r = ((sr * sa) + (dr * inv_sa)) >> 8;
    uint32_t out_g = ((sg * sa) + (dg * inv_sa)) >> 8;
    uint32_t out_b = ((sb * sa) + (db * inv_sa)) >> 8;
    
    return (out_a << 24) | (out_r << 16) | (out_g << 8) | out_b;
}

// Plot anti-aliased pixel with coverage
static inline void plot_aa(uint32_t* pixels, int32_t width, int32_t height,
                           int32_t x, int32_t y, uint32_t color, float coverage) {
    if (x < 0 || x >= width || y < 0 || y >= height) return;
    if (coverage <= 0.0f) return;
    
    uint32_t alpha = (color >> 24) & 0xFF;
    uint32_t newAlpha = (uint32_t)(alpha * clampf(coverage, 0.0f, 1.0f));
    if (newAlpha == 0) return;
    
    uint32_t src = (newAlpha << 24) | (color & 0x00FFFFFF);
    int32_t idx = y * width + x;
    pixels[idx] = alpha_blend(src, pixels[idx]);
}

/**
 * Draw a thick anti-aliased line segment with variable thickness.
 * Uses distance-based coverage for smooth edges.
 */
static void draw_thick_segment_aa(uint32_t* pixels, int32_t width, int32_t height,
                                   float x0, float y0, float t0,
                                   float x1, float y1, float t1,
                                   uint32_t color) {
    float dx = x1 - x0;
    float dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    
    float maxT = fmaxf(t0, t1);
    float halfMaxT = maxT * 0.5f;
    
    if (len < 0.5f) {
        // Single point - draw filled circle
        int32_t cx = (int32_t)(x0 + 0.5f);
        int32_t cy = (int32_t)(y0 + 0.5f);
        int32_t r = (int32_t)(halfMaxT + 1.5f);
        
        for (int32_t py = cy - r; py <= cy + r; py++) {
            for (int32_t px = cx - r; px <= cx + r; px++) {
                float dist = sqrtf((float)((px - cx) * (px - cx) + (py - cy) * (py - cy)));
                float coverage = clampf(halfMaxT + 0.5f - dist, 0.0f, 1.0f);
                plot_aa(pixels, width, height, px, py, color, coverage);
            }
        }
        return;
    }
    
    // Normalize direction
    float nx = -dy / len;
    float ny = dx / len;
    
    // Bounding box with padding for thickness
    float minX = fminf(x0, x1) - halfMaxT - 1.0f;
    float maxX = fmaxf(x0, x1) + halfMaxT + 1.0f;
    float minY = fminf(y0, y1) - halfMaxT - 1.0f;
    float maxY = fmaxf(y0, y1) + halfMaxT + 1.0f;
    
    int32_t startX = (int32_t)fmaxf(0.0f, minX);
    int32_t endX = (int32_t)fminf((float)(width - 1), maxX);
    int32_t startY = (int32_t)fmaxf(0.0f, minY);
    int32_t endY = (int32_t)fminf((float)(height - 1), maxY);
    
    // Scanline fill with distance-based antialiasing
    for (int32_t py = startY; py <= endY; py++) {
        for (int32_t px = startX; px <= endX; px++) {
            // Find closest point on segment
            float t = ((px - x0) * dx + (py - y0) * dy) / (len * len);
            t = clampf(t, 0.0f, 1.0f);
            
            float closestX = x0 + t * dx;
            float closestY = y0 + t * dy;
            float dist = sqrtf((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
            
            // Interpolate thickness at this point
            float thickness = lerpf(t0, t1, t);
            float halfT = thickness * 0.5f;
            
            if (dist <= halfT + 1.0f) {
                float coverage = clampf(halfT + 0.5f - dist, 0.0f, 1.0f);
                plot_aa(pixels, width, height, px, py, color, coverage);
            }
        }
    }
    
    // Round caps at endpoints
    for (int i = 0; i < 2; i++) {
        float cx = (i == 0) ? x0 : x1;
        float cy = (i == 0) ? y0 : y1;
        float thickness = (i == 0) ? t0 : t1;
        float halfT = thickness * 0.5f;
        int32_t r = (int32_t)(halfT + 1.5f);
        int32_t icx = (int32_t)cx;
        int32_t icy = (int32_t)cy;
        
        for (int32_t py = icy - r; py <= icy + r; py++) {
            for (int32_t px = icx - r; px <= icx + r; px++) {
                float dist = sqrtf((float)((px - cx) * (px - cx) + (py - cy) * (py - cy)));
                if (dist <= halfT + 1.0f) {
                    float coverage = clampf(halfT + 0.5f - dist, 0.0f, 1.0f);
                    plot_aa(pixels, width, height, px, py, color, coverage);
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - STROKE MANAGER
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

/**
 * Create a stroke manager for handling multiple strokes with caching.
 */
int64_t simjot_draw_manager_create(int32_t docWidth, int32_t docHeight) {
    if (docWidth <= 0 || docHeight <= 0) return 0;
    
    StrokeManager* mgr = new (std::nothrow) StrokeManager();
    if (!mgr) return 0;
    
    mgr->cache.pixels = nullptr;
    mgr->cache.width = 0;
    mgr->cache.height = 0;
    mgr->cache.valid = false;
    mgr->docWidth = docWidth;
    mgr->docHeight = docHeight;
    mgr->viewportX = 0;
    mgr->viewportY = 0;
    mgr->viewportW = docWidth;
    mgr->viewportH = docHeight;
    mgr->hasDirtyRegion = false;
    
    int64_t handle = g_next_manager_id.fetch_add(1);
    
    {
        std::lock_guard<std::mutex> lock(g_manager_mutex);
        g_managers[handle] = mgr;
    }
    
    return handle;
}

/**
 * Destroy stroke manager and free resources.
 */
void simjot_draw_manager_destroy(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it != g_managers.end()) {
        StrokeManager* mgr = it->second;
        if (mgr->cache.pixels) free(mgr->cache.pixels);
        delete mgr;
        g_managers.erase(it);
    }
}

/**
 * Begin a new stroke with the given properties.
 * Returns stroke index, or -1 on error.
 */
int32_t simjot_draw_stroke_begin(int64_t handle, float x, float y, int64_t timestamp,
                                  float baseThickness, uint32_t color, int32_t usePressure) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return -1;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    LiveStroke stroke;
    stroke.smoothX = x;
    stroke.smoothY = y;
    stroke.smoothVelocity = 0.0f;
    stroke.lastTimestamp = timestamp;
    stroke.baseThickness = baseThickness;
    stroke.color = color;
    stroke.usePressure = (usePressure != 0);
    stroke.dirtyStartIdx = 0;
    stroke.isDirty = true;
    
    StrokePointEx pt;
    pt.x = x;
    pt.y = y;
    pt.pressure = 1.0f;  // Start with full pressure
    pt.velocity = 0.0f;
    pt.timestamp = timestamp;
    
    stroke.rawPoints.push_back(pt);
    stroke.smoothedPoints.push_back(pt);
    
    mgr->strokes.push_back(std::move(stroke));
    
    return (int32_t)(mgr->strokes.size() - 1);
}

/**
 * Add a point to the current stroke with real-time smoothing.
 * Returns 1 if point was added, 0 if skipped (too close), -1 on error.
 */
int32_t simjot_draw_stroke_add_point(int64_t handle, int32_t strokeIdx,
                                      float x, float y, int64_t timestamp) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return -1;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    if (strokeIdx < 0 || strokeIdx >= (int32_t)mgr->strokes.size()) return -1;
    
    LiveStroke& stroke = mgr->strokes[strokeIdx];
    if (stroke.rawPoints.empty()) return -1;
    
    const StrokePointEx& lastRaw = stroke.rawPoints.back();
    
    // Distance-based sampling
    float distSq = distance_sq(lastRaw.x, lastRaw.y, x, y);
    if (distSq < MIN_POINT_DISTANCE_SQ) {
        return 0;  // Too close, skip
    }

    if (!stroke.usePressure) {
        StrokePointEx pt;
        pt.x = x;
        pt.y = y;
        pt.pressure = 1.0f;
        pt.velocity = 0.0f;
        pt.timestamp = timestamp;
        stroke.rawPoints.push_back(pt);
        stroke.smoothedPoints.push_back(pt);
        stroke.smoothX = x;
        stroke.smoothY = y;
        stroke.lastTimestamp = timestamp;
        stroke.isDirty = true;
        return 1;
    }
    
    // Calculate velocity
    float dt = (float)(timestamp - stroke.lastTimestamp);
    if (dt < 1.0f) dt = 1.0f;  // Prevent division by zero
    float dist = sqrtf(distSq);
    float velocity = dist / dt;
    
    // Smooth velocity with EMA
    stroke.smoothVelocity = VELOCITY_SMOOTHING * velocity + (1.0f - VELOCITY_SMOOTHING) * stroke.smoothVelocity;
    
    // Compute pressure from velocity
    float pressure = velocity_to_pressure(stroke.smoothVelocity);
    
    // Apply real-time smoothing (EMA)
    float alpha = 0.6f;  // Responsive but smooth
    float smoothX = alpha * x + (1.0f - alpha) * stroke.smoothX;
    float smoothY = alpha * y + (1.0f - alpha) * stroke.smoothY;
    
    // Store raw point
    StrokePointEx rawPt;
    rawPt.x = x;
    rawPt.y = y;
    rawPt.pressure = pressure;
    rawPt.velocity = velocity;
    rawPt.timestamp = timestamp;
    stroke.rawPoints.push_back(rawPt);
    
    // Store smoothed point
    StrokePointEx smoothPt;
    smoothPt.x = smoothX;
    smoothPt.y = smoothY;
    smoothPt.pressure = pressure;
    smoothPt.velocity = stroke.smoothVelocity;
    smoothPt.timestamp = timestamp;
    stroke.smoothedPoints.push_back(smoothPt);
    
    // Update state
    stroke.smoothX = smoothX;
    stroke.smoothY = smoothY;
    stroke.lastTimestamp = timestamp;
    stroke.isDirty = true;
    
    return 1;
}

/**
 * End the current stroke and apply final smoothing.
 */
int32_t simjot_draw_stroke_end(int64_t handle, int32_t strokeIdx, int32_t applySmoothing) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return -1;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    if (strokeIdx < 0 || strokeIdx >= (int32_t)mgr->strokes.size()) return -1;
    
    LiveStroke& stroke = mgr->strokes[strokeIdx];
    
    if (applySmoothing && stroke.smoothedPoints.size() >= 3) {
        // Apply Chaikin smoothing for final polish
        chaikin_smooth(stroke.smoothedPoints, CHAIKIN_ITERATIONS);
    }
    
    stroke.isDirty = true;
    mgr->cache.valid = false;  // Invalidate cache
    
    return 1;
}

/**
 * Remove a stroke by index.
 */
int32_t simjot_draw_stroke_remove(int64_t handle, int32_t strokeIdx) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return -1;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    if (strokeIdx < 0 || strokeIdx >= (int32_t)mgr->strokes.size()) return -1;
    
    mgr->strokes.erase(mgr->strokes.begin() + strokeIdx);
    mgr->cache.valid = false;
    
    return 1;
}

/**
 * Get stroke count.
 */
int32_t simjot_draw_stroke_count(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return 0;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    return (int32_t)mgr->strokes.size();
}

/**
 * Clear all strokes.
 */
void simjot_draw_stroke_clear_all(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    mgr->strokes.clear();
    mgr->cache.valid = false;
}

/**
 * Render all strokes to a pixel buffer.
 * Uses variable thickness based on velocity/pressure.
 */
int32_t simjot_draw_render_all(int64_t handle, uint32_t* pixels, 
                                int32_t width, int32_t height,
                                float offsetX, float offsetY) {
    if (!pixels || width <= 0 || height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return 0;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    for (const LiveStroke& stroke : mgr->strokes) {
        const std::vector<StrokePointEx>& pts = stroke.smoothedPoints;
        if (pts.size() < 2) {
            if (pts.size() == 1) {
                // Draw single point as dot
                float x = pts[0].x - offsetX;
                float y = pts[0].y - offsetY;
                float thickness = resolve_thickness(stroke, pts[0].pressure);
                draw_thick_segment_aa(pixels, width, height, x, y, thickness, x, y, thickness, stroke.color);
            }
            continue;
        }
        
        // Render each segment with variable thickness
        for (size_t i = 0; i < pts.size() - 1; i++) {
            float x0 = pts[i].x - offsetX;
            float y0 = pts[i].y - offsetY;
            float t0 = resolve_thickness(stroke, pts[i].pressure);
            
            float x1 = pts[i + 1].x - offsetX;
            float y1 = pts[i + 1].y - offsetY;
            float t1 = resolve_thickness(stroke, pts[i + 1].pressure);
            
            draw_thick_segment_aa(pixels, width, height, x0, y0, t0, x1, y1, t1, stroke.color);
        }
    }
    
    return 1;
}

/**
 * Render a single stroke by index.
 */
int32_t simjot_draw_render_one(int64_t handle, int32_t strokeIdx,
                                uint32_t* pixels, int32_t width, int32_t height,
                                float offsetX, float offsetY) {
    if (!pixels || width <= 0 || height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_manager_mutex);
    auto it = g_managers.find(handle);
    if (it == g_managers.end()) return 0;
    
    StrokeManager* mgr = it->second;
    std::lock_guard<std::mutex> strokeLock(mgr->mutex);
    
    if (strokeIdx < 0 || strokeIdx >= (int32_t)mgr->strokes.size()) return 0;
    
    const LiveStroke& stroke = mgr->strokes[strokeIdx];
    const std::vector<StrokePointEx>& pts = stroke.smoothedPoints;
    
    if (pts.size() < 2) {
        if (pts.size() == 1) {
            float x = pts[0].x - offsetX;
            float y = pts[0].y - offsetY;
            float thickness = resolve_thickness(stroke, pts[0].pressure);
            draw_thick_segment_aa(pixels, width, height, x, y, thickness, x, y, thickness, stroke.color);
        }
        return 1;
    }
    
    for (size_t i = 0; i < pts.size() - 1; i++) {
        float x0 = pts[i].x - offsetX;
        float y0 = pts[i].y - offsetY;
        float t0 = resolve_thickness(stroke, pts[i].pressure);
        
        float x1 = pts[i + 1].x - offsetX;
        float y1 = pts[i + 1].y - offsetY;
        float t1 = resolve_thickness(stroke, pts[i + 1].pressure);
        
        draw_thick_segment_aa(pixels, width, height, x0, y0, t0, x1, y1, t1, stroke.color);
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STANDALONE STROKE PROCESSING FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Apply Catmull-Rom interpolation to a point array.
 * Output arrays must be at least (in_count * subdivisions) in size.
 * Returns number of output points.
 */
int32_t simjot_draw_interpolate_catmull_rom(
    const float* in_x, const float* in_y, const float* in_pressure, int32_t in_count,
    float* out_x, float* out_y, float* out_pressure, int32_t out_capacity,
    int32_t subdivisions) {
    
    if (!in_x || !in_y || !out_x || !out_y || in_count < 2 || out_capacity < 2) return 0;
    if (subdivisions < 1) subdivisions = BEZIER_SUBDIVISION;
    
    std::vector<StrokePointEx> input(in_count);
    for (int32_t i = 0; i < in_count; i++) {
        input[i].x = in_x[i];
        input[i].y = in_y[i];
        input[i].pressure = in_pressure ? in_pressure[i] : 1.0f;
        input[i].velocity = 0.0f;
        input[i].timestamp = 0;
    }
    
    std::vector<StrokePointEx> output;
    interpolate_catmull_rom(input, output, subdivisions);
    
    int32_t outCount = std::min((int32_t)output.size(), out_capacity);
    for (int32_t i = 0; i < outCount; i++) {
        out_x[i] = output[i].x;
        out_y[i] = output[i].y;
        if (out_pressure) out_pressure[i] = output[i].pressure;
    }
    
    return outCount;
}

/**
 * Apply Chaikin smoothing to a point array in-place.
 * Output arrays must be at least (in_count * 2^iterations + 2) in size.
 * Returns number of output points.
 */
int32_t simjot_draw_smooth_chaikin(
    const float* in_x, const float* in_y, int32_t in_count,
    float* out_x, float* out_y, int32_t out_capacity,
    int32_t iterations) {
    
    if (!in_x || !in_y || !out_x || !out_y || in_count < 3 || out_capacity < 3) return 0;
    if (iterations < 1) iterations = CHAIKIN_ITERATIONS;
    
    std::vector<StrokePointEx> points(in_count);
    for (int32_t i = 0; i < in_count; i++) {
        points[i].x = in_x[i];
        points[i].y = in_y[i];
        points[i].pressure = 1.0f;
        points[i].velocity = 0.0f;
        points[i].timestamp = 0;
    }
    
    chaikin_smooth(points, iterations);
    
    int32_t outCount = std::min((int32_t)points.size(), out_capacity);
    for (int32_t i = 0; i < outCount; i++) {
        out_x[i] = points[i].x;
        out_y[i] = points[i].y;
    }
    
    return outCount;
}

/**
 * Compute velocity and pressure for each point based on inter-point timing.
 * Modifies pressure array in-place.
 */
int32_t simjot_draw_compute_pressure(
    const float* x, const float* y, const int64_t* timestamps, int32_t count,
    float* pressure) {
    
    if (!x || !y || !timestamps || !pressure || count < 2) return 0;
    
    pressure[0] = 1.0f;  // Start with full pressure
    
    float smoothVelocity = 0.0f;
    
    for (int32_t i = 1; i < count; i++) {
        float dx = x[i] - x[i - 1];
        float dy = y[i] - y[i - 1];
        float dist = sqrtf(dx * dx + dy * dy);
        
        float dt = (float)(timestamps[i] - timestamps[i - 1]);
        if (dt < 1.0f) dt = 1.0f;
        
        float velocity = dist / dt;
        smoothVelocity = VELOCITY_SMOOTHING * velocity + (1.0f - VELOCITY_SMOOTHING) * smoothVelocity;
        
        pressure[i] = velocity_to_pressure(smoothVelocity);
    }
    
    return 1;
}

/**
 * Render a stroke with variable thickness to a pixel buffer.
 */
int32_t simjot_draw_render_variable(
    uint32_t* pixels, int32_t width, int32_t height,
    const float* points_x, const float* points_y, const float* thicknesses,
    int32_t point_count, uint32_t argb_color,
    float offset_x, float offset_y) {
    
    if (!pixels || !points_x || !points_y || !thicknesses || point_count < 1) return 0;
    if (width <= 0 || height <= 0) return 0;
    
    if (point_count == 1) {
        float x = points_x[0] - offset_x;
        float y = points_y[0] - offset_y;
        draw_thick_segment_aa(pixels, width, height, x, y, thicknesses[0], 
                              x, y, thicknesses[0], argb_color);
        return 1;
    }
    
    for (int32_t i = 0; i < point_count - 1; i++) {
        float x0 = points_x[i] - offset_x;
        float y0 = points_y[i] - offset_y;
        float t0 = thicknesses[i];
        
        float x1 = points_x[i + 1] - offset_x;
        float y1 = points_y[i + 1] - offset_y;
        float t1 = thicknesses[i + 1];
        
        draw_thick_segment_aa(pixels, width, height, x0, y0, t0, x1, y1, t1, argb_color);
    }
    
    return 1;
}

/**
 * Apply distance-based sampling to reduce point count and eliminate jitter.
 * Returns number of output points.
 */
int32_t simjot_draw_distance_sample(
    const float* in_x, const float* in_y, int32_t in_count,
    float* out_x, float* out_y, int32_t out_capacity,
    float min_distance) {
    
    if (!in_x || !in_y || !out_x || !out_y || in_count < 1 || out_capacity < 1) return 0;
    if (min_distance < 0.5f) min_distance = MIN_POINT_DISTANCE;
    
    float minDistSq = min_distance * min_distance;
    
    out_x[0] = in_x[0];
    out_y[0] = in_y[0];
    int32_t outCount = 1;
    
    for (int32_t i = 1; i < in_count && outCount < out_capacity; i++) {
        float distSq = distance_sq(out_x[outCount - 1], out_y[outCount - 1], in_x[i], in_y[i]);
        if (distSq >= minDistSq) {
            out_x[outCount] = in_x[i];
            out_y[outCount] = in_y[i];
            outCount++;
        }
    }
    
    // Always include last point if different
    if (outCount > 1 && in_count > 1) {
        float lastInX = in_x[in_count - 1];
        float lastInY = in_y[in_count - 1];
        if (out_x[outCount - 1] != lastInX || out_y[outCount - 1] != lastInY) {
            if (outCount < out_capacity) {
                out_x[outCount] = lastInX;
                out_y[outCount] = lastInY;
                outCount++;
            } else {
                out_x[outCount - 1] = lastInX;
                out_y[outCount - 1] = lastInY;
            }
        }
    }
    
    return outCount;
}

} /* extern "C" */
