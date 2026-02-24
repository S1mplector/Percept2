/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file link_detector.cpp
 * @brief Fast URL/link detection for text processing.
 * 
 * Provides high-performance URL detection using optimized string scanning
 * rather than full regex for common URL patterns.
 */

#include "simjot_native.h"
#include <cstring>
#include <cstdlib>
#include <algorithm>
#include <vector>
#include <mutex>

// ═══════════════════════════════════════════════════════════════════════════
// URL DETECTION STATE
// ═══════════════════════════════════════════════════════════════════════════

namespace {

// Valid URL scheme characters
constexpr bool is_url_char(char c) {
    // Alphanumeric
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        return true;
    }
    // Special URL chars: -._~:/?#[]@!$&'()*+,;=%
    switch (c) {
        case '-': case '.': case '_': case '~': case ':': case '/': case '?':
        case '#': case '[': case ']': case '@': case '!': case '$': case '&':
        case '\'': case '(': case ')': case '*': case '+': case ',': case ';':
        case '=': case '%':
            return true;
        default:
            return false;
    }
}

// Check if we're at start of a URL scheme
bool starts_with_scheme(const char* text, size_t len, size_t pos, size_t* scheme_end) {
    const char* p = text + pos;
    size_t remaining = len - pos;
    
    // http://
    if (remaining >= 7 && strncmp(p, "http://", 7) == 0) {
        *scheme_end = pos + 7;
        return true;
    }
    // https://
    if (remaining >= 8 && strncmp(p, "https://", 8) == 0) {
        *scheme_end = pos + 8;
        return true;
    }
    // ftp://
    if (remaining >= 6 && strncmp(p, "ftp://", 6) == 0) {
        *scheme_end = pos + 6;
        return true;
    }
    // www.
    if (remaining >= 4 && strncmp(p, "www.", 4) == 0) {
        *scheme_end = pos + 4;
        return true;
    }
    // WWW. (case insensitive)
    if (remaining >= 4 && 
        (p[0] == 'W' || p[0] == 'w') &&
        (p[1] == 'W' || p[1] == 'w') &&
        (p[2] == 'W' || p[2] == 'w') &&
        p[3] == '.') {
        *scheme_end = pos + 4;
        return true;
    }
    
    return false;
}

// Find end of URL starting from given position
size_t find_url_end(const char* text, size_t len, size_t start) {
    size_t end = start;
    int paren_depth = 0;
    int bracket_depth = 0;
    
    while (end < len) {
        char c = text[end];
        
        if (!is_url_char(c)) {
            break;
        }
        
        // Track parentheses for URLs like Wikipedia links
        if (c == '(') paren_depth++;
        else if (c == ')') {
            if (paren_depth > 0) paren_depth--;
            else break; // Unmatched closing paren ends URL
        }
        
        // Track brackets
        if (c == '[') bracket_depth++;
        else if (c == ']') {
            if (bracket_depth > 0) bracket_depth--;
            else break;
        }
        
        end++;
    }
    
    // Strip trailing punctuation that's likely not part of URL
    while (end > start) {
        char c = text[end - 1];
        if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?') {
            end--;
        } else {
            break;
        }
    }
    
    return end;
}

// Link range structure
struct LinkRange {
    int32_t start;
    int32_t end;
};

// Find all links in text
std::vector<LinkRange> find_all_links(const char* text, size_t len) {
    std::vector<LinkRange> links;
    size_t pos = 0;
    
    while (pos < len) {
        size_t scheme_end;
        if (starts_with_scheme(text, len, pos, &scheme_end)) {
            // Found a potential URL, find its end
            size_t url_end = find_url_end(text, len, scheme_end);
            
            // Minimum URL length check (scheme + at least some content)
            if (url_end > scheme_end + 2) {
                links.push_back({static_cast<int32_t>(pos), static_cast<int32_t>(url_end)});
            }
            
            pos = url_end;
        } else {
            pos++;
        }
    }
    
    return links;
}

} // anonymous namespace

// ═══════════════════════════════════════════════════════════════════════════
// PUBLIC API
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

/**
 * Check if text contains any URLs.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @return 1 if contains links, 0 if not
 */
int32_t simjot_link_contains(const char* text, int32_t len) {
    if (!text || len <= 0) return 0;
    
    size_t pos = 0;
    size_t text_len = static_cast<size_t>(len);
    
    while (pos < text_len) {
        size_t scheme_end;
        if (starts_with_scheme(text, text_len, pos, &scheme_end)) {
            size_t url_end = find_url_end(text, text_len, scheme_end);
            if (url_end > scheme_end + 2) {
                return 1; // Found at least one link
            }
            pos = url_end;
        } else {
            pos++;
        }
    }
    
    return 0;
}

/**
 * Count number of URLs in text.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @return Number of URLs found
 */
int32_t simjot_link_count(const char* text, int32_t len) {
    if (!text || len <= 0) return 0;
    
    auto links = find_all_links(text, static_cast<size_t>(len));
    return static_cast<int32_t>(links.size());
}

/**
 * Find all link ranges in text.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @param out_ranges Output buffer for [start, end] pairs (must be 2*max_ranges ints)
 * @param max_ranges Maximum number of ranges to return
 * @return Number of ranges found (may be less than max_ranges)
 */
