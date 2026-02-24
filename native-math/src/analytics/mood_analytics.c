/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native Mood Analytics Engine
 * High-performance mood data processing and statistical analysis
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * DATA STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Single mood sample */
typedef struct {
    int64_t timestamp;      /* Unix timestamp */
    int32_t composite;      /* 0-100 overall mood */
    int32_t joy, calm, gratitude, energy;
    int32_t sadness, anger, anxiety, stress;
} MoodSample;

/* Daily aggregated statistics */
typedef struct {
    int32_t day_offset;     /* Days from start date */
    double average;
    int32_t min, max;
    int32_t sample_count;
    double avg_joy, avg_calm, avg_gratitude, avg_energy;
    double avg_sadness, avg_anger, avg_anxiety, avg_stress;
} DailyStats;

/* Complete analytics result */
typedef struct {
    int32_t day_count;
    double overall_average;
    double volatility;
    int32_t current_streak;
    int32_t longest_good_streak;
    int32_t longest_bad_streak;
    int32_t total_samples;
    DailyStats* daily;      /* Array of day_count elements */
    double* smoothed;       /* Array of day_count smoothed averages */
} AnalyticsResult;

/* ═══════════════════════════════════════════════════════════════════════════
 * CORE ANALYTICS FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute daily averages from mood samples.
 * @param samples Array of mood values (0-100, -1 for missing)
 * @param timestamps Array of timestamps (seconds since epoch)
 * @param count Number of samples
 * @param start_day Start day (days since epoch)
 * @param num_days Number of days to analyze
 * @param out_averages Output array of daily averages (num_days elements, -1 for no data)
 * @param out_counts Output array of sample counts per day
 * @return Number of days with data
 */
int32_t simjot_mood_daily_averages(
    const int32_t* samples, const int64_t* timestamps, int32_t count,
    int32_t start_day, int32_t num_days,
    double* out_averages, int32_t* out_counts
) {
    if (!samples || !timestamps || !out_averages || !out_counts || count <= 0 || num_days <= 0) {
        return 0;
    }
    
    /* Initialize outputs */
    for (int32_t i = 0; i < num_days; i++) {
        out_averages[i] = -1.0;
        out_counts[i] = 0;
    }
    
    /* Temporary sums */
    double* sums = (double*)calloc(num_days, sizeof(double));
    if (!sums) return 0;
    
    int32_t days_with_data = 0;
    
    for (int32_t i = 0; i < count; i++) {
        if (samples[i] < 0 || samples[i] > 100) continue;
        
        int32_t day = (int32_t)(timestamps[i] / 86400);
        int32_t day_idx = day - start_day;
        
        if (day_idx >= 0 && day_idx < num_days) {
            sums[day_idx] += samples[i];
            out_counts[day_idx]++;
        }
    }
    
    for (int32_t i = 0; i < num_days; i++) {
        if (out_counts[i] > 0) {
            out_averages[i] = sums[i] / out_counts[i];
            days_with_data++;
        }
    }
    
    free(sums);
    return days_with_data;
}

/**
 * Compute smoothed (rolling) average.
 * @param values Input values (-1 for missing)
 * @param count Number of values
 * @param window Window size for rolling average
 * @param out_smoothed Output smoothed values
 * @return 1 on success
 */
int32_t simjot_mood_smooth(
    const double* values, int32_t count, int32_t window,
    double* out_smoothed
) {
    if (!values || !out_smoothed || count <= 0 || window <= 0) return 0;
    
    for (int32_t i = 0; i < count; i++) {
        double sum = 0.0;
        int32_t valid = 0;
        
        int32_t start = (i - window + 1 < 0) ? 0 : i - window + 1;
        for (int32_t j = start; j <= i; j++) {
            if (values[j] >= 0) {
                sum += values[j];
                valid++;
            }
        }
        
        out_smoothed[i] = (valid > 0) ? sum / valid : -1.0;
    }
    
    return 1;
}

/**
 * Compute volatility (standard deviation) of mood values.
 * @param values Array of values (-1 for missing)
 * @param count Number of values
 * @return Standard deviation (volatility)
 */
double simjot_mood_volatility(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    /* Compute mean */
    double sum = 0.0;
    int32_t valid = 0;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            sum += values[i];
            valid++;
        }
    }
    
    if (valid <= 1) return 0.0;
    double mean = sum / valid;
    
    /* Compute variance */
    double var_sum = 0.0;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            double diff = values[i] - mean;
            var_sum += diff * diff;
        }
    }
    
    return sqrt(var_sum / (valid - 1));
}

/**
 * Compute mood streaks.
 * @param values Array of daily averages (-1 for missing)
 * @param count Number of days
 * @param threshold Threshold for good/bad (typically 50)
 * @param out_current Current streak (positive=good, negative=bad)
 * @param out_longest_good Longest good streak
 * @param out_longest_bad Longest bad streak
 * @return 1 on success
 */
