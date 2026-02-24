/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native Math Utilities
 * High-performance statistical and mathematical functions
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * BASIC STATISTICS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Calculate mean of array.
 */
double simjot_math_mean(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double sum = 0.0;
    for (int32_t i = 0; i < count; i++) {
        sum += values[i];
    }
    return sum / count;
}

/**
 * Calculate mean of int array.
 */
double simjot_math_mean_int(const int32_t* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    int64_t sum = 0;
    for (int32_t i = 0; i < count; i++) {
        sum += values[i];
    }
    return (double)sum / count;
}

/**
 * Calculate variance (population).
 */
double simjot_math_variance(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double mean = simjot_math_mean(values, count);
    double sum_sq = 0.0;
    
    for (int32_t i = 0; i < count; i++) {
        double diff = values[i] - mean;
        sum_sq += diff * diff;
    }
    
    return sum_sq / count;
}

/**
 * Calculate standard deviation (population).
 */
double simjot_math_stddev(const double* values, int32_t count) {
    return sqrt(simjot_math_variance(values, count));
}

/**
 * Calculate min value.
 */
double simjot_math_min(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double min = values[0];
    for (int32_t i = 1; i < count; i++) {
        if (values[i] < min) min = values[i];
    }
    return min;
}

/**
 * Calculate max value.
 */
double simjot_math_max(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double max = values[0];
    for (int32_t i = 1; i < count; i++) {
        if (values[i] > max) max = values[i];
    }
    return max;
}

/**
 * Calculate sum.
 */
double simjot_math_sum(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double sum = 0.0;
    for (int32_t i = 0; i < count; i++) {
        sum += values[i];
    }
    return sum;
}

/**
 * Calculate median (modifies array - sorts in place).
 */
static int compare_double(const void* a, const void* b) {
    double da = *(const double*)a;
    double db = *(const double*)b;
    if (da < db) return -1;
    if (da > db) return 1;
    return 0;
}

double simjot_math_median(double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    qsort(values, count, sizeof(double), compare_double);
    
    if (count % 2 == 0) {
        return (values[count/2 - 1] + values[count/2]) / 2.0;
    }
    return values[count/2];
}

/**
 * Calculate percentile (0-100).
 */
double simjot_math_percentile(double* values, int32_t count, double percentile) {
    if (!values || count <= 0) return 0.0;
    if (percentile <= 0) return simjot_math_min(values, count);
    if (percentile >= 100) return simjot_math_max(values, count);
    
    qsort(values, count, sizeof(double), compare_double);
    
    double idx = (percentile / 100.0) * (count - 1);
    int32_t lower = (int32_t)idx;
    int32_t upper = lower + 1;
    double frac = idx - lower;
    
    if (upper >= count) return values[count - 1];
    
    return values[lower] + frac * (values[upper] - values[lower]);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * MOVING AVERAGES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Simple Moving Average.
 * Computes SMA for each point using window_size previous values.
 * @param out Output array (same size as input)
 * @return 1 on success
 */
int32_t simjot_math_sma(const double* values, int32_t count, int32_t window, double* out) {
    if (!values || !out || count <= 0 || window <= 0) return 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t start = (i - window + 1 < 0) ? 0 : i - window + 1;
        int32_t end = i + 1;
        double sum = 0.0;
        for (int32_t j = start; j < end; j++) {
            sum += values[j];
        }
        out[i] = sum / (end - start);
    }
    
    return 1;
}

/**
 * Exponential Moving Average.
 * @param alpha Smoothing factor (0-1), higher = more weight to recent
 */
