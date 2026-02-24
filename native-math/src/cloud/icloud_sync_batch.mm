/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 * 
 * iCloud Sync - Batch Operations & Delta Tracking
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>
#include <mutex>
#include <vector>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <chrono>
#include <atomic>
#include <algorithm>

#pragma mark - Batch Operation Types

struct SyncBatchItem {
    std::string path;
    int32_t operation; // 0=download, 1=upload, 2=delete, 3=conflict
    int32_t priority;
    int64_t size;
    int64_t modTime;
    int32_t retryCount;
    int64_t lastAttemptMs;
};

struct SyncBatchResult {
    int32_t succeeded;
    int32_t failed;
    int32_t skipped;
    int32_t conflicts;
    int64_t bytesProcessed;
    int64_t durationMs;
};

struct DeltaEntry {
    std::string path;
    int64_t modTime;
    int64_t size;
    int32_t changeType; // 0=created, 1=modified, 2=deleted
    uint8_t hash[32];
};

#pragma mark - Global Batch State

static std::mutex g_batchMutex;
static std::vector<SyncBatchItem> g_pendingBatch;
static std::unordered_map<std::string, DeltaEntry> g_deltaLog;
static std::atomic<int64_t> g_lastDeltaScanMs{0};
static std::atomic<int32_t> g_batchInProgress{0};

static const int32_t BATCH_MAX_SIZE = 512;
static const int32_t BATCH_MAX_RETRIES = 3;
static const int64_t DELTA_SCAN_COOLDOWN_MS = 5000;

#pragma mark - Delta Tracking

static int64_t sync_time_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

#endif // __APPLE__

extern "C" int32_t simjot_sync_delta_scan(const char* root_path, int64_t since_ms) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!root_path || !*root_path) return -1;
        
        int64_t now = sync_time_ms();
        int64_t last = g_lastDeltaScanMs.load();
        if (now - last < DELTA_SCAN_COOLDOWN_MS) return 0;
        if (!g_lastDeltaScanMs.compare_exchange_strong(last, now)) return 0;
        
        NSString* rootStr = [NSString stringWithUTF8String:root_path];
        if (!rootStr) return -1;
        
        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootStr isDirectory:&isDir] || !isDir) return -1;
        
        NSDate* sinceDate = [NSDate dateWithTimeIntervalSince1970:since_ms / 1000.0];
        
        NSArray* keys = @[
            NSURLContentModificationDateKey,
            NSURLFileSizeKey,
            NSURLIsDirectoryKey,
            NSURLIsRegularFileKey
        ];
        
        NSDirectoryEnumerator* enumerator = [fm enumeratorAtURL:[NSURL fileURLWithPath:rootStr]
                                     includingPropertiesForKeys:keys
                                                        options:(NSDirectoryEnumerationSkipsHiddenFiles |
                                                                 NSDirectoryEnumerationSkipsPackageDescendants)
                                                   errorHandler:nil];
        
        int32_t changedCount = 0;
        std::lock_guard<std::mutex> lock(g_batchMutex);
        
        for (NSURL* url in enumerator) {
            NSNumber* isFile = nil;
            [url getResourceValue:&isFile forKey:NSURLIsRegularFileKey error:nil];
            if (!isFile || ![isFile boolValue]) continue;
            
            NSDate* modDate = nil;
            [url getResourceValue:&modDate forKey:NSURLContentModificationDateKey error:nil];
            if (!modDate) continue;
            
            if ([modDate compare:sinceDate] == NSOrderedDescending) {
                NSString* path = [url path];
                if (!path) continue;
                
                NSNumber* fileSize = nil;
                [url getResourceValue:&fileSize forKey:NSURLFileSizeKey error:nil];
                
                std::string pathStr([path UTF8String]);
                DeltaEntry entry;
                entry.path = pathStr;
                entry.modTime = (int64_t)([modDate timeIntervalSince1970] * 1000);
                entry.size = fileSize ? [fileSize longLongValue] : 0;
                entry.changeType = 1; // modified
                memset(entry.hash, 0, 32);
                
                g_deltaLog[pathStr] = entry;
                changedCount++;
            }
        }
        
        return changedCount;
    }
