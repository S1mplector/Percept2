/**
 * Lasso Selector Utility
 * 
 * Provides functions for lasso selection of strokes:
 * - Point-in-polygon testing (ray casting algorithm)
 * - Stroke selection based on lasso polygon
 * - Bounding box computation
 * - Stroke translation
 * 
 * Copyright (c) 2025 Simjot
 * MIT License
 */

#include "simjot_native.h"
#include <cmath>
#include <cstdlib>
#include <algorithm>

/* ═══════════════════════════════════════════════════════════════════════════
 * POINT-IN-POLYGON TEST (Ray Casting Algorithm)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Test if a point is inside a polygon using ray casting.
 * Returns 1 if inside, 0 if outside.
 */
static int point_in_polygon(float px, float py,
                            const float* poly_x, const float* poly_y, int32_t poly_count) {
    if (poly_count < 3) return 0;
    
    int inside = 0;
    int j = poly_count - 1;
    
    for (int i = 0; i < poly_count; i++) {
        float xi = poly_x[i], yi = poly_y[i];
        float xj = poly_x[j], yj = poly_y[j];
        
        // Check if ray from point crosses this edge
        if (((yi > py) != (yj > py)) &&
            (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) {
            inside = !inside;
        }
        j = i;
    }
    
    return inside;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE SELECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Check if any point of a stroke is inside the lasso polygon.
 * Mode 0: Any point inside = selected (loose)
 * Mode 1: All points inside = selected (strict)
 * Mode 2: Center point inside = selected (centroid)
 */
static int stroke_intersects_lasso(const float* stroke_x, const float* stroke_y, int32_t stroke_count,
                                    const float* lasso_x, const float* lasso_y, int32_t lasso_count,
                                    int32_t mode) {
    if (stroke_count < 1 || lasso_count < 3) return 0;
    
    if (mode == 2) {
        // Centroid mode: check if center of stroke is inside
        float cx = 0, cy = 0;
        for (int i = 0; i < stroke_count; i++) {
            cx += stroke_x[i];
            cy += stroke_y[i];
        }
        cx /= stroke_count;
        cy /= stroke_count;
        return point_in_polygon(cx, cy, lasso_x, lasso_y, lasso_count);
    }
    
    if (mode == 1) {
        // Strict mode: all points must be inside
        for (int i = 0; i < stroke_count; i++) {
            if (!point_in_polygon(stroke_x[i], stroke_y[i], lasso_x, lasso_y, lasso_count)) {
                return 0;
            }
        }
        return 1;
    }
    
    // Loose mode (default): any point inside
    for (int i = 0; i < stroke_count; i++) {
        if (point_in_polygon(stroke_x[i], stroke_y[i], lasso_x, lasso_y, lasso_count)) {
            return 1;
        }
    }
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

/**
 * Test if a single point is inside a lasso polygon.
 * Returns 1 if inside, 0 if outside.
 */
int32_t simjot_lasso_point_in_polygon(float px, float py,
                                       const float* poly_x, const float* poly_y, int32_t poly_count) {
    if (!poly_x || !poly_y || poly_count < 3) return 0;
    return point_in_polygon(px, py, poly_x, poly_y, poly_count);
}

/**
 * Test if a stroke intersects with a lasso polygon.
 * mode: 0 = any point (loose), 1 = all points (strict), 2 = centroid
 * Returns 1 if selected, 0 if not.
 */
int32_t simjot_lasso_test_stroke(const float* stroke_x, const float* stroke_y, int32_t stroke_count,
                                  const float* lasso_x, const float* lasso_y, int32_t lasso_count,
                                  int32_t mode) {
    if (!stroke_x || !stroke_y || !lasso_x || !lasso_y) return 0;
    return stroke_intersects_lasso(stroke_x, stroke_y, stroke_count,
                                   lasso_x, lasso_y, lasso_count, mode);
}

/**
 * Test multiple strokes against a lasso polygon.
 * stroke_starts: array of starting indices for each stroke in the flat point arrays
 * stroke_counts: array of point counts for each stroke
 * results: output array (must be at least num_strokes in size), 1 = selected, 0 = not
 * Returns number of selected strokes.
 */
int32_t simjot_lasso_test_strokes_batch(const float* all_x, const float* all_y,
                                         const int32_t* stroke_starts, const int32_t* stroke_counts,
                                         int32_t num_strokes,
                                         const float* lasso_x, const float* lasso_y, int32_t lasso_count,
                                         int32_t mode, int32_t* results) {
    if (!all_x || !all_y || !stroke_starts || !stroke_counts || !results) return 0;
    if (!lasso_x || !lasso_y || lasso_count < 3) return 0;
    
    int32_t selected_count = 0;
    
    for (int32_t i = 0; i < num_strokes; i++) {
        int32_t start = stroke_starts[i];
        int32_t count = stroke_counts[i];
        
        results[i] = stroke_intersects_lasso(all_x + start, all_y + start, count,
                                              lasso_x, lasso_y, lasso_count, mode);
        if (results[i]) selected_count++;
    }
    
    return selected_count;
}

/**
 * Compute bounding box of a set of points.
 * Output: bounds[0]=minX, bounds[1]=minY, bounds[2]=maxX, bounds[3]=maxY
 * Returns 1 on success, 0 on error.
 */
int32_t simjot_lasso_compute_bounds(const float* x, const float* y, int32_t count, float* bounds) {
    if (!x || !y || !bounds || count < 1) return 0;
    
    float minX = x[0], minY = y[0];
    float maxX = x[0], maxY = y[0];
    
    for (int32_t i = 1; i < count; i++) {
        if (x[i] < minX) minX = x[i];
        if (y[i] < minY) minY = y[i];
        if (x[i] > maxX) maxX = x[i];
        if (y[i] > maxY) maxY = y[i];
    }
    
    bounds[0] = minX;
    bounds[1] = minY;
    bounds[2] = maxX;
    bounds[3] = maxY;
    
    return 1;
}

/**
 * Compute combined bounding box of multiple strokes.
 * stroke_starts: array of starting indices for each stroke
 * stroke_counts: array of point counts for each stroke
 * selected: array indicating which strokes to include (1 = include)
 * Output: bounds[0]=minX, bounds[1]=minY, bounds[2]=maxX, bounds[3]=maxY
 * Returns 1 on success, 0 on error.
 */
int32_t simjot_lasso_compute_selection_bounds(const float* all_x, const float* all_y,
                                               const int32_t* stroke_starts, const int32_t* stroke_counts,
                                               const int32_t* selected, int32_t num_strokes,
                                               float* bounds) {
    if (!all_x || !all_y || !stroke_starts || !stroke_counts || !selected || !bounds) return 0;
    
    float minX = 1e30f, minY = 1e30f;
    float maxX = -1e30f, maxY = -1e30f;
    int found = 0;
    
    for (int32_t i = 0; i < num_strokes; i++) {
        if (!selected[i]) continue;
        
        int32_t start = stroke_starts[i];
        int32_t count = stroke_counts[i];
        
        for (int32_t j = 0; j < count; j++) {
            float px = all_x[start + j];
            float py = all_y[start + j];
            if (px < minX) minX = px;
            if (py < minY) minY = py;
            if (px > maxX) maxX = px;
            if (py > maxY) maxY = py;
            found = 1;
        }
    }
    
    if (!found) return 0;
    
    bounds[0] = minX;
    bounds[1] = minY;
    bounds[2] = maxX;
    bounds[3] = maxY;
    
    return 1;
}

/**
 * Translate (move) points by a delta offset.
 * Modifies x and y arrays in-place.
 * Returns 1 on success, 0 on error.
 */
int32_t simjot_lasso_translate_points(float* x, float* y, int32_t count, float dx, float dy) {
    if (!x || !y || count < 1) return 0;
    
    for (int32_t i = 0; i < count; i++) {
        x[i] += dx;
        y[i] += dy;
    }
    
    return 1;
}

/**
 * Simplify a lasso polygon to reduce point count while preserving shape.
 * Uses Douglas-Peucker algorithm.
 * Returns number of output points.
 */
int32_t simjot_lasso_simplify(const float* in_x, const float* in_y, int32_t in_count,
                               float* out_x, float* out_y, int32_t out_capacity,
                               float epsilon) {
    if (!in_x || !in_y || !out_x || !out_y || in_count < 2 || out_capacity < 2) return 0;
    if (epsilon < 0.5f) epsilon = 2.0f;
    
    // Use the existing RDP simplification
    return simjot_stroke_simplify_rdp(in_x, in_y, in_count, out_x, out_y, out_capacity, epsilon);
}

} // extern "C"
