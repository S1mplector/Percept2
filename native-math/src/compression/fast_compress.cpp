/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Fast Compression Utilities
 * LZ4-style fast compression for data storage
 */

#include "simjot_native.h"
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <cstddef>
#include <algorithm>

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * LZ4-STYLE FAST COMPRESSION
 * ═══════════════════════════════════════════════════════════════════════════ */

#define HASH_LOG 12
#define HASH_SIZE (1 << HASH_LOG)
#define MIN_MATCH 4
#define ML_BITS 4
#define ML_MASK ((1 << ML_BITS) - 1)
#define RUN_BITS (8 - ML_BITS)
#define RUN_MASK ((1 << RUN_BITS) - 1)

static inline uint32_t hash4(const uint8_t* p) {
    uint32_t v = *(const uint32_t*)p;
    return (v * 2654435761U) >> (32 - HASH_LOG);
}

static inline void write_varint(uint8_t** dst, uint32_t val) {
    while (val >= 255) {
        *(*dst)++ = 255;
        val -= 255;
    }
    *(*dst)++ = (uint8_t)val;
}

/**
 * Compress data using LZ4-style algorithm.
 * @param src Source data
 * @param src_size Source size
 * @param dst Destination buffer
 * @param dst_capacity Destination capacity
 * @return Compressed size, or 0 on failure
 */
int32_t simjot_lz4_compress(const uint8_t* src, int32_t src_size,
                            uint8_t* dst, int32_t dst_capacity) {
    if (!src || !dst || src_size <= 0 || dst_capacity <= 0) return 0;
    
    // Small data - store uncompressed
    if (src_size < 16) {
        if (dst_capacity < src_size + 1) return 0;
        dst[0] = 0;  // Uncompressed marker
        memcpy(dst + 1, src, src_size);
        return src_size + 1;
    }
    
    uint16_t hash_table[HASH_SIZE] = {0};
    
    const uint8_t* ip = src;
    const uint8_t* anchor = src;
    const uint8_t* src_end = src + src_size;
    const uint8_t* match_limit = src_end - MIN_MATCH;
    
    uint8_t* op = dst;
    uint8_t* dst_end = dst + dst_capacity;
    
    // Compressed marker
    *op++ = 1;
    
    ip++;  // First byte is literal
    
    while (ip < match_limit) {
        // Find match
        uint32_t h = hash4(ip);
        const uint8_t* ref = src + hash_table[h];
        hash_table[h] = (uint16_t)(ip - src);
        
        if (ref < src || ip - ref > 65535 || *(const uint32_t*)ref != *(const uint32_t*)ip) {
            ip++;
            continue;
        }
        
        // Encode literals
        size_t lit_len = ip - anchor;
        
        if (op + lit_len + 16 > dst_end) {
            // Not enough space - store uncompressed
            if (dst_capacity < src_size + 1) return 0;
            dst[0] = 0;
            memcpy(dst + 1, src, src_size);
            return src_size + 1;
        }
        
        // Count match length
        const uint8_t* match_start = ip;
        ip += MIN_MATCH;
        const uint8_t* ref_p = ref + MIN_MATCH;
        
        while (ip < src_end && *ip == *ref_p) {
            ip++;
            ref_p++;
        }
        
        size_t match_len = ip - match_start;
        size_t offset = match_start - ref;
        
        // Encode token
        uint8_t token;
        if (lit_len >= 15) {
            token = 15 << ML_BITS;
        } else {
            token = (uint8_t)(lit_len << ML_BITS);
        }
        
        if (match_len - MIN_MATCH >= 15) {
            token |= 15;
        } else {
            token |= (uint8_t)(match_len - MIN_MATCH);
        }
        
        *op++ = token;
        
        // Extended literal length
        if (lit_len >= 15) {
            write_varint(&op, (uint32_t)(lit_len - 15));
        }
        
        // Copy literals
        memcpy(op, anchor, lit_len);
        op += lit_len;
        
        // Offset
        *op++ = (uint8_t)(offset & 0xFF);
        *op++ = (uint8_t)(offset >> 8);
        
        // Extended match length
        if (match_len - MIN_MATCH >= 15) {
            write_varint(&op, (uint32_t)(match_len - MIN_MATCH - 15));
        }
        
        anchor = ip;
    }
    
    // Remaining literals
    size_t last_lit = src_end - anchor;
    if (op + last_lit + 3 > dst_end) {
        // Store uncompressed
        if (dst_capacity < src_size + 1) return 0;
        dst[0] = 0;
        memcpy(dst + 1, src, src_size);
        return src_size + 1;
    }
    
    // Token with only literals
    if (last_lit >= 15) {
        *op++ = 15 << ML_BITS;
        write_varint(&op, (uint32_t)(last_lit - 15));
    } else {
        *op++ = (uint8_t)(last_lit << ML_BITS);
    }
    
    memcpy(op, anchor, last_lit);
    op += last_lit;
    
    return (int32_t)(op - dst);
}

