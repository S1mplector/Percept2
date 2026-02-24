/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Fast String Search with SIMD
 * High-performance text search using SSE2/AVX2/NEON
 */

#include "simjot_native.h"
#include <cstdlib>
#include <cstring>
#include <cctype>
#include <algorithm>
#include <vector>

#ifdef __SSE2__
#include <emmintrin.h>
#endif

#ifdef __AVX2__
#include <immintrin.h>
#endif

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMD-ACCELERATED SEARCH
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef __SSE2__
/**
 * Find first occurrence of byte using SSE2.
 */
static const char* find_byte_sse2(const char* haystack, size_t len, char needle) {
    const __m128i needle_vec = _mm_set1_epi8(needle);
    
    // Align to 16 bytes
    size_t align_offset = (16 - ((uintptr_t)haystack & 15)) & 15;
    for (size_t i = 0; i < align_offset && i < len; i++) {
        if (haystack[i] == needle) return haystack + i;
    }
    
    // SIMD search
    const char* aligned = haystack + align_offset;
    size_t remaining = len - align_offset;
    
    while (remaining >= 16) {
        __m128i chunk = _mm_load_si128((const __m128i*)aligned);
        __m128i cmp = _mm_cmpeq_epi8(chunk, needle_vec);
        int mask = _mm_movemask_epi8(cmp);
        
        if (mask != 0) {
            int pos = __builtin_ctz(mask);
            return aligned + pos;
        }
        
        aligned += 16;
        remaining -= 16;
    }
    
    // Remainder
    for (size_t i = 0; i < remaining; i++) {
        if (aligned[i] == needle) return aligned + i;
    }
    
    return nullptr;
}
#endif

#ifdef __ARM_NEON
/**
 * Find first occurrence of byte using NEON.
 */
static const char* find_byte_neon(const char* haystack, size_t len, char needle) {
    const uint8x16_t needle_vec = vdupq_n_u8((uint8_t)needle);
    
    size_t i = 0;
    while (i + 16 <= len) {
        uint8x16_t chunk = vld1q_u8((const uint8_t*)(haystack + i));
        uint8x16_t cmp = vceqq_u8(chunk, needle_vec);
        
        // Check if any match
        uint64x2_t cmp64 = vreinterpretq_u64_u8(cmp);
        if (vgetq_lane_u64(cmp64, 0) || vgetq_lane_u64(cmp64, 1)) {
            for (int j = 0; j < 16; j++) {
                if (haystack[i + j] == needle) return haystack + i + j;
            }
        }
        i += 16;
    }
    
    // Remainder
    for (; i < len; i++) {
        if (haystack[i] == needle) return haystack + i;
    }
    
    return nullptr;
}
#endif

/**
 * Fast substring search using SIMD where available.
 * @return Position of first match, or -1 if not found
 */
int64_t simjot_search_find(const char* haystack, int64_t haystack_len,
                           const char* needle, int64_t needle_len) {
    if (!haystack || !needle || haystack_len <= 0 || needle_len <= 0) return -1;
    if (needle_len > haystack_len) return -1;
    
    // Single char optimization
    if (needle_len == 1) {
#ifdef __SSE2__
        const char* found = find_byte_sse2(haystack, haystack_len, needle[0]);
#elif defined(__ARM_NEON)
        const char* found = find_byte_neon(haystack, haystack_len, needle[0]);
#else
        const char* found = (const char*)memchr(haystack, needle[0], haystack_len);
#endif
        return found ? (found - haystack) : -1;
    }
    
    // Multi-char: find first char then verify
    char first = needle[0];
    const char* pos = haystack;
    const char* end = haystack + haystack_len - needle_len + 1;
    
    while (pos < end) {
#ifdef __SSE2__
        const char* found = find_byte_sse2(pos, end - pos, first);
#elif defined(__ARM_NEON)
        const char* found = find_byte_neon(pos, end - pos, first);
#else
        const char* found = (const char*)memchr(pos, first, end - pos);
#endif
        
        if (!found) return -1;
        
        if (memcmp(found, needle, needle_len) == 0) {
            return found - haystack;
        }
        
        pos = found + 1;
    }
    
    return -1;
}

/**
 * Case-insensitive search.
 */
