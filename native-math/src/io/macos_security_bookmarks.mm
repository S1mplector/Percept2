/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Foundation/Foundation.h>
#import <CoreFoundation/CoreFoundation.h>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <string>
#include <string.h>

static std::mutex g_bookmark_mutex;
static std::unordered_map<int64_t, CFURLRef> g_active_scopes;
static std::atomic<int64_t> g_next_scope_token{1};

static NSData* simjot_bytes_to_nsdata(const uint8_t* bytes, int32_t len) {
    if (!bytes || len <= 0) return nil;
    return [NSData dataWithBytes:bytes length:(NSUInteger)len];
}

static int32_t simjot_copy_nsstring_utf8(NSString* s, char* out, int32_t out_len) {
    if (!s) return 0;
    NSData* data = [s dataUsingEncoding:NSUTF8StringEncoding];
    if (!data) return 0;
    int32_t len = (int32_t)[data length];
    if (!out || out_len <= 0) return len;
    int32_t copy_len = len < (out_len - 1) ? len : (out_len - 1);
    if (copy_len > 0) memcpy(out, [data bytes], (size_t)copy_len);
    out[copy_len] = '\0';
    return len;
}
#endif

extern "C" int32_t simjot_macos_bookmark_create(const char* path, uint8_t* out, int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath || nsPath.length == 0) return -1;

        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return -1;

        NSError* err = nil;
        NSData* bookmark = [url bookmarkDataWithOptions:NSURLBookmarkCreationWithSecurityScope
                          includingResourceValuesForKeys:nil
                                           relativeToURL:nil
                                                   error:&err];
        if (!bookmark || err) return 0;

        int32_t len = (int32_t)[bookmark length];
        if (!out || out_len <= 0) return len;

        int32_t copy_len = len < out_len ? len : out_len;
        if (copy_len > 0) memcpy(out, [bookmark bytes], (size_t)copy_len);
        return len;
    }
#else
    (void)path;
    (void)out;
    (void)out_len;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_bookmark_resolve(const uint8_t* bookmark_data, int32_t data_len,
                                                    char* out_path, int32_t out_path_len,
                                                    int32_t* out_is_stale) {
#ifdef __APPLE__
    @autoreleasepool {
        NSData* data = simjot_bytes_to_nsdata(bookmark_data, data_len);
        if (!data) return -1;

        BOOL stale = NO;
        NSError* err = nil;
        NSURL* url = [NSURL URLByResolvingBookmarkData:data
                                               options:NSURLBookmarkResolutionWithSecurityScope | NSURLBookmarkResolutionWithoutUI
                                         relativeToURL:nil
                                   bookmarkDataIsStale:&stale
                                                 error:&err];
        if (!url || err) return 0;

        if (out_is_stale) *out_is_stale = stale ? 1 : 0;
        NSString* path = [url path];
        return simjot_copy_nsstring_utf8(path, out_path, out_path_len);
    }
#else
    (void)bookmark_data;
    (void)data_len;
    (void)out_path;
    (void)out_path_len;
    (void)out_is_stale;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_bookmark_start_access(const uint8_t* bookmark_data, int32_t data_len,
                                                         int64_t* out_token) {
#ifdef __APPLE__
    @autoreleasepool {
        if (out_token) *out_token = 0;

        NSData* data = simjot_bytes_to_nsdata(bookmark_data, data_len);
        if (!data) return -1;

        BOOL stale = NO;
        NSError* err = nil;
        NSURL* url = [NSURL URLByResolvingBookmarkData:data
                                               options:NSURLBookmarkResolutionWithSecurityScope | NSURLBookmarkResolutionWithoutUI
                                         relativeToURL:nil
                                   bookmarkDataIsStale:&stale
                                                 error:&err];
        if (!url || err) return 0;

        BOOL ok = [url startAccessingSecurityScopedResource];
        if (!ok) return 0;

        CFURLRef retained_url = (CFURLRef)CFRetain((CFTypeRef)url);
        if (!retained_url) {
            [url stopAccessingSecurityScopedResource];
            return 0;
        }

        int64_t token = g_next_scope_token.fetch_add(1);
        {
            std::lock_guard<std::mutex> lock(g_bookmark_mutex);
            g_active_scopes[token] = retained_url;
        }
        if (out_token) *out_token = token;
        return stale ? 2 : 1;
    }
#else
    (void)bookmark_data;
    (void)data_len;
    (void)out_token;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_bookmark_stop_access(int64_t token) {
#ifdef __APPLE__
    @autoreleasepool {
        if (token <= 0) return 0;

        CFURLRef retained_url = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_bookmark_mutex);
            auto it = g_active_scopes.find(token);
            if (it == g_active_scopes.end()) return 0;
            retained_url = it->second;
            g_active_scopes.erase(it);
        }

        if (retained_url) {
            NSURL* url = (NSURL*)retained_url;
            [url stopAccessingSecurityScopedResource];
            CFRelease(retained_url);
            return 1;
        }
        return 0;
    }
#else
    (void)token;
    return -1;
#endif
}
