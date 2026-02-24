/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file collections.cpp
 * @brief High-Performance Collection Utilities for Simjot
 * 
 * C++ implementation of optimized data structures and collection operations
 * using STL and custom allocators for memory efficiency.
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <algorithm>
#include <cstring>
#include <functional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace {

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING SET (for fast membership testing)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StringSet {
    std::unordered_set<std::string> set;
    
    void add(std::string_view s) {
        set.emplace(s);
    }
    
    bool contains(std::string_view s) const {
        return set.find(std::string(s)) != set.end();
    }
    
    void remove(std::string_view s) {
        set.erase(std::string(s));
    }
    
    size_t size() const { return set.size(); }
    void clear() { set.clear(); }
};

static std::vector<StringSet> g_string_sets;

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING MAP (key-value store)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StringMap {
    std::unordered_map<std::string, std::string> map;
    
    void set(std::string_view key, std::string_view value) {
        map[std::string(key)] = std::string(value);
    }
    
    bool get(std::string_view key, std::string& out) const {
        auto it = map.find(std::string(key));
        if (it != map.end()) {
            out = it->second;
            return true;
        }
        return false;
    }
    
    bool has(std::string_view key) const {
        return map.find(std::string(key)) != map.end();
    }
    
    void remove(std::string_view key) {
        map.erase(std::string(key));
    }
    
    size_t size() const { return map.size(); }
    void clear() { map.clear(); }
};

static std::vector<StringMap> g_string_maps;

/* ═══════════════════════════════════════════════════════════════════════════
 * INTEGER MAP
 * ═══════════════════════════════════════════════════════════════════════════ */

struct IntMap {
    std::unordered_map<std::string, int64_t> map;
    
    void set(std::string_view key, int64_t value) {
        map[std::string(key)] = value;
    }
    
    int64_t get(std::string_view key, int64_t default_val = 0) const {
        auto it = map.find(std::string(key));
        return it != map.end() ? it->second : default_val;
    }
    
    int64_t increment(std::string_view key, int64_t delta = 1) {
        return map[std::string(key)] += delta;
    }
    
    bool has(std::string_view key) const {
        return map.find(std::string(key)) != map.end();
    }
    
    void remove(std::string_view key) {
        map.erase(std::string(key));
    }
    
    size_t size() const { return map.size(); }
    void clear() { map.clear(); }
};

static std::vector<IntMap> g_int_maps;

