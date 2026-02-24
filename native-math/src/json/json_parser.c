/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file json_parser.c
 * @brief Lightweight JSON Parser for Simjot
 * 
 * A fast, single-pass JSON parser optimized for common Simjot use cases:
 * - Extracting string values by key
 * - Iterating over object keys
 * - Parsing arrays of strings
 * - Dictionary file parsing
 * 
 * Zero external dependencies, minimal memory allocation.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/**
 * ════════════════════════════════════════════════════════════════════════════
 * JSON PARSER HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Skip whitespace */
static const char* skip_ws(const char* p, const char* end) {
    while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) p++;
    return p;
}

/* Skip a JSON string (after opening quote), return pointer after closing quote */
static const char* skip_string(const char* p, const char* end) {
    int escaped = 0;
    while (p < end) {
        char c = *p++;
        if (escaped) {
            escaped = 0;
            continue;
        }
        if (c == '\\') {
            escaped = 1;
            continue;
        }
        if (c == '"') return p;
    }
    return end;
}

/* Skip a JSON value (string, number, object, array, true/false/null) */
static const char* skip_value(const char* p, const char* end) {
    p = skip_ws(p, end);
    if (p >= end) return end;
    
    char c = *p;
    if (c == '"') {
        return skip_string(p + 1, end);
    } else if (c == '{') {
        int depth = 1;
        p++;
        while (p < end && depth > 0) {
            c = *p++;
            if (c == '"') p = skip_string(p, end);
            else if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return p;
    } else if (c == '[') {
        int depth = 1;
        p++;
        while (p < end && depth > 0) {
            c = *p++;
            if (c == '"') p = skip_string(p, end);
            else if (c == '[') depth++;
            else if (c == ']') depth--;
        }
        return p;
    } else if (c == 't') {
        return p + 4; /* true */
    } else if (c == 'f') {
        return p + 5; /* false */
    } else if (c == 'n') {
        return p + 4; /* null */
    } else if (c == '-' || isdigit((unsigned char)c)) {
        while (p < end && (isdigit((unsigned char)*p) || *p == '.' || 
               *p == 'e' || *p == 'E' || *p == '+' || *p == '-')) p++;
        return p;
    }
    return p + 1;
}

/* Unescape a JSON string in-place, return new length */
static int32_t unescape_string(char* str, int32_t len) {
    char* dst = str;
    const char* src = str;
    const char* end = str + len;
    
    while (src < end) {
        if (*src == '\\' && src + 1 < end) {
            src++;
            switch (*src) {
                case '"': *dst++ = '"'; break;
                case '\\': *dst++ = '\\'; break;
                case '/': *dst++ = '/'; break;
                case 'b': *dst++ = '\b'; break;
                case 'f': *dst++ = '\f'; break;
                case 'n': *dst++ = '\n'; break;
                case 'r': *dst++ = '\r'; break;
                case 't': *dst++ = '\t'; break;
                case 'u':
                    /* Unicode escape - simplified: just copy as-is or skip */
                    if (src + 4 < end) {
                        /* For now, just output ? for non-ASCII unicode */
                        *dst++ = '?';
                        src += 4;
                    }
                    break;
                default: *dst++ = *src; break;
            }
            src++;
        } else {
            *dst++ = *src++;
        }
    }
    *dst = '\0';
    return (int32_t)(dst - str);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Extract a string value by key from JSON object
 * 
 * @param json JSON string (null-terminated)
 * @param key Key to search for
 * @param output Output buffer for value
 * @param output_len Size of output buffer
 * @return Length of extracted value, or negative on error
 */
int32_t simjot_json_get_string(const char* json, const char* key,
                               char* output, int32_t output_len) {
    if (!json || !key || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    /* Find opening brace */
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -2;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end) break;
        if (*p == '}') break;
        if (*p == ',') { p++; continue; }
        
        /* Expect key string */
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        /* Skip colon */
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_ws(p, end);
        
        /* Check if this is the key we want */
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            /* Found the key, extract value */
            if (*p == '"') {
                p++;
                const char* vs = p;
                p = skip_string(p, end);
                const char* ve = p - 1;
                int32_t vlen = (int32_t)(ve - vs);
                
                if (vlen >= output_len) vlen = output_len - 1;
                memcpy(output, vs, vlen);
                output[vlen] = '\0';
                return unescape_string(output, vlen);
            } else {
                /* Value is not a string */
                return -3;
            }
        }
        
        /* Skip this value */
        p = skip_value(p, end);
    }
    
    return -4; /* Key not found */
}

