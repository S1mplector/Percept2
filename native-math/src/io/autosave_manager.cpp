/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file autosave_manager.cpp
 * @brief Native Autosave Manager/Coordinator for Simjot
 * 
 * High-performance, thread-safe autosave coordination:
 * - Multi-session dirty tracking
 * - Debounced save scheduling
 * - Atomic file writes (temp + rename)
 * - Crash recovery file detection
 * - Statistics and monitoring
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. MIT License.
 */

#include "../include/simjot_native.h"

#include <cstdint>
#include <cstring>
#include <cstdio>
#include <ctime>
#include <chrono>
#include <mutex>
#include <unordered_map>
#include <string>
#include <atomic>
#include <vector>
#include <algorithm>

#ifdef _WIN32
#include <windows.h>
#include <io.h>
#define PATH_SEP '\\'
#else
#include <unistd.h>
#include <sys/stat.h>
#include <dirent.h>
#define PATH_SEP '/'
#endif

namespace {

// ═══════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════

constexpr int MAX_SESSIONS = 64;
constexpr int MAX_PATH_LEN = 4096;
constexpr const char* RECOVERY_SUFFIX = ".sjrecovery";
constexpr const char* TEMP_SUFFIX = ".sjtmp";

// ═══════════════════════════════════════════════════════════════════════════
// SESSION STATE
// ═══════════════════════════════════════════════════════════════════════════

struct AutosaveSession {
    int32_t id;
    char file_path[MAX_PATH_LEN];
    bool dirty;
    int64_t last_dirty_time_ms;      // When content was last marked dirty
    int64_t last_save_time_ms;       // When last save completed
    int64_t debounce_delay_ms;       // Delay before saving after dirty
    int32_t save_count;              // Total saves for this session
    int32_t pending_save;            // 1 if save is pending
    bool active;
    
    void reset() {
        id = -1;
        file_path[0] = '\0';
        dirty = false;
        last_dirty_time_ms = 0;
        last_save_time_ms = 0;
        debounce_delay_ms = 1500;
        save_count = 0;
        pending_save = 0;
        active = false;
    }
};

struct AutosaveManager {
    std::mutex mutex;
    AutosaveSession sessions[MAX_SESSIONS];
    std::atomic<int32_t> next_id{1};
    int64_t global_save_count{0};
    int64_t last_global_save_ms{0};
    bool initialized{false};
    
    void init() {
        std::lock_guard<std::mutex> lock(mutex);
        if (initialized) return;
        for (int i = 0; i < MAX_SESSIONS; i++) {
            sessions[i].reset();
        }
        initialized = true;
    }
    
    AutosaveSession* find_session(int32_t id) {
        for (int i = 0; i < MAX_SESSIONS; i++) {
            if (sessions[i].active && sessions[i].id == id) {
                return &sessions[i];
            }
        }
        return nullptr;
    }
    
    AutosaveSession* find_by_path(const char* path) {
        for (int i = 0; i < MAX_SESSIONS; i++) {
            if (sessions[i].active && strcmp(sessions[i].file_path, path) == 0) {
                return &sessions[i];
            }
        }
        return nullptr;
    }
    
    AutosaveSession* allocate_session() {
        for (int i = 0; i < MAX_SESSIONS; i++) {
            if (!sessions[i].active) {
                sessions[i].reset();
                sessions[i].active = true;
                sessions[i].id = next_id.fetch_add(1);
                return &sessions[i];
            }
        }
        return nullptr;
    }
};

AutosaveManager g_manager;

// ═══════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════

int64_t current_time_ms() {
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch());
    return ms.count();
}

bool file_exists(const char* path) {
#ifdef _WIN32
    return GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES;
#else
    struct stat st;
    return stat(path, &st) == 0;
#endif
}