/* ═══════════════════════════════════════════════════════════════════════════
 * SORTED LIST (maintains sorted order)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct SortedStringList {
    std::vector<std::string> list;
    
    void add(std::string_view s) {
        auto it = std::lower_bound(list.begin(), list.end(), s);
        list.insert(it, std::string(s));
    }
    
    bool contains(std::string_view s) const {
        return std::binary_search(list.begin(), list.end(), s);
    }
    
    void remove(std::string_view s) {
        auto it = std::lower_bound(list.begin(), list.end(), s);
        if (it != list.end() && *it == s) {
            list.erase(it);
        }
    }
    
    const std::string& get(size_t index) const {
        static const std::string empty;
        return index < list.size() ? list[index] : empty;
    }
    
    size_t size() const { return list.size(); }
    void clear() { list.clear(); }
};

static std::vector<SortedStringList> g_sorted_lists;

/* ═══════════════════════════════════════════════════════════════════════════
 * FREQUENCY MAP (for word counting, etc.)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct FrequencyMap {
    std::unordered_map<std::string, int32_t> freq;
    int32_t total = 0;
    
    void add(std::string_view s, int32_t count = 1) {
        freq[std::string(s)] += count;
        total += count;
    }
    
    int32_t get(std::string_view s) const {
        auto it = freq.find(std::string(s));
        return it != freq.end() ? it->second : 0;
    }
    
    std::vector<std::pair<std::string, int32_t>> top_n(int n) const {
        std::vector<std::pair<std::string, int32_t>> sorted(freq.begin(), freq.end());
        std::partial_sort(sorted.begin(), 
                          sorted.begin() + std::min(n, static_cast<int>(sorted.size())),
                          sorted.end(),
                          [](const auto& a, const auto& b) { return a.second > b.second; });
        sorted.resize(std::min(n, static_cast<int>(sorted.size())));
        return sorted;
    }
    
    size_t unique_count() const { return freq.size(); }
    int32_t total_count() const { return total; }
    void clear() { freq.clear(); total = 0; }
};

static std::vector<FrequencyMap> g_freq_maps;

/* ═══════════════════════════════════════════════════════════════════════════
 * LRU CACHE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct LRUCache {
    struct Entry {
        std::string key;
        std::string value;
        int64_t access_time;
    };
    
    std::unordered_map<std::string, size_t> index;
    std::vector<Entry> entries;
    size_t max_size;
    int64_t access_counter = 0;
    
    LRUCache(size_t max = 1000) : max_size(max) {
        entries.reserve(max);
    }
    
    void set(std::string_view key, std::string_view value) {
        std::string k(key);
        auto it = index.find(k);
        
        if (it != index.end()) {
            entries[it->second].value = std::string(value);
            entries[it->second].access_time = ++access_counter;
            return;
        }
        
        if (entries.size() >= max_size) {
            evict_oldest();
        }
        
        size_t idx = entries.size();
        entries.push_back({k, std::string(value), ++access_counter});
        index[k] = idx;
    }
    
    bool get(std::string_view key, std::string& out) {
        auto it = index.find(std::string(key));
        if (it == index.end()) return false;
        
        entries[it->second].access_time = ++access_counter;
        out = entries[it->second].value;
        return true;
    }
    
    void evict_oldest() {
        if (entries.empty()) return;
        
        size_t oldest_idx = 0;
        int64_t oldest_time = entries[0].access_time;
        
        for (size_t i = 1; i < entries.size(); i++) {
            if (entries[i].access_time < oldest_time) {
                oldest_time = entries[i].access_time;
                oldest_idx = i;
            }
        }
        
        index.erase(entries[oldest_idx].key);
        
        if (oldest_idx != entries.size() - 1) {
            index[entries.back().key] = oldest_idx;
            entries[oldest_idx] = std::move(entries.back());
        }
        entries.pop_back();
    }
    
    size_t size() const { return entries.size(); }
    void clear() { entries.clear(); index.clear(); access_counter = 0; }
};

static std::vector<LRUCache> g_lru_caches;

} /* anonymous namespace */

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - STRING SET
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_set_create(void) {
    g_string_sets.emplace_back();
    return static_cast<int32_t>(g_string_sets.size() - 1);
}

void simjot_set_add(int32_t set_id, const char* str) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size() || !str) return;
    g_string_sets[set_id].add(str);
}

int32_t simjot_set_contains(int32_t set_id, const char* str) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size() || !str) return 0;
    return g_string_sets[set_id].contains(str) ? 1 : 0;
}

void simjot_set_remove(int32_t set_id, const char* str) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size() || !str) return;
    g_string_sets[set_id].remove(str);
}

int32_t simjot_set_size(int32_t set_id) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size()) return 0;
    return static_cast<int32_t>(g_string_sets[set_id].size());
}

void simjot_set_clear(int32_t set_id) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size()) return;
    g_string_sets[set_id].clear();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - STRING MAP
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_map_create(void) {
    g_string_maps.emplace_back();
    return static_cast<int32_t>(g_string_maps.size() - 1);
}

void simjot_map_set(int32_t map_id, const char* key, const char* value) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size() || !key || !value) return;
    g_string_maps[map_id].set(key, value);
}

int32_t simjot_map_get(int32_t map_id, const char* key, char* output, int32_t output_len) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size() || !key || !output || output_len <= 0) return 0;
    
    std::string value;
    if (!g_string_maps[map_id].get(key, value)) return 0;
    
    std::strncpy(output, value.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    return static_cast<int32_t>(value.size());
}

int32_t simjot_map_has(int32_t map_id, const char* key) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size() || !key) return 0;
    return g_string_maps[map_id].has(key) ? 1 : 0;
}

void simjot_map_remove(int32_t map_id, const char* key) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size() || !key) return;
    g_string_maps[map_id].remove(key);
}

int32_t simjot_map_size(int32_t map_id) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size()) return 0;
    return static_cast<int32_t>(g_string_maps[map_id].size());
}

