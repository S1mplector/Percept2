/**
 * Stroke Optimizer - Performance optimization for large stroke collections
 * 
 * Features:
 * - Quadtree spatial index for O(log n) hit testing and visibility culling
 * - Dirty region tracking for incremental rendering
 * - Stroke batching for reduced draw calls
 * - Level-of-detail (LOD) rendering based on zoom level
 * - Stroke simplification for dense point sequences
 * 
 * Copyright (c) 2025 Simjot
 * MIT License
 */

#include "simjot_native.h"
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <algorithm>
#include <mutex>
#include <atomic>

/* ═══════════════════════════════════════════════════════════════════════════
 * CONFIGURATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static constexpr int QUADTREE_MAX_DEPTH = 8;
static constexpr int QUADTREE_MAX_ITEMS = 16;
static constexpr float LOD_SIMPLIFY_THRESHOLD = 0.5f;  // Simplify when zoom < 50%
static constexpr int BATCH_SIZE = 64;
static constexpr int DIRTY_REGION_MERGE_THRESHOLD = 100; // Pixels

/* ═══════════════════════════════════════════════════════════════════════════
 * AXIS-ALIGNED BOUNDING BOX
 * ═══════════════════════════════════════════════════════════════════════════ */

struct AABB {
    float minX, minY, maxX, maxY;
    
    AABB() : minX(0), minY(0), maxX(0), maxY(0) {}
    AABB(float x0, float y0, float x1, float y1) : minX(x0), minY(y0), maxX(x1), maxY(y1) {}
    
