/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file ui_scale.c
 * @brief Native UI Scaling for Simjot
 * 
 * Cross-platform DPI-aware scaling using native APIs:
 * - macOS: CoreGraphics/AppKit for display scale factors
 * - Windows: GetDpiForMonitor/GetDpiForWindow
 * - Linux: X11 DPI or GDK_SCALE environment
 * 
 * Provides consistent, reliable scaling across all displays.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifdef __APPLE__
#include <CoreGraphics/CoreGraphics.h>
#include <ApplicationServices/ApplicationServices.h>
#endif

#ifdef _WIN32
#include <windows.h>
#include <shellscalingapi.h>
#pragma comment(lib, "Shcore.lib")
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS
 * ═══════════════════════════════════════════════════════════════════════════ */

#define BASE_DPI 96.0f
#define MIN_SCALE 0.5f
#define MAX_SCALE 4.0f
#define MAX_DISPLAYS 16

/* ═══════════════════════════════════════════════════════════════════════════
 * INTERNAL STATE
 * ═══════════════════════════════════════════════════════════════════════════ */

static float cached_scales[MAX_DISPLAYS] = {0};
static int cached_display_count = -1;
static float cached_primary_scale = -1.0f;

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline float clamp_scale(float s) {
    if (s < MIN_SCALE) return MIN_SCALE;
    if (s > MAX_SCALE) return MAX_SCALE;
    return s;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * macOS IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef __APPLE__

/**
 * @brief Get scale factor for a specific display on macOS
 * Uses CoreGraphics to get the backing scale factor
 */
static float macos_get_display_scale(uint32_t displayIndex) {
    uint32_t count;
    CGDirectDisplayID displays[MAX_DISPLAYS];
    
    if (CGGetActiveDisplayList(MAX_DISPLAYS, displays, &count) != kCGErrorSuccess) {
        return 1.0f;
    }
    
    if (displayIndex >= count) {
        return 1.0f;
    }
    
    CGDirectDisplayID display = displays[displayIndex];
    
    /* Get the display mode to determine scale */
    CGDisplayModeRef mode = CGDisplayCopyDisplayMode(display);
    if (mode == NULL) {
        return 1.0f;
    }
    
    /* Calculate scale from pixel width vs point width */
    size_t pixelWidth = CGDisplayModeGetPixelWidth(mode);
    size_t pointWidth = CGDisplayModeGetWidth(mode);
    CGDisplayModeRelease(mode);
    
    if (pointWidth == 0) {
        return 1.0f;
    }
    
    float scale = (float)pixelWidth / (float)pointWidth;
    return clamp_scale(scale);
}

static int macos_get_display_count(void) {
    uint32_t count;
    CGDirectDisplayID displays[MAX_DISPLAYS];
    
    if (CGGetActiveDisplayList(MAX_DISPLAYS, displays, &count) != kCGErrorSuccess) {
        return 1;
    }
    
    return (int)count;
}

static float macos_get_primary_scale(void) {
    CGDirectDisplayID mainDisplay = CGMainDisplayID();
    
    CGDisplayModeRef mode = CGDisplayCopyDisplayMode(mainDisplay);
    if (mode == NULL) {
        return 1.0f;
    }
    
    size_t pixelWidth = CGDisplayModeGetPixelWidth(mode);
    size_t pointWidth = CGDisplayModeGetWidth(mode);
    CGDisplayModeRelease(mode);
    
    if (pointWidth == 0) {
        return 1.0f;
    }
    
    float scale = (float)pixelWidth / (float)pointWidth;
    return clamp_scale(scale);
}

static float macos_get_display_dpi(uint32_t displayIndex) {
    uint32_t count;
    CGDirectDisplayID displays[MAX_DISPLAYS];
    
    if (CGGetActiveDisplayList(MAX_DISPLAYS, displays, &count) != kCGErrorSuccess) {
        return BASE_DPI;
    }
    
    if (displayIndex >= count) {
        return BASE_DPI;
    }
    
    CGDirectDisplayID display = displays[displayIndex];
    CGSize size = CGDisplayScreenSize(display); /* Physical size in mm */
    
    if (size.width <= 0) {
        return BASE_DPI;
    }
    
    /* Get pixel dimensions */
    CGDisplayModeRef mode = CGDisplayCopyDisplayMode(display);
    if (mode == NULL) {
        return BASE_DPI;
    }
    
    size_t pixelWidth = CGDisplayModeGetPixelWidth(mode);
    CGDisplayModeRelease(mode);
    
    /* Calculate DPI: pixels / (mm / 25.4) */
    float dpi = (float)pixelWidth / (size.width / 25.4f);
    return dpi;
}

#endif /* __APPLE__ */

/* ═══════════════════════════════════════════════════════════════════════════
 * WINDOWS IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef _WIN32

typedef struct {
    int count;
    float scales[MAX_DISPLAYS];
} WinDisplayInfo;

static BOOL CALLBACK enum_monitors_proc(HMONITOR hMonitor, HDC hdcMonitor, 
                                         LPRECT lprcMonitor, LPARAM dwData) {
    WinDisplayInfo* info = (WinDisplayInfo*)dwData;
    if (info->count >= MAX_DISPLAYS) return TRUE;
    
    UINT dpiX = 96, dpiY = 96;
    
    /* Try to get per-monitor DPI (Windows 8.1+) */
    HMODULE shcore = LoadLibraryA("Shcore.dll");
    if (shcore) {
        typedef HRESULT (WINAPI *GetDpiForMonitorFunc)(HMONITOR, int, UINT*, UINT*);
        GetDpiForMonitorFunc getDpi = (GetDpiForMonitorFunc)GetProcAddress(shcore, "GetDpiForMonitor");
        if (getDpi) {
            getDpi(hMonitor, 0 /* MDT_EFFECTIVE_DPI */, &dpiX, &dpiY);
        }
        FreeLibrary(shcore);
    }
    
    float scale = (float)dpiX / BASE_DPI;
    info->scales[info->count++] = clamp_scale(scale);
    
    return TRUE;
}