void simjot_map_clear(int32_t map_id) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_string_maps.size()) return;
    g_string_maps[map_id].clear();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - FREQUENCY MAP
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_freq_create(void) {
    g_freq_maps.emplace_back();
    return static_cast<int32_t>(g_freq_maps.size() - 1);
}

void simjot_freq_add(int32_t map_id, const char* str, int32_t count) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size() || !str) return;
    g_freq_maps[map_id].add(str, count);
}

int32_t simjot_freq_get(int32_t map_id, const char* str) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size() || !str) return 0;
    return g_freq_maps[map_id].get(str);
}

int32_t simjot_freq_top_n(int32_t map_id, int32_t n, char* output, int32_t output_len) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size() || !output || output_len <= 0) return 0;
    
    auto top = g_freq_maps[map_id].top_n(n);
    
    std::string result;
    for (const auto& [word, count] : top) {
        if (!result.empty()) result += '\n';
        result += word + '\t' + std::to_string(count);
    }
    
    std::strncpy(output, result.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    
    return static_cast<int32_t>(top.size());
}

int32_t simjot_freq_unique_count(int32_t map_id) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size()) return 0;
    return static_cast<int32_t>(g_freq_maps[map_id].unique_count());
}

int32_t simjot_freq_total_count(int32_t map_id) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size()) return 0;
    return g_freq_maps[map_id].total_count();
}

void simjot_freq_clear(int32_t map_id) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size()) return;
    g_freq_maps[map_id].clear();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - LRU CACHE
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_cache_create(int32_t max_size) {
    g_lru_caches.emplace_back(max_size > 0 ? max_size : 1000);
    return static_cast<int32_t>(g_lru_caches.size() - 1);
}

void simjot_cache_set(int32_t cache_id, const char* key, const char* value) {
    if (cache_id < 0 || static_cast<size_t>(cache_id) >= g_lru_caches.size() || !key || !value) return;
    g_lru_caches[cache_id].set(key, value);
}

int32_t simjot_cache_get(int32_t cache_id, const char* key, char* output, int32_t output_len) {
    if (cache_id < 0 || static_cast<size_t>(cache_id) >= g_lru_caches.size() || !key || !output || output_len <= 0) return 0;
    
    std::string value;
    if (!g_lru_caches[cache_id].get(key, value)) return 0;
    
    std::strncpy(output, value.c_str(), output_len - 1);
    output[output_len - 1] = '\0';
    return static_cast<int32_t>(value.size());
}

int32_t simjot_cache_size(int32_t cache_id) {
    if (cache_id < 0 || static_cast<size_t>(cache_id) >= g_lru_caches.size()) return 0;
    return static_cast<int32_t>(g_lru_caches[cache_id].size());
}

void simjot_cache_clear(int32_t cache_id) {
    if (cache_id < 0 || static_cast<size_t>(cache_id) >= g_lru_caches.size()) return;
    g_lru_caches[cache_id].clear();
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC C API - BULK OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Add multiple strings to set (newline-separated)
 */
int32_t simjot_set_add_bulk(int32_t set_id, const char* strings) {
    if (set_id < 0 || static_cast<size_t>(set_id) >= g_string_sets.size() || !strings) return 0;
    
    int32_t count = 0;
    std::string current;
    
    for (const char* p = strings; ; p++) {
        char c = *p;
        if (c == '\0' || c == '\n') {
            if (!current.empty()) {
                g_string_sets[set_id].add(current);
                current.clear();
                count++;
            }
            if (c == '\0') break;
        } else {
            current.push_back(c);
        }
    }
    
    return count;
}

/**
 * @brief Add words from text to frequency map
 */
int32_t simjot_freq_add_text(int32_t map_id, const char* text) {
    if (map_id < 0 || static_cast<size_t>(map_id) >= g_freq_maps.size() || !text) return 0;
    
    int32_t count = 0;
    std::string current;
    
    for (const char* p = text; ; p++) {
        char c = *p;
        if (std::isalpha(static_cast<unsigned char>(c))) {
            current.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
        } else {
            if (!current.empty()) {
                g_freq_maps[map_id].add(current);
                current.clear();
                count++;
            }
            if (c == '\0') break;
        }
    }
    
    return count;
}

} /* extern "C" */