bool atomic_write_file(const char* target_path, const uint8_t* data, size_t len) {
    if (!target_path || !data) return false;
    
    // Create temp file path
    char temp_path[MAX_PATH_LEN];
    snprintf(temp_path, sizeof(temp_path), "%s%s", target_path, TEMP_SUFFIX);
    
    // Write to temp file
    FILE* f = fopen(temp_path, "wb");
    if (!f) return false;
    
    size_t written = fwrite(data, 1, len, f);
    int flush_result = fflush(f);
    
#ifndef _WIN32
    // fsync on Unix for durability
    int fd = fileno(f);
    if (fd >= 0) fsync(fd);
#endif
    
    fclose(f);
    
    if (written != len || flush_result != 0) {
        remove(temp_path);
        return false;
    }
    
    // Atomic rename
#ifdef _WIN32
    // Windows: need to delete target first if exists
    if (file_exists(target_path)) {
        if (!DeleteFileA(target_path)) {
            remove(temp_path);
            return false;
        }
    }
    if (!MoveFileA(temp_path, target_path)) {
        remove(temp_path);
        return false;
    }
#else
    if (rename(temp_path, target_path) != 0) {
        remove(temp_path);
        return false;
    }
#endif
    
    return true;
}

void build_recovery_path(const char* original, char* recovery_path, size_t max_len) {
    snprintf(recovery_path, max_len, "%s%s", original, RECOVERY_SUFFIX);
}

} // anonymous namespace

extern "C" {

// ═══════════════════════════════════════════════════════════════════════════
// INITIALIZATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Initialize the autosave manager (call once at startup)
 * @return 1 on success, 0 if already initialized
 */
int32_t simjot_autosave_init(void) {
    g_manager.init();
    return 1;
}

// ═══════════════════════════════════════════════════════════════════════════
// SESSION MANAGEMENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Create a new autosave session for a document
 * @param file_path Path to the document file
 * @param debounce_ms Debounce delay in milliseconds (0 = use default 1500ms)
 * @return Session ID (>0), or -1 on error
 */
int32_t simjot_autosave_create_session(const char* file_path, int32_t debounce_ms) {
    if (!file_path || strlen(file_path) >= MAX_PATH_LEN - 32) return -1;
    
    g_manager.init();
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    
    // Check if session already exists for this path
    AutosaveSession* existing = g_manager.find_by_path(file_path);
    if (existing) {
        return existing->id;  // Return existing session
    }
    
    AutosaveSession* session = g_manager.allocate_session();
    if (!session) return -1;
    
    strncpy(session->file_path, file_path, MAX_PATH_LEN - 1);
    session->file_path[MAX_PATH_LEN - 1] = '\0';
    session->debounce_delay_ms = debounce_ms > 0 ? debounce_ms : 1500;
    session->last_save_time_ms = current_time_ms();
    
    return session->id;
}

/**
 * @brief Destroy an autosave session
 * @param session_id Session ID to destroy
 * @return 1 on success, 0 if not found
 */
int32_t simjot_autosave_destroy_session(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return 0;
    
    session->reset();
    return 1;
}

/**
 * @brief Update file path for a session (e.g., after "Save As")
 * @return 1 on success, 0 if session not found
 */
int32_t simjot_autosave_set_path(int32_t session_id, const char* new_path) {
    if (!new_path || strlen(new_path) >= MAX_PATH_LEN - 32) return 0;
    
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return 0;
    
    strncpy(session->file_path, new_path, MAX_PATH_LEN - 1);
    session->file_path[MAX_PATH_LEN - 1] = '\0';
    return 1;
}

// ═══════════════════════════════════════════════════════════════════════════
// DIRTY TRACKING
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Mark a session as dirty (content changed)
 * @return 1 on success, 0 if session not found
 */
int32_t simjot_autosave_mark_dirty(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return 0;
    
    session->dirty = true;
    session->last_dirty_time_ms = current_time_ms();
    session->pending_save = 1;
    return 1;
}

/**
 * @brief Mark session as clean (after successful save)
 * @return 1 on success, 0 if session not found
 */
int32_t simjot_autosave_mark_clean(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return 0;
    
    session->dirty = false;
    session->pending_save = 0;
    session->last_save_time_ms = current_time_ms();
    session->save_count++;
    g_manager.global_save_count++;
    g_manager.last_global_save_ms = session->last_save_time_ms;
    return 1;
}

/**
 * @brief Check if session is dirty
 * @return 1 if dirty, 0 if clean or not found
 */
int32_t simjot_autosave_is_dirty(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    return session && session->dirty ? 1 : 0;
}

// ═══════════════════════════════════════════════════════════════════════════
// SAVE SCHEDULING
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Check if a session should save now (debounce elapsed)
 * @return 1 if should save, 0 if not ready or not dirty
 */
int32_t simjot_autosave_should_save(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session || !session->dirty || !session->pending_save) return 0;
    
    int64_t now = current_time_ms();
    int64_t elapsed = now - session->last_dirty_time_ms;
    
    return elapsed >= session->debounce_delay_ms ? 1 : 0;
}

