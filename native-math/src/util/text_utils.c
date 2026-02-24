/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native Text Utilities
 * High-performance text processing functions
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <math.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING SANITIZATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Sanitize text: collapse whitespace, trim, normalize line endings.
 * Returns new string (caller must free) or NULL on failure.
 */
char* simjot_text_sanitize(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    size_t j = 0;
    int in_space = 1; // Start true to trim leading
    
    for (size_t i = 0; i < len; i++) {
        char c = input[i];
        
        // Treat all whitespace as space
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            if (!in_space) {
                out[j++] = ' ';
                in_space = 1;
            }
        } else {
            out[j++] = c;
            in_space = 0;
        }
    }
    
    // Trim trailing space
    if (j > 0 && out[j-1] == ' ') j--;
    out[j] = '\0';
    
    return out;
}

/**
 * Collapse whitespace but preserve newlines.
 */
char* simjot_text_collapse_whitespace(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    size_t j = 0;
    int in_hspace = 0; // Horizontal space
    
    for (size_t i = 0; i < len; i++) {
        char c = input[i];
        
        if (c == '\n' || c == '\r') {
            // Trim trailing space before newline
            if (j > 0 && out[j-1] == ' ') j--;
            out[j++] = '\n';
            in_hspace = 1; // Skip leading space after newline
        } else if (c == ' ' || c == '\t') {
            if (!in_hspace) {
                out[j++] = ' ';
                in_hspace = 1;
            }
        } else {
            out[j++] = c;
            in_hspace = 0;
        }
    }
    
    // Trim trailing
    while (j > 0 && (out[j-1] == ' ' || out[j-1] == '\n')) j--;
    out[j] = '\0';
    
    return out;
}

/**
 * Strip HTML tags from text.
 */
char* simjot_text_strip_html(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    size_t j = 0;
    int in_tag = 0;
    
    for (size_t i = 0; i < len; i++) {
        char c = input[i];
        
        if (c == '<') {
            in_tag = 1;
        } else if (c == '>') {
            in_tag = 0;
        } else if (!in_tag) {
            out[j++] = c;
        }
    }
    out[j] = '\0';
    
    return out;
}

/**
 * Convert string to lowercase (ASCII only).
 */
char* simjot_text_to_lower(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    for (size_t i = 0; i <= len; i++) {
        out[i] = (char)tolower((unsigned char)input[i]);
    }
    
    return out;
}

/**
 * Convert string to uppercase (ASCII only).
 */
char* simjot_text_to_upper(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    for (size_t i = 0; i <= len; i++) {
        out[i] = (char)toupper((unsigned char)input[i]);
    }
    
    return out;
}

/**
 * Title case: capitalize first letter of each word.
 */
