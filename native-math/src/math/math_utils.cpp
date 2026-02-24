/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file math_utils.cpp
 * @brief High-Performance Math & Graphics Utilities for Simjot
 * 
 * C++ implementation of mathematical operations, geometric calculations,
 * and graphics utilities optimized for performance.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

namespace {

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS
 * ═══════════════════════════════════════════════════════════════════════════ */

constexpr double PI = 3.14159265358979323846;
constexpr double TWO_PI = 2.0 * PI;
constexpr double HALF_PI = PI / 2.0;
constexpr double DEG_TO_RAD = PI / 180.0;
constexpr double RAD_TO_DEG = 180.0 / PI;

/* ═══════════════════════════════════════════════════════════════════════════
 * 2D VECTOR OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

struct Vec2 {
    double x, y;
    
    Vec2() : x(0), y(0) {}
    Vec2(double x_, double y_) : x(x_), y(y_) {}
    
    Vec2 operator+(const Vec2& o) const { return {x + o.x, y + o.y}; }
    Vec2 operator-(const Vec2& o) const { return {x - o.x, y - o.y}; }
    Vec2 operator*(double s) const { return {x * s, y * s}; }
    Vec2 operator/(double s) const { return {x / s, y / s}; }
    
    double dot(const Vec2& o) const { return x * o.x + y * o.y; }
    double cross(const Vec2& o) const { return x * o.y - y * o.x; }
    double length() const { return std::sqrt(x * x + y * y); }
    double lengthSq() const { return x * x + y * y; }
    
    Vec2 normalized() const {
        double len = length();
        return len > 0 ? *this / len : Vec2{};
    }
    
    Vec2 rotated(double angle) const {
        double c = std::cos(angle);
        double s = std::sin(angle);
        return {x * c - y * s, x * s + y * c};
    }
    
    Vec2 perpendicular() const { return {-y, x}; }
};

/* ═══════════════════════════════════════════════════════════════════════════
 * BEZIER CURVES
 * ═══════════════════════════════════════════════════════════════════════════ */

Vec2 bezier_quadratic(const Vec2& p0, const Vec2& p1, const Vec2& p2, double t) {
    double u = 1.0 - t;
    return p0 * (u * u) + p1 * (2.0 * u * t) + p2 * (t * t);
}

Vec2 bezier_cubic(const Vec2& p0, const Vec2& p1, const Vec2& p2, const Vec2& p3, double t) {
    double u = 1.0 - t;
    double uu = u * u;
    double tt = t * t;
    return p0 * (uu * u) + p1 * (3.0 * uu * t) + p2 * (3.0 * u * tt) + p3 * (tt * t);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * EASING FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

double ease_linear(double t) { return t; }

double ease_in_quad(double t) { return t * t; }
double ease_out_quad(double t) { return t * (2.0 - t); }
double ease_in_out_quad(double t) {
    return t < 0.5 ? 2.0 * t * t : -1.0 + (4.0 - 2.0 * t) * t;
}

double ease_in_cubic(double t) { return t * t * t; }
double ease_out_cubic(double t) { double u = t - 1.0; return u * u * u + 1.0; }
double ease_in_out_cubic(double t) {
    return t < 0.5 ? 4.0 * t * t * t : (t - 1.0) * (2.0 * t - 2.0) * (2.0 * t - 2.0) + 1.0;
}

double ease_in_sine(double t) { return 1.0 - std::cos(t * HALF_PI); }
double ease_out_sine(double t) { return std::sin(t * HALF_PI); }
double ease_in_out_sine(double t) { return 0.5 * (1.0 - std::cos(PI * t)); }

double ease_in_expo(double t) { return t == 0.0 ? 0.0 : std::pow(2.0, 10.0 * (t - 1.0)); }
double ease_out_expo(double t) { return t == 1.0 ? 1.0 : 1.0 - std::pow(2.0, -10.0 * t); }

double ease_in_elastic(double t) {
    if (t == 0.0 || t == 1.0) return t;
    return -std::pow(2.0, 10.0 * t - 10.0) * std::sin((t * 10.0 - 10.75) * TWO_PI / 3.0);
}

double ease_out_elastic(double t) {
    if (t == 0.0 || t == 1.0) return t;
    return std::pow(2.0, -10.0 * t) * std::sin((t * 10.0 - 0.75) * TWO_PI / 3.0) + 1.0;
}

double ease_in_bounce(double t);
double ease_out_bounce(double t) {
    if (t < 1.0 / 2.75) return 7.5625 * t * t;
    if (t < 2.0 / 2.75) { t -= 1.5 / 2.75; return 7.5625 * t * t + 0.75; }
    if (t < 2.5 / 2.75) { t -= 2.25 / 2.75; return 7.5625 * t * t + 0.9375; }
    t -= 2.625 / 2.75;
    return 7.5625 * t * t + 0.984375;
}
double ease_in_bounce(double t) { return 1.0 - ease_out_bounce(1.0 - t); }

/* ═══════════════════════════════════════════════════════════════════════════
 * COLOR UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

struct Color {
    uint8_t r, g, b, a;
    
    Color() : r(0), g(0), b(0), a(255) {}
    Color(uint8_t r_, uint8_t g_, uint8_t b_, uint8_t a_ = 255) 
        : r(r_), g(g_), b(b_), a(a_) {}
    
    static Color from_argb(uint32_t argb) {
        return Color(
            (argb >> 16) & 0xFF,
            (argb >> 8) & 0xFF,
            argb & 0xFF,
            (argb >> 24) & 0xFF
        );
    }
    
    uint32_t to_argb() const {
        return (static_cast<uint32_t>(a) << 24) |
               (static_cast<uint32_t>(r) << 16) |
               (static_cast<uint32_t>(g) << 8) |
               static_cast<uint32_t>(b);
    }
    
    Color blend(const Color& other, double t) const {
        return Color(
            static_cast<uint8_t>(r + (other.r - r) * t),
            static_cast<uint8_t>(g + (other.g - g) * t),
            static_cast<uint8_t>(b + (other.b - b) * t),
            static_cast<uint8_t>(a + (other.a - a) * t)
        );
    }
};

/* HSL to RGB conversion */
Color hsl_to_rgb(double h, double s, double l) {
    auto hue_to_rgb = [](double p, double q, double t) {
        if (t < 0.0) t += 1.0;
        if (t > 1.0) t -= 1.0;
        if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;
        if (t < 0.5) return q;
        if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;
        return p;
    };
    
    if (s == 0.0) {
        uint8_t v = static_cast<uint8_t>(l * 255.0);
        return Color(v, v, v);
    }
    
    double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    double p = 2.0 * l - q;
    
    return Color(
        static_cast<uint8_t>(hue_to_rgb(p, q, h + 1.0/3.0) * 255.0),
        static_cast<uint8_t>(hue_to_rgb(p, q, h) * 255.0),
        static_cast<uint8_t>(hue_to_rgb(p, q, h - 1.0/3.0) * 255.0)
    );
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STATISTICAL FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

struct Stats {
    double min, max, sum, mean, variance, stddev, median;
    int count;
};

Stats compute_stats(const double* data, int count) {
    Stats s{};
    if (!data || count <= 0) return s;
    
    s.count = count;
    s.min = data[0];
    s.max = data[0];
    s.sum = 0.0;
    
    for (int i = 0; i < count; i++) {
        s.sum += data[i];
        if (data[i] < s.min) s.min = data[i];
        if (data[i] > s.max) s.max = data[i];
    }
    
    s.mean = s.sum / count;
    
    double var_sum = 0.0;
    for (int i = 0; i < count; i++) {
        double diff = data[i] - s.mean;
        var_sum += diff * diff;
    }
    s.variance = var_sum / count;
    s.stddev = std::sqrt(s.variance);
    
    /* Median (requires sorting a copy) */
    std::vector<double> sorted(data, data + count);
    std::sort(sorted.begin(), sorted.end());
    if (count % 2 == 0) {
        s.median = (sorted[count/2 - 1] + sorted[count/2]) / 2.0;
    } else {
        s.median = sorted[count/2];
    }
    
    return s;
}

static Stats g_stats;

} /* anonymous namespace */

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - VECTOR OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

double simjot_math_vec2_length(double x, double y) {
    return std::sqrt(x * x + y * y);
}

double simjot_math_vec2_dot(double x1, double y1, double x2, double y2) {
    return x1 * x2 + y1 * y2;
}

double simjot_math_vec2_cross(double x1, double y1, double x2, double y2) {
    return x1 * y2 - y1 * x2;
}

void simjot_math_vec2_normalize(double x, double y, double* out_x, double* out_y) {
    double len = std::sqrt(x * x + y * y);
    if (len > 0 && out_x && out_y) {
        *out_x = x / len;
        *out_y = y / len;
    }
}

void simjot_math_vec2_rotate(double x, double y, double angle_rad, double* out_x, double* out_y) {
    if (!out_x || !out_y) return;
    double c = std::cos(angle_rad);
    double s = std::sin(angle_rad);
    *out_x = x * c - y * s;
    *out_y = x * s + y * c;
}

double simjot_math_vec2_angle(double x, double y) {
    return std::atan2(y, x);
}

double simjot_math_vec2_distance(double x1, double y1, double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    return std::sqrt(dx * dx + dy * dy);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - BEZIER CURVES
 * ═══════════════════════════════════════════════════════════════════════════ */

void simjot_math_bezier_quad(double p0x, double p0y, double p1x, double p1y,
                             double p2x, double p2y, double t,
                             double* out_x, double* out_y) {
    if (!out_x || !out_y) return;
    Vec2 result = bezier_quadratic({p0x, p0y}, {p1x, p1y}, {p2x, p2y}, t);
    *out_x = result.x;
    *out_y = result.y;
}

void simjot_math_bezier_cubic(double p0x, double p0y, double p1x, double p1y,
                              double p2x, double p2y, double p3x, double p3y,
                              double t, double* out_x, double* out_y) {
    if (!out_x || !out_y) return;
    Vec2 result = bezier_cubic({p0x, p0y}, {p1x, p1y}, {p2x, p2y}, {p3x, p3y}, t);
    *out_x = result.x;
    *out_y = result.y;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - EASING FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

double simjot_math_ease(int32_t type, double t) {
    t = std::clamp(t, 0.0, 1.0);
    
    switch (type) {
        case 0: return ease_linear(t);
        case 1: return ease_in_quad(t);
        case 2: return ease_out_quad(t);
        case 3: return ease_in_out_quad(t);
        case 4: return ease_in_cubic(t);
        case 5: return ease_out_cubic(t);
        case 6: return ease_in_out_cubic(t);
        case 7: return ease_in_sine(t);
        case 8: return ease_out_sine(t);
        case 9: return ease_in_out_sine(t);
        case 10: return ease_in_expo(t);
        case 11: return ease_out_expo(t);
        case 12: return ease_in_elastic(t);
        case 13: return ease_out_elastic(t);
        case 14: return ease_in_bounce(t);
        case 15: return ease_out_bounce(t);
        default: return t;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - COLOR UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

uint32_t simjot_math_color_blend(uint32_t color1, uint32_t color2, double t) {
    Color c1 = Color::from_argb(color1);
    Color c2 = Color::from_argb(color2);
    return c1.blend(c2, std::clamp(t, 0.0, 1.0)).to_argb();
}

uint32_t simjot_math_hsl_to_rgb(double h, double s, double l) {
    return hsl_to_rgb(h, s, l).to_argb();
}

void simjot_math_rgb_to_hsl(uint32_t argb, double* h, double* s, double* l) {
    Color c = Color::from_argb(argb);
    double r = c.r / 255.0;
    double g = c.g / 255.0;
    double b = c.b / 255.0;
    
    double max_c = std::max({r, g, b});
    double min_c = std::min({r, g, b});
    double lum = (max_c + min_c) / 2.0;
    
    if (l) *l = lum;
    
    if (max_c == min_c) {
        if (h) *h = 0.0;
        if (s) *s = 0.0;
    } else {
        double d = max_c - min_c;
        if (s) *s = lum > 0.5 ? d / (2.0 - max_c - min_c) : d / (max_c + min_c);
        
        double hue = 0.0;
        if (max_c == r) hue = (g - b) / d + (g < b ? 6.0 : 0.0);
        else if (max_c == g) hue = (b - r) / d + 2.0;
        else hue = (r - g) / d + 4.0;
        
        if (h) *h = hue / 6.0;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - STATISTICS
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_math_compute_stats(const double* data, int32_t count) {
    if (!data || count <= 0) return 0;
    g_stats = compute_stats(data, count);
    return 1;
}

double simjot_math_get_stat(int32_t which) {
    switch (which) {
        case 0: return g_stats.min;
        case 1: return g_stats.max;
        case 2: return g_stats.sum;
        case 3: return g_stats.mean;
        case 4: return g_stats.variance;
        case 5: return g_stats.stddev;
        case 6: return g_stats.median;
        case 7: return static_cast<double>(g_stats.count);
        default: return 0.0;
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - TRIGONOMETRY
 * ═══════════════════════════════════════════════════════════════════════════ */

double simjot_math_deg_to_rad(double degrees) {
    return degrees * DEG_TO_RAD;
}

double simjot_math_rad_to_deg(double radians) {
    return radians * RAD_TO_DEG;
}

double simjot_math_normalize_angle(double radians) {
    while (radians < 0) radians += TWO_PI;
    while (radians >= TWO_PI) radians -= TWO_PI;
    return radians;
}

double simjot_math_lerp(double a, double b, double t) {
    return a + (b - a) * t;
}

double simjot_math_clamp(double value, double min_val, double max_val) {
    return std::clamp(value, min_val, max_val);
}

double simjot_math_map_range(double value, double in_min, double in_max, 
                             double out_min, double out_max) {
    return out_min + (value - in_min) * (out_max - out_min) / (in_max - in_min);
}

} /* extern "C" */