/**
 * @brief Get milliseconds until next save should occur
 * @return ms until save (0 if ready now), -1 if not dirty/not found
 */
int64_t simjot_autosave_ms_until_save(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session || !session->dirty) return -1;
    
    int64_t now = current_time_ms();
    int64_t elapsed = now - session->last_dirty_time_ms;
    int64_t remaining = session->debounce_delay_ms - elapsed;
    
    return remaining > 0 ? remaining : 0;
}

/**
 * @brief Get all session IDs that should save now
 * @param out_ids Output array for session IDs
 * @param max_ids Maximum IDs to return
 * @return Number of sessions ready to save
 */
int32_t simjot_autosave_get_pending(int32_t* out_ids, int32_t max_ids) {
    if (!out_ids || max_ids <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    int64_t now = current_time_ms();
    int32_t count = 0;
    
    for (int i = 0; i < MAX_SESSIONS && count < max_ids; i++) {
        AutosaveSession& s = g_manager.sessions[i];
        if (s.active && s.dirty && s.pending_save) {
            int64_t elapsed = now - s.last_dirty_time_ms;
            if (elapsed >= s.debounce_delay_ms) {
                out_ids[count++] = s.id;
            }
        }
    }
    
    return count;
}

// ═══════════════════════════════════════════════════════════════════════════
// ATOMIC FILE OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Atomically write data to session's file (temp + rename)
 * @param session_id Session ID
 * @param data Data to write
 * @param data_len Length of data
 * @return 1 on success, 0 on error
 */
int32_t simjot_autosave_write_atomic(int32_t session_id, const uint8_t* data, int32_t data_len) {
    if (!data || data_len < 0) return 0;
    
    char path[MAX_PATH_LEN];
    {
        std::lock_guard<std::mutex> lock(g_manager.mutex);
        AutosaveSession* session = g_manager.find_session(session_id);
        if (!session) return 0;
        strncpy(path, session->file_path, MAX_PATH_LEN);
    }
    
    if (atomic_write_file(path, data, static_cast<size_t>(data_len))) {
        simjot_autosave_mark_clean(session_id);
        return 1;
    }
    return 0;
}

/**
 * @brief Write recovery file for crash protection
 * @return 1 on success, 0 on error
 */
int32_t simjot_autosave_write_recovery(int32_t session_id, const uint8_t* data, int32_t data_len) {
    if (!data || data_len < 0) return 0;
    
    char path[MAX_PATH_LEN];
    char recovery_path[MAX_PATH_LEN];
    {
        std::lock_guard<std::mutex> lock(g_manager.mutex);
        AutosaveSession* session = g_manager.find_session(session_id);
        if (!session) return 0;
        strncpy(path, session->file_path, MAX_PATH_LEN);
    }
    
    build_recovery_path(path, recovery_path, MAX_PATH_LEN);
    
    FILE* f = fopen(recovery_path, "wb");
    if (!f) return 0;
    
    size_t written = fwrite(data, 1, static_cast<size_t>(data_len), f);
    fflush(f);
    fclose(f);
    
    return written == static_cast<size_t>(data_len) ? 1 : 0;
}

/**
 * @brief Delete recovery file after successful save
 * @return 1 if deleted, 0 if not found or error
 */
int32_t simjot_autosave_delete_recovery(int32_t session_id) {
    char path[MAX_PATH_LEN];
    char recovery_path[MAX_PATH_LEN];
    {
        std::lock_guard<std::mutex> lock(g_manager.mutex);
        AutosaveSession* session = g_manager.find_session(session_id);
        if (!session) return 0;
        strncpy(path, session->file_path, MAX_PATH_LEN);
    }
    
    build_recovery_path(path, recovery_path, MAX_PATH_LEN);
    return remove(recovery_path) == 0 ? 1 : 0;
}

// ═══════════════════════════════════════════════════════════════════════════
// RECOVERY DETECTION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Check if a recovery file exists for the given path
 * @param file_path Original file path
 * @return 1 if recovery exists, 0 if not
 */
int32_t simjot_autosave_has_recovery(const char* file_path) {
    if (!file_path) return 0;
    
    char recovery_path[MAX_PATH_LEN];
    build_recovery_path(file_path, recovery_path, MAX_PATH_LEN);
    return file_exists(recovery_path) ? 1 : 0;
}

/**
 * @brief Get recovery file path for a document
 * @param file_path Original file path
 * @param out Output buffer for recovery path
 * @param out_len Output buffer length
 * @return Length of recovery path, or -1 on error
 */
int32_t simjot_autosave_get_recovery_path(const char* file_path, char* out, int32_t out_len) {
    if (!file_path || !out || out_len <= 0) return -1;
    
    char recovery_path[MAX_PATH_LEN];
    build_recovery_path(file_path, recovery_path, MAX_PATH_LEN);
    
    int len = static_cast<int>(strlen(recovery_path));
    if (len >= out_len) return -1;
    
    strncpy(out, recovery_path, out_len);
    return len;
}

// ═══════════════════════════════════════════════════════════════════════════
// STATISTICS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * @brief Get session statistics
 * @param session_id Session ID
 * @param out_save_count Output: total saves
 * @param out_last_save_ms Output: last save time (epoch ms)
 * @param out_last_dirty_ms Output: last dirty time (epoch ms)
 * @return 1 on success, 0 if session not found
 */
int32_t simjot_autosave_get_stats(int32_t session_id, 
                                   int32_t* out_save_count,
                                   int64_t* out_last_save_ms,
                                   int64_t* out_last_dirty_ms) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return 0;
    
    if (out_save_count) *out_save_count = session->save_count;
    if (out_last_save_ms) *out_last_save_ms = session->last_save_time_ms;
    if (out_last_dirty_ms) *out_last_dirty_ms = session->last_dirty_time_ms;
    return 1;
}