/**
 * Decompress data.
 * @param src Compressed data
 * @param src_size Compressed size
 * @param dst Destination buffer
 * @param dst_capacity Destination capacity
 * @return Decompressed size, or 0 on failure
 */
int32_t simjot_lz4_decompress(const uint8_t* src, int32_t src_size,
                              uint8_t* dst, int32_t dst_capacity) {
    if (!src || !dst || src_size <= 0 || dst_capacity <= 0) return 0;
    
    // Check marker
    if (src[0] == 0) {
        // Uncompressed
        if (dst_capacity < src_size - 1) return 0;
        memcpy(dst, src + 1, src_size - 1);
        return src_size - 1;
    }
    
    const uint8_t* ip = src + 1;
    const uint8_t* src_end = src + src_size;
    uint8_t* op = dst;
    uint8_t* dst_end = dst + dst_capacity;
    
    while (ip < src_end) {
        uint8_t token = *ip++;
        
        // Literal length
        size_t lit_len = token >> ML_BITS;
        if (lit_len == 15) {
            uint8_t s;
            do {
                if (ip >= src_end) return 0;
                s = *ip++;
                lit_len += s;
            } while (s == 255);
        }
        
        // Copy literals
        if (op + lit_len > dst_end || ip + lit_len > src_end) return 0;
        memcpy(op, ip, lit_len);
        op += lit_len;
        ip += lit_len;
        
        if (ip >= src_end) break;  // End of input
        
        // Offset
        if (ip + 2 > src_end) return 0;
        size_t offset = ip[0] | ((size_t)ip[1] << 8);
        ip += 2;
        
        if (offset == 0 || op - dst < (ptrdiff_t)offset) return 0;
        
        // Match length
        size_t match_len = (token & ML_MASK) + MIN_MATCH;
        if ((token & ML_MASK) == 15) {
            uint8_t s;
            do {
                if (ip >= src_end) return 0;
                s = *ip++;
                match_len += s;
            } while (s == 255);
        }
        
        // Copy match
        if (op + match_len > dst_end) return 0;
        
        const uint8_t* match = op - offset;
        if (offset >= 8) {
            // Fast copy
            while (match_len >= 8) {
                memcpy(op, match, 8);
                op += 8;
                match += 8;
                match_len -= 8;
            }
        }
        while (match_len-- > 0) {
            *op++ = *match++;
        }
    }
    
    return (int32_t)(op - dst);
}

/**
 * Get maximum compressed size for LZ4-style compression.
 * NOTE: Renamed to avoid ABI conflict with compression/compression.c (DEFLATE version).
 */