int32_t simjot_math_ema(const double* values, int32_t count, double alpha, double* out) {
    if (!values || !out || count <= 0 || alpha <= 0 || alpha > 1) return 0;
    
    out[0] = values[0];
    for (int32_t i = 1; i < count; i++) {
        out[i] = alpha * values[i] + (1 - alpha) * out[i - 1];
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CORRELATION & REGRESSION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Pearson correlation coefficient.
 */
double simjot_math_correlation(const double* x, const double* y, int32_t count) {
    if (!x || !y || count <= 1) return 0.0;
    
    double mean_x = simjot_math_mean(x, count);
    double mean_y = simjot_math_mean(y, count);
    
    double sum_xy = 0.0, sum_x2 = 0.0, sum_y2 = 0.0;
    
    for (int32_t i = 0; i < count; i++) {
        double dx = x[i] - mean_x;
        double dy = y[i] - mean_y;
        sum_xy += dx * dy;
        sum_x2 += dx * dx;
        sum_y2 += dy * dy;
    }
    
    double denom = sqrt(sum_x2 * sum_y2);
    if (denom < DBL_EPSILON) return 0.0;
    
    return sum_xy / denom;
}

/**
 * Linear regression: y = slope * x + intercept.
 * @param out_slope Output slope
 * @param out_intercept Output intercept
 * @return R-squared value
 */
double simjot_math_linear_regression(const double* x, const double* y, int32_t count,
                                      double* out_slope, double* out_intercept) {
    if (!x || !y || count <= 1 || !out_slope || !out_intercept) return 0.0;
    
    double mean_x = simjot_math_mean(x, count);
    double mean_y = simjot_math_mean(y, count);
    
    double sum_xy = 0.0, sum_x2 = 0.0, sum_y2 = 0.0;
    
    for (int32_t i = 0; i < count; i++) {
        double dx = x[i] - mean_x;
        double dy = y[i] - mean_y;
        sum_xy += dx * dy;
        sum_x2 += dx * dx;
        sum_y2 += dy * dy;
    }
    
    if (sum_x2 < DBL_EPSILON) {
        *out_slope = 0.0;
        *out_intercept = mean_y;
        return 0.0;
    }
    
    *out_slope = sum_xy / sum_x2;
    *out_intercept = mean_y - (*out_slope) * mean_x;
    
    // R-squared
    double ss_tot = sum_y2;
    double ss_res = 0.0;
    for (int32_t i = 0; i < count; i++) {
        double pred = (*out_slope) * x[i] + (*out_intercept);
        double err = y[i] - pred;
        ss_res += err * err;
    }
    
    if (ss_tot < DBL_EPSILON) return 1.0;
    return 1.0 - (ss_res / ss_tot);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CLAMPING & NORMALIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Clamp integer to range.
 */
int32_t simjot_math_clamp_int(int32_t value, int32_t min, int32_t max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

/**
 * Clamp double to range (internal helper).
 */
static double util_math_clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

/**
 * Linear interpolation (internal helper).
 */
static double util_math_lerp(double a, double b, double t) {
    return a + t * (b - a);
}

/**
 * Inverse lerp: find t such that lerp(a, b, t) = value.
 */
double simjot_math_inverse_lerp(double a, double b, double value) {
    if (fabs(b - a) < DBL_EPSILON) return 0.0;
    return (value - a) / (b - a);
}

/**
 * Normalize array to 0-1 range.
 */
int32_t simjot_math_normalize(const double* values, int32_t count, double* out) {
    if (!values || !out || count <= 0) return 0;
    
    double min = simjot_math_min(values, count);
    double max = simjot_math_max(values, count);
    double range = max - min;
    
    if (range < DBL_EPSILON) {
        for (int32_t i = 0; i < count; i++) {
            out[i] = 0.5;
        }
    } else {
        for (int32_t i = 0; i < count; i++) {
            out[i] = (values[i] - min) / range;
        }
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * COMPREHENSIVE STATS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute comprehensive statistics for an array.
 * Output format (14 doubles):
 *   0: count, 1: sum, 2: mean, 3: min, 4: max, 5: range,
 *   6: variance, 7: stddev, 8: median, 9: q1, 10: q3, 11: iqr,
 *   12: skewness, 13: kurtosis
 */
int32_t simjot_math_stats(const double* values, int32_t count, double* out, int32_t out_count) {
    if (!values || !out || count <= 0 || out_count < 14) return 0;
    
    // Make a copy for sorting
    double* sorted = (double*)malloc(count * sizeof(double));
    if (!sorted) return 0;
    memcpy(sorted, values, count * sizeof(double));
    qsort(sorted, count, sizeof(double), compare_double);
    
    double sum = simjot_math_sum(values, count);
    double mean = sum / count;
    double min = sorted[0];
    double max = sorted[count - 1];
    double range = max - min;
    
    // Variance and higher moments
    double sum_sq = 0.0, sum_cube = 0.0, sum_quad = 0.0;
    for (int32_t i = 0; i < count; i++) {
        double diff = values[i] - mean;
        sum_sq += diff * diff;
        sum_cube += diff * diff * diff;
        sum_quad += diff * diff * diff * diff;
    }
    
    double variance = sum_sq / count;
    double stddev = sqrt(variance);
    
    // Skewness and kurtosis
    double skewness = 0.0, kurtosis = 0.0;
    if (stddev > DBL_EPSILON) {
        skewness = (sum_cube / count) / (stddev * stddev * stddev);
        kurtosis = (sum_quad / count) / (variance * variance) - 3.0; // Excess kurtosis
    }
    
    // Quartiles
    double median = simjot_math_percentile(sorted, count, 50);
    double q1 = simjot_math_percentile(sorted, count, 25);
    double q3 = simjot_math_percentile(sorted, count, 75);
    double iqr = q3 - q1;
    
    free(sorted);
    
    out[0] = (double)count;
    out[1] = sum;
    out[2] = mean;
    out[3] = min;
    out[4] = max;
    out[5] = range;
    out[6] = variance;
    out[7] = stddev;
    out[8] = median;
    out[9] = q1;
    out[10] = q3;
    out[11] = iqr;
    out[12] = skewness;
    out[13] = kurtosis;
    
    return 14;
}
