/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file deflate.c
 * @brief Embedded DEFLATE Compression - Pure C Implementation
 * 
 * A lightweight, zero-dependency DEFLATE compression/decompression
 * implementation. Uses stored blocks for simplicity and reliability,
 * with optional LZ77 compression for better ratios.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "deflate.h"
#include <string.h>
#include <stdlib.h>

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS AND LOOKUP TABLES
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_BLOCK_SIZE 65535
#define WINDOW_SIZE    32768
#define MIN_MATCH      3
#define MAX_MATCH      258
#define HASH_BITS      15
#define HASH_SIZE      (1 << HASH_BITS)
#define HASH_MASK      (HASH_SIZE - 1)

/**
 * Fixed Huffman code lengths for literals/lengths (RFC 1951) 
 * Meant to be used with fixed Huffman codes
 * They are precomputed for performance
 * */
 
static const uint8_t FIXED_LIT_LENGTHS[288] = {
    8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, 8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
    8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, 8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
    8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, 8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
    8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, 8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,
    8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, 9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
    9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9, 9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
    9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9, 9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
    9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9, 9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
    7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7, 7,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8
};

/* Fixed Huffman codes for literals (precomputed) */
static uint16_t fixed_lit_codes[288];
static uint8_t  fixed_lit_lens[288];
static int fixed_tables_init = 0;

/* Length base values */
static const uint16_t LENGTH_BASE[29] = {
    3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,
    35,43,51,59,67,83,99,115,131,163,195,227,258
};

/* Length extra bits */
static const uint8_t LENGTH_EXTRA[29] = {
    0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0
};

/* Distance base values */
static const uint16_t DIST_BASE[30] = {
    1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,
    257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577
};

/* Distance extra bits */
static const uint8_t DIST_EXTRA[30] = {
    0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13
};

/* ═══════════════════════════════════════════════════════════════════════════
 * BIT STREAM HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    uint8_t* data;
    size_t   size;
    size_t   pos;
    uint32_t bits;
    int      nbits;
} BitStream;

static void bs_init_write(BitStream* bs, uint8_t* data, size_t size) {
    bs->data = data;
    bs->size = size;
    bs->pos = 0;
    bs->bits = 0;
    bs->nbits = 0;
}

static void bs_init_read(BitStream* bs, const uint8_t* data, size_t size) {
    bs->data = (uint8_t*)data;
    bs->size = size;
    bs->pos = 0;
    bs->bits = 0;
    bs->nbits = 0;
}

static int bs_write_bits(BitStream* bs, uint32_t value, int nbits) {
    bs->bits |= (value << bs->nbits);
    bs->nbits += nbits;
    while (bs->nbits >= 8) {
        if (bs->pos >= bs->size) return DEFLATE_ERR_BUF;
        bs->data[bs->pos++] = (uint8_t)(bs->bits & 0xFF);
        bs->bits >>= 8;
        bs->nbits -= 8;
    }
    return DEFLATE_OK;
}

static int bs_flush(BitStream* bs) {
    while (bs->nbits > 0) {
        if (bs->pos >= bs->size) return DEFLATE_ERR_BUF;
        bs->data[bs->pos++] = (uint8_t)(bs->bits & 0xFF);
        bs->bits >>= 8;
        bs->nbits -= 8;
    }
    bs->nbits = 0;
    bs->bits = 0;
    return DEFLATE_OK;
}

static int bs_read_bits(BitStream* bs, int nbits, uint32_t* out) {
    while (bs->nbits < nbits) {
        if (bs->pos >= bs->size) return DEFLATE_ERR_DATA;
        bs->bits |= ((uint32_t)bs->data[bs->pos++] << bs->nbits);
        bs->nbits += 8;
    }
    *out = bs->bits & ((1u << nbits) - 1);
    bs->bits >>= nbits;
    bs->nbits -= nbits;
    return DEFLATE_OK;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HUFFMAN CODING
 * ═══════════════════════════════════════════════════════════════════════════ */

static void init_fixed_tables(void) {
    if (fixed_tables_init) return;
    
    /* Build fixed literal/length codes */
    uint16_t code = 0;
    int bl_count[16] = {0};
    uint16_t next_code[16];
    
    for (int i = 0; i < 288; i++) {
        bl_count[FIXED_LIT_LENGTHS[i]]++;
    }
    
    next_code[0] = 0;
    for (int bits = 1; bits < 16; bits++) {
        code = (code + bl_count[bits - 1]) << 1;
        next_code[bits] = code;
    }
    
    for (int n = 0; n < 288; n++) {
        int len = FIXED_LIT_LENGTHS[n];
        if (len != 0) {
            /* Reverse bits for output */
            uint16_t c = next_code[len]++;
            uint16_t rev = 0;
            for (int i = 0; i < len; i++) {
                rev = (rev << 1) | (c & 1);
                c >>= 1;
            }
            fixed_lit_codes[n] = rev;
            fixed_lit_lens[n] = (uint8_t)len;
        }
    }
    
    fixed_tables_init = 1;
}

