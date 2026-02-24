/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file search_batch.c
 * @brief Native batch search for global entry search optimization
 * 
 * Provides high-performance batch searching across multiple directories,
 * reading and parsing entry files in parallel where possible.
 * 
 * @author S1mplector
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include <dirent.h>
#include <sys/stat.h>
#include <pthread.h>

/* ═══════════════════════════════════════════════════════════════════════════
 * SEARCH RESULT STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_TITLE_LEN 256
#define MAX_SNIPPET_LEN 512
#define MAX_TAGS 32
#define MAX_TAG_LEN 64
#define MAX_PATH_LEN 4096
#define MAX_RESULTS 1000
#define MAX_DIRS 100
#define READ_BUFFER_SIZE (256 * 1024)  /* 256KB per file max */

typedef struct SearchResult {
    char file_path[MAX_PATH_LEN];
    char title[MAX_TITLE_LEN];
    char snippet[MAX_SNIPPET_LEN];
    int32_t mood;
    int64_t saved_at;
    int32_t match_count;
    char tags[MAX_TAGS][MAX_TAG_LEN];
    int32_t tag_count;
} SearchResult;

typedef struct SearchContext {
    const char* query;
    int32_t query_len;
    const char** directories;
    int32_t dir_count;
    const char* extensions;  /* comma-separated: ".note,.txt,.ntk,.poem,.rtf" */
    int32_t case_sensitive;
    int32_t fuzzy;
    SearchResult* results;
    int32_t result_count;
    int32_t max_results;
    pthread_mutex_t mutex;
} SearchContext;

/* ═══════════════════════════════════════════════════════════════════════════
 * STRING UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

static inline int to_lower(int c) {
    return (c >= 'A' && c <= 'Z') ? c + 32 : c;
}

static int str_contains_ci(const char* haystack, const char* needle, int needle_len) {
    if (!haystack || !needle || needle_len <= 0) return 0;
    
    for (const char* p = haystack; *p; p++) {
        const char* h = p;
        const char* n = needle;
        int matched = 1;
        
        for (int i = 0; i < needle_len && *h; i++, h++, n++) {
            if (to_lower(*h) != to_lower(*n)) {
                matched = 0;
                break;
            }
        }
        
        if (matched && n == needle + needle_len) return 1;
    }
    return 0;
}

static int str_find_ci(const char* haystack, const char* needle, int needle_len) {
    if (!haystack || !needle || needle_len <= 0) return -1;
    
    int pos = 0;
    for (const char* p = haystack; *p; p++, pos++) {
        const char* h = p;
        const char* n = needle;
        int matched = 1;
        
        for (int i = 0; i < needle_len && *h; i++, h++, n++) {
            if (to_lower(*h) != to_lower(*n)) {
                matched = 0;
                break;
            }
        }
        
        if (matched && n == needle + needle_len) return pos;
    }
    return -1;
}

static int count_matches_ci(const char* text, const char* needle, int needle_len) {
    if (!text || !needle || needle_len <= 0) return 0;
    
    int count = 0;
    const char* p = text;
    
    while (*p) {
        int idx = str_find_ci(p, needle, needle_len);
        if (idx < 0) break;
        count++;
        p += idx + needle_len;
    }
    
    return count;
}

static int has_extension(const char* filename, const char* extensions) {
    if (!filename || !extensions) return 1;  /* No filter = match all */
    
    const char* dot = strrchr(filename, '.');
    if (!dot) return 0;
    
    /* Parse comma-separated extensions */
    char ext_buf[512];
    strncpy(ext_buf, extensions, sizeof(ext_buf) - 1);
    ext_buf[sizeof(ext_buf) - 1] = '\0';
    
    char* token = strtok(ext_buf, ",");
    while (token) {
        while (*token == ' ') token++;
        if (strcasecmp(dot, token) == 0) return 1;
        token = strtok(NULL, ",");
    }
    
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ENTRY PARSING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Parse entry header to extract metadata.
 * Header format: {"title":"...","mood":N,"savedAt":N,...}
 * Or for poems: first line is title
 */
