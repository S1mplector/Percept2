/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "font_types.h"
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <vector>

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE MEMORY MANAGEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" sjf_stroke_t* sjf_stroke_create(int32_t initial_capacity) {
    if (initial_capacity < 16) initial_capacity = 16;
    
    sjf_stroke_t* stroke = (sjf_stroke_t*)calloc(1, sizeof(sjf_stroke_t));
    if (!stroke) return nullptr;
    
    stroke->points = (sjf_point_t*)calloc(initial_capacity, sizeof(sjf_point_t));
    if (!stroke->points) {
        free(stroke);
        return nullptr;
    }
    
    stroke->capacity = initial_capacity;
    stroke->point_count = 0;
    stroke->thickness = 2.0f;
    stroke->color = 0xFF000000; // Black
    
    return stroke;
}

extern "C" void sjf_stroke_free(sjf_stroke_t* stroke) {
    if (stroke) {
        free(stroke->points);
        free(stroke);
    }
}

extern "C" int32_t sjf_stroke_add_point(sjf_stroke_t* stroke, float x, float y, float pressure, float timestamp) {
    if (!stroke) return SJF_ERR_NULL_PTR;
    
    // Grow if needed
    if (stroke->point_count >= stroke->capacity) {
        int32_t new_capacity = stroke->capacity * 2;
        sjf_point_t* new_points = (sjf_point_t*)realloc(stroke->points, new_capacity * sizeof(sjf_point_t));
        if (!new_points) return SJF_ERR_MEMORY;
        stroke->points = new_points;
        stroke->capacity = new_capacity;
    }
    
    sjf_point_t* p = &stroke->points[stroke->point_count++];
    p->x = x;
    p->y = y;
    p->pressure = pressure;
    p->timestamp = timestamp;
    
    return SJF_OK;
}

extern "C" int32_t sjf_stroke_set_points(sjf_stroke_t* stroke,
                                         const float* xs, const float* ys,
                                         const float* pressures, const float* timestamps,
                                         int32_t count) {
    if (!stroke || !xs || !ys) return SJF_ERR_NULL_PTR;
    if (count < 0) return SJF_ERR_INVALID_DATA;
    if (count == 0) {
        stroke->point_count = 0;
        return SJF_OK;
    }
    
    if (stroke->capacity < count) {
        sjf_point_t* new_points = (sjf_point_t*)realloc(stroke->points, count * sizeof(sjf_point_t));
        if (!new_points) return SJF_ERR_MEMORY;
        stroke->points = new_points;
        stroke->capacity = count;
    }
    
    stroke->point_count = count;
    for (int32_t i = 0; i < count; i++) {
        stroke->points[i].x = xs[i];
        stroke->points[i].y = ys[i];
        stroke->points[i].pressure = pressures ? pressures[i] : 1.0f;
        stroke->points[i].timestamp = timestamps ? timestamps[i] : 0.0f;
    }
    
    return SJF_OK;
}

extern "C" void sjf_stroke_set_thickness(sjf_stroke_t* stroke, float thickness) {
    if (!stroke) return;
    stroke->thickness = (thickness > 0.0f) ? thickness : 0.0f;
}

