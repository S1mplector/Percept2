/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 * 
 * iCloud File System Watcher & Background Sync Scheduler
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <CoreServices/CoreServices.h>
#import <dispatch/dispatch.h>
#include <mutex>
#include <atomic>
#include <vector>
#include <string>
#include <unordered_set>
#include <chrono>
#include <functional>

#pragma mark - File Watcher Types

struct WatchedPath {
    std::string path;
    FSEventStreamRef stream;
    dispatch_queue_t queue;
    int64_t lastEventMs;
    int32_t eventCount;
    int32_t isActive;
};

struct FileChangeEvent {
    std::string path;
    int64_t timestampMs;
    int32_t eventType; // 0=created, 1=modified, 2=deleted, 3=renamed
    int32_t flags;
};

#pragma mark - Global Watcher State

static std::mutex g_watcherMutex;
static std::vector<WatchedPath> g_watchedPaths;
static std::vector<FileChangeEvent> g_pendingEvents;
static std::atomic<int32_t> g_watcherActive{0};
static std::atomic<int32_t> g_eventCount{0};
static const size_t MAX_PENDING_EVENTS = 2048;
static const int64_t EVENT_COALESCE_MS = 100;

static int64_t watcher_time_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

#pragma mark - FSEvents Callback

static void simjot_fsevents_callback(ConstFSEventStreamRef streamRef,
                                      void* clientCallBackInfo,
                                      size_t numEvents,
                                      void* eventPaths,
                                      const FSEventStreamEventFlags eventFlags[],
                                      const FSEventStreamEventId eventIds[]) {
    (void)streamRef;
    (void)clientCallBackInfo;
    (void)eventIds;
    
    // eventPaths is CFArrayRef when using kFSEventStreamCreateFlagUseCFTypes
    CFArrayRef pathArray = (CFArrayRef)eventPaths;
    int64_t now = watcher_time_ms();
    
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    
    for (size_t i = 0; i < numEvents; i++) {
        if (g_pendingEvents.size() >= MAX_PENDING_EVENTS) {
            g_pendingEvents.erase(g_pendingEvents.begin());
        }
        
        // Get CFStringRef from array
        CFStringRef cfPath = (CFStringRef)CFArrayGetValueAtIndex(pathArray, (CFIndex)i);
        if (!cfPath) continue;
        
        // Convert to C string
        char pathBuffer[PATH_MAX];
        if (!CFStringGetCString(cfPath, pathBuffer, sizeof(pathBuffer), kCFStringEncodingUTF8)) {
            continue;
        }
        
        FileChangeEvent evt;
        evt.path = pathBuffer;
        evt.timestampMs = now;
        evt.flags = (int32_t)eventFlags[i];
        
        if (eventFlags[i] & kFSEventStreamEventFlagItemCreated) {
            evt.eventType = 0;
        } else if (eventFlags[i] & kFSEventStreamEventFlagItemRemoved) {
            evt.eventType = 2;
        } else if (eventFlags[i] & kFSEventStreamEventFlagItemRenamed) {
            evt.eventType = 3;
        } else {
            evt.eventType = 1; // modified
        }
        
        // Coalesce rapid events for same path
        bool coalesced = false;
        for (auto it = g_pendingEvents.rbegin(); it != g_pendingEvents.rend(); ++it) {
            if (it->path == evt.path && (now - it->timestampMs) < EVENT_COALESCE_MS) {
                it->timestampMs = now;
                it->eventType = evt.eventType;
                it->flags |= evt.flags;
                coalesced = true;
                break;
            }
        }
        
        if (!coalesced) {
            g_pendingEvents.push_back(evt);
            g_eventCount.fetch_add(1);
        }
    }
}

#endif // __APPLE__

