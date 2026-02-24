/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file rhyme_engine.cpp
 * @brief High-Performance Rhyme Analysis Engine for Simjot
 * 
 * C++ implementation using std::unordered_multimap for efficient
 * rhyme lookups and phonetic analysis.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace {

/* ═══════════════════════════════════════════════════════════════════════════
 * PHONETIC UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

std::string to_lower(std::string_view sv) {
    std::string result;
    result.reserve(sv.size());
    for (char c : sv) {
        result.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    return result;
}

bool is_vowel(char c) {
    c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

/**
 * @brief Extract rhyme key from word (vowel sound from last stressed syllable)
 */
std::string get_rhyme_key(std::string_view word) {
    if (word.empty()) return "";
    
    std::string lower = to_lower(word);
    
    /* Find last vowel cluster */
    int last_vowel_end = -1;
    int last_vowel_start = -1;
    
    for (int i = static_cast<int>(lower.size()) - 1; i >= 0; i--) {
        if (is_vowel(lower[i])) {
            if (last_vowel_end < 0) last_vowel_end = i;
            last_vowel_start = i;
        } else if (last_vowel_end >= 0 && last_vowel_start >= 0) {
            break;
        }
    }
    
    if (last_vowel_start < 0) {
        /* No vowels - use last few consonants */
        return lower.size() >= 2 ? lower.substr(lower.size() - 2) : lower;
    }
    
    /* Return from last vowel cluster to end */
    return lower.substr(last_vowel_start);
}

/**
 * @brief Get ending sound for near-rhyme detection
 */
std::string get_ending_sound(std::string_view word, int chars) {
    if (word.empty()) return "";
    std::string lower = to_lower(word);
    if (static_cast<int>(lower.size()) <= chars) return lower;
    return lower.substr(lower.size() - chars);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * RHYME DATABASE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct RhymeDatabase {
    /* Map rhyme key -> words */
    std::unordered_multimap<std::string, std::string> by_rhyme_key;
    
    /* Map ending -> words (for near rhymes) */
    std::unordered_multimap<std::string, std::string> by_ending_2;
    std::unordered_multimap<std::string, std::string> by_ending_3;
    
    /* All words */
    std::unordered_set<std::string> all_words;
    
    void add_word(std::string_view word) {
        std::string lower = to_lower(word);
        if (lower.empty() || all_words.count(lower)) return;
        
        all_words.insert(lower);
        
        std::string key = get_rhyme_key(lower);
        if (!key.empty()) {
            by_rhyme_key.emplace(key, lower);
        }
        
        std::string e2 = get_ending_sound(lower, 2);
        std::string e3 = get_ending_sound(lower, 3);
        
        if (!e2.empty()) by_ending_2.emplace(e2, lower);
        if (!e3.empty()) by_ending_3.emplace(e3, lower);
    }
    
    std::vector<std::string> find_rhymes(std::string_view word, int max_results) const {
        std::string key = get_rhyme_key(word);
        std::string lower = to_lower(word);
        
        std::vector<std::string> results;
        std::unordered_set<std::string> seen;
        seen.insert(lower);
        
        /* Perfect rhymes first */
        auto range = by_rhyme_key.equal_range(key);
        for (auto it = range.first; it != range.second && static_cast<int>(results.size()) < max_results; ++it) {
            if (seen.insert(it->second).second) {
                results.push_back(it->second);
            }
        }
        
        /* Near rhymes if needed */
        if (static_cast<int>(results.size()) < max_results) {
            std::string e3 = get_ending_sound(lower, 3);
            auto range3 = by_ending_3.equal_range(e3);
            for (auto it = range3.first; it != range3.second && static_cast<int>(results.size()) < max_results; ++it) {
                if (seen.insert(it->second).second) {
                    results.push_back(it->second);
                }
            }
        }
        
        return results;
    }
    
    void clear() {
        by_rhyme_key.clear();
        by_ending_2.clear();
        by_ending_3.clear();
        all_words.clear();
    }
    
    size_t size() const { return all_words.size(); }
};

static RhymeDatabase g_rhyme_db;
static std::vector<std::string> g_rhyme_results;

/* ═══════════════════════════════════════════════════════════════════════════
 * RHYME SCHEME DETECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

struct RhymeScheme {
    std::string pattern;  /* e.g., "ABAB", "AABB" */
    std::vector<std::pair<int, int>> rhyming_pairs;
};

std::string get_last_word(std::string_view line) {
    /* Find last word */
    size_t end = line.size();
    while (end > 0 && !std::isalpha(static_cast<unsigned char>(line[end - 1]))) {
        end--;
    }
    
    size_t start = end;
    while (start > 0 && std::isalpha(static_cast<unsigned char>(line[start - 1]))) {
        start--;
    }
    
    if (start >= end) return "";
    return to_lower(line.substr(start, end - start));
}

bool words_rhyme(std::string_view word1, std::string_view word2) {
    if (word1.empty() || word2.empty()) return false;
    
    std::string key1 = get_rhyme_key(word1);
    std::string key2 = get_rhyme_key(word2);
    
    if (key1 == key2 && key1.size() >= 2) return true;
    
    /* Check ending similarity */
    std::string e1 = get_ending_sound(word1, 2);
    std::string e2 = get_ending_sound(word2, 2);
    
    return e1 == e2 && !e1.empty();
}

RhymeScheme detect_rhyme_scheme(const std::vector<std::string_view>& lines) {
    RhymeScheme scheme;
    
    std::vector<std::string> end_words;
    for (const auto& line : lines) {
        end_words.push_back(get_last_word(line));
    }
    
    std::unordered_map<std::string, char> rhyme_groups;
    char next_letter = 'A';
    
    for (size_t i = 0; i < end_words.size(); i++) {
        const std::string& word = end_words[i];
        if (word.empty()) {
            scheme.pattern += 'X';
            continue;
        }
        
        /* Check if this word rhymes with any previous word */
        bool found = false;
        for (size_t j = 0; j < i; j++) {
            if (words_rhyme(word, end_words[j])) {
                std::string key = get_rhyme_key(end_words[j]);
                auto it = rhyme_groups.find(key);
                if (it != rhyme_groups.end()) {
                    scheme.pattern += it->second;
                    scheme.rhyming_pairs.emplace_back(static_cast<int>(j), static_cast<int>(i));
                    found = true;
                    break;
                }
            }
        }
        
        if (!found) {
            std::string key = get_rhyme_key(word);
            rhyme_groups[key] = next_letter;
            scheme.pattern += next_letter;
            if (next_letter < 'Z') next_letter++;
        }
    }
    
    return scheme;
}

static RhymeScheme g_scheme;

} /* anonymous namespace */

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Add word to rhyme database
 */
