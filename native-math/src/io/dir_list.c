/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * Directory listing helpers (Unix-focused).
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#ifndef _WIN32
#include <dirent.h>
#include <sys/stat.h>
#include <stdlib.h>

static int is_hidden_name(const char* name) {
    return name && name[0] == '.';
}

static int is_dot_entry(const char* name) {
    return name && (strcmp(name, ".") == 0 || strcmp(name, "..") == 0);
}

static int is_dir_entry(const char* dir_path, const struct dirent* entry) {
    if (!entry) return 0;
    if (entry->d_type == DT_DIR) return 1;
    if (entry->d_type != DT_UNKNOWN && entry->d_type != DT_LNK) return 0;

    size_t dir_len = strlen(dir_path);
    size_t name_len = strlen(entry->d_name);
    size_t full_len = dir_len + name_len + 2;
    char* full = (char*)malloc(full_len);
    if (!full) return 0;
    snprintf(full, full_len, "%s/%s", dir_path, entry->d_name);

    struct stat st;
    int ok = (stat(full, &st) == 0 && S_ISDIR(st.st_mode));
    free(full);
    return ok;
}

int32_t simjot_list_dir_size(const char* path, int32_t include_hidden) {
    if (!path) return 0;
    DIR* dir = opendir(path);
    if (!dir) return 0;

    size_t total = 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        const char* name = entry->d_name;
        if (is_dot_entry(name)) continue;
        if (!include_hidden && is_hidden_name(name)) continue;
        size_t name_len = strlen(name);
        total += 8 + name_len; // 4 bytes len + 4 bytes flags/padding
        if (total > INT32_MAX) {
            closedir(dir);
            return -1;
        }
    }

    closedir(dir);
    return (int32_t)total;
}

int32_t simjot_list_dir(const char* path, int32_t include_hidden, uint8_t* out, int32_t out_len) {
    if (!path || !out || out_len <= 0) return 0;

    DIR* dir = opendir(path);
    if (!dir) return 0;

    int32_t offset = 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        const char* name = entry->d_name;
        if (is_dot_entry(name)) continue;
        int hidden = is_hidden_name(name) ? 1 : 0;
        if (!include_hidden && hidden) continue;

        uint32_t name_len = (uint32_t)strlen(name);
        int32_t record_len = (int32_t)(8 + name_len);
        if (offset + record_len > out_len) {
            closedir(dir);
            return -record_len;
        }

        int is_dir = is_dir_entry(path, entry) ? 1 : 0;

        memcpy(out + offset, &name_len, 4);
        out[offset + 4] = (uint8_t)is_dir;
        out[offset + 5] = (uint8_t)hidden;
        out[offset + 6] = 0;
        out[offset + 7] = 0;
        memcpy(out + offset + 8, name, name_len);
        offset += record_len;
    }

    closedir(dir);
    return offset;
}

#else
int32_t simjot_list_dir_size(const char* path, int32_t include_hidden) {
    (void)path;
    (void)include_hidden;
    return 0;
}

int32_t simjot_list_dir(const char* path, int32_t include_hidden, uint8_t* out, int32_t out_len) {
    (void)path;
    (void)include_hidden;
    (void)out;
    (void)out_len;
    return 0;
}
#endif
