/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#import <CoreGraphics/CoreGraphics.h>
#import <CoreVideo/CoreVideo.h>
#import <objc/message.h>
#import <IOKit/ps/IOPowerSources.h>
#import <IOKit/ps/IOPSKeys.h>
#import <IOKit/IOKitLib.h>
#include <copyfile.h>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/sysctl.h>
#include <dlfcn.h>
#endif

#ifdef __APPLE__
enum {
    SIMJOT_ICLOUD_EXISTS = 1 << 0,
    SIMJOT_ICLOUD_UBIQUITOUS = 1 << 1,
    SIMJOT_ICLOUD_DOWNLOADED = 1 << 2,
    SIMJOT_ICLOUD_DOWNLOADING = 1 << 3,
    SIMJOT_ICLOUD_UPLOADING = 1 << 4,
    SIMJOT_ICLOUD_CONFLICT = 1 << 5
};

static int simjot_path_is_under(NSString* path, NSString* base) {
    if (!path || !base) return 0;
    NSString* p = [path stringByStandardizingPath];
    NSString* b = [base stringByStandardizingPath];
    if (!p || !b) return 0;
    if ([p isEqualToString:b]) return 1;
    if (![b hasSuffix:@"/"]) {
        b = [b stringByAppendingString:@"/"];
    }
    return [p hasPrefix:b] ? 1 : 0;
}

static int simjot_is_icloud_path_fallback(NSString* path) {
    if (!path) return 0;
    NSFileManager* fm = [NSFileManager defaultManager];
    NSString* base = nil;
    id token = nil;
    if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
        token = [fm ubiquityIdentityToken];
    }
    if (token) {
        SEL sel = NSSelectorFromString(@"URLForUbiquityContainerIdentifier:");
        if ([fm respondsToSelector:sel]) {
            NSURL* ubiq = ((NSURL* (*)(id, SEL, NSString*))objc_msgSend)(fm, sel, nil);
            if (ubiq) {
                NSURL* docs = [ubiq URLByAppendingPathComponent:@"Documents"];
                base = [docs path];
            }
        }
    }
    if (!base) {
        base = [NSHomeDirectory() stringByAppendingPathComponent:@"Library/Mobile Documents/com~apple~CloudDocs"];
    }
    return simjot_path_is_under(path, base);
}

static int simjot_append_utf8_line(char* out, int32_t out_len, int32_t* offset, NSString* line) {
    if (!out || !offset || !line || out_len <= 0) return 0;
    NSData* data = [line dataUsingEncoding:NSUTF8StringEncoding];
    if (!data) return 0;
    int32_t len = (int32_t)[data length];
    if (len <= 0) return 0;
    if (*offset + len + 1 >= out_len) return 0;
    if (*offset > 0) {
        out[(*offset)++] = '\n';
    }
    memcpy(out + *offset, [data bytes], (size_t)len);
    *offset += len;
    out[*offset] = '\0';
    return 1;
}
#endif

extern "C" float simjot_macos_get_primary_refresh_rate(void) {
#ifdef __APPLE__
    double rate = 0.0;
    @autoreleasepool {
        NSScreen* screen = [NSScreen mainScreen];
        if (screen) {
            SEL sel = NSSelectorFromString(@"maximumFramesPerSecond");
            if ([screen respondsToSelector:sel]) {
                NSInteger fps = ((NSInteger (*)(id, SEL))objc_msgSend)(screen, sel);
                if (fps > 0) rate = (double)fps;
            }
        }
    }

    if (rate <= 1.0) {
        CGDirectDisplayID display = CGMainDisplayID();
        CGDisplayModeRef mode = CGDisplayCopyDisplayMode(display);
        if (mode) {
            double fps = CGDisplayModeGetRefreshRate(mode);
            CGDisplayModeRelease(mode);
            if (fps > rate) rate = fps;
        }
    }

    if (rate <= 1.0) {
        CVDisplayLinkRef link = NULL;
        if (CVDisplayLinkCreateWithCGDisplay(CGMainDisplayID(), &link) == kCVReturnSuccess && link) {
            CVTime period = CVDisplayLinkGetNominalOutputVideoRefreshPeriod(link);
            if (period.timeValue > 0 && period.timeScale > 0) {
                rate = (double)period.timeScale / (double)period.timeValue;
            }
            CVDisplayLinkRelease(link);
        }
    }

    if (rate <= 1.0) rate = 60.0;
    return (float)rate;
#else
    return 0.0f;
#endif
}

