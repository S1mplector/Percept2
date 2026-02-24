/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

/**
 * @file hotkey_manager.cpp
 * @brief Fast OS-aware hotkey detection for text formatting
 * 
 * High-performance hotkey manager optimized for editor panels:
 * - Compile-time OS detection
 * - O(1) hotkey lookup using lookup tables
 * - Platform-appropriate modifier keys (Cmd on macOS, Ctrl on Win/Linux)
 * - Human-readable display strings with proper symbols
 * 
 * Supported actions:
 * - Bold: Cmd/Ctrl + B
 * - Italic: Cmd/Ctrl + I
 * - Underline: Cmd/Ctrl + U
 * - Strikethrough: Cmd/Ctrl + Shift + S
 * 
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdint>
#include <cstring>
#include <cctype>

/* ═══════════════════════════════════════════════════════════════════════════
 * COMPILE-TIME PLATFORM DETECTION
 * ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__APPLE__) && defined(__MACH__)
    #define SIMJOT_DETECTED_PLATFORM SIMJOT_PLATFORM_MACOS
    #define SIMJOT_PRIMARY_MOD SIMJOT_MOD_META
#elif defined(_WIN32) || defined(_WIN64)
    #define SIMJOT_DETECTED_PLATFORM SIMJOT_PLATFORM_WINDOWS
    #define SIMJOT_PRIMARY_MOD SIMJOT_MOD_CTRL
#elif defined(__linux__)
    #define SIMJOT_DETECTED_PLATFORM SIMJOT_PLATFORM_LINUX
    #define SIMJOT_PRIMARY_MOD SIMJOT_MOD_CTRL
#else
    #define SIMJOT_DETECTED_PLATFORM SIMJOT_PLATFORM_UNKNOWN
    #define SIMJOT_PRIMARY_MOD SIMJOT_MOD_CTRL
#endif

/* ═══════════════════════════════════════════════════════════════════════════
 * HOTKEY BINDING DEFINITIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

struct HotkeyBinding {
    int32_t action;
    int32_t key_code;      // ASCII uppercase letter
    int32_t extra_mods;    // Additional modifiers beyond primary (e.g., SHIFT)
};

// Standard text formatting hotkeys
// Primary modifier (Cmd/Ctrl) is implicit
static constexpr HotkeyBinding g_bindings[] = {
    { SIMJOT_ACTION_BOLD,          'B', SIMJOT_MOD_NONE },
    { SIMJOT_ACTION_ITALIC,        'I', SIMJOT_MOD_NONE },
    { SIMJOT_ACTION_UNDERLINE,     'U', SIMJOT_MOD_NONE },
    { SIMJOT_ACTION_STRIKETHROUGH, 'S', SIMJOT_MOD_SHIFT },
};

static constexpr int32_t g_binding_count = sizeof(g_bindings) / sizeof(g_bindings[0]);

/* ═══════════════════════════════════════════════════════════════════════════
 * FAST LOOKUP TABLE
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * For O(1) lookup, we use a 256-entry table indexed by key code.
 * Each entry stores the action and required extra modifiers.
 * This is faster than linear search for high-frequency hotkey checking.
 */

struct LookupEntry {
    int32_t action;
    int32_t extra_mods;
};

// Lookup table: index by uppercase ASCII key code
static LookupEntry g_lookup_table[256];
static bool g_lookup_initialized = false;

