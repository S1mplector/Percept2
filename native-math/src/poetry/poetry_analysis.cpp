/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file poetry_analysis.cpp
 * @brief High-Performance Poetry Analysis Engine for Simjot
 * 
 * C++ implementation of poetry analysis algorithms using STL containers
 * and modern C++ features for optimal performance.
 * 
 * Features:
 * - Sound device detection (alliteration, assonance, consonance)
 * - Thematic analysis with keyword extraction
 * - Meter and rhythm analysis
 * - Vocabulary statistics
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace {

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

std::string to_lower(std::string_view sv) {
    std::string result;
    result.reserve(sv.size());
    for (char c : sv) {
        result.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    return result;
}

std::vector<std::string> split_words(std::string_view text) {
    std::vector<std::string> words;
    std::string current;
    words.reserve(text.size() / 4);
    current.reserve(16);
    
    for (char c : text) {
        if (std::isalpha(static_cast<unsigned char>(c)) || c == '\'') {
            current.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
        } else if (!current.empty()) {
            words.push_back(std::move(current));
            current.clear();
        }
    }
    if (!current.empty()) {
        words.push_back(std::move(current));
    }
    return words;
}

std::vector<std::string_view> split_lines(std::string_view text) {
    std::vector<std::string_view> lines;
    size_t start = 0;
    
    for (size_t i = 0; i < text.size(); i++) {
        if (text[i] == '\n') {
            if (i > start) {
                lines.push_back(text.substr(start, i - start));
            }
            start = i + 1;
        }
    }
    if (start < text.size()) {
        lines.push_back(text.substr(start));
    }
    return lines;
}

bool is_vowel(char c) {
    c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SOUND DEVICES DETECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

struct SoundDevice {
    std::string type;      /* "alliteration", "assonance", "consonance" */
    std::string pattern;   /* The repeated sound */
    std::vector<std::string> words;
    int line_number;
};

std::vector<SoundDevice> find_alliteration(const std::vector<std::string>& words, int line_num) {
    std::vector<SoundDevice> devices;
    if (words.size() < 2) return devices;
    
    std::unordered_map<char, std::vector<std::string>> initial_sounds;
    
    for (const auto& word : words) {
        if (word.empty()) continue;
        char initial = word[0];
        if (std::isalpha(static_cast<unsigned char>(initial))) {
            initial_sounds[initial].push_back(word);
        }
    }
    
    for (const auto& [sound, matching_words] : initial_sounds) {
        if (matching_words.size() >= 2) {
            SoundDevice device;
            device.type = "alliteration";
            device.pattern = std::string(1, sound);
            device.words = matching_words;
            device.line_number = line_num;
            devices.push_back(std::move(device));
        }
    }
    
    return devices;
}

std::vector<SoundDevice> find_assonance(const std::vector<std::string>& words, int line_num) {
    std::vector<SoundDevice> devices;
    if (words.size() < 2) return devices;
    
    /* Extract vowel patterns from each word */
    auto get_vowels = [](const std::string& word) -> std::string {
        std::string vowels;
        for (char c : word) {
            if (is_vowel(c)) vowels.push_back(c);
        }
        return vowels;
    };
    
    std::unordered_map<std::string, std::vector<std::string>> vowel_patterns;
    
    for (const auto& word : words) {
        std::string pattern = get_vowels(word);
        if (pattern.size() >= 2) {
            vowel_patterns[pattern].push_back(word);
        }
    }
    
    for (const auto& [pattern, matching_words] : vowel_patterns) {
        if (matching_words.size() >= 2) {
            SoundDevice device;
            device.type = "assonance";
            device.pattern = pattern;
            device.words = matching_words;
            device.line_number = line_num;
            devices.push_back(std::move(device));
        }
    }
    
    return devices;
}

std::vector<SoundDevice> find_consonance(const std::vector<std::string>& words, int line_num) {
    std::vector<SoundDevice> devices;
    if (words.size() < 2) return devices;
    
    /* Extract ending consonant clusters */
    auto get_ending = [](const std::string& word) -> std::string {
        if (word.size() < 2) return "";
        std::string ending;
        for (size_t i = word.size(); i > 0; i--) {
            char c = word[i - 1];
            if (!is_vowel(c) && std::isalpha(static_cast<unsigned char>(c))) {
                ending = c + ending;
            } else if (!ending.empty()) {
                break;
            }
        }
        return ending;
    };
    
    std::unordered_map<std::string, std::vector<std::string>> endings;
    
    for (const auto& word : words) {
        std::string ending = get_ending(word);
        if (!ending.empty()) {
            endings[ending].push_back(word);
        }
    }
    
    for (const auto& [ending, matching_words] : endings) {
        if (matching_words.size() >= 2) {
            SoundDevice device;
            device.type = "consonance";
            device.pattern = ending;
            device.words = matching_words;
            device.line_number = line_num;
            devices.push_back(std::move(device));
        }
    }
    
    return devices;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * THEMATIC ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════ */

struct ThemeKeywords {
    const char* theme;
    std::vector<const char*> keywords;
};

static const ThemeKeywords THEME_DATA[] = {
    {"love", {"love", "heart", "passion", "desire", "beloved", "embrace", "kiss", "tender", "affection", "devotion"}},
    {"nature", {"tree", "forest", "river", "mountain", "sky", "sun", "moon", "star", "flower", "garden", "ocean", "wind"}},
    {"death", {"death", "die", "grave", "tomb", "eternal", "mortal", "darkness", "end", "decay", "fade"}},
    {"time", {"time", "moment", "hour", "day", "night", "year", "age", "eternal", "fleeting", "memory"}},
    {"solitude", {"alone", "lonely", "solitude", "silence", "quiet", "empty", "isolated", "withdrawn", "peace"}},
    {"hope", {"hope", "dream", "wish", "faith", "light", "tomorrow", "believe", "aspire", "future", "promise"}},
    {"sorrow", {"sorrow", "grief", "tears", "weep", "mourn", "sad", "pain", "anguish", "despair", "melancholy"}},
    {"joy", {"joy", "happy", "delight", "bliss", "celebrate", "laugh", "smile", "cheerful", "merry", "elation"}},
    {"war", {"war", "battle", "fight", "soldier", "enemy", "victory", "defeat", "weapon", "blood", "conflict"}},
    {"freedom", {"free", "freedom", "liberty", "escape", "release", "chains", "wings", "soar", "boundless", "wild"}},
};

std::unordered_map<std::string, double> analyze_themes(const std::vector<std::string>& words) {
    std::unordered_map<std::string, double> theme_scores;
    
    /* Build word frequency map */
    std::unordered_map<std::string, int> word_freq;
    for (const auto& word : words) {
        word_freq[word]++;
    }
    
    /* Score each theme */
    for (const auto& theme_data : THEME_DATA) {
        double score = 0.0;
        for (const char* keyword : theme_data.keywords) {
            auto it = word_freq.find(keyword);
            if (it != word_freq.end()) {
                score += it->second * 1.0;
            }
        }
        if (score > 0) {
            theme_scores[theme_data.theme] = score / static_cast<double>(words.size()) * 100.0;
        }
    }
    
    return theme_scores;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * VOCABULARY STATISTICS
 * ═══════════════════════════════════════════════════════════════════════════ */

struct VocabStats {
    int total_words;
    int unique_words;
    double lexical_diversity;
    double avg_word_length;
    int longest_word_len;
    std::string longest_word;
    std::vector<std::pair<std::string, int>> top_words;
};

VocabStats compute_vocab_stats(const std::vector<std::string>& words) {
    VocabStats stats{};
    stats.total_words = static_cast<int>(words.size());
    
    if (words.empty()) {
        stats.lexical_diversity = 0.0;
        stats.avg_word_length = 0.0;
        return stats;
    }
    
    std::unordered_map<std::string, int> freq;
    size_t total_length = 0;
    
    for (const auto& word : words) {
        freq[word]++;
        total_length += word.size();
        
        if (word.size() > static_cast<size_t>(stats.longest_word_len)) {
            stats.longest_word_len = static_cast<int>(word.size());
            stats.longest_word = word;
        }
    }
    
    stats.unique_words = static_cast<int>(freq.size());
    stats.lexical_diversity = static_cast<double>(stats.unique_words) / 
                              static_cast<double>(stats.total_words) * 100.0;
    stats.avg_word_length = static_cast<double>(total_length) / 
                            static_cast<double>(stats.total_words);
    
    /* Get top words */
    std::vector<std::pair<std::string, int>> sorted_freq(freq.begin(), freq.end());
    std::sort(sorted_freq.begin(), sorted_freq.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });
    
    size_t top_n = std::min(sorted_freq.size(), size_t{10});
    stats.top_words.assign(sorted_freq.begin(), sorted_freq.begin() + top_n);
    
    return stats;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * METER ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Simplified syllable counting */
int count_syllables(std::string_view word) {
    if (word.empty()) return 0;
    
    int count = 0;
    bool prev_vowel = false;
    
    for (size_t i = 0; i < word.size(); i++) {
        char c = static_cast<char>(std::tolower(static_cast<unsigned char>(word[i])));
        bool is_v = is_vowel(c);
        
        if (is_v && !prev_vowel) {
            count++;
        }
        prev_vowel = is_v;
    }
    
    /* Adjust for silent e */
    if (word.size() > 2 && word.back() == 'e' && !is_vowel(word[word.size() - 2])) {
        if (count > 1) count--;
    }
    
    return count > 0 ? count : 1;
}

std::vector<int> get_line_syllables(const std::vector<std::string>& words) {
    std::vector<int> syllables;
    syllables.reserve(words.size());
    
    for (const auto& word : words) {
        syllables.push_back(count_syllables(word));
    }
    
    return syllables;
}

/* Global storage for results */
static std::vector<SoundDevice> g_sound_devices;
static std::unordered_map<std::string, double> g_themes;
static VocabStats g_vocab_stats;
static std::vector<int> g_line_syllable_counts;
static thread_local PoetryAnalysisResult g_analysis_result;

const char* detect_meter_name(const std::vector<int>& syllable_counts) {
    if (syllable_counts.empty()) return "";

    std::unordered_map<int, int> counts;
    counts.reserve(syllable_counts.size());
    for (int s : syllable_counts) {
        counts[s]++;
    }

    int most_common = 0;
    int max_freq = 0;
    for (const auto& [syllables, freq] : counts) {
        if (freq > max_freq) {
            max_freq = freq;
            most_common = syllables;
        }
    }

    if (most_common == 10) return "iambic pentameter";
    if (most_common == 8) return "tetrameter";
    if (most_common == 12) return "alexandrine";
    if (most_common == 14) return "fourteener";
    if (most_common == 5 || most_common == 7) return "haiku-like";
    return "free verse";
}

} /* anonymous namespace */

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Analyze poem for sound devices
 * @return Number of sound devices found
 */
int32_t simjot_poetry_analyze_sounds(const char* text) {
    if (!text) return 0;
    
    g_sound_devices.clear();
    
    std::string_view sv(text);
    auto lines = split_lines(sv);
    
    int line_num = 0;
    for (const auto& line : lines) {
        auto words = split_words(line);
        
        auto alliterations = find_alliteration(words, line_num);
        auto assonances = find_assonance(words, line_num);
        auto consonances = find_consonance(words, line_num);
        
        g_sound_devices.insert(g_sound_devices.end(), 
            std::make_move_iterator(alliterations.begin()),
            std::make_move_iterator(alliterations.end()));
        g_sound_devices.insert(g_sound_devices.end(),
            std::make_move_iterator(assonances.begin()),
            std::make_move_iterator(assonances.end()));
        g_sound_devices.insert(g_sound_devices.end(),
            std::make_move_iterator(consonances.begin()),
            std::make_move_iterator(consonances.end()));
        
        line_num++;
    }
    
    return static_cast<int32_t>(g_sound_devices.size());
}

/**
 * @brief Get sound device info by index
 */
int32_t simjot_poetry_get_sound_device(int32_t index, char* type_buf, int32_t type_len,
                                        char* pattern_buf, int32_t pattern_len,
                                        int32_t* line_number) {
    if (index < 0 || static_cast<size_t>(index) >= g_sound_devices.size()) return 0;
    
    const auto& device = g_sound_devices[index];
    
    if (type_buf && type_len > 0) {
        std::strncpy(type_buf, device.type.c_str(), type_len - 1);
        type_buf[type_len - 1] = '\0';
    }
    
    if (pattern_buf && pattern_len > 0) {
        std::strncpy(pattern_buf, device.pattern.c_str(), pattern_len - 1);
        pattern_buf[pattern_len - 1] = '\0';
    }
    
    if (line_number) {
        *line_number = device.line_number;
    }
    
    return static_cast<int32_t>(device.words.size());
}

/**
 * @brief Analyze themes in text
 * @return Number of themes detected
 */
int32_t simjot_poetry_analyze_themes(const char* text) {
    if (!text) return 0;
    
    g_themes.clear();
    
    auto words = split_words(text);
    g_themes = analyze_themes(words);
    
    return static_cast<int32_t>(g_themes.size());
}

/**
 * @brief Get theme score by name
 */
double simjot_poetry_get_theme_score(const char* theme) {
    if (!theme) return 0.0;
    
    auto it = g_themes.find(theme);
    return it != g_themes.end() ? it->second : 0.0;
}

/**
 * @brief Get all themes as newline-separated string
 */
int32_t simjot_poetry_get_themes(char* output, int32_t output_len) {
    if (!output || output_len <= 0) return 0;
    
    std::string result;
    for (const auto& [theme, score] : g_themes) {
        if (!result.empty()) result += '\n';
        result += theme;
    }
    
    std::strncpy(output, result.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(g_themes.size());
}

/**
 * @brief Compute vocabulary statistics
 */
int32_t simjot_poetry_analyze_vocab(const char* text) {
    if (!text) return 0;
    
    auto words = split_words(text);
    g_vocab_stats = compute_vocab_stats(words);
    
    return g_vocab_stats.total_words;
}

/**
 * @brief Get vocabulary statistics
 */
void simjot_poetry_get_vocab_stats(int32_t* total, int32_t* unique, 
                                    double* diversity, double* avg_len) {
    if (total) *total = g_vocab_stats.total_words;
    if (unique) *unique = g_vocab_stats.unique_words;
    if (diversity) *diversity = g_vocab_stats.lexical_diversity;
    if (avg_len) *avg_len = g_vocab_stats.avg_word_length;
}

/**
 * @brief Count syllables in word
 */
int32_t simjot_poetry_count_syllables(const char* word) {
    if (!word) return 0;
    return count_syllables(word);
}

/**
 * @brief Count syllables per line
 */
int32_t simjot_poetry_analyze_meter(const char* text) {
    if (!text) return 0;
    
    g_line_syllable_counts.clear();
    
    auto lines = split_lines(text);
    
    for (const auto& line : lines) {
        auto words = split_words(line);
        int syllables = 0;
        for (const auto& word : words) {
            syllables += count_syllables(word);
        }
        g_line_syllable_counts.push_back(syllables);
    }
    
    return static_cast<int32_t>(g_line_syllable_counts.size());
}

/**
 * @brief Get syllable count for line
 */
int32_t simjot_poetry_get_line_syllables(int32_t line_index) {
    if (line_index < 0 || static_cast<size_t>(line_index) >= g_line_syllable_counts.size()) {
        return 0;
    }
    return g_line_syllable_counts[line_index];
}

/**
 * @brief Detect potential meter type
 */
int32_t simjot_poetry_detect_meter(char* output, int32_t output_len) {
    if (!output || output_len <= 0 || g_line_syllable_counts.empty()) return 0;
    
    const char* meter = detect_meter_name(g_line_syllable_counts);
    
    std::strncpy(output, meter, output_len - 1);
    output[output_len - 1] = '\0';

    return static_cast<int32_t>(std::min<std::size_t>(std::strlen(meter), output_len - 1));
}

const PoetryAnalysisResult* simjot_poetry_analyze_all(const char* text) {
    if (!text) return nullptr;

    g_sound_devices.clear();
    g_themes.clear();
    g_line_syllable_counts.clear();
    g_vocab_stats = VocabStats{};

    std::string_view sv(text);
    auto lines = split_lines(sv);

    std::vector<std::string> all_words;
    all_words.reserve(sv.size() / 4);

    int line_num = 0;
    for (const auto& line : lines) {
        auto words = split_words(line);
        int syllables = 0;
        for (const auto& word : words) {
            syllables += count_syllables(word);
            all_words.push_back(word);
        }
        g_line_syllable_counts.push_back(syllables);

        if (!words.empty()) {
            auto alliterations = find_alliteration(words, line_num);
            auto assonances = find_assonance(words, line_num);
            auto consonances = find_consonance(words, line_num);

            g_sound_devices.insert(g_sound_devices.end(),
                std::make_move_iterator(alliterations.begin()),
                std::make_move_iterator(alliterations.end()));
            g_sound_devices.insert(g_sound_devices.end(),
                std::make_move_iterator(assonances.begin()),
                std::make_move_iterator(assonances.end()));
            g_sound_devices.insert(g_sound_devices.end(),
                std::make_move_iterator(consonances.begin()),
                std::make_move_iterator(consonances.end()));
        }

        line_num++;
    }

    g_vocab_stats = compute_vocab_stats(all_words);
    g_themes = analyze_themes(all_words);

    std::string dominant_theme;
    double best_score = 0.0;
    for (const auto& [theme, score] : g_themes) {
        if (score > best_score) {
            best_score = score;
            dominant_theme = theme;
        }
    }

    const char* meter = detect_meter_name(g_line_syllable_counts);

    g_analysis_result.ttr = g_vocab_stats.lexical_diversity;
    g_analysis_result.sound_device_count = static_cast<int32_t>(g_sound_devices.size());
    g_analysis_result.unique_words = g_vocab_stats.unique_words;
    g_analysis_result.total_words = g_vocab_stats.total_words;

    std::strncpy(g_analysis_result.dominant_theme, dominant_theme.c_str(),
                 sizeof(g_analysis_result.dominant_theme) - 1);
    g_analysis_result.dominant_theme[sizeof(g_analysis_result.dominant_theme) - 1] = '\0';
    std::strncpy(g_analysis_result.dominant_meter, meter,
                 sizeof(g_analysis_result.dominant_meter) - 1);
    g_analysis_result.dominant_meter[sizeof(g_analysis_result.dominant_meter) - 1] = '\0';

    return &g_analysis_result;
}

} /* extern "C" */