extern "C" int32_t simjot_macos_is_low_power_mode(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        SEL sel = NSSelectorFromString(@"isLowPowerModeEnabled");
        if ([info respondsToSelector:sel]) {
            BOOL enabled = ((BOOL (*)(id, SEL))objc_msgSend)(info, sel);
            return enabled ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_reduce_motion_enabled(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSWorkspace* ws = [NSWorkspace sharedWorkspace];
        SEL sel = NSSelectorFromString(@"accessibilityDisplayShouldReduceMotion");
        if ([ws respondsToSelector:sel]) {
            BOOL enabled = ((BOOL (*)(id, SEL))objc_msgSend)(ws, sel);
            return enabled ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_get_thermal_state(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        SEL sel = NSSelectorFromString(@"thermalState");
        if ([info respondsToSelector:sel]) {
            NSInteger state = ((NSInteger (*)(id, SEL))objc_msgSend)(info, sel);
            return (int32_t)state;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_get_accent_color(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSColor* rgb = nil;
        SEL accentSel = NSSelectorFromString(@"controlAccentColor");
        if ([NSColor respondsToSelector:accentSel]) {
            rgb = ((NSColor* (*)(id, SEL))objc_msgSend)([NSColor class], accentSel);
        }
        if (rgb) {
            rgb = [rgb colorUsingColorSpace:[NSColorSpace sRGBColorSpace]];
        }
        if (!rgb) {
            SEL fallbackSel = NSSelectorFromString(@"selectedContentBackgroundColor");
            NSColor* fallback = nil;
            if ([NSColor respondsToSelector:fallbackSel]) {
                fallback = ((NSColor* (*)(id, SEL))objc_msgSend)([NSColor class], fallbackSel);
            } else {
                fallback = [NSColor selectedControlColor];
            }
            if (fallback) {
                rgb = [fallback colorUsingColorSpace:[NSColorSpace sRGBColorSpace]];
            }
        }
        if (!rgb) return 0;

        CGFloat r = 0, g = 0, b = 0, a = 1;
        [rgb getRed:&r green:&g blue:&b alpha:&a];

        int ri = (int)(r * 255.0 + 0.5);
        int gi = (int)(g * 255.0 + 0.5);
        int bi = (int)(b * 255.0 + 0.5);
        int ai = (int)(a * 255.0 + 0.5);

        if (ri < 0) ri = 0; else if (ri > 255) ri = 255;
        if (gi < 0) gi = 0; else if (gi > 255) gi = 255;
        if (bi < 0) bi = 0; else if (bi > 255) bi = 255;
        if (ai < 0) ai = 0; else if (ai > 255) ai = 255;

        return (int32_t)((ai << 24) | (ri << 16) | (gi << 8) | bi);
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_is_on_battery(void) {
#ifdef __APPLE__
    @autoreleasepool {
        CFTypeRef info = IOPSCopyPowerSourcesInfo();
        if (!info) return 0;
        CFArrayRef sources = IOPSCopyPowerSourcesList(info);
        if (!sources) {
            CFRelease(info);
            return 0;
        }
        int32_t on_battery = 0;
        CFIndex count = CFArrayGetCount(sources);
        for (CFIndex i = 0; i < count; i++) {
            CFTypeRef ps = CFArrayGetValueAtIndex(sources, i);
            CFDictionaryRef desc = IOPSGetPowerSourceDescription(info, ps);
            if (!desc) continue;
            CFStringRef state = (CFStringRef)CFDictionaryGetValue(desc, CFSTR(kIOPSPowerSourceStateKey));
            if (state && CFStringCompare(state, CFSTR(kIOPSBatteryPowerValue), 0) == kCFCompareEqualTo) {
                on_battery = 1;
                break;
            }
        }
        CFRelease(sources);
        CFRelease(info);
        return on_battery;
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_get_icloud_path(char* out, int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        id token = [fm ubiquityIdentityToken];
        if (!token) return 0; // iCloud not signed in

        NSString* basePath = nil;
        NSURL* ubiq = [fm URLForUbiquityContainerIdentifier:nil];
        if (ubiq) {
            NSURL* docs = [ubiq URLByAppendingPathComponent:@"Documents"];
            basePath = [docs path];
        }
        if (!basePath) {
            basePath = [NSHomeDirectory() stringByAppendingPathComponent:@"Library/Mobile Documents/com~apple~CloudDocs"];
        }
        if (!basePath) return 0;

        NSString* simjotPath = [basePath stringByAppendingPathComponent:@"Simjot"];
        NSData* data = [simjotPath dataUsingEncoding:NSUTF8StringEncoding];
        if (!data) return 0;

        int32_t len = (int32_t)[data length];
        if (out && out_len > 0) {
            int32_t copy_len = (len < out_len - 1) ? len : out_len - 1;
            memcpy(out, [data bytes], (size_t)copy_len);
            out[copy_len] = '\0';
        }
        return len;
    }
#else
    (void)out;
    (void)out_len;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_is_icloud_path(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return 0;
        NSNumber* isUbiq = nil;
        NSError* error = nil;
        if (![url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:&error]) {
            return simjot_is_icloud_path_fallback(nsPath);
        }
        if (isUbiq && [isUbiq boolValue]) return 1;
        return simjot_is_icloud_path_fallback(nsPath);
    }
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_is_available(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        SEL sel = NSSelectorFromString(@"ubiquityIdentityToken");
        if ([fm respondsToSelector:sel]) {
            id token = ((id (*)(id, SEL))objc_msgSend)(fm, sel);
            return token ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_icloud_item_status(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:nsPath isDirectory:&isDir]) return 0;

        int32_t flags = SIMJOT_ICLOUD_EXISTS;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return flags;

        NSNumber* isUbiq = nil;
        NSError* error = nil;
        if ([url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:&error]) {
            if (isUbiq && [isUbiq boolValue]) flags |= SIMJOT_ICLOUD_UBIQUITOUS;
        } else if (simjot_is_icloud_path_fallback(nsPath)) {
            flags |= SIMJOT_ICLOUD_UBIQUITOUS;
        }

        if (flags & SIMJOT_ICLOUD_UBIQUITOUS) {
            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) flags |= SIMJOT_ICLOUD_DOWNLOADED;
            }
            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) flags |= SIMJOT_ICLOUD_DOWNLOADING;
            }
            NSNumber* uploading = nil;
            if ([url getResourceValue:&uploading forKey:NSURLUbiquitousItemIsUploadingKey error:nil]) {
                if (uploading && [uploading boolValue]) flags |= SIMJOT_ICLOUD_UPLOADING;
            }
            NSNumber* conflicts = nil;
            if ([url getResourceValue:&conflicts forKey:NSURLUbiquitousItemHasUnresolvedConflictsKey error:nil]) {
                if (conflicts && [conflicts boolValue]) flags |= SIMJOT_ICLOUD_CONFLICT;
            }
            NSString* status = nil;
            if ([url getResourceValue:&status forKey:NSURLUbiquitousItemDownloadingStatusKey error:nil]) {
                if ([status isEqualToString:NSURLUbiquitousItemDownloadingStatusCurrent] ||
                    [status isEqualToString:NSURLUbiquitousItemDownloadingStatusDownloaded]) {
                    flags |= SIMJOT_ICLOUD_DOWNLOADED;
                }
            }
        }

        return flags;
    }
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_start_download(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSFileManager* fm = [NSFileManager defaultManager];
        if (![fm fileExistsAtPath:nsPath]) return 0;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return 0;

        NSNumber* isUbiq = nil;
        if ([url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:nil]) {
            if (!isUbiq || ![isUbiq boolValue]) return 0;
        } else if (!simjot_is_icloud_path_fallback(nsPath)) {
            return 0;
        }

        NSNumber* downloaded = nil;
        if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
            if (downloaded && [downloaded boolValue]) return 1;
        }

        SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
        if ([fm respondsToSelector:sel]) {
            NSError* error = nil;
            BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
            return ok ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_icloud_prefetch_dir(const char* path, int32_t max_items, int32_t max_depth) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* rootPath = [NSString stringWithUTF8String:path];
        if (!rootPath) return 0;

        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootPath isDirectory:&isDir] || !isDir) return 0;

        NSURL* rootUrl = [NSURL fileURLWithPath:rootPath];
        if (!rootUrl) return 0;

        NSArray* keys = @[
            NSURLIsUbiquitousItemKey,
            NSURLUbiquitousItemIsDownloadedKey,
            NSURLUbiquitousItemIsDownloadingKey,
            NSURLIsDirectoryKey
        ];

        NSDirectoryEnumerator* enumerator = [fm enumeratorAtURL:rootUrl
                                     includingPropertiesForKeys:keys
                                                        options:(NSDirectoryEnumerationSkipsHiddenFiles |
                                                                 NSDirectoryEnumerationSkipsPackageDescendants)
                                                   errorHandler:^BOOL(NSURL* url, NSError* error) {
            (void)url;
            (void)error;
            return YES;
        }];
        if (!enumerator) return 0;

        NSUInteger rootDepth = [[rootPath pathComponents] count];
        int32_t requested = 0;
        for (NSURL* url in enumerator) {
            if (!url) continue;
            NSString* itemPath = [url path];
            if (itemPath && max_depth > 0) {
                NSUInteger depth = [[itemPath pathComponents] count];
                if (depth > rootDepth + (NSUInteger)max_depth) {
                    [enumerator skipDescendants];
                    continue;
                }
            }

            NSNumber* isUbiq = nil;
            if (![url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:nil]) {
                continue;
            }
            if (!isUbiq || ![isUbiq boolValue]) continue;

            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) {
                    continue;
                }
            }

            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) {
                    continue;
                }
            }

            SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
            if ([fm respondsToSelector:sel]) {
                NSError* error = nil;
                BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
                if (ok) requested++;
            }

            if (max_items > 0 && requested >= max_items) break;
        }

        return requested;
    }
