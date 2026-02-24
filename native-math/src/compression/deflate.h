/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file deflate.h
 * @brief Embedded DEFLATE Compression in Pure C
 * 
 * A lightweight, zero-dependency DEFLATE compression/decompression
 * implementation for Simjot. Based on RFC 1951.
 * 
 * Features:
 * - No external dependencies (pure C11)
 * - Small footprint suitable for embedding
 * - Compatible with standard zlib/DEFLATE format
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#ifndef SIMJOT_DEFLATE_H
#define SIMJOT_DEFLATE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Return codes */
#define DEFLATE_OK           0
#define DEFLATE_STREAM_END   1
#define DEFLATE_ERR_DATA    -1
#define DEFLATE_ERR_MEM     -2
#define DEFLATE_ERR_BUF     -3
#define DEFLATE_ERR_PARAM   -4

/* Compression levels */
#define DEFLATE_NO_COMPRESSION      0
#define DEFLATE_BEST_SPEED          1
#define DEFLATE_BEST_COMPRESSION    9
#define DEFLATE_DEFAULT_COMPRESSION 6

/**
 * @brief Get maximum compressed size for given input length
 * 
 * @param src_len Original data length
 * @return Maximum possible compressed size
 */
size_t simjot_deflate_bound(size_t src_len);

/**
 * @brief Compress data using DEFLATE algorithm
 * 
 * @param dest Destination buffer
 * @param dest_len Pointer to destination buffer size (updated with actual size)
 * @param src Source data
 * @param src_len Source data length
 * @param level Compression level (0-9)
 * @return DEFLATE_OK on success, negative error code on failure
 */
int simjot_deflate(uint8_t* dest, size_t* dest_len,
                   const uint8_t* src, size_t src_len, int level);

/**
 * @brief Decompress DEFLATE-compressed data
 * 
 * @param dest Destination buffer
 * @param dest_len Pointer to destination buffer size (updated with actual size)
 * @param src Compressed source data
 * @param src_len Compressed data length
 * @return DEFLATE_OK on success, negative error code on failure
 */
int simjot_inflate(uint8_t* dest, size_t* dest_len,
                   const uint8_t* src, size_t src_len);

#ifdef __cplusplus
}
#endif

#endif /* SIMJOT_DEFLATE_H */