#else
    (void)root_path;
    (void)since_ms;
    return -1;
#endif
}

extern "C" int32_t simjot_sync_delta_count(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    return (int32_t)g_deltaLog.size();
#else
    return 0;
#endif
}

extern "C" int32_t simjot_sync_delta_get(int32_t index, char* path_out, int32_t path_len,
                                          int64_t* mod_time, int64_t* size, int32_t* change_type) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    if (index < 0 || (size_t)index >= g_deltaLog.size()) return 0;
    
    auto it = g_deltaLog.begin();
    std::advance(it, index);
    
    if (path_out && path_len > 0) {
        size_t copyLen = std::min((size_t)path_len - 1, it->second.path.length());
        memcpy(path_out, it->second.path.c_str(), copyLen);
        path_out[copyLen] = '\0';
    }
    
    if (mod_time) *mod_time = it->second.modTime;
    if (size) *size = it->second.size;
    if (change_type) *change_type = it->second.changeType;
    
    return 1;
#else
    (void)index; (void)path_out; (void)path_len;
    (void)mod_time; (void)size; (void)change_type;
    return 0;
#endif
}

extern "C" void simjot_sync_delta_clear(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    g_deltaLog.clear();
#endif
}

#pragma mark - Batch Queue Management

extern "C" int32_t simjot_sync_batch_add(const char* path, int32_t operation, int32_t priority) {
#ifdef __APPLE__
    if (!path || !*path) return 0;
    
    std::lock_guard<std::mutex> lock(g_batchMutex);
    if (g_pendingBatch.size() >= BATCH_MAX_SIZE) return 0;
    
    std::string pathStr(path);
    for (const auto& item : g_pendingBatch) {
        if (item.path == pathStr && item.operation == operation) return 0;
    }
    
    SyncBatchItem item;
    item.path = pathStr;
    item.operation = operation;
    item.priority = priority;
    item.size = 0;
    item.modTime = 0;
    item.retryCount = 0;
    item.lastAttemptMs = 0;
    
    g_pendingBatch.push_back(item);
    return 1;
#else
    (void)path; (void)operation; (void)priority;
    return 0;
#endif
}

extern "C" int32_t simjot_sync_batch_count(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    return (int32_t)g_pendingBatch.size();
#else
    return 0;
#endif
}

extern "C" int32_t simjot_sync_batch_peek(int32_t index, char* path_out, int32_t path_len,
                                           int32_t* operation, int32_t* priority) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    if (index < 0 || (size_t)index >= g_pendingBatch.size()) return 0;
    
    const auto& item = g_pendingBatch[index];
    
    if (path_out && path_len > 0) {
        size_t copyLen = std::min((size_t)path_len - 1, item.path.length());
        memcpy(path_out, item.path.c_str(), copyLen);
        path_out[copyLen] = '\0';
    }
    
    if (operation) *operation = item.operation;
    if (priority) *priority = item.priority;
    
    return 1;
#else
    (void)index; (void)path_out; (void)path_len;
    (void)operation; (void)priority;
    return 0;
#endif
}

extern "C" void simjot_sync_batch_clear(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    g_pendingBatch.clear();
#endif
}

extern "C" void simjot_sync_batch_sort_priority(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_batchMutex);
    std::sort(g_pendingBatch.begin(), g_pendingBatch.end(),
              [](const SyncBatchItem& a, const SyncBatchItem& b) {
                  return a.priority > b.priority;
              });
#endif
}

#pragma mark - Batch Execution