#else
    (void)path;
    (void)max_items;
    (void)max_depth;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_prefetch_query(const char* path, int32_t max_items, int32_t timeout_ms) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        NSString* rootPath = [NSString stringWithUTF8String:path];
        if (!rootPath) return -1;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return -1;

        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootPath isDirectory:&isDir] || !isDir) return -1;

        if (!simjot_is_icloud_path_fallback(rootPath)) return -1;

        NSURL* rootUrl = [NSURL fileURLWithPath:rootPath];
        if (!rootUrl) return -1;

        NSMetadataQuery* query = [[NSMetadataQuery alloc] init];
        if (!query) return -1;

        [query setSearchScopes:@[rootUrl]];
        NSPredicate* predicate = [NSPredicate predicateWithFormat:@"%K == FALSE",
                                  NSMetadataUbiquitousItemIsDownloadedKey];
        [query setPredicate:predicate];

        __block BOOL done = NO;
        NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
        id finishObserver = [nc addObserverForName:NSMetadataQueryDidFinishGatheringNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
            done = YES;
        }];
        id updateObserver = [nc addObserverForName:NSMetadataQueryDidUpdateNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
        }];

        if (![query startQuery]) {
            [nc removeObserver:finishObserver];
            if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
            [query release];
#endif
            return -1;
        }

        int32_t timeout = timeout_ms > 0 ? timeout_ms : 1200;
        NSDate* end = [NSDate dateWithTimeIntervalSinceNow:timeout / 1000.0];
        while (!done && [end timeIntervalSinceNow] > 0) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }

        [query disableUpdates];
        NSArray* results = [query results];
        int32_t requested = 0;
        for (NSMetadataItem* item in results) {
            if (!item) continue;
            if (max_items > 0 && requested >= max_items) break;

            NSURL* url = [item valueForAttribute:NSMetadataItemURLKey];
            if (![url isKindOfClass:[NSURL class]]) continue;

            NSNumber* isDirNum = nil;
            if ([url getResourceValue:&isDirNum forKey:NSURLIsDirectoryKey error:nil]) {
                if (isDirNum && [isDirNum boolValue]) {
                    continue;
                }
            }

            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) continue;
            }

            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) continue;
            }

            SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
            if ([fm respondsToSelector:sel]) {
                NSError* error = nil;
                BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
                if (ok) requested++;
            }
        }

        [query stopQuery];
        [query enableUpdates];
        [nc removeObserver:finishObserver];
        if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
        [query release];
