/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#ifndef SIMJOT_NATIVE_H
#define SIMJOT_NATIVE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t simjot_add(int32_t a, int32_t b);
int32_t simjot_strlen(const char* str);
int64_t simjot_sum_array(const int32_t* arr, int32_t len);
int64_t simjot_fib(int32_t n);

int32_t simjot_sha256_file(const char* path, uint8_t* out32);
int32_t simjot_perf_snapshot(uint8_t* out, int32_t out_len);
int32_t simjot_binary_health(const char* path, uint8_t* out, int32_t out_len);

int32_t simjot_count_syllables(const char* word);
int32_t simjot_rhyme_key(const char* word, char* out, int32_t out_len);
int32_t simjot_near_rhyme_key(const char* word, char* out, int32_t out_len);

int32_t simjot_dict_set_base_path(const char* path);
int32_t simjot_dict_contains(const char* word);
int32_t simjot_dict_lookup(const char* word, uint8_t* out, int32_t out_len);
int32_t simjot_dict_rhymes_for(const char* word, int32_t max_results, uint8_t* out, int32_t out_len);
int32_t simjot_dict_size(void);

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir);
int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed);
int32_t simjot_copy_file(const char* src_path, const char* dst_path, int32_t copy_attrs);
int32_t simjot_list_dir_size(const char* path, int32_t include_hidden);
int32_t simjot_list_dir(const char* path, int32_t include_hidden, uint8_t* out, int32_t out_len);

int32_t simjot_spell_init(const char* base_path);
int32_t simjot_spell_contains(const char* word);
int32_t simjot_spell_suggestions(const char* word, int32_t max_results, uint8_t* out, int32_t out_len);
int32_t simjot_spell_best_correction(const char* word, char* out, int32_t out_len);
int32_t simjot_spell_add_user_word(const char* word);
int32_t simjot_spell_clear_user_words(void);

/* Text processing utilities */
int32_t simjot_text_word_count(const char* text);
int32_t simjot_text_sentence_count(const char* text);
int32_t simjot_text_char_count(const char* text, int32_t include_spaces);
int32_t simjot_text_extract_words(const char* text, char* out, int32_t out_len);
int32_t simjot_text_extract_tags(const char* text, char* out, int32_t out_len);
int32_t simjot_text_last_word(const char* text, char* out, int32_t out_len);
int32_t simjot_text_normalize(const char* text, char* out, int32_t out_len);
int32_t simjot_text_fuzzy_match(const char* text, const char* query);
int32_t simjot_text_fuzzy_score(const char* text, const char* query);
int32_t simjot_text_line_count(const char* text);
int32_t simjot_text_get_line(const char* text, int32_t line_num, char* out, int32_t out_len);

/* String distance functions */
int32_t simjot_text_levenshtein(const char* a, const char* b);
int32_t simjot_text_damerau_levenshtein(const char* a, const char* b);
int32_t simjot_text_similarity(const char* a, const char* b);

/* List formatting utilities */
int32_t simjot_list_is_bulleted(const char* line);
int32_t simjot_list_get_number(const char* line);
int32_t simjot_list_add_bullets(const char* input, char* output, int32_t output_len, const char* bullet_char);
int32_t simjot_list_add_numbers(const char* input, char* output, int32_t output_len, int32_t start_num);
int32_t simjot_list_strip_prefix(const char* input, char* output, int32_t output_len);

/* Compression utilities */
int32_t simjot_compress(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len, int32_t level);
int32_t simjot_decompress(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);
int32_t simjot_compress_bound(int32_t input_len);
int32_t simjot_compress_default(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);
int32_t simjot_compress_fast(const uint8_t* input, int32_t input_len, uint8_t* output, int32_t output_len);

/* String operations */
int32_t simjot_string_sanitize(const char* input, char* output, int32_t output_len, int32_t max_len);
int32_t simjot_string_collapse_whitespace(char* str);
uint64_t simjot_string_hash(const char* str);
uint64_t simjot_string_hash_multi(const char** strings, int32_t count);
int32_t simjot_string_token_count(const char* text);
int32_t simjot_string_first_tokens(const char* text, char* output, int32_t output_len, int32_t max_tokens);
int32_t simjot_string_last_tokens(const char* text, char* output, int32_t output_len, int32_t max_tokens);
int32_t simjot_string_contains_ci(const char* haystack, const char* needle);
int32_t simjot_string_starts_with_ci(const char* str, const char* prefix);
int32_t simjot_string_join(const char** strings, int32_t count, const char* separator, char* output, int32_t output_len);
int32_t simjot_buffer_append_circular(char* buffer, int32_t buffer_len, int32_t max_size, const char* append, const char* separator);

/* JSON parsing */
int32_t simjot_json_get_string(const char* json, const char* key, char* output, int32_t output_len);
int32_t simjot_json_get_int(const char* json, const char* key, int64_t* out_value);
int32_t simjot_json_has_key(const char* json, const char* key);
int32_t simjot_json_count_keys(const char* json);
int32_t simjot_json_get_keys(const char* json, char* output, int32_t output_len);
int32_t simjot_json_parse_string_array(const char* json, char* output, int32_t output_len);
int32_t simjot_json_get_path(const char* json, const char* path, char* output, int32_t output_len);

