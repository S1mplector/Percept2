/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file spell_dict.c
 * @brief Native Spell Check Engine for Simjot
 * 
 * High-performance spell checking implementation using hash tables for O(1) lookups,
 * edit-distance algorithms for suggestions, and morphological analysis for word forms.
 * 
 * Features:
 * - Dictionary loading from JSON files (simple-english-dictionary format)
 * - FNV-1a hash-based lookup with separate chaining
 * - Levenshtein/Damerau-Levenshtein edit distance for suggestions
 * - Word form recognition (plurals, possessives, verb tenses, adverbs)
 * - Frequency-weighted suggestion ranking
 * - User dictionary overlay for custom words
 * - Common misspelling autocorrect mappings
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <ctype.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

/* Hash table size - prime number for better distribution */
#define SPELL_BUCKETS 131071

/* Maximum word length to process (prevents buffer overflows) */
#define MAX_WORD_LEN 64

/* Maximum number of suggestions to return */
#define MAX_SUGGESTIONS 16

/**
 * @brief Dictionary word entry with frequency data
 * 
 * Uses flexible array member for variable-length word storage.
 * Frequency score is used to rank suggestions (common words first).
 */
typedef struct SpellWord {
    struct SpellWord* next;     /**< Next word in hash bucket chain */
    int32_t frequency;          /**< Word frequency score (higher = more common) */
    char word[];                /**< Null-terminated word (flexible array) */
} SpellWord;

/**
 * @brief Suggestion candidate with scoring
 * 
 * Used during edit-distance suggestion generation to track
 * candidate words and their relevance scores.
 */
typedef struct {
    char word[MAX_WORD_LEN];    /**< Suggested word */
    int32_t score;              /**< Ranking score (frequency + edit bonus) */
} SuggestionCandidate;

/* Main dictionary hash table */
static SpellWord* g_dictionaryBuckets[SPELL_BUCKETS];

/* User-added words overlay (takes precedence over main dictionary) */
static SpellWord* g_userBuckets[SPELL_BUCKETS];

/* Initialization flag */
static int g_spellInitialized = 0;

/* Total words loaded */
static size_t g_dictionaryCount = 0;

/**
 * @brief High-frequency English words for suggestion ranking
 * 
 * Words in this list get boosted frequency scores, ensuring
 * common words appear first in suggestions.
 */
static const char* HIGH_FREQ_WORDS[] = {
    "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
    "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
    "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
    "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
    "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
    NULL
};

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * HASH TABLE OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief FNV-1a hash function for strings
 * 
 * Fast, well-distributed hash function suitable for hash tables.
 * FNV-1a has good avalanche properties and is simple to implement.
 * 
 * @param word Null-terminated string to hash
 * @return 32-bit hash value
 */
static uint32_t hash_word(const char* word) {
    uint32_t hash = 2166136261u;  /* FNV offset basis */
    while (*word) {
        hash ^= (uint8_t)(*word++);
        hash *= 16777619u;        /* FNV prime */
    }
    return hash;
}

/**
 * @brief Get the bucket pointer for a word in a hash table
 */
static SpellWord** bucket_for_table(SpellWord** table, const char* word) {
    uint32_t h = hash_word(word);
    return &table[h % SPELL_BUCKETS];
}

/**
 * @brief Copy and normalize a word to lowercase
 * 
 * @param src Source string
 * @param len Length to copy
 * @param dst Destination buffer (must be at least len+1 bytes)
 */
static void normalize_copy(const char* src, size_t len, char* dst) {
    for (size_t i = 0; i < len; ++i) {
        unsigned char ch = (unsigned char)src[i];
        dst[i] = (char)tolower(ch);
    }
    dst[len] = '\0';
}

static int table_contains(SpellWord** table, const char* word) {
    SpellWord* node = *bucket_for_table(table, word);
    while (node) {
        if (strcmp(node->word, word) == 0) {
            return 1;
        }
        node = node->next;
    }
    return 0;
}

