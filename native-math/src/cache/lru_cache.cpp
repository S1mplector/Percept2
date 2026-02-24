/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - High-Performance LRU Cache
 * Thread-safe cache with configurable eviction policies
 */

#include "simjot_native.h"
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <mutex>
#include <shared_mutex>
#include <unordered_map>
#include <list>
#include <string>
#include <atomic>
#include <chrono>

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * LRU CACHE IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

struct CacheEntry {
    std::string key;
    void* value;
    size_t value_size;
    int64_t expire_time;  // 0 = no expiry
    std::list<std::string>::iterator lru_pos;
};

struct LRUCache {
    std::unordered_map<std::string, CacheEntry> entries;
    std::list<std::string> lru_list;  // Most recent at front
    mutable std::shared_mutex mutex;
    size_t max_entries;
    size_t max_memory;
    size_t current_memory;
    std::atomic<int64_t> hits{0};
    std::atomic<int64_t> misses{0};
    void (*free_value)(void*);  // Custom value destructor
};

static int64_t current_time_ms() {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
}

/**
 * Create an LRU cache (extended API with memory limit).
 * NOTE: Renamed to avoid ABI conflict with collections/collections.cpp (simple int32 API).
 * @param max_entries Maximum number of entries (0 = unlimited)
 * @param max_memory Maximum memory usage in bytes (0 = unlimited)
 * @return Cache handle
 */
void* simjot_lru_cache_create(int32_t max_entries, int64_t max_memory) {
    LRUCache* cache = new(std::nothrow) LRUCache();
    if (!cache) return nullptr;
    
    cache->max_entries = max_entries > 0 ? max_entries : SIZE_MAX;
    cache->max_memory = max_memory > 0 ? max_memory : SIZE_MAX;
    cache->current_memory = 0;
    cache->free_value = nullptr;
    
    return cache;
}

/**
 * Set custom value destructor.
 */
void simjot_lru_cache_set_destructor(void* cache_handle, void (*destructor)(void*)) {
    if (!cache_handle) return;
    LRUCache* cache = (LRUCache*)cache_handle;
    cache->free_value = destructor;
}

static void evict_lru(LRUCache* cache) {
    if (cache->lru_list.empty()) return;
    
    const std::string& oldest_key = cache->lru_list.back();
    auto it = cache->entries.find(oldest_key);
    
    if (it != cache->entries.end()) {
        cache->current_memory -= it->second.value_size;
        if (cache->free_value && it->second.value) {
            cache->free_value(it->second.value);
        }
        cache->entries.erase(it);
    }
    
    cache->lru_list.pop_back();
}

/**
 * Put a value in the cache.
 * @param key Key string
 * @param value Value pointer (cache takes ownership)
 * @param value_size Size of value in bytes
 * @param ttl_ms Time-to-live in milliseconds (0 = no expiry)
 * @return 1 on success
 */
int32_t simjot_lru_cache_put(void* cache_handle, const char* key,
                             void* value, int32_t value_size, int64_t ttl_ms) {
    if (!cache_handle || !key || !value || value_size <= 0) return 0;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    std::unique_lock lock(cache->mutex);
    
    std::string key_str(key);
    
    // Check if key exists
    auto it = cache->entries.find(key_str);
    if (it != cache->entries.end()) {
        // Update existing
        cache->current_memory -= it->second.value_size;
        if (cache->free_value && it->second.value) {
            cache->free_value(it->second.value);
        }
        
        it->second.value = value;
        it->second.value_size = value_size;
        it->second.expire_time = ttl_ms > 0 ? current_time_ms() + ttl_ms : 0;
        
        // Move to front of LRU
        cache->lru_list.erase(it->second.lru_pos);
        cache->lru_list.push_front(key_str);
        it->second.lru_pos = cache->lru_list.begin();
        
        cache->current_memory += value_size;
        return 1;
    }
    
    // Evict if necessary
    while (cache->entries.size() >= cache->max_entries ||
           cache->current_memory + value_size > cache->max_memory) {
        if (cache->lru_list.empty()) break;
        evict_lru(cache);
    }
    
    // Insert new entry
    cache->lru_list.push_front(key_str);
    
    CacheEntry entry;
    entry.key = key_str;
    entry.value = value;
    entry.value_size = value_size;
    entry.expire_time = ttl_ms > 0 ? current_time_ms() + ttl_ms : 0;
    entry.lru_pos = cache->lru_list.begin();
    
    cache->entries[key_str] = std::move(entry);
    cache->current_memory += value_size;
    
    return 1;
}

