/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native Spell Check Edit Distance Generator
 * Fast generation of edit-distance-1 and edit-distance-2 candidates
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#define MAX_WORD_LEN 64
#define MAX_CANDIDATES 1024

/* ═══════════════════════════════════════════════════════════════════════════
 * EDIT DISTANCE CANDIDATE GENERATION
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct {
    char words[MAX_CANDIDATES][MAX_WORD_LEN];
    int count;
} CandidateSet;

static void add_candidate(CandidateSet* set, const char* word) {
    if (set->count >= MAX_CANDIDATES) return;
    int len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return;
    
    // Check for duplicates (simple linear search - candidates are small)
    for (int i = 0; i < set->count; i++) {
        if (strcmp(set->words[i], word) == 0) return;
    }
    
    strcpy(set->words[set->count], word);
    set->count++;
}

static void generate_deletions(const char* word, int len, CandidateSet* out) {
    char buf[MAX_WORD_LEN];
    for (int i = 0; i < len; i++) {
        int k = 0;
        for (int j = 0; j < len; j++) {
            if (j != i) buf[k++] = word[j];
        }
        buf[k] = '\0';
        add_candidate(out, buf);
    }
}

static void generate_transpositions(const char* word, int len, CandidateSet* out) {
    char buf[MAX_WORD_LEN];
    strcpy(buf, word);
    for (int i = 0; i < len - 1; i++) {
        char tmp = buf[i];
        buf[i] = buf[i + 1];
        buf[i + 1] = tmp;
        add_candidate(out, buf);
        // Restore
        buf[i + 1] = buf[i];
        buf[i] = tmp;
    }
}

static void generate_replacements(const char* word, int len, CandidateSet* out) {
    char buf[MAX_WORD_LEN];
    strcpy(buf, word);
    for (int i = 0; i < len; i++) {
        char orig = buf[i];
        for (char c = 'a'; c <= 'z'; c++) {
            if (c != orig) {
                buf[i] = c;
                add_candidate(out, buf);
            }
        }
        buf[i] = orig;
    }
}

static void generate_insertions(const char* word, int len, CandidateSet* out) {
    char buf[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        for (char c = 'a'; c <= 'z'; c++) {
            int k = 0;
            for (int j = 0; j < i; j++) buf[k++] = word[j];
            buf[k++] = c;
            for (int j = i; j < len; j++) buf[k++] = word[j];
            buf[k] = '\0';
            add_candidate(out, buf);
        }
    }
}

/**
 * Generate all edit-distance-1 candidates for a word.
 * @param word Input word (lowercase)
 * @param output Buffer for null-separated candidates
 * @param output_len Buffer size
 * @return Number of bytes written, or negative if buffer too small
 */
int32_t simjot_spell_edit1(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len <= 0) return -1;
    
    int len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return -1;
    
    // Lowercase the word
    char lower[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    
    CandidateSet set = {0};
    
    generate_deletions(lower, len, &set);
    generate_transpositions(lower, len, &set);
    generate_replacements(lower, len, &set);
    generate_insertions(lower, len, &set);
    
    // Write to output buffer (null-separated)
    int pos = 0;
    for (int i = 0; i < set.count; i++) {
        int wlen = strlen(set.words[i]);
        if (pos + wlen + 1 > output_len) {
            return -(pos + wlen + 1);  // Return required size as negative
        }
        memcpy(output + pos, set.words[i], wlen);
        pos += wlen;
        output[pos++] = '\0';
    }
    
    return pos;
}

/**
 * Generate edit-distance-2 candidates (deletions and transpositions only for speed).
 * Full edit-2 would be O(54n * 54n) = huge, so we limit operations.
 */
int32_t simjot_spell_edit2(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len <= 0) return -1;
    
    int len = strlen(word);
    if (len < 4 || len >= MAX_WORD_LEN) return -1;  // Only for words >= 4 chars
    
    char lower[MAX_WORD_LEN];
    for (int i = 0; i <= len; i++) {
        lower[i] = tolower((unsigned char)word[i]);
    }
    
    CandidateSet ed1 = {0};
    CandidateSet ed2 = {0};
    
    // Generate edit-1 (deletions and transpositions only)
    generate_deletions(lower, len, &ed1);
    generate_transpositions(lower, len, &ed1);
    
    // For each edit-1, generate another deletion
    for (int i = 0; i < ed1.count; i++) {
        int wlen = strlen(ed1.words[i]);
        generate_deletions(ed1.words[i], wlen, &ed2);
    }
    
    // Write to output
    int pos = 0;
    for (int i = 0; i < ed2.count; i++) {
        int wlen = strlen(ed2.words[i]);
        if (wlen == 0) continue;
        if (pos + wlen + 1 > output_len) {
            return -(pos + wlen + 1);
        }
        memcpy(output + pos, ed2.words[i], wlen);
        pos += wlen;
        output[pos++] = '\0';
    }
    
    return pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LEVENSHTEIN DISTANCE (Optimized)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute Levenshtein distance between two strings.
 * Uses single-row optimization for O(n) space.
 */
