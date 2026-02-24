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
#import <CoreServices/CoreServices.h>
#include <string.h>

static NSMutableArray<NSURL*>* simjot_parse_scopes(const char* roots_newline) {
    if (!roots_newline || !*roots_newline) return nil;
    NSString* roots = [NSString stringWithUTF8String:roots_newline];
    if (!roots || roots.length == 0) return nil;

    NSMutableArray<NSURL*>* scopes = [NSMutableArray array];
    NSArray<NSString*>* lines = [roots componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
    NSFileManager* fm = [NSFileManager defaultManager];
    for (NSString* raw in lines) {
        NSString* s = [raw stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (s.length == 0) continue;
        BOOL isDir = NO;
        if ([fm fileExistsAtPath:s isDirectory:&isDir] && isDir) {
            [scopes addObject:[NSURL fileURLWithPath:s]];
        }
    }
    return scopes.count > 0 ? scopes : nil;
}

static NSSet<NSString*>* simjot_parse_extensions(const char* extensions_csv) {
    if (!extensions_csv || !*extensions_csv) return nil;
    NSString* csv = [NSString stringWithUTF8String:extensions_csv];
    if (!csv || csv.length == 0) return nil;

    NSMutableSet<NSString*>* out = [NSMutableSet set];
    for (NSString* raw in [csv componentsSeparatedByString:@","]) {
        NSString* s = [[raw stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] lowercaseString];
        if (s.length == 0) continue;
        if ([s hasPrefix:@"."]) s = [s substringFromIndex:1];
        if (s.length > 0) [out addObject:s];
    }
    return out.count > 0 ? out : nil;
}

static int simjot_path_matches_ext(NSString* path, NSSet<NSString*>* exts) {
    if (!path || !exts || exts.count == 0) return 1;
    NSString* ext = [[path pathExtension] lowercaseString];
    if (!ext || ext.length == 0) return 0;
    return [exts containsObject:ext] ? 1 : 0;
}
#endif

extern "C" int32_t simjot_macos_spotlight_search(const char* root_paths_newline,
                                                    const char* query,
                                                    const char* extensions_csv,
                                                    int32_t max_results,
                                                    int32_t timeout_ms,
                                                    char* out,
                                                    int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!root_paths_newline || !query) return -1;
        if (max_results <= 0) max_results = 100;
        if (timeout_ms <= 0) timeout_ms = 1200;

        NSString* q = [NSString stringWithUTF8String:query];
        if (!q) return -1;
        q = [q stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (q.length == 0) return 0;

        NSMutableArray<NSURL*>* scopes = simjot_parse_scopes(root_paths_newline);
        if (!scopes) return 0;

        NSSet<NSString*>* extFilter = simjot_parse_extensions(extensions_csv);

        NSMetadataQuery* mdq = [[NSMetadataQuery alloc] init];
        [mdq setSearchScopes:scopes];

        NSPredicate* predicate = [NSPredicate predicateWithFormat:
                                  @"((%K CONTAINS[cd] %@) OR (%K CONTAINS[cd] %@))",
                                  (NSString*)kMDItemFSName,
                                  q,
                                  (NSString*)kMDItemTextContent,
                                  q];
        [mdq setPredicate:predicate];

        __block BOOL finished = NO;
        NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
        id observer = [nc addObserverForName:NSMetadataQueryDidFinishGatheringNotification
                                      object:mdq
                                       queue:nil
                                  usingBlock:^(__unused NSNotification* note) {
            finished = YES;
        }];

        if (![mdq startQuery]) {
            [nc removeObserver:observer];
            return -1;
        }

        NSDate* deadline = [NSDate dateWithTimeIntervalSinceNow:((double)timeout_ms / 1000.0)];
        while (!finished && [deadline timeIntervalSinceNow] > 0) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.02]];
        }

        [mdq disableUpdates];
        NSArray* results = [mdq results] ?: @[];

        NSMutableString* lines = [NSMutableString string];
        int32_t accepted = 0;
        for (NSMetadataItem* item in results) {
            if (accepted >= max_results) break;
            id value = [item valueForAttribute:NSMetadataItemPathKey];
            if (![value isKindOfClass:[NSString class]]) continue;
            NSString* path = (NSString*)value;
            if (!simjot_path_matches_ext(path, extFilter)) continue;

            if (lines.length > 0) [lines appendString:@"\n"];
            [lines appendString:path];
            accepted++;
        }

        [mdq stopQuery];
        [nc removeObserver:observer];

        NSData* data = [lines dataUsingEncoding:NSUTF8StringEncoding];
        if (!data) return 0;

        int32_t len = (int32_t)[data length];
        if (!out || out_len <= 0) return len;

        int32_t copy_len = len < (out_len - 1) ? len : (out_len - 1);
        if (copy_len > 0) memcpy(out, [data bytes], (size_t)copy_len);
        out[copy_len] = '\0';
        return len;
    }
#else
    (void)root_paths_newline;
    (void)query;
    (void)extensions_csv;
    (void)max_results;
    (void)timeout_ms;
    (void)out;
    (void)out_len;
    return -1;
#endif
}
