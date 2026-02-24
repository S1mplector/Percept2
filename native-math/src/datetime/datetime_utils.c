/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file datetime_utils.c
 * @brief Native Date/Time Utilities for Simjot
 * 
 * Fast date/time formatting and parsing without Java overhead.
 * Supports common patterns used throughout the application.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/time.h>
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * TIMESTAMP UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Get current Unix timestamp in milliseconds
 * @return Milliseconds since epoch
 */
int64_t simjot_time_now_millis(void) {
#ifdef _WIN32
    FILETIME ft;
    GetSystemTimeAsFileTime(&ft);
    ULARGE_INTEGER uli;
    uli.LowPart = ft.dwLowDateTime;
    uli.HighPart = ft.dwHighDateTime;
    /* Windows epoch is 1601, Unix epoch is 1970 */
    return (int64_t)((uli.QuadPart - 116444736000000000ULL) / 10000);
#else
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000 + (int64_t)tv.tv_usec / 1000;
#endif
}

/**
 * @brief Get current Unix timestamp in seconds
 * @return Seconds since epoch
 */
int64_t simjot_time_now_secs(void) {
    return (int64_t)time(NULL);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DATE FORMATTING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Format timestamp with pattern (Java-compatible subset)
 * 
 * Supported patterns:
 * - yyyy: 4-digit year
 * - yy: 2-digit year
 * - MM: 2-digit month (01-12)
 * - dd: 2-digit day (01-31)
 * - HH: 2-digit hour 24h (00-23)
 * - hh: 2-digit hour 12h (01-12)
 * - mm: 2-digit minute (00-59)
 * - ss: 2-digit second (00-59)
 * - SSS: 3-digit milliseconds
 * - a: AM/PM
 * 
 * @param millis Timestamp in milliseconds (0 = now)
 * @param pattern Format pattern
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of formatted string, or negative on error
 */
int32_t simjot_time_format(int64_t millis, const char* pattern,
                           char* output, int32_t output_len) {
    if (!pattern || !output || output_len <= 0) return -1;
    
    if (millis == 0) millis = simjot_time_now_millis();
    
    time_t secs = (time_t)(millis / 1000);
    int ms = (int)(millis % 1000);
    
    struct tm* tm = localtime(&secs);
    if (!tm) return -2;
    
    int32_t out_pos = 0;
    const char* p = pattern;
    
    while (*p && out_pos < output_len - 1) {
        if (p[0] == 'y' && p[1] == 'y' && p[2] == 'y' && p[3] == 'y') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%04d", tm->tm_year + 1900);
            p += 4;
        } else if (p[0] == 'y' && p[1] == 'y') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", (tm->tm_year + 1900) % 100);
            p += 2;
        } else if (p[0] == 'M' && p[1] == 'M') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", tm->tm_mon + 1);
            p += 2;
        } else if (p[0] == 'd' && p[1] == 'd') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", tm->tm_mday);
            p += 2;
        } else if (p[0] == 'H' && p[1] == 'H') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", tm->tm_hour);
            p += 2;
        } else if (p[0] == 'h' && p[1] == 'h') {
            int h12 = tm->tm_hour % 12;
            if (h12 == 0) h12 = 12;
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", h12);
            p += 2;
        } else if (p[0] == 'm' && p[1] == 'm') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", tm->tm_min);
            p += 2;
        } else if (p[0] == 's' && p[1] == 's') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%02d", tm->tm_sec);
            p += 2;
        } else if (p[0] == 'S' && p[1] == 'S' && p[2] == 'S') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%03d", ms);
            p += 3;
        } else if (p[0] == 'a') {
            out_pos += snprintf(output + out_pos, output_len - out_pos, "%s", tm->tm_hour < 12 ? "AM" : "PM");
            p += 1;
        } else {
            output[out_pos++] = *p++;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Format current time with pattern
 */
int32_t simjot_time_format_now(const char* pattern, char* output, int32_t output_len) {
    return simjot_time_format(0, pattern, output, output_len);
}

/**
 * @brief Format timestamp as ISO 8601 (yyyy-MM-ddTHH:mm:ss)
 */
int32_t simjot_time_format_iso(int64_t millis, char* output, int32_t output_len) {
    return simjot_time_format(millis, "yyyy-MM-dd'T'HH:mm:ss", output, output_len);
}

/**
 * @brief Format timestamp for backup filenames (yyyyMMdd_HHmmss)
 */
int32_t simjot_time_format_backup(int64_t millis, char* output, int32_t output_len) {
    return simjot_time_format(millis, "yyyyMMdd_HHmmss", output, output_len);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DATE PARSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Parse date string with pattern
 * 
 * @param str Date string to parse
 * @param pattern Format pattern (same as simjot_time_format)
 * @return Timestamp in milliseconds, or negative on error
 */
int64_t simjot_time_parse(const char* str, const char* pattern) {
    if (!str || !pattern) return -1;
    
    struct tm tm = {0};
    tm.tm_isdst = -1;
    
    const char* s = str;
    const char* p = pattern;
    
    while (*p && *s) {
        if (p[0] == 'y' && p[1] == 'y' && p[2] == 'y' && p[3] == 'y') {
            int year;
            if (sscanf(s, "%4d", &year) != 1) return -2;
            tm.tm_year = year - 1900;
            s += 4;
            p += 4;
        } else if (p[0] == 'y' && p[1] == 'y') {
            int year;
            if (sscanf(s, "%2d", &year) != 1) return -2;
            tm.tm_year = (year >= 70 ? year : year + 100);
            s += 2;
            p += 2;
        } else if (p[0] == 'M' && p[1] == 'M') {
            int month;
            if (sscanf(s, "%2d", &month) != 1) return -2;
            tm.tm_mon = month - 1;
            s += 2;
            p += 2;
        } else if (p[0] == 'd' && p[1] == 'd') {
            int day;
            if (sscanf(s, "%2d", &day) != 1) return -2;
            tm.tm_mday = day;
            s += 2;
            p += 2;
        } else if (p[0] == 'H' && p[1] == 'H') {
            int hour;
            if (sscanf(s, "%2d", &hour) != 1) return -2;
            tm.tm_hour = hour;
            s += 2;
            p += 2;
        } else if (p[0] == 'm' && p[1] == 'm') {
            int min;
            if (sscanf(s, "%2d", &min) != 1) return -2;
            tm.tm_min = min;
            s += 2;
            p += 2;
        } else if (p[0] == 's' && p[1] == 's') {
            int sec;
            if (sscanf(s, "%2d", &sec) != 1) return -2;
            tm.tm_sec = sec;
            s += 2;
            p += 2;
        } else if (p[0] == 'S' && p[1] == 'S' && p[2] == 'S') {
            /* Skip milliseconds in parsing for now */
            s += 3;
            p += 3;
        } else {
            /* Literal character */
            if (*s != *p && *p != '\'') return -3;
            if (*p == '\'') p++;
            else { s++; p++; }
        }
    }
    
    time_t t = mktime(&tm);
    if (t == (time_t)-1) return -4;
    
    return (int64_t)t * 1000;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DATE CALCULATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Add days to timestamp
 */
int64_t simjot_time_add_days(int64_t millis, int32_t days) {
    return millis + (int64_t)days * 24 * 60 * 60 * 1000;
}

/**
 * @brief Get difference in days between two timestamps
 */
int32_t simjot_time_diff_days(int64_t millis1, int64_t millis2) {
    int64_t diff = millis2 - millis1;
    return (int32_t)(diff / (24 * 60 * 60 * 1000));
}

/**
 * @brief Get start of day (midnight) for timestamp
 */
int64_t simjot_time_start_of_day(int64_t millis) {
    time_t secs = (time_t)(millis / 1000);
    struct tm* tm = localtime(&secs);
    if (!tm) return millis;
    
    tm->tm_hour = 0;
    tm->tm_min = 0;
    tm->tm_sec = 0;
    
    time_t t = mktime(tm);
    return (int64_t)t * 1000;
}

/**
 * @brief Get day of week (0=Sunday, 6=Saturday)
 */
int32_t simjot_time_day_of_week(int64_t millis) {
    time_t secs = (time_t)(millis / 1000);
    struct tm* tm = localtime(&secs);
    return tm ? tm->tm_wday : -1;
}

/**
 * @brief Check if year is leap year
 */
int32_t simjot_time_is_leap_year(int32_t year) {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
}

/**
 * @brief Get days in month
 */
int32_t simjot_time_days_in_month(int32_t year, int32_t month) {
    static const int days[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    if (month < 1 || month > 12) return -1;
    int d = days[month - 1];
    if (month == 2 && simjot_time_is_leap_year(year)) d++;
    return d;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RELATIVE TIME
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Format relative time (e.g., "5 minutes ago", "in 2 hours")
 * 
 * @param millis Timestamp to compare
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of string, or negative on error
 */
int32_t simjot_time_relative(int64_t millis, char* output, int32_t output_len) {
    if (!output || output_len <= 0) return -1;
    
    int64_t now = simjot_time_now_millis();
    int64_t diff = now - millis;
    int future = 0;
    
    if (diff < 0) {
        diff = -diff;
        future = 1;
    }
    
    int64_t secs = diff / 1000;
    int64_t mins = secs / 60;
    int64_t hours = mins / 60;
    int64_t days = hours / 24;
    
    const char* fmt;
    int64_t val;
    
    if (secs < 60) {
        fmt = future ? "in %lld seconds" : "%lld seconds ago";
        val = secs < 1 ? 1 : secs;
    } else if (mins < 60) {
        fmt = future ? "in %lld minutes" : "%lld minutes ago";
        val = mins;
    } else if (hours < 24) {
        fmt = future ? "in %lld hours" : "%lld hours ago";
        val = hours;
    } else if (days < 7) {
        fmt = future ? "in %lld days" : "%lld days ago";
        val = days;
    } else if (days < 30) {
        fmt = future ? "in %lld weeks" : "%lld weeks ago";
        val = days / 7;
    } else if (days < 365) {
        fmt = future ? "in %lld months" : "%lld months ago";
        val = days / 30;
    } else {
        fmt = future ? "in %lld years" : "%lld years ago";
        val = days / 365;
    }
    
    return snprintf(output, output_len, fmt, val);
}