static SpellWord* table_find(SpellWord** table, const char* word) {
    SpellWord* node = *bucket_for_table(table, word);
    while (node) {
        if (strcmp(node->word, word) == 0) {
            return node;
        }
        node = node->next;
    }
    return NULL;
}

static int table_add_with_freq(SpellWord** table, const char* word, int32_t freq) {
    if (table_contains(table, word)) {
        return 1;
    }
    size_t len = strlen(word);
    SpellWord* node = (SpellWord*)malloc(sizeof(SpellWord) + len + 1);
    if (!node) {
        return 0;
    }
    memcpy(node->word, word, len + 1);
    node->frequency = freq;
    SpellWord** bucket = bucket_for_table(table, word);
    node->next = *bucket;
    *bucket = node;
    return 1;
}

static int table_add(SpellWord** table, const char* word) {
    return table_add_with_freq(table, word, 0);
}

static void table_clear(SpellWord** table) {
    for (size_t i = 0; i < SPELL_BUCKETS; ++i) {
        SpellWord* node = table[i];
        while (node) {
            SpellWord* next = node->next;
            free(node);
            node = next;
        }
        table[i] = NULL;
    }
}

static int32_t get_word_frequency(const char* word) {
    for (int i = 0; HIGH_FREQ_WORDS[i]; i++) {
        if (strcmp(word, HIGH_FREQ_WORDS[i]) == 0) {
            return 10000 - i * 100;
        }
    }
    return 1;
}

static int add_dictionary_word(const char* word, size_t len) {
    if (len == 0 || len >= MAX_WORD_LEN) return 1;
    char lower[MAX_WORD_LEN];
    normalize_copy(word, len, lower);
    int32_t freq = get_word_frequency(lower);
    int ok = table_add_with_freq(g_dictionaryBuckets, lower, freq);
    if (ok) g_dictionaryCount++;
    return ok;
}

static size_t skip_json_string(const char* data, size_t idx, size_t len) {
    while (idx < len) {
        char c = data[idx++];
        if (c == '\\') {
            if (idx < len) idx++;
        } else if (c == '"') {
            break;
        }
    }
    return idx;
}

static size_t skip_json_object(const char* data, size_t idx, size_t len) {
    int depth = 1;
    while (idx < len && depth > 0) {
        char c = data[idx++];
        if (c == '"') {
            idx = skip_json_string(data, idx, len);
        } else if (c == '{') {
            depth++;
        } else if (c == '}') {
            depth--;
        }
    }
    return idx;
}

static void parse_word_keys(const char* data, size_t len) {
    size_t idx = 0;
    while (idx < len && data[idx] != '{') idx++;
    if (idx >= len) return;
    idx++;
    while (idx < len) {
        while (idx < len && isspace((unsigned char)data[idx])) idx++;
        if (idx >= len) break;
        if (data[idx] != '"') {
            idx++;
            continue;
        }
        size_t keyStart = ++idx;
        idx = skip_json_string(data, idx, len);
        if (idx == 0 || idx > len) break;
        size_t keyEnd = idx - 1;
        size_t keyLen = keyEnd >= keyStart ? keyEnd - keyStart : 0;
        while (idx < len && isspace((unsigned char)data[idx])) idx++;
        if (idx >= len || data[idx] != ':') continue;
        idx++;
        while (idx < len && isspace((unsigned char)data[idx])) idx++;
        if (idx >= len) break;
        if (keyLen > 0) {
            add_dictionary_word(data + keyStart, keyLen);
        }
        if (data[idx] == '{') {
            idx = skip_json_object(data, idx + 1, len);
        }
    }
}

static int load_letter_file(const char* base_path, char letter) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/%c.json", base_path, letter);
    FILE* f = fopen(path, "rb");
    if (!f) {
        return 1;
    }
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return 0;
    }
    long size = ftell(f);
    if (size < 0) {
        fclose(f);
        return 0;
    }
    if (fseek(f, 0, SEEK_SET) != 0) {
        fclose(f);
        return 0;
    }
    char* buffer = (char*)malloc((size_t)size + 1);
    if (!buffer) {
        fclose(f);
        return 0;
    }
    size_t read = fread(buffer, 1, (size_t)size, f);
    fclose(f);
    buffer[read] = '\0';
    parse_word_keys(buffer, read);
    free(buffer);
    return 1;
}

