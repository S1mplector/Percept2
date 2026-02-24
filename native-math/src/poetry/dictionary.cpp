/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

/**
 * A simple dictionary implementation that loads JSON files from a specified
 * base path. Each file corresponds to words starting with a specific letter.
 * The dictionary supports word lookups and rhyme queries.
 * The JSON parsing is done manually to avoid external dependencies.
 * The data structures are kept in memory for fast access after loading.
 * This implementation is not optimized for memory usage or speed,
 * but is sufficient for basic dictionary functionalities.
 * The dictionary files are expected to be in a specific format.
 * Error handling is minimal for simplicity.
 * Thread safety is ensured during the loading phase.
 * The dictionary can be reloaded by changing the base path before any lookups.
 * The implementation assumes ASCII input for simplicity.
 * All string comparisons are case-insensitive.
 * Rhyme keys are generated using the provided simjot_rhyme_key function.
 * The output format for lookups and rhyme queries is defined in the header file.
 */

namespace {

struct DictEntry {
    std::vector<std::string> parts_of_speech;
    std::vector<std::string> synonyms;
    std::vector<std::string> antonyms;
};

static std::unordered_map<std::string, DictEntry> g_dictionary;
static std::unordered_map<std::string, std::vector<std::string>> g_rhyme_index;
static std::string g_base_path;
static std::mutex g_load_mutex;
static bool g_loaded = false;

static std::string normalize_word(const char* word) {
    if (!word) return {};
    std::string out(word);
    std::transform(out.begin(), out.end(), out.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return out;
}

static size_t find_string_end(const std::string& s, size_t start) {
    bool escaped = false;
    for (size_t i = start; i < s.size(); i++) {
        char c = s[i];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (c == '\\') {
            escaped = true;
            continue;
        }
        if (c == '"') return i;
    }
    return std::string::npos;
}

static size_t find_matching_brace(const std::string& s, size_t start) {
    if (start >= s.size() || s[start] != '{') return std::string::npos;
    int depth = 1;
    for (size_t i = start + 1; i < s.size(); i++) {
        char c = s[i];
        if (c == '"') {
            size_t end = find_string_end(s, i + 1);
            if (end == std::string::npos) return std::string::npos;
            i = end;
            continue;
        }
        if (c == '{') depth++;
        if (c == '}') {
            depth--;
            if (depth == 0) return i;
        }
    }
    return std::string::npos;
}

static size_t find_matching_bracket(const std::string& s, size_t start) {
    if (start >= s.size() || s[start] != '[') return std::string::npos;
    int depth = 1;
    for (size_t i = start + 1; i < s.size(); i++) {
        char c = s[i];
        if (c == '"') {
            size_t end = find_string_end(s, i + 1);
            if (end == std::string::npos) return std::string::npos;
            i = end;
            continue;
        }
        if (c == '[') depth++;
        if (c == ']') {
            depth--;
            if (depth == 0) return i;
        }
    }
    return std::string::npos;
}

static bool is_valid_pos(const std::string& pos) {
    return pos == "Noun" || pos == "Verb" || pos == "Adjective" || pos == "Adverb" ||
           pos == "Preposition" || pos == "Conjunction" || pos == "Pronoun" ||
           pos == "Determiner" || pos == "Article" || pos == "Interjection" ||
           pos == "Numeral";
}

static std::vector<std::string> extract_pos(const std::string& obj) {
    std::vector<std::string> result;
    size_t meanings_pos = obj.find("\"MEANINGS\"");
    if (meanings_pos == std::string::npos) return result;
    size_t brace_start = obj.find('{', meanings_pos);
    if (brace_start == std::string::npos) return result;
    size_t brace_end = find_matching_brace(obj, brace_start);
    if (brace_end == std::string::npos || brace_end <= brace_start) return result;
    std::string meanings = obj.substr(brace_start, brace_end - brace_start + 1);
    size_t idx = 0;
    while (idx < meanings.size()) {
        size_t q1 = meanings.find("\"", idx);
        if (q1 == std::string::npos) break;
        size_t q2 = find_string_end(meanings, q1 + 1);
        if (q2 == std::string::npos) break;
        std::string token = meanings.substr(q1 + 1, q2 - q1 - 1);
        if (is_valid_pos(token)) {
            if (std::find(result.begin(), result.end(), token) == result.end()) {
                result.push_back(token);
            }
        }
        idx = q2 + 1;
    }
    return result;
}

static std::vector<std::string> extract_string_array(const std::string& obj, const char* key) {
    std::vector<std::string> result;
    if (!key) return result;
    size_t pos = obj.find(key);
    if (pos == std::string::npos) return result;
    size_t bracket_start = obj.find('[', pos);
    if (bracket_start == std::string::npos) return result;
    size_t bracket_end = find_matching_bracket(obj, bracket_start);
    if (bracket_end == std::string::npos || bracket_end <= bracket_start) return result;
    size_t idx = bracket_start + 1;
    while (idx < bracket_end) {
        size_t q1 = obj.find('"', idx);
        if (q1 == std::string::npos || q1 >= bracket_end) break;
        size_t q2 = find_string_end(obj, q1 + 1);
        if (q2 == std::string::npos || q2 > bracket_end) break;
        std::string value = obj.substr(q1 + 1, q2 - q1 - 1);
        if (!value.empty()) result.push_back(value);
        idx = q2 + 1;
    }
    return result;
}

static bool read_file(const std::string& path, std::string& out) {
    std::ifstream in(path, std::ios::in | std::ios::binary);
    if (!in) return false;
    in.seekg(0, std::ios::end);
    std::streampos end = in.tellg();
    if (end <= 0) return false;
    std::streamsize size = static_cast<std::streamsize>(end);
    out.resize(static_cast<size_t>(size));
    in.seekg(0, std::ios::beg);
    in.read(&out[0], size);
    return true;
}

static void index_word(const std::string& word) {
    std::vector<char> buf(word.size() + 8);
    int32_t len = simjot_rhyme_key(word.c_str(), buf.data(), (int32_t)buf.size());
    if (len <= 0) return;
    std::string key(buf.data(), (size_t)len);
    g_rhyme_index[key].push_back(word);
}

static void parse_json_chunk(const std::string& json) {
    if (json.empty()) return;
    size_t idx = 0;
    const size_t len = json.size();
    while (idx < len) {
        size_t key_start = json.find('"', idx);
        if (key_start == std::string::npos || key_start + 1 >= len) break;
        size_t key_end = find_string_end(json, key_start + 1);
        if (key_end == std::string::npos) break;
        std::string word = json.substr(key_start + 1, key_end - key_start - 1);
        idx = key_end + 1;

        size_t obj_start = json.find('{', idx);
        if (obj_start == std::string::npos) break;
        size_t obj_end = find_matching_brace(json, obj_start);
        if (obj_end == std::string::npos || obj_end <= obj_start) {
            idx = obj_start + 1;
            continue;
        }

        std::string obj = json.substr(obj_start + 1, obj_end - obj_start - 1);
        idx = obj_end + 1;

        if (word.empty()) continue;
        std::string lower = normalize_word(word.c_str());
        DictEntry entry;
        entry.parts_of_speech = extract_pos(obj);
        entry.synonyms = extract_string_array(obj, "\"SYNONYMS\"");
        entry.antonyms = extract_string_array(obj, "\"ANTONYMS\"");
        g_dictionary[lower] = std::move(entry);
        index_word(lower);
    }
}

static bool ensure_loaded() {
    if (g_loaded) return true;
    std::lock_guard<std::mutex> lock(g_load_mutex);
    if (g_loaded) return true;

    std::string base = g_base_path;
    if (base.empty()) {
        const char* env = std::getenv("SIMJOT_DICT_PATH");
        if (env && *env) base = env;
    }
    if (base.empty()) return false;

    for (char c = 'a'; c <= 'z'; c++) {
        std::string path = base;
        if (!path.empty() && path.back() != '/' && path.back() != '\\') {
            path.push_back('/');
        }
        path.push_back(c);
        path.append(".json");
        std::string json;
        if (!read_file(path, json)) continue;
        parse_json_chunk(json);
    }

    g_loaded = true;
    return true;
}

static size_t list_bytes(const std::vector<std::string>& items, size_t limit) {
    size_t total = 0;
    size_t count = (limit == 0 || limit > items.size()) ? items.size() : limit;
    for (size_t i = 0; i < count; i++) {
        total += sizeof(uint32_t);
        total += items[i].size();
    }
    return total;
}

static void write_u32(uint8_t* out, size_t& offset, uint32_t value) {
    std::memcpy(out + offset, &value, sizeof(uint32_t));
    offset += sizeof(uint32_t);
}

static void write_string(uint8_t* out, size_t& offset, const std::string& value) {
    uint32_t len = static_cast<uint32_t>(value.size());
    write_u32(out, offset, len);
    if (len > 0) {
        std::memcpy(out + offset, value.data(), len);
        offset += len;
    }
}

} // namespace

extern "C" int32_t simjot_dict_set_base_path(const char* path) {
    if (path == nullptr || *path == '\0') return 0;
    if (g_loaded) return 1;
    g_base_path = path;
    return 1;
}

extern "C" int32_t simjot_dict_contains(const char* word) {
    if (!word || !ensure_loaded()) return 0;
    std::string key = normalize_word(word);
    if (key.empty()) return 0;
    return g_dictionary.find(key) != g_dictionary.end() ? 1 : 0;
}

extern "C" int32_t simjot_dict_lookup(const char* word, uint8_t* out, int32_t out_len) {
    if (!word || !ensure_loaded()) return 0;
    std::string key = normalize_word(word);
    if (key.empty()) return 0;
    auto it = g_dictionary.find(key);
    if (it == g_dictionary.end()) return 0;

    const DictEntry& entry = it->second;
    size_t required = sizeof(uint32_t) * 3;
    required += list_bytes(entry.parts_of_speech, 0);
    required += list_bytes(entry.synonyms, 0);
    required += list_bytes(entry.antonyms, 0);
    if (required > static_cast<size_t>(std::numeric_limits<int32_t>::max())) return 0;
    if (out == nullptr || out_len < static_cast<int32_t>(required)) {
        return -static_cast<int32_t>(required);
    }

    size_t offset = 0;
    write_u32(out, offset, static_cast<uint32_t>(entry.parts_of_speech.size()));
    write_u32(out, offset, static_cast<uint32_t>(entry.synonyms.size()));
    write_u32(out, offset, static_cast<uint32_t>(entry.antonyms.size()));
    for (const auto& pos : entry.parts_of_speech) write_string(out, offset, pos);
    for (const auto& syn : entry.synonyms) write_string(out, offset, syn);
    for (const auto& ant : entry.antonyms) write_string(out, offset, ant);

    return static_cast<int32_t>(required);
}

extern "C" int32_t simjot_dict_rhymes_for(const char* word, int32_t max_results, uint8_t* out, int32_t out_len) {
    if (!word || !ensure_loaded()) return 0;
    std::string lower = normalize_word(word);
    if (lower.empty()) return 0;

    std::vector<char> buf(lower.size() + 8);
    int32_t key_len = simjot_rhyme_key(lower.c_str(), buf.data(), (int32_t)buf.size());
    if (key_len <= 0) return 0;
    std::string key(buf.data(), (size_t)key_len);

    auto it = g_rhyme_index.find(key);
    if (it == g_rhyme_index.end()) return 0;

    std::vector<std::string> results;
    size_t limit = max_results > 0 ? (size_t)max_results : it->second.size();
    for (const auto& w : it->second) {
        if (w == lower) continue;
        results.push_back(w);
        if (results.size() >= limit) break;
    }
    if (results.empty()) return 0;

    size_t required = sizeof(uint32_t) + list_bytes(results, results.size());
    if (required > static_cast<size_t>(std::numeric_limits<int32_t>::max())) return 0;
    if (out == nullptr || out_len < static_cast<int32_t>(required)) {
        return -static_cast<int32_t>(required);
    }

    size_t offset = 0;
    write_u32(out, offset, static_cast<uint32_t>(results.size()));
    for (const auto& w : results) write_string(out, offset, w);
    return static_cast<int32_t>(required);
}

extern "C" int32_t simjot_dict_size(void) {
    if (!ensure_loaded()) return 0;
    if (g_dictionary.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max())) return 0;
    return static_cast<int32_t>(g_dictionary.size());
}
