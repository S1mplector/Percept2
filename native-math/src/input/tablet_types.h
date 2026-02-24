/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#ifndef SIMJOT_TABLET_TYPES_H
#define SIMJOT_TABLET_TYPES_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET INPUT SYSTEM
 * 
 * Low-level tablet detection and pressure sensitivity support.
 * Supports Wacom, Apple Pencil (via Sidecar), and generic HID tablets.
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Maximum devices and capabilities */
#define TABLET_MAX_DEVICES          8
#define TABLET_MAX_NAME_LEN         128
#define TABLET_MAX_BUTTONS          32

/* Pressure and tilt ranges */
#define TABLET_PRESSURE_MIN         0.0f
#define TABLET_PRESSURE_MAX         1.0f
#define TABLET_TILT_MAX_DEGREES     60.0f
#define TABLET_ROTATION_MAX         360.0f

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET DEVICE TYPE
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef enum tablet_device_type {
    TABLET_TYPE_UNKNOWN         = 0,
    TABLET_TYPE_STYLUS          = 1,    /* Pen/stylus input */
    TABLET_TYPE_ERASER          = 2,    /* Eraser end of stylus */
    TABLET_TYPE_CURSOR          = 3,    /* Puck/cursor device */
    TABLET_TYPE_TOUCH           = 4,    /* Touch input on tablet */
    TABLET_TYPE_TRACKPAD        = 5,    /* Force Touch trackpad */
    TABLET_TYPE_APPLE_PENCIL    = 6,    /* Apple Pencil via Sidecar */
} tablet_device_type_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET VENDOR
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef enum tablet_vendor {
    TABLET_VENDOR_UNKNOWN       = 0,
    TABLET_VENDOR_WACOM         = 1,
    TABLET_VENDOR_HUION         = 2,
    TABLET_VENDOR_XP_PEN        = 3,
    TABLET_VENDOR_APPLE         = 4,    /* Apple Pencil, Force Touch */
    TABLET_VENDOR_GAOMON        = 5,
    TABLET_VENDOR_UGEE          = 6,
    TABLET_VENDOR_VEIKK         = 7,
    TABLET_VENDOR_GENERIC       = 99,
} tablet_vendor_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET CAPABILITIES - What the device supports
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct tablet_capabilities {
    uint32_t has_pressure       : 1;    /* Pressure sensitivity */
    uint32_t has_tilt           : 1;    /* Tilt detection (X and Y) */
    uint32_t has_rotation       : 1;    /* Barrel rotation */
    uint32_t has_tangent        : 1;    /* Tangential pressure (airbrush) */
    uint32_t has_eraser         : 1;    /* Eraser tip */
    uint32_t has_buttons        : 1;    /* Stylus buttons */
    uint32_t has_touch          : 1;    /* Touch input */
    uint32_t has_hover          : 1;    /* Hover detection */
    uint32_t reserved           : 24;
    
    int32_t pressure_levels;            /* Number of pressure levels (e.g., 8192) */
    int32_t tilt_levels;                /* Tilt resolution levels */
    int32_t button_count;               /* Number of buttons on stylus */
    float max_tilt_x;                   /* Maximum tilt X in degrees */
    float max_tilt_y;                   /* Maximum tilt Y in degrees */
} tablet_capabilities_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET DEVICE INFO - Static device information
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct tablet_device_info {
    uint64_t device_id;                     /* Unique device identifier */
    char name[TABLET_MAX_NAME_LEN];         /* Human-readable device name */
    tablet_vendor_t vendor;                 /* Device manufacturer */
    tablet_device_type_t type;              /* Device type */
    tablet_capabilities_t capabilities;     /* Device capabilities */
    
    /* Physical dimensions (in mm, 0 if unknown) */
    float active_width_mm;
    float active_height_mm;
    
    /* USB/Bluetooth IDs */
    uint16_t vendor_id;                     /* USB vendor ID */
    uint16_t product_id;                    /* USB product ID */
    
    uint8_t connected;                      /* 1 if currently connected */
    uint8_t reserved[7];
} tablet_device_info_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET POINTER STATE - Current stylus/pointer state
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct tablet_pointer_state {
    /* Position (normalized 0.0-1.0 or absolute in points) */
    double x;
    double y;
    
    /* Pressure (0.0 = no contact, 1.0 = max pressure) */
    float pressure;
    
    /* Tilt angles in degrees (-max to +max) */
    float tilt_x;
    float tilt_y;
    
    /* Barrel rotation in degrees (0-360) */
    float rotation;
    
    /* Tangential pressure for airbrush wheels (-1.0 to 1.0) */
    float tangent_pressure;
    
    /* Button states (bitmask) */
    uint32_t buttons;
    
    /* Proximity state */
    uint8_t in_range;           /* 1 if stylus is in detection range */
    uint8_t touching;           /* 1 if touching surface */
    uint8_t inverted;           /* 1 if eraser end is active */
    uint8_t reserved;
    
    /* Timestamp in milliseconds */
    uint64_t timestamp_ms;
    
    /* Device that generated this event */
    uint64_t device_id;
} tablet_pointer_state_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET EVENT TYPE
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef enum tablet_event_type {
    TABLET_EVENT_NONE           = 0,
    TABLET_EVENT_PROXIMITY_IN   = 1,    /* Stylus entered range */
    TABLET_EVENT_PROXIMITY_OUT  = 2,    /* Stylus left range */
    TABLET_EVENT_PEN_DOWN       = 3,    /* Stylus touched surface */
    TABLET_EVENT_PEN_UP         = 4,    /* Stylus lifted from surface */
    TABLET_EVENT_PEN_MOVE       = 5,    /* Stylus moved while touching */
    TABLET_EVENT_PEN_HOVER      = 6,    /* Stylus moved while hovering */
    TABLET_EVENT_BUTTON_DOWN    = 7,    /* Stylus button pressed */
    TABLET_EVENT_BUTTON_UP      = 8,    /* Stylus button released */
    TABLET_EVENT_DEVICE_ADDED   = 9,    /* New tablet connected */
    TABLET_EVENT_DEVICE_REMOVED = 10,   /* Tablet disconnected */
} tablet_event_type_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET EVENT - Complete event data
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct tablet_event {
    tablet_event_type_t type;
    tablet_pointer_state_t state;
    int32_t button_number;          /* For button events, which button */
    int32_t reserved;
} tablet_event_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET CONTEXT - Manages tablet input state
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct tablet_context tablet_context_t;

/* Callback for tablet events */
typedef void (*tablet_event_callback_t)(const tablet_event_t* event, void* user_data);

/* ═══════════════════════════════════════════════════════════════════════════
 * ERROR CODES
 * ═══════════════════════════════════════════════════════════════════════════ */

#define TABLET_OK                   0
#define TABLET_ERR_NULL_PTR        -1
#define TABLET_ERR_NOT_SUPPORTED   -2
#define TABLET_ERR_NO_DEVICES      -3
#define TABLET_ERR_INIT_FAILED     -4
#define TABLET_ERR_INVALID_DEVICE  -5
#define TABLET_ERR_PERMISSION      -6

#ifdef __cplusplus
}
#endif

#endif /* SIMJOT_TABLET_TYPES_H */