char* simjot_text_title_case(const char* input) {
    if (!input) return NULL;
    
    size_t len = strlen(input);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    int cap_next = 1;
    for (size_t i = 0; i <= len; i++) {
        char c = input[i];
        if (isspace((unsigned char)c) || c == '\0') {
            cap_next = 1;
            out[i] = c;
        } else if (cap_next) {
            out[i] = (char)toupper((unsigned char)c);
            cap_next = 0;
        } else {
            out[i] = (char)tolower((unsigned char)c);
        }
    }
    
    return out;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT STATISTICS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Count words in text (internal helper).
 */
static int32_t util_text_word_count(const char* text) {
    if (!text || !*text) return 0;
    
    int32_t count = 0;
    int in_word = 0;
    
    for (const char* p = text; *p; p++) {
        if (isspace((unsigned char)*p)) {
            in_word = 0;
        } else if (!in_word) {
            in_word = 1;
            count++;
        }
    }
    
    return count;
}

/**
 * Count characters (excluding whitespace).
 * NOTE: Renamed to avoid ABI conflict with text/text_utils.c version which has include_spaces param.
 */
int32_t simjot_text_char_count_nospace(const char* text) {
    if (!text) return 0;
    
    int32_t count = 0;
    for (const char* p = text; *p; p++) {
        if (!isspace((unsigned char)*p)) count++;
    }
    return count;
}

/**
 * Count lines in text (internal helper).
 */
static int32_t util_text_line_count(const char* text) {
    if (!text || !*text) return 0;
    
    int32_t count = 1;
    for (const char* p = text; *p; p++) {
        if (*p == '\n') count++;
    }
    return count;
}

/**
 * Count sentences (rough estimate based on . ! ?) (internal helper).
 */
static int32_t util_text_sentence_count(const char* text) {
    if (!text || !*text) return 0;
    
    int32_t count = 0;
    for (const char* p = text; *p; p++) {
        if (*p == '.' || *p == '!' || *p == '?') {
            // Avoid counting abbreviations like "Mr." or "..."
            if (p[1] == '\0' || isspace((unsigned char)p[1]) || 
                (p[1] >= 'A' && p[1] <= 'Z')) {
                count++;
            }
        }
    }
    return count > 0 ? count : 1;
}

/**
 * Count syllables in a word (English heuristic).
 */
int32_t simjot_text_syllable_count(const char* word) {
    if (!word || !*word) return 0;
    
    int32_t count = 0;
    int prev_vowel = 0;
    size_t len = strlen(word);
    
    for (size_t i = 0; i < len; i++) {
        char c = (char)tolower((unsigned char)word[i]);
        int is_vowel = (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y');
        
        if (is_vowel && !prev_vowel) {
            count++;
        }
        prev_vowel = is_vowel;
    }
    
    // Silent 'e' at end
    if (len > 2 && tolower((unsigned char)word[len-1]) == 'e' && count > 1) {
        count--;
    }
    
    return count > 0 ? count : 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT ANALYSIS STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Comprehensive text statistics.
 */
typedef struct {
    int32_t word_count;
    int32_t char_count;
    int32_t char_count_no_spaces;
    int32_t line_count;
    int32_t sentence_count;
    int32_t syllable_count;
    int32_t unique_words;
    double avg_word_length;
    double avg_sentence_length;
    double avg_syllables_per_word;
    double flesch_reading_ease;
    double flesch_kincaid_grade;
    double gunning_fog;
    double type_token_ratio;
} TextStats;

/**
 * Compute comprehensive text statistics.
 */
int32_t simjot_text_analyze(const char* text, int32_t* out_stats, int32_t stats_count) {
    if (!text || !out_stats || stats_count < 14) return 0;
    
    TextStats stats = {0};
    
    // Basic counts
    stats.word_count = util_text_word_count(text);
    stats.line_count = util_text_line_count(text);
    stats.sentence_count = util_text_sentence_count(text);
    
    // Character counts
    for (const char* p = text; *p; p++) {
        stats.char_count++;
        if (!isspace((unsigned char)*p)) {
            stats.char_count_no_spaces++;
        }
    }
    
    // Word-level analysis
    int32_t total_word_len = 0;
    int32_t total_syllables = 0;
    int in_word = 0;
    const char* word_start = NULL;
    
    for (const char* p = text; ; p++) {
        if (!*p || isspace((unsigned char)*p)) {
            if (in_word && word_start) {
                size_t wlen = p - word_start;
                total_word_len += (int32_t)wlen;
                
                // Count syllables in this word
                char word_buf[64];
                if (wlen < sizeof(word_buf)) {
                    memcpy(word_buf, word_start, wlen);
                    word_buf[wlen] = '\0';
                    total_syllables += simjot_text_syllable_count(word_buf);
                }
            }
            in_word = 0;
            word_start = NULL;
            if (!*p) break;
        } else if (!in_word) {
            in_word = 1;
            word_start = p;
        }
    }
    
    stats.syllable_count = total_syllables;
    
    // Averages
    if (stats.word_count > 0) {
        stats.avg_word_length = (double)total_word_len / stats.word_count;
        stats.avg_syllables_per_word = (double)total_syllables / stats.word_count;
    }
    if (stats.sentence_count > 0) {
        stats.avg_sentence_length = (double)stats.word_count / stats.sentence_count;
    }
    
    // Readability metrics
    if (stats.word_count > 0 && stats.sentence_count > 0) {
        double asl = stats.avg_sentence_length;
        double asw = stats.avg_syllables_per_word;
        
        // Flesch Reading Ease
        stats.flesch_reading_ease = 206.835 - (1.015 * asl) - (84.6 * asw);
        
        // Flesch-Kincaid Grade Level
        stats.flesch_kincaid_grade = (0.39 * asl) + (11.8 * asw) - 15.59;
        
        // Gunning Fog Index (simplified - uses avg syllables as proxy for complex words)
        double complex_ratio = (asw > 1.5) ? (asw - 1.0) * 0.3 : 0.0;
        stats.gunning_fog = 0.4 * (asl + 100.0 * complex_ratio);
    }
    
    // Output stats as int32_t array (multiply floats by 1000 for precision)
    out_stats[0] = stats.word_count;
    out_stats[1] = stats.char_count;
    out_stats[2] = stats.char_count_no_spaces;
    out_stats[3] = stats.line_count;
    out_stats[4] = stats.sentence_count;
    out_stats[5] = stats.syllable_count;
    out_stats[6] = (int32_t)(stats.avg_word_length * 1000);
    out_stats[7] = (int32_t)(stats.avg_sentence_length * 1000);
    out_stats[8] = (int32_t)(stats.avg_syllables_per_word * 1000);
    out_stats[9] = (int32_t)(stats.flesch_reading_ease * 1000);
    out_stats[10] = (int32_t)(stats.flesch_kincaid_grade * 1000);
    out_stats[11] = (int32_t)(stats.gunning_fog * 1000);
    out_stats[12] = stats.unique_words;
    out_stats[13] = (int32_t)(stats.type_token_ratio * 1000);
    
    return 14;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * VALIDATION UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Check if string is ASCII only.
 */
int32_t simjot_text_is_ascii(const char* text) {
    if (!text) return 1;
    for (const char* p = text; *p; p++) {
        if ((unsigned char)*p > 127) return 0;
    }
    return 1;
}

/**
 * Check if string is alphanumeric only.
 */
int32_t simjot_text_is_alnum(const char* text) {
    if (!text || !*text) return 0;
    for (const char* p = text; *p; p++) {
        if (!isalnum((unsigned char)*p)) return 0;
    }
    return 1;
}

/**
 * Check if string is a valid identifier (alphanumeric + underscore, not starting with digit).
 */
int32_t simjot_text_is_identifier(const char* text) {
    if (!text || !*text) return 0;
    if (isdigit((unsigned char)text[0])) return 0;
    
    for (const char* p = text; *p; p++) {
        if (!isalnum((unsigned char)*p) && *p != '_') return 0;
    }
    return 1;
}

/**
 * Simple email validation (basic pattern check).
 */
int32_t simjot_text_is_email(const char* text) {
    if (!text || !*text) return 0;
    
    const char* at = strchr(text, '@');
    if (!at || at == text) return 0;
    
    const char* dot = strchr(at, '.');
    if (!dot || dot == at + 1 || dot[1] == '\0') return 0;
    
    // Check no spaces
    for (const char* p = text; *p; p++) {
        if (isspace((unsigned char)*p)) return 0;
    }
    
    return 1;
}

/**
 * Check if filename is safe (no path separators, no special chars).
 */
int32_t simjot_text_is_safe_filename(const char* text) {
    if (!text || !*text) return 0;
    if (strlen(text) > 255) return 0;
    if (strcmp(text, ".") == 0 || strcmp(text, "..") == 0) return 0;
    
    for (const char* p = text; *p; p++) {
        char c = *p;
        if (c == '/' || c == '\\' || c == ':' || c == '*' || 
            c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
            return 0;
        }
        if ((unsigned char)c < 32) return 0;
    }
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * NUMERIC PARSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Parse integer with default value on failure.
 */
int32_t simjot_parse_int(const char* text, int32_t default_val) {
    if (!text || !*text) return default_val;
    
    // Skip leading whitespace
    while (isspace((unsigned char)*text)) text++;
    if (!*text) return default_val;
    
    char* end;
    long val = strtol(text, &end, 10);
    
    // Check if any conversion happened
    if (end == text) return default_val;
    
    // Skip trailing whitespace
    while (isspace((unsigned char)*end)) end++;
    
    // Check for overflow
    if (val > INT32_MAX || val < INT32_MIN) return default_val;
    
    return (int32_t)val;
}

/**
 * Parse double with default value on failure.
 */
double simjot_parse_double(const char* text, double default_val) {
    if (!text || !*text) return default_val;
    
    while (isspace((unsigned char)*text)) text++;
    if (!*text) return default_val;
    
    char* end;
    double val = strtod(text, &end);
    
    if (end == text) return default_val;
    
    return val;
}

/**
 * Parse boolean (true/yes/1/on = 1, false/no/0/off = 0).
 */
int32_t simjot_parse_bool(const char* text, int32_t default_val) {
    if (!text || !*text) return default_val;
    
    while (isspace((unsigned char)*text)) text++;
    
    if (strcasecmp(text, "true") == 0 || strcasecmp(text, "yes") == 0 ||
        strcmp(text, "1") == 0 || strcasecmp(text, "on") == 0) {
        return 1;
    }
    if (strcasecmp(text, "false") == 0 || strcasecmp(text, "no") == 0 ||
        strcmp(text, "0") == 0 || strcasecmp(text, "off") == 0) {
        return 0;
    }
    
    return default_val;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING EXTRACTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Extract first N words from text.
 * Returns new string (caller must free).
 */
char* simjot_text_first_words(const char* text, int32_t count) {
    if (!text || count <= 0) return NULL;
    
    size_t len = strlen(text);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    int32_t words = 0;
    size_t j = 0;
    int in_word = 0;
    
    for (size_t i = 0; i < len && words < count; i++) {
        char c = text[i];
        
        if (isspace((unsigned char)c)) {
            if (in_word) {
                out[j++] = ' ';
                in_word = 0;
            }
        } else {
            if (!in_word) {
                words++;
                if (words > count) break;
            }
            out[j++] = c;
            in_word = 1;
        }
    }
    
    // Trim trailing space
    if (j > 0 && out[j-1] == ' ') j--;
    out[j] = '\0';
    
    return out;
}

/**
 * Extract first line from text.
 * Returns new string (caller must free).
 */
char* simjot_text_first_line(const char* text) {
    if (!text) return NULL;
    
    const char* newline = strchr(text, '\n');
    size_t len = newline ? (size_t)(newline - text) : strlen(text);
    
    // Trim trailing whitespace
    while (len > 0 && isspace((unsigned char)text[len-1])) len--;
    
    // Trim leading whitespace
    while (len > 0 && isspace((unsigned char)*text)) {
        text++;
        len--;
    }
    
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    
    memcpy(out, text, len);
    out[len] = '\0';
    
    return out;
}

/**
 * Truncate string with ellipsis.
 * Returns new string (caller must free).
 */
char* simjot_text_ellipsis(const char* text, int32_t max_len) {
    if (!text) return NULL;
    
    size_t len = strlen(text);
    if ((int32_t)len <= max_len) {
        char* out = strdup(text);
        return out;
    }
    
    if (max_len <= 3) {
        char* out = (char*)malloc(max_len + 1);
        if (!out) return NULL;
        memcpy(out, text, max_len);
        out[max_len] = '\0';
        return out;
    }
    
    char* out = (char*)malloc(max_len + 1);
    if (!out) return NULL;
    
    memcpy(out, text, max_len - 1);
    out[max_len - 1] = '\xe2'; // UTF-8 ellipsis
    out[max_len] = '\0';
    
    // Actually use ASCII ellipsis for simplicity
    memcpy(out, text, max_len - 3);
    out[max_len - 3] = '.';
    out[max_len - 2] = '.';
    out[max_len - 1] = '.';
    out[max_len] = '\0';
    
    return out;
}
