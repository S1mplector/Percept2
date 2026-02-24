/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file autocorrect.c
 * @brief High-Performance Autocorrect Engine for Simjot
 * 
 * Native implementations of autocorrect functions:
 * - Keyboard adjacency correction (QWERTY layout)
 * - Phonetic correction
 * - Case preservation
 * - Context-aware phrase correction
 * 
 * @author S1mplector
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#define MAX_WORD_LEN 64

/* ═══════════════════════════════════════════════════════════════════════════
 * QWERTY KEYBOARD ADJACENCY MAP
 * ═══════════════════════════════════════════════════════════════════════════ */

static const char* QWERTY_ADJACENT[26] = {
    "qwsz",     /* a */
    "vghn",     /* b */
    "xdfv",     /* c */
    "serfcx",   /* d */
    "wrsd",     /* e */
    "drtgvc",   /* f */
    "ftyhbv",   /* g */
    "gyujnb",   /* h */
    "uojk",     /* i */
    "huikmn",   /* j */
    "jiolm",    /* k */
    "kop",      /* l */
    "njk",      /* m */
    "bhjm",     /* n */
    "ipkl",     /* o */
    "ol",       /* p */
    "wa",       /* q */
    "etdf",     /* r */
    "awedxz",   /* s */
    "ryfg",     /* t */
    "yihj",     /* u */
    "cfgb",     /* v */
    "qeas",     /* w */
    "zsdc",     /* x */
    "tugh",     /* y */
    "asx"       /* z */
};

/* Get adjacent keys for a character (returns NULL for non-alpha) */
static const char* get_adjacent_keys(char c) {
    c = tolower((unsigned char)c);
    if (c >= 'a' && c <= 'z') {
        return QWERTY_ADJACENT[c - 'a'];
    }
    return NULL;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PHONETIC PAIRS
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    const char* from;
    const char* to;
    int from_len;
    int to_len;
} PhoneticPair;

static const PhoneticPair PHONETIC_PAIRS[] = {
    {"ei", "ie", 2, 2},
    {"ie", "ei", 2, 2},
    {"tion", "sion", 4, 4},
    {"sion", "tion", 4, 4},
    {"ible", "able", 4, 4},
    {"able", "ible", 4, 4},
    {"ence", "ance", 4, 4},
    {"ance", "ence", 4, 4},
    {"ant", "ent", 3, 3},
    {"ent", "ant", 3, 3},
    {"er", "or", 2, 2},
    {"or", "er", 2, 2},
    {"c", "k", 1, 1},
    {"k", "c", 1, 1},
    {"ph", "f", 2, 1},
    {"f", "ph", 1, 2},
    {"gh", "f", 2, 1},
    {"ough", "uff", 4, 3},
    {"ough", "off", 4, 3},
    {NULL, NULL, 0, 0}
};

/* ═══════════════════════════════════════════════════════════════════════════
 * NEVER-CORRECT WORDS (common valid words that should not be auto-corrected)
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Hash function for quick lookup */
static unsigned int hash_word(const char* word) {
    unsigned int hash = 5381;
    while (*word) {
        hash = ((hash << 5) + hash) + (unsigned char)tolower(*word);
        word++;
    }
    return hash;
}

/* Simple hash set for never-correct words */
#define NEVER_CORRECT_BUCKETS 256
static const char* NEVER_CORRECT_WORDS[] = {
    "a", "i", "an", "as", "at", "be", "by", "do", "go", "he", "if", "in", "is", "it",
    "me", "my", "no", "of", "on", "or", "so", "to", "up", "us", "we",
    "all", "and", "any", "are", "but", "can", "did", "for", "get", "got", "had", "has",
    "her", "him", "his", "how", "its", "let", "may", "new", "not", "now", "off", "old",
    "one", "our", "out", "own", "put", "say", "see", "she", "the", "too", "two", "use",
    "was", "way", "who", "why", "yes", "yet", "you",
    "also", "back", "been", "both", "come", "does", "down", "each", "even", "find",
    "from", "give", "good", "have", "here", "into", "just", "know", "last", "like",
    "long", "look", "made", "make", "many", "more", "most", "much", "must", "need",
    "only", "over", "said", "same", "some", "such", "take", "tell", "than", "that",
    "them", "then", "they", "this", "time", "very", "want", "well", "went", "were",
    "what", "when", "will", "with", "work", "would", "year", "your",
    "uh", "um", "oh", "ah", "eh", "er", "hm", "mm", "ok", "okay",
    "thing", "think", "those", "these", "there", "where", "which", "while", "being",
    "going", "doing", "having", "making", "taking", "coming", "getting",
    NULL
};

