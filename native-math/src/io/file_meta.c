/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <sys/stat.h>

#ifndef _WIN32
#include <unistd.h>
#include <dirent.h>
#else
#include <windows.h>
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * WORD COUNTING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Count words in a text buffer.
 * A word is a sequence of non-whitespace characters.
 */
int32_t simjot_count_words(const char* text, int32_t len) {
    if (text == NULL || len <= 0) return 0;
    
    int32_t count = 0;
    int in_word = 0;
    
    for (int32_t i = 0; i < len; i++) {
        unsigned char c = (unsigned char)text[i];
        if (c == '\0') break;
        
        int is_space = (c <= 32); /* space, tab, newline, etc. */
        
        if (is_space) {
            in_word = 0;
        } else if (!in_word) {
            in_word = 1;
            count++;
        }
    }
    
    return count;
}

/**
 * Count words in a file.
 * Reads file in chunks to avoid loading entire file into memory.
 */
int32_t simjot_count_words_file(const char* path) {
    if (path == NULL) return -1;
    
    FILE* f = fopen(path, "rb");
    if (f == NULL) return -1;
    
    int32_t count = 0;
    int in_word = 0;
    char buf[8192];
    size_t n;
    
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        for (size_t i = 0; i < n; i++) {
            unsigned char c = (unsigned char)buf[i];
            int is_space = (c <= 32);
            
            if (is_space) {
                in_word = 0;
            } else if (!in_word) {
                in_word = 1;
                count++;
            }
        }
    }
    
    fclose(f);
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TITLE EXTRACTION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Extract first non-empty line from a file as title.
 * Skips leading whitespace and empty lines.
 * Returns number of bytes written to out (excluding null terminator).
 */
