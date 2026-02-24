/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 * 
 * Advanced iCloud Sync Module - Optimized cloud synchronization for macOS
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <SystemConfiguration/SystemConfiguration.h>
#import <CommonCrypto/CommonDigest.h>
#import <dispatch/dispatch.h>
#import <os/lock.h>
#import <copyfile.h>
#import <sys/stat.h>
#import <sys/socket.h>
#import <netinet/in.h>
#import <arpa/inet.h>
#import <fcntl.h>
#import <unistd.h>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <string>
#include <chrono>

#pragma mark - Constants & Types

static const int32_t SIMJOT_SYNC_VERSION = 1;
static const int32_t SYNC_MAX_BATCH_SIZE = 256;
static const int32_t SYNC_MAX_RETRY_COUNT = 5;
static const int64_t SYNC_RETRY_BASE_MS = 100;
static const int64_t SYNC_THROTTLE_MIN_MS = 50;
static const int64_t SYNC_THROTTLE_MAX_MS = 5000;
static const int32_t SYNC_HASH_CACHE_SIZE = 4096;
static const int32_t SYNC_DELTA_WINDOW_SECS = 300;

enum SimjotSyncState {
    SYNC_STATE_IDLE = 0,
    SYNC_STATE_SCANNING = 1,
    SYNC_STATE_UPLOADING = 2,
    SYNC_STATE_DOWNLOADING = 3,
    SYNC_STATE_RESOLVING = 4,
    SYNC_STATE_ERROR = 5
};

enum SimjotNetworkState {
    NETWORK_STATE_UNKNOWN = 0,
    NETWORK_STATE_DISCONNECTED = 1,
    NETWORK_STATE_WIFI = 2,
    NETWORK_STATE_CELLULAR = 3,
    NETWORK_STATE_WIRED = 4
};

enum SimjotSyncPriority {
    SYNC_PRIORITY_LOW = 0,
    SYNC_PRIORITY_NORMAL = 1,
    SYNC_PRIORITY_HIGH = 2,
    SYNC_PRIORITY_CRITICAL = 3
};

struct SyncFileRecord {
    std::string path;
    uint64_t localModTime;
    uint64_t cloudModTime;
    uint64_t size;
    uint8_t localHash[32];
    uint8_t cloudHash[32];
    int32_t syncState;
    int32_t retryCount;
    int64_t lastAttemptMs;
    int32_t priority;
};

struct SyncProgress {
    std::atomic<int32_t> totalFiles{0};
    std::atomic<int32_t> processedFiles{0};
    std::atomic<int64_t> totalBytes{0};
    std::atomic<int64_t> processedBytes{0};
    std::atomic<int32_t> failedFiles{0};
    std::atomic<int32_t> conflictFiles{0};
    std::atomic<int32_t> state{SYNC_STATE_IDLE};
    std::atomic<int64_t> startTimeMs{0};
    std::atomic<int64_t> lastUpdateMs{0};
};

struct SyncMetrics {
    std::atomic<int64_t> totalSyncs{0};
    std::atomic<int64_t> successfulSyncs{0};
    std::atomic<int64_t> failedSyncs{0};
    std::atomic<int64_t> totalBytesUp{0};
    std::atomic<int64_t> totalBytesDown{0};
    std::atomic<int64_t> totalTimeMs{0};
    std::atomic<int64_t> avgLatencyMs{0};
    std::atomic<int32_t> activeConnections{0};
};

#pragma mark - Global State

static std::mutex g_syncMutex;
static std::unordered_map<std::string, SyncFileRecord> g_syncRegistry;
static std::unordered_map<std::string, std::pair<uint8_t[32], int64_t>> g_hashCache;
static SyncProgress g_syncProgress;
static SyncMetrics g_syncMetrics;
static std::atomic<int32_t> g_networkState{NETWORK_STATE_UNKNOWN};
static std::atomic<bool> g_syncEnabled{true};
static std::atomic<bool> g_lowPowerMode{false};
static std::atomic<int64_t> g_lastThrottleMs{0};
static std::atomic<int32_t> g_throttleLevel{0};
static dispatch_queue_t g_syncQueue = nil;
static SCNetworkReachabilityRef g_reachabilityRef = NULL;

#pragma mark - Utility Functions

