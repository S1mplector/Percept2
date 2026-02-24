/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file encoding_utils.c
 * @brief Native Encoding Utilities for Simjot
 * 
 * Base64 encoding/decoding and Unicode handling.
 * Zero external dependencies.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * BASE64 ENCODING/DECODING
 * ═══════════════════════════════════════════════════════════════════════════ */

static const char BASE64_CHARS[] = 
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static const uint8_t BASE64_DECODE[256] = {
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,62,64,64,64,63,
    52,53,54,55,56,57,58,59,60,61,64,64,64,64,64,64,
    64, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
    15,16,17,18,19,20,21,22,23,24,25,64,64,64,64,64,
    64,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
    41,42,43,44,45,46,47,48,49,50,51,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
    64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64
};

/**
 * @brief Get required buffer size for Base64 encoding
 */
int32_t simjot_base64_encode_len(int32_t input_len) {
    return ((input_len + 2) / 3) * 4 + 1;
}

/**
 * @brief Get required buffer size for Base64 decoding
 */
int32_t simjot_base64_decode_len(int32_t input_len) {
    return (input_len * 3) / 4 + 1;
}

/**
 * @brief Encode data to Base64
 * 
 * @param input Input data
 * @param input_len Length of input
 * @param output Output buffer (must be at least simjot_base64_encode_len)
 * @param output_len Size of output buffer
 * @return Length of encoded string, or negative on error
 */
