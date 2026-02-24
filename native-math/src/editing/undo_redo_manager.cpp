/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file undo_redo_manager.cpp
 * @brief High-performance undo/redo manager for text editing
 * 
 * Features:
 * - Session-based undo stacks (multiple independent editors)
 * - Text delta storage for memory efficiency
 * - Save point tracking for dirty state detection
 * - Configurable history limits
 * - Thread-safe operations with fine-grained locking
 * - Coalescing of rapid successive edits
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdint>
#include <cstring>
#include <vector>
#include <string>
#include <deque>
#include <unordered_map>
#include <mutex>
#include <chrono>
#include <algorithm>

/* ═══════════════════════════════════════════════════════════════════════════
 * CONSTANTS AND CONFIGURATION
 * ═══════════════════════════════════════════════════════════════════════════ */

static constexpr int32_t DEFAULT_HISTORY_LIMIT = 1000;
static constexpr int32_t MAX_SESSIONS = 256;
static constexpr int64_t COALESCE_THRESHOLD_MS = 500;  // Coalesce edits within 500ms

/* ═══════════════════════════════════════════════════════════════════════════
 * EDIT TYPES AND STRUCTURES
 * ═══════════════════════════════════════════════════════════════════════════ */

enum class EditType : int32_t {
    INSERT = 1,
    DELETE = 2,
    REPLACE = 3,
    STYLE_CHANGE = 4,
    COMPOUND = 5  // Multiple edits grouped together
};

/**
 * Represents a single undoable edit operation.
 * Uses delta storage - only stores the changed portion.
 */
struct EditRecord {
    EditType type;
    int32_t offset;           // Position in document
    int32_t length;           // Length of affected region
    std::string old_text;     // Text before edit (for undo)
    std::string new_text;     // Text after edit (for redo)
    int64_t timestamp;        // When the edit occurred
    int32_t style_flags;      // For style changes (bold, italic, etc.)
    
    // For compound edits
    std::vector<EditRecord> sub_edits;
    
    EditRecord() : type(EditType::INSERT), offset(0), length(0), 
                   timestamp(0), style_flags(0) {}
    
    // Memory estimate for this record
    size_t memory_size() const {
        size_t size = sizeof(EditRecord);
        size += old_text.capacity();
        size += new_text.capacity();
        for (const auto& sub : sub_edits) {
            size += sub.memory_size();
        }
        return size;
    }
};

/**
 * Undo/redo session for a single editor instance.
 */
struct UndoSession {
    int32_t id;
    std::deque<EditRecord> undo_stack;
    std::deque<EditRecord> redo_stack;
    int32_t history_limit;
    int32_t change_index;     // Current position in history
    int32_t save_point;       // Position when last saved
    int64_t last_edit_time;   // For coalescing
    bool in_compound;         // Currently building compound edit
    EditRecord compound_edit; // Accumulating compound edit
    std::mutex mutex;
    
    UndoSession() : id(-1), history_limit(DEFAULT_HISTORY_LIMIT),
                    change_index(0), save_point(0), last_edit_time(0),
                    in_compound(false) {}
};

/* ═══════════════════════════════════════════════════════════════════════════
 * GLOBAL STATE
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::unordered_map<int32_t, UndoSession*> g_sessions;
static std::mutex g_global_mutex;
static int32_t g_next_session_id = 1;
static bool g_initialized = false;

/* ═══════════════════════════════════════════════════════════════════════════
 * UTILITY FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static int64_t current_time_ms() {
    auto now = std::chrono::steady_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch());
    return ms.count();
}

static UndoSession* get_session(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    auto it = g_sessions.find(session_id);
    if (it == g_sessions.end()) return nullptr;
    return it->second;
}

/**
 * Check if two edits can be coalesced (merged).
 * Edits are coalesced if they're:
 * - Same type (INSERT or DELETE)
 * - Within time threshold
 * - Adjacent in document position
 */
static bool can_coalesce(const EditRecord& prev, const EditRecord& curr) {
    if (prev.type != curr.type) return false;
    if (curr.timestamp - prev.timestamp > COALESCE_THRESHOLD_MS) return false;
    
    if (prev.type == EditType::INSERT) {
        // Can coalesce if new insert is right after previous
        return curr.offset == prev.offset + static_cast<int32_t>(prev.new_text.length());
    } else if (prev.type == EditType::DELETE) {
        // Can coalesce if deleting backwards (backspace) or forwards (delete key)
        return curr.offset == prev.offset || 
               curr.offset + curr.length == prev.offset;
    }
    
    return false;
}

/**
 * Merge two coalesced edits.
 */
