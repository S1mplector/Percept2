/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file simd_ops.c
 * @brief SIMD-Optimized Operations for Simjot
 * 
 * Platform-specific SIMD implementations for performance-critical operations.
 * Provides optimized string operations, math, and array processing.
 * 
 * Supports:
 * - x86/x64: SSE2, SSE4.1, AVX2
 * - ARM: NEON
 * - Fallback: Scalar implementations
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <string.h>
#include <stdlib.h>

/* Platform detection */
#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
    #define SIMJOT_X86 1
    #ifdef __SSE2__
        #include <emmintrin.h>
        #define SIMJOT_SSE2 1
    #endif
    #ifdef __SSE4_1__
        #include <smmintrin.h>
        #define SIMJOT_SSE41 1
    #endif
    #ifdef __AVX2__
        #include <immintrin.h>
        #define SIMJOT_AVX2 1
    #endif
#elif defined(__aarch64__) || defined(__ARM_NEON) || defined(__ARM_NEON__)
    #include <arm_neon.h>
    #define SIMJOT_NEON 1
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMD STRING OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Fast string length using SIMD
 */
int32_t simjot_simd_strlen(const char* str) {
    if (!str) return 0;
    
#ifdef SIMJOT_SSE2
    const char* p = str;
    
    /* Align to 16 bytes */
    while (((uintptr_t)p & 15) && *p) p++;
    if (!*p) return (int32_t)(p - str);
    
    const __m128i zero = _mm_setzero_si128();
    
    while (1) {
        __m128i chunk = _mm_load_si128((const __m128i*)p);
        __m128i cmp = _mm_cmpeq_epi8(chunk, zero);
        int mask = _mm_movemask_epi8(cmp);
        
        if (mask != 0) {
            /* Find position of first zero */
            int pos = __builtin_ctz(mask);
            return (int32_t)(p - str + pos);
        }
        p += 16;
    }
#elif defined(SIMJOT_NEON)
    const char* p = str;
    
    /* Align to 16 bytes */
    while (((uintptr_t)p & 15) && *p) p++;
    if (!*p) return (int32_t)(p - str);
    
    const uint8x16_t zero = vdupq_n_u8(0);
    
    while (1) {
        uint8x16_t chunk = vld1q_u8((const uint8_t*)p);
        uint8x16_t cmp = vceqq_u8(chunk, zero);
        uint64_t mask = vget_lane_u64(vreinterpret_u64_u8(vshrn_n_u16(vreinterpretq_u16_u8(cmp), 4)), 0);
        
        if (mask != 0) {
            int pos = __builtin_ctzll(mask) / 4;
            return (int32_t)(p - str + pos);
        }
        p += 16;
    }
#else
    return (int32_t)strlen(str);
#endif
}

/**
 * @brief Fast memory search using SIMD
 */
int32_t simjot_simd_memchr(const void* haystack, int needle, int32_t len) {
    if (!haystack || len <= 0) return -1;
    
    const uint8_t* p = (const uint8_t*)haystack;
    uint8_t c = (uint8_t)needle;
    
#ifdef SIMJOT_SSE2
    __m128i target = _mm_set1_epi8((char)c);
    
    /* Process 16 bytes at a time */
    while (len >= 16) {
        __m128i chunk = _mm_loadu_si128((const __m128i*)p);
        __m128i cmp = _mm_cmpeq_epi8(chunk, target);
        int mask = _mm_movemask_epi8(cmp);
        
        if (mask != 0) {
            return (int32_t)((p - (const uint8_t*)haystack) + __builtin_ctz(mask));
        }
        p += 16;
        len -= 16;
    }
#elif defined(SIMJOT_NEON)
    uint8x16_t target = vdupq_n_u8(c);
    
    while (len >= 16) {
        uint8x16_t chunk = vld1q_u8(p);
        uint8x16_t cmp = vceqq_u8(chunk, target);
        uint64_t mask = vget_lane_u64(vreinterpret_u64_u8(vshrn_n_u16(vreinterpretq_u16_u8(cmp), 4)), 0);
        
        if (mask != 0) {
            return (int32_t)((p - (const uint8_t*)haystack) + __builtin_ctzll(mask) / 4);
        }
        p += 16;
        len -= 16;
    }
#endif
    
    /* Scalar fallback for remainder */
    while (len > 0) {
        if (*p == c) return (int32_t)(p - (const uint8_t*)haystack);
        p++;
        len--;
    }
    
    return -1;
}

/**
 * @brief Fast case-insensitive search using SIMD
 */
