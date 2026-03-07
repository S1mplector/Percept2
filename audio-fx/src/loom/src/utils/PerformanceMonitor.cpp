#include "PerformanceMonitor.hpp"
#include <numeric>
#include <sstream>
#include <iomanip>

namespace ethereal {

PerformanceMonitor::PerformanceMonitor()
    : historySize(120)
    , lastFrameTimeMs(0.0f)
    , estimatedMemory(0)
    , cachedAverageFrameTime(0.0f)
    , averageDirty(true) {}

void PerformanceMonitor::beginFrame() {
    frameStart = std::chrono::high_resolution_clock::now();
}

void PerformanceMonitor::endFrame() {
    frameEnd = std::chrono::high_resolution_clock::now();
    
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(frameEnd - frameStart);
    lastFrameTimeMs = duration.count() / 1000.0f;
    
    frameTimeHistory.push_back(lastFrameTimeMs);
    while (frameTimeHistory.size() > historySize) {
        frameTimeHistory.pop_front();
    }
    
    averageDirty = true;
}

float PerformanceMonitor::getFrameTimeMs() const {
    return lastFrameTimeMs;
}

float PerformanceMonitor::getAverageFrameTimeMs() const {
    if (averageDirty && !frameTimeHistory.empty()) {
        cachedAverageFrameTime = std::accumulate(frameTimeHistory.begin(), frameTimeHistory.end(), 0.0f) 
                                 / frameTimeHistory.size();
        averageDirty = false;
    }
    return cachedAverageFrameTime;
}

float PerformanceMonitor::getFPS() const {
    if (lastFrameTimeMs > 0.0f) {
        return 1000.0f / lastFrameTimeMs;
    }
    return 0.0f;
}

float PerformanceMonitor::getAverageFPS() const {
    float avgFrameTime = getAverageFrameTimeMs();
    if (avgFrameTime > 0.0f) {
        return 1000.0f / avgFrameTime;
    }
    return 0.0f;
}

size_t PerformanceMonitor::getEstimatedMemoryUsage() const {
    return estimatedMemory;
}

void PerformanceMonitor::addMemoryAllocation(size_t bytes) {
    estimatedMemory += bytes;
}

void PerformanceMonitor::removeMemoryAllocation(size_t bytes) {
    if (bytes <= estimatedMemory) {
        estimatedMemory -= bytes;
    }
}

std::string PerformanceMonitor::getStatsString() const {
    std::ostringstream ss;
    ss << std::fixed << std::setprecision(2);
    ss << "Frame: " << getFrameTimeMs() << "ms | ";
    ss << "Avg: " << getAverageFrameTimeMs() << "ms | ";
    ss << "FPS: " << static_cast<int>(getAverageFPS()) << " | ";
    ss << "Mem: " << (estimatedMemory / 1024) << "KB";
    return ss.str();
}

void PerformanceMonitor::setHistorySize(size_t size) {
    historySize = size;
    while (frameTimeHistory.size() > historySize) {
        frameTimeHistory.pop_front();
    }
}

} // namespace ethereal