int32_t simjot_mood_streaks(
    const double* values, int32_t count, double threshold,
    int32_t* out_current, int32_t* out_longest_good, int32_t* out_longest_bad
) {
    if (!values || !out_current || !out_longest_good || !out_longest_bad || count <= 0) {
        return 0;
    }
    
    *out_current = 0;
    *out_longest_good = 0;
    *out_longest_bad = 0;
    
    int32_t good_streak = 0, bad_streak = 0;
    int32_t max_good = 0, max_bad = 0;
    int32_t last_type = 0; /* 1=good, -1=bad, 0=none */
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] < 0) {
            if (good_streak > max_good) max_good = good_streak;
            if (bad_streak > max_bad) max_bad = bad_streak;
            good_streak = 0;
            bad_streak = 0;
            last_type = 0;
            continue;
        }
        
        if (values[i] >= threshold) {
            /* Good day */
            if (last_type == 1) {
                good_streak++;
            } else {
                if (bad_streak > max_bad) max_bad = bad_streak;
                bad_streak = 0;
                good_streak = 1;
            }
            last_type = 1;
            if (good_streak > max_good) max_good = good_streak;
        } else {
            /* Bad day */
            if (last_type == -1) {
                bad_streak++;
            } else {
                if (good_streak > max_good) max_good = good_streak;
                good_streak = 0;
                bad_streak = 1;
            }
            last_type = -1;
            if (bad_streak > max_bad) max_bad = bad_streak;
        }
    }
    
    *out_current = (last_type == 1) ? good_streak : -bad_streak;
    *out_longest_good = max_good;
    *out_longest_bad = max_bad;
    
    return 1;
}

/**
 * Compute overall mood average.
 */
double simjot_mood_average(const double* values, int32_t count) {
    if (!values || count <= 0) return 0.0;
    
    double sum = 0.0;
    int32_t valid = 0;
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            sum += values[i];
            valid++;
        }
    }
    
    return (valid > 0) ? sum / valid : 0.0;
}

/**
 * Complete mood analytics computation.
 * @param mood_values Array of mood samples (0-100)
 * @param timestamps Array of timestamps (unix seconds)
 * @param sample_count Number of samples
 * @param days_back Number of days to analyze (0 = all)
 * @param smoothing_window Smoothing window size
 * @param out_stats Output stats array (14 doubles):
 *   0: day_count, 1: overall_avg, 2: volatility,
 *   3: current_streak, 4: longest_good, 5: longest_bad,
 *   6: total_samples, 7: days_with_data,
 *   8-13: reserved
 * @param out_daily_avgs Output daily averages (day_count doubles)
 * @param out_smoothed Output smoothed averages (day_count doubles)
 * @param max_days Maximum days in output arrays
 * @return Number of days processed
 */
int32_t simjot_mood_analyze(
    const int32_t* mood_values, const int64_t* timestamps, int32_t sample_count,
    int32_t days_back, int32_t smoothing_window,
    double* out_stats, double* out_daily_avgs, double* out_smoothed,
    int32_t max_days
) {
    if (!mood_values || !timestamps || !out_stats || sample_count <= 0 || max_days <= 0) {
        return 0;
    }
    
    /* Find date range */
    int64_t now = 0;
    int64_t min_ts = INT64_MAX, max_ts = 0;
    for (int32_t i = 0; i < sample_count; i++) {
        if (timestamps[i] < min_ts) min_ts = timestamps[i];
        if (timestamps[i] > max_ts) max_ts = timestamps[i];
    }
    now = max_ts;
    
    int32_t today = (int32_t)(now / 86400);
    int32_t start_day;
    int32_t num_days;
    
    if (days_back > 0) {
        start_day = today - days_back + 1;
        num_days = days_back;
    } else {
        start_day = (int32_t)(min_ts / 86400);
        num_days = today - start_day + 1;
    }
    
    if (num_days > max_days) num_days = max_days;
    if (num_days <= 0) return 0;
    
    /* Allocate temp arrays */
    int32_t* counts = (int32_t*)calloc(num_days, sizeof(int32_t));
    if (!counts) return 0;
    
    /* Compute daily averages */
    int32_t days_with_data = simjot_mood_daily_averages(
        mood_values, timestamps, sample_count,
        start_day, num_days,
        out_daily_avgs, counts
    );
    
    /* Compute smoothed */
    if (out_smoothed && smoothing_window > 0) {
        simjot_mood_smooth(out_daily_avgs, num_days, smoothing_window, out_smoothed);
    }
    
    /* Overall average */
    double overall_avg = simjot_mood_average(out_daily_avgs, num_days);
    
    /* Volatility */
    double volatility = simjot_mood_volatility(out_daily_avgs, num_days);
    
    /* Streaks */
    int32_t current_streak, longest_good, longest_bad;
    simjot_mood_streaks(out_daily_avgs, num_days, 50.0,
                        &current_streak, &longest_good, &longest_bad);
    
    /* Total samples */
    int32_t total_samples = 0;
    for (int32_t i = 0; i < num_days; i++) {
        total_samples += counts[i];
    }
    
    free(counts);
    
    /* Output stats */
    out_stats[0] = (double)num_days;
    out_stats[1] = overall_avg;
    out_stats[2] = volatility;
    out_stats[3] = (double)current_streak;
    out_stats[4] = (double)longest_good;
    out_stats[5] = (double)longest_bad;
    out_stats[6] = (double)total_samples;
    out_stats[7] = (double)days_with_data;
    
    return num_days;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TREND ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute mood trend (linear regression slope).
 * @param values Daily averages (-1 for missing)
 * @param count Number of days
 * @return Slope (positive = improving, negative = declining)
 */
double simjot_mood_trend(const double* values, int32_t count) {
    if (!values || count <= 1) return 0.0;
    
    /* Filter valid values */
    double sum_x = 0, sum_y = 0, sum_xy = 0, sum_x2 = 0;
    int32_t n = 0;
    
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            sum_x += i;
            sum_y += values[i];
            sum_xy += i * values[i];
            sum_x2 += i * i;
            n++;
        }
    }
    
    if (n <= 1) return 0.0;
    
    double denom = n * sum_x2 - sum_x * sum_x;
    if (fabs(denom) < 1e-10) return 0.0;
    
    return (n * sum_xy - sum_x * sum_y) / denom;
}

