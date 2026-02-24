/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/*
 * SIMJOT - Native File Utilities
 * High-performance file operations
 */

#include "simjot_native.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#ifdef __APPLE__
#include <sys/param.h>
#include <sys/mount.h>
#include <copyfile.h>
#else
#include <sys/statvfs.h>
#include <sys/sendfile.h>
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE INFORMATION
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Get file size in bytes.
 * @return Size or -1 on error
 */
int64_t simjot_file_size(const char* path) {
    if (!path) return -1;
    
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    
    return (int64_t)st.st_size;
}

/**
 * Get file modification time (Unix timestamp).
 * @return Timestamp or -1 on error
 */
int64_t simjot_file_mtime(const char* path) {
    if (!path) return -1;
    
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    
    return (int64_t)st.st_mtime;
}

/**
 * Check if path exists.
 */
int32_t simjot_file_exists(const char* path) {
    if (!path) return 0;
    return access(path, F_OK) == 0 ? 1 : 0;
}

/**
 * Check if path is a regular file.
 */
int32_t simjot_file_is_file(const char* path) {
    if (!path) return 0;
    
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    
    return S_ISREG(st.st_mode) ? 1 : 0;
}

/**
 * Check if path is a directory.
 */
int32_t simjot_file_is_dir(const char* path) {
    if (!path) return 0;
    
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    
    return S_ISDIR(st.st_mode) ? 1 : 0;
}

/**
 * Check if file is readable.
 */
int32_t simjot_file_is_readable(const char* path) {
    if (!path) return 0;
    return access(path, R_OK) == 0 ? 1 : 0;
}

/**
 * Check if file is writable.
 */
int32_t simjot_file_is_writable(const char* path) {
    if (!path) return 0;
    return access(path, W_OK) == 0 ? 1 : 0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DISK SPACE
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Get available disk space on path's filesystem.
 * @return Available bytes or -1 on error
 */
int64_t simjot_disk_available(const char* path) {
    if (!path) return -1;
    
#ifdef __APPLE__
    struct statfs st;
    if (statfs(path, &st) != 0) return -1;
    return (int64_t)st.f_bavail * st.f_bsize;
#else
    struct statvfs st;
    if (statvfs(path, &st) != 0) return -1;
    return (int64_t)st.f_bavail * st.f_frsize;
#endif
}

/**
 * Get total disk space on path's filesystem.
 */
int64_t simjot_disk_total(const char* path) {
    if (!path) return -1;
    
#ifdef __APPLE__
    struct statfs st;
    if (statfs(path, &st) != 0) return -1;
    return (int64_t)st.f_blocks * st.f_bsize;
#else
    struct statvfs st;
    if (statvfs(path, &st) != 0) return -1;
    return (int64_t)st.f_blocks * st.f_frsize;
#endif
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FILE OPERATIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Read entire file into buffer.
 * @param out_size Output: size of data read
 * @return Allocated buffer (caller must free) or NULL on error
 */
char* simjot_file_read(const char* path, int64_t* out_size) {
    if (!path || !out_size) return NULL;
    *out_size = 0;
    
    int fd = open(path, O_RDONLY);
    if (fd < 0) return NULL;
    
    struct stat st;
    if (fstat(fd, &st) != 0) {
        close(fd);
        return NULL;
    }
    
    size_t size = (size_t)st.st_size;
    char* buf = (char*)malloc(size + 1);
    if (!buf) {
        close(fd);
        return NULL;
    }
    
    size_t total = 0;
    while (total < size) {
        ssize_t n = read(fd, buf + total, size - total);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            break;
        }
        total += n;
    }
    
    close(fd);
    buf[total] = '\0';
    *out_size = (int64_t)total;
    return buf;
}

/**
 * Read file as text (convenience wrapper).
 */
char* simjot_file_read_text(const char* path) {
    int64_t size;
    return simjot_file_read(path, &size);
}

/**
 * Write data to file atomically.
 * Creates temp file, writes, then renames.
 * @return 1 on success, 0 on failure
 */
int32_t simjot_file_write_atomic(const char* path, const char* data, int64_t size) {
    if (!path || !data || size < 0) return 0;
    
    // Create temp path
    size_t path_len = strlen(path);
    char* tmp_path = (char*)malloc(path_len + 8);
    if (!tmp_path) return 0;
    snprintf(tmp_path, path_len + 8, "%s.tmp", path);
    
    // Write to temp file
    int fd = open(tmp_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        free(tmp_path);
        return 0;
    }
    
    size_t total = 0;
    while (total < (size_t)size) {
        ssize_t n = write(fd, data + total, (size_t)size - total);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            close(fd);
            unlink(tmp_path);
            free(tmp_path);
            return 0;
        }
        total += n;
    }
    
    // Fsync
    fsync(fd);
    close(fd);
    
    // Atomic rename
    if (rename(tmp_path, path) != 0) {
        unlink(tmp_path);
        free(tmp_path);
        return 0;
    }
    
    free(tmp_path);
    return 1;
}

/**
 * Copy file.
 * @return 1 on success, 0 on failure
 */
