/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Rhyme key utilities for poetry analysis.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

static const char* SILENT_E_EXCEPTIONS[] = {
    "be", "he", "me", "we", "she", "the", "cafe", "forte", "finale", "recipe",
    "adobe", "coyote", "karate", "maybe", "sesame", "simile", "apostrophe"
};

static int is_vowel(char c) {
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

static int is_silent_e_exception(const char* word) {
    size_t count = sizeof(SILENT_E_EXCEPTIONS) / sizeof(SILENT_E_EXCEPTIONS[0]);
    for (size_t i = 0; i < count; i++) {
        if (strcmp(word, SILENT_E_EXCEPTIONS[i]) == 0) return 1;
    }
    return 0;
}

static int normalize_word(const char* word, char* out, int32_t out_len) {
    if (!word || !out || out_len <= 1) return 0;
    int32_t len = 0;
    for (const char* p = word; *p; p++) {
        unsigned char c = (unsigned char)*p;
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
            if (len + 1 >= out_len) {
                out[0] = '\0';
                return 0;
            }
            out[len++] = (char)tolower(c);
        }
    }
    out[len] = '\0';
    return len;
}

int32_t simjot_rhyme_key(const char* word, char* out, int32_t out_len) {
    if (!word || !out || out_len <= 1) return 0;

    size_t in_len = strlen(word);
    char* w = (char*)malloc(in_len + 1);
    if (!w) {
        out[0] = '\0';
        return 0;
    }

    int32_t len = normalize_word(word, w, (int32_t)in_len + 1);
    if (len <= 0) {
        free(w);
        out[0] = '\0';
        return 0;
    }

    if (len > 2 && w[len - 1] == 'e' && !is_silent_e_exception(w)) {
        char prev = w[len - 2];
        if (!is_vowel(prev) && prev != 'l') {
            w[len - 1] = '\0';
            len -= 1;
        }
    }

    int32_t last_start = -1;
    int prev_vowel = 0;
    for (int32_t i = 0; i < len; i++) {
        int is_v = is_vowel(w[i]);
        if (is_v && !prev_vowel) last_start = i;
        prev_vowel = is_v;
    }

    int32_t key_start = 0;
    if (last_start < 0) {
        key_start = (len >= 2) ? (len - 2) : 0;
    } else {
        key_start = last_start;
    }

    int32_t key_len = len - key_start;
    const char* key = w + key_start;

    if (key_len > 2 && key[0] == 'e' && key[1] == 'a' && key[2] == 'r') {
        key += 1;
        key_len -= 1;
    }

    if (key_len <= 0 || out_len <= key_len) {
        free(w);
        out[0] = '\0';
        return 0;
    }

    if (key[0] == 'y') {
        out[0] = 'i';
        if (key_len > 1) {
            memcpy(out + 1, key + 1, (size_t)key_len - 1);
        }
        out[key_len] = '\0';
        free(w);
        return key_len;
    }

    memcpy(out, key, (size_t)key_len);
    out[key_len] = '\0';
    free(w);
    return key_len;
}

int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t out_len) {
    if (!word || !out || out_len <= 1) return 0;

    size_t in_len = strlen(word);
    char* w = (char*)malloc(in_len + 1);
    if (!w) {
        out[0] = '\0';
        return 0;
    }

    int32_t len = normalize_word(word, w, (int32_t)in_len + 1);
    if (len <= 0) {
        free(w);
        out[0] = '\0';
        return 0;
    }

    int32_t start = (len >= 3) ? (len - 3) : 0;
    int32_t key_len = len - start;
    if (out_len <= key_len) {
        free(w);
        out[0] = '\0';
        return 0;
    }

    memcpy(out, w + start, (size_t)key_len);
    out[key_len] = '\0';
    free(w);
    return key_len;
}
