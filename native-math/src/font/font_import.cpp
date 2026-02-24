/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "font_types.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif
sjf_font_t* sjf_font_create(const char* name, const char* author);
void sjf_font_free(sjf_font_t* font);
sjf_glyph_t* sjf_font_add_glyph(sjf_font_t* font, uint32_t codepoint);
int32_t sjf_glyph_add_stroke(sjf_glyph_t* glyph, const sjf_stroke_t* stroke);
int32_t sjf_glyph_compute_metrics(sjf_glyph_t* glyph, float em_size);
sjf_stroke_t* sjf_stroke_create(int32_t initial_capacity);
void sjf_stroke_free(sjf_stroke_t* stroke);
int32_t sjf_stroke_add_point(sjf_stroke_t* stroke, float x, float y, float pressure, float timestamp);
int32_t sjf_stroke_simplify(sjf_stroke_t* stroke, float epsilon);
#ifdef __cplusplus
}
#endif

#if defined(__APPLE__)
#include <CoreFoundation/CoreFoundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>
#endif

static const char* kFallbackCharset =
    " !\"#$%&'()*+,-./0123456789:;<=>?@"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
    "abcdefghijklmnopqrstuvwxyz{|}~";

static void set_out(int32_t* out, int32_t value) {
    if (out) *out = value;
}

static uint32_t decode_utf8_char(const char* s, size_t len, size_t* index) {
    if (!s || !index || *index >= len) return 0;

    uint8_t c = static_cast<uint8_t>(s[*index]);
    if ((c & 0x80) == 0) {
        (*index)++;
        return c;
    }
    if ((c & 0xE0) == 0xC0 && *index + 1 < len) {
        uint32_t cp = (c & 0x1F) << 6;
        cp |= (static_cast<uint8_t>(s[*index + 1]) & 0x3F);
        *index += 2;
        return cp;
    }
    if ((c & 0xF0) == 0xE0 && *index + 2 < len) {
        uint32_t cp = (c & 0x0F) << 12;
        cp |= (static_cast<uint8_t>(s[*index + 1]) & 0x3F) << 6;
        cp |= (static_cast<uint8_t>(s[*index + 2]) & 0x3F);
        *index += 3;
        return cp;
    }
    if ((c & 0xF8) == 0xF0 && *index + 3 < len) {
        uint32_t cp = (c & 0x07) << 18;
        cp |= (static_cast<uint8_t>(s[*index + 1]) & 0x3F) << 12;
        cp |= (static_cast<uint8_t>(s[*index + 2]) & 0x3F) << 6;
        cp |= (static_cast<uint8_t>(s[*index + 3]) & 0x3F);
        *index += 4;
        return cp;
    }

    (*index)++;
    return 0;
}

static std::vector<uint32_t> decode_utf8(const char* text) {
    std::vector<uint32_t> cps;
    if (!text || !*text) return cps;
    size_t len = std::strlen(text);
    for (size_t i = 0; i < len; ) {
        uint32_t cp = decode_utf8_char(text, len, &i);
        if (cp > 0) cps.push_back(cp);
    }
    return cps;
}

static inline float map_y(float y, float ascender) {
    return ascender - y;
}

static bool points_equal(const sjf_point_t& a, const sjf_point_t& b) {
    return std::fabs(a.x - b.x) < 0.01f && std::fabs(a.y - b.y) < 0.01f;
}

static void truncate_stroke(sjf_stroke_t* stroke, int32_t max_points) {
    if (!stroke || stroke->point_count <= max_points) return;
    int32_t original = stroke->point_count;
    if (max_points < 2) {
        stroke->point_count = 0;
        return;
    }

    std::vector<sjf_point_t> sampled;
    sampled.reserve(max_points);
    float step = static_cast<float>(original - 1) / static_cast<float>(max_points - 1);
    for (int32_t i = 0; i < max_points - 1; i++) {
        int32_t idx = static_cast<int32_t>(i * step);
        if (idx < 0) idx = 0;
        if (idx >= original) idx = original - 1;
        sampled.push_back(stroke->points[idx]);
    }
    sampled.push_back(stroke->points[original - 1]);

    std::memcpy(stroke->points, sampled.data(), sampled.size() * sizeof(sjf_point_t));
    stroke->point_count = static_cast<int32_t>(sampled.size());
}