int32_t simjot_file_copy(const char* src, const char* dst) {
    if (!src || !dst) return 0;
    
#ifdef __APPLE__
    // Prefer APFS clone for speed (falls back to regular copy)
    if (copyfile(src, dst, NULL, COPYFILE_ALL | COPYFILE_CLONE) == 0) return 1;
    if (copyfile(src, dst, NULL, COPYFILE_ALL) == 0) return 1;
#endif
    
    // Fallback: manual copy
    int fd_src = open(src, O_RDONLY);
    if (fd_src < 0) return 0;
    
    struct stat st;
    if (fstat(fd_src, &st) != 0) {
        close(fd_src);
        return 0;
    }
    
    int fd_dst = open(dst, O_WRONLY | O_CREAT | O_TRUNC, st.st_mode);
    if (fd_dst < 0) {
        close(fd_src);
        return 0;
    }
    
    char buf[65536];
    ssize_t n;
    while ((n = read(fd_src, buf, sizeof(buf))) > 0) {
        char* ptr = buf;
        while (n > 0) {
            ssize_t w = write(fd_dst, ptr, n);
            if (w <= 0) {
                if (w < 0 && errno == EINTR) continue;
                close(fd_src);
                close(fd_dst);
                return 0;
            }
            ptr += w;
            n -= w;
        }
    }
    
    close(fd_src);
    close(fd_dst);
    return 1;
}

/**
 * Delete file.
 */
int32_t simjot_file_delete(const char* path) {
    if (!path) return 0;
    return unlink(path) == 0 ? 1 : 0;
}

/**
 * Create directory (and parents).
 */
int32_t simjot_mkdir_p(const char* path) {
    if (!path || !*path) return 0;
    
    char* tmp = strdup(path);
    if (!tmp) return 0;
    
    size_t len = strlen(tmp);
    if (tmp[len - 1] == '/') tmp[len - 1] = '\0';
    
    for (char* p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
                free(tmp);
                return 0;
            }
            *p = '/';
        }
    }
    
    int result = (mkdir(tmp, 0755) == 0 || errno == EEXIST) ? 1 : 0;
    free(tmp);
    return result;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DIRECTORY LISTING
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Count files in directory with optional extension filter.
 * @param extension File extension filter (e.g., ".txt") or NULL for all
 */
int32_t simjot_dir_count(const char* path, const char* extension) {
    if (!path) return 0;
    
    DIR* dir = opendir(path);
    if (!dir) return 0;
    
    int32_t count = 0;
    struct dirent* entry;
    size_t ext_len = extension ? strlen(extension) : 0;
    
    while ((entry = readdir(dir)) != NULL) {
        if (entry->d_name[0] == '.') continue;
        
        if (extension) {
            size_t name_len = strlen(entry->d_name);
            if (name_len < ext_len) continue;
            if (strcasecmp(entry->d_name + name_len - ext_len, extension) != 0) continue;
        }
        
        count++;
    }
    
    closedir(dir);
    return count;
}

/**
 * List files in directory.
 * @param out_names Output buffer for names (null-separated)
 * @param out_size Size of output buffer
 * @param extension Extension filter or NULL
 * @return Number of files found, or -1 on error
 */
int32_t simjot_dir_list(const char* path, const char* extension, 
                        char* out_names, int32_t out_size) {
    if (!path || !out_names || out_size <= 0) return -1;
    
    DIR* dir = opendir(path);
    if (!dir) return -1;
    
    int32_t count = 0;
    int32_t pos = 0;
    struct dirent* entry;
    size_t ext_len = extension ? strlen(extension) : 0;
    
    while ((entry = readdir(dir)) != NULL) {
        if (entry->d_name[0] == '.') continue;
        
        if (extension) {
            size_t name_len = strlen(entry->d_name);
            if (name_len < ext_len) continue;
            if (strcasecmp(entry->d_name + name_len - ext_len, extension) != 0) continue;
        }
        
        size_t name_len = strlen(entry->d_name);
        if (pos + name_len + 1 >= (size_t)out_size) break;
        
        memcpy(out_names + pos, entry->d_name, name_len);
        pos += name_len;
        out_names[pos++] = '\0';
        count++;
    }
    
    closedir(dir);
    return count;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PATH UTILITIES
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Get file basename (filename without directory).
 * Returns pointer into original string.
 */
const char* simjot_path_basename(const char* path) {
    if (!path) return NULL;
    
    const char* last_slash = strrchr(path, '/');
    return last_slash ? last_slash + 1 : path;
}

/**
 * Get file extension (including dot).
 * Returns pointer into original string or empty string.
 */
const char* simjot_path_extension(const char* path) {
    if (!path) return "";
    
    const char* basename = simjot_path_basename(path);
    const char* dot = strrchr(basename, '.');
    return dot ? dot : "";
}

/**
 * Get directory part of path.
 * Returns new string (caller must free).
 */
char* simjot_path_dirname(const char* path) {
    if (!path) return NULL;
    
    const char* last_slash = strrchr(path, '/');
    if (!last_slash) return strdup(".");
    if (last_slash == path) return strdup("/");
    
    size_t len = last_slash - path;
    char* dir = (char*)malloc(len + 1);
    if (!dir) return NULL;
    
    memcpy(dir, path, len);
    dir[len] = '\0';
    return dir;
}

/**
 * Join path components.
 * Returns new string (caller must free).
 */
char* simjot_path_join(const char* dir, const char* name) {
    if (!dir || !name) return NULL;
    
    size_t dir_len = strlen(dir);
    size_t name_len = strlen(name);
    
    // Remove trailing slash from dir
    while (dir_len > 0 && dir[dir_len - 1] == '/') dir_len--;
    
    // Skip leading slash from name
    while (*name == '/') { name++; name_len--; }
    
    char* result = (char*)malloc(dir_len + 1 + name_len + 1);
    if (!result) return NULL;
    
    memcpy(result, dir, dir_len);
    result[dir_len] = '/';
    memcpy(result + dir_len + 1, name, name_len);
    result[dir_len + 1 + name_len] = '\0';
    
    return result;
}