/**
 * @brief Extract an integer value by key from JSON object
 * 
 * @param json JSON string
 * @param key Key to search for
 * @param out_value Pointer to store result
 * @return 1 on success, 0 on failure
 */
int32_t simjot_json_get_int(const char* json, const char* key, int64_t* out_value) {
    if (!json || !key || !out_value) return 0;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return 0;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_ws(p, end);
        
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            /* Parse integer */
            char* endptr;
            *out_value = strtoll(p, &endptr, 10);
            return endptr > p ? 1 : 0;
        }
        
        p = skip_value(p, end);
    }
    
    return 0;
}

/**
 * @brief Check if a key exists in JSON object
 * 
 * @param json JSON string
 * @param key Key to check
 * @return 1 if key exists, 0 otherwise
 */
int32_t simjot_json_has_key(const char* json, const char* key) {
    if (!json || !key) return 0;
    
    const char* p = json;
    const char* end = json + strlen(json);
    size_t key_len = strlen(key);
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return 0;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        if (klen == key_len && memcmp(ks, key, key_len) == 0) {
            return 1;
        }
        
        p = skip_value(p, end);
    }
    
    return 0;
}

/**
 * @brief Count keys in a JSON object
 * 
 * @param json JSON string
 * @return Number of keys, or negative on error
 */
int32_t simjot_json_count_keys(const char* json) {
    if (!json) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        p = skip_string(p, end);
        count++;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        p = skip_value(p, end);
    }
    
    return count;
}

/**
 * @brief Extract keys from JSON object to buffer (newline-separated)
 * 
 * @param json JSON string
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Number of keys extracted, or negative on error
 */
int32_t simjot_json_get_keys(const char* json, char* output, int32_t output_len) {
    if (!json || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t out_pos = 0;
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        int32_t klen = (int32_t)(ke - ks);
        
        /* Copy key to output */
        if (out_pos + klen + 1 < output_len) {
            if (out_pos > 0) output[out_pos++] = '\n';
            memcpy(output + out_pos, ks, klen);
            out_pos += klen;
            count++;
        }
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        
        p = skip_value(p, end);
    }
    
    output[out_pos] = '\0';
    return count;
}

/**
 * @brief Parse a JSON array of strings into newline-separated output
 * 
 * @param json JSON string containing array
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Number of strings extracted, or negative on error
 */
int32_t simjot_json_parse_string_array(const char* json, char* output, int32_t output_len) {
    if (!json || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    int32_t out_pos = 0;
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '[') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == ']') break;
        if (*p == ',') { p++; continue; }
        
        if (*p == '"') {
            p++;
            const char* vs = p;
            p = skip_string(p, end);
            const char* ve = p - 1;
            int32_t vlen = (int32_t)(ve - vs);
            
            if (out_pos + vlen + 1 < output_len) {
                if (out_pos > 0) output[out_pos++] = '\n';
                memcpy(output + out_pos, vs, vlen);
                out_pos += vlen;
                count++;
            }
        } else {
            p = skip_value(p, end);
        }
    }
    
    output[out_pos] = '\0';
    return count;
}

/**
 * @brief Extract a nested string value using dot notation path
 * 
 * Example: simjot_json_get_path(json, "user.name", buf, 256)
 * 
 * @param json JSON string
 * @param path Dot-separated path (e.g., "user.name")
 * @param output Output buffer
 * @param output_len Size of output buffer
 * @return Length of value, or negative on error
 */