extern "C" int32_t simjot_watcher_start(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return -1;
        
        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:nsPath isDirectory:&isDir] || !isDir) return -1;
        
        std::string pathStr(path);
        
        std::lock_guard<std::mutex> lock(g_watcherMutex);
        
        // Check if already watching
        for (size_t i = 0; i < g_watchedPaths.size(); i++) {
            if (g_watchedPaths[i].path == pathStr && g_watchedPaths[i].isActive) {
                return (int32_t)i;
            }
        }
        
        // Create new watcher
        CFStringRef cfPath = (__bridge CFStringRef)nsPath;
        CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void**)&cfPath, 1, NULL);
        
        FSEventStreamContext context = {0, NULL, NULL, NULL, NULL};
        
        FSEventStreamRef stream = FSEventStreamCreate(
            kCFAllocatorDefault,
            simjot_fsevents_callback,
            &context,
            pathsToWatch,
            kFSEventStreamEventIdSinceNow,
            0.3, // latency in seconds
            kFSEventStreamCreateFlagFileEvents |
            kFSEventStreamCreateFlagUseCFTypes |
            kFSEventStreamCreateFlagNoDefer
        );
        
        CFRelease(pathsToWatch);
        
        if (!stream) return -1;
        
        dispatch_queue_t queue = dispatch_queue_create("com.simjot.fswatcher", 
                                                        DISPATCH_QUEUE_SERIAL);
        FSEventStreamSetDispatchQueue(stream, queue);
        
        if (!FSEventStreamStart(stream)) {
            FSEventStreamInvalidate(stream);
            FSEventStreamRelease(stream);
            return -1;
        }
        
        WatchedPath wp;
        wp.path = pathStr;
        wp.stream = stream;
        wp.queue = queue;
        wp.lastEventMs = watcher_time_ms();
        wp.eventCount = 0;
        wp.isActive = 1;
        
        int32_t watcherId = (int32_t)g_watchedPaths.size();
        g_watchedPaths.push_back(wp);
        g_watcherActive.fetch_add(1);
        
        return watcherId;
    }
#else
    (void)path;
    return -1;
#endif
}

extern "C" int32_t simjot_watcher_stop(int32_t watcher_id) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    
    if (watcher_id < 0 || (size_t)watcher_id >= g_watchedPaths.size()) return 0;
    
    WatchedPath& wp = g_watchedPaths[watcher_id];
    if (!wp.isActive) return 0;
    
    if (wp.stream) {
        FSEventStreamStop(wp.stream);
        FSEventStreamInvalidate(wp.stream);
        FSEventStreamRelease(wp.stream);
        wp.stream = NULL;
    }
    
    wp.isActive = 0;
    g_watcherActive.fetch_sub(1);
    
    return 1;
#else
    (void)watcher_id;
    return 0;
#endif
}

extern "C" void simjot_watcher_stop_all(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    
    for (auto& wp : g_watchedPaths) {
        if (wp.isActive && wp.stream) {
            FSEventStreamStop(wp.stream);
            FSEventStreamInvalidate(wp.stream);
            FSEventStreamRelease(wp.stream);
            wp.stream = NULL;
            wp.isActive = 0;
        }
    }
    
    g_watcherActive.store(0);
#endif
}

extern "C" int32_t simjot_watcher_active_count(void) {
#ifdef __APPLE__
    return g_watcherActive.load();
#else
    return 0;
#endif
}

extern "C" int32_t simjot_watcher_event_count(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    return (int32_t)g_pendingEvents.size();
#else
    return 0;
#endif
}

extern "C" int32_t simjot_watcher_pop_event(char* path_out, int32_t path_len,
                                             int64_t* timestamp, int32_t* event_type) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    
    if (g_pendingEvents.empty()) return 0;
    
    FileChangeEvent evt = g_pendingEvents.front();
    g_pendingEvents.erase(g_pendingEvents.begin());
    
    if (path_out && path_len > 0) {
        size_t copyLen = std::min((size_t)path_len - 1, evt.path.length());
        memcpy(path_out, evt.path.c_str(), copyLen);
        path_out[copyLen] = '\0';
    }
    
    if (timestamp) *timestamp = evt.timestampMs;
    if (event_type) *event_type = evt.eventType;
    
    return 1;
#else
    (void)path_out; (void)path_len; (void)timestamp; (void)event_type;
    return 0;
#endif
}

