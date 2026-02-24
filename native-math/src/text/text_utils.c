/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file text_utils.c
 * @brief Native Text Processing Utilities for Simjot
 * 
 * High-performance text processing operations including:
 * - Word tokenization and extraction
 * - Character classification
 * - Text statistics (word count, char count, sentence count)
 * - Fuzzy string matching
 * - Text normalization
 * 
 * These operations are performance-critical for real-time spell checking,
 * poetry analysis, and autocomplete features.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TEXT STATISTICS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Count words in text
 * 
 * Words are defined as sequences of letters and apostrophes.
 * 
 * @param text Input text (UTF-8)
 * @return Number of words
 */
int32_t simjot_text_word_count(const char* text) {
    if (!text) return 0;
    
    int32_t count = 0;
    int in_word = 0;
    
    while (*text) {
        unsigned char c = (unsigned char)*text++;
        int is_word_char = isalpha(c) || c == '\'';
        
        if (is_word_char && !in_word) {
            count++;
            in_word = 1;
        } else if (!is_word_char) {
            in_word = 0;
        }
    }
    
    return count;
}

/**
 * @brief Count sentences in text
 * 
 * Sentences end with '.', '!', or '?'
 * Handles abbreviations and decimals heuristically.
 * 
 * @param text Input text (UTF-8)
 * @return Number of sentences
 */
int32_t simjot_text_sentence_count(const char* text) {
    if (!text) return 0;
    
    int32_t count = 0;
    int prev_was_space = 1;
    
    while (*text) {
        char c = *text++;
        
        if (c == '.' || c == '!' || c == '?') {
            /* Check if followed by space or end (not abbreviation) */
            if (*text == '\0' || isspace((unsigned char)*text) || *text == '"' || *text == '\'') {
                count++;
            }
        }
        prev_was_space = isspace((unsigned char)c);
    }
    
    return count > 0 ? count : (simjot_text_word_count(text) > 0 ? 1 : 0);
}

/**
 * @brief Count characters in text (excluding whitespace)
 * 
 * @param text Input text (UTF-8)
 * @param include_spaces If non-zero, include whitespace in count
 * @return Character count
 */