extern "C" int32_t simjot_sync_batch_execute(int32_t max_items, int32_t timeout_ms,
                                              int32_t* out_succeeded, int32_t* out_failed) {
#ifdef __APPLE__
    @autoreleasepool {
        if (g_batchInProgress.exchange(1) != 0) return -1;
        
        int64_t startTime = sync_time_ms();
        int64_t deadline = startTime + timeout_ms;
        int32_t succeeded = 0;
        int32_t failed = 0;
        int32_t processed = 0;
        
        NSFileManager* fm = [NSFileManager defaultManager];
        
        while (processed < max_items && sync_time_ms() < deadline) {
            SyncBatchItem item;
            {
                std::lock_guard<std::mutex> lock(g_batchMutex);
                if (g_pendingBatch.empty()) break;
                
                item = g_pendingBatch.front();
                g_pendingBatch.erase(g_pendingBatch.begin());
            }
            
            processed++;
            NSString* path = [NSString stringWithUTF8String:item.path.c_str()];
            if (!path) {
                failed++;
                continue;
            }
            
            BOOL success = NO;
            NSURL* url = [NSURL fileURLWithPath:path];
            
            switch (item.operation) {
                case 0: { // download
                    NSError* error = nil;
                    success = [fm startDownloadingUbiquitousItemAtURL:url error:&error];
                    break;
                }
                case 1: { // upload - touch to trigger sync
                    NSDictionary* attrs = @{NSFileModificationDate: [NSDate date]};
                    success = [fm setAttributes:attrs ofItemAtPath:path error:nil];
                    break;
                }
                case 2: { // delete
                    NSError* error = nil;
                    if ([fm fileExistsAtPath:path]) {
                        success = [fm removeItemAtPath:path error:&error];
                    } else {
                        success = YES;
                    }
                    break;
                }
                case 3: { // conflict resolution - mark as resolved
                    NSNumber* hasConflict = nil;
                    if ([url getResourceValue:&hasConflict 
                                       forKey:NSURLUbiquitousItemHasUnresolvedConflictsKey 
                                        error:nil]) {
                        success = !hasConflict || ![hasConflict boolValue];
                    }
                    break;
                }
                default:
                    success = NO;
            }
            
            if (success) {
                succeeded++;
            } else {
                failed++;
                if (item.retryCount < BATCH_MAX_RETRIES) {
                    item.retryCount++;
                    item.lastAttemptMs = sync_time_ms();
                    std::lock_guard<std::mutex> lock(g_batchMutex);
                    g_pendingBatch.push_back(item);
                }
            }
        }
        
        g_batchInProgress.store(0);
        
        if (out_succeeded) *out_succeeded = succeeded;
        if (out_failed) *out_failed = failed;
        
        return processed;
    }
#else
    (void)max_items; (void)timeout_ms;
    (void)out_succeeded; (void)out_failed;
    return -1;
#endif
}

extern "C" int32_t simjot_sync_batch_in_progress(void) {
#ifdef __APPLE__
    return g_batchInProgress.load();
#else
    return 0;
#endif
}

#pragma mark - Throttling

static std::atomic<int64_t> g_throttleWindowStart{0};
static std::atomic<int32_t> g_throttleWindowOps{0};
static const int32_t THROTTLE_WINDOW_MS = 1000;
static const int32_t THROTTLE_MAX_OPS = 50;

extern "C" int32_t simjot_sync_should_throttle(void) {
#ifdef __APPLE__
    int64_t now = sync_time_ms();
    int64_t windowStart = g_throttleWindowStart.load();
    
    if (now - windowStart >= THROTTLE_WINDOW_MS) {
        g_throttleWindowStart.store(now);
        g_throttleWindowOps.store(0);
        return 0;
    }
    
    int32_t ops = g_throttleWindowOps.load();
    return (ops >= THROTTLE_MAX_OPS) ? 1 : 0;
#else
    return 0;
#endif
}

extern "C" void simjot_sync_record_operation(void) {
#ifdef __APPLE__
    g_throttleWindowOps.fetch_add(1);
#endif
}

extern "C" int32_t simjot_sync_get_throttle_delay_ms(void) {
#ifdef __APPLE__
    int32_t ops = g_throttleWindowOps.load();
    if (ops < THROTTLE_MAX_OPS / 2) return 0;
    if (ops < THROTTLE_MAX_OPS) return 50;
    return 200;
#else
    return 0;
#endif
}