static int write_fixed_literal(BitStream* bs, int lit) {
    init_fixed_tables();
    return bs_write_bits(bs, fixed_lit_codes[lit], fixed_lit_lens[lit]);
}

static int write_fixed_length(BitStream* bs, int length) {
    init_fixed_tables();
    
    /* Find length code */
    int code = 0;
    for (int i = 0; i < 29; i++) {
        if (length < LENGTH_BASE[i + 1] || i == 28) {
            code = 257 + i;
            break;
        }
    }
    
    int ret = bs_write_bits(bs, fixed_lit_codes[code], fixed_lit_lens[code]);
    if (ret != DEFLATE_OK) return ret;
    
    /* Write extra bits */
    int extra = LENGTH_EXTRA[code - 257];
    if (extra > 0) {
        ret = bs_write_bits(bs, length - LENGTH_BASE[code - 257], extra);
    }
    return ret;
}

static int write_fixed_distance(BitStream* bs, int dist) {
    /* Find distance code */
    int code = 0;
    for (int i = 0; i < 30; i++) {
        if (dist <= DIST_BASE[i + 1] || i == 29) {
            code = i;
            break;
        }
    }
    
    /* Distance codes are 5-bit reversed */
    uint8_t rev = 0;
    int c = code;
    for (int i = 0; i < 5; i++) {
        rev = (rev << 1) | (c & 1);
        c >>= 1;
    }
    
    int ret = bs_write_bits(bs, rev, 5);
    if (ret != DEFLATE_OK) return ret;
    
    /* Write extra bits */
    int extra = DIST_EXTRA[code];
    if (extra > 0) {
        ret = bs_write_bits(bs, dist - DIST_BASE[code], extra);
    }
    return ret;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LZ77 COMPRESSION
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    int16_t head[HASH_SIZE];
    int16_t prev[WINDOW_SIZE];
} HashChain;

static uint32_t hash3(const uint8_t* p) {
    return ((uint32_t)p[0] ^ ((uint32_t)p[1] << 4) ^ ((uint32_t)p[2] << 8)) & HASH_MASK;
}

static int find_match(const uint8_t* src, size_t pos, size_t src_len,
                      HashChain* hc, int max_chain, int* match_dist, int* match_len) {
    if (pos + MIN_MATCH > src_len) return 0;
    
    uint32_t h = hash3(src + pos);
    int best_len = MIN_MATCH - 1;
    int best_dist = 0;
    
    int chain_len = 0;
    int16_t idx = hc->head[h];
    
    while (idx >= 0 && chain_len < max_chain) {
        size_t p = (size_t)idx;
        if (pos - p > WINDOW_SIZE) break;
        
        /* Check for match */
        const uint8_t* a = src + pos;
        const uint8_t* b = src + p;
        int len = 0;
        size_t max_len = src_len - pos;
        if (max_len > MAX_MATCH) max_len = MAX_MATCH;
        
        while (len < (int)max_len && a[len] == b[len]) len++;
        
        if (len > best_len) {
            best_len = len;
            best_dist = (int)(pos - p);
            if (len >= MAX_MATCH) break;
        }
        
        idx = hc->prev[p & (WINDOW_SIZE - 1)];
        chain_len++;
    }
    
    if (best_len >= MIN_MATCH) {
        *match_dist = best_dist;
        *match_len = best_len;
        return 1;
    }
    return 0;
}