static int load_dictionary(const char* base_path) {
    if (!base_path || !*base_path) {
        return 0;
    }
    for (char letter = 'a'; letter <= 'z'; ++letter) {
        if (!load_letter_file(base_path, letter)) {
            return 0;
        }
    }
    return g_dictionaryCount > 0;
}

/** 
 * ═══════════════════════════════════════════════════════════════════════════
 * EDIT DISTANCE ALGORITHMS
 * 
 * These algorithms compute the minimum number of single-character edits
 * (insertions, deletions, substitutions, transpositions) required to
 * transform one word into another. Used for spelling suggestions.
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Classic Levenshtein distance (insertions, deletions, substitutions)
 * 
 * Uses dynamic programming with O(n*m) time and O(n*m) space.
 * Limited to words of 32 characters or less to prevent stack overflow.
 * 
 * @param a First string
 * @param b Second string
 * @return Edit distance, or 100 if either string exceeds 32 chars
 */
static int levenshtein(const char* a, const char* b) {
    size_t la = strlen(a);
    size_t lb = strlen(b);
    if (la > 32 || lb > 32) return 100;
    
    int dp[33][33];
    for (size_t i = 0; i <= la; i++) dp[i][0] = (int)i;
    for (size_t j = 0; j <= lb; j++) dp[0][j] = (int)j;
    
    for (size_t i = 1; i <= la; i++) {
        for (size_t j = 1; j <= lb; j++) {
            int cost = (a[i-1] == b[j-1]) ? 0 : 1;
            int del = dp[i-1][j] + 1;
            int ins = dp[i][j-1] + 1;
            int sub = dp[i-1][j-1] + cost;
            dp[i][j] = del < ins ? (del < sub ? del : sub) : (ins < sub ? ins : sub);
        }
    }
    return dp[la][lb];
}

/**
 * @brief Damerau-Levenshtein distance (adds transposition to Levenshtein)
 * 
 * Extends Levenshtein to also consider adjacent character swaps as single edits.
 * More accurate for common typing errors like "teh" -> "the".
 * 
 * @param a First string
 * @param b Second string  
 * @return Edit distance, or 100 if either string exceeds 32 chars
 */
static int damerau_levenshtein(const char* a, const char* b) {
    size_t la = strlen(a);
    size_t lb = strlen(b);
    if (la > 32 || lb > 32) return 100;
    
    int dp[34][34];
    for (size_t i = 0; i <= la + 1; i++) dp[i][0] = (int)i;
    for (size_t j = 0; j <= lb + 1; j++) dp[0][j] = (int)j;
    
    for (size_t i = 1; i <= la; i++) {
        for (size_t j = 1; j <= lb; j++) {
            int cost = (a[i-1] == b[j-1]) ? 0 : 1;
            int del = dp[i-1][j] + 1;
            int ins = dp[i][j-1] + 1;
            int sub = dp[i-1][j-1] + cost;
            dp[i][j] = del < ins ? (del < sub ? del : sub) : (ins < sub ? ins : sub);
            
            /* Transposition: adjacent character swap */
            if (i > 1 && j > 1 && a[i-1] == b[j-2] && a[i-2] == b[j-1]) {
                int trans = dp[i-2][j-2] + cost;
                if (trans < dp[i][j]) dp[i][j] = trans;
            }
        }
    }
    return dp[la][lb];
}

static int dict_contains_normalized(const char* lower) {
    if (table_contains(g_userBuckets, lower)) return 1;
    return table_contains(g_dictionaryBuckets, lower);
}