/**
 * Get a value from cache.
 * @param key Key string
 * @param out_size Output: value size
 * @return Value pointer or NULL if not found/expired
 */
void* simjot_lru_cache_get(void* cache_handle, const char* key, int32_t* out_size) {
    if (!cache_handle || !key) return nullptr;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    
    // Try read lock first
    {
        std::shared_lock lock(cache->mutex);
        
        auto it = cache->entries.find(key);
        if (it == cache->entries.end()) {
            cache->misses++;
            return nullptr;
        }
        
        // Check expiry
        if (it->second.expire_time > 0 && current_time_ms() > it->second.expire_time) {
            cache->misses++;
            // Need write lock to remove
        } else {
            cache->hits++;
            if (out_size) *out_size = (int32_t)it->second.value_size;
            return it->second.value;
        }
    }
    
    // Expired - remove with write lock
    {
        std::unique_lock lock(cache->mutex);
        auto it = cache->entries.find(key);
        if (it != cache->entries.end()) {
            cache->lru_list.erase(it->second.lru_pos);
            cache->current_memory -= it->second.value_size;
            if (cache->free_value && it->second.value) {
                cache->free_value(it->second.value);
            }
            cache->entries.erase(it);
        }
    }
    
    return nullptr;
}

/**
 * Check if key exists (without updating LRU).
 */
int32_t simjot_lru_cache_contains(void* cache_handle, const char* key) {
    if (!cache_handle || !key) return 0;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    std::shared_lock lock(cache->mutex);
    
    auto it = cache->entries.find(key);
    if (it == cache->entries.end()) return 0;
    
    // Check expiry
    if (it->second.expire_time > 0 && current_time_ms() > it->second.expire_time) {
        return 0;
    }
    
    return 1;
}

/**
 * Remove a key from cache.
 */
int32_t simjot_lru_cache_remove(void* cache_handle, const char* key) {
    if (!cache_handle || !key) return 0;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    std::unique_lock lock(cache->mutex);
    
    auto it = cache->entries.find(key);
    if (it == cache->entries.end()) return 0;
    
    cache->lru_list.erase(it->second.lru_pos);
    cache->current_memory -= it->second.value_size;
    if (cache->free_value && it->second.value) {
        cache->free_value(it->second.value);
    }
    cache->entries.erase(it);
    
    return 1;
}

/**
 * Clear all entries.
 */
void simjot_lru_cache_clear(void* cache_handle) {
    if (!cache_handle) return;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    std::unique_lock lock(cache->mutex);
    
    if (cache->free_value) {
        for (auto& pair : cache->entries) {
            if (pair.second.value) {
                cache->free_value(pair.second.value);
            }
        }
    }
    
    cache->entries.clear();
    cache->lru_list.clear();
    cache->current_memory = 0;
}

/**
 * Get cache statistics.
 */
void simjot_lru_cache_stats(void* cache_handle, int64_t* out_hits, int64_t* out_misses,
                            int32_t* out_size, int64_t* out_memory) {
    if (!cache_handle) return;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    std::shared_lock lock(cache->mutex);
    
    if (out_hits) *out_hits = cache->hits.load();
    if (out_misses) *out_misses = cache->misses.load();
    if (out_size) *out_size = (int32_t)cache->entries.size();
    if (out_memory) *out_memory = (int64_t)cache->current_memory;
}

/**
 * Destroy cache.
 */