static int64_t simjot_sync_time_ms(void) {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static void simjot_sync_compute_sha256(const uint8_t* data, size_t len, uint8_t* out32) {
    CC_SHA256(data, (CC_LONG)len, out32);
}

static int simjot_sync_compute_file_hash(const char* path, uint8_t* out32) {
    if (!path || !out32) return 0;
    
    int fd = open(path, O_RDONLY);
    if (fd < 0) return 0;
    
    CC_SHA256_CTX ctx;
    CC_SHA256_Init(&ctx);
    
    uint8_t buffer[65536];
    ssize_t bytesRead;
    while ((bytesRead = read(fd, buffer, sizeof(buffer))) > 0) {
        CC_SHA256_Update(&ctx, buffer, (CC_LONG)bytesRead);
    }
    
    close(fd);
    if (bytesRead < 0) return 0;
    
    CC_SHA256_Final(out32, &ctx);
    return 1;
}

static int simjot_sync_hashes_equal(const uint8_t* h1, const uint8_t* h2) {
    if (!h1 || !h2) return 0;
    return memcmp(h1, h2, 32) == 0;
}

static int64_t simjot_sync_get_mtime(const char* path) {
    if (!path) return 0;
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return (int64_t)st.st_mtime * 1000LL;
}

static int64_t simjot_sync_get_size(const char* path) {
    if (!path) return 0;
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return (int64_t)st.st_size;
}

#pragma mark - Network Reachability

static void simjot_reachability_callback(SCNetworkReachabilityRef target, 
                                          SCNetworkReachabilityFlags flags, 
                                          void* info) {
    (void)target;
    (void)info;
    
    if (!(flags & kSCNetworkReachabilityFlagsReachable)) {
        g_networkState.store(NETWORK_STATE_DISCONNECTED);
        return;
    }
    
    // On macOS, we don't have WWAN - use connection type heuristics
    if (flags & kSCNetworkReachabilityFlagsConnectionOnDemand) {
        g_networkState.store(NETWORK_STATE_WIFI);
    } else if (flags & kSCNetworkReachabilityFlagsIsLocalAddress) {
        g_networkState.store(NETWORK_STATE_WIRED);
    } else {
        g_networkState.store(NETWORK_STATE_WIFI);
    }
}

#endif // __APPLE__

extern "C" int32_t simjot_sync_init(void) {
#ifdef __APPLE__
    static std::once_flag initFlag;
    std::call_once(initFlag, []() {
        g_syncQueue = dispatch_queue_create("com.simjot.sync", 
                                            DISPATCH_QUEUE_SERIAL);
        dispatch_set_target_queue(g_syncQueue, 
            dispatch_get_global_queue(QOS_CLASS_UTILITY, 0));
        
        struct sockaddr_in zeroAddress;
        memset(&zeroAddress, 0, sizeof(zeroAddress));
        zeroAddress.sin_len = sizeof(zeroAddress);
        zeroAddress.sin_family = AF_INET;
        
        g_reachabilityRef = SCNetworkReachabilityCreateWithAddress(
            kCFAllocatorDefault, (const struct sockaddr*)&zeroAddress);
        
        if (g_reachabilityRef) {
            SCNetworkReachabilityContext ctx = {0, NULL, NULL, NULL, NULL};
            SCNetworkReachabilitySetCallback(g_reachabilityRef, 
                                             simjot_reachability_callback, &ctx);
            SCNetworkReachabilityScheduleWithRunLoop(g_reachabilityRef,
                CFRunLoopGetMain(), kCFRunLoopDefaultMode);
            
            SCNetworkReachabilityFlags flags;
            if (SCNetworkReachabilityGetFlags(g_reachabilityRef, &flags)) {
                simjot_reachability_callback(g_reachabilityRef, flags, NULL);
            }
        }
    });
    return 1;
#else
    return 0;
#endif
}

extern "C" void simjot_sync_shutdown(void) {
#ifdef __APPLE__
    if (g_reachabilityRef) {
        SCNetworkReachabilityUnscheduleFromRunLoop(g_reachabilityRef,
            CFRunLoopGetMain(), kCFRunLoopDefaultMode);
        CFRelease(g_reachabilityRef);
        g_reachabilityRef = NULL;
    }
    
    std::lock_guard<std::mutex> lock(g_syncMutex);
    g_syncRegistry.clear();
    g_hashCache.clear();
#endif
}

extern "C" int32_t simjot_sync_get_network_state(void) {
#ifdef __APPLE__
    return g_networkState.load();
#else
    return NETWORK_STATE_UNKNOWN;
#endif
}

extern "C" int32_t simjot_sync_is_connected(void) {
#ifdef __APPLE__
    int32_t state = g_networkState.load();
    return (state >= NETWORK_STATE_WIFI) ? 1 : 0;
#else
    return 0;
#endif
}

extern "C" void simjot_sync_set_enabled(int32_t enabled) {
#ifdef __APPLE__
    g_syncEnabled.store(enabled != 0);
#else
    (void)enabled;
#endif
}

extern "C" int32_t simjot_sync_is_enabled(void) {
#ifdef __APPLE__
    return g_syncEnabled.load() ? 1 : 0;
#else
    return 0;
#endif
}

extern "C" void simjot_sync_set_low_power(int32_t lowPower) {
#ifdef __APPLE__
    g_lowPowerMode.store(lowPower != 0);
#else
    (void)lowPower;
#endif
}

#ifdef __APPLE__

#pragma mark - Hash Cache Management

static int simjot_sync_get_cached_hash(const char* path, uint8_t* out32, int64_t* outMtime) {
    if (!path || !out32) return 0;
    
    std::string key(path);
    std::lock_guard<std::mutex> lock(g_syncMutex);
    
    auto it = g_hashCache.find(key);
    if (it == g_hashCache.end()) return 0;
    
    int64_t currentMtime = simjot_sync_get_mtime(path);
    if (currentMtime != it->second.second) {
        g_hashCache.erase(it);
        return 0;
    }
    
    memcpy(out32, it->second.first, 32);
    if (outMtime) *outMtime = it->second.second;
    return 1;
}

static void simjot_sync_cache_hash(const char* path, const uint8_t* hash32, int64_t mtime) {
    if (!path || !hash32) return;
    
    std::string key(path);
    std::lock_guard<std::mutex> lock(g_syncMutex);
    
    if (g_hashCache.size() >= SYNC_HASH_CACHE_SIZE) {
        auto oldest = g_hashCache.begin();
        int64_t oldestTime = oldest->second.second;
        for (auto it = g_hashCache.begin(); it != g_hashCache.end(); ++it) {
            if (it->second.second < oldestTime) {
                oldest = it;
                oldestTime = it->second.second;
            }
        }
        g_hashCache.erase(oldest);
    }
    
    auto& entry = g_hashCache[key];
    memcpy(entry.first, hash32, 32);
    entry.second = mtime;
}

#endif // __APPLE__

extern "C" int32_t simjot_sync_compute_hash(const char* path, uint8_t* out32) {
#ifdef __APPLE__
    if (!path || !out32) return 0;
    
    int64_t cachedMtime;
    if (simjot_sync_get_cached_hash(path, out32, &cachedMtime)) {
        return 1;
    }
    
    if (!simjot_sync_compute_file_hash(path, out32)) {
        return 0;
    }
    
    int64_t mtime = simjot_sync_get_mtime(path);
    simjot_sync_cache_hash(path, out32, mtime);
    return 1;
#else
    (void)path;
    (void)out32;
    return 0;
#endif
}

extern "C" int32_t simjot_sync_files_identical(const char* path1, const char* path2) {
#ifdef __APPLE__
    if (!path1 || !path2) return 0;
    
    uint8_t hash1[32], hash2[32];
    if (!simjot_sync_compute_hash(path1, hash1)) return -1;
    if (!simjot_sync_compute_hash(path2, hash2)) return -1;
    
    return simjot_sync_hashes_equal(hash1, hash2) ? 1 : 0;
#else
    (void)path1;
    (void)path2;
    return -1;
#endif
}

extern "C" void simjot_sync_invalidate_hash(const char* path) {
#ifdef __APPLE__
    if (!path) return;
    std::string key(path);
    std::lock_guard<std::mutex> lock(g_syncMutex);
    g_hashCache.erase(key);
#else
    (void)path;
#endif
}

extern "C" void simjot_sync_clear_hash_cache(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_syncMutex);
    g_hashCache.clear();
#endif
}