int32_t simjot_levenshtein(const char* a, const char* b) {
    if (!a || !b) return -1;
    
    int m = strlen(a);
    int n = strlen(b);
    
    if (m == 0) return n;
    if (n == 0) return m;
    
    // Single row optimization
    int* row = (int*)malloc((n + 1) * sizeof(int));
    if (!row) return -1;
    
    for (int j = 0; j <= n; j++) row[j] = j;
    
    for (int i = 1; i <= m; i++) {
        int prev = row[0];
        row[0] = i;
        
        for (int j = 1; j <= n; j++) {
            int cost = (tolower((unsigned char)a[i-1]) == tolower((unsigned char)b[j-1])) ? 0 : 1;
            int del = row[j] + 1;
            int ins = row[j-1] + 1;
            int sub = prev + cost;
            
            prev = row[j];
            row[j] = del < ins ? (del < sub ? del : sub) : (ins < sub ? ins : sub);
        }
    }
    
    int result = row[n];
    free(row);
    return result;
}

/**
 * Compute Damerau-Levenshtein distance (allows transpositions).
 */
int32_t simjot_damerau_levenshtein(const char* a, const char* b) {
    if (!a || !b) return -1;
    
    int m = strlen(a);
    int n = strlen(b);
    
    if (m == 0) return n;
    if (n == 0) return m;
    
    // Need 2 previous rows for transposition check
    int* row0 = (int*)calloc(n + 1, sizeof(int));
    int* row1 = (int*)calloc(n + 1, sizeof(int));
    int* row2 = (int*)calloc(n + 1, sizeof(int));
    if (!row0 || !row1 || !row2) {
        free(row0); free(row1); free(row2);
        return -1;
    }
    
    for (int j = 0; j <= n; j++) row1[j] = j;
    
    for (int i = 1; i <= m; i++) {
        row2[0] = i;
        
        for (int j = 1; j <= n; j++) {
            char ca = tolower((unsigned char)a[i-1]);
            char cb = tolower((unsigned char)b[j-1]);
            int cost = (ca == cb) ? 0 : 1;
            
            int del = row1[j] + 1;
            int ins = row2[j-1] + 1;
            int sub = row1[j-1] + cost;
            
            int min = del < ins ? del : ins;
            if (sub < min) min = sub;
            
            // Transposition
            if (i > 1 && j > 1 && 
                ca == tolower((unsigned char)b[j-2]) && 
                tolower((unsigned char)a[i-2]) == cb) {
                int trans = row0[j-2] + cost;
                if (trans < min) min = trans;
            }
            
            row2[j] = min;
        }
        
        // Rotate rows
        int* tmp = row0;
        row0 = row1;
        row1 = row2;
        row2 = tmp;
    }
    
    int result = row1[n];
    free(row0); free(row1); free(row2);
    return result;
}

/**
 * Batch Levenshtein: compute distance from word to multiple candidates.
 * @param word The query word
 * @param candidates Null-separated candidate list
 * @param candidates_len Length of candidates buffer
 * @param distances Output array for distances
 * @param max_results Maximum results to return
 * @return Number of distances computed
 */
int32_t simjot_levenshtein_batch(const char* word, const char* candidates, int32_t candidates_len,
                                  int32_t* distances, int32_t max_results) {
    if (!word || !candidates || !distances || max_results <= 0) return 0;
    
    int count = 0;
    int pos = 0;
    
    while (pos < candidates_len && count < max_results) {
        const char* cand = candidates + pos;
        int clen = strlen(cand);
        if (clen == 0) {
            pos++;
            continue;
        }
        
        distances[count] = simjot_levenshtein(word, cand);
        count++;
        pos += clen + 1;
    }
    
    return count;
}
