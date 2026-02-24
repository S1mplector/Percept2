/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Core test utilities for native integration.
 */

#include "simjot_native.h"

#include <string.h>

int32_t simjot_add(int32_t a, int32_t b) {
    return a + b;
}

int32_t simjot_strlen(const char* str) {
    if (str == NULL) return 0;
    return (int32_t)strlen(str);
}

int64_t simjot_sum_array(const int32_t* arr, int32_t len) {
    int64_t sum = 0;
    for (int32_t i = 0; i < len; i++) {
        sum += arr[i];
    }
    return sum;
}

int64_t simjot_fib(int32_t n) {
    if (n <= 1) return n;
    int64_t a = 0, b = 1;
    for (int32_t i = 2; i <= n; i++) {
        int64_t tmp = a + b;
        a = b;
        b = tmp;
    }
    return b;
}