int32_t simjot_link_find_ranges(
    const char* text, 
    int32_t len,
    int32_t* out_ranges,
    int32_t max_ranges
) {
    if (!text || len <= 0 || !out_ranges || max_ranges <= 0) return 0;
    
    auto links = find_all_links(text, static_cast<size_t>(len));
    
    int32_t count = std::min(static_cast<int32_t>(links.size()), max_ranges);
    for (int32_t i = 0; i < count; i++) {
        out_ranges[i * 2] = links[i].start;
        out_ranges[i * 2 + 1] = links[i].end;
    }
    
    return count;
}

/**
 * Extract first URL from text.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @param out_url Output buffer for URL (null-terminated)
 * @param out_len Size of output buffer
 * @return Length of URL written (excluding null), or 0 if none found
 */
int32_t simjot_link_extract_first(
    const char* text,
    int32_t len,
    char* out_url,
    int32_t out_len
) {
    if (!text || len <= 0 || !out_url || out_len <= 0) return 0;
    
    size_t text_len = static_cast<size_t>(len);
    size_t pos = 0;
    
    while (pos < text_len) {
        size_t scheme_end;
        if (starts_with_scheme(text, text_len, pos, &scheme_end)) {
            size_t url_end = find_url_end(text, text_len, scheme_end);
            if (url_end > scheme_end + 2) {
                size_t url_len = url_end - pos;
                
                // Check if starts with www. and needs https:// prefix
                bool needs_prefix = (text[pos] == 'w' || text[pos] == 'W');
                const char* prefix = "https://";
                size_t prefix_len = needs_prefix ? 8 : 0;
                
                size_t total_len = prefix_len + url_len;
                if (static_cast<int32_t>(total_len) >= out_len) {
                    total_len = out_len - 1;
                }
                
                size_t written = 0;
                if (needs_prefix) {
                    size_t to_copy = std::min(prefix_len, total_len);
                    memcpy(out_url, prefix, to_copy);
                    written = to_copy;
                }
                
                size_t remaining = total_len - written;
                memcpy(out_url + written, text + pos, remaining);
                written += remaining;
                
                out_url[written] = '\0';
                return static_cast<int32_t>(written);
            }
            pos = url_end;
        } else {
            pos++;
        }
    }
    
    out_url[0] = '\0';
    return 0;
}

/**
 * Normalize a URL (add https:// if starts with www.).
 * 
 * @param url Input URL
 * @param url_len Length of input URL
 * @param out_url Output buffer
 * @param out_len Size of output buffer
 * @return Length of normalized URL
 */
int32_t simjot_link_normalize(
    const char* url,
    int32_t url_len,
    char* out_url,
    int32_t out_len
) {
    if (!url || url_len <= 0 || !out_url || out_len <= 0) return 0;
    
    // Check if starts with www.
    bool needs_prefix = (url_len >= 4 && 
        (url[0] == 'w' || url[0] == 'W') &&
        (url[1] == 'w' || url[1] == 'W') &&
        (url[2] == 'w' || url[2] == 'W') &&
        url[3] == '.');
    
    const char* prefix = "https://";
    size_t prefix_len = needs_prefix ? 8 : 0;
    size_t total_len = prefix_len + url_len;
    
    if (static_cast<int32_t>(total_len) >= out_len) {
        total_len = out_len - 1;
    }
    
    size_t written = 0;
    if (needs_prefix) {
        size_t to_copy = std::min(prefix_len, total_len);
        memcpy(out_url, prefix, to_copy);
        written = to_copy;
    }
    
    size_t remaining = std::min(static_cast<size_t>(url_len), total_len - written);
    memcpy(out_url + written, url, remaining);
    written += remaining;
    
    out_url[written] = '\0';
    return static_cast<int32_t>(written);
}

/**
 * Validate if a string is a valid URL.
 * 
 * @param url URL to validate
 * @param len Length of URL
 * @return 1 if valid, 0 if invalid
 */
int32_t simjot_link_is_valid(const char* url, int32_t len) {
    if (!url || len <= 0) return 0;
    
    size_t scheme_end;
    if (!starts_with_scheme(url, static_cast<size_t>(len), 0, &scheme_end)) {
        return 0;
    }
    
    // Must have content after scheme
    if (scheme_end >= static_cast<size_t>(len)) return 0;
    
    // Check for at least one dot in domain
    bool has_dot = false;
    for (size_t i = scheme_end; i < static_cast<size_t>(len); i++) {
        if (url[i] == '.') {
            has_dot = true;
            break;
        }
        if (url[i] == '/') break; // Reached path before dot
    }
    
    return has_dot ? 1 : 0;
}

/**
 * Get link at specific position in text.
 * 
 * @param text UTF-8 encoded text
 * @param len Length of text
 * @param position Character position to check
 * @param out_start Output: start position of link (or -1)
 * @param out_end Output: end position of link (or -1)
 * @return 1 if position is within a link, 0 otherwise
 */
int32_t simjot_link_at_position(
    const char* text,
    int32_t len,
    int32_t position,
    int32_t* out_start,
    int32_t* out_end
) {
    if (!text || len <= 0 || position < 0 || position >= len) {
        if (out_start) *out_start = -1;
        if (out_end) *out_end = -1;
        return 0;
    }
    
    auto links = find_all_links(text, static_cast<size_t>(len));
    
    for (const auto& link : links) {
        if (position >= link.start && position < link.end) {
            if (out_start) *out_start = link.start;
            if (out_end) *out_end = link.end;
            return 1;
        }
    }
    
    if (out_start) *out_start = -1;
    if (out_end) *out_end = -1;
    return 0;
}

} // extern "C"