static void coalesce_edits(EditRecord& prev, const EditRecord& curr) {
    if (prev.type == EditType::INSERT) {
        prev.new_text += curr.new_text;
        prev.length += curr.length;
    } else if (prev.type == EditType::DELETE) {
        if (curr.offset < prev.offset) {
            // Backspace: prepend
            prev.old_text = curr.old_text + prev.old_text;
            prev.offset = curr.offset;
        } else {
            // Forward delete: append
            prev.old_text += curr.old_text;
        }
        prev.length += curr.length;
    }
    prev.timestamp = curr.timestamp;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

int32_t simjot_undo_init(void) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    if (g_initialized) return 1;
    
    g_sessions.clear();
    g_next_session_id = 1;
    g_initialized = true;
    return 1;
}

void simjot_undo_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    
    for (auto& pair : g_sessions) {
        delete pair.second;
    }
    g_sessions.clear();
    g_initialized = false;
}

int32_t simjot_undo_create_session(int32_t history_limit) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    
    if (!g_initialized) simjot_undo_init();
    if (g_sessions.size() >= MAX_SESSIONS) return -1;
    
    int32_t id = g_next_session_id++;
    UndoSession* session = new UndoSession();
    session->id = id;
    session->history_limit = history_limit > 0 ? history_limit : DEFAULT_HISTORY_LIMIT;
    
    g_sessions[id] = session;
    return id;
}

int32_t simjot_undo_destroy_session(int32_t session_id) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    
    auto it = g_sessions.find(session_id);
    if (it == g_sessions.end()) return 0;
    
    delete it->second;
    g_sessions.erase(it);
    return 1;
}

int32_t simjot_undo_clear(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    session->undo_stack.clear();
    session->redo_stack.clear();
    session->change_index = 0;
    session->save_point = 0;
    session->in_compound = false;
    session->compound_edit = EditRecord();
    return 1;
}

int32_t simjot_undo_push_insert(int32_t session_id, int32_t offset, 
                                 const char* text, int32_t text_len) {
    if (!text || text_len <= 0) return 0;
    
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    EditRecord edit;
    edit.type = EditType::INSERT;
    edit.offset = offset;
    edit.length = text_len;
    edit.new_text = std::string(text, text_len);
    edit.timestamp = current_time_ms();
    
    // Handle compound edit mode
    if (session->in_compound) {
        session->compound_edit.sub_edits.push_back(std::move(edit));
        return 1;
    }
    
    // Try to coalesce with previous edit
    if (!session->undo_stack.empty() && 
        can_coalesce(session->undo_stack.back(), edit)) {
        coalesce_edits(session->undo_stack.back(), edit);
    } else {
        // Clear redo stack when new edit is made
        session->redo_stack.clear();
        
        // Enforce history limit
        while (session->undo_stack.size() >= 
               static_cast<size_t>(session->history_limit)) {
            session->undo_stack.pop_front();
            if (session->save_point > 0) session->save_point--;
        }
        
        session->undo_stack.push_back(std::move(edit));
    }
    
    session->change_index++;
    session->last_edit_time = current_time_ms();
    return 1;
}

int32_t simjot_undo_push_delete(int32_t session_id, int32_t offset,
                                 const char* deleted_text, int32_t text_len) {
    if (!deleted_text || text_len <= 0) return 0;
    
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    EditRecord edit;
    edit.type = EditType::DELETE;
    edit.offset = offset;
    edit.length = text_len;
    edit.old_text = std::string(deleted_text, text_len);
    edit.timestamp = current_time_ms();
    
    if (session->in_compound) {
        session->compound_edit.sub_edits.push_back(std::move(edit));
        return 1;
    }
    
    if (!session->undo_stack.empty() && 
        can_coalesce(session->undo_stack.back(), edit)) {
        coalesce_edits(session->undo_stack.back(), edit);
    } else {
        session->redo_stack.clear();
        
        while (session->undo_stack.size() >= 
               static_cast<size_t>(session->history_limit)) {
            session->undo_stack.pop_front();
            if (session->save_point > 0) session->save_point--;
        }
        
        session->undo_stack.push_back(std::move(edit));
    }
    
    session->change_index++;
    session->last_edit_time = current_time_ms();
    return 1;
}

