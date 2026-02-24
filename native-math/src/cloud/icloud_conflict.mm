/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 * 
 * iCloud Conflict Resolution & Version Management
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <CommonCrypto/CommonDigest.h>
#include <mutex>
#include <vector>
#include <string>
#include <unordered_map>
#include <chrono>
#include <atomic>

#pragma mark - Conflict Types

struct ConflictVersion {
    std::string path;
    std::string versionId;
    int64_t modTime;
    int64_t size;
    uint8_t hash[32];
    int32_t isLocal;
    int32_t isCloud;
};

struct ConflictInfo {
    std::string originalPath;
    std::vector<ConflictVersion> versions;
    int64_t detectedTime;
    int32_t resolved;
    int32_t resolutionStrategy; // 0=none, 1=keepLocal, 2=keepCloud, 3=keepBoth, 4=merge
};

#pragma mark - Global Conflict State

static std::mutex g_conflictMutex;
static std::unordered_map<std::string, ConflictInfo> g_conflicts;
static std::atomic<int32_t> g_conflictCount{0};

static int64_t conflict_time_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

#endif // __APPLE__

extern "C" int32_t simjot_conflict_scan(const char* root_path, int32_t max_conflicts) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!root_path || !*root_path) return -1;
        
        NSString* rootStr = [NSString stringWithUTF8String:root_path];
        if (!rootStr) return -1;
        
        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootStr isDirectory:&isDir] || !isDir) return -1;
        
        NSURL* rootUrl = [NSURL fileURLWithPath:rootStr];
        
        NSMetadataQuery* query = [[NSMetadataQuery alloc] init];
        [query setSearchScopes:@[rootUrl]];
        [query setPredicate:[NSPredicate predicateWithFormat:@"%K == TRUE",
                            NSMetadataUbiquitousItemHasUnresolvedConflictsKey]];
        
        __block BOOL done = NO;
        NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
        id observer = [nc addObserverForName:NSMetadataQueryDidFinishGatheringNotification
                                      object:query
                                       queue:nil
                                  usingBlock:^(NSNotification* note) {
            (void)note;
            done = YES;
        }];
        
        if (![query startQuery]) {
            [nc removeObserver:observer];
            return -1;
        }
        
        NSDate* deadline = [NSDate dateWithTimeIntervalSinceNow:2.0];
        while (!done && [deadline timeIntervalSinceNow] > 0) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }
        
        [query disableUpdates];
        NSArray* results = [query results];
        
        int32_t count = 0;
        std::lock_guard<std::mutex> lock(g_conflictMutex);
        
        for (NSMetadataItem* item in results) {
            if (max_conflicts > 0 && count >= max_conflicts) break;
            
            NSURL* url = [item valueForAttribute:NSMetadataItemURLKey];
            if (![url isKindOfClass:[NSURL class]]) continue;
            
            NSString* path = [url path];
            if (!path) continue;
            
            std::string pathStr([path UTF8String]);
            
            if (g_conflicts.find(pathStr) == g_conflicts.end()) {
                ConflictInfo info;
                info.originalPath = pathStr;
                info.detectedTime = conflict_time_ms();
                info.resolved = 0;
                info.resolutionStrategy = 0;
                
                // Get conflict versions
                NSArray* conflictVersions = [NSFileVersion unresolvedConflictVersionsOfItemAtURL:url];
                if (conflictVersions) {
                    for (NSFileVersion* version in conflictVersions) {
                        ConflictVersion cv;
                        cv.path = [[version URL] path] ? [[[version URL] path] UTF8String] : pathStr;
                        cv.versionId = version.localizedNameOfSavingComputer ? 
                            [version.localizedNameOfSavingComputer UTF8String] : "unknown";
                        cv.modTime = version.modificationDate ? 
                            (int64_t)([version.modificationDate timeIntervalSince1970] * 1000) : 0;
                        cv.size = 0;
                        memset(cv.hash, 0, 32);
                        cv.isLocal = version.isConflict ? 0 : 1;
                        cv.isCloud = version.isConflict ? 1 : 0;
                        
                        info.versions.push_back(cv);
                    }
                }
                
                // Add current version
                ConflictVersion currentVer;
                currentVer.path = pathStr;
                currentVer.versionId = "current";
                NSDictionary* attrs = [fm attributesOfItemAtPath:path error:nil];
                if (attrs) {
                    NSDate* modDate = attrs[NSFileModificationDate];
                    currentVer.modTime = modDate ? 
                        (int64_t)([modDate timeIntervalSince1970] * 1000) : 0;
                    currentVer.size = [attrs[NSFileSize] longLongValue];
                }
                currentVer.isLocal = 1;
                currentVer.isCloud = 0;
                memset(currentVer.hash, 0, 32);
                info.versions.insert(info.versions.begin(), currentVer);
                
                g_conflicts[pathStr] = info;
                count++;
            }
        }
        
        [query stopQuery];
        [nc removeObserver:observer];
        
        g_conflictCount.store((int32_t)g_conflicts.size());
        return count;
    }