int32_t simjot_text_char_count(const char* text, int32_t include_spaces) {
    if (!text) return 0;
    
    int32_t count = 0;
    while (*text) {
        unsigned char c = (unsigned char)*text++;
        if (include_spaces || !isspace(c)) {
            /* Handle UTF-8 multi-byte sequences */
            if ((c & 0xC0) != 0x80) {  /* Not a continuation byte */
                count++;
            }
        }
    }
    
    return count;
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WORD EXTRACTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Extract words from text into buffer
 * 
 * Words are separated by newlines in the output buffer.
 * 
 * @param text Input text
 * @param out Output buffer
 * @param out_len Size of output buffer
 * @return Number of bytes written, or negative if buffer too small
 */
int32_t simjot_text_extract_words(const char* text, char* out, int32_t out_len) {
    if (!text || !out || out_len <= 0) return 0;
    
    int32_t written = 0;
    int32_t word_start = -1;
    const char* ptr = text;
    int idx = 0;
    
    while (*ptr) {
        unsigned char c = (unsigned char)*ptr;
        int is_word_char = isalpha(c) || c == '\'';
        
        if (is_word_char) {
            if (word_start < 0) word_start = idx;
        } else if (word_start >= 0) {
            /* End of word - copy it */
            int word_len = idx - word_start;
            if (written + word_len + 1 >= out_len) {
                return -(written + word_len + 10);  /* Need more space */
            }
            if (written > 0) out[written++] = '\n';
            memcpy(out + written, text + word_start, word_len);
            written += word_len;
            word_start = -1;
        }
        
        ptr++;
        idx++;
    }
    
    /* Handle last word */
    if (word_start >= 0) {
        int word_len = idx - word_start;
        if (written + word_len + 1 >= out_len) {
            return -(written + word_len + 10);
        }
        if (written > 0) out[written++] = '\n';
        memcpy(out + written, text + word_start, word_len);
        written += word_len;
    }
    
    return written;
}

/**
 * @brief Extract hashtag-style tags (#tag) from text.
 *
 * Tags are sequences of [A-Za-z0-9_-] preceded by '#'.
 * Results are newline-separated and lowercased.
 *
 * @param text Input text
 * @param out Output buffer
 * @param out_len Size of output buffer
 * @return Number of bytes written
 */
int32_t simjot_text_extract_tags(const char* text, char* out, int32_t out_len) {
    if (!text || !out || out_len <= 0) return 0;

    int32_t out_pos = 0;
    const char* p = text;

    while (*p) {
        if (*p == '#') {
            const char* start = p + 1;
            int32_t len = 0;

            while (start[len]) {
                unsigned char c = (unsigned char)start[len];
                if (isalnum(c) || c == '_' || c == '-') {
                    len++;
                } else {
                    break;
                }
            }

            if (len > 0) {
                if (out_pos + len + 1 >= out_len) {
                    break;
                }
                if (out_pos > 0) out[out_pos++] = '\n';
                for (int32_t i = 0; i < len && out_pos < out_len - 1; i++) {
                    out[out_pos++] = (char)tolower((unsigned char)start[i]);
                }
            }

            p = start + len;
            continue;
        }
        p++;
    }

    out[out_pos] = '\0';
    return out_pos;
}

/**
 * @brief Get the last word in text
 * 
 * Useful for rhyme analysis and autocomplete.
 * 
 * @param text Input text
 * @param out Output buffer for the word
 * @param out_len Size of output buffer
 * @return Length of word, or 0 if no word found
 */
int32_t simjot_text_last_word(const char* text, char* out, int32_t out_len) {
    if (!text || !out || out_len <= 0) return 0;
    
    int len = strlen(text);
    int word_end = -1;
    int word_start = -1;
    
    /* Find last word from end */
    for (int i = len - 1; i >= 0; i--) {
        unsigned char c = (unsigned char)text[i];
        int is_word_char = isalpha(c) || c == '\'';
        
        if (is_word_char) {
            if (word_end < 0) word_end = i + 1;
            word_start = i;
        } else if (word_end >= 0) {
            break;  /* Found complete word */
        }
    }
    
    if (word_start < 0 || word_end < 0) return 0;
    
    int word_len = word_end - word_start;
    if (word_len >= out_len) word_len = out_len - 1;
    
    memcpy(out, text + word_start, word_len);
    out[word_len] = '\0';
    
    return word_len;
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TEXT NORMALIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Normalize text for comparison (lowercase, strip punctuation)
 * 
 * @param text Input text
 * @param out Output buffer
 * @param out_len Size of output buffer
 * @return Length of normalized text
 */
int32_t simjot_text_normalize(const char* text, char* out, int32_t out_len) {
    if (!text || !out || out_len <= 0) return 0;
    
    int32_t written = 0;
    while (*text && written < out_len - 1) {
        unsigned char c = (unsigned char)*text++;
        
        if (isalpha(c)) {
            out[written++] = (char)tolower(c);
        } else if (isdigit(c)) {
            out[written++] = c;
        } else if (isspace(c) && written > 0 && out[written-1] != ' ') {
            out[written++] = ' ';
        }
        /* Skip punctuation and other characters */
    }
    
    /* Trim trailing space */
    while (written > 0 && out[written-1] == ' ') written--;
    
    out[written] = '\0';
    return written;
}

/**
 * ════════════════════════════════════════════════════════════════════════════
 * FUZZY MATCHING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Check if text contains all characters of query (in order)
 * 
 * Used for fuzzy search/autocomplete. Case-insensitive.
 * 
 * @param text Text to search in
 * @param query Characters to find (in order)
 * @return 1 if match, 0 otherwise
 */
int32_t simjot_text_fuzzy_match(const char* text, const char* query) {
    if (!text || !query) return 0;
    if (!*query) return 1;  /* Empty query matches everything */
    
    const char* q = query;
    while (*text && *q) {
        if (tolower((unsigned char)*text) == tolower((unsigned char)*q)) {
            q++;
        }
        text++;
    }
    
    return (*q == '\0') ? 1 : 0;
}

/**
 * @brief Score fuzzy match quality (higher = better match)
 * 
 * Scoring factors:
 * - Consecutive character matches
 * - Matches at word boundaries
 * - Case-sensitive matches
 * 
 * @param text Text to search in
 * @param query Query string
 * @return Match score (0 = no match, higher = better)
 */
int32_t simjot_text_fuzzy_score(const char* text, const char* query) {
    if (!text || !query || !*query) return 0;
    if (!simjot_text_fuzzy_match(text, query)) return 0;
    
    int32_t score = 0;
    int consecutive = 0;
    int prev_was_boundary = 1;
    const char* q = query;
    
    while (*text && *q) {
        char tc = *text;
        char qc = *q;
        int tc_lower = tolower((unsigned char)tc);
        int qc_lower = tolower((unsigned char)qc);
        
        if (tc_lower == qc_lower) {
            score += 10;  /* Base match score */
            
            if (tc == qc) score += 5;  /* Case match bonus */
            if (prev_was_boundary) score += 20;  /* Word boundary bonus */
            if (consecutive > 0) score += consecutive * 5;  /* Consecutive bonus */
            
            consecutive++;
            q++;
        } else {
            consecutive = 0;
        }
        
        prev_was_boundary = !isalnum((unsigned char)tc);
        text++;
    }
    
    /* Bonus for query matching from start */
    if (query == q - strlen(query) && 
        tolower((unsigned char)text[0]) == tolower((unsigned char)query[0])) {
        score += 50;
    }
    
    return score;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LINE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Count lines in text
 * 
 * @param text Input text
 * @return Number of lines
 */
int32_t simjot_text_line_count(const char* text) {
    if (!text || !*text) return 0;
    
    int32_t count = 1;
    while (*text) {
        if (*text++ == '\n') count++;
    }
    return count;
}

/**
 * @brief Get a specific line from text
 * 
 * @param text Input text
 * @param line_num Line number (0-indexed)
 * @param out Output buffer
 * @param out_len Size of output buffer
 * @return Length of line, or -1 if line doesn't exist
 */
int32_t simjot_text_get_line(const char* text, int32_t line_num, char* out, int32_t out_len) {
    if (!text || !out || out_len <= 0 || line_num < 0) return -1;
    
    int32_t current_line = 0;
    const char* line_start = text;
    
    /* Find the requested line */
    while (*text) {
        if (*text == '\n') {
            if (current_line == line_num) {
                int len = (int)(text - line_start);
                if (len >= out_len) len = out_len - 1;
                memcpy(out, line_start, len);
                out[len] = '\0';
                return len;
            }
            current_line++;
            line_start = text + 1;
        }
        text++;
    }
    
    /* Handle last line (no trailing newline) */
    if (current_line == line_num) {
        int len = (int)(text - line_start);
        if (len >= out_len) len = out_len - 1;
        memcpy(out, line_start, len);
        out[len] = '\0';
        return len;
    }
    
    return -1;  /* Line not found */
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING DISTANCE FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Calculate Levenshtein edit distance between two strings
 * 
 * Uses dynamic programming with O(n*m) time complexity.
 * Limited to strings of 128 characters to prevent stack overflow.
 * 
 * @param a First string
 * @param b Second string
 * @return Edit distance, or -1 on error
 */
int32_t simjot_text_levenshtein(const char* a, const char* b) {
    if (!a || !b) return -1;
    
    size_t la = strlen(a);
    size_t lb = strlen(b);
    
    /* Limit to prevent stack overflow */
    if (la > 128 || lb > 128) return -1;
    
    /* Quick checks */
    if (la == 0) return (int32_t)lb;
    if (lb == 0) return (int32_t)la;
    
    int dp[129][129];
    
    for (size_t i = 0; i <= la; i++) dp[i][0] = (int)i;
    for (size_t j = 0; j <= lb; j++) dp[0][j] = (int)j;
    
    for (size_t i = 1; i <= la; i++) {
        for (size_t j = 1; j <= lb; j++) {
            int cost = (tolower((unsigned char)a[i-1]) == tolower((unsigned char)b[j-1])) ? 0 : 1;
            int del = dp[i-1][j] + 1;
            int ins = dp[i][j-1] + 1;
            int sub = dp[i-1][j-1] + cost;
            dp[i][j] = del < ins ? (del < sub ? del : sub) : (ins < sub ? ins : sub);
        }
    }
    
    return dp[la][lb];
}

/**
 * @brief Calculate Damerau-Levenshtein distance (includes transpositions)
 * 
 * Extends Levenshtein to also consider adjacent character swaps.
 * Better for detecting common typos like "teh" -> "the".
 * 
 * @param a First string
 * @param b Second string
 * @return Edit distance, or -1 on error
 */
int32_t simjot_text_damerau_levenshtein(const char* a, const char* b) {
    if (!a || !b) return -1;
    
    size_t la = strlen(a);
    size_t lb = strlen(b);
    
    if (la > 128 || lb > 128) return -1;
    if (la == 0) return (int32_t)lb;
    if (lb == 0) return (int32_t)la;
    
    int dp[130][130];
    
    for (size_t i = 0; i <= la + 1; i++) dp[i][0] = (int)i;
    for (size_t j = 0; j <= lb + 1; j++) dp[0][j] = (int)j;
    
    for (size_t i = 1; i <= la; i++) {
        for (size_t j = 1; j <= lb; j++) {
            char ac = tolower((unsigned char)a[i-1]);
            char bc = tolower((unsigned char)b[j-1]);
            int cost = (ac == bc) ? 0 : 1;
            
            int del = dp[i-1][j] + 1;
            int ins = dp[i][j-1] + 1;
            int sub = dp[i-1][j-1] + cost;
            dp[i][j] = del < ins ? (del < sub ? del : sub) : (ins < sub ? ins : sub);
            
            /* Transposition */
            if (i > 1 && j > 1) {
                char a_prev = tolower((unsigned char)a[i-2]);
                char b_prev = tolower((unsigned char)b[j-2]);
                if (ac == b_prev && a_prev == bc) {
                    int trans = dp[i-2][j-2] + cost;
                    if (trans < dp[i][j]) dp[i][j] = trans;
                }
            }
        }
    }
    
    return dp[la][lb];
}

/**
 * @brief Calculate similarity ratio between two strings (0.0 to 1.0)
 * 
 * Returns 1.0 for identical strings, 0.0 for completely different.
 * Based on Levenshtein distance normalized by max length.
 * 
 * @param a First string
 * @param b Second string
 * @return Similarity as integer percentage (0-100), or -1 on error
 */
int32_t simjot_text_similarity(const char* a, const char* b) {
    if (!a || !b) return -1;
    
    size_t la = strlen(a);
    size_t lb = strlen(b);
    
    if (la == 0 && lb == 0) return 100;
    if (la == 0 || lb == 0) return 0;
    
    int32_t dist = simjot_text_levenshtein(a, b);
    if (dist < 0) return -1;
    
    size_t max_len = la > lb ? la : lb;
    return (int32_t)(100 - (dist * 100 / (int32_t)max_len));
}
