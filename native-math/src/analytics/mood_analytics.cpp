/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>

// ═══════════════════════════════════════════════════════════════════════════
// MOOD ANALYTICS - Fast computations for mood data analysis
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

// Note: simjot_mood_smooth, simjot_mood_volatility, and simjot_mood_streaks
// are already defined in other files with const double* signatures.
// This file provides additional mood analytics utilities.

/**
 * Compute daily aggregates from timestamped mood samples.
 * Assumes samples are sorted by timestamp.
 * 
 * @param timestamps Array of timestamps (days since epoch)
 * @param values Array of mood values (0-100)
 * @param count Number of samples
 * @param out_days Output array for unique days
 * @param out_averages Output array for daily averages
 * @param max_days Maximum number of days to output
 * @return Number of unique days found
 */
int32_t simjot_mood_daily_aggregate(const int32_t* timestamps, const int32_t* values,
                                     int32_t count, int32_t* out_days, double* out_averages,
                                     int32_t max_days) {
    if (!timestamps || !values || count <= 0 || !out_days || !out_averages || max_days <= 0) return 0;
    
    int32_t dayCount = 0;
    int32_t currentDay = -1;
    double daySum = 0.0;
    int32_t daySamples = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t day = timestamps[i];
        int32_t val = values[i];
        
        if (val < 0) continue; // Skip invalid
        
        if (day != currentDay) {
            // Save previous day's aggregate
            if (currentDay >= 0 && daySamples > 0 && dayCount < max_days) {
                out_days[dayCount] = currentDay;
                out_averages[dayCount] = daySum / daySamples;
                dayCount++;
            }
            
            // Start new day
            currentDay = day;
            daySum = val;
            daySamples = 1;
        } else {
            daySum += val;
            daySamples++;
        }
    }
    
    // Don't forget last day
    if (currentDay >= 0 && daySamples > 0 && dayCount < max_days) {
        out_days[dayCount] = currentDay;
        out_averages[dayCount] = daySum / daySamples;
        dayCount++;
    }
    
    return dayCount;
}

/**
 * Compute trend slope using simple linear regression.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @return Slope (positive = improving, negative = declining), or NaN if insufficient data
 */
double simjot_mood_trend_slope(const int32_t* values, int32_t count) {
    if (!values || count < 2) return NAN;
    
    // Collect valid points
    std::vector<double> x, y;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            x.push_back(static_cast<double>(i));
            y.push_back(static_cast<double>(values[i]));
        }
    }
    
    int32_t n = static_cast<int32_t>(x.size());
    if (n < 2) return NAN;
    
    // Compute means
    double sumX = 0, sumY = 0;
    for (int32_t i = 0; i < n; i++) {
        sumX += x[i];
        sumY += y[i];
    }
    double meanX = sumX / n;
    double meanY = sumY / n;
    
    // Compute slope: sum((x-meanX)(y-meanY)) / sum((x-meanX)^2)
    double num = 0, denom = 0;
    for (int32_t i = 0; i < n; i++) {
        double dx = x[i] - meanX;
        double dy = y[i] - meanY;
        num += dx * dy;
        denom += dx * dx;
    }
    
    if (std::abs(denom) < 1e-10) return 0.0;
    
    return num / denom;
}

/**
 * Find optimal mood prediction using exponential smoothing.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param alpha Smoothing factor (0-1, higher = more weight on recent)
 * @return Predicted next value, or -1 if insufficient data
 */
double simjot_mood_predict_next(const int32_t* values, int32_t count, double alpha) {
    if (!values || count <= 0 || alpha <= 0 || alpha > 1) return -1.0;
    
    double smoothed = -1.0;
    bool started = false;
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            if (!started) {
                smoothed = values[i];
                started = true;
            } else {
                smoothed = alpha * values[i] + (1 - alpha) * smoothed;
            }
        }
    }
    
    return smoothed;
}

/**
 * Categorize mood distribution into buckets.
 * 
 * @param values Mood values (0-100, -1 for missing)
 * @param count Number of values
 * @param out_buckets Output array of 5 buckets (0-20, 20-40, 40-60, 60-80, 80-100)
 * @return Total number of valid samples
 */
int32_t simjot_mood_distribution(const int32_t* values, int32_t count, int32_t* out_buckets) {
    if (!values || count <= 0 || !out_buckets) return 0;
    
    std::memset(out_buckets, 0, 5 * sizeof(int32_t));
    int32_t total = 0;
    
    for (int32_t i = 0; i < count; i++) {
        int32_t v = values[i];
        if (v < 0) continue;
        
        int bucket = std::min(4, v / 20);
        out_buckets[bucket]++;
        total++;
    }
    
    return total;
}

} // extern "C"