static void parse_entry_header(const char* content, const char* filename,
                                char* title, int32_t* mood, int64_t* saved_at) {
    title[0] = '\0';
    *mood = -1;
    *saved_at = 0;
    
    if (!content || !*content) return;
    
    /* Check if it's a JSON header */
    if (content[0] == '{') {
        /* Extract title */
        const char* title_key = "\"title\":\"";
        const char* title_start = strstr(content, title_key);
        if (title_start) {
            title_start += strlen(title_key);
            const char* title_end = strchr(title_start, '"');
            if (title_end) {
                int len = (int)(title_end - title_start);
                if (len > MAX_TITLE_LEN - 1) len = MAX_TITLE_LEN - 1;
                strncpy(title, title_start, len);
                title[len] = '\0';
            }
        }
        
        /* Extract mood */
        const char* mood_key = "\"mood\":";
        const char* mood_start = strstr(content, mood_key);
        if (mood_start) {
            mood_start += strlen(mood_key);
            *mood = atoi(mood_start);
        }
        
        /* Extract savedAt */
        const char* saved_key = "\"savedAt\":";
        const char* saved_start = strstr(content, saved_key);
        if (saved_start) {
            saved_start += strlen(saved_key);
            *saved_at = atoll(saved_start);
        }
    } else {
        /* Plain text - first line is title */
        const char* newline = strchr(content, '\n');
        if (newline) {
            int len = (int)(newline - content);
            if (len > MAX_TITLE_LEN - 1) len = MAX_TITLE_LEN - 1;
            strncpy(title, content, len);
            title[len] = '\0';
        } else {
            strncpy(title, content, MAX_TITLE_LEN - 1);
            title[MAX_TITLE_LEN - 1] = '\0';
        }
    }
    
    /* Fallback title from filename */
    if (title[0] == '\0' && filename) {
        const char* base = strrchr(filename, '/');
        if (base) base++;
        else base = filename;
        
        strncpy(title, base, MAX_TITLE_LEN - 1);
        title[MAX_TITLE_LEN - 1] = '\0';
        
        /* Remove extension */
        char* dot = strrchr(title, '.');
        if (dot) *dot = '\0';
    }
}

/**
 * Extract #tags from text
 */
static int extract_tags(const char* text, char tags[][MAX_TAG_LEN], int max_tags) {
    if (!text) return 0;
    
    int count = 0;
    const char* p = text;
    
    while (*p && count < max_tags) {
        if (*p == '#') {
            p++;
            if (isalnum(*p)) {
                char* tag = tags[count];
                int len = 0;
                
                while (len < MAX_TAG_LEN - 1 && (isalnum(*p) || *p == '_' || *p == '-')) {
                    tag[len++] = *p++;
                }
                tag[len] = '\0';
                
                if (len > 0) count++;
            }
        } else {
            p++;
        }
    }
    
    return count;
}

/**
 * Build a snippet around the first match
 */