extern "C" void sjf_stroke_clear(sjf_stroke_t* stroke) {
    if (stroke) {
        stroke->point_count = 0;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * VECTOR MATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float vec_distance(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    return sqrtf(dx * dx + dy * dy);
}

static inline float vec_length(float x, float y) {
    return sqrtf(x * x + y * y);
}

static inline void vec_normalize(float* x, float* y) {
    float len = vec_length(*x, *y);
    if (len > 0.0001f) {
        *x /= len;
        *y /= len;
    }
}

static inline float vec_angle(float x1, float y1, float x2, float y2) {
    float dot = x1 * x2 + y1 * y2;
    float cross = x1 * y2 - y1 * x2;
    return atan2f(cross, dot);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE SMOOTHING - Chaikin's Algorithm + Gaussian
 * ═══════════════════════════════════════════════════════════════════════════ */

// Chaikin corner cutting for smooth curves
static void chaikin_smooth(std::vector<sjf_point_t>& points) {
    if (points.size() < 3) return;
    
    std::vector<sjf_point_t> result;
    result.reserve(points.size() * 2);
    
    // Keep first point
    result.push_back(points[0]);
    
    for (size_t i = 0; i < points.size() - 1; i++) {
        const sjf_point_t& p0 = points[i];
        const sjf_point_t& p1 = points[i + 1];
        
        // Q = 3/4 * P[i] + 1/4 * P[i+1]
        sjf_point_t q;
        q.x = 0.75f * p0.x + 0.25f * p1.x;
        q.y = 0.75f * p0.y + 0.25f * p1.y;
        q.pressure = 0.75f * p0.pressure + 0.25f * p1.pressure;
        q.timestamp = 0.75f * p0.timestamp + 0.25f * p1.timestamp;
        result.push_back(q);
        
        // R = 1/4 * P[i] + 3/4 * P[i+1]
        sjf_point_t r;
        r.x = 0.25f * p0.x + 0.75f * p1.x;
        r.y = 0.25f * p0.y + 0.75f * p1.y;
        r.pressure = 0.25f * p0.pressure + 0.75f * p1.pressure;
        r.timestamp = 0.25f * p0.timestamp + 0.75f * p1.timestamp;
        result.push_back(r);
    }
    
    // Keep last point
    result.push_back(points.back());
    
    points = std::move(result);
}

// Gaussian smoothing pass
static void gaussian_smooth(std::vector<sjf_point_t>& points, float sigma) {
    if (points.size() < 3) return;
    
    // Simple 1D Gaussian kernel (3-point)
    float k0 = expf(0.0f);
    float k1 = expf(-1.0f / (2.0f * sigma * sigma));
    float sum = k0 + 2.0f * k1;
    k0 /= sum;
    k1 /= sum;
    
    std::vector<sjf_point_t> result(points.size());
    
    // Keep endpoints
    result[0] = points[0];
    result[points.size() - 1] = points[points.size() - 1];
    
    // Smooth middle points
    for (size_t i = 1; i < points.size() - 1; i++) {
        result[i].x = k1 * points[i-1].x + k0 * points[i].x + k1 * points[i+1].x;
        result[i].y = k1 * points[i-1].y + k0 * points[i].y + k1 * points[i+1].y;
        result[i].pressure = k1 * points[i-1].pressure + k0 * points[i].pressure + k1 * points[i+1].pressure;
        result[i].timestamp = points[i].timestamp; // Keep original timestamp
    }
    
    points = std::move(result);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE RESAMPLING - Uniform point distribution
 * ═══════════════════════════════════════════════════════════════════════════ */

static void resample_stroke(std::vector<sjf_point_t>& points, float target_distance) {
    if (points.size() < 2 || target_distance <= 0.0f) return;
    
    std::vector<sjf_point_t> result;
    result.push_back(points[0]);
    
    float accumulated = 0.0f;
    
    for (size_t i = 1; i < points.size(); i++) {
        const sjf_point_t& prev = points[i - 1];
        const sjf_point_t& curr = points[i];
        
        float dist = vec_distance(prev.x, prev.y, curr.x, curr.y);
        
        if (dist < 0.0001f) continue;
        
        while (accumulated + dist >= target_distance) {
            float t = (target_distance - accumulated) / dist;
            
            sjf_point_t interp;
            interp.x = prev.x + t * (curr.x - prev.x);
            interp.y = prev.y + t * (curr.y - prev.y);
            interp.pressure = prev.pressure + t * (curr.pressure - prev.pressure);
            interp.timestamp = prev.timestamp + t * (curr.timestamp - prev.timestamp);
            
            result.push_back(interp);
            accumulated = 0.0f;
            dist -= target_distance - accumulated;
        }
        
        accumulated += dist;
    }
    
    // Always include last point
    if (result.empty() || vec_distance(result.back().x, result.back().y, 
                                        points.back().x, points.back().y) > 0.1f) {
        result.push_back(points.back());
    }
    
    points = std::move(result);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CORNER DETECTION - Preserve sharp angles
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::vector<int32_t> detect_corners(const std::vector<sjf_point_t>& points, float threshold) {
    std::vector<int32_t> corners;
    corners.push_back(0); // Start is always a corner
    
    if (points.size() < 3) {
        if (points.size() > 1) {
            corners.push_back((int32_t)points.size() - 1);
        }
        return corners;
    }
    
    for (size_t i = 1; i < points.size() - 1; i++) {
        float dx1 = points[i].x - points[i-1].x;
        float dy1 = points[i].y - points[i-1].y;
        float dx2 = points[i+1].x - points[i].x;
        float dy2 = points[i+1].y - points[i].y;
        
        vec_normalize(&dx1, &dy1);
        vec_normalize(&dx2, &dy2);
        
        float angle = fabsf(vec_angle(dx1, dy1, dx2, dy2));
        
        if (angle > threshold) {
            corners.push_back((int32_t)i);
        }
    }
    
    corners.push_back((int32_t)points.size() - 1); // End is always a corner
    return corners;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - Stroke Smoothing
 * ═══════════════════════════════════════════════════════════════════════════ */

static int32_t stroke_smooth_impl(sjf_stroke_t* stroke, const sjf_smooth_opts_t* opts) {
    if (!stroke || stroke->point_count < SJF_MIN_STROKE_POINTS) {
        return SJF_ERR_NULL_PTR;
    }
    
    // Default options
    sjf_smooth_opts_t default_opts = {
        SJF_SMOOTH_ITERATIONS,  // iterations
        0.5f,                   // tension
        SJF_RESAMPLE_DISTANCE,  // resample_distance
        1,                      // preserve_corners
        0.7f                    // corner_threshold (~40 degrees)
    };
    
    const sjf_smooth_opts_t* o = opts ? opts : &default_opts;
    
    // Copy points to vector
    std::vector<sjf_point_t> points(stroke->points, stroke->points + stroke->point_count);
    
    // Detect corners if preserving
    std::vector<int32_t> corners;
    if (o->preserve_corners) {
        corners = detect_corners(points, o->corner_threshold);
    }
    
    // Apply smoothing iterations
    for (int32_t iter = 0; iter < o->iterations; iter++) {
        if (o->preserve_corners && corners.size() > 1) {
            // Smooth segments between corners
            std::vector<sjf_point_t> result;
            
            for (size_t c = 0; c < corners.size() - 1; c++) {
                int32_t start = corners[c];
                int32_t end = corners[c + 1];
                
                if (end - start > 2) {
                    std::vector<sjf_point_t> segment(points.begin() + start, points.begin() + end + 1);
                    chaikin_smooth(segment);
                    gaussian_smooth(segment, o->tension);
                    
                    if (c == 0) {
                        result.insert(result.end(), segment.begin(), segment.end());
                    } else {
                        result.insert(result.end(), segment.begin() + 1, segment.end());
                    }
                } else {
                    if (c == 0) {
                        result.insert(result.end(), points.begin() + start, points.begin() + end + 1);
                    } else {
                        result.insert(result.end(), points.begin() + start + 1, points.begin() + end + 1);
                    }
                }
            }
            
            points = std::move(result);
            corners = detect_corners(points, o->corner_threshold);
        } else {
            chaikin_smooth(points);
            gaussian_smooth(points, o->tension);
        }
    }
    
    // Resample for uniform distribution
    if (o->resample_distance > 0.0f) {
        resample_stroke(points, o->resample_distance);
    }
    
    // Copy back to stroke
    if ((int32_t)points.size() > stroke->capacity) {
        sjf_point_t* new_points = (sjf_point_t*)realloc(stroke->points, points.size() * sizeof(sjf_point_t));
        if (!new_points) return SJF_ERR_MEMORY;
        stroke->points = new_points;
        stroke->capacity = (int32_t)points.size();
    }
    
    memcpy(stroke->points, points.data(), points.size() * sizeof(sjf_point_t));
    stroke->point_count = (int32_t)points.size();
    
    return SJF_OK;
}

extern "C" int32_t sjf_stroke_smooth(sjf_stroke_t* stroke, int32_t iterations, float tension,
                                     float resample_distance, int32_t preserve_corners) {
    sjf_smooth_opts_t opts = {
        SJF_SMOOTH_ITERATIONS,
        0.5f,
        SJF_RESAMPLE_DISTANCE,
        1,
        0.7f
    };

    if (iterations >= 0) {
        opts.iterations = iterations;
    }
    if (tension > 0.0f) {
        opts.tension = tension;
    }
    if (resample_distance > 0.0f) {
        opts.resample_distance = resample_distance;
    }
    opts.preserve_corners = preserve_corners ? 1 : 0;

    return stroke_smooth_impl(stroke, &opts);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE PATH LENGTH AND BOUNDS
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" float sjf_stroke_length(const sjf_stroke_t* stroke) {
    if (!stroke || stroke->point_count < 2) return 0.0f;
    
    float length = 0.0f;
    for (int32_t i = 1; i < stroke->point_count; i++) {
        length += vec_distance(
            stroke->points[i-1].x, stroke->points[i-1].y,
            stroke->points[i].x, stroke->points[i].y
        );
    }
    return length;
}

extern "C" int32_t sjf_stroke_get_point_count(const sjf_stroke_t* stroke) {
    if (!stroke) return 0;
    return stroke->point_count;
}

extern "C" int32_t sjf_stroke_get_points(const sjf_stroke_t* stroke, float* out, int32_t out_len) {
    if (!stroke || !out) return SJF_ERR_NULL_PTR;
    int32_t needed = stroke->point_count * 4;
    if (out_len < needed) return SJF_ERR_BUFFER_TOO_SMALL;
    for (int32_t i = 0; i < stroke->point_count; i++) {
        int32_t idx = i * 4;
        out[idx] = stroke->points[i].x;
        out[idx + 1] = stroke->points[i].y;
        out[idx + 2] = stroke->points[i].pressure;
        out[idx + 3] = stroke->points[i].timestamp;
    }
    return needed;
}

extern "C" void sjf_stroke_bounds(const sjf_stroke_t* stroke, 
                                   float* min_x, float* min_y, 
                                   float* max_x, float* max_y) {
    if (!stroke || stroke->point_count == 0) {
        if (min_x) *min_x = 0.0f;
        if (min_y) *min_y = 0.0f;
        if (max_x) *max_x = 0.0f;
        if (max_y) *max_y = 0.0f;
        return;
    }
    
    float mnx = stroke->points[0].x;
    float mny = stroke->points[0].y;
    float mxx = stroke->points[0].x;
    float mxy = stroke->points[0].y;
    
    for (int32_t i = 1; i < stroke->point_count; i++) {
        mnx = std::min(mnx, stroke->points[i].x);
        mny = std::min(mny, stroke->points[i].y);
        mxx = std::max(mxx, stroke->points[i].x);
        mxy = std::max(mxy, stroke->points[i].y);
    }
    
    // Add thickness padding
    float pad = stroke->thickness * 0.5f;
    if (min_x) *min_x = mnx - pad;
    if (min_y) *min_y = mny - pad;
    if (max_x) *max_x = mxx + pad;
    if (max_y) *max_y = mxy + pad;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE TRANSFORMATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" void sjf_stroke_translate(sjf_stroke_t* stroke, float dx, float dy) {
    if (!stroke) return;
    for (int32_t i = 0; i < stroke->point_count; i++) {
        stroke->points[i].x += dx;
        stroke->points[i].y += dy;
    }
}

extern "C" void sjf_stroke_scale(sjf_stroke_t* stroke, float sx, float sy, float cx, float cy) {
    if (!stroke) return;
    for (int32_t i = 0; i < stroke->point_count; i++) {
        stroke->points[i].x = cx + (stroke->points[i].x - cx) * sx;
        stroke->points[i].y = cy + (stroke->points[i].y - cy) * sy;
    }
}

extern "C" void sjf_stroke_normalize(sjf_stroke_t* stroke, float target_size) {
    if (!stroke || stroke->point_count == 0) return;
    
    float min_x, min_y, max_x, max_y;
    sjf_stroke_bounds(stroke, &min_x, &min_y, &max_x, &max_y);
    
    float width = max_x - min_x;
    float height = max_y - min_y;
    float size = std::max(width, height);
    
    if (size < 0.001f) return;
    
    float scale = target_size / size;
    
    // Center at origin, scale, center in target
    for (int32_t i = 0; i < stroke->point_count; i++) {
        stroke->points[i].x = (stroke->points[i].x - min_x - width * 0.5f) * scale + target_size * 0.5f;
        stroke->points[i].y = (stroke->points[i].y - min_y - height * 0.5f) * scale + target_size * 0.5f;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CATMULL-ROM SPLINE INTERPOLATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static sjf_point_t catmull_rom(const sjf_point_t& p0, const sjf_point_t& p1,
                                const sjf_point_t& p2, const sjf_point_t& p3,
                                float t, float tension) {
    float t2 = t * t;
    float t3 = t2 * t;
    
    float s = (1.0f - tension) * 0.5f;
    
    float h1 =  2.0f * t3 - 3.0f * t2 + 1.0f;
    float h2 = -2.0f * t3 + 3.0f * t2;
    float h3 =  t3 - 2.0f * t2 + t;
    float h4 =  t3 - t2;
    
    sjf_point_t result;
    result.x = h1 * p1.x + h2 * p2.x + s * h3 * (p2.x - p0.x) + s * h4 * (p3.x - p1.x);
    result.y = h1 * p1.y + h2 * p2.y + s * h3 * (p2.y - p0.y) + s * h4 * (p3.y - p1.y);
    result.pressure = h1 * p1.pressure + h2 * p2.pressure + 
                      s * h3 * (p2.pressure - p0.pressure) + s * h4 * (p3.pressure - p1.pressure);
    result.timestamp = p1.timestamp + t * (p2.timestamp - p1.timestamp);
    
    return result;
}

extern "C" int32_t sjf_stroke_spline_smooth(sjf_stroke_t* stroke, float tension, int32_t subdivisions) {
    if (!stroke || stroke->point_count < 4) return SJF_ERR_INVALID_DATA;
    if (subdivisions < 1) subdivisions = 4;
    
    std::vector<sjf_point_t> points(stroke->points, stroke->points + stroke->point_count);
    std::vector<sjf_point_t> result;
    
    result.push_back(points[0]);
    
    for (size_t i = 0; i < points.size() - 1; i++) {
        // Get 4 control points for Catmull-Rom
        const sjf_point_t& p0 = points[i > 0 ? i - 1 : i];
        const sjf_point_t& p1 = points[i];
        const sjf_point_t& p2 = points[i + 1];
        const sjf_point_t& p3 = points[i + 2 < points.size() ? i + 2 : i + 1];
        
        for (int32_t j = 1; j <= subdivisions; j++) {
            float t = (float)j / (float)subdivisions;
            result.push_back(catmull_rom(p0, p1, p2, p3, t, tension));
        }
    }
    
    // Copy back
    if ((int32_t)result.size() > stroke->capacity) {
        sjf_point_t* new_points = (sjf_point_t*)realloc(stroke->points, result.size() * sizeof(sjf_point_t));
        if (!new_points) return SJF_ERR_MEMORY;
        stroke->points = new_points;
        stroke->capacity = (int32_t)result.size();
    }
    
    memcpy(stroke->points, result.data(), result.size() * sizeof(sjf_point_t));
    stroke->point_count = (int32_t)result.size();
    
    return SJF_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DOUGLAS-PEUCKER SIMPLIFICATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static float perpendicular_distance(const sjf_point_t& point, 
                                     const sjf_point_t& line_start, 
                                     const sjf_point_t& line_end) {
    float dx = line_end.x - line_start.x;
    float dy = line_end.y - line_start.y;
    float mag = sqrtf(dx * dx + dy * dy);
    
    if (mag < 0.0001f) {
        return vec_distance(point.x, point.y, line_start.x, line_start.y);
    }
    
    float u = ((point.x - line_start.x) * dx + (point.y - line_start.y) * dy) / (mag * mag);
    u = std::max(0.0f, std::min(1.0f, u));
    
    float proj_x = line_start.x + u * dx;
    float proj_y = line_start.y + u * dy;
    
    return vec_distance(point.x, point.y, proj_x, proj_y);
}

static void douglas_peucker_recursive(const std::vector<sjf_point_t>& points,
                                       int32_t start, int32_t end,
                                       float epsilon,
                                       std::vector<bool>& keep) {
    if (end <= start + 1) return;
    
    float max_dist = 0.0f;
    int32_t max_idx = start;
    
    for (int32_t i = start + 1; i < end; i++) {
        float dist = perpendicular_distance(points[i], points[start], points[end]);
        if (dist > max_dist) {
            max_dist = dist;
            max_idx = i;
        }
    }
    
    if (max_dist > epsilon) {
        keep[max_idx] = true;
        douglas_peucker_recursive(points, start, max_idx, epsilon, keep);
        douglas_peucker_recursive(points, max_idx, end, epsilon, keep);
    }
}

extern "C" int32_t sjf_stroke_simplify(sjf_stroke_t* stroke, float epsilon) {
    if (!stroke || stroke->point_count < 3) return SJF_OK;
    if (epsilon <= 0.0f) epsilon = 1.0f;
    
    std::vector<sjf_point_t> points(stroke->points, stroke->points + stroke->point_count);
    std::vector<bool> keep(points.size(), false);
    
    keep[0] = true;
    keep[points.size() - 1] = true;
    
    douglas_peucker_recursive(points, 0, (int32_t)points.size() - 1, epsilon, keep);
    
    std::vector<sjf_point_t> result;
    for (size_t i = 0; i < points.size(); i++) {
        if (keep[i]) {
            result.push_back(points[i]);
        }
    }
    
    memcpy(stroke->points, result.data(), result.size() * sizeof(sjf_point_t));
    stroke->point_count = (int32_t)result.size();
    
    return SJF_OK;
}
