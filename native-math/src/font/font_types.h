/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#ifndef SIMJOT_FONT_TYPES_H
#define SIMJOT_FONT_TYPES_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMJOT CUSTOM FONT FORMAT (.sjf) - Version 1
 * 
 * Binary format for storing handwritten/drawn fonts with stroke data.
 * Supports antialiased rendering and smooth stroke capture.
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Format constants */
#define SJF_MAGIC           0x534A4631  /* "SJF1" */
#define SJF_VERSION         1
#define SJF_MAX_GLYPHS      256
#define SJF_MAX_STROKES     64
#define SJF_MAX_POINTS      1024
#define SJF_MAX_NAME_LEN    64
#define SJF_MAX_AUTHOR_LEN  64

/* Default glyph metrics */
#define SJF_DEFAULT_EM_SIZE     1000.0f
#define SJF_DEFAULT_ASCENDER    800.0f
#define SJF_DEFAULT_DESCENDER   200.0f
#define SJF_DEFAULT_LINE_GAP    100.0f

/* Stroke smoothing parameters */
#define SJF_SMOOTH_ITERATIONS   3
#define SJF_RESAMPLE_DISTANCE   2.0f
#define SJF_MIN_STROKE_POINTS   2
#define SJF_PRESSURE_DEFAULT    1.0f

/* Rasterization parameters */
#define SJF_RASTER_OVERSAMPLE   4
#define SJF_RASTER_MAX_SIZE     512
#define SJF_ATLAS_MAX_SIZE      4096
#define SJF_ATLAS_PADDING       2

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE POINT - Single point in a stroke with position and pressure
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_point {
    float x;            /* X coordinate (0.0 to em_size) */
    float y;            /* Y coordinate (0.0 to em_size) */
    float pressure;     /* Pen pressure (0.0 to 1.0) */
    float timestamp;    /* Time offset in ms from stroke start (optional) */
} sjf_point_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE - A single continuous pen stroke
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_stroke {
    sjf_point_t* points;    /* Array of points */
    int32_t point_count;    /* Number of points */
    int32_t capacity;       /* Allocated capacity */
    float thickness;        /* Base stroke thickness (1.0 to 10.0) */
    uint32_t color;         /* Stroke color (ARGB, usually black) */
} sjf_stroke_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH METRICS - Positioning and spacing information
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_metrics {
    float advance_width;    /* Horizontal advance after glyph */
    float left_bearing;     /* Space before glyph starts */
    float right_bearing;    /* Space after glyph ends */
    float bbox_x;           /* Bounding box X origin */
    float bbox_y;           /* Bounding box Y origin */
    float bbox_width;       /* Bounding box width */
    float bbox_height;      /* Bounding box height */
} sjf_metrics_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH - Single character representation with strokes
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_glyph {
    uint32_t codepoint;         /* Unicode codepoint */
    sjf_stroke_t* strokes;      /* Array of strokes */
    int32_t stroke_count;       /* Number of strokes */
    int32_t stroke_capacity;    /* Allocated stroke capacity */
    sjf_metrics_t metrics;      /* Glyph metrics */
    uint8_t defined;            /* 1 if glyph has been drawn */
} sjf_glyph_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * FONT HEADER - File header for .sjf format
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_header {
    uint32_t magic;                     /* SJF_MAGIC */
    uint32_t version;                   /* SJF_VERSION */
    char name[SJF_MAX_NAME_LEN];        /* Font family name */
    char author[SJF_MAX_AUTHOR_LEN];    /* Font author */
    uint32_t glyph_count;               /* Number of defined glyphs */
    uint32_t flags;                     /* Font flags (reserved) */
    float em_size;                      /* Design units per em */
    float ascender;                     /* Ascender height */
    float descender;                    /* Descender depth */
    float line_gap;                     /* Recommended line gap */
    float default_thickness;            /* Default stroke thickness */
    uint64_t created_timestamp;         /* Creation time (unix ms) */
    uint64_t modified_timestamp;        /* Last modified time (unix ms) */
    uint8_t reserved[64];               /* Reserved for future use */
} sjf_header_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * FONT - Complete font with all glyphs
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_font {
    sjf_header_t header;
    sjf_glyph_t* glyphs;        /* Array of glyphs */
    int32_t glyph_capacity;     /* Allocated glyph capacity */
    int32_t glyph_count;        /* Number of defined glyphs */
} sjf_font_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * RASTERIZED GLYPH - Bitmap representation for rendering
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_bitmap {
    uint8_t* pixels;        /* Grayscale pixel data (0-255 alpha) */
    int32_t width;          /* Bitmap width */
    int32_t height;         /* Bitmap height */
    int32_t stride;         /* Bytes per row */
    float origin_x;         /* X offset for rendering */
    float origin_y;         /* Y offset for rendering */
} sjf_bitmap_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * GLYPH ATLAS - Packed texture atlas for efficient rendering
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_atlas_entry {
    uint32_t codepoint;     /* Unicode codepoint */
    int32_t x;              /* X position in atlas */
    int32_t y;              /* Y position in atlas */
    int32_t width;          /* Glyph width in atlas */
    int32_t height;         /* Glyph height in atlas */
    float origin_x;         /* Render offset X */
    float origin_y;         /* Render offset Y */
    float advance;          /* Horizontal advance */
} sjf_atlas_entry_t;

typedef struct sjf_atlas {
    uint8_t* pixels;                /* Atlas pixel data */
    int32_t width;                  /* Atlas width */
    int32_t height;                 /* Atlas height */
    int32_t font_size;              /* Rendered font size */
    sjf_atlas_entry_t* entries;     /* Glyph entries */
    int32_t entry_count;            /* Number of entries */
    int32_t entry_capacity;         /* Allocated entry capacity */
} sjf_atlas_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * SMOOTHING OPTIONS - Parameters for stroke smoothing
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_smooth_opts {
    int32_t iterations;         /* Number of smoothing passes */
    float tension;              /* Spline tension (0.0 to 1.0) */
    float resample_distance;    /* Distance between resampled points */
    int32_t preserve_corners;   /* Preserve sharp corners */
    float corner_threshold;     /* Angle threshold for corners (radians) */
} sjf_smooth_opts_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * RASTERIZATION OPTIONS - Parameters for glyph rasterization
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct sjf_raster_opts {
    int32_t size;               /* Target size in pixels */
    int32_t oversample;         /* Oversampling factor (1-8) */
    float gamma;                /* Gamma correction (1.0 = linear) */
    int32_t subpixel;           /* Enable subpixel rendering */
    int32_t hinting;            /* Enable grid-fitting */
} sjf_raster_opts_t;

/* ═══════════════════════════════════════════════════════════════════════════
 * ERROR CODES
 * ═══════════════════════════════════════════════════════════════════════════ */

#define SJF_OK                  0
#define SJF_ERR_NULL_PTR       -1
#define SJF_ERR_INVALID_MAGIC  -2
#define SJF_ERR_VERSION        -3
#define SJF_ERR_IO             -4
#define SJF_ERR_MEMORY         -5
#define SJF_ERR_OVERFLOW       -6
#define SJF_ERR_INVALID_DATA   -7
#define SJF_ERR_NOT_FOUND      -8
#define SJF_ERR_BUFFER_TOO_SMALL -9

#ifdef __cplusplus
}
#endif

#endif /* SIMJOT_FONT_TYPES_H */
