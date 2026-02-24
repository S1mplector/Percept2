/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 * 
 * iCloud Sync - Progress Tracking & Performance Metrics
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>
#include <mutex>
#include <atomic>
#include <chrono>
#include <deque>
#include <string>

#pragma mark - Progress State

struct SyncProgressState {
    std::atomic<int32_t> state{0};
    std::atomic<int32_t> totalFiles{0};
    std::atomic<int32_t> processedFiles{0};
    std::atomic<int32_t> failedFiles{0};
    std::atomic<int32_t> skippedFiles{0};
    std::atomic<int64_t> totalBytes{0};
    std::atomic<int64_t> processedBytes{0};
    std::atomic<int64_t> startTimeMs{0};
    std::atomic<int64_t> lastUpdateMs{0};
    std::atomic<int32_t> currentBatchSize{0};
    std::atomic<int32_t> currentBatchProgress{0};
};

struct SyncMetricsData {
    std::atomic<int64_t> totalSyncs{0};
    std::atomic<int64_t> successfulSyncs{0};
    std::atomic<int64_t> failedSyncs{0};
    std::atomic<int64_t> totalBytesUploaded{0};
    std::atomic<int64_t> totalBytesDownloaded{0};
    std::atomic<int64_t> totalTimeMs{0};
    std::atomic<int64_t> avgLatencyMs{0};
    std::atomic<int64_t> peakLatencyMs{0};
    std::atomic<int32_t> conflictsResolved{0};
    std::atomic<int32_t> retriesPerformed{0};
};

struct LatencySample {
    int64_t timestampMs;
    int64_t latencyMs;
    int32_t operation;
};

static SyncProgressState g_progress;
static SyncMetricsData g_metrics;
static std::mutex g_latencyMutex;
static std::deque<LatencySample> g_latencySamples;
static const size_t MAX_LATENCY_SAMPLES = 1000;

static int64_t progress_time_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

#endif // __APPLE__

#pragma mark - Progress State

extern "C" void simjot_sync_progress_reset(void) {
#ifdef __APPLE__
    g_progress.state.store(0);
    g_progress.totalFiles.store(0);
    g_progress.processedFiles.store(0);
    g_progress.failedFiles.store(0);
    g_progress.skippedFiles.store(0);
    g_progress.totalBytes.store(0);
    g_progress.processedBytes.store(0);
    g_progress.startTimeMs.store(0);
    g_progress.lastUpdateMs.store(0);
    g_progress.currentBatchSize.store(0);
    g_progress.currentBatchProgress.store(0);
#endif
}

extern "C" void simjot_sync_progress_start(int32_t total_files, int64_t total_bytes) {
#ifdef __APPLE__
    simjot_sync_progress_reset();
    g_progress.state.store(1);
    g_progress.totalFiles.store(total_files);
    g_progress.totalBytes.store(total_bytes);
    g_progress.startTimeMs.store(progress_time_ms());
    g_progress.lastUpdateMs.store(progress_time_ms());
#else
    (void)total_files; (void)total_bytes;
#endif
}

extern "C" void simjot_sync_progress_update(int32_t processed_files, int64_t processed_bytes) {
#ifdef __APPLE__
    g_progress.processedFiles.store(processed_files);
    g_progress.processedBytes.store(processed_bytes);
    g_progress.lastUpdateMs.store(progress_time_ms());
#else
    (void)processed_files; (void)processed_bytes;
#endif
}

extern "C" void simjot_sync_progress_increment(int64_t bytes) {
#ifdef __APPLE__
    g_progress.processedFiles.fetch_add(1);
    g_progress.processedBytes.fetch_add(bytes);
    g_progress.lastUpdateMs.store(progress_time_ms());
#else
    (void)bytes;
#endif
}

extern "C" void simjot_sync_progress_fail(void) {
#ifdef __APPLE__
    g_progress.failedFiles.fetch_add(1);
    g_progress.lastUpdateMs.store(progress_time_ms());
#endif
}

extern "C" void simjot_sync_progress_skip(void) {
#ifdef __APPLE__
    g_progress.skippedFiles.fetch_add(1);
#endif
}

extern "C" void simjot_sync_progress_complete(int32_t success) {
#ifdef __APPLE__
    g_progress.state.store(success ? 2 : 3);
    g_progress.lastUpdateMs.store(progress_time_ms());
    
    int64_t duration = g_progress.lastUpdateMs.load() - g_progress.startTimeMs.load();
    g_metrics.totalSyncs.fetch_add(1);
    g_metrics.totalTimeMs.fetch_add(duration);
    
    if (success) {
        g_metrics.successfulSyncs.fetch_add(1);
    } else {
        g_metrics.failedSyncs.fetch_add(1);
    }
#else
    (void)success;
#endif
}

extern "C" void simjot_sync_progress_set_batch(int32_t batch_size, int32_t batch_progress) {
#ifdef __APPLE__
    g_progress.currentBatchSize.store(batch_size);
    g_progress.currentBatchProgress.store(batch_progress);
#else
    (void)batch_size; (void)batch_progress;
#endif
}

