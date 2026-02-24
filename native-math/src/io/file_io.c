/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * File I/O helpers for atomic writes and space checks.
 * Provides functions for:
 * - Atomic file writing to ensure data integrity.
 * - Checking available disk space.
 * - Efficient file copying with attribute preservation.
 *
 * Meant to be used only on POSIX systems, but stubs are provided for Windows.
 * Depends on standard C library and POSIX APIs.
 * Works with C17 standard.
 * Written in a portable manner with fallbacks for different Unix-like systems.
 * Use cases include safely writing files atomically, checking disk space,
 * and copying files efficiently while preserving attributes.
 * It is designed to be easy to use and integrate into existing codebases.
 * 
 * Depending on your preference, you may modify the error handling strategy to suit your needs.
 * This may include changing the logging behavior, adjusting the level of detail in error messages,
 * or implementing custom recovery actions in response to specific errors.
 * 
 * So, feel free to adapt the error handling to align with your application's requirements and coding standards.
 * Regarding memory safety, this code takes care to allocate and free memory appropriately.
 * However, it is always advisable to review and test the code in the context of your specific
 * application to ensure it meets your memory safety standards and practices.
 * No guarantees are made about memory safety beyond standard C practices.
 * Use with caution and perform your own validation as needed.
 * 
 * @author S1mplector
 */

#include "simjot_native.h"

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef _WIN32
#include <unistd.h>
#include <fcntl.h>
#include <sys/statvfs.h>
#include <sys/stat.h>
#include <sys/types.h>
#if defined(__APPLE__)
#include <copyfile.h>
#else
#include <sys/sendfile.h>
#include <sys/syscall.h>
#endif
#endif

#ifndef _WIN32
static char* simjot_strdup(const char* s) {
    size_t len = strlen(s);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, s, len + 1);
    return out;
}

static char* simjot_parent_dir(const char* path) {
    if (!path || *path == '\0') return NULL;
    const char* slash = strrchr(path, '/');
    if (!slash) return simjot_strdup(".");
    if (slash == path) return simjot_strdup("/");
    size_t len = (size_t)(slash - path);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, path, len);
    out[len] = '\0';
    return out;
}