#endif
        return requested;
    }
#else
    (void)path;
    (void)max_items;
    (void)timeout_ms;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_icloud_list_conflicts(const char* path, int32_t max_items, int32_t timeout_ms, char* out, int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path || !out || out_len <= 0) return -1;
        out[0] = '\0';

        NSString* rootPath = [NSString stringWithUTF8String:path];
        if (!rootPath) return -1;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return -1;

        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootPath isDirectory:&isDir] || !isDir) return -1;
        if (!simjot_is_icloud_path_fallback(rootPath)) return -1;

        NSURL* rootUrl = [NSURL fileURLWithPath:rootPath];
        if (!rootUrl) return -1;

        NSMetadataQuery* query = [[NSMetadataQuery alloc] init];
        if (!query) return -1;

        [query setSearchScopes:@[rootUrl]];
        NSPredicate* predicate = [NSPredicate predicateWithFormat:@"%K == TRUE",
                                  NSMetadataUbiquitousItemHasUnresolvedConflictsKey];
        [query setPredicate:predicate];

        __block BOOL done = NO;
        NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
        id finishObserver = [nc addObserverForName:NSMetadataQueryDidFinishGatheringNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
            done = YES;
        }];
        id updateObserver = [nc addObserverForName:NSMetadataQueryDidUpdateNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
        }];

        if (![query startQuery]) {
            [nc removeObserver:finishObserver];
            if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
            [query release];
#endif
            return -1;
        }

        int32_t timeout = timeout_ms > 0 ? timeout_ms : 1200;
        NSDate* end = [NSDate dateWithTimeIntervalSinceNow:timeout / 1000.0];
        while (!done && [end timeIntervalSinceNow] > 0) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }

        [query disableUpdates];
        NSArray* results = [query results];
        int32_t count = 0;
        int32_t offset = 0;
        for (NSMetadataItem* item in results) {
            if (!item) continue;
            if (max_items > 0 && count >= max_items) break;
            NSURL* url = [item valueForAttribute:NSMetadataItemURLKey];
            if (![url isKindOfClass:[NSURL class]]) continue;
            NSString* itemPath = [url path];
            if (!itemPath || itemPath.length == 0) continue;
            count++;
            if (!simjot_append_utf8_line(out, out_len, &offset, itemPath)) {
                break;
            }
        }

        [query stopQuery];
        [query enableUpdates];
        [nc removeObserver:finishObserver];
        if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
        [query release];