int32_t simjot_extract_title(const char* path, char* out, int32_t out_len) {
    if (path == NULL || out == NULL || out_len <= 0) return 0;
    out[0] = '\0';
    
    FILE* f = fopen(path, "r");
    if (f == NULL) return 0;
    
    char line[512];
    while (fgets(line, sizeof(line), f) != NULL) {
        /* Skip leading whitespace */
        char* p = line;
        while (*p && isspace((unsigned char)*p)) p++;
        
        /* Skip empty lines */
        if (*p == '\0' || *p == '\n' || *p == '\r') continue;
        
        /* Remove trailing newline/whitespace */
        size_t len = strlen(p);
        while (len > 0 && (p[len-1] == '\n' || p[len-1] == '\r' || isspace((unsigned char)p[len-1]))) {
            p[--len] = '\0';
        }
        
        if (len == 0) continue;
        
        /* Copy to output, truncate if needed */
        int32_t copy_len = (int32_t)len;
        if (copy_len >= out_len) copy_len = out_len - 1;
        memcpy(out, p, copy_len);
        out[copy_len] = '\0';
        
        fclose(f);
        return copy_len;
    }
    
    fclose(f);
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE METADATA
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Get file metadata: size and modification time.
 * Returns 1 on success, 0 on failure.
 * out_size: file size in bytes
 * out_mtime: modification time in milliseconds since epoch
 */
int32_t simjot_file_meta(const char* path, int64_t* out_size, int64_t* out_mtime) {
    if (path == NULL) return 0;
    
#ifdef _WIN32
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (!GetFileAttributesExA(path, GetFileExInfoStandard, &data)) return 0;
    
    if (out_size) {
        LARGE_INTEGER size;
        size.HighPart = data.nFileSizeHigh;
        size.LowPart = data.nFileSizeLow;
        *out_size = size.QuadPart;
    }
    if (out_mtime) {
        /* Convert FILETIME to milliseconds since epoch */
        ULARGE_INTEGER ull;
        ull.HighPart = data.ftLastWriteTime.dwHighDateTime;
        ull.LowPart = data.ftLastWriteTime.dwLowDateTime;
        /* FILETIME is 100-nanosecond intervals since Jan 1, 1601 */
        /* Unix epoch is Jan 1, 1970 - difference is 116444736000000000 100-ns intervals */
        *out_mtime = (int64_t)((ull.QuadPart - 116444736000000000ULL) / 10000);
    }
    return 1;
#else
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    
    if (out_size) *out_size = (int64_t)st.st_size;
    if (out_mtime) {
#if defined(__APPLE__)
        *out_mtime = (int64_t)st.st_mtimespec.tv_sec * 1000 + st.st_mtimespec.tv_nsec / 1000000;
#elif defined(_POSIX_C_SOURCE) && _POSIX_C_SOURCE >= 200809L
        *out_mtime = (int64_t)st.st_mtim.tv_sec * 1000 + st.st_mtim.tv_nsec / 1000000;
#else
        *out_mtime = (int64_t)st.st_mtime * 1000;
#endif
    }
    return 1;
#endif
}

/**
 * Batch file metadata: get word count, title, size, mtime for a file.
 * Output format (binary):
 *   int32: word count
 *   int64: file size
 *   int64: mtime (ms since epoch)
 *   int32: title length
 *   char[]: title (not null-terminated in buffer)
 * Returns total bytes written, or 0 on failure.
 */
int32_t simjot_file_meta_batch(const char* path, uint8_t* out, int32_t out_len) {
    if (path == NULL || out == NULL || out_len < 28) return 0;
    
    int64_t size = 0, mtime = 0;
    if (!simjot_file_meta(path, &size, &mtime)) return 0;
    
    int32_t wc = simjot_count_words_file(path);
    if (wc < 0) wc = 0;
    
    char title[256];
    int32_t title_len = simjot_extract_title(path, title, sizeof(title));
    
    /* Calculate required size */
    int32_t needed = 4 + 8 + 8 + 4 + title_len;
    if (needed > out_len) {
        title_len = out_len - 24;
        if (title_len < 0) title_len = 0;
        needed = 24 + title_len;
    }
    
    /* Write output */
    int32_t pos = 0;
    
    /* Word count (int32) */
    memcpy(out + pos, &wc, 4); pos += 4;
    
    /* File size (int64) */
    memcpy(out + pos, &size, 8); pos += 8;
    
    /* Mtime (int64) */
    memcpy(out + pos, &mtime, 8); pos += 8;
    
    /* Title length (int32) */
    memcpy(out + pos, &title_len, 4); pos += 4;
    
    /* Title bytes */
    if (title_len > 0) {
        memcpy(out + pos, title, title_len);
        pos += title_len;
    }
    
    return pos;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DIRECTORY LISTING WITH METADATA
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * List files in a directory with basic metadata.
 * Output format (binary, repeated):
 *   int32: name length
 *   char[]: filename (not null-terminated)
 *   int64: file size
 *   int64: mtime
 * Returns total bytes written.
 */
int32_t simjot_list_files_meta(const char* dir_path, const char* extension,
                                uint8_t* out, int32_t out_len) {
    if (dir_path == NULL || out == NULL || out_len <= 0) return 0;
    
    int32_t pos = 0;
    size_t ext_len = extension ? strlen(extension) : 0;
    
#ifdef _WIN32
    char pattern[512];
    snprintf(pattern, sizeof(pattern), "%s\\*", dir_path);
    
    WIN32_FIND_DATAA fd;
    HANDLE hFind = FindFirstFileA(pattern, &fd);
    if (hFind == INVALID_HANDLE_VALUE) return 0;
    
    do {
        if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) continue;
        
        const char* name = fd.cFileName;
        size_t name_len = strlen(name);
        
        /* Check extension */
        if (ext_len > 0) {
            if (name_len <= ext_len) continue;
            if (strcmp(name + name_len - ext_len, extension) != 0) continue;
        }
        
        /* Check space */
        int32_t needed = 4 + (int32_t)name_len + 8 + 8;
        if (pos + needed > out_len) break;
        
        /* Write entry */
        int32_t nl = (int32_t)name_len;
        memcpy(out + pos, &nl, 4); pos += 4;
        memcpy(out + pos, name, name_len); pos += (int32_t)name_len;
        
        LARGE_INTEGER sz;
        sz.HighPart = fd.nFileSizeHigh;
        sz.LowPart = fd.nFileSizeLow;
        int64_t size = sz.QuadPart;
        memcpy(out + pos, &size, 8); pos += 8;
        
        ULARGE_INTEGER ull;
        ull.HighPart = fd.ftLastWriteTime.dwHighDateTime;
        ull.LowPart = fd.ftLastWriteTime.dwLowDateTime;
        int64_t mtime = (int64_t)((ull.QuadPart - 116444736000000000ULL) / 10000);
        memcpy(out + pos, &mtime, 8); pos += 8;
        
    } while (FindNextFileA(hFind, &fd));
    
    FindClose(hFind);
#else
    DIR* dir = opendir(dir_path);
    if (dir == NULL) return 0;
    
    struct dirent* entry;
    char full_path[1024];
    
    while ((entry = readdir(dir)) != NULL) {
        if (entry->d_name[0] == '.') continue;
        
        const char* name = entry->d_name;
        size_t name_len = strlen(name);
        
        /* Check extension */
        if (ext_len > 0) {
            if (name_len <= ext_len) continue;
            if (strcmp(name + name_len - ext_len, extension) != 0) continue;
        }
        
        /* Get full path for stat */
        snprintf(full_path, sizeof(full_path), "%s/%s", dir_path, name);
        
        struct stat st;
        if (stat(full_path, &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) continue;
        
        /* Check space */
        int32_t needed = 4 + (int32_t)name_len + 8 + 8;
        if (pos + needed > out_len) break;
        
        /* Write entry */
        int32_t nl = (int32_t)name_len;
        memcpy(out + pos, &nl, 4); pos += 4;
        memcpy(out + pos, name, name_len); pos += (int32_t)name_len;
        
        int64_t size = (int64_t)st.st_size;
        memcpy(out + pos, &size, 8); pos += 8;
        
#if defined(__APPLE__)
        int64_t mtime = (int64_t)st.st_mtimespec.tv_sec * 1000 + st.st_mtimespec.tv_nsec / 1000000;
#elif defined(_POSIX_C_SOURCE) && _POSIX_C_SOURCE >= 200809L
        int64_t mtime = (int64_t)st.st_mtim.tv_sec * 1000 + st.st_mtim.tv_nsec / 1000000;
#else
        int64_t mtime = (int64_t)st.st_mtime * 1000;
#endif
        memcpy(out + pos, &mtime, 8); pos += 8;
    }
    
    closedir(dir);
#endif
    
    return pos;
}
