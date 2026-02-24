/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native Vocabulary Analyzer
 * High-performance text vocabulary analysis and readability metrics
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <math.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * WORD EXTRACTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Extract words from text into buffer.
 * Words are null-separated in output.
 * @param text Input text
 * @param out_words Output buffer for words
 * @param out_size Size of output buffer
 * @param out_count Number of words found
 * @return Total bytes used in output buffer
 */
int32_t simjot_vocab_extract_words(
    const char* text,
    char* out_words, int32_t out_size,
    int32_t* out_count
) {
    if (!text || !out_words || !out_count || out_size <= 0) return 0;
    
    *out_count = 0;
    int32_t pos = 0;
    int in_word = 0;
    int32_t word_start = 0;
    
    for (const char* p = text; ; p++) {
        int is_word_char = *p && (isalnum((unsigned char)*p) || *p == '\'');
        
        if (is_word_char && !in_word) {
            /* Start of word */
            word_start = pos;
            in_word = 1;
        }
        
        if (in_word) {
            if (is_word_char) {
                if (pos < out_size - 1) {
                    out_words[pos++] = (char)tolower((unsigned char)*p);
                }
            } else {
                /* End of word */
                if (pos > word_start && pos < out_size) {
                    out_words[pos++] = '\0';
                    (*out_count)++;
                }
                in_word = 0;
            }
        }
        
        if (!*p) break;
    }
    
    return pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * WORD FREQUENCY
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Simple hash table for word frequency */
#define VOCAB_HASH_SIZE 4096

typedef struct VocabEntry {
    char word[64];
    int32_t count;
    struct VocabEntry* next;
} VocabEntry;

typedef struct {
    VocabEntry* buckets[VOCAB_HASH_SIZE];
    int32_t unique_count;
    int32_t total_count;
} VocabTable;

static uint32_t vocab_hash(const char* word) {
    uint32_t h = 5381;
    for (const char* p = word; *p; p++) {
        h = ((h << 5) + h) + (unsigned char)*p;
    }
    return h % VOCAB_HASH_SIZE;
}

static VocabTable* vocab_create(void) {
    VocabTable* t = (VocabTable*)calloc(1, sizeof(VocabTable));
    return t;
}

static void vocab_free(VocabTable* t) {
    if (!t) return;
    for (int i = 0; i < VOCAB_HASH_SIZE; i++) {
        VocabEntry* e = t->buckets[i];
        while (e) {
            VocabEntry* next = e->next;
            free(e);
            e = next;
        }
    }
    free(t);
}

static void vocab_add(VocabTable* t, const char* word) {
    if (!t || !word || !*word) return;
    
    uint32_t h = vocab_hash(word);
    VocabEntry* e = t->buckets[h];
    
    while (e) {
        if (strcmp(e->word, word) == 0) {
            e->count++;
            t->total_count++;
            return;
        }
        e = e->next;
    }
    
    /* New word */
    e = (VocabEntry*)malloc(sizeof(VocabEntry));
    if (!e) return;
    
    strncpy(e->word, word, sizeof(e->word) - 1);
    e->word[sizeof(e->word) - 1] = '\0';
    e->count = 1;
    e->next = t->buckets[h];
    t->buckets[h] = e;
    t->unique_count++;
    t->total_count++;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SYLLABLE COUNTING
 * ═══════════════════════════════════════════════════════════════════════════ */

static int is_vowel(char c) {
    c = (char)tolower((unsigned char)c);
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

/**
 * Count syllables in a word (English heuristic).
 */
int32_t simjot_vocab_syllables(const char* word) {
    if (!word || !*word) return 0;
    
    int32_t count = 0;
    int prev_vowel = 0;
    size_t len = strlen(word);
    
    for (size_t i = 0; i < len; i++) {
        int vowel = is_vowel(word[i]);
        if (vowel && !prev_vowel) {
            count++;
        }
        prev_vowel = vowel;
    }
    
    /* Silent 'e' at end */
    if (len > 2 && tolower((unsigned char)word[len-1]) == 'e' && count > 1) {
        count--;
    }
    
    /* Common suffixes that don't add syllables */
    if (len > 3) {
        const char* end = word + len - 2;
        if (strcmp(end, "ed") == 0 && len > 4) {
            char prev = (char)tolower((unsigned char)word[len-3]);
            if (prev != 't' && prev != 'd') {
                /* 'ed' is silent unless preceded by t/d */
            }
        }
    }
    
    return count > 0 ? count : 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * READABILITY METRICS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Flesch Reading Ease score.
 * Higher = easier to read (0-100 scale typically)
 */
double simjot_vocab_flesch_ease(int32_t words, int32_t sentences, int32_t syllables) {
    if (words == 0 || sentences == 0) return 0.0;
    return 206.835 - 1.015 * ((double)words / sentences) - 84.6 * ((double)syllables / words);
}

/**
 * Flesch-Kincaid Grade Level.
 * Returns approximate US grade level.
 */
double simjot_vocab_flesch_kincaid(int32_t words, int32_t sentences, int32_t syllables) {
    if (words == 0 || sentences == 0) return 0.0;
    return 0.39 * ((double)words / sentences) + 11.8 * ((double)syllables / words) - 15.59;
}

/**
 * Gunning Fog Index.
 */
double simjot_vocab_gunning_fog(int32_t words, int32_t sentences, int32_t complex_words) {
    if (words == 0 || sentences == 0) return 0.0;
    return 0.4 * (((double)words / sentences) + 100.0 * ((double)complex_words / words));
}

/**
 * SMOG Index.
 */
double simjot_vocab_smog(int32_t polysyllabic, int32_t sentences) {
    if (polysyllabic == 0 || sentences == 0) return 0.0;
    return 1.043 * sqrt(polysyllabic * (30.0 / sentences)) + 3.1291;
}

/**
 * Coleman-Liau Index.
 */
double simjot_vocab_coleman_liau(int32_t words, int32_t sentences, int32_t characters) {
    if (words == 0 || sentences == 0) return 0.0;
    double l = ((double)characters / words) * 100;
    double s = ((double)sentences / words) * 100;
    return 0.0588 * l - 0.296 * s - 15.8;
}

/**
 * Automated Readability Index.
 */
double simjot_vocab_ari(int32_t words, int32_t sentences, int32_t characters) {
    if (words == 0 || sentences == 0) return 0.0;
    return 4.71 * ((double)characters / words) + 0.5 * ((double)words / sentences) - 21.43;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LEXICAL DIVERSITY METRICS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Type-Token Ratio.
 */
double simjot_vocab_ttr(int32_t unique_words, int32_t total_words) {
    if (total_words == 0) return 0.0;
    return (double)unique_words / total_words;
}

/**
 * Yule's K measure of lexical diversity.
 * Lower = more diverse vocabulary
 */
double simjot_vocab_yules_k(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words) {
    if (!freq_spectrum || total_words <= 1) return 0.0;
    
    double m1 = total_words;
    double m2 = 0.0;
    
    for (int32_t i = 1; i <= max_freq; i++) {
        m2 += (double)i * i * freq_spectrum[i];
    }
    
    if (m1 < 1e-10) return 0.0;
    return 10000.0 * (m2 - m1) / (m1 * m1);
}

/**
 * Simpson's D diversity index.
 */
double simjot_vocab_simpsons_d(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words) {
    if (!freq_spectrum || total_words <= 1) return 0.0;
    
    double sum = 0.0;
    for (int32_t i = 1; i <= max_freq; i++) {
        double ni = freq_spectrum[i];
        sum += ni * (ni - 1);
    }
    
    double n = total_words;
    return 1.0 - (sum / (n * (n - 1)));
}

/* ═══════════════════════════════════════════════════════════════════════════
 * COMPREHENSIVE ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Comprehensive vocabulary analysis.
 * @param text Input text to analyze
 * @param out_stats Output statistics array (20 doubles):
 *   0: total_words, 1: unique_words, 2: ttr,
 *   3: hapax_legomena, 4: dis_legomena, 5: avg_word_length,
 *   6: total_syllables, 7: avg_syllables_per_word, 8: polysyllabic_words,
 *   9: sentence_count, 10: character_count,
 *   11: flesch_ease, 12: flesch_kincaid, 13: gunning_fog,
 *   14: smog, 15: coleman_liau, 16: ari,
 *   17: lexical_density, 18: yules_k, 19: simpsons_d
 * @return 1 on success
 */
int32_t simjot_vocab_analyze(const char* text, double* out_stats) {
    if (!text || !out_stats) return 0;
    
    /* Extract words */
    size_t text_len = strlen(text);
    char* words_buf = (char*)malloc(text_len + 1);
    if (!words_buf) return 0;
    
    int32_t word_count;
    simjot_vocab_extract_words(text, words_buf, (int32_t)(text_len + 1), &word_count);
    
    if (word_count == 0) {
        free(words_buf);
        memset(out_stats, 0, 20 * sizeof(double));
        return 1;
    }
    
    /* Build frequency table */
    VocabTable* vocab = vocab_create();
    if (!vocab) {
        free(words_buf);
        return 0;
    }
    
    /* Process words */
    int32_t total_word_len = 0;
    int32_t total_syllables = 0;
    int32_t polysyllabic = 0;
    int32_t content_words = 0;
    
    /* Common function words to exclude from content word count */
    static const char* function_words[] = {
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "must", "shall", "can", "to", "of", "in",
        "for", "on", "with", "at", "by", "from", "as", "into", "through",
        "and", "but", "or", "nor", "so", "yet", "if", "then", "else",
        "this", "that", "these", "those", "it", "its", "he", "she", "they",
        "we", "you", "i", "me", "him", "her", "us", "them", "my", "your",
        "his", "her", "our", "their", "what", "which", "who", "whom", NULL
    };
    
    const char* word = words_buf;
    while (*word) {
        vocab_add(vocab, word);
        
        size_t wlen = strlen(word);
        total_word_len += (int32_t)wlen;
        
        int32_t syl = simjot_vocab_syllables(word);
        total_syllables += syl;
        if (syl >= 3) polysyllabic++;
        
        /* Check if function word */
        int is_func = 0;
        for (const char** fw = function_words; *fw; fw++) {
            if (strcmp(word, *fw) == 0) {
                is_func = 1;
                break;
            }
        }
        if (!is_func) content_words++;
        
        word += wlen + 1;
    }
    
    /* Count sentences */
    int32_t sentences = 0;
    for (const char* p = text; *p; p++) {
        if (*p == '.' || *p == '!' || *p == '?') sentences++;
    }
    if (sentences == 0) sentences = 1;
    
    /* Count characters (excluding spaces) */
    int32_t characters = 0;
    for (const char* p = text; *p; p++) {
        if (!isspace((unsigned char)*p)) characters++;
    }
    
    /* Hapax and dis legomena */
    int32_t hapax = 0, dis = 0;
    int32_t max_freq = 0;
    for (int i = 0; i < VOCAB_HASH_SIZE; i++) {
        for (VocabEntry* e = vocab->buckets[i]; e; e = e->next) {
            if (e->count == 1) hapax++;
            else if (e->count == 2) dis++;
            if (e->count > max_freq) max_freq = e->count;
        }
    }
    
    /* Build frequency spectrum for Yule's K and Simpson's D */
    int32_t* freq_spectrum = (int32_t*)calloc(max_freq + 1, sizeof(int32_t));
    if (freq_spectrum) {
        for (int i = 0; i < VOCAB_HASH_SIZE; i++) {
            for (VocabEntry* e = vocab->buckets[i]; e; e = e->next) {
                freq_spectrum[e->count]++;
            }
        }
    }
    
    /* Calculate metrics */
    int32_t total = vocab->total_count;
    int32_t unique = vocab->unique_count;
    
    out_stats[0] = (double)total;
    out_stats[1] = (double)unique;
    out_stats[2] = simjot_vocab_ttr(unique, total);
    out_stats[3] = (double)hapax;
    out_stats[4] = (double)dis;
    out_stats[5] = total > 0 ? (double)total_word_len / total : 0.0;
    out_stats[6] = (double)total_syllables;
    out_stats[7] = total > 0 ? (double)total_syllables / total : 0.0;
    out_stats[8] = (double)polysyllabic;
    out_stats[9] = (double)sentences;
    out_stats[10] = (double)characters;
    out_stats[11] = simjot_vocab_flesch_ease(total, sentences, total_syllables);
    out_stats[12] = simjot_vocab_flesch_kincaid(total, sentences, total_syllables);
    out_stats[13] = simjot_vocab_gunning_fog(total, sentences, polysyllabic);
    out_stats[14] = simjot_vocab_smog(polysyllabic, sentences);
    out_stats[15] = simjot_vocab_coleman_liau(total, sentences, characters);
    out_stats[16] = simjot_vocab_ari(total, sentences, characters);
    out_stats[17] = total > 0 ? (double)content_words / total : 0.0;
    out_stats[18] = freq_spectrum ? simjot_vocab_yules_k(freq_spectrum, max_freq, total) : 0.0;
    out_stats[19] = freq_spectrum ? simjot_vocab_simpsons_d(freq_spectrum, max_freq, total) : 0.0;
    
    /* Cleanup */
    if (freq_spectrum) free(freq_spectrum);
    vocab_free(vocab);
    free(words_buf);
    
    return 1;
}

/**
 * Get top N most frequent words.
 * @param text Input text
 * @param n Number of words to return
 * @param out_words Output buffer for words (null-separated)
 * @param out_counts Output counts for each word
 * @param out_size Size of output buffer
 * @return Number of words returned
 */
int32_t simjot_vocab_top_words(
    const char* text, int32_t n,
    char* out_words, int32_t* out_counts, int32_t out_size
) {
    if (!text || !out_words || !out_counts || n <= 0 || out_size <= 0) return 0;
    
    /* Extract and count words */
    size_t text_len = strlen(text);
    char* words_buf = (char*)malloc(text_len + 1);
    if (!words_buf) return 0;
    
    int32_t word_count;
    simjot_vocab_extract_words(text, words_buf, (int32_t)(text_len + 1), &word_count);
    
    VocabTable* vocab = vocab_create();
    if (!vocab) {
        free(words_buf);
        return 0;
    }
    
    const char* word = words_buf;
    while (*word) {
        vocab_add(vocab, word);
        word += strlen(word) + 1;
    }
    
    /* Find top N */
    typedef struct { const char* word; int32_t count; } WordCount;
    WordCount* top = (WordCount*)calloc(n, sizeof(WordCount));
    if (!top) {
        vocab_free(vocab);
        free(words_buf);
        return 0;
    }
    
    for (int i = 0; i < VOCAB_HASH_SIZE; i++) {
        for (VocabEntry* e = vocab->buckets[i]; e; e = e->next) {
            /* Find position in top N */
            int pos = n;
            for (int j = n - 1; j >= 0; j--) {
                if (!top[j].word || e->count > top[j].count) {
                    pos = j;
                } else {
                    break;
                }
            }
            
            if (pos < n) {
                /* Shift down */
                for (int j = n - 1; j > pos; j--) {
                    top[j] = top[j - 1];
                }
                top[pos].word = e->word;
                top[pos].count = e->count;
            }
        }
    }
    
    /* Output results */
    int32_t pos = 0;
    int32_t result_count = 0;
    
    for (int i = 0; i < n && top[i].word; i++) {
        size_t wlen = strlen(top[i].word);
        if (pos + wlen + 1 > (size_t)out_size) break;
        
        memcpy(out_words + pos, top[i].word, wlen);
        pos += (int32_t)wlen;
        out_words[pos++] = '\0';
        out_counts[result_count++] = top[i].count;
    }
    
    free(top);
    vocab_free(vocab);
    free(words_buf);
    
    return result_count;
}