/* Dictionary JSON parsing - optimized for simple-english-dictionary format */
int32_t simjot_json_parse_dict_words(const char* json, int32_t json_len, char* output, int32_t output_len);
int32_t simjot_json_parse_dict_entry(const char* json, const char* word, uint8_t* out, int32_t out_len);
int32_t simjot_json_load_dict_file(const char* file_path, char* output, int32_t output_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD ANALYTICS - Fast mood log parsing and statistics
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Load and parse mood log file */
int32_t simjot_mood_load(const char* file_path);

/* Compute daily statistics (call after load) */
int32_t simjot_mood_compute_daily(int32_t days_back);

/* Compute analytics summary (call after compute_daily) */
int32_t simjot_mood_compute_summary(int32_t threshold);

/* Get daily stats by index (binary output) */
int32_t simjot_mood_get_daily(int32_t index, uint8_t* out, int32_t out_len);

/* Get analytics summary (binary output) */
int32_t simjot_mood_get_summary(uint8_t* out, int32_t out_len);

/* Get counts */
int32_t simjot_mood_daily_count(void);
int32_t simjot_mood_sample_count(void);

/* Clear all loaded data */
void simjot_mood_clear(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD GRAPHICS - Native chart rendering to pixel buffers
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Render sparkline chart (mood trend line) */
int32_t simjot_mood_sparkline(const int32_t* values, int32_t count,
                               int32_t width, int32_t height,
                               uint32_t* out, uint32_t bg_color,
                               int32_t line_thickness);

/* Render bar chart (daily averages) */
int32_t simjot_mood_barchart(const int32_t* values, int32_t count,
                              int32_t width, int32_t height,
                              uint32_t* out, uint32_t bg_color,
                              int32_t bar_spacing);

/* Render gauge/radial chart */
int32_t simjot_mood_gauge(int32_t value, int32_t size,
                           uint32_t* out, uint32_t bg_color,
                           uint32_t track_color, int32_t thickness);

/* Render heatmap grid (contribution graph style) */
int32_t simjot_mood_heatmap(const int32_t* values, int32_t count,
                             int32_t cols, int32_t cell_size, int32_t cell_gap,
                             uint32_t* out, int32_t out_width, int32_t out_height,
                             uint32_t bg_color, uint32_t empty_color);

/* Date/time utilities */
int64_t simjot_time_now_millis(void);
int64_t simjot_time_now_secs(void);
int32_t simjot_time_format(int64_t millis, const char* pattern, char* output, int32_t output_len);
int32_t simjot_time_format_now(const char* pattern, char* output, int32_t output_len);
int32_t simjot_time_format_iso(int64_t millis, char* output, int32_t output_len);
int32_t simjot_time_format_backup(int64_t millis, char* output, int32_t output_len);
int64_t simjot_time_parse(const char* str, const char* pattern);
int64_t simjot_time_add_days(int64_t millis, int32_t days);
int32_t simjot_time_diff_days(int64_t millis1, int64_t millis2);
int64_t simjot_time_start_of_day(int64_t millis);
int32_t simjot_time_day_of_week(int64_t millis);
int32_t simjot_time_is_leap_year(int32_t year);
int32_t simjot_time_days_in_month(int32_t year, int32_t month);
int32_t simjot_time_relative(int64_t millis, char* output, int32_t output_len);

/* Pattern matching */
int32_t simjot_pattern_match_wildcard(const char* str, const char* pattern);
int32_t simjot_pattern_find(const char* text, const char* pattern, int32_t word_boundary);
int32_t simjot_pattern_count(const char* text, const char* pattern, int32_t word_boundary);
int32_t simjot_pattern_extract_after(const char* text, const char* prefix, char* output, int32_t output_len, int32_t max_phrase_len);
int32_t simjot_pattern_extract_all(const char* text, const char* prefix, char* output, int32_t output_len, int32_t max_phrase_len);
int32_t simjot_pattern_extract_words(const char* text, const char* pattern, char* output, int32_t output_len);
int32_t simjot_pattern_replace_all(const char* text, const char* pattern, const char* replacement, char* output, int32_t output_len);
int32_t simjot_pattern_collapse_spaces(const char* text, char* output, int32_t output_len);

/* Base64 encoding */
int32_t simjot_base64_encode_len(int32_t input_len);
int32_t simjot_base64_decode_len(int32_t input_len);
int32_t simjot_base64_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_base64_decode(const char* input, uint8_t* output, int32_t output_len);
int32_t simjot_base64url_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_base64url_decode(const char* input, uint8_t* output, int32_t output_len);

/* Unicode/UTF-8 utilities */
int32_t simjot_utf8_strlen(const char* str);
int32_t simjot_utf8_valid(const char* str);
int32_t simjot_utf8_encode_codepoint(uint32_t codepoint, char* output, int32_t output_len);
int32_t simjot_utf8_decode_codepoint(const char* str, int32_t* bytes_consumed);
int32_t simjot_unicode_unescape(const char* input, char* output, int32_t output_len);

/* Hex encoding */
int32_t simjot_hex_encode(const uint8_t* input, int32_t input_len, char* output, int32_t output_len);
int32_t simjot_hex_decode(const char* input, uint8_t* output, int32_t output_len);

/* Poetry analysis */
int32_t simjot_poetry_analyze_sounds(const char* text);
int32_t simjot_poetry_get_sound_device(int32_t index, char* type_buf, int32_t type_len, char* pattern_buf, int32_t pattern_len, int32_t* line_number);
int32_t simjot_poetry_analyze_themes(const char* text);
double simjot_poetry_get_theme_score(const char* theme);
int32_t simjot_poetry_get_themes(char* output, int32_t output_len);
int32_t simjot_poetry_analyze_vocab(const char* text);
void simjot_poetry_get_vocab_stats(int32_t* total, int32_t* unique, double* diversity, double* avg_len);
int32_t simjot_poetry_count_syllables(const char* word);
int32_t simjot_poetry_analyze_meter(const char* text);
int32_t simjot_poetry_get_line_syllables(int32_t line_index);
int32_t simjot_poetry_detect_meter(char* output, int32_t output_len);
typedef struct PoetryAnalysisResult {
    double ttr;
    int32_t sound_device_count;
    int32_t unique_words;
    int32_t total_words;
    char dominant_theme[64];
    char dominant_meter[64];
} PoetryAnalysisResult;
const PoetryAnalysisResult* simjot_poetry_analyze_all(const char* text);

/* Rhyme engine */
void simjot_rhyme_add_word(const char* word);
int32_t simjot_rhyme_add_words(const char* words);
int32_t simjot_rhyme_find(const char* word, int32_t max_results);
int32_t simjot_rhyme_get_result(int32_t index, char* output, int32_t output_len);
int32_t simjot_rhyme_get_all_results(char* output, int32_t output_len);
int32_t simjot_rhyme_get_key(const char* word, char* output, int32_t output_len);
int32_t simjot_rhyme_check(const char* word1, const char* word2);
int32_t simjot_rhyme_detect_scheme(const char* text, char* output, int32_t output_len);
int32_t simjot_rhyme_get_pair_count(void);
int32_t simjot_rhyme_get_pair(int32_t index, int32_t* line1, int32_t* line2);
void simjot_rhyme_clear(void);
int32_t simjot_rhyme_db_size(void);

/* Math utilities */
double simjot_math_vec2_length(double x, double y);
double simjot_math_vec2_dot(double x1, double y1, double x2, double y2);
double simjot_math_vec2_cross(double x1, double y1, double x2, double y2);
void simjot_math_vec2_normalize(double x, double y, double* out_x, double* out_y);
void simjot_math_vec2_rotate(double x, double y, double angle_rad, double* out_x, double* out_y);
double simjot_math_vec2_angle(double x, double y);
double simjot_math_vec2_distance(double x1, double y1, double x2, double y2);
void simjot_math_bezier_quad(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y, double t, double* out_x, double* out_y);
void simjot_math_bezier_cubic(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y, double p3x, double p3y, double t, double* out_x, double* out_y);
double simjot_math_ease(int32_t type, double t);
uint32_t simjot_math_color_blend(uint32_t color1, uint32_t color2, double t);
uint32_t simjot_math_hsl_to_rgb(double h, double s, double l);
void simjot_math_rgb_to_hsl(uint32_t argb, double* h, double* s, double* l);
int32_t simjot_math_compute_stats(const double* data, int32_t count);
double simjot_math_get_stat(int32_t which);
double simjot_math_deg_to_rad(double degrees);
double simjot_math_rad_to_deg(double radians);
double simjot_math_normalize_angle(double radians);
double simjot_math_lerp(double a, double b, double t);
double simjot_math_clamp(double value, double min_val, double max_val);
double simjot_math_map_range(double value, double in_min, double in_max, double out_min, double out_max);

/* Concurrent/task utilities */
int32_t simjot_task_create(const char* data, int32_t priority);
int32_t simjot_task_pending_count(void);
int32_t simjot_task_pop(char* data_out, int32_t data_len, int32_t timeout_ms);
void simjot_task_complete(int32_t task_id, const char* result);
void simjot_task_clear(void);
void simjot_task_stop(void);
void simjot_parallel_set_threads(int32_t count);
int32_t simjot_parallel_get_hw_threads(void);
void simjot_thread_sleep(int32_t milliseconds);
int64_t simjot_thread_id(void);
int64_t simjot_atomic_inc(int32_t counter_id);
int64_t simjot_atomic_dec(int32_t counter_id);
int64_t simjot_atomic_get(int32_t counter_id);
void simjot_atomic_set(int32_t counter_id, int64_t value);
int32_t simjot_atomic_cas(int32_t counter_id, int64_t expected, int64_t desired);
int64_t simjot_atomic_add(int32_t counter_id, int64_t value);
int64_t simjot_hrtime_ns(void);
int64_t simjot_monotonic_ms(void);

/* Collection utilities */
int32_t simjot_set_create(void);
void simjot_set_add(int32_t set_id, const char* str);
int32_t simjot_set_contains(int32_t set_id, const char* str);
void simjot_set_remove(int32_t set_id, const char* str);
int32_t simjot_set_size(int32_t set_id);
void simjot_set_clear(int32_t set_id);
int32_t simjot_set_add_bulk(int32_t set_id, const char* strings);
int32_t simjot_map_create(void);
void simjot_map_set(int32_t map_id, const char* key, const char* value);
int32_t simjot_map_get(int32_t map_id, const char* key, char* output, int32_t output_len);
int32_t simjot_map_has(int32_t map_id, const char* key);
void simjot_map_remove(int32_t map_id, const char* key);
int32_t simjot_map_size(int32_t map_id);
void simjot_map_clear(int32_t map_id);
int32_t simjot_freq_create(void);
void simjot_freq_add(int32_t map_id, const char* str, int32_t count);
int32_t simjot_freq_get(int32_t map_id, const char* str);
int32_t simjot_freq_top_n(int32_t map_id, int32_t n, char* output, int32_t output_len);
int32_t simjot_freq_unique_count(int32_t map_id);
int32_t simjot_freq_total_count(int32_t map_id);
void simjot_freq_clear(int32_t map_id);
int32_t simjot_freq_add_text(int32_t map_id, const char* text);
int32_t simjot_cache_create(int32_t max_size);
void simjot_cache_set(int32_t cache_id, const char* key, const char* value);
int32_t simjot_cache_get(int32_t cache_id, const char* key, char* output, int32_t output_len);
int32_t simjot_cache_size(int32_t cache_id);
void simjot_cache_clear(int32_t cache_id);

/* SIMD operations */
int32_t simjot_simd_strlen(const char* str);
int32_t simjot_simd_memchr(const void* haystack, int needle, int32_t len);
int32_t simjot_simd_strcasechr(const char* str, int c);
int64_t simjot_simd_sum_i32(const int32_t* arr, int32_t len);
double simjot_simd_sum_f64(const double* arr, int32_t len);
void simjot_simd_minmax_f64(const double* arr, int32_t len, double* out_min, double* out_max);
int32_t simjot_simd_memcmp(const void* a, const void* b, int32_t len);
int32_t simjot_simd_support_level(void);

/* Memory pool operations */
int32_t simjot_pool_create(int32_t block_size, int32_t initial_blocks);
void* simjot_pool_alloc(int32_t pool_id);
void simjot_pool_free(int32_t pool_id, void* ptr);
void simjot_pool_stats(int32_t pool_id, int32_t* total, int32_t* used, int32_t* block_size);
void simjot_pool_destroy(int32_t pool_id);

/* Arena allocator */
int32_t simjot_arena_create(void);
void* simjot_arena_alloc(int32_t arena_id, int32_t size, int32_t alignment);
const char* simjot_arena_strdup(int32_t arena_id, const char* str);
void simjot_arena_reset(int32_t arena_id);
void simjot_arena_destroy(int32_t arena_id);
void simjot_arena_stats(int32_t arena_id, int64_t* total_allocated, int64_t* total_used);

/* String interning */
int32_t simjot_intern_init(void);
const char* simjot_intern(const char* str);
int32_t simjot_intern_contains(const char* str);
int32_t simjot_intern_count(void);
void simjot_intern_clear(void);

/* Scratch buffer */
void* simjot_scratch_alloc(int32_t size);
void simjot_scratch_reset(void);
int32_t simjot_scratch_capacity(void);

/* File system operations */
int64_t simjot_fs_size(const char* path);
int64_t simjot_fs_mtime(const char* path);
int32_t simjot_fs_exists(const char* path);
int32_t simjot_fs_is_dir(const char* path);
int32_t simjot_fs_is_file(const char* path);
int32_t simjot_fs_count(const char* path, int32_t max_depth, int32_t* file_count, int32_t* dir_count, int64_t* total_size);
int32_t simjot_fs_list_recursive(const char* path, const char* extension, int32_t max_depth, char* output, int32_t output_len);
int32_t simjot_fs_find(const char* path, const char* pattern, char* output, int32_t output_len);
int32_t simjot_fs_read_all(const char* path, uint8_t* output, int32_t output_len);
int32_t simjot_fs_write_all(const char* path, const uint8_t* data, int32_t len);
int32_t simjot_fs_append(const char* path, const uint8_t* data, int32_t len);
int32_t simjot_fs_mkdir(const char* path);
int32_t simjot_fs_remove(const char* path);
int32_t simjot_fs_rename(const char* old_path, const char* new_path);
int32_t simjot_fs_watch_create(const char* path);
int32_t simjot_fs_watch_poll(int32_t watch_id, int32_t timeout_ms);
void simjot_fs_watch_destroy(int32_t watch_id);
int32_t simjot_fs_extension(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_basename(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_dirname(const char* path, char* output, int32_t output_len);
int32_t simjot_fs_join(const char* base, const char* child, char* output, int32_t output_len);
int32_t simjot_fs_list_filtered(const char* dir_path, const char* extensions, int32_t include_hidden, char* output, int32_t output_len);
int32_t simjot_fs_count_entries(const char* dir_path, int32_t include_hidden);

/* Image operations */
int32_t simjot_image_resize_bilinear(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize_bicubic(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize_area(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h);
int32_t simjot_image_resize(const uint8_t* src, int32_t src_w, int32_t src_h, uint8_t* dst, int32_t dst_w, int32_t dst_h, int32_t quality);
void simjot_image_calc_fit_size(int32_t src_w, int32_t src_h, int32_t max_w, int32_t max_h, int32_t* out_w, int32_t* out_h);
void simjot_image_argb_to_rgba(const int32_t* argb, uint8_t* rgba, int32_t pixel_count);
void simjot_image_rgba_to_argb(const uint8_t* rgba, int32_t* argb, int32_t pixel_count);
int32_t simjot_image_resize_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h, int32_t* dst_argb, int32_t dst_w, int32_t dst_h, int32_t quality);

/* Accent color extraction from images */
int32_t simjot_image_extract_accent(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride);
int32_t simjot_image_extract_accent_64(const uint8_t* pixels64);

/* Background image processing (native cache + scaling + opacity) */
void simjot_bg_calc_cover_fit(int32_t src_w, int32_t src_h, int32_t panel_w, int32_t panel_h, int32_t* out_w, int32_t* out_h, int32_t* out_x, int32_t* out_y);
void simjot_bg_apply_opacity(int32_t* argb, int32_t pixel_count, float opacity);
int32_t simjot_bg_process(const int32_t* src_argb, int32_t src_w, int32_t src_h, int32_t panel_w, int32_t panel_h, float opacity, const char* cache_key, int32_t** out_pixels, int32_t* out_w, int32_t* out_h, int32_t* out_x, int32_t* out_y);
void simjot_bg_cache_clear(void);
void simjot_bg_cache_invalidate(const char* cache_key, int32_t panel_w, int32_t panel_h, float opacity);
void simjot_bg_cache_stats(int32_t* count, int64_t* total_bytes);
void simjot_bg_blur(int32_t* argb, int32_t width, int32_t height, int32_t radius, int32_t passes);

/* Aero/Glass UI effect computations */
void simjot_aero_outer_glow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas);
int32_t simjot_aero_outer_glow_alpha(int32_t layer, int32_t size, int32_t max_alpha);
void simjot_aero_inner_shadow_alphas(int32_t size, int32_t max_alpha, uint8_t* out_alphas);
int32_t simjot_aero_inner_shadow_alpha(int32_t layer, int32_t size, int32_t max_alpha);
int32_t simjot_aero_lerp_color(int32_t color1, int32_t color2, float t);
void simjot_aero_gradient_colors(int32_t top_color, int32_t bottom_color, int32_t height, int32_t* out_colors);
int32_t simjot_aero_blend_over(int32_t fg, int32_t bg);
int32_t simjot_aero_apply_alpha(int32_t color, int32_t alpha);
void simjot_aero_glass_overlay(int32_t height, uint8_t* out_sheen_alphas, uint8_t* out_shadow_alphas);
void simjot_aero_frosted_glass(int32_t height, int32_t base_top_alpha, int32_t base_bottom_alpha, uint8_t* out_base_alphas);
void simjot_aero_glow_stroke_widths(int32_t size, int32_t* out_widths);
void simjot_aero_shadow_insets(int32_t size, int32_t* out_insets);

/* Animation math - easing functions */
float simjot_ease_cosine(float t);
float simjot_ease_smootherstep(float t);
float simjot_ease_smoothstep(float t);
float simjot_ease_out_cubic(float t);
float simjot_ease_in_cubic(float t);
float simjot_ease_in_out_cubic(float t);
float simjot_ease_elastic(float t);
float simjot_spring_decay(float current, float damping, float threshold);

/* Animation math - heartbeat */
float simjot_heartbeat_scale(float phase, float baseAmplitude, float spring);
int32_t simjot_heartbeat_is_peak(float currentEased, float lastEased, float threshold);
void simjot_heartbeat_update(float* phase, float phaseStep, float* spring, float springDamping, float springKick, float* lastEased, float baseAmplitude, float* outScale, int32_t* outPeaked);

/* Animation math - ECG waveform */
float simjot_ecg_sample(float phase);
void simjot_ecg_generate(float startPhase, float phaseStep, float* output, int32_t count);

/* Animation math - fade transitions */
float simjot_fade_alpha(int64_t elapsedMs, int64_t durationMs, int32_t fadeOut, int32_t easingType);
int32_t simjot_fade_is_complete(int64_t elapsedMs, int64_t durationMs);

/* Animation math - color interpolation */
int32_t simjot_color_lerp(int32_t color1, int32_t color2, float t);
int32_t simjot_color_lerp_eased(int32_t color1, int32_t color2, float t, int32_t easingType);

/* Animation math - disappear animation */
void simjot_disappear_anim(float t, float* outAlpha, float* outScale, float* outOffsetX);
float simjot_disappear_value(float t);
float simjot_collapse_height(float t);

/* ═══════════════════════════════════════════════════════════════════════════
 * UI SCALING - Cross-platform DPI-aware scaling
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Display information */
int32_t simjot_get_display_count(void);
float simjot_get_display_scale(int32_t displayIndex);
float simjot_get_primary_display_scale(void);
float simjot_get_display_dpi(int32_t displayIndex);
void simjot_invalidate_display_cache(void);

/* macOS system hints */
float simjot_macos_get_primary_refresh_rate(void);
int32_t simjot_macos_is_low_power_mode(void);
int32_t simjot_macos_reduce_motion_enabled(void);
int32_t simjot_macos_get_thermal_state(void);
int32_t simjot_macos_get_accent_color(void);
int32_t simjot_macos_is_on_battery(void);
int32_t simjot_macos_get_icloud_path(char* out, int32_t out_len);
int32_t simjot_macos_is_icloud_path(const char* path);
int32_t simjot_macos_icloud_is_available(void);
int32_t simjot_macos_icloud_item_status(const char* path);
int32_t simjot_macos_icloud_start_download(const char* path);
int32_t simjot_macos_icloud_prefetch_dir(const char* path, int32_t max_items, int32_t max_depth);
int32_t simjot_macos_icloud_prefetch_query(const char* path, int32_t max_items, int32_t timeout_ms);
int32_t simjot_macos_icloud_list_conflicts(const char* path, int32_t max_items, int32_t timeout_ms, char* out, int32_t out_len);
int32_t simjot_macos_icloud_ensure_downloaded(const char* path, int32_t timeout_ms);
int32_t simjot_macos_icloud_coordinated_write(const char* path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir);
int32_t simjot_macos_icloud_coordinated_copy(const char* src_path, const char* dst_path, int32_t copy_attributes);
int32_t simjot_macos_icloud_coordinated_move(const char* src_path, const char* dst_path);

/* macOS image pipeline (Accelerate/vImage fast path) */
int32_t simjot_macos_image_scale_argb(const int32_t* src_argb, int32_t src_w, int32_t src_h,
                                      int32_t* dst_argb, int32_t dst_w, int32_t dst_h,
                                      int32_t quality);
int32_t simjot_macos_image_blur_argb(int32_t* argb, int32_t width, int32_t height, int32_t radius);
int32_t simjot_macos_image_tint_argb(int32_t* argb, int32_t width, int32_t height,
                                     int32_t tint_color, float intensity);

/* macOS Spotlight search */
int32_t simjot_macos_spotlight_search(const char* root_paths_newline,
                                      const char* query,
                                      const char* extensions_csv,
                                      int32_t max_results,
                                      int32_t timeout_ms,
                                      char* out,
                                      int32_t out_len);

/* macOS Quick Look thumbnails (PNG output bytes) */
int32_t simjot_macos_quicklook_thumbnail_png(const char* path,
                                             int32_t max_edge,
                                             uint8_t* out,
                                             int32_t out_len);

/* macOS security-scoped bookmarks */
int32_t simjot_macos_bookmark_create(const char* path, uint8_t* out, int32_t out_len);
int32_t simjot_macos_bookmark_resolve(const uint8_t* bookmark_data, int32_t data_len,
                                      char* out_path, int32_t out_path_len,
                                      int32_t* out_is_stale);
int32_t simjot_macos_bookmark_start_access(const uint8_t* bookmark_data, int32_t data_len,
                                           int64_t* out_token);
int32_t simjot_macos_bookmark_stop_access(int64_t token);

/* macOS power assertions (prevent sleep during long operations) */
int32_t simjot_macos_power_assertion_begin(const char* reason, int64_t* out_token);
int32_t simjot_macos_power_assertion_end(int64_t token);

/* ═══════════════════════════════════════════════════════════════════════════
 * HARDWARE DETECTION - CPU/GPU/Memory profiling for performance optimization
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Get CPU architecture: 1=Apple Silicon, 2=Intel 64-bit, 3=Intel 32-bit, 0=Unknown */
int32_t simjot_hw_get_architecture(void);

/* Get total system memory in MB */
int64_t simjot_hw_get_total_memory_mb(void);

/* Get CPU brand string */
int32_t simjot_hw_get_cpu_brand(char* out, int32_t out_len);

/* Get CPU core counts */
int32_t simjot_hw_get_cpu_core_count(void);
int32_t simjot_hw_get_active_core_count(void);

/* GPU detection */
int32_t simjot_hw_has_discrete_gpu(void);
int32_t simjot_hw_supports_metal(void);
int32_t simjot_hw_get_gpu_memory_mb(void);

/* Platform detection */
int32_t simjot_hw_is_apple_silicon(void);
int32_t simjot_hw_is_rosetta(void);

/* Performance profiling */
int32_t simjot_hw_get_performance_tier(void);  /* 0=HIGH, 1=MEDIUM, 2=LOW, 3=VERY_LOW */
int32_t simjot_hw_get_recommended_fps(void);
int32_t simjot_hw_supports_promotion(void);    /* ProMotion (120Hz) display */

/* Scaling utilities */
int32_t simjot_scale_dimension(int32_t value, float scale);
float simjot_scale_value(float value, float scale);
float simjot_scale_font_size(float baseSize, float scale);
void simjot_scale_insets(int32_t top, int32_t left, int32_t bottom, int32_t right,
                         float scale, int32_t* outInsets);
void simjot_scale_dimensions(int32_t width, int32_t height, float scale, int32_t* outDimensions);

/* ═══════════════════════════════════════════════════════════════════════════
 * SETUP & INITIALIZATION - First-time setup utilities
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Initialize directory structure */
int32_t simjot_setup_init(const char* root_path);

/* Verify setup is complete (returns bitmask) */
int32_t simjot_setup_verify(const char* root_path);

/* Check if directory is truly writable */
int32_t simjot_verify_writable(const char* dir_path);

/* Get detailed status (8-element array) */
int32_t simjot_setup_status(const char* root_path, int32_t* out_status);

/* Config file operations */
int32_t simjot_write_config(const char* config_path, const char* root_path);
int32_t simjot_read_config(const char* config_path, char* out_root_path);

/* Directory operations */
int32_t simjot_create_directory(const char* dir_path);

/* Check if setup is needed */
int32_t simjot_needs_setup(const char* config_path);

/* ═══════════════════════════════════════════════════════════════════════════
 * WATCHDOG SYSTEM - Application lifecycle management
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Start a watchdog timer (action: 0=none, 1=callback, 2=exit, 3=halt) */
int32_t simjot_watchdog_start(int64_t timeout_ms, int32_t action, const char* name);

/* Cancel a running watchdog */
int32_t simjot_watchdog_cancel(int32_t id);

/* Reset watchdog timer (restart countdown) */
int32_t simjot_watchdog_reset(int32_t id);

/* Get watchdog state (0=inactive, 1=running, 2=triggered, 3=cancelled) */
int32_t simjot_watchdog_state(int32_t id);

/* Get remaining time in milliseconds */
int64_t simjot_watchdog_remaining(int32_t id);

/* Force immediate process halt */
void simjot_force_halt(void);

/* Get monotonic time in milliseconds */
int64_t simjot_monotonic_time_ms(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE METADATA UTILITIES - Fast native file operations
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Count words in a text buffer */
int32_t simjot_count_words(const char* text, int32_t len);

/* Count words in a file (reads in chunks, memory efficient) */
int32_t simjot_count_words_file(const char* path);

/* Extract first non-empty line from file as title */
int32_t simjot_extract_title(const char* path, char* out, int32_t out_len);

/* Get file size and modification time */
int32_t simjot_file_meta(const char* path, int64_t* out_size, int64_t* out_mtime);

/* Batch metadata: word count, size, mtime, title in one call */
int32_t simjot_file_meta_batch(const char* path, uint8_t* out, int32_t out_len);

/* List files in directory with metadata (name, size, mtime) */
int32_t simjot_list_files_meta(const char* dir_path, const char* extension,
                                uint8_t* out, int32_t out_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * COMPONENT PROFILER - Per-component CPU and memory tracking
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Initialize profiler with sampling interval in milliseconds */
int32_t simjot_profiler_init(int32_t sample_interval_ms);

/* Start/stop profiling */
int32_t simjot_profiler_start(void);
int32_t simjot_profiler_stop(void);
void simjot_profiler_reset(void);

/* Component registration */
int32_t simjot_profiler_register_component(const char* name);
int32_t simjot_profiler_register_thread(const char* component_name, uint64_t thread_id);
int32_t simjot_profiler_unregister_thread(const char* component_name, uint64_t thread_id);

/* Memory tracking */
void simjot_profiler_track_alloc(const char* component_name, int64_t bytes);
void simjot_profiler_track_free(const char* component_name, int64_t bytes);

/* Sampling - call periodically to update metrics */
int32_t simjot_profiler_sample(void);

/* Snapshot/reporting */
int32_t simjot_profiler_component_count(void);
int32_t simjot_profiler_get_component_snapshot(int32_t index, uint8_t* out, int32_t out_len);
int32_t simjot_profiler_get_summary(uint8_t* out, int32_t out_len);

/* CLI output - returns length written */
int32_t simjot_profiler_print_report(char* out, int32_t out_len);
int32_t simjot_profiler_status_line(char* out, int32_t out_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * IMAGE SCALING - SIMD-accelerated resize
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Bilinear interpolation (fast) */
int32_t simjot_image_scale_bilinear(const uint32_t* src, int32_t src_w, int32_t src_h,
                                     uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Bicubic interpolation (high quality) */
int32_t simjot_image_scale_bicubic(const uint32_t* src, int32_t src_w, int32_t src_h,
                                    uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Progressive downscale (best for large reductions) */
int32_t simjot_image_scale_progressive(const uint32_t* src, int32_t src_w, int32_t src_h,
                                        uint32_t* dst, int32_t dst_w, int32_t dst_h);

/* Auto-select algorithm: quality 0=fast, 1=balanced, 2=best */
int32_t simjot_image_scale(const uint32_t* src, int32_t src_w, int32_t src_h,
                            uint32_t* dst, int32_t dst_w, int32_t dst_h,
                            int32_t quality);

/* Gaussian blur (separable) */
int32_t simjot_image_blur(uint32_t* pixels, int32_t width, int32_t height, int32_t radius);

/* Tint image with color */
int32_t simjot_image_tint(uint32_t* pixels, int32_t width, int32_t height,
                           uint32_t tint_color, float intensity);

/* ═══════════════════════════════════════════════════════════════════════════
 * SPELL CHECK - Edit distance and candidate generation
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Generate edit-distance-1 candidates (null-separated output) */
int32_t simjot_spell_edit1(const char* word, char* output, int32_t output_len);

/* Generate edit-distance-2 candidates (limited for performance) */
int32_t simjot_spell_edit2(const char* word, char* output, int32_t output_len);

/* Levenshtein distance */
int32_t simjot_levenshtein(const char* a, const char* b);

/* Damerau-Levenshtein distance (with transpositions) */
int32_t simjot_damerau_levenshtein(const char* a, const char* b);

/* Batch Levenshtein: compute distance to multiple candidates */
int32_t simjot_levenshtein_batch(const char* word, const char* candidates, int32_t candidates_len,
                                  int32_t* distances, int32_t max_results);

/* ═══════════════════════════════════════════════════════════════════════════
 * AUTOCORRECT - Intelligent correction with keyboard/phonetic awareness
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Keyboard adjacency correction (QWERTY layout) */
int32_t simjot_autocorrect_adjacent_key(const char* word, char* output, int32_t output_len);

/* Phonetic pattern correction */
int32_t simjot_autocorrect_phonetic(const char* word, char* output, int32_t output_len);

/* Preserve case pattern from original to correction */
int32_t simjot_autocorrect_preserve_case(const char* original, char* correction, int32_t correction_len);

/* Combined correction (phonetic -> adjacent -> spell suggestions) */
int32_t simjot_autocorrect_correct(const char* word, char* output, int32_t output_len);

/* Check if word starts with vowel sound (for a/an) */
int32_t simjot_autocorrect_starts_vowel_sound(const char* word);

/* Phrase correction (e.g., "should of" -> "should have") */
int32_t simjot_autocorrect_phrase(const char* word1, const char* word2, char* output, int32_t output_len);

/* Fix capitalization issues (standalone i, double spaces) */
int32_t simjot_autocorrect_fix_caps(const char* text, char* output, int32_t output_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * TEXT UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* String sanitization */
char* simjot_text_sanitize(const char* input);
char* simjot_text_collapse_whitespace(const char* input);
char* simjot_text_strip_html(const char* input);
char* simjot_text_to_lower(const char* input);
char* simjot_text_to_upper(const char* input);
char* simjot_text_title_case(const char* input);

/* Text statistics (alternate signatures) */
int32_t simjot_text_char_count_nospace(const char* text);
int32_t simjot_text_syllable_count(const char* word);
int32_t simjot_text_analyze(const char* text, int32_t* out_stats, int32_t stats_count);

/* Text validation */
int32_t simjot_text_is_ascii(const char* text);
int32_t simjot_text_is_alnum(const char* text);
int32_t simjot_text_is_identifier(const char* text);
int32_t simjot_text_is_email(const char* text);
int32_t simjot_text_is_safe_filename(const char* text);

/* Parsing */
int32_t simjot_parse_int(const char* text, int32_t default_val);
double simjot_parse_double(const char* text, double default_val);
int32_t simjot_parse_bool(const char* text, int32_t default_val);

/* String extraction */
char* simjot_text_first_words(const char* text, int32_t count);
char* simjot_text_first_line(const char* text);
char* simjot_text_ellipsis(const char* text, int32_t max_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * MATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Basic statistics */
double simjot_math_mean(const double* values, int32_t count);
double simjot_math_mean_int(const int32_t* values, int32_t count);
double simjot_math_variance(const double* values, int32_t count);
double simjot_math_stddev(const double* values, int32_t count);
double simjot_math_min(const double* values, int32_t count);
double simjot_math_max(const double* values, int32_t count);
double simjot_math_sum(const double* values, int32_t count);
double simjot_math_median(double* values, int32_t count);
double simjot_math_percentile(double* values, int32_t count, double percentile);

/* Moving averages */
int32_t simjot_math_sma(const double* values, int32_t count, int32_t window, double* out);
int32_t simjot_math_ema(const double* values, int32_t count, double alpha, double* out);

/* Correlation & regression */
double simjot_math_correlation(const double* x, const double* y, int32_t count);
double simjot_math_linear_regression(const double* x, const double* y, int32_t count,
                                      double* out_slope, double* out_intercept);

/* Clamping & normalization */
int32_t simjot_math_clamp_int(int32_t value, int32_t min, int32_t max);
double simjot_math_clamp(double value, double min, double max);
double simjot_math_lerp(double a, double b, double t);
double simjot_math_inverse_lerp(double a, double b, double value);
int32_t simjot_math_normalize(const double* values, int32_t count, double* out);

/* Comprehensive stats */
int32_t simjot_math_stats(const double* values, int32_t count, double* out, int32_t out_count);

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* File information */
int64_t simjot_file_size(const char* path);
int64_t simjot_file_mtime(const char* path);
int32_t simjot_file_exists(const char* path);
int32_t simjot_file_is_file(const char* path);
int32_t simjot_file_is_dir(const char* path);
int32_t simjot_file_is_readable(const char* path);
int32_t simjot_file_is_writable(const char* path);

/* Disk space */
int64_t simjot_disk_available(const char* path);
int64_t simjot_disk_total(const char* path);

/* File operations */
char* simjot_file_read(const char* path, int64_t* out_size);
char* simjot_file_read_text(const char* path);
int32_t simjot_file_write_atomic(const char* path, const char* data, int64_t size);
int32_t simjot_file_copy(const char* src, const char* dst);
int32_t simjot_file_delete(const char* path);
int32_t simjot_mkdir_p(const char* path);

/* Directory listing */
int32_t simjot_dir_count(const char* path, const char* extension);
int32_t simjot_dir_list(const char* path, const char* extension, char* out_names, int32_t out_size);

/* Path utilities */
const char* simjot_path_basename(const char* path);
const char* simjot_path_extension(const char* path);
char* simjot_path_dirname(const char* path);
char* simjot_path_join(const char* dir, const char* name);

/* ═══════════════════════════════════════════════════════════════════════════
 * MOOD ANALYTICS ENGINE
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Daily averages computation */
int32_t simjot_mood_daily_averages(
    const int32_t* samples, const int64_t* timestamps, int32_t count,
    int32_t start_day, int32_t num_days,
    double* out_averages, int32_t* out_counts);

/* Smoothing (rolling average) */
int32_t simjot_mood_smooth(const double* values, int32_t count, int32_t window, double* out_smoothed);

/* Volatility (standard deviation) */
double simjot_mood_volatility(const double* values, int32_t count);

/* Streak detection */
int32_t simjot_mood_streaks(
    const double* values, int32_t count, double threshold,
    int32_t* out_current, int32_t* out_longest_good, int32_t* out_longest_bad);

/* Overall average */
double simjot_mood_average(const double* values, int32_t count);

/* Complete mood analysis */
int32_t simjot_mood_analyze(
    const int32_t* mood_values, const int64_t* timestamps, int32_t sample_count,
    int32_t days_back, int32_t smoothing_window,
    double* out_stats, double* out_daily_avgs, double* out_smoothed,
    int32_t max_days);

/* Trend analysis */
double simjot_mood_trend(const double* values, int32_t count);
double simjot_mood_weighted_recent(const double* values, int32_t count, double decay);

/* Correlation and anomaly detection */
double simjot_mood_correlation(const int32_t* a, const int32_t* b, int32_t count);
int32_t simjot_mood_anomalies(const double* values, int32_t count, double threshold, int32_t* out_anomalies);

/* ═══════════════════════════════════════════════════════════════════════════
 * VOCABULARY ANALYZER
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Word extraction */
int32_t simjot_vocab_extract_words(const char* text, char* out_words, int32_t out_size, int32_t* out_count);

/* Syllable counting */
int32_t simjot_vocab_syllables(const char* word);

/* Readability metrics */
double simjot_vocab_flesch_ease(int32_t words, int32_t sentences, int32_t syllables);
double simjot_vocab_flesch_kincaid(int32_t words, int32_t sentences, int32_t syllables);
double simjot_vocab_gunning_fog(int32_t words, int32_t sentences, int32_t complex_words);
double simjot_vocab_smog(int32_t polysyllabic, int32_t sentences);
double simjot_vocab_coleman_liau(int32_t words, int32_t sentences, int32_t characters);
double simjot_vocab_ari(int32_t words, int32_t sentences, int32_t characters);

/* Lexical diversity */
double simjot_vocab_ttr(int32_t unique_words, int32_t total_words);
double simjot_vocab_yules_k(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words);
double simjot_vocab_simpsons_d(const int32_t* freq_spectrum, int32_t max_freq, int32_t total_words);

/* Comprehensive analysis */
int32_t simjot_vocab_analyze(const char* text, double* out_stats);
int32_t simjot_vocab_top_words(const char* text, int32_t n, char* out_words, int32_t* out_counts, int32_t out_size);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST SEARCH (SIMD-accelerated)
 * ═══════════════════════════════════════════════════════════════════════════ */

int64_t simjot_search_find(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int64_t simjot_search_find_ci(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int32_t simjot_search_count(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len);
int32_t simjot_search_find_all(const char* haystack, int64_t haystack_len, const char* needle, int64_t needle_len, int64_t* out_positions, int32_t max_results);

/* Multi-pattern search (Aho-Corasick) */
void* simjot_search_ac_build(const char* patterns, int32_t pattern_count);
int32_t simjot_search_ac_find(void* ac_handle, const char* text, int64_t text_len, int64_t* out_positions, int32_t* out_patterns, int32_t max_results);
void simjot_search_ac_free(void* ac_handle);

/* Fuzzy search */
int32_t simjot_search_fuzzy(const char* text, int64_t text_len, const char* pattern, int32_t pattern_len, int32_t max_distance, int64_t* out_positions, int32_t* out_distances, int32_t max_results);

/* ═══════════════════════════════════════════════════════════════════════════
 * BATCH SEARCH - Global entry search optimization
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Batch search across multiple directories for entry files.
 * @param query Search query string
 * @param dirs Newline-separated list of directory paths
 * @param extensions Comma-separated extensions (e.g., ".note,.txt")
 * @param max_results Maximum number of results
 * @param output Output buffer for binary results
 * @param output_len Size of output buffer
 * @return Number of results found, or -1 on error
 */
int32_t simjot_search_batch(const char* query, const char* dirs, const char* extensions,
                             int32_t max_results, uint8_t* output, int32_t output_len);

/**
 * Get recommended buffer size for batch search.
 * @param max_results Maximum number of results
 * @return Recommended buffer size in bytes
 */
int32_t simjot_search_batch_buffer_size(int32_t max_results);

/* ═══════════════════════════════════════════════════════════════════════════
 * MEMORY POOL ALLOCATORS
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Fixed-size block pool (handle-based API) */
void* simjot_pool_create_ex(int32_t block_size, int32_t block_count);
void* simjot_pool_alloc_ex(void* pool_handle);
void simjot_pool_free_ex(void* pool_handle, void* ptr);
void simjot_pool_stats_ex(void* pool_handle, int32_t* out_total, int32_t* out_allocated, int32_t* out_block_size);
void simjot_pool_reset_ex(void* pool_handle);
void simjot_pool_destroy_ex(void* pool_handle);

/* Arena allocator (bump pointer, handle-based API) */
void* simjot_arena_create_ex(int32_t initial_size);
void* simjot_arena_alloc_ex(void* arena_handle, int32_t size, int32_t align);
void* simjot_arena_calloc_ex(void* arena_handle, int32_t count, int32_t size);
char* simjot_arena_strdup_ex(void* arena_handle, const char* str);
void simjot_arena_stats_ex(void* arena_handle, int64_t* out_used, int64_t* out_capacity);
void simjot_arena_reset_ex(void* arena_handle);
void simjot_arena_destroy_ex(void* arena_handle);

/* Slab allocator (multiple size classes) */
void* simjot_slab_create(void);
void* simjot_slab_alloc(void* slab_handle, int32_t size);
void simjot_slab_free(void* slab_handle, void* ptr, int32_t size);
void simjot_slab_destroy(void* slab_handle);

/* ═══════════════════════════════════════════════════════════════════════════
 * LRU CACHE
 * ═══════════════════════════════════════════════════════════════════════════ */

void* simjot_lru_cache_create(int32_t max_entries, int64_t max_memory);
void simjot_lru_cache_set_destructor(void* cache_handle, void (*destructor)(void*));
int32_t simjot_lru_cache_put(void* cache_handle, const char* key, void* value, int32_t value_size, int64_t ttl_ms);
void* simjot_lru_cache_get(void* cache_handle, const char* key, int32_t* out_size);
int32_t simjot_lru_cache_contains(void* cache_handle, const char* key);
int32_t simjot_lru_cache_remove(void* cache_handle, const char* key);
void simjot_lru_cache_clear(void* cache_handle);
void simjot_lru_cache_stats(void* cache_handle, int64_t* out_hits, int64_t* out_misses, int32_t* out_size, int64_t* out_memory);
void simjot_lru_cache_destroy(void* cache_handle);

/* String interning */
void* simjot_interner_create(void);
const char* simjot_interner_intern(void* interner_handle, const char* str);
void simjot_interner_stats(void* interner_handle, int64_t* out_count, int64_t* out_saved);
void simjot_interner_destroy(void* interner_handle);

/* Bloom filter */
void* simjot_bloom_create(int32_t expected_items, double false_positive_rate);
void simjot_bloom_add(void* filter_handle, const char* item);
int32_t simjot_bloom_test(void* filter_handle, const char* item);
void simjot_bloom_destroy(void* filter_handle);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST COMPRESSION
 * ═══════════════════════════════════════════════════════════════════════════ */

int32_t simjot_lz4_compress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_lz4_decompress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_lz4_compress_bound(int32_t src_size);

/* RLE compression */
int32_t simjot_rle_compress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);
int32_t simjot_rle_decompress(const uint8_t* src, int32_t src_size, uint8_t* dst, int32_t dst_capacity);

/* Delta encoding */
int32_t simjot_delta_encode_i32(const int32_t* src, int32_t count, int32_t* dst);
int32_t simjot_delta_decode_i32(const int32_t* src, int32_t count, int32_t* dst);
int32_t simjot_delta_encode_f64(const double* src, int32_t count, int64_t* dst);
int32_t simjot_delta_decode_f64(const int64_t* src, int32_t count, double* dst);

/* Zigzag encoding */
int32_t simjot_zigzag_encode_i32(const int32_t* src, int32_t count, uint32_t* dst);
int32_t simjot_zigzag_decode_i32(const uint32_t* src, int32_t count, int32_t* dst);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST JSON PARSER
 * ═══════════════════════════════════════════════════════════════════════════ */

void* simjot_json_parse(const char* json, int32_t len);
int32_t simjot_json_type(void* parser);
const char* simjot_json_get_string_at(void* parser, int32_t index, int32_t* out_len);
double simjot_json_get_number(void* parser, int32_t index);
int32_t simjot_json_get_bool(void* parser, int32_t index);
int32_t simjot_json_array_length(void* parser, int32_t index);
int32_t simjot_json_array_get(void* parser, int32_t array_idx, int32_t elem_idx);
int32_t simjot_json_object_size(void* parser, int32_t index);
int32_t simjot_json_object_get(void* parser, int32_t obj_idx, const char* key);
const char* simjot_json_error(void* parser);
void simjot_json_free(void* parser);

/* JSON builder */
void* simjot_json_builder_create(void);
void simjot_json_builder_object_start(void* builder);
void simjot_json_builder_object_end(void* builder);
void simjot_json_builder_array_start(void* builder);
void simjot_json_builder_array_end(void* builder);
void simjot_json_builder_key(void* builder, const char* key);
void simjot_json_builder_string(void* builder, const char* value);
void simjot_json_builder_number(void* builder, double value);
void simjot_json_builder_bool(void* builder, int32_t value);
void simjot_json_builder_null(void* builder);
const char* simjot_json_builder_get(void* builder, int32_t* out_len);
void simjot_json_builder_free(void* builder);

/* ═══════════════════════════════════════════════════════════════════════════
 * VIEWPORT IMAGE CACHE - Efficient embedded image rendering for scrolling
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Initialize the viewport image cache with specified capacity.
 * @param max_entries Maximum number of cached images (recommend 32-64)
 * @param max_memory_mb Maximum memory in MB for the cache (recommend 64-128)
 * @return 1 on success, 0 on failure
 */
int32_t simjot_imgcache_init(int32_t max_entries, int32_t max_memory_mb);

/**
 * Shutdown and free all cached images.
 */
void simjot_imgcache_shutdown(void);

/**
 * Register an image in the cache with a unique ID.
 * @param image_id Unique identifier for this image (e.g., document offset or hash)
 * @param pixels ARGB pixel data
 * @param width Image width
 * @param height Image height
 * @return 1 on success, 0 on failure
 */
int32_t simjot_imgcache_put(int64_t image_id, const uint32_t* pixels, 
                            int32_t width, int32_t height);

/**
 * Get cached image pixels by ID.
 * @param image_id Image identifier
 * @param out_width Output: image width (or NULL)
 * @param out_height Output: image height (or NULL)
 * @return Pointer to cached ARGB pixels, or NULL if not cached
 */
const uint32_t* simjot_imgcache_get(int64_t image_id, 
                                     int32_t* out_width, int32_t* out_height);

/**
 * Check if image is in cache.
 * @param image_id Image identifier
 * @return 1 if cached, 0 if not
 */
int32_t simjot_imgcache_contains(int64_t image_id);

/**
 * Remove a specific image from the cache.
 * @param image_id Image identifier
 * @return 1 if removed, 0 if not found
 */
int32_t simjot_imgcache_remove(int64_t image_id);

/**
 * Clear all cached images.
 */
void simjot_imgcache_clear(void);

/**
 * Get cache statistics.
 * @param out_count Number of cached images
 * @param out_memory_bytes Total memory used
 * @param out_hits Cache hit count
 * @param out_misses Cache miss count
 */
void simjot_imgcache_stats(int32_t* out_count, int64_t* out_memory_bytes,
                           int64_t* out_hits, int64_t* out_misses);

/**
 * Perform viewport culling - returns which images are visible.
 * @param image_ids Array of image IDs to check
 * @param image_y_positions Y position of each image in document coordinates
 * @param image_heights Height of each image
 * @param count Number of images
 * @param viewport_y Top of viewport in document coordinates
 * @param viewport_height Height of viewport
 * @param out_visible Output array: 1 if visible, 0 if not (must be preallocated)
 * @return Number of visible images
 */
int32_t simjot_imgcache_cull_viewport(const int64_t* image_ids,
                                       const int32_t* image_y_positions,
                                       const int32_t* image_heights,
                                       int32_t count,
                                       int32_t viewport_y,
                                       int32_t viewport_height,
                                       int32_t* out_visible);

/**
 * Blit a cached image to an output buffer at specified position.
 * Used for compositing visible images onto a scroll buffer.
 * @param image_id Image to blit
 * @param dst_pixels Destination ARGB buffer
 * @param dst_width Destination buffer width
 * @param dst_height Destination buffer height
 * @param dst_x X position in destination
 * @param dst_y Y position in destination
 * @param clip_y Top of clip region (viewport)
 * @param clip_height Height of clip region
 * @return 1 on success, 0 on failure
 */
int32_t simjot_imgcache_blit(int64_t image_id,
                              uint32_t* dst_pixels, int32_t dst_width, int32_t dst_height,
                              int32_t dst_x, int32_t dst_y,
                              int32_t clip_y, int32_t clip_height);

/**
 * Pre-scale an image and cache the result.
 * @param image_id ID for the scaled result
 * @param src_pixels Source ARGB pixels
 * @param src_width Source width
 * @param src_height Source height
 * @param target_width Desired width
 * @param quality 0=fast, 1=balanced, 2=best
 * @return 1 on success, 0 on failure
 */
int32_t simjot_imgcache_prescale(int64_t image_id,
                                  const uint32_t* src_pixels, int32_t src_width, int32_t src_height,
                                  int32_t target_width, int32_t quality);

/* ═══════════════════════════════════════════════════════════════════════════
 * AUTOSAVE MANAGER - Multi-session dirty tracking, debouncing, atomic writes
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Initialization */
int32_t simjot_autosave_init(void);

/* Session management */
int32_t simjot_autosave_create_session(const char* file_path, int32_t debounce_ms);
int32_t simjot_autosave_destroy_session(int32_t session_id);
int32_t simjot_autosave_set_path(int32_t session_id, const char* new_path);

/* Dirty tracking */
int32_t simjot_autosave_mark_dirty(int32_t session_id);
int32_t simjot_autosave_mark_clean(int32_t session_id);
int32_t simjot_autosave_is_dirty(int32_t session_id);

/* Save scheduling */
int32_t simjot_autosave_should_save(int32_t session_id);
int64_t simjot_autosave_ms_until_save(int32_t session_id);
int32_t simjot_autosave_get_pending(int32_t* out_ids, int32_t max_ids);

/* Atomic file operations */
int32_t simjot_autosave_write_atomic(int32_t session_id, const uint8_t* data, int32_t data_len);
int32_t simjot_autosave_write_recovery(int32_t session_id, const uint8_t* data, int32_t data_len);
int32_t simjot_autosave_delete_recovery(int32_t session_id);

/* Recovery detection */
int32_t simjot_autosave_has_recovery(const char* file_path);
int32_t simjot_autosave_get_recovery_path(const char* file_path, char* out, int32_t out_len);

/* Statistics */
int32_t simjot_autosave_get_stats(int32_t session_id, int32_t* out_save_count, int64_t* out_last_save_ms, int64_t* out_last_dirty_ms);
void simjot_autosave_get_global_stats(int64_t* out_total_saves, int32_t* out_active_sessions, int32_t* out_dirty_sessions);
int32_t simjot_autosave_get_path(int32_t session_id, char* out, int32_t out_len);

/* ═══════════════════════════════════════════════════════════════════════════
 * UNDO/REDO MANAGER - High-performance text editing history
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Edit type constants */
#define SIMJOT_EDIT_INSERT      1
#define SIMJOT_EDIT_DELETE      2
#define SIMJOT_EDIT_REPLACE     3
#define SIMJOT_EDIT_STYLE       4
#define SIMJOT_EDIT_COMPOUND    5

/**
 * Initialize the undo/redo system.
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_init(void);

/**
 * Shutdown and free all undo/redo resources.
 */
void simjot_undo_shutdown(void);

/**
 * Create a new undo session for an editor instance.
 * @param history_limit Maximum number of undo steps (0 = default 1000)
 * @return Session ID (>0) on success, -1 on failure
 */
int32_t simjot_undo_create_session(int32_t history_limit);

/**
 * Destroy an undo session.
 * @param session_id Session to destroy
 * @return 1 on success, 0 if not found
 */
int32_t simjot_undo_destroy_session(int32_t session_id);

/**
 * Clear all undo/redo history for a session.
 * @param session_id Session ID
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_clear(int32_t session_id);

/**
 * Push an insert operation onto the undo stack.
 * @param session_id Session ID
 * @param offset Position where text was inserted
 * @param text The inserted text
 * @param text_len Length of inserted text
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_push_insert(int32_t session_id, int32_t offset,
                                 const char* text, int32_t text_len);

/**
 * Push a delete operation onto the undo stack.
 * @param session_id Session ID
 * @param offset Position where text was deleted
 * @param deleted_text The deleted text (for restoration)
 * @param text_len Length of deleted text
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_push_delete(int32_t session_id, int32_t offset,
                                 const char* deleted_text, int32_t text_len);

/**
 * Push a replace operation onto the undo stack.
 * @param session_id Session ID
 * @param offset Position of replacement
 * @param old_text Text that was replaced
 * @param old_len Length of old text
 * @param new_text Replacement text
 * @param new_len Length of new text
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_push_replace(int32_t session_id, int32_t offset,
                                  const char* old_text, int32_t old_len,
                                  const char* new_text, int32_t new_len);

/**
 * Push a style change operation onto the undo stack.
 * @param session_id Session ID
 * @param offset Start position of styled region
 * @param length Length of styled region
 * @param style_flags Style flags (application-defined)
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_push_style(int32_t session_id, int32_t offset,
                                int32_t length, int32_t style_flags);

/**
 * Begin a compound edit (groups multiple edits as one undo step).
 * @param session_id Session ID
 * @return 1 on success, 0 if already in compound mode
 */
int32_t simjot_undo_begin_compound(int32_t session_id);

/**
 * End a compound edit.
 * @param session_id Session ID
 * @return 1 on success, 0 if not in compound mode
 */
int32_t simjot_undo_end_compound(int32_t session_id);

/**
 * Check if undo is available.
 * @param session_id Session ID
 * @return 1 if can undo, 0 otherwise
 */
int32_t simjot_undo_can_undo(int32_t session_id);

/**
 * Check if redo is available.
 * @param session_id Session ID
 * @return 1 if can redo, 0 otherwise
 */
int32_t simjot_undo_can_redo(int32_t session_id);

/**
 * Peek at the next undo operation without performing it.
 * @param session_id Session ID
 * @param out_type Output: edit type (SIMJOT_EDIT_*)
 * @param out_offset Output: edit offset
 * @param out_length Output: edit length
 * @param out_text Output: text buffer
 * @param out_text_len Size of text buffer
 * @return 1 on success, 0 if no undo available
 */
int32_t simjot_undo_peek(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len);

/**
 * Perform undo and get the edit details.
 * @param session_id Session ID
 * @param out_type Output: edit type
 * @param out_offset Output: position to apply undo
 * @param out_length Output: length of text to restore/remove
 * @param out_text Output: text buffer for restoration
 * @param out_text_len Size of text buffer
 * @return 1 on success, 0 if no undo available
 */
int32_t simjot_undo_undo(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len);

/**
 * Perform redo and get the edit details.
 * @param session_id Session ID
 * @param out_type Output: edit type
 * @param out_offset Output: position to apply redo
 * @param out_length Output: length of text to apply
 * @param out_text Output: text buffer
 * @param out_text_len Size of text buffer
 * @return 1 on success, 0 if no redo available
 */
int32_t simjot_undo_redo(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len);

/**
 * Mark the current state as a save point.
 * Used for dirty detection.
 * @param session_id Session ID
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_mark_save_point(int32_t session_id);

/**
 * Check if current state matches the save point.
 * @param session_id Session ID
 * @return 1 if at save point, 0 otherwise
 */
int32_t simjot_undo_is_at_save_point(int32_t session_id);

/**
 * Check if document has been modified since last save.
 * @param session_id Session ID
 * @return 1 if dirty (modified), 0 if clean
 */
int32_t simjot_undo_is_dirty(int32_t session_id);

/**
 * Get the number of available undo steps.
 * @param session_id Session ID
 * @return Number of undo steps
 */
int32_t simjot_undo_get_undo_count(int32_t session_id);

/**
 * Get the number of available redo steps.
 * @param session_id Session ID
 * @return Number of redo steps
 */
int32_t simjot_undo_get_redo_count(int32_t session_id);

/**
 * Set the history limit for a session.
 * @param session_id Session ID
 * @param limit New limit (must be > 0)
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_set_history_limit(int32_t session_id, int32_t limit);

/**
 * Get statistics for an undo session.
 * @param session_id Session ID
 * @param out_memory Output: approximate memory usage in bytes
 * @param out_undo_count Output: number of undo steps
 * @param out_redo_count Output: number of redo steps
 * @param out_save_point Output: save point index
 * @param out_change_index Output: current change index
 * @return 1 on success, 0 on failure
 */
int32_t simjot_undo_get_stats(int32_t session_id, int64_t* out_memory,
                               int32_t* out_undo_count, int32_t* out_redo_count,
                               int32_t* out_save_point, int32_t* out_change_index);

/**
 * Get the total number of active undo sessions.
 * @return Number of active sessions
 */
int32_t simjot_undo_session_count(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * HOTKEY MANAGER - Fast OS-aware hotkey detection for text formatting
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Modifier key flags */
#define SIMJOT_MOD_NONE     0x00
#define SIMJOT_MOD_SHIFT    0x01
#define SIMJOT_MOD_CTRL     0x02
#define SIMJOT_MOD_ALT      0x04
#define SIMJOT_MOD_META     0x08  /* Cmd on macOS, Win key on Windows */

/* Text formatting action codes */
#define SIMJOT_ACTION_NONE          0
#define SIMJOT_ACTION_BOLD          1
#define SIMJOT_ACTION_ITALIC        2
#define SIMJOT_ACTION_UNDERLINE     3
#define SIMJOT_ACTION_STRIKETHROUGH 4

/* Platform detection */
#define SIMJOT_PLATFORM_UNKNOWN 0
#define SIMJOT_PLATFORM_MACOS   1
#define SIMJOT_PLATFORM_WINDOWS 2
#define SIMJOT_PLATFORM_LINUX   3

/**
 * Get the current platform identifier.
 * @return SIMJOT_PLATFORM_* constant
 */
int32_t simjot_hotkey_get_platform(void);

/**
 * Get the platform-appropriate modifier mask for text formatting hotkeys.
 * Returns SIMJOT_MOD_META on macOS, SIMJOT_MOD_CTRL on Windows/Linux.
 * @return Modifier mask
 */
int32_t simjot_hotkey_get_primary_modifier(void);

/**
 * Check if a key event matches a text formatting hotkey.
 * Fast O(1) lookup using precomputed tables.
 * 
 * @param key_code The key code (ASCII for A-Z, or platform-specific)
 * @param modifiers Combination of SIMJOT_MOD_* flags
 * @return SIMJOT_ACTION_* code, or SIMJOT_ACTION_NONE if no match
 */
int32_t simjot_hotkey_check(int32_t key_code, int32_t modifiers);

/**
 * Get the expected modifier + key for a specific action on current platform.
 * Useful for displaying hotkey hints in UI.
 * 
 * @param action SIMJOT_ACTION_* code
 * @param out_key_code Output: the key code (e.g., 'B' for bold)
 * @param out_modifiers Output: the modifier mask
 * @return 1 if action is valid, 0 otherwise
 */
int32_t simjot_hotkey_get_binding(int32_t action, int32_t* out_key_code, int32_t* out_modifiers);

/**
 * Get a human-readable string for a hotkey (e.g., "⌘B" or "Ctrl+B").
 * 
 * @param action SIMJOT_ACTION_* code
 * @param out Buffer for the string
 * @param out_len Buffer size
 * @return Length written, or negative if buffer too small
 */
int32_t simjot_hotkey_get_display_string(int32_t action, char* out, int32_t out_len);

/**
 * Batch check multiple key events for efficiency.
 * Useful when processing queued input events.
 * 
 * @param key_codes Array of key codes
 * @param modifiers Array of modifier masks
 * @param count Number of events
 * @param out_actions Output array of SIMJOT_ACTION_* codes
 * @return Number of matched actions (non-NONE)
 */
int32_t simjot_hotkey_check_batch(const int32_t* key_codes, const int32_t* modifiers,
                                   int32_t count, int32_t* out_actions);

/* ═══════════════════════════════════════════════════════════════════════════
 * OFFSCREEN BUFFER - Native double-buffering for smooth scrolling
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Create an offscreen buffer for double-buffered rendering.
 * Returns an opaque handle to the buffer.
 * 
 * @param width Buffer width in pixels
 * @param height Buffer height in pixels
 * @return Buffer handle, or 0 on failure
 */
int64_t simjot_buffer_create(int32_t width, int32_t height);

/**
 * Resize an existing buffer. Preserves content if possible.
 * 
 * @param handle Buffer handle from simjot_buffer_create
 * @param width New width
 * @param height New height
 * @return 1 on success, 0 on failure
 */
int32_t simjot_buffer_resize(int64_t handle, int32_t width, int32_t height);

/**
 * Destroy a buffer and free its memory.
 * 
 * @param handle Buffer handle
 */
void simjot_buffer_destroy(int64_t handle);

/**
 * Clear the buffer with a solid color.
 * 
 * @param handle Buffer handle
 * @param argb Color in 0xAARRGGBB format
 */
void simjot_buffer_clear(int64_t handle, uint32_t argb);

/**
 * Copy pixels from Java int[] array into the buffer.
 * Used to capture current rendered state.
 * 
 * @param handle Buffer handle
 * @param pixels Source ARGB pixel array
 * @param src_width Source width
 * @param src_height Source height
 * @param dst_x Destination X offset in buffer
 * @param dst_y Destination Y offset in buffer
 * @return 1 on success, 0 on failure
 */
int32_t simjot_buffer_write(int64_t handle, const int32_t* pixels,
                            int32_t src_width, int32_t src_height,
                            int32_t dst_x, int32_t dst_y);

/**
 * Read pixels from buffer back into Java int[] array.
 * Used to blit cached content to screen.
 * 
 * @param handle Buffer handle
 * @param out_pixels Destination ARGB pixel array
 * @param src_x Source X offset in buffer
 * @param src_y Source Y offset in buffer
 * @param width Width to read
 * @param height Height to read
 * @return 1 on success, 0 on failure
 */
int32_t simjot_buffer_read(int64_t handle, int32_t* out_pixels,
                           int32_t src_x, int32_t src_y,
                           int32_t width, int32_t height);

/**
 * Scroll buffer contents by offset, filling exposed area with color.
 * Efficient for incremental scroll updates.
 * 
 * @param handle Buffer handle
 * @param dx Horizontal scroll offset (positive = right)
 * @param dy Vertical scroll offset (positive = down)
 * @param fill_argb Color for exposed areas
 */
void simjot_buffer_scroll(int64_t handle, int32_t dx, int32_t dy, uint32_t fill_argb);

/**
 * Composite (alpha-blend) source pixels onto buffer.
 * 
 * @param handle Buffer handle
 * @param pixels Source ARGB pixels with alpha
 * @param src_width Source width
 * @param src_height Source height
 * @param dst_x Destination X
 * @param dst_y Destination Y
 * @return 1 on success, 0 on failure
 */
int32_t simjot_buffer_composite(int64_t handle, const int32_t* pixels,
                                 int32_t src_width, int32_t src_height,
                                 int32_t dst_x, int32_t dst_y);

/**
 * Get buffer dimensions.
 * 
 * @param handle Buffer handle
 * @param out_width Output width (can be NULL)
 * @param out_height Output height (can be NULL)
 * @return 1 if handle valid, 0 otherwise
 */
int32_t simjot_buffer_get_size(int64_t handle, int32_t* out_width, int32_t* out_height);

/* ═══════════════════════════════════════════════════════════════════════════
 * HASKELL POETRY ANALYSIS - FFI bridge to Haskell poetry engine
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Analyze meter and return dominant foot type.
 * Returns: 0=Iamb, 1=Trochee, 2=Spondee, 3=Pyrrhic, 4=Anapest, 5=Dactyl, 6=Amphibrach
 */
int32_t hs_analyze_meter(const char* text);

/**
 * Analyze rhyme scheme and write to output buffer.
 * @return Length of scheme written
 */
int32_t hs_analyze_rhyme_scheme(const char* text, char* out_buf, int32_t out_len);

/**
 * Count syllables in a word using Haskell implementation.
 */
int32_t hs_count_syllables(const char* word);

/**
 * Analyze sound devices and return count.
 */
int32_t hs_analyze_sound_devices(const char* text);

/**
 * Get meter name and write to output buffer.
 * @return Length of name written
 */
int32_t hs_get_meter_name(const char* text, char* out_buf, int32_t out_len);

/**
 * Get meter regularity as percentage (0-100).
 */
int32_t hs_get_meter_regularity(const char* text);

/**
 * Check if two words rhyme.
 * @return 1 if they rhyme, 0 otherwise
 */
int32_t hs_check_rhyme(const char* word1, const char* word2);

/**
 * Get vocabulary stats as comma-separated string.
 * Format: "total,unique,polysyl,hapax,avglen*100"
 * @return Length written
 */
int32_t hs_get_vocab_stats(const char* text, char* out_buf, int32_t out_len);

/**
 * Get type-token ratio * 100 (vocabulary richness).
 */
int32_t hs_type_token_ratio(const char* text);

/**
 * Get rhyme key for a word.
 * @return Length written
 */
int32_t hs_get_rhyme_key(const char* word, char* out_buf, int32_t out_len);

/**
 * Estimate stress pattern as packed bits.
 * LSB = first syllable, 1 = stressed.
 */
int32_t hs_estimate_stress(const char* word);

/* ═══════════════════════════════════════════════════════════════════════════
 * LINK DETECTOR - Fast URL/link detection for text processing
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Check if text contains any URLs.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @return 1 if contains links, 0 if not
 */
int32_t simjot_link_contains(const char* text, int32_t len);

/**
 * Count number of URLs in text.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @return Number of URLs found
 */
int32_t simjot_link_count(const char* text, int32_t len);

/**
 * Find all link ranges in text.
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @param out_ranges Output buffer for [start, end] pairs (must be 2*max_ranges ints)
 * @param max_ranges Maximum number of ranges to return
 * @return Number of ranges found (may be less than max_ranges)
 */
int32_t simjot_link_find_ranges(const char* text, int32_t len,
                                 int32_t* out_ranges, int32_t max_ranges);

/**
 * Extract first URL from text, normalizing www. to https://
 * 
 * @param text UTF-8 encoded text to scan
 * @param len Length of text in bytes
 * @param out_url Output buffer for URL (null-terminated)
 * @param out_len Size of output buffer
 * @return Length of URL written (excluding null), or 0 if none found
 */
int32_t simjot_link_extract_first(const char* text, int32_t len,
                                   char* out_url, int32_t out_len);

/**
 * Normalize a URL (add https:// if starts with www.).
 * 
 * @param url Input URL
 * @param url_len Length of input URL
 * @param out_url Output buffer
 * @param out_len Size of output buffer
 * @return Length of normalized URL
 */
int32_t simjot_link_normalize(const char* url, int32_t url_len,
                               char* out_url, int32_t out_len);

/**
 * Validate if a string is a valid URL.
 * 
 * @param url URL to validate
 * @param len Length of URL
 * @return 1 if valid, 0 if invalid
 */
int32_t simjot_link_is_valid(const char* url, int32_t len);

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
int32_t simjot_link_at_position(const char* text, int32_t len, int32_t position,
                                 int32_t* out_start, int32_t* out_end);

/* ═══════════════════════════════════════════════════════════════════════════
 * CUSTOM FONT API - Stroke-based font creation and rendering
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Custom font symbols are exported with sjf_ prefix in the native library. */
#define simjot_font_create sjf_font_create
#define simjot_font_free sjf_font_free
#define simjot_font_load sjf_font_load
#define simjot_font_save sjf_font_save
#define simjot_font_get_name sjf_font_get_name
#define simjot_font_set_name sjf_font_set_name
#define simjot_font_get_author sjf_font_get_author
#define simjot_font_set_author sjf_font_set_author
#define simjot_font_glyph_count sjf_font_glyph_count
#define simjot_font_defined_glyph_count sjf_font_defined_glyph_count
#define simjot_font_get_ascender sjf_font_get_ascender
#define simjot_font_get_descender sjf_font_get_descender
#define simjot_font_get_line_height sjf_font_get_line_height
#define simjot_font_get_em_size sjf_font_get_em_size
#define simjot_font_measure_text sjf_font_measure_text
#define simjot_font_measure_char sjf_font_measure_char
#define simjot_font_get_glyph sjf_font_get_glyph
#define simjot_font_add_glyph sjf_font_add_glyph
#define simjot_glyph_add_stroke sjf_glyph_add_stroke
#define simjot_glyph_clear_strokes sjf_glyph_clear_strokes
#define simjot_glyph_compute_metrics sjf_glyph_compute_metrics
#define simjot_glyph_normalize sjf_glyph_normalize
#define simjot_glyph_get_advance sjf_glyph_get_advance
#define simjot_glyph_get_width sjf_glyph_get_width
#define simjot_glyph_get_height sjf_glyph_get_height
#define simjot_glyph_get_bounds sjf_glyph_get_bounds
#define simjot_stroke_create sjf_stroke_create
#define simjot_stroke_free sjf_stroke_free
#define simjot_stroke_add_point sjf_stroke_add_point
#define simjot_stroke_set_points sjf_stroke_set_points
#define simjot_stroke_set_thickness sjf_stroke_set_thickness
#define simjot_stroke_clear sjf_stroke_clear
#define simjot_stroke_smooth sjf_stroke_smooth
#define simjot_stroke_length sjf_stroke_length
#define simjot_stroke_get_point_count sjf_stroke_get_point_count
#define simjot_stroke_get_points sjf_stroke_get_points
#define simjot_stroke_bounds sjf_stroke_bounds
#define simjot_stroke_translate sjf_stroke_translate
#define simjot_stroke_scale sjf_stroke_scale
#define simjot_stroke_normalize sjf_stroke_normalize
#define simjot_stroke_simplify sjf_stroke_simplify
#define simjot_bitmap_create sjf_bitmap_create
#define simjot_bitmap_free sjf_bitmap_free
#define simjot_bitmap_clear sjf_bitmap_clear
#define simjot_bitmap_get_width sjf_bitmap_get_width
#define simjot_bitmap_get_height sjf_bitmap_get_height
#define simjot_bitmap_get_stride sjf_bitmap_get_stride
#define simjot_bitmap_get_pixels sjf_bitmap_get_pixels
#define simjot_bitmap_to_argb sjf_bitmap_to_argb
#define simjot_raster_glyph sjf_raster_glyph
#define simjot_raster_stroke sjf_raster_stroke
#define simjot_render_glyph_to_buffer sjf_render_glyph_to_buffer
#define simjot_atlas_create sjf_atlas_create
#define simjot_atlas_free sjf_atlas_free
#define simjot_atlas_add_glyph sjf_atlas_add_glyph
#define simjot_atlas_get_width sjf_atlas_get_width
#define simjot_atlas_get_height sjf_atlas_get_height
#define simjot_atlas_get_pixels sjf_atlas_get_pixels
#define simjot_font_pack sjf_font_pack
#define simjot_font_unpack sjf_font_unpack
#define simjot_font_import sjf_font_import

/* Opaque handles for FFM */
typedef struct sjf_font sjf_font_t;
typedef struct sjf_glyph sjf_glyph_t;
typedef struct sjf_stroke sjf_stroke_t;
typedef struct sjf_bitmap sjf_bitmap_t;
typedef struct sjf_atlas sjf_atlas_t;

/* Font creation and management */
void* simjot_font_create(const char* name, const char* author);
void simjot_font_free(void* font);
void* simjot_font_load(const char* path);
int32_t simjot_font_save(const void* font, const char* path);

/* Font metadata */
int32_t simjot_font_get_name(const void* font, char* out, int32_t out_len);
int32_t simjot_font_set_name(void* font, const char* name);
int32_t simjot_font_get_author(const void* font, char* out, int32_t out_len);
int32_t simjot_font_set_author(void* font, const char* author);
int32_t simjot_font_glyph_count(const void* font);
int32_t simjot_font_defined_glyph_count(const void* font);

/* Font metrics */
float simjot_font_get_ascender(const void* font, int32_t size);
float simjot_font_get_descender(const void* font, int32_t size);
float simjot_font_get_line_height(const void* font, int32_t size);
float simjot_font_get_em_size(const void* font);
float simjot_font_measure_text(const void* font, const char* text, int32_t size);
float simjot_font_measure_char(const void* font, uint32_t codepoint, int32_t size);

/* Glyph management */
void* simjot_font_get_glyph(void* font, uint32_t codepoint);
void* simjot_font_add_glyph(void* font, uint32_t codepoint);
int32_t simjot_glyph_add_stroke(void* glyph, const void* stroke);
void simjot_glyph_clear_strokes(void* glyph);
int32_t simjot_glyph_compute_metrics(void* glyph, float em_size);
int32_t simjot_glyph_normalize(void* glyph, float em_size, float margin);
float simjot_glyph_get_advance(const void* glyph);
float simjot_glyph_get_width(const void* glyph);
float simjot_glyph_get_height(const void* glyph);
void simjot_glyph_get_bounds(const void* glyph, float* x, float* y, float* w, float* h);

/* Stroke creation and manipulation */
void* simjot_stroke_create(int32_t initial_capacity);
void simjot_stroke_free(void* stroke);
int32_t simjot_stroke_add_point(void* stroke, float x, float y, float pressure, float timestamp);
int32_t simjot_stroke_set_points(void* stroke, const float* xs, const float* ys,
                                 const float* pressures, const float* timestamps, int32_t count);
void simjot_stroke_set_thickness(void* stroke, float thickness);
void simjot_stroke_clear(void* stroke);
int32_t simjot_stroke_smooth(void* stroke, int32_t iterations, float tension, float resample_dist, int32_t preserve_corners);
float simjot_stroke_length(const void* stroke);
int32_t simjot_stroke_get_point_count(const void* stroke);
int32_t simjot_stroke_get_points(const void* stroke, float* out, int32_t out_len);
void simjot_stroke_bounds(const void* stroke, float* min_x, float* min_y, float* max_x, float* max_y);
void simjot_stroke_translate(void* stroke, float dx, float dy);
void simjot_stroke_scale(void* stroke, float sx, float sy, float cx, float cy);
void simjot_stroke_normalize(void* stroke, float target_size);
int32_t simjot_stroke_simplify(void* stroke, float epsilon);

/* Rasterization */
void* simjot_bitmap_create(int32_t width, int32_t height);
void simjot_bitmap_free(void* bitmap);
void simjot_bitmap_clear(void* bitmap);
int32_t simjot_bitmap_get_width(const void* bitmap);
int32_t simjot_bitmap_get_height(const void* bitmap);
int32_t simjot_bitmap_get_stride(const void* bitmap);
uint8_t* simjot_bitmap_get_pixels(void* bitmap);
int32_t simjot_bitmap_to_argb(const void* bitmap, uint32_t color, uint32_t* out_argb, int32_t out_stride);

void* simjot_raster_glyph(const void* glyph, int32_t size, int32_t oversample, float gamma, float em_size);
int32_t simjot_raster_stroke(void* bitmap, const void* stroke, float scale, float offset_x, float offset_y);
int32_t simjot_render_glyph_to_buffer(const void* glyph, uint32_t* buffer, int32_t buf_width, int32_t buf_height,
                                       int32_t x, int32_t y, int32_t size, uint32_t color, float em_size);

/* Atlas building */
void* simjot_atlas_create(int32_t width, int32_t height);
void simjot_atlas_free(void* atlas);
int32_t simjot_atlas_add_glyph(void* atlas, const void* bitmap, uint32_t codepoint, float advance, int32_t* out_x, int32_t* out_y);
int32_t simjot_atlas_get_width(const void* atlas);
int32_t simjot_atlas_get_height(const void* atlas);
uint8_t* simjot_atlas_get_pixels(void* atlas);

/* Serialization */
int32_t simjot_font_pack(const void* font, uint8_t* buffer, int32_t buffer_len);
void* simjot_font_unpack(const uint8_t* buffer, int32_t buffer_len);
void* simjot_font_import(const char* path, const char* charset_utf8, float stroke_thickness,
                          int32_t* out_error, int32_t* out_total, int32_t* out_defined, int32_t* out_skipped);

/* ═══════════════════════════════════════════════════════════════════════════
 * TABLET INPUT API
 * 
 * Low-level drawing tablet detection and pressure sensitivity support.
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Opaque tablet context handle */
typedef struct tablet_context tablet_context_t;

/* Context management */
void* simjot_tablet_create_context(void);
void simjot_tablet_destroy_context(void* ctx);
int32_t simjot_tablet_initialize(void* ctx);
int32_t simjot_tablet_is_available(void);

/* Device enumeration */
int32_t simjot_tablet_get_device_count(void* ctx);
int32_t simjot_tablet_get_device_name(void* ctx, int32_t index, char* out, int32_t out_len);
int32_t simjot_tablet_get_device_vendor(void* ctx, int32_t index);
int32_t simjot_tablet_get_device_type(void* ctx, int32_t index);
int32_t simjot_tablet_get_pressure_levels(void* ctx, int32_t index);
int32_t simjot_tablet_has_tilt(void* ctx, int32_t index);
int32_t simjot_tablet_refresh_devices(void* ctx);

/* Current state queries */
float simjot_tablet_get_pressure(void* ctx);
int32_t simjot_tablet_get_tilt(void* ctx, float* tilt_x, float* tilt_y);
float simjot_tablet_get_rotation(void* ctx);
int32_t simjot_tablet_is_stylus_in_range(void* ctx);
int32_t simjot_tablet_is_eraser_active(void* ctx);
int32_t simjot_tablet_is_touching(void* ctx);
uint32_t simjot_tablet_get_buttons(void* ctx);

/* Pressure curve utilities */
float simjot_tablet_apply_pressure_curve(float pressure, float gamma);
float simjot_tablet_apply_pressure_bezier(float pressure, float p1, float p2);
float simjot_tablet_smooth_pressure(float current, float previous, float smoothing);

/* ═══════════════════════════════════════════════════════════════════════════
 * NOTETAKING UTILITIES API
 * 
 * High-performance utilities for notetaking: stroke smoothing, text formatting,
 * color manipulation, and math symbol handling.
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Stroke smoothing - Catmull-Rom splines */
int32_t simjot_stroke_smooth_catmull(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t output_capacity,
    int32_t smoothing_factor
);

/* Stroke smoothing - Bezier curves */
int32_t simjot_stroke_smooth_bezier(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t output_capacity,
    int32_t smoothing_factor
);

/* Stroke smoothing - Moving average */
int32_t simjot_stroke_smooth_average(
    const float* input_x, const float* input_y, const float* input_pressure,
    int32_t input_count,
    float* output_x, float* output_y, float* output_pressure,
    int32_t window_size
);

/* Stroke simplification - Douglas-Peucker algorithm */
int32_t simjot_nt_stroke_simplify(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float epsilon
);

/* Pressure processing */
int32_t simjot_pressure_smooth(const float* input, int32_t count, float* output, float alpha);
float simjot_pressure_to_width(float pressure, float min_width, float max_width, float curve);

/* Color utilities */
int32_t simjot_color_blend(int32_t color1, int32_t color2, float t);
int32_t simjot_color_highlight(int32_t base_color, float intensity);
int32_t simjot_color_brightness(int32_t color, float factor);
int32_t simjot_hsl_to_rgb(float h, float s, float l, int32_t alpha);

/* Math formatting */
int32_t simjot_math_format_simple(const char* input, char* output, int32_t output_len);
int32_t simjot_math_greek_letter(const char* name, char* output, int32_t output_len);
int32_t simjot_math_operator(const char* name, char* output, int32_t output_len);

/* Stroke geometry (array-based, for notetaking) */
int32_t simjot_nt_stroke_bounds(
    const float* x, const float* y, int32_t count,
    float* out_min_x, float* out_min_y, float* out_max_x, float* out_max_y
);
float simjot_nt_stroke_length(const float* x, const float* y, int32_t count);
int32_t simjot_nt_stroke_outline(
    const float* x, const float* y, const float* width,
    int32_t count,
    float* left_x, float* left_y,
    float* right_x, float* right_y
);

/* ═══════════════════════════════════════════════════════════════════════════
 * REAL-TIME STROKE SMOOTHING (Optimized for iPad/tablet input)
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Chaikin's corner-cutting algorithm - fast subdivision smoothing */
int32_t simjot_stroke_chaikin(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    int32_t iterations
);

/* Velocity-based adaptive smoothing */
int32_t simjot_stroke_velocity_smooth(
    const float* x, const float* y, const float* timestamps,
    int32_t count,
    float* smoothed_x, float* smoothed_y,
    float velocity_threshold, float max_smooth_factor
);

/* Gap interpolation for fast strokes */
int32_t simjot_stroke_interpolate_gaps(
    const float* input_x, const float* input_y,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float max_gap
);

/* Combined real-time processing pipeline (recommended for tablet input) */
int32_t simjot_stroke_process_realtime(
    const float* input_x, const float* input_y, const float* timestamps,
    int32_t input_count,
    float* output_x, float* output_y,
    int32_t output_capacity,
    float max_gap,
    int32_t chaikin_iterations
);

/* Bezier control points for smooth rendering */
int32_t simjot_stroke_bezier_control_points(
    const float* x, const float* y,
    int32_t count,
    float* control_points,
    int32_t control_capacity
);

/* One-Euro filter - low-latency adaptive smoothing for real-time input */
int32_t simjot_stroke_one_euro_init(void** filter_out, float min_cutoff, float beta, float d_cutoff);
int32_t simjot_stroke_one_euro_filter(void* filter, float x, float y, float t, float* out_x, float* out_y);
void simjot_stroke_one_euro_free(void* filter);
int32_t simjot_stroke_one_euro_batch(
    const float* input_x, const float* input_y, const float* timestamps,
    int32_t count,
    float* output_x, float* output_y,
    float min_cutoff, float beta
);

/* ═══════════════════════════════════════════════════════════════════════════
 * NATIVE STROKE RENDERING - Direct pixel buffer rendering for maximum performance
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Render a single stroke to an ARGB pixel buffer with anti-aliasing */
int32_t simjot_draw_stroke(uint32_t* pixels, int32_t width, int32_t height,
                            const float* points_x, const float* points_y,
                            int32_t point_count, float thickness, uint32_t argb_color,
                            float offset_x, float offset_y);

/* Render multiple strokes in batch (more efficient than individual calls) */
int32_t simjot_draw_strokes_batch(uint32_t* pixels, int32_t width, int32_t height,
                                   const float* all_points_x, const float* all_points_y,
                                   const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                   const float* thicknesses, const uint32_t* colors,
                                   int32_t stroke_count,
                                   float offset_x, float offset_y);

/* ═══════════════════════════════════════════════════════════════════════════
 * ERASER HIT TESTING - Fast point-to-segment distance for stroke erasure
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Check if eraser hits any stroke, returns first hit stroke index or -1 */
int32_t simjot_eraser_hit_test(float px, float py, float radius_sq,
                                const float* all_points_x, const float* all_points_y,
                                const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                int32_t stroke_count);

/* Batch hit test - returns bitmask of hit strokes (up to 64) */
int64_t simjot_eraser_hit_test_batch(float px, float py, float radius_sq,
                                      const float* all_points_x, const float* all_points_y,
                                      const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                      int32_t stroke_count);

/* Hit test for unlimited strokes - writes hit indices to array */
int32_t simjot_eraser_hit_test_array(float px, float py, float radius_sq,
                                      const float* all_points_x, const float* all_points_y,
                                      const int32_t* stroke_starts, const int32_t* stroke_lengths,
                                      int32_t stroke_count,
                                      int32_t* hit_indices, int32_t hit_capacity);

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE GEOMETRY UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Calculate bounding box of a stroke */
int32_t simjot_stroke_calc_bounds(const float* points_x, const float* points_y, int32_t count,
                                   float* out_min_x, float* out_min_y,
                                   float* out_max_x, float* out_max_y);

/* Calculate total arc length of a stroke */
float simjot_stroke_calc_length(const float* points_x, const float* points_y, int32_t count);

/* Simplify stroke using Ramer-Douglas-Peucker algorithm */
int32_t simjot_stroke_simplify_rdp(const float* in_x, const float* in_y, int32_t in_count,
                                    float* out_x, float* out_y, int32_t out_capacity,
                                    float epsilon);

/* Convert int point arrays to float */
void simjot_points_int_to_float(const int32_t* src_x, const int32_t* src_y, int32_t count,
                                 float* dst_x, float* dst_y);

/* Clear rectangular region of pixel buffer */
void simjot_buffer_clear_rect(uint32_t* pixels, int32_t width, int32_t height,
                               int32_t x, int32_t y, int32_t w, int32_t h, uint32_t color);

/* ═══════════════════════════════════════════════════════════════════════════
 * REAL-TIME STROKE SMOOTHING - Lightweight filters for live input
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Smooth single point using EMA - call per incoming point */
void simjot_smooth_point_ema(float new_x, float new_y,
                              float prev_x, float prev_y,
                              float* out_x, float* out_y,
                              float alpha);

/* Smooth entire stroke in-place using bidirectional EMA */
int32_t simjot_smooth_stroke_ema(float* points_x, float* points_y,
                                  int32_t count, float alpha);

/* Smooth stroke with velocity-adaptive alpha */
int32_t simjot_smooth_stroke_adaptive(float* points_x, float* points_y,
                                       int32_t count, float base_alpha,
                                       float velocity_scale);

/* ═══════════════════════════════════════════════════════════════════════════
 * PROFESSIONAL STROKE ENGINE - Notability/Xournal++ quality rendering
 * Uses simjot_draw_* prefix to avoid conflict with font studio stroke API
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Stroke Manager - handles multiple strokes with caching */
int64_t simjot_draw_manager_create(int32_t docWidth, int32_t docHeight);
void simjot_draw_manager_destroy(int64_t handle);

/* Live stroke operations */
int32_t simjot_draw_stroke_begin(int64_t handle, float x, float y, int64_t timestamp,
                                  float baseThickness, uint32_t color, int32_t usePressure);
int32_t simjot_draw_stroke_add_point(int64_t handle, int32_t strokeIdx,
                                      float x, float y, int64_t timestamp);
int32_t simjot_draw_stroke_end(int64_t handle, int32_t strokeIdx, int32_t applySmoothing);
int32_t simjot_draw_stroke_remove(int64_t handle, int32_t strokeIdx);
int32_t simjot_draw_stroke_count(int64_t handle);
void simjot_draw_stroke_clear_all(int64_t handle);

/* Stroke rendering with variable thickness */
int32_t simjot_draw_render_all(int64_t handle, uint32_t* pixels, 
                                int32_t width, int32_t height,
                                float offsetX, float offsetY);
int32_t simjot_draw_render_one(int64_t handle, int32_t strokeIdx,
                                uint32_t* pixels, int32_t width, int32_t height,
                                float offsetX, float offsetY);

/* Standalone stroke processing */
int32_t simjot_draw_interpolate_catmull_rom(
    const float* in_x, const float* in_y, const float* in_pressure, int32_t in_count,
    float* out_x, float* out_y, float* out_pressure, int32_t out_capacity,
    int32_t subdivisions);

int32_t simjot_draw_smooth_chaikin(
    const float* in_x, const float* in_y, int32_t in_count,
    float* out_x, float* out_y, int32_t out_capacity,
    int32_t iterations);

int32_t simjot_draw_compute_pressure(
    const float* x, const float* y, const int64_t* timestamps, int32_t count,
    float* pressure);

int32_t simjot_draw_render_variable(
    uint32_t* pixels, int32_t width, int32_t height,
    const float* points_x, const float* points_y, const float* thicknesses,
    int32_t point_count, uint32_t argb_color,
    float offset_x, float offset_y);

int32_t simjot_draw_distance_sample(
    const float* in_x, const float* in_y, int32_t in_count,
    float* out_x, float* out_y, int32_t out_capacity,
    float min_distance);

/* ═══════════════════════════════════════════════════════════════════════════
 * LASSO SELECTOR - Selection and movement of strokes
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Point-in-polygon test (ray casting) */
int32_t simjot_lasso_point_in_polygon(float px, float py,
                                       const float* poly_x, const float* poly_y, int32_t poly_count);

/* Test if stroke intersects lasso. mode: 0=any, 1=all, 2=centroid */
int32_t simjot_lasso_test_stroke(const float* stroke_x, const float* stroke_y, int32_t stroke_count,
                                  const float* lasso_x, const float* lasso_y, int32_t lasso_count,
                                  int32_t mode);

/* Batch test multiple strokes against lasso */
int32_t simjot_lasso_test_strokes_batch(const float* all_x, const float* all_y,
                                         const int32_t* stroke_starts, const int32_t* stroke_counts,
                                         int32_t num_strokes,
                                         const float* lasso_x, const float* lasso_y, int32_t lasso_count,
                                         int32_t mode, int32_t* results);

/* Compute bounding box: bounds[0]=minX, [1]=minY, [2]=maxX, [3]=maxY */
int32_t simjot_lasso_compute_bounds(const float* x, const float* y, int32_t count, float* bounds);

/* Compute bounds of selected strokes */
int32_t simjot_lasso_compute_selection_bounds(const float* all_x, const float* all_y,
                                               const int32_t* stroke_starts, const int32_t* stroke_counts,
                                               const int32_t* selected, int32_t num_strokes,
                                               float* bounds);

/* Translate points by delta */
int32_t simjot_lasso_translate_points(float* x, float* y, int32_t count, float dx, float dy);

/* Simplify lasso polygon (Douglas-Peucker) */
int32_t simjot_lasso_simplify(const float* in_x, const float* in_y, int32_t in_count,
                               float* out_x, float* out_y, int32_t out_capacity,
                               float epsilon);

/* ═══════════════════════════════════════════════════════════════════════════
 * STROKE OPTIMIZER - Quadtree spatial index, dirty-region tracking, batching
 * For efficient handling of large stroke collections
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Create/destroy optimizer */
int64_t simjot_optimizer_create(float worldWidth, float worldHeight);
void simjot_optimizer_destroy(int64_t handle);

/* Stroke management */
int32_t simjot_optimizer_add_stroke(int64_t handle,
                                     const float* x, const float* y, int32_t count,
                                     const float* thicknesses, uint32_t color, float baseThickness);
void simjot_optimizer_remove_stroke(int64_t handle, int32_t strokeId);
void simjot_optimizer_move_stroke(int64_t handle, int32_t strokeId, float dx, float dy);

/* Spatial queries */
int32_t simjot_optimizer_query_visible(int64_t handle,
                                        float viewX, float viewY, float viewW, float viewH,
                                        int32_t* outIds, int32_t capacity);
int32_t simjot_optimizer_query_point(int64_t handle,
                                      float x, float y, float radius,
                                      int32_t* outIds, int32_t capacity);

/* Stroke data access */
int32_t simjot_optimizer_get_stroke(int64_t handle, int32_t strokeId,
                                     float* outX, float* outY, float* outThick,
                                     int32_t capacity, uint32_t* outColor);
int32_t simjot_optimizer_get_stroke_bounds(int64_t handle, int32_t strokeId, float* bounds);
int32_t simjot_optimizer_stroke_count(int64_t handle);

/* LOD and dirty tracking */
void simjot_optimizer_set_zoom(int64_t handle, float zoom);
int32_t simjot_optimizer_needs_repaint(int64_t handle,
                                        float viewX, float viewY, float viewW, float viewH);
void simjot_optimizer_clear_dirty(int64_t handle);
void simjot_optimizer_mark_full_repaint(int64_t handle);
void simjot_optimizer_rebuild(int64_t handle);

/* Optimized rendering */
int32_t simjot_optimizer_render_visible(int64_t handle,
                                         uint32_t* pixels, int32_t width, int32_t height,
                                         float viewX, float viewY, float viewW, float viewH);

/* ═══════════════════════════════════════════════════════════════════════════
 * ICLOUD SYNC - Advanced cloud synchronization (macOS)
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Sync initialization and network state */
int32_t simjot_sync_init(void);
void simjot_sync_shutdown(void);
int32_t simjot_sync_get_network_state(void);
int32_t simjot_sync_is_connected(void);
void simjot_sync_set_enabled(int32_t enabled);
int32_t simjot_sync_is_enabled(void);
void simjot_sync_set_low_power(int32_t lowPower);

/* Hash computation and caching */
int32_t simjot_sync_compute_hash(const char* path, uint8_t* out32);
int32_t simjot_sync_files_identical(const char* path1, const char* path2);
void simjot_sync_invalidate_hash(const char* path);
void simjot_sync_clear_hash_cache(void);

/* Delta tracking */
int32_t simjot_sync_delta_scan(const char* root_path, int64_t since_ms);
int32_t simjot_sync_delta_count(void);
int32_t simjot_sync_delta_get(int32_t index, char* path_out, int32_t path_len,
                               int64_t* mod_time, int64_t* size, int32_t* change_type);
void simjot_sync_delta_clear(void);

/* Batch queue management */
int32_t simjot_sync_batch_add(const char* path, int32_t operation, int32_t priority);
int32_t simjot_sync_batch_count(void);
int32_t simjot_sync_batch_peek(int32_t index, char* path_out, int32_t path_len,
                                int32_t* operation, int32_t* priority);
void simjot_sync_batch_clear(void);
void simjot_sync_batch_sort_priority(void);
int32_t simjot_sync_batch_execute(int32_t max_items, int32_t timeout_ms,
                                   int32_t* out_succeeded, int32_t* out_failed);
int32_t simjot_sync_batch_in_progress(void);

/* Throttling */
int32_t simjot_sync_should_throttle(void);
void simjot_sync_record_operation(void);
int32_t simjot_sync_get_throttle_delay_ms(void);

/* Conflict detection and resolution */
int32_t simjot_conflict_scan(const char* root_path, int32_t max_conflicts);
int32_t simjot_conflict_count(void);
int32_t simjot_conflict_get_path(int32_t index, char* path_out, int32_t path_len);
int32_t simjot_conflict_version_count(const char* path);
int32_t simjot_conflict_get_version(const char* path, int32_t version_index,
                                     char* version_path, int32_t path_len,
                                     int64_t* mod_time, int64_t* size);
int32_t simjot_conflict_resolve_keep_local(const char* path);
int32_t simjot_conflict_resolve_keep_cloud(const char* path, int32_t version_index);
int32_t simjot_conflict_resolve_keep_both(const char* path, const char* renamed_suffix);
void simjot_conflict_clear_resolved(void);
void simjot_conflict_clear_all(void);

/* Progress tracking */
void simjot_sync_progress_reset(void);
void simjot_sync_progress_start(int32_t total_files, int64_t total_bytes);
void simjot_sync_progress_update(int32_t processed_files, int64_t processed_bytes);
void simjot_sync_progress_increment(int64_t bytes);
void simjot_sync_progress_fail(void);
void simjot_sync_progress_skip(void);
void simjot_sync_progress_complete(int32_t success);
void simjot_sync_progress_set_batch(int32_t batch_size, int32_t batch_progress);
int32_t simjot_sync_progress_get_state(void);
float simjot_sync_progress_get_percent(void);
float simjot_sync_progress_get_bytes_percent(void);
int32_t simjot_sync_progress_get_stats(int32_t* total, int32_t* processed,
                                        int32_t* failed, int32_t* skipped);
int64_t simjot_sync_progress_get_elapsed_ms(void);
int64_t simjot_sync_progress_estimate_remaining_ms(void);
int64_t simjot_sync_progress_get_speed_bps(void);

/* Performance metrics */
void simjot_sync_metrics_reset(void);
void simjot_sync_metrics_record_upload(int64_t bytes, int64_t latency_ms);
void simjot_sync_metrics_record_download(int64_t bytes, int64_t latency_ms);
void simjot_sync_metrics_record_conflict(void);
void simjot_sync_metrics_record_retry(void);
int32_t simjot_sync_metrics_get_summary(uint8_t* out, int32_t out_len);
int64_t simjot_sync_metrics_get_avg_latency(void);
int64_t simjot_sync_metrics_get_p95_latency(void);
float simjot_sync_metrics_get_success_rate(void);
int64_t simjot_sync_metrics_total_bytes_synced(void);

/* Retry with exponential backoff */
int64_t simjot_sync_get_retry_delay_ms(int32_t attempt);
int32_t simjot_sync_should_retry(int32_t current_attempt, int32_t error_code);
void simjot_sync_record_retry_attempt(void);
int32_t simjot_sync_get_retry_count(void);
void simjot_sync_reset_retry_count(void);

/* File system watcher */
int32_t simjot_watcher_start(const char* path);
int32_t simjot_watcher_stop(int32_t watcher_id);
void simjot_watcher_stop_all(void);
int32_t simjot_watcher_active_count(void);
int32_t simjot_watcher_event_count(void);
int32_t simjot_watcher_pop_event(char* path_out, int32_t path_len,
                                  int64_t* timestamp, int32_t* event_type);
int32_t simjot_watcher_drain_events(int32_t max_events,
                                     char* paths_out, int32_t paths_len);
void simjot_watcher_clear_events(void);

/* Background sync scheduler */
int32_t simjot_scheduler_add(const char* root_path, int64_t interval_ms, int32_t priority);
int32_t simjot_scheduler_remove(int32_t entry_id);
int32_t simjot_scheduler_get_next_due(char* path_out, int32_t path_len,
                                       int64_t* next_run_ms);
void simjot_scheduler_mark_completed(int32_t entry_id);
int32_t simjot_scheduler_entry_count(void);
void simjot_scheduler_clear(void);

/* iCloud container discovery and status */
int32_t simjot_icloud_discover_containers(char* out, int32_t out_len);
int32_t simjot_icloud_get_quota(int64_t* total_bytes, int64_t* available_bytes);
int32_t simjot_icloud_account_status(void);

/* ═══════════════════════════════════════════════════════════════════════════
 * MENU BAR SERVICE - Quick Entry Panel (macOS)
 * ═══════════════════════════════════════════════════════════════════════════ */

/* Callback type for quick entry submission */
typedef void (*simjot_quick_entry_callback)(const char* text, int32_t format_flags);

/* Format flags for quick entry */
#define SIMJOT_FORMAT_BOLD      (1 << 0)
#define SIMJOT_FORMAT_ITALIC    (1 << 1)
#define SIMJOT_FORMAT_UNDERLINE (1 << 2)
#define SIMJOT_FORMAT_BULLET    (1 << 3)
#define SIMJOT_FORMAT_HEADER    (1 << 4)

/* Initialize the menu bar status item with quick entry panel */
int32_t simjot_menubar_init(void);

/* Shutdown and remove the menu bar status item */
void simjot_menubar_shutdown(void);

/* Check if menu bar service is initialized */
int32_t simjot_menubar_is_initialized(void);

/* Show the quick entry panel */
void simjot_menubar_show_panel(void);

/* Hide the quick entry panel */
void simjot_menubar_hide_panel(void);

/* Check if quick entry panel is visible */
int32_t simjot_menubar_is_panel_visible(void);

/* Set callback for when entry is submitted */
void simjot_menubar_set_callback(simjot_quick_entry_callback callback);

/* Set the status item icon (SF Symbol name) */
void simjot_menubar_set_icon(const char* symbol_name);

/* Set the status item tooltip */
void simjot_menubar_set_tooltip(const char* tooltip);

/* Set a badge count on the status item (0 to hide) */
void simjot_menubar_set_badge(int32_t count);

/* Get current text from the quick entry panel */
int32_t simjot_menubar_get_panel_text(char* buffer, int32_t buffer_size);

/* Set text in the quick entry panel */
void simjot_menubar_set_panel_text(const char* text);

/* Clear the quick entry panel */
void simjot_menubar_clear_panel(void);

#ifdef __cplusplus
}
#endif

#endif
