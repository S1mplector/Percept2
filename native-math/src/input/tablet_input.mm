/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "tablet_types.h"
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <mutex>
#include <vector>
#include <atomic>

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#import <Carbon/Carbon.h>
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET CONTEXT IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

struct tablet_context {
    std::vector<tablet_device_info_t> devices;
    tablet_pointer_state_t current_state;
    tablet_event_callback_t callback;
    void* user_data;
    std::mutex mutex;
    std::atomic<bool> initialized;
    
#ifdef __APPLE__
    id event_monitor;
    id local_monitor;
#endif
};

/* Global context for singleton pattern */
static tablet_context_t* g_tablet_ctx = nullptr;
static std::mutex g_init_mutex;

/* ═══════════════════════════════════════════════════════════════════════════
 * VENDOR DETECTION FROM USB IDS
 * ═══════════════════════════════════════════════════════════════════════════ */

static tablet_vendor_t detect_vendor(uint16_t vendor_id) {
    switch (vendor_id) {
        case 0x056A: return TABLET_VENDOR_WACOM;
        case 0x256C: return TABLET_VENDOR_HUION;
        case 0x28BD: return TABLET_VENDOR_XP_PEN;
        case 0x05AC: return TABLET_VENDOR_APPLE;
        case 0x256F: return TABLET_VENDOR_GAOMON;
        case 0x5543: return TABLET_VENDOR_UGEE;
        case 0x2FEB: return TABLET_VENDOR_VEIKK;
        default:     return TABLET_VENDOR_GENERIC;
    }
}

#ifdef __APPLE__

/* ═══════════════════════════════════════════════════════════════════════════
 * macOS EVENT PROCESSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Helper to safely get tablet pressure */
static float safe_get_pressure(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(pressure)]) {
            return [event pressure];
        }
    } @catch (NSException* e) {
        /* Ignore - event doesn't support pressure */
    }
    return 0.0f;
}

/* Helper to safely get tablet tilt */
static NSPoint safe_get_tilt(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(tilt)]) {
            return [event tilt];
        }
    } @catch (NSException* e) {
        /* Ignore - event doesn't support tilt */
    }
    return NSMakePoint(0, 0);
}

/* Helper to safely get tablet rotation */
static float safe_get_rotation(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(rotation)]) {
            return [event rotation];
        }
    } @catch (NSException* e) {
        /* Ignore - event doesn't support rotation */
    }
    return 0.0f;
}

/* Helper to safely get tangential pressure */
static float safe_get_tangential_pressure(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(tangentialPressure)]) {
            return [event tangentialPressure];
        }
    } @catch (NSException* e) {
        /* Ignore */
    }
    return 0.0f;
}

/* Helper to safely get device ID */
static NSUInteger safe_get_device_id(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(deviceID)]) {
            return [event deviceID];
        }
    } @catch (NSException* e) {
        /* Ignore */
    }
    return 0;
}

/* Helper to safely check event subtype */
static BOOL is_tablet_subtype(NSEvent* event) {
    @try {
        if ([event respondsToSelector:@selector(subtype)]) {
            return [event subtype] == NSEventSubtypeTabletPoint;
        }
    } @catch (NSException* e) {
        /* Ignore */
    }
    return NO;
}