void simjot_rhyme_add_word(const char* word) {
    if (word) g_rhyme_db.add_word(word);
}

/**
 * @brief Add multiple words (space or newline separated)
 */
int32_t simjot_rhyme_add_words(const char* words) {
    if (!words) return 0;
    
    int count = 0;
    std::string current;
    
    for (const char* p = words; ; p++) {
        char c = *p;
        if (c == '\0' || c == ' ' || c == '\n' || c == '\t' || c == ',') {
            if (!current.empty()) {
                g_rhyme_db.add_word(current);
                current.clear();
                count++;
            }
            if (c == '\0') break;
        } else if (std::isalpha(static_cast<unsigned char>(c))) {
            current.push_back(c);
        }
    }
    
    return count;
}

/**
 * @brief Find rhymes for word
 * @return Number of rhymes found
 */
int32_t simjot_rhyme_find(const char* word, int32_t max_results) {
    if (!word) return 0;
    
    g_rhyme_results = g_rhyme_db.find_rhymes(word, max_results);
    return static_cast<int32_t>(g_rhyme_results.size());
}

/**
 * @brief Get rhyme result by index
 */
int32_t simjot_rhyme_get_result(int32_t index, char* output, int32_t output_len) {
    if (index < 0 || static_cast<size_t>(index) >= g_rhyme_results.size()) return 0;
    if (!output || output_len <= 0) return 0;
    
    const auto& word = g_rhyme_results[index];
    std::strncpy(output, word.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(word.size());
}

/**
 * @brief Get all rhyme results as newline-separated string
 */
int32_t simjot_rhyme_get_all_results(char* output, int32_t output_len) {
    if (!output || output_len <= 0) return 0;
    
    std::string result;
    for (const auto& word : g_rhyme_results) {
        if (!result.empty()) result += '\n';
        result += word;
    }
    
    std::strncpy(output, result.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(g_rhyme_results.size());
}

/**
 * @brief Get rhyme key for word
 */
int32_t simjot_rhyme_get_key(const char* word, char* output, int32_t output_len) {
    if (!word || !output || output_len <= 0) return 0;
    
    std::string key = get_rhyme_key(word);
    std::strncpy(output, key.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(key.size());
}

/**
 * @brief Check if two words rhyme
 */
int32_t simjot_rhyme_check(const char* word1, const char* word2) {
    if (!word1 || !word2) return 0;
    return words_rhyme(word1, word2) ? 1 : 0;
}

/**
 * @brief Detect rhyme scheme from text
 */
int32_t simjot_rhyme_detect_scheme(const char* text, char* output, int32_t output_len) {
    if (!text || !output || output_len <= 0) return 0;
    
    /* Split into lines */
    std::vector<std::string_view> lines;
    std::string_view sv(text);
    size_t start = 0;
    
    for (size_t i = 0; i < sv.size(); i++) {
        if (sv[i] == '\n') {
            if (i > start) {
                lines.push_back(sv.substr(start, i - start));
            }
            start = i + 1;
        }
    }
    if (start < sv.size()) {
        lines.push_back(sv.substr(start));
    }
    
    g_scheme = detect_rhyme_scheme(lines);
    
    std::strncpy(output, g_scheme.pattern.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(g_scheme.pattern.size());
}

/**
 * @brief Get number of rhyming pairs detected
 */
int32_t simjot_rhyme_get_pair_count(void) {
    return static_cast<int32_t>(g_scheme.rhyming_pairs.size());
}

/**
 * @brief Get rhyming pair by index
 */
int32_t simjot_rhyme_get_pair(int32_t index, int32_t* line1, int32_t* line2) {
    if (index < 0 || static_cast<size_t>(index) >= g_scheme.rhyming_pairs.size()) return 0;
    
    if (line1) *line1 = g_scheme.rhyming_pairs[index].first;
    if (line2) *line2 = g_scheme.rhyming_pairs[index].second;
    
    return 1;
}

/**
 * @brief Clear rhyme database
 */
void simjot_rhyme_clear(void) {
    g_rhyme_db.clear();
    g_rhyme_results.clear();
    g_scheme = RhymeScheme{};
}

/**
 * @brief Get database size
 */
int32_t simjot_rhyme_db_size(void) {
    return static_cast<int32_t>(g_rhyme_db.size());
}

} /* extern "C" */