int64_t simjot_search_find_ci(const char* haystack, int64_t haystack_len,
                              const char* needle, int64_t needle_len) {
    if (!haystack || !needle || haystack_len <= 0 || needle_len <= 0) return -1;
    if (needle_len > haystack_len) return -1;
    
    // Create lowercase needle
    char* lower_needle = (char*)malloc(needle_len + 1);
    if (!lower_needle) return -1;
    
    for (int64_t i = 0; i < needle_len; i++) {
        lower_needle[i] = (char)tolower((unsigned char)needle[i]);
    }
    lower_needle[needle_len] = '\0';
    
    char first_lower = lower_needle[0];
    char first_upper = (char)toupper((unsigned char)needle[0]);
    
    for (int64_t i = 0; i <= haystack_len - needle_len; i++) {
        char c = haystack[i];
        if (c == first_lower || c == first_upper) {
            bool match = true;
            for (int64_t j = 1; j < needle_len && match; j++) {
                char h = (char)tolower((unsigned char)haystack[i + j]);
                if (h != lower_needle[j]) match = false;
            }
            if (match) {
                free(lower_needle);
                return i;
            }
        }
    }
    
    free(lower_needle);
    return -1;
}

/**
 * Count occurrences of substring.
 */
int32_t simjot_search_count(const char* haystack, int64_t haystack_len,
                            const char* needle, int64_t needle_len) {
    if (!haystack || !needle || haystack_len <= 0 || needle_len <= 0) return 0;
    
    int32_t count = 0;
    int64_t pos = 0;
    
    while (pos <= haystack_len - needle_len) {
        int64_t found = simjot_search_find(haystack + pos, haystack_len - pos,
                                           needle, needle_len);
        if (found < 0) break;
        
        count++;
        pos += found + 1;
    }
    
    return count;
}

/**
 * Find all occurrences.
 * @param out_positions Output array for positions
 * @param max_results Maximum results to return
 * @return Number of matches found
 */