#else
    (void)root_path;
    (void)max_conflicts;
    return -1;
#endif
}

extern "C" int32_t simjot_conflict_count(void) {
#ifdef __APPLE__
    return g_conflictCount.load();
#else
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_get_path(int32_t index, char* path_out, int32_t path_len) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_conflictMutex);
    if (index < 0 || (size_t)index >= g_conflicts.size()) return 0;
    
    auto it = g_conflicts.begin();
    std::advance(it, index);
    
    if (path_out && path_len > 0) {
        size_t copyLen = std::min((size_t)path_len - 1, it->first.length());
        memcpy(path_out, it->first.c_str(), copyLen);
        path_out[copyLen] = '\0';
        return (int32_t)copyLen;
    }
    return 0;
#else
    (void)index; (void)path_out; (void)path_len;
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_version_count(const char* path) {
#ifdef __APPLE__
    if (!path) return 0;
    std::string pathStr(path);
    std::lock_guard<std::mutex> lock(g_conflictMutex);
    
    auto it = g_conflicts.find(pathStr);
    if (it == g_conflicts.end()) return 0;
    
    return (int32_t)it->second.versions.size();
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_get_version(const char* path, int32_t version_index,
                                                char* version_path, int32_t path_len,
                                                int64_t* mod_time, int64_t* size) {
#ifdef __APPLE__
    if (!path) return 0;
    std::string pathStr(path);
    std::lock_guard<std::mutex> lock(g_conflictMutex);
    
    auto it = g_conflicts.find(pathStr);
    if (it == g_conflicts.end()) return 0;
    if (version_index < 0 || (size_t)version_index >= it->second.versions.size()) return 0;
    
    const auto& ver = it->second.versions[version_index];
    
    if (version_path && path_len > 0) {
        size_t copyLen = std::min((size_t)path_len - 1, ver.path.length());
        memcpy(version_path, ver.path.c_str(), copyLen);
        version_path[copyLen] = '\0';
    }
    
    if (mod_time) *mod_time = ver.modTime;
    if (size) *size = ver.size;
    
    return 1;
#else
    (void)path; (void)version_index; (void)version_path;
    (void)path_len; (void)mod_time; (void)size;
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_resolve_keep_local(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path) return 0;
        
        NSString* nsPath = [NSString stringWithUTF8String:path];
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        
        NSArray* conflictVersions = [NSFileVersion unresolvedConflictVersionsOfItemAtURL:url];
        if (!conflictVersions || conflictVersions.count == 0) return 1;
        
        BOOL success = YES;
        for (NSFileVersion* version in conflictVersions) {
            version.resolved = YES;
            NSError* error = nil;
            if (![version removeAndReturnError:&error]) {
                success = NO;
            }
        }
        
        if (success) {
            std::string pathStr(path);
            std::lock_guard<std::mutex> lock(g_conflictMutex);
            auto it = g_conflicts.find(pathStr);
            if (it != g_conflicts.end()) {
                it->second.resolved = 1;
                it->second.resolutionStrategy = 1;
            }
        }
        
        return success ? 1 : 0;
    }
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_resolve_keep_cloud(const char* path, int32_t version_index) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path) return 0;
        
        NSString* nsPath = [NSString stringWithUTF8String:path];
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        
        NSArray* conflictVersions = [NSFileVersion unresolvedConflictVersionsOfItemAtURL:url];
        if (!conflictVersions || conflictVersions.count == 0) return 0;
        if (version_index < 0 || (NSUInteger)version_index >= conflictVersions.count) return 0;
        
        NSFileVersion* chosenVersion = conflictVersions[version_index];
        NSError* error = nil;
        
        // Replace current with chosen version
        if (![chosenVersion replaceItemAtURL:url options:0 error:&error]) {
            return 0;
        }
        
        // Mark all as resolved and remove
        for (NSFileVersion* version in conflictVersions) {
            version.resolved = YES;
            [version removeAndReturnError:nil];
        }
        
        std::string pathStr(path);
        std::lock_guard<std::mutex> lock(g_conflictMutex);
        auto it = g_conflicts.find(pathStr);
        if (it != g_conflicts.end()) {
            it->second.resolved = 1;
            it->second.resolutionStrategy = 2;
        }
        
        return 1;
    }