int32_t simjot_base64_encode(const uint8_t* input, int32_t input_len,
                             char* output, int32_t output_len) {
    if (!input || !output || input_len < 0 || output_len <= 0) return -1;
    
    int32_t needed = simjot_base64_encode_len(input_len);
    if (output_len < needed) return -2;
    
    int32_t out_pos = 0;
    int32_t i = 0;
    
    while (i < input_len) {
        uint32_t a = (i < input_len) ? input[i++] : 0;
        uint32_t b = (i < input_len) ? input[i++] : 0;
        uint32_t c = (i < input_len) ? input[i++] : 0;
        
        uint32_t triple = (a << 16) | (b << 8) | c;
        
        output[out_pos++] = BASE64_CHARS[(triple >> 18) & 0x3F];
        output[out_pos++] = BASE64_CHARS[(triple >> 12) & 0x3F];
        output[out_pos++] = BASE64_CHARS[(triple >> 6) & 0x3F];
        output[out_pos++] = BASE64_CHARS[triple & 0x3F];
    }
    
    /* Add padding */
    int mod = input_len % 3;
    if (mod == 1) {
        output[out_pos - 1] = '=';
        output[out_pos - 2] = '=';
    } else if (mod == 2) {
        output[out_pos - 1] = '=';
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Decode Base64 to data
 * 
 * @param input Base64 string
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of decoded data, or negative on error
 */
int32_t simjot_base64_decode(const char* input, uint8_t* output, int32_t output_len) {
    if (!input || !output || output_len <= 0) return -1;
    
    int32_t input_len = (int32_t)strlen(input);
    if (input_len == 0) return 0;
    if (input_len % 4 != 0) return -2;
    
    /* Count padding */
    int padding = 0;
    if (input[input_len - 1] == '=') padding++;
    if (input_len > 1 && input[input_len - 2] == '=') padding++;
    
    int32_t out_len = (input_len * 3) / 4 - padding;
    if (out_len > output_len) return -3;
    
    int32_t out_pos = 0;
    
    for (int32_t i = 0; i < input_len; i += 4) {
        uint8_t a = BASE64_DECODE[(unsigned char)input[i]];
        uint8_t b = BASE64_DECODE[(unsigned char)input[i + 1]];
        uint8_t c = BASE64_DECODE[(unsigned char)input[i + 2]];
        uint8_t d = BASE64_DECODE[(unsigned char)input[i + 3]];
        
        if (a == 64 || b == 64) return -4;
        
        uint32_t triple = (a << 18) | (b << 12) | (c << 6) | d;
        
        if (out_pos < out_len) output[out_pos++] = (triple >> 16) & 0xFF;
        if (out_pos < out_len) output[out_pos++] = (triple >> 8) & 0xFF;
        if (out_pos < out_len) output[out_pos++] = triple & 0xFF;
    }
    
    return out_len;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * URL-SAFE BASE64
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Encode to URL-safe Base64 (+ -> -, / -> _, no padding)
 */
int32_t simjot_base64url_encode(const uint8_t* input, int32_t input_len,
                                char* output, int32_t output_len) {
    int32_t len = simjot_base64_encode(input, input_len, output, output_len);
    if (len < 0) return len;
    
    /* Convert to URL-safe and remove padding */
    for (int32_t i = 0; i < len; i++) {
        if (output[i] == '+') output[i] = '-';
        else if (output[i] == '/') output[i] = '_';
    }
    
    /* Remove padding */
    while (len > 0 && output[len - 1] == '=') {
        output[--len] = '\0';
    }
    
    return len;
}

/**
 * @brief Decode URL-safe Base64
 */
int32_t simjot_base64url_decode(const char* input, uint8_t* output, int32_t output_len) {
    if (!input) return -1;
    
    int32_t len = (int32_t)strlen(input);
    
    /* Convert back to standard Base64 */
    char* temp = (char*)malloc(len + 4 + 1);
    if (!temp) return -5;
    
    for (int32_t i = 0; i < len; i++) {
        if (input[i] == '-') temp[i] = '+';
        else if (input[i] == '_') temp[i] = '/';
        else temp[i] = input[i];
    }
    
    /* Add padding */
    int pad = (4 - (len % 4)) % 4;
    for (int i = 0; i < pad; i++) {
        temp[len + i] = '=';
    }
    temp[len + pad] = '\0';
    
    int32_t result = simjot_base64_decode(temp, output, output_len);
    free(temp);
    return result;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * UNICODE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Get UTF-8 string length in characters (not bytes)
 */
int32_t simjot_utf8_strlen(const char* str) {
    if (!str) return 0;
    
    int32_t len = 0;
    while (*str) {
        if ((*str & 0xC0) != 0x80) len++;  /* Count non-continuation bytes */
        str++;
    }
    return len;
}

/**
 * @brief Check if string is valid UTF-8
 */
int32_t simjot_utf8_valid(const char* str) {
    if (!str) return 0;
    
    const unsigned char* s = (const unsigned char*)str;
    while (*s) {
        if (*s < 0x80) {
            s++;
        } else if ((*s & 0xE0) == 0xC0) {
            if ((s[1] & 0xC0) != 0x80) return 0;
            s += 2;
        } else if ((*s & 0xF0) == 0xE0) {
            if ((s[1] & 0xC0) != 0x80 || (s[2] & 0xC0) != 0x80) return 0;
            s += 3;
        } else if ((*s & 0xF8) == 0xF0) {
            if ((s[1] & 0xC0) != 0x80 || (s[2] & 0xC0) != 0x80 || (s[3] & 0xC0) != 0x80) return 0;
            s += 4;
        } else {
            return 0;  /* Invalid UTF-8 lead byte */
        }
    }
    return 1;
}

/**
 * @brief Convert UTF-8 codepoint to character
 * @return Number of bytes written, or negative on error
 */
int32_t simjot_utf8_encode_codepoint(uint32_t codepoint, char* output, int32_t output_len) {
    if (!output || output_len < 1) return -1;
    
    if (codepoint < 0x80) {
        if (output_len < 1) return -2;
        output[0] = (char)codepoint;
        return 1;
    } else if (codepoint < 0x800) {
        if (output_len < 2) return -2;
        output[0] = (char)(0xC0 | (codepoint >> 6));
        output[1] = (char)(0x80 | (codepoint & 0x3F));
        return 2;
    } else if (codepoint < 0x10000) {
        if (output_len < 3) return -2;
        output[0] = (char)(0xE0 | (codepoint >> 12));
        output[1] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
        output[2] = (char)(0x80 | (codepoint & 0x3F));
        return 3;
    } else if (codepoint < 0x110000) {
        if (output_len < 4) return -2;
        output[0] = (char)(0xF0 | (codepoint >> 18));
        output[1] = (char)(0x80 | ((codepoint >> 12) & 0x3F));
        output[2] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
        output[3] = (char)(0x80 | (codepoint & 0x3F));
        return 4;
    }
    return -3;  /* Invalid codepoint */
}

/**
 * @brief Decode UTF-8 character to codepoint
 * @return Codepoint, or negative on error
 */
int32_t simjot_utf8_decode_codepoint(const char* str, int32_t* bytes_consumed) {
    if (!str || !*str) return -1;
    
    const unsigned char* s = (const unsigned char*)str;
    uint32_t cp;
    int len;
    
    if (*s < 0x80) {
        cp = *s;
        len = 1;
    } else if ((*s & 0xE0) == 0xC0) {
        if ((s[1] & 0xC0) != 0x80) return -2;
        cp = ((s[0] & 0x1F) << 6) | (s[1] & 0x3F);
        len = 2;
    } else if ((*s & 0xF0) == 0xE0) {
        if ((s[1] & 0xC0) != 0x80 || (s[2] & 0xC0) != 0x80) return -2;
        cp = ((s[0] & 0x0F) << 12) | ((s[1] & 0x3F) << 6) | (s[2] & 0x3F);
        len = 3;
    } else if ((*s & 0xF8) == 0xF0) {
        if ((s[1] & 0xC0) != 0x80 || (s[2] & 0xC0) != 0x80 || (s[3] & 0xC0) != 0x80) return -2;
        cp = ((s[0] & 0x07) << 18) | ((s[1] & 0x3F) << 12) | ((s[2] & 0x3F) << 6) | (s[3] & 0x3F);
        len = 4;
    } else {
        return -3;
    }
    
    if (bytes_consumed) *bytes_consumed = len;
    return (int32_t)cp;
}

/**
 * @brief Decode Unicode escape sequences (\uXXXX) in string
 */
int32_t simjot_unicode_unescape(const char* input, char* output, int32_t output_len) {
    if (!input || !output || output_len <= 0) return -1;
    
    int32_t out_pos = 0;
    const char* p = input;
    
    while (*p && out_pos < output_len - 4) {
        if (p[0] == '\\' && p[1] == 'u' && p[2] && p[3] && p[4] && p[5]) {
            /* Parse hex */
            char hex[5] = {p[2], p[3], p[4], p[5], 0};
            char* endptr;
            uint32_t cp = (uint32_t)strtoul(hex, &endptr, 16);
            
            if (*endptr == '\0') {
                int32_t len = simjot_utf8_encode_codepoint(cp, output + out_pos, output_len - out_pos);
                if (len > 0) {
                    out_pos += len;
                    p += 6;
                    continue;
                }
            }
        }
        output[out_pos++] = *p++;
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * HEX ENCODING
 * ═══════════════════════════════════════════════════════════════════════════ */

static const char HEX_CHARS[] = "0123456789abcdef";

/**
 * @brief Encode bytes to hex string
 */
int32_t simjot_hex_encode(const uint8_t* input, int32_t input_len,
                          char* output, int32_t output_len) {
    if (!input || !output || input_len < 0 || output_len <= 0) return -1;
    if (output_len < input_len * 2 + 1) return -2;
    
    for (int32_t i = 0; i < input_len; i++) {
        output[i * 2] = HEX_CHARS[(input[i] >> 4) & 0x0F];
        output[i * 2 + 1] = HEX_CHARS[input[i] & 0x0F];
    }
    output[input_len * 2] = '\0';
    return input_len * 2;
}

/**
 * @brief Decode hex string to bytes
 */
int32_t simjot_hex_decode(const char* input, uint8_t* output, int32_t output_len) {
    if (!input || !output || output_len <= 0) return -1;
    
    int32_t len = (int32_t)strlen(input);
    if (len % 2 != 0) return -2;
    if (output_len < len / 2) return -3;
    
    for (int32_t i = 0; i < len / 2; i++) {
        char c1 = input[i * 2];
        char c2 = input[i * 2 + 1];
        
        uint8_t v1, v2;
        if (c1 >= '0' && c1 <= '9') v1 = c1 - '0';
        else if (c1 >= 'a' && c1 <= 'f') v1 = c1 - 'a' + 10;
        else if (c1 >= 'A' && c1 <= 'F') v1 = c1 - 'A' + 10;
        else return -4;
        
        if (c2 >= '0' && c2 <= '9') v2 = c2 - '0';
        else if (c2 >= 'a' && c2 <= 'f') v2 = c2 - 'a' + 10;
        else if (c2 >= 'A' && c2 <= 'F') v2 = c2 - 'A' + 10;
        else return -4;
        
        output[i] = (v1 << 4) | v2;
    }
    
    return len / 2;
}