extern "C" int32_t simjot_sync_progress_get_state(void) {
#ifdef __APPLE__
    return g_progress.state.load();
#else
    return 0;
#endif
}

extern "C" float simjot_sync_progress_get_percent(void) {
#ifdef __APPLE__
    int32_t total = g_progress.totalFiles.load();
    if (total <= 0) return 0.0f;
    
    int32_t processed = g_progress.processedFiles.load();
    return (float)processed / (float)total * 100.0f;
#else
    return 0.0f;
#endif
}

extern "C" float simjot_sync_progress_get_bytes_percent(void) {
#ifdef __APPLE__
    int64_t total = g_progress.totalBytes.load();
    if (total <= 0) return 0.0f;
    
    int64_t processed = g_progress.processedBytes.load();
    return (float)((double)processed / (double)total * 100.0);
#else
    return 0.0f;
#endif
}

extern "C" int32_t simjot_sync_progress_get_stats(int32_t* total, int32_t* processed,
                                                   int32_t* failed, int32_t* skipped) {
#ifdef __APPLE__
    if (total) *total = g_progress.totalFiles.load();
    if (processed) *processed = g_progress.processedFiles.load();
    if (failed) *failed = g_progress.failedFiles.load();
    if (skipped) *skipped = g_progress.skippedFiles.load();
    return g_progress.state.load();
#else
    (void)total; (void)processed; (void)failed; (void)skipped;
    return 0;
#endif
}

extern "C" int64_t simjot_sync_progress_get_elapsed_ms(void) {
#ifdef __APPLE__
    int64_t start = g_progress.startTimeMs.load();
    if (start == 0) return 0;
    return progress_time_ms() - start;
#else
    return 0;
#endif
}

extern "C" int64_t simjot_sync_progress_estimate_remaining_ms(void) {
#ifdef __APPLE__
    int32_t total = g_progress.totalFiles.load();
    int32_t processed = g_progress.processedFiles.load();
    if (total <= 0 || processed <= 0) return 0;
    
    int64_t elapsed = simjot_sync_progress_get_elapsed_ms();
    if (elapsed <= 0) return 0;
    
    int32_t remaining = total - processed;
    double msPerFile = (double)elapsed / (double)processed;
    return (int64_t)(msPerFile * remaining);
#else
    return 0;
#endif
}

extern "C" int64_t simjot_sync_progress_get_speed_bps(void) {
#ifdef __APPLE__
    int64_t elapsed = simjot_sync_progress_get_elapsed_ms();
    if (elapsed <= 0) return 0;
    
    int64_t bytes = g_progress.processedBytes.load();
    return (int64_t)((double)bytes / (double)elapsed * 1000.0);
#else
    return 0;
#endif
}

#pragma mark - Performance Metrics

extern "C" void simjot_sync_metrics_reset(void) {
#ifdef __APPLE__
    g_metrics.totalSyncs.store(0);
    g_metrics.successfulSyncs.store(0);
    g_metrics.failedSyncs.store(0);
    g_metrics.totalBytesUploaded.store(0);
    g_metrics.totalBytesDownloaded.store(0);
    g_metrics.totalTimeMs.store(0);
    g_metrics.avgLatencyMs.store(0);
    g_metrics.peakLatencyMs.store(0);
    g_metrics.conflictsResolved.store(0);
    g_metrics.retriesPerformed.store(0);
    
    std::lock_guard<std::mutex> lock(g_latencyMutex);
    g_latencySamples.clear();
#endif
}

extern "C" void simjot_sync_metrics_record_upload(int64_t bytes, int64_t latency_ms) {
#ifdef __APPLE__
    g_metrics.totalBytesUploaded.fetch_add(bytes);
    
    int64_t peak = g_metrics.peakLatencyMs.load();
    while (latency_ms > peak && 
           !g_metrics.peakLatencyMs.compare_exchange_weak(peak, latency_ms)) {}
    
    std::lock_guard<std::mutex> lock(g_latencyMutex);
    g_latencySamples.push_back({progress_time_ms(), latency_ms, 1});
    while (g_latencySamples.size() > MAX_LATENCY_SAMPLES) {
        g_latencySamples.pop_front();
    }
#else
    (void)bytes; (void)latency_ms;
#endif
}

extern "C" void simjot_sync_metrics_record_download(int64_t bytes, int64_t latency_ms) {
#ifdef __APPLE__
    g_metrics.totalBytesDownloaded.fetch_add(bytes);
    
    int64_t peak = g_metrics.peakLatencyMs.load();
    while (latency_ms > peak && 
           !g_metrics.peakLatencyMs.compare_exchange_weak(peak, latency_ms)) {}
    
    std::lock_guard<std::mutex> lock(g_latencyMutex);
    g_latencySamples.push_back({progress_time_ms(), latency_ms, 0});
    while (g_latencySamples.size() > MAX_LATENCY_SAMPLES) {
        g_latencySamples.pop_front();
    }
#else
    (void)bytes; (void)latency_ms;
#endif
}

