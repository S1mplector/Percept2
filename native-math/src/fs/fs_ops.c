/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file fs_ops.c
 * @brief Native File System Operations for Simjot
 * 
 * High-performance file listing operations including:
 * - Fast directory traversal
 * - File watching (platform-specific)
 * - Batch file operations
 * - File metadata
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <time.h>

#ifdef __APPLE__
    #include <sys/event.h>
    #include <CoreServices/CoreServices.h>
#elif defined(__linux__)
    #include <sys/inotify.h>
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE METADATA
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef struct SimjotFileInfo {
    int64_t size;
    int64_t modified_time;
    int64_t created_time;
    int32_t is_directory;
    int32_t is_regular;
    int32_t is_symlink;
    int32_t is_hidden;
    int32_t permissions;
} SimjotFileInfo;

/**
 * @brief Get file metadata
 */
int32_t simjot_fs_stat(const char* path, SimjotFileInfo* info) {
    if (!path || !info) return -1;
    
    struct stat st;
    if (lstat(path, &st) != 0) return -1;
    
    memset(info, 0, sizeof(SimjotFileInfo));
    info->size = st.st_size;
    info->modified_time = (int64_t)st.st_mtime * 1000;
    info->is_directory = S_ISDIR(st.st_mode) ? 1 : 0;
    info->is_regular = S_ISREG(st.st_mode) ? 1 : 0;
    info->is_symlink = S_ISLNK(st.st_mode) ? 1 : 0;
    info->permissions = st.st_mode & 0777;
    
#ifdef __APPLE__
    info->created_time = (int64_t)st.st_birthtimespec.tv_sec * 1000;
#else
    info->created_time = info->modified_time;
#endif
    
    /* Check if hidden (starts with dot on Unix) */
    const char* basename = strrchr(path, '/');
    if (basename) basename++;
    else basename = path;
    info->is_hidden = (basename[0] == '.') ? 1 : 0;
    
    return 0;
}

/**
 * @brief Get file size
 */
int64_t simjot_fs_size(const char* path) {
    if (!path) return -1;
    
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    
    return st.st_size;
}

/**
 * @brief Get file modification time (milliseconds since epoch)
 */
int64_t simjot_fs_mtime(const char* path) {
    if (!path) return -1;
    
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    
    return (int64_t)st.st_mtime * 1000;
}

/**
 * @brief Check if path exists
 */
int32_t simjot_fs_exists(const char* path) {
    if (!path) return 0;
    return access(path, F_OK) == 0 ? 1 : 0;
}

/**
 * @brief Check if path is directory
 */
int32_t simjot_fs_is_dir(const char* path) {
    if (!path) return 0;
    
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    
    return S_ISDIR(st.st_mode) ? 1 : 0;
}

/**
 * @brief Check if path is regular file
 */
