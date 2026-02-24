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
#if __has_include(<QuickLookThumbnailing/QuickLookThumbnailing.h>)
#import <QuickLookThumbnailing/QuickLookThumbnailing.h>
#define SIMJOT_HAS_QL_THUMBNAILING 1
#else
#define SIMJOT_HAS_QL_THUMBNAILING 0
#endif
#import <dispatch/dispatch.h>
#include <string.h>

static NSImage* simjot_generate_quicklook_image(NSURL* url, int32_t max_edge) {
#if SIMJOT_HAS_QL_THUMBNAILING
    if (@available(macOS 10.15, *)) {
        CGSize size = CGSizeMake((CGFloat)max_edge, (CGFloat)max_edge);
        QLThumbnailGenerationRequest* req = [[QLThumbnailGenerationRequest alloc]
            initWithFileAtURL:url
            size:size
            scale:1.0
            representationTypes:QLThumbnailGenerationRequestRepresentationTypeAll];

        __block NSImage* outImage = nil;
        dispatch_semaphore_t sema = dispatch_semaphore_create(0);

        [[QLThumbnailGenerator sharedGenerator] generateBestRepresentationForRequest:req
                                                                   completionHandler:^(QLThumbnailRepresentation* rep, NSError* err) {
            if (rep && !err) {
                outImage = rep.NSImage;
            }
            dispatch_semaphore_signal(sema);
        }];

        dispatch_time_t waitUntil = dispatch_time(DISPATCH_TIME_NOW, 2LL * NSEC_PER_SEC);
        long waitResult = dispatch_semaphore_wait(sema, waitUntil);
        if (waitResult == 0 && outImage) {
            return outImage;
        }
    }
#endif

    // Fallback: Finder icon thumbnail.
    NSImage* icon = [[NSWorkspace sharedWorkspace] iconForFile:[url path]];
    if (!icon) return nil;
    [icon setSize:NSMakeSize(max_edge, max_edge)];
    return icon;
}

static NSData* simjot_image_to_png_data(NSImage* image) {
    if (!image) return nil;
    NSData* tiff = [image TIFFRepresentation];
    if (!tiff) return nil;
    NSBitmapImageRep* rep = [NSBitmapImageRep imageRepWithData:tiff];
    if (!rep) return nil;
    return [rep representationUsingType:NSBitmapImageFileTypePNG properties:@{}];
}
#endif

extern "C" int32_t simjot_macos_quicklook_thumbnail_png(const char* path,
                                                           int32_t max_edge,
                                                           uint8_t* out,
                                                           int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        if (max_edge <= 0) max_edge = 256;
        if (max_edge > 2048) max_edge = 2048;

        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath || nsPath.length == 0) return -1;

        NSFileManager* fm = [NSFileManager defaultManager];
        if (![fm fileExistsAtPath:nsPath]) return -1;

        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return -1;

        NSImage* image = simjot_generate_quicklook_image(url, max_edge);
        if (!image) return 0;

        NSData* png = simjot_image_to_png_data(image);
        if (!png) return 0;

        int32_t len = (int32_t)[png length];
        if (!out || out_len <= 0) return len;

        int32_t copy_len = len < (out_len - 1) ? len : (out_len - 1);
        if (copy_len > 0) memcpy(out, [png bytes], (size_t)copy_len);
        out[copy_len] = 0;
        return len;
    }
#else
    (void)path;
    (void)max_edge;
    (void)out;
    (void)out_len;
    return -1;
#endif
}
