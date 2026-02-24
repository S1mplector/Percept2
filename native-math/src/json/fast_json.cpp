/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Fast JSON Parser
 * Zero-copy JSON parsing with SIMD acceleration
 */

#include "simjot_native.h"
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <vector>
#include <string>
#include <string_view>

#ifdef __SSE2__
#include <emmintrin.h>
#endif

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * JSON VALUE TYPES
 * ═══════════════════════════════════════════════════════════════════════════ */

enum JsonType {
    JSON_NULL = 0,
    JSON_BOOL = 1,
    JSON_NUMBER = 2,
    JSON_STRING = 3,
    JSON_ARRAY = 4,
    JSON_OBJECT = 5
};

struct JsonValue {
    JsonType type;
    union {
        bool bool_val;
        double num_val;
        struct { const char* str; int32_t len; } str_val;
        struct { int32_t start; int32_t count; } arr_val;
        struct { int32_t start; int32_t count; } obj_val;
    } data;
};

struct JsonParser {
    const char* json;
    int32_t len;
    int32_t pos;
    std::vector<JsonValue> values;
    std::vector<std::pair<std::string_view, int32_t>> keys;  // For objects
    char error[256];
};

/* ═══════════════════════════════════════════════════════════════════════════
 * PARSER HELPERS
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline void skip_whitespace(JsonParser* p) {
#ifdef __SSE2__
    // SIMD whitespace skip
    __m128i ws_space = _mm_set1_epi8(' ');
    __m128i ws_tab = _mm_set1_epi8('\t');
    __m128i ws_nl = _mm_set1_epi8('\n');
    __m128i ws_cr = _mm_set1_epi8('\r');
    
    while (p->pos + 16 <= p->len) {
        __m128i chunk = _mm_loadu_si128((const __m128i*)(p->json + p->pos));
        __m128i is_ws = _mm_or_si128(
            _mm_or_si128(_mm_cmpeq_epi8(chunk, ws_space), _mm_cmpeq_epi8(chunk, ws_tab)),
            _mm_or_si128(_mm_cmpeq_epi8(chunk, ws_nl), _mm_cmpeq_epi8(chunk, ws_cr))
        );
        int mask = _mm_movemask_epi8(is_ws);
        
        if (mask != 0xFFFF) {
            p->pos += __builtin_ctz(~mask);
            return;
        }
        p->pos += 16;
    }
#endif
    
    while (p->pos < p->len) {
        char c = p->json[p->pos];
        if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
        p->pos++;
    }
}

static inline bool match(JsonParser* p, char c) {
    skip_whitespace(p);
    if (p->pos < p->len && p->json[p->pos] == c) {
        p->pos++;
        return true;
    }
    return false;
}

static inline bool expect(JsonParser* p, char c) {
    if (!match(p, c)) {
        snprintf(p->error, sizeof(p->error), "Expected '%c' at position %d", c, p->pos);
        return false;
    }
    return true;
}

static int32_t parse_value(JsonParser* p);

static int32_t parse_string(JsonParser* p) {
    if (!expect(p, '"')) return -1;
    
    const char* start = p->json + p->pos;
    
    // Fast scan for end quote
#ifdef __SSE2__
    __m128i quote_char = _mm_set1_epi8('"');
    __m128i escape_char = _mm_set1_epi8('\\');
    
    while (p->pos + 16 <= p->len) {
        __m128i chunk = _mm_loadu_si128((const __m128i*)(p->json + p->pos));
        __m128i is_quote = _mm_cmpeq_epi8(chunk, quote_char);
        __m128i is_escape = _mm_cmpeq_epi8(chunk, escape_char);
        int quote_mask = _mm_movemask_epi8(is_quote);
        int escape_mask = _mm_movemask_epi8(is_escape);
        
        if (quote_mask || escape_mask) {
            // Found special char, fall back to byte scan
            break;
        }
        p->pos += 16;
    }
#endif
    
    while (p->pos < p->len) {
        char c = p->json[p->pos];
        if (c == '"') {
            int32_t idx = (int32_t)p->values.size();
            JsonValue val;
            val.type = JSON_STRING;
            val.data.str_val.str = start;
            val.data.str_val.len = (int32_t)(p->json + p->pos - start);
            p->values.push_back(val);
            p->pos++;
            return idx;
        }
        if (c == '\\') {
            p->pos += 2;  // Skip escape sequence
        } else {
            p->pos++;
        }
    }
    
    snprintf(p->error, sizeof(p->error), "Unterminated string");
    return -1;
}

static int32_t parse_number(JsonParser* p) {
    const char* start = p->json + p->pos;
    
    // Sign
    if (p->pos < p->len && (p->json[p->pos] == '-' || p->json[p->pos] == '+')) {
        p->pos++;
    }
    
    // Integer part
    while (p->pos < p->len && p->json[p->pos] >= '0' && p->json[p->pos] <= '9') {
        p->pos++;
    }
    
    // Decimal part
    if (p->pos < p->len && p->json[p->pos] == '.') {
        p->pos++;
        while (p->pos < p->len && p->json[p->pos] >= '0' && p->json[p->pos] <= '9') {
            p->pos++;
        }
    }
    
    // Exponent
    if (p->pos < p->len && (p->json[p->pos] == 'e' || p->json[p->pos] == 'E')) {
        p->pos++;
        if (p->pos < p->len && (p->json[p->pos] == '-' || p->json[p->pos] == '+')) {
            p->pos++;
        }
        while (p->pos < p->len && p->json[p->pos] >= '0' && p->json[p->pos] <= '9') {
            p->pos++;
        }
    }
    
    int32_t idx = (int32_t)p->values.size();
    JsonValue val;
    val.type = JSON_NUMBER;
    val.data.num_val = strtod(start, nullptr);
    p->values.push_back(val);
    
    return idx;
}

static int32_t parse_array(JsonParser* p) {
    if (!expect(p, '[')) return -1;
    
    int32_t idx = (int32_t)p->values.size();
    JsonValue val;
    val.type = JSON_ARRAY;
    val.data.arr_val.start = (int32_t)p->values.size() + 1;
    val.data.arr_val.count = 0;
    p->values.push_back(val);
    
    skip_whitespace(p);
    if (match(p, ']')) {
        return idx;
    }
    
    do {
        if (parse_value(p) < 0) return -1;
        p->values[idx].data.arr_val.count++;
    } while (match(p, ','));
    
    if (!expect(p, ']')) return -1;
    return idx;
}

static int32_t parse_object(JsonParser* p) {
    if (!expect(p, '{')) return -1;
    
    int32_t idx = (int32_t)p->values.size();
    JsonValue val;
    val.type = JSON_OBJECT;
    val.data.obj_val.start = (int32_t)p->keys.size();
    val.data.obj_val.count = 0;
    p->values.push_back(val);
    
    skip_whitespace(p);
    if (match(p, '}')) {
        return idx;
    }
    
    do {
        skip_whitespace(p);
        
        // Parse key
        if (!expect(p, '"')) return -1;
        const char* key_start = p->json + p->pos;
        while (p->pos < p->len && p->json[p->pos] != '"') {
            if (p->json[p->pos] == '\\') p->pos++;
            p->pos++;
        }
        std::string_view key(key_start, p->json + p->pos - key_start);
        if (!expect(p, '"')) return -1;
        
        if (!expect(p, ':')) return -1;
        
        int32_t val_idx = parse_value(p);
        if (val_idx < 0) return -1;
        
        p->keys.emplace_back(key, val_idx);
        p->values[idx].data.obj_val.count++;
        
    } while (match(p, ','));
    
    if (!expect(p, '}')) return -1;
    return idx;
}

static int32_t parse_value(JsonParser* p) {
    skip_whitespace(p);
    
    if (p->pos >= p->len) {
        snprintf(p->error, sizeof(p->error), "Unexpected end of input");
        return -1;
    }
    
    char c = p->json[p->pos];
    
    if (c == '"') {
        return parse_string(p);
    }
    if (c == '[') {
        return parse_array(p);
    }
    if (c == '{') {
        return parse_object(p);
    }
    if (c == '-' || (c >= '0' && c <= '9')) {
        return parse_number(p);
    }
    if (c == 't' && p->pos + 4 <= p->len && memcmp(p->json + p->pos, "true", 4) == 0) {
        p->pos += 4;
        int32_t idx = (int32_t)p->values.size();
        JsonValue val;
        val.type = JSON_BOOL;
        val.data.bool_val = true;
        p->values.push_back(val);
        return idx;
    }
    if (c == 'f' && p->pos + 5 <= p->len && memcmp(p->json + p->pos, "false", 5) == 0) {
        p->pos += 5;
        int32_t idx = (int32_t)p->values.size();
        JsonValue val;
        val.type = JSON_BOOL;
        val.data.bool_val = false;
        p->values.push_back(val);
        return idx;
    }
    if (c == 'n' && p->pos + 4 <= p->len && memcmp(p->json + p->pos, "null", 4) == 0) {
        p->pos += 4;
        int32_t idx = (int32_t)p->values.size();
        JsonValue val;
        val.type = JSON_NULL;
        p->values.push_back(val);
        return idx;
    }
    
    snprintf(p->error, sizeof(p->error), "Unexpected character '%c' at position %d", c, p->pos);
    return -1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Parse JSON string.
 * @return Parser handle or NULL on error
 */
