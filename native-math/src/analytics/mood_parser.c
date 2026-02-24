/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Native Mood Log Parser for Simjot.
 * Fast parsing of mood_log.txt with daily aggregation.
 * 
 * Format: timestamp,composite[,joy,calm,gratitude,energy,sadness,anger,anxiety,stress]
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <ctype.h>
#include <math.h>

/* Maximum samples we can hold */
#define MAX_MOOD_SAMPLES 100000
#define MAX_DAILY_STATS 3650  /* 10 years of daily data */

/* ═══════════════════════════════════════════════════════════════════════════
 * DATA STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    int64_t timestamp_ms;  /* Milliseconds since epoch */
    int16_t composite;     /* 0-100 */
    int16_t joy;           /* -1 if not present */
    int16_t calm;
    int16_t gratitude;
    int16_t energy;
    int16_t sadness;
    int16_t anger;
    int16_t anxiety;
    int16_t stress;
} MoodSample;

typedef struct {
    int32_t date_days;     /* Days since epoch (1970-01-01) */
    int32_t sample_count;
    double average;
    int16_t min;
    int16_t max;
    double avg_joy, avg_calm, avg_gratitude, avg_energy;
    double avg_sadness, avg_anger, avg_anxiety, avg_stress;
} DailyStats;

typedef struct {
    double overall_average;
    double volatility;
    int32_t current_streak;
    int32_t longest_good_streak;
    int32_t longest_bad_streak;
    int32_t total_samples;
    int32_t total_days;
} AnalyticsSummary;

/* Global storage */
static MoodSample g_samples[MAX_MOOD_SAMPLES];
static int32_t g_sample_count = 0;
static DailyStats g_daily[MAX_DAILY_STATS];
static int32_t g_daily_count = 0;
static AnalyticsSummary g_summary;

/* ═══════════════════════════════════════════════════════════════════════════
 * TIMESTAMP PARSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Parse timestamp in various formats, return ms since epoch or -1 on failure */
static int64_t parse_timestamp(const char* s) {
    if (!s || !*s) return -1;
    
    struct tm tm = {0};
    int year, month, day, hour = 0, min = 0, sec = 0;
    
    /* Try yyyyMMdd_HHmmss format */
    if (sscanf(s, "%4d%2d%2d_%2d%2d%2d", &year, &month, &day, &hour, &min, &sec) >= 3) {
        tm.tm_year = year - 1900;
        tm.tm_mon = month - 1;
        tm.tm_mday = day;
        tm.tm_hour = hour;
        tm.tm_min = min;
        tm.tm_sec = sec;
        time_t t = mktime(&tm);
        if (t != -1) return (int64_t)t * 1000;
    }
    
    /* Try yyyy-MM-dd'T'HH:mm:ss or yyyy-MM-dd HH:mm:ss */
    if (sscanf(s, "%4d-%2d-%2d%*c%2d:%2d:%2d", &year, &month, &day, &hour, &min, &sec) >= 3) {
        tm.tm_year = year - 1900;
        tm.tm_mon = month - 1;
        tm.tm_mday = day;
        tm.tm_hour = hour;
        tm.tm_min = min;
        tm.tm_sec = sec;
        time_t t = mktime(&tm);
        if (t != -1) return (int64_t)t * 1000;
    }
    
    /* Try yyyy-MM-dd (date only) */
    if (sscanf(s, "%4d-%2d-%2d", &year, &month, &day) == 3) {
        tm.tm_year = year - 1900;
        tm.tm_mon = month - 1;
        tm.tm_mday = day;
        time_t t = mktime(&tm);
        if (t != -1) return (int64_t)t * 1000;
    }
    
    return -1;
}