/**
 * Compute weighted recent average (more weight to recent days).
 * @param values Daily averages (-1 for missing)
 * @param count Number of days
 * @param decay Decay factor per day (0.9 typical)
 * @return Weighted average
 */
double simjot_mood_weighted_recent(const double* values, int32_t count, double decay) {
    if (!values || count <= 0 || decay <= 0 || decay > 1) return 0.0;
    
    double weighted_sum = 0.0;
    double weight_sum = 0.0;
    double weight = 1.0;
    
    for (int32_t i = count - 1; i >= 0; i--) {
        if (values[i] >= 0) {
            weighted_sum += values[i] * weight;
            weight_sum += weight;
        }
        weight *= decay;
    }
    
    return (weight_sum > 0) ? weighted_sum / weight_sum : 0.0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * EMOTION CORRELATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute correlation between two emotion arrays.
 */
double simjot_mood_correlation(const int32_t* a, const int32_t* b, int32_t count) {
    if (!a || !b || count <= 1) return 0.0;
    
    /* Mean */
    double sum_a = 0, sum_b = 0;
    int32_t valid = 0;
    for (int32_t i = 0; i < count; i++) {
        if (a[i] >= 0 && b[i] >= 0) {
            sum_a += a[i];
            sum_b += b[i];
            valid++;
        }
    }
    
    if (valid <= 1) return 0.0;
    double mean_a = sum_a / valid;
    double mean_b = sum_b / valid;
    
    /* Correlation */
    double sum_ab = 0, sum_a2 = 0, sum_b2 = 0;
    for (int32_t i = 0; i < count; i++) {
        if (a[i] >= 0 && b[i] >= 0) {
            double da = a[i] - mean_a;
            double db = b[i] - mean_b;
            sum_ab += da * db;
            sum_a2 += da * da;
            sum_b2 += db * db;
        }
    }
    
    double denom = sqrt(sum_a2 * sum_b2);
    if (denom < 1e-10) return 0.0;
    
    return sum_ab / denom;
}

/**
 * Detect anomalies (days significantly different from trend).
 * @param values Daily averages
 * @param count Number of days
 * @param threshold Standard deviations for anomaly
 * @param out_anomalies Output: 1 = anomaly, 0 = normal
 * @return Number of anomalies found
 */
int32_t simjot_mood_anomalies(
    const double* values, int32_t count, double threshold,
    int32_t* out_anomalies
) {
    if (!values || !out_anomalies || count <= 0) return 0;
    
    double mean = simjot_mood_average(values, count);
    double stddev = simjot_mood_volatility(values, count);
    
    if (stddev < 1e-10) {
        memset(out_anomalies, 0, count * sizeof(int32_t));
        return 0;
    }
    
    int32_t anomaly_count = 0;
    for (int32_t i = 0; i < count; i++) {
        if (values[i] >= 0) {
            double z = fabs(values[i] - mean) / stddev;
            out_anomalies[i] = (z > threshold) ? 1 : 0;
            if (out_anomalies[i]) anomaly_count++;
        } else {
            out_anomalies[i] = 0;
        }
    }
    
    return anomaly_count;
}