#if defined(__APPLE__)
struct PathContext {
    std::vector<sjf_stroke_t*> strokes;
    sjf_stroke_t* current = nullptr;
    sjf_point_t start_point{};
    sjf_point_t last_point{};
    float em_size = SJF_DEFAULT_EM_SIZE;
    float ascender = SJF_DEFAULT_ASCENDER;
    float thickness = 2.0f;
    float flatness = 0.5f;
};

static void flatten_quad_bezier(PathContext* ctx, float x0, float y0, float x1, float y1, float x2, float y2, int depth) {
    if (!ctx || !ctx->current || depth > 8) return;
    
    float dx = x2 - x0;
    float dy = y2 - y0;
    float d = std::fabs((x1 - x2) * dy - (y1 - y2) * dx);
    
    if (d * d <= ctx->flatness * ctx->flatness * (dx * dx + dy * dy)) {
        sjf_point_t p{};
        p.x = x2;
        p.y = y2;
        p.pressure = 1.0f;
        if (ctx->current->point_count > 0) {
            sjf_point_t last = ctx->current->points[ctx->current->point_count - 1];
            if (!points_equal(last, p)) {
                sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
            }
        } else {
            sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
        }
        return;
    }
    
    float x01 = (x0 + x1) * 0.5f;
    float y01 = (y0 + y1) * 0.5f;
    float x12 = (x1 + x2) * 0.5f;
    float y12 = (y1 + y2) * 0.5f;
    float x012 = (x01 + x12) * 0.5f;
    float y012 = (y01 + y12) * 0.5f;
    
    flatten_quad_bezier(ctx, x0, y0, x01, y01, x012, y012, depth + 1);
    flatten_quad_bezier(ctx, x012, y012, x12, y12, x2, y2, depth + 1);
}

static void flatten_cubic_bezier(PathContext* ctx, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int depth) {
    if (!ctx || !ctx->current || depth > 8) return;
    
    float dx = x3 - x0;
    float dy = y3 - y0;
    float d1 = std::fabs((x1 - x3) * dy - (y1 - y3) * dx);
    float d2 = std::fabs((x2 - x3) * dy - (y2 - y3) * dx);
    
    float d = d1 + d2;
    if (d * d <= ctx->flatness * ctx->flatness * (dx * dx + dy * dy)) {
        sjf_point_t p{};
        p.x = x3;
        p.y = y3;
        p.pressure = 1.0f;
        if (ctx->current->point_count > 0) {
            sjf_point_t last = ctx->current->points[ctx->current->point_count - 1];
            if (!points_equal(last, p)) {
                sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
            }
        } else {
            sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
        }
        return;
    }
    
    float x01 = (x0 + x1) * 0.5f;
    float y01 = (y0 + y1) * 0.5f;
    float x12 = (x1 + x2) * 0.5f;
    float y12 = (y1 + y2) * 0.5f;
    float x23 = (x2 + x3) * 0.5f;
    float y23 = (y2 + y3) * 0.5f;
    float x012 = (x01 + x12) * 0.5f;
    float y012 = (y01 + y12) * 0.5f;
    float x123 = (x12 + x23) * 0.5f;
    float y123 = (y12 + y23) * 0.5f;
    float x0123 = (x012 + x123) * 0.5f;
    float y0123 = (y012 + y123) * 0.5f;
    
    flatten_cubic_bezier(ctx, x0, y0, x01, y01, x012, y012, x0123, y0123, depth + 1);
    flatten_cubic_bezier(ctx, x0123, y0123, x123, y123, x23, y23, x3, y3, depth + 1);
}