extern "C" void simjot_sync_metrics_record_conflict(void) {
#ifdef __APPLE__
    g_metrics.conflictsResolved.fetch_add(1);
#endif
}

extern "C" void simjot_sync_metrics_record_retry(void) {
#ifdef __APPLE__
    g_metrics.retriesPerformed.fetch_add(1);
#endif
}

extern "C" int32_t simjot_sync_metrics_get_summary(uint8_t* out, int32_t out_len) {
#ifdef __APPLE__
    if (!out || out_len < 80) return 0;
    
    // Pack metrics into binary format: 10 x int64_t
    int64_t* data = (int64_t*)out;
    data[0] = g_metrics.totalSyncs.load();
    data[1] = g_metrics.successfulSyncs.load();
    data[2] = g_metrics.failedSyncs.load();
    data[3] = g_metrics.totalBytesUploaded.load();
    data[4] = g_metrics.totalBytesDownloaded.load();
    data[5] = g_metrics.totalTimeMs.load();
    data[6] = g_metrics.avgLatencyMs.load();
    data[7] = g_metrics.peakLatencyMs.load();
    data[8] = g_metrics.conflictsResolved.load();
    data[9] = g_metrics.retriesPerformed.load();
    
    return 80;
#else
    (void)out; (void)out_len;
    return 0;
#endif
}

extern "C" int64_t simjot_sync_metrics_get_avg_latency(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_latencyMutex);
    if (g_latencySamples.empty()) return 0;
    
    int64_t sum = 0;
    for (const auto& sample : g_latencySamples) {
        sum += sample.latencyMs;
    }
    return sum / (int64_t)g_latencySamples.size();
#else
    return 0;
#endif
}

extern "C" int64_t simjot_sync_metrics_get_p95_latency(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_latencyMutex);
    if (g_latencySamples.empty()) return 0;
    
    std::vector<int64_t> latencies;
    latencies.reserve(g_latencySamples.size());
    for (const auto& sample : g_latencySamples) {
        latencies.push_back(sample.latencyMs);
    }
    
    std::sort(latencies.begin(), latencies.end());
    size_t p95Index = (size_t)(latencies.size() * 0.95);
    if (p95Index >= latencies.size()) p95Index = latencies.size() - 1;
    
    return latencies[p95Index];
#else
    return 0;
#endif
}

extern "C" float simjot_sync_metrics_get_success_rate(void) {
#ifdef __APPLE__
    int64_t total = g_metrics.totalSyncs.load();
    if (total <= 0) return 100.0f;
    
    int64_t successful = g_metrics.successfulSyncs.load();
    return (float)((double)successful / (double)total * 100.0);
#else
    return 0.0f;
#endif
}

extern "C" int64_t simjot_sync_metrics_total_bytes_synced(void) {
#ifdef __APPLE__
    return g_metrics.totalBytesUploaded.load() + g_metrics.totalBytesDownloaded.load();
#else
    return 0;
#endif
}

#pragma mark - Retry Management with Exponential Backoff

static std::atomic<int32_t> g_retryCount{0};
static std::atomic<int64_t> g_lastRetryMs{0};
static const int64_t RETRY_BASE_MS = 100;
static const int64_t RETRY_MAX_MS = 30000;
static const int32_t RETRY_MAX_COUNT = 8;

extern "C" int64_t simjot_sync_get_retry_delay_ms(int32_t attempt) {
#ifdef __APPLE__
    if (attempt <= 0) return 0;
    if (attempt > RETRY_MAX_COUNT) return RETRY_MAX_MS;
    
    // Exponential backoff with jitter
    int64_t base = RETRY_BASE_MS * (1LL << (attempt - 1));
    if (base > RETRY_MAX_MS) base = RETRY_MAX_MS;
    
    // Add ±25% jitter
    int32_t jitter = (int32_t)(arc4random_uniform((uint32_t)(base / 2))) - (int32_t)(base / 4);
    return base + jitter;
#else
    (void)attempt;
    return 0;
#endif
}

extern "C" int32_t simjot_sync_should_retry(int32_t current_attempt, int32_t error_code) {
#ifdef __APPLE__
    if (current_attempt >= RETRY_MAX_COUNT) return 0;
    
    // Don't retry for permanent errors
    switch (error_code) {
        case 401: // unauthorized
        case 403: // forbidden
        case 404: // not found
        case 410: // gone
            return 0;
        default:
            return 1;
    }
#else
    (void)current_attempt; (void)error_code;
    return 0;
#endif
}

extern "C" void simjot_sync_record_retry_attempt(void) {
#ifdef __APPLE__
    g_retryCount.fetch_add(1);
    g_lastRetryMs.store(progress_time_ms());
    g_metrics.retriesPerformed.fetch_add(1);
#endif
}

extern "C" int32_t simjot_sync_get_retry_count(void) {
#ifdef __APPLE__
    return g_retryCount.load();
#else
    return 0;
#endif
}

extern "C" void simjot_sync_reset_retry_count(void) {
#ifdef __APPLE__
    g_retryCount.store(0);
#endif
}