extern "C" int32_t simjot_watcher_drain_events(int32_t max_events,
                                                char* paths_out, int32_t paths_len) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    
    if (g_pendingEvents.empty()) return 0;
    if (!paths_out || paths_len <= 0) {
        int32_t count = (int32_t)g_pendingEvents.size();
        if (max_events > 0 && count > max_events) count = max_events;
        g_pendingEvents.erase(g_pendingEvents.begin(), 
                              g_pendingEvents.begin() + count);
        return count;
    }
    
    int32_t count = 0;
    int32_t offset = 0;
    
    while (!g_pendingEvents.empty() && (max_events <= 0 || count < max_events)) {
        const FileChangeEvent& evt = g_pendingEvents.front();
        int32_t needed = (int32_t)evt.path.length() + 1;
        
        if (offset + needed >= paths_len) break;
        
        memcpy(paths_out + offset, evt.path.c_str(), evt.path.length());
        offset += (int32_t)evt.path.length();
        paths_out[offset++] = '\n';
        
        g_pendingEvents.erase(g_pendingEvents.begin());
        count++;
    }
    
    if (offset > 0) paths_out[offset - 1] = '\0';
    
    return count;
#else
    (void)max_events; (void)paths_out; (void)paths_len;
    return 0;
#endif
}

extern "C" void simjot_watcher_clear_events(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_watcherMutex);
    g_pendingEvents.clear();
#endif
}

#pragma mark - Background Sync Scheduler

#ifdef __APPLE__

struct SyncScheduleEntry {
    std::string rootPath;
    int64_t intervalMs;
    int64_t lastRunMs;
    int64_t nextRunMs;
    int32_t priority;
    int32_t enabled;
    int32_t runCount;
};

static std::mutex g_schedulerMutex;
static std::vector<SyncScheduleEntry> g_scheduleEntries;
static dispatch_source_t g_schedulerTimer = nil;
static std::atomic<int32_t> g_schedulerRunning{0};

#endif

extern "C" int32_t simjot_scheduler_add(const char* root_path, int64_t interval_ms, 
                                         int32_t priority) {
#ifdef __APPLE__
    if (!root_path || !*root_path || interval_ms <= 0) return -1;
    
    std::string pathStr(root_path);
    int64_t now = watcher_time_ms();
    
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    
    // Check if already scheduled
    for (size_t i = 0; i < g_scheduleEntries.size(); i++) {
        if (g_scheduleEntries[i].rootPath == pathStr) {
            g_scheduleEntries[i].intervalMs = interval_ms;
            g_scheduleEntries[i].priority = priority;
            g_scheduleEntries[i].enabled = 1;
            return (int32_t)i;
        }
    }
    
    SyncScheduleEntry entry;
    entry.rootPath = pathStr;
    entry.intervalMs = interval_ms;
    entry.lastRunMs = 0;
    entry.nextRunMs = now + interval_ms;
    entry.priority = priority;
    entry.enabled = 1;
    entry.runCount = 0;
    
    int32_t entryId = (int32_t)g_scheduleEntries.size();
    g_scheduleEntries.push_back(entry);
    
    return entryId;
#else
    (void)root_path; (void)interval_ms; (void)priority;
    return -1;
#endif
}

extern "C" int32_t simjot_scheduler_remove(int32_t entry_id) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    
    if (entry_id < 0 || (size_t)entry_id >= g_scheduleEntries.size()) return 0;
    
    g_scheduleEntries[entry_id].enabled = 0;
    return 1;
#else
    (void)entry_id;
    return 0;
#endif
}

extern "C" int32_t simjot_scheduler_get_next_due(char* path_out, int32_t path_len,
                                                   int64_t* next_run_ms) {
#ifdef __APPLE__
    int64_t now = watcher_time_ms();
    
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    
    int32_t bestIdx = -1;
    int64_t bestTime = INT64_MAX;
    
    for (size_t i = 0; i < g_scheduleEntries.size(); i++) {
        const auto& entry = g_scheduleEntries[i];
        if (!entry.enabled) continue;
        
        if (entry.nextRunMs <= now) {
            if (bestIdx < 0 || entry.priority > g_scheduleEntries[bestIdx].priority) {
                bestIdx = (int32_t)i;
                bestTime = entry.nextRunMs;
            }
        } else if (entry.nextRunMs < bestTime && bestIdx < 0) {
            bestTime = entry.nextRunMs;
        }
    }
    
    if (bestIdx >= 0 && path_out && path_len > 0) {
        const auto& entry = g_scheduleEntries[bestIdx];
        size_t copyLen = std::min((size_t)path_len - 1, entry.rootPath.length());
        memcpy(path_out, entry.rootPath.c_str(), copyLen);
        path_out[copyLen] = '\0';
    }
    
    if (next_run_ms) *next_run_ms = bestTime;
    
    return bestIdx;
#else
    (void)path_out; (void)path_len; (void)next_run_ms;
    return -1;
#endif
}