static void finalize_stroke(PathContext* ctx) {
    if (!ctx || !ctx->current) return;
    if (ctx->current->point_count > 1) {
        if (ctx->current->point_count > SJF_MAX_POINTS) {
            sjf_stroke_simplify(ctx->current, ctx->em_size / 100.0f);
        }
        if (ctx->current->point_count > SJF_MAX_POINTS) {
            truncate_stroke(ctx->current, SJF_MAX_POINTS);
        }
        ctx->strokes.push_back(ctx->current);
    } else {
        sjf_stroke_free(ctx->current);
    }
    ctx->current = nullptr;
}

static void path_applier(void* info, const CGPathElement* element) {
    if (!info || !element) return;
    PathContext* ctx = static_cast<PathContext*>(info);

    switch (element->type) {
        case kCGPathElementMoveToPoint: {
            finalize_stroke(ctx);
            ctx->current = sjf_stroke_create(64);
            if (!ctx->current) return;
            ctx->current->thickness = ctx->thickness;
            ctx->current->color = 0xFF000000;
            sjf_point_t p{};
            p.x = element->points[0].x;
            p.y = map_y(element->points[0].y, ctx->ascender);
            p.pressure = 1.0f;
            ctx->start_point = p;
            sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
            ctx->last_point = p;
            break;
        }
        case kCGPathElementAddLineToPoint: {
            if (!ctx->current) return;
            sjf_point_t p{};
            p.x = element->points[0].x;
            p.y = map_y(element->points[0].y, ctx->ascender);
            p.pressure = 1.0f;
            if (ctx->current->point_count > 0) {
                sjf_point_t last = ctx->current->points[ctx->current->point_count - 1];
                if (points_equal(last, p)) return;
            }
            sjf_stroke_add_point(ctx->current, p.x, p.y, p.pressure, 0.0f);
            ctx->last_point = p;
            break;
        }
        case kCGPathElementAddQuadCurveToPoint: {
            if (!ctx->current) return;
            float x0 = ctx->last_point.x;
            float y0 = ctx->last_point.y;
            float x1 = element->points[0].x;
            float y1 = map_y(element->points[0].y, ctx->ascender);
            float x2 = element->points[1].x;
            float y2 = map_y(element->points[1].y, ctx->ascender);
            flatten_quad_bezier(ctx, x0, y0, x1, y1, x2, y2, 0);
            ctx->last_point.x = x2;
            ctx->last_point.y = y2;
            break;
        }
        case kCGPathElementAddCurveToPoint: {
            if (!ctx->current) return;
            float x0 = ctx->last_point.x;
            float y0 = ctx->last_point.y;
            float x1 = element->points[0].x;
            float y1 = map_y(element->points[0].y, ctx->ascender);
            float x2 = element->points[1].x;
            float y2 = map_y(element->points[1].y, ctx->ascender);
            float x3 = element->points[2].x;
            float y3 = map_y(element->points[2].y, ctx->ascender);
            flatten_cubic_bezier(ctx, x0, y0, x1, y1, x2, y2, x3, y3, 0);
            ctx->last_point.x = x3;
            ctx->last_point.y = y3;
            break;
        }
        case kCGPathElementCloseSubpath: {
            if (!ctx->current) return;
            if (ctx->current->point_count > 0) {
                sjf_point_t last = ctx->current->points[ctx->current->point_count - 1];
                if (!points_equal(last, ctx->start_point)) {
                    sjf_stroke_add_point(ctx->current, ctx->start_point.x, ctx->start_point.y, ctx->start_point.pressure, 0.0f);
                }
            }
            finalize_stroke(ctx);
            break;
        }
        default:
            break;
    }
}