static void update_hash(HashChain* hc, const uint8_t* src, size_t pos) {
    uint32_t h = hash3(src + pos);
    hc->prev[pos & (WINDOW_SIZE - 1)] = hc->head[h];
    hc->head[h] = (int16_t)pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

size_t simjot_deflate_bound(size_t src_len) {
    /* Worst case: stored blocks with 5-byte headers every 65535 bytes */
    size_t blocks = (src_len + MAX_BLOCK_SIZE - 1) / MAX_BLOCK_SIZE;
    if (blocks == 0) blocks = 1;
    return src_len + blocks * 5 + 10;
}

int simjot_deflate(uint8_t* dest, size_t* dest_len,
                   const uint8_t* src, size_t src_len, int level) {
    if (!dest || !dest_len || !src) return DEFLATE_ERR_PARAM;
    if (*dest_len < simjot_deflate_bound(src_len)) return DEFLATE_ERR_BUF;
    
    BitStream bs;
    bs_init_write(&bs, dest, *dest_len);
    
    if (level == 0 || src_len < 16) {
        /* Store uncompressed (stored blocks) */
        size_t remaining = src_len;
        size_t offset = 0;
        
        while (remaining > 0) {
            size_t block_size = remaining > MAX_BLOCK_SIZE ? MAX_BLOCK_SIZE : remaining;
            int is_final = (remaining <= MAX_BLOCK_SIZE) ? 1 : 0;
            
            /* Block header: BFINAL=is_final, BTYPE=00 (stored) */
            int ret = bs_write_bits(&bs, is_final, 1);
            if (ret != DEFLATE_OK) return ret;
            ret = bs_write_bits(&bs, 0, 2);  /* BTYPE = 00 */
            if (ret != DEFLATE_OK) return ret;
            
            /* Align to byte boundary */
            ret = bs_flush(&bs);
            if (ret != DEFLATE_OK) return ret;
            
            /* LEN and NLEN */
            uint16_t len = (uint16_t)block_size;
            uint16_t nlen = ~len;
            
            if (bs.pos + 4 + block_size > bs.size) return DEFLATE_ERR_BUF;
            
            bs.data[bs.pos++] = len & 0xFF;
            bs.data[bs.pos++] = (len >> 8) & 0xFF;
            bs.data[bs.pos++] = nlen & 0xFF;
            bs.data[bs.pos++] = (nlen >> 8) & 0xFF;
            
            /* Copy data */
            memcpy(bs.data + bs.pos, src + offset, block_size);
            bs.pos += block_size;
            
            offset += block_size;
            remaining -= block_size;
        }
    } else {
        /* Compressed with fixed Huffman codes */
        HashChain* hc = NULL;
        int max_chain = 4;  /* Adjust based on level */
        
        if (level >= 4) {
            hc = (HashChain*)calloc(1, sizeof(HashChain));
            if (!hc) return DEFLATE_ERR_MEM;
            memset(hc->head, -1, sizeof(hc->head));
            max_chain = level * 4;
        }
        
        /* Single block with fixed Huffman */
        int ret = bs_write_bits(&bs, 1, 1);  /* BFINAL = 1 */
        if (ret != DEFLATE_OK) { free(hc); return ret; }
        ret = bs_write_bits(&bs, 1, 2);      /* BTYPE = 01 (fixed Huffman) */
        if (ret != DEFLATE_OK) { free(hc); return ret; }
        
        size_t pos = 0;
        while (pos < src_len) {
            int match_dist = 0, match_len = 0;
            int found = 0;
            
            if (hc && pos + MIN_MATCH <= src_len) {
                found = find_match(src, pos, src_len, hc, max_chain, &match_dist, &match_len);
            }
            
            if (found && match_len >= MIN_MATCH) {
                /* Emit length/distance pair */
                ret = write_fixed_length(&bs, match_len);
                if (ret != DEFLATE_OK) { free(hc); return ret; }
                ret = write_fixed_distance(&bs, match_dist);
                if (ret != DEFLATE_OK) { free(hc); return ret; }
                
                /* Update hash for all positions */
                if (hc) {
                    for (int i = 0; i < match_len; i++) {
                        if (pos + i + MIN_MATCH <= src_len) {
                            update_hash(hc, src, pos + i);
                        }
                    }
                }
                pos += match_len;
            } else {
                /* Emit literal */
                ret = write_fixed_literal(&bs, src[pos]);
                if (ret != DEFLATE_OK) { free(hc); return ret; }
                
                if (hc && pos + MIN_MATCH <= src_len) {
                    update_hash(hc, src, pos);
                }
                pos++;
            }
        }
        
        /* End of block marker (256) */
        ret = write_fixed_literal(&bs, 256);
        if (ret != DEFLATE_OK) { free(hc); return ret; }
        
        ret = bs_flush(&bs);
        if (ret != DEFLATE_OK) { free(hc); return ret; }
        
        free(hc);
    }
    
    *dest_len = bs.pos;
    return DEFLATE_OK;
}

int simjot_inflate(uint8_t* dest, size_t* dest_len,
                   const uint8_t* src, size_t src_len) {
    if (!dest || !dest_len || !src) return DEFLATE_ERR_PARAM;
    
    BitStream bs;
    bs_init_read(&bs, src, src_len);
    
    size_t out_pos = 0;
    size_t out_size = *dest_len;
    int is_final = 0;
    
    while (!is_final) {
        uint32_t bfinal, btype;
        
        if (bs_read_bits(&bs, 1, &bfinal) != DEFLATE_OK) return DEFLATE_ERR_DATA;
        if (bs_read_bits(&bs, 2, &btype) != DEFLATE_OK) return DEFLATE_ERR_DATA;
        
        is_final = bfinal;
        
        if (btype == 0) {
            /* Stored block */
            /* Align to byte boundary */
            bs.bits = 0;
            bs.nbits = 0;
            
            if (bs.pos + 4 > bs.size) return DEFLATE_ERR_DATA;
            
            uint16_t len = bs.data[bs.pos] | ((uint16_t)bs.data[bs.pos + 1] << 8);
            uint16_t nlen = bs.data[bs.pos + 2] | ((uint16_t)bs.data[bs.pos + 3] << 8);
            bs.pos += 4;
            
            if (len != (uint16_t)~nlen) return DEFLATE_ERR_DATA;
            if (bs.pos + len > bs.size) return DEFLATE_ERR_DATA;
            if (out_pos + len > out_size) return DEFLATE_ERR_BUF;
            
            memcpy(dest + out_pos, bs.data + bs.pos, len);
            bs.pos += len;
            out_pos += len;
            
        } else if (btype == 1) {
            /* Fixed Huffman codes */
            init_fixed_tables();
            
            while (1) {
                /* Decode literal/length */
                uint32_t code = 0;
                int len = 0;
                
                /* Read bits and decode using fixed table */
                while (len < 15) {
                    uint32_t bit;
                    if (bs_read_bits(&bs, 1, &bit) != DEFLATE_OK) return DEFLATE_ERR_DATA;
                    code = (code << 1) | bit;
                    len++;
                    
                    /* Check fixed code ranges */
                    int symbol = -1;
                    if (len == 7 && code >= 0 && code <= 23) {
                        symbol = 256 + code;  /* 256-279 */
                    } else if (len == 8 && code >= 48 && code <= 191) {
                        symbol = code - 48;   /* 0-143 */
                    } else if (len == 8 && code >= 192 && code <= 199) {
                        symbol = 280 + (code - 192);  /* 280-287 */
                    } else if (len == 9 && code >= 400 && code <= 511) {
                        symbol = 144 + (code - 400);  /* 144-255 */
                    }
                    
                    if (symbol >= 0) {
                        if (symbol == 256) {
                            goto block_end;
                        } else if (symbol < 256) {
                            if (out_pos >= out_size) return DEFLATE_ERR_BUF;
                            dest[out_pos++] = (uint8_t)symbol;
                        } else {
                            /* Length code */
                            int length_code = symbol - 257;
                            int length = LENGTH_BASE[length_code];
                            int extra = LENGTH_EXTRA[length_code];
                            if (extra > 0) {
                                uint32_t extra_bits;
                                if (bs_read_bits(&bs, extra, &extra_bits) != DEFLATE_OK)
                                    return DEFLATE_ERR_DATA;
                                length += extra_bits;
                            }
                            
                            /* Read 5-bit distance code */
                            uint32_t dist_code;
                            if (bs_read_bits(&bs, 5, &dist_code) != DEFLATE_OK)
                                return DEFLATE_ERR_DATA;
                            
                            /* Reverse bits */
                            int dc = 0;
                            for (int i = 0; i < 5; i++) {
                                dc = (dc << 1) | ((dist_code >> i) & 1);
                            }
                            
                            int dist = DIST_BASE[dc];
                            extra = DIST_EXTRA[dc];
                            if (extra > 0) {
                                uint32_t extra_bits;
                                if (bs_read_bits(&bs, extra, &extra_bits) != DEFLATE_OK)
                                    return DEFLATE_ERR_DATA;
                                dist += extra_bits;
                            }
                            
                            /* Copy from output buffer */
                            if (out_pos < (size_t)dist) return DEFLATE_ERR_DATA;
                            if (out_pos + length > out_size) return DEFLATE_ERR_BUF;
                            
                            for (int i = 0; i < length; i++) {
                                dest[out_pos] = dest[out_pos - dist];
                                out_pos++;
                            }
                        }
                        break;
                    }
                }
            }
            block_end:;
            
        } else if (btype == 2) {
            /* Dynamic Huffman - not implemented, return error */
            return DEFLATE_ERR_DATA;
        } else {
            return DEFLATE_ERR_DATA;
        }
    }
    
    *dest_len = out_pos;
    return DEFLATE_OK;
}