#else
    (void)path; (void)version_index;
    return 0;
#endif
}

extern "C" int32_t simjot_conflict_resolve_keep_both(const char* path, 
                                                      const char* renamed_suffix) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path) return 0;
        
        NSString* nsPath = [NSString stringWithUTF8String:path];
        NSString* suffix = renamed_suffix ? 
            [NSString stringWithUTF8String:renamed_suffix] : @"_conflict";
        
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        NSFileManager* fm = [NSFileManager defaultManager];
        
        NSArray* conflictVersions = [NSFileVersion unresolvedConflictVersionsOfItemAtURL:url];
        if (!conflictVersions || conflictVersions.count == 0) return 1;
        
        int counter = 1;
        for (NSFileVersion* version in conflictVersions) {
            NSString* ext = [nsPath pathExtension];
            NSString* base = [nsPath stringByDeletingPathExtension];
            NSString* newPath = [NSString stringWithFormat:@"%@%@_%d.%@", 
                                 base, suffix, counter++, ext];
            
            NSURL* newUrl = [NSURL fileURLWithPath:newPath];
            NSError* error = nil;
            
            // Copy conflict version to new location
            NSURL* versionUrl = version.URL;
            if (versionUrl && ![fm fileExistsAtPath:newPath]) {
                [fm copyItemAtURL:versionUrl toURL:newUrl error:&error];
            }
            
            version.resolved = YES;
            [version removeAndReturnError:nil];
        }
        
        std::string pathStr(path);
        std::lock_guard<std::mutex> lock(g_conflictMutex);
        auto it = g_conflicts.find(pathStr);
        if (it != g_conflicts.end()) {
            it->second.resolved = 1;
            it->second.resolutionStrategy = 3;
        }
        
        return 1;
    }
#else
    (void)path; (void)renamed_suffix;
    return 0;
#endif
}

extern "C" void simjot_conflict_clear_resolved(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_conflictMutex);
    
    for (auto it = g_conflicts.begin(); it != g_conflicts.end(); ) {
        if (it->second.resolved) {
            it = g_conflicts.erase(it);
        } else {
            ++it;
        }
    }
    
    g_conflictCount.store((int32_t)g_conflicts.size());
#endif
}

extern "C" void simjot_conflict_clear_all(void) {
#ifdef __APPLE__
    std::lock_guard<std::mutex> lock(g_conflictMutex);
    g_conflicts.clear();
    g_conflictCount.store(0);
#endif
}