/**
 * @brief Get global autosave statistics
 * @param out_total_saves Output: total saves across all sessions
 * @param out_active_sessions Output: number of active sessions
 * @param out_dirty_sessions Output: number of dirty sessions
 */
void simjot_autosave_get_global_stats(int64_t* out_total_saves,
                                       int32_t* out_active_sessions,
                                       int32_t* out_dirty_sessions) {
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    
    int32_t active = 0, dirty = 0;
    for (int i = 0; i < MAX_SESSIONS; i++) {
        if (g_manager.sessions[i].active) {
            active++;
            if (g_manager.sessions[i].dirty) dirty++;
        }
    }
    
    if (out_total_saves) *out_total_saves = g_manager.global_save_count;
    if (out_active_sessions) *out_active_sessions = active;
    if (out_dirty_sessions) *out_dirty_sessions = dirty;
}

/**
 * @brief Get file path for a session
 * @return Length of path, or -1 on error
 */
int32_t simjot_autosave_get_path(int32_t session_id, char* out, int32_t out_len) {
    if (!out || out_len <= 0) return -1;
    
    std::lock_guard<std::mutex> lock(g_manager.mutex);
    AutosaveSession* session = g_manager.find_session(session_id);
    if (!session) return -1;
    
    int len = static_cast<int>(strlen(session->file_path));
    if (len >= out_len) return -1;
    
    strncpy(out, session->file_path, out_len);
    return len;
}

} // extern "C"
