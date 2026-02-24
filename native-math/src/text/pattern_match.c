/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file pattern_match.c
 * @brief Native Pattern Matching Utilities for Simjot
 * 
 * Lightweight pattern matching for common use cases without full regex.
 * Optimized for the specific patterns used in Simjot (fact extraction,
 * word boundaries, simple wildcards).
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
 * SIMPLE PATTERN MATCHING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Match simple wildcard pattern (* and ?)
 * 
 * @param str String to match
 * @param pattern Pattern with * (any chars) and ? (single char)
 * @return 1 if matches, 0 otherwise
 */
int32_t simjot_pattern_match_wildcard(const char* str, const char* pattern) {
    if (!str || !pattern) return 0;
    
    const char* s = str;
    const char* p = pattern;
    const char* star = NULL;
    const char* ss = str;
    
    while (*s) {
        if (*p == '?' || tolower((unsigned char)*p) == tolower((unsigned char)*s)) {
            s++;
            p++;
        } else if (*p == '*') {
            star = p++;
            ss = s;
        } else if (star) {
            p = star + 1;
            s = ++ss;
        } else {
            return 0;
        }
    }
    
    while (*p == '*') p++;
    return *p == '\0';
}

/**
 * @brief Find pattern in text (case-insensitive, with word boundary option)
 * 
 * @param text Text to search
 * @param pattern Pattern to find
 * @param word_boundary If 1, match only at word boundaries
 * @return Position of match (0-indexed), or -1 if not found
 */
int32_t simjot_pattern_find(const char* text, const char* pattern, int32_t word_boundary) {
    if (!text || !pattern || !*pattern) return -1;
    
    size_t tlen = strlen(text);
    size_t plen = strlen(pattern);
    
    if (plen > tlen) return -1;
    
    for (size_t i = 0; i <= tlen - plen; i++) {
        /* Check word boundary at start */
        if (word_boundary && i > 0 && isalnum((unsigned char)text[i - 1])) {
            continue;
        }
        
        /* Case-insensitive compare */
        int match = 1;
        for (size_t j = 0; j < plen; j++) {
            if (tolower((unsigned char)text[i + j]) != tolower((unsigned char)pattern[j])) {
                match = 0;
                break;
            }
        }
        
        if (match) {
            /* Check word boundary at end */
            if (word_boundary && i + plen < tlen && isalnum((unsigned char)text[i + plen])) {
                continue;
            }
            return (int32_t)i;
        }
    }
    
    return -1;
}

/**
 * @brief Count occurrences of pattern in text
 */