#endif
        return count;
    }
#else
    (void)path;
    (void)max_items;
    (void)timeout_ms;
    (void)out;
    (void)out_len;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_icloud_ensure_downloaded(const char* path, int32_t timeout_ms) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return -1;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return -1;

        BOOL isDir = NO;
        if (![fm fileExistsAtPath:nsPath isDirectory:&isDir]) return 0;

        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return -1;

        NSNumber* isUbiq = nil;
        if (![url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:nil]) {
            if (!simjot_is_icloud_path_fallback(nsPath)) return 1;
        } else if (!isUbiq || ![isUbiq boolValue]) {
            return 1;
        }

        NSNumber* downloaded = nil;
        if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
            if (downloaded && [downloaded boolValue]) return 1;
        }

        SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
        if ([fm respondsToSelector:sel]) {
            NSError* error = nil;
            ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
        }

        int32_t timeout = timeout_ms > 0 ? timeout_ms : 2000;
        NSDate* end = [NSDate dateWithTimeIntervalSinceNow:timeout / 1000.0];
        while ([end timeIntervalSinceNow] > 0) {
            NSNumber* nowDownloaded = nil;
            if ([url getResourceValue:&nowDownloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (nowDownloaded && [nowDownloaded boolValue]) return 1;
            }
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }
        return 0;
    }
#else
    (void)path;
    (void)timeout_ms;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_icloud_coordinated_write(const char* path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path || !data || data_len < 0) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return 0;

        if (!simjot_is_icloud_path_fallback(nsPath)) return 0;

        NSString* dirPath = [nsPath stringByDeletingLastPathComponent];
        if (dirPath && ![fm fileExistsAtPath:dirPath]) {
            [fm createDirectoryAtPath:dirPath withIntermediateDirectories:YES attributes:nil error:nil];
        }

        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return 0;

        __block BOOL ok = NO;
        __block NSError* coordError = nil;
        NSFileCoordinator* coordinator = [[NSFileCoordinator alloc] initWithFilePresenter:nil];
        [coordinator coordinateWritingItemAtURL:url
                                        options:NSFileCoordinatorWritingForReplacing
                                          error:&coordError
                                     byAccessor:^(NSURL* newURL) {
            NSData* payload = [NSData dataWithBytes:data length:(NSUInteger)data_len];
            NSError* writeError = nil;
            ok = [payload writeToURL:newURL options:NSDataWritingAtomic error:&writeError];
            if (!ok && writeError) {
                coordError = writeError;
            }
        }];

#if !__has_feature(objc_arc)
        [coordinator release];
#endif

        if (!ok) return 0;

        if (fsync_file) {
            int fd = open(path, O_RDONLY);
            if (fd >= 0) {
                fsync(fd);
                close(fd);
            }
        }
        if (fsync_dir && dirPath) {
            const char* dirC = [dirPath fileSystemRepresentation];
            if (dirC && dirC[0]) {
                int fd = open(dirC, O_RDONLY);
                if (fd >= 0) {
                    fsync(fd);
                    close(fd);
                }
            }
        }
        return 1;
    }