static void init_lookup_table() {
    if (g_lookup_initialized) return;
    
    // Clear table
    for (int i = 0; i < 256; i++) {
        g_lookup_table[i].action = SIMJOT_ACTION_NONE;
        g_lookup_table[i].extra_mods = 0;
    }
    
    // Populate from bindings
    for (int i = 0; i < g_binding_count; i++) {
        int key = g_bindings[i].key_code;
        if (key >= 0 && key < 256) {
            g_lookup_table[key].action = g_bindings[i].action;
            g_lookup_table[key].extra_mods = g_bindings[i].extra_mods;
            
            // Also map lowercase
            int lower = std::tolower(key);
            if (lower != key && lower >= 0 && lower < 256) {
                g_lookup_table[lower].action = g_bindings[i].action;
                g_lookup_table[lower].extra_mods = g_bindings[i].extra_mods;
            }
        }
    }
    
    g_lookup_initialized = true;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

int32_t simjot_hotkey_get_platform(void) {
    return SIMJOT_DETECTED_PLATFORM;
}

int32_t simjot_hotkey_get_primary_modifier(void) {
    return SIMJOT_PRIMARY_MOD;
}

int32_t simjot_hotkey_check(int32_t key_code, int32_t modifiers) {
    init_lookup_table();
    
    // Bounds check
    if (key_code < 0 || key_code >= 256) {
        return SIMJOT_ACTION_NONE;
    }
    
    // Check if primary modifier is pressed
    if (!(modifiers & SIMJOT_PRIMARY_MOD)) {
        return SIMJOT_ACTION_NONE;
    }
    
    const LookupEntry& entry = g_lookup_table[key_code];
    if (entry.action == SIMJOT_ACTION_NONE) {
        return SIMJOT_ACTION_NONE;
    }
    
    // Check extra modifiers match exactly
    // We need the primary mod + any extra mods, but no other mods
    int32_t required_mods = SIMJOT_PRIMARY_MOD | entry.extra_mods;
    
    // Mask out irrelevant bits for comparison
    // Allow the key to work regardless of caps lock, num lock, etc.
    int32_t relevant_mods = modifiers & (SIMJOT_MOD_SHIFT | SIMJOT_MOD_CTRL | 
                                          SIMJOT_MOD_ALT | SIMJOT_MOD_META);
    
    if (relevant_mods == required_mods) {
        return entry.action;
    }
    
    return SIMJOT_ACTION_NONE;
}

int32_t simjot_hotkey_get_binding(int32_t action, int32_t* out_key_code, int32_t* out_modifiers) {
    if (action < SIMJOT_ACTION_BOLD || action > SIMJOT_ACTION_STRIKETHROUGH) {
        return 0;
    }
    
    for (int i = 0; i < g_binding_count; i++) {
        if (g_bindings[i].action == action) {
            if (out_key_code) *out_key_code = g_bindings[i].key_code;
            if (out_modifiers) *out_modifiers = SIMJOT_PRIMARY_MOD | g_bindings[i].extra_mods;
            return 1;
        }
    }
    
    return 0;
}

int32_t simjot_hotkey_get_display_string(int32_t action, char* out, int32_t out_len) {
    if (!out || out_len <= 0) return -1;
    
    int32_t key_code = 0;
    int32_t modifiers = 0;
    
    if (!simjot_hotkey_get_binding(action, &key_code, &modifiers)) {
        out[0] = '\0';
        return 0;
    }
    
    char buffer[64];
    int pos = 0;
    
#if SIMJOT_DETECTED_PLATFORM == SIMJOT_PLATFORM_MACOS
    // macOS: Use symbols ⌘⇧⌥⌃
    if (modifiers & SIMJOT_MOD_CTRL) {
        // Control: ⌃ (U+2303)
        const char* ctrl = "⌃";
        int len = strlen(ctrl);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, ctrl, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_ALT) {
        // Option: ⌥ (U+2325)
        const char* opt = "⌥";
        int len = strlen(opt);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, opt, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_SHIFT) {
        // Shift: ⇧ (U+21E7)
        const char* shift = "⇧";
        int len = strlen(shift);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, shift, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_META) {
        // Command: ⌘ (U+2318)
        const char* cmd = "⌘";
        int len = strlen(cmd);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, cmd, len);
            pos += len;
        }
    }
#else
    // Windows/Linux: Use text abbreviations
    if (modifiers & SIMJOT_MOD_CTRL) {
        const char* ctrl = "Ctrl+";
        int len = strlen(ctrl);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, ctrl, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_ALT) {
        const char* alt = "Alt+";
        int len = strlen(alt);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, alt, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_SHIFT) {
        const char* shift = "Shift+";
        int len = strlen(shift);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, shift, len);
            pos += len;
        }
    }
    if (modifiers & SIMJOT_MOD_META) {
        const char* meta = "Win+";
        int len = strlen(meta);
        if (pos + len < (int)sizeof(buffer)) {
            memcpy(buffer + pos, meta, len);
            pos += len;
        }
    }
#endif
    
    // Append the key
    if (pos < (int)sizeof(buffer) - 1) {
        buffer[pos++] = static_cast<char>(std::toupper(key_code));
    }
    buffer[pos] = '\0';
    
    // Copy to output
    int needed = pos + 1;
    if (needed > out_len) {
        // Buffer too small
        if (out_len > 0) out[0] = '\0';
        return -needed;
    }
    
    memcpy(out, buffer, needed);
    return pos;
}

int32_t simjot_hotkey_check_batch(const int32_t* key_codes, const int32_t* modifiers,
                                   int32_t count, int32_t* out_actions) {
    if (!key_codes || !modifiers || !out_actions || count <= 0) {
        return 0;
    }
    
    init_lookup_table();
    
    int32_t matched = 0;
    for (int32_t i = 0; i < count; i++) {
        out_actions[i] = simjot_hotkey_check(key_codes[i], modifiers[i]);
        if (out_actions[i] != SIMJOT_ACTION_NONE) {
            matched++;
        }
    }
    
    return matched;
}

} /* extern "C" */