static std::vector<sjf_stroke_t*> outline_to_strokes(CGPathRef path, float em_size, float ascender,
                                                     float thickness, float flatness) {
    std::vector<sjf_stroke_t*> strokes;
    if (!path) return strokes;

    PathContext ctx;
    ctx.em_size = em_size;
    ctx.thickness = thickness;
    ctx.flatness = flatness;
    ctx.ascender = ascender;
    CGPathApply(path, &ctx, path_applier);
    finalize_stroke(&ctx);

    strokes = std::move(ctx.strokes);
    return strokes;
}

static std::string basename_no_ext(const char* path) {
    if (!path) return "Imported Font";
    const char* slash = std::strrchr(path, '/');
    const char* back = std::strrchr(path, '\\');
    const char* base = slash ? slash + 1 : path;
    if (back && back + 1 > base) base = back + 1;
    std::string name(base);
    size_t dot = name.find_last_of('.');
    if (dot != std::string::npos) {
        name = name.substr(0, dot);
    }
    if (name.empty()) return "Imported Font";
    return name;
}

static std::string cfstring_to_utf8(CFStringRef str) {
    if (!str) return {};
    CFIndex len = CFStringGetLength(str);
    if (len <= 0) return {};
    CFIndex max = CFStringGetMaximumSizeForEncoding(len, kCFStringEncodingUTF8) + 1;
    std::string out;
    out.resize(static_cast<size_t>(max), '\0');
    if (CFStringGetCString(str, out.data(), max, kCFStringEncodingUTF8)) {
        out.resize(std::strlen(out.c_str()));
        return out;
    }
    return {};
}
#endif