void* simjot_json_parse(const char* json, int32_t len) {
    if (!json || len <= 0) return nullptr;
    
    JsonParser* p = new(std::nothrow) JsonParser();
    if (!p) return nullptr;
    
    p->json = json;
    p->len = len;
    p->pos = 0;
    p->error[0] = '\0';
    p->values.reserve(64);
    p->keys.reserve(32);
    
    if (parse_value(p) < 0) {
        delete p;
        return nullptr;
    }
    
    return p;
}

/**
 * Get root value type.
 */
int32_t simjot_json_type(void* parser) {
    if (!parser) return JSON_NULL;
    JsonParser* p = (JsonParser*)parser;
    if (p->values.empty()) return JSON_NULL;
    return p->values[0].type;
}

/**
 * Get string value at index (fast parser API).
 * NOTE: Renamed to avoid ABI conflict with json/json_parser.c (key-based API).
 */
const char* simjot_json_get_string_at(void* parser, int32_t index, int32_t* out_len) {
    if (!parser) return nullptr;
    JsonParser* p = (JsonParser*)parser;
    
    if (index < 0 || index >= (int32_t)p->values.size()) return nullptr;
    if (p->values[index].type != JSON_STRING) return nullptr;
    
    if (out_len) *out_len = p->values[index].data.str_val.len;
    return p->values[index].data.str_val.str;
}