static void build_snippet(const char* text, const char* query, int query_len,
                          char* snippet, int max_len) {
    snippet[0] = '\0';
    if (!text || !query || query_len <= 0) return;
    
    /* Skip header (first line if JSON) */
    const char* body = text;
    if (text[0] == '{') {
        const char* nl = strchr(text, '\n');
        if (nl) body = nl + 1;
        /* Skip blank line after header */
        while (*body == '\n' || *body == '\r') body++;
    }
    
    int match_pos = str_find_ci(body, query, query_len);
    if (match_pos < 0) {
        /* No match in body, just take beginning */
        int len = strlen(body);
        if (len > max_len - 1) len = max_len - 1;
        strncpy(snippet, body, len);
        snippet[len] = '\0';
        return;
    }
    
    /* Build snippet around match */
    int context = (max_len - query_len) / 2 - 3;  /* Room for "..." */
    if (context < 20) context = 20;
    
    int start = match_pos - context;
    if (start < 0) start = 0;
    
    int end = match_pos + query_len + context;
    int body_len = strlen(body);
    if (end > body_len) end = body_len;
    
    char* out = snippet;
    if (start > 0) {
        strcpy(out, "...");
        out += 3;
    }
    
    int copy_len = end - start;
    if (copy_len > max_len - 7) copy_len = max_len - 7;  /* Room for "..." on both ends */
    
    strncpy(out, body + start, copy_len);
    out += copy_len;
    
    if (end < body_len) {
        strcpy(out, "...");
        out += 3;
    }
    
    *out = '\0';
    
    /* Replace newlines with spaces */
    for (char* c = snippet; *c; c++) {
        if (*c == '\n' || *c == '\r') *c = ' ';
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BATCH SEARCH IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static void search_file(SearchContext* ctx, const char* dir_path, const char* filename) {
    /* Build full path */
    char path[MAX_PATH_LEN];
    snprintf(path, sizeof(path), "%s/%s", dir_path, filename);
    
    /* Read file content */
    FILE* f = fopen(path, "rb");
    if (!f) return;
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (size <= 0 || size > READ_BUFFER_SIZE) {
        fclose(f);
        return;
    }
    
    char* content = (char*)malloc(size + 1);
    if (!content) {
        fclose(f);
        return;
    }
    
    size_t read = fread(content, 1, size, f);
    fclose(f);
    content[read] = '\0';
    
    /* Parse header */
    char title[MAX_TITLE_LEN];
    int32_t mood;
    int64_t saved_at;
    parse_entry_header(content, filename, title, &mood, &saved_at);
    
    /* Check if query matches */
    int match_count = 0;
    int title_match = str_contains_ci(title, ctx->query, ctx->query_len);
    int content_match = str_contains_ci(content, ctx->query, ctx->query_len);
    
    if (!title_match && !content_match) {
        free(content);
        return;
    }
    
    match_count = count_matches_ci(content, ctx->query, ctx->query_len);
    
    /* Add result */
    pthread_mutex_lock(&ctx->mutex);
    
    if (ctx->result_count < ctx->max_results) {
        SearchResult* res = &ctx->results[ctx->result_count];
        
        strncpy(res->file_path, path, MAX_PATH_LEN - 1);
        res->file_path[MAX_PATH_LEN - 1] = '\0';
        
        strncpy(res->title, title, MAX_TITLE_LEN - 1);
        res->title[MAX_TITLE_LEN - 1] = '\0';
        
        res->mood = mood;
        res->saved_at = saved_at;
        res->match_count = match_count;
        
        /* Build snippet */
        build_snippet(content, ctx->query, ctx->query_len, res->snippet, MAX_SNIPPET_LEN);
        
        /* Extract tags */
        res->tag_count = extract_tags(content, res->tags, MAX_TAGS);
        
        ctx->result_count++;
    }
    
    pthread_mutex_unlock(&ctx->mutex);
    
    free(content);
}

static void search_directory(SearchContext* ctx, const char* dir_path) {
    DIR* dir = opendir(dir_path);
    if (!dir) return;
    
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        /* Skip . and .. */
        if (entry->d_name[0] == '.' && 
            (entry->d_name[1] == '\0' || 
             (entry->d_name[1] == '.' && entry->d_name[2] == '\0'))) {
            continue;
        }
        
        /* Check extension filter */
        if (!has_extension(entry->d_name, ctx->extensions)) {
            continue;
        }
        
        /* Check if file (not directory) */
        char path[MAX_PATH_LEN];
        snprintf(path, sizeof(path), "%s/%s", dir_path, entry->d_name);
        
        struct stat st;
        if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
            continue;
        }
        
        /* Search file */
        search_file(ctx, dir_path, entry->d_name);
        
        /* Check if we've hit max results */
        if (ctx->result_count >= ctx->max_results) break;
    }
    
    closedir(dir);
}

/* Thread work structure for parallel search */
typedef struct ThreadWork {
    SearchContext* ctx;
    const char* dir_path;
} ThreadWork;

