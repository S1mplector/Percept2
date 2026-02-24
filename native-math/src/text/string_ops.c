/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file string_ops.c
 * @brief High-Performance String Operations for Simjot
 * 
 * Native implementations of common string operations that are
 * memory-intensive in Java due to immutable strings and regex overhead.
 * 
 * Operations:
 * - Whitespace normalization (collapse multiple spaces)
 * - String sanitization (newlines to spaces, trim, truncate)
 * - Fast string hashing (for cache keys)
 * - Word/token extraction
 * - Pattern matching helpers
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING SANITIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Sanitize string: newlines to spaces, collapse whitespace, trim
 * 
 * Equivalent to Java:
 *   s.replace('\n', ' ').replace('\r', ' ')
 *    .replaceAll("\\s+", " ").trim()
 * 
 * @param input Input string (UTF-8)
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @param max_len Maximum output length (0 = no limit, truncate with ellipsis)
 * @return Length of sanitized string, or negative on error
 */
int32_t simjot_string_sanitize(const char* input, char* output, 
                                int32_t output_len, int32_t max_len) {
    if (!input || !output || output_len <= 0) return -1;
    
    int32_t out_pos = 0;
    int32_t effective_max = (max_len > 0 && max_len < output_len - 4) 
                           ? max_len : (output_len - 4);
    int in_whitespace = 1;  /* Start true to skip leading whitespace */
    int truncated = 0;
    
    const unsigned char* p = (const unsigned char*)input;
    while (*p && out_pos < effective_max) {
        unsigned char c = *p++;
        
        /* Convert newlines and tabs to space */
        if (c == '\n' || c == '\r' || c == '\t') {
            c = ' ';
        }
        
        /* Collapse whitespace */
        if (isspace(c)) {
            if (!in_whitespace && out_pos < effective_max) {
                output[out_pos++] = ' ';
                in_whitespace = 1;
            }
        } else {
            output[out_pos++] = c;
            in_whitespace = 0;
        }
    }
    
    /* Check if we truncated */
    if (*p != '\0') {
        truncated = 1;
    }
    
    /* Trim trailing whitespace */
    while (out_pos > 0 && isspace((unsigned char)output[out_pos - 1])) {
        out_pos--;
    }
    
    /* Add ellipsis if truncated and max_len was specified */
    if (truncated && max_len > 0 && out_pos + 3 < output_len) {
        /* UTF-8 ellipsis: E2 80 A6 or just "..." */
        output[out_pos++] = '.';
        output[out_pos++] = '.';
        output[out_pos++] = '.';
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Collapse whitespace in-place (returns new length)
 * 
 * @param str String to modify (must be writable)
 * @return New length of string
 */
int32_t simjot_string_collapse_whitespace(char* str) {
    if (!str) return 0;
    
    char* dst = str;
    const char* src = str;
    int in_ws = 1;
    
    /* Skip leading whitespace */
    while (*src && isspace((unsigned char)*src)) src++;
    
    while (*src) {
        if (isspace((unsigned char)*src)) {
            if (!in_ws) {
                *dst++ = ' ';
                in_ws = 1;
            }
            src++;
        } else {
            *dst++ = *src++;
            in_ws = 0;
        }
    }
    
    /* Trim trailing space */
    if (dst > str && isspace((unsigned char)*(dst - 1))) {
        dst--;
    }
    
    *dst = '\0';
    return (int32_t)(dst - str);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING HASHING (for cache keys)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Fast string hash (FNV-1a)
 * 
 * Good distribution for hash tables and cache keys.
 * 
 * @param str Input string
 * @return 64-bit hash value
 */
uint64_t simjot_string_hash(const char* str) {
    if (!str) return 0;
    
    uint64_t hash = 14695981039346656037ULL;  /* FNV offset basis */
    while (*str) {
        hash ^= (uint64_t)(unsigned char)*str++;
        hash *= 1099511628211ULL;  /* FNV prime */
    }
    return hash;
}

/**
 * @brief Hash multiple strings together (for composite cache keys)
 * 
 * @param strings Array of string pointers
 * @param count Number of strings
 * @return Combined 64-bit hash
 */
uint64_t simjot_string_hash_multi(const char** strings, int32_t count) {
    uint64_t hash = 14695981039346656037ULL;
    
    for (int32_t i = 0; i < count; i++) {
        if (strings[i]) {
            const char* s = strings[i];
            while (*s) {
                hash ^= (uint64_t)(unsigned char)*s++;
                hash *= 1099511628211ULL;
            }
        }
        /* Add separator between strings */
        hash ^= 0xFF;
        hash *= 1099511628211ULL;
    }
    
    return hash;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TOKEN/WORD OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Count tokens (words) in text
 * 
 * @param text Input text
 * @return Number of whitespace-separated tokens
 */
int32_t simjot_string_token_count(const char* text) {
    if (!text) return 0;
    
    int32_t count = 0;
    int in_word = 0;
    
    while (*text) {
        if (isspace((unsigned char)*text)) {
            in_word = 0;
        } else {
            if (!in_word) {
                count++;
                in_word = 1;
            }
        }
        text++;
    }
    
    return count;
}

/**
 * @brief Extract first N tokens from text
 * 
 * @param text Input text
 * @param output Output buffer
 * @param output_len Output buffer size
 * @param max_tokens Maximum tokens to extract
 * @return Number of tokens extracted
 */
int32_t simjot_string_first_tokens(const char* text, char* output, 
                                    int32_t output_len, int32_t max_tokens) {
    if (!text || !output || output_len <= 0 || max_tokens <= 0) return 0;
    
    int32_t out_pos = 0;
    int32_t token_count = 0;
    int in_word = 0;
    
    /* Skip leading whitespace */
    while (*text && isspace((unsigned char)*text)) text++;
    
    while (*text && token_count < max_tokens && out_pos < output_len - 1) {
        if (isspace((unsigned char)*text)) {
            if (in_word) {
                token_count++;
                if (token_count < max_tokens && out_pos < output_len - 1) {
                    output[out_pos++] = ' ';
                }
                in_word = 0;
            }
            text++;
        } else {
            output[out_pos++] = *text++;
            in_word = 1;
        }
    }
    
    if (in_word) token_count++;
    
    /* Trim trailing space */
    while (out_pos > 0 && isspace((unsigned char)output[out_pos - 1])) {
        out_pos--;
    }
    
    output[out_pos] = '\0';
    return token_count;
}

/**
 * @brief Extract last N tokens from text
 * 
 * Useful for getting recent context from a buffer.
 * 
 * @param text Input text
 * @param output Output buffer
 * @param output_len Output buffer size
 * @param max_tokens Maximum tokens to extract
 * @return Number of tokens extracted
 */
int32_t simjot_string_last_tokens(const char* text, char* output, 
                                   int32_t output_len, int32_t max_tokens) {
    if (!text || !output || output_len <= 0 || max_tokens <= 0) return 0;
    
    int len = (int)strlen(text);
    if (len == 0) {
        output[0] = '\0';
        return 0;
    }
    
    /* Find token boundaries from the end */
    int token_starts[256];  /* Max 256 tokens tracked */
    int token_count = 0;
    int in_word = 0;
    
    for (int i = len - 1; i >= 0 && token_count < 256; i--) {
        if (isspace((unsigned char)text[i])) {
            in_word = 0;
        } else {
            if (!in_word) {
                in_word = 1;
            }
            if (i == 0 || isspace((unsigned char)text[i - 1])) {
                token_starts[token_count++] = i;
            }
        }
    }
    
    /* Copy last max_tokens */
    int start_idx = token_count > max_tokens ? max_tokens : token_count;
    int start_pos = start_idx > 0 ? token_starts[start_idx - 1] : 0;
    
    int32_t out_pos = 0;
    int copied_tokens = 0;
    in_word = 0;
    
    for (int i = start_pos; text[i] && out_pos < output_len - 1; i++) {
        if (isspace((unsigned char)text[i])) {
            if (in_word && out_pos < output_len - 1) {
                output[out_pos++] = ' ';
                in_word = 0;
            }
        } else {
            output[out_pos++] = text[i];
            if (!in_word) {
                copied_tokens++;
                in_word = 1;
            }
        }
    }
    
    /* Trim trailing space */
    while (out_pos > 0 && isspace((unsigned char)output[out_pos - 1])) {
        out_pos--;
    }
    
    output[out_pos] = '\0';
    return copied_tokens > max_tokens ? max_tokens : copied_tokens;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING COMPARISON AND SEARCH
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Case-insensitive string contains check
 * 
 * @param haystack String to search in
 * @param needle String to search for
 * @return 1 if found, 0 otherwise
 */
int32_t simjot_string_contains_ci(const char* haystack, const char* needle) {
    if (!haystack || !needle) return 0;
    if (!*needle) return 1;  /* Empty needle matches everything */
    
    size_t needle_len = strlen(needle);
    size_t haystack_len = strlen(haystack);
    
    if (needle_len > haystack_len) return 0;
    
    for (size_t i = 0; i <= haystack_len - needle_len; i++) {
        int match = 1;
        for (size_t j = 0; j < needle_len; j++) {
            if (tolower((unsigned char)haystack[i + j]) != 
                tolower((unsigned char)needle[j])) {
                match = 0;
                break;
            }
        }
        if (match) return 1;
    }
    
    return 0;
}

/**
 * @brief Check if string starts with prefix (case-insensitive)
 * 
 * @param str String to check
 * @param prefix Prefix to look for
 * @return 1 if starts with prefix, 0 otherwise
 */
int32_t simjot_string_starts_with_ci(const char* str, const char* prefix) {
    if (!str || !prefix) return 0;
    
    while (*prefix) {
        if (!*str) return 0;
        if (tolower((unsigned char)*str) != tolower((unsigned char)*prefix)) {
            return 0;
        }
        str++;
        prefix++;
    }
    
    return 1;
}

/**
 * @brief Join strings with separator
 * 
 * @param strings Array of string pointers
 * @param count Number of strings
 * @param separator Separator string
 * @param output Output buffer
 * @param output_len Output buffer size
 * @return Length of result, or negative if buffer too small
 */
int32_t simjot_string_join(const char** strings, int32_t count,
                           const char* separator, char* output, int32_t output_len) {
    if (!output || output_len <= 0) return -1;
    if (!strings || count <= 0) {
        output[0] = '\0';
        return 0;
    }
    
    int32_t out_pos = 0;
    int sep_len = separator ? (int)strlen(separator) : 0;
    
    for (int32_t i = 0; i < count; i++) {
        /* Add separator between strings */
        if (i > 0 && sep_len > 0) {
            if (out_pos + sep_len >= output_len) return -(out_pos + sep_len + 1);
            memcpy(output + out_pos, separator, sep_len);
            out_pos += sep_len;
        }
        
        /* Add string */
        if (strings[i]) {
            int slen = (int)strlen(strings[i]);
            if (out_pos + slen >= output_len) return -(out_pos + slen + 1);
            memcpy(output + out_pos, strings[i], slen);
            out_pos += slen;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BUFFER OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Circular buffer append with size limit
 * 
 * Appends new content to buffer, removing old content from the front
 * if the buffer would exceed max_size.
 * 
 * @param buffer Current buffer content (modified in place)
 * @param buffer_len Buffer allocation size
 * @param max_size Maximum content size to keep
 * @param append String to append
 * @param separator Separator between old and new content
 * @return New content length
 */
int32_t simjot_buffer_append_circular(char* buffer, int32_t buffer_len,
                                       int32_t max_size, const char* append,
                                       const char* separator) {
    if (!buffer || buffer_len <= 0) return -1;
    if (!append || !*append) return (int32_t)strlen(buffer);
    
    int cur_len = (int)strlen(buffer);
    int append_len = (int)strlen(append);
    int sep_len = separator ? (int)strlen(separator) : 0;
    
    int needed = cur_len + (cur_len > 0 ? sep_len : 0) + append_len;
    
    /* If would exceed max_size, trim from front */
    if (needed > max_size && max_size < buffer_len) {
        int excess = needed - max_size;
        
        /* Find a word boundary to cut at */
        int cut_at = excess;
        while (cut_at < cur_len && !isspace((unsigned char)buffer[cut_at])) {
            cut_at++;
        }
        /* Skip whitespace */
        while (cut_at < cur_len && isspace((unsigned char)buffer[cut_at])) {
            cut_at++;
        }
        
        if (cut_at < cur_len) {
            memmove(buffer, buffer + cut_at, cur_len - cut_at + 1);
            cur_len = cur_len - cut_at;
        } else {
            buffer[0] = '\0';
            cur_len = 0;
        }
    }
    
    /* Check if we have room */
    int final_len = cur_len + (cur_len > 0 ? sep_len : 0) + append_len;
    if (final_len >= buffer_len) {
        return -1;  /* Buffer too small */
    }
    
    /* Add separator if there's existing content */
    if (cur_len > 0 && sep_len > 0) {
        memcpy(buffer + cur_len, separator, sep_len);
        cur_len += sep_len;
    }
    
    /* Add new content */
    memcpy(buffer + cur_len, append, append_len);
    cur_len += append_len;
    buffer[cur_len] = '\0';
    
    return cur_len;
}