extern "C" void simjot_scheduler_mark_completed(int32_t entry_id) {
#ifdef __APPLE__
    int64_t now = watcher_time_ms();
    
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    
    if (entry_id < 0 || (size_t)entry_id >= g_scheduleEntries.size()) return;
    
    auto& entry = g_scheduleEntries[entry_id];
    entry.lastRunMs = now;
    entry.nextRunMs = now + entry.intervalMs;
    entry.runCount++;
#else
    (void)entry_id;
#endif
}

extern "C" int32_t simjot_scheduler_entry_count(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    int32_t count = 0;
    for (const auto& entry : g_scheduleEntries) {
        if (entry.enabled) count++;
    }
    return count;
#else
    return 0;
#endif
}

extern "C" void simjot_scheduler_clear(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_schedulerMutex);
    g_scheduleEntries.clear();
#endif
}

#pragma mark - iCloud Container Discovery

extern "C" int32_t simjot_icloud_discover_containers(char* out, int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!out || out_len <= 0) return 0;
        out[0] = '\0';
        
        NSFileManager* fm = [NSFileManager defaultManager];
        
        // Check ubiquity identity
        id token = [fm ubiquityIdentityToken];
        if (!token) return 0;
        
        int32_t offset = 0;
        
        // Get default container
        NSURL* defaultUrl = [fm URLForUbiquityContainerIdentifier:nil];
        if (defaultUrl) {
            NSString* path = [defaultUrl path];
            if (path) {
                NSData* data = [path dataUsingEncoding:NSUTF8StringEncoding];
                if (data && offset + (int32_t)[data length] + 1 < out_len) {
                    memcpy(out + offset, [data bytes], [data length]);
                    offset += (int32_t)[data length];
                    out[offset++] = '\n';
                }
            }
        }
        
        // Get iCloud Drive path
        NSString* icloudDrive = [NSHomeDirectory() 
            stringByAppendingPathComponent:@"Library/Mobile Documents/com~apple~CloudDocs"];
        if ([fm fileExistsAtPath:icloudDrive]) {
            NSData* data = [icloudDrive dataUsingEncoding:NSUTF8StringEncoding];
            if (data && offset + (int32_t)[data length] + 1 < out_len) {
                memcpy(out + offset, [data bytes], [data length]);
                offset += (int32_t)[data length];
                out[offset++] = '\n';
            }
        }
        
        if (offset > 0) out[offset - 1] = '\0';
        
        return offset > 0 ? 1 : 0;
    }
#else
    (void)out; (void)out_len;
    return 0;
#endif
}

extern "C" int32_t simjot_icloud_get_quota(int64_t* total_bytes, int64_t* available_bytes) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        
        NSURL* containerUrl = [fm URLForUbiquityContainerIdentifier:nil];
        if (!containerUrl) return 0;
        
        NSError* error = nil;
        NSDictionary* attrs = [fm attributesOfFileSystemForPath:[containerUrl path] 
                                                          error:&error];
        if (!attrs) return 0;
        
        if (total_bytes) {
            NSNumber* total = attrs[NSFileSystemSize];
            *total_bytes = total ? [total longLongValue] : 0;
        }
        
        if (available_bytes) {
            NSNumber* available = attrs[NSFileSystemFreeSize];
            *available_bytes = available ? [available longLongValue] : 0;
        }
        
        return 1;
    }
#else
    (void)total_bytes; (void)available_bytes;
    return 0;
#endif
}

extern "C" int32_t simjot_icloud_account_status(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        id token = [fm ubiquityIdentityToken];
        
        if (!token) return 0; // not signed in
        
        // Check if container is accessible
        NSURL* containerUrl = [fm URLForUbiquityContainerIdentifier:nil];
        if (!containerUrl) return 1; // signed in but no container
        
        // Check if we can write to container
        NSString* testPath = [[containerUrl path] 
            stringByAppendingPathComponent:@".simjot_connectivity_test"];
        NSData* testData = [@"test" dataUsingEncoding:NSUTF8StringEncoding];
        
        if ([testData writeToFile:testPath atomically:YES]) {
            [fm removeItemAtPath:testPath error:nil];
            return 3; // fully connected and writable
        }
        
        return 2; // connected but read-only or restricted
    }
#else
    return 0;
#endif
}