int32_t simjot_lz4_compress_bound(int32_t src_size) {
    return src_size + (src_size / 255) + 16;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RUN-LENGTH ENCODING (for sparse data)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * RLE compress (good for data with many repeated bytes).
 */
int32_t simjot_rle_compress(const uint8_t* src, int32_t src_size,
                            uint8_t* dst, int32_t dst_capacity) {
    if (!src || !dst || src_size <= 0) return 0;
    
    uint8_t* op = dst;
    uint8_t* dst_end = dst + dst_capacity;
    const uint8_t* ip = src;
    const uint8_t* src_end = src + src_size;
    
    while (ip < src_end) {
        uint8_t byte = *ip;
        int32_t count = 1;
        
        while (ip + count < src_end && src[ip - src + count] == byte && count < 255) {
            count++;
        }
        
        if (count >= 3 || byte == 0xFF) {
            // Encode run
            if (op + 3 > dst_end) return 0;
            *op++ = 0xFF;  // Escape
            *op++ = byte;
            *op++ = (uint8_t)count;
        } else {
            // Literal
            for (int i = 0; i < count; i++) {
                if (op >= dst_end) return 0;
                *op++ = byte;
            }
        }
        
        ip += count;
    }
    
    return (int32_t)(op - dst);
}

/**
 * RLE decompress.
 */
int32_t simjot_rle_decompress(const uint8_t* src, int32_t src_size,
                              uint8_t* dst, int32_t dst_capacity) {
    if (!src || !dst || src_size <= 0) return 0;
    
    uint8_t* op = dst;
    uint8_t* dst_end = dst + dst_capacity;
    const uint8_t* ip = src;
    const uint8_t* src_end = src + src_size;
    
    while (ip < src_end) {
        uint8_t byte = *ip++;
        
        if (byte == 0xFF && ip + 2 <= src_end) {
            // Run
            uint8_t val = *ip++;
            uint8_t count = *ip++;
            
            if (op + count > dst_end) return 0;
            memset(op, val, count);
            op += count;
        } else {
            // Literal
            if (op >= dst_end) return 0;
            *op++ = byte;
        }
    }
    
    return (int32_t)(op - dst);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DELTA ENCODING (for time series)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Delta encode int32 array.
 */
int32_t simjot_delta_encode_i32(const int32_t* src, int32_t count,
                                int32_t* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    dst[0] = src[0];
    for (int32_t i = 1; i < count; i++) {
        dst[i] = src[i] - src[i - 1];
    }
    
    return count;
}

/**
 * Delta decode int32 array.
 */
int32_t simjot_delta_decode_i32(const int32_t* src, int32_t count,
                                int32_t* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    dst[0] = src[0];
    for (int32_t i = 1; i < count; i++) {
        dst[i] = dst[i - 1] + src[i];
    }
    
    return count;
}

/**
 * Delta encode double array.
 */
int32_t simjot_delta_encode_f64(const double* src, int32_t count,
                                int64_t* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    // Store as fixed-point with scaling
    int64_t prev = (int64_t)(src[0] * 1000000.0);
    dst[0] = prev;
    
    for (int32_t i = 1; i < count; i++) {
        int64_t curr = (int64_t)(src[i] * 1000000.0);
        dst[i] = curr - prev;
        prev = curr;
    }
    
    return count;
}

/**
 * Delta decode double array.
 */
int32_t simjot_delta_decode_f64(const int64_t* src, int32_t count,
                                double* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    int64_t val = src[0];
    dst[0] = val / 1000000.0;
    
    for (int32_t i = 1; i < count; i++) {
        val += src[i];
        dst[i] = val / 1000000.0;
    }
    
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ZIGZAG ENCODING (for signed integers)
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline uint32_t zigzag_encode32(int32_t n) {
    return (uint32_t)((n << 1) ^ (n >> 31));
}

static inline int32_t zigzag_decode32(uint32_t n) {
    return (int32_t)((n >> 1) ^ -(int32_t)(n & 1));
}

static inline uint64_t zigzag_encode64(int64_t n) {
    return (uint64_t)((n << 1) ^ (n >> 63));
}

static inline int64_t zigzag_decode64(uint64_t n) {
    return (int64_t)((n >> 1) ^ -(int64_t)(n & 1));
}

/**
 * Zigzag encode int32 array (better for compression of signed values).
 */
int32_t simjot_zigzag_encode_i32(const int32_t* src, int32_t count,
                                  uint32_t* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    for (int32_t i = 0; i < count; i++) {
        dst[i] = zigzag_encode32(src[i]);
    }
    
    return count;
}

/**
 * Zigzag decode int32 array.
 */
int32_t simjot_zigzag_decode_i32(const uint32_t* src, int32_t count,
                                  int32_t* dst) {
    if (!src || !dst || count <= 0) return 0;
    
    for (int32_t i = 0; i < count; i++) {
        dst[i] = zigzag_decode32(src[i]);
    }
    
    return count;
}

} // extern "C"