int32_t simjot_search_find_all(const char* haystack, int64_t haystack_len,
                               const char* needle, int64_t needle_len,
                               int64_t* out_positions, int32_t max_results) {
    if (!haystack || !needle || !out_positions || max_results <= 0) return 0;
    
    int32_t count = 0;
    int64_t pos = 0;
    
    while (pos <= haystack_len - needle_len && count < max_results) {
        int64_t found = simjot_search_find(haystack + pos, haystack_len - pos,
                                           needle, needle_len);
        if (found < 0) break;
        
        out_positions[count++] = pos + found;
        pos += found + 1;
    }
    
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * MULTI-PATTERN SEARCH (Aho-Corasick style)
 * ═══════════════════════════════════════════════════════════════════════════ */

#define AC_ALPHABET_SIZE 256
#define AC_MAX_STATES 4096

typedef struct {
    int32_t go[AC_ALPHABET_SIZE];
    int32_t fail;
    int32_t output;  // Pattern index, -1 if none
} ACState;

typedef struct {
    ACState states[AC_MAX_STATES];
    int32_t state_count;
    int32_t pattern_count;
} ACAutomaton;

/**
 * Build Aho-Corasick automaton for multi-pattern search.
 * @param patterns Null-separated pattern strings
 * @param pattern_count Number of patterns
 * @return Opaque automaton handle, or NULL on failure
 */
void* simjot_search_ac_build(const char* patterns, int32_t pattern_count) {
    if (!patterns || pattern_count <= 0) return nullptr;
    
    ACAutomaton* ac = (ACAutomaton*)calloc(1, sizeof(ACAutomaton));
    if (!ac) return nullptr;
    
    // Initialize root state
    for (int i = 0; i < AC_ALPHABET_SIZE; i++) {
        ac->states[0].go[i] = -1;
    }
    ac->states[0].fail = 0;
    ac->states[0].output = -1;
    ac->state_count = 1;
    ac->pattern_count = pattern_count;
    
    // Build trie
    const char* p = patterns;
    for (int32_t pat_idx = 0; pat_idx < pattern_count; pat_idx++) {
        int32_t state = 0;
        
        while (*p) {
            unsigned char c = (unsigned char)*p;
            
            if (ac->states[state].go[c] == -1) {
                if (ac->state_count >= AC_MAX_STATES) {
                    free(ac);
                    return nullptr;
                }
                
                int32_t new_state = ac->state_count++;
                for (int i = 0; i < AC_ALPHABET_SIZE; i++) {
                    ac->states[new_state].go[i] = -1;
                }
                ac->states[new_state].fail = 0;
                ac->states[new_state].output = -1;
                ac->states[state].go[c] = new_state;
            }
            
            state = ac->states[state].go[c];
            p++;
        }
        
        ac->states[state].output = pat_idx;
        p++; // Skip null terminator
    }
    
    // Build failure links using BFS
    std::vector<int32_t> queue;
    
    for (int c = 0; c < AC_ALPHABET_SIZE; c++) {
        if (ac->states[0].go[c] != -1) {
            ac->states[ac->states[0].go[c]].fail = 0;
            queue.push_back(ac->states[0].go[c]);
        } else {
            ac->states[0].go[c] = 0;
        }
    }
    
    size_t front = 0;
    while (front < queue.size()) {
        int32_t state = queue[front++];
        
        for (int c = 0; c < AC_ALPHABET_SIZE; c++) {
            if (ac->states[state].go[c] != -1) {
                int32_t next = ac->states[state].go[c];
                queue.push_back(next);
                
                int32_t fail = ac->states[state].fail;
                while (ac->states[fail].go[c] == -1) {
                    fail = ac->states[fail].fail;
                }
                ac->states[next].fail = ac->states[fail].go[c];
            }
        }
    }
    
    return ac;
}

/**
 * Search using Aho-Corasick automaton.
 * @param ac Automaton handle from simjot_search_ac_build
 * @param text Text to search
 * @param text_len Length of text
 * @param out_positions Output: match positions
 * @param out_patterns Output: which pattern matched
 * @param max_results Maximum results
 * @return Number of matches found
 */
int32_t simjot_search_ac_find(void* ac_handle, const char* text, int64_t text_len,
                              int64_t* out_positions, int32_t* out_patterns,
                              int32_t max_results) {
    if (!ac_handle || !text || !out_positions || !out_patterns) return 0;
    
    ACAutomaton* ac = (ACAutomaton*)ac_handle;
    int32_t count = 0;
    int32_t state = 0;
    
    for (int64_t i = 0; i < text_len && count < max_results; i++) {
        unsigned char c = (unsigned char)text[i];
        
        while (ac->states[state].go[c] == -1) {
            state = ac->states[state].fail;
        }
        state = ac->states[state].go[c];
        
        // Check for matches
        int32_t temp = state;
        while (temp != 0) {
            if (ac->states[temp].output >= 0) {
                out_positions[count] = i;
                out_patterns[count] = ac->states[temp].output;
                count++;
                if (count >= max_results) break;
            }
            temp = ac->states[temp].fail;
        }
    }
    
    return count;
}

/**
 * Free Aho-Corasick automaton.
 */
void simjot_search_ac_free(void* ac_handle) {
    free(ac_handle);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FUZZY SEARCH (Edit Distance Based)
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Find best fuzzy matches within max_distance.
 * @param text Text to search in
 * @param pattern Pattern to find
 * @param max_distance Maximum edit distance
 * @param out_positions Output match positions
 * @param out_distances Output edit distances
 * @param max_results Maximum results
 * @return Number of matches found
 */
int32_t simjot_search_fuzzy(const char* text, int64_t text_len,
                            const char* pattern, int32_t pattern_len,
                            int32_t max_distance,
                            int64_t* out_positions, int32_t* out_distances,
                            int32_t max_results) {
    if (!text || !pattern || !out_positions || !out_distances) return 0;
    if (pattern_len <= 0 || max_results <= 0) return 0;
    
    int32_t count = 0;
    int32_t window = pattern_len + max_distance;
    
    // Sliding window with edit distance
    for (int64_t i = 0; i <= text_len - pattern_len && count < max_results; i++) {
        // Quick filter: check if enough matching chars
        int32_t matches = 0;
        for (int32_t j = 0; j < pattern_len && i + j < text_len; j++) {
            if (text[i + j] == pattern[j]) matches++;
        }
        
        // Skip if too few matches
        if (matches < pattern_len - max_distance) continue;
        
        // Compute actual edit distance for this window
        int32_t len = std::min((int32_t)(text_len - i), window);
        
        // Simple Levenshtein for window
        std::vector<int32_t> prev(pattern_len + 1), curr(pattern_len + 1);
        
        for (int32_t j = 0; j <= pattern_len; j++) {
            prev[j] = j;
        }
        
        int32_t best_dist = pattern_len;
        
        for (int32_t ti = 0; ti < len; ti++) {
            curr[0] = 0;  // Allow free insertions at start
            
            for (int32_t pi = 1; pi <= pattern_len; pi++) {
                int32_t cost = (text[i + ti] == pattern[pi - 1]) ? 0 : 1;
                curr[pi] = std::min({prev[pi] + 1, curr[pi - 1] + 1, prev[pi - 1] + cost});
            }
            
            if (curr[pattern_len] < best_dist) {
                best_dist = curr[pattern_len];
            }
            
            std::swap(prev, curr);
        }
        
        if (best_dist <= max_distance) {
            out_positions[count] = i;
            out_distances[count] = best_dist;
            count++;
        }
    }
    
    return count;
}

} // extern "C"