extern "C" sjf_font_t* sjf_font_import(const char* path, const char* charset_utf8, float stroke_thickness,
                                       int32_t* out_error, int32_t* out_total, int32_t* out_defined, int32_t* out_skipped) {
    set_out(out_error, SJF_OK);
    set_out(out_total, 0);
    set_out(out_defined, 0);
    set_out(out_skipped, 0);

    if (!path) {
        set_out(out_error, SJF_ERR_NULL_PTR);
        return nullptr;
    }

#if defined(__APPLE__)
    const char* charset = (charset_utf8 && *charset_utf8) ? charset_utf8 : kFallbackCharset;
    std::vector<uint32_t> codepoints = decode_utf8(charset);
    if (codepoints.empty()) {
        set_out(out_error, SJF_ERR_INVALID_DATA);
        return nullptr;
    }

    CFURLRef url = CFURLCreateFromFileSystemRepresentation(kCFAllocatorDefault,
                                                           reinterpret_cast<const UInt8*>(path),
                                                           std::strlen(path), false);
    if (!url) {
        set_out(out_error, SJF_ERR_IO);
        return nullptr;
    }

    CGDataProviderRef provider = CGDataProviderCreateWithURL(url);
    CFRelease(url);
    if (!provider) {
        set_out(out_error, SJF_ERR_IO);
        return nullptr;
    }

    CGFontRef cgFont = CGFontCreateWithDataProvider(provider);
    CGDataProviderRelease(provider);
    if (!cgFont) {
        set_out(out_error, SJF_ERR_INVALID_DATA);
        return nullptr;
    }

    float em_size = SJF_DEFAULT_EM_SIZE;
    CTFontRef ctFont = CTFontCreateWithGraphicsFont(cgFont, em_size, NULL, NULL);
    CGFontRelease(cgFont);
    if (!ctFont) {
        set_out(out_error, SJF_ERR_INVALID_DATA);
        return nullptr;
    }

    std::string name = basename_no_ext(path);
    CFStringRef fullName = CTFontCopyFullName(ctFont);
    std::string ctName = cfstring_to_utf8(fullName);
    if (fullName) CFRelease(fullName);
    if (!ctName.empty()) {
        name = ctName;
    }

    sjf_font_t* font = sjf_font_create(name.c_str(), "Imported");
    if (!font) {
        CFRelease(ctFont);
        set_out(out_error, SJF_ERR_MEMORY);
        return nullptr;
    }
    font->header.default_thickness = std::max(0.5f, stroke_thickness);
    font->header.em_size = em_size;
    font->header.ascender = CTFontGetAscent(ctFont);
    font->header.descender = CTFontGetDescent(ctFont);
    font->header.line_gap = CTFontGetLeading(ctFont);

    int32_t total = 0;
    int32_t defined = 0;
    int32_t skipped = 0;

    for (uint32_t cp : codepoints) {
        total++;
        if (cp > 0xFFFF) {
            skipped++;
            continue;
        }

        UniChar uc = static_cast<UniChar>(cp);
        CGGlyph glyph = 0;
        bool hasGlyph = CTFontGetGlyphsForCharacters(ctFont, &uc, &glyph, 1);
        if (!hasGlyph || glyph == 0) {
            skipped++;
            continue;
        }

        sjf_glyph_t* outGlyph = sjf_font_add_glyph(font, cp);
        if (!outGlyph) {
            sjf_font_free(font);
            CFRelease(ctFont);
            set_out(out_error, SJF_ERR_MEMORY);
            return nullptr;
        }

        CGSize adv{};
        CTFontGetAdvancesForGlyphs(ctFont, kCTFontOrientationHorizontal, &glyph, &adv, 1);

        CGPathRef pathRef = CTFontCreatePathForGlyph(ctFont, glyph, NULL);
        if (!pathRef) {
            outGlyph->defined = 0;
            outGlyph->metrics.advance_width = static_cast<float>(adv.width);
            outGlyph->metrics.left_bearing = 0.0f;
            outGlyph->metrics.right_bearing = 0.0f;
            outGlyph->metrics.bbox_x = 0.0f;
            outGlyph->metrics.bbox_y = 0.0f;
            outGlyph->metrics.bbox_width = 0.0f;
            outGlyph->metrics.bbox_height = 0.0f;
            continue;
        }

        float flatness = em_size / 50.0f;
        std::vector<sjf_stroke_t*> strokes = outline_to_strokes(pathRef, em_size,
                                                                font->header.ascender,
                                                                font->header.default_thickness, flatness);
        if (strokes.size() > SJF_MAX_STROKES) {
            for (size_t i = SJF_MAX_STROKES; i < strokes.size(); i++) {
                sjf_stroke_free(strokes[i]);
            }
            strokes.resize(SJF_MAX_STROKES);
        }

        bool glyph_ok = true;
        for (sjf_stroke_t* stroke : strokes) {
            if (stroke && stroke->point_count > 1 && glyph_ok) {
                int32_t add_ok = sjf_glyph_add_stroke(outGlyph, stroke);
                if (add_ok != SJF_OK) {
                    glyph_ok = false;
                }
            }
            sjf_stroke_free(stroke);
        }

        if (!glyph_ok) {
            CGPathRelease(pathRef);
            sjf_font_free(font);
            CFRelease(ctFont);
            set_out(out_error, SJF_ERR_MEMORY);
            return nullptr;
        }

        if (outGlyph->stroke_count > 0) {
            outGlyph->defined = 1;
            sjf_glyph_compute_metrics(outGlyph, em_size);
            if (adv.width > 0.0f) {
                float right_edge = outGlyph->metrics.bbox_x + outGlyph->metrics.bbox_width;
                outGlyph->metrics.advance_width = static_cast<float>(adv.width);
                outGlyph->metrics.left_bearing = outGlyph->metrics.bbox_x;
                outGlyph->metrics.right_bearing = static_cast<float>(adv.width) - right_edge;
            }
            defined++;
        }

        CGPathRelease(pathRef);
    }

    CFRelease(ctFont);

    set_out(out_total, total);
    set_out(out_defined, defined);
    set_out(out_skipped, skipped);
    return font;
#else
    set_out(out_error, SJF_ERR_NOT_FOUND);
    return nullptr;
#endif
}