static void process_tablet_event(NSEvent* event, tablet_context_t* ctx) {
    if (!ctx || !event) return;
    
    /* Wrap entire processing in try/catch for safety */
    @try {
        @autoreleasepool {
            tablet_event_t tablet_evt = {};
            tablet_pointer_state_t* state = &tablet_evt.state;
            
            /* Get basic position - these should always be safe */
            NSPoint loc = [event locationInWindow];
            state->x = loc.x;
            state->y = loc.y;
            
            /* Get timestamp */
            state->timestamp_ms = (uint64_t)([event timestamp] * 1000.0);
            
            /* Determine event type and get pressure data */
            NSEventType type = [event type];
            
            switch (type) {
                case NSEventTypeTabletProximity: {
                    @try {
                        BOOL entering = [event isEnteringProximity];
                        tablet_evt.type = entering ? TABLET_EVENT_PROXIMITY_IN : TABLET_EVENT_PROXIMITY_OUT;
                        state->in_range = entering ? 1 : 0;
                        state->device_id = safe_get_device_id(event);
                        
                        /* Check if eraser */
                        if ([event respondsToSelector:@selector(pointingDeviceType)]) {
                            NSPointingDeviceType deviceType = [event pointingDeviceType];
                            state->inverted = (deviceType == NSPointingDeviceTypeEraser) ? 1 : 0;
                        }
                    } @catch (NSException* e) {
                        return; /* Skip this event */
                    }
                    break;
                }
                    
                case NSEventTypeTabletPoint: {
                    /* Full tablet point data */
                    state->pressure = safe_get_pressure(event);
                    NSPoint tilt = safe_get_tilt(event);
                    state->tilt_x = tilt.x * TABLET_TILT_MAX_DEGREES;
                    state->tilt_y = tilt.y * TABLET_TILT_MAX_DEGREES;
                    state->rotation = safe_get_rotation(event);
                    state->tangent_pressure = safe_get_tangential_pressure(event);
                    @try {
                        if ([event respondsToSelector:@selector(buttonMask)]) {
                            state->buttons = (uint32_t)[event buttonMask];
                        }
                    } @catch (NSException* e) { /* ignore */ }
                    state->device_id = safe_get_device_id(event);
                    state->in_range = 1;
                    state->touching = (state->pressure > 0.0f) ? 1 : 0;
                    
                    tablet_evt.type = state->touching ? TABLET_EVENT_PEN_MOVE : TABLET_EVENT_PEN_HOVER;
                    break;
                }
                    
                case NSEventTypeLeftMouseDown:
                case NSEventTypeRightMouseDown:
                case NSEventTypeOtherMouseDown: {
                    /* Check for tablet pressure on mouse events */
                    if (is_tablet_subtype(event)) {
                        state->pressure = safe_get_pressure(event);
                        NSPoint tilt = safe_get_tilt(event);
                        state->tilt_x = tilt.x * TABLET_TILT_MAX_DEGREES;
                        state->tilt_y = tilt.y * TABLET_TILT_MAX_DEGREES;
                        state->rotation = safe_get_rotation(event);
                        state->device_id = safe_get_device_id(event);
                        state->in_range = 1;
                        state->touching = 1;
                        tablet_evt.type = TABLET_EVENT_PEN_DOWN;
                    } else {
                        /* Regular mouse - no pressure */
                        state->pressure = 1.0f;
                        state->touching = 1;
                        tablet_evt.type = TABLET_EVENT_PEN_DOWN;
                    }
                    break;
                }
                    
                case NSEventTypeLeftMouseUp:
                case NSEventTypeRightMouseUp:
                case NSEventTypeOtherMouseUp: {
                    state->pressure = 0.0f;
                    state->touching = 0;
                    if (is_tablet_subtype(event)) {
                        state->device_id = safe_get_device_id(event);
                    }
                    tablet_evt.type = TABLET_EVENT_PEN_UP;
                    break;
                }
                    
                case NSEventTypeLeftMouseDragged:
                case NSEventTypeRightMouseDragged:
                case NSEventTypeOtherMouseDragged: {
                    if (is_tablet_subtype(event)) {
                        state->pressure = safe_get_pressure(event);
                        NSPoint tilt = safe_get_tilt(event);
                        state->tilt_x = tilt.x * TABLET_TILT_MAX_DEGREES;
                        state->tilt_y = tilt.y * TABLET_TILT_MAX_DEGREES;
                        state->rotation = safe_get_rotation(event);
                        state->tangent_pressure = safe_get_tangential_pressure(event);
                        state->device_id = safe_get_device_id(event);
                    } else {
                        /* Regular mouse drag - full pressure */
                        state->pressure = 1.0f;
                    }
                    state->in_range = 1;
                    state->touching = 1;
                    tablet_evt.type = TABLET_EVENT_PEN_MOVE;
                    break;
                }
                    
                case NSEventTypeMouseMoved: {
                    if (is_tablet_subtype(event)) {
                        state->pressure = safe_get_pressure(event);
                        NSPoint tilt = safe_get_tilt(event);
                        state->tilt_x = tilt.x * TABLET_TILT_MAX_DEGREES;
                        state->tilt_y = tilt.y * TABLET_TILT_MAX_DEGREES;
                        state->device_id = safe_get_device_id(event);
                        state->in_range = 1;
                    }
                    state->touching = 0;
                    tablet_evt.type = TABLET_EVENT_PEN_HOVER;
                    break;
                }
                    
                case NSEventTypePressure: {
                    /* Force Touch trackpad */
                    state->pressure = safe_get_pressure(event);
                    state->touching = (state->pressure > 0.0f) ? 1 : 0;
                    tablet_evt.type = TABLET_EVENT_PEN_MOVE;
                    break;
                }
                    
                default:
                    return; /* Ignore other events */
            }
            
            /* Update current state */
            {
                std::lock_guard<std::mutex> lock(ctx->mutex);
                ctx->current_state = *state;
            }
            
            /* Fire callback if registered */
            if (ctx->callback) {
                ctx->callback(&tablet_evt, ctx->user_data);
            }
        }
    } @catch (NSException* e) {
        /* Silently ignore any exceptions to prevent crashes */
    } @catch (...) {
        /* Catch any C++ exceptions too */
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * macOS DEVICE ENUMERATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static void enumerate_tablets_macos(tablet_context_t* ctx) {
    if (!ctx) return;
    
    std::lock_guard<std::mutex> lock(ctx->mutex);
    ctx->devices.clear();
    
    /* Check for Force Touch trackpad (always available on modern Macs) */
    tablet_device_info_t trackpad = {};
    trackpad.device_id = 1;
    strncpy(trackpad.name, "Force Touch Trackpad", TABLET_MAX_NAME_LEN - 1);
    trackpad.vendor = TABLET_VENDOR_APPLE;
    trackpad.type = TABLET_TYPE_TRACKPAD;
    trackpad.capabilities.has_pressure = 1;
    trackpad.capabilities.pressure_levels = 256;
    trackpad.connected = 1;
    trackpad.vendor_id = 0x05AC;
    ctx->devices.push_back(trackpad);
    
    /* Note: Wacom and other tablets are detected via IOKit HID
     * For simplicity, we rely on NSEvent subtype detection during input */
}

#endif /* __APPLE__ */

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - CONTEXT MANAGEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

tablet_context_t* tablet_create_context(void) {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    
    if (g_tablet_ctx) {
        return g_tablet_ctx; /* Return existing singleton */
    }
    
    tablet_context_t* ctx = new (std::nothrow) tablet_context_t();
    if (!ctx) return nullptr;
    
    ctx->callback = nullptr;
    ctx->user_data = nullptr;
    ctx->initialized = false;
    memset(&ctx->current_state, 0, sizeof(tablet_pointer_state_t));
    
#ifdef __APPLE__
    ctx->event_monitor = nil;
    ctx->local_monitor = nil;
#endif
    
    g_tablet_ctx = ctx;
    return ctx;
}

void tablet_destroy_context(tablet_context_t* ctx) {
    if (!ctx) return;
    
    std::lock_guard<std::mutex> lock(g_init_mutex);
    
#ifdef __APPLE__
    if (ctx->event_monitor) {
        [NSEvent removeMonitor:ctx->event_monitor];
        ctx->event_monitor = nil;
    }
    if (ctx->local_monitor) {
        [NSEvent removeMonitor:ctx->local_monitor];
        ctx->local_monitor = nil;
    }
#endif
    
    ctx->initialized = false;
    
    if (g_tablet_ctx == ctx) {
        g_tablet_ctx = nullptr;
    }
    
    delete ctx;
}

int tablet_initialize(tablet_context_t* ctx) {
    if (!ctx) return TABLET_ERR_NULL_PTR;
    if (ctx->initialized) return TABLET_OK;
    
#ifdef __APPLE__
    @autoreleasepool {
        /* Enumerate available devices */
        enumerate_tablets_macos(ctx);
        
        /* DISABLED: Event monitoring causes crashes with Sidecar/Apple Pencil
         * For now, we rely on Java's built-in tablet support instead.
         * The native API will return default values.
         *
        NSEventMask mask = NSEventMaskTabletPoint | 
                          NSEventMaskTabletProximity |
                          NSEventMaskLeftMouseDown |
                          NSEventMaskLeftMouseUp |
                          NSEventMaskLeftMouseDragged |
                          NSEventMaskRightMouseDown |
                          NSEventMaskRightMouseUp |
                          NSEventMaskRightMouseDragged |
                          NSEventMaskOtherMouseDown |
                          NSEventMaskOtherMouseUp |
                          NSEventMaskOtherMouseDragged |
                          NSEventMaskMouseMoved |
                          NSEventMaskPressure;
        
        ctx->local_monitor = [NSEvent addLocalMonitorForEventsMatchingMask:mask
            handler:^NSEvent*(NSEvent* event) {
                process_tablet_event(event, ctx);
                return event;
            }];
        
        if (!ctx->local_monitor) {
            return TABLET_ERR_INIT_FAILED;
        }
        */
    }
#endif
    
    ctx->initialized = true;
    return TABLET_OK;
}

int tablet_is_available(void) {
#ifdef __APPLE__
    return 1; /* macOS always has some form of pressure input */
#else
    return 0;
#endif
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - DEVICE ENUMERATION
 * ═══════════════════════════════════════════════════════════════════════════ */

int tablet_get_device_count(tablet_context_t* ctx) {
    if (!ctx) return 0;
    std::lock_guard<std::mutex> lock(ctx->mutex);
    return (int)ctx->devices.size();
}

int tablet_get_device_info(tablet_context_t* ctx, int index, tablet_device_info_t* info) {
    if (!ctx || !info) return TABLET_ERR_NULL_PTR;
    
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (index < 0 || index >= (int)ctx->devices.size()) {
        return TABLET_ERR_INVALID_DEVICE;
    }
    
    *info = ctx->devices[index];
    return TABLET_OK;
}

int tablet_refresh_devices(tablet_context_t* ctx) {
    if (!ctx) return TABLET_ERR_NULL_PTR;
    
#ifdef __APPLE__
    enumerate_tablets_macos(ctx);
#endif
    
    return TABLET_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - STATE QUERIES
 * ═══════════════════════════════════════════════════════════════════════════ */

int tablet_get_current_state(tablet_context_t* ctx, tablet_pointer_state_t* state) {
    if (!ctx || !state) return TABLET_ERR_NULL_PTR;
    
    std::lock_guard<std::mutex> lock(ctx->mutex);
    *state = ctx->current_state;
    return TABLET_OK;
}

float tablet_get_current_pressure(tablet_context_t* ctx) {
    if (!ctx) return 0.0f;
    std::lock_guard<std::mutex> lock(ctx->mutex);
    return ctx->current_state.pressure;
}

int tablet_get_current_tilt(tablet_context_t* ctx, float* tilt_x, float* tilt_y) {
    if (!ctx) return TABLET_ERR_NULL_PTR;
    
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (tilt_x) *tilt_x = ctx->current_state.tilt_x;
    if (tilt_y) *tilt_y = ctx->current_state.tilt_y;
    return TABLET_OK;
}

int tablet_is_stylus_in_range(tablet_context_t* ctx) {
    if (!ctx) return 0;
    std::lock_guard<std::mutex> lock(ctx->mutex);
    return ctx->current_state.in_range;
}

int tablet_is_eraser_active(tablet_context_t* ctx) {
    if (!ctx) return 0;
    std::lock_guard<std::mutex> lock(ctx->mutex);
    return ctx->current_state.inverted;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - CALLBACKS
 * ═══════════════════════════════════════════════════════════════════════════ */

int tablet_set_callback(tablet_context_t* ctx, tablet_event_callback_t callback, void* user_data) {
    if (!ctx) return TABLET_ERR_NULL_PTR;
    
    std::lock_guard<std::mutex> lock(ctx->mutex);
    ctx->callback = callback;
    ctx->user_data = user_data;
    return TABLET_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API - PRESSURE CURVE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

float tablet_apply_pressure_curve(float pressure, float gamma) {
    if (pressure <= 0.0f) return 0.0f;
    if (pressure >= 1.0f) return 1.0f;
    if (gamma <= 0.0f) gamma = 1.0f;
    return powf(pressure, 1.0f / gamma);
}

float tablet_apply_pressure_bezier(float pressure, float p1, float p2) {
    /* Simple cubic bezier: B(t) = 3(1-t)^2*t*p1 + 3(1-t)*t^2*p2 + t^3 */
    if (pressure <= 0.0f) return 0.0f;
    if (pressure >= 1.0f) return 1.0f;
    
    float t = pressure;
    float mt = 1.0f - t;
    return 3.0f * mt * mt * t * p1 + 3.0f * mt * t * t * p2 + t * t * t;
}

float tablet_smooth_pressure(float current, float previous, float smoothing) {
    if (smoothing <= 0.0f) return current;
    if (smoothing >= 1.0f) return previous;
    return previous + (current - previous) * (1.0f - smoothing);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMJOT API WRAPPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

void* simjot_tablet_create_context(void) {
    return tablet_create_context();
}

void simjot_tablet_destroy_context(void* ctx) {
    tablet_destroy_context((tablet_context_t*)ctx);
}

int32_t simjot_tablet_initialize(void* ctx) {
    return tablet_initialize((tablet_context_t*)ctx);
}

int32_t simjot_tablet_is_available(void) {
    return tablet_is_available();
}

int32_t simjot_tablet_get_device_count(void* ctx) {
    return tablet_get_device_count((tablet_context_t*)ctx);
}

int32_t simjot_tablet_get_device_name(void* ctx, int32_t index, char* out, int32_t out_len) {
    if (!ctx || !out || out_len <= 0) return TABLET_ERR_NULL_PTR;
    
    tablet_device_info_t info;
    int result = tablet_get_device_info((tablet_context_t*)ctx, index, &info);
    if (result != TABLET_OK) return result;
    
    strncpy(out, info.name, out_len - 1);
    out[out_len - 1] = '\0';
    return (int32_t)strlen(out);
}

int32_t simjot_tablet_get_device_vendor(void* ctx, int32_t index) {
    if (!ctx) return TABLET_VENDOR_UNKNOWN;
    
    tablet_device_info_t info;
    if (tablet_get_device_info((tablet_context_t*)ctx, index, &info) != TABLET_OK) {
        return TABLET_VENDOR_UNKNOWN;
    }
    return (int32_t)info.vendor;
}

int32_t simjot_tablet_get_device_type(void* ctx, int32_t index) {
    if (!ctx) return TABLET_TYPE_UNKNOWN;
    
    tablet_device_info_t info;
    if (tablet_get_device_info((tablet_context_t*)ctx, index, &info) != TABLET_OK) {
        return TABLET_TYPE_UNKNOWN;
    }
    return (int32_t)info.type;
}

int32_t simjot_tablet_get_pressure_levels(void* ctx, int32_t index) {
    if (!ctx) return 0;
    
    tablet_device_info_t info;
    if (tablet_get_device_info((tablet_context_t*)ctx, index, &info) != TABLET_OK) {
        return 0;
    }
    return info.capabilities.pressure_levels;
}

int32_t simjot_tablet_has_tilt(void* ctx, int32_t index) {
    if (!ctx) return 0;
    
    tablet_device_info_t info;
    if (tablet_get_device_info((tablet_context_t*)ctx, index, &info) != TABLET_OK) {
        return 0;
    }
    return info.capabilities.has_tilt;
}

int32_t simjot_tablet_refresh_devices(void* ctx) {
    return tablet_refresh_devices((tablet_context_t*)ctx);
}

float simjot_tablet_get_pressure(void* ctx) {
    return tablet_get_current_pressure((tablet_context_t*)ctx);
}

int32_t simjot_tablet_get_tilt(void* ctx, float* tilt_x, float* tilt_y) {
    return tablet_get_current_tilt((tablet_context_t*)ctx, tilt_x, tilt_y);
}

float simjot_tablet_get_rotation(void* ctx) {
    if (!ctx) return 0.0f;
    
    tablet_pointer_state_t state;
    if (tablet_get_current_state((tablet_context_t*)ctx, &state) != TABLET_OK) {
        return 0.0f;
    }
    return state.rotation;
}

int32_t simjot_tablet_is_stylus_in_range(void* ctx) {
    return tablet_is_stylus_in_range((tablet_context_t*)ctx);
}

int32_t simjot_tablet_is_eraser_active(void* ctx) {
    return tablet_is_eraser_active((tablet_context_t*)ctx);
}

int32_t simjot_tablet_is_touching(void* ctx) {
    if (!ctx) return 0;
    
    tablet_pointer_state_t state;
    if (tablet_get_current_state((tablet_context_t*)ctx, &state) != TABLET_OK) {
        return 0;
    }
    return state.touching;
}

uint32_t simjot_tablet_get_buttons(void* ctx) {
    if (!ctx) return 0;
    
    tablet_pointer_state_t state;
    if (tablet_get_current_state((tablet_context_t*)ctx, &state) != TABLET_OK) {
        return 0;
    }
    return state.buttons;
}

float simjot_tablet_apply_pressure_curve(float pressure, float gamma) {
    return tablet_apply_pressure_curve(pressure, gamma);
}

float simjot_tablet_apply_pressure_bezier(float pressure, float p1, float p2) {
    return tablet_apply_pressure_bezier(pressure, p1, p2);
}

float simjot_tablet_smooth_pressure(float current, float previous, float smoothing) {
    return tablet_smooth_pressure(current, previous, smoothing);
}

} /* extern "C" */