static int is_never_correct(const char* word) {
    char lower[MAX_WORD_LEN];
    int len = strlen(word);
    if (len >= MAX_WORD_LEN) return 0;
    
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    
    for (int i = 0; NEVER_CORRECT_WORDS[i] != NULL; i++) {
        if (strcmp(lower, NEVER_CORRECT_WORDS[i]) == 0) {
            return 1;
        }
    }
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ADJACENT KEY CORRECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Find a correction by replacing each character with adjacent QWERTY keys.
 * Uses the spell dictionary to validate candidates.
 * 
 * @param word Input word (will be lowercased internally)
 * @param output Buffer for correction (null-terminated)
 * @param output_len Size of output buffer
 * @return Length of correction, 0 if no correction found, negative on error
 */
int32_t simjot_autocorrect_adjacent_key(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len < 2) return -1;
    
    int len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN || len >= output_len) return -1;
    
    /* Never correct common valid words */
    if (is_never_correct(word)) {
        output[0] = '\0';
        return 0;
    }
    
    /* Work buffer */
    char buffer[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        buffer[i] = tolower((unsigned char)word[i]);
    }
    
    /* Try replacing each character with adjacent keys */
    for (int i = 0; i < len; i++) {
        char original = buffer[i];
        const char* adjacent = get_adjacent_keys(original);
        if (!adjacent) continue;
        
        for (const char* adj = adjacent; *adj; adj++) {
            buffer[i] = *adj;
            
            /* Check if this is a valid word using spell dictionary */
            if (simjot_spell_contains(buffer) > 0) {
                /* Found a valid correction */
                memcpy(output, buffer, len);
                output[len] = '\0';
                return len;
            }
        }
        buffer[i] = original;  /* Restore */
    }
    
    output[0] = '\0';
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PHONETIC CORRECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Find a correction by replacing common phonetic patterns.
 * 
 * @param word Input word
 * @param output Buffer for correction
 * @param output_len Size of output buffer
 * @return Length of correction, 0 if none found, negative on error
 */
