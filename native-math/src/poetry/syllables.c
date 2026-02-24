/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Syllable counting utilities for poetry analysis.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    const char* word;
    int count;
} syllable_override;

static const syllable_override SYLLABLE_OVERRIDES[] = {
    {"every", 3}, {"evening", 3},
    {"different", 3}, {"interesting", 4},
    {"beautiful", 3}, {"favorite", 3},
    {"family", 3}, {"chocolate", 3},
    {"comfortable", 4}, {"vegetable", 4},
    {"camera", 3}, {"actually", 4},
    {"generally", 4}, {"naturally", 4},
    {"practically", 4}, {"literally", 4},
    {"probably", 3}, {"definitely", 4},
    {"especially", 5}, {"unfortunately", 5},
    {"immediately", 5}, {"particularly", 5},
    {"area", 3}, {"idea", 3},
    {"real", 1}, {"really", 2},
    {"being", 2}, {"doing", 2},
    {"going", 2}, {"saying", 2},
    {"having", 2}, {"making", 2},
    {"taking", 2}, {"coming", 2},
    {"getting", 2}, {"looking", 2},
    {"nothing", 2}, {"something", 2},
    {"everything", 4}, {"everyone", 3},
    {"someone", 2}, {"anyone", 3},
    {"ourselves", 2}, {"themselves", 2},
    {"fire", 1}, {"desire", 2},
    {"hour", 1}, {"flower", 2},
    {"power", 2}, {"tower", 2},
    {"poem", 2}, {"poet", 2},
    {"poetry", 3}, {"quiet", 2},
    {"science", 2}, {"patient", 2},
    {"ancient", 2}, {"ocean", 2},
    {"heaven", 2}, {"seven", 2},
    {"even", 2}, {"given", 2},
    {"driven", 2}, {"written", 2},
    {"rhythm", 2}, {"prism", 2},
    {"naive", 2}, {"cafe", 2}
};

static const char* SILENT_E_EXCEPTIONS[] = {
    "be", "he", "me", "we", "she", "the", "cafe", "forte", "finale", "recipe",
    "adobe", "coyote", "karate", "maybe", "sesame", "simile", "apostrophe"
};

static int is_vowel(char c) {
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

static int ends_with(const char* word, const char* suffix) {
    size_t wlen = strlen(word);
    size_t slen = strlen(suffix);
    if (slen > wlen) return 0;
    return strcmp(word + (wlen - slen), suffix) == 0;
}

static int contains(const char* word, const char* needle) {
    return strstr(word, needle) != NULL;
}

static int is_silent_e_exception(const char* word) {
    size_t count = sizeof(SILENT_E_EXCEPTIONS) / sizeof(SILENT_E_EXCEPTIONS[0]);
    for (size_t i = 0; i < count; i++) {
        if (strcmp(word, SILENT_E_EXCEPTIONS[i]) == 0) return 1;
    }
    return 0;
}

static int override_count(const char* word) {
    size_t count = sizeof(SYLLABLE_OVERRIDES) / sizeof(SYLLABLE_OVERRIDES[0]);
    for (size_t i = 0; i < count; i++) {
        if (strcmp(word, SYLLABLE_OVERRIDES[i].word) == 0) return SYLLABLE_OVERRIDES[i].count;
    }
    return -1;
}

int32_t simjot_count_syllables(const char* word) {
    if (word == NULL || *word == '\0') return 0;

    size_t in_len = strlen(word);
    char* w = (char*)malloc(in_len + 1);
    if (!w) return 0;

    size_t out_len = 0;
    for (size_t i = 0; i < in_len; i++) {
        unsigned char c = (unsigned char)word[i];
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
            w[out_len++] = (char)tolower(c);
        } else if (c == '\'') {
            w[out_len++] = (char)c;
        }
    }
    w[out_len] = '\0';

    if (out_len == 0) {
        free(w);
        return 0;
    }

    int override = override_count(w);
    if (override >= 0) {
        free(w);
        return override;
    }

    int count = 0;
    int prev_vowel = 0;
    for (size_t i = 0; i < out_len; i++) {
        char c = w[i];
        int is_v = is_vowel(c);
        if (is_v && !prev_vowel) count++;
        prev_vowel = is_v;
    }

    if (ends_with(w, "e") && out_len > 2 && !is_silent_e_exception(w)) {
        char prev = w[out_len - 2];
        if (!is_vowel(prev) && prev != 'l') {
            count--;
        }
    }

    if (ends_with(w, "ed") && out_len > 3) {
        char prev = w[out_len - 3];
        if (prev != 't' && prev != 'd') {
            count--;
        }
    }

    if (contains(w, "ious") || contains(w, "eous")) count--;
    if (contains(w, "tion") || contains(w, "sion")) {
        if (count < 1) count = 1;
    }
    if (ends_with(w, "ism") && count < 2) count = 2;
    if (ends_with(w, "ity") && count < 2) count = 2;

    if (count < 1) count = 1;
    free(w);
    return count;
}