int32_t simjot_simd_strcasechr(const char* str, int c) {
    if (!str) return -1;
    
    char lower = (c >= 'A' && c <= 'Z') ? c + 32 : c;
    char upper = (c >= 'a' && c <= 'z') ? c - 32 : c;
    
#ifdef SIMJOT_SSE2
    __m128i target_lower = _mm_set1_epi8(lower);
    __m128i target_upper = _mm_set1_epi8(upper);
    __m128i zero = _mm_setzero_si128();
    
    const char* p = str;
    int32_t pos = 0;
    
    while (1) {
        __m128i chunk = _mm_loadu_si128((const __m128i*)p);
        __m128i cmp_zero = _mm_cmpeq_epi8(chunk, zero);
        __m128i cmp_lower = _mm_cmpeq_epi8(chunk, target_lower);
        __m128i cmp_upper = _mm_cmpeq_epi8(chunk, target_upper);
        __m128i cmp_char = _mm_or_si128(cmp_lower, cmp_upper);
        
        int mask_zero = _mm_movemask_epi8(cmp_zero);
        int mask_char = _mm_movemask_epi8(cmp_char);
        
        if (mask_char != 0) {
            int char_pos = __builtin_ctz(mask_char);
            if (mask_zero == 0 || char_pos < __builtin_ctz(mask_zero)) {
                return pos + char_pos;
            }
        }
        
        if (mask_zero != 0) return -1;
        
        p += 16;
        pos += 16;
    }
#else
    const char* p = str;
    while (*p) {
        if (*p == lower || *p == upper) return (int32_t)(p - str);
        p++;
    }
    return -1;
#endif
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMD ARRAY OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Sum int32 array using SIMD
 */
int64_t simjot_simd_sum_i32(const int32_t* arr, int32_t len) {
    if (!arr || len <= 0) return 0;
    
    int64_t sum = 0;
    
#ifdef SIMJOT_AVX2
    __m256i vsum = _mm256_setzero_si256();
    
    while (len >= 8) {
        __m256i chunk = _mm256_loadu_si256((const __m256i*)arr);
        __m256i lo = _mm256_cvtepi32_epi64(_mm256_extracti128_si256(chunk, 0));
        __m256i hi = _mm256_cvtepi32_epi64(_mm256_extracti128_si256(chunk, 1));
        vsum = _mm256_add_epi64(vsum, lo);
        vsum = _mm256_add_epi64(vsum, hi);
        arr += 8;
        len -= 8;
    }
    
    int64_t temp[4];
    _mm256_storeu_si256((__m256i*)temp, vsum);
    sum = temp[0] + temp[1] + temp[2] + temp[3];
#elif defined(SIMJOT_SSE41)
    __m128i vsum_lo = _mm_setzero_si128();
    __m128i vsum_hi = _mm_setzero_si128();
    
    while (len >= 4) {
        __m128i chunk = _mm_loadu_si128((const __m128i*)arr);
        __m128i lo = _mm_cvtepi32_epi64(chunk);
        __m128i hi = _mm_cvtepi32_epi64(_mm_srli_si128(chunk, 8));
        vsum_lo = _mm_add_epi64(vsum_lo, lo);
        vsum_hi = _mm_add_epi64(vsum_hi, hi);
        arr += 4;
        len -= 4;
    }
    
    int64_t temp[4];
    _mm_storeu_si128((__m128i*)temp, vsum_lo);
    _mm_storeu_si128((__m128i*)(temp + 2), vsum_hi);
    sum = temp[0] + temp[1] + temp[2] + temp[3];
#elif defined(SIMJOT_NEON)
    int64x2_t vsum = vdupq_n_s64(0);
    
    while (len >= 4) {
        int32x4_t chunk = vld1q_s32(arr);
        int64x2_t lo = vmovl_s32(vget_low_s32(chunk));
        int64x2_t hi = vmovl_s32(vget_high_s32(chunk));
        vsum = vaddq_s64(vsum, lo);
        vsum = vaddq_s64(vsum, hi);
        arr += 4;
        len -= 4;
    }
    
    sum = vgetq_lane_s64(vsum, 0) + vgetq_lane_s64(vsum, 1);
#endif
    
    /* Scalar fallback for remainder */
    while (len > 0) {
        sum += *arr++;
        len--;
    }
    
    return sum;
}

/**
 * @brief Sum double array using SIMD
 */
double simjot_simd_sum_f64(const double* arr, int32_t len) {
    if (!arr || len <= 0) return 0.0;
    
    double sum = 0.0;
    
#ifdef SIMJOT_AVX2
    __m256d vsum = _mm256_setzero_pd();
    
    while (len >= 4) {
        __m256d chunk = _mm256_loadu_pd(arr);
        vsum = _mm256_add_pd(vsum, chunk);
        arr += 4;
        len -= 4;
    }
    
    double temp[4];
    _mm256_storeu_pd(temp, vsum);
    sum = temp[0] + temp[1] + temp[2] + temp[3];
#elif defined(SIMJOT_SSE2)
    __m128d vsum = _mm_setzero_pd();
    
    while (len >= 2) {
        __m128d chunk = _mm_loadu_pd(arr);
        vsum = _mm_add_pd(vsum, chunk);
        arr += 2;
        len -= 2;
    }
    
    double temp[2];
    _mm_storeu_pd(temp, vsum);
    sum = temp[0] + temp[1];
#elif defined(SIMJOT_NEON)
    float64x2_t vsum = vdupq_n_f64(0.0);
    
    while (len >= 2) {
        float64x2_t chunk = vld1q_f64(arr);
        vsum = vaddq_f64(vsum, chunk);
        arr += 2;
        len -= 2;
    }
    
    sum = vgetq_lane_f64(vsum, 0) + vgetq_lane_f64(vsum, 1);
#endif
    
    while (len > 0) {
        sum += *arr++;
        len--;
    }
    
    return sum;
}

/**
 * @brief Find min/max in double array using SIMD
 */
void simjot_simd_minmax_f64(const double* arr, int32_t len, double* out_min, double* out_max) {
    if (!arr || len <= 0) return;
    
    double min_val = arr[0];
    double max_val = arr[0];
    
#ifdef SIMJOT_AVX2
    if (len >= 4) {
        __m256d vmin = _mm256_loadu_pd(arr);
        __m256d vmax = vmin;
        arr += 4;
        len -= 4;
        
        while (len >= 4) {
            __m256d chunk = _mm256_loadu_pd(arr);
            vmin = _mm256_min_pd(vmin, chunk);
            vmax = _mm256_max_pd(vmax, chunk);
            arr += 4;
            len -= 4;
        }
        
        double temp_min[4], temp_max[4];
        _mm256_storeu_pd(temp_min, vmin);
        _mm256_storeu_pd(temp_max, vmax);
        
        for (int i = 0; i < 4; i++) {
            if (temp_min[i] < min_val) min_val = temp_min[i];
            if (temp_max[i] > max_val) max_val = temp_max[i];
        }
    }
#elif defined(SIMJOT_SSE2)
    if (len >= 2) {
        __m128d vmin = _mm_loadu_pd(arr);
        __m128d vmax = vmin;
        arr += 2;
        len -= 2;
        
        while (len >= 2) {
            __m128d chunk = _mm_loadu_pd(arr);
            vmin = _mm_min_pd(vmin, chunk);
            vmax = _mm_max_pd(vmax, chunk);
            arr += 2;
            len -= 2;
        }
        
        double temp_min[2], temp_max[2];
        _mm_storeu_pd(temp_min, vmin);
        _mm_storeu_pd(temp_max, vmax);
        
        if (temp_min[0] < min_val) min_val = temp_min[0];
        if (temp_min[1] < min_val) min_val = temp_min[1];
        if (temp_max[0] > max_val) max_val = temp_max[0];
        if (temp_max[1] > max_val) max_val = temp_max[1];
    }
#endif
    
    while (len > 0) {
        if (*arr < min_val) min_val = *arr;
        if (*arr > max_val) max_val = *arr;
        arr++;
        len--;
    }
    
    if (out_min) *out_min = min_val;
    if (out_max) *out_max = max_val;
}

/**
 * @brief Fast memory compare using SIMD
 */
int32_t simjot_simd_memcmp(const void* a, const void* b, int32_t len) {
    if (!a || !b || len <= 0) return 0;
    
    const uint8_t* pa = (const uint8_t*)a;
    const uint8_t* pb = (const uint8_t*)b;
    
#ifdef SIMJOT_SSE2
    while (len >= 16) {
        __m128i va = _mm_loadu_si128((const __m128i*)pa);
        __m128i vb = _mm_loadu_si128((const __m128i*)pb);
        __m128i cmp = _mm_cmpeq_epi8(va, vb);
        int mask = _mm_movemask_epi8(cmp);
        
        if (mask != 0xFFFF) {
            int pos = __builtin_ctz(~mask);
            return pa[pos] - pb[pos];
        }
        
        pa += 16;
        pb += 16;
        len -= 16;
    }
#endif
    
    while (len > 0) {
        if (*pa != *pb) return *pa - *pb;
        pa++;
        pb++;
        len--;
    }
    
    return 0;
}

/**
 * @brief Check SIMD support level
 * @return 0=scalar, 1=SSE2, 2=SSE4.1, 3=AVX2, 4=NEON
 */
int32_t simjot_simd_support_level(void) {
#ifdef SIMJOT_AVX2
    return 3;
#elif defined(SIMJOT_SSE41)
    return 2;
#elif defined(SIMJOT_SSE2)
    return 1;
#elif defined(SIMJOT_NEON)
    return 4;
#else
    return 0;
#endif
}