int32_t simjot_pattern_count(const char* text, const char* pattern, int32_t word_boundary) {
    if (!text || !pattern || !*pattern) return 0;
    
    int32_t count = 0;
    const char* p = text;
    size_t plen = strlen(pattern);
    
    while (*p) {
        int32_t pos = simjot_pattern_find(p, pattern, word_boundary);
        if (pos < 0) break;
        count++;
        p += pos + plen;
    }
    
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PHRASE EXTRACTION (for PersistentMemoryStore patterns)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Extract phrase after a prefix pattern
 * 
 * Example: simjot_pattern_extract_after(text, "I like ", buf, 256, 60)
 * From "I like chocolate" -> "chocolate"
 * 
 * @param text Text to search
 * @param prefix Prefix to find (case-insensitive)
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @param max_phrase_len Maximum phrase length to extract
 * @return Length of extracted phrase, or -1 if not found
 */
int32_t simjot_pattern_extract_after(const char* text, const char* prefix,
                                      char* output, int32_t output_len,
                                      int32_t max_phrase_len) {
    if (!text || !prefix || !output || output_len <= 0) return -1;
    
    int32_t pos = simjot_pattern_find(text, prefix, 1);
    if (pos < 0) return -1;
    
    const char* start = text + pos + strlen(prefix);
    
    /* Skip leading whitespace */
    while (*start && isspace((unsigned char)*start)) start++;
    
    /* Find end (sentence boundary or max length) */
    int32_t len = 0;
    while (start[len] && len < max_phrase_len && len < output_len - 1) {
        char c = start[len];
        if (c == '.' || c == '!' || c == '?' || c == '\n') break;
        len++;
    }
    
    /* Trim trailing whitespace */
    while (len > 0 && isspace((unsigned char)start[len - 1])) len--;
    
    memcpy(output, start, len);
    output[len] = '\0';
    return len;
}

/**
 * @brief Extract all matches of prefix pattern from text
 * 
 * Returns matches as newline-separated strings.
 * 
 * @param text Text to search
 * @param prefix Prefix pattern
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @param max_phrase_len Maximum length per phrase
 * @return Number of matches found
 */
int32_t simjot_pattern_extract_all(const char* text, const char* prefix,
                                    char* output, int32_t output_len,
                                    int32_t max_phrase_len) {
    if (!text || !prefix || !output || output_len <= 0) return 0;
    
    int32_t count = 0;
    int32_t out_pos = 0;
    const char* p = text;
    size_t prefix_len = strlen(prefix);
    char phrase[512];
    
    while (*p) {
        int32_t len = simjot_pattern_extract_after(p, prefix, phrase, sizeof(phrase), max_phrase_len);
        if (len > 0) {
            /* Add to output */
            if (out_pos + len + 1 < output_len) {
                if (out_pos > 0) output[out_pos++] = '\n';
                memcpy(output + out_pos, phrase, len);
                out_pos += len;
                count++;
            }
            /* Move past this match */
            int32_t pos = simjot_pattern_find(p, prefix, 1);
            p += pos + prefix_len + len;
        } else {
            /* Move to next word */
            while (*p && !isspace((unsigned char)*p)) p++;
            while (*p && isspace((unsigned char)*p)) p++;
        }
        
        if (count >= 24) break; /* Cap matches */
    }
    
    output[out_pos] = '\0';
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * WORD EXTRACTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Extract words matching a pattern from text
 * 
 * @param text Text to search
 * @param pattern Pattern to match words against
 * @param output Output buffer (newline-separated)
 * @param output_len Size of output buffer
 * @return Number of words extracted
 */
int32_t simjot_pattern_extract_words(const char* text, const char* pattern,
                                      char* output, int32_t output_len) {
    if (!text || !output || output_len <= 0) return 0;
    
    int32_t count = 0;
    int32_t out_pos = 0;
    const char* p = text;
    
    while (*p) {
        /* Skip to word start */
        while (*p && !isalnum((unsigned char)*p)) p++;
        if (!*p) break;
        
        /* Find word end */
        const char* ws = p;
        while (*p && (isalnum((unsigned char)*p) || *p == '\'')) p++;
        int32_t wlen = (int32_t)(p - ws);
        
        /* Check if word matches pattern (or no pattern = all words) */
        int match = 1;
        if (pattern && *pattern) {
            char word[256];
            if (wlen < (int32_t)sizeof(word)) {
                memcpy(word, ws, wlen);
                word[wlen] = '\0';
                match = simjot_pattern_match_wildcard(word, pattern);
            } else {
                match = 0;
            }
        }
        
        if (match && wlen > 0) {
            if (out_pos + wlen + 1 < output_len) {
                if (out_pos > 0) output[out_pos++] = '\n';
                memcpy(output + out_pos, ws, wlen);
                out_pos += wlen;
                count++;
            }
        }
    }
    
    output[out_pos] = '\0';
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * REPLACEMENT
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Replace all occurrences of pattern with replacement
 * 
 * @param text Text to modify
 * @param pattern Pattern to find
 * @param replacement Replacement string
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of result, or negative on error
 */
int32_t simjot_pattern_replace_all(const char* text, const char* pattern,
                                    const char* replacement, char* output,
                                    int32_t output_len) {
    if (!text || !pattern || !replacement || !output || output_len <= 0) return -1;
    
    size_t plen = strlen(pattern);
    size_t rlen = strlen(replacement);
    
    if (plen == 0) {
        /* Empty pattern - just copy */
        size_t tlen = strlen(text);
        if ((int32_t)tlen >= output_len) return -2;
        strcpy(output, text);
        return (int32_t)tlen;
    }
    
    int32_t out_pos = 0;
    const char* p = text;
    
    while (*p) {
        /* Case-insensitive search */
        int match = 1;
        for (size_t i = 0; i < plen && p[i]; i++) {
            if (tolower((unsigned char)p[i]) != tolower((unsigned char)pattern[i])) {
                match = 0;
                break;
            }
        }
        
        if (match && p[plen - 1]) {
            /* Copy replacement */
            if (out_pos + (int32_t)rlen >= output_len) return -2;
            memcpy(output + out_pos, replacement, rlen);
            out_pos += (int32_t)rlen;
            p += plen;
        } else {
            /* Copy character */
            if (out_pos >= output_len - 1) return -2;
            output[out_pos++] = *p++;
        }
    }
    
    output[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Collapse multiple spaces to single space
 */
int32_t simjot_pattern_collapse_spaces(const char* text, char* output, int32_t output_len) {
    if (!text || !output || output_len <= 0) return -1;
    
    int32_t out_pos = 0;
    int in_space = 0;
    
    /* Skip leading spaces */
    while (*text && isspace((unsigned char)*text)) text++;
    
    while (*text && out_pos < output_len - 1) {
        if (isspace((unsigned char)*text)) {
            if (!in_space) {
                output[out_pos++] = ' ';
                in_space = 1;
            }
        } else {
            output[out_pos++] = *text;
            in_space = 0;
        }
        text++;
    }
    
    /* Trim trailing space */
    if (out_pos > 0 && output[out_pos - 1] == ' ') out_pos--;
    
    output[out_pos] = '\0';
    return out_pos;
}
