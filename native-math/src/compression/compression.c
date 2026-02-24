/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file compression.c
 * @brief Native Compression Utilities for Simjot
 * 
 * High-performance compression and decompression using embedded pure C
 * DEFLATE implementation. Zero external dependencies.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"
#include "deflate.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* Maximum compression level */
#define MAX_COMPRESSION_LEVEL 9

/* Default compression level (best compression) */
#define DEFAULT_COMPRESSION_LEVEL DEFLATE_BEST_COMPRESSION

/**
 * @brief Compress data using DEFLATE algorithm
 * 
 * Uses embedded pure C DEFLATE implementation - no external dependencies.
 * 
 * @param input Input data to compress
 * @param input_len Length of input data
 * @param output Output buffer for compressed data
 * @param output_len Size of output buffer
 * @param level Compression level (0-9, 9 = best)
 * @return Actual compressed size, or negative on error
 */
int32_t simjot_compress(const uint8_t* input, int32_t input_len,
                        uint8_t* output, int32_t output_len, int32_t level) {
    if (!input || !output || input_len <= 0 || output_len <= 0) {
        return -1;
    }
    
    /* Clamp compression level */
    if (level < 0) level = 0;
    if (level > MAX_COMPRESSION_LEVEL) level = MAX_COMPRESSION_LEVEL;
    
    size_t dest_len = (size_t)output_len;
    int ret = simjot_deflate(output, &dest_len, input, (size_t)input_len, level);
    
    if (ret != DEFLATE_OK) {
        return -2 + ret;  /* Map error codes */
    }
    
    return (int32_t)dest_len;
}

/**
 * @brief Decompress DEFLATE-compressed data
 * 
 * Uses embedded pure C DEFLATE implementation - no external dependencies.
 * 
 * @param input Compressed input data
 * @param input_len Length of compressed data
 * @param output Output buffer for decompressed data
 * @param output_len Size of output buffer
 * @return Actual decompressed size, or negative on error
 */
int32_t simjot_decompress(const uint8_t* input, int32_t input_len,
                          uint8_t* output, int32_t output_len) {
    if (!input || !output || input_len <= 0 || output_len <= 0) {
        return -1;
    }
    
    size_t dest_len = (size_t)output_len;
    int ret = simjot_inflate(output, &dest_len, input, (size_t)input_len);
    
    if (ret != DEFLATE_OK) {
        return -2 + ret;  /* Map error codes */
    }
    
    return (int32_t)dest_len;
}

/**
 * @brief Get the maximum compressed size for given input
 * 
 * Used to allocate output buffer before compression.
 * 
 * @param input_len Original data length
 * @return Maximum possible compressed size
 */
int32_t simjot_compress_bound(int32_t input_len) {
    if (input_len <= 0) return 0;
    return (int32_t)simjot_deflate_bound((size_t)input_len);
}

/**
 * @brief Compress data with default settings (best compression)
 * 
 * Convenience wrapper using default compression level.
 * 
 * @param input Input data
 * @param input_len Input length
 * @param output Output buffer
 * @param output_len Output buffer size
 * @return Compressed size, or negative on error
 */
int32_t simjot_compress_default(const uint8_t* input, int32_t input_len,
                                 uint8_t* output, int32_t output_len) {
    return simjot_compress(input, input_len, output, output_len, DEFAULT_COMPRESSION_LEVEL);
}

/**
 * @brief Fast compression (lower ratio, faster speed)
 * 
 * @param input Input data
 * @param input_len Input length
 * @param output Output buffer
 * @param output_len Output buffer size
 * @return Compressed size, or negative on error
 */
int32_t simjot_compress_fast(const uint8_t* input, int32_t input_len,
                              uint8_t* output, int32_t output_len) {
    return simjot_compress(input, input_len, output, output_len, DEFLATE_BEST_SPEED);
}