static const char* simjot_basename(const char* path) {
    if (!path) return NULL;
    const char* slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

#if !defined(__APPLE__)
static int simjot_copy_with_copy_file_range(int in_fd, int out_fd, off_t total) {
#if defined(__linux__) && defined(__NR_copy_file_range)
    off_t in_off = 0;
    off_t out_off = 0;
    while (in_off < total) {
        size_t remaining = (size_t)(total - in_off);
        size_t chunk = remaining > (1u << 30) ? (1u << 30) : remaining;
        ssize_t n = (ssize_t)syscall(__NR_copy_file_range, in_fd, &in_off, out_fd, &out_off, chunk, 0);
        if (n > 0) continue;
        if (n == 0) break;
        if (errno == EINTR) continue;
        if (errno == EXDEV || errno == ENOSYS || errno == EOPNOTSUPP || errno == EINVAL) return 0;
        return -1;
    }
    return 1;
#else
    (void)in_fd;
    (void)out_fd;
    (void)total;
    return 0;
#endif
}

static int simjot_copy_with_sendfile(int in_fd, int out_fd, off_t total) {
#if defined(__linux__)
    off_t offset = 0;
    while (offset < total) {
        size_t remaining = (size_t)(total - offset);
        size_t chunk = remaining > (1u << 30) ? (1u << 30) : remaining;
        ssize_t n = sendfile(out_fd, in_fd, &offset, chunk);
        if (n > 0) continue;
        if (n == 0) break;
        if (errno == EINTR) continue;
        if (errno == EINVAL || errno == ENOSYS || errno == EOPNOTSUPP) return 0;
        return -1;
    }
    return 1;
#else
    (void)in_fd;
    (void)out_fd;
    (void)total;
    return 0;
#endif
}

static int simjot_copy_with_read_write(int in_fd, int out_fd) {
    uint8_t buf[131072];
    while (1) {
        ssize_t n = read(in_fd, buf, sizeof(buf));
        if (n == 0) return 1;
        if (n < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        ssize_t written = 0;
        while (written < n) {
            ssize_t w = write(out_fd, buf + written, (size_t)(n - written));
            if (w < 0) {
                if (errno == EINTR) continue;
                return 0;
            }
            written += w;
        }
    }
}

static void simjot_copy_attrs(int out_fd, const struct stat* st) {
    if (!st) return;
    (void)fchmod(out_fd, st->st_mode & 07777);
#if defined(__linux__)
    struct timespec times[2];
    times[0] = st->st_atim;
    times[1] = st->st_mtim;
    (void)futimens(out_fd, times);
#endif
}
#endif

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    if (!target_path || !data || data_len < 0) return 0;
    const char* base = simjot_basename(target_path);
    if (!base || *base == '\0') return 0;

    char* dir = simjot_parent_dir(target_path);
    if (!dir) return 0;

    size_t template_len = strlen(dir) + strlen(base) + 12;
    char* tmp = (char*)malloc(template_len + 1);
    if (!tmp) {
        free(dir);
        return 0;
    }
    snprintf(tmp, template_len + 1, "%s/.%s.tmpXXXXXX", dir, base);

    int fd = mkstemp(tmp);
    if (fd < 0) {
        free(tmp);
        free(dir);
        return 0;
    }

    const uint8_t* p = data;
    int32_t remaining = data_len;
    while (remaining > 0) {
        ssize_t n = write(fd, p, (size_t)remaining);
        if (n < 0) {
            close(fd);
            unlink(tmp);
            free(tmp);
            free(dir);
            return 0;
        }
        p += n;
        remaining -= (int32_t)n;
    }

    if (fsync_file) {
        (void)fsync(fd);
    }
    if (close(fd) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (rename(tmp, target_path) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (fsync_dir) {
        int dfd = open(dir, O_RDONLY);
        if (dfd >= 0) {
            (void)fsync(dfd);
            close(dfd);
        }
    }

    free(tmp);
    free(dir);
    return 1;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    if (!path) return -1;
    struct statvfs fs;
    if (statvfs(path, &fs) != 0) {
        char* dir = simjot_parent_dir(path);
        if (!dir) return -1;
        int ok = statvfs(dir, &fs);
        free(dir);
        if (ok != 0) return -1;
    }
    unsigned long long block = fs.f_frsize ? fs.f_frsize : fs.f_bsize;
    unsigned long long avail = (unsigned long long)fs.f_bavail * block;
    return (avail < bytes_needed) ? 0 : 1;
}

int32_t simjot_copy_file(const char* src_path, const char* dst_path, int32_t copy_attrs) {
    if (!src_path || !dst_path) return 0;
#if defined(__APPLE__)
    uint32_t flags = COPYFILE_DATA;
    if (copy_attrs) flags |= COPYFILE_STAT;
    return copyfile(src_path, dst_path, NULL, flags) == 0;
#else
    int in_fd = open(src_path, O_RDONLY);
    if (in_fd < 0) return 0;
    struct stat st;
    if (fstat(in_fd, &st) != 0) {
        close(in_fd);
        return 0;
    }
    mode_t mode = copy_attrs ? (st.st_mode & 0777) : 0666;
    if (mode == 0) mode = 0666;
    int out_fd = open(dst_path, O_WRONLY | O_CREAT | O_TRUNC, mode);
    if (out_fd < 0) {
        close(in_fd);
        return 0;
    }
#ifdef POSIX_FADV_SEQUENTIAL
    (void)posix_fadvise(in_fd, 0, 0, POSIX_FADV_SEQUENTIAL);
#endif
    int result = simjot_copy_with_copy_file_range(in_fd, out_fd, st.st_size);
    if (result == 0) {
        (void)lseek(in_fd, 0, SEEK_SET);
        (void)lseek(out_fd, 0, SEEK_SET);
        (void)ftruncate(out_fd, 0);
        result = simjot_copy_with_sendfile(in_fd, out_fd, st.st_size);
    }
    if (result == 0) {
        (void)lseek(in_fd, 0, SEEK_SET);
        (void)lseek(out_fd, 0, SEEK_SET);
        (void)ftruncate(out_fd, 0);
        result = simjot_copy_with_read_write(in_fd, out_fd) ? 1 : -1;
    }
    if (result == 1 && copy_attrs) {
        simjot_copy_attrs(out_fd, &st);
    }
    close(out_fd);
    close(in_fd);
    return result == 1;
#endif
}
#else
int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    (void)target_path;
    (void)data;
    (void)data_len;
    (void)fsync_file;
    (void)fsync_dir;
    return 0;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    (void)path;
    (void)bytes_needed;
    return -1;
}

int32_t simjot_copy_file(const char* src_path, const char* dst_path, int32_t copy_attrs) {
    (void)src_path;
    (void)dst_path;
    (void)copy_attrs;
    return 0;
}
#endif
