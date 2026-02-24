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
#include <stdint.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>

#ifdef _WIN32
    #include <windows.h>
    #include <direct.h>
    #include <io.h>
    #define PATH_SEP '\\'
    #define mkdir_p(path, mode) _mkdir(path)
    #define access _access
    #define F_OK 0
    #define W_OK 2
    #define R_OK 4
#else
    #include <unistd.h>
    #include <dirent.h>
    #define PATH_SEP '/'
    #define mkdir_p(path, mode) mkdir(path, mode)
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS
 * ═══════════════════════════════════════════════════════════════════════════ */

#define MAX_PATH_LEN 4096
#define SETUP_MAGIC 0x534A5354  /* "SJST" - Simjot Setup */
#define SETUP_VERSION 1

/* Directory names */
static const char* SUBDIRS[] = {
    "notebooks",
    "mood",
    "settings",
    "wallpapers"
};
static const int NUM_SUBDIRS = 4;

/* Setup status codes */
typedef enum {
    SETUP_OK = 0,
    SETUP_ERR_NULL_PATH = -1,
    SETUP_ERR_PATH_TOO_LONG = -2,
    SETUP_ERR_CREATE_ROOT = -3,
    SETUP_ERR_CREATE_SUBDIR = -4,
    SETUP_ERR_NOT_WRITABLE = -5,
    SETUP_ERR_VERIFY_FAILED = -6,
    SETUP_ERR_CONFIG_WRITE = -7,
    SETUP_ERR_MARKER_WRITE = -8
} SetupStatus;

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Create a directory with all parent directories.
 * Returns 0 on success, -1 on failure.
 */
static int create_directory_recursive(const char* path) {
    if (path == NULL || path[0] == '\0') return -1;
    
    char tmp[MAX_PATH_LEN];
    size_t len = strlen(path);
    if (len >= MAX_PATH_LEN) return -1;
    
    strncpy(tmp, path, MAX_PATH_LEN - 1);
    tmp[MAX_PATH_LEN - 1] = '\0';
    
    /* Remove trailing separator if present */
    if (len > 0 && (tmp[len - 1] == '/' || tmp[len - 1] == '\\')) {
        tmp[len - 1] = '\0';
    }
    
    /* Check if already exists */
    struct stat st;
    if (stat(tmp, &st) == 0) {
        return (st.st_mode & S_IFDIR) ? 0 : -1;
    }
    
    /* Create parent directories */
    for (char* p = tmp + 1; *p; p++) {
        if (*p == '/' || *p == '\\') {
            *p = '\0';
            if (stat(tmp, &st) != 0) {
                if (mkdir_p(tmp, 0755) != 0 && errno != EEXIST) {
                    return -1;
                }
            }
            *p = PATH_SEP;
        }
    }
    
    /* Create final directory */
    if (mkdir_p(tmp, 0755) != 0 && errno != EEXIST) {
        return -1;
    }
    
    return 0;
}

/**
 * Check if a directory exists and is writable.
 */
static int is_dir_writable(const char* path) {
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    if (!(st.st_mode & S_IFDIR)) return 0;
    return (access(path, W_OK) == 0) ? 1 : 0;
}

/**
 * Write a test file to verify directory is truly writable.
 * More reliable than just checking permissions.
 */
static int verify_writable_by_test(const char* dir_path) {
    char test_path[MAX_PATH_LEN];
    snprintf(test_path, sizeof(test_path), "%s%c.simjot_write_test_%ld", 
             dir_path, PATH_SEP, (long)time(NULL));
    
    FILE* f = fopen(test_path, "w");
    if (f == NULL) return 0;
    
    int written = fprintf(f, "test");
    fclose(f);
    
    /* Clean up test file */
    remove(test_path);
    
    return (written > 0) ? 1 : 0;
}

/**
 * Build a subdirectory path.
 */