int32_t simjot_json_get_path(const char* json, const char* path,
                             char* output, int32_t output_len) {
    if (!json || !path || !output || output_len <= 0) return -1;
    
    char key[256];
    const char* dot;
    char temp[8192];
    const char* current = json;
    
    while ((dot = strchr(path, '.')) != NULL) {
        size_t klen = dot - path;
        if (klen >= sizeof(key)) return -2;
        memcpy(key, path, klen);
        key[klen] = '\0';
        
        /* Get nested object */
        const char* p = current;
        const char* end = current + strlen(current);
        size_t key_len = strlen(key);
        
        p = skip_ws(p, end);
        if (p >= end || *p != '{') return -3;
        p++;
        
        int found = 0;
        while (p < end && !found) {
            p = skip_ws(p, end);
            if (p >= end || *p == '}') break;
            if (*p == ',') { p++; continue; }
            
            if (*p != '"') break;
            p++;
            const char* ks = p;
            p = skip_string(p, end);
            const char* ke = p - 1;
            size_t kl = ke - ks;
            
            p = skip_ws(p, end);
            if (p >= end || *p != ':') break;
            p++;
            p = skip_ws(p, end);
            
            if (kl == key_len && memcmp(ks, key, key_len) == 0) {
                /* Found - extract the value as new current */
                const char* vs = p;
                const char* ve = skip_value(p, end);
                size_t vlen = ve - vs;
                if (vlen >= sizeof(temp)) return -4;
                memcpy(temp, vs, vlen);
                temp[vlen] = '\0';
                current = temp;
                found = 1;
            } else {
                p = skip_value(p, end);
            }
        }
        
        if (!found) return -5;
        path = dot + 1;
    }
    
    /* Final key */
    return simjot_json_get_string(current, path, output, output_len);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DICTIONARY FILE PARSING - Optimized for simple-english-dictionary format
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Parse dictionary JSON file and extract all words
 * 
 * Optimized for the simple-english-dictionary format:
 * { "word1": {...}, "word2": {...}, ... }
 * 
 * Returns words as newline-separated list.
 * 
 * @param json JSON content
 * @param json_len Length of JSON content
 * @param output Output buffer for words (newline-separated)
 * @param output_len Size of output buffer
 * @return Number of words extracted, or negative on error
 */
int32_t simjot_json_parse_dict_words(const char* json, int32_t json_len,
                                      char* output, int32_t output_len) {
    if (!json || json_len <= 0 || !output || output_len <= 0) return -1;
    
    const char* p = json;
    const char* end = json + json_len;
    int32_t out_pos = 0;
    int32_t count = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -1;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        /* Extract word (key) */
        if (*p != '"') break;
        p++;
        const char* ws = p;
        p = skip_string(p, end);
        const char* we = p - 1;
        int32_t wlen = (int32_t)(we - ws);
        
        /* Copy word to output */
        if (wlen > 0 && out_pos + wlen + 1 < output_len) {
            if (out_pos > 0) output[out_pos++] = '\n';
            memcpy(output + out_pos, ws, wlen);
            out_pos += wlen;
            count++;
        }
        
        /* Skip colon and value (the word's definition object) */
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_value(p, end);
    }
    
    output[out_pos] = '\0';
    return count;
}

/**
 * @brief Dictionary entry parsed from JSON
 */
typedef struct {
    char word[64];
    char pos[128];      /* Parts of speech, comma-separated */
    char synonyms[512]; /* Newline-separated */
    char antonyms[256]; /* Newline-separated */
} DictEntry;