int32_t simjot_undo_push_replace(int32_t session_id, int32_t offset,
                                  const char* old_text, int32_t old_len,
                                  const char* new_text, int32_t new_len) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    EditRecord edit;
    edit.type = EditType::REPLACE;
    edit.offset = offset;
    edit.length = old_len;
    if (old_text && old_len > 0) {
        edit.old_text = std::string(old_text, old_len);
    }
    if (new_text && new_len > 0) {
        edit.new_text = std::string(new_text, new_len);
    }
    edit.timestamp = current_time_ms();
    
    if (session->in_compound) {
        session->compound_edit.sub_edits.push_back(std::move(edit));
        return 1;
    }
    
    session->redo_stack.clear();
    
    while (session->undo_stack.size() >= 
           static_cast<size_t>(session->history_limit)) {
        session->undo_stack.pop_front();
        if (session->save_point > 0) session->save_point--;
    }
    
    session->undo_stack.push_back(std::move(edit));
    session->change_index++;
    session->last_edit_time = current_time_ms();
    return 1;
}

int32_t simjot_undo_push_style(int32_t session_id, int32_t offset,
                                int32_t length, int32_t style_flags) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    EditRecord edit;
    edit.type = EditType::STYLE_CHANGE;
    edit.offset = offset;
    edit.length = length;
    edit.style_flags = style_flags;
    edit.timestamp = current_time_ms();
    
    if (session->in_compound) {
        session->compound_edit.sub_edits.push_back(std::move(edit));
        return 1;
    }
    
    session->redo_stack.clear();
    
    while (session->undo_stack.size() >= 
           static_cast<size_t>(session->history_limit)) {
        session->undo_stack.pop_front();
        if (session->save_point > 0) session->save_point--;
    }
    
    session->undo_stack.push_back(std::move(edit));
    session->change_index++;
    return 1;
}

int32_t simjot_undo_begin_compound(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (session->in_compound) return 0;  // Already in compound
    
    session->in_compound = true;
    session->compound_edit = EditRecord();
    session->compound_edit.type = EditType::COMPOUND;
    session->compound_edit.timestamp = current_time_ms();
    return 1;
}

int32_t simjot_undo_end_compound(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (!session->in_compound) return 0;
    
    session->in_compound = false;
    
    if (!session->compound_edit.sub_edits.empty()) {
        session->redo_stack.clear();
        
        while (session->undo_stack.size() >= 
               static_cast<size_t>(session->history_limit)) {
            session->undo_stack.pop_front();
            if (session->save_point > 0) session->save_point--;
        }
        
        session->undo_stack.push_back(std::move(session->compound_edit));
        session->change_index++;
    }
    
    session->compound_edit = EditRecord();
    return 1;
}

int32_t simjot_undo_can_undo(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return session->undo_stack.empty() ? 0 : 1;
}

int32_t simjot_undo_can_redo(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return session->redo_stack.empty() ? 0 : 1;
}

int32_t simjot_undo_peek(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (session->undo_stack.empty()) return 0;
    
    const EditRecord& edit = session->undo_stack.back();
    
    if (out_type) *out_type = static_cast<int32_t>(edit.type);
    if (out_offset) *out_offset = edit.offset;
    if (out_length) *out_length = edit.length;
    
    if (out_text && out_text_len > 0) {
        // For undo, return the old text (what to restore)
        const std::string& text = (edit.type == EditType::INSERT) ? 
                                   edit.new_text : edit.old_text;
        int32_t copy_len = std::min(out_text_len - 1, 
                                     static_cast<int32_t>(text.length()));
        if (copy_len > 0) {
            std::memcpy(out_text, text.c_str(), copy_len);
        }
        out_text[copy_len] = '\0';
    }
    
    return 1;
}

int32_t simjot_undo_undo(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (session->undo_stack.empty()) return 0;
    
    EditRecord edit = std::move(session->undo_stack.back());
    session->undo_stack.pop_back();
    
    if (out_type) *out_type = static_cast<int32_t>(edit.type);
    if (out_offset) *out_offset = edit.offset;
    
    // Return the text to restore
    const std::string* restore_text = nullptr;
    int32_t restore_len = 0;
    
    switch (edit.type) {
        case EditType::INSERT:
            // Undo insert = delete the inserted text
            if (out_length) *out_length = static_cast<int32_t>(edit.new_text.length());
            restore_text = &edit.new_text;
            restore_len = static_cast<int32_t>(edit.new_text.length());
            break;
            
        case EditType::DELETE:
            // Undo delete = restore the deleted text
            if (out_length) *out_length = static_cast<int32_t>(edit.old_text.length());
            restore_text = &edit.old_text;
            restore_len = static_cast<int32_t>(edit.old_text.length());
            break;
            
        case EditType::REPLACE:
            if (out_length) *out_length = static_cast<int32_t>(edit.old_text.length());
            restore_text = &edit.old_text;
            restore_len = static_cast<int32_t>(edit.old_text.length());
            break;
            
        case EditType::STYLE_CHANGE:
            if (out_length) *out_length = edit.length;
            break;
            
        case EditType::COMPOUND:
            if (out_length) *out_length = static_cast<int32_t>(edit.sub_edits.size());
            break;
    }
    
    if (out_text && out_text_len > 0 && restore_text) {
        int32_t copy_len = std::min(out_text_len - 1, restore_len);
        if (copy_len > 0) {
            std::memcpy(out_text, restore_text->c_str(), copy_len);
        }
        out_text[copy_len] = '\0';
    }
    
    session->redo_stack.push_back(std::move(edit));
    session->change_index--;
    return 1;
}

