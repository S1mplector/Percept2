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
#import <IOKit/pwr_mgt/IOPMLib.h>
#endif

extern "C" int32_t simjot_macos_power_assertion_begin(const char* reason, int64_t* out_token) {
#ifdef __APPLE__
    @autoreleasepool {
        if (out_token) *out_token = 0;

        NSString* why = nil;
        if (reason && *reason) {
            why = [NSString stringWithUTF8String:reason];
        }
        if (!why || why.length == 0) {
            why = @"Simjot long-running operation";
        }

        IOPMAssertionID assertionId = kIOPMNullAssertionID;
        IOReturn rc = IOPMAssertionCreateWithName(kIOPMAssertionTypeNoIdleSleep,
                                                  kIOPMAssertionLevelOn,
                                                  (CFStringRef)why,
                                                  &assertionId);
        if (rc != kIOReturnSuccess || assertionId == kIOPMNullAssertionID) {
            return 0;
        }

        if (out_token) *out_token = (int64_t)assertionId;
        return 1;
    }
#else
    (void)reason;
    (void)out_token;
    return -1;
#endif
}

extern "C" int32_t simjot_macos_power_assertion_end(int64_t token) {
#ifdef __APPLE__
    if (token <= 0) return 0;
    IOPMAssertionID assertionId = (IOPMAssertionID)token;
    IOReturn rc = IOPMAssertionRelease(assertionId);
    return (rc == kIOReturnSuccess) ? 1 : 0;
#else
    (void)token;
    return -1;
#endif
}