int32_t simjot_fs_is_file(const char* path) {
    if (!path) return 0;
    
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    
    return S_ISREG(st.st_mode) ? 1 : 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST DIRECTORY TRAVERSAL
 * ═══════════════════════════════════════════════════════════════════════════ */

typedef void (*DirCallback)(const char* path, const char* name, int32_t is_dir, void* user_data);

static void traverse_dir_internal(const char* path, DirCallback callback, 
                                   void* user_data, int32_t max_depth, int32_t current_depth) {
    if (current_depth > max_depth) return;
    
    DIR* dir = opendir(path);
    if (!dir) return;
    
    struct dirent* entry;
    char child_path[4096];
    
    while ((entry = readdir(dir)) != NULL) {
        /* Skip . and .. */
        if (entry->d_name[0] == '.' && 
            (entry->d_name[1] == '\0' || 
             (entry->d_name[1] == '.' && entry->d_name[2] == '\0'))) {
            continue;
        }
        
        /* Build full path */
        snprintf(child_path, sizeof(child_path), "%s/%s", path, entry->d_name);
        
        int32_t is_dir = 0;
#ifdef _DIRENT_HAVE_D_TYPE
        if (entry->d_type == DT_DIR) {
            is_dir = 1;
        } else if (entry->d_type == DT_UNKNOWN) {
            is_dir = simjot_fs_is_dir(child_path);
        }
#else
        is_dir = simjot_fs_is_dir(child_path);
#endif
        
        callback(child_path, entry->d_name, is_dir, user_data);
        
        if (is_dir) {
            traverse_dir_internal(child_path, callback, user_data, max_depth, current_depth + 1);
        }
    }
    
    closedir(dir);
}

/* Counting callback data */
typedef struct CountData {
    int32_t file_count;
    int32_t dir_count;
    int64_t total_size;
} CountData;

static void count_callback(const char* path, const char* name, int32_t is_dir, void* user_data) {
    (void)name;
    CountData* data = (CountData*)user_data;
    
    if (is_dir) {
        data->dir_count++;
    } else {
        data->file_count++;
        data->total_size += simjot_fs_size(path);
    }
}

/**
 * @brief Count files and directories recursively
 */
int32_t simjot_fs_count(const char* path, int32_t max_depth, 
                        int32_t* file_count, int32_t* dir_count, int64_t* total_size) {
    if (!path) return -1;
    
    CountData data = {0, 0, 0};
    traverse_dir_internal(path, count_callback, &data, max_depth, 0);
    
    if (file_count) *file_count = data.file_count;
    if (dir_count) *dir_count = data.dir_count;
    if (total_size) *total_size = data.total_size;
    
    return data.file_count + data.dir_count;
}

/* List callback data */
typedef struct ListData {
    char* buffer;
    int32_t buffer_len;
    int32_t offset;
    int32_t count;
    const char* extension;
} ListData;

static void list_callback(const char* path, const char* name, int32_t is_dir, void* user_data) {
    ListData* data = (ListData*)user_data;
    
    /* Filter by extension if specified */
    if (data->extension && !is_dir) {
        const char* ext = strrchr(name, '.');
        if (!ext || strcasecmp(ext + 1, data->extension) != 0) {
            return;
        }
    }
    
    int32_t path_len = (int32_t)strlen(path);
    if (data->offset + path_len + 2 < data->buffer_len) {
        if (data->offset > 0) {
            data->buffer[data->offset++] = '\n';
        }
        memcpy(data->buffer + data->offset, path, path_len);
        data->offset += path_len;
        data->buffer[data->offset] = '\0';
    }
    data->count++;
}

/**
 * @brief List files recursively (newline-separated)
 */
int32_t simjot_fs_list_recursive(const char* path, const char* extension,
                                  int32_t max_depth, char* output, int32_t output_len) {
    if (!path || !output || output_len <= 0) return 0;
    
    ListData data;
    data.buffer = output;
    data.buffer_len = output_len;
    data.offset = 0;
    data.count = 0;
    data.extension = extension;
    
    output[0] = '\0';
    traverse_dir_internal(path, list_callback, &data, max_depth, 0);
    
    return data.count;
}

/**
 * @brief Find files matching pattern (glob-like)
 */
int32_t simjot_fs_find(const char* path, const char* pattern, 
                        char* output, int32_t output_len) {
    if (!path || !pattern || !output || output_len <= 0) return 0;
    
    /* Simple pattern matching - just extension for now */
    const char* ext = NULL;
    if (pattern[0] == '*' && pattern[1] == '.') {
        ext = pattern + 2;
    }
    
    return simjot_fs_list_recursive(path, ext, 10, output, output_len);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * BATCH FILE OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Read entire file into buffer
 */
int32_t simjot_fs_read_all(const char* path, uint8_t* output, int32_t output_len) {
    if (!path || !output || output_len <= 0) return -1;
    
    FILE* f = fopen(path, "rb");
    if (!f) return -1;
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (size > output_len - 1) {
        fclose(f);
        return -2;  /* Buffer too small */
    }
    
    size_t read = fread(output, 1, size, f);
    fclose(f);
    
    output[read] = '\0';
    return (int32_t)read;
}

/**
 * @brief Write buffer to file
 */
int32_t simjot_fs_write_all(const char* path, const uint8_t* data, int32_t len) {
    if (!path || !data || len < 0) return -1;
    
    FILE* f = fopen(path, "wb");
    if (!f) return -1;
    
    size_t written = fwrite(data, 1, len, f);
    fclose(f);
    
    return (written == (size_t)len) ? 0 : -1;
}

/**
 * @brief Append to file
 */
int32_t simjot_fs_append(const char* path, const uint8_t* data, int32_t len) {
    if (!path || !data || len < 0) return -1;
    
    FILE* f = fopen(path, "ab");
    if (!f) return -1;
    
    size_t written = fwrite(data, 1, len, f);
    fclose(f);
    
    return (written == (size_t)len) ? 0 : -1;
}

/**
 * @brief Create directory (including parents)
 */
int32_t simjot_fs_mkdir(const char* path) {
    if (!path) return -1;
    
    char tmp[4096];
    snprintf(tmp, sizeof(tmp), "%s", path);
    
    for (char* p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (access(tmp, F_OK) != 0) {
                if (mkdir(tmp, 0755) != 0 && errno != EEXIST) return -1;
            }
            *p = '/';
        }
    }
    
    if (access(tmp, F_OK) != 0) {
        if (mkdir(tmp, 0755) != 0 && errno != EEXIST) return -1;
    }
    
    return 0;
}

/**
 * @brief Remove file or empty directory
 */
int32_t simjot_fs_remove(const char* path) {
    if (!path) return -1;
    
    if (simjot_fs_is_dir(path)) {
        return rmdir(path) == 0 ? 0 : -1;
    }
    return unlink(path) == 0 ? 0 : -1;
}

/**
 * @brief Rename/move file
 */
int32_t simjot_fs_rename(const char* old_path, const char* new_path) {
    if (!old_path || !new_path) return -1;
    return rename(old_path, new_path) == 0 ? 0 : -1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE WATCHING (macOS)
 * ═══════════════════════════════════════════════════════════════════════════ */

#ifdef __APPLE__

#define MAX_WATCHES 16

typedef struct FileWatch {
    int fd;
    int kq;
    char path[4096];
    int32_t active;
} FileWatch;

static FileWatch g_watches[MAX_WATCHES];

/**
 * @brief Create file watcher
 */
int32_t simjot_fs_watch_create(const char* path) {
    if (!path) return -1;
    
    int32_t watch_id = -1;
    for (int i = 0; i < MAX_WATCHES; i++) {
        if (!g_watches[i].active) {
            watch_id = i;
            break;
        }
    }
    if (watch_id < 0) return -1;
    
    FileWatch* watch = &g_watches[watch_id];
    
    watch->kq = kqueue();
    if (watch->kq < 0) return -1;
    
    watch->fd = open(path, O_EVTONLY);
    if (watch->fd < 0) {
        close(watch->kq);
        return -1;
    }
    
    struct kevent event;
    EV_SET(&event, watch->fd, EVFILT_VNODE, 
           EV_ADD | EV_CLEAR,
           NOTE_WRITE | NOTE_DELETE | NOTE_RENAME | NOTE_ATTRIB,
           0, NULL);
    
    if (kevent(watch->kq, &event, 1, NULL, 0, NULL) < 0) {
        close(watch->fd);
        close(watch->kq);
        return -1;
    }
    
    strncpy(watch->path, path, sizeof(watch->path) - 1);
    watch->active = 1;
    
    return watch_id;
}

/**
 * @brief Poll for file changes
 * @return Event flags (1=modified, 2=deleted, 4=renamed, 8=attrib)
 */
int32_t simjot_fs_watch_poll(int32_t watch_id, int32_t timeout_ms) {
    if (watch_id < 0 || watch_id >= MAX_WATCHES) return 0;
    
    FileWatch* watch = &g_watches[watch_id];
    if (!watch->active) return 0;
    
    struct kevent event;
    struct timespec timeout = {
        timeout_ms / 1000,
        (timeout_ms % 1000) * 1000000
    };
    
    int n = kevent(watch->kq, NULL, 0, &event, 1, &timeout);
    if (n <= 0) return 0;
    
    int32_t flags = 0;
    if (event.fflags & NOTE_WRITE) flags |= 1;
    if (event.fflags & NOTE_DELETE) flags |= 2;
    if (event.fflags & NOTE_RENAME) flags |= 4;
    if (event.fflags & NOTE_ATTRIB) flags |= 8;
    
    return flags;
}

/**
 * @brief Destroy file watcher
 */
void simjot_fs_watch_destroy(int32_t watch_id) {
    if (watch_id < 0 || watch_id >= MAX_WATCHES) return;
    
    FileWatch* watch = &g_watches[watch_id];
    if (!watch->active) return;
    
    close(watch->fd);
    close(watch->kq);
    memset(watch, 0, sizeof(FileWatch));
}

#else

/* Linux inotify implementation */
int32_t simjot_fs_watch_create(const char* path) {
    (void)path;
    return -1;  /* TODO: Implement Linux inotify */
}

int32_t simjot_fs_watch_poll(int32_t watch_id, int32_t timeout_ms) {
    (void)watch_id;
    (void)timeout_ms;
    return 0;
}

void simjot_fs_watch_destroy(int32_t watch_id) {
    (void)watch_id;
}

#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * PATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief Get file extension
 */
int32_t simjot_fs_extension(const char* path, char* output, int32_t output_len) {
    if (!path || !output || output_len <= 0) return 0;
    
    const char* ext = strrchr(path, '.');
    if (!ext || ext == path) {
        output[0] = '\0';
        return 0;
    }
    
    /* Make sure dot isn't in directory part */
    const char* sep = strrchr(path, '/');
    if (sep && ext < sep) {
        output[0] = '\0';
        return 0;
    }
    
    ext++;  /* Skip dot */
    strncpy(output, ext, output_len - 1);
    output[output_len - 1] = '\0';
    
    return (int32_t)strlen(output);
}

/**
 * @brief Get file basename (without directory)
 */
int32_t simjot_fs_basename(const char* path, char* output, int32_t output_len) {
    if (!path || !output || output_len <= 0) return 0;
    
    const char* base = strrchr(path, '/');
    if (base) base++;
    else base = path;
    
    strncpy(output, base, output_len - 1);
    output[output_len - 1] = '\0';
    
    return (int32_t)strlen(output);
}

/**
 * @brief Get directory part of path
 */
int32_t simjot_fs_dirname(const char* path, char* output, int32_t output_len) {
    if (!path || !output || output_len <= 0) return 0;
    
    const char* sep = strrchr(path, '/');
    if (!sep) {
        output[0] = '.';
        output[1] = '\0';
        return 1;
    }
    
    int32_t len = (int32_t)(sep - path);
    if (len == 0) len = 1;  /* Root directory */
    
    if (len >= output_len) len = output_len - 1;
    
    strncpy(output, path, len);
    output[len] = '\0';
    
    return len;
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * HIGH-PERFORMANCE DIRECTORY LISTING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @brief List directory entries with extension filtering
 * 
 * Returns entries as newline-separated strings in format:
 * type|mtime|size|name\n
 * 
 * Where type is 'f' for file, 'd' for directory
 * 
 * @param dir_path Directory to list
 * @param extensions Comma-separated extensions to include (e.g. ".txt,.md") or NULL for all
 * @param include_hidden Whether to include hidden files (starting with .)
 * @param output Output buffer for results
 * @param output_len Size of output buffer
 * @return Number of bytes written, or -1 on error
 */
int32_t simjot_fs_list_filtered(const char* dir_path, const char* extensions, 
                                 int32_t include_hidden, char* output, int32_t output_len) {
    if (!dir_path || !output || output_len <= 0) return -1;
    
    DIR* dir = opendir(dir_path);
    if (!dir) return -1;
    
    /* Parse extensions into array for fast matching */
    #define MAX_EXTS 32
    const char* ext_list[MAX_EXTS];
    int32_t ext_count = 0;
    char ext_buf[512];
    
    if (extensions && extensions[0]) {
        strncpy(ext_buf, extensions, sizeof(ext_buf) - 1);
        ext_buf[sizeof(ext_buf) - 1] = '\0';
        
        char* token = strtok(ext_buf, ",");
        while (token && ext_count < MAX_EXTS) {
            /* Trim whitespace */
            while (*token == ' ') token++;
            if (*token) {
                ext_list[ext_count++] = token;
            }
            token = strtok(NULL, ",");
        }
    }
    
    int32_t written = 0;
    struct dirent* entry;
    char full_path[4096];
    int32_t dir_len = (int32_t)strlen(dir_path);
    
    while ((entry = readdir(dir)) != NULL) {
        const char* name = entry->d_name;
        
        /* Skip . and .. */
        if (name[0] == '.' && (name[1] == '\0' || (name[1] == '.' && name[2] == '\0'))) {
            continue;
        }
        
        /* Skip hidden files if requested */
        if (!include_hidden && name[0] == '.') {
            continue;
        }
        
        /* Check extension filter if specified */
        if (ext_count > 0) {
            const char* dot = strrchr(name, '.');
            int32_t matched = 0;
            if (dot) {
                for (int32_t i = 0; i < ext_count; i++) {
                    if (strcasecmp(dot, ext_list[i]) == 0) {
                        matched = 1;
                        break;
                    }
                }
            }
            if (!matched) continue;
        }
        
        /* Build full path for stat */
        if (dir_len + 1 + strlen(name) >= sizeof(full_path)) continue;
        snprintf(full_path, sizeof(full_path), "%s/%s", dir_path, name);
        
        struct stat st;
        if (stat(full_path, &st) != 0) continue;
        
        char type = S_ISDIR(st.st_mode) ? 'd' : 'f';
        int64_t mtime = (int64_t)st.st_mtime * 1000;
        int64_t size = st.st_size;
        
        /* Format: type|mtime|size|name\n */
        int32_t name_len = (int32_t)strlen(name);
        int32_t needed = 1 + 1 + 20 + 1 + 20 + 1 + name_len + 1; /* type|mtime|size|name\n */
        
        if (written + needed >= output_len) {
            /* Buffer full */
            break;
        }
        
        int32_t line_len = snprintf(output + written, output_len - written,
                                    "%c|%lld|%lld|%s\n", type, mtime, size, name);
        if (line_len > 0) {
            written += line_len;
        }
    }
    
    closedir(dir);
    
    if (written > 0) {
        output[written] = '\0';
    } else {
        output[0] = '\0';
    }
    
    return written;
    #undef MAX_EXTS
}

/**
 * @brief Count entries in a directory (fast, no stat calls)
 */
int32_t simjot_fs_count_entries(const char* dir_path, int32_t include_hidden) {
    if (!dir_path) return -1;
    
    DIR* dir = opendir(dir_path);
    if (!dir) return -1;
    
    int32_t count = 0;
    struct dirent* entry;
    
    while ((entry = readdir(dir)) != NULL) {
        const char* name = entry->d_name;
        
        /* Skip . and .. */
        if (name[0] == '.' && (name[1] == '\0' || (name[1] == '.' && name[2] == '\0'))) {
            continue;
        }
        
        /* Skip hidden files if requested */
        if (!include_hidden && name[0] == '.') {
            continue;
        }
        
        count++;
    }
    
    closedir(dir);
    return count;
}

/**
 * @brief Join path components
 */
int32_t simjot_fs_join(const char* base, const char* child, char* output, int32_t output_len) {
    if (!base || !child || !output || output_len <= 0) return 0;
    
    int32_t base_len = (int32_t)strlen(base);
    int32_t child_len = (int32_t)strlen(child);
    
    /* Remove trailing slash from base */
    while (base_len > 0 && base[base_len - 1] == '/') base_len--;
    
    /* Remove leading slash from child */
    while (child_len > 0 && child[0] == '/') {
        child++;
        child_len--;
    }
    
    if (base_len + 1 + child_len >= output_len) return 0;
    
    memcpy(output, base, base_len);
    output[base_len] = '/';
    memcpy(output + base_len + 1, child, child_len);
    output[base_len + 1 + child_len] = '\0';
    
    return base_len + 1 + child_len;
}