static float windows_get_display_scale(uint32_t displayIndex) {
    WinDisplayInfo info = {0};
    EnumDisplayMonitors(NULL, NULL, enum_monitors_proc, (LPARAM)&info);
    
    if (displayIndex >= (uint32_t)info.count) {
        return 1.0f;
    }
    
    return info.scales[displayIndex];
}

static int windows_get_display_count(void) {
    return GetSystemMetrics(SM_CMONITORS);
}

static float windows_get_primary_scale(void) {
    HDC hdc = GetDC(NULL);
    if (!hdc) return 1.0f;
    
    int dpi = GetDeviceCaps(hdc, LOGPIXELSX);
    ReleaseDC(NULL, hdc);
    
    return clamp_scale((float)dpi / BASE_DPI);
}

static float windows_get_display_dpi(uint32_t displayIndex) {
    WinDisplayInfo info = {0};
    EnumDisplayMonitors(NULL, NULL, enum_monitors_proc, (LPARAM)&info);
    
    if (displayIndex >= (uint32_t)info.count) {
        return BASE_DPI;
    }
    
    return info.scales[displayIndex] * BASE_DPI;
}

#endif /* _WIN32 */

/* ═══════════════════════════════════════════════════════════════════════════
 * LINUX IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

#if !defined(__APPLE__) && !defined(_WIN32)

static float linux_get_env_scale(void) {
    float scale = 1.0f;
    
    /* Check GDK_SCALE (GNOME/GTK integer scale) */
    const char* gdk = getenv("GDK_SCALE");
    if (gdk && *gdk) {
        float s = (float)atof(gdk);
        if (s > 0) scale = s;
    }
    
    /* Check GDK_DPI_SCALE (GNOME/GTK fractional scale) */
    const char* gdkDpi = getenv("GDK_DPI_SCALE");
    if (gdkDpi && *gdkDpi) {
        float s = (float)atof(gdkDpi);
        if (s > 0) scale = fmaxf(scale, s);
    }
    
    /* Check QT_SCALE_FACTOR (Qt/KDE) */
    const char* qt = getenv("QT_SCALE_FACTOR");
    if (qt && *qt) {
        float s = (float)atof(qt);
        if (s > 0) scale = fmaxf(scale, s);
    }
    
    return clamp_scale(scale);
}

static float linux_get_display_scale(uint32_t displayIndex) {
    /* Linux doesn't have great per-monitor scaling detection without X11/Wayland libs */
    /* For now, use environment-based detection */
    (void)displayIndex;
    return linux_get_env_scale();
}

static int linux_get_display_count(void) {
    /* Would need X11/Wayland to properly detect */
    return 1;
}

static float linux_get_primary_scale(void) {
    return linux_get_env_scale();
}

static float linux_get_display_dpi(uint32_t displayIndex) {
    (void)displayIndex;
    return BASE_DPI * linux_get_env_scale();
}

#endif /* Linux */

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Get the number of connected displays
 */
int32_t simjot_get_display_count(void) {
    if (cached_display_count >= 0) {
        return cached_display_count;
    }
    
#ifdef __APPLE__
    cached_display_count = macos_get_display_count();
#elif defined(_WIN32)
    cached_display_count = windows_get_display_count();
#else
    cached_display_count = linux_get_display_count();
#endif
    
    return cached_display_count;
}

/**
 * @brief Get scale factor for a specific display
 * @param displayIndex 0-based display index
 * @return Scale factor (1.0 = 100%, 2.0 = 200%, etc.)
 */