/**
 * @brief Parse a single dictionary entry
 * 
 * @param json JSON object for the word entry
 * @param word The word being looked up
 * @param out Output buffer (binary format)
 * @param out_len Size of output buffer
 * @return Bytes written, or negative on error
 * 
 * Output format:
 *   int32: word length
 *   char[]: word
 *   int32: POS count
 *   int32: POS string length
 *   char[]: POS (newline-separated)
 *   int32: synonyms count
 *   int32: synonyms string length
 *   char[]: synonyms (newline-separated)
 *   int32: antonyms count
 *   int32: antonyms string length
 *   char[]: antonyms (newline-separated)
 */
int32_t simjot_json_parse_dict_entry(const char* json, const char* word,
                                      uint8_t* out, int32_t out_len) {
    if (!json || !word || !out || out_len < 64) return -1;
    
    const char* p = json;
    const char* end = json + strlen(json);
    
    char pos_buf[256] = {0};
    char syn_buf[1024] = {0};
    char ant_buf[512] = {0};
    int32_t pos_count = 0;
    int32_t syn_count = 0;
    int32_t ant_count = 0;
    int32_t pos_len = 0;
    int32_t syn_len = 0;
    int32_t ant_len = 0;
    
    p = skip_ws(p, end);
    if (p >= end || *p != '{') return -2;
    p++;
    
    while (p < end) {
        p = skip_ws(p, end);
        if (p >= end || *p == '}') break;
        if (*p == ',') { p++; continue; }
        
        /* Get key */
        if (*p != '"') break;
        p++;
        const char* ks = p;
        p = skip_string(p, end);
        const char* ke = p - 1;
        size_t klen = ke - ks;
        
        p = skip_ws(p, end);
        if (p >= end || *p != ':') break;
        p++;
        p = skip_ws(p, end);
        
        /* Check key and extract values */
        if (klen == 8 && memcmp(ks, "MEANINGS", 8) == 0 && *p == '{') {
            /* Parse MEANINGS to extract POS */
            const char* ms = p + 1;
            const char* me = skip_value(p, end);
            
            /* Look for POS tags in the meanings */
            while (ms < me) {
                ms = skip_ws(ms, me);
                if (ms >= me || *ms == '}') break;
                if (*ms == ',') { ms++; continue; }
                
                if (*ms == '"') {
                    ms++;
                    const char* poss = ms;
                    ms = skip_string(ms, me);
                    const char* pose = ms - 1;
                    int32_t plen = (int32_t)(pose - poss);
                    
                    /* Check if this looks like a POS */
                    if (plen > 0 && plen < 20) {
                        char temp[32];
                        if (plen < (int32_t)sizeof(temp) - 1) {
                            memcpy(temp, poss, plen);
                            temp[plen] = '\0';
                            
                            /* Common POS tags */
                            if (strcmp(temp, "Noun") == 0 || strcmp(temp, "Verb") == 0 ||
                                strcmp(temp, "Adjective") == 0 || strcmp(temp, "Adverb") == 0 ||
                                strcmp(temp, "Preposition") == 0 || strcmp(temp, "Conjunction") == 0 ||
                                strcmp(temp, "Pronoun") == 0 || strcmp(temp, "Interjection") == 0) {
                                if (pos_len + plen + 1 < (int32_t)sizeof(pos_buf)) {
                                    if (pos_len > 0) pos_buf[pos_len++] = '\n';
                                    memcpy(pos_buf + pos_len, temp, plen);
                                    pos_len += plen;
                                    pos_count++;
                                }
                            }
                        }
                    }
                } else {
                    ms = skip_value(ms, me);
                }
            }
            p = me;
        } else if (klen == 8 && memcmp(ks, "SYNONYMS", 8) == 0 && *p == '[') {
            /* Parse synonyms array */
            p++;
            while (p < end && *p != ']') {
                p = skip_ws(p, end);
                if (*p == ',') { p++; continue; }
                if (*p == '"') {
                    p++;
                    const char* ss = p;
                    p = skip_string(p, end);
                    const char* se = p - 1;
                    int32_t slen = (int32_t)(se - ss);
                    
                    if (slen > 0 && syn_len + slen + 1 < (int32_t)sizeof(syn_buf)) {
                        if (syn_len > 0) syn_buf[syn_len++] = '\n';
                        memcpy(syn_buf + syn_len, ss, slen);
                        syn_len += slen;
                        syn_count++;
                    }
                } else {
                    break;
                }
            }
            if (p < end && *p == ']') p++;
        } else if (klen == 8 && memcmp(ks, "ANTONYMS", 8) == 0 && *p == '[') {
            /* Parse antonyms array */
            p++;
            while (p < end && *p != ']') {
                p = skip_ws(p, end);
                if (*p == ',') { p++; continue; }
                if (*p == '"') {
                    p++;
                    const char* as = p;
                    p = skip_string(p, end);
                    const char* ae = p - 1;
                    int32_t alen = (int32_t)(ae - as);
                    
                    if (alen > 0 && ant_len + alen + 1 < (int32_t)sizeof(ant_buf)) {
                        if (ant_len > 0) ant_buf[ant_len++] = '\n';
                        memcpy(ant_buf + ant_len, as, alen);
                        ant_len += alen;
                        ant_count++;
                    }
                } else {
                    break;
                }
            }
            if (p < end && *p == ']') p++;
        } else {
            p = skip_value(p, end);
        }
    }
    
    pos_buf[pos_len] = '\0';
    syn_buf[syn_len] = '\0';
    ant_buf[ant_len] = '\0';
    
    /* Write output */
    int32_t word_len = (int32_t)strlen(word);
    int32_t needed = 4 + word_len + 4 + 4 + pos_len + 4 + 4 + syn_len + 4 + 4 + ant_len;
    if (needed > out_len) return -3;
    
    int32_t pos = 0;
    
    /* Word */
    memcpy(out + pos, &word_len, 4); pos += 4;
    memcpy(out + pos, word, word_len); pos += word_len;
    
    /* POS */
    memcpy(out + pos, &pos_count, 4); pos += 4;
    memcpy(out + pos, &pos_len, 4); pos += 4;
    if (pos_len > 0) { memcpy(out + pos, pos_buf, pos_len); pos += pos_len; }
    
    /* Synonyms */
    memcpy(out + pos, &syn_count, 4); pos += 4;
    memcpy(out + pos, &syn_len, 4); pos += 4;
    if (syn_len > 0) { memcpy(out + pos, syn_buf, syn_len); pos += syn_len; }
    
    /* Antonyms */
    memcpy(out + pos, &ant_count, 4); pos += 4;
    memcpy(out + pos, &ant_len, 4); pos += 4;
    if (ant_len > 0) { memcpy(out + pos, ant_buf, ant_len); pos += ant_len; }
    
    return pos;
}

/**
 * @brief Load and parse a dictionary letter file
 * 
 * Reads file, parses JSON, and returns word list.
 * 
 * @param file_path Path to the JSON file
 * @param output Output buffer for words (newline-separated)
 * @param output_len Size of output buffer
 * @return Number of words, or negative on error
 */
int32_t simjot_json_load_dict_file(const char* file_path,
                                    char* output, int32_t output_len) {
    if (!file_path || !output || output_len <= 0) return -1;
    
    FILE* f = fopen(file_path, "rb");
    if (!f) return -2;
    
    /* Get file size */
    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (fsize <= 0 || fsize > 50 * 1024 * 1024) { /* Max 50MB */
        fclose(f);
        return -3;
    }
    
    /* Allocate and read file */
    char* json = (char*)malloc(fsize + 1);
    if (!json) {
        fclose(f);
        return -4;
    }
    
    size_t read = fread(json, 1, fsize, f);
    fclose(f);
    
    if ((long)read != fsize) {
        free(json);
        return -5;
    }
    json[fsize] = '\0';
    
    /* Parse and extract words */
    int32_t count = simjot_json_parse_dict_words(json, (int32_t)fsize, output, output_len);
    
    free(json);
    return count;
}