int32_t simjot_autocorrect_phonetic(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len < 2) return -1;
    
    int len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return -1;
    
    /* Never correct common valid words */
    if (is_never_correct(word)) {
        output[0] = '\0';
        return 0;
    }
    
    /* Lowercase the word */
    char lower[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    
    /* Try each phonetic substitution */
    for (int p = 0; PHONETIC_PAIRS[p].from != NULL; p++) {
        const PhoneticPair* pair = &PHONETIC_PAIRS[p];
        
        /* Find pattern in word */
        char* pos = strstr(lower, pair->from);
        if (!pos) continue;
        
        /* Calculate position and build candidate */
        int prefix_len = (int)(pos - lower);
        int suffix_start = prefix_len + pair->from_len;
        int new_len = prefix_len + pair->to_len + (len - suffix_start);
        
        if (new_len >= MAX_WORD_LEN || new_len >= output_len) continue;
        
        char candidate[MAX_WORD_LEN];
        memcpy(candidate, lower, prefix_len);
        memcpy(candidate + prefix_len, pair->to, pair->to_len);
        memcpy(candidate + prefix_len + pair->to_len, lower + suffix_start, len - suffix_start);
        candidate[new_len] = '\0';
        
        /* Check if valid word */
        if (simjot_spell_contains(candidate) > 0) {
            memcpy(output, candidate, new_len + 1);
            return new_len;
        }
    }
    
    output[0] = '\0';
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CASE PRESERVATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Apply the case pattern from original word to correction.
 * - ALL CAPS -> ALL CAPS
 * - Title Case -> Title Case
 * - lowercase -> lowercase
 * 
 * @param original Original word (for case reference)
 * @param correction Correction to apply case to (modified in place)
 * @param correction_len Length of correction
 * @return 0 on success, negative on error
 */
int32_t simjot_autocorrect_preserve_case(const char* original, char* correction, int32_t correction_len) {
    if (!original || !correction || correction_len <= 0) return -1;
    
    int orig_len = strlen(original);
    if (orig_len == 0) return 0;
    
    /* Check if all uppercase */
    int all_upper = 1;
    for (int i = 0; i < orig_len; i++) {
        if (isalpha((unsigned char)original[i]) && !isupper((unsigned char)original[i])) {
            all_upper = 0;
            break;
        }
    }
    
    if (all_upper) {
        /* Convert correction to uppercase */
        for (int i = 0; i < correction_len; i++) {
            correction[i] = toupper((unsigned char)correction[i]);
        }
        return 0;
    }
    
    /* Check if title case (first letter uppercase) */
    if (isupper((unsigned char)original[0])) {
        correction[0] = toupper((unsigned char)correction[0]);
        for (int i = 1; i < correction_len; i++) {
            correction[i] = tolower((unsigned char)correction[i]);
        }
        return 0;
    }
    
    /* Default: lowercase */
    for (int i = 0; i < correction_len; i++) {
        correction[i] = tolower((unsigned char)correction[i]);
    }
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * COMBINED CORRECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Try all correction strategies and return the best one.
 * Order: phonetic -> adjacent key -> spell suggestions
 * 
 * @param word Input word
 * @param output Buffer for correction
 * @param output_len Size of output buffer
 * @return Length of correction, 0 if none found, negative on error
 */
int32_t simjot_autocorrect_correct(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len < 2) return -1;
    
    int len = strlen(word);
    if (len <= 1 || len >= MAX_WORD_LEN) {
        output[0] = '\0';
        return 0;
    }
    
    /* Never correct common valid words */
    if (is_never_correct(word)) {
        output[0] = '\0';
        return 0;
    }
    
    /* Check if already correct */
    char lower[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    if (simjot_spell_contains(lower) > 0) {
        output[0] = '\0';
        return 0;
    }
    
    int result;
    
    /* Try phonetic correction first */
    result = simjot_autocorrect_phonetic(word, output, output_len);
    if (result > 0) {
        simjot_autocorrect_preserve_case(word, output, result);
        return result;
    }
    
    /* Try adjacent key correction */
    result = simjot_autocorrect_adjacent_key(word, output, output_len);
    if (result > 0) {
        simjot_autocorrect_preserve_case(word, output, result);
        return result;
    }
    
    /* Fall back to spell checker best correction */
    result = simjot_spell_best_correction(lower, output, output_len);
    if (result > 0) {
        /* Check edit distance - only accept close matches */
        int dist = simjot_levenshtein(lower, output);
        if (dist <= 2) {
            simjot_autocorrect_preserve_case(word, output, result);
            return result;
        }
    }
    
    output[0] = '\0';
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * VOWEL SOUND DETECTION (for a/an correction)
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Words starting with silent h (sound like vowel) */
static const char* SILENT_H_WORDS[] = {
    "hour", "honor", "honest", "heir", "herb", NULL
};

/* Words starting with consonant sound despite vowel letter */
static const char* CONSONANT_SOUND_WORDS[] = {
    "uni", "use", "user", "usual", "utility", "european", "one", "once", NULL
};

/**
 * Check if a word starts with a vowel sound (for a/an determination).
 * 
 * @param word Word to check
 * @return 1 if vowel sound, 0 if consonant sound, -1 on error
 */
int32_t simjot_autocorrect_starts_vowel_sound(const char* word) {
    if (!word || !*word) return -1;
    
    char lower[MAX_WORD_LEN];
    int len = strlen(word);
    if (len >= MAX_WORD_LEN) return -1;
    
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    
    /* Check silent h words */
    for (int i = 0; SILENT_H_WORDS[i] != NULL; i++) {
        if (strncmp(lower, SILENT_H_WORDS[i], strlen(SILENT_H_WORDS[i])) == 0) {
            return 1;  /* Vowel sound */
        }
    }
    
    /* Check consonant sound words (uni-, use-, etc.) */
    for (int i = 0; CONSONANT_SOUND_WORDS[i] != NULL; i++) {
        if (strncmp(lower, CONSONANT_SOUND_WORDS[i], strlen(CONSONANT_SOUND_WORDS[i])) == 0) {
            return 0;  /* Consonant sound */
        }
    }
    
    /* Default: check first letter */
    char first = lower[0];
    if (first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u') {
        return 1;
    }
    
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PHRASE CORRECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    const char* wrong;
    const char* correct;
} PhrasePair;

static const PhrasePair PHRASE_CORRECTIONS[] = {
    {"should of", "should have"},
    {"could of", "could have"},
    {"would of", "would have"},
    {"must of", "must have"},
    {"might of", "might have"},
    {"alot of", "a lot of"},
    {"incase of", "in case of"},
    {"apart of", "a part of"},
    {NULL, NULL}
};

/**
 * Check if a two-word phrase has a correction.
 * 
 * @param word1 First word
 * @param word2 Second word
 * @param output Buffer for corrected phrase
 * @param output_len Size of output buffer
 * @return Length of corrected phrase, 0 if none, negative on error
 */
int32_t simjot_autocorrect_phrase(const char* word1, const char* word2, 
                                   char* output, int32_t output_len) {
    if (!word1 || !word2 || !output || output_len < 4) return -1;
    
    /* Build the phrase */
    char phrase[MAX_WORD_LEN * 2 + 2];
    int len1 = strlen(word1);
    int len2 = strlen(word2);
    
    if (len1 + len2 + 2 >= sizeof(phrase)) return -1;
    
    for (int i = 0; i < len1; i++) {
        phrase[i] = tolower((unsigned char)word1[i]);
    }
    phrase[len1] = ' ';
    for (int i = 0; i < len2; i++) {
        phrase[len1 + 1 + i] = tolower((unsigned char)word2[i]);
    }
    phrase[len1 + 1 + len2] = '\0';
    
    /* Look up phrase */
    for (int i = 0; PHRASE_CORRECTIONS[i].wrong != NULL; i++) {
        if (strcmp(phrase, PHRASE_CORRECTIONS[i].wrong) == 0) {
            int result_len = strlen(PHRASE_CORRECTIONS[i].correct);
            if (result_len >= output_len) return -1;
            memcpy(output, PHRASE_CORRECTIONS[i].correct, result_len + 1);
            return result_len;
        }
    }
    
    output[0] = '\0';
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CAPITALIZATION FIXES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Fix common capitalization issues in text.
 * - Standalone "i" -> "I"
 * - Collapse double spaces
 * 
 * @param text Input text
 * @param output Buffer for fixed text
 * @param output_len Size of output buffer
 * @return Length of result, negative on error
 */
int32_t simjot_autocorrect_fix_caps(const char* text, char* output, int32_t output_len) {
    if (!text || !output || output_len <= 0) return -1;
    
    int out_pos = 0;
    int in_whitespace = 1;
    
    const char* p = text;
    while (*p && out_pos < output_len - 1) {
        char c = *p;
        
        /* Collapse multiple spaces */
        if (isspace((unsigned char)c)) {
            if (!in_whitespace && out_pos < output_len - 1) {
                output[out_pos++] = ' ';
                in_whitespace = 1;
            }
            p++;
            continue;
        }
        
        in_whitespace = 0;
        
        /* Check for standalone "i" */
        if (c == 'i' && (p == text || isspace((unsigned char)*(p-1)))) {
            /* Check if followed by space or end */
            if (*(p+1) == '\0' || isspace((unsigned char)*(p+1)) || 
                *(p+1) == '\'' || *(p+1) == ',' || *(p+1) == '.') {
                c = 'I';
            }
        }
        
        output[out_pos++] = c;
        p++;
    }
    
    /* Trim trailing space */
    while (out_pos > 0 && isspace((unsigned char)output[out_pos - 1])) {
        out_pos--;
    }
    
    output[out_pos] = '\0';
    return out_pos;
}