/**
 *═══════════════════════════════════════════════════════════════════════════
 * MORPHOLOGICAL ANALYSIS
 * 
 * Recognizes common English word forms to reduce false spelling errors.
 * Checks if a word is a valid inflection of a dictionary word.
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Check if word is a valid morphological form of a dictionary word
 * 
 * Handles:
 * - Possessives: word's -> word
 * - Plurals: words -> word, boxes -> box
 * - Past tense: walked -> walk, loved -> love
 * - Present participle: walking -> walk, loving -> love
 * - Adverbs: quickly -> quick
 * 
 * @param lower Lowercase word to check
 * @return 1 if valid word form, 0 otherwise
 */
static int check_word_forms(const char* lower) {
    size_t len = strlen(lower);
    char base[MAX_WORD_LEN];
    
    // Possessives: word's -> word
    if (len > 2 && lower[len-2] == '\'' && lower[len-1] == 's') {
        strncpy(base, lower, len - 2);
        base[len - 2] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    // Plurals: words -> word
    if (len > 2 && lower[len-1] == 's') {
        strncpy(base, lower, len - 1);
        base[len - 1] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    // -es plurals: boxes -> box
    if (len > 3 && lower[len-2] == 'e' && lower[len-1] == 's') {
        strncpy(base, lower, len - 2);
        base[len - 2] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    // Past tense: walked -> walk, loved -> love
    if (len > 3 && lower[len-2] == 'e' && lower[len-1] == 'd') {
        strncpy(base, lower, len - 2);
        base[len - 2] = '\0';
        if (dict_contains_normalized(base)) return 1;
        strncpy(base, lower, len - 1);
        base[len - 1] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    // Present participle: walking -> walk, loving -> love
    if (len > 4 && lower[len-3] == 'i' && lower[len-2] == 'n' && lower[len-1] == 'g') {
        strncpy(base, lower, len - 3);
        base[len - 3] = '\0';
        if (dict_contains_normalized(base)) return 1;
        // Add 'e' back: loving -> love
        base[len - 3] = 'e';
        base[len - 2] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    // Adverbs: quickly -> quick
    if (len > 3 && lower[len-2] == 'l' && lower[len-1] == 'y') {
        strncpy(base, lower, len - 2);
        base[len - 2] = '\0';
        if (dict_contains_normalized(base)) return 1;
    }
    
    return 0;
}

// Insert candidate into sorted list (by score descending)
static void insert_candidate(SuggestionCandidate* list, int* count, int max,
                             const char* word, int32_t score) {
    // Check if already in list
    for (int i = 0; i < *count; i++) {
        if (strcmp(list[i].word, word) == 0) return;
    }
    
    // Find insertion position
    int pos = *count;
    while (pos > 0 && list[pos-1].score < score) {
        pos--;
    }
    
    if (pos >= max) return;
    
    // Shift elements
    if (*count < max) (*count)++;
    for (int i = *count - 1; i > pos; i--) {
        list[i] = list[i-1];
    }
    
    strncpy(list[pos].word, word, MAX_WORD_LEN - 1);
    list[pos].word[MAX_WORD_LEN - 1] = '\0';
    list[pos].score = score;
}

// Generate edit distance 1 candidates
static void generate_ed1_candidates(const char* word, SuggestionCandidate* list,
                                    int* count, int max) {
    size_t len = strlen(word);
    char candidate[MAX_WORD_LEN];
    
    // Deletions
    for (size_t i = 0; i < len; i++) {
        size_t k = 0;
        for (size_t j = 0; j < len; j++) {
            if (j != i) candidate[k++] = word[j];
        }
        candidate[k] = '\0';
        SpellWord* found = table_find(g_dictionaryBuckets, candidate);
        if (found) {
            insert_candidate(list, count, max, candidate, found->frequency + 1000);
        }
    }
    
    // Transpositions
    for (size_t i = 0; i < len - 1; i++) {
        strcpy(candidate, word);
        char tmp = candidate[i];
        candidate[i] = candidate[i+1];
        candidate[i+1] = tmp;
        SpellWord* found = table_find(g_dictionaryBuckets, candidate);
        if (found) {
            insert_candidate(list, count, max, candidate, found->frequency + 1500);
        }
    }
    
    // Replacements
    for (size_t i = 0; i < len; i++) {
        strcpy(candidate, word);
        for (char c = 'a'; c <= 'z'; c++) {
            if (c != word[i]) {
                candidate[i] = c;
                SpellWord* found = table_find(g_dictionaryBuckets, candidate);
                if (found) {
                    insert_candidate(list, count, max, candidate, found->frequency + 500);
                }
            }
        }
    }
    
    // Insertions
    for (size_t i = 0; i <= len; i++) {
        for (char c = 'a'; c <= 'z'; c++) {
            size_t k = 0;
            for (size_t j = 0; j < len; j++) {
                if (j == i) candidate[k++] = c;
                candidate[k++] = word[j];
            }
            if (i == len) candidate[k++] = c;
            candidate[k] = '\0';
            SpellWord* found = table_find(g_dictionaryBuckets, candidate);
            if (found) {
                insert_candidate(list, count, max, candidate, found->frequency + 300);
            }
        }
    }
}

// Generate edit distance 2 candidates (limited)
static void generate_ed2_candidates(const char* word, SuggestionCandidate* list,
                                    int* count, int max) {
    size_t len = strlen(word);
    if (len < 4 || *count >= max - 2) return;
    
    char ed1[MAX_WORD_LEN];
    char candidate[MAX_WORD_LEN];
    
    // Apply deletions, then deletions again
    for (size_t i = 0; i < len; i++) {
        size_t k = 0;
        for (size_t j = 0; j < len; j++) {
            if (j != i) ed1[k++] = word[j];
        }
        ed1[k] = '\0';
        size_t len1 = k;
        
        for (size_t m = 0; m < len1; m++) {
            k = 0;
            for (size_t n = 0; n < len1; n++) {
                if (n != m) candidate[k++] = ed1[n];
            }
            candidate[k] = '\0';
            SpellWord* found = table_find(g_dictionaryBuckets, candidate);
            if (found) {
                insert_candidate(list, count, max, candidate, found->frequency);
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// AUTOCORRECT MAPPINGS
// ═══════════════════════════════════════════════════════════════════════════

typedef struct {
    const char* wrong;
    const char* correct;
} AutocorrectEntry;

static const AutocorrectEntry AUTOCORRECT_MAP[] = {
    {"teh", "the"}, {"hte", "the"}, {"thier", "their"}, {"recieve", "receive"},
    {"wierd", "weird"}, {"occured", "occurred"}, {"seperate", "separate"},
    {"definately", "definitely"}, {"occurence", "occurrence"}, {"accomodate", "accommodate"},
    {"untill", "until"}, {"becuase", "because"}, {"beleive", "believe"},
    {"freind", "friend"}, {"goverment", "government"}, {"independant", "independent"},
    {"knowlege", "knowledge"}, {"liason", "liaison"}, {"mispell", "misspell"},
    {"noticable", "noticeable"}, {"occurrance", "occurrence"}, {"persistant", "persistent"},
    {"posession", "possession"}, {"priviledge", "privilege"}, {"publically", "publicly"},
    {"recomend", "recommend"}, {"refered", "referred"}, {"relevent", "relevant"},
    {"rythm", "rhythm"}, {"succesful", "successful"}, {"suprise", "surprise"},
    {"tommorrow", "tomorrow"}, {"truely", "truly"}, {"writting", "writing"},
    {"accross", "across"}, {"arguement", "argument"}, {"begining", "beginning"},
    {"calender", "calendar"}, {"catagory", "category"}, {"comming", "coming"},
    {"concious", "conscious"}, {"dissapear", "disappear"}, {"enviroment", "environment"},
    {"existance", "existence"}, {"familar", "familiar"}, {"finaly", "finally"},
    {"foriegn", "foreign"}, {"fourty", "forty"}, {"grammer", "grammar"},
    {"harrass", "harass"}, {"immediatly", "immediately"}, {"intresting", "interesting"},
    {"its", "it's"}, {"judgement", "judgment"}, {"libary", "library"},
    {"lisence", "license"}, {"maintainance", "maintenance"}, {"millenium", "millennium"},
    {"neccessary", "necessary"}, {"nickle", "nickel"}, {"nineth", "ninth"},
    {"ocasion", "occasion"}, {"occassion", "occasion"}, {"paralell", "parallel"},
    {"parliment", "parliament"}, {"peice", "piece"}, {"percieve", "perceive"},
    {"personnell", "personnel"}, {"pharoah", "pharaoh"}, {"plagarism", "plagiarism"},
    {"preceed", "precede"}, {"prefered", "preferred"}, {"pronounciation", "pronunciation"},
    {"que", "queue"}, {"reciept", "receipt"}, {"restaraunt", "restaurant"},
    {"scholarsip", "scholarship"}, {"sieze", "seize"}, {"speach", "speech"},
    {"therefor", "therefore"}, {"threshhold", "threshold"}, {"tomatos", "tomatoes"},
    {"tounge", "tongue"}, {"vaccuum", "vacuum"}, {"vegeterian", "vegetarian"},
    {"wether", "whether"}, {"wich", "which"}, {"yeild", "yield"},
    {"im", "I'm"}, {"dont", "don't"}, {"cant", "can't"}, {"wont", "won't"},
    {"isnt", "isn't"}, {"wasnt", "wasn't"}, {"didnt", "didn't"}, {"doesnt", "doesn't"},
    {"havent", "haven't"}, {"hasnt", "hasn't"}, {"wouldnt", "wouldn't"},
    {"couldnt", "couldn't"}, {"shouldnt", "shouldn't"}, {"youre", "you're"},
    {"theyre", "they're"}, {"were", "we're"}, {"ive", "I've"}, {"youve", "you've"},
    {"weve", "we've"}, {"theyve", "they've"}, {"lets", "let's"}, {"hes", "he's"},
    {"shes", "she's"}, {"whats", "what's"}, {"thats", "that's"}, {"whos", "who's"},
    {"heres", "here's"}, {"theres", "there's"}, {"wheres", "where's"},
    {NULL, NULL}
};

static const char* get_autocorrect(const char* word) {
    for (int i = 0; AUTOCORRECT_MAP[i].wrong; i++) {
        if (strcmp(word, AUTOCORRECT_MAP[i].wrong) == 0) {
            return AUTOCORRECT_MAP[i].correct;
        }
    }
    return NULL;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * 
 * These functions are exported and called from Java via Panama FFM.
 * All functions are thread-safe for concurrent read access after initialization.
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Initialize the spell check engine
 * 
 * Loads dictionary from JSON files (a.json through z.json) in the specified
 * directory. Must be called before any other spell functions.
 * 
 * @param base_path Directory containing dictionary JSON files
 * @return 1 on success, 0 on failure
 */
int32_t simjot_spell_init(const char* base_path) {
    if (g_spellInitialized) return 1;
    table_clear(g_userBuckets);
    g_dictionaryCount = 0;
    table_clear(g_dictionaryBuckets);
    if (!load_dictionary(base_path)) {
        return 0;
    }
    g_spellInitialized = 1;
    return 1;
}

/**
 * @brief Check if a word is spelled correctly
 * 
 * Checks the main dictionary, user dictionary, and morphological forms.
 * Single-character words are always considered valid.
 * 
 * @param word Word to check (case-insensitive)
 * @return 1 if correct, 0 if misspelled or invalid
 */
int32_t simjot_spell_contains(const char* word) {
    if (!g_spellInitialized || !word) return 0;
    size_t len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return 0;
    if (len == 1) return 1;  /* Single chars always valid */
    
    char lower[MAX_WORD_LEN];
    normalize_copy(word, len, lower);
    
    // Check direct match
    if (dict_contains_normalized(lower)) return 1;
    
    // Check word forms
    return check_word_forms(lower);
}

/**
 * @brief Get spelling suggestions for a misspelled word
 * 
 * Returns suggestions ranked by:
 * 1. Known autocorrect mappings (highest priority)
 * 2. Edit distance 1 candidates with frequency weighting
 * 3. Edit distance 2 candidates (if needed)
 * 
 * Output format: newline-separated list of suggestions.
 * 
 * @param word Misspelled word
 * @param max_results Maximum suggestions to return (capped at 16)
 * @param out Output buffer for suggestions
 * @param out_len Size of output buffer
 * @return Number of bytes written, or 0 on error
 */
int32_t simjot_spell_suggestions(const char* word, int32_t max_results, uint8_t* out, int32_t out_len) {
    if (!g_spellInitialized || !word || !out || out_len <= 0) return 0;
    if (max_results <= 0) max_results = 5;
    if (max_results > MAX_SUGGESTIONS) max_results = MAX_SUGGESTIONS;
    
    size_t len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return 0;
    
    char lower[MAX_WORD_LEN];
    normalize_copy(word, len, lower);
    
    // Check autocorrect first
    const char* autocorrect = get_autocorrect(lower);
    if (autocorrect) {
        size_t alen = strlen(autocorrect);
        if ((int32_t)alen < out_len) {
            memcpy(out, autocorrect, alen);
            return (int32_t)alen;
        }
    }
    
    SuggestionCandidate candidates[MAX_SUGGESTIONS];
    int count = 0;
    
    generate_ed1_candidates(lower, candidates, &count, max_results);
    if (count < 3) {
        generate_ed2_candidates(lower, candidates, &count, max_results);
    }
    
    // Build output string (newline-separated)
    int32_t written = 0;
    for (int i = 0; i < count && i < max_results; i++) {
        size_t wlen = strlen(candidates[i].word);
        if (written + (int32_t)wlen + 1 >= out_len) break;
        if (written > 0) {
            out[written++] = '\n';
        }
        memcpy(out + written, candidates[i].word, wlen);
        written += (int32_t)wlen;
    }
    
    return written;
}

/**
 * @brief Get the best autocorrect suggestion for a word
 * 
 * Returns the single best correction for instant autocorrect.
 * Prioritizes known autocorrect mappings, then best edit-distance match.
 * 
 * @param word Word to correct
 * @param out Output buffer for correction
 * @param out_len Size of output buffer
 * @return Length of correction, or 0 if no suggestion
 */
int32_t simjot_spell_best_correction(const char* word, char* out, int32_t out_len) {
    if (!g_spellInitialized || !word || !out || out_len <= 0) return 0;
    
    size_t len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return 0;
    
    char lower[MAX_WORD_LEN];
    normalize_copy(word, len, lower);
    
    // Check autocorrect first
    const char* autocorrect = get_autocorrect(lower);
    if (autocorrect) {
        size_t alen = strlen(autocorrect);
        if ((int32_t)alen < out_len) {
            memcpy(out, autocorrect, alen);
            out[alen] = '\0';
            return (int32_t)alen;
        }
    }
    
    // Get best suggestion
    SuggestionCandidate candidates[4];
    int count = 0;
    generate_ed1_candidates(lower, candidates, &count, 4);
    
    if (count > 0) {
        // Return the best candidate (highest score)
        size_t clen = strlen(candidates[0].word);
        if ((int32_t)clen < out_len) {
            memcpy(out, candidates[0].word, clen);
            out[clen] = '\0';
            return (int32_t)clen;
        }
    }
    
    return 0;
}

/**
 * @brief Add a word to the user dictionary
 * 
 * User dictionary words take precedence over the main dictionary
 * and will not be flagged as misspellings.
 * 
 * @param word Word to add (will be normalized to lowercase)
 * @return 1 on success, 0 on failure
 */
int32_t simjot_spell_add_user_word(const char* word) {
    if (!g_spellInitialized || !word) return 0;
    size_t len = strlen(word);
    if (len == 0 || len >= MAX_WORD_LEN) return 0;
    
    char lower[MAX_WORD_LEN];
    normalize_copy(word, len, lower);
    return table_add(g_userBuckets, lower);
}

/**
 * @brief Clear all user dictionary words
 * 
 * Removes all words added via simjot_spell_add_user_word.
 * Does not affect the main dictionary.
 * 
 * @return 1 always
 */
int32_t simjot_spell_clear_user_words(void) {
    table_clear(g_userBuckets);
    return 1;
}