/**
 * Get number value at index.
 */
double simjot_json_get_number(void* parser, int32_t index) {
    if (!parser) return 0.0;
    JsonParser* p = (JsonParser*)parser;
    
    if (index < 0 || index >= (int32_t)p->values.size()) return 0.0;
    if (p->values[index].type != JSON_NUMBER) return 0.0;
    
    return p->values[index].data.num_val;
}

/**
 * Get bool value at index.
 */
int32_t simjot_json_get_bool(void* parser, int32_t index) {
    if (!parser) return 0;
    JsonParser* p = (JsonParser*)parser;
    
    if (index < 0 || index >= (int32_t)p->values.size()) return 0;
    if (p->values[index].type != JSON_BOOL) return 0;
    
    return p->values[index].data.bool_val ? 1 : 0;
}

/**
 * Get array length.
 */
int32_t simjot_json_array_length(void* parser, int32_t index) {
    if (!parser) return 0;
    JsonParser* p = (JsonParser*)parser;
    
    if (index < 0 || index >= (int32_t)p->values.size()) return 0;
    if (p->values[index].type != JSON_ARRAY) return 0;
    
    return p->values[index].data.arr_val.count;
}

/**
 * Get array element index.
 */
int32_t simjot_json_array_get(void* parser, int32_t array_idx, int32_t elem_idx) {
    if (!parser) return -1;
    JsonParser* p = (JsonParser*)parser;
    
    if (array_idx < 0 || array_idx >= (int32_t)p->values.size()) return -1;
    if (p->values[array_idx].type != JSON_ARRAY) return -1;
    
    int32_t count = p->values[array_idx].data.arr_val.count;
    if (elem_idx < 0 || elem_idx >= count) return -1;
    
    return p->values[array_idx].data.arr_val.start + elem_idx;
}

