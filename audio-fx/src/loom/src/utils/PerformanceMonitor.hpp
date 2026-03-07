#pragma once
#include <chrono>
#include <deque>
#include <string>

namespace ethereal {

class PerformanceMonitor {
public:
    PerformanceMonitor();

    void beginFrame();
    void endFrame();
    
    float getFrameTimeMs() const;
    float getAverageFrameTimeMs() const;
    float getFPS() const;
    float getAverageFPS() const;
    
    size_t getEstimatedMemoryUsage() const;
    void addMemoryAllocation(size_t bytes);
    void removeMemoryAllocation(size_t bytes);
    
    std::string getStatsString() const;
    
    void setHistorySize(size_t size);

private:
    std::chrono::high_resolution_clock::time_point frameStart;
    std::chrono::high_resolution_clock::time_point frameEnd;
    
    std::deque<float> frameTimeHistory;
    size_t historySize;
    
    float lastFrameTimeMs;
    size_t estimatedMemory;
    
    mutable float cachedAverageFrameTime;
    mutable bool averageDirty;
};

} // namespace ethereal