#else
    (void)path;
    (void)data;
    (void)data_len;
    (void)fsync_file;
    (void)fsync_dir;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_coordinated_copy(const char* src_path, const char* dst_path, int32_t copy_attributes) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!src_path || !*src_path || !dst_path || !*dst_path) return 0;
        NSString* srcPath = [NSString stringWithUTF8String:src_path];
        NSString* dstPath = [NSString stringWithUTF8String:dst_path];
        if (!srcPath || !dstPath) return 0;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return 0;

        if (!simjot_is_icloud_path_fallback(srcPath) && !simjot_is_icloud_path_fallback(dstPath)) {
            return 0;
        }

        NSString* dstDir = [dstPath stringByDeletingLastPathComponent];
        if (dstDir && ![fm fileExistsAtPath:dstDir]) {
            [fm createDirectoryAtPath:dstDir withIntermediateDirectories:YES attributes:nil error:nil];
        }

        NSURL* srcUrl = [NSURL fileURLWithPath:srcPath];
        NSURL* dstUrl = [NSURL fileURLWithPath:dstPath];
        if (!srcUrl || !dstUrl) return 0;

        __block BOOL ok = NO;
        __block NSError* coordError = nil;
        NSFileCoordinator* coordinator = [[NSFileCoordinator alloc] initWithFilePresenter:nil];
        [coordinator coordinateReadingItemAtURL:srcUrl
                                        options:0
                               writingItemAtURL:dstUrl
                                        options:NSFileCoordinatorWritingForReplacing
                                          error:&coordError
                                     byAccessor:^(NSURL* newSrc, NSURL* newDst) {
            const char* srcFs = [newSrc fileSystemRepresentation];
            const char* dstFs = [newDst fileSystemRepresentation];
            if (!srcFs || !dstFs) return;
            int flags = copy_attributes ? COPYFILE_ALL : COPYFILE_DATA;
            flags |= COPYFILE_UNLINK;
            ok = (copyfile(srcFs, dstFs, NULL, (copyfile_flags_t)flags) == 0);
        }];

#if !__has_feature(objc_arc)
        [coordinator release];
#endif
        (void)coordError;
        return ok ? 1 : 0;
    }
#else
    (void)src_path;
    (void)dst_path;
    (void)copy_attributes;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_coordinated_move(const char* src_path, const char* dst_path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!src_path || !*src_path || !dst_path || !*dst_path) return 0;
        NSString* srcPath = [NSString stringWithUTF8String:src_path];
        NSString* dstPath = [NSString stringWithUTF8String:dst_path];
        if (!srcPath || !dstPath) return 0;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return 0;

        if (!simjot_is_icloud_path_fallback(srcPath) && !simjot_is_icloud_path_fallback(dstPath)) {
            return 0;
        }

        NSString* dstDir = [dstPath stringByDeletingLastPathComponent];
        if (dstDir && ![fm fileExistsAtPath:dstDir]) {
            [fm createDirectoryAtPath:dstDir withIntermediateDirectories:YES attributes:nil error:nil];
        }

        NSURL* srcUrl = [NSURL fileURLWithPath:srcPath];
        NSURL* dstUrl = [NSURL fileURLWithPath:dstPath];
        if (!srcUrl || !dstUrl) return 0;

        __block BOOL ok = NO;
        __block NSError* coordError = nil;
        NSFileCoordinator* coordinator = [[NSFileCoordinator alloc] initWithFilePresenter:nil];
        [coordinator coordinateWritingItemAtURL:srcUrl
                                        options:NSFileCoordinatorWritingForMoving
                               writingItemAtURL:dstUrl
                                        options:NSFileCoordinatorWritingForReplacing
                                          error:&coordError
                                     byAccessor:^(NSURL* newSrc, NSURL* newDst) {
            NSError* moveError = nil;
            [fm removeItemAtURL:newDst error:nil];
            ok = [fm moveItemAtURL:newSrc toURL:newDst error:&moveError];
            if (!ok && moveError) {
                coordError = moveError;
            }
        }];

#if !__has_feature(objc_arc)
        [coordinator release];
#endif
        (void)coordError;
        return ok ? 1 : 0;
    }
#else
    (void)src_path;
    (void)dst_path;
    return 0;
#endif
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HARDWARE DETECTION - CPU/GPU/Memory profiling for performance optimization
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" int32_t simjot_hw_get_architecture(void) {
#ifdef __APPLE__
    #if defined(__aarch64__) || defined(__arm64__)
        return 1; // Apple Silicon (ARM64)
    #elif defined(__x86_64__)
        return 2; // Intel 64-bit
    #elif defined(__i386__)
        return 3; // Intel 32-bit
    #else
        return 0; // Unknown
    #endif