static void* search_thread_func(void* arg) {
    ThreadWork* work = (ThreadWork*)arg;
    search_directory(work->ctx, work->dir_path);
    return NULL;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Batch search across multiple directories
 * 
 * @param query Search query string
 * @param dirs Newline-separated list of directory paths
 * @param extensions Comma-separated extensions (e.g., ".note,.txt")
 * @param max_results Maximum number of results to return
 * @param output Output buffer for results (binary format)
 * @param output_len Size of output buffer
 * @return Number of results found, or -1 on error
 * 
 * Output format (per result):
 *   int32: path_len
 *   chars: file_path (path_len bytes)
 *   int32: title_len
 *   chars: title (title_len bytes)
 *   int32: snippet_len
 *   chars: snippet (snippet_len bytes)
 *   int32: mood
 *   int64: saved_at
 *   int32: match_count
 *   int32: tag_count
 *   [for each tag: int32 len, chars data]
 */
int32_t simjot_search_batch(const char* query, const char* dirs, const char* extensions,
                             int32_t max_results, uint8_t* output, int32_t output_len) {
    if (!query || !dirs || !output || output_len <= 0) return -1;
    if (max_results <= 0) max_results = MAX_RESULTS;
    if (max_results > MAX_RESULTS) max_results = MAX_RESULTS;
    
    int query_len = strlen(query);
    if (query_len == 0) return 0;
    
    /* Parse directories */
    char dir_buf[MAX_PATH_LEN * MAX_DIRS];
    strncpy(dir_buf, dirs, sizeof(dir_buf) - 1);
    dir_buf[sizeof(dir_buf) - 1] = '\0';
    
    const char* dir_list[MAX_DIRS];
    int dir_count = 0;
    
    char* line = strtok(dir_buf, "\n");
    while (line && dir_count < MAX_DIRS) {
        while (*line == ' ') line++;
        if (*line && *line != '\n') {
            dir_list[dir_count++] = line;
        }
        line = strtok(NULL, "\n");
    }
    
    if (dir_count == 0) return 0;
    
    /* Initialize context */
    SearchContext ctx;
    ctx.query = query;
    ctx.query_len = query_len;
    ctx.directories = dir_list;
    ctx.dir_count = dir_count;
    ctx.extensions = extensions;
    ctx.case_sensitive = 0;
    ctx.fuzzy = 0;
    ctx.max_results = max_results;
    ctx.result_count = 0;
    ctx.results = (SearchResult*)calloc(max_results, sizeof(SearchResult));
    
    if (!ctx.results) return -1;
    
    pthread_mutex_init(&ctx.mutex, NULL);
    
    /* Search each directory (parallel for multiple dirs) */
    if (dir_count > 1 && dir_count <= 8) {
        /* Parallel search */
        pthread_t threads[8];
        ThreadWork work[8];
        int thread_count = 0;
        
        for (int i = 0; i < dir_count && i < 8; i++) {
            work[i].ctx = &ctx;
            work[i].dir_path = dir_list[i];
            
            if (pthread_create(&threads[i], NULL, search_thread_func, &work[i]) == 0) {
                thread_count++;
            } else {
                /* Fallback to sequential if thread creation fails */
                search_directory(&ctx, dir_list[i]);
            }
        }
        
        /* Wait for threads */
        for (int i = 0; i < thread_count; i++) {
            pthread_join(threads[i], NULL);
        }
    } else {
        /* Sequential search */
        for (int i = 0; i < dir_count; i++) {
            search_directory(&ctx, dir_list[i]);
            if (ctx.result_count >= max_results) break;
        }
    }
    
    pthread_mutex_destroy(&ctx.mutex);
    
    /* Serialize results to output buffer */
    uint8_t* ptr = output;
    uint8_t* end = output + output_len;
    
    /* Write result count */
    if (ptr + 4 > end) {
        free(ctx.results);
        return -1;
    }
    memcpy(ptr, &ctx.result_count, 4);
    ptr += 4;
    
    for (int i = 0; i < ctx.result_count; i++) {
        SearchResult* res = &ctx.results[i];
        
        int32_t path_len = strlen(res->file_path);
        int32_t title_len = strlen(res->title);
        int32_t snippet_len = strlen(res->snippet);
        
        /* Check space */
        int32_t needed = 4 + path_len + 4 + title_len + 4 + snippet_len + 4 + 8 + 4 + 4;
        for (int t = 0; t < res->tag_count; t++) {
            needed += 4 + strlen(res->tags[t]);
        }
        
        if (ptr + needed > end) break;
        
        /* Write path */
        memcpy(ptr, &path_len, 4); ptr += 4;
        memcpy(ptr, res->file_path, path_len); ptr += path_len;
        
        /* Write title */
        memcpy(ptr, &title_len, 4); ptr += 4;
        memcpy(ptr, res->title, title_len); ptr += title_len;
        
        /* Write snippet */
        memcpy(ptr, &snippet_len, 4); ptr += 4;
        memcpy(ptr, res->snippet, snippet_len); ptr += snippet_len;
        
        /* Write mood */
        memcpy(ptr, &res->mood, 4); ptr += 4;
        
        /* Write saved_at */
        memcpy(ptr, &res->saved_at, 8); ptr += 8;
        
        /* Write match_count */
        memcpy(ptr, &res->match_count, 4); ptr += 4;
        
        /* Write tags */
        memcpy(ptr, &res->tag_count, 4); ptr += 4;
        for (int t = 0; t < res->tag_count; t++) {
            int32_t tag_len = strlen(res->tags[t]);
            memcpy(ptr, &tag_len, 4); ptr += 4;
            memcpy(ptr, res->tags[t], tag_len); ptr += tag_len;
        }
    }
    
    int32_t result_count = ctx.result_count;
    free(ctx.results);
    
    return result_count;
}

/**
 * @brief Get estimated output buffer size for batch search
 * 
 * @param max_results Maximum number of results
 * @return Recommended buffer size in bytes
 */
int32_t simjot_search_batch_buffer_size(int32_t max_results) {
    if (max_results <= 0) max_results = MAX_RESULTS;
    if (max_results > MAX_RESULTS) max_results = MAX_RESULTS;
    
    /* Each result needs roughly:
     * 4 + 4096 (path) + 4 + 256 (title) + 4 + 512 (snippet) + 4 + 8 + 4 + 4 + 32*68 (tags)
     * ≈ 7KB per result
     */
    return 4 + (max_results * 8192);
}