/**
 * Get object field count.
 */
int32_t simjot_json_object_size(void* parser, int32_t index) {
    if (!parser) return 0;
    JsonParser* p = (JsonParser*)parser;
    
    if (index < 0 || index >= (int32_t)p->values.size()) return 0;
    if (p->values[index].type != JSON_OBJECT) return 0;
    
    return p->values[index].data.obj_val.count;
}

/**
 * Get object field by key.
 */
int32_t simjot_json_object_get(void* parser, int32_t obj_idx, const char* key) {
    if (!parser || !key) return -1;
    JsonParser* p = (JsonParser*)parser;
    
    if (obj_idx < 0 || obj_idx >= (int32_t)p->values.size()) return -1;
    if (p->values[obj_idx].type != JSON_OBJECT) return -1;
    
    int32_t start = p->values[obj_idx].data.obj_val.start;
    int32_t count = p->values[obj_idx].data.obj_val.count;
    
    std::string_view key_view(key);
    for (int32_t i = start; i < start + count && i < (int32_t)p->keys.size(); i++) {
        if (p->keys[i].first == key_view) {
            return p->keys[i].second;
        }
    }
    
    return -1;
}

/**
 * Get parse error message.
 */
const char* simjot_json_error(void* parser) {
    if (!parser) return "Null parser";
    JsonParser* p = (JsonParser*)parser;
    return p->error;
}

/**
 * Free parser.
 */
void simjot_json_free(void* parser) {
    delete (JsonParser*)parser;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * JSON BUILDER
 * ═══════════════════════════════════════════════════════════════════════════ */

struct JsonBuilder {
    std::string buffer;
    int depth;
    bool first_elem;
};

/**
 * Create JSON builder.
 */
void* simjot_json_builder_create(void) {
    JsonBuilder* b = new(std::nothrow) JsonBuilder();
    if (b) {
        b->buffer.reserve(4096);
        b->depth = 0;
        b->first_elem = true;
    }
    return b;
}

static void json_builder_separator(JsonBuilder* b) {
    if (!b->first_elem) {
        b->buffer += ',';
    }
    b->first_elem = false;
}

void simjot_json_builder_object_start(void* builder) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += '{';
    b->depth++;
    b->first_elem = true;
}

void simjot_json_builder_object_end(void* builder) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    b->buffer += '}';
    b->depth--;
    b->first_elem = false;
}

void simjot_json_builder_array_start(void* builder) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += '[';
    b->depth++;
    b->first_elem = true;
}

void simjot_json_builder_array_end(void* builder) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    b->buffer += ']';
    b->depth--;
    b->first_elem = false;
}

void simjot_json_builder_key(void* builder, const char* key) {
    if (!builder || !key) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += '"';
    b->buffer += key;
    b->buffer += "\":";
    b->first_elem = true;
}

void simjot_json_builder_string(void* builder, const char* value) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += '"';
    if (value) {
        for (const char* p = value; *p; p++) {
            switch (*p) {
                case '"': b->buffer += "\\\""; break;
                case '\\': b->buffer += "\\\\"; break;
                case '\n': b->buffer += "\\n"; break;
                case '\r': b->buffer += "\\r"; break;
                case '\t': b->buffer += "\\t"; break;
                default: b->buffer += *p;
            }
        }
    }
    b->buffer += '"';
}

void simjot_json_builder_number(void* builder, double value) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    char buf[32];
    snprintf(buf, sizeof(buf), "%.15g", value);
    b->buffer += buf;
}

void simjot_json_builder_bool(void* builder, int32_t value) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += value ? "true" : "false";
}

void simjot_json_builder_null(void* builder) {
    if (!builder) return;
    JsonBuilder* b = (JsonBuilder*)builder;
    json_builder_separator(b);
    b->buffer += "null";
}

const char* simjot_json_builder_get(void* builder, int32_t* out_len) {
    if (!builder) return nullptr;
    JsonBuilder* b = (JsonBuilder*)builder;
    if (out_len) *out_len = (int32_t)b->buffer.size();
    return b->buffer.c_str();
}

void simjot_json_builder_free(void* builder) {
    delete (JsonBuilder*)builder;
}

} // extern "C"