static void build_subdir_path(char* out, size_t out_size, const char* root, const char* subdir) {
    snprintf(out, out_size, "%s%c%s", root, PATH_SEP, subdir);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SETUP MARKER FILE
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Structure for setup marker file (binary format).
 */
typedef struct {
    uint32_t magic;
    uint32_t version;
    int64_t setup_timestamp;
    uint32_t dirs_created;
    uint32_t checksum;
} SetupMarker;

/**
 * Calculate simple checksum for marker.
 */
static uint32_t calc_marker_checksum(const SetupMarker* m) {
    uint32_t sum = m->magic ^ m->version;
    sum ^= (uint32_t)(m->setup_timestamp & 0xFFFFFFFF);
    sum ^= (uint32_t)(m->setup_timestamp >> 32);
    sum ^= m->dirs_created;
    return sum;
}

/**
 * Write setup marker file.
 */
static int write_setup_marker(const char* root_path, int dirs_created) {
    char marker_path[MAX_PATH_LEN];
    snprintf(marker_path, sizeof(marker_path), "%s%c.simjot_setup", root_path, PATH_SEP);
    
    SetupMarker marker = {
        .magic = SETUP_MAGIC,
        .version = SETUP_VERSION,
        .setup_timestamp = (int64_t)time(NULL),
        .dirs_created = (uint32_t)dirs_created,
        .checksum = 0
    };
    marker.checksum = calc_marker_checksum(&marker);
    
    FILE* f = fopen(marker_path, "wb");
    if (f == NULL) return -1;
    
    size_t written = fwrite(&marker, sizeof(SetupMarker), 1, f);
    fclose(f);
    
    return (written == 1) ? 0 : -1;
}

/**
 * Read and verify setup marker file.
 * Returns 1 if valid, 0 if invalid/missing.
 */
static int verify_setup_marker(const char* root_path) {
    char marker_path[MAX_PATH_LEN];
    snprintf(marker_path, sizeof(marker_path), "%s%c.simjot_setup", root_path, PATH_SEP);
    
    FILE* f = fopen(marker_path, "rb");
    if (f == NULL) return 0;
    
    SetupMarker marker;
    size_t read = fread(&marker, sizeof(SetupMarker), 1, f);
    fclose(f);
    
    if (read != 1) return 0;
    if (marker.magic != SETUP_MAGIC) return 0;
    if (marker.checksum != calc_marker_checksum(&marker)) return 0;
    
    return 1;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Initialize the Simjot directory structure.
 * Creates root folder and all required subdirectories.
 * 
 * @param root_path Path to the Simjot root folder
 * @return 0 on success, negative error code on failure
 */
int32_t simjot_setup_init(const char* root_path) {
    if (root_path == NULL || root_path[0] == '\0') {
        return SETUP_ERR_NULL_PATH;
    }
    
    size_t len = strlen(root_path);
    if (len >= MAX_PATH_LEN - 64) {
        return SETUP_ERR_PATH_TOO_LONG;
    }
    
    /* Create root directory */
    if (create_directory_recursive(root_path) != 0) {
        return SETUP_ERR_CREATE_ROOT;
    }
    
    /* Create all subdirectories */
    int dirs_created = 0;
    char subdir_path[MAX_PATH_LEN];
    
    for (int i = 0; i < NUM_SUBDIRS; i++) {
        build_subdir_path(subdir_path, sizeof(subdir_path), root_path, SUBDIRS[i]);
        if (create_directory_recursive(subdir_path) != 0) {
            return SETUP_ERR_CREATE_SUBDIR;
        }
        dirs_created++;
    }
    
    /* Write setup marker */
    if (write_setup_marker(root_path, dirs_created) != 0) {
        return SETUP_ERR_MARKER_WRITE;
    }
    
    return SETUP_OK;
}

/**
 * Verify that Simjot setup is complete and valid.
 * Checks all directories exist, are writable, and marker is valid.
 * 
 * @param root_path Path to the Simjot root folder
 * @return Bitmask: bit 0 = root ok, bits 1-4 = subdirs ok, bit 7 = marker ok
 */
int32_t simjot_setup_verify(const char* root_path) {
    if (root_path == NULL || root_path[0] == '\0') {
        return 0;
    }
    
    int32_t result = 0;
    char subdir_path[MAX_PATH_LEN];
    
    /* Check root */
    if (is_dir_writable(root_path)) {
        result |= (1 << 0);
    }
    
    /* Check subdirectories */
    for (int i = 0; i < NUM_SUBDIRS; i++) {
        build_subdir_path(subdir_path, sizeof(subdir_path), root_path, SUBDIRS[i]);
        if (is_dir_writable(subdir_path)) {
            result |= (1 << (i + 1));
        }
    }
    
    /* Check marker */
    if (verify_setup_marker(root_path)) {
        result |= (1 << 7);
    }
    
    return result;
}

/**
 * Check if directory is truly writable by creating a test file.
 * More reliable than permission checks alone.
 * 
 * @param dir_path Directory to test
 * @return 1 if writable, 0 if not
 */
int32_t simjot_verify_writable(const char* dir_path) {
    if (dir_path == NULL || dir_path[0] == '\0') {
        return 0;
    }
    return verify_writable_by_test(dir_path) ? 1 : 0;
}

/**
 * Get detailed setup status.
 * 
 * @param root_path Path to the Simjot root folder
 * @param out_status Array of 8 ints: [root_exists, root_writable, 
 *                   notebooks_ok, mood_ok, settings_ok, wallpapers_ok,
 *                   marker_valid, setup_complete]
 * @return Number of directories that are OK (0-5), or -1 on error
 */
int32_t simjot_setup_status(const char* root_path, int32_t* out_status) {
    if (root_path == NULL || out_status == NULL) {
        return -1;
    }
    
    memset(out_status, 0, 8 * sizeof(int32_t));
    
    struct stat st;
    char subdir_path[MAX_PATH_LEN];
    int ok_count = 0;
    
    /* Root exists */
    out_status[0] = (stat(root_path, &st) == 0 && (st.st_mode & S_IFDIR)) ? 1 : 0;
    
    /* Root writable */
    out_status[1] = is_dir_writable(root_path) ? 1 : 0;
    if (out_status[1]) ok_count++;
    
    /* Check each subdirectory */
    for (int i = 0; i < NUM_SUBDIRS; i++) {
        build_subdir_path(subdir_path, sizeof(subdir_path), root_path, SUBDIRS[i]);
        out_status[2 + i] = is_dir_writable(subdir_path) ? 1 : 0;
        if (out_status[2 + i]) ok_count++;
    }
    
    /* Marker valid */
    out_status[6] = verify_setup_marker(root_path) ? 1 : 0;
    
    /* Setup complete (all dirs ok + marker) */
    out_status[7] = (ok_count == 5 && out_status[6]) ? 1 : 0;
    
    return ok_count;
}

/**
 * Write config file atomically with the root path.
 * 
 * @param config_path Path to config file
 * @param root_path Root folder path to write
 * @return 0 on success, negative on error
 */
int32_t simjot_write_config(const char* config_path, const char* root_path) {
    if (config_path == NULL || root_path == NULL) {
        return SETUP_ERR_NULL_PATH;
    }
    
    /* Write to temp file first */
    char temp_path[MAX_PATH_LEN];
    snprintf(temp_path, sizeof(temp_path), "%s.tmp", config_path);
    
    FILE* f = fopen(temp_path, "w");
    if (f == NULL) {
        return SETUP_ERR_CONFIG_WRITE;
    }
    
    int written = fprintf(f, "%s\n", root_path);
    fflush(f);
    
#ifndef _WIN32
    /* Sync to disk on Unix */
    fsync(fileno(f));
#endif
    
    fclose(f);
    
    if (written <= 0) {
        remove(temp_path);
        return SETUP_ERR_CONFIG_WRITE;
    }
    
    /* Atomic rename */
#ifdef _WIN32
    /* Windows: need to remove target first */
    remove(config_path);
#endif
    
    if (rename(temp_path, config_path) != 0) {
        remove(temp_path);
        return SETUP_ERR_CONFIG_WRITE;
    }
    
    return SETUP_OK;
}

/**
 * Read config file and verify the root path exists.
 * 
 * @param config_path Path to config file
 * @param out_root_path Buffer to receive root path (must be MAX_PATH_LEN)
 * @return 1 if valid config found, 0 if not
 */
int32_t simjot_read_config(const char* config_path, char* out_root_path) {
    if (config_path == NULL || out_root_path == NULL) {
        return 0;
    }
    
    FILE* f = fopen(config_path, "r");
    if (f == NULL) {
        return 0;
    }
    
    char line[MAX_PATH_LEN];
    if (fgets(line, sizeof(line), f) == NULL) {
        fclose(f);
        return 0;
    }
    fclose(f);
    
    /* Trim newline */
    size_t len = strlen(line);
    while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r')) {
        line[--len] = '\0';
    }
    
    if (len == 0) {
        return 0;
    }
    
    /* Verify directory exists */
    struct stat st;
    if (stat(line, &st) != 0 || !(st.st_mode & S_IFDIR)) {
        return 0;
    }
    
    strncpy(out_root_path, line, MAX_PATH_LEN - 1);
    out_root_path[MAX_PATH_LEN - 1] = '\0';
    
    return 1;
}

/**
 * Create a single directory if it doesn't exist.
 * Uses native APIs for reliability.
 * 
 * @param dir_path Directory path to create
 * @return 0 on success (or already exists), negative on error
 */
int32_t simjot_create_directory(const char* dir_path) {
    if (dir_path == NULL || dir_path[0] == '\0') {
        return SETUP_ERR_NULL_PATH;
    }
    
    return create_directory_recursive(dir_path);
}

/**
 * Check if first-time setup is needed.
 * 
 * @param config_path Path to config file
 * @return 1 if setup needed, 0 if already configured
 */
int32_t simjot_needs_setup(const char* config_path) {
    if (config_path == NULL) {
        return 1;
    }
    
    char root_path[MAX_PATH_LEN];
    if (!simjot_read_config(config_path, root_path)) {
        return 1;
    }
    
    /* Verify setup is complete */
    int32_t verify = simjot_setup_verify(root_path);
    
    /* Check root + all 4 subdirs (bits 0-4) */
    int32_t required_bits = 0x1F;  /* bits 0-4 */
    return ((verify & required_bits) == required_bits) ? 0 : 1;
}