void simjot_lru_cache_destroy(void* cache_handle) {
    if (!cache_handle) return;
    
    LRUCache* cache = (LRUCache*)cache_handle;
    
    if (cache->free_value) {
        for (auto& pair : cache->entries) {
            if (pair.second.value) {
                cache->free_value(pair.second.value);
            }
        }
    }
    
    delete cache;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING INTERNING (Deduplication)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct StringInterner {
    std::unordered_map<std::string, const char*> strings;
    std::list<std::string> storage;
    mutable std::shared_mutex mutex;
    std::atomic<int64_t> intern_count{0};
    std::atomic<int64_t> bytes_saved{0};
};

/**
 * Create string interner for deduplication.
 */
void* simjot_interner_create(void) {
    return new(std::nothrow) StringInterner();
}

/**
 * Intern a string (get canonical pointer).
 * @return Interned string pointer (valid for lifetime of interner)
 */
const char* simjot_interner_intern(void* interner_handle, const char* str) {
    if (!interner_handle || !str) return str;
    
    StringInterner* interner = (StringInterner*)interner_handle;
    
    // Try read lock first
    {
        std::shared_lock lock(interner->mutex);
        auto it = interner->strings.find(str);
        if (it != interner->strings.end()) {
            interner->bytes_saved += strlen(str) + 1;
            return it->second;
        }
    }
    
    // Need to insert
    {
        std::unique_lock lock(interner->mutex);
        
        // Double-check
        auto it = interner->strings.find(str);
        if (it != interner->strings.end()) {
            interner->bytes_saved += strlen(str) + 1;
            return it->second;
        }
        
        // Store string
        interner->storage.emplace_back(str);
        const char* ptr = interner->storage.back().c_str();
        interner->strings[str] = ptr;
        interner->intern_count++;
        
        return ptr;
    }
}

/**
 * Get interner statistics.
 */
void simjot_interner_stats(void* interner_handle, int64_t* out_count, int64_t* out_saved) {
    if (!interner_handle) return;
    
    StringInterner* interner = (StringInterner*)interner_handle;
    
    if (out_count) *out_count = interner->intern_count.load();
    if (out_saved) *out_saved = interner->bytes_saved.load();
}

/**
 * Destroy string interner.
 */
void simjot_interner_destroy(void* interner_handle) {
    delete (StringInterner*)interner_handle;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BLOOM FILTER (Fast Membership Test)
 * ═══════════════════════════════════════════════════════════════════════════ */

struct BloomFilter {
    uint64_t* bits;
    size_t bit_count;
    int32_t hash_count;
};

static uint64_t bloom_hash(const char* str, int32_t seed) {
    uint64_t h = seed * 0x9e3779b97f4a7c15ULL;
    for (const char* p = str; *p; p++) {
        h ^= *p;
        h *= 0x85ebca6b;
        h ^= h >> 13;
    }
    return h;
}

/**
 * Create bloom filter.
 * @param expected_items Expected number of items
 * @param false_positive_rate Target false positive rate (0.01 = 1%)
 */
void* simjot_bloom_create(int32_t expected_items, double false_positive_rate) {
    if (expected_items <= 0 || false_positive_rate <= 0 || false_positive_rate >= 1) {
        return nullptr;
    }
    
    // Calculate optimal size
    double ln2 = 0.693147;
    double ln_fpr = -log(false_positive_rate);
    size_t bit_count = (size_t)(-1.44 * expected_items * log(false_positive_rate) / (ln2 * ln2));
    int32_t hash_count = (int32_t)(bit_count / expected_items * ln2);
    
    if (hash_count < 1) hash_count = 1;
    if (hash_count > 16) hash_count = 16;
    
    // Round up to multiple of 64
    bit_count = (bit_count + 63) & ~63ULL;
    if (bit_count < 64) bit_count = 64;
    
    BloomFilter* filter = new(std::nothrow) BloomFilter();
    if (!filter) return nullptr;
    
    filter->bits = (uint64_t*)calloc(bit_count / 64, sizeof(uint64_t));
    if (!filter->bits) {
        delete filter;
        return nullptr;
    }
    
    filter->bit_count = bit_count;
    filter->hash_count = hash_count;
    
    return filter;
}

/**
 * Add item to bloom filter.
 */
void simjot_bloom_add(void* filter_handle, const char* item) {
    if (!filter_handle || !item) return;
    
    BloomFilter* filter = (BloomFilter*)filter_handle;
    
    for (int32_t i = 0; i < filter->hash_count; i++) {
        uint64_t h = bloom_hash(item, i + 1) % filter->bit_count;
        filter->bits[h / 64] |= (1ULL << (h % 64));
    }
}

/**
 * Test if item might be in filter.
 * @return 1 if possibly present, 0 if definitely not
 */
int32_t simjot_bloom_test(void* filter_handle, const char* item) {
    if (!filter_handle || !item) return 0;
    
    BloomFilter* filter = (BloomFilter*)filter_handle;
    
    for (int32_t i = 0; i < filter->hash_count; i++) {
        uint64_t h = bloom_hash(item, i + 1) % filter->bit_count;
        if (!(filter->bits[h / 64] & (1ULL << (h % 64)))) {
            return 0;
        }
    }
    
    return 1;
}

/**
 * Destroy bloom filter.
 */
void simjot_bloom_destroy(void* filter_handle) {
    if (!filter_handle) return;
    
    BloomFilter* filter = (BloomFilter*)filter_handle;
    free(filter->bits);
    delete filter;
}

} // extern "C"