/* Convert ms to days since epoch */
static int32_t ms_to_days(int64_t ms) {
    return (int32_t)(ms / (1000LL * 60 * 60 * 24));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LINE PARSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Parse mood value, handle emoji */
static int16_t parse_mood_value(const char* s) {
    if (!s) return 50;
    
    /* Skip whitespace */
    while (*s && isspace((unsigned char)*s)) s++;
    
    /* Check for emoji/text values */
    if (strstr(s, ":)") || strstr(s, "😊") || strstr(s, "😀")) return 100;
    if (strstr(s, ":/") || strstr(s, "😐")) return 50;
    if (strstr(s, ":(") || strstr(s, "😢") || strstr(s, "😞")) return 0;
    
    /* Parse as integer */
    int val = atoi(s);
    if (val < 0) val = 0;
    if (val > 100) val = 100;
    return (int16_t)val;
}

/* Parse a single line of the mood log */
static int parse_line(const char* line, MoodSample* out) {
    if (!line || !out) return 0;
    
    /* Skip empty lines */
    while (*line && isspace((unsigned char)*line)) line++;
    if (!*line) return 0;
    
    /* Split by comma */
    char buf[512];
    strncpy(buf, line, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';
    
    char* fields[12];
    int field_count = 0;
    char* p = buf;
    
    while (*p && field_count < 12) {
        fields[field_count++] = p;
        while (*p && *p != ',') p++;
        if (*p == ',') *p++ = '\0';
    }
    
    if (field_count < 2) return 0;
    
    /* Parse timestamp */
    int64_t ts = parse_timestamp(fields[0]);
    if (ts < 0) return 0;
    
    out->timestamp_ms = ts;
    out->composite = parse_mood_value(fields[1]);
    
    /* Parse detailed emotions if present */
    if (field_count >= 10) {
        out->joy = (int16_t)atoi(fields[2]);
        out->calm = (int16_t)atoi(fields[3]);
        out->gratitude = (int16_t)atoi(fields[4]);
        out->energy = (int16_t)atoi(fields[5]);
        out->sadness = (int16_t)atoi(fields[6]);
        out->anger = (int16_t)atoi(fields[7]);
        out->anxiety = (int16_t)atoi(fields[8]);
        out->stress = (int16_t)atoi(fields[9]);
    } else {
        out->joy = out->calm = out->gratitude = out->energy = -1;
        out->sadness = out->anger = out->anxiety = out->stress = -1;
    }
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Load and parse mood log file.
 * @return Number of samples parsed, or negative on error
 */
int32_t simjot_mood_load(const char* file_path) {
    if (!file_path) return -1;
    
    FILE* f = fopen(file_path, "r");
    if (!f) return -2;
    
    g_sample_count = 0;
    char line[1024];
    
    while (fgets(line, sizeof(line), f) && g_sample_count < MAX_MOOD_SAMPLES) {
        MoodSample sample;
        if (parse_line(line, &sample)) {
            g_samples[g_sample_count++] = sample;
        }
    }
    
    fclose(f);
    return g_sample_count;
}

/**
 * Compute daily statistics from loaded samples.
 * @param days_back Number of days to analyze (0 = all time)
 * @return Number of days with data
 */
int32_t simjot_mood_compute_daily(int32_t days_back) {
    if (g_sample_count == 0) return 0;
    
    /* Get current time and compute date range */
    time_t now = time(NULL);
    int32_t today_days = (int32_t)(now / (60 * 60 * 24));
    int32_t start_days = days_back > 0 ? today_days - days_back + 1 : 0;
    
    /* Find actual earliest sample if days_back == 0 */
    if (days_back == 0) {
        start_days = today_days;
        for (int32_t i = 0; i < g_sample_count; i++) {
            int32_t d = ms_to_days(g_samples[i].timestamp_ms);
            if (d < start_days) start_days = d;
        }
    }
    
    /* Clear daily stats */
    g_daily_count = 0;
    
    /* Group samples by day and compute stats */
    int32_t current_day = -1;
    int64_t sum = 0;
    int16_t min_v = 100, max_v = 0;
    int32_t count = 0;
    double joy_sum = 0, calm_sum = 0, grat_sum = 0, energy_sum = 0;
    double sad_sum = 0, anger_sum = 0, anx_sum = 0, stress_sum = 0;
    int32_t detail_count = 0;
    
    /* Sort samples by timestamp (simple insertion sort for stability) */
    for (int32_t i = 1; i < g_sample_count; i++) {
        MoodSample key = g_samples[i];
        int32_t j = i - 1;
        while (j >= 0 && g_samples[j].timestamp_ms > key.timestamp_ms) {
            g_samples[j + 1] = g_samples[j];
            j--;
        }
        g_samples[j + 1] = key;
    }
    
    for (int32_t i = 0; i < g_sample_count && g_daily_count < MAX_DAILY_STATS; i++) {
        int32_t sample_day = ms_to_days(g_samples[i].timestamp_ms);
        
        if (sample_day < start_days || sample_day > today_days) continue;
        
        if (sample_day != current_day) {
            /* Save previous day's stats */
            if (current_day >= 0 && count > 0) {
                DailyStats* ds = &g_daily[g_daily_count++];
                ds->date_days = current_day;
                ds->sample_count = count;
                ds->average = (double)sum / count;
                ds->min = min_v;
                ds->max = max_v;
                
                if (detail_count > 0) {
                    ds->avg_joy = joy_sum / detail_count;
                    ds->avg_calm = calm_sum / detail_count;
                    ds->avg_gratitude = grat_sum / detail_count;
                    ds->avg_energy = energy_sum / detail_count;
                    ds->avg_sadness = sad_sum / detail_count;
                    ds->avg_anger = anger_sum / detail_count;
                    ds->avg_anxiety = anx_sum / detail_count;
                    ds->avg_stress = stress_sum / detail_count;
                } else {
                    ds->avg_joy = ds->avg_calm = ds->avg_gratitude = ds->avg_energy = -1;
                    ds->avg_sadness = ds->avg_anger = ds->avg_anxiety = ds->avg_stress = -1;
                }
            }
            
            /* Reset for new day */
            current_day = sample_day;
            sum = 0; count = 0; min_v = 100; max_v = 0;
            joy_sum = calm_sum = grat_sum = energy_sum = 0;
            sad_sum = anger_sum = anx_sum = stress_sum = 0;
            detail_count = 0;
        }
        
        /* Accumulate sample */
        MoodSample* s = &g_samples[i];
        sum += s->composite;
        count++;
        if (s->composite < min_v) min_v = s->composite;
        if (s->composite > max_v) max_v = s->composite;
        
        if (s->joy >= 0) {
            joy_sum += s->joy;
            calm_sum += s->calm;
            grat_sum += s->gratitude;
            energy_sum += s->energy;
            sad_sum += s->sadness;
            anger_sum += s->anger;
            anx_sum += s->anxiety;
            stress_sum += s->stress;
            detail_count++;
        }
    }
    
    /* Save last day */
    if (current_day >= 0 && count > 0 && g_daily_count < MAX_DAILY_STATS) {
        DailyStats* ds = &g_daily[g_daily_count++];
        ds->date_days = current_day;
        ds->sample_count = count;
        ds->average = (double)sum / count;
        ds->min = min_v;
        ds->max = max_v;
        
        if (detail_count > 0) {
            ds->avg_joy = joy_sum / detail_count;
            ds->avg_calm = calm_sum / detail_count;
            ds->avg_gratitude = grat_sum / detail_count;
            ds->avg_energy = energy_sum / detail_count;
            ds->avg_sadness = sad_sum / detail_count;
            ds->avg_anger = anger_sum / detail_count;
            ds->avg_anxiety = anx_sum / detail_count;
            ds->avg_stress = stress_sum / detail_count;
        } else {
            ds->avg_joy = ds->avg_calm = ds->avg_gratitude = ds->avg_energy = -1;
            ds->avg_sadness = ds->avg_anger = ds->avg_anxiety = ds->avg_stress = -1;
        }
    }
    
    return g_daily_count;
}

/**
 * Compute analytics summary (volatility, streaks, overall average).
 * Must call simjot_mood_compute_daily first.
 * @param threshold Good/bad threshold (typically 60)
 */
int32_t simjot_mood_compute_summary(int32_t threshold) {
    if (g_daily_count == 0) {
        memset(&g_summary, 0, sizeof(g_summary));
        return 0;
    }
    
    /* Compute overall average */
    double sum = 0;
    int32_t total_samples = 0;
    
    for (int32_t i = 0; i < g_daily_count; i++) {
        sum += g_daily[i].average * g_daily[i].sample_count;
        total_samples += g_daily[i].sample_count;
    }
    
    g_summary.overall_average = total_samples > 0 ? sum / total_samples : 0;
    g_summary.total_samples = total_samples;
    g_summary.total_days = g_daily_count;
    
    /* Compute volatility (std deviation of daily averages) */
    double mean = 0;
    for (int32_t i = 0; i < g_daily_count; i++) {
        mean += g_daily[i].average;
    }
    mean /= g_daily_count;
    
    double sum_sq = 0;
    for (int32_t i = 0; i < g_daily_count; i++) {
        double diff = g_daily[i].average - mean;
        sum_sq += diff * diff;
    }
    g_summary.volatility = g_daily_count > 1 ? sqrt(sum_sq / (g_daily_count - 1)) : 0;
    
    /* Compute streaks */
    int32_t running_good = 0, running_bad = 0;
    int32_t longest_good = 0, longest_bad = 0;
    
    for (int32_t i = 0; i < g_daily_count; i++) {
        if (g_daily[i].average >= threshold) {
            running_good++;
            if (running_bad > longest_bad) longest_bad = running_bad;
            running_bad = 0;
        } else {
            running_bad++;
            if (running_good > longest_good) longest_good = running_good;
            running_good = 0;
        }
    }
    
    if (running_good > longest_good) longest_good = running_good;
    if (running_bad > longest_bad) longest_bad = running_bad;
    
    g_summary.longest_good_streak = longest_good;
    g_summary.longest_bad_streak = longest_bad;
    
    /* Current streak (from last day) */
    if (g_daily_count > 0) {
        double last_avg = g_daily[g_daily_count - 1].average;
        g_summary.current_streak = last_avg >= threshold ? running_good : -running_bad;
    }
    
    return 1;
}

/**
 * Get daily statistics by index.
 * Output format (binary):
 *   int32: date_days
 *   int32: sample_count
 *   double: average
 *   int16: min
 *   int16: max
 *   double[8]: avg emotions (joy,calm,gratitude,energy,sadness,anger,anxiety,stress)
 * @return Bytes written, or negative on error
 */
int32_t simjot_mood_get_daily(int32_t index, uint8_t* out, int32_t out_len) {
    if (index < 0 || index >= g_daily_count || !out || out_len < 80) return -1;
    
    DailyStats* ds = &g_daily[index];
    int32_t pos = 0;
    
    memcpy(out + pos, &ds->date_days, 4); pos += 4;
    memcpy(out + pos, &ds->sample_count, 4); pos += 4;
    memcpy(out + pos, &ds->average, 8); pos += 8;
    memcpy(out + pos, &ds->min, 2); pos += 2;
    memcpy(out + pos, &ds->max, 2); pos += 2;
    memcpy(out + pos, &ds->avg_joy, 8); pos += 8;
    memcpy(out + pos, &ds->avg_calm, 8); pos += 8;
    memcpy(out + pos, &ds->avg_gratitude, 8); pos += 8;
    memcpy(out + pos, &ds->avg_energy, 8); pos += 8;
    memcpy(out + pos, &ds->avg_sadness, 8); pos += 8;
    memcpy(out + pos, &ds->avg_anger, 8); pos += 8;
    memcpy(out + pos, &ds->avg_anxiety, 8); pos += 8;
    memcpy(out + pos, &ds->avg_stress, 8); pos += 8;
    
    return pos;
}

/**
 * Get analytics summary.
 * Output format (binary):
 *   double: overall_average
 *   double: volatility
 *   int32: current_streak
 *   int32: longest_good_streak
 *   int32: longest_bad_streak
 *   int32: total_samples
 *   int32: total_days
 * @return Bytes written
 */
int32_t simjot_mood_get_summary(uint8_t* out, int32_t out_len) {
    if (!out || out_len < 36) return -1;
    
    int32_t pos = 0;
    memcpy(out + pos, &g_summary.overall_average, 8); pos += 8;
    memcpy(out + pos, &g_summary.volatility, 8); pos += 8;
    memcpy(out + pos, &g_summary.current_streak, 4); pos += 4;
    memcpy(out + pos, &g_summary.longest_good_streak, 4); pos += 4;
    memcpy(out + pos, &g_summary.longest_bad_streak, 4); pos += 4;
    memcpy(out + pos, &g_summary.total_samples, 4); pos += 4;
    memcpy(out + pos, &g_summary.total_days, 4); pos += 4;
    
    return pos;
}

/**
 * Get number of daily stats entries.
 */
int32_t simjot_mood_daily_count(void) {
    return g_daily_count;
}

/**
 * Get number of raw samples.
 */
int32_t simjot_mood_sample_count(void) {
    return g_sample_count;
}

/**
 * Clear all loaded mood data.
 */
void simjot_mood_clear(void) {
    g_sample_count = 0;
    g_daily_count = 0;
    memset(&g_summary, 0, sizeof(g_summary));
}