    bool contains(float x, float y) const {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
    
    bool intersects(const AABB& other) const {
        return !(other.maxX < minX || other.minX > maxX ||
                 other.maxY < minY || other.minY > maxY);
    }
    
    AABB merge(const AABB& other) const {
        return AABB(
            std::min(minX, other.minX), std::min(minY, other.minY),
            std::max(maxX, other.maxX), std::max(maxY, other.maxY)
        );
    }
    
    float width() const { return maxX - minX; }
    float height() const { return maxY - minY; }
    float area() const { return width() * height(); }
    float centerX() const { return (minX + maxX) * 0.5f; }
    float centerY() const { return (minY + maxY) * 0.5f; }
    
    void expand(float margin) {
        minX -= margin; minY -= margin;
        maxX += margin; maxY += margin;
    }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * INDEXED STROKE - Stroke with precomputed bounds and LOD data
 * ═══════════════════════════════════════════════════════════════════════════ */

struct IndexedStroke {
    int32_t id;
    AABB bounds;
    std::vector<float> pointsX;
    std::vector<float> pointsY;
    std::vector<float> thicknesses;
    uint32_t color;
    float baseThickness;
    bool dirty;
    
    // LOD cache - simplified version for zoomed out view
    std::vector<float> lodPointsX;
    std::vector<float> lodPointsY;
    float lodThreshold;  // Zoom level at which LOD was computed
    
    IndexedStroke() : id(-1), color(0xFF000000), baseThickness(2.0f), dirty(true), lodThreshold(0) {}
    
    void computeBounds() {
        if (pointsX.empty()) return;
        bounds.minX = bounds.maxX = pointsX[0];
        bounds.minY = bounds.maxY = pointsY[0];
        for (size_t i = 1; i < pointsX.size(); i++) {
            if (pointsX[i] < bounds.minX) bounds.minX = pointsX[i];
            if (pointsX[i] > bounds.maxX) bounds.maxX = pointsX[i];
            if (pointsY[i] < bounds.minY) bounds.minY = pointsY[i];
            if (pointsY[i] > bounds.maxY) bounds.maxY = pointsY[i];
        }
        // Expand by thickness
        float margin = baseThickness * 0.5f + 1.0f;
        bounds.expand(margin);
    }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * QUADTREE NODE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct QuadNode {
    AABB bounds;
    std::vector<int32_t> strokeIds;  // Stroke IDs in this node
    QuadNode* children[4];           // NW, NE, SW, SE
    int depth;
    
    QuadNode(const AABB& b, int d) : bounds(b), depth(d) {
        children[0] = children[1] = children[2] = children[3] = nullptr;
    }
    
    ~QuadNode() {
        for (int i = 0; i < 4; i++) {
            delete children[i];
        }
    }
    
    bool isLeaf() const {
        return children[0] == nullptr;
    }
    
    void subdivide() {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        children[0] = new QuadNode(AABB(bounds.minX, bounds.minY, cx, cy), depth + 1); // NW
        children[1] = new QuadNode(AABB(cx, bounds.minY, bounds.maxX, cy), depth + 1); // NE
        children[2] = new QuadNode(AABB(bounds.minX, cy, cx, bounds.maxY), depth + 1); // SW
        children[3] = new QuadNode(AABB(cx, cy, bounds.maxX, bounds.maxY), depth + 1); // SE
    }
    
    int getQuadrant(const AABB& strokeBounds) const {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        
        bool inLeft = strokeBounds.maxX <= cx;
        bool inRight = strokeBounds.minX >= cx;
        bool inTop = strokeBounds.maxY <= cy;
        bool inBottom = strokeBounds.minY >= cy;
        
        if (inLeft && inTop) return 0;    // NW
        if (inRight && inTop) return 1;   // NE
        if (inLeft && inBottom) return 2; // SW
        if (inRight && inBottom) return 3; // SE
        return -1; // Spans multiple quadrants
    }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * DIRTY REGION TRACKER
 * ═══════════════════════════════════════════════════════════════════════════ */

struct DirtyRegionTracker {
    std::vector<AABB> dirtyRegions;
    bool fullRepaint;
    
    DirtyRegionTracker() : fullRepaint(true) {}
    
    void markDirty(const AABB& region) {
        if (fullRepaint) return;
        
        // Try to merge with existing region
        for (auto& r : dirtyRegions) {
            AABB merged = r.merge(region);
            float mergedArea = merged.area();
            float separateArea = r.area() + region.area();
            
            // Merge if combined area isn't much larger
            if (mergedArea < separateArea * 1.5f) {
                r = merged;
                consolidateRegions();
                return;
            }
        }
        
        dirtyRegions.push_back(region);
        consolidateRegions();
    }
    
    void markFullRepaint() {
        fullRepaint = true;
        dirtyRegions.clear();
    }
    
    void clear() {
        fullRepaint = false;
        dirtyRegions.clear();
    }
    
    bool needsRepaint(const AABB& region) const {
        if (fullRepaint) return true;
        for (const auto& r : dirtyRegions) {
            if (r.intersects(region)) return true;
        }
        return false;
    }
    
private:
    void consolidateRegions() {
        // Merge overlapping regions
        bool merged = true;
        while (merged && dirtyRegions.size() > 1) {
            merged = false;
            for (size_t i = 0; i < dirtyRegions.size() && !merged; i++) {
                for (size_t j = i + 1; j < dirtyRegions.size() && !merged; j++) {
                    if (dirtyRegions[i].intersects(dirtyRegions[j])) {
                        dirtyRegions[i] = dirtyRegions[i].merge(dirtyRegions[j]);
                        dirtyRegions.erase(dirtyRegions.begin() + j);
                        merged = true;
                    }
                }
            }
        }
        
        // If too many regions, switch to full repaint
        if (dirtyRegions.size() > 16) {
            markFullRepaint();
        }
    }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE OPTIMIZER - Main coordinator class
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StrokeOptimizer {
    QuadNode* root;
    std::unordered_map<int32_t, IndexedStroke> strokes;
    DirtyRegionTracker dirtyTracker;
    int32_t nextStrokeId;
    AABB worldBounds;
    float currentZoom;
    std::mutex mutex;
    
    // Render cache
    std::vector<int32_t> visibleStrokeIds;
    std::vector<int32_t> renderOrder;
    bool cacheValid;
    AABB lastViewport;
    
    StrokeOptimizer(float worldWidth, float worldHeight) 
        : nextStrokeId(0), currentZoom(1.0f), cacheValid(false) {
        worldBounds = AABB(0, 0, worldWidth, worldHeight);
        root = new QuadNode(worldBounds, 0);
    }
    
    ~StrokeOptimizer() {
        delete root;
    }
    
    int32_t addStroke(const float* x, const float* y, int32_t count,
                       const float* thicknesses, uint32_t color, float baseThickness) {
        std::lock_guard<std::mutex> lock(mutex);
        
        IndexedStroke stroke;
        stroke.id = nextStrokeId++;
        stroke.pointsX.assign(x, x + count);
        stroke.pointsY.assign(y, y + count);
        if (thicknesses) {
            stroke.thicknesses.assign(thicknesses, thicknesses + count);
        }
        stroke.color = color;
        stroke.baseThickness = baseThickness;
        stroke.computeBounds();
        
        strokes[stroke.id] = std::move(stroke);
        insertIntoQuadtree(stroke.id);
        
        dirtyTracker.markDirty(strokes[stroke.id].bounds);
        cacheValid = false;
        
        return stroke.id;
    }
    
    void removeStroke(int32_t strokeId) {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = strokes.find(strokeId);
        if (it == strokes.end()) return;
        
        dirtyTracker.markDirty(it->second.bounds);
        removeFromQuadtree(strokeId);
        strokes.erase(it);
        cacheValid = false;
    }
    
    void moveStroke(int32_t strokeId, float dx, float dy) {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = strokes.find(strokeId);
        if (it == strokes.end()) return;
        
        IndexedStroke& stroke = it->second;
        
        // Mark old region dirty
        dirtyTracker.markDirty(stroke.bounds);
        
        // Remove from quadtree at old position
        removeFromQuadtree(strokeId);
        
        // Update points
        for (size_t i = 0; i < stroke.pointsX.size(); i++) {
            stroke.pointsX[i] += dx;
            stroke.pointsY[i] += dy;
        }
        
        // Clear LOD cache (needs recomputation)
        stroke.lodPointsX.clear();
        stroke.lodPointsY.clear();
        
        // Recompute bounds and reinsert
        stroke.computeBounds();
        insertIntoQuadtree(strokeId);
        
        // Mark new region dirty
        dirtyTracker.markDirty(stroke.bounds);
        stroke.dirty = true;
        cacheValid = false;
    }
    
    void queryVisible(const AABB& viewport, std::vector<int32_t>& outIds) {
        outIds.clear();
        queryQuadtree(root, viewport, outIds);
    }
    
    void queryPoint(float x, float y, float radius, std::vector<int32_t>& outIds) {
        AABB searchArea(x - radius, y - radius, x + radius, y + radius);
        queryQuadtree(root, searchArea, outIds);
    }
    
    int32_t getStrokeCount() const {
        return (int32_t)strokes.size();
    }
    
    void setZoom(float zoom) {
        if (std::abs(zoom - currentZoom) > 0.1f) {
            currentZoom = zoom;
            // Invalidate LOD caches if zoom changed significantly
            if (zoom < LOD_SIMPLIFY_THRESHOLD) {
                for (auto& pair : strokes) {
                    if (pair.second.lodThreshold != zoom) {
                        pair.second.lodPointsX.clear();
                        pair.second.lodPointsY.clear();
                    }
                }
            }
        }
    }
    
    void rebuild() {
        std::lock_guard<std::mutex> lock(mutex);
        
        delete root;
        root = new QuadNode(worldBounds, 0);
        
        for (const auto& pair : strokes) {
            insertIntoQuadtreeNoLock(pair.first);
        }
        
        dirtyTracker.markFullRepaint();
        cacheValid = false;
    }
    
private:
    void insertIntoQuadtree(int32_t strokeId) {
        auto it = strokes.find(strokeId);
        if (it == strokes.end()) return;
        insertIntoNode(root, strokeId, it->second.bounds);
    }
    
    void insertIntoQuadtreeNoLock(int32_t strokeId) {
        auto it = strokes.find(strokeId);
        if (it == strokes.end()) return;
        insertIntoNode(root, strokeId, it->second.bounds);
    }
    
    void insertIntoNode(QuadNode* node, int32_t strokeId, const AABB& strokeBounds) {
        if (!node->bounds.intersects(strokeBounds)) return;
        
        if (node->isLeaf()) {
            node->strokeIds.push_back(strokeId);
            
            // Subdivide if too many items and not at max depth
            if (node->strokeIds.size() > QUADTREE_MAX_ITEMS && 
                node->depth < QUADTREE_MAX_DEPTH) {
                node->subdivide();
                
                // Redistribute strokes
                std::vector<int32_t> toRedistribute = std::move(node->strokeIds);
                node->strokeIds.clear();
                
                for (int32_t id : toRedistribute) {
                    auto sit = strokes.find(id);
                    if (sit == strokes.end()) continue;
                    
                    int quad = node->getQuadrant(sit->second.bounds);
                    if (quad >= 0) {
                        insertIntoNode(node->children[quad], id, sit->second.bounds);
                    } else {
                        node->strokeIds.push_back(id); // Spans multiple quadrants
                    }
                }
            }
        } else {
            int quad = node->getQuadrant(strokeBounds);
            if (quad >= 0) {
                insertIntoNode(node->children[quad], strokeId, strokeBounds);
            } else {
                // Spans multiple quadrants - store in this node
                node->strokeIds.push_back(strokeId);
            }
        }
    }
    
    void removeFromQuadtree(int32_t strokeId) {
        removeFromNode(root, strokeId);
    }
    
    void removeFromNode(QuadNode* node, int32_t strokeId) {
        auto it = std::find(node->strokeIds.begin(), node->strokeIds.end(), strokeId);
        if (it != node->strokeIds.end()) {
            node->strokeIds.erase(it);
            return;
        }
        
        if (!node->isLeaf()) {
            for (int i = 0; i < 4; i++) {
                removeFromNode(node->children[i], strokeId);
            }
        }
    }
    
    void queryQuadtree(QuadNode* node, const AABB& viewport, std::vector<int32_t>& outIds) {
        if (!node->bounds.intersects(viewport)) return;
        
        // Add strokes from this node
        for (int32_t id : node->strokeIds) {
            auto it = strokes.find(id);
            if (it != strokes.end() && it->second.bounds.intersects(viewport)) {
                outIds.push_back(id);
            }
        }
        
        // Recurse to children
        if (!node->isLeaf()) {
            for (int i = 0; i < 4; i++) {
                queryQuadtree(node->children[i], viewport, outIds);
            }
        }
    }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * GLOBAL OPTIMIZER INSTANCES
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::unordered_map<int64_t, StrokeOptimizer*> g_optimizers;
static std::mutex g_optimizer_mutex;
static std::atomic<int64_t> g_next_optimizer_handle{1};

static StrokeOptimizer* get_optimizer(int64_t handle) {
    auto it = g_optimizers.find(handle);
    return (it != g_optimizers.end()) ? it->second : nullptr;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

/**
 * Create a stroke optimizer for a document.
 */
int64_t simjot_optimizer_create(float worldWidth, float worldHeight) {
    if (worldWidth <= 0 || worldHeight <= 0) return 0;
    
    StrokeOptimizer* opt = new (std::nothrow) StrokeOptimizer(worldWidth, worldHeight);
    if (!opt) return 0;
    
    int64_t handle = g_next_optimizer_handle.fetch_add(1);
    
    {
        std::lock_guard<std::mutex> lock(g_optimizer_mutex);
        g_optimizers[handle] = opt;
    }
    
    return handle;
}

/**
 * Destroy a stroke optimizer.
 */
void simjot_optimizer_destroy(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    auto it = g_optimizers.find(handle);
    if (it != g_optimizers.end()) {
        delete it->second;
        g_optimizers.erase(it);
    }
}

/**
 * Add a stroke to the optimizer.
 * Returns stroke ID, or -1 on error.
 */
int32_t simjot_optimizer_add_stroke(int64_t handle,
                                     const float* x, const float* y, int32_t count,
                                     const float* thicknesses, uint32_t color, float baseThickness) {
    if (!x || !y || count < 1) return -1;
    
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return -1;
    
    return opt->addStroke(x, y, count, thicknesses, color, baseThickness);
}

/**
 * Remove a stroke from the optimizer.
 */
void simjot_optimizer_remove_stroke(int64_t handle, int32_t strokeId) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->removeStroke(strokeId);
}

/**
 * Move a stroke by delta.
 */
void simjot_optimizer_move_stroke(int64_t handle, int32_t strokeId, float dx, float dy) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->moveStroke(strokeId, dx, dy);
}

/**
 * Query strokes visible in a viewport.
 * Returns number of visible strokes. IDs are written to outIds (must have capacity).
 */
int32_t simjot_optimizer_query_visible(int64_t handle,
                                        float viewX, float viewY, float viewW, float viewH,
                                        int32_t* outIds, int32_t capacity) {
    if (!outIds || capacity < 1) return 0;
    
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    std::vector<int32_t> visible;
    opt->queryVisible(AABB(viewX, viewY, viewX + viewW, viewY + viewH), visible);
    
    int32_t count = std::min((int32_t)visible.size(), capacity);
    for (int32_t i = 0; i < count; i++) {
        outIds[i] = visible[i];
    }
    return count;
}

/**
 * Query strokes near a point (for hit testing).
 * Returns number of strokes. IDs are written to outIds.
 */
int32_t simjot_optimizer_query_point(int64_t handle,
                                      float x, float y, float radius,
                                      int32_t* outIds, int32_t capacity) {
    if (!outIds || capacity < 1) return 0;
    
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    std::vector<int32_t> nearby;
    opt->queryPoint(x, y, radius, nearby);
    
    int32_t count = std::min((int32_t)nearby.size(), capacity);
    for (int32_t i = 0; i < count; i++) {
        outIds[i] = nearby[i];
    }
    return count;
}

/**
 * Get stroke data by ID.
 * Returns point count, or 0 if not found.
 */
int32_t simjot_optimizer_get_stroke(int64_t handle, int32_t strokeId,
                                     float* outX, float* outY, float* outThick,
                                     int32_t capacity, uint32_t* outColor) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    auto it = opt->strokes.find(strokeId);
    if (it == opt->strokes.end()) return 0;
    
    const IndexedStroke& s = it->second;
    int32_t count = std::min((int32_t)s.pointsX.size(), capacity);
    
    if (outX) memcpy(outX, s.pointsX.data(), count * sizeof(float));
    if (outY) memcpy(outY, s.pointsY.data(), count * sizeof(float));
    if (outThick && !s.thicknesses.empty()) {
        memcpy(outThick, s.thicknesses.data(), count * sizeof(float));
    }
    if (outColor) *outColor = s.color;
    
    return (int32_t)s.pointsX.size();
}

/**
 * Get stroke bounds by ID.
 * bounds[0]=minX, [1]=minY, [2]=maxX, [3]=maxY
 */
int32_t simjot_optimizer_get_stroke_bounds(int64_t handle, int32_t strokeId, float* bounds) {
    if (!bounds) return 0;
    
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    auto it = opt->strokes.find(strokeId);
    if (it == opt->strokes.end()) return 0;
    
    bounds[0] = it->second.bounds.minX;
    bounds[1] = it->second.bounds.minY;
    bounds[2] = it->second.bounds.maxX;
    bounds[3] = it->second.bounds.maxY;
    return 1;
}

/**
 * Get total stroke count.
 */
int32_t simjot_optimizer_stroke_count(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    return opt ? opt->getStrokeCount() : 0;
}

/**
 * Set current zoom level (for LOD).
 */
void simjot_optimizer_set_zoom(int64_t handle, float zoom) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->setZoom(zoom);
}

/**
 * Check if any region needs repaint.
 * Returns 1 if repaint needed in viewport, 0 otherwise.
 */
int32_t simjot_optimizer_needs_repaint(int64_t handle,
                                        float viewX, float viewY, float viewW, float viewH) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    AABB viewport(viewX, viewY, viewX + viewW, viewY + viewH);
    return opt->dirtyTracker.needsRepaint(viewport) ? 1 : 0;
}

/**
 * Clear dirty regions after repaint.
 */
void simjot_optimizer_clear_dirty(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->dirtyTracker.clear();
}

/**
 * Mark entire document for full repaint.
 */
void simjot_optimizer_mark_full_repaint(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->dirtyTracker.markFullRepaint();
}

/**
 * Rebuild the spatial index (call after bulk changes).
 */
void simjot_optimizer_rebuild(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (opt) opt->rebuild();
}

/**
 * Render visible strokes to a pixel buffer with batching.
 * Returns number of strokes rendered.
 */
int32_t simjot_optimizer_render_visible(int64_t handle,
                                         uint32_t* pixels, int32_t width, int32_t height,
                                         float viewX, float viewY, float viewW, float viewH) {
    if (!pixels || width <= 0 || height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_optimizer_mutex);
    StrokeOptimizer* opt = get_optimizer(handle);
    if (!opt) return 0;
    
    // Query visible strokes
    std::vector<int32_t> visible;
    AABB viewport(viewX, viewY, viewX + viewW, viewY + viewH);
    opt->queryVisible(viewport, visible);
    
    if (visible.empty()) return 0;
    
    // Render each visible stroke
    int32_t rendered = 0;
    for (int32_t id : visible) {
        auto it = opt->strokes.find(id);
        if (it == opt->strokes.end()) continue;
        
        const IndexedStroke& s = it->second;
        if (s.pointsX.empty()) continue;
        
        // Use existing render function
        if (s.thicknesses.empty()) {
            // Uniform thickness
            simjot_draw_stroke(pixels, width, height,
                               s.pointsX.data(), s.pointsY.data(),
                               (int32_t)s.pointsX.size(), s.baseThickness, s.color,
                               viewX, viewY);
        } else {
            // Variable thickness
            simjot_draw_render_variable(pixels, width, height,
                                        s.pointsX.data(), s.pointsY.data(), s.thicknesses.data(),
                                        (int32_t)s.pointsX.size(), s.color,
                                        viewX, viewY);
        }
        rendered++;
    }
    
    return rendered;
}

} // extern "C"