int32_t simjot_undo_redo(int32_t session_id, int32_t* out_type,
                          int32_t* out_offset, int32_t* out_length,
                          char* out_text, int32_t out_text_len) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (session->redo_stack.empty()) return 0;
    
    EditRecord edit = std::move(session->redo_stack.back());
    session->redo_stack.pop_back();
    
    if (out_type) *out_type = static_cast<int32_t>(edit.type);
    if (out_offset) *out_offset = edit.offset;
    
    // Return the text to apply
    const std::string* apply_text = nullptr;
    int32_t apply_len = 0;
    
    switch (edit.type) {
        case EditType::INSERT:
            // Redo insert = insert the text again
            if (out_length) *out_length = static_cast<int32_t>(edit.new_text.length());
            apply_text = &edit.new_text;
            apply_len = static_cast<int32_t>(edit.new_text.length());
            break;
            
        case EditType::DELETE:
            // Redo delete = delete again
            if (out_length) *out_length = static_cast<int32_t>(edit.old_text.length());
            apply_text = &edit.old_text;
            apply_len = static_cast<int32_t>(edit.old_text.length());
            break;
            
        case EditType::REPLACE:
            if (out_length) *out_length = static_cast<int32_t>(edit.new_text.length());
            apply_text = &edit.new_text;
            apply_len = static_cast<int32_t>(edit.new_text.length());
            break;
            
        case EditType::STYLE_CHANGE:
            if (out_length) *out_length = edit.length;
            break;
            
        case EditType::COMPOUND:
            if (out_length) *out_length = static_cast<int32_t>(edit.sub_edits.size());
            break;
    }
    
    if (out_text && out_text_len > 0 && apply_text) {
        int32_t copy_len = std::min(out_text_len - 1, apply_len);
        if (copy_len > 0) {
            std::memcpy(out_text, apply_text->c_str(), copy_len);
        }
        out_text[copy_len] = '\0';
    }
    
    session->undo_stack.push_back(std::move(edit));
    session->change_index++;
    return 1;
}

int32_t simjot_undo_mark_save_point(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    session->save_point = session->change_index;
    return 1;
}

int32_t simjot_undo_is_at_save_point(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return (session->change_index == session->save_point) ? 1 : 0;
}

int32_t simjot_undo_is_dirty(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return (session->change_index != session->save_point) ? 1 : 0;
}

int32_t simjot_undo_get_undo_count(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return static_cast<int32_t>(session->undo_stack.size());
}

int32_t simjot_undo_get_redo_count(int32_t session_id) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    return static_cast<int32_t>(session->redo_stack.size());
}

int32_t simjot_undo_set_history_limit(int32_t session_id, int32_t limit) {
    if (limit <= 0) return 0;
    
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    session->history_limit = limit;
    
    // Trim if over limit
    while (session->undo_stack.size() > static_cast<size_t>(limit)) {
        session->undo_stack.pop_front();
        if (session->save_point > 0) session->save_point--;
    }
    
    return 1;
}

int32_t simjot_undo_get_stats(int32_t session_id, int64_t* out_memory,
                               int32_t* out_undo_count, int32_t* out_redo_count,
                               int32_t* out_save_point, int32_t* out_change_index) {
    UndoSession* session = get_session(session_id);
    if (!session) return 0;
    
    std::lock_guard<std::mutex> lock(session->mutex);
    
    if (out_undo_count) *out_undo_count = static_cast<int32_t>(session->undo_stack.size());
    if (out_redo_count) *out_redo_count = static_cast<int32_t>(session->redo_stack.size());
    if (out_save_point) *out_save_point = session->save_point;
    if (out_change_index) *out_change_index = session->change_index;
    
    if (out_memory) {
        size_t mem = sizeof(UndoSession);
        for (const auto& edit : session->undo_stack) {
            mem += edit.memory_size();
        }
        for (const auto& edit : session->redo_stack) {
            mem += edit.memory_size();
        }
        *out_memory = static_cast<int64_t>(mem);
    }
    
    return 1;
}

int32_t simjot_undo_session_count(void) {
    std::lock_guard<std::mutex> lock(g_global_mutex);
    return static_cast<int32_t>(g_sessions.size());
}

} /* extern "C" */