#else
    #if defined(__aarch64__) || defined(__arm64__)
        return 1;
    #elif defined(__x86_64__) || defined(_M_X64)
        return 2;
    #elif defined(__i386__) || defined(_M_IX86)
        return 3;
    #else
        return 0;
    #endif
#endif
}

extern "C" int64_t simjot_hw_get_total_memory_mb(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        uint64_t physMem = [info physicalMemory];
        return (int64_t)(physMem / (1024 * 1024));
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_get_cpu_brand(char* out, int32_t out_len) {
#ifdef __APPLE__
    if (!out || out_len <= 0) return 0;
    
    char brand[256] = {0};
    size_t size = sizeof(brand);
    
    // Try machdep.cpu.brand_string first (Intel)
    if (sysctlbyname("machdep.cpu.brand_string", brand, &size, NULL, 0) == 0 && brand[0]) {
        int32_t len = (int32_t)strlen(brand);
        int32_t copy_len = (len < out_len - 1) ? len : out_len - 1;
        memcpy(out, brand, copy_len);
        out[copy_len] = '\0';
        return copy_len;
    }
    
    // For Apple Silicon, construct from chip info
    #if defined(__aarch64__) || defined(__arm64__)
    {
        char chip[64] = {0};
        size = sizeof(chip);
        if (sysctlbyname("machdep.cpu.brand", chip, &size, NULL, 0) == 0 && chip[0]) {
            int32_t len = (int32_t)strlen(chip);
            int32_t copy_len = (len < out_len - 1) ? len : out_len - 1;
            memcpy(out, chip, copy_len);
            out[copy_len] = '\0';
            return copy_len;
        }
        
        // Fallback: check for Apple Silicon
        const char* as_name = "Apple Silicon";
        int32_t len = (int32_t)strlen(as_name);
        int32_t copy_len = (len < out_len - 1) ? len : out_len - 1;
        memcpy(out, as_name, copy_len);
        out[copy_len] = '\0';
        return copy_len;
    }
    #endif
    
    return 0;
#else
    (void)out;
    (void)out_len;
    return 0;
#endif
}

extern "C" int32_t simjot_hw_get_cpu_core_count(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        return (int32_t)[info processorCount];
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_get_active_core_count(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        return (int32_t)[info activeProcessorCount];
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_has_discrete_gpu(void) {
#ifdef __APPLE__
    @autoreleasepool {
        // On Apple Silicon, there's no discrete GPU (unified memory)
        #if defined(__aarch64__) || defined(__arm64__)
            return 0;
        #else
            // Intel Mac: check for multiple GPUs via IOKit
            io_iterator_t iterator;
            kern_return_t result = IOServiceGetMatchingServices(
                kIOMasterPortDefault,
                IOServiceMatching("IOPCIDevice"),
                &iterator
            );
            
            if (result != KERN_SUCCESS) return 0;
            
            int gpu_count = 0;
            io_service_t service;
            while ((service = IOIteratorNext(iterator)) != 0) {
                CFTypeRef classCode = IORegistryEntryCreateCFProperty(
                    service, CFSTR("class-code"), kCFAllocatorDefault, 0
                );
                if (classCode) {
                    if (CFGetTypeID(classCode) == CFDataGetTypeID()) {
                        const UInt8* data = CFDataGetBytePtr((CFDataRef)classCode);
                        // PCI class code 0x03 = display controller
                        if (data && (data[2] == 0x03 || data[3] == 0x03)) {
                            gpu_count++;
                        }
                    }
                    CFRelease(classCode);
                }
                IOObjectRelease(service);
            }
            IOObjectRelease(iterator);
            
            // More than 1 GPU suggests discrete + integrated
            return (gpu_count > 1) ? 1 : 0;
        #endif
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_supports_metal(void) {
#ifdef __APPLE__
    @autoreleasepool {
        // Check if Metal framework is available and has devices
        Class mtlClass = NSClassFromString(@"MTLCreateSystemDefaultDevice");
        if (!mtlClass) {
            // Try direct function check
            void* metalLib = dlopen("/System/Library/Frameworks/Metal.framework/Metal", RTLD_LAZY);
            if (metalLib) {
                typedef id (*MTLCreateFunc)(void);
                MTLCreateFunc createDevice = (MTLCreateFunc)dlsym(metalLib, "MTLCreateSystemDefaultDevice");
                if (createDevice) {
                    id device = createDevice();
                    dlclose(metalLib);
                    return device ? 1 : 0;
                }
                dlclose(metalLib);
            }
        }
        
        // Apple Silicon always supports Metal
        #if defined(__aarch64__) || defined(__arm64__)
            return 1;
        #else
            // Intel Macs from 2012+ support Metal
            return 1;
        #endif
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_get_gpu_memory_mb(void) {
#ifdef __APPLE__
    @autoreleasepool {
        #if defined(__aarch64__) || defined(__arm64__)
            // Apple Silicon: unified memory, return total system memory
            NSProcessInfo* info = [NSProcessInfo processInfo];
            uint64_t physMem = [info physicalMemory];
            return (int32_t)(physMem / (1024 * 1024));
        #else
            // Intel Mac: try to get VRAM size via IOKit
            io_iterator_t iterator;
            kern_return_t result = IOServiceGetMatchingServices(
                kIOMasterPortDefault,
                IOServiceMatching("IOPCIDevice"),
                &iterator
            );
            
            if (result != KERN_SUCCESS) return 0;
            
            int64_t max_vram = 0;
            io_service_t service;
            while ((service = IOIteratorNext(iterator)) != 0) {
                CFTypeRef vramSize = IORegistryEntryCreateCFProperty(
                    service, CFSTR("VRAM,totalMB"), kCFAllocatorDefault, 0
                );
                if (vramSize) {
                    if (CFGetTypeID(vramSize) == CFNumberGetTypeID()) {
                        int64_t vram = 0;
                        CFNumberGetValue((CFNumberRef)vramSize, kCFNumberSInt64Type, &vram);
                        if (vram > max_vram) max_vram = vram;
                    }
                    CFRelease(vramSize);
                }
                IOObjectRelease(service);
            }
            IOObjectRelease(iterator);
            
            return (int32_t)max_vram;
        #endif
    }
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_is_apple_silicon(void) {
#if defined(__aarch64__) || defined(__arm64__)
    return 1;
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_is_rosetta(void) {
#ifdef __APPLE__
    int ret = 0;
    size_t size = sizeof(ret);
    // sysctl.proc_translated indicates Rosetta translation
    if (sysctlbyname("sysctl.proc_translated", &ret, &size, NULL, 0) == 0) {
        return ret;
    }
    return 0;
#else
    return 0;
#endif
}

extern "C" int32_t simjot_hw_get_performance_tier(void) {
#ifdef __APPLE__
    @autoreleasepool {
        int32_t arch = simjot_hw_get_architecture();
        int64_t memMB = simjot_hw_get_total_memory_mb();
        int32_t cores = simjot_hw_get_cpu_core_count();
        int32_t discreteGpu = simjot_hw_has_discrete_gpu();
        
        // Apple Silicon: HIGH or MEDIUM
        if (arch == 1) {
            if (cores >= 8 && memMB >= 16000) return 0; // HIGH
            return 1; // MEDIUM
        }
        
        // Intel Mac
        if (arch == 2) {
            // High: discrete GPU with 16GB+ RAM
            if (discreteGpu && memMB >= 16000) return 0; // HIGH
            // Medium: 8GB+ RAM and 4+ cores
            if (memMB >= 8000 && cores >= 4) return 1; // MEDIUM
            // Low: 4GB+ RAM
            if (memMB >= 4000) return 2; // LOW
            return 3; // VERY_LOW
        }
        
        // Intel 32-bit or unknown
        return 3; // VERY_LOW
    }
#else
    return 2; // LOW for non-macOS
#endif
}

extern "C" int32_t simjot_hw_get_recommended_fps(void) {
#ifdef __APPLE__
    @autoreleasepool {
        int32_t tier = simjot_hw_get_performance_tier();
        float refreshRate = simjot_macos_get_primary_refresh_rate();
        
        switch (tier) {
            case 0: // HIGH
                return (refreshRate > 60.5f) ? 120 : 60;
            case 1: // MEDIUM
                return 60;
            case 2: // LOW
                return 30;
            case 3: // VERY_LOW
            default:
                return 20;
        }
    }
#else
    return 60;
#endif
}

extern "C" int32_t simjot_hw_supports_promotion(void) {
#ifdef __APPLE__
    float refreshRate = simjot_macos_get_primary_refresh_rate();
    return (refreshRate > 60.5f) ? 1 : 0;
#else
    return 0;
#endif
}