float simjot_get_display_scale(int32_t displayIndex) {
    if (displayIndex < 0 || displayIndex >= MAX_DISPLAYS) {
        return 1.0f;
    }
    
    /* Check cache */
    if (cached_scales[displayIndex] > 0) {
        return cached_scales[displayIndex];
    }
    
#ifdef __APPLE__
    cached_scales[displayIndex] = macos_get_display_scale((uint32_t)displayIndex);
#elif defined(_WIN32)
    cached_scales[displayIndex] = windows_get_display_scale((uint32_t)displayIndex);
#else
    cached_scales[displayIndex] = linux_get_display_scale((uint32_t)displayIndex);
#endif
    
    return cached_scales[displayIndex];
}

/**
 * @brief Get scale factor for the primary display
 */
float simjot_get_primary_display_scale(void) {
    if (cached_primary_scale > 0) {
        return cached_primary_scale;
    }
    
#ifdef __APPLE__
    cached_primary_scale = macos_get_primary_scale();
#elif defined(_WIN32)
    cached_primary_scale = windows_get_primary_scale();
#else
    cached_primary_scale = linux_get_primary_scale();
#endif
    
    return cached_primary_scale;
}

/**
 * @brief Get DPI for a specific display
 * @param displayIndex 0-based display index
 * @return DPI value (typically 96-288)
 */
float simjot_get_display_dpi(int32_t displayIndex) {
    if (displayIndex < 0 || displayIndex >= MAX_DISPLAYS) {
        return BASE_DPI;
    }
    
#ifdef __APPLE__
    return macos_get_display_dpi((uint32_t)displayIndex);
#elif defined(_WIN32)
    return windows_get_display_dpi((uint32_t)displayIndex);
#else
    return linux_get_display_dpi((uint32_t)displayIndex);
#endif
}

/**
 * @brief Invalidate cached scale values (call when displays change)
 */
void simjot_invalidate_display_cache(void) {
    cached_display_count = -1;
    cached_primary_scale = -1.0f;
    for (int i = 0; i < MAX_DISPLAYS; i++) {
        cached_scales[i] = 0;
    }
}

/**
 * @brief Scale a dimension value
 * @param value Base value to scale
 * @param scale Scale factor (or 0 to use primary display scale)
 * @return Scaled and rounded value
 */
int32_t simjot_scale_dimension(int32_t value, float scale) {
    if (scale <= 0) {
        scale = simjot_get_primary_display_scale();
    }
    return (int32_t)roundf((float)value * scale);
}

/**
 * @brief Scale a float value
 * @param value Base value to scale
 * @param scale Scale factor (or 0 to use primary display scale)
 * @return Scaled value
 */
float simjot_scale_value(float value, float scale) {
    if (scale <= 0) {
        scale = simjot_get_primary_display_scale();
    }
    return value * scale;
}

/**
 * @brief Scale a font size with proper rounding for readability
 * Font sizes need special handling to avoid blurry text
 * @param baseSize Base font size in points
 * @param scale Scale factor (or 0 to use primary display scale)
 * @return Scaled font size, rounded to nearest 0.5pt
 */
float simjot_scale_font_size(float baseSize, float scale) {
    if (scale <= 0) {
        scale = simjot_get_primary_display_scale();
    }
    
    float scaled = baseSize * scale;
    
    /* Round to nearest 0.5pt for better rendering */
    scaled = roundf(scaled * 2.0f) / 2.0f;
    
    /* Ensure minimum readable size */
    if (scaled < 8.0f) scaled = 8.0f;
    
    return scaled;
}

/**
 * @brief Scale insets/margins uniformly
 * @param top Top inset
 * @param left Left inset  
 * @param bottom Bottom inset
 * @param right Right inset
 * @param scale Scale factor (or 0 to use primary display scale)
 * @param outInsets Output array [top, left, bottom, right]
 */
void simjot_scale_insets(int32_t top, int32_t left, int32_t bottom, int32_t right,
                         float scale, int32_t* outInsets) {
    if (!outInsets) return;
    
    if (scale <= 0) {
        scale = simjot_get_primary_display_scale();
    }
    
    outInsets[0] = (int32_t)roundf((float)top * scale);
    outInsets[1] = (int32_t)roundf((float)left * scale);
    outInsets[2] = (int32_t)roundf((float)bottom * scale);
    outInsets[3] = (int32_t)roundf((float)right * scale);
}

/**
 * @brief Calculate scaled dimensions maintaining aspect ratio
 * @param width Original width
 * @param height Original height
 * @param scale Scale factor (or 0 to use primary display scale)
 * @param outDimensions Output array [scaledWidth, scaledHeight]
 */
void simjot_scale_dimensions(int32_t width, int32_t height, float scale, int32_t* outDimensions) {
    if (!outDimensions) return;
    
    if (scale <= 0) {
        scale = simjot_get_primary_display_scale();
    }
    
    outDimensions[0] = (int32_t)roundf((float)width * scale);
    outDimensions[1] = (int32_t)roundf((float)height * scale);
}
